package io.github.hyperisland.compose.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import io.github.hyperisland.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
internal fun CollapsingPage(
    title: String,
    subtitle: String = "",
    actionIcon: ImageVector? = null,
    actionDescription: String = "",
    onAction: (() -> Unit)? = null,
    snackbarHost: @Composable () -> Unit = {},
    horizontalContentPadding: Dp = 16.dp,
    topContentPadding: Dp = 8.dp,
    bottomContentPadding: Dp = 28.dp,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurEnabled = LocalBarBlurEnabled.current
    BarBlurHost(enabled = blurEnabled) {
        Scaffold(
            topBar = {
                BlurredBar(topGradient = true) {
                    TopAppBar(
                        title = title,
                        largeTitle = title,
                        subtitle = subtitle,
                        color = Color.Transparent,
                        scrollBehavior = scrollBehavior,
                        actions = {
                            if (actionIcon != null && onAction != null) {
                                IconButton(onClick = onAction) {
                                    Icon(actionIcon, actionDescription)
                                }
                            }
                        },
                    )
                }
            },
            snackbarHost = snackbarHost,
        ) { padding ->
            BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                PageList(
                    padding = padding,
                    scrollBehavior = scrollBehavior,
                    horizontalContentPadding = horizontalContentPadding,
                    topContentPadding = topContentPadding,
                    bottomContentPadding = bottomContentPadding,
                    content = content,
                )
            }
        }
    }
}

@Composable
internal fun DetailPage(
    title: String,
    onBack: () -> Unit,
    actionIcon: ImageVector? = null,
    actionDescription: String = "",
    onAction: (() -> Unit)? = null,
    snackbarHost: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurEnabled = LocalBarBlurEnabled.current
    BarBlurHost(enabled = blurEnabled) {
        Scaffold(
            topBar = {
                BlurredBar(topGradient = true) {
                    TopAppBar(
                        title = title,
                        largeTitle = title,
                        color = Color.Transparent,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(MiuixIcons.Back, stringResource(R.string.back))
                            }
                        },
                        actions = {
                            if (actionIcon != null && onAction != null) {
                                IconButton(onClick = onAction) {
                                    Icon(actionIcon, actionDescription)
                                }
                            }
                        },
                    )
                }
            },
            snackbarHost = snackbarHost,
        ) { padding ->
            BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                PageList(
                    padding = padding,
                    scrollBehavior = scrollBehavior,
                    content = content,
                )
            }
        }
    }
}

/**
 * 与 [DetailPage] 同构的详情页，正文使用响应式网格。
 *
 * [columns] 使用 [GridCells.Adaptive] 时列数随可用宽度动态变化：手机等窄屏为双列，
 * 平板 / 折叠屏 / 桌面等宽屏自动增加列数，卡片同步拉伸铺满。
 * 默认 128dp 可保证 320dp 起的窄屏稳定双列，超过约 440dp 后开始增加列数。
 */
@Composable
internal fun DetailGridPage(
    title: String,
    onBack: () -> Unit,
    columns: GridCells = GridCells.Adaptive(minSize = 128.dp),
    horizontalContentPadding: Dp = 16.dp,
    topContentPadding: Dp = 8.dp,
    bottomContentPadding: Dp = 28.dp,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: LazyGridScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurEnabled = LocalBarBlurEnabled.current
    BarBlurHost(enabled = blurEnabled) {
        Scaffold(
            topBar = {
                BlurredBar(topGradient = true) {
                    TopAppBar(
                        title = title,
                        largeTitle = title,
                        color = Color.Transparent,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(MiuixIcons.Back, stringResource(R.string.back))
                            }
                        },
                        actions = actions,
                    )
                }
            },
            snackbarHost = snackbarHost,
        ) { padding ->
            BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                if (onRefresh != null) {
                    PullToRefresh(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                        topAppBarScrollBehavior = scrollBehavior,
                        contentPadding = PaddingValues(top = padding.calculateTopPadding()),
                    ) {
                        PageGrid(
                            padding = padding,
                            scrollBehavior = scrollBehavior,
                            columns = columns,
                            horizontalContentPadding = horizontalContentPadding,
                            topContentPadding = topContentPadding,
                            bottomContentPadding = bottomContentPadding,
                            content = content,
                        )
                    }
                } else {
                    PageGrid(
                        padding = padding,
                        scrollBehavior = scrollBehavior,
                        columns = columns,
                        horizontalContentPadding = horizontalContentPadding,
                        topContentPadding = topContentPadding,
                        bottomContentPadding = bottomContentPadding,
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
private fun PageGrid(
    padding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    columns: GridCells,
    horizontalContentPadding: Dp,
    topContentPadding: Dp,
    bottomContentPadding: Dp,
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = columns,
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            start = horizontalContentPadding,
            top = padding.calculateTopPadding() + topContentPadding,
            end = horizontalContentPadding,
            bottom = bottomContentPadding + LocalRootBottomBarPadding.current,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun PageList(
    padding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    horizontalContentPadding: Dp = 16.dp,
    topContentPadding: Dp = 8.dp,
    bottomContentPadding: Dp = 28.dp,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            start = horizontalContentPadding,
            top = padding.calculateTopPadding() + topContentPadding,
            end = horizontalContentPadding,
            bottom = bottomContentPadding + LocalRootBottomBarPadding.current,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun SectionTitle(title: String) {
    SmallTitle(
        text = title,
        modifier = Modifier.padding(top = 4.dp),
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
internal fun SettingsAction(
    title: String,
    icon: ImageVector? = null,
    summary: String? = null,
    endIcon: ImageVector? = null,
    endIconSize: Dp = 20.dp,
    endContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        startAction = icon?.let { image -> { SettingsIcon(image) } },
        endActions = {
            if (endContent != null) {
                endContent()
            } else if (endIcon != null) {
                Icon(
                    imageVector = endIcon,
                    contentDescription = null,
                    modifier = Modifier.size(endIconSize),
                    tint = if (enabled) {
                        MiuixTheme.colorScheme.onSurfaceVariantActions
                    } else {
                        MiuixTheme.colorScheme.disabledOnSurface
                    },
                )
            }
        },
        insideMargin = SettingsItemMargin,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
internal fun SettingsActionWithArrow(
    title: String,
    icon: ImageVector,
    summary: String? = null,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        startAction = { SettingsIcon(icon) },
        endActions = {
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                modifier = Modifier.size(width = 10.dp, height = 16.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        insideMargin = SettingsItemMargin,
        onClick = onClick,
    )
}

@Composable
internal fun SettingsIcon(imageVector: ImageVector) {
    Icon(
        modifier = Modifier.padding(end = 16.dp),
        imageVector = imageVector,
        contentDescription = null,
        tint = MiuixTheme.colorScheme.onBackground,
    )
}

internal val SettingsItemMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
