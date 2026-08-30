package io.github.hyperisland

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.security.MessageDigest

/**
 * 自定义 Application，负责将 Compose 端写入的兼容 SharedPreferences 镜像同步到
 * LSPosed 的 RemotePreferences，使 Hook 进程能读到最新配置。
 *
 * 架构参考 example/App.kt + example/MainActivity.kt：
 *   - App 端通过 [XposedServiceHelper] 获取 [XposedService]
 * RemotePreferences 在 hook 进程打开时会经 Binder 初始化整组数据，单组过大会触发
 * TransactionTooLarge/DeadObject。这里将配置拆为 core + shards，避免任何单个 prefs 组过大。
 */
class XposedPrefsSyncApp : Application(), XposedServiceHelper.OnServiceListener {

    private val configPrefs: SharedPreferences by lazy {
        getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Volatile
    private var xposedService: XposedService? = null

    private val syncLock = Any()

    private val configPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        syncKeyToRemote(prefs, key)
    }

    override fun onCreate() {
        super.onCreate()
        AppLocaleController.apply(
            this,
            configPrefs.getString("flutter.pref_locale", "").orEmpty(),
        )
        XposedServiceHelper.registerListener(this)
        configPrefs.registerOnSharedPreferenceChangeListener(configPrefsListener)
    }

