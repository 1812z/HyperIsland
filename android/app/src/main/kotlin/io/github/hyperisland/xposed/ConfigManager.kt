package io.github.hyperisland.xposed

import android.content.SharedPreferences
import io.github.libxposed.api.XposedModule

/**
 * 基于 XposedService.getRemotePreferences 的配置管理器（API 101 版本）。
 *
 * 架构：
 *   - Flutter 的 shared_preferences 插件将全量配置以 "flutter." 前缀写入模块 App 进程的
 *     FlutterSharedPreferences.xml。
 *   - Hook 进程（SystemUI / XMSF / 下载管理器）通过 XposedService.getRemotePreferences()
 *     跨进程读取该文件，并注册 OnSharedPreferenceChangeListener 实现热重载。
 */
object ConfigManager {

    private const val TAG = "HyperIsland[ConfigManager]"
    private const val FLUTTER_KEY_PREFIX = "flutter."
    private const val PREFS_GROUP = "FlutterSharedPreferences"
    /** Flutter shared_preferences 存储 double 时使用的 Base64 前缀（不需要解码，直接截取）。 */
    private const val DOUBLE_PREFIX_ENCODED = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"

    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var initialized = false
    @Volatile private var module: XposedModule? = null

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
            val p = module.getRemotePreferences(PREFS_GROUP)
            p.registerOnSharedPreferenceChangeListener(prefsListener)
            prefs = p
            this.module = module
            initialized = true
            module.log("$TAG: remote prefs '$PREFS_GROUP' loaded")
            notifyListeners()
        } catch (e: UnsupportedOperationException) {
            module.logWarn("$TAG: init failed — embedded framework, remote prefs unavailable")
            initialized = true
        } catch (e: Exception) {
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
        try { prefs?.getBoolean(fk(key), default) ?: default }
        catch (_: ClassCastException) { default }

    fun getString(key: String, default: String = ""): String =
        try { prefs?.getString(fk(key), default) ?: default }
        catch (_: ClassCastException) { default }

    /**
     * Flutter 的 int 在 Android SharedPreferences 中以 Long 存储，
     * 优先用 getLong 读取再转换，若类型不符再尝试 getInt。
     */
    fun getInt(key: String, default: Int): Int =
        try { prefs?.getLong(fk(key), default.toLong())?.toInt() ?: default }
        catch (_: ClassCastException) {
            try { prefs?.getInt(fk(key), default) ?: default }
            catch (_: ClassCastException) { default }
        }

    /**
     * Flutter 的 double 在 Android SharedPreferences 中以特定前缀 + 明文值存储，
     * 格式为 "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu" + value。
     * 直接截取前缀后的字符串即可获取实际 double 值。
     */
    fun getDouble(key: String, default: Double): Double {
        val raw = try { prefs?.getString(fk(key), null) } catch (_: Throwable) { null }
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
            val raw = prefs?.getString(fk(key), null)
            if (raw != null && raw.startsWith(DOUBLE_PREFIX_ENCODED)) {
                raw.substring(DOUBLE_PREFIX_ENCODED.length).trim().toFloatOrNull() ?: default
            } else {
                raw?.toFloatOrNull() ?: prefs?.getFloat(fk(key), default) ?: default
            }
        }
        catch (_: ClassCastException) { default }

    fun contains(key: String): Boolean =
        prefs?.contains(fk(key)) ?: false

    /** 供同进程内其他组件（如 template）获取 module 引用以写日志。 */
    fun module(): XposedModule? = module

    // ── 内部实现 ────────────────────────────────────────────────────────────────

    private fun fk(key: String) = "$FLUTTER_KEY_PREFIX$key"

    private fun notifyListeners() {
        val ls = synchronized(this) { changeListeners.toList() }
        ls.forEach { runCatching { it() } }
    }
}
