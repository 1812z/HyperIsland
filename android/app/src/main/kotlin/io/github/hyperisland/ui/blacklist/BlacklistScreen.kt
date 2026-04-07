package io.github.hyperisland.ui.blacklist

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField

@Composable
fun BlacklistScreen(
    state: BlacklistUiState,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onShowSystemChange: (Boolean) -> Unit,
    onSetBlacklisted: (String, Boolean) -> Unit,
    onEnableAllVisible: () -> Unit,
    onDisableAllVisible: () -> Unit,
    onApplyGamePreset: () -> Unit,
) {
    val filtered = state.apps.filter { app ->
        val matchSystem = state.showSystemApps || !app.isSystem || state.blacklistedPackages.contains(app.packageName)
        val q = state.query.trim().lowercase()
        val matchQuery = q.isBlank() || app.appName.lowercase().contains(q) || app.packageName.lowercase().contains(q)
        matchSystem && matchQuery
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        MiuixTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = "搜索应用 / 包名",
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("显示系统应用")
            MiuixSwitch(checked = state.showSystemApps, onCheckedChange = onShowSystemChange)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiuixButton(onClick = onApplyGamePreset) { Text("游戏预设") }
            MiuixButton(onClick = onEnableAllVisible) { Text("全部加入") }
            MiuixButton(onClick = onDisableAllVisible) { Text("全部移除") }
            MiuixButton(onClick = onRefresh) { Text("刷新") }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("黑名单应用：${state.blacklistedPackages.size}")

        if (state.loading) {
            Spacer(modifier = Modifier.height(20.dp))
            MiuixCircularProgressIndicator()
            return
        }

        state.error?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filtered, key = { it.packageName }) { app ->
                val enabled = state.blacklistedPackages.contains(app.packageName)
                MiuixCard(modifier = Modifier.fillMaxWidth()) {
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
                            onCheckedChange = { onSetBlacklisted(app.packageName, it) },
                        )
                    }
                }
            }
        }
    }
}
