package io.github.hyperisland.compose.page.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.ColorPaletteDialog
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.parseHexColor
import io.github.hyperisland.compose.component.toArgbHex
import io.github.hyperisland.compose.data.DefaultConfigSettings
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun DefaultConfigPage(
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
) {
    var settings by remember { mutableStateOf(prefs.defaultConfigSettings()) }
    var timeoutText by remember { mutableStateOf(settings.timeout.toString()) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var activeColorTarget by remember { mutableStateOf<DefaultColorTarget?>(null) }
    var selectedColor by remember { mutableStateOf(Color.Red) }
    val glowModes = remember { listOf(GLOW_ON, GLOW_OFF, GLOW_FOLLOW_DYNAMIC) }
    val glowModeLabels = listOf(
        stringResource(R.string.compose_enabled_option),
        stringResource(R.string.compose_disabled_option),
        stringResource(R.string.compose_follow_dynamic_color),
    )
    val marqueeAutoHideValues = remember { listOf("off", "1", "2", "1_override", "2_override") }
    val marqueeAutoHideLabels = listOf(
        stringResource(R.string.compose_disabled_option),
        stringResource(R.string.compose_marquee_once),
        stringResource(R.string.compose_marquee_twice),
        stringResource(R.string.compose_marquee_once_override),
        stringResource(R.string.compose_marquee_twice_override),
    )

    fun update(value: DefaultConfigSettings) {
        settings = value
        prefs.setDefaultConfigSettings(value)
    }

    DetailPage(
        title = stringResource(R.string.compose_default_config),
        onBack = onBack,
    ) {
        item {
            SectionTitle(stringResource(R.string.compose_behavior))
            Card {
                SwitchPreference(
                    checked = settings.firstFloat,
                    onCheckedChange = { update(settings.copy(firstFloat = it)) },
                    title = stringResource(R.string.compose_first_float),
                    summary = stringResource(R.string.compose_first_float_summary),
                    insideMargin = DEFAULT_ITEM_MARGIN,
                )
                SwitchPreference(
                    checked = settings.aodText,
                    onCheckedChange = { update(settings.copy(aodText = it)) },
                    title = stringResource(R.string.compose_aod_text),
                    summary = stringResource(R.string.compose_aod_text_summary),
                    insideMargin = DEFAULT_ITEM_MARGIN,
                )
                SwitchPreference(
                    checked = settings.enableFloat,
                    onCheckedChange = { update(settings.copy(enableFloat = it)) },
                    title = stringResource(R.string.compose_update_float),
                    summary = stringResource(R.string.compose_update_float_summary),
                    insideMargin = DEFAULT_ITEM_MARGIN,
                )
                SwitchPreference(
                    checked = settings.focusNotification,
                    onCheckedChange = { update(settings.copy(focusNotification = it)) },
                    title = stringResource(R.string.compose_focus_notification),
                    summary = stringResource(R.string.compose_focus_notification_summary),
                    insideMargin = DEFAULT_ITEM_MARGIN,
                )
                SwitchPreference(
                    checked = settings.restoreLockscreen,
                    onCheckedChange = { update(settings.copy(restoreLockscreen = it)) },
                    title = stringResource(R.string.compose_restore_lockscreen),
                    summary = stringResource(R.string.compose_restore_lockscreen_summary),
                    insideMargin = DEFAULT_ITEM_MARGIN,
                )
                ArrowPreference(
                    title = stringResource(R.string.compose_auto_disappear),
                    summary = stringResource(R.string.compose_timeout_seconds_value, settings.timeout),
                    insideMargin = DEFAULT_ITEM_MARGIN,
                    onClick = {
                        timeoutText = settings.timeout.toString()
                        showTimeoutDialog = true
                    },
                )
            }
        }
        item {
            SectionTitle(stringResource(R.string.compose_appearance))
            Card {
                SwitchPreference(
                    checked = settings.marquee,
                    onCheckedChange = { update(settings.copy(marquee = it)) },
                    title = stringResource(R.string.compose_marquee_channel),
                    summary = stringResource(R.string.compose_marquee_channel_summary),
                    insideMargin = DEFAULT_ITEM_MARGIN,
                )
                OverlayDropdownPreference(
                    items = marqueeAutoHideLabels,
                    selectedIndex = marqueeAutoHideValues.indexOf(settings.marqueeAutoHide).coerceAtLeast(0),
                    title = stringResource(R.string.compose_marquee_auto_hide),
                    summary = stringResource(R.string.compose_marquee_auto_hide_summary),
                    enabled = settings.marquee,
                    insideMargin = DEFAULT_ITEM_MARGIN,
                    renderInRootScaffold = false,
                    onSelectedIndexChange = { index ->
                        update(settings.copy(marqueeAutoHide = marqueeAutoHideValues[index]))
                    },
                )
                SwitchPreference(
                    checked = settings.dynamicHighlightColor,
                    onCheckedChange = { update(settings.copy(dynamicHighlightColor = it)) },
                    title = stringResource(R.string.compose_dynamic_highlight_color),
                    summary = stringResource(R.string.compose_dynamic_highlight_color_summary),
                    insideMargin = DEFAULT_ITEM_MARGIN,
                )
                SwitchPreference(
                    checked = settings.showIslandIcon,
                    onCheckedChange = { update(settings.copy(showIslandIcon = it)) },
                    title = stringResource(R.string.compose_island_icon),
                    summary = stringResource(R.string.compose_island_icon_summary),
                    insideMargin = DEFAULT_ITEM_MARGIN,
                )
                SwitchPreference(
                    checked = settings.preserveSmallIcon,
                    onCheckedChange = { update(settings.copy(preserveSmallIcon = it)) },
                    title = stringResource(R.string.compose_preserve_small_icon),
                    summary = stringResource(R.string.compose_preserve_small_icon_summary),
                    insideMargin = DEFAULT_ITEM_MARGIN,
                )
            }
        }
        item {
            SectionTitle(stringResource(R.string.compose_glow))
            Card {
                OverlayDropdownPreference(
                    items = glowModeLabels,
                    selectedIndex = glowModes.indexOf(settings.outerGlow).coerceAtLeast(0),
                    title = stringResource(R.string.compose_focus_outer_glow),
                    insideMargin = DEFAULT_ITEM_MARGIN,
                    renderInRootScaffold = false,
                    onSelectedIndexChange = { index ->
                        val mode = glowModes[index]
                        update(
                            settings.copy(
                                outerGlow = mode,
                                forceOuterGlow = settings.forceOuterGlow && mode != GLOW_OFF,
                            ),
                        )
                    },
                )
                AnimatedVisibility(visible = settings.outerGlow != GLOW_OFF) {
                    SwitchPreference(
                        checked = settings.forceOuterGlow,
                        onCheckedChange = { update(settings.copy(forceOuterGlow = it)) },
                        title = stringResource(R.string.compose_force_outer_glow),
                        summary = stringResource(R.string.compose_force_focus_outer_glow_summary),
                        insideMargin = DEFAULT_ITEM_MARGIN,
                    )
                }
                AnimatedVisibility(visible = settings.outerGlow == GLOW_ON) {
                    ArrowPreference(
                        title = stringResource(R.string.compose_out_effect_color),
                        summary = settings.outEffectColor.takeIf(String::isNotEmpty),
                        insideMargin = DEFAULT_ITEM_MARGIN,
                        onClick = {
                            selectedColor = parseHexColor(settings.outEffectColor)
                            activeColorTarget = DefaultColorTarget.Focus
                        },
                    )
                }
                OverlayDropdownPreference(
                    items = glowModeLabels,
                    selectedIndex = glowModes.indexOf(settings.islandOuterGlow).coerceAtLeast(0),
                    title = stringResource(R.string.compose_island_outer_glow),
                    insideMargin = DEFAULT_ITEM_MARGIN,
                    renderInRootScaffold = false,
                    onSelectedIndexChange = { index ->
                        val mode = glowModes[index]
                        update(
                            settings.copy(
                                islandOuterGlow = mode,
                                forceIslandOuterGlow = settings.forceIslandOuterGlow && mode != GLOW_OFF,
                            ),
                        )
                    },
                )
                AnimatedVisibility(visible = settings.islandOuterGlow != GLOW_OFF) {
                    SwitchPreference(
                        checked = settings.forceIslandOuterGlow,
                        onCheckedChange = { update(settings.copy(forceIslandOuterGlow = it)) },
                        title = stringResource(R.string.compose_force_outer_glow),
                        summary = stringResource(R.string.compose_force_island_outer_glow_summary),
                        insideMargin = DEFAULT_ITEM_MARGIN,
                    )
                }
                AnimatedVisibility(visible = settings.islandOuterGlow == GLOW_ON) {
                    ArrowPreference(
                        title = stringResource(R.string.compose_out_effect_color),
                        summary = settings.islandOuterGlowColor.takeIf(String::isNotEmpty),
                        insideMargin = DEFAULT_ITEM_MARGIN,
                        onClick = {
                            selectedColor = parseHexColor(settings.islandOuterGlowColor)
                            activeColorTarget = DefaultColorTarget.Island
                        },
                    )
                }
            }
        }
    }

    WindowDialog(
        show = showTimeoutDialog,
        title = stringResource(R.string.compose_auto_disappear),
        onDismissRequest = { showTimeoutDialog = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextField(
                value = timeoutText,
                onValueChange = { raw -> timeoutText = raw.filter(Char::isDigit).take(9) },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.compose_seconds),
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.compose_cancel),
                    onClick = { showTimeoutDialog = false },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val timeout = timeoutText.toIntOrNull()?.takeIf { it >= 1 }
                            ?: settings.timeout
                        timeoutText = timeout.toString()
                        update(settings.copy(timeout = timeout))
                        showTimeoutDialog = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text(stringResource(R.string.compose_save)) }
            }
        }
    }

    ColorPaletteDialog(
        show = activeColorTarget != null,
        title = stringResource(R.string.compose_out_effect_color),
        initialColor = selectedColor,
        onDismiss = { activeColorTarget = null },
        onSave = { color ->
            when (activeColorTarget) {
                DefaultColorTarget.Focus -> update(settings.copy(outEffectColor = color.toArgbHex()))
                DefaultColorTarget.Island -> update(settings.copy(islandOuterGlowColor = color.toArgbHex()))
                null -> Unit
            }
            activeColorTarget = null
        },
    )
}

private enum class DefaultColorTarget { Focus, Island }

private val DEFAULT_ITEM_MARGIN = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
private const val GLOW_ON = "on"
private const val GLOW_OFF = "off"
private const val GLOW_FOLLOW_DYNAMIC = "follow_dynamic"
