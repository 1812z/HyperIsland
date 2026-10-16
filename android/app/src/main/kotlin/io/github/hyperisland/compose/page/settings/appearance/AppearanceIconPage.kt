package io.github.hyperisland.compose.page.settings.appearance

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.data.rememberStringPreference
import top.yukonga.miuix.kmp.basic.Card

@Composable
internal fun AppearanceIconPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val roundIcon = rememberBooleanPreference(prefs, KEY_ROUND_ICON, true)
    AppearanceDetailPage(title = stringResource(R.string.appearance_icon), onBack = onBack) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                LongPreferenceSlider(prefs, KEY_ICON_SIZE, R.string.icon_size, 50, 150, 100, unit = SliderUnit.Percent)
                LongPreferenceSlider(prefs, KEY_ROUND_RADIUS, R.string.round_icon_radius, 0, 100, 40, unit = SliderUnit.Percent)
                PreferenceSwitch(
                    title = stringResource(R.string.round_icon),
                    summary = stringResource(R.string.round_icon_summary),
                    icon = null,
                    checked = roundIcon.value,
                ) {
                    roundIcon.value = it
                    if (it) prefs.remove(KEY_ROUND_ICON) else prefs.putBoolean(KEY_ROUND_ICON, false)
                }
                DoublePreferenceSlider(prefs, KEY_ICON_PADDING, R.string.icon_padding, 0.0, 10.0, 8.0, decimals = 1)
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                val iconColor = rememberStringPreference(prefs, KEY_ICON_COLOR, "default")
                val values = listOf("default", "black", "follow_status_bar", "invert_status_bar")
                PreferenceDropdown(
                    title = stringResource(R.string.icon_color),
                    summary = stringResource(R.string.icon_color_summary),
                    icon = null,
                    items = listOf(
                        stringResource(R.string.default_option),
                        stringResource(R.string.black),
                        stringResource(R.string.follow_status_bar),
                        stringResource(R.string.invert_status_bar),
                    ),
                    selectedIndex = values.indexOf(iconColor.value).coerceAtLeast(0),
                ) { index ->
                    val value = values[index]
                    iconColor.value = value
                    if (value == "default") prefs.remove(KEY_ICON_COLOR)
                    else prefs.putString(KEY_ICON_COLOR, value)
                }
            }
        }
    }
}
