package io.github.hyperisland.compose.service

import android.os.Build
import io.github.hyperisland.BuildConfig
import java.util.concurrent.TimeUnit

internal data class HomeSystemInfo(
    val systemVersion: String,
    val appVersion: String,
    val deviceModel: String,
)

internal object SystemInfoProvider {
    fun load(): HomeSystemInfo = HomeSystemInfo(
        systemVersion = getProperty("ro.build.version.incremental")
            .ifBlank { Build.VERSION.INCREMENTAL.orEmpty() },
        appVersion = BuildConfig.VERSION_NAME,
        deviceModel = getProperty("ro.product.marketname")
            .ifBlank { Build.MODEL.orEmpty() },
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
