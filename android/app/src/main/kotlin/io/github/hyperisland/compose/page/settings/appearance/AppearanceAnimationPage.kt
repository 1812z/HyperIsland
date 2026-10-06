package io.github.hyperisland.compose.page.settings.appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import io.github.hyperisland.compose.component.PreferenceSlider
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun AppearanceAnimationPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val enabled = rememberBooleanPreference(prefs, Keys.ENABLED, false)
    val type = rememberStringPreference(prefs, Keys.TYPE, "system")
    val rebound = rememberBooleanPreference(prefs, Keys.REBOUND, true)
    val pressTilt = rememberBooleanPreference(prefs, Keys.PRESS_TILT, false)
    val returnOvershoot = rememberBooleanPreference(prefs, Keys.RETURN_OVERSHOOT, Keys.DEFAULT_RETURN_OVERSHOOT)
    val showOvershootDialog = remember { mutableStateOf(false) }
    val dampingDraft = remember { mutableStateOf(Keys.DEFAULT_OVERSHOOT_DAMPING.toFloat()) }
    val durationDraft = remember { mutableStateOf(Keys.DEFAULT_OVERSHOOT_DURATION.toFloat()) }
    val gestureFollow = rememberBooleanPreference(prefs, Keys.GESTURE_FOLLOW, false)
    val parabolic = rememberBooleanPreference(prefs, Keys.PARABOLIC, false)
    val throwStrength = rememberStringPreference(prefs, Keys.THROW_STRENGTH, "balanced")
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
                         stringResource(R.string.expand_animation_lively)),
                    selectedIndex = if (type.value == "lively") 1 else 0,
                ) {
                    type.value = if (it == 1) "lively" else "system"
                    prefs.putString(Keys.TYPE, type.value)
                }
                AnimatedVisibility(type.value == "lively") {
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
                        PreferenceSwitch(stringResource(R.string.expand_animation_press_tilt),
                            null, null, pressTilt.value) {
                            pressTilt.value = it
                            prefs.putBoolean(Keys.PRESS_TILT, it)
                        }
                        PreferenceSwitch(stringResource(R.string.expand_animation_gesture_follow),
                            stringResource(R.string.expand_animation_gesture_follow_summary), null, gestureFollow.value) {
                            gestureFollow.value = it
                            prefs.putBoolean(Keys.GESTURE_FOLLOW, it)
                        }
                        AnimatedVisibility(gestureFollow.value) {
                            Column {
                                PreferenceSwitch(stringResource(R.string.expand_animation_parabolic),
                                    null, null, parabolic.value) {
                                    parabolic.value = it
                                    prefs.putBoolean(Keys.PARABOLIC, it)
                                }
                                 AnimatedVisibility(parabolic.value) {
                                    Column {
                                    val strengths = listOf("gentle", "balanced", "strong", "powerful", "maximum")
                                    PreferenceDropdown(
                                        title = stringResource(R.string.expand_animation_throw_strength),
                                        summary = null, icon = null,
                                        items = listOf(stringResource(R.string.expand_throw_gentle),
                                            stringResource(R.string.expand_throw_balanced),
                                            stringResource(R.string.expand_throw_strong),
                                            stringResource(R.string.expand_throw_powerful),
                                            stringResource(R.string.expand_throw_maximum)),
                                        selectedIndex = strengths.indexOf(throwStrength.value).coerceAtLeast(0),
                                    ) {
                                        throwStrength.value = strengths[it]
                                         prefs.putString(Keys.THROW_STRENGTH, throwStrength.value)
                                     }
                                    PreferenceSwitch(stringResource(R.string.expand_animation_return_overshoot),
                                        stringResource(R.string.expand_animation_return_overshoot_summary),
                                        null, returnOvershoot.value) {
                                        returnOvershoot.value = it
                                        prefs.putBoolean(Keys.RETURN_OVERSHOOT, it)
                                    }
                                    AnimatedVisibility(returnOvershoot.value) {
                                        BasicComponent(title = stringResource(R.string.expand_overshoot_custom),
                                            endActions = {
                                                Icon(MiuixIcons.Basic.ArrowRight, contentDescription = null,
                                                    modifier = Modifier.size(width = 10.dp, height = 16.dp),
                                                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions)
                                            },
                                            onClick = {
                                                dampingDraft.value = prefs.getLong(Keys.OVERSHOOT_DAMPING,
                                                    Keys.DEFAULT_OVERSHOOT_DAMPING).coerceIn(35, 120).toFloat()
                                                durationDraft.value = prefs.getLong(Keys.OVERSHOOT_DURATION,
                                                    Keys.DEFAULT_OVERSHOOT_DURATION).coerceIn(200, 800).toFloat()
                                                showOvershootDialog.value = true
                                            })
                                    }
                                    }
                                 }
                            }
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
    WindowDialog(show = showOvershootDialog.value,
        title = stringResource(R.string.expand_overshoot_custom),
        onDismissRequest = { showOvershootDialog.value = false }) {
        Column {
            PreferenceSlider(title = stringResource(R.string.expand_overshoot_damping), icon = null,
                value = dampingDraft.value, valueText = "${dampingDraft.value.toInt() / 100f}",
                valueRange = 35f..120f, steps = 84,
                resetVisible = dampingDraft.value.toLong() != Keys.DEFAULT_OVERSHOOT_DAMPING,
                onReset = { dampingDraft.value = Keys.DEFAULT_OVERSHOOT_DAMPING.toFloat() },
                onValueChange = { dampingDraft.value = it.toInt().toFloat() }, onValueChangeFinished = {})
            PreferenceSlider(title = stringResource(R.string.expand_overshoot_duration), icon = null,
                value = durationDraft.value, valueText = "${durationDraft.value.toInt()} ms",
                valueRange = 200f..800f, steps = 59,
                resetVisible = durationDraft.value.toLong() != Keys.DEFAULT_OVERSHOOT_DURATION,
                onReset = { durationDraft.value = Keys.DEFAULT_OVERSHOOT_DURATION.toFloat() },
                onValueChange = { durationDraft.value = (it.toInt() / 10 * 10).toFloat() }, onValueChangeFinished = {})
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = stringResource(R.string.cancel),
                    onClick = { showOvershootDialog.value = false }, modifier = Modifier.weight(1f))
                Button(onClick = {
                    prefs.putLong(Keys.OVERSHOOT_DAMPING, dampingDraft.value.toLong())
                    prefs.putLong(Keys.OVERSHOOT_DURATION, durationDraft.value.toLong())
                    showOvershootDialog.value = false
                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary()) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}
