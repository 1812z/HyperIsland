// Adapted from the compose-miuix-ui IosLiquidGlassNavigationBar example (Apache-2.0).
package io.github.hyperisland.compose.component.liquid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop

@Stable
private class CombinedBackdrop(
    private val first: Backdrop,
    private val remaining: List<Backdrop>,
) : Backdrop {
    override val isCoordinatesDependent = first.isCoordinatesDependent || remaining.any(Backdrop::isCoordinatesDependent)
    override val offsetResidualX: Float get() = first.offsetResidualX
    override val offsetResidualY: Float get() = first.offsetResidualY

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Float,
    ) {
        with(first) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        remaining.forEach { layer ->
            with(layer) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        }
    }
}

@Composable
internal fun rememberCombinedBackdrop(first: Backdrop, second: Backdrop): Backdrop =
    remember(first, second) { CombinedBackdrop(first, listOf(second)) }

@Composable
internal fun rememberCombinedBackdrop(first: Backdrop, remaining: List<Backdrop>): Backdrop =
    remember(first, remaining) { CombinedBackdrop(first, remaining) }
