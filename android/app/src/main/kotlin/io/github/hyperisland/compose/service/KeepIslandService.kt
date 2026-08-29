package io.github.hyperisland.compose.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.hyperisland.utils.RootShell
import java.io.File

internal object KeepIslandService {
    private val publicDirectory = File("/sdcard/Pictures/HyperIsland")

    fun refresh(context: Context) {
        context.sendBroadcast(
            Intent(ACTION_REFRESH_KEEP_ISLAND).setPackage(SYSTEM_UI_PACKAGE),
            SEND_ISLAND_PERMISSION,
        )
    }

    fun saveIcon(context: Context, source: Uri, oldPath: String): Result<String> = runCatching {
        val destination = File(publicDirectory, "hyperisland_keep_icon_${System.currentTimeMillis()}.png")
        val temporary = File.createTempFile("hyperisland_keep_icon_", ".png", context.cacheDir)
        try {
            context.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input) { "Cannot open selected image" }
                temporary.outputStream().use(input::copyTo)
            }
            publicDirectory.mkdirs()
            val copiedDirectly = runCatching {
                temporary.copyTo(destination, overwrite = true)
                destination.setReadable(true, false)
                true
            }.getOrDefault(false)
            if (!copiedDirectly) {
                val command = "mkdir -p ${shellQuote(publicDirectory.path)} && " +
                    "cp ${shellQuote(temporary.path)} ${shellQuote(destination.path)} && " +
                    "chmod 0644 ${shellQuote(destination.path)}"
                check(RootShell.run(command).exitCode == 0) { "Cannot save selected image" }
            }
            if (oldPath.isNotBlank() && oldPath != destination.path) deleteIcon(oldPath)
            destination.path
        } finally {
            temporary.delete()
        }
    }

    fun deleteIcon(configuredPath: String): Boolean {
        val file = File(configuredPath)
        if (!isAllowedTarget(file)) return false
        if (!file.exists()) return true
        if (file.delete()) return true
        return runCatching {
            RootShell.run("rm -f -- ${shellQuote(file.path)}").exitCode == 0
        }.getOrDefault(false)
    }

    private fun isAllowedTarget(file: File): Boolean = runCatching {
        file.name.matches(Regex("hyperisland_keep_icon_[0-9]+\\.png")) &&
            file.parentFile?.canonicalFile == publicDirectory.canonicalFile
    }.getOrDefault(false)

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private const val ACTION_REFRESH_KEEP_ISLAND = "io.github.hyperisland.action.REFRESH_KEEP_ISLAND"
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val SEND_ISLAND_PERMISSION = "io.github.hyperisland.SEND_ISLAND"
}
