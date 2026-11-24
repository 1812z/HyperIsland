package io.github.hyperisland.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.github.hyperisland.compose.component.liquid.rememberCombinedBackdrop
import io.github.hyperisland.compose.theme.LocalGlassEffectsEnabled
import io.github.hyperisland.compose.theme.UiGlassSettings
import io.github.hyperisland.compose.theme.isGlassEnabled
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val LocalRootBottomBarPadding = staticCompositionLocalOf { 0.dp }
private val LocalBarBlurBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

@Composable
internal fun barBackdrop(): LayerBackdrop? = LocalBarBlurBackdrop.current

@Composable
internal fun uiGlassBackdrop(): Backdrop? {
    if (!isGlassEnabled()) return null
    val content = barBackdrop() ?: return null
    val wallpaper = LocalSharedPageWallpaper.current?.backdrop ?: return content
    return rememberCombinedBackdrop(wallpaper, content)
}

@Composable
internal fun Modifier.navigationBarBlur(
    backdrop: Backdrop?,
    color: Color,
    settings: UiGlassSettings,
    shape: Shape = RectangleShape,
): Modifier {
    if (backdrop == null) return this
    return textureBlur(
        backdrop = backdrop,
        shape = shape,
        blurRadius = settings.blurRadius.toFloat(),
        noiseCoefficient = 0f,
        colors = BlurDefaults.blurColors(
            blendColors = listOf(BlendColorEntry(color.copy(alpha = settings.tintStrength))),
        ),
    )
}

@Composable
internal fun BarBlurHost(
    captureForEffects: Boolean = false,
    captureForGlass: Boolean = true,
    content: @Composable () -> Unit,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    val capture = LocalGlassEffectsEnabled.current && (captureForEffects || (captureForGlass && isGlassEnabled()))
    val sharedWallpaper = LocalSharedPageWallpaper.current?.backdrop
    val backdrop = if (capture && isRuntimeShaderSupported()) {
        rememberLayerBackdrop {
            // The shared wallpaper is sampled separately behind this page's scrolling content.
            if (sharedWallpaper == null) drawRect(surfaceColor)
            drawContent()
        }
    } else {
        null
    }
    CompositionLocalProvider(LocalBarBlurBackdrop provides backdrop) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
internal fun PredictiveBackBackdrop(
    intensity: Float,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalBarBlurBackdrop.current
    if (!visible) return
    val effectIntensity = intensity.coerceIn(0f, 1f)
    val dimColor = MiuixTheme.colorScheme.windowDimming.copy(
        alpha = PREDICTIVE_BACK_DIM_ALPHA * effectIntensity,
    )
    Box(
        modifier = if (backdrop != null) {
            modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = PREDICTIVE_BACK_BLUR_RADIUS * effectIntensity,
                noiseCoefficient = 0f,
                colors = BlurDefaults.blurColors(
                    blendColors = listOf(
                        BlendColorEntry(
                            color = dimColor,
                        ),
                    ),
                ),
            )
        } else {
            modifier.background(dimColor)
        },
    )
}

/** Captures a page and its wallpaper for the controls above it. Navigation hosts capture their child pages. */
@Composable
internal fun BarBackdropContent(
    modifier: Modifier = Modifier,
    showWallpaper: Boolean = true,
    shareWallpaperWithChildPages: Boolean = false,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalBarBlurBackdrop.current
    val capture = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
    if (showWallpaper) {
        WallpaperCardHost(
            modifier = modifier.then(capture),
            shareWithChildPages = shareWallpaperWithChildPages,
        ) { content() }
    } else {
        Box(modifier.then(capture)) { content() }
    }
}

private const val PREDICTIVE_BACK_BLUR_RADIUS = 12f
private const val PREDICTIVE_BACK_DIM_ALPHA = 0.16f
