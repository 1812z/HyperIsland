package io.github.hyperisland.compose.page.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SettingsActionWithArrow
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.data.rememberLongPreference
import io.github.hyperisland.compose.data.rememberStringPreference
import io.github.hyperisland.compose.theme.PREF_BLUR_BARS
import io.github.hyperisland.compose.theme.PREF_FLOATING_NAVIGATION_BAR
import io.github.hyperisland.compose.theme.PREF_MONET_ENABLED
import io.github.hyperisland.compose.theme.PREF_THEME_MODE
import io.github.hyperisland.compose.theme.PREF_THEME_SEED_COLOR
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun ThemeSettingsPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val themeModes = remember { listOf("system", "light", "dark") }
    val selectedMode = rememberStringPreference(prefs, PREF_THEME_MODE, "system")
    val monetEnabled = rememberBooleanPreference(prefs, PREF_MONET_ENABLED, false)
    val themeColor = rememberLongPreference(prefs, PREF_THEME_SEED_COLOR, DEFAULT_THEME_COLOR)
    val floatingNavigationBar = rememberBooleanPreference(prefs, PREF_FLOATING_NAVIGATION_BAR, false)
    val blurBars = rememberBooleanPreference(prefs, PREF_BLUR_BARS, false)
    var showColorDialog by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(Color.Red) }

    DetailPage(title = stringResource(R.string.compose_theme), onBack = onBack) {
        item {
            TabRow(
                tabs = listOf(
                    stringResource(R.string.compose_follow_system),
                    stringResource(R.string.compose_light),
                    stringResource(R.string.compose_dark),
                ),
                selectedTabIndex = themeModes.indexOf(selectedMode.value).coerceAtLeast(0),
                onTabSelected = { index ->
                    val mode = themeModes[index]
                    selectedMode.value = mode
                    prefs.putString(PREF_THEME_MODE, mode)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.compose_monet_color),
                    summary = stringResource(R.string.compose_monet_color_summary),
                    icon = MiuixIcons.Theme,
                    checked = monetEnabled.value,
                ) { enabled ->
                    monetEnabled.value = enabled
                    prefs.putBoolean(PREF_MONET_ENABLED, enabled)
                }
                AnimatedVisibility(visible = monetEnabled.value) {
                    SettingsActionWithArrow(
                        title = stringResource(R.string.compose_theme_color),
                        icon = MiuixIcons.Tune,
                        summary = stringResource(R.string.compose_theme_color_summary),
                    ) {
                        selectedColor = Color(themeColor.value.toInt())
                        showColorDialog = true
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.compose_floating_navigation_bar),
                    summary = stringResource(R.string.compose_floating_navigation_bar_summary),
                    icon = MiuixIcons.Sidebar,
                    checked = floatingNavigationBar.value,
                ) { enabled ->
                    floatingNavigationBar.value = enabled
                    prefs.putBoolean(PREF_FLOATING_NAVIGATION_BAR, enabled)
                }
                PreferenceSwitch(
                    title = stringResource(R.string.compose_interface_blur),
                    summary = stringResource(R.string.compose_interface_blur_summary),
                    icon = MiuixIcons.Layers,
                    checked = blurBars.value,
                ) { enabled ->
                    blurBars.value = enabled
                    prefs.putBoolean(PREF_BLUR_BARS, enabled)
                }
            }
        }
    }

    WindowDialog(
        show = showColorDialog,
        title = stringResource(R.string.compose_theme_color),
        onDismissRequest = { showColorDialog = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ColorPalette(
                color = selectedColor,
                onColorChanged = { newColor -> selectedColor = newColor },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.compose_cancel),
                    onClick = { showColorDialog = false },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val argb = selectedColor.toArgb().toLong() and 0xFFFFFFFFL
                        themeColor.value = argb
                        prefs.putLong(PREF_THEME_SEED_COLOR, argb)
                        showColorDialog = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.compose_save))
                }
            }
        }
    }
}

private const val DEFAULT_THEME_COLOR = 0xFF6750A4L
