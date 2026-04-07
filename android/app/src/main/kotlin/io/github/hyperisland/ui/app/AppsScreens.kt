package io.github.hyperisland.ui.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh as MiuixPullToRefresh
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.overlay.OverlayDialog as MiuixOverlayDialog

@Composable
fun AppsScreen(
    state: AppsUiState,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onAppEnabledChange: (String, Boolean) -> Unit,
    onOpenAppChannels: (String) -> Unit,
    onBatchApplyGlobal: (Map<String, String>) -> Unit,
    batchRequestId: Int = 0,
    modifier: Modifier = Modifier,
) {
    var showBatchDialog by remember { mutableStateOf(false) }
    LaunchedEffect(batchRequestId) {
        if (batchRequestId > 0) showBatchDialog = true
    }
    val pullToRefreshState = rememberPullToRefreshState()
    val filtered = state.apps.filter { app ->
        val matchSystem = state.showSystemApps || !app.isSystem || state.enabledPackages.contains(app.packageName)
        val q = state.query.trim().lowercase()
        val matchQuery = q.isBlank() || app.appName.lowercase().contains(q) || app.packageName.lowercase().contains(q)
        matchSystem && matchQuery
    }

    MiuixPullToRefresh(
        isRefreshing = state.loading,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        pullToRefreshState = pullToRefreshState,
        refreshTexts = listOf(
            "下拉刷新",
            "松开刷新",
            "正在刷新...",
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            MiuixTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = "搜索应用 / 包名",
                singleLine = true,
            )

            if (state.loading && state.apps.isEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                MiuixCircularProgressIndicator()
            }

            state.error?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("已启用应用：${state.enabledPackages.size}")
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    val enabled = state.enabledPackages.contains(app.packageName)
                    MiuixCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAppChannels(app.packageName) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName, fontWeight = FontWeight.SemiBold)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                            }
                            MiuixSwitch(
                                checked = enabled,
                                onCheckedChange = { onAppEnabledChange(app.packageName, it) },
                            )
                        }
                    }
                }
            }
        }
    }
    if (showBatchDialog) {
        BatchApplyDialog(
            onDismiss = { showBatchDialog = false },
            onApply = { settings ->
                showBatchDialog = false
                onBatchApplyGlobal(settings)
            },
        )
    }
}

@Composable
fun AppChannelsScreen(
    state: AppChannelsUiState,
    onRefresh: () -> Unit,
    onToggleChannel: (String, Boolean) -> Unit,
    onEnableAllChannels: () -> Unit,
    onCycleTemplate: (String) -> Unit,
    onSetTimeout: (String, String) -> Unit,
    onCycleSetting: (String, String) -> Unit,
    onSetHighlightColor: (String, String) -> Unit,
    onBatchApplyToEnabledChannels: (Map<String, String>) -> Unit,
) {
    val channels = state.channels
    var showBatchDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(state.packageName, style = MaterialTheme.typography.bodyMedium)

        if (state.loading) {
            Spacer(modifier = Modifier.height(20.dp))
            MiuixCircularProgressIndicator()
            return
        }

        state.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiuixButton(onClick = onRefresh) { Text("重试") }
            }
            return
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiuixButton(onClick = onEnableAllChannels) { Text("全部渠道生效") }
            MiuixButton(onClick = { showBatchDialog = true }) { Text("批量应用") }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (channels.isEmpty()) {
            Text(
                "未读取到通知渠道，可尝试点击“重试”或确认 Root 权限",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            MiuixButton(onClick = onRefresh) { Text("重试") }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(channels, key = { it.id }) { channel ->
                    val enabled = state.enabledChannels.isEmpty() || state.enabledChannels.contains(channel.id)
                    val template = state.channelTemplates[channel.id] ?: "notification_island"
                    val timeout = state.channelTimeout[channel.id] ?: "5"
                    val extras = state.channelExtras[channel.id] ?: ChannelExtraSettings()
                    ChannelCard(
                        channel = channel,
                        enabled = enabled,
                        template = template,
                        timeout = timeout,
                        extras = extras,
                        onEnableChange = { onToggleChannel(channel.id, it) },
                        onCycleTemplate = { onCycleTemplate(channel.id) },
                        onSetTimeout = { onSetTimeout(channel.id, it) },
                        onCycleSetting = { setting -> onCycleSetting(channel.id, setting) },
                        onSetHighlightColor = { onSetHighlightColor(channel.id, it) },
                    )
                }
            }
        }
    }

    if (showBatchDialog) {
        BatchApplyDialog(
            onDismiss = { showBatchDialog = false },
            onApply = { settings ->
                showBatchDialog = false
                onBatchApplyToEnabledChannels(settings)
            },
        )
    }
}

