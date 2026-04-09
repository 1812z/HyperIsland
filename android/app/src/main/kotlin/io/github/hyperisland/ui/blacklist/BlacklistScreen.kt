package io.github.hyperisland.ui.blacklist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.hyperisland.ui.app.AppIcon
import io.github.hyperisland.ui.app.AppItem
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh as MiuixPullToRefresh
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun BlacklistScreen(
    state: BlacklistUiState,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSetBlacklisted: (String, Boolean) -> Unit,
    canPullToRefresh: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val pullToRefreshState = rememberPullToRefreshState()
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
                .overScrollVertical()
                .scrollEndHaptic(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.loading && state.apps.isEmpty()) {
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
            }

            state.error?.let {
                item {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (!state.loading && state.filteredApps.isEmpty()) {
                item {
                    EmptyBlacklistState(
                        query = state.query,
                        onClearQuery = { onQueryChange("") },
                    )
                }
            }
            items(state.filteredApps, key = { it.packageName }) { app ->
                val enabled = state.blacklistedPackages.contains(app.packageName)
                BlacklistAppRow(
                    app = app,
                    enabled = enabled,
                    onEnabledChange = { onSetBlacklisted(app.packageName, it) },
                )
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
                refreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新..."),
            ) {
                listContent()
            }
        } else {
            listContent()
        }
    }
}

@Composable
private fun EmptyBlacklistState(
    query: String,
    onClearQuery: () -> Unit,
) {
    MiuixCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (query.isBlank()) "没有可显示的应用" else "没有匹配的应用",
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (query.isBlank()) "可以尝试显示系统应用或下拉刷新。" else "换个关键词试试。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (query.isNotBlank()) {
                MiuixButton(onClick = onClearQuery) {
                    Text("清空搜索")
                }
            }
        }
    }
}

@Composable
private fun BlacklistAppRow(
    app: AppItem,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .pressable(interactionSource = remember { MutableInteractionSource() })
            .clickable { onEnabledChange(!enabled) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(icon = app.icon, fallbackIcon = MiuixIcons.Regular.All, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (app.isSystem) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "系统应用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MiuixSwitch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun BlacklistScreenPreview() {
    MiuixTheme {
        MaterialTheme {
            BlacklistScreen(
                state = BlacklistUiState(
                    loading = false,
                    query = "",
                    showSystemApps = false,
                    apps = listOf(
                        AppItem("com.tencent.mm", "微信", false),
                        AppItem("com.ss.android.ugc.aweme", "抖音", false),
                        AppItem("com.miui.weather2", "天气", true),
                    ),
                    filteredApps = listOf(
                        AppItem("com.ss.android.ugc.aweme", "抖音", false),
                        AppItem("com.tencent.mm", "微信", false),
                        AppItem("com.miui.weather2", "天气", true),
                    ),
                    blacklistedPackages = setOf("com.ss.android.ugc.aweme"),
                ),
                onRefresh = {},
                onQueryChange = {},
                onSetBlacklisted = { _, _ -> },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
