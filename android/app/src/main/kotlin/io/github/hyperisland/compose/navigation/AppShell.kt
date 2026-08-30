package io.github.hyperisland.compose.navigation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import io.github.hyperisland.compose.component.PredictiveBackBackdrop
import io.github.hyperisland.compose.component.PredictiveBackMotionTracker
import io.github.hyperisland.compose.component.BACKGROUND_PARALLAX
import io.github.hyperisland.compose.component.BACKGROUND_SCALE_REDUCTION
import io.github.hyperisland.compose.component.EFFECT_VISIBILITY_THRESHOLD
import io.github.hyperisland.compose.component.LAYER_ENTER_DURATION
import io.github.hyperisland.compose.component.LAYER_EXIT_DURATION
import io.github.hyperisland.compose.component.PREDICTIVE_CANCEL_DURATION
import io.github.hyperisland.compose.component.PREDICTIVE_DISMISS_DURATION
import io.github.hyperisland.compose.component.predictiveEffectIntensity
import io.github.hyperisland.compose.component.predictiveExitProgress
import io.github.hyperisland.compose.component.predictiveSettleEasing
import io.github.hyperisland.compose.component.predictiveSettleDuration
import io.github.hyperisland.compose.component.predictiveTranslationFraction
import io.github.hyperisland.compose.component.smootherStep
import io.github.hyperisland.compose.component.UpdateDialogHost
import io.github.hyperisland.compose.component.UpdateDialogState
import io.github.hyperisland.compose.component.barBlurBackground
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
import io.github.hyperisland.compose.page.settings.AppearancePage
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
import io.github.hyperisland.compose.page.settings.extensions.FaceUnlockIslandPage
import io.github.hyperisland.compose.page.settings.extensions.HookExtensionDetail
import io.github.hyperisland.compose.page.settings.extensions.HookExtensionPage
import io.github.hyperisland.compose.service.UpdateService
import io.github.hyperisland.compose.theme.PREF_BLUR_BARS
import io.github.hyperisland.compose.theme.PREF_FLOATING_NAVIGATION_BAR
import io.github.hyperisland.compose.theme.PREF_LIQUID_GLASS_NAVIGATION_BAR
import io.github.hyperisland.compose.theme.DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT
import io.github.hyperisland.compose.theme.PREF_PREDICTIVE_BACK_MAX_TRANSLATION
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
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
    val updateSnackbarState = remember { SnackbarHostState() }
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
    val nestedDetailShown = mediaShown || materialShown || visibleChannelEditor != null ||
        (visibleChannelApp != null && batchChannelTarget != null) || extensionDetail != null
    var detailPredictiveBackActive by remember { mutableStateOf(false) }
    var mediaPredictiveBackActive by remember { mutableStateOf(false) }
    var detailPredictiveCommitting by remember { mutableStateOf(false) }
    var mediaPredictiveCommitting by remember { mutableStateOf(false) }
    val predictiveProgress = remember { Animatable(0f) }
    val mediaPredictiveProgress = remember { Animatable(0f) }
    val detailBackdropIntensity = remember { Animatable(0f) }
    val mediaBackdropIntensity = remember { Animatable(0f) }
    val rootLayerDepth = remember { Animatable(0f) }
    val detailLayerDepth = remember { Animatable(0f) }
    val detailPredictiveMotion = remember { PredictiveBackMotionTracker() }
    val mediaPredictiveMotion = remember { PredictiveBackMotionTracker() }

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
                updateSnackbarState.showSnackbar(alreadyLatestMessage)
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

    LaunchedEffect(detailShown, detailPredictiveBackActive) {
        if (!detailPredictiveBackActive) {
            val target = if (detailShown) 1f else 0f
            val duration = if (detailShown) LAYER_ENTER_DURATION else LAYER_EXIT_DURATION
            coroutineScope {
                launch {
                    detailBackdropIntensity.animateTo(
                        target,
                        tween(duration, easing = FastOutSlowInEasing),
                    )
                }
                launch {
                    rootLayerDepth.animateTo(
                        target,
                        tween(duration, easing = FastOutSlowInEasing),
                    )
                }
            }
        }
    }

    LaunchedEffect(nestedDetailShown, mediaPredictiveBackActive) {
        if (!mediaPredictiveBackActive) {
            val target = if (nestedDetailShown) 1f else 0f
            val duration = if (nestedDetailShown) LAYER_ENTER_DURATION else LAYER_EXIT_DURATION
            coroutineScope {
                launch {
                    mediaBackdropIntensity.animateTo(
                        target,
                        tween(duration, easing = FastOutSlowInEasing),
                    )
                }
                launch {
                    detailLayerDepth.animateTo(
                        target,
                        tween(duration, easing = FastOutSlowInEasing),
                    )
                }
            }
        }
    }

    suspend fun finishDetailPredictiveBack() {
        detailPredictiveCommitting = true
        val targetProgress = predictiveExitProgress(predictiveBackMaxTranslation.value)
        val duration = predictiveSettleDuration(
            progress = predictiveProgress.value,
            maxTranslationPercent = predictiveBackMaxTranslation.value,
        )
        val settleEasing = predictiveSettleEasing(
            releaseVelocity = detailPredictiveMotion.releaseVelocity(),
            currentProgress = predictiveProgress.value,
            targetProgress = targetProgress,
            durationMillis = duration,
        )
        coroutineScope {
            launch {
                predictiveProgress.animateTo(
                    targetProgress,
                    tween(duration, easing = settleEasing),
                )
            }
            launch {
                detailBackdropIntensity.animateTo(0f, tween(duration, easing = settleEasing))
            }
            launch {
                rootLayerDepth.animateTo(0f, tween(duration, easing = settleEasing))
            }
        }
        detailShown = false
        batchChannelTarget = null
        batchToastPackages = null
        delay(PREDICTIVE_DISMISS_DURATION.toLong())
        predictiveProgress.snapTo(0f)
        detailPredictiveMotion.reset()
        detailPredictiveBackActive = false
        detailPredictiveCommitting = false
    }

    suspend fun finishNestedPredictiveBack() {
        mediaPredictiveCommitting = true
        val targetProgress = predictiveExitProgress(predictiveBackMaxTranslation.value)
        val duration = predictiveSettleDuration(
            progress = mediaPredictiveProgress.value,
            maxTranslationPercent = predictiveBackMaxTranslation.value,
        )
        val settleEasing = predictiveSettleEasing(
            releaseVelocity = mediaPredictiveMotion.releaseVelocity(),
            currentProgress = mediaPredictiveProgress.value,
            targetProgress = targetProgress,
            durationMillis = duration,
        )
        coroutineScope {
            launch {
                mediaPredictiveProgress.animateTo(
                    targetProgress,
                    tween(duration, easing = settleEasing),
                )
            }
            launch {
                mediaBackdropIntensity.animateTo(0f, tween(duration, easing = settleEasing))
            }
            launch {
                detailLayerDepth.animateTo(0f, tween(duration, easing = settleEasing))
            }
        }
        mediaShown = false
        materialShown = false
        visibleChannelEditor = null
        if (visibleChannelApp != null) batchChannelTarget = null
        extensionDetail = null
        delay(PREDICTIVE_DISMISS_DURATION.toLong())
        mediaPredictiveProgress.snapTo(0f)
        mediaPredictiveMotion.reset()
        mediaPredictiveBackActive = false
        mediaPredictiveCommitting = false
    }

    PredictiveBackHandler(enabled = detailShown && !nestedDetailShown) { events ->
        try {
            events.collect { event ->
                if (!detailPredictiveBackActive) {
                    detailPredictiveMotion.reset(event.progress)
                } else {
                    detailPredictiveMotion.update(event.progress)
                }
                detailPredictiveBackActive = true
                predictiveProgress.snapTo(event.progress)
                val smoothProgress = smootherStep(event.progress)
                detailBackdropIntensity.snapTo(predictiveEffectIntensity(smoothProgress))
                rootLayerDepth.snapTo(1f - smoothProgress)
            }
            finishDetailPredictiveBack()
        } catch (_: CancellationException) {
            if (!detailPredictiveCommitting) {
                coroutineScope {
                    launch {
                        predictiveProgress.animateTo(
                            0f,
                            tween(PREDICTIVE_CANCEL_DURATION, easing = FastOutSlowInEasing),
                        )
                    }
                    launch {
                        detailBackdropIntensity.animateTo(
                            1f,
                            tween(PREDICTIVE_CANCEL_DURATION, easing = FastOutSlowInEasing),
                        )
                    }
                    launch {
                        rootLayerDepth.animateTo(
                            1f,
                            tween(PREDICTIVE_CANCEL_DURATION, easing = FastOutSlowInEasing),
                        )
                    }
                }
                detailPredictiveMotion.reset()
                detailPredictiveBackActive = false
            }
        }
    }

    PredictiveBackHandler(enabled = nestedDetailShown) { events ->
        try {
            events.collect { event ->
                if (!mediaPredictiveBackActive) {
                    mediaPredictiveMotion.reset(event.progress)
                } else {
                    mediaPredictiveMotion.update(event.progress)
                }
                mediaPredictiveBackActive = true
                mediaPredictiveProgress.snapTo(event.progress)
                val smoothProgress = smootherStep(event.progress)
                mediaBackdropIntensity.snapTo(predictiveEffectIntensity(smoothProgress))
                detailLayerDepth.snapTo(1f - smoothProgress)
            }
            finishNestedPredictiveBack()
        } catch (_: CancellationException) {
            if (!mediaPredictiveCommitting) {
                coroutineScope {
                    launch {
                        mediaPredictiveProgress.animateTo(
                            0f,
                            tween(PREDICTIVE_CANCEL_DURATION, easing = FastOutSlowInEasing),
                        )
                    }
                    launch {
                        mediaBackdropIntensity.animateTo(
                            1f,
                            tween(PREDICTIVE_CANCEL_DURATION, easing = FastOutSlowInEasing),
                        )
                    }
                    launch {
                        detailLayerDepth.animateTo(
                            1f,
                            tween(PREDICTIVE_CANCEL_DURATION, easing = FastOutSlowInEasing),
                        )
                    }
                }
                mediaPredictiveMotion.reset()
                mediaPredictiveBackActive = false
            }
        }
    }

    BackHandler(enabled = detailPredictiveBackActive && detailPredictiveCommitting) {
        scope.launch { finishDetailPredictiveBack() }
    }

    BackHandler(enabled = mediaPredictiveBackActive && mediaPredictiveCommitting) {
        scope.launch { finishNestedPredictiveBack() }
    }

    BarBlurHost(
        enabled = blurBars.value,
        liquidGlassEnabled = floatingNavigationBar.value && liquidGlassNavigationBar.value,
        captureForEffects = detailShown ||
            detailPredictiveBackActive ||
            detailBackdropIntensity.value > EFFECT_VISIBILITY_THRESHOLD,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val depth = rootLayerDepth.value.coerceIn(0f, 1f)
                        scaleX = 1f - depth * BACKGROUND_SCALE_REDUCTION
                        scaleY = scaleX
                        translationX = -size.width * depth * BACKGROUND_PARALLAX
                    },
                snackbarHost = { SnackbarHost(updateSnackbarState) },
                bottomBar = {
                    AnimatedVisibility(
                        visible = !detailShown &&
                            !detailPredictiveBackActive &&
                            rootLayerDepth.value < EFFECT_VISIBILITY_THRESHOLD,
                        enter = slideInVertically(tween(260)) { it } + fadeIn(tween(180)),
                        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(140)),
                    ) {
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

            PredictiveBackBackdrop(
                intensity = detailBackdropIntensity.value,
                visible = detailBackdropIntensity.value > EFFECT_VISIBILITY_THRESHOLD,
                modifier = Modifier.fillMaxSize(),
            )

            BarBlurHost(
                enabled = blurBars.value,
                captureForEffects = nestedDetailShown ||
                    mediaPredictiveBackActive ||
                    mediaBackdropIntensity.value > EFFECT_VISIBILITY_THRESHOLD,
            ) {
                BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = detailShown,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val progress = predictiveProgress.value.coerceAtLeast(0f)
                                val depth = detailLayerDepth.value.coerceIn(0f, 1f)
                                translationX = -size.width * depth * BACKGROUND_PARALLAX +
                                    size.width * progress *
                                    predictiveTranslationFraction(predictiveBackMaxTranslation.value)
                                scaleX = 1f - depth * BACKGROUND_SCALE_REDUCTION
                                scaleY = scaleX
                            },
                        enter = slideInHorizontally(
                            tween(LAYER_ENTER_DURATION, easing = FastOutSlowInEasing),
                        ) { it },
                        exit = if (detailPredictiveBackActive) {
                            ExitTransition.None
                        } else {
                            slideOutHorizontally(
                                tween(LAYER_EXIT_DURATION, easing = FastOutSlowInEasing),
                            ) { it }
                        },
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
                                    onOpenDetail = { extensionDetail = it },
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

                PredictiveBackBackdrop(
                    intensity = mediaBackdropIntensity.value,
                    visible = mediaBackdropIntensity.value > EFFECT_VISIBILITY_THRESHOLD,
                    modifier = Modifier.fillMaxSize(),
                )

                AnimatedVisibility(
                    visible = nestedDetailShown,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val progress = mediaPredictiveProgress.value.coerceAtLeast(0f)
                            translationX = size.width * progress *
                                predictiveTranslationFraction(predictiveBackMaxTranslation.value)
                        },
                    enter = slideInHorizontally(
                        tween(LAYER_ENTER_DURATION, easing = FastOutSlowInEasing),
                    ) { it },
                    exit = if (mediaPredictiveBackActive) {
                        ExitTransition.None
                    } else {
                        slideOutHorizontally(
                            tween(LAYER_EXIT_DURATION, easing = FastOutSlowInEasing),
                        ) { it }
                    },
                ) {
                    when (extensionDetail) {
                        HookExtensionDetail.Bluetooth -> BluetoothIslandPage(
                            prefs = prefs,
                            onBack = { extensionDetail = null },
                        )
                        HookExtensionDetail.Charge -> ChargeIslandPage(
                            prefs = prefs,
                            onBack = { extensionDetail = null },
                        )
                        HookExtensionDetail.FaceUnlock -> FaceUnlockIslandPage(
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

private const val PREF_CHECK_UPDATE_ON_LAUNCH = "pref_check_update_on_launch"
