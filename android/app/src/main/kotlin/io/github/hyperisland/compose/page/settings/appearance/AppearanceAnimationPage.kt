package io.github.hyperisland.compose.page.settings.appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.data.ExpandedCollapsePreferences as Keys
import top.yukonga.miuix.kmp.basic.Card

@Composable
internal fun AppearanceAnimationPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val enabled = rememberBooleanPreference(prefs, Keys.ENABLED, false)
    AppearanceDetailPage(title = stringResource(R.string.appearance_animation), onBack = onBack) {
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
