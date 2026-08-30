// Adapted from SukiSU-Ultra's FloatingBottomBar and the compose-miuix-ui
// IosLiquidGlassNavigationBar example. Licensed under Apache-2.0.
package io.github.hyperisland.compose.component

import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import io.github.hyperisland.compose.component.liquid.lens
import io.github.hyperisland.compose.component.liquid.rememberCombinedBackdrop
import io.github.hyperisland.compose.component.liquid.vibrancy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

@Immutable
internal data class LiquidGlassNavigationItem(val icon: ImageVector, val label: String)

@Composable
internal fun LiquidGlassNavigationBar(
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    items: List<LiquidGlassNavigationItem>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val backdrop = LocalBarBlurBackdrop.current
    val barModifier = modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
    if (backdrop == null) {
        Box(modifier = barModifier, contentAlignment = Alignment.Center) {
            FloatingNavigationBar {
                items.forEachIndexed { index, item ->
                    FloatingNavigationBarItem(
                        selected = selectedTabIndex() == index,
                        onClick = { onTabSelected(index) },
                        icon = item.icon,
                        label = item.label,
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
            selectedIndex = selectedTabIndex,
            onSelected = onTabSelected,
            backdrop = backdrop,
            tabsCount = items.size,
        ) {
            items.forEachIndexed { index, item ->
                FloatingLiquidBarItem(
                    selected = selectedTabIndex() == index,
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.defaultMinSize(minWidth = 76.dp),
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
        }
    }
}

private val LocalLiquidTabScale = staticCompositionLocalOf { { 1f } }

private val IndicatorSpecular = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f,
        ),
        dualPeak = true,
    ),
)

@Composable
private fun rememberGravityHighlight(base: Highlight, extraDegrees: Float): Highlight {
    val style = base.style as BloomStroke
    val tilt by rememberDeviceTilt()
    val primary = remember(tilt, style.primaryLight, extraDegrees) {
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
        style.primaryLight.copy(
            position = LightPosition(0.5f + rotatedX, 0.7f + rotatedY, style.primaryLight.position.z),
        )
    }
    return remember(base, primary) { base.copy(style = style.copy(primaryLight = primary)) }
}

@Composable
private fun RowScope.FloatingLiquidBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalLiquidTabScale.current
    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val value = scale()
                scaleX = value
                scaleY = value
            },
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun FloatingLiquidBar(
    selectedIndex: () -> Int,
    onSelected: (Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
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
    var currentIndex by remember(selectedIndex) { mutableIntStateOf(selectedIndex()) }
    class Holder { var animation: DampedDragAnimation? = null }
    val holder = remember { Holder() }
    val dragAnimation = remember(animationScope, tabsCount, density, isLeftToRight) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex().toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            canDrag = { offset ->
                val animation = holder.animation ?: return@DampedDragAnimation true
                if (tabWidthPx == 0f) return@DampedDragAnimation false
                val padding = with(density) { 4.dp.toPx() }
                val indicatorX = animation.value * tabWidthPx
                val touchX = if (isLeftToRight) {
                    padding + indicatorX + offset.x
                } else {
                    totalWidthPx - padding - tabWidthPx - indicatorX + offset.x
                }
                touchX in 0f..totalWidthPx
            },
            onDragStopped = {
                val target = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                currentIndex = target
                animateToValue(target.toFloat())
                animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
            },
            onDrag = { _, amount ->
                if (tabWidthPx > 0f) {
                    updateValue(
                        (targetValue + amount.x / tabWidthPx * if (isLeftToRight) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + amount.x) }
                }
            },
        ).also { holder.animation = it }
    }
    LaunchedEffect(selectedIndex) {
        snapshotFlow { selectedIndex() }.collectLatest {
            currentIndex = it.fastCoerceIn(0, tabsCount - 1)
        }
    }
    LaunchedEffect(dragAnimation) {
        snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
            dragAnimation.animateToValue(index.toFloat())
            onSelected(index)
        }
    }
    val interactiveHighlight = remember(animationScope, tabWidthPx, isLeftToRight) {
        InteractiveHighlight(animationScope) { size, _ ->
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
    val baseHighlight = rememberGravityHighlight(IndicatorSpecular, -45f)
    val pillHighlight = rememberGravityHighlight(IndicatorSpecular, 90f)
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)

    Box(modifier = modifier.width(IntrinsicSize.Min), contentAlignment = Alignment.CenterStart) {
        Row(
            modifier = Modifier
                .selectableGroup()
                .onGloballyPositioned { coordinates ->
                    totalWidthPx = coordinates.size.width.toFloat()
                    tabWidthPx = ((totalWidthPx - with(density) { 8.dp.toPx() }) / tabsCount)
                        .coerceAtLeast(0f)
                }
                .graphicsLayer { translationX = panelOffset }
                .dropShadow(
                    shape = pillShape,
                    shadow = Shadow(
                        radius = 10.dp,
                        color = Color.Black,
                        alpha = if (isDark) 0.2f else 0.1f,
                    ),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { pillShape },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx(), 4.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    highlight = { baseHighlight.copy(alpha = 0.75f) },
                    layerBlock = {
                        val width = size.width.coerceAtLeast(1f)
                        val scale = lerp(1f, 1f + 16.dp.toPx() / width, dragAnimation.pressProgress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .then(interactiveHighlight.modifier)
                .height(64.dp)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides tabContentColor) { content() }
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
                            vibrancy()
                            blur(4.dp.toPx(), 4.dp.toPx())
                            lens(24.dp.toPx(), 24.dp.toPx())
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        if (tabWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .graphicsLayer {
                        val offset = dragAnimation.value * tabWidthPx
                        translationX = if (isLeftToRight) offset + panelOffset else -offset + panelOffset
                    }
                    .then(interactiveHighlight.gestureModifier)
                    .then(dragAnimation.modifier)
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
                        highlight = { pillHighlight.copy(alpha = dragAnimation.pressProgress) },
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
                    )
                    .height(56.dp)
                    .width(with(density) { tabWidthPx.toDp() }),
            )
        }
    }
}

private class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedRange<Float>,
    private val canDrag: (Offset) -> Boolean = { true },
    private val onDragStopped: DampedDragAnimation.() -> Unit,
    private val onDrag: DampedDragAnimation.(IntSize, Offset) -> Unit,
) {
    private val valueAnimation = Animatable(initialValue, 0.001f)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(1f, 0.001f)
    private val scaleYAnimation = Animatable(1f, 0.001f)
    private val mutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = {
                velocityTracker.resetTracking()
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            },
        ) { change, amount ->
            if (canDrag(change.position) && canDrag(change.previousPosition)) onDrag(size, amount)
        }
    }

    private fun press() {
        animationScope.launch {
            launch { pressAnimation.animateTo(1f, spring(1f, 1000f, 0.001f)) }
            launch { scaleXAnimation.animateTo(78f / 56f, spring(0.6f, 250f, 0.001f)) }
            launch { scaleYAnimation.animateTo(78f / 56f, spring(0.7f, 250f, 0.001f)) }
        }
    }

    private fun release() {
        animationScope.launch {
            withFrameNanos { }
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .first { abs(it - valueAnimation.targetValue) < threshold }
            }
            launch { pressAnimation.animateTo(0f, spring(1f, 1000f, 0.001f)) }
            launch { scaleXAnimation.animateTo(1f, spring(0.6f, 250f, 0.001f)) }
            launch { scaleYAnimation.animateTo(1f, spring(0.7f, 250f, 0.001f)) }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch {
            valueAnimation.animateTo(target, spring(1f, 1000f, 0.001f)) { updateVelocity() }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutex.mutate {
                press()
                launch {
                    valueAnimation.animateTo(value.coerceIn(valueRange), spring(1f, 1000f, 0.001f))
                }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, spring(0.5f, 300f, 0.01f)) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(value, 0f))
        val range = valueRange.endInclusive - valueRange.start
        val target = if (range == 0f) 0f else velocityTracker.calculateVelocity().x / range
        animationScope.launch { velocityAnimation.animateTo(target, spring(0.5f, 300f, 0.01f)) }
    }
}

