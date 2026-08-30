package io.github.hyperisland.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val LocalRootBottomBarPadding = staticCompositionLocalOf { 0.dp }
internal val LocalBarBlurEnabled = staticCompositionLocalOf { false }

internal val LocalBarBlurBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

@Composable
internal fun BarBlurHost(
    enabled: Boolean,
    captureForEffects: Boolean = false,
    liquidGlassEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = if ((enabled || captureForEffects || liquidGlassEnabled) && isRuntimeShaderSupported()) {
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    } else {
        null
    }
    CompositionLocalProvider(
        LocalBarBlurEnabled provides enabled,
        LocalBarBlurBackdrop provides backdrop,
    ) {
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

@Composable
internal fun BarBackdropContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalBarBlurBackdrop.current
    Box(
        modifier = modifier.then(
            if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
        ),
    ) {
        content()
    }
}

@Composable
internal fun BlurredBar(
    topGradient: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.barBlurBackground(
            shape = RectangleShape,
            topGradient = topGradient,
        ),
    ) {
        content()
    }
}

@Composable
internal fun Modifier.barBlurBackground(
    shape: Shape,
    topGradient: Boolean = false,
): Modifier {
    val backdrop = LocalBarBlurBackdrop.current
    val blurEnabled = LocalBarBlurEnabled.current
    val surfaceColor = MiuixTheme.colorScheme.surface
    val contentColor = MiuixTheme.colorScheme.onSurface
    return if (blurEnabled && backdrop != null) {
        val blendColors = if (topGradient) {
            listOf(BlendColorEntry(color = surfaceColor.copy(alpha = TOP_BAR_SURFACE_ALPHA)))
        } else {
            listOf(
                BlendColorEntry(color = surfaceColor.copy(alpha = BOTTOM_BAR_SURFACE_ALPHA)),
                BlendColorEntry(color = contentColor.copy(alpha = BAR_GLASS_TINT_ALPHA)),
            )
        }
        val blurColors = BlurDefaults.blurColors(
            blendColors = blendColors,
        )
        if (topGradient) {
            progressiveTextureBlur(
                backdrop = backdrop,
                shape = shape,
                blurRadius = BAR_BLUR_RADIUS,
                gradient = TOP_BAR_PROGRESSIVE_BLUR,
                colors = blurColors,
            )
        } else {
            textureBlur(
                backdrop = backdrop,
                shape = shape,
                blurRadius = BAR_BLUR_RADIUS,
                colors = blurColors,
            )
        }
    } else {
        background(color = surfaceColor, shape = shape)
    }
}

private val TOP_BAR_PROGRESSIVE_BLUR = ProgressiveBlur.Top.copy(
    startFraction = 0.12f,
    endFraction = 1f,
    curve = 1.25f,
)
private const val BAR_BLUR_RADIUS = 16f
private const val TOP_BAR_SURFACE_ALPHA = 0.66f
private const val BOTTOM_BAR_SURFACE_ALPHA = 0.58f
private const val BAR_GLASS_TINT_ALPHA = 0.025f
private const val PREDICTIVE_BACK_BLUR_RADIUS = 12f
private const val PREDICTIVE_BACK_DIM_ALPHA = 0.16f
