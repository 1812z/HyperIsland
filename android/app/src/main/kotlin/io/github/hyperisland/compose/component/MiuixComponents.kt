package io.github.hyperisland.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.theme.LocalAppWallpaper
import io.github.hyperisland.compose.theme.LocalUiGlassSettings
import io.github.hyperisland.compose.theme.uiGlassMaterial
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRowDefaults
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.GlassMaterials
import top.yukonga.miuix.kmp.material.GlassMaterial
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.GlassShadows
import top.yukonga.miuix.kmp.material.EdgeStroke
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
internal fun PageTopAppBar(
    title: String,
    largeTitle: String = title,
    subtitle: String = "",
    scrollBehavior: ScrollBehavior? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    val backdrop = uiGlassBackdrop()
    val scrolled = remember(scrollBehavior) {
        derivedStateOf { (scrollBehavior?.state?.contentOffset ?: 0f) < 0f }
    }
    val band = GlassTopAppBarDefaults.bandBrush(MiuixTheme.colorScheme.surface)
    GlassTopAppBar(
        title = title,
        largeTitle = largeTitle,
        subtitle = subtitle,
        isContentScrolled = scrolled.value,
        modifier = if (backdrop == null) Modifier.background(band) else Modifier,
        progressiveBlur = backdrop != null,
        backdrop = backdrop,
        bandBrush = SolidColor(Color.Transparent),
        bandOverhang = 0.dp,
        scrollBehavior = scrollBehavior,
        navigationIcon = navigationIcon,
        actions = actions,
        bottomContent = bottomContent,
    )
}

@Composable
internal fun PageTopBarIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (enabled) {
        GlassIconButton(onClick = onClick, modifier = modifier, content = content)
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier.size(GlassTopAppBarDefaults.ButtonSize),
            enabled = false,
        ) {
            content()
        }
    }
}

/** Place in a top-bar slot outside the recorded page content. */
@Composable
internal fun PageTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalUiGlassSettings.current
    val backdrop = uiGlassBackdrop()
    val tint = MiuixTheme.colorScheme.surface
    val fallbackBackdrop = remember(tint) { TintBackdrop(tint) }
    // The native tab track inherits a fixed action-bar underlay. Draw our material separately.
    val trackModifier = modifier.pageControlSurface(
        backdrop = backdrop,
        shape = GlassSegmentedTabRowDefaults.shape(),
        material = uiGlassMaterial(GlassSegmentedTabRowDefaults.material(), glass),
        stroke = GlassSegmentedTabRowDefaults.stroke(),
    )
    GlassSegmentedTabRow(
        tabs = tabs,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        backdrop = fallbackBackdrop,
        surfaceAlpha = 0f,
        shadow = null,
        trackColor = if (backdrop != null) Color.Transparent else GlassSegmentedTabRowDefaults.restingTrackColor(),
        modifier = trackModifier,
    )
}

/**
 * 纯色 [Backdrop]：只提供一层稳定底色，不录制页面内容，因此不会被采样环牵连。
 *
 * 声明 `isCoordinatesDependent = false`：它的输出与绘制位置无关，不需要任何布局坐标。
 */
private class TintBackdrop(private val color: Color) : Backdrop {

    override val isCoordinatesDependent: Boolean = false

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Float,
    ) {
        drawRect(color)
    }
}

/**
 * 顶栏内胶囊控件的玻璃底（搜索框等）。
 *
 * 与顶栏分段条同款材质：`puredThinGlass` + 主题描边，`shading = false`（主体由材质层提供）。
 * 调用方需把控件自身背景设为 [Color.Transparent]，否则会盖住这层玻璃。
 *
 * 只能在 [BarBackdropContent] 之外使用（顶栏或固定头部），在录制子树内会形成采样环。
 */
