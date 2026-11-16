package io.github.hyperisland.compose.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.data.Donor
import io.github.hyperisland.compose.data.DonorBoard
import io.github.hyperisland.compose.data.formatGeneratedAt
import io.github.hyperisland.compose.service.SiteDataClient
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 捐赠名单。
 *
 * 数据来自站点构建期产物 `data/donors.json`（同源于 `docs/.vitepress/data/donors.json`），
 * 与网页展示的是同一份记录，只是在 App 里按原生列表渲染，并且倒序 ——
 * 进来先看到最近谁支持过，而不是从 2026 年 3 月一条条往下翻。
 */
@Composable
internal fun DonorsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var board by remember { mutableStateOf<DonorBoard?>(null) }
    var loading by remember { mutableStateOf(true) }
    var stale by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = SiteDataClient.loadDonors(context)
        board = result.data
        stale = result.stale
        failed = result.data == null && result.error != null
        loading = false
    }

    DetailPage(
        title = stringResource(R.string.donation_list),
        onBack = onBack,
        actionIcon = MiuixIcons.Refresh,
        actionDescription = stringResource(R.string.refresh_list),
        onAction = {
            if (!refreshing) {
                scope.launch {
                    refreshing = true
                    val result = SiteDataClient.loadDonors(context, force = true)
                    if (result.data != null) {
                        board = result.data
                        stale = result.stale
                    }
                    refreshing = false
                }
            }
        },
    ) {
        item { DonationThanksCard(board) }

        if (stale && board != null) {
            item { SiteDataMessageCard(stringResource(R.string.site_data_stale)) }
        }

        item { SectionTitle(stringResource(R.string.donation_list)) }

        val donors = board?.recentFirst.orEmpty()
        if (donors.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        donors.forEachIndexed { index, donor ->
                            DonorRow(donor)
                            if (index != donors.lastIndex) {
                                DonorDivider()
                            }
                        }
                    }
                }
            }
        } else if (loading) {
            item {
                SiteDataMessageCard(
                    text = stringResource(R.string.site_data_loading),
                    showProgress = true,
                )
            }
        } else {
            item {
                if (failed) {
                    SiteDataMessageCard(
                        text = stringResource(R.string.site_data_load_failed),
                        actionText = stringResource(R.string.retry),
                        onAction = {
                            if (!loading) {
                                scope.launch {
                                    loading = true
                                    val result = SiteDataClient.loadDonors(context, force = true)
                                    board = result.data
                                    stale = result.stale
                                    failed = result.data == null && result.error != null
                                    loading = false
                                }
                            }
                        },
                    )
                } else {
                    SiteDataMessageCard(text = stringResource(R.string.site_data_empty))
                }
            }
        }

        val updatedAt = board?.generatedAt?.let(::formatGeneratedAt).orEmpty()
        if (updatedAt.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.site_data_updated_at, updatedAt),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun DonationThanksCard(board: DonorBoard?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.donation_list_thanks),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            val total = board?.total.orEmpty()
            if (board != null && total.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.donation_list_summary, board.count, total),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun DonorRow(donor: Donor) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // 记录里有 `" "`（空格）和 `"?"` 这类占位昵称，统一按「匿名」展示
            val trimmedName = donor.name.trim()
            Text(
                text = if (trimmedName.isEmpty()) {
                    stringResource(R.string.donation_anonymous)
                } else {
                    trimmedName
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onSurface,
            )
            // 留言留两行就够：有人写一整段感谢，全展开会把一屏塞满三条记录
            if (donor.message.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = donor.message.trim(),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.donation_amount, donor.amount),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            if (donor.date.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = donor.date,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun DonorDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp)
            .height(1.dp)
            .background(MiuixTheme.colorScheme.dividerLine),
    )
}
