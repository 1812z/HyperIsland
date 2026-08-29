package io.github.hyperisland.compose.page.apps

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
import io.github.hyperisland.compose.component.KeywordListDialog
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.parseHexColor
import io.github.hyperisland.compose.component.toArgbHex
import io.github.hyperisland.compose.data.DefaultConfigSettings
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.InstalledApp
import io.github.hyperisland.compose.data.ToastAppSettings
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun ToastSettingsPage(
    app: InstalledApp,
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
) {
    val defaults = remember { prefs.defaultConfigSettings() }
    var settings by remember(app.packageName) { mutableStateOf(prefs.toastAppSettings(app.packageName)) }
    var timeoutText by remember(app.packageName) {
        mutableStateOf(settings.timeout.takeUnless { it == TRI_DEFAULT }.orEmpty())
    }
    var showTimeoutDialog by remember(app.packageName) { mutableStateOf(false) }
    var colorTarget by remember { mutableStateOf<ToastColorTarget?>(null) }
    var selectedColor by remember { mutableStateOf(Color.Red) }
    var keywordTarget by remember { mutableStateOf<KeywordTarget?>(null) }

    val triValues = remember { listOf(TRI_DEFAULT, TRI_ON, TRI_OFF) }
    val glowValues = remember { listOf(TRI_DEFAULT, TRI_ON, TRI_OFF, TRI_FOLLOW_DYNAMIC) }
    val marqueeAutoHideValues = remember {
        listOf(TRI_DEFAULT, TRI_OFF, "1", "2", "1_override", "2_override")
    }
    val dynamicValues = remember { listOf(TRI_DEFAULT, TRI_OFF, TRI_ON, "dark", "darker") }
    val filterValues = remember { listOf("blacklist", "whitelist") }

    val firstFloatLabels = triLabels(defaults.firstFloat)
    val enableFloatLabels = triLabels(defaults.enableFloat)
    val preserveIconLabels = triLabels(defaults.preserveSmallIcon)
    val marqueeLabels = triLabels(defaults.marquee)
    val glowLabels = glowLabels(defaults.outerGlow)
    val islandGlowLabels = glowLabels(defaults.islandOuterGlow)
    val dynamicLabels = listOf(
        stringResource(
            if (defaults.dynamicHighlightColor) R.string.default_on else R.string.default_off,
        ),
        stringResource(R.string.disabled_option),
        stringResource(R.string.enabled_option),
        stringResource(R.string.dynamic_dark),
        stringResource(R.string.dynamic_darker),
    )
    val marqueeAutoHideLabels = listOf(
        stringResource(
            R.string.default_value,
            marqueeAutoHideLabel(defaults.marqueeAutoHide),
        ),
        stringResource(R.string.disabled_option),
        stringResource(R.string.marquee_once),
        stringResource(R.string.marquee_twice),
        stringResource(R.string.marquee_once_override),
        stringResource(R.string.marquee_twice_override),
    )
    val filterLabels = listOf(
        stringResource(R.string.filter_blacklist),
        stringResource(R.string.filter_whitelist),
    )
    val marqueeEnabled = resolveBoolean(settings.marquee, defaults.marquee)
    val dynamicHighlightEnabled = resolveDynamic(settings.dynamicHighlightColor, defaults.dynamicHighlightColor)
    val hasHighlightSource = dynamicHighlightEnabled || settings.highlightColor.isNotBlank()
    val filterEnabled = settings.forwardEnabled || settings.blockOriginal

    fun update(value: ToastAppSettings) {
        settings = value
        prefs.setToastAppSettings(app.packageName, value)
    }

    DetailPage(title = app.appName, onBack = onBack) {
        item {
            SectionTitle(stringResource(R.string.toast_adaptation))
            Card {
                SwitchPreference(
                    checked = settings.forwardEnabled,
                    onCheckedChange = { update(settings.copy(forwardEnabled = it)) },
                    title = stringResource(R.string.toast_forward),
                    summary = stringResource(R.string.toast_forward_summary),
                    insideMargin = ITEM_MARGIN,
                )
                SwitchPreference(
                    checked = settings.blockOriginal,
                    onCheckedChange = { update(settings.copy(blockOriginal = it)) },
                    title = stringResource(R.string.toast_block_original),
                    summary = stringResource(R.string.toast_block_original_summary),
                    insideMargin = ITEM_MARGIN,
                )
                AnimatedVisibility(visible = settings.forwardEnabled) {
                    SwitchPreference(
                        checked = settings.showNotification,
                        onCheckedChange = { update(settings.copy(showNotification = it)) },
                        title = stringResource(R.string.toast_show_notification),
                        summary = stringResource(R.string.toast_show_notification_summary),
                        insideMargin = ITEM_MARGIN,
                    )
                }
            }
        }

        item {
            AnimatedVisibility(visible = settings.forwardEnabled) {
                Column {
                    SectionTitle(stringResource(R.string.island))
                    Card {
                        SwitchPreference(
                            checked = settings.showIslandIcon,
                            onCheckedChange = { update(settings.copy(showIslandIcon = it)) },
                            title = stringResource(R.string.island_icon),
                            summary = stringResource(R.string.island_icon_summary),
                            insideMargin = ITEM_MARGIN,
                        )
                        OverlayDropdownPreference(
                            items = firstFloatLabels,
                            selectedIndex = triValues.indexOf(settings.firstFloat).coerceAtLeast(0),
                            title = stringResource(R.string.first_float),
                            summary = stringResource(R.string.first_float_summary),
                            insideMargin = ITEM_MARGIN,
                            renderInRootScaffold = false,
                            onSelectedIndexChange = { update(settings.copy(firstFloat = triValues[it])) },
                        )
                        OverlayDropdownPreference(
                            items = enableFloatLabels,
                            selectedIndex = triValues.indexOf(settings.enableFloat).coerceAtLeast(0),
                            title = stringResource(R.string.update_float),
                            summary = stringResource(R.string.update_float_summary),
                            insideMargin = ITEM_MARGIN,
                            renderInRootScaffold = false,
                            onSelectedIndexChange = { update(settings.copy(enableFloat = triValues[it])) },
                        )
                        OverlayDropdownPreference(
                            items = preserveIconLabels,
                            selectedIndex = triValues.indexOf(settings.preserveSmallIcon).coerceAtLeast(0),
                            title = stringResource(R.string.preserve_small_icon),
                            summary = stringResource(R.string.preserve_small_icon_summary),
                            insideMargin = ITEM_MARGIN,
                            renderInRootScaffold = false,
                            onSelectedIndexChange = { update(settings.copy(preserveSmallIcon = triValues[it])) },
                        )
                        OverlayDropdownPreference(
                            items = marqueeLabels,
                            selectedIndex = triValues.indexOf(settings.marquee).coerceAtLeast(0),
                            title = stringResource(R.string.marquee_channel),
                            summary = stringResource(R.string.marquee_channel_summary),
                            insideMargin = ITEM_MARGIN,
                            renderInRootScaffold = false,
                            onSelectedIndexChange = { update(settings.copy(marquee = triValues[it])) },
                        )
                        AnimatedVisibility(visible = marqueeEnabled) {
                            OverlayDropdownPreference(
                                items = marqueeAutoHideLabels,
                                selectedIndex = marqueeAutoHideValues.indexOf(settings.marqueeAutoHide).coerceAtLeast(0),
                                title = stringResource(R.string.marquee_auto_hide),
                                summary = stringResource(R.string.marquee_auto_hide_summary),
                                insideMargin = ITEM_MARGIN,
                                renderInRootScaffold = false,
                                onSelectedIndexChange = {
                                    update(settings.copy(marqueeAutoHide = marqueeAutoHideValues[it]))
                                },
                            )
                        }
                        ArrowPreference(
                            title = stringResource(R.string.auto_disappear),
                            summary = settings.timeout.toIntOrNull()?.let {
                                stringResource(R.string.timeout_seconds_value, it)
                            } ?: stringResource(R.string.default_timeout_value, defaults.timeout),
                            insideMargin = ITEM_MARGIN,
                            onClick = {
                                timeoutText = settings.timeout.takeUnless { it == TRI_DEFAULT }.orEmpty()
                                showTimeoutDialog = true
                            },
                        )
                    }
                }
            }
        }

        item {
            AnimatedVisibility(visible = settings.forwardEnabled) {
                Column {
                    SectionTitle(stringResource(R.string.highlight_color))
                    Card {
                        OverlayDropdownPreference(
                            items = dynamicLabels,
                            selectedIndex = dynamicValues.indexOf(settings.dynamicHighlightColor).coerceAtLeast(0),
                            title = stringResource(R.string.dynamic_highlight_color),
                            summary = stringResource(R.string.dynamic_highlight_color_summary),
                            insideMargin = ITEM_MARGIN,
                            renderInRootScaffold = false,
                            onSelectedIndexChange = {
                                update(settings.copy(dynamicHighlightColor = dynamicValues[it]))
                            },
                        )
                        AnimatedVisibility(visible = !dynamicHighlightEnabled) {
                            ArrowPreference(
                                title = stringResource(R.string.highlight_color),
                                summary = settings.highlightColor.takeIf(String::isNotEmpty),
                                insideMargin = ITEM_MARGIN,
                                onClick = {
                                    selectedColor = parseHexColor(settings.highlightColor)
                                    colorTarget = ToastColorTarget.Highlight
                                },
                            )
                        }
                        AnimatedVisibility(visible = hasHighlightSource) {
                            Column {
                                SwitchPreference(
                                    checked = settings.showLeftHighlight == TRI_ON,
                                    onCheckedChange = {
                                        update(settings.copy(showLeftHighlight = if (it) TRI_ON else TRI_OFF))
                                    },
                                    title = stringResource(R.string.left_side),
                                    insideMargin = ITEM_MARGIN,
                                )
                                SwitchPreference(
                                    checked = settings.showRightHighlight == TRI_ON,
                                    onCheckedChange = {
                                        update(settings.copy(showRightHighlight = if (it) TRI_ON else TRI_OFF))
                                    },
                                    title = stringResource(R.string.right_side),
                                    insideMargin = ITEM_MARGIN,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            AnimatedVisibility(visible = settings.forwardEnabled) {
                Column {
                    SectionTitle(stringResource(R.string.glow))
                    Card {
                        OverlayDropdownPreference(
                            items = glowLabels,
                            selectedIndex = glowValues.indexOf(settings.outerGlow).coerceAtLeast(0),
                            title = stringResource(R.string.focus_notification),
                            insideMargin = ITEM_MARGIN,
                            renderInRootScaffold = false,
                            onSelectedIndexChange = { update(settings.copy(outerGlow = glowValues[it])) },
                        )
                        AnimatedVisibility(visible = settings.outerGlow == TRI_ON) {
                            ArrowPreference(
                                title = stringResource(R.string.out_effect_color),
                                summary = settings.outEffectColor.takeIf(String::isNotEmpty),
                                insideMargin = ITEM_MARGIN,
                                onClick = {
                                    selectedColor = parseHexColor(settings.outEffectColor)
                                    colorTarget = ToastColorTarget.FocusGlow
                                },
                            )
                        }
                        OverlayDropdownPreference(
                            items = islandGlowLabels,
                            selectedIndex = glowValues.indexOf(settings.islandOuterGlow).coerceAtLeast(0),
                            title = stringResource(R.string.island_outer_glow),
                            insideMargin = ITEM_MARGIN,
                            renderInRootScaffold = false,
                            onSelectedIndexChange = {
                                update(settings.copy(islandOuterGlow = glowValues[it]))
                            },
                        )
                        AnimatedVisibility(visible = settings.islandOuterGlow == TRI_ON) {
                            ArrowPreference(
                                title = stringResource(R.string.out_effect_color),
                                summary = settings.islandOuterGlowColor.takeIf(String::isNotEmpty),
                                insideMargin = ITEM_MARGIN,
                                onClick = {
                                    selectedColor = parseHexColor(settings.islandOuterGlowColor)
                                    colorTarget = ToastColorTarget.IslandGlow
                                },
                            )
                        }
                    }
                }
            }
        }

        item {
            AnimatedVisibility(visible = filterEnabled) {
                Column {
                    SectionTitle(stringResource(R.string.filter_rules))
                    Card {
                        OverlayDropdownPreference(
                            items = filterLabels,
                            selectedIndex = filterValues.indexOf(settings.filterMode).coerceAtLeast(0),
                            title = stringResource(R.string.filter_mode),
                            summary = stringResource(
                                if (settings.filterMode == "whitelist") {
                                    R.string.filter_whitelist_summary
                                } else {
                                    R.string.filter_blacklist_summary
                                },
                            ),
                            insideMargin = ITEM_MARGIN,
                            renderInRootScaffold = false,
                            onSelectedIndexChange = { update(settings.copy(filterMode = filterValues[it])) },
                        )
                        AnimatedVisibility(visible = settings.filterMode == "whitelist") {
                            ArrowPreference(
                                title = stringResource(R.string.whitelist_keywords),
                                summary = settings.whitelistKeywords.joinToString("、").takeIf(String::isNotEmpty),
                                insideMargin = ITEM_MARGIN,
                                onClick = { keywordTarget = KeywordTarget.Whitelist },
                            )
                        }
                        ArrowPreference(
                            title = stringResource(R.string.blacklist_keywords),
                            summary = settings.blacklistKeywords.joinToString("、").takeIf(String::isNotEmpty),
                            insideMargin = ITEM_MARGIN,
                            onClick = { keywordTarget = KeywordTarget.Blacklist },
                        )
                    }
                    AnimatedVisibility(visible = settings.filterMode == "whitelist") {
                        Card {
                            BasicComponent(
                                title = stringResource(R.string.keyword_filter_priority),
                                insideMargin = ITEM_MARGIN,
                            )
                        }
                    }
                }
            }
        }
    }

    WindowDialog(
        show = showTimeoutDialog,
        title = stringResource(R.string.auto_disappear),
        onDismissRequest = { showTimeoutDialog = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextField(
                value = timeoutText,
                onValueChange = { raw -> timeoutText = raw.filter(Char::isDigit).take(2) },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.seconds),
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.reset_default),
                    onClick = {
                        timeoutText = ""
                        update(settings.copy(timeout = TRI_DEFAULT))
                        showTimeoutDialog = false
                    },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val timeout = timeoutText.toIntOrNull()
                            ?.takeIf { it in 1..20 }
                            ?.toString()
                            ?: TRI_DEFAULT
                        timeoutText = timeout.takeUnless { it == TRI_DEFAULT }.orEmpty()
                        update(settings.copy(timeout = timeout))
                        showTimeoutDialog = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }

    ColorPaletteDialog(
        show = colorTarget != null,
        title = stringResource(
            if (colorTarget == ToastColorTarget.Highlight) {
                R.string.highlight_color
            } else {
                R.string.out_effect_color
            },
        ),
        initialColor = selectedColor,
        onDismiss = { colorTarget = null },
        onDelete = {
            when (colorTarget) {
                ToastColorTarget.Highlight -> update(settings.copy(highlightColor = ""))
                ToastColorTarget.FocusGlow -> update(settings.copy(outEffectColor = ""))
                ToastColorTarget.IslandGlow -> update(settings.copy(islandOuterGlowColor = ""))
                null -> Unit
            }
            colorTarget = null
        },
        onSave = { color ->
            val hex = color.toArgbHex()
            when (colorTarget) {
                ToastColorTarget.Highlight -> update(settings.copy(highlightColor = hex))
                ToastColorTarget.FocusGlow -> update(settings.copy(outEffectColor = hex))
                ToastColorTarget.IslandGlow -> update(settings.copy(islandOuterGlowColor = hex))
                null -> Unit
            }
            colorTarget = null
        },
    )

    val activeKeywords = when (keywordTarget) {
        KeywordTarget.Whitelist -> settings.whitelistKeywords
        KeywordTarget.Blacklist -> settings.blacklistKeywords
        null -> emptyList()
    }
    KeywordListDialog(
        show = keywordTarget != null,
        title = stringResource(
            if (keywordTarget == KeywordTarget.Whitelist) {
                R.string.whitelist_keywords
            } else {
                R.string.blacklist_keywords
            },
        ),
        keywords = activeKeywords,
        onDismiss = { keywordTarget = null },
        onSave = { keywords ->
            when (keywordTarget) {
                KeywordTarget.Whitelist -> update(settings.copy(whitelistKeywords = keywords))
                KeywordTarget.Blacklist -> update(settings.copy(blacklistKeywords = keywords))
                null -> Unit
            }
            keywordTarget = null
        },
    )
}

@Composable
private fun triLabels(defaultEnabled: Boolean): List<String> = listOf(
    stringResource(if (defaultEnabled) R.string.default_on else R.string.default_off),
    stringResource(R.string.enabled_option),
    stringResource(R.string.disabled_option),
)

@Composable
private fun glowLabels(defaultMode: String): List<String> {
    val defaultLabel = when (defaultMode) {
        TRI_ON -> stringResource(R.string.default_on)
        TRI_FOLLOW_DYNAMIC -> stringResource(
            R.string.default_value,
            stringResource(R.string.follow_dynamic_color),
        )
        else -> stringResource(R.string.default_off)
    }
    return listOf(
        defaultLabel,
        stringResource(R.string.enabled_option),
        stringResource(R.string.disabled_option),
        stringResource(R.string.follow_dynamic_color),
    )
}

@Composable
private fun marqueeAutoHideLabel(value: String): String = when (value) {
    "1" -> stringResource(R.string.marquee_once)
    "2" -> stringResource(R.string.marquee_twice)
    "1_override" -> stringResource(R.string.marquee_once_override)
    "2_override" -> stringResource(R.string.marquee_twice_override)
    else -> stringResource(R.string.disabled_option)
}

private fun resolveBoolean(value: String, defaultValue: Boolean): Boolean = when (value) {
    TRI_ON -> true
    TRI_OFF -> false
    else -> defaultValue
}

private fun resolveDynamic(value: String, defaultValue: Boolean): Boolean = when (value) {
    TRI_ON, "dark", "darker" -> true
    TRI_OFF -> false
    else -> defaultValue
}

private enum class ToastColorTarget { Highlight, FocusGlow, IslandGlow }
private enum class KeywordTarget { Whitelist, Blacklist }

private val ITEM_MARGIN = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
private const val TRI_DEFAULT = "default"
private const val TRI_ON = "on"
private const val TRI_OFF = "off"
private const val TRI_FOLLOW_DYNAMIC = "follow_dynamic"
