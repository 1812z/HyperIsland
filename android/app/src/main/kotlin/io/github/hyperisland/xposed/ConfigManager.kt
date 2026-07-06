package io.github.hyperisland.xposed

import android.content.SharedPreferences
import io.github.libxposed.api.XposedModule

/**
 * 基于 RemotePreferences 的配置管理器。RemotePreferences 打开时会初始化整组数据，
 * 所以配置被拆为一个小 core 组和多个 shard，避免单次 Binder 事务超过缓冲区。
 */
object ConfigManager {

    private const val TAG = "HyperIsland[ConfigManager]"
    private const val FLUTTER_KEY_PREFIX = "flutter."
    private const val PREFS_CORE = "HyperIslandXposedCore"
    private const val PREFS_SHARD_PREFIX = "HyperIslandXposedShard"
    private const val SHARD_COUNT = 32
    /** Flutter shared_preferences 存储 double 时使用的 Base64 前缀（不需要解码，直接截取）。 */
    private const val DOUBLE_PREFIX_ENCODED = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"

    @Volatile private var corePrefs: SharedPreferences? = null
    @Volatile private var initialized = false
    @Volatile private var module: XposedModule? = null

    private val shardPrefs = arrayOfNulls<SharedPreferences>(SHARD_COUNT)

    private val changeListeners = mutableListOf<() -> Unit>()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        module?.log("$TAG: prefs changed: key=$key")
        notifyListeners()
    }

    /**
     * 初始化：直接通过 [XposedModule.getRemotePreferences] 同步获取远程 SharedPreferences。
     * 幂等，多次调用只执行一次。
     */
    @Synchronized
    fun init(module: XposedModule) {
        if (initialized) return
        try {
            val p = module.getRemotePreferences(PREFS_CORE)
            p.registerOnSharedPreferenceChangeListener(prefsListener)
            corePrefs = p
            this.module = module
            initialized = true
            module.log("$TAG: remote prefs '$PREFS_CORE' loaded")
            notifyListeners()
        } catch (e: UnsupportedOperationException) {
            module.logWarn("$TAG: init failed: embedded framework, remote prefs unavailable")
            initialized = true
        } catch (e: Throwable) {
            module.logError("$TAG: init failed: ${e.message}")
            initialized = true
        }
    }

    /** 注册配置变化回调，Prefs 每次变更后触发（调用方负责只注册一次）。 */
    @Synchronized
    fun addChangeListener(listener: () -> Unit) {
        changeListeners += listener
    }

    // ── 类型化读取 ──────────────────────────────────────────────────────────────

    fun getBoolean(key: String, default: Boolean): Boolean =
        try { prefsForKey(key)?.getBoolean(fk(key), default) ?: default }
        catch (_: ClassCastException) { default }

    fun getString(key: String, default: String = ""): String =
        try { prefsForKey(key)?.getString(fk(key), default) ?: default }
        catch (_: ClassCastException) { default }

    /**
     * Flutter 的 int 在 Android SharedPreferences 中以 Long 存储，
     * 优先用 getLong 读取再转换，若类型不符再尝试 getInt。
     */
    fun getInt(key: String, default: Int): Int =
        try { prefsForKey(key)?.getLong(fk(key), default.toLong())?.toInt() ?: default }
        catch (_: ClassCastException) {
            try { prefsForKey(key)?.getInt(fk(key), default) ?: default }
            catch (_: ClassCastException) { default }
        }

    /**
     * Flutter 的 double 在 Android SharedPreferences 中以特定前缀 + 明文值存储，
     * 格式为 "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu" + value。
     * 直接截取前缀后的字符串即可获取实际 double 值。
     */
    fun getDouble(key: String, default: Double): Double {
        val raw = try { prefsForKey(key)?.getString(fk(key), null) } catch (_: Throwable) { null }
            ?: return default
        return try {
            if (raw.startsWith(DOUBLE_PREFIX_ENCODED)) {
                raw.substring(DOUBLE_PREFIX_ENCODED.length).trim().toDoubleOrNull() ?: default
            } else {
                raw.toDoubleOrNull() ?: default
            }
        } catch (_: Throwable) { default }
    }

    /**
     * Flutter 的 double 在 Android SharedPreferences 中以 String 存储，
     * 优先用 getString 读取再转换，若失败再尝试 getFloat。
     */
    fun getFloat(key: String, default: Float): Float =
        try {
            val prefs = prefsForKey(key)
            val raw = prefs?.getString(fk(key), null)
            if (raw != null && raw.startsWith(DOUBLE_PREFIX_ENCODED)) {
                raw.substring(DOUBLE_PREFIX_ENCODED.length).trim().toFloatOrNull() ?: default
            } else {
                raw?.toFloatOrNull() ?: prefs?.getFloat(fk(key), default) ?: default
            }
        }
        catch (_: ClassCastException) { default }

    fun contains(key: String): Boolean =
        prefsForKey(key)?.contains(fk(key)) ?: false

    fun isDebugLogEnabled(): Boolean = getBoolean("pref_debug_log", true)

    /** 供同进程内其他组件（如 template）获取 module 引用以写日志。 */
    fun module(): XposedModule? = module

    // ── 内部实现 ────────────────────────────────────────────────────────────────

    private fun fk(key: String) = "$FLUTTER_KEY_PREFIX$key"

    private fun prefsForKey(key: String): SharedPreferences? {
        if (isCoreKey(key)) return corePrefs
        val index = shardForKey(fk(key))
        shardPrefs[index]?.let { return it }
        val m = module ?: return null
        return synchronized(this) {
            shardPrefs[index] ?: try {
                m.getRemotePreferences("$PREFS_SHARD_PREFIX$index").also { prefs ->
                    prefs.registerOnSharedPreferenceChangeListener(prefsListener)
                    shardPrefs[index] = prefs
                    m.log("$TAG: remote prefs '$PREFS_SHARD_PREFIX$index' loaded")
                }
            } catch (e: Throwable) {
                m.logError("$TAG: shard $index load failed: ${e.message}")
                null
            }
        }
    }

    private fun isCoreKey(key: String): Boolean {
        return key in CORE_PREF_KEYS || key.startsWith("pref_scene_surface_")
    }

    private fun shardForKey(key: String): Int {
        return (key.hashCode() and Int.MAX_VALUE) % SHARD_COUNT
    }

    private fun notifyListeners() {
        val ls = synchronized(this) { changeListeners.toList() }
        ls.forEach { runCatching { it() } }
    }

    private val CORE_PREF_KEYS = setOf(
        "pref_show_welcome",
        "pref_resume_notification",
        "pref_settings_home_entry",
        "pref_bluetooth_island",
        "pref_bluetooth_island_show_device_name",
        "pref_bluetooth_island_display_duration_seconds",
        "pref_bluetooth_island_outer_glow",
        "pref_bluetooth_island_outer_glow_color",
        "pref_bluetooth_island_whitelist_enabled",
        "pref_bluetooth_island_whitelist_addresses",
        "pref_interaction_haptics",
        "pref_round_icon",
        "pref_marquee_feature",
        "pref_marquee_speed",
        "pref_big_island_max_width",
        "pref_big_island_min_width",
        "pref_smooth_island",
        "pref_smooth_island_smoothing",
        "pref_unlock_all_focus",
        "pref_unlock_focus_auth",
        "pref_charge_island",
        "pref_charge_island_left_mode",
        "pref_charge_island_right_mode",
        "pref_charge_island_duration_mode",
        "pref_charge_island_duration_seconds",
        "pref_charge_island_outer_glow",
        "pref_command_token_enabled",
        "pref_command_token_douyin_enabled",
        "pref_command_token_timeout_seconds",
        "pref_command_token_clear_clip_after_click",
        "pref_command_token_dedup_window_seconds",
        "pref_default_first_float",
        "pref_default_enable_float",
        "pref_default_show_island_icon",
        "pref_default_marquee",
        "pref_default_marquee_auto_hide",
        "pref_default_focus_notif",
        "pref_default_aod_text",
        "pref_default_dynamic_highlight_color",
        "pref_default_outer_glow",
        "pref_default_island_outer_glow",
        "pref_default_force_outer_glow",
        "pref_default_force_island_outer_glow",
        "pref_default_restore_lockscreen",
        "pref_default_preserve_small_icon",
        "pref_fullscreen_behavior",
        "pref_landscape_behavior",
        "pref_scene_dnd",
        "pref_scene_fullscreen",
        "pref_scene_landscape",
        "pref_ai_enabled",
        "pref_ai_prompt_in_user",
        "pref_ai_timeout",
        "pref_ai_temperature",
        "pref_ai_max_tokens",
        "pref_island_height",
        "pref_island_top_offset",
        "pref_island_text_color_mode",
        "pref_keep_island",
        "pref_keep_island_auto_hide",
        "pref_keep_island_hide_landscape",
        "pref_temp_hide_screen_pinning",
        "pref_temp_hide_bouncer_showing",
        "pref_temp_hide_fullscreen",
        "pref_temp_hide_screen_locked",
        "pref_temp_hide_notification_center",
        "pref_blur_bars",
        "pref_debug_log"
    )
}
