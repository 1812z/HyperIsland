package io.github.hyperisland.compose.page.settings.extensions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.ColorPaletteDialog
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.PreferenceSlider
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsAction
import io.github.hyperisland.compose.component.parseHexColor
import io.github.hyperisland.compose.component.toArgbHex
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.data.rememberLongPreference
import io.github.hyperisland.compose.data.rememberStringPreference
import io.github.hyperisland.compose.service.XposedScopeService
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

@Composable
internal fun MusicIslandSpectrumPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scopeFailed = stringResource(R.string.ext_scope_failed)
    val restartRequired = stringResource(R.string.restart_scope_app)

    val enabled = rememberBooleanPreference(prefs, KEY_MUSIC_ISLAND_SPECTRUM, false)
    val colorMode = rememberStringPreference(prefs, KEY_MUSIC_ISLAND_SPECTRUM_COLOR_MODE, SPECTRUM_COLOR_FOLLOW_COVER)
    val gradientStart = rememberStringPreference(prefs, KEY_MUSIC_ISLAND_SPECTRUM_GRADIENT_START, "")
    val gradientEnd = rememberStringPreference(prefs, KEY_MUSIC_ISLAND_SPECTRUM_GRADIENT_END, "")
    val barCount = rememberLongPreference(prefs, KEY_MUSIC_ISLAND_SPECTRUM_BAR_COUNT, DEFAULT_SPECTRUM_BAR_COUNT)
    val smoothing = rememberStringPreference(prefs, KEY_MUSIC_ISLAND_SPECTRUM_SMOOTHING, SPECTRUM_SMOOTHING_HIGH)

    val showStartColorDialog = remember { mutableStateOf(false) }
    val showEndColorDialog = remember { mutableStateOf(false) }

    val colorModeValues = listOf(SPECTRUM_COLOR_GRADIENT, SPECTRUM_COLOR_FOLLOW_COVER)
    val colorModeLabels = listOf(
        stringResource(R.string.ext_spectrum_color_gradient),
        stringResource(R.string.ext_spectrum_color_follow_cover),
    )
    val smoothingValues = listOf(SPECTRUM_SMOOTHING_LOW, SPECTRUM_SMOOTHING_MEDIUM, SPECTRUM_SMOOTHING_HIGH)
    val smoothingLabels = listOf(
        stringResource(R.string.ext_spectrum_smoothing_low),
        stringResource(R.string.ext_spectrum_smoothing_medium),
        stringResource(R.string.ext_spectrum_smoothing_high),
    )

    fun show(message: String) { scope.launch { snackbar.showSnackbar(message) } }

    HookExtensionScaffold(
        title = stringResource(R.string.ext_spectrum_settings),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
        restartPackages = RESTART_SCOPE_ISLAND,
    ) {
        item {
            SectionTitle(stringResource(R.string.config))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.ext_spectrum_enable),
                    summary = stringResource(R.string.ext_spectrum_enable_summary),
                    icon = null,
                    checked = enabled.value,
                ) { value ->
                    val granted = !value || XposedScopeService.requestScope(
                        context,
                        listOf("com.android.systemui"),
                    ).onFailure { show(it.message ?: scopeFailed) }.isSuccess
                    if (granted) {
                        enabled.value = value
                        prefs.putBoolean(KEY_MUSIC_ISLAND_SPECTRUM, value)
                        show(restartRequired)
                    }
                }
            }
        }

        item {
            AnimatedVisibility(enabled.value) {
                Column {
                    SectionTitle(stringResource(R.string.ext_spectrum_style))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        PreferenceDropdown(
                            title = stringResource(R.string.ext_spectrum_color),
                            summary = null,
                            icon = null,
                            items = colorModeLabels,
                            selectedIndex = colorModeValues.indexOf(colorMode.value).coerceAtLeast(0),
                        ) { index ->
                            colorMode.value = colorModeValues[index]
                            prefs.putString(KEY_MUSIC_ISLAND_SPECTRUM_COLOR_MODE, colorModeValues[index])
                        }

                        AnimatedVisibility(colorMode.value == SPECTRUM_COLOR_GRADIENT) {
                            Column {
                                SettingsAction(
                                    title = stringResource(R.string.ext_spectrum_gradient_start),
                                    summary = gradientStart.value.ifBlank {
                                        stringResource(R.string.ext_spectrum_gradient_start_default)
                                    },
                                    endIcon = MiuixIcons.ChevronForward,
                                ) { showStartColorDialog.value = true }
                                SettingsAction(
                                    title = stringResource(R.string.ext_spectrum_gradient_end),
                                    summary = gradientEnd.value.ifBlank {
                                        stringResource(R.string.ext_spectrum_gradient_end_default)
                                    },
                                    endIcon = MiuixIcons.ChevronForward,
                                ) { showEndColorDialog.value = true }
                            }
                        }

                        PreferenceDropdown(
                            title = stringResource(R.string.ext_spectrum_smoothing),
                            summary = null,
                            icon = null,
                            items = smoothingLabels,
                            selectedIndex = smoothingValues.indexOf(smoothing.value).coerceAtLeast(0),
                        ) { index ->
                            smoothing.value = smoothingValues[index]
                            prefs.putString(KEY_MUSIC_ISLAND_SPECTRUM_SMOOTHING, smoothingValues[index])
                        }

                        PreferenceSlider(
                            title = stringResource(R.string.ext_spectrum_bar_count),
                            icon = null,
                            value = barCount.value.toFloat(),
                            valueText = barCount.value.toString(),
                            valueRange = 4f..8f,
                            steps = 3,
                            resetVisible = barCount.value != DEFAULT_SPECTRUM_BAR_COUNT,
                            onReset = {
                                barCount.value = DEFAULT_SPECTRUM_BAR_COUNT
                                prefs.putLong(KEY_MUSIC_ISLAND_SPECTRUM_BAR_COUNT, DEFAULT_SPECTRUM_BAR_COUNT)
                            },
                            onValueChange = { barCount.value = it.roundToInt().toLong() },
                            onValueChangeFinished = {
                                prefs.putLong(KEY_MUSIC_ISLAND_SPECTRUM_BAR_COUNT, barCount.value)
                            },
                        )
                    }
                }
            }
        }
    }

    ColorPaletteDialog(
        show = showStartColorDialog.value,
        title = stringResource(R.string.ext_spectrum_gradient_start),
        initialColor = parseHexColor(gradientStart.value, Color(0xFF57F2FF)),
        onDismiss = { showStartColorDialog.value = false },
        onDelete = {
            gradientStart.value = ""
            prefs.remove(KEY_MUSIC_ISLAND_SPECTRUM_GRADIENT_START)
            showStartColorDialog.value = false
        },
    ) { color ->
        gradientStart.value = color.toArgbHex()
        prefs.putString(KEY_MUSIC_ISLAND_SPECTRUM_GRADIENT_START, gradientStart.value)
        showStartColorDialog.value = false
    }

    ColorPaletteDialog(
        show = showEndColorDialog.value,
        title = stringResource(R.string.ext_spectrum_gradient_end),
        initialColor = parseHexColor(gradientEnd.value, Color(0xFFF5E6FF)),
        onDismiss = { showEndColorDialog.value = false },
        onDelete = {
            gradientEnd.value = ""
            prefs.remove(KEY_MUSIC_ISLAND_SPECTRUM_GRADIENT_END)
            showEndColorDialog.value = false
        },
    ) { color ->
        gradientEnd.value = color.toArgbHex()
        prefs.putString(KEY_MUSIC_ISLAND_SPECTRUM_GRADIENT_END, gradientEnd.value)
        showEndColorDialog.value = false
    }
}
