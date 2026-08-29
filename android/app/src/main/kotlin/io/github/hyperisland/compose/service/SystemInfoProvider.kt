package io.github.hyperisland.compose.service

import android.content.Context
import android.os.Build
import android.provider.Settings
import io.github.hyperisland.BuildConfig
import java.util.concurrent.TimeUnit

internal data class HomeSystemInfo(
    val systemVersion: String,
    val appVersion: String,
    val appVersionCode: Int,
    val deviceModel: String,
    val focusProtocolVersion: Int,
    val androidSdkVersion: Int,
)

internal object SystemInfoProvider {
    fun load(context: Context): HomeSystemInfo = HomeSystemInfo(
        systemVersion = getProperty("ro.build.version.incremental")
            .ifBlank { Build.VERSION.INCREMENTAL.orEmpty() },
        appVersion = BuildConfig.VERSION_NAME,
        appVersionCode = BuildConfig.VERSION_CODE,
        deviceModel = getProperty("ro.product.marketname")
            .ifBlank { Build.MODEL.orEmpty() },
        focusProtocolVersion = Settings.System.getInt(
            context.contentResolver,
            "notification_focus_protocol",
            0,
        ),
        androidSdkVersion = Build.VERSION.SDK_INT,
    )

    private fun getProperty(key: String): String = runCatching {
        val process = ProcessBuilder("/system/bin/getprop", key)
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(1, TimeUnit.SECONDS)) {
            process.destroy()
            return@runCatching ""
        }
        process.inputStream.bufferedReader().use { it.readText().trim() }
    }.getOrDefault("")
}
