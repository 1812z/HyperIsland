package io.github.hyperisland.compose.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.AtomicFile
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberLongPreference
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal const val PREF_WALLPAPER_BLUR_RADIUS = "pref_wallpaper_blur_radius"
internal const val PREF_WALLPAPER_BRIGHTNESS = "pref_wallpaper_brightness"
internal const val PREF_WALLPAPER_DARK_BRIGHTNESS = "pref_wallpaper_dark_brightness"
internal const val MAX_WALLPAPER_BLUR_RADIUS = 60L
internal const val MAX_WALLPAPER_BRIGHTNESS = 100L
internal const val MAX_WALLPAPER_DARK_BRIGHTNESS = 100L

/** 深色亮度默认 70%：亮色壁纸直接铺在深色界面上会顶掉深色观感，也压不住上层半透明卡片的对比度。 */
internal const val DEFAULT_WALLPAPER_DARK_BRIGHTNESS = 70L

private const val DEFAULT_WALLPAPER_BLUR_RADIUS = 0L
private const val DEFAULT_WALLPAPER_BRIGHTNESS = 100L
private const val MAX_WALLPAPER_IMAGE_EDGE = 2560

internal val LocalAppWallpaper = staticCompositionLocalOf<AppWallpaperState?> { null }

@Stable
internal class AppWallpaperState(
    private val context: Context,
    private val scope: CoroutineScope,
    blurRadius: MutableState<Long>,
    brightness: MutableState<Long>,
    darkBrightness: MutableState<Long>,
) {
    var blurRadius by blurRadius
    var brightness by brightness

    /** 深色模式下在「壁纸亮度」基础上再乘一次的百分比，0~[MAX_WALLPAPER_DARK_BRIGHTNESS]。 */
    var darkBrightness by darkBrightness
    var image by mutableStateOf<ImageBitmap?>(null)
        private set
    var busy by mutableStateOf(true)
        private set

    private val file = AtomicFile(File(context.filesDir, "app_wallpaper.webp"))

    suspend fun load() {
        image = withContext(Dispatchers.IO) {
            try {
                file.openRead().use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            } catch (_: IOException) {
                null
            }
        }
        busy = false
    }

    fun replace(uri: Uri?, onFailure: () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        if (uri == null) {
                            file.delete()
                            check(!file.baseFile.exists())
                            null
                        } else {
                            save(uri).asImageBitmap()
                        }
                    }
                }
                result.onSuccess { image = it }.onFailure { onFailure() }
            } finally {
                busy = false
            }
        }
    }

    private fun save(uri: Uri): Bitmap {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            val longestEdge = maxOf(width, height)
            if (longestEdge > MAX_WALLPAPER_IMAGE_EDGE) {
                val scale = MAX_WALLPAPER_IMAGE_EDGE.toDouble() / longestEdge
                decoder.setTargetSize(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
        val output = file.startWrite()
        try {
            check(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 92, output))
            file.finishWrite(output)
        } catch (failure: Throwable) {
            file.failWrite(output)
            throw failure
        }
        return bitmap
    }
}

@Composable
internal fun rememberAppWallpaper(prefs: FlutterPrefsRepository): AppWallpaperState {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val blurRadius = rememberLongPreference(prefs, PREF_WALLPAPER_BLUR_RADIUS, DEFAULT_WALLPAPER_BLUR_RADIUS)
    val brightness = rememberLongPreference(prefs, PREF_WALLPAPER_BRIGHTNESS, DEFAULT_WALLPAPER_BRIGHTNESS)
    val darkBrightness =
        rememberLongPreference(prefs, PREF_WALLPAPER_DARK_BRIGHTNESS, DEFAULT_WALLPAPER_DARK_BRIGHTNESS)
    val wallpaper = remember(context, scope, blurRadius, brightness, darkBrightness) {
        AppWallpaperState(context, scope, blurRadius, brightness, darkBrightness)
    }
    LaunchedEffect(wallpaper) { wallpaper.load() }
    return wallpaper
}

@Composable
internal fun AppWallpaperBackground(modifier: Modifier) {
    LocalAppWallpaper.current?.let { AppWallpaperImage(it, modifier) }
}

@Composable
internal fun AppWallpaperImage(
    wallpaper: AppWallpaperState,
    modifier: Modifier = Modifier,
) {
    val image = wallpaper.image ?: return
    // 深色主题（含强制深色、Monet 深色）下再乘一次「深色亮度」，判定用实际 background 亮度而不是
    // isSystemInDarkTheme，否则「主题强制深色 + 系统浅色」时不会压暗（与 AppCard 的 isDark 同口径）。
    val darkTheme = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val brightness = (
        wallpaper.brightness.coerceIn(0, MAX_WALLPAPER_BRIGHTNESS).toFloat() /
            MAX_WALLPAPER_BRIGHTNESS.toFloat() *
            if (darkTheme) {
                wallpaper.darkBrightness.coerceIn(0, MAX_WALLPAPER_DARK_BRIGHTNESS).toFloat() /
                    MAX_WALLPAPER_DARK_BRIGHTNESS.toFloat()
            } else {
                1f
            }
        ).coerceIn(0f, 1f)
    val colorFilter = remember(brightness) {
        if (brightness == 1f) {
            null
        } else {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(brightness, brightness, brightness, 1f) })
        }
    }
    val blurRadius = wallpaper.blurRadius.coerceIn(0, MAX_WALLPAPER_BLUR_RADIUS).toInt().dp
    Image(
        bitmap = image,
        contentDescription = null,
        modifier = modifier.blur(blurRadius, BlurredEdgeTreatment.Rectangle),
        contentScale = ContentScale.Crop,
        colorFilter = colorFilter,
    )
}
