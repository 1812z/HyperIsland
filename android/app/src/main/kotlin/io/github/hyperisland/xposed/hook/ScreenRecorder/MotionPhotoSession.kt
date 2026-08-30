package io.github.hyperisland.xposed.hook.ScreenRecorder

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.system.Os
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileDescriptor
import java.io.DataOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal object MotionPhotoSession {
    private const val XMP_HEADER = "http://ns.adobe.com/xap/1.0/\u0000"
    private const val XIAOMI_CUSTOMIZE_HEADER = "XIAOMI_CUSTOMIZE"
    private const val XIAOMI_CUSTOMIZE_JSON =
        "{\"9a01\":\"1\",\"8897\":\"1\",\"version\":\"32\"}"
    private const val CONVERSION_DELAY_MILLIS = 1_000L
    private const val PUBLISH_WAIT_MILLIS = 8_000L
    private const val PUBLISH_POLL_MILLIS = 250L
    private const val MAX_RECORDING_DURATION_MILLIS = 60_000L
    private const val JPEG_QUALITY = 95

    private val outputs = Collections.synchronizedMap(WeakHashMap<Any, TrackedOutput>())
    private val converter = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "HyperIsland-MotionPhoto").apply { isDaemon = true }
    }
    private val timeoutScheduler = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "HyperIsland-MotionPhotoTimeout").apply { isDaemon = true }
    }

    @Volatile
    private var armed = false

    @Volatile
    private var applicationContext: Context? = null

    fun arm(context: Context, enabled: Boolean) {
        applicationContext = context.applicationContext
        armed = enabled
    }

    fun onMuxerCreated(
        muxer: Any,
        path: String?,
        fileDescriptor: FileDescriptor? = null,
    ): Boolean? {
        if (!armed) return null
        armed = false
        val ownedDescriptor = fileDescriptor?.let { descriptor ->
            runCatching { ParcelFileDescriptor.dup(descriptor) }.getOrNull()
        }
        val normalizedPath = (path ?: ownedDescriptor?.let { descriptor ->
            pathFrom(descriptor.fileDescriptor)
        })
            ?.removeSuffix(" (deleted)")
            ?.takeIf { it.endsWith(".mp4", ignoreCase = true) }
        if (normalizedPath == null) {
            ownedDescriptor?.close()
            return false
        }
        outputs[muxer] = TrackedOutput(normalizedPath, ownedDescriptor)
        return true
    }

    fun onMuxerStarted(muxer: Any, onTimeout: (Context) -> Unit) {
        val output = outputs[muxer] ?: return
        val context = applicationContext ?: return
        output.timeout?.cancel(false)
        output.timeout = timeoutScheduler.schedule(
            { onTimeout(context) },
            MAX_RECORDING_DURATION_MILLIS,
            TimeUnit.MILLISECONDS,
        )
    }

    fun onMuxerReleased(muxer: Any, onFinished: (Result) -> Unit) {
        val output = outputs.remove(muxer) ?: return
        output.timeout?.cancel(false)
        output.timeout = null
        val context = applicationContext ?: return
        output.descriptor?.let { descriptor ->
            pathFrom(descriptor.fileDescriptor)
                ?.removeSuffix(" (deleted)")
                ?.let { output.initialPath = it }
            descriptor.close()
            output.descriptor = null
        }
        converter.schedule(
            {
                val result = try {
                    runCatching { packageMotionPhoto(context, output) }
                        .getOrElse { Result.Failed(output.initialPath, it) }
                } finally {
                    output.descriptor?.close()
                }
                onFinished(result)
            },
            CONVERSION_DELAY_MILLIS,
            TimeUnit.MILLISECONDS,
        )
    }

    fun pathFrom(fileDescriptor: FileDescriptor): String? = runCatching {
        ParcelFileDescriptor.dup(fileDescriptor).use { duplicate ->
            Os.readlink("/proc/self/fd/${duplicate.fd}")
        }
    }.getOrNull()

    private fun packageMotionPhoto(context: Context, tracked: TrackedOutput): Result {
        val source = awaitPublishedRecording(tracked.initialPath)
        val currentPath = source.absolutePath

        val retriever = MediaMetadataRetriever()
        val cover = try {
            retriever.setDataSource(source.absolutePath)
            val durationMillis = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L
            val presentationTimestampUs = durationMillis * 500L
            val frame = retriever.getFrameAtTime(
                presentationTimestampUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            ) ?: throw IllegalStateException("unable to extract cover frame")
            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toFloatOrNull()
                ?: 0f
            CoverFrame(rotate(frame, rotation))
        } finally {
            retriever.release()
        }

        val mp4Patch = buildXiaomiMp4Patch(source)
        val videoLength = mp4Patch?.outputLength ?: source.length()
        check(videoLength > 0L) { "recording data is empty: $currentPath" }
        val jpeg = try {
            ByteArrayOutputStream().use { output ->
                check(cover.bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "unable to encode cover frame"
                }
                output.toByteArray()
            }
        } finally {
            cover.bitmap.recycle()
        }
        val jpegWithXmp = injectXmp(
            jpeg = jpeg,
            xmp = motionPhotoXmp(videoLength),
        )

        val destinationPath = publishMotionPhoto(
            context = context,
            source = source,
            jpegWithXmp = jpegWithXmp,
            mp4Patch = mp4Patch,
        )
        val sourceDeleted = removeVideoFromMediaStore(context, source.absolutePath) ||
            !source.exists()
        return Result.Success(destinationPath, sourceDeleted)
    }

    private fun publishMotionPhoto(
        context: Context,
        source: File,
        jpegWithXmp: ByteArray,
        mp4Patch: Mp4Patch?,
    ): String {
        val destination = uniqueDestination(source)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, destination.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, relativeDirectory(source))
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY,
        )
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("unable to create motion photo MediaStore item")
        try {
            resolver.openOutputStream(uri, "w")?.use { destinationStream ->
                destinationStream.write(jpegWithXmp)
                writeMotionPhotoVideo(source, destinationStream, mp4Patch)
                destinationStream.flush()
            } ?: throw IllegalStateException("unable to open motion photo MediaStore item")
            check(
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null,
                ) > 0,
            ) { "unable to publish motion photo MediaStore item" }
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
        return destination.absolutePath
    }

    private fun relativeDirectory(source: File): String {
        val parentPath = source.parentFile?.absolutePath.orEmpty().replace('\\', '/')
        val roots = listOf(
            "/storage/emulated/0/",
            "/mnt/user/0/emulated/0/",
            "/mnt/user/0/primary/",
        )
        roots.firstOrNull(parentPath::startsWith)?.let { root ->
            return parentPath.removePrefix(root).trim('/') + "/"
        }
        return "${Environment.DIRECTORY_DCIM}/ScreenRecorder/"
    }

    private fun buildXiaomiMp4Patch(source: File): Mp4Patch? = runCatching {
        RandomAccessFile(source, "r").use { input ->
            val fileLength = input.length()
            val topLevel = readMp4Boxes(input, 0L, fileLength)
            val moov = topLevel.firstOrNull { it.type == "moov" } ?: return@use null
            val mdat = topLevel.firstOrNull { it.type == "mdat" } ?: return@use null
            if (
                moov.headerSize != 8L ||
                moov.end != fileLength ||
                moov.offset <= mdat.offset
            ) {
                return@use null
            }
            val meta = readMp4Boxes(input, moov.offset + moov.headerSize, moov.end)
                .firstOrNull { it.type == "meta" }
            if (meta != null && meta.headerSize != 8L) return@use null
            val replacement = xiaomiMp4Metadata()
            val replacedSize = meta?.size ?: 0L
            val newMoovSize = moov.size - replacedSize + replacement.size
            if (newMoovSize > 0xFFFF_FFFFL) return@use null
            Mp4Patch(
                moov = moov,
                replacedMeta = meta,
                replacementMeta = replacement,
                newMoovSize = newMoovSize,
                outputLength = fileLength - replacedSize + replacement.size,
            )
        }
    }.getOrNull()

    private fun writeMotionPhotoVideo(
        source: File,
        destination: OutputStream,
        patch: Mp4Patch?,
    ) {
        RandomAccessFile(source, "r").use { input ->
            if (patch == null) {
                copyRange(input, destination, 0L, input.length())
                return
            }
            val meta = patch.replacedMeta
            val insertionOffset = meta?.offset ?: patch.moov.end
            val afterMeta = meta?.end ?: insertionOffset
            copyRange(input, destination, 0L, patch.moov.offset)
            writeUInt32(destination, patch.newMoovSize)
            destination.write("moov".toByteArray(StandardCharsets.US_ASCII))
            copyRange(
                input,
                destination,
                patch.moov.offset + patch.moov.headerSize,
                insertionOffset,
            )
            destination.write(patch.replacementMeta)
            copyRange(input, destination, afterMeta, input.length())
        }
    }

    private fun readMp4Boxes(
        input: RandomAccessFile,
        start: Long,
        end: Long,
    ): List<Mp4Box> {
        val boxes = mutableListOf<Mp4Box>()
        var offset = start
        while (offset + 8L <= end) {
            input.seek(offset)
            val size32 = input.readInt().toLong() and 0xFFFF_FFFFL
            val typeBytes = ByteArray(4).also { input.readFully(it) }
            val type = String(typeBytes, StandardCharsets.US_ASCII)
            val headerSize: Long
            val size = when (size32) {
                0L -> {
                    headerSize = 8L
                    end - offset
                }
                1L -> {
                    headerSize = 16L
                    input.readLong()
                }
                else -> {
                    headerSize = 8L
                    size32
                }
            }
            if (size < headerSize || offset + size > end) break
            boxes += Mp4Box(offset, size, headerSize, type)
            offset += size
        }
        return boxes
    }

    private fun xiaomiMp4Metadata(): ByteArray {
        val handler = mp4Box(
            "hdlr",
            byteArrayOf(0, 0, 0, 0) +
                byteArrayOf(0, 0, 0, 0) +
                "mdta".toByteArray(StandardCharsets.US_ASCII) +
                ByteArray(12) +
                byteArrayOf(0),
        )
        val androidVersionKey = mp4Key("com.android.version")
        val videoTypeKey = mp4Key("com.video.file.type")
        val keys = mp4Box(
            "keys",
            byteArrayOf(0, 0, 0, 0) +
                uint32Bytes(2L) +
                androidVersionKey +
                videoTypeKey,
        )
        val androidVersionData = mp4Box(
            "data",
            uint32Bytes(1L) +
                uint32Bytes(0L) +
                Build.VERSION.RELEASE.toByteArray(StandardCharsets.US_ASCII),
        )
        val videoTypeData = mp4Box(
            "data",
            uint32Bytes(67L) +
                uint32Bytes(0L) +
                uint32Bytes(3L),
        )
        val item1 = mp4Box(byteArrayOf(0, 0, 0, 1), androidVersionData)
        val item2 = mp4Box(byteArrayOf(0, 0, 0, 2), videoTypeData)
        val itemList = mp4Box("ilst", item1 + item2)
        return mp4Box("meta", handler + keys + itemList)
    }

    private fun mp4Key(value: String): ByteArray {
        val payload = "mdta".toByteArray(StandardCharsets.US_ASCII) +
            value.toByteArray(StandardCharsets.US_ASCII)
        return uint32Bytes(payload.size.toLong() + 4L) + payload
    }

    private fun mp4Box(type: String, payload: ByteArray): ByteArray =
        mp4Box(type.toByteArray(StandardCharsets.US_ASCII), payload)

    private fun mp4Box(type: ByteArray, payload: ByteArray): ByteArray {
        check(type.size == 4)
        return uint32Bytes(payload.size.toLong() + 8L) + type + payload
    }

    private fun uint32Bytes(value: Long): ByteArray = ByteArrayOutputStream(4).use { output ->
        DataOutputStream(output).use { it.writeInt(value.toInt()) }
        output.toByteArray()
    }

    private fun writeUInt32(output: OutputStream, value: Long) {
        output.write(uint32Bytes(value))
    }

    private fun copyRange(
        input: RandomAccessFile,
        output: OutputStream,
        start: Long,
        end: Long,
    ) {
        input.seek(start)
        var remaining = end - start
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (remaining > 0L) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw IllegalStateException("unexpected end of MP4")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun awaitPublishedRecording(initialPath: String): File {
        val candidates = recordingPathCandidates(initialPath)
        val deadline = android.os.SystemClock.uptimeMillis() + PUBLISH_WAIT_MILLIS
        var lastFailure: Throwable? = null
        do {
            candidates.forEach { candidate ->
                if (candidate.isFile && candidate.length() > 0L && candidate.canRead()) {
                    val probe = MediaMetadataRetriever()
                    val usable = try {
                        runCatching {
                            probe.setDataSource(candidate.absolutePath)
                            probe.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            true
                        }.getOrElse {
                            lastFailure = it
                            false
                        }
                    } finally {
                        probe.release()
                    }
                    if (usable) return candidate
                }
            }
            Thread.sleep(PUBLISH_POLL_MILLIS)
        } while (android.os.SystemClock.uptimeMillis() < deadline)
        throw IllegalStateException(
            "recording was not published: ${candidates.joinToString { it.absolutePath }}" +
                (lastFailure?.message?.let { "; $it" } ?: ""),
            lastFailure,
        )
    }

    private fun recordingPathCandidates(initialPath: String): List<File> {
        val initial = File(initialPath.removeSuffix(" (deleted)"))
        val pendingMatch = PENDING_FILE_PATTERN.matchEntire(initial.name)
        val published = pendingMatch
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf(String::isNotBlank)
            ?.let { File(initial.parentFile, it) }
        val paths = linkedSetOf<String>()
        if (published != null) paths += published.absolutePath
        paths += initial.absolutePath
        (paths.toList()).forEach { path ->
            when {
                path.startsWith("/mnt/user/0/emulated/0/") ->
                    paths += path.replaceFirst(
                        "/mnt/user/0/emulated/0/",
                        "/storage/emulated/0/",
                    )
                path.startsWith("/mnt/user/0/primary/") ->
                    paths += path.replaceFirst(
                        "/mnt/user/0/primary/",
                        "/storage/emulated/0/",
                    )
            }
        }
        return paths.map(::File)
    }

    private fun rotate(source: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return source
        val rotated = Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            Matrix().apply { postRotate(degrees) },
            true,
        )
        if (rotated !== source) source.recycle()
        return rotated
    }

    private fun injectXmp(jpeg: ByteArray, xmp: String): ByteArray {
        check(jpeg.size >= 2 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte()) {
            "invalid JPEG cover"
        }
        val xmpPayload = XMP_HEADER.toByteArray(StandardCharsets.UTF_8) +
            xmp.toByteArray(StandardCharsets.UTF_8)
        val xiaomiPayload = XIAOMI_CUSTOMIZE_HEADER.toByteArray(StandardCharsets.US_ASCII) +
            byteArrayOf(0, 1, 1) +
            XIAOMI_CUSTOMIZE_JSON.toByteArray(StandardCharsets.US_ASCII)
        val insertionOffset = jpegMetadataEnd(jpeg)
        return ByteArrayOutputStream(
            jpeg.size + xmpPayload.size + xiaomiPayload.size + 8,
        ).use { output ->
            output.write(jpeg, 0, insertionOffset)
            writeJpegSegment(output, 0xE4, xiaomiPayload)
            writeJpegSegment(output, 0xE1, xmpPayload)
            output.write(jpeg, insertionOffset, jpeg.size - insertionOffset)
            output.toByteArray()
        }
    }

    private fun jpegMetadataEnd(jpeg: ByteArray): Int {
        var offset = 2
        while (offset + 4 <= jpeg.size && jpeg[offset] == 0xFF.toByte()) {
            val marker = jpeg[offset + 1].toInt() and 0xFF
            if (marker !in 0xE0..0xEF) break
            val length = ((jpeg[offset + 2].toInt() and 0xFF) shl 8) or
                (jpeg[offset + 3].toInt() and 0xFF)
            if (length < 2 || offset + 2 + length > jpeg.size) break
            offset += 2 + length
        }
        return offset
    }

    private fun writeJpegSegment(
        output: ByteArrayOutputStream,
        marker: Int,
        payload: ByteArray,
    ) {
        val segmentLength = payload.size + 2
        check(segmentLength <= 0xFFFF) { "JPEG metadata is too large" }
        output.write(0xFF)
        output.write(marker)
        output.write(segmentLength ushr 8)
        output.write(segmentLength and 0xFF)
        output.write(payload)
    }

    private fun motionPhotoXmp(videoLength: Long): String =
        """<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
      xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
      xmlns:Container="http://ns.google.com/photos/1.0/container/"
      xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
      GCamera:MotionPhoto="1"
      GCamera:MotionPhotoVersion="1"
      GCamera:MotionPhotoPresentationTimestampUs="0">
      <Container:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <Container:Item Item:Mime="image/jpeg" Item:Semantic="Primary"/>
          </rdf:li>
          <rdf:li rdf:parseType="Resource">
            <Container:Item Item:Mime="video/mp4" Item:Semantic="MotionPhoto" Item:Length="$videoLength" Item:Padding="0"/>
          </rdf:li>
        </rdf:Seq>
      </Container:Directory>
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>
"""

    private fun uniqueDestination(source: File): File {
        val baseName = motionPhotoBaseName(source)
        val direct = File(source.parentFile, "$baseName.jpg")
        if (!direct.exists()) return direct
        var suffix = 1
        while (true) {
            val candidate = File(source.parentFile, "${baseName}_$suffix.jpg")
            if (!candidate.exists()) return candidate
            suffix++
        }
    }

    private fun motionPhotoBaseName(source: File): String {
        val match = SCREEN_RECORDER_FILE_PATTERN.find(source.name)
        if (match != null) {
            val (year, month, day, hour, minute, second) = match.destructured
            return "MVIMG_${year}${month}${day}_${hour}${minute}${second}"
        }
        return "MVIMG_${
            java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.ROOT)
                .format(java.util.Date())
        }"
    }

    private fun removeVideoFromMediaStore(context: Context, path: String): Boolean {
        return recordingPathCandidates(path).any { candidate ->
            runCatching {
                context.contentResolver.delete(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.DATA}=?",
                    arrayOf(candidate.absolutePath),
                ) > 0
            }.getOrDefault(false)
        }
    }

    private data class CoverFrame(
        val bitmap: Bitmap,
    )

    private data class TrackedOutput(
        var initialPath: String,
        var descriptor: ParcelFileDescriptor?,
        var timeout: ScheduledFuture<*>? = null,
    )

    private data class Mp4Box(
        val offset: Long,
        val size: Long,
        val headerSize: Long,
        val type: String,
    ) {
        val end: Long
            get() = offset + size
    }

    private data class Mp4Patch(
        val moov: Mp4Box,
        val replacedMeta: Mp4Box?,
        val replacementMeta: ByteArray,
        val newMoovSize: Long,
        val outputLength: Long,
    )

    private val PENDING_FILE_PATTERN = Regex("^\\.pending-\\d+-\\.?(.*)$")
    private val SCREEN_RECORDER_FILE_PATTERN = Regex(
        "Screenrecorder-(\\d{4})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})-(\\d{2})",
        RegexOption.IGNORE_CASE,
    )

    sealed interface Result {
        data class Success(
            val path: String,
            val sourceDeleted: Boolean,
        ) : Result

        data class Failed(
            val sourcePath: String,
            val error: Throwable,
        ) : Result
    }
}
