package io.github.hyperisland.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import io.github.hyperisland.compose.theme.AppWallpaperImage
import io.github.hyperisland.compose.theme.LocalAppWallpaper
import io.github.hyperisland.compose.theme.LocalCardGlassSettings
import io.github.hyperisland.compose.theme.uiGlassMaterial
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.GlassMaterials
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

internal val LocalWallpaperCardBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

@Composable
internal fun WallpaperCardHost(
    modifier: Modifier = Modifier,
    wallpaperModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val wallpaper = LocalAppWallpaper.current
    val surface = MiuixTheme.colorScheme.surface
    val hasWallpaper = wallpaper?.image != null
    val backdrop = if (hasWallpaper && LocalCardGlassSettings.current.enabled && isRuntimeShaderSupported()) {
        rememberLayerBackdrop {
            drawRect(surface)
            drawContent()
        }
    } else {
        null
    }
    Box(modifier = modifier) {
        if (wallpaper?.image != null) {
            val capture = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier
            Box(Modifier.matchParentSize().then(wallpaperModifier).then(capture)) {
                AppWallpaperImage(wallpaper, Modifier.matchParentSize())
            }
        }
        CompositionLocalProvider(LocalWallpaperCardBackdrop provides backdrop) {
            content()
        }
    }
}

@Composable
internal fun AppCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CardDefaults.CornerRadius,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    colors: CardColors = CardDefaults.defaultColors(),
    pressFeedbackType: PressFeedbackType = PressFeedbackType.None,
    showIndication: Boolean = false,
    holdDownState: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme
    val color = colors.color
    val isPageSurface = color == colorScheme.surfaceContainer || color == colorScheme.surface ||
        color == colorScheme.background || color == colorScheme.secondaryVariant
    val backdrop = LocalWallpaperCardBackdrop.current
    val glass = LocalCardGlassSettings.current
    if (backdrop == null || !isPageSurface) {
        Card(
            modifier = modifier,
            cornerRadius = cornerRadius,
            insideMargin = insideMargin,
            colors = colors,
            pressFeedbackType = pressFeedbackType,
            showIndication = showIndication,
            holdDownState = holdDownState,
            onClick = onClick,
            onLongPress = onLongPress,
            content = content,
        )
        return
    }

    val isDark = colorScheme.background.luminance() < 0.5f
    val shape = remember(cornerRadius) { GlassShape(cornerRadius) }
    val tint = color.copy(alpha = 0.3f * glass.tintStrength)
    val panel = Modifier.glassPanel(
        backdrop = backdrop,
        shape = shape,
        material = uiGlassMaterial(GlassMaterials.puredThinGlass(isDark), glass),
        stroke = GlassEdgeStrokes.forTheme(isDark),
        shadow = null,
        shading = false,
    )
    if (pressFeedbackType != PressFeedbackType.None) {
        Card(
            modifier = modifier,
            cornerRadius = cornerRadius,
            insideMargin = CardDefaults.InsideMargin,
            colors = colors.copy(color = Color.Transparent),
            pressFeedbackType = pressFeedbackType,
            showIndication = showIndication,
            holdDownState = holdDownState,
            onClick = onClick,
            onLongPress = onLongPress,
        ) {
            // Keep the single glass surface inside Miuix's press transform.
            Column(
                modifier = Modifier.graphicsLayer().then(panel).background(tint).padding(insideMargin),
                content = content,
            )
        }
        return
    }

    Box(modifier = modifier, propagateMinConstraints = true) {
        // Let Miuix invalidate the glass layer without relying on the card's press or content layer.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer()
                .then(panel),
        )
        Card(
            cornerRadius = cornerRadius,
            insideMargin = insideMargin,
            colors = colors.copy(color = tint),
            pressFeedbackType = pressFeedbackType,
            showIndication = showIndication,
            holdDownState = holdDownState,
            onClick = onClick,
            onLongPress = onLongPress,
            content = content,
        )
    }
}