@Composable
internal fun Modifier.glassPill(
    backdrop: Backdrop?,
    cornerRadius: Dp = 999.dp,
): Modifier {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val shape = remember(cornerRadius) { GlassShape(cornerRadius, smoothing = 0f) }
    return pageControlSurface(
        backdrop = backdrop,
        shape = shape,
        material = uiGlassMaterial(GlassMaterials.puredThinGlass(isDark)),
        stroke = GlassEdgeStrokes.forTheme(isDark),
        fallback = Modifier.clip(shape).background(MiuixTheme.colorScheme.surfaceContainerHigh),
    )
}

@Composable
private fun Modifier.pageControlSurface(
    backdrop: Backdrop?,
    shape: GlassShape,
    material: GlassMaterial,
    stroke: EdgeStroke,
    fallback: Modifier = Modifier,
): Modifier = glassPanel(
    backdrop = backdrop,
    shape = shape,
    material = material,
    stroke = stroke.takeIf { backdrop != null },
    shadow = GlassShadows.Float.takeIf { LocalAppWallpaper.current?.image == null },
    shading = false,
    fallback = fallback,
)

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
    BarBlurHost {
        Scaffold(
            containerColor = if (LocalSharedPageWallpaper.current != null) {
                Color.Transparent
            } else {
                MiuixTheme.colorScheme.surface
            },
            topBar = {
                PageTopAppBar(
                    title = title,
                    largeTitle = title,
                    subtitle = subtitle,
                    scrollBehavior = scrollBehavior,
                    actions = {
                        if (actionIcon != null && onAction != null) {
                            PageTopBarIconButton(onClick = onAction) {
                                Icon(actionIcon, actionDescription)
                            }
                        }
                    },
                )
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
    floatingActionButton: @Composable () -> Unit = {},
    /**
     * 列表底部留白。[PageList] 不使用 Scaffold 的底部内边距，所以右下角有悬浮按钮时
     * 必须由调用方把这个值调大（FAB 高 60dp + 12dp 间距），否则最后一行会被按钮压住。
     */
    bottomContentPadding: Dp = 28.dp,
    /**
     * 顶栏下方固定内容（如分段切换条）。
     *
     * 必须挂在顶栏内而不是列表首项：页面内容整体位于 [BarBackdropContent] 的录制子树中，
     * 玻璃控件若跟着列表一起被录制，就会采样到自己（采样环）。顶栏在录制子树之外。
     */
    topBarContent: @Composable (ScrollBehavior) -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    BarBlurHost {
        Scaffold(
            topBar = {
                PageTopAppBar(
                    title = title,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        PageTopBarIconButton(onClick = onBack) {
                            Icon(MiuixIcons.Back, stringResource(R.string.back))
                        }
                    },
                    actions = {
                        if (actionIcon != null && onAction != null) {
                            PageTopBarIconButton(onClick = onAction) {
                                Icon(actionIcon, actionDescription)
                            }
                        }
                    },
                    bottomContent = { topBarContent(scrollBehavior) },
                )
            },
            floatingActionButton = floatingActionButton,
            snackbarHost = snackbarHost,
        ) { padding ->
            BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                PageList(
                    padding = padding,
                    scrollBehavior = scrollBehavior,
                    bottomContentPadding = bottomContentPadding,
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
    topBarContent: @Composable (ScrollBehavior) -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: LazyGridScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    BarBlurHost {
        Scaffold(
            topBar = {
                PageTopAppBar(
                    title = title,
                    largeTitle = title,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        PageTopBarIconButton(onClick = onBack) {
                            Icon(MiuixIcons.Back, stringResource(R.string.back))
                        }
                    },
                    actions = actions,
                    bottomContent = { topBarContent(scrollBehavior) },
                )
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
                        refreshTexts = listOf("", "", "", ""),
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
        Box(modifier = Modifier.fillMaxSize(), content = overlay)
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
    icon: ImageVector? = null,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        startAction = icon?.let { image -> { SettingsIcon(image) } },
        endActions = {
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                modifier = Modifier.size(width = 10.dp, height = 16.dp),
                tint = if (enabled) {
                    MiuixTheme.colorScheme.onSurfaceVariantActions
                } else {
                    MiuixTheme.colorScheme.disabledOnSurface
                },
            )
        },
        insideMargin = SettingsItemMargin,
        enabled = enabled,
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
