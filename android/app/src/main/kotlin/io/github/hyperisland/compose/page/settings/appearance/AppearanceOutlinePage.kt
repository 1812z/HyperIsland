package io.github.hyperisland.compose.page.settings.appearance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.ColorPaletteDialog
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsAction
import io.github.hyperisland.compose.component.parseHexColor
import io.github.hyperisland.compose.component.toArgbHex
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.data.rememberStringPreference
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight

@Composable
internal fun AppearanceOutlinePage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val outlineValues = listOf("system", "show", "hide")
    val outlineLabels = listOf(stringResource(R.string.outline_follow_system),
        stringResource(R.string.outline_force_show), stringResource(R.string.outline_force_hide))
    val islandOutline = rememberStringPreference(prefs, KEY_ISLAND_OUTLINE_MODE, "system")
    val focusOutline = rememberStringPreference(prefs, KEY_FOCUS_OUTLINE_MODE, "system")
    val bigMaterial = rememberStringPreference(prefs, KEY_MATERIAL_BIG, "")
    val expandMaterial = rememberStringPreference(prefs, KEY_MATERIAL_EXPAND, "")
    val expandFollowBig = rememberBooleanPreference(prefs, KEY_MATERIAL_EXPAND_FOLLOW, true)
    val islandOutlineEnabled = !usesSoftGlass(bigMaterial.value)
    val focusOutlineEnabled = !usesSoftGlass(
        if (expandFollowBig.value) bigMaterial.value else expandMaterial.value,
    )
    val glowSingleColor = rememberBooleanPreference(prefs, KEY_GLOW_SINGLE_COLOR, false)
    val glowBaseColor = rememberStringPreference(prefs, KEY_GLOW_BASE_COLOR, "")
    var colorDialog by remember { mutableStateOf(false) }

    AppearanceDetailPage(title = stringResource(R.string.appearance_outline), onBack = onBack) {
        item {
            SectionTitle(stringResource(R.string.outline_control))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceDropdown(stringResource(R.string.island_outline), null, null,
                    outlineLabels, outlineValues.indexOf(islandOutline.value).coerceAtLeast(0),
                    enabled = islandOutlineEnabled) {
                    islandOutline.value = outlineValues[it]
                    prefs.putString(KEY_ISLAND_OUTLINE_MODE, outlineValues[it])
                }
                PreferenceDropdown(stringResource(R.string.focus_outline), null, null,
                    outlineLabels, outlineValues.indexOf(focusOutline.value).coerceAtLeast(0),
                    enabled = focusOutlineEnabled) {
                    focusOutline.value = outlineValues[it]
                    prefs.putString(KEY_FOCUS_OUTLINE_MODE, outlineValues[it])
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.outer_glow))
            Card(modifier = Modifier.fillMaxWidth()) {
                LongPreferenceSlider(prefs, KEY_GLOW_RANGE, R.string.glow_range, 0, 100, 0, unit = SliderUnit.Percent)
                PreferenceSwitch(stringResource(R.string.single_color_glow), null, null, glowSingleColor.value) {
                    glowSingleColor.value = it; prefs.putBoolean(KEY_GLOW_SINGLE_COLOR, it)
                }
                AnimatedVisibility(visible = !glowSingleColor.value) {
                    SettingsAction(
                        title = stringResource(R.string.glow_base_color),
                        summary = glowBaseColor.value.ifBlank { stringResource(R.string.default_option) },
                        endIcon = MiuixIcons.Basic.ArrowRight,
                    ) { colorDialog = true }
                }
            }
        }
    }

    ColorPaletteDialog(
        show = colorDialog,
        title = stringResource(R.string.glow_base_color),
        initialColor = parseHexColor(glowBaseColor.value, Color(0xFF0096FF)),
        onDismiss = { colorDialog = false },
        onDelete = { glowBaseColor.value = ""; prefs.remove(KEY_GLOW_BASE_COLOR); colorDialog = false },
    ) { color ->
        val value = color.toArgbHex()
        glowBaseColor.value = value
        prefs.putString(KEY_GLOW_BASE_COLOR, value)
        colorDialog = false
    }
}
