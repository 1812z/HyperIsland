package io.github.hyperisland.compose.page.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.AppCard
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.PreferenceSlider
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.theme.GlassPreferences
import io.github.hyperisland.compose.theme.LocalCardGlassSettings
import io.github.hyperisland.compose.theme.LocalNavigationBarGlassSettings
import io.github.hyperisland.compose.theme.LocalUiGlassSettings
import io.github.hyperisland.compose.theme.MAX_UI_GLASS_BLUR_RADIUS
import io.github.hyperisland.compose.theme.MAX_UI_GLASS_TRANSPARENCY
import io.github.hyperisland.compose.theme.UiGlassSettings
import kotlin.math.roundToInt

@Composable
internal fun BlurEffectSettingsPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val interfaceGlass = LocalUiGlassSettings.current
    val navigationBarGlass = LocalNavigationBarGlassSettings.current
    val cardGlass = LocalCardGlassSettings.current
    DetailPage(title = stringResource(R.string.blur_effect_settings), onBack = onBack) {
        item {
            SectionTitle(stringResource(R.string.blur_effect_interface_section))
            AppCard(modifier = Modifier.fillMaxWidth()) {
                GlassSettingsControls(
                    prefs,
                    interfaceGlass,
                    GlassPreferences.Interface,
                    title = stringResource(R.string.ui_glass_effect),
                )
            }
        }
        item {
            SectionTitle(stringResource(R.string.blur_effect_navigation_bar_section))
            AppCard(modifier = Modifier.fillMaxWidth()) {
                GlassSettingsControls(
                    prefs,
                    navigationBarGlass,
                    GlassPreferences.NavigationBar,
                    title = stringResource(R.string.navigation_bar_glass_effect),
                )
            }
        }
        item {
            SectionTitle(stringResource(R.string.blur_effect_cards_section))
            AppCard(modifier = Modifier.fillMaxWidth()) {
                GlassSettingsControls(
                    prefs,
                    cardGlass,
                    GlassPreferences.Cards,
                    title = stringResource(R.string.card_glass_effect),
                    summary = stringResource(R.string.card_glass_effect_summary),
                )
            }
        }
    }
}

@Composable
private fun GlassSettingsControls(
    prefs: FlutterPrefsRepository,
    glass: UiGlassSettings,
    preferences: GlassPreferences,
    title: String,
    summary: String? = null,
) {
    PreferenceSwitch(title = title, summary = summary, icon = null, checked = glass.enabled) { enabled ->
        glass.enabled = enabled
        prefs.putBoolean(preferences.enabledKey, enabled)
    }
    AnimatedVisibility(
        visible = glass.enabled,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        Column {
            GlassParameterSliders(prefs, glass, preferences)
        }
    }
}

@Composable
private fun GlassParameterSliders(
    prefs: FlutterPrefsRepository,
    glass: UiGlassSettings,
    preferences: GlassPreferences,
) {
    PreferenceSlider(
        title = stringResource(R.string.ui_glass_transparency),
        icon = null,
        value = glass.transparency.toFloat(),
        valueText = "${glass.transparency}%",
        valueRange = 0f..MAX_UI_GLASS_TRANSPARENCY.toFloat(),
        steps = MAX_UI_GLASS_TRANSPARENCY.toInt() - 1,
        onValueChange = { glass.transparency = it.roundToInt().toLong() },
        onValueChangeFinished = { prefs.putLong(preferences.transparencyKey, glass.transparency) },
    )
    PreferenceSlider(
        title = stringResource(R.string.ui_glass_blur_radius),
        icon = null,
        value = glass.blurRadius.toFloat(),
        valueText = "${glass.blurRadius} dp",
        valueRange = 0f..MAX_UI_GLASS_BLUR_RADIUS.toFloat(),
        steps = MAX_UI_GLASS_BLUR_RADIUS.toInt() - 1,
        onValueChange = { glass.blurRadius = it.roundToInt().toLong() },
        onValueChangeFinished = { prefs.putLong(preferences.blurRadiusKey, glass.blurRadius) },
    )
}
