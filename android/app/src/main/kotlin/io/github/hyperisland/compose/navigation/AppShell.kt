package io.github.hyperisland.compose.navigation

import android.content.Intent
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.MainActivity
import io.github.hyperisland.R
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.page.AppsPage
import io.github.hyperisland.compose.page.home.OverviewPage
import io.github.hyperisland.compose.page.SettingsDetail
import io.github.hyperisland.compose.page.SettingsPage
import io.github.hyperisland.compose.page.settings.HideBehaviorPage
import io.github.hyperisland.compose.page.settings.IslandOtherPage
import io.github.hyperisland.compose.page.settings.MiscPage
import io.github.hyperisland.compose.page.settings.ThemeSettingsPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Home
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
        )
    }
    val pagerState = rememberPagerState(pageCount = { destinations.size })
    val scope = rememberCoroutineScope()
    var visibleDetail by remember { mutableStateOf<SettingsDetail?>(null) }
    var detailShown by remember { mutableStateOf(false) }
    val predictiveProgress = remember { Animatable(0f) }
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

    PredictiveBackHandler(enabled = detailShown) { events ->
        try {
            events.collect { event -> predictiveProgress.snapTo(event.progress) }
            predictiveProgress.animateTo(1f, tween(120))
            detailShown = false
            delay(280)
            predictiveProgress.snapTo(0f)
        } catch (_: CancellationException) {
            predictiveProgress.animateTo(0f, tween(180))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    destinations.forEachIndexed { index, destination ->
                        NavigationBarItem(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            icon = destination.icon,
                            label = stringResource(destination.title),
                        )
                    }
                }
            },
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding()),
                beyondViewportPageCount = 1,
            ) { page ->
                when (page) {
                    0 -> OverviewPage(prefs)
                    1 -> AppsPage(prefs, openLegacy)
                    else -> SettingsPage(
                        prefs = prefs,
                        openLegacy = openLegacy,
                        onOpenDetail = {
                            visibleDetail = it
                            detailShown = true
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = detailShown,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = size.width * predictiveProgress.value },
            enter = slideInHorizontally(tween(300)) { it } + fadeIn(tween(180)),
            exit = slideOutHorizontally(tween(260)) { it } + fadeOut(tween(160)),
        ) {
            when (visibleDetail) {
                SettingsDetail.Theme -> ThemeSettingsPage(prefs, ::closeDetail)
                SettingsDetail.HideBehavior -> HideBehaviorPage(prefs, ::closeDetail)
                SettingsDetail.Misc -> MiscPage(prefs, openLegacy, ::closeDetail)
                SettingsDetail.Other -> IslandOtherPage(prefs, ::closeDetail)
                null -> Unit
            }
        }
    }
}
