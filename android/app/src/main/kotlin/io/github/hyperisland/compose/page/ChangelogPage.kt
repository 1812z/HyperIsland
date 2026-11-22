package io.github.hyperisland.compose.page

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hyperisland.BuildConfig
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.AppCard
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.MarkdownLines
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.data.ChangelogFeed
import io.github.hyperisland.compose.data.ReleaseEntry
import io.github.hyperisland.compose.data.formatGeneratedAt
import io.github.hyperisland.compose.service.SiteDataClient
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 更新日志 & 历史版本。
 *
 * 数据来自站点构建期产物 `data/changelog.json`（同源于 `docs/CHANGELOG.md`），
 * 不是网页套壳：版本、日期、分段、条目、下载地址全部按字段渲染。
 *
 * 交互按「一屏看得到全局，想看细节再展开」设计：
 * 每个版本收起时只占一行摘要，点击展开才渲染完整的 Markdown 明细。
 * 109 个版本全展开会让页面长到没法用，但默认全收起又看不到内容，
 * 所以默认展开**最新一版**，其余收起。
 */
@Composable
internal fun ChangelogPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var feed by remember { mutableStateOf<ChangelogFeed?>(null) }
    var loading by remember { mutableStateOf(true) }
    var stale by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(Unit) {
        val result = SiteDataClient.loadChangelog(context)
        feed = result.data
        stale = result.stale
        failed = result.data == null && result.error != null
        loading = false
        // 默认展开最新一版：用户点进来最想看的通常是「这次更新了什么」
        expanded = result.data?.releases?.firstOrNull()?.let { setOf(it.version) } ?: emptySet()
    }

    DetailPage(
        title = stringResource(R.string.changelog_history),
        onBack = onBack,
        actionIcon = MiuixIcons.Refresh,
        actionDescription = stringResource(R.string.refresh_list),
        onAction = {
            if (!refreshing) {
                scope.launch {
                    refreshing = true
                    val result = SiteDataClient.loadChangelog(context, force = true)
                    // 刷新失败时保留原内容：把已经看到的列表清空比显示旧数据更糟
                    if (result.data != null) {
                        feed = result.data
                        stale = result.stale
                    }
                    refreshing = false
                }
            }
        },
    ) {
        item {
            ChangelogHeaderCard(
                downloadUrl = feed?.newest?.downloadUrl ?: FALLBACK_DOWNLOAD_URL,
            )
        }

        if (stale && feed != null) {
            item { SiteDataMessageCard(stringResource(R.string.site_data_stale)) }
        }

        item { SectionTitle(stringResource(R.string.changelog_section)) }

        val releases = feed?.releases.orEmpty()
        if (releases.isNotEmpty()) {
            items(releases, key = { it.version }) { release ->
                ReleaseCard(
                    release = release,
                    expanded = release.version in expanded,
                    onToggle = {
                        expanded = if (release.version in expanded) {
                            expanded - release.version
                        } else {
                            expanded + release.version
                        }
                    },
                    onDownload = { context.openInBrowser(release.downloadUrl) },
                )
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
                // 有网络错误才给「重试」；纯空数据（比如 CHANGELOG 被清空）不该弹失败文案
                if (failed) {
                    SiteDataMessageCard(
                        text = stringResource(R.string.site_data_load_failed),
                        actionText = stringResource(R.string.retry),
                        onAction = {
                            if (!loading) {
                                scope.launch {
                                    loading = true
                                    val result = SiteDataClient.loadChangelog(context, force = true)
                                    feed = result.data
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

        val updatedAt = feed?.generatedAt?.let(::formatGeneratedAt).orEmpty()
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

/**
 * 页首卡片：应用图标 + 名称 + 当前安装版本 + 下载入口。
 *
 * 下载目标是**站点公布的最新版**，不是本地版本 —— 本地版本只作为「你在哪一版」的参照，
 * 拿来当下载目标会出现「点了下载还是同一版」的死循环。
 */
@Composable
private fun ChangelogHeaderCard(downloadUrl: String) {
    val context = LocalContext.current
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "HyperIsland",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.app_version_with_code,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = { context.openInBrowser(downloadUrl) },
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(
                    text = stringResource(R.string.changelog_download),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun ReleaseCard(
    release: ReleaseEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDownload: () -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp)
                // 展开 / 收起时让卡片高度顺着动画长出来，而不是「啪」一下跳变。
                // 展开后的 Markdown 比收起态高得多，没有这一步观感很生硬。
                .animateContentSize(animationSpec = tween(durationMillis = 220)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "v${release.version}",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    if (release.date.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = release.date,
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                DownloadButton(onClick = onDownload)
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                MarkdownLines(release.markdown)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    AccentTextAction(
                        text = stringResource(R.string.changelog_collapse),
                        onClick = onToggle,
                    )
                }
            } else {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = release.summary,
                        modifier = Modifier.weight(1f),
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.width(10.dp))
                    AccentTextAction(
                        text = stringResource(R.string.changelog_expand),
                        onClick = onToggle,
                    )
                }
            }
        }
    }
}

/**
 * 版本卡里的下载按钮：强调色实心 + 白字。
 *
 * 尺寸比对话框里那套默认按钮小一圈 —— 默认 `MinWidth 58dp / MinHeight 40dp`
 * 再加 16dp 侧边距，会把版本卡头部撑得比正文还高，109 张卡叠起来尤其难看。
 */
@Composable
private fun DownloadButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        minWidth = 0.dp,
        minHeight = 34.dp,
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
        colors = ButtonDefaults.buttonColorsPrimary(),
    ) {
        Text(
            text = stringResource(R.string.changelog_download),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onPrimary,
        )
    }
}

/** 数据还没到手时点下载的兜底目标：资源下载页，而不是空链接。 */
private const val FALLBACK_DOWNLOAD_URL = "https://hyperisland.1812z.top/downloads.html"
