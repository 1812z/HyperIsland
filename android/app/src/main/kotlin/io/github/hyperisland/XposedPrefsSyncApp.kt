package io.github.hyperisland

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/**
 * 自定义 Application，负责将 Flutter 端写入的 SharedPreferences 镜像同步到
 * LSPosed 的 RemotePreferences，使 Hook 进程能读到最新配置。
 *
 * 架构参考 example/App.kt + example/MainActivity.kt：
 *   - App 端通过 [XposedServiceHelper] 获取 [XposedService]
 * RemotePreferences 在 hook 进程打开时会经 Binder 初始化整组数据，单组过大会触发
 * TransactionTooLarge/DeadObject。这里将配置拆为 core + shards，避免任何单个 prefs 组过大。
 */
class XposedPrefsSyncApp : Application(), XposedServiceHelper.OnServiceListener {

    private val flutterPrefs: SharedPreferences by lazy {
        getSharedPreferences(FLUTTER_PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Volatile
    private var xposedService: XposedService? = null

    private val flutterPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        syncKeyToRemote(prefs, key)
    }

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
        flutterPrefs.registerOnSharedPreferenceChangeListener(flutterPrefsListener)
    }

    override fun onTerminate() {
        flutterPrefs.unregisterOnSharedPreferenceChangeListener(flutterPrefsListener)
        xposedService = null
        ServiceState.markNotReady()
        super.onTerminate()
    }

    // ── XposedService 回调 ────────────────────────────────────────────────────

    override fun onServiceBind(service: XposedService) {
        xposedService = service
        ServiceState.markReady(service.apiVersion, service.frameworkName, service.frameworkVersion)
        Log.d(TAG, "XposedService bound, syncing sharded prefs")
        syncAllToRemote(service)
        ServiceState.notifyReady()
    }

    override fun onServiceDied(service: XposedService) {
        xposedService = null
        ServiceState.markNotReady()
        Log.d(TAG, "XposedService died")
    }

    // ── 同步实现 ──────────────────────────────────────────────────────────────

    /** 单个 key 变更时，增量同步到 RemotePreferences。 */
    private fun syncKeyToRemote(prefs: SharedPreferences, key: String?) {
        val service = xposedService ?: return
        syncToRemote(service, prefs, key)
    }

    /** Service 刚绑定时，将所有 FlutterSharedPreferences 条目全量同步。 */
    private fun syncAllToRemote(service: XposedService) {
        syncToRemote(service, flutterPrefs, key = null)
    }

    private fun syncToRemote(service: XposedService, sourcePrefs: SharedPreferences, key: String?) {
        try {
            if (key == null) {
                writeAllSharded(service, sourcePrefs)
            } else {
                if (!shouldSyncKey(key)) return
                val remote = service.getRemotePreferences(remotePrefsNameForKey(key))
                val editor = remote.edit() ?: return
                writeValue(editor, key, sourcePrefs.all[key])
                editor.apply()
            }
            if (key == null) {
                Log.d(TAG, "full sync done: ${sourcePrefs.all.size} keys")
            } else {
                Log.d(TAG, "synced key=$key to remote prefs")
            }
        } catch (e: Exception) {
            val scope = if (key == null) "all" else key
            Log.w(TAG, "syncToRemote failed (key=$scope): ${e.message}")
        }
    }

