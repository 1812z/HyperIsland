package io.github.hyperisland.ui

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.platform.LocalContext
import io.github.hyperisland.data.prefs.PrefKeys
import io.github.hyperisland.data.prefs.SettingsState
import io.github.hyperisland.ui.ai.AiConfigScreen
import io.github.hyperisland.ui.ai.AiConfigViewModel
import io.github.hyperisland.ui.app.AppChannelsScreen
import io.github.hyperisland.ui.app.AppChannelsViewModel
import io.github.hyperisland.ui.app.AppsScreen
import io.github.hyperisland.ui.app.AppsViewModel
import io.github.hyperisland.ui.blacklist.BlacklistScreen
import io.github.hyperisland.ui.blacklist.BlacklistViewModel
import io.github.hyperisland.ui.home.HomeUiState
import io.github.hyperisland.ui.home.HomeViewModel
import io.github.hyperisland.ui.settings.SettingsViewModel
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.AppRecording
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn as MiuixListPopupColumn
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.basic.PopupPositionProvider as MiuixPopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.SnackbarHost as MiuixSnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState as MiuixSnackbarHostState
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as MiuixSmallTopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.overlay.OverlayDialog as MiuixOverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet as MiuixOverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayListPopup as MiuixOverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

class ComposeMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            MiuixTheme {
                MaterialTheme {
                    HyperIslandComposeApp()
                }
            }
        }
    }
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val HomeFilledIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "HomeFilledCustom",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd,
        ) {
            moveTo(2.5192f, 7.82274f)
            curveTo(2f, 8.77128f, 2f, 9.91549f, 2f, 12.2039f)
            verticalLineTo(13.725f)
            curveTo(2f, 17.6258f, 2f, 19.5763f, 3.17157f, 20.7881f)
            curveTo(4.34315f, 22f, 6.22876f, 22f, 10f, 22f)
            horizontalLineTo(14f)
            curveTo(17.7712f, 22f, 19.6569f, 22f, 20.8284f, 20.7881f)
            curveTo(22f, 19.5763f, 22f, 17.6258f, 22f, 13.725f)
            verticalLineTo(12.2039f)
            curveTo(22f, 9.91549f, 22f, 8.77128f, 21.4808f, 7.82274f)
            curveTo(20.9616f, 6.87421f, 20.0131f, 6.28551f, 18.116f, 5.10812f)
            lineTo(16.116f, 3.86687f)
            curveTo(14.1106f, 2.62229f, 13.1079f, 2f, 12f, 2f)
            curveTo(10.8921f, 2f, 9.88939f, 2.62229f, 7.88403f, 3.86687f)
            lineTo(5.88403f, 5.10813f)
            curveTo(3.98695f, 6.28551f, 3.0384f, 6.87421f, 2.5192f, 7.82274f)
            close()
            moveTo(9f, 17.25f)
            curveTo(8.58579f, 17.25f, 8.25f, 17.5858f, 8.25f, 18f)
            curveTo(8.25f, 18.4142f, 8.58579f, 18.75f, 9f, 18.75f)
            horizontalLineTo(15f)
            curveTo(15.4142f, 18.75f, 15.75f, 18.4142f, 15.75f, 18f)
            curveTo(15.75f, 17.5858f, 15.4142f, 17.25f, 15f, 17.25f)
            horizontalLineTo(9f)
            close()
        }
    }.build()
}

private fun mainRouteIndex(route: String?): Int = when (route) {
    "home" -> 0
    "apps" -> 1
    "settings" -> 2
    else -> -1
}

private const val DOCUMENTATION_URL = "https://hyperisland.1812z.top/"
private const val GITHUB_REPO_URL = "https://github.com/1812z/HyperIsland"
private const val GITHUB_RELEASE_URL = "https://github.com/1812z/HyperIsland/releases/latest"
private const val QQ_GROUP_NUMBER = "1045114341"

