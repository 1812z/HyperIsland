package io.github.hyperisland.compose.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.BuildConfig
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.BarBackdropContent
import io.github.hyperisland.compose.component.BarBlurHost
import io.github.hyperisland.compose.component.BlurredBar
import io.github.hyperisland.compose.component.LocalRootBottomBarPadding
import io.github.hyperisland.compose.component.LiquidGlassNavigationBar
import io.github.hyperisland.compose.component.LiquidGlassNavigationItem
import io.github.hyperisland.compose.component.LAYER_EXIT_DURATION
import io.github.hyperisland.compose.component.PredictiveNavigationBackHandler
import io.github.hyperisland.compose.component.PredictiveNavigationBackdrop
import io.github.hyperisland.compose.component.PredictiveNavigationLayer
import io.github.hyperisland.compose.component.UpdateDialogHost
import io.github.hyperisland.compose.component.UpdateDialogState
import io.github.hyperisland.compose.component.barBlurBackground
import io.github.hyperisland.compose.component.predictiveNavigationBackground
import io.github.hyperisland.compose.component.rememberPredictiveNavigationLayerState
import io.github.hyperisland.compose.component.requiresBackdropCapture
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.InstalledApp
import io.github.hyperisland.compose.data.channel.BatchChannelTarget
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.data.rememberLongPreference
import io.github.hyperisland.compose.page.AppsPage
import io.github.hyperisland.compose.page.AboutPage
import io.github.hyperisland.compose.page.apps.NotificationChannelsPage
import io.github.hyperisland.compose.page.apps.channel.ChannelEditorPage
import io.github.hyperisland.compose.page.apps.channel.BatchChannelSettingsPage
import io.github.hyperisland.compose.page.apps.MediaNotificationPage
import io.github.hyperisland.compose.page.apps.ToastSettingsPage
import io.github.hyperisland.compose.page.apps.toast.BatchToastSettingsPage
import io.github.hyperisland.compose.page.home.OverviewPage
import io.github.hyperisland.compose.page.home.rememberHomeOverviewState
import io.github.hyperisland.compose.page.onboarding.OnboardingPage
import io.github.hyperisland.compose.page.SettingsDetail
import io.github.hyperisland.compose.page.SettingsPage
import io.github.hyperisland.compose.page.settings.HideBehaviorPage
import io.github.hyperisland.compose.page.settings.appearance.AppearancePage
import io.github.hyperisland.compose.page.settings.IslandMaterialPage
import io.github.hyperisland.compose.page.settings.DefaultConfigPage
import io.github.hyperisland.compose.page.settings.AiConfigPage
import io.github.hyperisland.compose.page.settings.BackupRestorePage
import io.github.hyperisland.compose.page.settings.FilterRulesPage
import io.github.hyperisland.compose.page.settings.IslandOtherPage
import io.github.hyperisland.compose.page.settings.KeepIslandPage
import io.github.hyperisland.compose.page.settings.MiscPage
import io.github.hyperisland.compose.page.settings.ReferencesPage
import io.github.hyperisland.compose.page.settings.ThemeSettingsPage
import io.github.hyperisland.compose.page.settings.extensions.BluetoothIslandPage
import io.github.hyperisland.compose.page.settings.extensions.ChargeIslandPage
import io.github.hyperisland.compose.page.settings.extensions.DownloadManagerHookPage
import io.github.hyperisland.compose.page.settings.extensions.FaceUnlockIslandPage
import io.github.hyperisland.compose.page.settings.extensions.HeartRateIslandPage
import io.github.hyperisland.compose.page.settings.extensions.HookExtensionDetail
import io.github.hyperisland.compose.page.settings.extensions.HookExtensionPage
import io.github.hyperisland.compose.page.settings.extensions.ScreenRecorderHookPage
import io.github.hyperisland.compose.page.settings.extensions.SecurityCenterHookPage
import io.github.hyperisland.compose.page.settings.extensions.SystemSettingsHookPage
import io.github.hyperisland.compose.page.settings.extensions.SystemUiHookPage
import io.github.hyperisland.compose.page.settings.extensions.SystemUiExtensionDetail
import io.github.hyperisland.compose.page.settings.extensions.XmsfHookPage
import io.github.hyperisland.compose.service.UpdateService
import io.github.hyperisland.compose.theme.PREF_BLUR_BARS
import io.github.hyperisland.compose.theme.PREF_FLOATING_NAVIGATION_BAR
import io.github.hyperisland.compose.theme.PREF_LIQUID_GLASS_NAVIGATION_BAR
import io.github.hyperisland.compose.theme.DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT
import io.github.hyperisland.compose.theme.PREF_PREDICTIVE_BACK_MAX_TRANSLATION
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.FloatingToolbarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Settings

