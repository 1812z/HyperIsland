package io.github.hyperisland.compose.page.apps.channel

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.ColorPaletteDialog
import io.github.hyperisland.compose.component.BACKGROUND_PARALLAX
import io.github.hyperisland.compose.component.BACKGROUND_SCALE_REDUCTION
import io.github.hyperisland.compose.component.BarBackdropContent
import io.github.hyperisland.compose.component.BarBlurHost
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.EFFECT_VISIBILITY_THRESHOLD
import io.github.hyperisland.compose.component.KeywordListDialog
import io.github.hyperisland.compose.component.LAYER_ENTER_DURATION
import io.github.hyperisland.compose.component.LAYER_EXIT_DURATION
import io.github.hyperisland.compose.component.LocalBarBlurEnabled
import io.github.hyperisland.compose.component.PREDICTIVE_CANCEL_DURATION
import io.github.hyperisland.compose.component.PREDICTIVE_DISMISS_DURATION
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.PredictiveBackBackdrop
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsItemMargin
import io.github.hyperisland.compose.component.parseHexColor
import io.github.hyperisland.compose.component.predictiveEffectIntensity
import io.github.hyperisland.compose.component.predictiveExitProgress
import io.github.hyperisland.compose.component.predictiveSettleDuration
import io.github.hyperisland.compose.component.predictiveTranslationFraction
import io.github.hyperisland.compose.component.smootherStep
import io.github.hyperisland.compose.component.toArgbHex
import io.github.hyperisland.compose.data.DefaultConfigSettings
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.NotificationChannelInfo
import io.github.hyperisland.compose.data.rememberLongPreference
import io.github.hyperisland.compose.data.channel.ChannelCustomizationTarget
import io.github.hyperisland.compose.data.channel.ChannelSettings
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
import io.github.hyperisland.compose.theme.DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT
import io.github.hyperisland.compose.theme.PREF_PREDICTIVE_BACK_MAX_TRANSLATION
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun ChannelEditorPage(
    appPackage: String,
    channel: NotificationChannelInfo,
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
) {
    var settings by remember(appPackage, channel.id) {
        mutableStateOf(prefs.channelSettings(appPackage, channel.id))
    }
    val defaults = remember { prefs.defaultConfigSettings() }
    var customizationTarget by remember { mutableStateOf<ChannelCustomizationTarget?>(null) }
    var customizationVisible by remember { mutableStateOf(false) }
    var predictiveBackActive by remember { mutableStateOf(false) }
    var predictiveCommitting by remember { mutableStateOf(false) }
    val predictiveProgress = remember { Animatable(0f) }
    val backdropIntensity = remember { Animatable(0f) }
    val editorLayerDepth = remember { Animatable(0f) }
    val predictiveBackMaxTranslation = rememberLongPreference(
        prefs,
        PREF_PREDICTIVE_BACK_MAX_TRANSLATION,
        DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT,
    )
    val blurBarsEnabled = LocalBarBlurEnabled.current

    fun update(value: ChannelSettings) {
        settings = value
        prefs.setChannelSettings(appPackage, channel.id, value)
    }

    fun closeCustomization() {
        customizationVisible = false
    }

    LaunchedEffect(customizationVisible, predictiveBackActive) {
        if (!predictiveBackActive) {
            val target = if (customizationVisible) 1f else 0f
            val duration = if (customizationVisible) LAYER_ENTER_DURATION else LAYER_EXIT_DURATION
            coroutineScope {
                launch {
                    backdropIntensity.animateTo(
                        target,
                        tween(duration, easing = FastOutSlowInEasing),
                    )
                }
                launch {
                    editorLayerDepth.animateTo(
                        target,
                        tween(duration, easing = FastOutSlowInEasing),
                    )
                }
            }
        }
    }

    suspend fun finishPredictiveBack() {
        predictiveCommitting = true
        val duration = predictiveSettleDuration(
            progress = predictiveProgress.value,
            maxTranslationPercent = predictiveBackMaxTranslation.value,
        )
        coroutineScope {
            launch {
                predictiveProgress.animateTo(
                    predictiveExitProgress(predictiveBackMaxTranslation.value),
                    tween(duration, easing = LinearEasing),
                )
            }
            launch { backdropIntensity.animateTo(0f, tween(duration, easing = LinearEasing)) }
            launch { editorLayerDepth.animateTo(0f, tween(duration, easing = LinearEasing)) }
        }
        closeCustomization()
        delay(PREDICTIVE_DISMISS_DURATION.toLong())
        predictiveProgress.snapTo(0f)
        predictiveBackActive = false
        predictiveCommitting = false
    }

    PredictiveBackHandler(enabled = customizationVisible) { events ->
        try {
            events.collect { event ->
                predictiveBackActive = true
                predictiveProgress.snapTo(event.progress)
                val smoothProgress = smootherStep(event.progress)
                backdropIntensity.snapTo(predictiveEffectIntensity(smoothProgress))
                editorLayerDepth.snapTo(1f - smoothProgress)
            }
            finishPredictiveBack()
        } catch (_: CancellationException) {
            if (!predictiveCommitting) {
                coroutineScope {
                    launch {
                        predictiveProgress.animateTo(
                            0f,
                            tween(PREDICTIVE_CANCEL_DURATION, easing = FastOutSlowInEasing),
                        )
                    }
                    launch {
                        backdropIntensity.animateTo(
                            1f,
                            tween(PREDICTIVE_CANCEL_DURATION, easing = FastOutSlowInEasing),
                        )
                    }
                    launch {
                        editorLayerDepth.animateTo(
                            1f,
                            tween(PREDICTIVE_CANCEL_DURATION, easing = FastOutSlowInEasing),
                        )
                    }
                }
                predictiveBackActive = false
            }
        }
    }

    BarBlurHost(
        enabled = blurBarsEnabled,
        captureForEffects = customizationVisible || predictiveBackActive ||
            backdropIntensity.value > EFFECT_VISIBILITY_THRESHOLD,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val depth = editorLayerDepth.value.coerceIn(0f, 1f)
                            scaleX = 1f - depth * BACKGROUND_SCALE_REDUCTION
                            scaleY = scaleX
                            translationX = -size.width * depth * BACKGROUND_PARALLAX
                        },
                ) {
                    ChannelEditorContent(
                        channelName = channel.name,
                        settings = settings,
                        defaults = defaults,
                        onUpdate = ::update,
                        onOpenCustomization = {
                            customizationTarget = it
                            customizationVisible = true
                        },
                        onBack = onBack,
                    )
                }
            }
            PredictiveBackBackdrop(
                intensity = backdropIntensity.value,
                visible = backdropIntensity.value > EFFECT_VISIBILITY_THRESHOLD,
                modifier = Modifier.fillMaxSize(),
            )
            AnimatedVisibility(
                visible = customizationVisible,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = predictiveProgress.value.coerceAtLeast(0f)
                        translationX = size.width * progress *
                            predictiveTranslationFraction(predictiveBackMaxTranslation.value)
                    },
                enter = slideInHorizontally(
                    tween(LAYER_ENTER_DURATION, easing = FastOutSlowInEasing),
                ) { it },
                exit = if (predictiveBackActive) {
                    ExitTransition.None
                } else {
                    slideOutHorizontally(
                        tween(LAYER_EXIT_DURATION, easing = FastOutSlowInEasing),
                    ) { it }
                },
            ) {
                val target = customizationTarget
                if (target != null) {
                    ChannelCustomizationPage(
                        target = target,
                        template = settings.template,
                        renderer = settings.renderer,
                        rawConfig = when (target) {
                            ChannelCustomizationTarget.Island -> settings.islandCustom
                            ChannelCustomizationTarget.Focus -> settings.focusCustom
                            ChannelCustomizationTarget.Aod -> settings.aodCustom
                        },
                        onSave = { raw ->
                            update(
                                when (target) {
                                    ChannelCustomizationTarget.Island -> settings.copy(islandCustom = raw)
                                    ChannelCustomizationTarget.Focus -> settings.copy(focusCustom = raw)
                                    ChannelCustomizationTarget.Aod -> settings.copy(aodCustom = raw)
                                },
                            )
                            closeCustomization()
                        },
                        onBack = ::closeCustomization,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelEditorContent(
    channelName: String,
    settings: ChannelSettings,
    defaults: DefaultConfigSettings,
    onUpdate: (ChannelSettings) -> Unit,
    onOpenCustomization: (ChannelCustomizationTarget) -> Unit,
    onBack: () -> Unit,
) {
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var timeoutDraft by remember { mutableStateOf("") }
    var activeColor by remember { mutableStateOf<ChannelColorTarget?>(null) }
    var selectedColor by remember { mutableStateOf(Color.Red) }
    var keywordTarget by remember { mutableStateOf<KeywordTarget?>(null) }

    val focusEnabled = resolveOption(settings.focus, defaults.focusNotification)
    val marqueeEnabled = resolveOption(settings.marquee, defaults.marquee)
    val dynamicHighlightEnabled = when (settings.dynamicHighlightColor) {
        OPTION_ON, "dark", "darker" -> true
        OPTION_OFF -> false
        else -> defaults.dynamicHighlightColor
    }
    val hasHighlightColor = dynamicHighlightEnabled || settings.highlightColor.isNotBlank()
    val islandVisible = settings.islandEnabled
    val aodEnabled = resolveOption(settings.aodText, defaults.aodText)
    val islandGlow = resolveMode(settings.islandOuterGlow, defaults.islandOuterGlow)
    val focusGlow = resolveMode(settings.outerGlow, defaults.outerGlow)

    val triValues = remember { listOf(OPTION_DEFAULT, OPTION_ON, OPTION_OFF) }
    @Composable
    fun triLabels(default: Boolean) = listOf(
        defaultOptionLabel(default, true),
        stringResource(R.string.compose_enabled_option),
        stringResource(R.string.compose_disabled_option),
    )
    val glowValues = remember { listOf(OPTION_DEFAULT, OPTION_ON, OPTION_OFF, OPTION_FOLLOW_DYNAMIC) }
    @Composable
    fun glowLabels(default: String) = listOf(
        stringResource(R.string.compose_default_with_value, glowModeLabel(default)),
        stringResource(R.string.compose_enabled_option),
        stringResource(R.string.compose_disabled_option),
        stringResource(R.string.compose_follow_dynamic_color),
    )

    DetailPage(title = channelName, onBack = onBack) {
        item {
            SectionTitle(stringResource(R.string.compose_channel_template_section))
            Card {
                PreferenceDropdown(
                    title = stringResource(R.string.compose_channel_template),
                    summary = null,
                    icon = null,
                    items = listOf(
                        stringResource(R.string.compose_template_progress),
                        stringResource(R.string.compose_template_notification),
                        stringResource(R.string.compose_template_ai_notification),
                    ),
                    selectedIndex = listOf(TEMPLATE_PROGRESS, TEMPLATE_NOTIFICATION, TEMPLATE_AI_NOTIFICATION)
                        .indexOf(settings.template).coerceAtLeast(0),
                    insideMargin = CHANNEL_EDITOR_MARGIN,
                ) { index ->
                    onUpdate(settings.copy(template = listOf(TEMPLATE_PROGRESS, TEMPLATE_NOTIFICATION, TEMPLATE_AI_NOTIFICATION)[index]))
                }
                PreferenceDropdown(
                    title = stringResource(R.string.compose_channel_renderer),
                    summary = null,
                    icon = null,
                    items = listOf(
                        stringResource(R.string.compose_renderer_image_text_buttons),
                        stringResource(R.string.compose_renderer_image_text_wrap),
                        stringResource(R.string.compose_renderer_image_text_right_button),
                        stringResource(R.string.compose_renderer_image_text_progress),
                    ),
                    selectedIndex = listOf(
                        RENDERER_IMAGE_TEXT_BUTTONS,
                        RENDERER_IMAGE_TEXT_WRAP,
                        RENDERER_IMAGE_TEXT_RIGHT_BUTTON,
                        RENDERER_IMAGE_TEXT_PROGRESS,
                    ).indexOf(settings.renderer).coerceAtLeast(0),
                    insideMargin = CHANNEL_EDITOR_MARGIN,
                ) { index ->
                    onUpdate(
                        settings.copy(
                            renderer = listOf(
                                RENDERER_IMAGE_TEXT_BUTTONS,
                                RENDERER_IMAGE_TEXT_WRAP,
                                RENDERER_IMAGE_TEXT_RIGHT_BUTTON,
                                RENDERER_IMAGE_TEXT_PROGRESS,
                            )[index],
                        ),
                    )
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.compose_island))
            Card {
                SwitchPreference(
                    title = stringResource(R.string.compose_channel_enable_island),
                    checked = settings.islandEnabled,
                    enabled = focusEnabled,
                    insideMargin = CHANNEL_EDITOR_MARGIN,
                    onCheckedChange = { onUpdate(settings.copy(islandEnabled = it)) },
                )
                AnimatedVisibility(visible = islandVisible) {
                    Column {
                        PreferenceDropdown(
                            title = stringResource(R.string.compose_channel_icon_source),
                            summary = null,
                            icon = null,
                            items = listOf(
                                stringResource(R.string.compose_icon_auto),
                                stringResource(R.string.compose_icon_notification_small),
                                stringResource(R.string.compose_icon_notification_large),
                                stringResource(R.string.compose_icon_app),
                            ),
                            selectedIndex = listOf(ICON_AUTO, ICON_NOTIFICATION_SMALL, ICON_NOTIFICATION_LARGE, ICON_APP)
                                .indexOf(settings.iconMode).coerceAtLeast(0),
                            insideMargin = CHANNEL_EDITOR_MARGIN,
                        ) { index ->
                            onUpdate(settings.copy(iconMode = listOf(ICON_AUTO, ICON_NOTIFICATION_SMALL, ICON_NOTIFICATION_LARGE, ICON_APP)[index]))
                        }
                        TriStatePreference(
                            title = stringResource(R.string.compose_island_icon),
                            value = settings.showIslandIcon,
                            labels = triLabels(defaults.showIslandIcon),
                            values = triValues,
                        ) { onUpdate(settings.copy(showIslandIcon = it)) }
                        TriStatePreference(
                            title = stringResource(R.string.compose_first_float),
                            value = settings.firstFloat,
                            labels = triLabels(defaults.firstFloat),
                            values = triValues,
                        ) { onUpdate(settings.copy(firstFloat = it)) }
                        TriStatePreference(
                            title = stringResource(R.string.compose_update_float),
                            value = settings.enableFloat,
                            labels = triLabels(defaults.enableFloat),
                            values = triValues,
                        ) { onUpdate(settings.copy(enableFloat = it)) }
                        TriStatePreference(
                            title = stringResource(R.string.compose_marquee_channel),
                            value = settings.marquee,
                            labels = triLabels(defaults.marquee),
                            values = triValues,
                        ) { onUpdate(settings.copy(marquee = it)) }
                        val autoHideValues = listOf(OPTION_DEFAULT, OPTION_OFF, "1", "2", "1_override", "2_override")
                        PreferenceDropdown(
                            title = stringResource(R.string.compose_marquee_auto_hide),
                            summary = null,
                            icon = null,
                            items = listOf(
                                stringResource(R.string.compose_default_with_value, marqueeAutoHideLabel(defaults.marqueeAutoHide)),
                                stringResource(R.string.compose_disabled_option),
                                stringResource(R.string.compose_marquee_once),
                                stringResource(R.string.compose_marquee_twice),
                                stringResource(R.string.compose_marquee_once_override),
                                stringResource(R.string.compose_marquee_twice_override),
                            ),
                            selectedIndex = autoHideValues.indexOf(settings.marqueeAutoHide).coerceAtLeast(0),
                            enabled = marqueeEnabled,
                            insideMargin = CHANNEL_EDITOR_MARGIN,
                        ) { onUpdate(settings.copy(marqueeAutoHide = autoHideValues[it])) }
                        ArrowPreference(
                            title = stringResource(R.string.compose_auto_disappear),
                            summary = if (settings.timeout == OPTION_DEFAULT) {
                                stringResource(R.string.compose_default_timeout_seconds, defaults.timeout)
                            } else {
                                stringResource(R.string.compose_timeout_seconds_value, settings.timeout.toIntOrNull() ?: defaults.timeout)
                            },
                            insideMargin = CHANNEL_EDITOR_MARGIN,
                            onClick = {
                                timeoutDraft = settings.timeout.takeUnless { it == OPTION_DEFAULT }.orEmpty()
                                showTimeoutDialog = true
                            },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.compose_channel_island_customization),
                            insideMargin = CHANNEL_EDITOR_MARGIN,
                            onClick = { onOpenCustomization(ChannelCustomizationTarget.Island) },
                        )
                    }
                }
            }
        }
        AnimatedSection(
            visible = islandVisible,
            titleRes = R.string.compose_appearance,
        ) {
            PreferenceDropdown(
                title = stringResource(R.string.compose_island_outer_glow),
                summary = null,
                icon = null,
                items = glowLabels(defaults.islandOuterGlow),
                selectedIndex = glowValues.indexOf(settings.islandOuterGlow).coerceAtLeast(0),
                insideMargin = CHANNEL_EDITOR_MARGIN,
            ) { onUpdate(settings.copy(islandOuterGlow = glowValues[it])) }
            ColorPreference(
                title = stringResource(R.string.compose_out_effect_color),
                value = settings.islandOuterGlowColor,
                enabled = islandGlow != OPTION_FOLLOW_DYNAMIC,
            ) {
                selectedColor = parseHexColor(settings.islandOuterGlowColor)
                activeColor = ChannelColorTarget.IslandGlow
            }
            val dynamicValues = listOf(OPTION_DEFAULT, OPTION_OFF, OPTION_ON, "dark", "darker")
            PreferenceDropdown(
                title = stringResource(R.string.compose_dynamic_highlight_color),
                summary = null,
                icon = null,
                items = listOf(
                    stringResource(R.string.compose_default_with_value, enabledLabel(defaults.dynamicHighlightColor)),
                    stringResource(R.string.compose_disabled_option),
                    stringResource(R.string.compose_enabled_option),
                    stringResource(R.string.compose_dynamic_dark),
                    stringResource(R.string.compose_dynamic_darker),
                ),
                selectedIndex = dynamicValues.indexOf(settings.dynamicHighlightColor).coerceAtLeast(0),
                insideMargin = CHANNEL_EDITOR_MARGIN,
            ) { onUpdate(settings.copy(dynamicHighlightColor = dynamicValues[it])) }
            ColorPreference(
                title = stringResource(R.string.compose_highlight_color),
                value = settings.highlightColor,
                enabled = !dynamicHighlightEnabled,
            ) {
                selectedColor = parseHexColor(settings.highlightColor)
                activeColor = ChannelColorTarget.Highlight
            }
            OptionSwitch(
                title = stringResource(R.string.compose_channel_left_text_highlight),
                value = settings.showLeftHighlight,
                enabled = hasHighlightColor,
            ) { onUpdate(settings.copy(showLeftHighlight = if (it) OPTION_ON else OPTION_OFF)) }
            OptionSwitch(
                title = stringResource(R.string.compose_channel_right_text_highlight),
                value = settings.showRightHighlight,
                enabled = hasHighlightColor,
            ) { onUpdate(settings.copy(showRightHighlight = if (it) OPTION_ON else OPTION_OFF)) }
            OptionSwitch(
                title = stringResource(R.string.compose_channel_left_narrow_font),
                value = settings.showLeftNarrowFont,
            ) { onUpdate(settings.copy(showLeftNarrowFont = if (it) OPTION_ON else OPTION_OFF)) }
            OptionSwitch(
                title = stringResource(R.string.compose_channel_right_narrow_font),
                value = settings.showRightNarrowFont,
            ) { onUpdate(settings.copy(showRightNarrowFont = if (it) OPTION_ON else OPTION_OFF)) }
        }
        item {
            SectionTitle(stringResource(R.string.compose_focus_notification))
            Card {
                TriStatePreference(
                    title = stringResource(R.string.compose_focus_notification),
                    value = settings.focus,
                    labels = triLabels(defaults.focusNotification),
                    values = triValues,
                ) { value ->
                    onUpdate(
                        if (value != OPTION_OFF) settings.copy(focus = value)
                        else settings.copy(
                            focus = value,
                            showNotification = OPTION_ON,
                            preserveSmallIcon = OPTION_OFF,
                            islandEnabled = true,
                        ),
                    )
                }
                AnimatedVisibility(visible = focusEnabled) {
                    Column {
                        PreferenceSwitch(
                            title = stringResource(R.string.compose_channel_hide_notification),
                            summary = null,
                            icon = null,
                            checked = settings.showNotification == OPTION_OFF,
                            insideMargin = CHANNEL_EDITOR_MARGIN,
                        ) { onUpdate(settings.copy(showNotification = if (it) OPTION_OFF else OPTION_ON)) }
                        TriStatePreference(
                            title = stringResource(R.string.compose_preserve_small_icon),
                            value = settings.preserveSmallIcon,
                            labels = triLabels(defaults.preserveSmallIcon),
                            values = triValues,
                        ) { onUpdate(settings.copy(preserveSmallIcon = it)) }
                        TriStatePreference(
                            title = stringResource(R.string.compose_restore_lockscreen),
                            value = settings.restoreLockscreen,
                            labels = triLabels(defaults.restoreLockscreen),
                            values = triValues,
                        ) { onUpdate(settings.copy(restoreLockscreen = it)) }
                    }
                }
                PreferenceDropdown(
                    title = stringResource(R.string.compose_focus_outer_glow),
                    summary = null,
                    icon = null,
                    items = glowLabels(defaults.outerGlow),
                    selectedIndex = glowValues.indexOf(settings.outerGlow).coerceAtLeast(0),
                    insideMargin = CHANNEL_EDITOR_MARGIN,
                ) { onUpdate(settings.copy(outerGlow = glowValues[it])) }
                ColorPreference(
                    title = stringResource(R.string.compose_out_effect_color),
                    value = settings.outEffectColor,
                    enabled = focusGlow != OPTION_FOLLOW_DYNAMIC,
                ) {
                    selectedColor = parseHexColor(settings.outEffectColor)
                    activeColor = ChannelColorTarget.FocusGlow
                }
                ArrowPreference(
                    title = stringResource(R.string.compose_channel_focus_customization),
                    insideMargin = CHANNEL_EDITOR_MARGIN,
                    onClick = { onOpenCustomization(ChannelCustomizationTarget.Focus) },
                )
            }
        }
        item {
            SectionTitle(stringResource(R.string.compose_filter_rules))
            Card {
                PreferenceDropdown(
                    title = stringResource(R.string.compose_filter_mode),
                    summary = if (settings.filterMode == FILTER_WHITELIST) {
                        stringResource(R.string.compose_filter_whitelist_summary)
                    } else {
                        stringResource(R.string.compose_filter_blacklist_summary)
                    },
                    icon = null,
                    items = listOf(
                        stringResource(R.string.compose_filter_blacklist),
                        stringResource(R.string.compose_filter_whitelist),
                    ),
                    selectedIndex = if (settings.filterMode == FILTER_WHITELIST) 1 else 0,
                    insideMargin = CHANNEL_EDITOR_MARGIN,
                ) { onUpdate(settings.copy(filterMode = if (it == 1) FILTER_WHITELIST else FILTER_BLACKLIST)) }
                ArrowPreference(
                    title = stringResource(R.string.compose_whitelist_keywords),
                    summary = keywordSummary(settings.whitelistKeywords),
                    enabled = settings.filterMode == FILTER_WHITELIST,
                    insideMargin = CHANNEL_EDITOR_MARGIN,
                    onClick = { keywordTarget = KeywordTarget.Whitelist },
                )
                ArrowPreference(
                    title = stringResource(R.string.compose_blacklist_keywords),
                    summary = keywordSummary(settings.blacklistKeywords),
                    insideMargin = CHANNEL_EDITOR_MARGIN,
                    onClick = { keywordTarget = KeywordTarget.Blacklist },
                )
            }
        }
        AnimatedSection(visible = true, titleRes = R.string.compose_channel_aod_section) {
            TriStatePreference(
                title = stringResource(R.string.compose_aod_text),
                value = settings.aodText,
                labels = triLabels(defaults.aodText),
                values = triValues,
                enabled = focusEnabled,
            ) { onUpdate(settings.copy(aodText = it)) }
            AnimatedVisibility(visible = focusEnabled && aodEnabled) {
                ArrowPreference(
                    title = stringResource(R.string.compose_channel_aod_customization),
                    insideMargin = CHANNEL_EDITOR_MARGIN,
                    onClick = { onOpenCustomization(ChannelCustomizationTarget.Aod) },
                )
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
                value = timeoutDraft,
                onValueChange = { timeoutDraft = it.filter(Char::isDigit).take(9) },
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
                    text = stringResource(R.string.compose_restore_default),
                    onClick = {
                        onUpdate(settings.copy(timeout = OPTION_DEFAULT))
                        showTimeoutDialog = false
                    },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val timeout = timeoutDraft.toIntOrNull()?.takeIf { it >= 1 }?.toString()
                            ?: OPTION_DEFAULT
                        onUpdate(settings.copy(timeout = timeout))
                        showTimeoutDialog = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text(stringResource(R.string.compose_save)) }
            }
        }
    }

    ColorPaletteDialog(
        show = activeColor != null,
        title = when (activeColor) {
            ChannelColorTarget.Highlight -> stringResource(R.string.compose_highlight_color)
            else -> stringResource(R.string.compose_out_effect_color)
        },
        initialColor = selectedColor,
        onDismiss = { activeColor = null },
        onDelete = {
            onUpdate(
                when (activeColor) {
                    ChannelColorTarget.Highlight -> settings.copy(highlightColor = "")
                    ChannelColorTarget.IslandGlow -> settings.copy(islandOuterGlowColor = "")
                    ChannelColorTarget.FocusGlow -> settings.copy(outEffectColor = "")
                    null -> settings
                },
            )
            activeColor = null
        },
        onSave = { color ->
            onUpdate(
                when (activeColor) {
                    ChannelColorTarget.Highlight -> settings.copy(highlightColor = color.toArgbHex())
                    ChannelColorTarget.IslandGlow -> settings.copy(islandOuterGlowColor = color.toArgbHex())
                    ChannelColorTarget.FocusGlow -> settings.copy(outEffectColor = color.toArgbHex())
                    null -> settings
                },
            )
            activeColor = null
        },
    )

    KeywordListDialog(
        show = keywordTarget != null,
        title = stringResource(
            if (keywordTarget == KeywordTarget.Whitelist) {
                R.string.compose_whitelist_keywords
            } else {
                R.string.compose_blacklist_keywords
            },
        ),
        keywords = if (keywordTarget == KeywordTarget.Whitelist) {
            settings.whitelistKeywords
        } else {
            settings.blacklistKeywords
        },
        onDismiss = { keywordTarget = null },
        onSave = { keywords ->
            onUpdate(
                if (keywordTarget == KeywordTarget.Whitelist) {
                    settings.copy(whitelistKeywords = keywords)
                } else {
                    settings.copy(blacklistKeywords = keywords)
                },
            )
            keywordTarget = null
        },
    )
}

@Composable
private fun TriStatePreference(
    title: String,
    value: String,
    labels: List<String>,
    values: List<String>,
    enabled: Boolean = true,
    onChange: (String) -> Unit,
) {
    PreferenceDropdown(
        title = title,
        summary = null,
        icon = null,
        items = labels,
        selectedIndex = values.indexOf(value).coerceAtLeast(0),
        enabled = enabled,
        insideMargin = CHANNEL_EDITOR_MARGIN,
        onSelectedIndexChange = { onChange(values[it]) },
    )
}

@Composable
private fun OptionSwitch(
    title: String,
    value: String,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    PreferenceSwitch(
        title = title,
        summary = null,
        icon = null,
        checked = value == OPTION_ON,
        enabled = enabled,
        insideMargin = CHANNEL_EDITOR_MARGIN,
        onCheckedChange = onChange,
    )
}

@Composable
private fun ColorPreference(
    title: String,
    value: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = title,
        summary = value.takeIf(String::isNotEmpty),
        enabled = enabled,
        insideMargin = CHANNEL_EDITOR_MARGIN,
        endActions = { ChannelColorPreview(value) },
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

private fun androidx.compose.foundation.lazy.LazyListScope.AnimatedSection(
    visible: Boolean,
    titleRes: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    item {
        AnimatedVisibility(visible = visible) {
            Column {
                SectionTitle(stringResource(titleRes))
                Card(content = content)
            }
        }
    }
}

@Composable
private fun defaultOptionLabel(default: Boolean, includePrefix: Boolean): String {
    val label = enabledLabel(default)
    return if (includePrefix) stringResource(R.string.compose_default_with_value, label) else label
}

@Composable
private fun enabledLabel(enabled: Boolean): String = stringResource(
    if (enabled) R.string.compose_enabled_option else R.string.compose_disabled_option,
)

@Composable
private fun glowModeLabel(value: String): String = when (value) {
    OPTION_ON -> stringResource(R.string.compose_enabled_option)
    OPTION_FOLLOW_DYNAMIC -> stringResource(R.string.compose_follow_dynamic_color)
    else -> stringResource(R.string.compose_disabled_option)
}

@Composable
private fun marqueeAutoHideLabel(value: String): String = when (value) {
    "1" -> stringResource(R.string.compose_marquee_once)
    "2" -> stringResource(R.string.compose_marquee_twice)
    "1_override" -> stringResource(R.string.compose_marquee_once_override)
    "2_override" -> stringResource(R.string.compose_marquee_twice_override)
    else -> stringResource(R.string.compose_disabled_option)
}

@Composable
private fun keywordSummary(values: List<String>): String = if (values.isEmpty()) {
    stringResource(R.string.compose_not_configured)
} else {
    stringResource(R.string.compose_keyword_count, values.size)
}

private fun resolveOption(value: String, default: Boolean): Boolean = when (value) {
    OPTION_ON -> true
    OPTION_OFF -> false
    else -> default
}

private fun resolveMode(value: String, default: String): String =
    if (value == OPTION_DEFAULT) default else value

private enum class ChannelColorTarget { Highlight, IslandGlow, FocusGlow }
private enum class KeywordTarget { Whitelist, Blacklist }

private val CHANNEL_EDITOR_MARGIN = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