    override fun onTerminate() {
        configPrefs.unregisterOnSharedPreferenceChangeListener(configPrefsListener)
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

    /** Service 刚绑定时，仅在配置摘要变化后按分组差异同步。 */
    private fun syncAllToRemote(service: XposedService) {
        syncToRemote(service, configPrefs, key = null)
    }

    private fun syncToRemote(service: XposedService, sourcePrefs: SharedPreferences, key: String?) {
        synchronized(syncLock) {
            try {
                if (key == null) {
                    syncAllIfChanged(service, sourcePrefs)
                } else {
                    if (!shouldSyncKey(key)) return
                    invalidateRemoteDigest(service)
                    val remote = service.getRemotePreferences(remotePrefsNameForKey(key))
                    val editor = remote.edit() ?: error("remote editor unavailable")
                    check(writeValue(editor, key, sourcePrefs.all[key])) {
                        "unsupported preference value for $key"
                    }
                    check(editor.commit()) { "remote commit failed for $key" }
                    Log.d(TAG, "synced key=$key to remote prefs")
                }
            } catch (e: Exception) {
                val scope = if (key == null) "all" else key
                Log.w(TAG, "syncToRemote failed (key=$scope): ${e.message}")
            }
        }
    }

    fun requestScope(packages: List<String>) {
        val service = xposedService ?: throw IllegalStateException("XposedService is not ready")
        requestScope(service, packages) {}
    }

    fun requestScope(
        packages: List<String>,
        onResult: (Result<List<String>>) -> Unit,
    ) {
        val service = xposedService
        if (service == null) {
            onResult(Result.failure(IllegalStateException("XposedService is not ready")))
            return
        }
        requestScope(service, packages, onResult)
    }

    private fun requestScope(
        service: XposedService,
        packages: List<String>,
        onResult: (Result<List<String>>) -> Unit,
    ) {
        val currentScope = service.scope.toSet()
        val missingPackages = packages.filterNot { it in currentScope }
        if (missingPackages.isEmpty()) {
            Log.d(TAG, "scope already granted: $packages")
            onResult(Result.success(service.scope))
            return
        }

        service.requestScope(missingPackages, object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(scope: List<String>) {
                Log.d(TAG, "scope request approved: $scope")
                onResult(Result.success(scope))
            }

            override fun onScopeRequestFailed(message: String) {
                Log.w(TAG, "scope request failed: $message")
                onResult(Result.failure(IllegalStateException(message)))
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

    private fun syncAllIfChanged(service: XposedService, src: SharedPreferences) {
        val source = src.all.filterKeys(::shouldSyncKey)
        val digest = configDigest(source)
        val meta = service.getRemotePreferences(REMOTE_PREFS_META)
        if (meta.getInt(META_FORMAT_VERSION, 0) == SYNC_FORMAT_VERSION &&
            meta.getString(META_CONFIG_DIGEST, null) == digest
        ) {
            Log.d(TAG, "config unchanged, skipped full sync (${source.size} keys)")
            return
        }

        val grouped = source.entries.groupBy({ remotePrefsNameForKey(it.key) }, { it })
        var changedGroups = 0
        for (prefsName in allRemotePrefsNames()) {
            val entries = grouped[prefsName].orEmpty()
            if (syncGroupDiff(service, prefsName, entries)) changedGroups++
        }
        updateRemoteDigest(service, digest)
        Log.d(TAG, "diff sync done: ${source.size} keys, $changedGroups changed groups")
    }

    private fun syncGroupDiff(
        service: XposedService,
        prefsName: String,
        entries: List<Map.Entry<String, Any?>>,
    ): Boolean {
        val remote = service.getRemotePreferences(prefsName)
        val current = remote.all
        val target = entries.associate { it.key to it.value }
        val removedKeys = current.keys - target.keys
        val changedEntries = target.filter { (key, value) -> current[key] != value }
        if (removedKeys.isEmpty() && changedEntries.isEmpty()) return false

        val editor = remote.edit() ?: error("remote editor unavailable for $prefsName")
        removedKeys.forEach(editor::remove)
        for ((key, value) in changedEntries) {
            check(writeValue(editor, key, value)) { "unsupported preference value for $key" }
        }
        check(editor.commit()) { "remote commit failed for $prefsName" }
        Log.d(
            TAG,
            "diff synced $prefsName: ${changedEntries.size} changed, ${removedKeys.size} removed",
        )
        return true
    }

    private fun updateRemoteDigest(service: XposedService, digest: String) {
        val editor = service.getRemotePreferences(REMOTE_PREFS_META).edit()
            ?: error("remote metadata editor unavailable")
        editor.putInt(META_FORMAT_VERSION, SYNC_FORMAT_VERSION)
        editor.putString(META_CONFIG_DIGEST, digest)
        check(editor.commit()) { "remote metadata commit failed" }
    }

    private fun invalidateRemoteDigest(service: XposedService) {
        val editor = service.getRemotePreferences(REMOTE_PREFS_META).edit()
            ?: error("remote metadata editor unavailable")
        editor.remove(META_CONFIG_DIGEST)
        check(editor.commit()) { "remote metadata invalidation failed" }
    }

    private fun configDigest(values: Map<String, *>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        values.asSequence()
            .filter { shouldSyncKey(it.key) }
            .sortedBy { it.key }
            .forEach { (key, value) ->
                updateDigestPart(digest, key)
                when (value) {
                    is Boolean -> updateDigestPart(digest, "b:$value")
                    is Int -> updateDigestPart(digest, "i:$value")
                    is Long -> updateDigestPart(digest, "l:$value")
                    is Float -> updateDigestPart(digest, "f:${value.toRawBits()}")
                    is String -> updateDigestPart(digest, "s:$value")
                    is Set<*> -> {
                        updateDigestPart(digest, "set")
                        value.filterIsInstance<String>().sorted().forEach {
                            updateDigestPart(digest, it)
                        }
                    }
                    null -> updateDigestPart(digest, "null")
                    else -> updateDigestPart(digest, "unsupported:${value::class.java.name}:$value")
                }
            }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun updateDigestPart(digest: MessageDigest, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        digest.update((bytes.size ushr 24).toByte())
        digest.update((bytes.size ushr 16).toByte())
        digest.update((bytes.size ushr 8).toByte())
        digest.update(bytes.size.toByte())
        digest.update(bytes)
    }

    private fun allRemotePrefsNames(): List<String> = buildList(SHARD_COUNT + 1) {
        add(REMOTE_PREFS_CORE)
        for (index in 0 until SHARD_COUNT) add("$REMOTE_PREFS_SHARD_PREFIX$index")
    }

    private fun writeValue(editor: SharedPreferences.Editor, key: String, value: Any?): Boolean {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int     -> editor.putInt(key, value)
            is Long    -> editor.putLong(key, value)
            is Float   -> editor.putFloat(key, value)
            is String  -> editor.putString(key, value)
            is Set<*>  -> {
                if (value.any { it !is String }) return false
                editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
            null       -> editor.remove(key)
            else       -> return false
        }
        return true
    }

    private fun shouldSyncKey(key: String): Boolean {
        if (!key.startsWith(LEGACY_KEY_PREFIX)) return false
        val rawKey = key.removePrefix(LEGACY_KEY_PREFIX)
        if (!rawKey.startsWith("pref_")) return false
        return rawKey != "pref_onboarding_completed" &&
            rawKey != "pref_config_app_version" &&
            rawKey != "pref_config_schema_version"
    }

    private fun remotePrefsNameForKey(key: String): String {
        val rawKey = key.removePrefix(LEGACY_KEY_PREFIX)
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
        private const val LEGACY_PREFS_NAME = "FlutterSharedPreferences"
        private const val LEGACY_KEY_PREFIX = "flutter."
        const val REMOTE_PREFS_CORE = "HyperIslandXposedCore"
        const val REMOTE_PREFS_SHARD_PREFIX = "HyperIslandXposedShard"
        private const val REMOTE_PREFS_META = "HyperIslandXposedMeta"
        const val SHARD_COUNT = 32
        private const val META_FORMAT_VERSION = "sync_format_version"
        private const val META_CONFIG_DIGEST = "config_digest"
        private const val SYNC_FORMAT_VERSION = 1

        private val CORE_PREF_KEYS = setOf(
            "pref_show_welcome",
            "pref_resume_notification",
            "pref_screen_recorder_island",
            "pref_screen_recorder_immediate_start",
            "pref_screen_recorder_icon_style",
            "pref_settings_home_entry",
            "pref_settings_home_entry_position",
            "pref_settings_home_entry_icon_style",
            "pref_bluetooth_island",
            "pref_bluetooth_island_show_device_name",
            "pref_bluetooth_island_display_duration_seconds",
            "pref_bluetooth_island_outer_glow",
            "pref_bluetooth_island_outer_glow_color",
            "pref_bluetooth_island_whitelist_enabled",
            "pref_bluetooth_island_whitelist_addresses",
            "pref_round_icon",
            "pref_round_icon_radius",
            "pref_island_icon_size",
            "pref_island_icon_padding",
            "pref_marquee_feature",
            "pref_marquee_speed",
            "pref_big_island_max_width",
            "pref_big_island_min_width",
            "pref_smooth_island",
            "pref_smooth_island_smoothing",
            "pref_always_show_island_outline",
            "pref_always_show_focus_outline",
            "pref_outer_glow_range",
            "pref_outer_glow_single_color",
            "pref_outer_glow_base_color",
            "pref_unlock_all_focus",
            "pref_unlock_focus_auth",
            "pref_charge_island",
            "pref_charge_island_left_mode",
            "pref_charge_island_right_mode",
            "pref_charge_island_duration_mode",
            "pref_charge_island_duration_seconds",
            "pref_charge_island_outer_glow",
            "pref_face_unlock_island",
            "pref_face_unlock_island_first_float",
            "pref_face_unlock_island_animation_style",
            "pref_face_unlock_island_keep_until_keyguard_hidden",
            "pref_hide_lockscreen_face_unlock_icon",
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
            "pref_default_out_effect_color",
            "pref_default_island_outer_glow_color",
            "pref_default_restore_lockscreen",
            "pref_default_preserve_small_icon",
            "pref_default_timeout",
            "pref_fullscreen_behavior",
            "pref_landscape_behavior",
            "pref_scene_dnd",
            "pref_scene_fullscreen",
            "pref_scene_landscape",
            "pref_expanded_collapse_action",
            "pref_big_island_collapse_action",
            "pref_island_swipe_ignore_ongoing",
            "pref_ai_enabled",
            "pref_ai_prompt_in_user",
            "pref_ai_custom_fields",
            "pref_ai_timeout",
            "pref_ai_temperature",
            "pref_ai_max_tokens",
            "pref_ai_trigger_char_count",
            "pref_island_height",
            "pref_island_top_offset",
            "pref_island_text_color_mode",
            "pref_focus_notification_text_color_mode",
            "pref_media_notification_text_color_mode",
            "pref_island_blur_small_enabled",
            "pref_island_blur_small_radius",
            "pref_island_blur_small_color",
            "pref_island_blur_big_enabled",
            "pref_island_blur_big_radius",
            "pref_island_blur_big_color",
            "pref_island_blur_expand_enabled",
            "pref_island_blur_expand_radius",
            "pref_island_blur_expand_color",
            "pref_island_glass_enabled",
            "pref_island_glass_small_enabled",
            "pref_island_glass_big_enabled",
            "pref_island_glass_expand_enabled",
            "pref_island_glass_edge_width",
            "pref_island_glass_refraction",
            "pref_island_glass_highlight",
            "pref_island_glass_shadow",
            "pref_island_glass_light_direction",
            "pref_island_glass_dispersion",
            "pref_island_glass_gyroscope",
            "pref_island_glass_hdr_highlight",
            "pref_island_glass_true_refraction",
            "pref_island_refraction_small_enabled",
            "pref_island_refraction_big_enabled",
            "pref_island_refraction_expand_enabled",
            "pref_island_glass_capture_fps",
            "pref_island_glass_capture_quality",
            "pref_keep_island",
            "pref_keep_island_display_timing",
            "pref_keep_island_show_notification",
            "pref_keep_island_auto_hide",
            "pref_keep_island_hide_landscape",
            "pref_keep_island_highlight_color",
            "pref_keep_island_left_highlight",
            "pref_keep_island_right_highlight",
            "pref_keep_island_left_content",
            "pref_keep_island_right_content",
            "pref_keep_island_carousel_interval_seconds",
            "pref_keep_island_focus_notification",
            "pref_keep_island_focus_content_type",
            "pref_keep_island_expand_text_color_mode",
            "pref_keep_island_notification_title",
            "pref_keep_island_notification_content",
            "pref_keep_island_show_island_icon",
            "pref_keep_island_custom_icon_path",
            "pref_temp_hide_screen_pinning",
            "pref_temp_hide_bouncer_showing",
            "pref_temp_hide_fullscreen",
            "pref_temp_hide_fullscreen_landscape_disable",
            "pref_temp_hide_screen_locked",
            "pref_temp_hide_notification_center",
            "pref_temp_hide_foreground_app",
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