private data class RootDestination(@StringRes val title: Int, val icon: ImageVector)

@Composable
internal fun HyperIslandApp(prefs: FlutterPrefsRepository) {
    val context = LocalContext.current
    val destinations = remember {
        listOf(
            RootDestination(R.string.nav_home, MiuixIcons.Home),
            RootDestination(R.string.nav_apps, MiuixIcons.GridView),
            RootDestination(R.string.nav_settings, MiuixIcons.Settings),
            RootDestination(R.string.about, MiuixIcons.Info),
        )
    }
    val pagerState = rememberPagerState(pageCount = { destinations.size })
    var appsSelectedMode by remember { mutableIntStateOf(0) }
    val homeOverviewState = rememberHomeOverviewState(prefs)
    val scope = rememberCoroutineScope()
    val rootSnackbarState = remember { SnackbarHostState() }
    val alreadyLatestMessage = stringResource(R.string.already_latest)
    var updateDialogState by remember { mutableStateOf<UpdateDialogState?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    val floatingNavigationBar = rememberBooleanPreference(prefs, PREF_FLOATING_NAVIGATION_BAR, false)
    val liquidGlassNavigationBar = rememberBooleanPreference(
        prefs,
        PREF_LIQUID_GLASS_NAVIGATION_BAR,
        false,
    )
    val blurBars = rememberBooleanPreference(prefs, PREF_BLUR_BARS, false)
    val predictiveBackMaxTranslation = rememberLongPreference(
        prefs,
        PREF_PREDICTIVE_BACK_MAX_TRANSLATION,
        DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT,
    )
    var visibleDetail by remember { mutableStateOf<SettingsDetail?>(null) }
    var visibleChannelApp by remember { mutableStateOf<InstalledApp?>(null) }
    var visibleToastApp by remember { mutableStateOf<InstalledApp?>(null) }
    var detailShown by remember { mutableStateOf(false) }
    var mediaShown by remember { mutableStateOf(false) }
    var visibleChannelEditor by remember { mutableStateOf<io.github.hyperisland.compose.data.NotificationChannelInfo?>(null) }
    var batchChannelTarget by remember { mutableStateOf<BatchChannelTarget?>(null) }
    var batchToastPackages by remember { mutableStateOf<Set<String>?>(null) }
    var materialShown by remember { mutableStateOf(false) }
    var extensionDetail by remember { mutableStateOf<HookExtensionDetail?>(null) }
    var extensionSubDetail by remember { mutableStateOf<SystemUiExtensionDetail?>(null) }
    // Keep the last route composed until AnimatedVisibility finishes its exit transition.
    var renderedExtensionSubDetail by remember { mutableStateOf<SystemUiExtensionDetail?>(null) }
    val nestedDetailShown = mediaShown || materialShown || visibleChannelEditor != null ||
        (visibleChannelApp != null && batchChannelTarget != null) || extensionDetail != null
    val extensionSubDetailShown = extensionSubDetail != null
    val detailNavigationState = rememberPredictiveNavigationLayerState()
    val nestedNavigationState = rememberPredictiveNavigationLayerState()
    val extensionNavigationState = rememberPredictiveNavigationLayerState()
    val bottomBarProgress = remember { Animatable(0f) }

    @Composable
    fun RootBottomBar() {
        if (floatingNavigationBar.value) {
            if (liquidGlassNavigationBar.value) {
                LiquidGlassNavigationBar(
                    selectedTabIndex = { pagerState.currentPage },
                    onTabSelected = { index ->
                        if (pagerState.currentPage != index) {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                    },
                    items = destinations.map { destination ->
                        LiquidGlassNavigationItem(
                            icon = destination.icon,
                            label = stringResource(destination.title),
                        )
                    },
                )
            } else {
                FloatingNavigationBar(
                    modifier = Modifier.barBlurBackground(
                        RoundedCornerShape(FloatingToolbarDefaults.CornerRadius),
                    ),
                    color = Color.Transparent,
                ) {
                    destinations.forEachIndexed { index, destination ->
                        FloatingNavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            icon = destination.icon,
                            label = stringResource(destination.title),
                        )
                    }
                }
            }
        } else {
            BlurredBar {
                NavigationBar(color = Color.Transparent) {
                    destinations.forEachIndexed { index, destination ->
                        NavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            icon = destination.icon,
                            label = stringResource(destination.title),
                        )
                    }
                }
            }
        }
    }

    fun requestUpdateCheck(showUpToDate: Boolean) {
        if (isCheckingUpdate) return
        isCheckingUpdate = true
        scope.launch {
            var showAlreadyLatest = false
            try {
                val update = UpdateService.fetchIfNewer(BuildConfig.VERSION_NAME)
                if (update != null) {
                    updateDialogState = UpdateDialogState.Available(
                        currentVersion = BuildConfig.VERSION_NAME,
                        update = update,
                    )
                } else if (showUpToDate) {
                    showAlreadyLatest = true
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                updateDialogState = UpdateDialogState.Failure
            } finally {
                isCheckingUpdate = false
            }
            if (showAlreadyLatest) {
                rootSnackbarState.showSnackbar(alreadyLatestMessage)
            }
        }
    }

    LaunchedEffect(prefs) {
        if (prefs.getBoolean(PREF_CHECK_UPDATE_ON_LAUNCH, true)) {
            requestUpdateCheck(showUpToDate = false)
        }
    }

    fun closeDetail() {
        detailShown = false
        batchChannelTarget = null
        batchToastPackages = null
    }

    LaunchedEffect(detailShown, detailNavigationState.isBackActive) {
        if (!detailNavigationState.isBackActive) {
            bottomBarProgress.animateTo(
                if (detailShown) 1f else 0f,
                tween(
                    if (detailShown) BOTTOM_BAR_ENTER_DURATION else LAYER_EXIT_DURATION,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    PredictiveNavigationBackHandler(
        visible = detailShown,
        enabled = detailShown && !nestedDetailShown,
        state = detailNavigationState,
        maxTranslationPercent = predictiveBackMaxTranslation.value,
        onDismiss = {
            detailShown = false
            batchChannelTarget = null
            batchToastPackages = null
        },
        additionalCommitAnimation = { _, _ ->
            bottomBarProgress.animateTo(
                0f,
                tween(LAYER_EXIT_DURATION, easing = FastOutSlowInEasing),
            )
        },
    )

    PredictiveNavigationBackHandler(
        visible = nestedDetailShown,
        enabled = nestedDetailShown && !extensionSubDetailShown,
        state = nestedNavigationState,
        maxTranslationPercent = predictiveBackMaxTranslation.value,
        onDismiss = {
            mediaShown = false
            materialShown = false
            visibleChannelEditor = null
            if (visibleChannelApp != null) batchChannelTarget = null
            extensionDetail = null
            extensionSubDetail = null
        },
    )

    PredictiveNavigationBackHandler(
        visible = extensionSubDetailShown,
        enabled = extensionSubDetailShown,
        state = extensionNavigationState,
        maxTranslationPercent = predictiveBackMaxTranslation.value,
        onDismiss = { extensionSubDetail = null },
    )

    BarBlurHost(
        enabled = blurBars.value,
        liquidGlassEnabled = floatingNavigationBar.value && liquidGlassNavigationBar.value,
        captureForEffects = detailNavigationState.requiresBackdropCapture(detailShown),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .predictiveNavigationBackground(detailNavigationState),
                snackbarHost = { SnackbarHost(rootSnackbarState) },
                bottomBar = {
                    // Keep the Scaffold slot measured so page content keeps the bottom-bar padding.
                    Box(modifier = Modifier.graphicsLayer { alpha = 0f }) {
                        RootBottomBar()
                    }
                },
            ) { padding ->
                BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(
                        LocalRootBottomBarPadding provides padding.calculateBottomPadding(),
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            beyondViewportPageCount = 1,
                        ) { page ->
                            when (page) {
                                0 -> OverviewPage(
                                    state = homeOverviewState,
                                    isActive = pagerState.settledPage == page,
                                    onOpenApps = {
                                        appsSelectedMode = 0
                                        scope.launch { pagerState.animateScrollToPage(1) }
                                    },
                                    onOpenToastApps = {
                                        appsSelectedMode = 1
                                        scope.launch { pagerState.animateScrollToPage(1) }
                                    },
                                )
                                1 -> AppsPage(
                                    prefs = prefs,
                                    selectedMode = appsSelectedMode,
                                    onSelectedModeChange = { appsSelectedMode = it },
                                    onOpenChannels = { app ->
                                        batchChannelTarget = null
                                        batchToastPackages = null
                                        visibleDetail = null
                                        visibleToastApp = null
                                        visibleChannelApp = app
                                        detailShown = true
                                    },
                                    onOpenToastSettings = { app ->
                                        batchChannelTarget = null
                                        batchToastPackages = null
                                        visibleDetail = null
                                        visibleChannelApp = null
                                        visibleToastApp = app
                                        detailShown = true
                                    },
                                    onOpenBatchChannelSettings = { packages ->
                                        batchToastPackages = null
                                        visibleDetail = null
                                        visibleChannelApp = null
                                        visibleToastApp = null
                                        batchChannelTarget = BatchChannelTarget.Apps(packages)
                                        detailShown = true
                                    },
                                    onOpenBatchToastSettings = { packages ->
                                        batchChannelTarget = null
                                        visibleDetail = null
                                        visibleChannelApp = null
                                        visibleToastApp = null
                                        batchToastPackages = packages
                                        detailShown = true
                                    },
                                )
                                2 -> SettingsPage(
                                    prefs = prefs,
                                    onOpenDetail = {
                                        batchChannelTarget = null
                                        batchToastPackages = null
                                        visibleChannelApp = null
                                        visibleToastApp = null
                                        extensionDetail = null
                                        extensionSubDetail = null
                                        visibleDetail = it
                                        detailShown = true
                                    },
                                )
                                else -> AboutPage(
                                    isActive = pagerState.currentPage == page,
                                    isCheckingUpdate = isCheckingUpdate,
                                    onCheckUpdate = { requestUpdateCheck(showUpToDate = true) },
                                    onOpenBackupRestore = {
                                        batchChannelTarget = null
                                        batchToastPackages = null
                                        visibleChannelApp = null
                                        visibleToastApp = null
                                        visibleDetail = SettingsDetail.BackupRestore
                                        detailShown = true
                                    },
                                    onOpenReferences = {
                                        batchChannelTarget = null
                                        batchToastPackages = null
                                        visibleChannelApp = null
                                        visibleToastApp = null
                                        visibleDetail = SettingsDetail.References
                                        detailShown = true
                                    },
                                )
                            }
                        }
                    }
                }
            }

            PredictiveNavigationBackdrop(
                state = detailNavigationState,
                modifier = Modifier.fillMaxSize(),
            )

            BarBlurHost(
                enabled = blurBars.value,
                captureForEffects = nestedNavigationState.requiresBackdropCapture(
                    nestedDetailShown,
                ),
            ) {
                BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                    PredictiveNavigationLayer(
                        visible = detailShown,
                        state = detailNavigationState,
                        backgroundState = nestedNavigationState,
                        maxTranslationPercent = predictiveBackMaxTranslation.value,
                    ) {
                        val channelApp = visibleChannelApp
                        if (channelApp != null) {
                            NotificationChannelsPage(
                                app = channelApp,
                                prefs = prefs,
                                onBack = ::closeDetail,
                                onOpenMediaSettings = {
                                    materialShown = false
                                    visibleChannelEditor = null
                                    mediaShown = true
                                },
                                onOpenChannelSettings = { channel ->
                                    mediaShown = false
                                    materialShown = false
                                    visibleChannelEditor = channel
                                },
                                onOpenBatchChannelSettings = { channelIds ->
                                    mediaShown = false
                                    materialShown = false
                                    visibleChannelEditor = null
                                    batchChannelTarget = BatchChannelTarget.Channels(
                                        channelApp.packageName,
                                        channelIds,
                                    )
                                },
                            )
                        } else if (visibleToastApp != null) {
                            ToastSettingsPage(
                                app = visibleToastApp!!,
                                prefs = prefs,
                                onBack = ::closeDetail,
                            )
                        } else if (batchChannelTarget != null) {
                            BatchChannelSettingsPage(
                                target = batchChannelTarget!!,
                                prefs = prefs,
                                onBack = ::closeDetail,
                            )
                        } else if (batchToastPackages != null) {
                            BatchToastSettingsPage(
                                packageNames = batchToastPackages!!,
                                prefs = prefs,
                                onBack = ::closeDetail,
                            )
                        } else {
                            when (visibleDetail) {
                                SettingsDetail.Appearance -> AppearancePage(
                                    prefs = prefs,
                                    materialVisible = materialShown,
                                    onOpenMaterial = {
                                        mediaShown = false
                                        materialShown = true
                                    },
                                    onBack = ::closeDetail,
                                )
                                SettingsDetail.Theme -> ThemeSettingsPage(prefs, ::closeDetail)
                                SettingsDetail.HideBehavior -> HideBehaviorPage(prefs, ::closeDetail)
                                SettingsDetail.DefaultConfig -> DefaultConfigPage(prefs, ::closeDetail)
                                SettingsDetail.AiConfig -> AiConfigPage(prefs, ::closeDetail)
                                SettingsDetail.Misc -> MiscPage(
                                    prefs = prefs,
                                    onOpenOnboarding = {
                                        visibleDetail = SettingsDetail.Onboarding
                                    },
                                    onBack = ::closeDetail,
                                )
                                SettingsDetail.Other -> IslandOtherPage(prefs, ::closeDetail)
                                SettingsDetail.References -> ReferencesPage(::closeDetail)
                                SettingsDetail.BackupRestore -> BackupRestorePage(::closeDetail)
                                SettingsDetail.FilterRules -> FilterRulesPage(prefs, ::closeDetail)
                                SettingsDetail.KeepIsland -> KeepIslandPage(prefs, ::closeDetail)
                                SettingsDetail.HookExtension -> HookExtensionPage(
                                    prefs = prefs,
                                    onOpenDetail = {
                                        extensionSubDetail = null
                                        extensionDetail = it
                                    },
                                    onBack = ::closeDetail,
                                )
                                SettingsDetail.Onboarding -> OnboardingPage(
                                    prefs = prefs,
                                    showCloseButton = true,
                                    onFinished = ::closeDetail,
                                )
                                null -> Unit
                            }
                        }
                    }
                }

                PredictiveNavigationBackdrop(
                    state = nestedNavigationState,
                    modifier = Modifier.fillMaxSize(),
                )

                BarBlurHost(
                    enabled = blurBars.value,
                    captureForEffects = extensionNavigationState.requiresBackdropCapture(
                        extensionSubDetailShown,
                    ),
                ) {
                    BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                        PredictiveNavigationLayer(
                            visible = nestedDetailShown,
                            state = nestedNavigationState,
                            backgroundState = extensionNavigationState,
                            maxTranslationPercent = predictiveBackMaxTranslation.value,
                        ) {
                            when (extensionDetail) {
                                HookExtensionDetail.SystemUi -> SystemUiHookPage(
                                    prefs = prefs,
                                    onOpenDetail = {
                                        renderedExtensionSubDetail = it
                                        extensionSubDetail = it
                                    },
                                    onBack = {
                                        extensionSubDetail = null
                                        extensionDetail = null
                                    },
                                )
                                HookExtensionDetail.SystemSettings -> SystemSettingsHookPage(
                                    prefs = prefs,
                                    onBack = { extensionDetail = null },
                                )
                                HookExtensionDetail.SecurityCenter -> SecurityCenterHookPage(
                                    prefs = prefs,
                                    onBack = { extensionDetail = null },
                                )
                                HookExtensionDetail.Xmsf -> XmsfHookPage(
                                    prefs = prefs,
                                    onBack = { extensionDetail = null },
                                )
                                HookExtensionDetail.ScreenRecorder -> ScreenRecorderHookPage(
                                    prefs = prefs,
                                    onBack = { extensionDetail = null },
                                )
                                HookExtensionDetail.DownloadManager -> DownloadManagerHookPage(
                                    prefs = prefs,
                                    onBack = { extensionDetail = null },
                                )
                                null -> if (batchChannelTarget != null && visibleChannelApp != null) {
                                    BatchChannelSettingsPage(
                                        target = batchChannelTarget!!,
                                        prefs = prefs,
                                        onBack = { batchChannelTarget = null },
                                    )
                                } else if (visibleChannelEditor != null && visibleChannelApp != null) {
                                    ChannelEditorPage(
                                        appPackage = visibleChannelApp!!.packageName,
                                        channel = visibleChannelEditor!!,
                                        prefs = prefs,
                                        onBack = { visibleChannelEditor = null },
                                    )
                                } else if (materialShown) {
                                    IslandMaterialPage(
                                        prefs = prefs,
                                        onBack = { materialShown = false },
                                    )
                                } else {
                                    visibleChannelApp?.let { app ->
                                        MediaNotificationPage(
                                            app = app,
                                            prefs = prefs,
                                            onBack = { mediaShown = false },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    PredictiveNavigationBackdrop(
                        state = extensionNavigationState,
                        modifier = Modifier.fillMaxSize(),
                    )

                    PredictiveNavigationLayer(
                        visible = extensionSubDetailShown,
                        state = extensionNavigationState,
                        maxTranslationPercent = predictiveBackMaxTranslation.value,
                    ) {
                        ExtensionSubDetailPage(
                            detail = renderedExtensionSubDetail,
                            prefs = prefs,
                            onBack = { extensionSubDetail = null },
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        translationY = size.height * bottomBarProgress.value
                        alpha = 1f - bottomBarProgress.value
                    },
            ) {
                RootBottomBar()
            }
        }
    }

    UpdateDialogHost(
        state = updateDialogState,
        onDismiss = { updateDialogState = null },
        onViewUpdate = { releaseUrl ->
            updateDialogState = null
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)))
            }.onFailure {
                updateDialogState = UpdateDialogState.Failure
            }
        },
    )
}

@Composable
private fun ExtensionSubDetailPage(
    detail: SystemUiExtensionDetail?,
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
) {
    when (detail) {
        SystemUiExtensionDetail.Bluetooth -> BluetoothIslandPage(prefs, onBack)
        SystemUiExtensionDetail.HeartRate -> HeartRateIslandPage(prefs, onBack)
        SystemUiExtensionDetail.Charge -> ChargeIslandPage(prefs, onBack)
        SystemUiExtensionDetail.FaceUnlock -> FaceUnlockIslandPage(prefs, onBack)
        null -> Unit
    }
}

private const val PREF_CHECK_UPDATE_ON_LAUNCH = "pref_check_update_on_launch"
private const val BOTTOM_BAR_ENTER_DURATION = 300
