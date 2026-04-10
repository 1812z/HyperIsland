package io.github.hyperisland.ui

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator as MiuixCircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import io.github.hyperisland.data.prefs.PrefKeys
import io.github.hyperisland.data.prefs.SettingsState
import io.github.hyperisland.ui.ai.AiConfigScreen
import io.github.hyperisland.ui.ai.AiConfigViewModel
import io.github.hyperisland.ui.app.AppItem
import io.github.hyperisland.ui.app.AppChannelsScreen
import io.github.hyperisland.ui.app.AppChannelsViewModel
import io.github.hyperisland.ui.app.AppsScreen
import io.github.hyperisland.ui.app.AppsUiState
import io.github.hyperisland.ui.app.AppsViewModel
import io.github.hyperisland.ui.app.ChannelSettingsScreen
import io.github.hyperisland.ui.blacklist.BlacklistScreen
import io.github.hyperisland.ui.blacklist.BlacklistViewModel
import io.github.hyperisland.ui.home.HomeUiState
import io.github.hyperisland.ui.home.HomeViewModel
import io.github.hyperisland.ui.settings.SettingsViewModel
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.AppRecording
import top.yukonga.miuix.kmp.icon.extended.Create
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox
import top.yukonga.miuix.kmp.basic.DropdownImpl as MiuixDropdownImpl
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarDisplayMode as MiuixFloatingNavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn as MiuixListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider as MiuixPopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.SmallTitle as MiuixSmallTitle
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference as MiuixOverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import androidx.compose.runtime.compositionLocalOf

val LocalContentPadding = compositionLocalOf { PaddingValues(0.dp) }

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
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
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

private data class NavigationStyleState(
    val floating: Boolean,
    val floatingMode: MiuixFloatingNavigationBarDisplayMode,
    val floatingBottomOffset: Dp,
    val floatingHorizontalOutSidePadding: Dp,
    val floatingCornerRadius: Dp,
    val floatingShadowElevation: Dp,
    val floatingWindowInsetsPadding: Boolean,
    val floatingContainerWidth: Dp,
    val floatingContainerHeight: Dp,
    val floatingIconSize: Dp,
    val floatingItemHorizontalPadding: Dp,
    val floatingStrokeWidth: Dp,
    val bottomShowDivider: Boolean,
    val bottomWindowInsetsPadding: Boolean,
    val bottomContainerHeight: Dp,
    val bottomIconSize: Dp,
    val bottomItemHorizontalPadding: Dp,
    val bottomShowLabel: Boolean,
    val unselectedAlpha: Float,
)

private enum class TopBarVariant {
    PrimaryHome,
    PrimaryApps,
    PrimarySettings,
    Secondary,
}

private data class TopBarStyleState(
    val variant: TopBarVariant,
    val defaultWindowInsetsPadding: Boolean,
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

private val SettingsFilledIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SettingsFilledCustom",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color(0xFF292D32)),
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(18.9401f, 5.42141f)
            lineTo(13.7701f, 2.43141f)
            curveTo(12.7801f, 1.86141f, 11.2301f, 1.86141f, 10.2401f, 2.43141f)
            lineTo(5.02008f, 5.44141f)
            curveTo(2.95008f, 6.84141f, 2.83008f, 7.05141f, 2.83008f, 9.28141f)
            verticalLineTo(14.7114f)
            curveTo(2.83008f, 16.9414f, 2.95008f, 17.1614f, 5.06008f, 18.5814f)
            lineTo(10.2301f, 21.5714f)
            curveTo(10.7301f, 21.8614f, 11.3701f, 22.0014f, 12.0001f, 22.0014f)
            curveTo(12.6301f, 22.0014f, 13.2701f, 21.8614f, 13.7601f, 21.5714f)
            lineTo(18.9801f, 18.5614f)
            curveTo(21.0501f, 17.1614f, 21.1701f, 16.9514f, 21.1701f, 14.7214f)
            verticalLineTo(9.28141f)
            curveTo(21.1701f, 7.05141f, 21.0501f, 6.84141f, 18.9401f, 5.42141f)
            close()
            moveTo(12.0001f, 15.2514f)
            curveTo(10.2101f, 15.2514f, 8.75008f, 13.7914f, 8.75008f, 12.0014f)
            curveTo(8.75008f, 10.2114f, 10.2101f, 8.75141f, 12.0001f, 8.75141f)
            curveTo(13.7901f, 8.75141f, 15.2501f, 10.2114f, 15.2501f, 12.0014f)
            curveTo(15.2501f, 13.7914f, 13.7901f, 15.2514f, 12.0001f, 15.2514f)
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

private fun routeLevel(route: String?): Int = when {
    route == "home" || route == "apps" || route == "settings" -> 1
    route == "blacklist" || route == "ai_config" || route?.startsWith("app_channels/") == true -> 2
    route?.startsWith("channel_settings/") == true -> 3
    else -> 1
}

private fun resolveMainSwitchDirection(
    fromRoute: String?,
    toRoute: String?,
): AnimatedContentTransitionScope.SlideDirection? {
    val fromMain = mainRouteIndex(fromRoute)
    val toMain = mainRouteIndex(toRoute)
    if (fromMain < 0 || toMain < 0 || fromMain == toMain) return null
    return if (toMain > fromMain) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }
}

private fun resolveRouteForwardSlideDirection(
    fromRoute: String?,
    toRoute: String?,
): AnimatedContentTransitionScope.SlideDirection? {
    if (fromRoute == toRoute) return null

    val fromLevel = routeLevel(fromRoute)
    val toLevel = routeLevel(toRoute)

    return if (toLevel > fromLevel) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else if (toLevel < fromLevel) {
        AnimatedContentTransitionScope.SlideDirection.Right
    } else {
        AnimatedContentTransitionScope.SlideDirection.Left
    }
}

private fun resolveRoutePopSlideDirection(
    fromRoute: String?,
    toRoute: String?,
): AnimatedContentTransitionScope.SlideDirection? {
    if (fromRoute == toRoute) return null

    val fromMain = mainRouteIndex(fromRoute)
    val toMain = mainRouteIndex(toRoute)
    if (fromMain >= 0 && toMain >= 0 && fromMain != toMain) {
        return null
    }

    val fromLevel = routeLevel(fromRoute)
    val toLevel = routeLevel(toRoute)
    return if (toLevel < fromLevel) {
        AnimatedContentTransitionScope.SlideDirection.Right
    } else if (toLevel > fromLevel) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }
}

private const val DOCUMENTATION_URL = "https://hyperisland.1812z.top/"
private const val GITHUB_REPO_URL = "https://github.com/1812z/HyperIsland"
private const val GITHUB_RELEASE_URL = "https://github.com/1812z/HyperIsland/releases/latest"
private const val QQ_GROUP_NUMBER = "1045114341"
private const val DEFAULT_MARQUEE_SPEED = 100
private const val DEFAULT_BIG_ISLAND_MAX_WIDTH = 600
private const val ROUTE_TRANSITION_DURATION_MS = 280
private const val OVERLAY_TRANSITION_DURATION_MS = 320

private fun resolveNightMode(themeMode: String): Int = when (themeMode) {
    "light" -> AppCompatDelegate.MODE_NIGHT_NO
    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
}

