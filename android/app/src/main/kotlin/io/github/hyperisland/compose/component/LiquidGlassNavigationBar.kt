// Adapted from SukiSU-Ultra's FloatingBottomBar and the compose-miuix-ui
// IosLiquidGlassNavigationBar example. Licensed under Apache-2.0.
package io.github.hyperisland.compose.component

import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import io.github.hyperisland.compose.component.liquid.DampedDragAnimation
import io.github.hyperisland.compose.component.liquid.lens
import io.github.hyperisland.compose.component.liquid.rememberCombinedBackdrop
import io.github.hyperisland.compose.component.liquid.vibrancy
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.material.EdgeLightSource
import top.yukonga.miuix.kmp.material.EdgePeak
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.material.drawEdgeStroke
import top.yukonga.miuix.kmp.material.sensor.rememberDeviceTilt
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
internal fun LiquidGlassNavigationBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    items: List<NavigationItem>,
    backdrop: Backdrop?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val barModifier = modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
    if (backdrop == null) {
        Box(modifier = barModifier, contentAlignment = Alignment.Center) {
            FloatingNavigationBar(defaultWindowInsetsPadding = false) {
                items.forEachIndexed { index, item ->
                    FloatingNavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { onTabSelected(index) },
                        icon = item.icon,
                        label = item.label,
                        enabled = enabled,
                    )
                }
            }
        }
        return
    }

    Box(
        modifier = barModifier,
        contentAlignment = Alignment.Center,
    ) {
        FloatingLiquidBar(
            selectedIndex = selectedIndex,
            onSelected = onTabSelected,
            items = items,
            backdrop = backdrop,
            enabled = enabled,
        )
    }
}

private val LocalLiquidTabScale = staticCompositionLocalOf { { 1f } }

private val IndicatorSpecular = EdgeStroke(
    width = 1.dp,
    bevel = 2.dp,
    color = Color.White.copy(alpha = 0.12f),
    primary = EdgeLightSource(x = 0.5f, y = -0.3f, z = -0.05f),
    secondary = EdgeLightSource(x = 0.5f, y = 0.8f, z = -0.5f, intensity = 0.4f),
    peak = EdgePeak.Dual,
)

@Composable
private fun rememberGravityStroke(base: EdgeStroke, extraDegrees: Float): EdgeStroke {
    val tilt by rememberDeviceTilt()
    return remember(tilt, base, extraDegrees) {
        val magnitudeSquared = tilt.gravityX * tilt.gravityX + tilt.gravityY * tilt.gravityY
        val (x, y) = if (magnitudeSquared > 0.01f) {
            val inverseMagnitude = 1f / sqrt(magnitudeSquared)
            tilt.gravityX * inverseMagnitude to tilt.gravityY * inverseMagnitude
        } else {
            0f to -1f
        }
        val radians = extraDegrees * PI / 180.0
        val rotatedX = cos(radians).toFloat() * x - sin(radians).toFloat() * y
        val rotatedY = sin(radians).toFloat() * x + cos(radians).toFloat() * y
        base.copy(primary = base.primary.copy(x = 0.5f + rotatedX, y = 0.7f + rotatedY))
    }
}

