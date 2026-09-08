package io.github.hyperisland.compose.page.settings.appearance

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import io.github.hyperisland.compose.component.BACKGROUND_PARALLAX
import io.github.hyperisland.compose.component.BACKGROUND_SCALE_REDUCTION
import io.github.hyperisland.compose.component.BarBackdropContent
import io.github.hyperisland.compose.component.BarBlurHost
import io.github.hyperisland.compose.component.EFFECT_VISIBILITY_THRESHOLD
import io.github.hyperisland.compose.component.LAYER_ENTER_DURATION
import io.github.hyperisland.compose.component.LAYER_EXIT_DURATION
import io.github.hyperisland.compose.component.LocalBarBlurEnabled
import io.github.hyperisland.compose.component.PREDICTIVE_CANCEL_DURATION
import io.github.hyperisland.compose.component.PREDICTIVE_DISMISS_DURATION
import io.github.hyperisland.compose.component.PredictiveBackBackdrop
import io.github.hyperisland.compose.component.PredictiveBackMotionTracker
import io.github.hyperisland.compose.component.predictiveEffectIntensity
import io.github.hyperisland.compose.component.predictiveExitProgress
import io.github.hyperisland.compose.component.predictiveSettleDuration
import io.github.hyperisland.compose.component.predictiveSettleEasing
import io.github.hyperisland.compose.component.predictiveTranslationFraction
import io.github.hyperisland.compose.component.smootherStep
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberLongPreference
import io.github.hyperisland.compose.theme.DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT
import io.github.hyperisland.compose.theme.PREF_PREDICTIVE_BACK_MAX_TRANSLATION
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
internal fun AppearancePage(
    prefs: FlutterPrefsRepository,
    materialVisible: Boolean,
    onOpenMaterial: () -> Unit,
    onBack: () -> Unit,
) {
    var sectionTarget by rememberSaveable { mutableStateOf<AppearanceSection?>(null) }
    var sectionVisible by rememberSaveable { mutableStateOf(false) }
    var predictiveBackActive by remember { mutableStateOf(false) }
    var predictiveCommitting by remember { mutableStateOf(false) }
    val predictiveProgress = remember { Animatable(0f) }
    val backdropIntensity = remember { Animatable(0f) }
    val overviewLayerDepth = remember { Animatable(0f) }
    val predictiveMotion = remember { PredictiveBackMotionTracker() }
    val scope = rememberCoroutineScope()
    val predictiveBackMaxTranslation = rememberLongPreference(
        prefs,
        PREF_PREDICTIVE_BACK_MAX_TRANSLATION,
        DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT,
    )
    val blurBarsEnabled = LocalBarBlurEnabled.current

    fun openSection(section: AppearanceSection) {
        sectionTarget = section
        sectionVisible = true
    }

    fun closeSection() {
        sectionVisible = false
    }

    LaunchedEffect(sectionVisible, predictiveBackActive) {
        if (!predictiveBackActive) {
            val target = if (sectionVisible) 1f else 0f
            val duration = if (sectionVisible) LAYER_ENTER_DURATION else LAYER_EXIT_DURATION
            coroutineScope {
                launch { backdropIntensity.animateTo(target, tween(duration, easing = FastOutSlowInEasing)) }
                launch { overviewLayerDepth.animateTo(target, tween(duration, easing = FastOutSlowInEasing)) }
            }
        }
    }

    suspend fun finishPredictiveBack() {
        predictiveCommitting = true
        val targetProgress = predictiveExitProgress(predictiveBackMaxTranslation.value)
        val duration = predictiveSettleDuration(predictiveProgress.value, predictiveBackMaxTranslation.value)
        val settleEasing = predictiveSettleEasing(
            releaseVelocity = predictiveMotion.releaseVelocity(),
            currentProgress = predictiveProgress.value,
            targetProgress = targetProgress,
            durationMillis = duration,
        )
        coroutineScope {
            launch { predictiveProgress.animateTo(targetProgress, tween(duration, easing = settleEasing)) }
            launch { backdropIntensity.animateTo(0f, tween(duration, easing = settleEasing)) }
            launch { overviewLayerDepth.animateTo(0f, tween(duration, easing = settleEasing)) }
        }
        closeSection()
        delay(PREDICTIVE_DISMISS_DURATION.toLong())
        predictiveProgress.snapTo(0f)
        predictiveMotion.reset()
        predictiveBackActive = false
        predictiveCommitting = false
    }

    PredictiveBackHandler(enabled = sectionVisible && !materialVisible) { events ->
        try {
            events.collect { event ->
                if (!predictiveBackActive) predictiveMotion.reset(event.progress)
                else predictiveMotion.update(event.progress)
                predictiveBackActive = true
                predictiveProgress.snapTo(event.progress)
                val progress = smootherStep(event.progress)
                backdropIntensity.snapTo(predictiveEffectIntensity(progress))
                overviewLayerDepth.snapTo(1f - progress)
            }
            finishPredictiveBack()
        } catch (_: CancellationException) {
            if (!predictiveCommitting) {
                coroutineScope {
                    launch { predictiveProgress.animateTo(0f, tween(PREDICTIVE_CANCEL_DURATION, easing = FastOutSlowInEasing)) }
                    launch { backdropIntensity.animateTo(1f, tween(PREDICTIVE_CANCEL_DURATION, easing = FastOutSlowInEasing)) }
                    launch { overviewLayerDepth.animateTo(1f, tween(PREDICTIVE_CANCEL_DURATION, easing = FastOutSlowInEasing)) }
                }
                predictiveMotion.reset()
                predictiveBackActive = false
            }
        }
    }

    BackHandler(enabled = predictiveBackActive && predictiveCommitting) {
        scope.launch { finishPredictiveBack() }
    }

    BarBlurHost(
        enabled = blurBarsEnabled,
        captureForEffects = sectionVisible || predictiveBackActive || backdropIntensity.value > EFFECT_VISIBILITY_THRESHOLD,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        val depth = overviewLayerDepth.value.coerceIn(0f, 1f)
                        scaleX = 1f - depth * BACKGROUND_SCALE_REDUCTION
                        scaleY = scaleX
                        translationX = -size.width * depth * BACKGROUND_PARALLAX
                    },
                ) {
                    AppearanceOverviewPage(::openSection, onBack)
                }
            }
            PredictiveBackBackdrop(
                intensity = backdropIntensity.value,
                visible = backdropIntensity.value > EFFECT_VISIBILITY_THRESHOLD,
                modifier = Modifier.fillMaxSize(),
            )
            AnimatedVisibility(
                visible = sectionVisible,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    translationX = size.width * predictiveProgress.value.coerceAtLeast(0f) *
                        predictiveTranslationFraction(predictiveBackMaxTranslation.value)
                },
                enter = slideInHorizontally(tween(LAYER_ENTER_DURATION, easing = FastOutSlowInEasing)) { it },
                exit = if (predictiveBackActive) ExitTransition.None
                else slideOutHorizontally(tween(LAYER_EXIT_DURATION, easing = FastOutSlowInEasing)) { it },
            ) {
                when (sectionTarget) {
                    AppearanceSection.Size -> AppearanceSizePage(prefs, ::closeSection)
                    AppearanceSection.Background -> AppearanceBackgroundPage(prefs, onOpenMaterial, ::closeSection)
                    AppearanceSection.Text -> AppearanceTextPage(prefs, ::closeSection)
                    AppearanceSection.Icon -> AppearanceIconPage(prefs, ::closeSection)
                    AppearanceSection.Outline -> AppearanceOutlinePage(prefs, ::closeSection)
                    null -> Unit
                }
            }
        }
    }
}
