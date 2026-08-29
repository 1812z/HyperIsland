package io.github.hyperisland.compose.service

import android.content.Context
import android.net.Uri
import io.github.hyperisland.utils.RootShell
import java.io.File

internal enum class IslandBackgroundType(
    val preferenceKey: String,
    val fileBaseName: String,
) {
    Small("pref_island_bg_small_path", "hyperisland_bg_small"),
    Big("pref_island_bg_big_path", "hyperisland_bg_big"),
    Expand("pref_island_bg_expand_path", "hyperisland_bg_expand"),
}

internal object IslandBackgroundService {
    private val publicDirectory = File("/sdcard/Pictures/HyperIsland")

    fun save(context: Context, source: Uri, type: IslandBackgroundType): Result<String> = runCatching {
        val extension = if (context.contentResolver.getType(source) == "image/gif") "gif" else "png"
        val destination = File(publicDirectory, "${type.fileBaseName}.$extension")
        val temporary = File.createTempFile(type.fileBaseName, ".$extension", context.cacheDir)
        try {
            context.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input) { "Cannot open selected image" }
                temporary.outputStream().use(input::copyTo)
            }
            publicDirectory.mkdirs()
            val copiedDirectly = runCatching {
                temporary.copyTo(destination, overwrite = true)
                destination.setReadable(true, false)
                removeSiblingFiles(type, destination)
                true
            }.getOrDefault(false)
            if (!copiedDirectly) {
                val staging = File(publicDirectory, ".${type.fileBaseName}.$extension.tmp")
                val command = "mkdir -p ${shellQuote(publicDirectory.path)} && " +
                    "cp ${shellQuote(temporary.path)} ${shellQuote(staging.path)} && " +
                    "chmod 0644 ${shellQuote(staging.path)} && " +
                    "mv -f ${shellQuote(staging.path)} ${shellQuote(destination.path)} && " +
                    "rm -f ${shellQuote(siblingFile(type, destination).path)}"
                check(RootShell.run(command).exitCode == 0) { "Cannot save selected image" }
            }
            destination.path
        } finally {
            temporary.delete()
        }
    }

    fun delete(type: IslandBackgroundType, configuredPath: String): Boolean {
        val targets = buildSet {
            add(File(publicDirectory, "${type.fileBaseName}.png"))
            add(File(publicDirectory, "${type.fileBaseName}.gif"))
            configuredPath.takeIf(String::isNotBlank)?.let { add(File(it)) }
        }.filter(::isAllowedTarget)
        var success = true
        targets.forEach { file ->
            if (file.exists() && !file.delete()) {
                success = runCatching {
                    RootShell.run("rm -f -- ${shellQuote(file.path)}").exitCode == 0
                }.getOrDefault(false) && success
            }
        }
        return success
    }

    private fun removeSiblingFiles(type: IslandBackgroundType, destination: File) {
        siblingFile(type, destination).delete()
    }

    private fun siblingFile(type: IslandBackgroundType, destination: File): File = File(
        publicDirectory,
        "${type.fileBaseName}.${if (destination.extension == "gif") "png" else "gif"}",
    )

    private fun isAllowedTarget(file: File): Boolean = runCatching {
        file.name.matches(Regex("hyperisland_bg_(small|big|expand)\\.(png|gif)")) &&
            file.parentFile?.canonicalFile == publicDirectory.canonicalFile
    }.getOrDefault(false)

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
}
