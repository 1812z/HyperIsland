package io.github.hyperisland.compose.service

import android.app.Application
import android.content.Context
import com.aptabase.Aptabase
import io.github.hyperisland.utils.DevelopmentEnvironmentInfoProvider

/** Sends a low-frequency compatibility snapshot from the app process only. */
internal object AnalyticsService {
    private const val APP_KEY = "A-US-5058310366"
    private const val STATE_PREFS = "HyperIslandAnalyticsState"
    private const val LAST_SENT_AT = "last_environment_snapshot_at"
    private const val LAST_SENT_VERSION = "last_environment_snapshot_version"
    private const val LAST_SENT_SCHEMA = "last_environment_snapshot_schema"
    private const val SCHEMA_VERSION = 2
    private const val REPORT_INTERVAL_MS = 24L * 60L * 60L * 1000L

    @Volatile
    private var initialized = false

    @Synchronized
    fun trackEnvironmentSnapshot(context: Context, isNewUser: Boolean) {
        val appContext = context.applicationContext
        if (Application.getProcessName() != appContext.packageName) return

        val environment = DevelopmentEnvironmentInfoProvider.load(appContext, isNewUser)
        val state = appContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastSentAt = state.getLong(LAST_SENT_AT, 0L)
        val lastSentVersion = state.getInt(LAST_SENT_VERSION, -1)
        val lastSentSchema = state.getInt(LAST_SENT_SCHEMA, 0)
        val elapsed = now - lastSentAt
        val due = lastSentSchema != SCHEMA_VERSION ||
            lastSentVersion != environment.appVersionCode ||
            elapsed < 0L || elapsed >= REPORT_INTERVAL_MS
        if (!due) return

        if (!initialized) {
            Aptabase.instance.initialize(appContext, APP_KEY)
            initialized = true
        }
        Aptabase.instance.trackEvent(
            "environment_snapshot",
            mapOf(
                "app_version_name" to environment.appVersionName,
                "app_version_code" to environment.appVersionCode,
                "android_api" to environment.androidApi,
                "android_release" to environment.androidRelease,
                "device_model" to environment.deviceModel,
                "rom_incremental" to environment.romIncremental,
                "hyperos_version_name" to environment.hyperOsVersionName,
                "systemui_version_name" to environment.systemUi.versionName,
                "systemui_version_code" to environment.systemUi.versionCode,
                "systemui_plugin_version_name" to environment.systemUiPlugin.versionName,
                "systemui_plugin_version_code" to environment.systemUiPlugin.versionCode,
                "focus_protocol_version" to environment.focusProtocolVersion,
                "xposed_framework_name" to environment.xposedFrameworkName,
                "xposed_framework_version" to environment.xposedFrameworkVersion,
                "module_active" to if (environment.moduleActive) "yes" else "no",
                "new_user" to if (environment.newUser) "yes" else "no",
            ),
        )
        state.edit()
            .putLong(LAST_SENT_AT, now)
            .putInt(LAST_SENT_VERSION, environment.appVersionCode)
            .putInt(LAST_SENT_SCHEMA, SCHEMA_VERSION)
            .apply()
    }
}