@Composable
private fun HyperCeilerNavItem(
    destination: TopLevelDestination,
    selected: Boolean,
    showLabel: Boolean,
    iconSize: Dp,
    itemHorizontalPadding: Dp,
    unselectedAlpha: Float,
    suppressPressEffect: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = if (selected) 1f else unselectedAlpha
    val clickableModifier = if (suppressPressEffect) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(clickableModifier)
            .padding(horizontal = itemHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = destination.label,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        )
        if (showLabel) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = destination.label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HyperCeilerNavigationSwitchBar(
    items: List<TopLevelDestination>,
    selectedIndex: Int,
    style: NavigationStyleState,
    onDestinationClick: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null,
) {
    val isDarkTheme = isSystemInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    AnimatedContent(
        targetState = style.floating,
        label = "nav_style_transition",
        transitionSpec = {
            if (targetState) {
                (
                    fadeIn(animationSpec = tween(durationMillis = 320)) +
                        slideInVertically(animationSpec = tween(durationMillis = 320), initialOffsetY = { it / 3 }) +
                        scaleIn(animationSpec = tween(durationMillis = 320), initialScale = 0.94f)
                    ) togetherWith
                    (
                        fadeOut(animationSpec = tween(durationMillis = 220)) +
                            slideOutVertically(animationSpec = tween(durationMillis = 220), targetOffsetY = { it / 4 }) +
                            scaleOut(animationSpec = tween(durationMillis = 220), targetScale = 0.98f)
                        )
            } else {
                (
                    fadeIn(animationSpec = tween(durationMillis = 280)) +
                        slideInVertically(animationSpec = tween(durationMillis = 280), initialOffsetY = { it / 4 }) +
                        scaleIn(animationSpec = tween(durationMillis = 280), initialScale = 0.98f)
                    ) togetherWith
                    (
                        fadeOut(animationSpec = tween(durationMillis = 220)) +
                            slideOutVertically(animationSpec = tween(durationMillis = 220), targetOffsetY = { it / 3 }) +
                            scaleOut(animationSpec = tween(durationMillis = 220), targetScale = 0.96f)
                        )
            }
        },
    ) { isFloating ->
        if (isFloating) {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(start = style.floatingHorizontalOutSidePadding, end = style.floatingHorizontalOutSidePadding)
                    .padding(
                        bottom = style.floatingBottomOffset +
                            if (style.floatingWindowInsetsPadding) navBottomInset else 0.dp,
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Row(
                    modifier = Modifier
                        .width(style.floatingContainerWidth)
                        .height(style.floatingContainerHeight)
                        .shadow(
                            elevation = style.floatingShadowElevation,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(style.floatingCornerRadius),
                            ambientColor = colorScheme.onSurface.copy(alpha = if (isDarkTheme) 0.34f else 0.10f),
                            spotColor = colorScheme.outline.copy(alpha = if (isDarkTheme) 0.42f else 0.12f),
                            clip = false,
                        )
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(style.floatingCornerRadius))
                        .let { if (backdrop != null) it.textureBlur(backdrop, shape = androidx.compose.foundation.shape.RoundedCornerShape(style.floatingCornerRadius), blurRadiusX = 32f, blurRadiusY = 32f) else it }
                        .background(
                            brush = Brush.verticalGradient(
                                colors = if (isDarkTheme) {
                                    listOf(
                                        colorScheme.surface.copy(alpha = if (backdrop != null) 0.78f else 0.96f),
                                        colorScheme.surfaceVariant.copy(alpha = if (backdrop != null) 0.70f else 0.90f),
                                        colorScheme.surface.copy(alpha = if (backdrop != null) 0.66f else 0.86f),
                                    )
                                } else {
                                    listOf(
                                        Color(0xFFFFFFFF).copy(alpha = if (backdrop != null) 0.65f else 1f),
                                        Color(0xFFFAFAFB).copy(alpha = if (backdrop != null) 0.65f else 1f),
                                        Color(0xFFF3F4F6).copy(alpha = if (backdrop != null) 0.65f else 1f),
                                    )
                                },
                            ),
                        )
                        .drawWithCache {
                            val halfW = size.width / 2f
                            val halfH = size.height / 2f
                            val corner = style.floatingCornerRadius.toPx()
                            val outerStroke = (style.floatingStrokeWidth * 1.10f).toPx()
                            val innerStroke = (style.floatingStrokeWidth * 0.58f).toPx()
                            // HyperCeiler 风格：高光从左上打向右下，角度约 34°。
                            val strokeAngleRad = Math.toRadians(34.0)
                            val dx = (cos(strokeAngleRad) * halfW).toFloat()
                            val dy = (sin(strokeAngleRad) * halfH).toFloat()
                            val outerStrokeBrush = if (isDarkTheme) {
                                Brush.linearGradient(
                                    colors = listOf(
                                        colorScheme.outline.copy(alpha = 0.44f),
                                        colorScheme.onSurface.copy(alpha = 0.22f),
                                        colorScheme.surfaceVariant.copy(alpha = 0.26f),
                                        colorScheme.outline.copy(alpha = 0.34f),
                                    ),
                                    start = Offset(halfW - dx, halfH - dy),
                                    end = Offset(halfW + dx, halfH + dy),
                                )
                            } else {
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.97f),
                                        Color(0xFFF1F2F5).copy(alpha = 0.78f),
                                        Color(0xFFE2E4E9).copy(alpha = 0.62f),
                                        Color.White.copy(alpha = 0.93f),
                                    ),
                                    start = Offset(halfW - dx, halfH - dy),
                                    end = Offset(halfW + dx, halfH + dy),
                                )
                            }
                            val innerStrokeBrush = if (isDarkTheme) {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        colorScheme.onSurface.copy(alpha = 0.20f),
                                        colorScheme.outline.copy(alpha = 0.10f),
                                        Color.Transparent,
                                    ),
                                    startY = 0f,
                                    endY = size.height * 0.70f,
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.84f),
                                        Color.White.copy(alpha = 0.28f),
                                        Color.Transparent,
                                    ),
                                    startY = 0f,
                                    endY = size.height * 0.70f,
                                )
                            }
                            onDrawWithContent {
                                drawContent()
                                drawRoundRect(
                                    brush = outerStrokeBrush,
                                    size = Size(size.width, size.height),
                                    cornerRadius = CornerRadius(corner, corner),
                                    style = Stroke(width = outerStroke),
                                )
                                drawRoundRect(
                                    brush = innerStrokeBrush,
                                    size = Size(size.width, size.height * 0.78f),
                                    cornerRadius = CornerRadius(corner, corner),
                                    style = Stroke(width = innerStroke),
                                )
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, destination ->
                        HyperCeilerNavItem(
                            destination = destination,
                            selected = index == selectedIndex,
                            showLabel = style.floatingMode != MiuixFloatingNavigationBarDisplayMode.IconOnly,
                            iconSize = style.floatingIconSize,
                            itemHorizontalPadding = style.floatingItemHorizontalPadding,
                            unselectedAlpha = style.unselectedAlpha,
                            suppressPressEffect = true,
                            onClick = { onDestinationClick(destination) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .let { if (backdrop != null) it.textureBlur(backdrop, shape = androidx.compose.ui.graphics.RectangleShape, blurRadiusX = 32f, blurRadiusY = 32f) else it }
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = if (backdrop != null) 0.7f else 1f))
            ) {
                if (style.bottomShowDivider) {
                    HorizontalDivider()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (style.bottomWindowInsetsPadding) navBottomInset else 0.dp)
                        .height(style.bottomContainerHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, destination ->
                        HyperCeilerNavItem(
                            destination = destination,
                            selected = index == selectedIndex,
                            showLabel = style.bottomShowLabel,
                            iconSize = style.bottomIconSize,
                            itemHorizontalPadding = style.bottomItemHorizontalPadding,
                            unselectedAlpha = style.unselectedAlpha,
                            suppressPressEffect = false,
                            onClick = { onDestinationClick(destination) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

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

@Composable
private fun primaryCardModifier(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
): Modifier {
    val isDarkTheme = isSystemInDarkTheme()
    return modifier
        .clip(shape)
        .then(
            if (isDarkTheme) {
                Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.34f),
                    shape,
                )
            } else {
                Modifier
            },
        )
}

@Composable
private fun OverlayPopupMenuContainer(content: @Composable () -> Unit) {
    val isDarkTheme = isSystemInDarkTheme()
    val containerShape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .clip(containerShape)
            .background(
                MaterialTheme.colorScheme.surface.copy(
                    alpha = if (isDarkTheme) 0.95f else 0.98f,
                ),
            )
            .then(
                if (isDarkTheme) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f),
                        shape = containerShape,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        MiuixListPopupColumn {
            content()
        }
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
    val navigationEventDispatcherOwner = rememberNavigationEventDispatcherOwner(parent = null)
    val backdrop = rememberLayerBackdrop()
    val context = LocalContext.current
    val homeVm: HomeViewModel = viewModel()
    val appsVm: AppsViewModel = viewModel()
    val blacklistVm: BlacklistViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()
    val settingsState by settingsVm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(settingsState.themeMode) {
        val targetMode = resolveNightMode(settingsState.themeMode)
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode)
        }
    }
    val appsState by appsVm.uiState.collectAsStateWithLifecycle()
    val blacklistState by blacklistVm.uiState.collectAsStateWithLifecycle()
    var showRestartDialog by remember { mutableStateOf(false) }
    var showSponsorDialog by remember { mutableStateOf(false) }
    var showAppsMenu by remember { mutableStateOf(false) }
    var showBlacklistMenu by remember { mutableStateOf(false) }
    var appsSelectionMode by remember { mutableStateOf(false) }
    var appsSelectionRequestId by remember { mutableStateOf(0) }
    var appsExitSelectionRequestId by remember { mutableStateOf(0) }
    var appsEnableSelectedRequestId by remember { mutableStateOf(0) }
    var appsDisableSelectedRequestId by remember { mutableStateOf(0) }
    var appsSelectEnabledRequestId by remember { mutableStateOf(0) }
    var appsBatchSelectedRequestId by remember { mutableStateOf(0) }
    var appsEnableAllRequestId by remember { mutableStateOf(0) }
    var appsDisableAllRequestId by remember { mutableStateOf(0) }
    var appsBatchRequestId by remember { mutableStateOf(0) }
    var showAppChannelsMenu by remember { mutableStateOf(false) }
    var appChannelsEnableAllRequestId by remember { mutableStateOf(0) }
    var appChannelsBatchRequestId by remember { mutableStateOf(0) }
    val popupShowing = showAppsMenu || showAppChannelsMenu || showBlacklistMenu

    val items = listOf(
        TopLevelDestination("home", "主页", HomeFilledIcon),
        TopLevelDestination("apps", "应用", MiuixIcons.Regular.All),
        TopLevelDestination("settings", "设置", SettingsFilledIcon),
    )
    val capsuleNavStyleState = remember {
        NavigationStyleState(
            floating = true,
            floatingMode = MiuixFloatingNavigationBarDisplayMode.IconOnly,
            floatingBottomOffset = 12.dp,
            floatingHorizontalOutSidePadding = 24.dp,
            floatingCornerRadius = 26.dp,
            floatingShadowElevation = 4.dp,
            floatingWindowInsetsPadding = true,
            floatingContainerWidth = 220.dp,
            floatingContainerHeight = 56.dp,
            floatingIconSize = 22.dp,
            floatingItemHorizontalPadding = 16.dp,
            floatingStrokeWidth = 1.6.dp,
            bottomShowDivider = false,
            bottomWindowInsetsPadding = false,
            bottomContainerHeight = 0.dp,
            bottomIconSize = 0.dp,
            bottomItemHorizontalPadding = 0.dp,
            bottomShowLabel = false,
            unselectedAlpha = 0.4f,
        )
    }
    val bottomNavStyleState = remember {
        NavigationStyleState(
            floating = false,
            floatingMode = MiuixFloatingNavigationBarDisplayMode.IconOnly,
            floatingBottomOffset = 0.dp,
            floatingHorizontalOutSidePadding = 0.dp,
            floatingCornerRadius = 0.dp,
            floatingShadowElevation = 0.dp,
            floatingWindowInsetsPadding = false,
            floatingContainerWidth = 0.dp,
            floatingContainerHeight = 0.dp,
            floatingIconSize = 0.dp,
            floatingItemHorizontalPadding = 0.dp,
            floatingStrokeWidth = 0.dp,
            bottomShowDivider = false,
            bottomWindowInsetsPadding = false,
            bottomContainerHeight = 56.dp,
            bottomIconSize = 22.dp,
            bottomItemHorizontalPadding = 0.dp,
            bottomShowLabel = true,
            unselectedAlpha = 0.4f,
        )
    }
    val activeNavStyleState = if (settingsState.useFloatingNavigationBar) {
                        capsuleNavStyleState
    } else {
        bottomNavStyleState
    }

    var isAppsSearchExpanded by remember { mutableStateOf(false) }
    var isBlacklistSearchExpanded by remember { mutableStateOf(false) }
    var appsSearchFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var blacklistSearchFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val appsSearchFocusRequester = remember { FocusRequester() }
    val blacklistSearchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(appsState.query, isAppsSearchExpanded) {
        if (!isAppsSearchExpanded) {
            appsSearchFieldValue = TextFieldValue(
                text = appsState.query,
                selection = TextRange(appsState.query.length),
            )
        }
    }
    LaunchedEffect(blacklistState.query, isBlacklistSearchExpanded) {
        if (!isBlacklistSearchExpanded) {
            blacklistSearchFieldValue = TextFieldValue(
                text = blacklistState.query,
                selection = TextRange(blacklistState.query.length),
            )
        }
    }

    LaunchedEffect(isAppsSearchExpanded) {
        if (isAppsSearchExpanded) {
            appsSearchFieldValue = appsSearchFieldValue.copy(
                selection = TextRange(appsSearchFieldValue.text.length),
            )
            delay(120)
            appsSearchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    LaunchedEffect(isBlacklistSearchExpanded) {
        if (isBlacklistSearchExpanded) {
            blacklistSearchFieldValue = blacklistSearchFieldValue.copy(
                selection = TextRange(blacklistSearchFieldValue.text.length),
            )
            delay(120)
            blacklistSearchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val appListState = rememberLazyListState()

    val scope = rememberCoroutineScope()
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val onPrimaryDestinationClick: (TopLevelDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }
    val topBarTitle = if (currentRoute?.startsWith("channel_settings/") == true) {
        Uri.decode(backStackEntry?.arguments?.getString("channelName").orEmpty())
            .ifBlank { "渠道详情" }
    } else {
        routeTitle(currentRoute)
    }
    val isSecondaryRoute = currentRoute?.startsWith("app_channels/") == true ||
        currentRoute?.startsWith("channel_settings/") == true ||
        currentRoute == "blacklist" ||
        currentRoute == "ai_config"
    val secondaryTopBarStyleState = remember {
        TopBarStyleState(
            variant = TopBarVariant.Secondary,
            defaultWindowInsetsPadding = false,
        )
    }
    val homeTopBarStyleState = remember {
        TopBarStyleState(
            variant = TopBarVariant.PrimaryHome,
            defaultWindowInsetsPadding = false,
        )
    }
    val appsTopBarStyleState = remember {
        TopBarStyleState(
            variant = TopBarVariant.PrimaryApps,
            defaultWindowInsetsPadding = false,
        )
    }
    val settingsTopBarStyleState = remember {
        TopBarStyleState(
            variant = TopBarVariant.PrimarySettings,
            defaultWindowInsetsPadding = false,
        )
    }
    val activeTopBarStyleState = when {
        isSecondaryRoute -> secondaryTopBarStyleState
        currentRoute == "apps" -> appsTopBarStyleState
        currentRoute == "settings" -> settingsTopBarStyleState
        else -> homeTopBarStyleState
    }
    fun dismissTransientUi(): Boolean {
        return when {
            showAppsMenu -> {
                showAppsMenu = false
                true
            }
            showAppChannelsMenu -> {
                showAppChannelsMenu = false
                true
            }
            showBlacklistMenu -> {
                showBlacklistMenu = false
                true
            }
            showRestartDialog -> {
                showRestartDialog = false
                true
            }
            showSponsorDialog -> {
                showSponsorDialog = false
                true
            }
            isAppsSearchExpanded -> {
                isAppsSearchExpanded = false
                appsVm.setQuery("")
                true
            }
            isBlacklistSearchExpanded -> {
                isBlacklistSearchExpanded = false
                blacklistVm.setQuery("")
                true
            }
            else -> false
        }
    }
    val shouldHandleBack = showAppsMenu || showAppChannelsMenu || showBlacklistMenu || showRestartDialog || showSponsorDialog || isAppsSearchExpanded || isBlacklistSearchExpanded
    val homeScrollBehavior = MiuixScrollBehavior(
        state = rememberTopAppBarState(),
        canScroll = { !popupShowing },
    )
    val appsScrollBehavior = MiuixScrollBehavior(
        state = rememberTopAppBarState(),
        canScroll = { !popupShowing },
    )
    val settingsScrollBehavior = MiuixScrollBehavior(
        state = rememberTopAppBarState(),
        canScroll = { !popupShowing },
    )
    val topBarOwnerRoute = when {
        currentRoute?.startsWith("app_channels/") == true ||
            currentRoute?.startsWith("channel_settings/") == true -> "apps"
        currentRoute == "blacklist" || currentRoute == "ai_config" -> "settings"
        else -> currentRoute
    }
    val activeTopBarScrollBehavior = when (topBarOwnerRoute) {
        "apps" -> appsScrollBehavior
        "settings" -> settingsScrollBehavior
        else -> homeScrollBehavior
    }
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    BackHandler(enabled = shouldHandleBack) {
        dismissTransientUi()
    }
    val layoutDirection = LocalLayoutDirection.current
    val insets = WindowInsets.navigationBars.asPaddingValues()
    val isAppsRoute = currentRoute == "apps"
    val topBarCollapseProgress by remember(
        currentRoute,
        activeTopBarScrollBehavior.state,
    ) {
        derivedStateOf {
            activeTopBarScrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
        }
    }
    val isAppsLargeTitleExpanded by remember(currentRoute, appsScrollBehavior.state) {
        derivedStateOf {
            currentRoute == "apps" && appsScrollBehavior.state.collapsedFraction < 0.98f
        }
    }
    
    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner.provides(navigationEventDispatcherOwner),
    ) {
        MiuixScaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                val topBarScrollBehavior = activeTopBarScrollBehavior
                
                val isAppsRoute = currentRoute == "apps"
                val isBlacklistRoute = currentRoute == "blacklist"
                val showTopBarExtraContent =
                    (isAppsRoute && (appsSelectionMode || isAppsSearchExpanded)) ||
                    isBlacklistRoute
                val searchExpandTransitionMs = 260
                val collapseProgress = topBarCollapseProgress
                val baseExpandedAlpha = when {
                    isAppsRoute && showTopBarExtraContent -> 0.72f
                    activeTopBarStyleState.variant == TopBarVariant.PrimaryHome -> 0.62f
                    else -> 0.68f
                }
                val baseCollapsedAlpha = when {
                    activeTopBarStyleState.variant == TopBarVariant.Secondary -> 0.88f
                    isAppsRoute && showTopBarExtraContent -> 0.9f
                    else -> 0.86f
                }
                val topBarSurfaceAlpha = androidx.compose.ui.util.lerp(
                    baseExpandedAlpha,
                    baseCollapsedAlpha,
                    collapseProgress,
                )
                val topBarBlurRadius = androidx.compose.ui.util.lerp(
                    16f,
                    if (activeTopBarStyleState.variant == TopBarVariant.Secondary) 22f else 26f,
                    collapseProgress,
                )
                val topBarBackgroundColor = lerp(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                    MaterialTheme.colorScheme.surface.copy(alpha = topBarSurfaceAlpha),
                    collapseProgress,
                )
                val shouldBlurTopBar = collapseProgress > 0.02f
                
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .let {
                                if (backdrop != null) {
                                    it.textureBlur(
                                        backdrop,
                                        shape = RectangleShape,
                                        blurRadiusX = topBarBlurRadius,
                                        blurRadiusY = topBarBlurRadius,
                                    )
                                } else it
                            }
                            .alpha(if (shouldBlurTopBar) 1f else 0f)
                            .background(topBarBackgroundColor)
                    )
                    Column(modifier = Modifier.fillMaxWidth()) {
                    MiuixTopAppBar(
                        title = topBarTitle,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Transparent,
                        navigationIcon = {
                            when (activeTopBarStyleState.variant) {
                                TopBarVariant.Secondary -> {
                                    MiuixIconButton(onClick = {
                                        backDispatcher?.onBackPressed() ?: run {
                                            val dismissed = dismissTransientUi()
                                            if (!dismissed) {
                                                navController.popBackStack()
                                            }
                                        }
                                    }) {
                                        Icon(
                                            imageVector = MiuixIcons.Basic.ArrowRight,
                                            contentDescription = "返回",
                                            modifier = Modifier.rotate(180f),
                                        )
                                    }
                                }

                                TopBarVariant.PrimaryApps -> {
                                    if (appsSelectionMode) {
                                        MiuixIconButton(onClick = { appsExitSelectionRequestId += 1 }) {
                                            FaIcon(
                                                glyph = FaGlyph.Times,
                                                contentDescription = "退出多选",
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(40.dp))
                                    }
                                }

                                else -> Spacer(modifier = Modifier.size(40.dp))
                            }
                        },
                        actions = {
                            when (activeTopBarStyleState.variant) {
                                TopBarVariant.Secondary -> {
                                    if (currentRoute?.startsWith("app_channels/") == true) {
                                        Box {
                                            BackHandler(enabled = showAppChannelsMenu) {
                                                showAppChannelsMenu = false
                                            }
                                            MiuixIconButton(onClick = { showAppChannelsMenu = true }) {
                                                Icon(
                                                    imageVector = MiuixIcons.Regular.MoreCircle,
                                                    contentDescription = "渠道页更多操作",
                                                )
                                            }
                                            OverlayListPopup(
                                                show = showAppChannelsMenu,
                                                alignment = MiuixPopupPositionProvider.Align.End,
                                                onDismissRequest = { showAppChannelsMenu = false },
                                                onDismissFinished = {},
                                            ) {
                                                val menuItems = listOf(
                                                    "启用全部渠道" to {
                                                        showAppChannelsMenu = false
                                                        appChannelsEnableAllRequestId += 1
                                                    },
                                                    "批量设置渠道配置" to {
                                                        showAppChannelsMenu = false
                                                        appChannelsBatchRequestId += 1
                                                    },
                                                )
                                                OverlayPopupMenuContainer {
                                                    menuItems.forEachIndexed { index, (title, action) ->
                                                        MiuixDropdownImpl(
                                                            text = title,
                                                            optionSize = menuItems.size,
                                                            isSelected = false,
                                                            onSelectedIndexChange = { action() },
                                                            index = index,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else if (currentRoute == "blacklist") {
                                        MiuixIconButton(
                                            onClick = {
                                                if (!isBlacklistSearchExpanded) {
                                                    blacklistSearchFieldValue = TextFieldValue(
                                                        text = blacklistState.query,
                                                        selection = TextRange(blacklistState.query.length),
                                                    )
                                                }
                                                isBlacklistSearchExpanded = !isBlacklistSearchExpanded
                                            },
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.Regular.Search,
                                                contentDescription = "搜索",
                                            )
                                        }
                                        Box {
                                            BackHandler(enabled = showBlacklistMenu) {
                                                showBlacklistMenu = false
                                            }
                                            MiuixIconButton(onClick = { showBlacklistMenu = true }) {
                                                Icon(
                                                    imageVector = MiuixIcons.Regular.MoreCircle,
                                                    contentDescription = "黑名单页更多操作",
                                                )
                                            }
                                            OverlayListPopup(
                                                show = showBlacklistMenu,
                                                alignment = MiuixPopupPositionProvider.Align.End,
                                                onDismissRequest = { showBlacklistMenu = false },
                                                onDismissFinished = {},
                                            ) {
                                                val menuItems = listOf(
                                                    "游戏预设" to {
                                                        showBlacklistMenu = false
                                                        blacklistVm.applyGamePreset()
                                                    },
                                                    "全部加入" to {
                                                        showBlacklistMenu = false
                                                        blacklistVm.enableAllVisible()
                                                    },
                                                    "全部移除" to {
                                                        showBlacklistMenu = false
                                                        blacklistVm.disableAllVisible()
                                                    },
                                                    (if (blacklistState.showSystemApps) "隐藏系统应用" else "显示系统应用") to {
                                                        showBlacklistMenu = false
                                                        blacklistVm.setShowSystemApps(!blacklistState.showSystemApps)
                                                    },
                                                    "刷新" to {
                                                        showBlacklistMenu = false
                                                        blacklistVm.refresh()
                                                    },
                                                )
                                                OverlayPopupMenuContainer {
                                                    menuItems.forEachIndexed { index, (title, action) ->
                                                        MiuixDropdownImpl(
                                                            text = title,
                                                            optionSize = menuItems.size,
                                                            isSelected = false,
                                                            onSelectedIndexChange = { action() },
                                                            index = index,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                TopBarVariant.PrimaryHome -> {
                                    MiuixIconButton(onClick = { openExternalUrl(context, DOCUMENTATION_URL) }) {
                                        Icon(
                                            imageVector = MiuixIcons.Regular.Info,
                                            contentDescription = "文档",
                                        )
                                    }
                                    MiuixIconButton(onClick = { showSponsorDialog = true }) {
                                        Icon(
                                            imageVector = MiuixIcons.Regular.Create,
                                            contentDescription = "赞助",
                                        )
                                    }
                                    MiuixIconButton(onClick = { showRestartDialog = true }) {
                                        Icon(
                                            imageVector = MiuixIcons.Regular.Refresh,
                                            contentDescription = "重启作用域",
                                        )
                                    }
                                }

                                TopBarVariant.PrimaryApps -> {
                                    MiuixIconButton(
                                        onClick = {
                                            if (!isAppsSearchExpanded) {
                                                appsSearchFieldValue = TextFieldValue(
                                                    text = appsState.query,
                                                    selection = TextRange(appsState.query.length),
                                                )
                                            }
                                            isAppsSearchExpanded = !isAppsSearchExpanded
                                        },
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Regular.Search,
                                            contentDescription = "搜索",
                                        )
                                    }
                                    if (!appsSelectionMode) {
                                        MiuixIconButton(onClick = { appsSelectionRequestId += 1 }) {
                                            Icon(
                                                imageVector = MiuixIcons.Regular.SelectAll,
                                                contentDescription = "进入多选",
                                            )
                                        }
                                    }
                                    Box {
                                        BackHandler(enabled = showAppsMenu) {
                                            showAppsMenu = false
                                        }
                                        MiuixIconButton(onClick = { showAppsMenu = true }) {
                                            Icon(
                                                imageVector = MiuixIcons.Regular.MoreCircle,
                                                contentDescription = "更多操作",
                                            )
                                        }
                                        OverlayListPopup(
                                            show = showAppsMenu,
                                            alignment = MiuixPopupPositionProvider.Align.End,
                                            onDismissRequest = { showAppsMenu = false },
                                            onDismissFinished = {},
                                        ) {
                                            val menuItems = if (appsSelectionMode) {
                                                listOf(
                                                    (if (appsState.showSystemApps) "隐藏系统应用" else "显示系统应用") to {
                                                        showAppsMenu = false
                                                        appsVm.setShowSystemApps(!appsState.showSystemApps)
                                                    },
                                                    "开启已选" to {
                                                        showAppsMenu = false
                                                        appsEnableSelectedRequestId += 1
                                                    },
                                                    "关闭已选" to {
                                                        showAppsMenu = false
                                                        appsDisableSelectedRequestId += 1
                                                    },
                                                    "选中已启用" to {
                                                        showAppsMenu = false
                                                        appsSelectEnabledRequestId += 1
                                                    },
                                                    "批量设置渠道配置" to {
                                                        showAppsMenu = false
                                                        appsBatchSelectedRequestId += 1
                                                    },
                                                )
                                            } else {
                                                listOf(
                                                    (if (appsState.showSystemApps) "隐藏系统应用" else "显示系统应用") to {
                                                        showAppsMenu = false
                                                        appsVm.setShowSystemApps(!appsState.showSystemApps)
                                                    },
                                                    "一键开启全部" to {
                                                        showAppsMenu = false
                                                        appsEnableAllRequestId += 1
                                                    },
                                                    "一键关闭全部" to {
                                                        showAppsMenu = false
                                                        appsDisableAllRequestId += 1
                                                    },
                                                    "刷新" to {
                                                        showAppsMenu = false
                                                        appsVm.refresh()
                                                    },
                                                )
                                            }
                                            OverlayPopupMenuContainer {
                                                menuItems.forEachIndexed { index, (title, action) ->
                                                    MiuixDropdownImpl(
                                                        text = title,
                                                        optionSize = menuItems.size,
                                                        isSelected = false,
                                                        onSelectedIndexChange = { action() },
                                                        index = index,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                TopBarVariant.PrimarySettings -> Unit
                            }
                        },
                        scrollBehavior = topBarScrollBehavior,
                        defaultWindowInsetsPadding = activeTopBarStyleState.defaultWindowInsetsPadding,
                    )
                    
                    if (isAppsRoute) {
                        if (isAppsSearchExpanded || appsSelectionMode) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(animationSpec = tween(durationMillis = searchExpandTransitionMs)),
                            ) {
                                AnimatedVisibility(
                                    visible = isAppsSearchExpanded,
                                    enter = fadeIn(animationSpec = tween(durationMillis = searchExpandTransitionMs)) +
                                        expandVertically(animationSpec = tween(durationMillis = searchExpandTransitionMs)),
                                    exit = fadeOut(animationSpec = tween(durationMillis = 180)) +
                                        shrinkVertically(animationSpec = tween(durationMillis = 220)),
                                    label = "apps_search_bar_visibility",
                                ) {
                                    MiuixTextField(
                                        value = appsSearchFieldValue,
                                        onValueChange = {
                                            appsSearchFieldValue = it
                                            appsVm.setQuery(it.text)
                                        },
                                        label = "搜索应用 / 包名",
                                        useLabelAsPlaceholder = true,
                                        modifier = Modifier
                                            .focusRequester(appsSearchFocusRequester)
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }

                                AnimatedVisibility(
                                    visible = appsSelectionMode,
                                    enter = fadeIn(animationSpec = tween(durationMillis = searchExpandTransitionMs)) +
                                        expandVertically(animationSpec = tween(durationMillis = searchExpandTransitionMs)),
                                    exit = fadeOut(animationSpec = tween(durationMillis = 180)) +
                                        shrinkVertically(animationSpec = tween(durationMillis = 220)),
                                    label = "apps_selection_info_visibility",
                                ) {
                                    val visiblePackages = appsState.filteredApps.map { it.packageName }.toSet()
                                    val allVisibleSelected = visiblePackages.isNotEmpty() && visiblePackages.all { appsState.selectedPackages.contains(it) }
                                    val selectAllState = if (allVisibleSelected) ToggleableState.On else if (appsState.selectedPackages.isNotEmpty()) ToggleableState.Indeterminate else ToggleableState.Off
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("已选择 ${appsState.selectedPackages.size} 项", style = MaterialTheme.typography.bodySmall)
                                        MiuixCheckbox(
                                            state = selectAllState,
                                            onClick = {
                                                if (allVisibleSelected) {
                                                    appsVm.setSelectedPackages(appsState.selectedPackages - visiblePackages)
                                                } else {
                                                    appsVm.setSelectedPackages(appsState.selectedPackages + visiblePackages)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isBlacklistRoute) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(animationSpec = tween(durationMillis = searchExpandTransitionMs)),
                        ) {
                            AnimatedVisibility(
                                visible = isBlacklistSearchExpanded,
                                enter = fadeIn(animationSpec = tween(durationMillis = searchExpandTransitionMs)) +
                                    expandVertically(animationSpec = tween(durationMillis = searchExpandTransitionMs)),
                                exit = fadeOut(animationSpec = tween(durationMillis = 180)) +
                                    shrinkVertically(animationSpec = tween(durationMillis = 220)),
                                label = "blacklist_search_bar_visibility",
                            ) {
                                MiuixTextField(
                                    value = blacklistSearchFieldValue,
                                    onValueChange = {
                                        blacklistSearchFieldValue = it
                                        blacklistVm.setQuery(it.text)
                                    },
                                    label = "搜索应用 / 包名",
                                    useLabelAsPlaceholder = true,
                                    modifier = Modifier
                                        .focusRequester(blacklistSearchFocusRequester)
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            Text(
                                text = "已加入黑名单 ${blacklistState.blacklistedPackages.size} 项",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
                }
            },
            bottomBar = {}, // Handle bottom bar as overlay so list draws beneath it for `.textureBlur`
        ) { innerPadding ->
            val bottomOverlayPaddingTarget = when {
                isSecondaryRoute -> 0.dp
                activeNavStyleState.floating -> {
                    activeNavStyleState.floatingBottomOffset +
                        activeNavStyleState.floatingContainerHeight +
                        if (activeNavStyleState.floatingWindowInsetsPadding) {
                            insets.calculateBottomPadding()
                        } else {
                            0.dp
                        }
                }
                else -> {
                    activeNavStyleState.bottomContainerHeight +
                        if (activeNavStyleState.bottomWindowInsetsPadding) {
                            insets.calculateBottomPadding()
                        } else {
                            0.dp
                        }
                }
            }
            val bottomOverlayPadding by animateDpAsState(
                targetValue = bottomOverlayPaddingTarget,
                animationSpec = tween(durationMillis = 320),
                label = "bottom_overlay_padding_transition",
            )
            val combinedPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + bottomOverlayPadding
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Background content capturing box
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(backdrop)
                        .consumeWindowInsets(combinedPadding),
                ) {
                    CompositionLocalProvider(LocalContentPadding provides combinedPadding) {
                        NavHost(
                            navController = navController,
                            startDestination = "home",
                            enterTransition = {
                                val fromRoute = initialState.destination.route
                                val toRoute = targetState.destination.route
                                val fromLevel = routeLevel(fromRoute)
                                val toLevel = routeLevel(toRoute)
                                val mainSwitchDirection = resolveMainSwitchDirection(fromRoute, toRoute)
                                when {
                                    mainSwitchDirection != null -> {
                                        slideIntoContainer(
                                            mainSwitchDirection,
                                            animationSpec = tween(
                                                durationMillis = ROUTE_TRANSITION_DURATION_MS,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        ) + fadeIn(
                                            animationSpec = tween(
                                                durationMillis = ROUTE_TRANSITION_DURATION_MS,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )
                                    }
                                    toLevel > fromLevel -> {
                                        // HyperCeiler-like push: new page overlays from right.
                                        slideIntoContainer(
                                            AnimatedContentTransitionScope.SlideDirection.Left,
                                            animationSpec = tween(
                                                durationMillis = OVERLAY_TRANSITION_DURATION_MS,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        ) + fadeIn(
                                            animationSpec = tween(
                                                durationMillis = OVERLAY_TRANSITION_DURATION_MS,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )
                                    }
                                    else -> {
                                        val direction = resolveRouteForwardSlideDirection(fromRoute, toRoute)
                                        if (direction != null) {
                                            slideIntoContainer(
                                                direction,
                                                animationSpec = tween(
                                                    durationMillis = ROUTE_TRANSITION_DURATION_MS,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                            )
                                        } else {
                                            EnterTransition.None
                                        }
                                    }
                                }
                            },
                            exitTransition = {
                                val fromRoute = initialState.destination.route
                                val toRoute = targetState.destination.route
                                val fromLevel = routeLevel(fromRoute)
                                val toLevel = routeLevel(toRoute)
                                val mainSwitchDirection = resolveMainSwitchDirection(fromRoute, toRoute)
                                when {
                                    mainSwitchDirection != null -> {
                                        slideOutOfContainer(
                                            mainSwitchDirection,
                                            animationSpec = tween(
                                                durationMillis = ROUTE_TRANSITION_DURATION_MS,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        ) + fadeOut(
                                            animationSpec = tween(
                                                durationMillis = ROUTE_TRANSITION_DURATION_MS,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )
                                    }
                                    toLevel > fromLevel -> {
                                        // Keep background page as an underlay while pushing.
                                        fadeOut(
                                            animationSpec = tween(
                                                durationMillis = OVERLAY_TRANSITION_DURATION_MS,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        ) + scaleOut(
                                            targetScale = 0.97f,
                                            animationSpec = tween(
                                                durationMillis = OVERLAY_TRANSITION_DURATION_MS,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )
                                    }
                                    else -> {
                                        val direction = resolveRouteForwardSlideDirection(fromRoute, toRoute)
                                        if (direction != null) {
                                            slideOutOfContainer(
                                                direction,
                                                animationSpec = tween(
                                                    durationMillis = ROUTE_TRANSITION_DURATION_MS,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                            )
                                        } else {
                                            ExitTransition.None
                                        }
                                    }
                                }
                            },
                            popEnterTransition = {
                                val fromRoute = initialState.destination.route
                                val toRoute = targetState.destination.route
                                val fromLevel = routeLevel(fromRoute)
                                val toLevel = routeLevel(toRoute)
                                val mainSwitchDirection = resolveMainSwitchDirection(fromRoute, toRoute)
                                when {
                                    mainSwitchDirection != null -> {
                                        slideIntoContainer(
                                            mainSwitchDirection,
                                            animationSpec = tween(
                                                durationMillis = ROUTE_TRANSITION_DURATION_MS,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        ) + fadeIn(
                                            animationSpec = tween(
                                                durationMillis = ROUTE_TRANSITION_DURATION_MS,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )
                                    }
                                    toLevel < fromLevel -> {
                                        // Underlay page re-appears from the back.
                                        fadeIn(
                                            animationSpec = tween(
                                                durationMillis = OVERLAY_TRANSITION_DURATION_MS,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        ) + scaleIn(
                                            initialScale = 0.97f,
                                            animationSpec = tween(
                                                durationMillis = OVERLAY_TRANSITION_DURATION_MS,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )
                                    }
                                    else -> {
                                        val direction = resolveRoutePopSlideDirection(fromRoute, toRoute)
                                        if (direction != null) {
                                            slideIntoContainer(
                                                direction,
                                                animationSpec = tween(
                                                    durationMillis = ROUTE_TRANSITION_DURATION_MS,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                            )
                                        } else {
                                            EnterTransition.None
                                        }
                                    }
                                }
                            },
                            popExitTransition = {
                                val fromRoute = initialState.destination.route
                                val toRoute = targetState.destination.route
                                val fromLevel = routeLevel(fromRoute)
                                val toLevel = routeLevel(toRoute)
                                val mainSwitchDirection = resolveMainSwitchDirection(fromRoute, toRoute)
                                when {
                                    mainSwitchDirection != null -> {
                                        slideOutOfContainer(
                                            mainSwitchDirection,
                                            animationSpec = tween(
                                                durationMillis = ROUTE_TRANSITION_DURATION_MS,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        ) + fadeOut(
                                            animationSpec = tween(
                                                durationMillis = ROUTE_TRANSITION_DURATION_MS,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )
                                    }
                                    toLevel < fromLevel -> {
                                        // Top overlay page leaves to right on back.
                                        slideOutOfContainer(
                                            AnimatedContentTransitionScope.SlideDirection.Right,
                                            animationSpec = tween(
                                                durationMillis = OVERLAY_TRANSITION_DURATION_MS,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        ) + fadeOut(
                                            animationSpec = tween(
                                                durationMillis = OVERLAY_TRANSITION_DURATION_MS,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )
                                    }
                                    else -> {
                                        val direction = resolveRoutePopSlideDirection(fromRoute, toRoute)
                                        if (direction != null) {
                                            slideOutOfContainer(
                                                direction,
                                                animationSpec = tween(
                                                    durationMillis = ROUTE_TRANSITION_DURATION_MS,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                            )
                                        } else {
                                            ExitTransition.None
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            composable("home") {
                                val uiState by homeVm.uiState.collectAsStateWithLifecycle()
                                LaunchedEffect(Unit) {
                                    homeVm.events.collect {
                                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                HomeScreen(
                                    uiState = uiState,
                                    onRefresh = homeVm::refreshStatus,
                                    onSendTest = homeVm::sendTest,
                                    modifier = Modifier.nestedScroll(homeScrollBehavior.nestedScrollConnection),
                                )
                            }
                            composable("apps") {
                                LaunchedEffect(Unit) {
                                    appsVm.events.collect {
                                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                AppsScreen(
                                    state = appsState,
                                    onRefresh = appsVm::refresh,
                                    onQueryChange = appsVm::setQuery,
                                    onAppEnabledChange = appsVm::setEnabled,
                                    onAppSelectedChange = appsVm::toggleSelectedPackage,
                                    onSelectAll = appsVm::setSelectedPackages,
                                    onOpenAppChannels = { pkg -> navController.navigate("app_channels/$pkg") },
                                    onBatchApplyGlobal = appsVm::batchApplyToAllEnabledApps,
                                    onBatchApplySelected = appsVm::batchApplyToSelectedApps,
                                    onSelectionModeChanged = { appsSelectionMode = it },

                                    appListState = appListState,
                                    selectionRequestId = appsSelectionRequestId,
                                    exitSelectionRequestId = appsExitSelectionRequestId,
                                    enableSelectedRequestId = appsEnableSelectedRequestId,
                                    disableSelectedRequestId = appsDisableSelectedRequestId,
                                    selectEnabledRequestId = appsSelectEnabledRequestId,
                                    batchSelectedRequestId = appsBatchSelectedRequestId,
                                    enableAllRequestId = appsEnableAllRequestId,
                                    disableAllRequestId = appsDisableAllRequestId,
                                    batchRequestId = appsBatchRequestId,
                                    topAppBarScrollBehavior = appsScrollBehavior,
                                    canPullToRefresh = true,
                                    modifier = Modifier.nestedScroll(appsScrollBehavior.nestedScrollConnection),
                                )
                            }
                            composable("settings") {
                                val importLauncher = rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.OpenDocument(),
                                ) { uri ->
                                    if (uri != null) {
                                        settingsVm.importConfigFromUri(uri)
                                    }
                                }
                                LaunchedEffect(Unit) {
                                    settingsVm.events.collect {
                                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                SettingsScreen(
                                    state = settingsState,
                                    onToggle = settingsVm::updateSwitch,
                                    onMarqueeSpeed = settingsVm::updateMarqueeSpeed,
                                    onBigIslandWidth = settingsVm::updateBigIslandMaxWidth,
                                    onThemeModeChange = settingsVm::updateThemeMode,
                                    onLocaleChange = settingsVm::updateLocale,
                                    onHideDesktopIcon = settingsVm::setDesktopIconHidden,
                                    onOpenBlacklist = { navController.navigate("blacklist") },
                                    onOpenAiConfig = { navController.navigate("ai_config") },
                                    onCheckUpdate = { openExternalUrl(context, GITHUB_RELEASE_URL) },
                                    onOpenGithub = { openExternalUrl(context, GITHUB_REPO_URL) },
                                    onExportToFile = settingsVm::exportConfigToFile,
                                    onPickImportFile = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                                    onExportToClipboard = settingsVm::exportConfigToClipboard,
                                    onImportFromClipboard = settingsVm::importConfigFromClipboard,
                                    modifier = Modifier.nestedScroll(settingsScrollBehavior.nestedScrollConnection),
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
                                    onSetAppEnabled = vm::setAppEnabled,
                                    onToggleChannel = vm::toggleChannel,
                                    onEnableAllChannels = vm::enableAllChannels,
                                    onOpenChannelSettings = { channelId, channelName ->
                                        navController.navigate(
                                            "channel_settings/${Uri.encode(packageNameArg)}/${Uri.encode(channelId)}/${Uri.encode(channelName)}",
                                        )
                                    },
                                    onBatchApplyToEnabledChannels = vm::batchApplyToEnabledChannels,
                                    enableAllRequestId = appChannelsEnableAllRequestId,
                                    batchRequestId = appChannelsBatchRequestId,
                                    modifier = Modifier.nestedScroll(appsScrollBehavior.nestedScrollConnection),
                                )
                            }
                            composable(
                                route = "channel_settings/{packageName}/{channelId}/{channelName}",
                                arguments = listOf(
                                    navArgument("packageName") { type = NavType.StringType },
                                    navArgument("channelId") { type = NavType.StringType },
                                    navArgument("channelName") { type = NavType.StringType },
                                ),
                            ) { backStack ->
                                val vm: AppChannelsViewModel = viewModel()
                                val state by vm.uiState.collectAsStateWithLifecycle()
                                val packageNameArg = Uri.decode(
                                    backStack.arguments?.getString("packageName").orEmpty(),
                                )
                                val channelIdArg = Uri.decode(
                                    backStack.arguments?.getString("channelId").orEmpty(),
                                )
                                LaunchedEffect(packageNameArg) {
                                    vm.setPackageNameIfEmpty(packageNameArg)
                                }
                                ChannelSettingsScreen(
                                    state = state,
                                    channelId = channelIdArg,
                                    onRefresh = vm::refresh,
                                    onSetTemplate = { vm.setTemplate(channelIdArg, it) },
                                    onSetTimeout = { vm.setTimeout(channelIdArg, it) },
                                    onSetSetting = { setting, value -> vm.setSetting(channelIdArg, setting, value) },
                                    onSetHighlightColor = { vm.setHighlightColor(channelIdArg, it) },
                                    modifier = Modifier.nestedScroll(appsScrollBehavior.nestedScrollConnection),
                                )
                            }
                            composable("blacklist") {
                                LaunchedEffect(Unit) {
                                    blacklistVm.events.collect {
                                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                BlacklistScreen(
                                    state = blacklistState,
                                    onRefresh = blacklistVm::refresh,
                                    onQueryChange = blacklistVm::setQuery,
                                    onSetBlacklisted = blacklistVm::setBlacklisted,
                                    canPullToRefresh = false,
                                    modifier = Modifier.nestedScroll(settingsScrollBehavior.nestedScrollConnection),
                                )
                            }
                            composable("ai_config") {
                                val vm: AiConfigViewModel = viewModel()
                                val uiState by vm.uiState.collectAsStateWithLifecycle()
                                LaunchedEffect(Unit) {
                                    vm.events.collect {
                                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                AiConfigScreen(
                                    state = uiState,
                                    onUpdate = vm::setState,
                                    onSave = vm::save,
                                    onTest = vm::testConnection,
                                    modifier = Modifier.nestedScroll(settingsScrollBehavior.nestedScrollConnection),
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(combinedPadding.calculateTopPadding() + 72.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface.copy(alpha = androidx.compose.ui.util.lerp(0.18f, 0.56f, topBarCollapseProgress)),
                                        MaterialTheme.colorScheme.surface.copy(alpha = androidx.compose.ui.util.lerp(0.08f, 0.32f, topBarCollapseProgress)),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(insets.calculateBottomPadding() + 96.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.24f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.46f),
                                    ),
                                ),
                            ),
                    )
                } // End of layerBackdrop box

                if (!isSecondaryRoute) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        HyperCeilerNavigationSwitchBar(
                            items = items,
                            selectedIndex = selectedIndex,
                            style = activeNavStyleState,
                            onDestinationClick = onPrimaryDestinationClick,
                            backdrop = backdrop,
                        )
                    }
                }
            }
            SponsorDialog(
                show = showSponsorDialog,
                onDismiss = { showSponsorDialog = false },
            )
            RestartScopeDialog(
                show = showRestartDialog,
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
    val notes = listOf(
        "1.此页面仅用于测试是否支持超级岛，并不代表实际效果",
        "2.请在 HyperCeiler 中关闭系统界面和小米服务框架的焦点通知白名单",
        "3.LSPosed 管理器中激活后，必须重启相关作用域软件",
        "4.支持通用适配，自行勾选合适的模板尝试",
    )
    val contentPadding = LocalContentPadding.current
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .overScrollVertical()
            .scrollEndHaptic()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 6.dp,
            bottom = contentPadding.calculateBottomPadding() + 6.dp
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            MiuixCard(modifier = primaryCardModifier(Modifier.fillMaxWidth())) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MiuixButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                    Text("刷新状态")
                }
                MiuixButton(onClick = onSendTest, modifier = Modifier.weight(1f)) {
                    Text("发送测试通知")
                }
            }
        }
        item {
            MiuixSmallTitle(text = "注意事项")
        }
        item {
            MiuixCard(modifier = primaryCardModifier(Modifier.fillMaxWidth())) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    notes.forEach { text ->
                        val dotIndex = text.indexOf('.')
                        val indexText = if (dotIndex > 0) text.substring(0, dotIndex + 1) else ""
                        val contentText = if (dotIndex > 0) text.substring(dotIndex + 1) else text
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = indexText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(22.dp),
                            )
                            Text(
                                text = contentText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SponsorDialog(show: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()
    val panelShape = RoundedCornerShape(16.dp)
    val qrBitmap = remember {
        runCatching {
            context.assets.open("flutter_assets/assets/images/wechat.jpg").use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }
    OverlayDialog(
        show = show,
        title = "赞助支持",
        summary = "赞助作者",
        onDismissRequest = onDismiss,
        onDismissFinished = {},
        renderInRootScaffold = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .overScrollVertical()
                .scrollEndHaptic(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "支持项目持续更新，感谢你的认可。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            if (qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(panelShape)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(
                                alpha = if (isDarkTheme) 0.28f else 0.18f,
                            ),
                        )
                        .then(
                            if (isDarkTheme) {
                                Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.38f),
                                    panelShape,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .padding(8.dp),
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "微信赞助二维码",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
            } else {
                Text(
                    text = "未找到赞助图片 assets/images/wechat.jpg",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MiuixButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
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
    val isDarkTheme = isSystemInDarkTheme()
    val scopeCardShape = RoundedCornerShape(16.dp)
    var restartSystemUi by remember { mutableStateOf(true) }
    var restartDownloads by remember { mutableStateOf(true) }
    var restartXmsf by remember { mutableStateOf(true) }

    val allSelected = restartSystemUi && restartDownloads && restartXmsf

    OverlayDialog(
        show = show,
        title = "选择需要重启的进程",
        onDismissRequest = onDismiss,
        onDismissFinished = {},
        renderInRootScaffold = false,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .overScrollVertical()
                .scrollEndHaptic(),
        ) {
            Text(
                text = "选择后将依次重启对应进程并刷新岛通知能力。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(scopeCardShape)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = if (isDarkTheme) 0.22f else 0.12f,
                        ),
                    )
                    .then(
                        if (isDarkTheme) {
                            Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.40f),
                                scopeCardShape,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScopeCheckboxRow(
                    title = "系统界面",
                    subtitle = "com.android.systemui",
                    checked = restartSystemUi,
                ) { restartSystemUi = !restartSystemUi }
                ScopeCheckboxRow(
                    title = "下载管理",
                    subtitle = "com.android.providers.downloads",
                    checked = restartDownloads,
                ) { restartDownloads = !restartDownloads }
                ScopeCheckboxRow(
                    title = "小米服务框架",
                    subtitle = "com.xiaomi.xmsf",
                    checked = restartXmsf,
                ) { restartXmsf = !restartXmsf }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MiuixButton(
                    onClick = {
                        val nextChecked = !allSelected
                        restartSystemUi = nextChecked
                        restartDownloads = nextChecked
                        restartXmsf = nextChecked
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (allSelected) "全不选" else "全选")
                }
                MiuixButton(
                    onClick = { onConfirm(restartSystemUi, restartDownloads, restartXmsf) },
                    modifier = Modifier.weight(1f),
                    colors = MiuixButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        text = "确定",
                        color = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScopeCheckboxRow(title: String, subtitle: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(interactionSource = remember { MutableInteractionSource() })
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    val themeModeOptions = listOf(
        "system" to "跟随系统",
        "light" to "浅色",
        "dark" to "深色",
    )
    val selectedThemeIndex = themeModeOptions.indexOfFirst { it.first == state.themeMode }.coerceAtLeast(0)

    val localeOptions = listOf(
        "__system__" to "跟随系统",
        "zh" to "中文",
        "en" to "English",
        "ja" to "日本語",
        "tr" to "Türkçe",
    )
    val localeValue = state.locale ?: "__system__"
    val selectedLocaleIndex = localeOptions.indexOfFirst { it.first == localeValue }.coerceAtLeast(0)

    val contentPadding = LocalContentPadding.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .overScrollVertical()
            .scrollEndHaptic()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 6.dp,
            bottom = contentPadding.calculateBottomPadding() + 6.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionTitle("AI 增强")
            SettingsGroupCard {
                SettingsEntryItem(
                    title = "AI 通知摘要",
                    subtitle = if (state.aiEnabled) "已启用 · 点击配置 AI 参数" else "已关闭 · 点击进行配置",
                    onClick = onOpenAiConfig,
                )
            }
        }

        item {
            SectionTitle("通知黑名单")
            SettingsGroupCard {
                SettingsEntryItem(
                    title = "通知黑名单",
                    subtitle = "启动黑名单应用时，停用焦点通知的自动展开功能",
                    onClick = onOpenBlacklist,
                )
            }
        }

        item {
            SectionTitle("行为")
            SettingsGroupCard {
                ToggleItem(
                    "交互触感",
                    "为开关、滑块和按钮启用 Hyper 定制震感反馈",
                    state.interactionHaptics,
                ) { onToggle(PrefKeys.INTERACTION_HAPTICS, it) }
                ToggleItem(
                    "下载管理器暂停后保留焦点通知",
                    "显示一条通知，点击以继续下载，可能导致状态不同步",
                    state.resumeNotification,
                ) { onToggle(PrefKeys.RESUME_NOTIFICATION, it) }
                ToggleItem(
                    "移除焦点通知白名单",
                    "允许所有应用发送焦点通知，无需系统授权",
                    state.unlockAllFocus,
                ) { onToggle(PrefKeys.UNLOCK_ALL_FOCUS, it) }
                ToggleItem(
                    "移除焦点通知签名验证",
                    "允许所有应用向手表/手环发送焦点通知，跳过签名校验（需 Hook 小米服务框架）",
                    state.unlockFocusAuth,
                ) { onToggle(PrefKeys.UNLOCK_FOCUS_AUTH, it) }
                ToggleItem(
                    "显示启动欢迎语",
                    "应用启动时在超级岛显示欢迎信息",
                    state.showWelcome,
                ) { onToggle(PrefKeys.SHOW_WELCOME, it) }
                ToggleItem(
                    "隐藏桌面图标",
                    "隐藏启动器中的应用图标，隐藏后可通过 LSPosed 管理器打开",
                    state.hideDesktopIcon,
                    onHideDesktopIcon,
                )
                ToggleItem(
                    "启动时检查更新",
                    "启动应用时自动检查是否有新版本",
                    state.checkUpdateOnLaunch,
                ) { onToggle(PrefKeys.CHECK_UPDATE_ON_LAUNCH, it) }
            }
        }

        item {
            SectionTitle("渠道默认配置")
            SettingsGroupCard {
                ToggleItem(
                    "初次展开",
                    "超级岛初次收到通知后是否展开为焦点通知",
                    state.defaultFirstFloat,
                ) { onToggle(PrefKeys.DEFAULT_FIRST_FLOAT, it) }
                ToggleItem(
                    "更新展开",
                    "超级岛更新后是否展开通知",
                    state.defaultEnableFloat,
                ) { onToggle(PrefKeys.DEFAULT_ENABLE_FLOAT, it) }
                ToggleItem(
                    "消息滚动",
                    "超级岛消息过长是否滚动显示",
                    state.defaultMarquee,
                ) { onToggle(PrefKeys.DEFAULT_MARQUEE, it) }
                ToggleItem(
                    "高亮动态取色",
                    "开启后默认使用图标自动取色",
                    state.defaultDynamicHighlightColor,
                ) { onToggle(PrefKeys.DEFAULT_DYNAMIC_HIGHLIGHT_COLOR, it) }
                ToggleItem(
                    "外圈光效",
                    "",
                    state.defaultOuterGlow,
                ) { onToggle(PrefKeys.DEFAULT_OUTER_GLOW, it) }
                ToggleItem(
                    "焦点通知",
                    "替换通知为焦点通知（关闭后显示原始通知）",
                    state.defaultFocusNotif,
                ) { onToggle(PrefKeys.DEFAULT_FOCUS_NOTIF, it) }
                ToggleItem(
                    "锁屏通知复原",
                    "锁屏时跳过焦点通知处理，保持原始通知隐私行为",
                    state.defaultRestoreLockscreen,
                ) { onToggle(PrefKeys.DEFAULT_RESTORE_LOCKSCREEN, it) }
                ToggleItem(
                    "大岛图标",
                    "开启后显示超级岛的大图标（小岛不受影响）",
                    state.defaultShowIslandIcon,
                ) { onToggle(PrefKeys.DEFAULT_SHOW_ISLAND_ICON, it) }
                ToggleItem(
                    "状态栏图标",
                    "焦点通知打开时，是否强制保留状态栏小图标",
                    state.defaultPreserveSmallIcon,
                ) { onToggle(PrefKeys.DEFAULT_PRESERVE_SMALL_ICON, it) }
            }
        }

        item {
            SectionTitle("外观")
            SettingsGroupCard {
                ToggleItem(
                    "使用应用图标",
                    "下载管理器通知使用应用图标",
                    state.useHookAppIcon,
                ) { onToggle(PrefKeys.USE_HOOK_APP_ICON, it) }
                ToggleItem(
                    "图标圆角",
                    "为通知图标添加圆角效果",
                    state.roundIcon,
                ) { onToggle(PrefKeys.ROUND_ICON, it) }
                ToggleItem(
                    "悬浮底部导航栏",
                    "启用后使用 FloatingNavigationBar 样式",
                    state.useFloatingNavigationBar,
                ) { onToggle(PrefKeys.USE_FLOATING_NAVIGATION_BAR, it) }
                SliderItem(
                    title = "消息滚动",
                    subtitle = "滚动速度",
                        valueText = "${state.marqueeSpeed} px/s",
                    value = state.marqueeSpeed.toFloat(),
                    defaultValue = DEFAULT_MARQUEE_SPEED.toFloat(),
                    valueRange = 20f..500f,
                    steps = 48,
                    onValueChange = { onMarqueeSpeed(it.toInt()) },
                    onResetToDefault = { onMarqueeSpeed(DEFAULT_MARQUEE_SPEED) },
                )
                ToggleSliderItem(
                    "修改超级岛最大宽度",
                    "开启后修改超级岛的最大宽度",
                    state.bigIslandMaxWidthEnabled,
                    valueText = "${state.bigIslandMaxWidth} dp",
                    value = state.bigIslandMaxWidth.toFloat(),
                    defaultValue = DEFAULT_BIG_ISLAND_MAX_WIDTH.toFloat(),
                    valueRange = 500f..1000f,
                    steps = 54,
                    onCheckedChange = { onToggle(PrefKeys.BIG_ISLAND_MAX_WIDTH_ENABLED, it) },
                    onValueChange = { onBigIslandWidth(it.toInt()) },
                    onResetToDefault = { onBigIslandWidth(DEFAULT_BIG_ISLAND_MAX_WIDTH) },
                )
                MiuixOverlayDropdownPreference(
                    title = "颜色模式",
                    items = themeModeOptions.map { it.second },
                    selectedIndex = selectedThemeIndex,
                    renderInRootScaffold = false,
                    onSelectedIndexChange = { index ->
                        val next = themeModeOptions.getOrNull(index)?.first ?: return@MiuixOverlayDropdownPreference
                        onThemeModeChange(next)
                    },
                )
                MiuixOverlayDropdownPreference(
                    title = "语言",
                    items = localeOptions.map { it.second },
                    selectedIndex = selectedLocaleIndex,
                    renderInRootScaffold = false,
                    onSelectedIndexChange = { index ->
                        val next = localeOptions.getOrNull(index)?.first ?: return@MiuixOverlayDropdownPreference
                        onLocaleChange(if (next == "__system__") null else next)
                    },
                )
            }
        }

        item {
            SectionTitle("配置")
            SettingsGroupCard {
                SettingsEntryItem("导出到文件", "将配置保存为 JSON 文件", onExportToFile)
                SettingsEntryItem("导出到剪贴板", "将配置复制为 JSON 文本", onExportToClipboard)
                SettingsEntryItem("从文件导入", "从 JSON 文件恢复配置", onPickImportFile)
                SettingsEntryItem("从剪贴板导入", "从剪贴板中的 JSON 文本恢复配置", onImportFromClipboard)
            }
        }

        item {
            SectionTitle("关于")
            SettingsGroupCard {
                SettingsEntryItem("检查更新", "", onCheckUpdate)
                SettingsEntryItem("GitHub", "1812z/HyperIsland", onOpenGithub)
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
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SettingsScreenPreview() {
    MiuixTheme {
        MaterialTheme {
            SettingsScreen(
                state = SettingsState(
                    interactionHaptics = true,
                    useHookAppIcon = true,
                    roundIcon = true,
                    useFloatingNavigationBar = true,
                    marqueeSpeed = 100,
                ),
                onToggle = { _, _ -> },
                onMarqueeSpeed = {},
                onBigIslandWidth = {},
                onThemeModeChange = {},
                onLocaleChange = {},
                onHideDesktopIcon = {},
                onOpenBlacklist = {},
                onOpenAiConfig = {},
                onCheckUpdate = {},
                onOpenGithub = {},
                onExportToFile = {},
                onPickImportFile = {},
                onExportToClipboard = {},
                onImportFromClipboard = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun MainActivityPreview() {
    val items = listOf(
        TopLevelDestination("home", "主页", HomeFilledIcon),
        TopLevelDestination("apps", "应用", MiuixIcons.Regular.All),
        TopLevelDestination("settings", "设置", SettingsFilledIcon),
    )
    var selectedIndex by remember { mutableStateOf(0) }
    var previewSettingsState by remember {
        mutableStateOf(
            SettingsState(
                showWelcome = true,
                resumeNotification = true,
                interactionHaptics = true,
                checkUpdateOnLaunch = true,
                themeMode = "system",
                locale = "zh",
                aiEnabled = false,
                useHookAppIcon = true,
                roundIcon = true,
                marqueeFeature = true,
                marqueeSpeed = 120,
                bigIslandMaxWidthEnabled = true,
                bigIslandMaxWidth = 680,
                useFloatingNavigationBar = true,
                defaultFirstFloat = false,
                defaultEnableFloat = true,
                defaultShowIslandIcon = true,
                defaultMarquee = true,
                defaultDynamicHighlightColor = true,
                defaultOuterGlow = false,
                defaultFocusNotif = true,
                defaultPreserveSmallIcon = false,
                defaultRestoreLockscreen = false,
            ),
        )
    }
    val selectedRoute = items.getOrNull(selectedIndex)?.route ?: "home"
    val homeScrollBehavior = MiuixScrollBehavior(
        state = rememberTopAppBarState(),
        canScroll = { true },
    )
    val appsScrollBehavior = MiuixScrollBehavior(
        state = rememberTopAppBarState(),
        canScroll = { true },
    )
    val settingsScrollBehavior = MiuixScrollBehavior(
        state = rememberTopAppBarState(),
        canScroll = { true },
    )
    val activePrimaryScrollBehavior = when (selectedRoute) {
        "apps" -> appsScrollBehavior
        "settings" -> settingsScrollBehavior
        else -> homeScrollBehavior
    }

    MiuixTheme {
        MaterialTheme {
            MiuixScaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    MiuixTopAppBar(
                        title = routeTitle(selectedRoute),
                        scrollBehavior = activePrimaryScrollBehavior,
                        defaultWindowInsetsPadding = false,
                    )
                },
                bottomBar = {
                    HyperCeilerNavigationSwitchBar(
                        items = items,
                        selectedIndex = selectedIndex,
                        style = NavigationStyleState(
                            floating = false,
                            floatingMode = MiuixFloatingNavigationBarDisplayMode.IconOnly,
                            floatingBottomOffset = 0.dp,
                            floatingHorizontalOutSidePadding = 0.dp,
                            floatingCornerRadius = 0.dp,
                            floatingShadowElevation = 0.dp,
                            floatingWindowInsetsPadding = false,
                            floatingContainerWidth = 0.dp,
                            floatingContainerHeight = 0.dp,
                            floatingIconSize = 0.dp,
                            floatingItemHorizontalPadding = 0.dp,
                            floatingStrokeWidth = 0.dp,
                            bottomShowDivider = false,
                            bottomWindowInsetsPadding = false,
                            bottomContainerHeight = 56.dp,
                            bottomIconSize = 22.dp,
                            bottomItemHorizontalPadding = 0.dp,
                            bottomShowLabel = true,
                            unselectedAlpha = 0.4f,
                        ),
                        onDestinationClick = { destination ->
                            selectedIndex = items.indexOfFirst { it.route == destination.route }
                        },
                    )
                },
            ) { innerPadding ->
                when (selectedRoute) {
                    "home" -> HomeScreen(
                        uiState = HomeUiState(
                            moduleActive = true,
                            lsposedApiVersion = 101,
                            focusProtocolVersion = 9,
                            restarting = false,
                        ),
                        onRefresh = {},
                        onSendTest = {},
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(homeScrollBehavior.nestedScrollConnection)
                            .consumeWindowInsets(innerPadding)
                            .padding(innerPadding),
                    )
                    "apps" -> AppsScreen(
                        state = AppsUiState(
                            loading = false,
                            query = "",
                            showSystemApps = false,
                            apps = listOf(
                                AppItem("com.android.providers.downloads", "下载管理", true),
                                AppItem("com.tencent.mm", "微信", false),
                                AppItem("com.ss.android.ugc.aweme", "抖音", false),
                                AppItem("com.eg.android.AlipayGphone", "支付宝", false),
                                AppItem("com.tencent.mobileqq", "QQ", false),
                                AppItem("com.miui.home", "系统桌面", true),
                            ),
                            enabledPackages = setOf(
                                "com.android.providers.downloads",
                                "com.tencent.mm",
                                "com.eg.android.AlipayGphone",
                            ),
                        ),
                        onRefresh = {},
                        onQueryChange = {},
                        onAppEnabledChange = { _, _ -> },
                        onOpenAppChannels = {},
                        onBatchApplyGlobal = {},
                        onBatchApplySelected = { _, _ -> },
                        canPullToRefresh = true,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(appsScrollBehavior.nestedScrollConnection)
                            .consumeWindowInsets(innerPadding)
                            .padding(innerPadding),
                    )
                    else -> SettingsScreen(
                        state = previewSettingsState,
                        onToggle = { key, enabled ->
                            previewSettingsState = applyPreviewToggle(previewSettingsState, key, enabled)
                        },
                        onMarqueeSpeed = {
                            previewSettingsState = previewSettingsState.copy(marqueeSpeed = it)
                        },
                        onBigIslandWidth = {
                            previewSettingsState = previewSettingsState.copy(bigIslandMaxWidth = it)
                        },
                        onThemeModeChange = {
                            previewSettingsState = previewSettingsState.copy(themeMode = it)
                        },
                        onLocaleChange = {
                            previewSettingsState = previewSettingsState.copy(locale = it)
                        },
                        onHideDesktopIcon = {
                            previewSettingsState = previewSettingsState.copy(hideDesktopIcon = it)
                        },
                        onOpenBlacklist = {},
                        onOpenAiConfig = {},
                        onCheckUpdate = {},
                        onOpenGithub = {},
                        onExportToFile = {},
                        onPickImportFile = {},
                        onExportToClipboard = {},
                        onImportFromClipboard = {},
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(settingsScrollBehavior.nestedScrollConnection)
                            .consumeWindowInsets(innerPadding)
                            .padding(innerPadding),
                    )
                }
            }
        }
    }
}

private fun applyPreviewToggle(state: SettingsState, key: String, enabled: Boolean): SettingsState {
    return when (key) {
        PrefKeys.INTERACTION_HAPTICS -> state.copy(interactionHaptics = enabled)
        PrefKeys.RESUME_NOTIFICATION -> state.copy(resumeNotification = enabled)
        PrefKeys.UNLOCK_ALL_FOCUS -> state.copy(unlockAllFocus = enabled)
        PrefKeys.UNLOCK_FOCUS_AUTH -> state.copy(unlockFocusAuth = enabled)
        PrefKeys.SHOW_WELCOME -> state.copy(showWelcome = enabled)
        PrefKeys.HIDE_DESKTOP_ICON -> state.copy(hideDesktopIcon = enabled)
        PrefKeys.CHECK_UPDATE_ON_LAUNCH -> state.copy(checkUpdateOnLaunch = enabled)
        PrefKeys.DEFAULT_FIRST_FLOAT -> state.copy(defaultFirstFloat = enabled)
        PrefKeys.DEFAULT_ENABLE_FLOAT -> state.copy(defaultEnableFloat = enabled)
        PrefKeys.DEFAULT_MARQUEE -> state.copy(defaultMarquee = enabled)
        PrefKeys.DEFAULT_DYNAMIC_HIGHLIGHT_COLOR -> state.copy(defaultDynamicHighlightColor = enabled)
        PrefKeys.DEFAULT_OUTER_GLOW -> state.copy(defaultOuterGlow = enabled)
        PrefKeys.DEFAULT_FOCUS_NOTIF -> state.copy(defaultFocusNotif = enabled)
        PrefKeys.DEFAULT_RESTORE_LOCKSCREEN -> state.copy(defaultRestoreLockscreen = enabled)
        PrefKeys.DEFAULT_SHOW_ISLAND_ICON -> state.copy(defaultShowIslandIcon = enabled)
        PrefKeys.DEFAULT_PRESERVE_SMALL_ICON -> state.copy(defaultPreserveSmallIcon = enabled)
        PrefKeys.USE_HOOK_APP_ICON -> state.copy(useHookAppIcon = enabled)
        PrefKeys.ROUND_ICON -> state.copy(roundIcon = enabled)
        PrefKeys.USE_FLOATING_NAVIGATION_BAR -> state.copy(useFloatingNavigationBar = enabled)
        PrefKeys.BIG_ISLAND_MAX_WIDTH_ENABLED -> state.copy(bigIslandMaxWidthEnabled = enabled)
        PrefKeys.AI_ENABLED -> state.copy(aiEnabled = enabled)
        else -> state
    }
}

@Composable
private fun SettingsEntryItem(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(interactionSource = remember { MutableInteractionSource() })
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    MiuixSmallTitle(
        text = title,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
    )
}

@Composable
private fun SettingsGroupCard(content: @Composable () -> Unit) {
    MiuixCard(modifier = primaryCardModifier(Modifier.fillMaxWidth())) {
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))) {
            content()
        }
    }
}

@Composable
private fun ToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun SliderItem(
    title: String,
    subtitle: String,
    valueText: String,
    value: Float,
    defaultValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onResetToDefault: () -> Unit,
) {
    val showResetButton = kotlin.math.abs(value - defaultValue) > 0.0001f
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    if (showResetButton) {
                        MiuixIconButton(
                            onClick = onResetToDefault,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Refresh,
                                contentDescription = "恢复默认值",
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
                Text(
                    valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(72.dp),
                )
            }
        }
        if (subtitle.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun ToggleSliderItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    valueText: String,
    value: Float,
    defaultValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onCheckedChange: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit,
    onResetToDefault: () -> Unit,
) {
    val showResetButton = kotlin.math.abs(value - defaultValue) > 0.0001f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        if (showResetButton) {
                            MiuixIconButton(
                                onClick = onResetToDefault,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Regular.Refresh,
                                    contentDescription = "恢复默认值",
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                    Text(
                        valueText,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(72.dp),
                    )
                }
            }
        }
    }
}