private fun routeTitle(route: String?): String {
    return when {
        route == "home" -> "主页"
        route == "apps" -> "应用"
        route == "settings" -> "设置"
        route?.startsWith("app_channels/") == true -> "渠道设置"
        route == "blacklist" -> "通知黑名单"
        route == "ai_config" -> "AI 配置"
        else -> "HyperIsland"
    }
}

private fun routeLargeTitle(route: String?): String {
    return when {
        route == "home" -> "主页"
        route == "apps" -> "应用适配"
        route == "settings" -> "系统设置"
        route?.startsWith("app_channels/") == true -> "通知渠道"
        route == "blacklist" -> "通知黑名单"
        route == "ai_config" -> "AI 配置"
        else -> "HyperIsland"
    }
}

private fun openExternalUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

@Composable
private fun HyperIslandComposeApp() {
    val navController = rememberNavController()
    val snackbarHostState = remember { MiuixSnackbarHostState() }
    val context = LocalContext.current
    val homeVm: HomeViewModel = viewModel()
    val appsVm: AppsViewModel = viewModel()
    val appsState by appsVm.uiState.collectAsStateWithLifecycle()
    var showRestartDialog by remember { mutableStateOf(false) }
    var showSponsorDialog by remember { mutableStateOf(false) }
    var showAppsMenu by remember { mutableStateOf(false) }
    var appsBatchRequestId by remember { mutableStateOf(0) }

    val items = listOf(
        TopLevelDestination("home", "主页", HomeFilledIcon),
        TopLevelDestination("apps", "应用", MiuixIcons.Regular.All),
        TopLevelDestination("settings", "设置", MiuixIcons.Regular.Settings),
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isSecondaryRoute = currentRoute?.startsWith("app_channels/") == true ||
        currentRoute == "blacklist" ||
        currentRoute == "ai_config"
    val scrollBehavior = MiuixScrollBehavior(
        state = rememberTopAppBarState(),
        canScroll = { true },
    )

    MiuixScaffold(
        snackbarHost = { MiuixSnackbarHost(snackbarHostState) },
        topBar = {
            if (isSecondaryRoute) {
                MiuixSmallTopAppBar(
                    title = routeTitle(currentRoute),
                    navigationIcon = {
                        MiuixIconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = MiuixIcons.Basic.ArrowRight,
                                contentDescription = "返回",
                                modifier = Modifier.rotate(180f),
                            )
                        }
                    },
                    modifier = Modifier,
                    scrollBehavior = null,
                    defaultWindowInsetsPadding = false,
                )
            } else {
                MiuixTopAppBar(
                    title = routeTitle(currentRoute),
                    largeTitle = routeLargeTitle(currentRoute),
                    actions = {
                        if (currentRoute == "home") {
                            MiuixIconButton(onClick = { openExternalUrl(context, DOCUMENTATION_URL) }) {
                                Icon(
                                    imageVector = Icons.Filled.InsertDriveFile,
                                    contentDescription = "文档",
                                )
                            }
                            MiuixIconButton(onClick = { showSponsorDialog = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = "赞助",
                                )
                            }
                            MiuixIconButton(onClick = { showRestartDialog = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "重启作用域",
                                )
                            }
                        } else if (currentRoute == "apps") {
                            Box {
                                MiuixIconButton(onClick = { showAppsMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "更多操作",
                                    )
                                }
                                MiuixOverlayListPopup(
                                    show = showAppsMenu,
                                    alignment = MiuixPopupPositionProvider.Align.Start,
                                    onDismissRequest = { showAppsMenu = false },
                                    onDismissFinished = {},
                                ) {
                                    MiuixListPopupColumn {
                                        OverlayListPopupMenuItem(if (appsState.showSystemApps) "隐藏系统应用" else "显示系统应用") {
                                            showAppsMenu = false
                                            appsVm.setShowSystemApps(!appsState.showSystemApps)
                                        }
                                        OverlayListPopupMenuItem("全局批量应用") {
                                            showAppsMenu = false
                                            appsBatchRequestId += 1
                                        }
                                        OverlayListPopupMenuItem("刷新") {
                                            showAppsMenu = false
                                            appsVm.refresh()
                                        }
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier,
                    scrollBehavior = scrollBehavior,
                    defaultWindowInsetsPadding = false,
                )
            }
        },
        bottomBar = {
            if (!isSecondaryRoute) {
                val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
                MiuixNavigationBar(
                    modifier = Modifier.height(62.dp),
                    showDivider = false,
                    defaultWindowInsetsPadding = false,
                ) {
                    items.forEachIndexed { index, destination ->
                        MiuixNavigationBarItem(
                            selected = index == selectedIndex,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = destination.icon,
                            label = destination.label,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            enterTransition = {
                val from = mainRouteIndex(initialState.destination.route)
                val to = mainRouteIndex(targetState.destination.route)
                if (from >= 0 && to >= 0 && from != to) {
                    if (to > from) {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(260),
                        )
                    } else {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(260),
                        )
                    }
                } else {
                    EnterTransition.None
                }
            },
            exitTransition = {
                val from = mainRouteIndex(initialState.destination.route)
                val to = mainRouteIndex(targetState.destination.route)
                if (from >= 0 && to >= 0 && from != to) {
                    if (to > from) {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(260),
                        )
                    } else {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(260),
                        )
                    }
                } else {
                    ExitTransition.None
                }
            },
            popEnterTransition = {
                val from = mainRouteIndex(initialState.destination.route)
                val to = mainRouteIndex(targetState.destination.route)
                if (from >= 0 && to >= 0 && from != to) {
                    if (to > from) {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(260),
                        )
                    } else {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(260),
                        )
                    }
                } else {
                    EnterTransition.None
                }
            },
            popExitTransition = {
                val from = mainRouteIndex(initialState.destination.route)
                val to = mainRouteIndex(targetState.destination.route)
                if (from >= 0 && to >= 0 && from != to) {
                    if (to > from) {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Left,
                            animationSpec = tween(260),
                        )
                    } else {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(260),
                        )
                    }
                } else {
                    ExitTransition.None
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable("home") {
                val uiState by homeVm.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    homeVm.events.collect { snackbarHostState.showSnackbar(it) }
                }
                HomeScreen(
                    uiState = uiState,
                    onRefresh = homeVm::refreshStatus,
                    onSendTest = homeVm::sendTest,
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                )
            }
            composable("apps") {
                LaunchedEffect(Unit) {
                    appsVm.events.collect { snackbarHostState.showSnackbar(it) }
                }
                AppsScreen(
                    state = appsState,
                    onRefresh = appsVm::refresh,
                    onQueryChange = appsVm::setQuery,
                    onAppEnabledChange = appsVm::setEnabled,
                    onOpenAppChannels = { pkg -> navController.navigate("app_channels/$pkg") },
                    onBatchApplyGlobal = appsVm::batchApplyToAllEnabledApps,
                    batchRequestId = appsBatchRequestId,
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                )
            }
            composable("settings") {
                val vm: SettingsViewModel = viewModel()
                val uiState by vm.uiState.collectAsStateWithLifecycle()
                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        vm.importConfigFromUri(uri)
                    }
                }
                LaunchedEffect(Unit) {
                    vm.events.collect { snackbarHostState.showSnackbar(it) }
                }
                SettingsScreen(
                    state = uiState,
                    onToggle = vm::updateSwitch,
                    onMarqueeSpeed = vm::updateMarqueeSpeed,
                    onBigIslandWidth = vm::updateBigIslandMaxWidth,
                    onThemeModeChange = vm::updateThemeMode,
                    onLocaleChange = vm::updateLocale,
                    onHideDesktopIcon = vm::setDesktopIconHidden,
                    onOpenBlacklist = { navController.navigate("blacklist") },
                    onOpenAiConfig = { navController.navigate("ai_config") },
                    onCheckUpdate = { openExternalUrl(context, GITHUB_RELEASE_URL) },
                    onOpenGithub = { openExternalUrl(context, GITHUB_REPO_URL) },
                    onExportToFile = vm::exportConfigToFile,
                    onPickImportFile = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    onExportToClipboard = vm::exportConfigToClipboard,
                    onImportFromClipboard = vm::importConfigFromClipboard,
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                )
            }
            composable(
                route = "app_channels/{packageName}",
                arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
            ) { backStack ->
                val vm: AppChannelsViewModel = viewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                val packageNameArg = backStack.arguments?.getString("packageName").orEmpty()
                LaunchedEffect(packageNameArg) {
                    vm.setPackageNameIfEmpty(packageNameArg)
                }
                AppChannelsScreen(
                    state = state,
                    onRefresh = vm::refresh,
                    onToggleChannel = vm::toggleChannel,
                    onEnableAllChannels = vm::enableAllChannels,
                    onCycleTemplate = vm::cycleTemplate,
                    onSetTimeout = vm::setTimeout,
                    onCycleSetting = vm::cycleSetting,
                    onSetHighlightColor = vm::setHighlightColor,
                    onBatchApplyToEnabledChannels = vm::batchApplyToEnabledChannels,
                )
            }
            composable("blacklist") {
                val vm: BlacklistViewModel = viewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    vm.events.collect { snackbarHostState.showSnackbar(it) }
                }
                BlacklistScreen(
                    state = state,
                    onRefresh = vm::refresh,
                    onQueryChange = vm::setQuery,
                    onShowSystemChange = vm::setShowSystemApps,
                    onSetBlacklisted = vm::setBlacklisted,
                    onEnableAllVisible = vm::enableAllVisible,
                    onDisableAllVisible = vm::disableAllVisible,
                    onApplyGamePreset = vm::applyGamePreset,
                )
            }
            composable("ai_config") {
                val vm: AiConfigViewModel = viewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    vm.events.collect { snackbarHostState.showSnackbar(it) }
                }
                AiConfigScreen(
                    state = state,
                    onUpdate = vm::setState,
                    onSave = vm::save,
                    onTest = vm::testConnection,
                )
            }
        }
        if (showSponsorDialog) {
            SponsorDialog(
                show = true,
                onDismiss = { showSponsorDialog = false },
            )
        }
        if (showRestartDialog) {
            RestartScopeDialog(
                show = true,
                onDismiss = { showRestartDialog = false },
                onConfirm = { systemUi, downloads, xmsf ->
                    showRestartDialog = false
                    homeVm.restartScopes(systemUi, downloads, xmsf)
                },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onSendTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MiuixCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("模块状态", style = MaterialTheme.typography.titleMedium)
                    val statusText = when (uiState.moduleActive) {
                        null -> "检测中..."
                        true -> "已激活"
                        false -> "未激活"
                    }
                    Text("LSPosed API: ${uiState.lsposedApiVersion}")
                    Text("模块状态: $statusText")
                    Text("Focus 协议版本: ${uiState.focusProtocolVersion}")
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MiuixButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                    Text("刷新状态")
                }
                MiuixButton(onClick = onSendTest, modifier = Modifier.weight(1f)) {
                    Text("发送测试通知")
                }
            }
        }
    }
}

