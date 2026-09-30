package io.github.hyperisland.compose.page.settings.appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.data.rememberStringPreference
import io.github.hyperisland.data.ExpandedCollapsePreferences as Keys
import top.yukonga.miuix.kmp.basic.Card

@Composable
internal fun AppearanceAnimationPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val enabled = rememberBooleanPreference(prefs, Keys.ENABLED, false)
    val type = rememberStringPreference(prefs, Keys.TYPE, "system")
    val rebound = rememberBooleanPreference(prefs, Keys.REBOUND, true)
    val gestureFollow = rememberBooleanPreference(prefs, Keys.GESTURE_FOLLOW, false)
    val curve = rememberStringPreference(prefs, Keys.CURVE, "balanced")
    val keepContentSize = rememberBooleanPreference(prefs, Keys.KEEP_CONTENT_SIZE, false)
    LaunchedEffect(keepContentSize.value, rebound.value) {
        if (keepContentSize.value && rebound.value) {
            rebound.value = false
            prefs.putBoolean(Keys.REBOUND, false)
        }
    }
    AppearanceDetailPage(title = stringResource(R.string.appearance_animation), onBack = onBack) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceDropdown(
                    title = stringResource(R.string.expand_animation_type),
                    summary = stringResource(R.string.expand_animation_type_summary),
                    icon = null,
                    items = listOf(stringResource(R.string.follow_system),
                         stringResource(R.string.expand_animation_lively), stringResource(R.string.expand_animation_ios)),
                    selectedIndex = when (type.value) { "lively" -> 1; "ios" -> 2; else -> 0 },
                ) {
                    type.value = when (it) { 1 -> "lively"; 2 -> "ios"; else -> "system" }
                    prefs.putString(Keys.TYPE, type.value)
                }
                AnimatedVisibility(type.value == "lively" || type.value == "ios") {
                    Column {
                        AnimatedVisibility(!rebound.value) {
                        PreferenceSwitch(stringResource(R.string.expand_animation_keep_content_size),
                            null, null, keepContentSize.value) {
                            keepContentSize.value = it
                            prefs.putBoolean(Keys.KEEP_CONTENT_SIZE, it)
                            if (it) {
                                rebound.value = false
                                prefs.putBoolean(Keys.REBOUND, false)
                            }
                        }
                        }
                        AnimatedVisibility(!keepContentSize.value) {
                        PreferenceSwitch(stringResource(R.string.expand_animation_rebound),
                            null, null, rebound.value) {
                            rebound.value = it
                            prefs.putBoolean(Keys.REBOUND, it)
                            if (it) {
                                keepContentSize.value = false
                                prefs.putBoolean(Keys.KEEP_CONTENT_SIZE, false)
                            }
                        }
                        }
                        PreferenceSwitch(stringResource(R.string.expand_animation_gesture_follow),
                            stringResource(R.string.expand_animation_gesture_follow_summary), null, gestureFollow.value) {
                            gestureFollow.value = it
                            prefs.putBoolean(Keys.GESTURE_FOLLOW, it)
                        }
                        val curves = listOf("balanced", "snappy", "gentle")
                        PreferenceDropdown(
                            title = stringResource(R.string.expand_animation_curve),
                            summary = null,
                            icon = null,
                            items = listOf(stringResource(R.string.expand_curve_balanced),
                                stringResource(R.string.expand_curve_snappy), stringResource(R.string.expand_curve_gentle)),
                            selectedIndex = curves.indexOf(curve.value).coerceAtLeast(0),
                        ) {
                            curve.value = curves[it]
                            prefs.putString(Keys.CURVE, curve.value)
                        }
                    }
                }
                AnimatedVisibility(type.value == "ios") {
                    LongPreferenceSlider(prefs, Keys.IOS_CONTENT_TOP_GAP,
                        R.string.expand_ios_content_top_gap, 0, 20, Keys.DEFAULT_IOS_CONTENT_TOP_GAP,
                        showDefaultAsSystem = false)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    stringResource(R.string.expand_collapse_custom),
                    stringResource(R.string.expand_collapse_custom_summary),
                    null,
                    enabled.value,
                ) {
                    enabled.value = it
                    prefs.putBoolean(Keys.ENABLED, it)
                }
            }
        }
        item {
            AnimatedVisibility(enabled.value) {
                Column {
                    SectionTitle(stringResource(R.string.expand_collapse_transparency))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        LongPreferenceSlider(prefs, Keys.TRANSPARENCY_START, R.string.animation_start_degree,
                            0, 100, Keys.DEFAULT_START_PERCENT, unit = SliderUnit.Percent, showDefaultAsSystem = false)
                        LongPreferenceSlider(prefs, Keys.TRANSPARENCY_END, R.string.animation_end_degree,
                            0, 100, Keys.DEFAULT_END_PERCENT, unit = SliderUnit.Percent, showDefaultAsSystem = false)
                    }
                    SectionTitle(stringResource(R.string.expand_collapse_blur))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        LongPreferenceSlider(prefs, Keys.BLUR_START, R.string.animation_start_degree,
                            0, 100, Keys.DEFAULT_START_PERCENT, unit = SliderUnit.Percent, showDefaultAsSystem = false)
                        LongPreferenceSlider(prefs, Keys.BLUR_END, R.string.animation_end_degree,
                            0, 100, Keys.DEFAULT_END_PERCENT, unit = SliderUnit.Percent, showDefaultAsSystem = false)
                    }
                }
            }
        }
    }
}
