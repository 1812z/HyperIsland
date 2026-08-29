package io.github.hyperisland.compose.page.apps.channel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.ColorPaletteDialog
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.KeywordListDialog
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.parseHexColor
import io.github.hyperisland.compose.component.toArgbHex
import io.github.hyperisland.compose.data.DefaultConfigSettings
import io.github.hyperisland.compose.data.channel.ChannelCustomizationTarget
import io.github.hyperisland.compose.data.channel.ChannelSettingsPatch
import io.github.hyperisland.compose.data.channel.FILTER_BLACKLIST
import io.github.hyperisland.compose.data.channel.FILTER_WHITELIST
import io.github.hyperisland.compose.data.channel.ICON_APP
import io.github.hyperisland.compose.data.channel.ICON_AUTO
import io.github.hyperisland.compose.data.channel.ICON_NOTIFICATION_LARGE
import io.github.hyperisland.compose.data.channel.ICON_NOTIFICATION_SMALL
import io.github.hyperisland.compose.data.channel.OPTION_DEFAULT
import io.github.hyperisland.compose.data.channel.OPTION_FOLLOW_DYNAMIC
import io.github.hyperisland.compose.data.channel.OPTION_OFF
import io.github.hyperisland.compose.data.channel.OPTION_ON
import io.github.hyperisland.compose.data.channel.RENDERER_IMAGE_TEXT_BUTTONS
import io.github.hyperisland.compose.data.channel.RENDERER_IMAGE_TEXT_PROGRESS
import io.github.hyperisland.compose.data.channel.RENDERER_IMAGE_TEXT_RIGHT_BUTTON
import io.github.hyperisland.compose.data.channel.RENDERER_IMAGE_TEXT_WRAP
import io.github.hyperisland.compose.data.channel.TEMPLATE_AI_NOTIFICATION
import io.github.hyperisland.compose.data.channel.TEMPLATE_NOTIFICATION
import io.github.hyperisland.compose.data.channel.TEMPLATE_PROGRESS
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 渠道设置的唯一表单实现。
 * Single 使用完整补丁并即时保存；Batch 以 null 表示“不修改”，由入口页统一提交。
 */
internal enum class ChannelSettingsFormMode { Single, Batch }

