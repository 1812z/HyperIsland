package io.github.hyperisland.compose.page.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import io.github.hyperisland.compose.component.AppActionTextButton
import io.github.hyperisland.compose.component.AppActionButton
import io.github.hyperisland.compose.component.AppCard
import io.github.hyperisland.compose.component.AppDialog
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.PageTabRow
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.PreferenceSlider
import io.github.hyperisland.compose.component.SettingsActionWithArrow
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.data.rememberLongPreference
import io.github.hyperisland.compose.data.rememberStringPreference
import io.github.hyperisland.compose.theme.DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT
import io.github.hyperisland.compose.theme.LocalGlassEffectsEnabled
import io.github.hyperisland.compose.theme.NavigationBarStyle
import io.github.hyperisland.compose.theme.PREF_NAVIGATION_BAR_STYLE
import io.github.hyperisland.compose.theme.PREF_GLASS_EFFECTS_ENABLED
import io.github.hyperisland.compose.theme.rememberNavigationBarStyle
import io.github.hyperisland.compose.theme.PREF_MONET_ENABLED
import io.github.hyperisland.compose.theme.PREF_THEME_MODE
import io.github.hyperisland.compose.theme.PREF_THEME_SEED_COLOR
import io.github.hyperisland.compose.theme.PREF_PREDICTIVE_BACK_MAX_TRANSLATION
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune

@Composable
internal fun ThemeSettingsPage(
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
    onOpenBlurSettings: () -> Unit,
    onOpenWallpaper: () -> Unit,
) {
    val themeModes = remember { listOf("system", "light", "dark") }
    val selectedMode = rememberStringPreference(prefs, PREF_THEME_MODE, "system")
    val monetEnabled = rememberBooleanPreference(prefs, PREF_MONET_ENABLED, false)
    val themeColor = rememberLongPreference(prefs, PREF_THEME_SEED_COLOR, DEFAULT_THEME_COLOR)
    val navigationBarStyle = rememberNavigationBarStyle(prefs)
    val glassEffectsEnabled = LocalGlassEffectsEnabled.current
    val predictiveBackMaxTranslation = rememberLongPreference(
        prefs,
        PREF_PREDICTIVE_BACK_MAX_TRANSLATION,
        DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT,
    )
    var showColorDialog by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(Color.Red) }

    DetailPage(
        title = stringResource(R.string.theme),
        onBack = onBack,
        topBarContent = {
            PageTabRow(
                tabs = listOf(
                    stringResource(R.string.follow_system),
                    stringResource(R.string.light),
                    stringResource(R.string.dark),
                ),
                selectedIndex = themeModes.indexOf(selectedMode.value).coerceAtLeast(0),
                onSelect = { index ->
                    val mode = themeModes[index]
                    selectedMode.value = mode
                    prefs.putString(PREF_THEME_MODE, mode)
                },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        },
    ) {
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.monet_color),
                    summary = stringResource(R.string.monet_color_summary),
                    icon = MiuixIcons.Theme,
                    checked = monetEnabled.value,
                ) { enabled ->
                    monetEnabled.value = enabled
                    prefs.putBoolean(PREF_MONET_ENABLED, enabled)
                }
                AnimatedVisibility(visible = monetEnabled.value) {
                    SettingsActionWithArrow(
                        title = stringResource(R.string.theme_color),
                        icon = MiuixIcons.Tune,
                        summary = stringResource(R.string.theme_color_summary),
                    ) {
                        selectedColor = Color(themeColor.value.toInt())
                        showColorDialog = true
                    }
                }
            }
        }
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                PreferenceDropdown(
                    title = stringResource(R.string.navigation_bar_style),
                    summary = null,
                    icon = MiuixIcons.Sidebar,
                    items = listOf(
                        stringResource(R.string.default_option),
                        stringResource(R.string.navigation_bar_style_os3),
                        stringResource(R.string.navigation_bar_style_os4),
                        stringResource(R.string.navigation_bar_style_liquid_glass),
                    ),
                    selectedIndex = navigationBarStyle.ordinal,
                ) { index ->
                    prefs.putString(PREF_NAVIGATION_BAR_STYLE, NavigationBarStyle.entries[index].storedValue)
                }
                PreferenceSwitch(
                    title = stringResource(R.string.glass_effects_master_switch),
                    summary = null,
                    icon = MiuixIcons.Layers,
                    checked = glassEffectsEnabled,
                ) { enabled ->
                    prefs.putBoolean(PREF_GLASS_EFFECTS_ENABLED, enabled)
                }
                AnimatedVisibility(
                    visible = glassEffectsEnabled,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    SettingsActionWithArrow(
                        title = stringResource(R.string.blur_effect_settings),
                        icon = MiuixIcons.Tune,
                        onClick = onOpenBlurSettings,
                    )
                }
                PreferenceSlider(
                    value = predictiveBackMaxTranslation.value.toFloat(),
                    onValueChange = { value ->
                        predictiveBackMaxTranslation.value = value.roundToInt().toLong()
                    },
                    title = stringResource(R.string.predictive_back_distance),
                    summary = stringResource(R.string.predictive_back_distance_summary),
                    icon = MiuixIcons.Back,
                    valueText = "${predictiveBackMaxTranslation.value}%",
                    valueRange = 0f..100f,
                    steps = 19,
                    resetVisible = predictiveBackMaxTranslation.value != DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT,
                    onReset = {
                        predictiveBackMaxTranslation.value = DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT
                        prefs.remove(PREF_PREDICTIVE_BACK_MAX_TRANSLATION)
                    },
                    onValueChangeFinished = {
                        prefs.putLong(
                            PREF_PREDICTIVE_BACK_MAX_TRANSLATION,
                            predictiveBackMaxTranslation.value,
                        )
                    },
                    showKeyPoints = true,
                    keyPoints = listOf(0f, 25f, 50f, 75f, 100f),
                )
            }
        }
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                SettingsActionWithArrow(
                    title = stringResource(R.string.wallpaper),
                    icon = MiuixIcons.Image,
                    onClick = onOpenWallpaper,
                )
            }
        }
    }

    AppDialog(
        show = showColorDialog,
        title = stringResource(R.string.theme_color),
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
                AppActionTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showColorDialog = false },
                    modifier = Modifier.weight(1f),
                )
                AppActionButton(
                    onClick = {
                        val argb = selectedColor.toArgb().toLong() and 0xFFFFFFFFL
                        themeColor.value = argb
                        prefs.putLong(PREF_THEME_SEED_COLOR, argb)
                        showColorDialog = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

private const val DEFAULT_THEME_COLOR = 0xFF6750A4L
