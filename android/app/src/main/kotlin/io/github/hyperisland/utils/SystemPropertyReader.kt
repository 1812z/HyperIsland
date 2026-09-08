package io.github.hyperisland.utils

import java.util.concurrent.TimeUnit

/** Reads Android system properties without exposing hidden APIs to callers. */
object SystemPropertyReader {
    @JvmStatic
    fun get(key: String, default: String = ""): String {
        if (key.isBlank()) return default
        val reflected = runCatching {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val method = systemProperties.getMethod(
                "get",
                String::class.java,
                String::class.java,
            )
            (method.invoke(null, key, default) as? String).orEmpty().trim()
        }.getOrDefault("")
        if (reflected.isNotEmpty()) return reflected

        return runCatching {
            val process = ProcessBuilder("/system/bin/getprop", key)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                process.destroy()
                return@runCatching default
            }
            process.inputStream.bufferedReader().use { it.readText().trim() }
                .ifBlank { default }
        }.getOrDefault(default)
    }
}
