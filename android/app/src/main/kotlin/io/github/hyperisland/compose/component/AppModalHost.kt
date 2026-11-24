package io.github.hyperisland.compose.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import io.github.hyperisland.compose.component.liquid.rememberCombinedBackdrop
import io.github.hyperisland.compose.theme.isGlassEnabled
import io.github.hyperisland.compose.theme.uiGlassMaterial
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassBottomSheetDefaults
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogDefaults
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

private class ModalLayer(val backdrop: LayerBackdrop?)

private class ModalHostState(val backdrop: LayerBackdrop?) {
    val layers = mutableStateListOf<ModalLayer>()
}

private val LocalModalHost = staticCompositionLocalOf<ModalHostState?> { null }
internal val LocalAppModalIsTopmost = compositionLocalOf { true }

/** Records the scene and bars below the root Scaffold's popup host. */
@Composable
internal fun AppModalHost(content: @Composable () -> Unit) {
    val surface = MiuixTheme.colorScheme.surface
    val glassEnabled = isGlassEnabled()
    val backdrop = if (isRuntimeShaderSupported()) {
        rememberLayerBackdrop {
            drawRect(surface)
            drawContent()
        }
    } else {
        null
    }
    val host = remember(backdrop) { ModalHostState(backdrop) }

    CompositionLocalProvider(LocalModalHost provides host) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            val capture = if (glassEnabled && backdrop != null && host.layers.isNotEmpty()) {
                Modifier.layerBackdrop(backdrop)
            } else {
                Modifier
            }
            Box(modifier = Modifier.fillMaxSize().then(capture)) {
                content()
            }
        }
    }
}

@Composable
internal fun AppDialog(
    show: Boolean,
    title: String? = null,
    summary: String? = null,
    insideMargin: DpSize = DialogDefaults.insideMargin,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val glassEnabled = isGlassEnabled()
    AppModalSurface(show, onDismissFinished) { backdrop, capture, _, onHidden ->
        GlassDialog(
            show = show,
            backdrop = backdrop,
            modifier = capture,
            title = title,
            summary = summary,
            insideMargin = insideMargin,
            material = uiGlassMaterial(GlassDialogDefaults.material()),
            stroke = if (backdrop != null) GlassDialogDefaults.stroke() else null,
            onDismissRequest = { onDismissRequest?.invoke() },
            onDismissFinished = onHidden,
        ) {
            ModalControlScope(glassEnabled, content)
        }
    }
}

@Composable
internal fun AppBottomSheet(
    show: Boolean,
    title: String? = null,
    startAction: (@Composable () -> Unit)? = null,
    endAction: (@Composable () -> Unit)? = null,
    backgroundColor: Color = GlassBottomSheetDefaults.containerColor(),
    insideMargin: DpSize = BottomSheetDefaults.insideMargin,
    onDismissRequest: () -> Unit,
    onDismissFinished: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val glassEnabled = isGlassEnabled()
    val scopedStartAction: (@Composable () -> Unit)? = if (startAction != null) {
        { ModalControlScope(glassEnabled, startAction) }
    } else {
        null
    }
    val scopedEndAction: (@Composable () -> Unit)? = if (endAction != null) {
        { ModalControlScope(glassEnabled, endAction) }
    } else {
        null
    }
    AppModalSurface(show, onDismissFinished) { backdrop, capture, isTopmost, onHidden ->
        GlassBottomSheet(
            show = show,
            backdrop = backdrop,
            modifier = capture,
            title = title,
            startAction = scopedStartAction,
            endAction = scopedEndAction,
            containerColor = backgroundColor,
            material = uiGlassMaterial(GlassBottomSheetDefaults.material()),
            stroke = if (backdrop != null) GlassBottomSheetDefaults.stroke() else null,
            insideMargin = insideMargin,
            onDismissRequest = onDismissRequest,
            onDismissFinished = onHidden,
        ) {
            ModalControlScope(glassEnabled) {
                CompositionLocalProvider(LocalAppModalIsTopmost provides isTopmost, content = content)
            }
        }
    }
}

// Miuix invokes these slots from its root popup host, outside the caller's composition locals.
@Composable
private fun ModalControlScope(glassEnabled: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalWallpaperCardBackdrop provides null,
        LocalModalGlassEnabled provides glassEnabled,
        content = content,
    )
}

@Composable
private fun AppModalSurface(
    show: Boolean,
    onDismissFinished: (() -> Unit)?,
    content: @Composable (Backdrop?, Modifier, Boolean, () -> Unit) -> Unit,
) {
    var mounted by remember { mutableStateOf(false) }
    if (!show && !mounted) return
    if (show && !mounted) SideEffect { mounted = true }

    val host = LocalModalHost.current
    val panelBackdrop = if (host?.backdrop != null) rememberLayerBackdrop() else null
    val layer = remember(panelBackdrop) { ModalLayer(panelBackdrop) }
    DisposableEffect(host, layer) {
        host?.layers?.add(layer)
        onDispose { host?.layers?.remove(layer) }
    }

    val layers = host?.layers.orEmpty()
    val index = layers.indexOf(layer)
    val isTopmost = index < 0 || index == layers.lastIndex
    val glassEnabled = isGlassEnabled()
    val scene = host?.backdrop?.takeIf { glassEnabled }
    val backdrop = if (scene != null) {
        // A panel can sample the scene and the panels below it in display order.
        val below = layers.take(if (index < 0) layers.size else index).mapNotNull(ModalLayer::backdrop)
        if (below.isEmpty()) scene else rememberCombinedBackdrop(scene, below)
    } else {
        null
    }
    val capture = if (scene != null && panelBackdrop != null && !isTopmost) {
        Modifier.layerBackdrop(panelBackdrop)
    } else {
        Modifier
    }

    content(backdrop, capture, isTopmost) {
        if (!show) mounted = false
        onDismissFinished?.invoke()
    }
}