@Composable
private fun SponsorDialog(show: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val qrBitmap = remember {
        runCatching {
            context.assets.open("flutter_assets/assets/images/wechat.jpg").use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }
    MiuixOverlayDialog(
        show = show,
        title = "赞助支持",
        summary = "",
        onDismissRequest = onDismiss,
        onDismissFinished = {},
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("赞助作者")
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "微信赞助二维码",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("未找到赞助图片 assets/images/wechat.jpg")
            }
            MiuixButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("关闭")
            }
        }
    }
}

@Composable
private fun RestartScopeDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, Boolean, Boolean) -> Unit,
) {
    var restartSystemUi by remember { mutableStateOf(true) }
    var restartDownloads by remember { mutableStateOf(true) }
    var restartXmsf by remember { mutableStateOf(true) }

    MiuixOverlayBottomSheet(
        show = show,
        title = "选择需要重启的进程",
        onDismissRequest = onDismiss,
        onDismissFinished = {},
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ScopeCheckboxRow("SystemUI（com.android.systemui）", restartSystemUi) { restartSystemUi = !restartSystemUi }
            ScopeCheckboxRow("下载管理（com.android.providers.downloads）", restartDownloads) { restartDownloads = !restartDownloads }
            ScopeCheckboxRow("XMSF（com.xiaomi.xmsf）", restartXmsf) { restartXmsf = !restartXmsf }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiuixButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                MiuixButton(
                    onClick = { onConfirm(restartSystemUi, restartDownloads, restartXmsf) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("重启")
                }
            }
        }
    }
}