@SuppressLint("NewApi")
private class InteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val position: (Size, Offset) -> Offset,
) {
    private val pressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(
        Offset.Zero,
        Offset.VectorConverter,
        Offset.VisibilityThreshold,
    )
    private var startPosition = Offset.Zero
    private val shader = RuntimeShader(
        """
        uniform float2 size;
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

    val modifier = Modifier.drawWithContent {
            val progress = pressAnimation.value
            if (progress > 0f) {
                drawRect(Color.White.copy(alpha = 0.06f * progress), blendMode = BlendMode.Plus)
                val highlightPosition = position(size, positionAnimation.value)
                shader.setFloatUniform("size", size.width, size.height)
                shader.setColorUniform("color", Color.White.copy(alpha = 0.12f * progress).toArgb())
                shader.setFloatUniform("radius", size.minDimension * 1.2f)
                shader.setFloatUniform(
                    "position",
                    highlightPosition.x.fastCoerceIn(0f, size.width),
                    highlightPosition.y.fastCoerceIn(0f, size.height),
                )
                drawRect(ShaderBrush(shader), blendMode = BlendMode.Plus)
            }
            drawContent()
    }

    val gestureModifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch { pressAnimation.animateTo(1f, spring(0.5f, 300f, 0.001f)) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = { reset() },
            onDragCancel = { reset() },
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }

    private fun reset() {
        animationScope.launch {
            launch { pressAnimation.animateTo(0f, spring(0.5f, 300f, 0.001f)) }
            launch {
                positionAnimation.animateTo(
                    startPosition,
                    spring(0.5f, 300f, Offset.VisibilityThreshold),
                )
            }
        }
    }
}

private suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (PointerInputChange) -> Unit = {},
    onDragEnd: (PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (PointerInputChange, Offset) -> Unit,
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val down = awaitFirstDown(requireUnconsumed = false)
        onDragStart(down)
        onDrag(initialDown, Offset.Zero)
        val up = drag(initialDown.id) { change -> onDrag(change, change.positionChange()) }
        if (up == null) onDragCancel() else onDragEnd(up)
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    if (currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId,
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (change.changedToUpIgnoreConsumed()) {
            val other = event.changes.fastFirstOrNull { it.pressed }
            if (other == null) return change
            pointer = other.id
        } else if (change.previousPosition != change.position) {
            return change
        }
    }
}
