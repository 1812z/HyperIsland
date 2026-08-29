package io.github.hyperisland.compose.component

import androidx.compose.animation.core.CubicBezierEasing
import kotlin.math.roundToInt

internal val PredictiveSettleEasing = CubicBezierEasing(0.30f, 0.45f, 0.35f, 1f)

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
