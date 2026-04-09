package io.github.hyperisland.ui.app

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.isSystemInDarkTheme
import io.github.hyperisland.ui.FaGlyph
import io.github.hyperisland.ui.FaIcon
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh as MiuixPullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle as MiuixSmallTitle
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import io.github.hyperisland.ui.LocalContentPadding
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference as MiuixOverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.delay

@Composable
fun AppsScreen(
    state: AppsUiState,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onAppEnabledChange: (String, Boolean) -> Unit,
    onOpenAppChannels: (String) -> Unit,
    onBatchApplyGlobal: (Map<String, String>) -> Unit,
    onBatchApplySelected: (Set<String>, Map<String, String>) -> Unit = { _, _ -> },
    onAppSelectedChange: (String) -> Unit = {},
    onSelectAll: (Set<String>) -> Unit = {},
    onSelectionModeChanged: (Boolean) -> Unit = {},
    selectionRequestId: Int = 0,
    exitSelectionRequestId: Int = 0,
    enableSelectedRequestId: Int = 0,
    disableSelectedRequestId: Int = 0,
    selectEnabledRequestId: Int = 0,
    batchSelectedRequestId: Int = 0,
    enableAllRequestId: Int = 0,
    disableAllRequestId: Int = 0,
    batchRequestId: Int = 0,
    appListState: LazyListState = rememberLazyListState(),
    onSearchVisibilityChange: (Boolean) -> Unit = {},
    topAppBarScrollBehavior: ScrollBehavior? = null,
    canPullToRefresh: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val pullToRefreshState = rememberPullToRefreshState()
    var showBatchDialog by remember { mutableStateOf(false) }
    var batchForSelected by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var handledSelectionRequestId by rememberSaveable { mutableStateOf(0) }
    var handledExitSelectionRequestId by rememberSaveable { mutableStateOf(0) }
    var handledEnableSelectedRequestId by rememberSaveable { mutableStateOf(0) }
    var handledDisableSelectedRequestId by rememberSaveable { mutableStateOf(0) }
    var handledSelectEnabledRequestId by rememberSaveable { mutableStateOf(0) }
    var handledEnableAllRequestId by rememberSaveable { mutableStateOf(0) }
    var handledDisableAllRequestId by rememberSaveable { mutableStateOf(0) }
    var handledBatchRequestId by rememberSaveable { mutableStateOf(0) }
    var handledBatchSelectedRequestId by rememberSaveable { mutableStateOf(0) }




    val filtered = state.filteredApps
    val visiblePackages = filtered.map { it.packageName }

    fun setEnabledForPackages(packages: Set<String>, enabled: Boolean) {
        packages.forEach { pkg -> onAppEnabledChange(pkg, enabled) }
    }

    fun setEnabledForVisible(enabled: Boolean) {
        if (visiblePackages.isEmpty()) return
        setEnabledForPackages(visiblePackages.toSet(), enabled)
    }

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        onSelectAll(emptySet())
    }

    fun selectEnabledVisible() {
        onSelectAll(visiblePackages.filter { state.enabledPackages.contains(it) }.toSet())
    }

    LaunchedEffect(batchRequestId) {
        if (batchRequestId > 0 && batchRequestId != handledBatchRequestId) {
            handledBatchRequestId = batchRequestId
            batchForSelected = false
            showBatchDialog = true
        }
    }
    LaunchedEffect(batchSelectedRequestId) {
        if (
            batchSelectedRequestId > 0 &&
            batchSelectedRequestId != handledBatchSelectedRequestId
        ) {
            handledBatchSelectedRequestId = batchSelectedRequestId
            batchForSelected = true
            showBatchDialog = true
        }
    }
    LaunchedEffect(selectionRequestId) {
        if (selectionRequestId > 0 && selectionRequestId != handledSelectionRequestId) {
            handledSelectionRequestId = selectionRequestId
            selectionMode = true
        }
    }
    LaunchedEffect(exitSelectionRequestId) {
        if (
            exitSelectionRequestId > 0 &&
            exitSelectionRequestId != handledExitSelectionRequestId
        ) {
            handledExitSelectionRequestId = exitSelectionRequestId
            selectionMode = false
            onSelectAll(emptySet())
        }
    }
    LaunchedEffect(enableSelectedRequestId) {
        if (
            enableSelectedRequestId > 0 &&
            enableSelectedRequestId != handledEnableSelectedRequestId
        ) {
            handledEnableSelectedRequestId = enableSelectedRequestId
            setEnabledForPackages(state.selectedPackages, true)
        }
    }
    LaunchedEffect(disableSelectedRequestId) {
        if (
            disableSelectedRequestId > 0 &&
            disableSelectedRequestId != handledDisableSelectedRequestId
        ) {
            handledDisableSelectedRequestId = disableSelectedRequestId
            setEnabledForPackages(state.selectedPackages, false)
        }
    }
    LaunchedEffect(selectEnabledRequestId) {
        if (
            selectEnabledRequestId > 0 &&
            selectEnabledRequestId != handledSelectEnabledRequestId
        ) {
            handledSelectEnabledRequestId = selectEnabledRequestId
            selectionMode = true
            selectEnabledVisible()
        }
    }
    LaunchedEffect(enableAllRequestId) {
        if (enableAllRequestId > 0 && enableAllRequestId != handledEnableAllRequestId) {
            handledEnableAllRequestId = enableAllRequestId
            setEnabledForVisible(true)
        }
    }
    LaunchedEffect(disableAllRequestId) {
        if (disableAllRequestId > 0 && disableAllRequestId != handledDisableAllRequestId) {
            handledDisableAllRequestId = disableAllRequestId
            setEnabledForVisible(false)
        }
    }
    LaunchedEffect(selectionMode) {
        onSelectionModeChanged(selectionMode)
    }

    BackHandler(enabled = showBatchDialog) {
        showBatchDialog = false
    }
    BackHandler(enabled = state.query.isNotBlank() && !showBatchDialog) {
        onQueryChange("")
    }

    val contentPadding = io.github.hyperisland.ui.LocalContentPadding.current

    val topPadding = contentPadding.calculateTopPadding()
    val listTranslationY = if (canPullToRefresh) -topPadding else 0.dp
    
    val listContent: @Composable () -> Unit = {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = listTranslationY.toPx()
                    clip = false
                }
                .scrollEndHaptic(),
            state = appListState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (state.loading && state.apps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MiuixCircularProgressIndicator()
                    }
                }
            } else {
                state.error?.let {
                    item {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    }
                }
                items(filtered, key = { it.packageName }) { app ->
                    val enabled = state.enabledPackages.contains(app.packageName)
                    val selected = state.selectedPackages.contains(app.packageName)
                    AppItemRow(
                        app = app,
                        enabled = enabled,
                        onEnabledChange = { enabledValue -> onAppEnabledChange(app.packageName, enabledValue) },
                        onClick = {
                            if (selectionMode) {
                                onAppSelectedChange(app.packageName)
                            } else {
                                onOpenAppChannels(app.packageName)
                            }
                        },
                        selectionMode = selectionMode,
                        selected = selected,
                        onSelectedChange = { onAppSelectedChange(app.packageName) },
                    )
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (canPullToRefresh) {
            MiuixPullToRefresh(
                isRefreshing = state.loading,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = if (canPullToRefresh) topPadding.toPx() else 0f
                        clip = false
                },
                pullToRefreshState = pullToRefreshState,
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新..."),
            ) {
                listContent()
            }
        } else {
            listContent()
        }
    }
    if (showBatchDialog) {
        BatchApplyDialog(
            title = if (batchForSelected || selectionMode) "批量应用到已选应用的渠道" else "批量应用到已启用应用",
            onDismiss = { showBatchDialog = false },
            onApply = { settings ->
                showBatchDialog = false
                if (batchForSelected || selectionMode) {
                    onBatchApplySelected(state.selectedPackages, settings)
                } else {
                    onBatchApplyGlobal(settings)
                }
            },
        )
    }
}