@Composable
private fun ScopeCheckboxRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        MiuixCheckbox(
            state = if (checked) ToggleableState.On else ToggleableState.Off,
            onClick = onClick,
        )
    }
}

@Composable
private fun SettingsScreen(
    state: SettingsState,
    onToggle: (String, Boolean) -> Unit,
    onMarqueeSpeed: (Int) -> Unit,
    onBigIslandWidth: (Int) -> Unit,
    onThemeModeChange: (String) -> Unit,
    onLocaleChange: (String?) -> Unit,
    onHideDesktopIcon: (Boolean) -> Unit,
    onOpenBlacklist: () -> Unit,
    onOpenAiConfig: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenGithub: () -> Unit,
    onExportToFile: () -> Unit,
    onPickImportFile: () -> Unit,
    onExportToClipboard: () -> Unit,
    onImportFromClipboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    if (showThemeDialog) {
        MiuixOverlayDialog(
            show = true,
            title = "颜色模式",
            summary = "",
            onDismissRequest = { showThemeDialog = false },
            onDismissFinished = {},
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeOption("跟随系统", state.themeMode == "system") {
                    onThemeModeChange("system")
                    showThemeDialog = false
                }
                ThemeOption("浅色", state.themeMode == "light") {
                    onThemeModeChange("light")
                    showThemeDialog = false
                }
                ThemeOption("深色", state.themeMode == "dark") {
                    onThemeModeChange("dark")
                    showThemeDialog = false
                }
            }
        }
    }

    if (showLanguageDialog) {
        MiuixOverlayDialog(
            show = true,
            title = "语言",
            summary = "",
            onDismissRequest = { showLanguageDialog = false },
            onDismissFinished = {},
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeOption("跟随系统", state.locale == null) {
                    onLocaleChange(null)
                    showLanguageDialog = false
                }
                ThemeOption("中文", state.locale == "zh") {
                    onLocaleChange("zh")
                    showLanguageDialog = false
                }
                ThemeOption("English", state.locale == "en") {
                    onLocaleChange("en")
                    showLanguageDialog = false
                }
                ThemeOption("日本語", state.locale == "ja") {
                    onLocaleChange("ja")
                    showLanguageDialog = false
                }
                ThemeOption("Türkçe", state.locale == "tr") {
                    onLocaleChange("tr")
                    showLanguageDialog = false
                }
            }
        }
    }

    val themeModeText = when (state.themeMode) {
        "light" -> "浅色"
        "dark" -> "深色"
        else -> "跟随系统"
    }
    val languageText = when (state.locale) {
        "zh" -> "中文"
        "en" -> "English"
        "ja" -> "日本語"
        "tr" -> "Türkçe"
        else -> "跟随系统"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionTitle("AI 增强") }
        item {
            SettingsEntryItem(
                title = "AI 通知摘要",
                subtitle = if (state.aiEnabled) "已启用 · 点击配置 AI 参数" else "已关闭 · 点击进行配置",
                onClick = onOpenAiConfig,
            )
        }

        item { SectionTitle("通知黑名单") }
        item {
            SettingsEntryItem(
                title = "通知黑名单",
                subtitle = "启动黑名单应用时，停用焦点通知的自动展开功能",
                onClick = onOpenBlacklist,
            )
        }

        item { SectionTitle("行为") }
        item {
            ToggleItem(
                "交互触感",
                "为开关、滑块和按钮启用 Hyper 定制震感反馈",
                state.interactionHaptics,
            ) { onToggle(PrefKeys.INTERACTION_HAPTICS, it) }
        }
        item {
            ToggleItem(
                "下载管理器暂停后保留焦点通知",
                "显示一条通知，点击以继续下载，可能导致状态不同步",
                state.resumeNotification,
            ) { onToggle(PrefKeys.RESUME_NOTIFICATION, it) }
        }
        item {
            ToggleItem(
                "移除焦点通知白名单",
                "允许所有应用发送焦点通知，无需系统授权",
                state.unlockAllFocus,
            ) { onToggle(PrefKeys.UNLOCK_ALL_FOCUS, it) }
        }
        item {
            ToggleItem(
                "移除焦点通知签名验证",
                "允许所有应用向手表/手环发送焦点通知，跳过签名校验（需 Hook 小米服务框架）",
                state.unlockFocusAuth,
            ) { onToggle(PrefKeys.UNLOCK_FOCUS_AUTH, it) }
        }
        item {
            ToggleItem(
                "显示启动欢迎语",
                "应用启动时在超级岛显示欢迎信息",
                state.showWelcome,
            ) { onToggle(PrefKeys.SHOW_WELCOME, it) }
        }
        item {
            ToggleItem(
                "隐藏桌面图标",
                "隐藏启动器中的应用图标，隐藏后可通过 LSPosed 管理器打开",
                state.hideDesktopIcon,
                onHideDesktopIcon,
            )
        }
        item {
            ToggleItem(
                "启动时检查更新",
                "启动应用时自动检查是否有新版本",
                state.checkUpdateOnLaunch,
            ) { onToggle(PrefKeys.CHECK_UPDATE_ON_LAUNCH, it) }
        }

        item { SectionTitle("渠道默认配置") }
        item {
            ToggleItem(
                "初次展开",
                "超级岛初次收到通知后是否展开为焦点通知",
                state.defaultFirstFloat,
            ) { onToggle(PrefKeys.DEFAULT_FIRST_FLOAT, it) }
        }
        item {
            ToggleItem(
                "更新展开",
                "超级岛更新后是否展开通知",
                state.defaultEnableFloat,
            ) { onToggle(PrefKeys.DEFAULT_ENABLE_FLOAT, it) }
        }
        item {
            ToggleItem(
                "消息滚动",
                "超级岛消息过长是否滚动显示",
                state.defaultMarquee,
            ) { onToggle(PrefKeys.DEFAULT_MARQUEE, it) }
        }
        item {
            ToggleItem(
                "焦点通知",
                "替换通知为焦点通知（关闭后显示原始通知）",
                state.defaultFocusNotif,
            ) { onToggle(PrefKeys.DEFAULT_FOCUS_NOTIF, it) }
        }
        item {
            ToggleItem(
                "锁屏通知复原",
                "锁屏时跳过焦点通知处理，保持原始通知隐私行为",
                state.defaultRestoreLockscreen,
            ) { onToggle(PrefKeys.DEFAULT_RESTORE_LOCKSCREEN, it) }
        }
        item {
            ToggleItem(
                "大岛图标",
                "开启后显示超级岛的大图标（小岛不受影响）",
                state.defaultShowIslandIcon,
            ) { onToggle(PrefKeys.DEFAULT_SHOW_ISLAND_ICON, it) }
        }
        item {
            ToggleItem(
                "状态栏图标",
                "焦点通知打开时，是否强制保留状态栏小图标",
                state.defaultPreserveSmallIcon,
            ) { onToggle(PrefKeys.DEFAULT_PRESERVE_SMALL_ICON, it) }
        }

        item { SectionTitle("外观") }
        item {
            ToggleItem(
                "使用应用图标",
                "下载管理器通知使用应用图标",
                state.useHookAppIcon,
            ) { onToggle(PrefKeys.USE_HOOK_APP_ICON, it) }
        }
        item {
            ToggleItem(
                "图标圆角",
                "为通知图标添加圆角效果",
                state.roundIcon,
            ) { onToggle(PrefKeys.ROUND_ICON, it) }
        }
        item {
            SliderItem(
                title = "消息滚动",
                subtitle = "滚动速度",
                valueText = "${state.marqueeSpeed} 像素/秒",
                value = state.marqueeSpeed.toFloat(),
                valueRange = 20f..500f,
                steps = 48,
                onValueChange = { onMarqueeSpeed(it.toInt()) },
            )
        }
        item {
            ToggleSliderItem(
                "修改超级岛最大宽度",
                "开启后修改超级岛的最大宽度",
                state.bigIslandMaxWidthEnabled,
                valueText = "${state.bigIslandMaxWidth} dp",
                value = state.bigIslandMaxWidth.toFloat(),
                valueRange = 500f..1000f,
                steps = 54,
                onCheckedChange = { onToggle(PrefKeys.BIG_ISLAND_MAX_WIDTH_ENABLED, it) },
                onValueChange = { onBigIslandWidth(it.toInt()) },
            )
        }
        item {
            SettingsEntryItem(
                title = "颜色模式",
                subtitle = themeModeText,
                onClick = { showThemeDialog = true },
            )
        }
        item {
            SettingsEntryItem(
                title = "语言",
                subtitle = languageText,
                onClick = { showLanguageDialog = true },
            )
        }

        item { SectionTitle("配置") }
        item { SettingsEntryItem("导出到文件", "将配置保存为 JSON 文件", onExportToFile) }
        item { SettingsEntryItem("导出到剪贴板", "将配置复制为 JSON 文本", onExportToClipboard) }
        item { SettingsEntryItem("从文件导入", "从 JSON 文件恢复配置", onPickImportFile) }
        item { SettingsEntryItem("从剪贴板导入", "从剪贴板中的 JSON 文本恢复配置", onImportFromClipboard) }

        item { SectionTitle("关于") }
        item { SettingsEntryItem("检查更新", "", onCheckUpdate) }
        item { SettingsEntryItem("GitHub", "1812z/HyperIsland", onOpenGithub) }
        item {
            SettingsEntryItem(
                title = "QQ 交流群",
                subtitle = QQ_GROUP_NUMBER,
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("qq_group", QQ_GROUP_NUMBER))
                    Toast.makeText(context, "群号已复制到剪贴板", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

@Composable
private fun SettingsEntryItem(title: String, subtitle: String, onClick: () -> Unit) {
    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                )
            }
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun ToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    MiuixCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title)
                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            MiuixSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SliderItem(
    title: String,
    subtitle: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    MiuixCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(valueText)
            }
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
            }
            MiuixSlider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
            )
        }
    }
}

@Composable
private fun ToggleSliderItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onCheckedChange: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit,
) {
    MiuixCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(title)
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                MiuixSwitch(checked = checked, onCheckedChange = onCheckedChange)
            }
            if (checked) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiuixSlider(
                        value = value,
                        onValueChange = onValueChange,
                        valueRange = valueRange,
                        steps = steps,
                        modifier = Modifier.weight(1f),
                    )
                    Text(valueText)
                }
            }
        }
    }
}

@Composable
private fun ThemeOption(title: String, selected: Boolean, onClick: () -> Unit) {
    MiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title)
            if (selected) {
                Text("已选择", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun OverlayListPopupMenuItem(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
    }
}
