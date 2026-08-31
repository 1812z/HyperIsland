package io.github.hyperisland.compose.page.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.ColorPaletteDialog
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsAction
import io.github.hyperisland.compose.component.SettingsItemMargin
import io.github.hyperisland.compose.component.keepisland.KeepIslandContentListDialog
import io.github.hyperisland.compose.component.keepisland.KeepIslandIntervalDialog
import io.github.hyperisland.compose.component.keepisland.KeepIslandPlaceholderSheet
import io.github.hyperisland.compose.component.keepisland.KeepIslandTextDialog
import io.github.hyperisland.compose.component.keepisland.PlaceholderGroup
import io.github.hyperisland.compose.component.keepisland.PlaceholderItem
import io.github.hyperisland.compose.component.parseHexColor
import io.github.hyperisland.compose.component.toArgbHex
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.KeepIslandProfile
import io.github.hyperisland.compose.data.KeepIslandScene
import io.github.hyperisland.compose.data.KeepIslandSettings
import io.github.hyperisland.compose.service.KeepIslandService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

@Composable
internal fun KeepIslandPage(
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    var settings by remember { mutableStateOf(prefs.keepIslandSettings()) }
    var activeEditor by remember { mutableStateOf<KeepIslandEditor?>(null) }
    var showInterval by remember { mutableStateOf(false) }
    var showColor by remember { mutableStateOf(false) }
    var showPlaceholders by remember { mutableStateOf(false) }
    var iconPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var selectedScene by remember { mutableStateOf(KeepIslandScene.Default) }

    fun update(next: KeepIslandSettings) {
        val previous = settings
        if (previous == next) return
        settings = next
        prefs.setKeepIslandSettings(previous, next)
        KeepIslandService.refresh(context)
    }

    fun updateProfile(transform: (KeepIslandProfile) -> KeepIslandProfile) {
        update(
            when (selectedScene) {
                KeepIslandScene.Default -> settings.copy(
                    defaultProfile = transform(settings.defaultProfile),
                )
                KeepIslandScene.Charging -> settings.copy(
                    chargingProfile = transform(settings.chargingProfile),
                )
                KeepIslandScene.Locked -> settings.copy(
                    lockedProfile = transform(settings.lockedProfile),
                )
            },
        )
    }

    fun iconUsedByAnotherScene(path: String): Boolean {
        if (path.isBlank()) return false
        return listOf(
            Triple(KeepIslandScene.Default, true, settings.defaultProfile.customIconPath),
            Triple(
                KeepIslandScene.Charging,
                !settings.chargingFollowDefault,
                settings.chargingProfile.customIconPath,
            ),
            Triple(
                KeepIslandScene.Locked,
                !settings.lockedFollowDefault,
                settings.lockedProfile.customIconPath,
            ),
        ).any { (scene, active, configuredPath) ->
            scene != selectedScene && active && configuredPath == path
        }
    }

    val profile = when (selectedScene) {
        KeepIslandScene.Default -> settings.defaultProfile
        KeepIslandScene.Charging -> settings.chargingProfile
        KeepIslandScene.Locked -> settings.lockedProfile
    }
    val followsAnotherScene = when (selectedScene) {
        KeepIslandScene.Default -> false
        KeepIslandScene.Charging -> settings.chargingFollowDefault
        KeepIslandScene.Locked -> settings.lockedFollowDefault
    }
    val sceneControlsEnabled = settings.masterEnabled && !followsAnotherScene
    val profileEnabled = sceneControlsEnabled && profile.islandEnabled

    LaunchedEffect(profile.customIconPath) {
        iconPreview = withContext(Dispatchers.IO) {
            profile.customIconPath.takeIf(String::isNotBlank)?.let { path ->
                runCatching { BitmapFactory.decodeFile(File(path).path)?.asImageBitmap() }.getOrNull()
            }
        }
    }

    val chooseIcon = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    KeepIslandService.saveIcon(context, uri, "")
                }
                result.onSuccess { path ->
                    val previousPath = profile.customIconPath
                    updateProfile { it.copy(customIconPath = path) }
                    if (!iconUsedByAnotherScene(previousPath)) {
                        withContext(Dispatchers.IO) { KeepIslandService.deleteIcon(previousPath) }
                    }
                }
            }
        }
    }

    val focusContentEnabled = sceneControlsEnabled &&
            ((profile.focusNotification && profile.islandEnabled) || profile.showNotification)
    val contentTypeValues = remember {
        listOf(CONTENT_NOTIFICATION, CONTENT_PERFORMANCE, CONTENT_DEVICE, CONTENT_CHARGING)
    }
    val textColorValues = remember {
        listOf(TEXT_WHITE, TEXT_FOLLOW_STATUS, TEXT_INVERT_STATUS, TEXT_BLACK)
    }
    val placeholderGroups = placeholderGroups()

    DetailPage(
        title = stringResource(R.string.always_on_island),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarState) },
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.keep_island_subtitle),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        item {
            SectionTitle(stringResource(R.string.keep_island_global_settings))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.keep_island_master_enable),
                    summary = stringResource(R.string.keep_island_master_enable_summary),
                    icon = null,
                    checked = settings.masterEnabled,
                ) { update(settings.copy(masterEnabled = it)) }
                PreferenceSwitch(
                    title = stringResource(R.string.keep_island_auto_hide),
                    summary = stringResource(R.string.keep_island_auto_hide_summary),
                    icon = null,
                    checked = settings.autoHide,
                    enabled = settings.masterEnabled,
                ) { update(settings.copy(autoHide = it)) }
                PreferenceSwitch(
                    title = stringResource(R.string.keep_island_hide_landscape),
                    summary = stringResource(R.string.keep_island_hide_landscape_summary),
                    icon = null,
                    checked = settings.hideLandscape,
                        enabled = settings.masterEnabled,
                ) { update(settings.copy(hideLandscape = it)) }
            }
        }
        item {
            TabRow(
                tabs = listOf(
                    stringResource(R.string.keep_island_tab_default),
                    stringResource(R.string.keep_island_tab_charging),
                    stringResource(R.string.keep_island_tab_locked),
                ),
                selectedTabIndex = selectedScene.ordinal,
                onTabSelected = { selectedScene = KeepIslandScene.entries[it] },
            )
        }
        if (selectedScene != KeepIslandScene.Default) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    PreferenceSwitch(
                        title = stringResource(R.string.keep_island_follow_default),
                        summary = stringResource(R.string.keep_island_follow_default_summary),
                        icon = null,
                        checked = followsAnotherScene,
                    enabled = settings.masterEnabled,
                    ) { checked ->
                        val freshSettings = if (checked) settings else prefs.keepIslandSettings()
                        update(
                            if (selectedScene == KeepIslandScene.Charging) {
                                settings.copy(
                                    chargingFollowDefault = checked,
                                    chargingProfile = freshSettings.chargingProfile,
                                )
                            } else {
                                settings.copy(
                                    lockedFollowDefault = checked,
                                    lockedProfile = freshSettings.lockedProfile,
                                )
                            },
                        )
                    }
                }
            }
        }
        if (!followsAnotherScene) {
        item {
            SectionTitle(stringResource(R.string.keep_island_island_config))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.keep_island_enable),
                    summary = null,
                    icon = null,
                    checked = profile.islandEnabled,
                    enabled = sceneControlsEnabled,
                ) { checked -> updateProfile { it.copy(islandEnabled = checked) } }
                ContentAction(
                    title = stringResource(R.string.keep_island_left_content),
                    values = profile.leftContents,
                    enabled = profileEnabled,
                ) { activeEditor = KeepIslandEditor.Left }
                ContentAction(
                    title = stringResource(R.string.keep_island_right_content),
                    values = profile.rightContents,
                    enabled = profileEnabled,
                ) { activeEditor = KeepIslandEditor.Right }
                SettingsAction(
                    title = stringResource(R.string.keep_island_carousel_interval),
                    summary = stringResource(R.string.keep_island_carousel_interval_summary),
                    endIcon = MiuixIcons.ChevronForward,
                    enabled = profileEnabled,
                ) { showInterval = true }
                ArrowPreference(
                    title = stringResource(R.string.keep_island_highlight_color),
                    summary = stringResource(R.string.keep_island_highlight_color_summary),
                    enabled = profileEnabled,
                    insideMargin = SettingsItemMargin,
                    endActions = {
                        ColorPreview(profile.highlightColor, profileEnabled)
                        if (profile.highlightColor.isNotBlank()) {
                            IconButton(
                                onClick = { updateProfile { it.copy(highlightColor = "") } },
                                modifier = Modifier.align(Alignment.CenterVertically),
                                enabled = profileEnabled,
                            ) {
                                Icon(
                                    MiuixIcons.Refresh,
                                    stringResource(R.string.reset_default),
                                    tint = if (profileEnabled) {
                                        MiuixTheme.colorScheme.onSurfaceVariantActions
                                    } else {
                                        MiuixTheme.colorScheme.disabledOnSecondaryVariant
                                    },
                                )
                            }
                        }
                    },
                    onClick = { showColor = true },
                )
                AnimatedVisibility(visible = profile.highlightColor.isNotBlank()) {
                    PreferenceSwitch(
                        title = stringResource(R.string.keep_island_highlight_left),
                        summary = stringResource(R.string.keep_island_text_highlight),
                        icon = null,
                        checked = profile.leftHighlight,
                        enabled = profileEnabled,
                    ) { checked -> updateProfile { it.copy(leftHighlight = checked) } }
                }
                AnimatedVisibility(visible = profile.highlightColor.isNotBlank()) {
                    PreferenceSwitch(
                        title = stringResource(R.string.keep_island_highlight_right),
                        summary = stringResource(R.string.keep_island_text_highlight),
                        icon = null,
                        checked = profile.rightHighlight,
                        enabled = profileEnabled,
                    ) { checked -> updateProfile { it.copy(rightHighlight = checked) } }
                }
                PreferenceSwitch(
                    title = stringResource(R.string.keep_island_show_icon),
                    summary = stringResource(R.string.keep_island_show_icon_summary),
                    icon = null,
                    checked = profile.showIslandIcon,
                    enabled = profileEnabled,
                ) { checked -> updateProfile { it.copy(showIslandIcon = checked) } }
            }
        }
        item {
            SectionTitle(stringResource(R.string.keep_island_custom_icon))
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = stringResource(R.string.keep_island_custom_icon),
                    summary = stringResource(
                        if (profile.customIconPath.isBlank()) R.string.click_select_file
                        else R.string.keep_island_custom_icon_selected,
                    ),
                    insideMargin = SettingsItemMargin,
                    endActions = {
                        if (iconPreview != null) {
                            Image(
                                bitmap = iconPreview!!,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        if (profile.customIconPath.isNotBlank()) {
                            IconButton(onClick = {
                                val oldPath = profile.customIconPath
                                val shared = iconUsedByAnotherScene(oldPath)
                                updateProfile { it.copy(customIconPath = "") }
                                scope.launch {
                                    if (!shared) {
                                        withContext(Dispatchers.IO) {
                                            KeepIslandService.deleteIcon(oldPath)
                                        }
                                    }
                                }
                            }) {
                                Icon(MiuixIcons.Close, stringResource(R.string.delete))
                            }
                        }
                    },
                    enabled = profileEnabled,
                    onClick = { chooseIcon.launch(arrayOf("image/*")) },
                )
            }
        }
        item {
            SectionTitle(stringResource(R.string.keep_island_focus_config))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.keep_island_clickable),
                    summary = stringResource(R.string.keep_island_clickable_summary),
                    icon = null,
                    checked = profile.focusNotification,
                    enabled = profileEnabled,
                ) { checked -> updateProfile { it.copy(focusNotification = checked) } }
                PreferenceDropdown(
                    title = stringResource(R.string.keep_island_focus_content_type),
                    summary = null,
                    icon = null,
                    items = listOf(
                        stringResource(R.string.keep_island_focus_notification),
                        stringResource(R.string.keep_island_focus_performance),
                        stringResource(R.string.keep_island_focus_device),
                        stringResource(R.string.keep_island_focus_charging),
                    ),
                    selectedIndex = contentTypeValues.indexOf(profile.focusContentType).coerceAtLeast(0),
                    enabled = focusContentEnabled,
                ) { index -> updateProfile { it.copy(focusContentType = contentTypeValues[index]) } }
                if (profile.focusContentType == CONTENT_NOTIFICATION) {
                    TextAction(
                        title = stringResource(R.string.keep_island_notification_title),
                        value = profile.notificationTitle,
                        enabled = focusContentEnabled,
                    ) { activeEditor = KeepIslandEditor.NotificationTitle }
                    TextAction(
                        title = stringResource(R.string.keep_island_notification_content),
                        value = profile.notificationContent,
                        enabled = focusContentEnabled,
                    ) { activeEditor = KeepIslandEditor.NotificationContent }
                }
                AnimatedVisibility(
                    visible = profile.focusContentType != CONTENT_NOTIFICATION,
                ) {
                    PreferenceDropdown(
                        title = stringResource(R.string.keep_island_expand_text_color),
                        summary = null,
                        icon = null,
                        items = listOf(
                            stringResource(R.string.keep_island_text_white),
                            stringResource(R.string.keep_island_text_follow_status_bar),
                            stringResource(R.string.keep_island_text_invert_status_bar),
                            stringResource(R.string.keep_island_text_black),
                        ),
                        selectedIndex = textColorValues.indexOf(profile.expandTextColorMode).coerceAtLeast(0),
                        enabled = profileEnabled && profile.focusNotification,
                    ) { index -> updateProfile { it.copy(expandTextColorMode = textColorValues[index]) } }
                }
                PreferenceSwitch(
                    title = stringResource(R.string.keep_island_show_notification),
                    summary = null,
                    icon = null,
                    checked = profile.showNotification,
                    enabled = sceneControlsEnabled,
                ) { enabled ->
                    updateProfile {
                        it.copy(
                            showNotification = enabled,
                            focusNotification = if (enabled && profile.islandEnabled) true else it.focusNotification,
                        )
                    }
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.keep_island_placeholders))
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.keep_island_placeholders_summary),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                SettingsAction(
                    title = stringResource(R.string.keep_island_placeholders),
                    endIcon = MiuixIcons.ChevronForward,
                    enabled = profileEnabled,
                ) { showPlaceholders = true }
            }
        }
        }
    }

    KeepIslandContentListDialog(
        show = activeEditor == KeepIslandEditor.Left,
        title = stringResource(R.string.keep_island_left_content),
        initialValues = profile.leftContents,
        onDismiss = { activeEditor = null },
    ) { values -> updateProfile { it.copy(leftContents = values) }; activeEditor = null }
    KeepIslandContentListDialog(
        show = activeEditor == KeepIslandEditor.Right,
        title = stringResource(R.string.keep_island_right_content),
        initialValues = profile.rightContents,
        onDismiss = { activeEditor = null },
    ) { values -> updateProfile { it.copy(rightContents = values) }; activeEditor = null }
    KeepIslandTextDialog(
        show = activeEditor == KeepIslandEditor.NotificationTitle,
        title = stringResource(R.string.keep_island_notification_title),
        initialValue = profile.notificationTitle,
        onDismiss = { activeEditor = null },
    ) { value -> updateProfile { it.copy(notificationTitle = value) }; activeEditor = null }
    KeepIslandTextDialog(
        show = activeEditor == KeepIslandEditor.NotificationContent,
        title = stringResource(R.string.keep_island_notification_content),
        initialValue = profile.notificationContent,
        onDismiss = { activeEditor = null },
    ) { value -> updateProfile { it.copy(notificationContent = value) }; activeEditor = null }
    KeepIslandIntervalDialog(
        show = showInterval,
        initialValue = profile.carouselInterval,
        onDismiss = { showInterval = false },
    ) { value -> updateProfile { it.copy(carouselInterval = value) }; showInterval = false }
    ColorPaletteDialog(
        show = showColor,
        title = stringResource(R.string.keep_island_highlight_color),
        initialColor = parseHexColor(profile.highlightColor, Color.Red),
        onDismiss = { showColor = false },
        onDelete = {
            updateProfile { it.copy(highlightColor = "") }
            showColor = false
        },
    ) { color -> updateProfile { it.copy(highlightColor = color.toArgbHex()) }; showColor = false }
    KeepIslandPlaceholderSheet(
        show = showPlaceholders,
        title = stringResource(R.string.keep_island_placeholders),
        groups = placeholderGroups,
        onDismiss = { showPlaceholders = false },
    ) { placeholder ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(placeholder.label, placeholder.value))
        showPlaceholders = false
        scope.launch {
            snackbarState.showSnackbar(
                context.getString(R.string.keep_island_placeholder_copied, placeholder.label),
            )
        }
    }
}

