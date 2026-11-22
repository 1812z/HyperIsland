// Adapted from the compose-miuix-ui LiquidGlassNavigationBar example (Apache-2.0).
// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package io.github.hyperisland.compose.component.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedFloatingPointRange<Float>,
    private val onDragStarted: DampedDragAnimation.(Offset) -> Unit,
    private val onDragStopped: DampedDragAnimation.() -> Unit,
    private val onDragCancelled: DampedDragAnimation.() -> Unit,
    private val onDrag: DampedDragAnimation.(Offset) -> Unit,
) {
    private val valueSpec = spring(1f, 1000f, 0.001f)
    private val scaleXSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYSpec = spring(0.7f, 250f, 0.001f)
    private val velocitySpec = spring(0.5f, 300f, 0.01f)
    private val valueAnimation = Animatable(initialValue, 0.001f)
    private val pressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(1f, 0.001f)
    private val scaleYAnimation = Animatable(1f, 0.001f)
    private val velocityAnimation = Animatable(0f, 5f)
    private val velocityTracker = VelocityTracker()
    private val startedAt = TimeSource.Monotonic.markNow()
    private var pressJob: Job? = null
    private var releaseJob: Job? = null
    private var valueJob: Job? = null

    var targetValue = initialValue
        private set
    val value: Float get() = valueAnimation.value
    val pressProgress: Float get() = pressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier = Modifier.pointerInput(this) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            press()
            onDragStarted(down.position)
            var completed = false
            try {
                completed = trackDrag(down.id) { onDrag(it) }
            } finally {
                if (completed) onDragStopped() else onDragCancelled()
                release()
            }
        }
    }

    fun updateValue(value: Float) {
        moveTo(value, trackVelocity = true)
    }

    fun animateToValue(value: Float) {
        press()
        moveTo(value, trackVelocity = false)
        release()
    }

    private fun moveTo(value: Float, trackVelocity: Boolean) {
        val target = value.coerceIn(valueRange)
        if (target == targetValue) return
        targetValue = target
        valueJob?.cancel()
        valueJob = animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            valueAnimation.animateTo(target, valueSpec) {
                if (trackVelocity) updateVelocity()
            }
        }
    }

    private fun press() {
        releaseJob?.cancel()
        pressJob?.cancel()
        velocityTracker.resetTracking()
        pressJob = animationScope.launch {
            launch { pressAnimation.animateTo(1f, valueSpec) }
            launch { scaleXAnimation.animateTo(78f / 56f, scaleXSpec) }
            launch { scaleYAnimation.animateTo(78f / 56f, scaleYSpec) }
        }
    }

    private fun release() {
        releaseJob?.cancel()
        releaseJob = animationScope.launch {
            withFrameMillis { }
            val threshold = ((valueRange.endInclusive - valueRange.start) * 0.025f).coerceAtLeast(0.001f)
            snapshotFlow { abs(value - valueAnimation.targetValue) <= threshold }.first { it }
            launch { pressAnimation.animateTo(0f, valueSpec) }
            launch { scaleXAnimation.animateTo(1f, scaleXSpec) }
            launch { scaleYAnimation.animateTo(1f, scaleYSpec) }
            launch { velocityAnimation.animateTo(0f, velocitySpec) }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(startedAt.elapsedNow().inWholeMilliseconds, Offset(value, 0f))
        val range = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.001f)
        val velocity = velocityTracker.calculateVelocity().x / range
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) { velocityAnimation.snapTo(velocity) }
    }
}

private suspend fun AwaitPointerEventScope.trackDrag(
    pointerId: PointerId,
    onDrag: (Offset) -> Unit,
): Boolean {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointer } ?: return false
        if (change.isConsumed) return false
        val movement = change.positionChange()
        if (movement != Offset.Zero) {
            onDrag(movement)
            change.consume()
        }
        if (change.changedToUpIgnoreConsumed()) {
            change.consume()
            pointer = event.changes.firstOrNull { it.pressed }?.id ?: return true
        }
    }
}
