package io.github.hyperisland.compose.page.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.theme.PREF_THEME_MODE
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.RadioButtonPreference

@Composable
internal fun ThemeSettingsPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    var selectedMode by remember { mutableStateOf(prefs.getString(PREF_THEME_MODE, "system")) }
    DetailPage(title = stringResource(R.string.compose_theme), onBack = onBack) {
        item {
            SectionTitle(stringResource(R.string.compose_color_mode))
            Card(modifier = Modifier.fillMaxWidth()) {
                ThemeModeItem("system", R.string.compose_follow_system, selectedMode) {
                    selectedMode = it
                    prefs.putString(PREF_THEME_MODE, it)
                }
                ThemeModeItem("light", R.string.compose_light, selectedMode) {
                    selectedMode = it
                    prefs.putString(PREF_THEME_MODE, it)
                }
                ThemeModeItem("dark", R.string.compose_dark, selectedMode) {
                    selectedMode = it
                    prefs.putString(PREF_THEME_MODE, it)
                }
            }
        }
    }
}

@Composable
private fun ThemeModeItem(
    mode: String,
    @StringRes title: Int,
    selectedMode: String,
    onSelect: (String) -> Unit,
) {
    RadioButtonPreference(
        title = stringResource(title),
        selected = selectedMode == mode,
        onClick = { onSelect(mode) },
    )
}
