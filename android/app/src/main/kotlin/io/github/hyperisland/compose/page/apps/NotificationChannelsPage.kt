package io.github.hyperisland.compose.page.apps

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.BarBackdropContent
import io.github.hyperisland.compose.component.BarBlurHost
import io.github.hyperisland.compose.component.BlurredBar
import io.github.hyperisland.compose.component.LocalBarBlurEnabled
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.InstalledApp
import io.github.hyperisland.compose.data.NotificationChannelInfo
import io.github.hyperisland.compose.data.NotificationChannelsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Paste
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun NotificationChannelsPage(
    app: InstalledApp,
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
    onOpenMediaSettings: () -> Unit,
    openLegacySettings: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { NotificationChannelsRepository() }
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    val scrollBehavior = MiuixScrollBehavior()
    var channels by remember(app.packageName) { mutableStateOf(emptyList<NotificationChannelInfo>()) }
    var enabledChannelIds by remember(app.packageName) { mutableStateOf(prefs.enabledChannelIds(app.packageName)) }
    var appEnabled by remember(app.packageName) { mutableStateOf(app.packageName in prefs.enabledPackages()) }
    var mediaSettings by remember(app.packageName) {
        mutableStateOf(prefs.mediaNotificationSettings(app.packageName))
    }
    var loading by remember(app.packageName) { mutableStateOf(true) }
    var rootErrorVisible by remember { mutableStateOf(false) }
    var refreshRevision by remember { mutableIntStateOf(0) }

    val exportSuccess = stringResource(R.string.compose_export_channels_success)
    val emptyClipboard = stringResource(R.string.compose_import_empty_clipboard)
    val invalidJson = stringResource(R.string.compose_import_invalid_json)
    val missingChannels = stringResource(R.string.compose_import_missing_channels)
    val noMatch = stringResource(R.string.compose_import_no_match)
    val importUnknown = stringResource(R.string.compose_import_unknown_error)

    fun refresh() {
        refreshRevision++
    }

    fun showMessage(message: String) {
        scope.launch { snackbarState.showSnackbar(message) }
    }

    LaunchedEffect(app.packageName, refreshRevision) {
        loading = true
        val loaded = withContext(Dispatchers.IO) { repository.load(app.packageName) }
        if (loaded == null) {
            channels = emptyList()
            rootErrorVisible = true
        } else {
            channels = loaded
        }
        enabledChannelIds = prefs.enabledChannelIds(app.packageName)
        appEnabled = app.packageName in prefs.enabledPackages()
        loading = false
    }
    DisposableEffect(prefs, app.packageName) {
        val removeListener = prefs.addChangeListener { key ->
            if (key == "pref_generic_whitelist") {
                appEnabled = app.packageName in prefs.enabledPackages()
            } else if (key == "pref_app_config_${app.packageName}") {
                enabledChannelIds = prefs.enabledChannelIds(app.packageName)
                mediaSettings = prefs.mediaNotificationSettings(app.packageName)
            }
        }
        onDispose(removeListener)
    }

    fun setAllChannelsEnabled() {
        enabledChannelIds = emptySet()
        prefs.setEnabledChannelIds(app.packageName, emptySet())
    }

    fun toggleChannel(channelId: String, enabled: Boolean) {
        if (!appEnabled) return
        val next = if (enabledChannelIds.isEmpty()) {
            if (enabled) return
            channels.map(NotificationChannelInfo::id).filterNot { it == channelId }.toSet()
        } else {
            enabledChannelIds.toMutableSet().apply {
                if (enabled) add(channelId) else remove(channelId)
            }.let { selected ->
                if (selected.size == channels.size) emptySet() else selected
            }
        }
        enabledChannelIds = next
        prefs.setEnabledChannelIds(app.packageName, next)
    }

    fun exportChannels() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val json = prefs.exportChannelSettings(app.packageName, app.appName, channels)
        clipboard.setPrimaryClip(ClipData.newPlainText(app.appName, json))
        showMessage(exportSuccess)
    }

    fun importChannels() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (text.isEmpty()) {
            showMessage(emptyClipboard)
            return
        }
        try {
            val (matched, total) = prefs.importChannelSettings(
                app.packageName,
                channels.map(NotificationChannelInfo::id).toSet(),
                text,
            )
            if (matched == 0) {
                showMessage(noMatch)
            } else {
                enabledChannelIds = prefs.enabledChannelIds(app.packageName)
                appEnabled = true
                val message = context.resources.getQuantityString(
                    R.plurals.compose_import_channels_success,
                    matched,
                    matched,
                ) + if (matched < total) {
                    context.getString(R.string.compose_import_channels_partial, total, matched)
                } else {
                    ""
                }
                showMessage(message)
            }
        } catch (error: IllegalArgumentException) {
            showMessage(if (error.message == "missing_channels") missingChannels else invalidJson)
        } catch (_: JSONException) {
            showMessage(invalidJson)
        } catch (_: Exception) {
            showMessage(importUnknown)
        }
    }

    val menuEntry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = stringResource(R.string.compose_refresh_list),
                onClick = ::refresh,
                icon = { modifier -> Icon(MiuixIcons.Refresh, null, modifier) },
            ),
            DropdownItem(
                text = stringResource(R.string.compose_enable_all_channels),
                enabled = channels.isNotEmpty(),
                onClick = ::setAllChannelsEnabled,
                icon = { modifier -> Icon(MiuixIcons.SelectAll, null, modifier) },
            ),
            DropdownItem(
                text = stringResource(R.string.compose_export_channels),
                enabled = channels.isNotEmpty(),
                onClick = ::exportChannels,
                icon = { modifier -> Icon(MiuixIcons.Copy, null, modifier) },
            ),
            DropdownItem(
                text = stringResource(R.string.compose_import_channels),
                enabled = channels.isNotEmpty(),
                onClick = ::importChannels,
                icon = { modifier -> Icon(MiuixIcons.Paste, null, modifier) },
            ),
        ),
    )

    val pageBlurEnabled = LocalBarBlurEnabled.current
    BarBlurHost(enabled = pageBlurEnabled) {
        Scaffold(
            topBar = {
                BlurredBar(topGradient = true) {
                    TopAppBar(
                    title = app.appName,
                    largeTitle = app.appName,
                    color = Color.Transparent,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.Back, stringResource(R.string.compose_back))
                        }
                    },
                    actions = {
                        Switch(
                            checked = appEnabled,
                            onCheckedChange = { enabled ->
                                appEnabled = enabled
                                prefs.setAppEnabled(app.packageName, enabled)
                            },
                        )
                        OverlayIconDropdownMenu(entry = menuEntry) {
                            Icon(MiuixIcons.More, stringResource(R.string.compose_list_actions))
                        }
                    },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarState) },
        ) { padding ->
            BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                PullToRefresh(
            isRefreshing = loading,
            onRefresh = ::refresh,
            topAppBarScrollBehavior = scrollBehavior,
            contentPadding = PaddingValues(top = padding.calculateTopPadding()),
            modifier = Modifier.fillMaxSize(),
            refreshTexts = listOf("", "", "", ""),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    end = 12.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!appEnabled) {
                    item {
                        Card {
                            BasicComponent(
                                title = stringResource(R.string.compose_app_disabled_banner),
                                insideMargin = CHANNEL_ITEM_MARGIN,
                            )
                        }
                    }
                }
                item {
                    Column {
                        SectionTitle(stringResource(R.string.compose_media_notification))
                        Card {
                            ArrowPreference(
                                title = stringResource(R.string.compose_media_notification),
                                summary = mediaSettingsSummary(mediaSettings),
                                insideMargin = CHANNEL_ITEM_MARGIN,
                                onClick = onOpenMediaSettings,
                            )
                        }
                    }
                }
                if (loading && channels.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (channels.isEmpty()) {
                    item {
                        Card {
                            BasicComponent(
                                title = stringResource(R.string.compose_no_channels_found),
                                summary = stringResource(R.string.compose_no_channels_found_summary),
                                insideMargin = CHANNEL_ITEM_MARGIN,
                            )
                        }
                    }
                } else {
                    item {
                        Column {
                            val enabledCount = if (!appEnabled) 0 else if (enabledChannelIds.isEmpty()) {
                                channels.size
                            } else {
                                enabledChannelIds.size
                            }
                            SectionTitle(
                                title = if (!appEnabled) {
                                    stringResource(R.string.compose_all_channels_disabled, channels.size)
                                } else if (enabledChannelIds.isEmpty()) {
                                    stringResource(R.string.compose_all_channels_active, channels.size)
                                } else {
                                    stringResource(
                                        R.string.compose_selected_channels,
                                        enabledCount,
                                        channels.size,
                                    )
                                },
                            )
                            Card {
                                channels.forEach { channel ->
                                    val channelEnabled = appEnabled &&
                                        (enabledChannelIds.isEmpty() || channel.id in enabledChannelIds)
                                    val importance = importanceLabel(channel.importance)
                                    val details = stringResource(
                                        R.string.compose_channel_importance,
                                        stringResource(importance),
                                        channel.id,
                                    )
                                    BasicComponent(
                                        title = channel.name,
                                        summary = if (channel.description.isEmpty()) {
                                            details
                                        } else {
                                            "${channel.description}\n$details"
                                        },
                                        enabled = appEnabled,
                                        endActions = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                IconButton(
                                                    onClick = openLegacySettings,
                                                    enabled = channelEnabled,
                                                ) {
                                                    Icon(
                                                        MiuixIcons.Settings,
                                                        stringResource(R.string.compose_channel_settings),
                                                        modifier = Modifier.size(24.dp),
                                                    )
                                                }
                                                Switch(
                                                    checked = channelEnabled,
                                                    enabled = appEnabled,
                                                    onCheckedChange = { toggleChannel(channel.id, it) },
                                                )
                                            }
                                        },
                                        insideMargin = CHANNEL_ITEM_MARGIN,
                                        onClick = { toggleChannel(channel.id, !channelEnabled) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
                }
            }
        }
    }

    WindowDialog(
        show = rootErrorVisible,
        title = stringResource(R.string.compose_cannot_read_channels),
        onDismissRequest = { rootErrorVisible = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.compose_root_required_message))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.compose_cancel),
                    onClick = { rootErrorVisible = false },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        rootErrorVisible = false
                        refresh()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.compose_refresh_list))
                }
            }
        }
    }
}