@Composable
private fun RowScope.FloatingLiquidBarItem(
    item: NavigationItem,
    isSelected: Boolean,
    onActivate: (() -> Unit)?,
) {
    val scale = LocalLiquidTabScale.current
    val interaction = if (onActivate != null) {
        Modifier
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                selected = isSelected
                onClick {
                    onActivate()
                    true
                }
            }
            .onKeyEvent { event ->
                when (event.key) {
                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        if (event.type == KeyEventType.KeyUp) onActivate()
                        true
                    }
                    else -> false
                }
            }
            .focusable()
    } else {
        Modifier
    }
    Column(
        modifier = Modifier
            .defaultMinSize(minWidth = 76.dp)
            .then(interaction)
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val value = scale()
                scaleX = value
                scaleY = value
            },
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = item.label,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FloatingLiquidBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    items: List<NavigationItem>,
    backdrop: Backdrop,
    enabled: Boolean,
) {
    val tabsCount = items.size
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pillShape = CircleShape
    val accentColor = MiuixTheme.colorScheme.primary
    val tabContentColor = MiuixTheme.colorScheme.onSurface
    val containerColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val isLeftToRight = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    val horizontalPaddingPx = with(density) { 4.dp.toPx() }
    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) 0f else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }
    val latestOnSelected by rememberUpdatedState(onSelected)
    var currentIndex by remember { mutableIntStateOf(selectedIndex) }
    var dragStartIndex by remember { mutableIntStateOf(selectedIndex) }
    var dragDistance by remember { mutableFloatStateOf(0f) }

    fun commitSelection(index: Int) {
        if (currentIndex == index) return
        currentIndex = index
        latestOnSelected(index)
    }

    fun resetPanelOffset() {
        animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
    }

    val dragAnimation = remember(animationScope, tabsCount, density, isLeftToRight) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            onDragStarted = { position ->
                dragDistance = 0f
                animationScope.launch(start = CoroutineStart.UNDISPATCHED) { offsetAnimation.snapTo(0f) }
                dragStartIndex = if (tabWidthPx > 0f) {
                    val logicalX = if (isLeftToRight) position.x else totalWidthPx - position.x
                    ((logicalX - horizontalPaddingPx) / tabWidthPx).toInt().coerceIn(0, tabsCount - 1)
                } else {
                    currentIndex
                }
                updateValue(dragStartIndex.toFloat())
            },
            onDragStopped = {
                val index = targetValue.fastRoundToInt()
                commitSelection(index)
                updateValue(index.toFloat())
                resetPanelOffset()
            },
            onDragCancelled = {
                updateValue(currentIndex.toFloat())
                resetPanelOffset()
            },
            onDrag = { amount ->
                if (tabWidthPx > 0f && amount.x != 0f) {
                    dragDistance += amount.x
                    val logicalDistance = if (isLeftToRight) dragDistance else -dragDistance
                    updateValue(dragStartIndex + logicalDistance / tabWidthPx)
                    animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        offsetAnimation.snapTo(dragDistance)
                    }
                }
            },
        )
    }
    LaunchedEffect(dragAnimation, selectedIndex) {
        if (currentIndex != selectedIndex) {
            currentIndex = selectedIndex
            dragAnimation.animateToValue(selectedIndex.toFloat())
        }
    }
    val interactiveHighlight = remember(dragAnimation, isLeftToRight) {
        InteractiveHighlight({ dragAnimation.pressProgress }) { size ->
            Offset(
                x = if (isLeftToRight) {
                    (dragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                } else {
                    size.width - (dragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                },
                y = size.height / 2f,
            )
        }
    }
    val baseStroke = rememberGravityStroke(IndicatorSpecular, -45f)
    val pillStroke = rememberGravityStroke(IndicatorSpecular, 90f)
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)

    Box(
        modifier = Modifier
            .width(IntrinsicSize.Min)
            .onSizeChanged { size ->
                totalWidthPx = size.width.toFloat()
                tabWidthPx = ((totalWidthPx - horizontalPaddingPx * 2) / tabsCount).coerceAtLeast(0f)
            }
            // Keep the pointer coordinates attached to the bar instead of the sliding indicator.
            .then(if (enabled) dragAnimation.modifier else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .selectableGroup()
                .graphicsLayer { translationX = panelOffset }
                .dropShadow(
                    shape = pillShape,
                    shadow = Shadow(
                        radius = 10.dp,
                        color = Color.Black,
                        alpha = if (isDark) 0.2f else 0.1f,
                    ),
                )
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { pillShape },
                    effects = {
                        // Reserve room for the lens and the press scale before sampling the blur.
                        padding = maxOf(padding, 40.dp.toPx())
                        vibrancy()
                        blur(4.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    layerBlock = {
                        val width = size.width.coerceAtLeast(1f)
                        val scale = lerp(1f, 1f + 16.dp.toPx() / width, dragAnimation.pressProgress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                    onDrawFront = { drawEdgeStroke(pillShape, baseStroke, alpha = 0.75f) },
                )
                .then(interactiveHighlight.modifier)
                .height(64.dp)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides tabContentColor) {
                items.forEachIndexed { index, item ->
                    FloatingLiquidBarItem(
                        item = item,
                        isSelected = currentIndex == index,
                        onActivate = if (enabled) {
                            {
                                commitSelection(index)
                                dragAnimation.animateToValue(index.toFloat())
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        CompositionLocalProvider(
            LocalLiquidTabScale provides { lerp(1f, 1.2f, dragAnimation.pressProgress) },
            LocalContentColor provides accentColor,
        ) {
            Row(
                modifier = Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { pillShape },
                        effects = {
                            padding = maxOf(padding, 24.dp.toPx())
                            vibrancy()
                            blur(4.dp.toPx())
                            lens(24.dp.toPx(), 24.dp.toPx())
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { FloatingLiquidBarItem(it, isSelected = false, onActivate = null) }
            }
        }

        if (tabWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .graphicsLayer {
                        val offset = dragAnimation.value * tabWidthPx
                        translationX = if (isLeftToRight) offset + panelOffset else -offset + panelOffset
                    }
                    .drawBackdrop(
                        backdrop = combinedBackdrop,
                        shape = { pillShape },
                        effects = {
                            val progress = dragAnimation.pressProgress
                            lens(
                                refractionHeight = 10.dp.toPx() * progress,
                                refractionAmount = 14.dp.toPx() * progress,
                                depthEffect = true,
                                chromaticAberration = 0.5f,
                            )
                        },
                        layerBlock = {
                            scaleX = dragAnimation.scaleX
                            scaleY = dragAnimation.scaleY
                            val velocity = dragAnimation.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            val progress = dragAnimation.pressProgress
                            drawRect(
                                color = if (isDark) Color.White.copy(alpha = 0.1f)
                                else Color.Black.copy(alpha = 0.1f),
                                alpha = 1f - progress,
                            )
                            drawRect(Color.Black.copy(alpha = 0.03f * progress))
                        },
                        onDrawFront = {
                            drawEdgeStroke(pillShape, pillStroke, alpha = dragAnimation.pressProgress)
                        },
                    )
                    .height(56.dp)
                    .width(with(density) { tabWidthPx.toDp() }),
            )
        }
    }
}


@SuppressLint("NewApi")
private class InteractiveHighlight(
    pressProgress: () -> Float,
    position: (Size) -> Offset,
) {
    private val shader = RuntimeShader(
        """
        layout(color) uniform half4 color;
        uniform float radius;
        uniform float2 position;
        half4 main(float2 coord) {
            float dist = distance(coord, position);
            float intensity = smoothstep(radius, radius * 0.5, dist);
            return color * intensity;
        }
        """.trimIndent(),
    )
    private val brush = ShaderBrush(shader)

    val modifier = Modifier.drawWithContent {
        val progress = pressProgress()
        if (progress > 0f) {
            drawRect(Color.White.copy(alpha = 0.06f * progress), blendMode = BlendMode.Plus)
            val highlightPosition = position(size)
            shader.setColorUniform("color", Color.White.copy(alpha = 0.12f * progress).toArgb())
            shader.setFloatUniform("radius", size.minDimension * 1.2f)
            shader.setFloatUniform(
                "position",
                highlightPosition.x.fastCoerceIn(0f, size.width),
                highlightPosition.y.fastCoerceIn(0f, size.height),
            )
            drawRect(brush, blendMode = BlendMode.Plus)
        }
        drawContent()
    }
}
