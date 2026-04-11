package io.github.hyperisland.ui.app

import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import io.github.hyperisland.data.prefs.PrefKeys
import io.github.hyperisland.ui.FaGlyph
import io.github.hyperisland.ui.FaIcon
import io.github.hyperisland.ui.isAppInDarkTheme
import io.github.hyperisland.ui.textOf
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator as MiuixInfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh as MiuixPullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle as MiuixSmallTitle
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.ColorPalette as MiuixColorPalette
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
private fun sectionCardModifier(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
): Modifier {
    val isDarkTheme = isAppInDarkTheme()
    return modifier
        .clip(shape)
        .then(
            if (isDarkTheme) {
                Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.34f),
                    shape = shape,
                )
            } else {
                Modifier
            },
        )
}

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
    val listContentPadding = PaddingValues(
        top = topPadding + 12.dp,
        bottom = contentPadding.calculateBottomPadding(),
    )
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
            contentPadding = listContentPadding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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

    Box(modifier = modifier.fillMaxSize()) {
        if (canPullToRefresh) {
            MiuixPullToRefresh(
                isRefreshing = state.loading && state.apps.isNotEmpty(),
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = if (canPullToRefresh) topPadding.toPx() else 0f
                        clip = false
                },
                pullToRefreshState = pullToRefreshState,
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                refreshTexts = listOf(
                    textOf("下拉刷新", "Pull to Refresh"),
                    textOf("松开刷新", "Release to Refresh"),
                    textOf("正在刷新...", "Refreshing..."),
                ),
            ) {
                listContent()
            }
        } else {
            listContent()
        }
        if (state.loading && state.apps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                MiuixInfiniteProgressIndicator(
                    modifier = Modifier.size(72.dp),
                )
            }
        }
    }
    if (showBatchDialog) {
        BatchApplyDialog(
            title = if (batchForSelected || selectionMode) {
                textOf("批量应用到已选应用的渠道", "Batch Apply to Selected Apps")
            } else {
                textOf("批量应用到已启用应用", "Batch Apply to Enabled Apps")
            },
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
            MiuixCard(modifier = sectionCardModifier(Modifier.fillMaxWidth())) {
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
                            color = MaterialTheme.colorScheme.onBackground,
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
                    MiuixInfiniteProgressIndicator()
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
                    MiuixButton(onClick = onRefresh) {
                        Text(textOf("重试", "Retry"), color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
            return@LazyColumn
        }

        if (!state.appEnabled) {
            item {
                MiuixCard(modifier = sectionCardModifier(Modifier.fillMaxWidth())) {
                    Text(
                        text = textOf("请先开启应用总开关后再配置通知渠道", "Enable the app switch before configuring notification channels"),
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
                        textOf("未读取到通知渠道", "No notification channels found"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MiuixButton(onClick = onRefresh) {
                        Text(textOf("刷新", "Refresh"), color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
        } else {
            item {
                MiuixCard(modifier = sectionCardModifier(Modifier.fillMaxWidth())) {
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
            title = textOf("批量应用到已启用渠道", "Batch Apply to Enabled Channels"),
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
            MiuixInfiniteProgressIndicator()
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
                    MiuixButton(onClick = onRefresh) {
                        Text(textOf("重试", "Retry"), color = MaterialTheme.colorScheme.onBackground)
                    }
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
            Text(textOf("未找到该通知渠道", "Notification channel not found"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                MiuixButton(onClick = onRefresh) {
                    Text(textOf("刷新", "Refresh"), color = MaterialTheme.colorScheme.onBackground)
                }
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
                    Text(
                        channel.name,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${textOf("重要性", "Importance")}: ${channel.importance}",
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
                        contentDescription = textOf("渠道设置", "Channel Settings"),
                        tint = if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    )
                }
                MiuixSwitch(
                    checked = enabled,
                    onCheckedChange = onEnableChange,
                )
            }
            if (channel.description.isNotBlank()) {
                Text(
                    "${textOf("描述", "Description")}: ${channel.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    val uiLanguage = io.github.hyperisland.ui.LocalUiLanguage.current
    val context = LocalContext.current
    val defaultDynamicHighlightEnabled = remember(context) {
        context.getSharedPreferences(PrefKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PrefKeys.DEFAULT_DYNAMIC_HIGHLIGHT_COLOR, false)
    }
    val dynamicHighlightEnabled = remember(extras.dynamicHighlightColor, defaultDynamicHighlightEnabled) {
        isDynamicHighlightEnabled(extras.dynamicHighlightColor, defaultDynamicHighlightEnabled)
    }
    val hasHighlightColor = dynamicHighlightEnabled || extras.highlightColor.trim().isNotEmpty()
    val focusDisabled = extras.focus == "off"
    val preserveSmallIconValue = if (focusDisabled) "off" else extras.preserveSmallIcon

    fun triStateOptions(defaultOn: Boolean): List<Pair<String, String>> = listOf(
        "default" to if (defaultOn) textOf(uiLanguage, "默认(开启)", "Default (On)") else textOf(uiLanguage, "默认(关闭)", "Default (Off)"),
        "on" to textOf(uiLanguage, "开启", "On"),
        "off" to textOf(uiLanguage, "关闭", "Off"),
    )

    val templateOptions = listOf(
        "generic_progress" to textOf("下载", "Download"),
        "notification_island" to textOf("通知超级岛", "Notification Island"),
        "ai_notification_island" to textOf("AI 通知超级岛", "AI Notification Island"),
    )
    val iconModeOptions = listOf(
        "auto" to textOf("自动", "Auto"),
        "notif_small" to textOf("通知小图标", "Small Notification Icon"),
        "notif_large" to textOf("通知大图标", "Large Notification Icon"),
        "app_icon" to textOf("应用图标", "App Icon"),
    )
    val showIslandIconOptions = triStateOptions(defaultOn = true)
    val firstFloatOptions = triStateOptions(defaultOn = false)
    val enableFloatOptions = triStateOptions(defaultOn = false)
    val marqueeOptions = triStateOptions(defaultOn = false)
    val focusOptions = triStateOptions(defaultOn = true)
    val preserveStatusBarOptions = triStateOptions(defaultOn = false)
    val restoreLockscreenOptions = triStateOptions(defaultOn = false)
    val dynamicHighlightOptions = listOf(
        "default" to textOf("默认(关闭)", "Default (Off)"),
        "on" to textOf("开启", "On"),
        "off" to textOf("关闭", "Off"),
        "dark" to textOf("暗", "Dark"),
        "darker" to textOf("更暗", "Darker"),
    )
    val outerGlowOptions = triStateOptions(defaultOn = false)
    val rendererOptions = listOf(
        "image_text_with_buttons_4" to textOf("新图文组件 + 底部文本按钮", "Image+Text+Bottom Text Buttons"),
        "image_text_with_buttons_4_wrap" to textOf("封面组件 + 自动换行", "Cover Info+Auto Wrap"),
        "image_text_with_right_text_button" to textOf("新图文组件 + 右侧文本按钮", "Image+Text+Right Text Button"),
        "image_text_with_progress" to textOf("IM 图文组件 + 进度条", "IM Chat Info + Progress"),
    )
    var highlightDraft by remember(extras.highlightColor) { mutableStateOf(extras.highlightColor) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ChannelSectionTitle(textOf("模板", "Template"))
        ChannelSectionCard(
            verticalPadding = 0.dp,
            itemSpacing = 0.dp,
        ) {
            SettingsDropdownRow(
                title = textOf("模板", "Template"),
                options = templateOptions,
                selectedValue = template,
                enabled = true,
                largeText = true,
                onValueChange = onSetTemplate,
            )
            SettingsDropdownRow(textOf("样式", "Style"), rendererOptions, extras.renderer, true, largeText = true) {
                onSetSetting("renderer", it)
            }
        }

        ChannelSectionTitle(textOf("岛", "Island"))
        ChannelSectionCard(
            verticalPadding = 0.dp,
            itemSpacing = 0.dp,
        ) {
            SettingsDropdownRow(textOf("超级岛图标", "Dynamic Island Icon"), iconModeOptions, extras.icon, true, largeText = true) { onSetSetting("icon", it) }
            SettingsDropdownRow(textOf("大岛图标", "Large Island Icon"), showIslandIconOptions, extras.showIslandIcon, true, largeText = true) {
                onSetSetting("show_island_icon", it)
            }
            SettingsDropdownRow(textOf("初次展开", "Expand on First Arrival"), firstFloatOptions, extras.firstFloat, true, largeText = true) {
                onSetSetting("first_float", it)
            }
            SettingsDropdownRow(textOf("更新展开", "Expand on Update"), enableFloatOptions, extras.enableFloat, true, largeText = true) {
                onSetSetting("enable_float", it)
            }
            SettingsDropdownRow(textOf("消息滚动速度", "Marquee Speed"), marqueeOptions, extras.marquee, true, largeText = true) {
                onSetSetting("marquee", it)
            }
            InputDialogRow(
                title = textOf("自动消失时长", "Auto Dismiss Timeout"),
                subtitle = textOf("点击后在对话框中输入 1-30 秒", "Tap to enter a value between 1 and 30 seconds"),
                value = timeout,
                emptyValueText = "5",
                dialogTitle = textOf("修改自动消失时长", "Edit Auto Dismiss Timeout"),
                dialogDescription = textOf("值应该大于等于 1 并小于等于 30", "The value must be between 1 and 30"),
                onConfirm = { onSetTimeout(it.trim()) },
            )
            InputDialogRow(
                title = textOf("高亮颜色", "Highlight Color"),
                subtitle = if (dynamicHighlightEnabled) {
                    textOf("动态取色开启时不可编辑", "Disabled while dynamic highlight is enabled")
                } else {
                    textOf("点击后输入 #RRGGBB，留空可清空", "Tap to enter #RRGGBB, or leave blank to clear")
                },
                value = highlightDraft,
                emptyValueText = textOf("未设置", "Not Set"),
                dialogTitle = textOf("修改高亮颜色", "Edit Highlight Color"),
                dialogDescription = textOf("请输入 #RRGGBB 格式，留空可清空当前颜色", "Enter a color in #RRGGBB format, or leave blank to clear"),
                enableColorPalette = true,
                enabled = !dynamicHighlightEnabled,
                onConfirm = {
                    val next = it.trim()
                    highlightDraft = next
                    onSetHighlightColor(next)
                },
            )
            InputDialogRow(
                title = textOf("外圈光效颜色", "Outer Glow Color"),
                subtitle = textOf("点击后输入 #RRGGBB，留空可清空", "Tap to enter #RRGGBB, or leave blank to clear"),
                value = extras.outEffectColor,
                emptyValueText = textOf("未设置", "Not Set"),
                dialogTitle = textOf("修改外圈光效颜色", "Edit Outer Glow Color"),
                dialogDescription = textOf("请输入 #RRGGBB 格式，留空可清空当前颜色", "Enter a color in #RRGGBB format, or leave blank to clear"),
                enableColorPalette = true,
                onConfirm = { onSetSetting("out_effect_color", it.trim()) },
            )
            SwitchSettingRow(
                title = textOf("左侧高亮", "Left Highlight"),
                checked = extras.showLeftHighlight == "on",
                enabled = hasHighlightColor,
                onCheckedChange = { onSetSetting("show_left_highlight", if (it) "on" else "off") },
            )
            SwitchSettingRow(
                title = textOf("右侧高亮", "Right Highlight"),
                checked = extras.showRightHighlight == "on",
                enabled = hasHighlightColor,
                onCheckedChange = { onSetSetting("show_right_highlight", if (it) "on" else "off") },
            )
            SettingsDropdownRow(
                textOf("高亮动态取色", "Dynamic Highlight Color"),
                dynamicHighlightOptions,
                extras.dynamicHighlightColor,
                true,
                largeText = true,
            ) {
                onSetSetting("dynamic_highlight_color", it)
            }
            SwitchSettingRow(
                title = textOf("左侧窄字体", "Left Narrow Font"),
                checked = extras.showLeftNarrowFont == "on",
                onCheckedChange = { onSetSetting("show_left_narrow_font", if (it) "on" else "off") },
            )
            SwitchSettingRow(
                title = textOf("右侧窄字体", "Right Narrow Font"),
                checked = extras.showRightNarrowFont == "on",
                onCheckedChange = { onSetSetting("show_right_narrow_font", if (it) "on" else "off") },
            )
        }

        ChannelSectionTitle(textOf("焦点通知", "Focus Notification"))
        ChannelSectionCard(
            verticalPadding = 0.dp,
            itemSpacing = 0.dp,
        ) {
            SettingsDropdownRow(textOf("焦点通知", "Focus Notification"), focusOptions, extras.focus, true, largeText = true) {
                onSetSetting("focus", it)
                if (it == "off" && extras.preserveSmallIcon != "off") {
                    onSetSetting("preserve_small_icon", "off")
                }
            }
            SettingsDropdownRow(
                textOf("状态栏图标", "Status Bar Icon"),
                preserveStatusBarOptions,
                preserveSmallIconValue,
                !focusDisabled,
                largeText = true,
            ) { onSetSetting("preserve_small_icon", it) }
            SettingsDropdownRow(textOf("锁屏通知恢复", "Restore on Lock Screen"), restoreLockscreenOptions, extras.restoreLockscreen, true, largeText = true) {
                onSetSetting("restore_lockscreen", it)
            }
            SettingsDropdownRow(textOf("外圈光效", "Outer Glow"), outerGlowOptions, extras.outerGlow, true, largeText = true) {
                onSetSetting("outer_glow", it)
            }
            InputDialogRow(
                title = textOf("焦点表达式自定义", "Custom Focus Expression"),
                subtitle = textOf("点击后输入 JSON，留空可清空", "Tap to enter JSON, or leave blank to clear"),
                value = extras.focusCustom,
                emptyValueText = textOf("未设置", "Not Set"),
                dialogTitle = textOf("修改焦点表达式自定义", "Edit Custom Focus Expression"),
                dialogDescription = textOf("请输入 JSON 字符串，留空可清空", "Enter a JSON string, or leave blank to clear"),
                onConfirm = { onSetSetting("focus_custom", it.trim()) },
            )
            InputDialogRow(
                title = textOf("岛表达式自定义", "Custom Island Expression"),
                subtitle = textOf("点击后输入 JSON，留空可清空", "Tap to enter JSON, or leave blank to clear"),
                value = extras.islandCustom,
                emptyValueText = textOf("未设置", "Not Set"),
                dialogTitle = textOf("修改岛表达式自定义", "Edit Custom Island Expression"),
                dialogDescription = textOf("请输入 JSON 字符串，留空可清空", "Enter a JSON string, or leave blank to clear"),
                onConfirm = { onSetSetting("island_custom", it.trim()) },
            )
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
private fun ChannelSectionCard(
    verticalPadding: Dp = 8.dp,
    itemSpacing: Dp = 6.dp,
    content: @Composable () -> Unit,
) {
    MiuixCard(modifier = sectionCardModifier(Modifier.fillMaxWidth())) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            content()
        }
    }
}

@Composable
private fun BatchSheetSectionCard(content: @Composable () -> Unit) {
    val isDarkTheme = isAppInDarkTheme()
    val shape = RoundedCornerShape(18.dp)
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
        alpha = if (isDarkTheme) 0.42f else 0.65f,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .then(
                if (isDarkTheme) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.36f),
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
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
    enableColorPalette: Boolean = false,
    enabled: Boolean = true,
    onConfirm: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    var draft by remember(value) { mutableStateOf(value) }
    val inputFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val displayValue = value.ifBlank { emptyValueText }
    val titleColor = if (enabled) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f)
    val summaryColor = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantSummary else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f)
    val valueColor = if (enabled) MiuixTheme.colorScheme.onSurfaceVariantActions else MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.5f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled) {
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
            if (enableColorPalette) {
                MiuixColorPalette(
                    color = parseHexColorOrNull(draft) ?: MaterialTheme.colorScheme.primary,
                    onColorChanged = { color ->
                        draft = colorToHexRgb(color)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiuixButton(
                    onClick = { showDialog = false },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("取消", color = MaterialTheme.colorScheme.onBackground)
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
    val context = LocalContext.current
    val defaultDynamicHighlightEnabled = remember(context) {
        context.getSharedPreferences(PrefKeys.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PrefKeys.DEFAULT_DYNAMIC_HIGHLIGHT_COLOR, false)
    }
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
        "ai_notification_island" to "AI 通知超级岛",
    )
    val rendererOptions = listOf(
        noChange to "不更改",
        "image_text_with_buttons_4" to "新图文组件 + 底部文本按钮",
        "image_text_with_buttons_4_wrap" to "封面组件 + 自动换行",
        "image_text_with_right_text_button" to "新图文组件 + 右侧文本按钮",
        "image_text_with_progress" to "IM 图文组件 + 进度条",
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
    var outEffectColor by remember { mutableStateOf("") }
    var focusCustom by remember { mutableStateOf("") }
    var islandCustom by remember { mutableStateOf("") }
    val dynamicHighlightEnabled = remember(dynamicHighlightColor, defaultDynamicHighlightEnabled) {
        isDynamicHighlightEnabled(
            value = dynamicHighlightColor,
            defaultEnabled = defaultDynamicHighlightEnabled,
            noChangeValue = noChange,
        )
    }
    val hasHighlightColor = dynamicHighlightEnabled || highlightColor.trim().isNotEmpty()
    val focusDisabled = focus == "off"
    val preserveSmallIconValue = if (focusDisabled) "off" else preserveSmallIcon
    OverlayBottomSheet(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
        onDismissFinished = {},
        enableNestedScroll = false,
        startAction = {
            MiuixIconButton(onClick = onDismiss) {
                FaIcon(
                    glyph = FaGlyph.Times,
                    contentDescription = "取消",
                    tint = MaterialTheme.colorScheme.onBackground,
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
                    putIfChanged("focus", focus)
                    putIfChanged("preserve_small_icon", preserveSmallIconValue)
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
                    if (outEffectColor.trim().isNotEmpty()) {
                        settings["out_effect_color"] = outEffectColor.trim()
                    }
                    if (focusCustom.trim().isNotEmpty()) {
                        settings["focus_custom"] = focusCustom.trim()
                    }
                    if (islandCustom.trim().isNotEmpty()) {
                        settings["island_custom"] = islandCustom.trim()
                    }

                    onApply(settings)
                },
            ) {
                FaIcon(
                    glyph = FaGlyph.Check,
                    contentDescription = "应用",
                    tint = MaterialTheme.colorScheme.onBackground,
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
                BatchSheetSectionCard {
                    SettingsDropdownRow("模板", templateOptions, template, true, largeText = true) { template = it }
                    SettingsDropdownRow("样式", rendererOptions, renderer, true, largeText = true) { renderer = it }
                }

                ChannelSectionTitle("岛")
                BatchSheetSectionCard {
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
                    SettingsDropdownRow("消息滚动速度", triStateOptions, marquee, true, largeText = true) {
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
                        subtitle = if (dynamicHighlightEnabled) "动态取色开启时不可编辑" else "点击后输入 #RRGGBB，留空表示不更改",
                        value = highlightColor,
                        emptyValueText = "不更改",
                        dialogTitle = "修改高亮颜色",
                        dialogDescription = "请输入 #RRGGBB 格式，留空表示不更改",
                        enableColorPalette = true,
                        enabled = !dynamicHighlightEnabled,
                        onConfirm = { highlightColor = it.trim() },
                    )
                    InputDialogRow(
                        title = "外圈光效颜色",
                        subtitle = "点击后输入 #RRGGBB，留空表示不更改",
                        value = outEffectColor,
                        emptyValueText = "不更改",
                        dialogTitle = "修改外圈光效颜色",
                        dialogDescription = "请输入 #RRGGBB 格式，留空表示不更改",
                        enableColorPalette = true,
                        onConfirm = { outEffectColor = it.trim() },
                    )
                    SettingsDropdownRow("高亮动态取色", dynamicHighlightOptions, dynamicHighlightColor, true, largeText = true) {
                        dynamicHighlightColor = it
                    }
                    SettingsDropdownRow("左侧高亮", toggleOptions, showLeftHighlight, hasHighlightColor, largeText = true) {
                        showLeftHighlight = it
                    }
                    SettingsDropdownRow("右侧高亮", toggleOptions, showRightHighlight, hasHighlightColor, largeText = true) {
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
                BatchSheetSectionCard {
                    SettingsDropdownRow("焦点通知", triStateOptions, focus, true, largeText = true) {
                        focus = it
                        if (it == "off") {
                            preserveSmallIcon = "off"
                        }
                    }
                    SettingsDropdownRow("状态栏图标", triStateOptions, preserveSmallIconValue, !focusDisabled, largeText = true) {
                        preserveSmallIcon = it
                    }
                    SettingsDropdownRow("锁屏通知恢复", triStateOptions, restoreLockscreen, true, largeText = true) {
                        restoreLockscreen = it
                    }
                    SettingsDropdownRow("外圈光效", triStateOptions, outerGlow, true, largeText = true) {
                        outerGlow = it
                    }
                    InputDialogRow(
                        title = "焦点表达式自定义",
                        subtitle = "点击后输入 JSON，留空表示不更改",
                        value = focusCustom,
                        emptyValueText = "不更改",
                        dialogTitle = "修改焦点表达式自定义",
                        dialogDescription = "请输入 JSON 字符串，留空表示不更改",
                        onConfirm = { focusCustom = it.trim() },
                    )
                    InputDialogRow(
                        title = "岛表达式自定义",
                        subtitle = "点击后输入 JSON，留空表示不更改",
                        value = islandCustom,
                        emptyValueText = "不更改",
                        dialogTitle = "修改岛表达式自定义",
                        dialogDescription = "请输入 JSON 字符串，留空表示不更改",
                        onConfirm = { islandCustom = it.trim() },
                    )
                }
            }
        },
    )
}

private fun isDynamicHighlightEnabled(
    value: String,
    defaultEnabled: Boolean,
    noChangeValue: String? = null,
): Boolean {
    if (value == noChangeValue) return false
    return when (value) {
        "default" -> defaultEnabled
        "on", "dark", "darker" -> true
        else -> false
    }
}

private fun parseHexColorOrNull(raw: String): Color? {
    val text = raw.trim()
    if (text.isBlank()) return null
    val normalized = if (text.startsWith("#")) text else "#$text"
    return runCatching { Color(android.graphics.Color.parseColor(normalized)) }.getOrNull()
}

private fun colorToHexRgb(color: Color): String {
    val red = (color.red * 255f).toInt().coerceIn(0, 255)
    val green = (color.green * 255f).toInt().coerceIn(0, 255)
    val blue = (color.blue * 255f).toInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X", red, green, blue)
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
    val cardShape = RoundedCornerShape(18.dp)
    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .then(sectionCardModifier(shape = cardShape))
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
                Text(
                    app.appName,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    enabled: Boolean = true,
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
            color = if (enabled) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )
        MiuixSwitch(checked = checked, onCheckedChange = if (enabled) onCheckedChange else ({ _: Boolean -> }))
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