@Composable
private fun mediaSettingsSummary(settings: io.github.hyperisland.compose.data.MediaNotificationSettings): String {
    val modified = settings != io.github.hyperisland.compose.data.MediaNotificationSettings()
    if (!modified) return stringResource(R.string.compose_channel_settings_unmodified)
    val enabled = stringResource(
        if (settings.enabled) R.string.compose_enabled_option else R.string.compose_disabled_option,
    )
    val normalNotification = stringResource(
        if (settings.normalNotification) R.string.compose_enabled_option else R.string.compose_disabled_option,
    )
    val glow = when (settings.islandOuterGlow) {
        "on" -> stringResource(R.string.compose_enabled_option)
        "off" -> stringResource(R.string.compose_disabled_option)
        "follow_dynamic" -> stringResource(R.string.compose_follow_dynamic_color)
        else -> stringResource(R.string.compose_default)
    }
    return stringResource(R.string.compose_media_settings_summary, enabled, normalNotification, glow)
}

private fun importanceLabel(importance: Int): Int = when (importance) {
    0 -> R.string.compose_importance_none
    1 -> R.string.compose_importance_min
    2 -> R.string.compose_importance_low
    3 -> R.string.compose_importance_default
    4, 5 -> R.string.compose_importance_high
    else -> R.string.compose_importance_unknown
}

private val CHANNEL_ITEM_MARGIN = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
