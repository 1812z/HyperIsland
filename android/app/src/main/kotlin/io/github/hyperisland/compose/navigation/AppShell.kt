package io.github.hyperisland.compose.navigation

import android.content.Intent
import android.net.Uri
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
import io.github.hyperisland.MainActivity
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.BarBackdropContent
import io.github.hyperisland.compose.component.BarBlurHost
import io.github.hyperisland.compose.component.BlurredBar
import io.github.hyperisland.compose.component.LocalRootBottomBarPadding
import io.github.hyperisland.compose.component.PredictiveBackBackdrop
import io.github.hyperisland.compose.component.barBlurBackground
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.InstalledApp
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.data.rememberLongPreference
import io.github.hyperisland.compose.page.AppsPage
import io.github.hyperisland.compose.page.AboutPage
import io.github.hyperisland.compose.page.apps.NotificationChannelsPage
import io.github.hyperisland.compose.page.apps.MediaNotificationPage
import io.github.hyperisland.compose.page.apps.ToastSettingsPage
import io.github.hyperisland.compose.page.home.OverviewPage
import io.github.hyperisland.compose.page.home.rememberHomeOverviewState
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
import io.github.hyperisland.compose.theme.PREF_BLUR_BARS
import io.github.hyperisland.compose.theme.PREF_FLOATING_NAVIGATION_BAR
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
            RootDestination(R.string.compose_nav_home, MiuixIcons.Home),
            RootDestination(R.string.compose_nav_apps, MiuixIcons.GridView),
            RootDestination(R.string.compose_nav_settings, MiuixIcons.Settings),
            RootDestination(R.string.compose_about, MiuixIcons.Info),
        )
    }
    val pagerState = rememberPagerState(pageCount = { destinations.size })
    val homeOverviewState = rememberHomeOverviewState(prefs)
    val scope = rememberCoroutineScope()
    val floatingNavigationBar = rememberBooleanPreference(prefs, PREF_FLOATING_NAVIGATION_BAR, false)
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
    var materialShown by remember { mutableStateOf(false) }
    var extensionDetail by remember { mutableStateOf<HookExtensionDetail?>(null) }
    val nestedDetailShown = mediaShown || materialShown || extensionDetail != null
    var detailPredictiveBackActive by remember { mutableStateOf(false) }
    var mediaPredictiveBackActive by remember { mutableStateOf(false) }
    val predictiveProgress = remember { Animatable(0f) }
    val mediaPredictiveProgress = remember { Animatable(0f) }
    val detailBackdropIntensity = remember { Animatable(0f) }
    val mediaBackdropIntensity = remember { Animatable(0f) }
    val rootLayerDepth = remember { Animatable(0f) }
    val detailLayerDepth = remember { Animatable(0f) }
    val openLegacy = remember(context) {
        { route: String ->
            context.startActivity(
                Intent(context, MainActivity::class.java).putExtra("legacy_route", route),
            )
        }
    }

    fun closeDetail() {
        detailShown = false
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

    PredictiveBackHandler(enabled = detailShown && !nestedDetailShown) { events ->
        try {
            events.collect { event ->
                detailPredictiveBackActive = true
                predictiveProgress.snapTo(event.progress)
                val smoothProgress = smootherStep(event.progress)
                detailBackdropIntensity.snapTo(predictiveEffectIntensity(smoothProgress))
                rootLayerDepth.snapTo(1f - smoothProgress)
            }
            coroutineScope {
                launch {
                    predictiveProgress.animateTo(
                        predictiveExitProgress(predictiveBackMaxTranslation.value),
                        tween(PREDICTIVE_SETTLE_DURATION, easing = FastOutSlowInEasing),
                    )
                }
                launch {
                    detailBackdropIntensity.animateTo(
                        0f,
                        tween(PREDICTIVE_SETTLE_DURATION, easing = FastOutSlowInEasing),
                    )
                }
                launch {
                    rootLayerDepth.animateTo(
                        0f,
                        tween(PREDICTIVE_SETTLE_DURATION, easing = FastOutSlowInEasing),
                    )
                }
            }
            detailShown = false
            delay(PREDICTIVE_DISMISS_DURATION.toLong())
            predictiveProgress.snapTo(0f)
            detailPredictiveBackActive = false
        } catch (_: CancellationException) {
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
            detailPredictiveBackActive = false
        }
    }

    PredictiveBackHandler(enabled = nestedDetailShown) { events ->
        try {
            events.collect { event ->
                mediaPredictiveBackActive = true
                mediaPredictiveProgress.snapTo(event.progress)
                val smoothProgress = smootherStep(event.progress)
                mediaBackdropIntensity.snapTo(predictiveEffectIntensity(smoothProgress))
                detailLayerDepth.snapTo(1f - smoothProgress)
            }
            coroutineScope {
                launch {
                    mediaPredictiveProgress.animateTo(
                        predictiveExitProgress(predictiveBackMaxTranslation.value),
                        tween(PREDICTIVE_SETTLE_DURATION, easing = FastOutSlowInEasing),
                    )
                }
                launch {
                    mediaBackdropIntensity.animateTo(
                        0f,
                        tween(PREDICTIVE_SETTLE_DURATION, easing = FastOutSlowInEasing),
                    )
                }
                launch {
                    detailLayerDepth.animateTo(
                        0f,
                        tween(PREDICTIVE_SETTLE_DURATION, easing = FastOutSlowInEasing),
                    )
                }
            }
            mediaShown = false
            materialShown = false
            extensionDetail = null
            delay(PREDICTIVE_DISMISS_DURATION.toLong())
            mediaPredictiveProgress.snapTo(0f)
            mediaPredictiveBackActive = false
        } catch (_: CancellationException) {
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
            mediaPredictiveBackActive = false
        }
    }

    BarBlurHost(
        enabled = blurBars.value,
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
                bottomBar = {
                    AnimatedVisibility(
                        visible = !detailShown &&
                            !detailPredictiveBackActive &&
                            rootLayerDepth.value < EFFECT_VISIBILITY_THRESHOLD,
                        enter = slideInVertically(tween(260)) { it } + fadeIn(tween(180)),
                        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(140)),
                    ) {
                        if (floatingNavigationBar.value) {
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
                                )
                                1 -> AppsPage(
                                    prefs = prefs,
                                    onOpenChannels = { app ->
                                        visibleDetail = null
                                        visibleToastApp = null
                                        visibleChannelApp = app
                                        detailShown = true
                                    },
                                    onOpenToastSettings = { app ->
                                        visibleDetail = null
                                        visibleChannelApp = null
                                        visibleToastApp = app
                                        detailShown = true
                                    },
                                )
                                2 -> SettingsPage(
                                    prefs = prefs,
                                    openLegacy = openLegacy,
                                    onOpenDetail = {
                                        visibleChannelApp = null
                                        visibleToastApp = null
                                        extensionDetail = null
                                        visibleDetail = it
                                        detailShown = true
                                    },
                                )
                                else -> AboutPage(
                                    isActive = pagerState.currentPage == page,
                                    openLegacy = openLegacy,
                                    onOpenBackupRestore = {
                                        visibleChannelApp = null
                                        visibleToastApp = null
                                        visibleDetail = SettingsDetail.BackupRestore
                                        detailShown = true
                                    },
                                    onOpenReferences = {
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
                                    mediaShown = true
                                },
                                openLegacySettings = {
                                    openLegacy(
                                        "/app-settings?package=${Uri.encode(channelApp.packageName)}" +
                                            "&name=${Uri.encode(channelApp.appName)}&mode=notification" +
                                            "&system=${channelApp.isSystem}",
                                    )
                                },
                            )
                        } else if (visibleToastApp != null) {
                            ToastSettingsPage(
                                app = visibleToastApp!!,
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
                                SettingsDetail.Misc -> MiscPage(prefs, openLegacy, ::closeDetail)
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
                        null -> if (materialShown) {
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
}

private fun smootherStep(progress: Float): Float {
    val value = progress.coerceIn(0f, 1f)
    return value * value * value * (value * (value * 6f - 15f) + 10f)
}

private fun predictiveEffectIntensity(smoothProgress: Float): Float =
    1f - smoothProgress * (1f - PREDICTIVE_MIN_EFFECT_INTENSITY)

private fun predictiveTranslationFraction(percent: Long): Float =
    percent.coerceIn(1L, 100L).toFloat() / 100f

private fun predictiveExitProgress(maxTranslationPercent: Long): Float =
    1f / predictiveTranslationFraction(maxTranslationPercent)

private const val LAYER_ENTER_DURATION = 420
private const val LAYER_EXIT_DURATION = 380
private const val PREDICTIVE_SETTLE_DURATION = 420
private const val PREDICTIVE_CANCEL_DURATION = 280
private const val PREDICTIVE_DISMISS_DURATION = 24
private const val PREDICTIVE_MIN_EFFECT_INTENSITY = 0.5f
private const val BACKGROUND_SCALE_REDUCTION = 0.035f
private const val BACKGROUND_PARALLAX = 0.025f
private const val EFFECT_VISIBILITY_THRESHOLD = 0.001f