@Composable
private fun ContentAction(
    title: String,
    values: List<String>,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val preview = values.filter(String::isNotEmpty).joinToString("  |  ")
    SettingsAction(
        title = title,
        summary = preview.ifBlank { stringResource(R.string.keep_island_default_empty) },
        endIcon = MiuixIcons.ChevronForward,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun TextAction(
    title: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SettingsAction(
        title = title,
        summary = value.ifBlank { stringResource(R.string.keep_island_default_empty) },
        endIcon = MiuixIcons.ChevronForward,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun RowScope.ColorPreview(value: String, enabled: Boolean) {
    val shape = RoundedCornerShape(7.dp)
    val fillColor = if (enabled) {
        parseHexColor(value, MiuixTheme.colorScheme.surfaceContainer)
    } else {
        MiuixTheme.colorScheme.disabledOnSecondaryVariant
    }
    val borderColor = if (enabled) {
        MiuixTheme.colorScheme.outline
    } else {
        MiuixTheme.colorScheme.disabledOnSurface
    }
    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .size(26.dp)
            .align(Alignment.CenterVertically)
            .background(fillColor, shape)
            .border(1.dp, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {}
}

@Composable
private fun placeholderGroups(): List<PlaceholderGroup> = listOf(
    PlaceholderGroup(
        stringResource(R.string.keep_island_category_battery),
        listOf(
            placeholder(R.string.keep_placeholder_battery_power, "{battery.power}"),
            placeholder(R.string.keep_placeholder_battery_voltage, "{battery.voltage}"),
            placeholder(R.string.keep_placeholder_battery_current, "{battery.current}"),
            placeholder(R.string.keep_placeholder_battery_level, "{battery.level}"),
            placeholder(R.string.keep_placeholder_battery_temperature, "{battery.temperature}"),
        ),
    ),
    PlaceholderGroup(
        stringResource(R.string.keep_island_category_cpu),
        listOf(
            placeholder(R.string.keep_placeholder_cpu_usage, "{cpu.usage}"),
            placeholder(R.string.keep_placeholder_cpu_temperature, "{cpu.temperature}"),
        ),
    ),
    PlaceholderGroup(
        stringResource(R.string.keep_island_category_gpu),
        listOf(
            placeholder(R.string.keep_placeholder_gpu_usage, "{gpu.usage}"),
            placeholder(R.string.keep_placeholder_gpu_frequency, "{gpu.frequency}"),
        ),
    ),
    PlaceholderGroup(
        stringResource(R.string.keep_island_category_memory),
        listOf(
            placeholder(R.string.keep_placeholder_memory_usage, "{memory.usage}"),
            placeholder(R.string.keep_placeholder_memory_used, "{memory.used}"),
            placeholder(R.string.keep_placeholder_memory_total, "{memory.total}"),
        ),
    ),
    PlaceholderGroup(
        stringResource(R.string.keep_island_category_network),
        listOf(
            placeholder(R.string.keep_placeholder_network_download, "{network.download}"),
            placeholder(R.string.keep_placeholder_network_upload, "{network.upload}"),
            placeholder(R.string.keep_placeholder_network_speed, "{network.speed}"),
            placeholder(R.string.keep_placeholder_network_received, "{network.received}"),
            placeholder(R.string.keep_placeholder_network_sent, "{network.sent}"),
        ),
    ),
    PlaceholderGroup(
        stringResource(R.string.keep_island_category_time),
        listOf(
            placeholder(R.string.keep_placeholder_time_24_hour, "{time.HH}"),
            placeholder(R.string.keep_placeholder_time_12_hour_padded, "{time.hh}"),
            placeholder(R.string.keep_placeholder_time_12_hour, "{time.h}"),
            placeholder(R.string.keep_placeholder_time_minute, "{time.mm}"),
            placeholder(R.string.keep_placeholder_time_second, "{time.ss}"),
            placeholder(R.string.keep_placeholder_time_hour_minute, "{time.HH:mm}"),
            placeholder(R.string.keep_placeholder_time_full, "{time.HH:mm:ss}"),
        ),
    ),
    PlaceholderGroup(
        stringResource(R.string.keep_island_category_weather),
        listOf(
            placeholder(R.string.keep_placeholder_weather_location, "{weather.location}"),
            placeholder(R.string.keep_placeholder_weather_condition, "{weather.condition}"),
            placeholder(R.string.keep_placeholder_weather_temperature, "{weather.temperature}"),
        ),
    ),
    PlaceholderGroup(
        stringResource(R.string.keep_island_category_display),
        listOf(
            placeholder(R.string.keep_placeholder_display_refresh_rate, "{display.refreshRate}"),
            placeholder(R.string.keep_placeholder_display_actual_refresh_rate, "{display.actualRefreshRate}"),
        ),
    ),
    PlaceholderGroup(
        stringResource(R.string.keep_island_category_device),
        listOf(
            placeholder(R.string.keep_placeholder_device_manufacturer, "{device.manufacturer}"),
            placeholder(R.string.keep_placeholder_device_model, "{device.model}"),
            placeholder(R.string.keep_placeholder_device_name, "{device.name}"),
            placeholder(R.string.keep_placeholder_device_chipset, "{device.chipset}"),
            placeholder(R.string.keep_placeholder_device_uptime, "{device.uptime}"),
        ),
    ),
)

@Composable
private fun placeholder(labelRes: Int, value: String) = PlaceholderItem(
    label = stringResource(labelRes),
    value = value,
)

private enum class KeepIslandEditor { Left, Right, NotificationTitle, NotificationContent }

private const val CONTENT_NOTIFICATION = "notification"
private const val CONTENT_PERFORMANCE = "performance"
private const val CONTENT_DEVICE = "device"
private const val CONTENT_CHARGING = "charging"
private const val TEXT_WHITE = "white"
private const val TEXT_FOLLOW_STATUS = "follow_status_bar"
private const val TEXT_INVERT_STATUS = "invert_status_bar"
private const val TEXT_BLACK = "black"