@Composable
fun AppChannelsScreen(
    state: AppChannelsUiState,
    onRefresh: () -> Unit,
    onSetAppEnabled: (Boolean) -> Unit,
    onToggleChannel: (String, Boolean) -> Unit,
    onEnableAllChannels: () -> Unit,
    onOpenChannelSettings: (String, String) -> Unit,
    onBatchApplyToEnabledChannels: (Map<String, String>) -> Unit,
    enableAllRequestId: Int = 0,
    batchRequestId: Int = 0,
    modifier: Modifier = Modifier,
) {
    val channels = state.channels
    var showBatchDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = showBatchDialog) {
        showBatchDialog = false
    }
    LaunchedEffect(enableAllRequestId) {
        if (enableAllRequestId > 0) onEnableAllChannels()
    }
    LaunchedEffect(batchRequestId) {
        if (batchRequestId > 0) showBatchDialog = true
    }

    val contentPadding = io.github.hyperisland.ui.LocalContentPadding.current
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .overScrollVertical()
            .scrollEndHaptic(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            MiuixCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppIcon(icon = state.appIcon, fallbackIcon = MiuixIcons.Regular.All, size = 30.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.appName.isBlank()) state.packageName else state.appName,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            state.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MiuixSwitch(
                        checked = state.appEnabled,
                        onCheckedChange = onSetAppEnabled,
                    )
                }
            }
        }

        if (state.loading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixCircularProgressIndicator()
                }
            }
            return@LazyColumn
        }

        state.error?.let {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    MiuixButton(onClick = onRefresh) { Text("重试") }
                }
            }
            return@LazyColumn
        }

        if (!state.appEnabled) {
            item {
                MiuixCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "请先开启应用总开关后再配置通知渠道",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@LazyColumn
        }

        if (channels.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "未读取到通知渠道",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MiuixButton(onClick = onRefresh) { Text("刷新") }
                }
            }
        } else {
            item {
                MiuixCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        channels.forEach { channel ->
                            val enabled = state.enabledChannels.isEmpty() || state.enabledChannels.contains(channel.id)
                            ChannelListItem(
                                channel = channel,
                                enabled = enabled,
                                onEnableChange = { onToggleChannel(channel.id, it) },
                                onOpenSettings = {
                                    if (enabled) onOpenChannelSettings(channel.id, channel.name)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBatchDialog) {
        BatchApplyDialog(
            title = "批量应用到已启用渠道",
            onDismiss = { showBatchDialog = false },
            onApply = { settings ->
                showBatchDialog = false
                onBatchApplyToEnabledChannels(settings)
            },
        )
    }
}

@Composable
fun ChannelSettingsScreen(
    state: AppChannelsUiState,
    channelId: String,
    onRefresh: () -> Unit,
    onSetTemplate: (String) -> Unit,
    onSetTimeout: (String) -> Unit,
    onSetSetting: (String, String) -> Unit,
    onSetHighlightColor: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val channel = state.channels.firstOrNull { it.id == channelId }
    val template = state.channelTemplates[channelId] ?: "notification_island"
    val timeout = state.channelTimeout[channelId] ?: "5"
    val extras = state.channelExtras[channelId] ?: ChannelExtraSettings()

    if (state.loading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            MiuixCircularProgressIndicator()
        }
        return
    }

    if (state.error != null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(state.error, color = MaterialTheme.colorScheme.error)
            MiuixButton(onClick = onRefresh) { Text("重试") }
        }
        return
    }

    if (channel == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("未找到该通知渠道", color = MaterialTheme.colorScheme.onSurfaceVariant)
            MiuixButton(onClick = onRefresh) { Text("刷新") }
        }
        return
    }

    val contentPadding = io.github.hyperisland.ui.LocalContentPadding.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .overScrollVertical()
            .scrollEndHaptic()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding() + 12.dp,
                    bottom = contentPadding.calculateBottomPadding() + 12.dp
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        ChannelSettingsContent(
            channel = channel,
            template = template,
            timeout = timeout,
            extras = extras,
            onSetTemplate = onSetTemplate,
            onSetTimeout = onSetTimeout,
            onSetSetting = onSetSetting,
            onSetHighlightColor = onSetHighlightColor,
        )
    }
}
}

