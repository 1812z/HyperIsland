package io.github.hyperisland.compose.page.apps.channel

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
import io.github.hyperisland.compose.component.PredictiveSettleEasing
import io.github.hyperisland.compose.component.predictiveEffectIntensity
import io.github.hyperisland.compose.component.predictiveExitProgress
import io.github.hyperisland.compose.component.predictiveSettleDuration
import io.github.hyperisland.compose.component.predictiveTranslationFraction
import io.github.hyperisland.compose.component.smootherStep
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.NotificationChannelInfo
import io.github.hyperisland.compose.data.channel.ChannelCustomizationTarget
import io.github.hyperisland.compose.data.channel.ChannelSettingsPatch
import io.github.hyperisland.compose.data.channel.toFullPatch
import io.github.hyperisland.compose.data.channel.withPatch
import io.github.hyperisland.compose.data.rememberLongPreference
import io.github.hyperisland.compose.theme.DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT
import io.github.hyperisland.compose.theme.PREF_PREDICTIVE_BACK_MAX_TRANSLATION
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
internal fun ChannelEditorPage(
    appPackage: String,
    channel: NotificationChannelInfo,
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
) {
    var settings by remember(appPackage, channel.id) {
        mutableStateOf(prefs.channelSettings(appPackage, channel.id))
    }
    var formState by remember(appPackage, channel.id) {
        mutableStateOf(settings.toFullPatch())
    }
    val defaults = remember { prefs.defaultConfigSettings() }
    var customizationTarget by remember { mutableStateOf<ChannelCustomizationTarget?>(null) }
    var customizationVisible by remember { mutableStateOf(false) }
    var predictiveBackActive by remember { mutableStateOf(false) }
    var predictiveCommitting by remember { mutableStateOf(false) }
    val predictiveProgress = remember { Animatable(0f) }
    val backdropIntensity = remember { Animatable(0f) }
    val editorLayerDepth = remember { Animatable(0f) }
    val predictiveBackMaxTranslation = rememberLongPreference(
        prefs,
        PREF_PREDICTIVE_BACK_MAX_TRANSLATION,
        DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT,
    )
    val blurBarsEnabled = LocalBarBlurEnabled.current

    fun updateForm(value: ChannelSettingsPatch) {
        val updated = settings.withPatch(value)
        settings = updated
        formState = updated.toFullPatch()
        prefs.setChannelSettings(appPackage, channel.id, updated)
    }

    fun updateCustom(target: ChannelCustomizationTarget, raw: String) {
        val updated = when (target) {
            ChannelCustomizationTarget.Island -> settings.copy(islandCustom = raw)
            ChannelCustomizationTarget.Focus -> settings.copy(focusCustom = raw)
            ChannelCustomizationTarget.Aod -> settings.copy(aodCustom = raw)
        }
        settings = updated
        prefs.setChannelSettings(appPackage, channel.id, updated)
    }

    fun closeCustomization() {
        customizationVisible = false
    }

    LaunchedEffect(customizationVisible, predictiveBackActive) {
        if (!predictiveBackActive) {
            val target = if (customizationVisible) 1f else 0f
            val duration = if (customizationVisible) LAYER_ENTER_DURATION else LAYER_EXIT_DURATION
            coroutineScope {
                launch {
                    backdropIntensity.animateTo(target, tween(duration, easing = FastOutSlowInEasing))
                }
                launch {
                    editorLayerDepth.animateTo(target, tween(duration, easing = FastOutSlowInEasing))
                }
            }
        }
    }

    suspend fun finishPredictiveBack() {
        predictiveCommitting = true
        val duration = predictiveSettleDuration(
            progress = predictiveProgress.value,
            maxTranslationPercent = predictiveBackMaxTranslation.value,
        )
        coroutineScope {
            launch {
                predictiveProgress.animateTo(
                    predictiveExitProgress(predictiveBackMaxTranslation.value),
                    tween(duration, easing = PredictiveSettleEasing),
                )
            }
            launch { backdropIntensity.animateTo(0f, tween(duration, easing = PredictiveSettleEasing)) }
            launch { editorLayerDepth.animateTo(0f, tween(duration, easing = PredictiveSettleEasing)) }
        }
        closeCustomization()
        delay(PREDICTIVE_DISMISS_DURATION.toLong())
        predictiveProgress.snapTo(0f)
        predictiveBackActive = false
        predictiveCommitting = false
    }

    PredictiveBackHandler(enabled = customizationVisible) { events ->
        try {
            events.collect { event ->
                predictiveBackActive = true
                predictiveProgress.snapTo(event.progress)
                val smoothProgress = smootherStep(event.progress)
                backdropIntensity.snapTo(predictiveEffectIntensity(smoothProgress))
                editorLayerDepth.snapTo(1f - smoothProgress)
            }
            finishPredictiveBack()
        } catch (_: CancellationException) {
            if (!predictiveCommitting) {
                coroutineScope {
                    launch {
                        predictiveProgress.animateTo(
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
                        editorLayerDepth.animateTo(
                            1f,
                            tween(PREDICTIVE_CANCEL_DURATION, easing = FastOutSlowInEasing),
                        )
                    }
                }
                predictiveBackActive = false
            }
        }
    }

    BarBlurHost(
        enabled = blurBarsEnabled,
        captureForEffects = customizationVisible || predictiveBackActive ||
            backdropIntensity.value > EFFECT_VISIBILITY_THRESHOLD,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val depth = editorLayerDepth.value.coerceIn(0f, 1f)
                            scaleX = 1f - depth * BACKGROUND_SCALE_REDUCTION
                            scaleY = scaleX
                            translationX = -size.width * depth * BACKGROUND_PARALLAX
                        },
                ) {
                    ChannelSettingsFormPage(
                        title = channel.name,
                        state = formState,
                        defaults = defaults,
                        mode = ChannelSettingsFormMode.Single,
                        onStateChange = ::updateForm,
                        onBack = onBack,
                        onOpenCustomization = {
                            customizationTarget = it
                            customizationVisible = true
                        },
                    )
                }
            }
            PredictiveBackBackdrop(
                intensity = backdropIntensity.value,
                visible = backdropIntensity.value > EFFECT_VISIBILITY_THRESHOLD,
                modifier = Modifier.fillMaxSize(),
            )
            AnimatedVisibility(
                visible = customizationVisible,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = predictiveProgress.value.coerceAtLeast(0f)
                        translationX = size.width * progress *
                            predictiveTranslationFraction(predictiveBackMaxTranslation.value)
                    },
                enter = slideInHorizontally(
                    tween(LAYER_ENTER_DURATION, easing = FastOutSlowInEasing),
                ) { it },
                exit = if (predictiveBackActive) {
                    ExitTransition.None
                } else {
                    slideOutHorizontally(
                        tween(LAYER_EXIT_DURATION, easing = FastOutSlowInEasing),
                    ) { it }
                },
            ) {
                val target = customizationTarget
                if (target != null) {
                    ChannelCustomizationPage(
                        target = target,
                        template = settings.template,
                        renderer = settings.renderer,
                        rawConfig = when (target) {
                            ChannelCustomizationTarget.Island -> settings.islandCustom
                            ChannelCustomizationTarget.Focus -> settings.focusCustom
                            ChannelCustomizationTarget.Aod -> settings.aodCustom
                        },
                        onSave = { raw ->
                            updateCustom(target, raw)
                            closeCustomization()
                        },
                        onBack = ::closeCustomization,
                    )
                }
            }
        }
    }
}
