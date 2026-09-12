package io.github.hyperisland.compose.component

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Stable
internal class PredictiveNavigationLayerState internal constructor() {
    var isBackActive by mutableStateOf(false)
        private set

    var isCommitting by mutableStateOf(false)
        private set

    val progress = Animatable(0f)
    val backdropIntensity = Animatable(0f)
    val backgroundDepth = Animatable(0f)

    private val motion = PredictiveBackMotionTracker()

    internal suspend fun animateVisibility(visible: Boolean) {
        if (isBackActive) return
        val target = if (visible) 1f else 0f
        val duration = if (visible) LAYER_ENTER_DURATION else LAYER_EXIT_DURATION
        coroutineScope {
            launch {
                backdropIntensity.animateTo(
                    target,
                    tween(duration, easing = FastOutSlowInEasing),
                )
            }
            launch {
                backgroundDepth.animateTo(
                    target,
                    tween(duration, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    internal suspend fun trackBack(progress: Float) {
        if (!isBackActive) {
            motion.reset(progress)
        } else {
            motion.update(progress)
        }
        isBackActive = true
        this.progress.snapTo(progress)
        val smoothProgress = smootherStep(progress)
        backdropIntensity.snapTo(predictiveEffectIntensity(smoothProgress))
        backgroundDepth.snapTo(1f - smoothProgress)
    }

    internal suspend fun cancelBack() {
        if (isCommitting) return
        coroutineScope {
            launch {
                progress.animateTo(
                    0f,
                    tween(PREDICTIVE_CANCEL_DURATION, easing = FastOutSlowInEasing),
                )
            }
            launch {
                backdropIntensity.animateTo(
                    1f,
                    tween(PREDICTIVE_CANCEL_DURATION, easing = FastOutSlowInEasing),
                )
            }
            launch {
                backgroundDepth.animateTo(
                    1f,
                    tween(PREDICTIVE_CANCEL_DURATION, easing = FastOutSlowInEasing),
                )
            }
        }
        motion.reset()
        isBackActive = false
    }

    internal suspend fun commitBack(
        maxTranslationPercent: Long,
        additionalAnimation: suspend (durationMillis: Int, easing: Easing) -> Unit,
        onDismiss: () -> Unit,
    ) {
        if (isCommitting) return
        isCommitting = true
        try {
            val targetProgress = predictiveExitProgress(maxTranslationPercent)
            val duration = predictiveSettleDuration(
                progress = progress.value,
                maxTranslationPercent = maxTranslationPercent,
            )
            val settleEasing = predictiveSettleEasing(
                releaseVelocity = motion.releaseVelocity(),
                currentProgress = progress.value,
                targetProgress = targetProgress,
                durationMillis = duration,
            )
            coroutineScope {
                launch {
                    progress.animateTo(
                        targetProgress,
                        tween(duration, easing = settleEasing),
                    )
                }
                launch {
                    backdropIntensity.animateTo(0f, tween(duration, easing = settleEasing))
                }
                launch {
                    backgroundDepth.animateTo(0f, tween(duration, easing = settleEasing))
                }
                launch { additionalAnimation(duration, settleEasing) }
            }
            onDismiss()
            delay(PREDICTIVE_DISMISS_DURATION.toLong())
        } finally {
            withContext(NonCancellable) {
                progress.snapTo(0f)
                motion.reset()
                isBackActive = false
                isCommitting = false
            }
        }
    }
}

@Composable
internal fun rememberPredictiveNavigationLayerState(): PredictiveNavigationLayerState =
    remember { PredictiveNavigationLayerState() }

@Composable
internal fun PredictiveNavigationBackHandler(
    visible: Boolean,
    enabled: Boolean,
    state: PredictiveNavigationLayerState,
    maxTranslationPercent: Long,
    onDismiss: () -> Unit,
    additionalCommitAnimation: suspend (durationMillis: Int, easing: Easing) -> Unit = { _, _ -> },
) {
    LaunchedEffect(visible, state.isBackActive) {
        state.animateVisibility(visible)
    }

    PredictiveBackHandler(enabled = enabled) { events ->
        var cancelled = false
        try {
            events.collect { event -> state.trackBack(event.progress) }
        } catch (_: CancellationException) {
            state.cancelBack()
            cancelled = true
        }
        if (!cancelled) {
            state.commitBack(maxTranslationPercent, additionalCommitAnimation, onDismiss)
        }
    }

    BackHandler(enabled = state.isBackActive && state.isCommitting) {
        // Consume additional back events until the committed transition finishes.
    }
}

@Composable
internal fun PredictiveNavigationLayer(
    visible: Boolean,
    state: PredictiveNavigationLayerState,
    maxTranslationPercent: Long,
    modifier: Modifier = Modifier,
    backgroundState: PredictiveNavigationLayerState? = null,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier
            .fillMaxSize()
            .predictiveNavigationTransform(
                foregroundState = state,
                backgroundState = backgroundState,
                maxTranslationPercent = maxTranslationPercent,
            ),
        enter = slideInHorizontally(
            tween(LAYER_ENTER_DURATION, easing = FastOutSlowInEasing),
        ) { it },
        exit = if (state.isBackActive) {
            ExitTransition.None
        } else {
            slideOutHorizontally(
                tween(LAYER_EXIT_DURATION, easing = FastOutSlowInEasing),
            ) { it }
        },
    ) {
        content()
    }
}

@Composable
internal fun PredictiveNavigationBackdrop(
    state: PredictiveNavigationLayerState,
    modifier: Modifier = Modifier,
) {
    PredictiveBackBackdrop(
        intensity = state.backdropIntensity.value,
        visible = state.backdropIntensity.value > EFFECT_VISIBILITY_THRESHOLD,
        modifier = modifier,
    )
}

internal fun PredictiveNavigationLayerState.requiresBackdropCapture(visible: Boolean): Boolean =
    visible || isBackActive || backdropIntensity.value > EFFECT_VISIBILITY_THRESHOLD

internal fun Modifier.predictiveNavigationBackground(
    state: PredictiveNavigationLayerState,
): Modifier = graphicsLayer {
    val depth = state.backgroundDepth.value.coerceIn(0f, 1f)
    scaleX = 1f - depth * BACKGROUND_SCALE_REDUCTION
    scaleY = scaleX
    translationX = -size.width * depth * BACKGROUND_PARALLAX
}

private fun Modifier.predictiveNavigationTransform(
    foregroundState: PredictiveNavigationLayerState,
    backgroundState: PredictiveNavigationLayerState?,
    maxTranslationPercent: Long,
): Modifier = graphicsLayer {
    val progress = foregroundState.progress.value.coerceAtLeast(0f)
    val depth = backgroundState?.backgroundDepth?.value?.coerceIn(0f, 1f) ?: 0f
    translationX = -size.width * depth * BACKGROUND_PARALLAX +
        size.width * progress * predictiveTranslationFraction(maxTranslationPercent)
    scaleX = 1f - depth * BACKGROUND_SCALE_REDUCTION
    scaleY = scaleX
}