    fun requestScope(packages: List<String>) {
        val service = xposedService ?: throw IllegalStateException("XposedService is not ready")
        val currentScope = service.scope.toSet()
        val missingPackages = packages.filterNot { it in currentScope }
        if (missingPackages.isEmpty()) {
            Log.d(TAG, "scope already granted: $packages")
            return
        }

        service.requestScope(missingPackages, object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(scope: List<String>) {
                Log.d(TAG, "scope request approved: $scope")
            }

            override fun onScopeRequestFailed(message: String) {
                Log.w(TAG, "scope request failed: $message")
            }
        })
    }

    fun getCurrentScope(): List<String> {
        val service = xposedService ?: throw IllegalStateException("XposedService is not ready")
        return service.scope
    }

    fun getFrameworkInfo(): Map<String, Any> {
        val service = xposedService ?: throw IllegalStateException("XposedService is not ready")
        return mapOf(
            "apiVersion" to service.apiVersion,
            "frameworkName" to service.frameworkName,
            "frameworkVersion" to service.frameworkVersion,
            "frameworkVersionCode" to service.frameworkVersionCode,
            "scope" to service.scope
        )
    }

    private fun writeAllSharded(service: XposedService, src: SharedPreferences) {
        clearAllRemotePrefs(service)

        val grouped = src.all
            .filterKeys { shouldSyncKey(it) }
            .entries
            .groupBy { remotePrefsNameForKey(it.key) }

        for ((prefsName, entries) in grouped) {
            val remote = service.getRemotePreferences(prefsName)
            var editor = remote.edit() ?: continue
            for ((key, value) in entries) {
                writeValue(editor, key, value)
            }
            editor.apply()
            Log.d(TAG, "synced ${entries.size} keys to $prefsName")
        }
    }

    private fun clearAllRemotePrefs(service: XposedService) {
        service.getRemotePreferences(REMOTE_PREFS_CORE).edit()?.clear()?.apply()
        for (index in 0 until SHARD_COUNT) {
            service.getRemotePreferences("$REMOTE_PREFS_SHARD_PREFIX$index").edit()?.clear()?.apply()
        }
    }

    private fun writeValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int     -> editor.putInt(key, value)
            is Long    -> editor.putLong(key, value)
            is Float   -> editor.putFloat(key, value)
            is String  -> editor.putString(key, value)
            is Set<*>  -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            null       -> editor.remove(key)
        }
    }

    private fun shouldSyncKey(key: String): Boolean {
        if (!key.startsWith(FLUTTER_KEY_PREFIX)) return false
        val rawKey = key.removePrefix(FLUTTER_KEY_PREFIX)
        if (!rawKey.startsWith("pref_")) return false
        return rawKey != "pref_onboarding_completed" &&
            rawKey != "pref_config_app_version" &&
            rawKey != "pref_config_schema_version"
    }

    private fun remotePrefsNameForKey(key: String): String {
        val rawKey = key.removePrefix(FLUTTER_KEY_PREFIX)
        return if (isCoreKey(rawKey)) {
            REMOTE_PREFS_CORE
        } else {
            "$REMOTE_PREFS_SHARD_PREFIX${shardForKey(key)}"
        }
    }

    private fun isCoreKey(rawKey: String): Boolean {
        return rawKey in CORE_PREF_KEYS ||
            rawKey.startsWith("pref_scene_surface_")
    }

    private fun shardForKey(key: String): Int {
        return (key.hashCode() and Int.MAX_VALUE) % SHARD_COUNT
    }

    companion object {
        private const val TAG = "HyperIsland[App]"
        private const val FLUTTER_PREFS_NAME = "FlutterSharedPreferences"
        private const val FLUTTER_KEY_PREFIX = "flutter."
        const val REMOTE_PREFS_CORE = "HyperIslandXposedCore"
        const val REMOTE_PREFS_SHARD_PREFIX = "HyperIslandXposedShard"
        const val SHARD_COUNT = 32

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
            "pref_keep_island_highlight_color",
            "pref_keep_island_left_content",
            "pref_keep_island_right_content",
            "pref_temp_hide_screen_pinning",
            "pref_temp_hide_bouncer_showing",
            "pref_temp_hide_fullscreen",
            "pref_temp_hide_screen_locked",
            "pref_temp_hide_notification_center",
            "pref_blur_bars",
            "pref_debug_log"
        )

        private object ServiceState {
            @Volatile private var serviceReady = false
            @Volatile private var apiVersion: Int = 0
            @Volatile private var frameworkName: String = ""
            @Volatile private var frameworkVersion: String = ""
            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
            private val serviceReadyLock = Object()

            fun isReady(): Boolean = serviceReady

            fun getApiVersion(): Int = apiVersion

            fun getFrameworkName(): String = frameworkName

            fun getFrameworkVersion(): String = frameworkVersion

            fun markReady(newApiVersion: Int, newFrameworkName: String, newFrameworkVersion: String) {
                apiVersion = newApiVersion
                frameworkName = newFrameworkName
                frameworkVersion = newFrameworkVersion
                serviceReady = true
            }

            fun markNotReady() {
                serviceReady = false
                apiVersion = 0
                frameworkName = ""
                frameworkVersion = ""
            }

            fun notifyReady() {
                synchronized(serviceReadyLock) { serviceReadyLock.notifyAll() }
            }

            fun awaitReady(timeoutMs: Long): Boolean {
                if (isReady()) return true
                synchronized(serviceReadyLock) {
                    if (!isReady()) {
                        try {
                            serviceReadyLock.wait(timeoutMs)
                        } catch (_: InterruptedException) {
                        }
                    }
                }
                return isReady()
            }
        }

        fun isReady(): Boolean = ServiceState.isReady()

        fun getApiVersion(): Int = ServiceState.getApiVersion()

        fun getFrameworkName(): String = ServiceState.getFrameworkName()

        fun getFrameworkVersion(): String = ServiceState.getFrameworkVersion()

        fun awaitReady(timeoutMs: Long = 1500): Boolean = ServiceState.awaitReady(timeoutMs)
    }
}
