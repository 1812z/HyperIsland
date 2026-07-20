package io.github.hyperisland.xposed.hook

import java.io.File

internal object IslandBackgroundFile {
    private const val MAX_FILE_SIZE = 128L * 1024L * 1024L
    private val allowedExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
    private val allowedRoots = listOf(
        File("/sdcard/Pictures/HyperIsland"),
        File("/storage/emulated/0/Pictures/HyperIsland"),
        File("/sdcard/Android/data/io.github.hyperisland/files"),
        File("/storage/emulated/0/Android/data/io.github.hyperisland/files"),
    )

    fun resolve(path: String): File? {
        if (path.isBlank()) return null
        return runCatching {
            val file = File(path).canonicalFile
            if (!file.isFile || !file.canRead() || file.length() !in 1..MAX_FILE_SIZE) {
                return@runCatching null
            }
            if (file.extension.lowercase() !in allowedExtensions) return@runCatching null
            val insideAllowedRoot = allowedRoots.any { root ->
                val canonicalRoot = root.canonicalFile
                file.parentFile == canonicalRoot
            }
            file.takeIf { insideAllowedRoot }
        }.getOrNull()
    }
}