@Composable
internal fun ChannelSettingsFormPage(
    title: String,
    state: ChannelSettingsPatch,
    defaults: DefaultConfigSettings,
    mode: ChannelSettingsFormMode,
    onStateChange: (ChannelSettingsPatch) -> Unit,
    onBack: () -> Unit,
    headerText: String? = null,
    onOpenCustomization: ((ChannelCustomizationTarget) -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val isBatch = mode == ChannelSettingsFormMode.Batch
    val noChange = stringResource(R.string.no_change)
    val on = stringResource(R.string.enabled_option)
    val off = stringResource(R.string.disabled_option)
    val default = stringResource(R.string.default_option)
    var timeoutDialog by remember { mutableStateOf(false) }
    var timeoutDraft by remember { mutableStateOf("") }
    var colorTarget by remember { mutableStateOf<ChannelFormColorTarget?>(null) }
    var colorDraft by remember { mutableStateOf(Color.Red) }
    var keywordTarget by remember { mutableStateOf<ChannelFormKeywordTarget?>(null) }

    val focusValue = state.focus ?: OPTION_OFF
    val focusEnabled = focusValue == OPTION_ON ||
        (focusValue == OPTION_DEFAULT && defaults.focusNotification)
    val islandVisible = state.islandEnabled != false
    val marqueeEnabled = when (state.marquee) {
        OPTION_ON -> true
        OPTION_OFF -> false
        OPTION_DEFAULT -> defaults.marquee
        else -> true
    }
    val dynamicHighlightEnabled = state.dynamicHighlightColor in setOf(OPTION_ON, "dark", "darker") ||
        (state.dynamicHighlightColor == OPTION_DEFAULT && defaults.dynamicHighlightColor)
    val hasHighlightColor = dynamicHighlightEnabled || !state.highlightColor.isNullOrBlank()
    val islandGlowFollowsDynamic = followsDynamic(state.islandOuterGlow, defaults.islandOuterGlow)
    val focusGlowFollowsDynamic = followsDynamic(state.outerGlow, defaults.outerGlow)
    val aodEnabled = resolveOption(state.aodText, defaults.aodText)

    val triValues = if (isBatch) {
        listOf<String?>(null, OPTION_DEFAULT, OPTION_ON, OPTION_OFF)
    } else {
        listOf<String?>(OPTION_DEFAULT, OPTION_ON, OPTION_OFF)
    }
    @Composable
    fun triLabels(defaultValue: Boolean): List<String> = if (isBatch) {
        listOf(noChange, default, on, off)
    } else {
        listOf(defaultOptionLabel(defaultValue), on, off)
    }
    val glowValues = if (isBatch) {
        listOf<String?>(null, OPTION_DEFAULT, OPTION_ON, OPTION_OFF, OPTION_FOLLOW_DYNAMIC)
    } else {
        listOf<String?>(OPTION_DEFAULT, OPTION_ON, OPTION_OFF, OPTION_FOLLOW_DYNAMIC)
    }
    @Composable
    fun glowLabels(defaultValue: String): List<String> {
        val defaultLabel = if (isBatch) default else defaultGlowLabel(defaultValue)
        val labels = listOf(defaultLabel, on, off, stringResource(R.string.follow_dynamic_color))
        return if (isBatch) listOf(noChange) + labels else labels
    }

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
            SectionTitle(stringResource(R.string.channel_template_section))
            Card {
                FormDropdown(
                    title = stringResource(R.string.channel_template),
                    value = state.template,
                    values = optionalValues(isBatch, TEMPLATE_PROGRESS, TEMPLATE_NOTIFICATION, TEMPLATE_AI_NOTIFICATION),
                    labels = optionalLabels(
                        isBatch,
                        noChange,
                        stringResource(R.string.template_progress),
                        stringResource(R.string.template_notification),
                        stringResource(R.string.template_ai_notification),
                    ),
                ) { onStateChange(state.copy(template = it)) }
                FormDropdown(
                    title = stringResource(R.string.channel_renderer),
                    value = state.renderer,
                    values = optionalValues(
                        isBatch,
                        RENDERER_IMAGE_TEXT_BUTTONS,
                        RENDERER_IMAGE_TEXT_WRAP,
                        RENDERER_IMAGE_TEXT_RIGHT_BUTTON,
                        RENDERER_IMAGE_TEXT_PROGRESS,
                    ),
                    labels = optionalLabels(
                        isBatch,
                        noChange,
                        stringResource(R.string.renderer_image_text_buttons),
                        stringResource(R.string.renderer_image_text_wrap),
                        stringResource(R.string.renderer_image_text_right_button),
                        stringResource(R.string.renderer_image_text_progress),
                    ),
                ) { onStateChange(state.copy(renderer = it)) }
            }
        }
        item {
            SectionTitle(stringResource(R.string.island))
            Card {
                if (isBatch) {
                    FormDropdown(
                        title = stringResource(R.string.channel_enable_island),
                        value = state.islandEnabled,
                        values = listOf(null, true, false),
                        labels = listOf(noChange, on, off),
                        enabled = focusEnabled,
                    ) { onStateChange(state.copy(islandEnabled = it)) }
                } else {
                    PreferenceSwitch(
                        title = stringResource(R.string.channel_enable_island),
                        summary = null,
                        icon = null,
                        checked = state.islandEnabled != false,
                        enabled = focusEnabled,
                        insideMargin = CHANNEL_FORM_MARGIN,
                    ) { onStateChange(state.copy(islandEnabled = it)) }
                }
                AnimatedVisibility(visible = islandVisible) {
                    Column {
                        FormDropdown(
                            title = stringResource(R.string.channel_icon_source),
                            value = state.iconMode,
                            values = optionalValues(
                                isBatch,
                                ICON_AUTO,
                                ICON_NOTIFICATION_SMALL,
                                ICON_NOTIFICATION_LARGE,
                                ICON_APP,
                            ),
                            labels = optionalLabels(
                                isBatch,
                                noChange,
                                stringResource(R.string.icon_auto),
                                stringResource(R.string.icon_notification_small),
                                stringResource(R.string.icon_notification_large),
                                stringResource(R.string.icon_app),
                            ),
                        ) { onStateChange(state.copy(iconMode = it)) }
                        TriField(stringResource(R.string.island_icon), state.showIslandIcon, triValues, triLabels(defaults.showIslandIcon)) {
                            onStateChange(state.copy(showIslandIcon = it))
                        }
                        TriField(stringResource(R.string.first_float), state.firstFloat, triValues, triLabels(defaults.firstFloat)) {
                            onStateChange(state.copy(firstFloat = it))
                        }
                        TriField(stringResource(R.string.update_float), state.enableFloat, triValues, triLabels(defaults.enableFloat)) {
                            onStateChange(state.copy(enableFloat = it))
                        }
                        TriField(stringResource(R.string.marquee_channel), state.marquee, triValues, triLabels(defaults.marquee)) {
                            onStateChange(state.copy(marquee = it))
                        }
                        FormDropdown(
                            title = stringResource(R.string.marquee_auto_hide),
                            value = state.marqueeAutoHide,
                            values = optionalValues(isBatch, OPTION_DEFAULT, OPTION_OFF, "1", "2", "1_override", "2_override"),
                            labels = optionalLabels(
                                isBatch,
                                noChange,
                                if (isBatch) default else stringResource(
                                    R.string.default_with_value,
                                    marqueeAutoHideLabel(defaults.marqueeAutoHide),
                                ),
                                off,
                                stringResource(R.string.marquee_once),
                                stringResource(R.string.marquee_twice),
                                stringResource(R.string.marquee_once_override),
                                stringResource(R.string.marquee_twice_override),
                            ),
                            enabled = marqueeEnabled,
                        ) { onStateChange(state.copy(marqueeAutoHide = it)) }
                        ArrowPreference(
                            title = stringResource(R.string.auto_disappear),
                            summary = timeoutSummary(state.timeout, defaults.timeout, isBatch, noChange),
                            insideMargin = CHANNEL_FORM_MARGIN,
                            onClick = {
                                timeoutDraft = state.timeout?.takeUnless { it == OPTION_DEFAULT }.orEmpty()
                                timeoutDialog = true
                            },
                        )
                        if (!isBatch && onOpenCustomization != null) {
                            ArrowPreference(
                                title = stringResource(R.string.channel_island_customization),
                                insideMargin = CHANNEL_FORM_MARGIN,
                                onClick = { onOpenCustomization(ChannelCustomizationTarget.Island) },
                            )
                        }
                    }
                }
            }
        }
        item {
            AnimatedVisibility(visible = islandVisible) {
                Column {
                    SectionTitle(stringResource(R.string.appearance))
                    Card {
                        FormDropdown(
                            title = stringResource(R.string.island_outer_glow),
                            value = state.islandOuterGlow,
                            values = glowValues,
                            labels = glowLabels(defaults.islandOuterGlow),
                        ) { onStateChange(state.copy(islandOuterGlow = it)) }
                        ColorField(
                            title = stringResource(R.string.out_effect_color),
                            value = state.islandOuterGlowColor,
                            noChange = noChange,
                            isBatch = isBatch,
                            enabled = !islandGlowFollowsDynamic,
                        ) {
                            colorDraft = parseHexColor(state.islandOuterGlowColor.orEmpty())
                            colorTarget = ChannelFormColorTarget.Island
                        }
                        FormDropdown(
                            title = stringResource(R.string.dynamic_highlight_color),
                            value = state.dynamicHighlightColor,
                            values = optionalValues(isBatch, OPTION_DEFAULT, OPTION_OFF, OPTION_ON, "dark", "darker"),
                            labels = optionalLabels(
                                isBatch,
                                noChange,
                                if (isBatch) default else stringResource(
                                    R.string.default_with_value,
                                    enabledLabel(defaults.dynamicHighlightColor),
                                ),
                                off,
                                on,
                                stringResource(R.string.dynamic_dark),
                                stringResource(R.string.dynamic_darker),
                            ),
                        ) { onStateChange(state.copy(dynamicHighlightColor = it)) }
                        AnimatedVisibility(visible = !dynamicHighlightEnabled) {
                            ColorField(
                                title = stringResource(R.string.highlight_color),
                                value = state.highlightColor,
                                noChange = noChange,
                                isBatch = isBatch,
                            ) {
                                colorDraft = parseHexColor(state.highlightColor.orEmpty())
                                colorTarget = ChannelFormColorTarget.Highlight
                            }
                        }
                        AnimatedVisibility(visible = hasHighlightColor) {
                            Column {
                                ToggleField(stringResource(R.string.channel_left_text_highlight), state.showLeftHighlight, triValues, triLabels(false), isBatch) {
                                    onStateChange(state.copy(showLeftHighlight = it))
                                }
                                ToggleField(stringResource(R.string.channel_right_text_highlight), state.showRightHighlight, triValues, triLabels(false), isBatch) {
                                    onStateChange(state.copy(showRightHighlight = it))
                                }
                            }
                        }
                        ToggleField(stringResource(R.string.channel_left_narrow_font), state.showLeftNarrowFont, triValues, triLabels(false), isBatch) {
                            onStateChange(state.copy(showLeftNarrowFont = it))
                        }
                        ToggleField(stringResource(R.string.channel_right_narrow_font), state.showRightNarrowFont, triValues, triLabels(false), isBatch) {
                            onStateChange(state.copy(showRightNarrowFont = it))
                        }
                    }
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.focus_notification))
            Card {
                TriField(stringResource(R.string.focus_notification), state.focus, triValues, triLabels(defaults.focusNotification)) { value ->
                    onStateChange(
                        if (value == OPTION_OFF) {
                            state.copy(
                                focus = value,
                                showNotification = OPTION_ON,
                                preserveSmallIcon = OPTION_OFF,
                                islandEnabled = true,
                            )
                        } else {
                            state.copy(focus = value)
                        },
                    )
                }
                AnimatedVisibility(visible = focusEnabled) {
                    Column {
                        if (isBatch) {
                            FormDropdown(
                                title = stringResource(R.string.channel_hide_notification),
                                value = state.showNotification?.let { it == OPTION_OFF },
                                values = listOf(null, true, false),
                                labels = listOf(noChange, on, off),
                            ) { hidden ->
                                onStateChange(state.copy(showNotification = hidden?.let { if (it) OPTION_OFF else OPTION_ON }))
                            }
                        } else {
                            PreferenceSwitch(
                                title = stringResource(R.string.channel_hide_notification),
                                summary = null,
                                icon = null,
                                checked = state.showNotification == OPTION_OFF,
                                insideMargin = CHANNEL_FORM_MARGIN,
                            ) { onStateChange(state.copy(showNotification = if (it) OPTION_OFF else OPTION_ON)) }
                        }
                        TriField(stringResource(R.string.preserve_small_icon), state.preserveSmallIcon, triValues, triLabels(defaults.preserveSmallIcon)) {
                            onStateChange(state.copy(preserveSmallIcon = it))
                        }
                        TriField(stringResource(R.string.restore_lockscreen), state.restoreLockscreen, triValues, triLabels(defaults.restoreLockscreen)) {
                            onStateChange(state.copy(restoreLockscreen = it))
                        }
                    }
                }
                FormDropdown(
                    title = stringResource(R.string.focus_outer_glow),
                    value = state.outerGlow,
                    values = glowValues,
                    labels = glowLabels(defaults.outerGlow),
                ) { onStateChange(state.copy(outerGlow = it)) }
                ColorField(
                    title = stringResource(R.string.out_effect_color),
                    value = state.outEffectColor,
                    noChange = noChange,
                    isBatch = isBatch,
                    enabled = !focusGlowFollowsDynamic,
                ) {
                    colorDraft = parseHexColor(state.outEffectColor.orEmpty())
                    colorTarget = ChannelFormColorTarget.Focus
                }
                if (!isBatch && onOpenCustomization != null) {
                    ArrowPreference(
                        title = stringResource(R.string.channel_focus_customization),
                        insideMargin = CHANNEL_FORM_MARGIN,
                        onClick = { onOpenCustomization(ChannelCustomizationTarget.Focus) },
                    )
                }
            }
        }
        item {
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
                ArrowPreference(
                    title = stringResource(R.string.whitelist_keywords),
                    summary = keywordSummary(state.whitelistKeywords, isBatch, noChange),
                    enabled = state.filterMode == FILTER_WHITELIST,
                    insideMargin = CHANNEL_FORM_MARGIN,
                    onClick = { keywordTarget = ChannelFormKeywordTarget.Whitelist },
                )
                ArrowPreference(
                    title = stringResource(R.string.blacklist_keywords),
                    summary = keywordSummary(state.blacklistKeywords, isBatch, noChange),
                    insideMargin = CHANNEL_FORM_MARGIN,
                    onClick = { keywordTarget = ChannelFormKeywordTarget.Blacklist },
                )
            }
        }
        item {
            SectionTitle(stringResource(R.string.channel_aod_section))
            Card {
                TriField(
                    title = stringResource(R.string.aod_text),
                    value = state.aodText,
                    values = triValues,
                    labels = triLabels(defaults.aodText),
                    enabled = focusEnabled,
                ) { onStateChange(state.copy(aodText = it)) }
                AnimatedVisibility(visible = !isBatch && focusEnabled && aodEnabled && onOpenCustomization != null) {
                    ArrowPreference(
                        title = stringResource(R.string.channel_aod_customization),
                        insideMargin = CHANNEL_FORM_MARGIN,
                        onClick = { onOpenCustomization?.invoke(ChannelCustomizationTarget.Aod) },
                    )
                }
            }
        }
        footer?.let { content -> item { content() } }
    }

    WindowDialog(
        show = timeoutDialog,
        title = stringResource(R.string.auto_disappear),
        onDismissRequest = { timeoutDialog = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextField(
                value = timeoutDraft,
                onValueChange = { timeoutDraft = it.filter(Char::isDigit).take(9) },
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
                            timeoutDialog = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(
                    text = stringResource(R.string.restore_default),
                    onClick = { onStateChange(state.copy(timeout = OPTION_DEFAULT)); timeoutDialog = false },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val timeout = timeoutDraft.toIntOrNull()?.takeIf { it >= 1 }?.toString()
                        if (isBatch) {
                            timeout?.let { onStateChange(state.copy(timeout = it)) }
                        } else {
                            onStateChange(state.copy(timeout = timeout ?: OPTION_DEFAULT))
                        }
                        timeoutDialog = false
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
            if (colorTarget == ChannelFormColorTarget.Highlight) {
                R.string.highlight_color
            } else {
                R.string.out_effect_color
            },
        ),
        initialColor = colorDraft,
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
            if (keywordTarget == ChannelFormKeywordTarget.Whitelist) {
                R.string.whitelist_keywords
            } else {
                R.string.blacklist_keywords
            },
        ),
        keywords = when (keywordTarget) {
            ChannelFormKeywordTarget.Whitelist -> state.whitelistKeywords.orEmpty()
            ChannelFormKeywordTarget.Blacklist -> state.blacklistKeywords.orEmpty()
            null -> emptyList()
        },
        onDismiss = { keywordTarget = null },
        onSave = { values ->
            onStateChange(
                if (keywordTarget == ChannelFormKeywordTarget.Whitelist) {
                    state.copy(whitelistKeywords = values)
                } else {
                    state.copy(blacklistKeywords = values)
                },
            )
            keywordTarget = null
        },
    )
}

