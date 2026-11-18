package io.github.hyperisland.compose.page.settings.appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.PreferenceSlider
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.utils.SoftGlassSceneBrightnessConfig
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card

@Composable
internal fun SoftGlassSceneBrightnessPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    var settings by remember(prefs) { mutableStateOf(prefs.getSoftGlassSceneBrightness()) }
    var draft by remember(settings.whiteSceneBrightness) {
        mutableFloatStateOf(settings.whiteSceneBrightness.toFloat())
    }
    DisposableEffect(prefs) {
        val unregister = prefs.addChangeListener { key ->
            if (key == SoftGlassSceneBrightnessConfig.ENABLED_KEY || key == SoftGlassSceneBrightnessConfig.BRIGHTNESS_KEY) {
                settings = prefs.getSoftGlassSceneBrightness()
            }
        }
        onDispose(unregister)
    }
    AppearanceDetailPage(title = stringResource(R.string.soft_glass_scene_brightness), onBack = onBack) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.soft_glass_scene_dimming),
                    summary = null,
                    icon = null,
                    checked = settings.enabled,
                ) {
                    settings = settings.copy(enabled = it)
                    prefs.setSoftGlassSceneDimmingEnabled(it)
                }
                AnimatedVisibility(
                    visible = settings.enabled,
                    enter = expandVertically(tween(260), expandFrom = Alignment.Top) +
                        slideInVertically(tween(260)) { -it / 3 } + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(220), shrinkTowards = Alignment.Top) +
                        slideOutVertically(tween(220)) { -it / 3 } + fadeOut(tween(150)),
                ) {
                    PreferenceSlider(
                        title = stringResource(R.string.soft_glass_white_scene_edge_brightness),
                        icon = null,
                        value = draft,
                        valueText = draft.roundToInt().toString(),
                        valueRange = SoftGlassSceneBrightnessConfig.MIN_BRIGHTNESS.toFloat()..
                            SoftGlassSceneBrightnessConfig.MAX_BRIGHTNESS.toFloat(),
                        steps = (SoftGlassSceneBrightnessConfig.MAX_BRIGHTNESS - SoftGlassSceneBrightnessConfig.MIN_BRIGHTNESS - 1).toInt(),
                        resetVisible = settings.whiteSceneBrightness != SoftGlassSceneBrightnessConfig.DEFAULT_BRIGHTNESS,
                        onReset = {
                            draft = SoftGlassSceneBrightnessConfig.DEFAULT_BRIGHTNESS.toFloat()
                            settings = settings.copy(whiteSceneBrightness = SoftGlassSceneBrightnessConfig.DEFAULT_BRIGHTNESS)
                            prefs.setSoftGlassWhiteSceneBrightness(SoftGlassSceneBrightnessConfig.DEFAULT_BRIGHTNESS)
                        },
                        onValueChange = { draft = it.roundToInt().toFloat() },
                        onValueChangeFinished = {
                            val value = SoftGlassSceneBrightnessConfig.brightness(draft.roundToInt().toLong())
                            settings = settings.copy(whiteSceneBrightness = value)
                            prefs.setSoftGlassWhiteSceneBrightness(value)
                        },
                    )
                }
            }
        }
    }
}