@Composable
fun AppListIcon(app: AppItem) {
    AppIcon(icon = app.icon, fallbackIcon = MiuixIcons.Regular.All, size = 40.dp)
}

@Composable
fun AppIcon(icon: ByteArray, fallbackIcon: ImageVector, size: Dp) {
    val bitmap = remember(icon) {
        if (icon.isEmpty()) {
            null
        } else {
            BitmapFactory.decodeByteArray(icon, 0, icon.size)
        }
    }
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(size),
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                modifier = Modifier.size(size - 8.dp),
            )
        }
    }
}

@Composable
private fun ChannelListItem(
    channel: ChannelItem,
    enabled: Boolean,
    onEnableChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(channel.name, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "重要性: ${channel.importance}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        Text(
                            text = channel.id,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                MiuixIconButton(onClick = onOpenSettings, enabled = enabled) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Settings,
                        contentDescription = "渠道设置",
                    )
                }
                MiuixSwitch(
                    checked = enabled,
                    onCheckedChange = onEnableChange,
                )
            }
            if (channel.description.isNotBlank()) {
                Text("描述: ${channel.description}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ChannelSettingsContent(
    channel: ChannelItem,
    template: String,
    timeout: String,
    extras: ChannelExtraSettings,
    onSetTemplate: (String) -> Unit,
    onSetTimeout: (String) -> Unit,
    onSetSetting: (String, String) -> Unit,
    onSetHighlightColor: (String) -> Unit,
) {
    fun triStateOptions(defaultOn: Boolean): List<Pair<String, String>> = listOf(
        "default" to if (defaultOn) "默认(开启)" else "默认(关闭)",
        "on" to "开启",
        "off" to "关闭",
    )

    val templateOptions = listOf(
        "generic_progress" to "下载",
        "notification_island" to "通知超级岛",
        "notification_island_lite" to "通知超级岛 Lite",
        "download_lite" to "下载 Lite",
        "ai_notification_island" to "AI 通知超级岛",
    )
    val iconModeOptions = listOf(
        "auto" to "自动",
        "notif_small" to "通知小图标",
        "notif_large" to "通知大图标",
        "app_icon" to "应用图标",
    )
    val showIslandIconOptions = triStateOptions(defaultOn = true)
    val firstFloatOptions = triStateOptions(defaultOn = false)
    val enableFloatOptions = triStateOptions(defaultOn = false)
    val marqueeOptions = triStateOptions(defaultOn = false)
    val focusOptions = triStateOptions(defaultOn = true)
    val preserveStatusBarOptions = triStateOptions(defaultOn = false)
    val restoreLockscreenOptions = triStateOptions(defaultOn = false)
    val dynamicHighlightOptions = listOf(
        "default" to "默认(关闭)",
        "on" to "开启",
        "off" to "关闭",
        "dark" to "暗",
        "darker" to "更暗",
    )
    val outerGlowOptions = triStateOptions(defaultOn = false)
    val rendererOptions = listOf(
        "image_text_with_buttons_4" to "新图文组件 + 底部文本按钮",
        "image_text_with_buttons_4_wrap" to "封面信息样式",
        "image_text_with_right_text_button" to "图文右侧文本按钮",
    )
    var highlightDraft by remember(extras.highlightColor) { mutableStateOf(extras.highlightColor) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ChannelSectionTitle("模板")
        ChannelSectionCard {
            SettingsDropdownRow(
                title = "模板",
                options = templateOptions,
                selectedValue = template,
                enabled = true,
                largeText = true,
                onValueChange = onSetTemplate,
            )
            SettingsDropdownRow("样式", rendererOptions, extras.renderer, true, largeText = true) {
                onSetSetting("renderer", it)
            }
        }

        ChannelSectionTitle("岛")
        ChannelSectionCard {
            SettingsDropdownRow("超级岛图标", iconModeOptions, extras.icon, true, largeText = true) { onSetSetting("icon", it) }
            SettingsDropdownRow("大岛图标", showIslandIconOptions, extras.showIslandIcon, true, largeText = true) {
                onSetSetting("show_island_icon", it)
            }
            SettingsDropdownRow("初次展开", firstFloatOptions, extras.firstFloat, true, largeText = true) {
                onSetSetting("first_float", it)
            }
            SettingsDropdownRow("更新展开", enableFloatOptions, extras.enableFloat, true, largeText = true) {
                onSetSetting("enable_float", it)
            }
            SettingsDropdownRow("消息滚动", marqueeOptions, extras.marquee, true, largeText = true) {
                onSetSetting("marquee", it)
            }
            InputDialogRow(
                title = "自动消失时长",
                subtitle = "点击后在对话框中输入 1-30 秒",
                value = timeout,
                emptyValueText = "5",
                dialogTitle = "修改自动消失时长",
                dialogDescription = "值应该大于等于 1 并小于等于 30",
                onConfirm = { onSetTimeout(it.trim()) },
            )
            InputDialogRow(
                title = "高亮颜色",
                subtitle = "点击后输入 #RRGGBB，留空可清空",
                value = highlightDraft,
                emptyValueText = "未设置",
                dialogTitle = "修改高亮颜色",
                dialogDescription = "请输入 #RRGGBB 格式，留空可清空当前颜色",
                onConfirm = {
                    val next = it.trim()
                    highlightDraft = next
                    onSetHighlightColor(next)
                },
            )
            SwitchSettingRow(
                title = "左侧高亮",
                checked = extras.showLeftHighlight == "on",
                onCheckedChange = { onSetSetting("show_left_highlight", if (it) "on" else "off") },
            )
            SwitchSettingRow(
                title = "右侧高亮",
                checked = extras.showRightHighlight == "on",
                onCheckedChange = { onSetSetting("show_right_highlight", if (it) "on" else "off") },
            )
            SettingsDropdownRow(
                "高亮动态取色",
                dynamicHighlightOptions,
                extras.dynamicHighlightColor,
                true,
                largeText = true,
            ) {
                onSetSetting("dynamic_highlight_color", it)
            }
            SwitchSettingRow(
                title = "左侧窄字体",
                checked = extras.showLeftNarrowFont == "on",
                onCheckedChange = { onSetSetting("show_left_narrow_font", if (it) "on" else "off") },
            )
            SwitchSettingRow(
                title = "右侧窄字体",
                checked = extras.showRightNarrowFont == "on",
                onCheckedChange = { onSetSetting("show_right_narrow_font", if (it) "on" else "off") },
            )
        }

        ChannelSectionTitle("焦点通知")
        ChannelSectionCard {
            SettingsDropdownRow("焦点图标", iconModeOptions, extras.focusIcon, true, largeText = true) {
                onSetSetting("focus_icon", it)
            }
            SettingsDropdownRow("焦点通知", focusOptions, extras.focus, true, largeText = true) { onSetSetting("focus", it) }
            SettingsDropdownRow(
                "状态栏图标",
                preserveStatusBarOptions,
                extras.preserveSmallIcon,
                extras.focus != "off",
                largeText = true,
            ) { onSetSetting("preserve_small_icon", it) }
            SettingsDropdownRow("锁屏通知恢复", restoreLockscreenOptions, extras.restoreLockscreen, true, largeText = true) {
                onSetSetting("restore_lockscreen", it)
            }
            SettingsDropdownRow("外圈光效", outerGlowOptions, extras.outerGlow, true, largeText = true) {
                onSetSetting("outer_glow", it)
            }
        }
    }
}

@Composable
private fun ChannelSectionTitle(title: String) {
    MiuixSmallTitle(
        text = title,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
    )
}

@Composable
private fun ChannelSectionCard(content: @Composable () -> Unit) {
    val isDarkTheme = isSystemInDarkTheme()
    val cardShape = RoundedCornerShape(18.dp)
    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .then(
                if (isDarkTheme) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.34f),
                        shape = cardShape,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsDropdownRow(
    title: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    enabled: Boolean,
    largeText: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    val selectedIndex = options.indexOfFirst { it.first == selectedValue }.coerceAtLeast(0)
    MiuixOverlayDropdownPreference(
        title = title,
        summary = if (largeText) null else options.getOrNull(selectedIndex)?.second,
        items = options.map { it.second },
        selectedIndex = selectedIndex,
        renderInRootScaffold = false,
        onSelectedIndexChange = { index ->
            val value = options.getOrNull(index)?.first ?: return@MiuixOverlayDropdownPreference
            onValueChange(value)
        },
        enabled = enabled,
    )
}

@Composable
private fun InputDialogRow(
    title: String,
    subtitle: String,
    value: String,
    emptyValueText: String,
    dialogTitle: String,
    dialogDescription: String,
    onConfirm: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value) }
    val inputFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val displayValue = value.ifBlank { emptyValueText }
    val titleColor = MiuixTheme.colorScheme.onBackground
    val summaryColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val valueColor = MiuixTheme.colorScheme.onSurfaceVariantActions

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable {
                draft = value
                showDialog = true
            }
            .padding(start = 16.dp, end = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = titleColor,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = summaryColor,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = displayValue,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = valueColor,
            )
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = valueColor,
            )
        }
    }

    OverlayDialog(
        title = dialogTitle,
        show = showDialog,
        onDismissRequest = { showDialog = false },
        renderInRootScaffold = false,
    ) {
        LaunchedEffect(showDialog) {
            if (showDialog) {
                delay(120)
                inputFocusRequester.requestFocus()
                keyboardController?.show()
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (dialogDescription.isNotBlank()) {
                Text(
                    text = dialogDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            MiuixTextField(
                value = draft,
                onValueChange = { draft = it },
                label = title,
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(inputFocusRequester),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiuixButton(
                    onClick = { showDialog = false },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("取消")
                }
                MiuixButton(
                    onClick = {
                        onConfirm(draft)
                        showDialog = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = MiuixButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        text = "确认",
                        color = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchApplyDialog(
    title: String,
    onDismiss: () -> Unit,
    onApply: (Map<String, String>) -> Unit,
) {
    val noChange = "__NO_CHANGE__"
    val triStateOptions = listOf(
        noChange to "不更改",
        "default" to "默认",
        "on" to "开启",
        "off" to "关闭",
    )
    val toggleOptions = listOf(
        noChange to "不更改",
        "on" to "开启",
        "off" to "关闭",
    )
    val iconModeOptions = listOf(
        noChange to "不更改",
        "auto" to "自动",
        "notif_small" to "通知小图标",
        "notif_large" to "通知大图标",
        "app_icon" to "应用图标",
    )
    val templateOptions = listOf(
        noChange to "不更改",
        "generic_progress" to "下载",
        "notification_island" to "通知超级岛",
        "notification_island_lite" to "通知超级岛 Lite",
        "download_lite" to "下载 Lite",
        "ai_notification_island" to "AI 通知超级岛",
    )
    val rendererOptions = listOf(
        noChange to "不更改",
        "image_text_with_buttons_4" to "新图文组件 + 底部文本按钮",
        "image_text_with_buttons_4_wrap" to "封面信息样式",
        "image_text_with_right_text_button" to "图文右侧文本按钮",
    )
    val dynamicHighlightOptions = listOf(
        noChange to "不更改",
        "default" to "默认",
        "on" to "开启",
        "off" to "关闭",
        "dark" to "暗",
        "darker" to "更暗",
    )

    var template by remember { mutableStateOf(noChange) }
    var renderer by remember { mutableStateOf(noChange) }
    var timeout by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(noChange) }
    var focusIcon by remember { mutableStateOf(noChange) }
    var focus by remember { mutableStateOf(noChange) }
    var preserveSmallIcon by remember { mutableStateOf(noChange) }
    var showIslandIcon by remember { mutableStateOf(noChange) }
    var firstFloat by remember { mutableStateOf(noChange) }
    var enableFloat by remember { mutableStateOf(noChange) }
    var marquee by remember { mutableStateOf(noChange) }
    var dynamicHighlightColor by remember { mutableStateOf(noChange) }
    var restoreLockscreen by remember { mutableStateOf(noChange) }
    var outerGlow by remember { mutableStateOf(noChange) }
    var showLeftHighlight by remember { mutableStateOf(noChange) }
    var showRightHighlight by remember { mutableStateOf(noChange) }
    var showLeftNarrowFont by remember { mutableStateOf(noChange) }
    var showRightNarrowFont by remember { mutableStateOf(noChange) }
    var highlightColor by remember { mutableStateOf("") }
    OverlayBottomSheet(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
        onDismissFinished = {},
        startAction = {
            MiuixIconButton(onClick = onDismiss) {
                FaIcon(
                    glyph = FaGlyph.Times,
                    contentDescription = "取消",
                )
            }
        },
        endAction = {
            MiuixIconButton(
                onClick = {
                    val settings = mutableMapOf<String, String>()
                    fun putIfChanged(key: String, value: String) {
                        if (value != noChange) settings[key] = value
                    }

                    putIfChanged("template", template)
                    putIfChanged("renderer", renderer)
                    putIfChanged("icon", icon)
                    putIfChanged("focus_icon", focusIcon)
                    putIfChanged("focus", focus)
                    putIfChanged("preserve_small_icon", preserveSmallIcon)
                    putIfChanged("show_island_icon", showIslandIcon)
                    putIfChanged("first_float", firstFloat)
                    putIfChanged("enable_float", enableFloat)
                    putIfChanged("marquee", marquee)
                    putIfChanged("dynamic_highlight_color", dynamicHighlightColor)
                    putIfChanged("restore_lockscreen", restoreLockscreen)
                    putIfChanged("outer_glow", outerGlow)
                    putIfChanged("show_left_highlight", showLeftHighlight)
                    putIfChanged("show_right_highlight", showRightHighlight)
                    putIfChanged("show_left_narrow_font", showLeftNarrowFont)
                    putIfChanged("show_right_narrow_font", showRightNarrowFont)

                    val normalizedTimeout = timeout.trim().toIntOrNull()?.coerceIn(1, 30)?.toString()
                    if (!normalizedTimeout.isNullOrEmpty()) {
                        settings["timeout"] = normalizedTimeout
                    }
                    if (highlightColor.trim().isNotEmpty()) {
                        settings["highlight_color"] = highlightColor.trim()
                    }

                    onApply(settings)
                },
            ) {
                FaIcon(
                    glyph = FaGlyph.Check,
                    contentDescription = "应用",
                )
            }
        },
        renderInRootScaffold = false,
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .overScrollVertical()
                    .scrollEndHaptic(),
            ) {
                ChannelSectionTitle("模板")
                ChannelSectionCard {
                    SettingsDropdownRow("模板", templateOptions, template, true, largeText = true) { template = it }
                    SettingsDropdownRow("样式", rendererOptions, renderer, true, largeText = true) { renderer = it }
                }

                ChannelSectionTitle("岛")
                ChannelSectionCard {
                    SettingsDropdownRow("超级岛图标", iconModeOptions, icon, true, largeText = true) { icon = it }
                    SettingsDropdownRow("大岛图标", triStateOptions, showIslandIcon, true, largeText = true) {
                        showIslandIcon = it
                    }
                    SettingsDropdownRow("初次展开", triStateOptions, firstFloat, true, largeText = true) {
                        firstFloat = it
                    }
                    SettingsDropdownRow("更新展开", triStateOptions, enableFloat, true, largeText = true) {
                        enableFloat = it
                    }
                    SettingsDropdownRow("消息滚动", triStateOptions, marquee, true, largeText = true) {
                        marquee = it
                    }
                    InputDialogRow(
                        title = "自动消失时长",
                        subtitle = "点击后在对话框中输入，留空表示不更改",
                        value = timeout,
                        emptyValueText = "不更改",
                        dialogTitle = "修改自动消失时长",
                        dialogDescription = "值应该大于等于 1 并小于等于 30，留空表示不更改",
                        onConfirm = { timeout = it.trim() },
                    )
                    InputDialogRow(
                        title = "高亮颜色",
                        subtitle = "点击后输入 #RRGGBB，留空表示不更改",
                        value = highlightColor,
                        emptyValueText = "不更改",
                        dialogTitle = "修改高亮颜色",
                        dialogDescription = "请输入 #RRGGBB 格式，留空表示不更改",
                        onConfirm = { highlightColor = it.trim() },
                    )
                    SettingsDropdownRow("高亮动态取色", dynamicHighlightOptions, dynamicHighlightColor, true, largeText = true) {
                        dynamicHighlightColor = it
                    }
                    SettingsDropdownRow("左侧高亮", toggleOptions, showLeftHighlight, true, largeText = true) {
                        showLeftHighlight = it
                    }
                    SettingsDropdownRow("右侧高亮", toggleOptions, showRightHighlight, true, largeText = true) {
                        showRightHighlight = it
                    }
                    SettingsDropdownRow("左侧窄字体", toggleOptions, showLeftNarrowFont, true, largeText = true) {
                        showLeftNarrowFont = it
                    }
                    SettingsDropdownRow("右侧窄字体", toggleOptions, showRightNarrowFont, true, largeText = true) {
                        showRightNarrowFont = it
                    }
                }

                ChannelSectionTitle("焦点通知")
                ChannelSectionCard {
                    SettingsDropdownRow("焦点图标", iconModeOptions, focusIcon, true, largeText = true) {
                        focusIcon = it
                    }
                    SettingsDropdownRow("焦点通知", triStateOptions, focus, true, largeText = true) { focus = it }
                    SettingsDropdownRow("状态栏图标", triStateOptions, preserveSmallIcon, true, largeText = true) {
                        preserveSmallIcon = it
                    }
                    SettingsDropdownRow("锁屏通知恢复", triStateOptions, restoreLockscreen, true, largeText = true) {
                        restoreLockscreen = it
                    }
                    SettingsDropdownRow("外圈光效", triStateOptions, outerGlow, true, largeText = true) {
                        outerGlow = it
                    }
                }
            }
        },
    )
}

@Composable
private fun AppItemRow(
    app: AppItem,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
) {
    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .pressable(interactionSource = remember { MutableInteractionSource() })
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppListIcon(app = app)
            Spacer(modifier = Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, fontWeight = FontWeight.SemiBold)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
            }
            if (selectionMode) {
                MiuixCheckbox(
                    state = if (selected) ToggleableState.On else ToggleableState.Off,
                    onClick = { onSelectedChange(!selected) },
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiuixSwitch(
                        checked = enabled,
                        onCheckedChange = { onEnabledChange(it) },
                    )
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = "进入渠道设置",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = MiuixTheme.textStyles.headline1.fontSize,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onBackground,
        )
        MiuixSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AppsScreenPreview() {
    MiuixTheme {
        MaterialTheme {
            AppsScreen(
                state = AppsUiState(
                    loading = false,
                    query = "",
                    showSystemApps = true,
                    apps = listOf(
                        AppItem("com.miui.home", "系统桌面", true),
                        AppItem("com.tencent.mm", "微信", false),
                        AppItem("com.ss.android.ugc.aweme", "抖音", false),
                    ),
                    enabledPackages = setOf("com.tencent.mm"),
                ),
                onRefresh = {},
                onQueryChange = {},
                onAppEnabledChange = { _, _ -> },
                onOpenAppChannels = {},
                onBatchApplyGlobal = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AppChannelsScreenPreview() {
    MiuixTheme {
        MaterialTheme {
            AppChannelsScreen(
                state = AppChannelsUiState(
                    packageName = "com.tencent.mm",
                    appName = "微信",
                    appEnabled = true,
                    loading = false,
                    channels = listOf(
                        ChannelItem("chat_msg", "聊天消息", "收到新消息时通知", 4),
                        ChannelItem("pay", "支付通知", "收付款结果提醒", 4),
                    ),
                    enabledChannels = setOf("chat_msg", "pay"),
                ),
                onRefresh = {},
                onSetAppEnabled = {},
                onToggleChannel = { _, _ -> },
                onEnableAllChannels = {},
                onOpenChannelSettings = { _, _ -> },
                onBatchApplyToEnabledChannels = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ChannelSettingsScreenPreview() {
    val channelId = "chat_msg"
    MiuixTheme {
        MaterialTheme {
            ChannelSettingsScreen(
                state = AppChannelsUiState(
                    packageName = "com.tencent.mm",
                    appName = "微信",
                    appEnabled = true,
                    loading = false,
                    channels = listOf(
                        ChannelItem(channelId, "聊天消息", "收到新消息时通知", 4),
                    ),
                    enabledChannels = setOf(channelId),
                    channelTemplates = mapOf(channelId to "notification_island"),
                    channelTimeout = mapOf(channelId to "5"),
                    channelExtras = mapOf(
                        channelId to ChannelExtraSettings(
                            icon = "app_icon",
                            focusIcon = "notif_small",
                            focus = "on",
                            preserveSmallIcon = "off",
                            showIslandIcon = "on",
                            firstFloat = "off",
                            enableFloat = "on",
                            marquee = "on",
                            renderer = "image_text_with_buttons_4",
                            restoreLockscreen = "off",
                            highlightColor = "#00C2FF",
                            dynamicHighlightColor = "dark",
                            showLeftHighlight = "on",
                            showRightHighlight = "off",
                            showLeftNarrowFont = "on",
                            showRightNarrowFont = "off",
                            outerGlow = "on",
                        ),
                    ),
                ),
                channelId = channelId,
                onRefresh = {},
                onSetTemplate = {},
                onSetTimeout = {},
                onSetSetting = { _, _ -> },
                onSetHighlightColor = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
