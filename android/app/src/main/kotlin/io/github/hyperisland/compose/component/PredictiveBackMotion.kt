package io.github.hyperisland.compose.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import kotlin.math.abs
import kotlin.math.roundToInt

internal class PredictiveBackMotionTracker(
    private val timeNanos: () -> Long = System::nanoTime,
) {
    private var lastProgress = 0f
    private var lastSampleNanos = Long.MIN_VALUE
    private var lastMovementNanos = Long.MIN_VALUE
    private var filteredVelocity = 0f
    private var latchedReleaseVelocity: Float? = null

    fun reset(progress: Float = 0f) {
        lastProgress = progress
        lastSampleNanos = timeNanos()
        lastMovementNanos = lastSampleNanos
        filteredVelocity = 0f
        latchedReleaseVelocity = null
    }

    fun update(progress: Float) {
        val now = timeNanos()
        val delta = progress - lastProgress
        val elapsedNanos = now - lastSampleNanos
        if (elapsedNanos in 1..PREDICTIVE_MAX_VELOCITY_SAMPLE_NANOS &&
            abs(delta) >= PREDICTIVE_MOTION_EPSILON
        ) {
            if (delta > 0f) {
                val elapsedSeconds = elapsedNanos / NANOS_PER_SECOND
                val instantVelocity = (delta / elapsedSeconds)
                    .coerceIn(0f, PREDICTIVE_MAX_TRACKED_VELOCITY)
                val blend = (elapsedSeconds / PREDICTIVE_VELOCITY_FILTER_SECONDS)
                    .coerceIn(PREDICTIVE_MIN_VELOCITY_BLEND, PREDICTIVE_MAX_VELOCITY_BLEND)
                filteredVelocity += (instantVelocity - filteredVelocity) * blend
            } else {
                filteredVelocity = 0f
            }
            lastMovementNanos = now
        }
        lastProgress = progress
        lastSampleNanos = now
        latchedReleaseVelocity = null
    }

    fun releaseVelocity(): Float {
        latchedReleaseVelocity?.let { return it }
        val idleNanos = (timeNanos() - lastMovementNanos).coerceAtLeast(0L)
        val idleRetention = when {
            idleNanos <= PREDICTIVE_FULL_MOMENTUM_IDLE_NANOS -> 1f
            idleNanos >= PREDICTIVE_ZERO_MOMENTUM_IDLE_NANOS -> 0f
            else -> {
                val remaining = (PREDICTIVE_ZERO_MOMENTUM_IDLE_NANOS - idleNanos).toFloat()
                val range = (PREDICTIVE_ZERO_MOMENTUM_IDLE_NANOS -
                    PREDICTIVE_FULL_MOMENTUM_IDLE_NANOS).toFloat()
                smootherStep(remaining / range)
            }
        }
        return (filteredVelocity * idleRetention).also { latchedReleaseVelocity = it }
    }
}

internal fun predictiveSettleEasing(
    releaseVelocity: Float,
    currentProgress: Float,
    targetProgress: Float,
    durationMillis: Int,
): Easing {
    val remainingProgress = (targetProgress - currentProgress).coerceAtLeast(PREDICTIVE_MOTION_EPSILON)
    val normalizedInitialSlope = releaseVelocity * (durationMillis / MILLIS_PER_SECOND) /
        remainingProgress
    val initialY = (PREDICTIVE_EASING_INITIAL_X * normalizedInitialSlope)
        .coerceIn(0f, PREDICTIVE_MAX_INITIAL_EASING_Y)
    return CubicBezierEasing(PREDICTIVE_EASING_INITIAL_X, initialY, 0.35f, 1f)
}

internal fun smootherStep(progress: Float): Float {
    val value = progress.coerceIn(0f, 1f)
    return value * value * value * (value * (value * 6f - 15f) + 10f)
}

internal fun predictiveEffectIntensity(smoothProgress: Float): Float =
    1f - smoothProgress * (1f - PREDICTIVE_MIN_EFFECT_INTENSITY)

internal fun predictiveTranslationFraction(percent: Long): Float =
    percent.coerceIn(1L, 100L).toFloat() / 100f

internal fun predictiveExitProgress(maxTranslationPercent: Long): Float =
    1f / predictiveTranslationFraction(maxTranslationPercent)

internal fun predictiveSettleDuration(progress: Float, maxTranslationPercent: Long): Int {
    val translationFraction = predictiveTranslationFraction(maxTranslationPercent)
    val currentTranslation = (progress.coerceAtLeast(0f) * translationFraction).coerceIn(0f, 1f)
    return (PREDICTIVE_SETTLE_DURATION * (1f - currentTranslation))
        .roundToInt()
        .coerceIn(PREDICTIVE_MIN_SETTLE_DURATION, PREDICTIVE_SETTLE_DURATION)
}

internal const val LAYER_ENTER_DURATION = 420
internal const val LAYER_EXIT_DURATION = 380
internal const val PREDICTIVE_CANCEL_DURATION = 280
internal const val PREDICTIVE_DISMISS_DURATION = 24
internal const val BACKGROUND_SCALE_REDUCTION = 0.035f
internal const val BACKGROUND_PARALLAX = 0.025f
internal const val EFFECT_VISIBILITY_THRESHOLD = 0.001f

private const val PREDICTIVE_SETTLE_DURATION = 480
private const val PREDICTIVE_MIN_SETTLE_DURATION = 24
private const val PREDICTIVE_MIN_EFFECT_INTENSITY = 0.5f
private const val PREDICTIVE_MOTION_EPSILON = 0.0005f
private const val PREDICTIVE_MAX_VELOCITY_SAMPLE_NANOS = 120_000_000L
private const val PREDICTIVE_FULL_MOMENTUM_IDLE_NANOS = 40_000_000L
private const val PREDICTIVE_ZERO_MOMENTUM_IDLE_NANOS = 160_000_000L
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MILLIS_PER_SECOND = 1_000f
private const val PREDICTIVE_VELOCITY_FILTER_SECONDS = 0.05f
private const val PREDICTIVE_MIN_VELOCITY_BLEND = 0.2f
private const val PREDICTIVE_MAX_VELOCITY_BLEND = 0.65f
private const val PREDICTIVE_MAX_TRACKED_VELOCITY = 6f
private const val PREDICTIVE_EASING_INITIAL_X = 0.30f
private const val PREDICTIVE_MAX_INITIAL_EASING_Y = 0.42f
