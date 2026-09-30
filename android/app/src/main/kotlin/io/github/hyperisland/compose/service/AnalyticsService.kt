package io.github.hyperisland.compose.service

import android.app.Application
import android.content.Context
import android.util.Log
import com.aptabase.EnvironmentInfo
import io.github.hyperisland.utils.DevelopmentEnvironmentInfoProvider
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import java.util.UUID

/** Sends a compatibility snapshot on each app launch, using a persistent installation ID. */
internal object AnalyticsService {
    private const val APP_KEY = "A-SH-5661625625"
    private const val EVENT_URL = "https://aptabase.1812z.top/api/v0/event"
    private const val STATE_PREFS = "HyperIslandAnalyticsState"
    private const val INSTALLATION_ID = "installation_id"

    @Synchronized
    fun trackEnvironmentSnapshot(context: Context, isNewUser: Boolean) {
        val appContext = context.applicationContext
        if (Application.getProcessName() != appContext.packageName) return
        if (!PrivacyConsentStore.isAccepted(appContext)) return

        try {
            val state = appContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            val installationId = state.getString(INSTALLATION_ID, null)
                ?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString().also {
                    // Persist before sending so an interrupted launch cannot change the ID.
                    if (!state.edit().putString(INSTALLATION_ID, it).commit()) return
                }
            val environment = DevelopmentEnvironmentInfoProvider.load(appContext, isNewUser)
            val sdkEnvironment = EnvironmentInfo.get(appContext)
            val properties = mapOf(
                "installation_id" to installationId,
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
            )
            // SDK 0.0.8 cannot supply a persistent sessionId. Send its event format directly
            // so the ID used by Aptabase itself stays stable, not just a custom property.
            val event = JSONObject()
                .put("timestamp", Instant.now().toString())
                .put("sessionId", installationId)
                .put("eventName", "environment_snapshot")
                .put("systemProps", JSONObject(mapOf(
                    "isDebug" to sdkEnvironment.isDebug,
                    "osName" to sdkEnvironment.osName,
                    "osVersion" to sdkEnvironment.osVersion,
                    "locale" to sdkEnvironment.locale,
                    "appVersion" to sdkEnvironment.appVersion,
                    "appBuildNumber" to sdkEnvironment.appBuildNumber,
                    "deviceModel" to sdkEnvironment.deviceModel,
                    "sdkVersion" to "hyperisland-aptabase@1",
                )))
                .put("props", JSONObject(properties))
            val connection = URI(EVENT_URL).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("App-Key", APP_KEY)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                    it.write(event.toString())
                }
                if (connection.responseCode !in 200..299) {
                    Log.w("AnalyticsService", "Snapshot rejected: HTTP ${connection.responseCode}")
                }
            } finally {
                connection.disconnect()
            }
        } catch (exception: Exception) {
            Log.w("AnalyticsService", "Could not send environment snapshot", exception)
        }
    }
}
