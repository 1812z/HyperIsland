package io.github.hyperisland.compose.page.apps.toast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import io.github.hyperisland.compose.data.toast.ToastSettingsPatch
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

internal enum class ToastSettingsFormMode { Single, Batch }

/** 单应用与批量应用共用的 Toast 配置表单。 */
@Composable
internal fun ToastSettingsFormPage(
    title: String,
    state: ToastSettingsPatch,
    defaults: DefaultConfigSettings,
    mode: ToastSettingsFormMode,
    onStateChange: (ToastSettingsPatch) -> Unit,
    onBack: () -> Unit,
    headerText: String? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val isBatch = mode == ToastSettingsFormMode.Batch
    val noChange = stringResource(R.string.no_change)
    val enabled = stringResource(R.string.enabled_option)
    val disabled = stringResource(R.string.disabled_option)
    val default = stringResource(R.string.default_option)
    var timeoutText by remember { mutableStateOf("") }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var colorTarget by remember { mutableStateOf<ToastColorTarget?>(null) }
    var selectedColor by remember { mutableStateOf(Color.Red) }
    var keywordTarget by remember { mutableStateOf<ToastKeywordTarget?>(null) }

    val forwardOptionsVisible = state.forwardEnabled != false
    val filterVisible = !isBatch && (state.forwardEnabled == true || state.blockOriginal == true)
    val marqueeEnabled = resolveBoolean(state.marquee, defaults.marquee, isBatch)
    val dynamicHighlightEnabled = resolveDynamic(
        state.dynamicHighlightColor,
        defaults.dynamicHighlightColor,
    )
    val hasHighlightSource = dynamicHighlightEnabled || !state.highlightColor.isNullOrBlank()

    val triValues = optionalValues(isBatch, TRI_DEFAULT, TRI_ON, TRI_OFF)
    @Composable
    fun triLabels(defaultEnabled: Boolean): List<String> = optionalLabels(
        isBatch,
        noChange,
        if (isBatch) default else stringResource(
            if (defaultEnabled) R.string.default_on else R.string.default_off,
        ),
        enabled,
        disabled,
    )
    val glowValues = optionalValues(isBatch, TRI_DEFAULT, TRI_ON, TRI_OFF, TRI_FOLLOW_DYNAMIC)
    @Composable
    fun glowLabels(defaultMode: String): List<String> = optionalLabels(
        isBatch,
        noChange,
        if (isBatch) default else defaultGlowLabel(defaultMode),
        enabled,
        disabled,
        stringResource(R.string.follow_dynamic_color),
    )
    val marqueeAutoHideValues = optionalValues(
        isBatch,
        TRI_DEFAULT,
        TRI_OFF,
        "1",
        "2",
        "1_override",
        "2_override",
    )
    val marqueeAutoHideLabels = optionalLabels(
        isBatch,
        noChange,
        if (isBatch) default else stringResource(
            R.string.default_value,
            marqueeAutoHideLabel(defaults.marqueeAutoHide),
        ),
        disabled,
        stringResource(R.string.marquee_once),
        stringResource(R.string.marquee_twice),
        stringResource(R.string.marquee_once_override),
        stringResource(R.string.marquee_twice_override),
    )
    val dynamicValues = optionalValues(isBatch, TRI_DEFAULT, TRI_OFF, TRI_ON, "dark", "darker")
    val dynamicLabels = optionalLabels(
        isBatch,
        noChange,
        if (isBatch) default else stringResource(
            if (defaults.dynamicHighlightColor) R.string.default_on else R.string.default_off,
        ),
        disabled,
        enabled,
        stringResource(R.string.dynamic_dark),
        stringResource(R.string.dynamic_darker),
    )

    DetailPage(title = title, onBack = onBack) {
        headerText?.let { message ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.toast_adaptation))
            Card {
                BooleanField(
                    title = stringResource(R.string.toast_forward),
                    summary = stringResource(R.string.toast_forward_summary),
                    value = state.forwardEnabled,
                    isBatch = isBatch,
                    noChange = noChange,
                    enabledLabel = enabled,
                    disabledLabel = disabled,
                ) { onStateChange(state.copy(forwardEnabled = it)) }
                BooleanField(
                    title = stringResource(R.string.toast_block_original),
                    summary = stringResource(R.string.toast_block_original_summary),
                    value = state.blockOriginal,
                    isBatch = isBatch,
                    noChange = noChange,
                    enabledLabel = enabled,
                    disabledLabel = disabled,
                ) { onStateChange(state.copy(blockOriginal = it)) }
                AnimatedVisibility(visible = forwardOptionsVisible) {
                    BooleanField(
                        title = stringResource(R.string.toast_show_notification),
                        summary = stringResource(R.string.toast_show_notification_summary),
                        value = state.showNotification,
                        isBatch = isBatch,
                        noChange = noChange,
                        enabledLabel = enabled,
                        disabledLabel = disabled,
                    ) { onStateChange(state.copy(showNotification = it)) }
                }
            }
        }
        item {
            AnimatedVisibility(visible = forwardOptionsVisible) {
                Column {
                    SectionTitle(stringResource(R.string.island))
                    Card {
                        BooleanField(
                            title = stringResource(R.string.island_icon),
                            summary = stringResource(R.string.island_icon_summary),
                            value = state.showIslandIcon,
                            isBatch = isBatch,
                            noChange = noChange,
                            enabledLabel = enabled,
                            disabledLabel = disabled,
                        ) { onStateChange(state.copy(showIslandIcon = it)) }
                        FormDropdown(
                            title = stringResource(R.string.first_float),
                            summary = stringResource(R.string.first_float_summary),
                            value = state.firstFloat,
                            values = triValues,
                            labels = triLabels(defaults.firstFloat),
                        ) { onStateChange(state.copy(firstFloat = it)) }
                        FormDropdown(
                            title = stringResource(R.string.update_float),
                            summary = stringResource(R.string.update_float_summary),
                            value = state.enableFloat,
                            values = triValues,
                            labels = triLabels(defaults.enableFloat),
                        ) { onStateChange(state.copy(enableFloat = it)) }
                        FormDropdown(
                            title = stringResource(R.string.preserve_small_icon),
                            summary = stringResource(R.string.preserve_small_icon_summary),
                            value = state.preserveSmallIcon,
                            values = triValues,
                            labels = triLabels(defaults.preserveSmallIcon),
                        ) { onStateChange(state.copy(preserveSmallIcon = it)) }
                        FormDropdown(
                            title = stringResource(R.string.marquee_channel),
                            summary = stringResource(R.string.marquee_channel_summary),
                            value = state.marquee,
                            values = triValues,
                            labels = triLabels(defaults.marquee),
                        ) { onStateChange(state.copy(marquee = it)) }
                        AnimatedVisibility(visible = marqueeEnabled) {
                            FormDropdown(
                                title = stringResource(R.string.marquee_auto_hide),
                                summary = stringResource(R.string.marquee_auto_hide_summary),
                                value = state.marqueeAutoHide,
                                values = marqueeAutoHideValues,
                                labels = marqueeAutoHideLabels,
                            ) { onStateChange(state.copy(marqueeAutoHide = it)) }
                        }
                        ArrowPreference(
                            title = stringResource(R.string.auto_disappear),
                            summary = timeoutSummary(state.timeout, defaults.timeout, isBatch, noChange),
                            insideMargin = TOAST_ITEM_MARGIN,
                            onClick = {
                                timeoutText = state.timeout?.takeUnless { it == TRI_DEFAULT }.orEmpty()
                                showTimeoutDialog = true
                            },
                        )
                    }
                }
            }
        }
        item {
            AnimatedVisibility(visible = forwardOptionsVisible) {
                Column {
                    SectionTitle(stringResource(R.string.highlight_color))
                    Card {
                        FormDropdown(
                            title = stringResource(R.string.dynamic_highlight_color),
                            summary = stringResource(R.string.dynamic_highlight_color_summary),
                            value = state.dynamicHighlightColor,
                            values = dynamicValues,
                            labels = dynamicLabels,
                        ) { onStateChange(state.copy(dynamicHighlightColor = it)) }
                        AnimatedVisibility(visible = !dynamicHighlightEnabled) {
                            ColorField(
                                title = stringResource(R.string.highlight_color),
                                value = state.highlightColor,
                                isBatch = isBatch,
                                noChange = noChange,
                            ) {
                                selectedColor = parseHexColor(state.highlightColor.orEmpty())
                                colorTarget = ToastColorTarget.Highlight
                            }
                        }
                        AnimatedVisibility(visible = hasHighlightSource) {
                            Column {
                                TriSwitchField(
                                    title = stringResource(R.string.left_side),
                                    value = state.showLeftHighlight,
                                    isBatch = isBatch,
                                    values = triValues,
                                    labels = triLabels(false),
                                ) { onStateChange(state.copy(showLeftHighlight = it)) }
                                TriSwitchField(
                                    title = stringResource(R.string.right_side),
                                    value = state.showRightHighlight,
                                    isBatch = isBatch,
                                    values = triValues,
                                    labels = triLabels(false),
                                ) { onStateChange(state.copy(showRightHighlight = it)) }
                            }
                        }
                    }
                }
            }
        }
        item {
            AnimatedVisibility(visible = forwardOptionsVisible) {
                Column {
                    SectionTitle(stringResource(R.string.glow))
                    Card {
                        FormDropdown(
                            title = stringResource(R.string.focus_notification),
                            value = state.outerGlow,
                            values = glowValues,
                            labels = glowLabels(defaults.outerGlow),
                        ) { onStateChange(state.copy(outerGlow = it)) }
                        AnimatedVisibility(visible = state.outerGlow == TRI_ON) {
                            ColorField(
                                title = stringResource(R.string.out_effect_color),
                                value = state.outEffectColor,
                                isBatch = isBatch,
                                noChange = noChange,
                            ) {
                                selectedColor = parseHexColor(state.outEffectColor.orEmpty())
                                colorTarget = ToastColorTarget.FocusGlow
                            }
                        }
                        FormDropdown(
                            title = stringResource(R.string.island_outer_glow),
                            value = state.islandOuterGlow,
                            values = glowValues,
                            labels = glowLabels(defaults.islandOuterGlow),
                        ) { onStateChange(state.copy(islandOuterGlow = it)) }
                        AnimatedVisibility(visible = state.islandOuterGlow == TRI_ON) {
                            ColorField(
                                title = stringResource(R.string.out_effect_color),
                                value = state.islandOuterGlowColor,
                                isBatch = isBatch,
                                noChange = noChange,
                            ) {
                                selectedColor = parseHexColor(state.islandOuterGlowColor.orEmpty())
                                colorTarget = ToastColorTarget.IslandGlow
                            }
                        }
                    }
                }
            }
        }
        item {
            AnimatedVisibility(visible = filterVisible) {
                Column {
                    SectionTitle(stringResource(R.string.filter_rules))
                    Card {
                        FormDropdown(
                            title = stringResource(R.string.filter_mode),
                            summary = when (state.filterMode) {
                                FILTER_WHITELIST -> stringResource(R.string.filter_whitelist_summary)
                                FILTER_BLACKLIST -> stringResource(R.string.filter_blacklist_summary)
                                else -> null
                            },
                            value = state.filterMode,
                            values = optionalValues(isBatch, FILTER_BLACKLIST, FILTER_WHITELIST),
                            labels = optionalLabels(
                                isBatch,
                                noChange,
                                stringResource(R.string.filter_blacklist),
                                stringResource(R.string.filter_whitelist),
                            ),
                        ) { onStateChange(state.copy(filterMode = it)) }
                        AnimatedVisibility(visible = state.filterMode == FILTER_WHITELIST) {
                            ArrowPreference(
                                title = stringResource(R.string.whitelist_keywords),
                                summary = keywordSummary(state.whitelistKeywords, isBatch, noChange),
                                insideMargin = TOAST_ITEM_MARGIN,
                                onClick = { keywordTarget = ToastKeywordTarget.Whitelist },
                            )
                        }
                        ArrowPreference(
                            title = stringResource(R.string.blacklist_keywords),
                            summary = keywordSummary(state.blacklistKeywords, isBatch, noChange),
                            insideMargin = TOAST_ITEM_MARGIN,
                            onClick = { keywordTarget = ToastKeywordTarget.Blacklist },
                        )
                    }
                    AnimatedVisibility(visible = state.filterMode == FILTER_WHITELIST) {
                        Card {
                            BasicComponent(
                                title = stringResource(R.string.keyword_filter_priority),
                                insideMargin = TOAST_ITEM_MARGIN,
                            )
                        }
                    }
                }
            }
        }
        footer?.let { content -> item { content() } }
    }

    WindowDialog(
        show = showTimeoutDialog,
        title = stringResource(R.string.auto_disappear),
        onDismissRequest = { showTimeoutDialog = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextField(
                value = timeoutText,
                onValueChange = { timeoutText = it.filter(Char::isDigit).take(2) },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.seconds),
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isBatch) {
                    TextButton(
                        text = stringResource(R.string.no_change),
                        onClick = {
                            onStateChange(state.copy(timeout = null))
                            showTimeoutDialog = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(
                    text = stringResource(R.string.reset_default),
                    onClick = {
                        onStateChange(state.copy(timeout = TRI_DEFAULT))
                        showTimeoutDialog = false
                    },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val timeout = timeoutText.toIntOrNull()?.takeIf { it in 1..20 }?.toString()
                        if (isBatch) {
                            timeout?.let { onStateChange(state.copy(timeout = it)) }
                        } else {
                            onStateChange(state.copy(timeout = timeout ?: TRI_DEFAULT))
                        }
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
            if (colorTarget == ToastColorTarget.Highlight) R.string.highlight_color else R.string.out_effect_color,
        ),
        initialColor = selectedColor,
        onDismiss = { colorTarget = null },
        onDelete = {
            onStateChange(updateColor(state, colorTarget, ""))
            colorTarget = null
        },
        onSave = { color ->
            onStateChange(updateColor(state, colorTarget, color.toArgbHex()))
            colorTarget = null
        },
    )

    KeywordListDialog(
        show = keywordTarget != null,
        title = stringResource(
            if (keywordTarget == ToastKeywordTarget.Whitelist) {
                R.string.whitelist_keywords
            } else {
                R.string.blacklist_keywords
            },
        ),
        keywords = when (keywordTarget) {
            ToastKeywordTarget.Whitelist -> state.whitelistKeywords.orEmpty()
            ToastKeywordTarget.Blacklist -> state.blacklistKeywords.orEmpty()
            null -> emptyList()
        },
        onDismiss = { keywordTarget = null },
        onSave = { keywords ->
            onStateChange(
                if (keywordTarget == ToastKeywordTarget.Whitelist) {
                    state.copy(whitelistKeywords = keywords)
                } else {
                    state.copy(blacklistKeywords = keywords)
                },
            )
            keywordTarget = null
        },
    )
}

@Composable
private fun BooleanField(
    title: String,
    summary: String?,
    value: Boolean?,
    isBatch: Boolean,
    noChange: String,
    enabledLabel: String,
    disabledLabel: String,
    onChange: (Boolean?) -> Unit,
) {
    if (isBatch) {
        FormDropdown(
            title = title,
            summary = summary,
            value = value,
            values = listOf(null, true, false),
            labels = listOf(noChange, enabledLabel, disabledLabel),
            onChange = onChange,
        )
    } else {
        SwitchPreference(
            checked = value == true,
            onCheckedChange = { onChange(it) },
            title = title,
            summary = summary,
            insideMargin = TOAST_ITEM_MARGIN,
        )
    }
}

@Composable
private fun TriSwitchField(
    title: String,
    value: String?,
    isBatch: Boolean,
    values: List<String?>,
    labels: List<String>,
    onChange: (String?) -> Unit,
) {
    if (isBatch) {
        FormDropdown(
            title = title,
            value = value,
            values = values,
            labels = labels,
            onChange = onChange,
        )
    } else {
        SwitchPreference(
            checked = value == TRI_ON,
            onCheckedChange = { onChange(if (it) TRI_ON else TRI_OFF) },
            title = title,
            insideMargin = TOAST_ITEM_MARGIN,
        )
    }
}

@Composable
private fun <T> FormDropdown(
    title: String,
    summary: String? = null,
    value: T?,
    values: List<T?>,
    labels: List<String>,
    onChange: (T?) -> Unit,
) {
    OverlayDropdownPreference(
        items = labels,
        selectedIndex = values.indexOf(value).coerceAtLeast(0),
        title = title,
        summary = summary,
        insideMargin = TOAST_ITEM_MARGIN,
        renderInRootScaffold = false,
        onSelectedIndexChange = { onChange(values[it]) },
    )
}

@Composable
private fun ColorField(
    title: String,
    value: String?,
    isBatch: Boolean,
    noChange: String,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = title,
        summary = if (isBatch) value ?: noChange else value?.takeIf(String::isNotEmpty),
        insideMargin = TOAST_ITEM_MARGIN,
        onClick = onClick,
    )
}

private fun updateColor(
    state: ToastSettingsPatch,
    target: ToastColorTarget?,
    value: String,
): ToastSettingsPatch = when (target) {
    ToastColorTarget.Highlight -> state.copy(highlightColor = value)
    ToastColorTarget.FocusGlow -> state.copy(outEffectColor = value)
    ToastColorTarget.IslandGlow -> state.copy(islandOuterGlowColor = value)
    null -> state
}

private fun <T> optionalValues(isBatch: Boolean, vararg values: T): List<T?> =
    if (isBatch) listOf(null) + values.toList() else values.toList()

private fun optionalLabels(isBatch: Boolean, noChange: String, vararg labels: String): List<String> =
    if (isBatch) listOf(noChange) + labels.toList() else labels.toList()

private fun resolveBoolean(value: String?, defaultValue: Boolean, isBatch: Boolean): Boolean = when (value) {
    TRI_ON -> true
    TRI_OFF -> false
    TRI_DEFAULT -> defaultValue
    else -> isBatch
}

private fun resolveDynamic(value: String?, defaultValue: Boolean): Boolean = when (value) {
    TRI_ON, "dark", "darker" -> true
    TRI_OFF -> false
    TRI_DEFAULT -> defaultValue
    else -> false
}

@Composable
private fun defaultGlowLabel(defaultMode: String): String = when (defaultMode) {
    TRI_ON -> stringResource(R.string.default_on)
    TRI_FOLLOW_DYNAMIC -> stringResource(
        R.string.default_value,
        stringResource(R.string.follow_dynamic_color),
    )
    else -> stringResource(R.string.default_off)
}

@Composable
private fun marqueeAutoHideLabel(value: String): String = when (value) {
    "1" -> stringResource(R.string.marquee_once)
    "2" -> stringResource(R.string.marquee_twice)
    "1_override" -> stringResource(R.string.marquee_once_override)
    "2_override" -> stringResource(R.string.marquee_twice_override)
    else -> stringResource(R.string.disabled_option)
}

@Composable
private fun timeoutSummary(value: String?, defaultValue: Int, isBatch: Boolean, noChange: String): String = when {
    value == null && isBatch -> noChange
    value == TRI_DEFAULT -> stringResource(R.string.default_timeout_value, defaultValue)
    else -> stringResource(R.string.timeout_seconds_value, value?.toIntOrNull() ?: defaultValue)
}

private fun keywordSummary(values: List<String>?, isBatch: Boolean, noChange: String): String? = when {
    values == null && isBatch -> noChange
    values.isNullOrEmpty() -> null
    else -> values.joinToString("、")
}

private enum class ToastColorTarget { Highlight, FocusGlow, IslandGlow }
private enum class ToastKeywordTarget { Whitelist, Blacklist }

private const val TRI_DEFAULT = "default"
private const val TRI_ON = "on"
private const val TRI_OFF = "off"
private const val TRI_FOLLOW_DYNAMIC = "follow_dynamic"
private const val FILTER_BLACKLIST = "blacklist"
private const val FILTER_WHITELIST = "whitelist"
private val TOAST_ITEM_MARGIN = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