@Composable
private fun ChannelCard(
    channel: ChannelItem,
    enabled: Boolean,
    template: String,
    timeout: String,
    extras: ChannelExtraSettings,
    onEnableChange: (Boolean) -> Unit,
    onCycleTemplate: () -> Unit,
    onSetTimeout: (String) -> Unit,
    onCycleSetting: (String) -> Unit,
    onSetHighlightColor: (String) -> Unit,
) {
    var highlightDraft by remember(extras.highlightColor) { mutableStateOf(extras.highlightColor) }
    MiuixCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(channel.name, fontWeight = FontWeight.SemiBold)
                    Text(channel.id, style = MaterialTheme.typography.bodySmall)
                }
                MiuixSwitch(
                    checked = enabled,
                    onCheckedChange = onEnableChange,
                )
            }
            Text("重要性: ${channel.importance}")
            if (channel.description.isNotBlank()) {
                Text("描述: ${channel.description}", style = MaterialTheme.typography.bodySmall)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("模板: $template")
                MiuixButton(onClick = onCycleTemplate) {
                    Text("切换模板")
                }
            }

            MiuixTextField(
                value = timeout,
                onValueChange = { onSetTimeout(it) },
                label = "超时秒数(1-30)",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsCycleRow("图标来源", extras.icon) { onCycleSetting("icon") }
            SettingsCycleRow("焦点图标", extras.focusIcon) { onCycleSetting("focus_icon") }
            SettingsCycleRow("焦点通知", extras.focus) { onCycleSetting("focus") }
            SettingsCycleRow("保留状态栏小图标", extras.preserveSmallIcon) { onCycleSetting("preserve_small_icon") }
            SettingsCycleRow("显示岛图标", extras.showIslandIcon) { onCycleSetting("show_island_icon") }
            SettingsCycleRow("首次展开", extras.firstFloat) { onCycleSetting("first_float") }
            SettingsCycleRow("更新展开", extras.enableFloat) { onCycleSetting("enable_float") }
            SettingsCycleRow("跑马灯", extras.marquee) { onCycleSetting("marquee") }
            SettingsCycleRow("渲染器", extras.renderer) { onCycleSetting("renderer") }
            SettingsCycleRow("锁屏恢复", extras.restoreLockscreen) { onCycleSetting("restore_lockscreen") }
            SettingsCycleRow("左侧高亮", extras.showLeftHighlight) { onCycleSetting("show_left_highlight") }
            SettingsCycleRow("右侧高亮", extras.showRightHighlight) { onCycleSetting("show_right_highlight") }
            MiuixTextField(
                value = highlightDraft,
                onValueChange = {
                    highlightDraft = it
                    onSetHighlightColor(it)
                },
                label = "高亮颜色(#RRGGBB，可空)",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SettingsCycleRow(title: String, value: String, onCycle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$title: $value", modifier = Modifier.weight(1f))
        MiuixButton(onClick = onCycle) { Text("切换") }
    }
}

@Composable
private fun BatchApplyDialog(
    onDismiss: () -> Unit,
    onApply: (Map<String, String>) -> Unit,
) {
    var template by remember { mutableStateOf("notification_island") }
    var timeout by remember { mutableStateOf("5") }
    var focus by remember { mutableStateOf("default") }
    MiuixOverlayDialog(
        show = true,
        title = "批量应用到已启用渠道",
        summary = "",
        onDismissRequest = onDismiss,
        onDismissFinished = {},
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MiuixTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = "模板ID",
                    singleLine = true,
                )
                MiuixTextField(
                    value = timeout,
                    onValueChange = { timeout = it },
                    label = "超时(1-30)",
                    singleLine = true,
                )
                MiuixTextField(
                    value = focus,
                    onValueChange = { focus = it },
                    label = "焦点通知(default/on/off)",
                    singleLine = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiuixButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                MiuixButton(
                    onClick = {
                        onApply(
                            mapOf(
                                "template" to template.ifBlank { "notification_island" },
                                "timeout" to (timeout.toIntOrNull()?.coerceIn(1, 30)?.toString() ?: "5"),
                                "focus" to focus.ifBlank { "default" },
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("应用")
                }
            }
        }
    }
}