@Composable
private fun <T> FormDropdown(
    title: String,
    value: T?,
    values: List<T?>,
    labels: List<String>,
    summary: String? = null,
    enabled: Boolean = true,
    onChange: (T?) -> Unit,
) {
    PreferenceDropdown(
        title = title,
        summary = summary,
        icon = null,
        items = labels,
        selectedIndex = values.indexOf(value).coerceAtLeast(0),
        enabled = enabled,
        insideMargin = CHANNEL_FORM_MARGIN,
        onSelectedIndexChange = { onChange(values[it]) },
    )
}

@Composable
private fun TriField(
    title: String,
    value: String?,
    values: List<String?>,
    labels: List<String>,
    enabled: Boolean = true,
    onChange: (String?) -> Unit,
) = FormDropdown(title, value, values, labels, enabled = enabled, onChange = onChange)

@Composable
private fun ToggleField(
    title: String,
    value: String?,
    values: List<String?>,
    labels: List<String>,
    isBatch: Boolean,
    onChange: (String?) -> Unit,
) {
    if (isBatch) {
        FormDropdown(title, value, values, labels, onChange = onChange)
    } else {
        PreferenceSwitch(
            title = title,
            summary = null,
            icon = null,
            checked = value == OPTION_ON,
            insideMargin = CHANNEL_FORM_MARGIN,
        ) { onChange(if (it) OPTION_ON else OPTION_OFF) }
    }
}

