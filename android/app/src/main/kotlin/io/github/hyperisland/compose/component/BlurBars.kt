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
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val LocalRootBottomBarPadding = staticCompositionLocalOf { 0.dp }

private val LocalBarBlurBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

@Composable
internal fun BarBlurHost(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    val backdrop = if (enabled && isRuntimeShaderSupported()) {
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    } else {
        null
    }
    CompositionLocalProvider(LocalBarBlurBackdrop provides backdrop) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            content()
        }
    }
}

@Composable
internal fun BlurredBar(content: @Composable () -> Unit) {
    Box(modifier = Modifier.barBlurBackground(RectangleShape)) {
        content()
    }
}

@Composable
internal fun Modifier.barBlurBackground(shape: Shape): Modifier {
    val backdrop = LocalBarBlurBackdrop.current
    val surfaceColor = MiuixTheme.colorScheme.surface
    return if (backdrop != null) {
        textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = 25f,
            colors = BlurDefaults.blurColors(
                blendColors = listOf(
                    BlendColorEntry(color = surfaceColor.copy(alpha = 0.8f)),
                ),
            ),
        )
    } else {
        background(color = surfaceColor, shape = shape)
    }
}
