package io.github.hyperisland.compose.page.apps.channel

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
import androidx.compose.runtime.rememberCoroutineScope
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
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.parseHexColor
import io.github.hyperisland.compose.component.toArgbHex
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.NotificationChannelsRepository
import io.github.hyperisland.compose.data.channel.BatchChannelTarget
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun BatchChannelSettingsPage(
    target: BatchChannelTarget,
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
) {
    var patch by remember(target) { mutableStateOf(ChannelSettingsPatch()) }
    var applying by remember { mutableStateOf(false) }
    var timeoutDialog by remember { mutableStateOf(false) }
    var timeoutDraft by remember { mutableStateOf("") }
    var colorTarget by remember { mutableStateOf<BatchColorTarget?>(null) }
    var colorDraft by remember { mutableStateOf(Color.Red) }
    var keywordTarget by remember { mutableStateOf<BatchKeywordTarget?>(null) }
    val scope = rememberCoroutineScope()
    val defaults = remember { prefs.defaultConfigSettings() }

    val noChange = stringResource(R.string.compose_no_change)
    val on = stringResource(R.string.compose_enabled_option)
    val off = stringResource(R.string.compose_disabled_option)
    val default = stringResource(R.string.compose_default)
    val triValues = listOf<String?>(null, OPTION_DEFAULT, OPTION_ON, OPTION_OFF)
    val triLabels = listOf(noChange, default, on, off)
    val boolValues = listOf<Boolean?>(null, true, false)
    val boolLabels = listOf(noChange, on, off)
    val focusEnabled = patch.focus == OPTION_ON ||
        (patch.focus == OPTION_DEFAULT && defaults.focusNotification)
    val islandVisible = patch.islandEnabled != false
    val marqueeEnabled = when (patch.marquee) {
        OPTION_ON -> true
        OPTION_OFF -> false
        OPTION_DEFAULT -> defaults.marquee
        else -> true
    }
    val dynamicHighlightEnabled = patch.dynamicHighlightColor in setOf(OPTION_ON, "dark", "darker") ||
        (patch.dynamicHighlightColor == OPTION_DEFAULT && defaults.dynamicHighlightColor)
    val hasHighlightColor = dynamicHighlightEnabled || !patch.highlightColor.isNullOrBlank()
    val islandGlowFollowsDynamic = patch.islandOuterGlow == OPTION_FOLLOW_DYNAMIC ||
        (patch.islandOuterGlow == OPTION_DEFAULT && defaults.islandOuterGlow == OPTION_FOLLOW_DYNAMIC)
    val focusGlowFollowsDynamic = patch.outerGlow == OPTION_FOLLOW_DYNAMIC ||
        (patch.outerGlow == OPTION_DEFAULT && defaults.outerGlow == OPTION_FOLLOW_DYNAMIC)

    fun applyPatch() {
        if (!patch.hasChanges || applying) return
        applying = true
        scope.launch {
            withContext(Dispatchers.IO) {
                when (target) {
                    is BatchChannelTarget.Channels -> prefs.applyChannelSettingsPatch(
                        target.packageName,
                        target.channelIds,
                        patch,
                    )
                    is BatchChannelTarget.Apps -> {
                        val channelRepository = NotificationChannelsRepository()
                        target.packageNames.forEach { packageName ->
                            val channels = channelRepository.load(packageName).orEmpty()
                            val enabled = prefs.enabledChannelIds(packageName)
                            val ids = if (enabled.isEmpty()) channels.map { it.id } else channels
                                .map { it.id }.filter { it in enabled }
                            prefs.applyChannelSettingsPatch(packageName, ids, patch)
                        }
                    }
                }
            }
            applying = false
            onBack()
        }
    }

    DetailPage(
        title = stringResource(R.string.compose_batch_channel_settings),
        onBack = onBack,
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        when (target) {
                            is BatchChannelTarget.Channels -> R.string.compose_batch_channel_scope_channels
                            is BatchChannelTarget.Apps -> R.string.compose_batch_channel_scope_apps
                        },
                        when (target) {
                            is BatchChannelTarget.Channels -> target.channelIds.size
                            is BatchChannelTarget.Apps -> target.packageNames.size
                        },
                    ),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        item {
            SectionTitle(stringResource(R.string.compose_channel_template_section))
            Card {
                PatchDropdown(
                    title = stringResource(R.string.compose_channel_template),
                    value = patch.template,
                    values = listOf(null, TEMPLATE_PROGRESS, TEMPLATE_NOTIFICATION, TEMPLATE_AI_NOTIFICATION),
                    labels = listOf(
                        noChange,
                        stringResource(R.string.compose_template_progress),
                        stringResource(R.string.compose_template_notification),
                        stringResource(R.string.compose_template_ai_notification),
                    ),
                ) { patch = patch.copy(template = it) }
                PatchDropdown(
                    title = stringResource(R.string.compose_channel_renderer),
                    value = patch.renderer,
                    values = listOf(
                        null,
                        RENDERER_IMAGE_TEXT_BUTTONS,
                        RENDERER_IMAGE_TEXT_WRAP,
                        RENDERER_IMAGE_TEXT_RIGHT_BUTTON,
                        RENDERER_IMAGE_TEXT_PROGRESS,
                    ),
                    labels = listOf(
                        noChange,
                        stringResource(R.string.compose_renderer_image_text_buttons),
                        stringResource(R.string.compose_renderer_image_text_wrap),
                        stringResource(R.string.compose_renderer_image_text_right_button),
                        stringResource(R.string.compose_renderer_image_text_progress),
                    ),
                ) { patch = patch.copy(renderer = it) }
            }
        }
        item {
            SectionTitle(stringResource(R.string.compose_island))
            Card {
                PatchDropdown(
                    title = stringResource(R.string.compose_channel_enable_island),
                    value = patch.islandEnabled,
                    values = boolValues,
                    labels = boolLabels,
                    enabled = focusEnabled,
                ) { patch = patch.copy(islandEnabled = it) }
                AnimatedVisibility(visible = islandVisible) {
                    Column {
                        PatchDropdown(
                            title = stringResource(R.string.compose_channel_icon_source),
                            value = patch.iconMode,
                            values = listOf(null, ICON_AUTO, ICON_NOTIFICATION_SMALL, ICON_NOTIFICATION_LARGE, ICON_APP),
                            labels = listOf(
                                noChange,
                                stringResource(R.string.compose_icon_auto),
                                stringResource(R.string.compose_icon_notification_small),
                                stringResource(R.string.compose_icon_notification_large),
                                stringResource(R.string.compose_icon_app),
                            ),
                        ) { patch = patch.copy(iconMode = it) }
                        PatchDropdown(stringResource(R.string.compose_island_icon), patch.showIslandIcon, triValues, triLabels) {
                            patch = patch.copy(showIslandIcon = it)
                        }
                        PatchDropdown(stringResource(R.string.compose_first_float), patch.firstFloat, triValues, triLabels) {
                            patch = patch.copy(firstFloat = it)
                        }
                        PatchDropdown(stringResource(R.string.compose_update_float), patch.enableFloat, triValues, triLabels) {
                            patch = patch.copy(enableFloat = it)
                        }
                        PatchDropdown(stringResource(R.string.compose_marquee_channel), patch.marquee, triValues, triLabels) {
                            patch = patch.copy(marquee = it)
                        }
                        PatchDropdown(
                            stringResource(R.string.compose_marquee_auto_hide),
                            patch.marqueeAutoHide,
                            listOf(null, OPTION_DEFAULT, OPTION_OFF, "1", "2", "1_override", "2_override"),
                            listOf(
                                noChange,
                                default,
                                off,
                                stringResource(R.string.compose_marquee_once),
                                stringResource(R.string.compose_marquee_twice),
                                stringResource(R.string.compose_marquee_once_override),
                                stringResource(R.string.compose_marquee_twice_override),
                            ),
                            enabled = marqueeEnabled,
                        ) { patch = patch.copy(marqueeAutoHide = it) }
                        ArrowPreference(
                            title = stringResource(R.string.compose_auto_disappear),
                            summary = patch.timeout ?: noChange,
                            insideMargin = BATCH_MARGIN,
                            onClick = {
                                timeoutDraft = patch.timeout?.takeUnless { it == OPTION_DEFAULT }.orEmpty()
                                timeoutDialog = true
                            },
                        )
                        PatchDropdown(
                            stringResource(R.string.compose_island_outer_glow),
                            patch.islandOuterGlow,
                            listOf(null, OPTION_DEFAULT, OPTION_ON, OPTION_OFF, OPTION_FOLLOW_DYNAMIC),
                            listOf(noChange, default, on, off, stringResource(R.string.compose_follow_dynamic_color)),
                        ) { patch = patch.copy(islandOuterGlow = it) }
                        BatchColorPreference(
                            stringResource(R.string.compose_out_effect_color),
                            patch.islandOuterGlowColor,
                            noChange,
                            enabled = !islandGlowFollowsDynamic,
                        ) {
                            colorDraft = parseHexColor(patch.islandOuterGlowColor.orEmpty())
                            colorTarget = BatchColorTarget.Island
                        }
                        PatchDropdown(
                            stringResource(R.string.compose_dynamic_highlight_color),
                            patch.dynamicHighlightColor,
                            listOf(null, OPTION_DEFAULT, OPTION_OFF, OPTION_ON, "dark", "darker"),
                            listOf(
                                noChange,
                                default,
                                off,
                                on,
                                stringResource(R.string.compose_dynamic_dark),
                                stringResource(R.string.compose_dynamic_darker),
                            ),
                        ) { patch = patch.copy(dynamicHighlightColor = it) }
                        BatchColorPreference(
                            stringResource(R.string.compose_highlight_color),
                            patch.highlightColor,
                            noChange,
                            enabled = !dynamicHighlightEnabled,
                        ) {
                            colorDraft = parseHexColor(patch.highlightColor.orEmpty())
                            colorTarget = BatchColorTarget.Highlight
                        }
                        PatchDropdown(
                            stringResource(R.string.compose_channel_left_text_highlight),
                            patch.showLeftHighlight,
                            triValues,
                            triLabels,
                            enabled = hasHighlightColor,
                        ) {
                            patch = patch.copy(showLeftHighlight = it)
                        }
                        PatchDropdown(
                            stringResource(R.string.compose_channel_right_text_highlight),
                            patch.showRightHighlight,
                            triValues,
                            triLabels,
                            enabled = hasHighlightColor,
                        ) {
                            patch = patch.copy(showRightHighlight = it)
                        }
                        PatchDropdown(stringResource(R.string.compose_channel_left_narrow_font), patch.showLeftNarrowFont, triValues, triLabels) {
                            patch = patch.copy(showLeftNarrowFont = it)
                        }
                        PatchDropdown(stringResource(R.string.compose_channel_right_narrow_font), patch.showRightNarrowFont, triValues, triLabels) {
                            patch = patch.copy(showRightNarrowFont = it)
                        }
                    }
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.compose_focus_notification))
            Card {
                PatchDropdown(stringResource(R.string.compose_focus_notification), patch.focus, triValues, triLabels) {
                    patch = if (it == OPTION_OFF) {
                        patch.copy(
                            focus = it,
                            showNotification = OPTION_ON,
                            preserveSmallIcon = OPTION_OFF,
                            islandEnabled = true,
                        )
                    } else {
                        patch.copy(focus = it)
                    }
                }
                AnimatedVisibility(visible = focusEnabled) {
                    Column {
                        PatchDropdown(
                            stringResource(R.string.compose_channel_hide_notification),
                            patch.showNotification?.let { it == OPTION_OFF },
                            boolValues,
                            boolLabels,
                        ) { hidden -> patch = patch.copy(showNotification = hidden?.let { if (it) OPTION_OFF else OPTION_ON }) }
                        PatchDropdown(
                            stringResource(R.string.compose_preserve_small_icon),
                            patch.preserveSmallIcon,
                            triValues,
                            triLabels,
                        ) { patch = patch.copy(preserveSmallIcon = it) }
                        PatchDropdown(
                            stringResource(R.string.compose_restore_lockscreen),
                            patch.restoreLockscreen,
                            triValues,
                            triLabels,
                        ) { patch = patch.copy(restoreLockscreen = it) }
                    }
                }
                PatchDropdown(
                    stringResource(R.string.compose_focus_outer_glow),
                    patch.outerGlow,
                    listOf(null, OPTION_DEFAULT, OPTION_ON, OPTION_OFF, OPTION_FOLLOW_DYNAMIC),
                    listOf(noChange, default, on, off, stringResource(R.string.compose_follow_dynamic_color)),
                ) { patch = patch.copy(outerGlow = it) }
                BatchColorPreference(
                    stringResource(R.string.compose_out_effect_color),
                    patch.outEffectColor,
                    noChange,
                    enabled = !focusGlowFollowsDynamic,
                ) {
                    colorDraft = parseHexColor(patch.outEffectColor.orEmpty())
                    colorTarget = BatchColorTarget.Focus
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.compose_filter_rules))
            Card {
                PatchDropdown(
                    stringResource(R.string.compose_filter_mode),
                    patch.filterMode,
                    listOf(null, FILTER_BLACKLIST, FILTER_WHITELIST),
                    listOf(
                        noChange,
                        stringResource(R.string.compose_filter_blacklist),
                        stringResource(R.string.compose_filter_whitelist),
                    ),
                ) { patch = patch.copy(filterMode = it) }
                ArrowPreference(
                    title = stringResource(R.string.compose_whitelist_keywords),
                    summary = batchKeywordSummary(patch.whitelistKeywords, noChange),
                    enabled = patch.filterMode == FILTER_WHITELIST,
                    insideMargin = BATCH_MARGIN,
                    onClick = { keywordTarget = BatchKeywordTarget.Whitelist },
                )
                ArrowPreference(
                    title = stringResource(R.string.compose_blacklist_keywords),
                    summary = batchKeywordSummary(patch.blacklistKeywords, noChange),
                    insideMargin = BATCH_MARGIN,
                    onClick = { keywordTarget = BatchKeywordTarget.Blacklist },
                )
            }
        }
        item {
            Column {
                SectionTitle(stringResource(R.string.compose_channel_aod_section))
                Card {
                    PatchDropdown(
                        stringResource(R.string.compose_aod_text),
                        patch.aodText,
                        triValues,
                        triLabels,
                        enabled = focusEnabled,
                    ) {
                        patch = patch.copy(aodText = it)
                    }
                }
            }
        }
        item {
            Button(
                onClick = ::applyPatch,
                modifier = Modifier.fillMaxWidth(),
                enabled = patch.hasChanges && !applying,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(if (applying) R.string.compose_applying else R.string.compose_apply))
            }
        }
    }

    WindowDialog(
        show = timeoutDialog,
        title = stringResource(R.string.compose_auto_disappear),
        onDismissRequest = { timeoutDialog = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextField(
                value = timeoutDraft,
                onValueChange = { timeoutDraft = it.filter(Char::isDigit).take(9) },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.compose_seconds),
                useLabelAsPlaceholder = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.compose_no_change),
                    onClick = { patch = patch.copy(timeout = null); timeoutDialog = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.compose_restore_default),
                    onClick = { patch = patch.copy(timeout = OPTION_DEFAULT); timeoutDialog = false },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        timeoutDraft.toIntOrNull()?.takeIf { it >= 1 }?.let {
                            patch = patch.copy(timeout = it.toString())
                        }
                        timeoutDialog = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text(stringResource(R.string.compose_save)) }
            }
        }
    }

    ColorPaletteDialog(
        show = colorTarget != null,
        title = if (colorTarget == BatchColorTarget.Highlight) {
            stringResource(R.string.compose_highlight_color)
        } else {
            stringResource(R.string.compose_out_effect_color)
        },
        initialColor = colorDraft,
        onDismiss = { colorTarget = null },
        onDelete = {
            patch = when (colorTarget) {
                BatchColorTarget.Highlight -> patch.copy(highlightColor = "")
                BatchColorTarget.Island -> patch.copy(islandOuterGlowColor = "")
                BatchColorTarget.Focus -> patch.copy(outEffectColor = "")
                null -> patch
            }
            colorTarget = null
        },
        onSave = { color ->
            patch = when (colorTarget) {
                BatchColorTarget.Highlight -> patch.copy(highlightColor = color.toArgbHex())
                BatchColorTarget.Island -> patch.copy(islandOuterGlowColor = color.toArgbHex())
                BatchColorTarget.Focus -> patch.copy(outEffectColor = color.toArgbHex())
                null -> patch
            }
            colorTarget = null
        },
    )

    KeywordListDialog(
        show = keywordTarget != null,
        title = stringResource(
            if (keywordTarget == BatchKeywordTarget.Whitelist) {
                R.string.compose_whitelist_keywords
            } else {
                R.string.compose_blacklist_keywords
            },
        ),
        keywords = when (keywordTarget) {
            BatchKeywordTarget.Whitelist -> patch.whitelistKeywords.orEmpty()
            BatchKeywordTarget.Blacklist -> patch.blacklistKeywords.orEmpty()
            null -> emptyList()
        },
        onDismiss = { keywordTarget = null },
        onSave = { values ->
            patch = if (keywordTarget == BatchKeywordTarget.Whitelist) {
                patch.copy(whitelistKeywords = values)
            } else {
                patch.copy(blacklistKeywords = values)
            }
            keywordTarget = null
        },
    )
}

@Composable
private fun <T> PatchDropdown(
    title: String,
    value: T?,
    values: List<T?>,
    labels: List<String>,
    enabled: Boolean = true,
    onChange: (T?) -> Unit,
) {
    PreferenceDropdown(
        title = title,
        summary = null,
        icon = null,
        items = labels,
        selectedIndex = values.indexOf(value).coerceAtLeast(0),
        enabled = enabled,
        insideMargin = BATCH_MARGIN,
        onSelectedIndexChange = { onChange(values[it]) },
    )
}

@Composable
private fun BatchColorPreference(
    title: String,
    value: String?,
    noChange: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = title,
        summary = value ?: noChange,
        enabled = enabled,
        insideMargin = BATCH_MARGIN,
        onClick = onClick,
    )
}

@Composable
private fun batchKeywordSummary(values: List<String>?, noChange: String): String = when {
    values == null -> noChange
    values.isEmpty() -> stringResource(R.string.compose_not_configured)
    else -> stringResource(R.string.compose_keyword_count, values.size)
}

private enum class BatchColorTarget { Highlight, Island, Focus }
private enum class BatchKeywordTarget { Whitelist, Blacklist }
private val BATCH_MARGIN = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