@Composable
private fun ColorField(
    title: String,
    value: String?,
    noChange: String,
    isBatch: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = title,
        summary = if (isBatch) value ?: noChange else value?.takeIf(String::isNotEmpty),
        enabled = enabled,
        insideMargin = CHANNEL_FORM_MARGIN,
        endActions = {
            if (!isBatch) {
                ChannelColorPreview(value.orEmpty())
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun RowScope.ChannelColorPreview(value: String) {
    Box(
        modifier = Modifier
            .align(Alignment.CenterVertically)
            .padding(end = 12.dp)
            .size(22.dp)
            .background(parseHexColor(value, MiuixTheme.colorScheme.primary), CircleShape),
    )
}

private fun updateColor(
    state: ChannelSettingsPatch,
    target: ChannelFormColorTarget?,
    value: String,
): ChannelSettingsPatch = when (target) {
    ChannelFormColorTarget.Highlight -> state.copy(highlightColor = value)
    ChannelFormColorTarget.Island -> state.copy(islandOuterGlowColor = value)
    ChannelFormColorTarget.Focus -> state.copy(outEffectColor = value)
    null -> state
}

private fun <T> optionalValues(isBatch: Boolean, vararg values: T): List<T?> =
    if (isBatch) listOf(null) + values.toList() else values.toList()

private fun optionalLabels(isBatch: Boolean, noChange: String, vararg labels: String): List<String> =
    if (isBatch) listOf(noChange) + labels.toList() else labels.toList()

private fun followsDynamic(value: String?, defaultValue: String): Boolean =
    value == OPTION_FOLLOW_DYNAMIC || (value == OPTION_DEFAULT && defaultValue == OPTION_FOLLOW_DYNAMIC)

private fun resolveOption(value: String?, defaultValue: Boolean): Boolean = when (value) {
    OPTION_ON -> true
    OPTION_OFF -> false
    OPTION_DEFAULT -> defaultValue
    else -> false
}

@Composable
private fun defaultOptionLabel(defaultValue: Boolean): String = stringResource(
    R.string.default_with_value,
    enabledLabel(defaultValue),
)

@Composable
private fun defaultGlowLabel(defaultValue: String): String = stringResource(
    R.string.default_with_value,
    when (defaultValue) {
        OPTION_ON -> stringResource(R.string.enabled_option)
        OPTION_FOLLOW_DYNAMIC -> stringResource(R.string.follow_dynamic_color)
        else -> stringResource(R.string.disabled_option)
    },
)

@Composable
private fun enabledLabel(value: Boolean): String = stringResource(
    if (value) R.string.enabled_option else R.string.disabled_option,
)

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
    value == OPTION_DEFAULT -> stringResource(R.string.default_timeout_seconds, defaultValue)
    else -> stringResource(R.string.timeout_seconds_value, value?.toIntOrNull() ?: defaultValue)
}

@Composable
private fun keywordSummary(values: List<String>?, isBatch: Boolean, noChange: String): String = when {
    values == null && isBatch -> noChange
    values.isNullOrEmpty() -> stringResource(R.string.not_configured)
    else -> stringResource(R.string.keyword_count, values.size)
}

private enum class ChannelFormColorTarget { Highlight, Island, Focus }
private enum class ChannelFormKeywordTarget { Whitelist, Blacklist }
private val CHANNEL_FORM_MARGIN = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
