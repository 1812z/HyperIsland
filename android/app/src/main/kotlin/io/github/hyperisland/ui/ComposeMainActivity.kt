package io.github.hyperisland.ui

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import top.yukonga.miuix.kmp.basic.Checkbox as MiuixCheckbox
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material3.darkColorScheme as MaterialDarkColorScheme
import androidx.compose.material3.lightColorScheme as MaterialLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import androidx.compose.ui.zIndex
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
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
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
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference as MiuixOverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme as MiuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as MiuixLightColorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import java.lang.reflect.Method

import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import androidx.compose.runtime.compositionLocalOf

val LocalContentPadding = compositionLocalOf { PaddingValues(0.dp) }
private var forcedAppDarkMode: Boolean? by mutableStateOf(null)

@Composable
fun isAppInDarkTheme(): Boolean = forcedAppDarkMode ?: isSystemInDarkTheme()

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
            val darkTheme = isAppInDarkTheme()
            SideEffect {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    insetsController.isAppearanceLightNavigationBars = !darkTheme
                }
            }
            MiuixTheme(colors = if (darkTheme) MiuixDarkColorScheme() else MiuixLightColorScheme()) {
                MaterialTheme(colorScheme = if (darkTheme) MaterialDarkColorScheme() else MaterialLightColorScheme()) {
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

private sealed interface AppScreen : NavKey {
    data object Home : AppScreen
    data object Apps : AppScreen
    data object Settings : AppScreen
    data class AppChannels(val packageName: String) : AppScreen
    data class ChannelSettings(
        val packageName: String,
        val channelId: String,
        val channelName: String,
    ) : AppScreen
    data object Blacklist : AppScreen
    data object AiConfig : AppScreen
}

private fun topLevelScreen(screen: AppScreen): AppScreen = when (screen) {
    AppScreen.Home -> AppScreen.Home
    AppScreen.Apps -> AppScreen.Apps
    AppScreen.Settings -> AppScreen.Settings
    is AppScreen.AppChannels -> AppScreen.Apps
    is AppScreen.ChannelSettings -> AppScreen.Apps
    AppScreen.Blacklist -> AppScreen.Settings
    AppScreen.AiConfig -> AppScreen.Settings
}

private fun screenTitle(language: UiLanguage, screen: AppScreen): String = when (screen) {
    AppScreen.Home -> textOf(language, "主页", "Home")
    AppScreen.Apps -> textOf(language, "应用", "Apps")
    AppScreen.Settings -> textOf(language, "设置", "Settings")
    is AppScreen.AppChannels -> textOf(language, "渠道设置", "Channel Settings")
    is AppScreen.ChannelSettings -> textOf(language, "渠道详情", "Channel Details")
    AppScreen.Blacklist -> textOf(language, "通知黑名单", "Notification Blacklist")
    AppScreen.AiConfig -> textOf(language, "AI 配置", "AI Config")
}

private fun screenDepth(screen: AppScreen): Int = when (screen) {
    AppScreen.Home,
    AppScreen.Apps,
    AppScreen.Settings -> 1
    is AppScreen.AppChannels,
    AppScreen.Blacklist,
    AppScreen.AiConfig -> 2
    is AppScreen.ChannelSettings -> 3
}

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

private const val DOCUMENTATION_URL = "https://hyperisland.1812z.top/"
private const val GITHUB_REPO_URL = "https://github.com/1812z/HyperIsland"
private const val GITHUB_RELEASE_URL = "https://github.com/1812z/HyperIsland/releases/latest"
private const val QQ_GROUP_NUMBER = "1045114341"
private const val DEFAULT_MARQUEE_SPEED = 100
private const val DEFAULT_BIG_ISLAND_MAX_WIDTH = 200
private const val BAR_BLUR_RADIUS = 28f
private const val BAR_BLUR_NOISE = 0.016f

@Composable
private fun barBlurColors(): BlurColors {
    val dark = isAppInDarkTheme()
    return if (dark) {
        BlurColors(
            brightness = -0.015f,
            contrast = 0.86f,
            saturation = 0.74f,
        )
    } else {
        BlurColors(
            brightness = 0.008f,
            contrast = 0.88f,
            saturation = 0.78f,
        )
    }
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
    val isDarkTheme = isAppInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme
    val blurColors = barBlurColors()
    val blurBackdrop = backdrop
    val useBackdropBlur = blurBackdrop != null && !isDarkTheme
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
                Box(
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
                                        colorScheme.outline.copy(alpha = 0.28f),
                                        colorScheme.onSurface.copy(alpha = 0.14f),
                                        colorScheme.surfaceVariant.copy(alpha = 0.16f),
                                        colorScheme.outline.copy(alpha = 0.22f),
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
                                        colorScheme.onSurface.copy(alpha = 0.12f),
                                        colorScheme.outline.copy(alpha = 0.06f),
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
                ) {
                    MiuiBlurredSurface(
                        modifier = Modifier.fillMaxSize(),
                        fallbackBackdrop = if (useBackdropBlur) blurBackdrop else null,
                        fallbackBlurColors = blurColors,
                        overlayAlphaFallbackDark = 0.48f,
                        overlayAlphaFallbackLight = 0.54f,
                        overlayAlphaNativeDark = 0.52f,
                        overlayAlphaNativeLight = 0.58f,
                    ) {}
                    Row(
                        modifier = Modifier.fillMaxSize(),
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
            }
        } else {
            MiuiBlurredSurface(
                modifier = modifier.fillMaxWidth(),
                fallbackBackdrop = if (useBackdropBlur) blurBackdrop else null,
                fallbackBlurColors = blurColors,
                overlayAlphaFallbackDark = 0.48f,
                overlayAlphaFallbackLight = 0.54f,
                overlayAlphaNativeDark = 0.52f,
                overlayAlphaNativeLight = 0.58f,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
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
}

@Composable
private fun primaryCardModifier(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
): Modifier {
    val isDarkTheme = isAppInDarkTheme()
    return modifier
        .clip(shape)
        .then(
            if (isDarkTheme) {
                Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                    shape,
                )
            } else {
                Modifier
            },
        )
}

@Composable
private fun OverlayPopupMenuContainer(content: @Composable () -> Unit) {
    val isDarkTheme = isAppInDarkTheme()
    val containerShape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
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
        Box(
            modifier = Modifier
                .clip(containerShape)
                .background(
                    MaterialTheme.colorScheme.surface.copy(
                        alpha = if (isDarkTheme) 0.95f else 0.98f,
                    ),
                ),
        ) {
            MiuixListPopupColumn {
                content()
            }
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

private fun isBlurEffectEnabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    return runCatching {
        val wm = context.getSystemService(WindowManager::class.java)
        wm?.isCrossWindowBlurEnabled == true
    }.getOrDefault(false)
}

@Composable
private fun MiuiBlurredSurface(
    modifier: Modifier = Modifier,
    fallbackBackdrop: LayerBackdrop? = null,
    fallbackBlurColors: BlurColors = BlurColors(),
    overlayAlphaFallbackDark: Float = 0.48f,
    overlayAlphaFallbackLight: Float = 0.54f,
    overlayAlphaNativeDark: Float = 0.52f,
    overlayAlphaNativeLight: Float = 0.58f,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val blurRadiusPx = with(LocalDensity.current) { 46.dp.roundToPx() }
    var nativeBlurApplied by remember { mutableStateOf(false) }
    val useFallbackBlur = fallbackBackdrop != null && !nativeBlurApplied
    val overlayAlpha = if (useFallbackBlur) {
        if (isAppInDarkTheme()) overlayAlphaFallbackDark else overlayAlphaFallbackLight
    } else {
        if (isAppInDarkTheme()) overlayAlphaNativeDark else overlayAlphaNativeLight
    }
    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                View(ctx).apply {
                    isClickable = false
                    isFocusable = false
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            modifier = Modifier
                .matchParentSize()
                .zIndex(-3f),
            update = { view ->
                nativeBlurApplied = MiuiTopBarBlurCompat.apply(view, context, blurRadiusPx)
            },
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .zIndex(-2f)
                .let {
                    if (useFallbackBlur) {
                        it.textureBlur(
                            backdrop = fallbackBackdrop,
                            shape = RectangleShape,
                            blurRadiusX = BAR_BLUR_RADIUS,
                            blurRadiusY = BAR_BLUR_RADIUS,
                            noiseCoefficient = BAR_BLUR_NOISE,
                            colors = fallbackBlurColors,
                        )
                    } else {
                        it
                    }
                },
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .zIndex(-1f)
                .background(MaterialTheme.colorScheme.background.copy(alpha = overlayAlpha)),
        )
        Box(modifier = Modifier.zIndex(1f)) {
            content()
        }
    }
}

@Composable
private fun MiuiBlurredTopBar(
    modifier: Modifier = Modifier,
    fallbackBackdrop: LayerBackdrop? = null,
    fallbackBlurColors: BlurColors = BlurColors(),
    content: @Composable BoxScope.() -> Unit,
) {
    MiuiBlurredSurface(
        modifier = modifier,
        fallbackBackdrop = fallbackBackdrop,
        fallbackBlurColors = fallbackBlurColors,
        content = content,
    )
}

private object MiuiTopBarBlurCompat {
    private const val TAG = "MiuiTopBarBlur"
    private var loggedOnce = false

    private val fanBlurClass: Class<*>? by lazy {
        runCatching { Class.forName("fan.core.utils.MiuiBlurUtils") }.getOrNull()
    }
    private val fanIsEnable: Method? by lazy {
        runCatching { fanBlurClass?.getDeclaredMethod("isEnable") }.getOrNull()
    }
    private val fanIsEffectEnable: Method? by lazy {
        runCatching { fanBlurClass?.getDeclaredMethod("isEffectEnable", Context::class.java) }.getOrNull()
    }
    private val fanSetBackgroundBlur: Method? by lazy {
        runCatching {
            fanBlurClass?.getDeclaredMethod("setBackgroundBlur", View::class.java, Int::class.javaPrimitiveType)
        }.getOrNull()
    }
    private val fanSetViewBlurMode: Method? by lazy {
        runCatching {
            fanBlurClass?.getDeclaredMethod("setViewBlurMode", View::class.java, Int::class.javaPrimitiveType)
        }.getOrNull()
    }

    private val setBackgroundBlur: Method? by lazy {
        runCatching { View::class.java.getDeclaredMethod("setBackgroundBlur", Int::class.javaPrimitiveType) }.getOrNull()
    }
    private val setViewBlurMode: Method? by lazy {
        runCatching { View::class.java.getDeclaredMethod("setViewBlurMode", Int::class.javaPrimitiveType) }.getOrNull()
    }
    private val setPassWindowBlurEnabled: Method? by lazy {
        runCatching {
            View::class.java.getDeclaredMethod("setPassWindowBlurEnabled", Boolean::class.javaPrimitiveType)
        }.getOrNull()
    }
    private val setMiBackgroundBlurMode: Method? by lazy {
        runCatching { View::class.java.getDeclaredMethod("setMiBackgroundBlurMode", Int::class.javaPrimitiveType) }.getOrNull()
    }
    private val setMiBackgroundBlurRadius: Method? by lazy {
        runCatching { View::class.java.getDeclaredMethod("setMiBackgroundBlurRadius", Int::class.javaPrimitiveType) }.getOrNull()
    }
    private val setMiViewBlurMode: Method? by lazy {
        runCatching { View::class.java.getDeclaredMethod("setMiViewBlurMode", Int::class.javaPrimitiveType) }.getOrNull()
    }

    fun apply(view: View, context: Context, radiusPx: Int): Boolean {
        return runCatching {
            var applied = false
            val safeRadius = radiusPx.coerceIn(1, 500)

            val fanEnabled = runCatching { fanIsEnable?.invoke(null) as? Boolean }.getOrNull() ?: false
            val fanEffectEnabled = runCatching { fanIsEffectEnable?.invoke(null, context) as? Boolean }.getOrNull() ?: false
            if (fanEnabled && fanEffectEnabled && fanSetBackgroundBlur != null && fanSetViewBlurMode != null) {
                fanSetBackgroundBlur?.invoke(null, view, safeRadius)
                fanSetViewBlurMode?.invoke(null, view, 0)
                applied = true
            }

            if (!applied) {
                setPassWindowBlurEnabled?.invoke(view, true)
                setMiBackgroundBlurMode?.invoke(view, 1)
                setMiBackgroundBlurRadius?.invoke(view, safeRadius)
                setMiViewBlurMode?.invoke(view, 1)
                applied = setMiBackgroundBlurMode != null && setMiBackgroundBlurRadius != null
            }

            if (!applied && setBackgroundBlur != null && setViewBlurMode != null) {
                setBackgroundBlur?.invoke(view, safeRadius)
                setViewBlurMode?.invoke(view, 0)
                applied = true
            }

            if (!loggedOnce) {
                loggedOnce = true
                Log.i(
                    TAG,
                    "applied=$applied, fanClass=${fanBlurClass != null}, fanEnabled=$fanEnabled, fanEffectEnabled=$fanEffectEnabled",
                )
            }
            applied
        }.getOrDefault(false)
    }
}

@Composable
private fun HyperIslandComposeApp() {
    val backStack = remember { mutableStateListOf<AppScreen>(AppScreen.Home) }
    val navigationEventDispatcherOwner = rememberNavigationEventDispatcherOwner(parent = null)
    val backdrop = rememberLayerBackdrop()
    val context = LocalContext.current
    val blurEnabled = remember(context) { isBlurEffectEnabled(context) }
    val activeBackdrop = if (blurEnabled) backdrop else null
    val topBarFallbackBlurColors = barBlurColors()
    val homeVm: HomeViewModel = viewModel()
    val appsVm: AppsViewModel = viewModel()
    val blacklistVm: BlacklistViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()
    val settingsState by settingsVm.uiState.collectAsStateWithLifecycle()
    val uiLanguage = rememberUiLanguage(settingsState.locale)
    var useFloatingNavigationBarUi by remember { mutableStateOf(settingsState.useFloatingNavigationBar) }
    LaunchedEffect(settingsState.useFloatingNavigationBar) {
        useFloatingNavigationBarUi = settingsState.useFloatingNavigationBar
    }
    SideEffect {
        forcedAppDarkMode = when (settingsState.themeMode) {
            "dark" -> true
            "light" -> false
            else -> null
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
        TopLevelDestination("home", textOf(uiLanguage, "主页", "Home"), HomeFilledIcon),
        TopLevelDestination("apps", textOf(uiLanguage, "应用", "Apps"), MiuixIcons.Regular.All),
        TopLevelDestination("settings", textOf(uiLanguage, "设置", "Settings"), SettingsFilledIcon),
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
    val activeNavStyleState = if (useFloatingNavigationBarUi) {
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

    val currentScreen = backStack.lastOrNull() ?: AppScreen.Home
    val currentTopLevelScreen = topLevelScreen(currentScreen)
    fun topLevelIndexOf(screen: AppScreen): Int = when (topLevelScreen(screen)) {
        AppScreen.Home -> 0
        AppScreen.Apps -> 1
        else -> 2
    }
    fun topLevelScreenOf(index: Int): AppScreen = when (index) {
        1 -> AppScreen.Apps
        2 -> AppScreen.Settings
        else -> AppScreen.Home
    }
    var selectedTopLevelIndex by rememberSaveable { mutableIntStateOf(topLevelIndexOf(currentTopLevelScreen)) }
    val isAppChannelsScreen = currentScreen is AppScreen.AppChannels
    val isChannelSettingsScreen = currentScreen is AppScreen.ChannelSettings
    val isBlacklistScreen = currentScreen == AppScreen.Blacklist
    val isAiConfigScreen = currentScreen == AppScreen.AiConfig
    val isAppsPrimaryScreen = currentScreen == AppScreen.Apps

    val appListState = rememberLazyListState()
    fun navigateTo(screen: AppScreen) {
        if (backStack.lastOrNull() != screen) {
            backStack.add(screen)
        }
    }
    fun popScreen(): Boolean {
        return if (backStack.size > 1) {
            backStack.removeLast()
            true
        } else {
            false
        }
    }
    fun navigateTopLevel(screen: AppScreen) {
        val target = topLevelScreen(screen)
        val latest = backStack.lastOrNull() ?: AppScreen.Home
        if (latest == target && backStack.size == 1) return
        if (backStack.size == 1) {
            // 一级页面切换使用同层替换，避免被识别为 push/pop 导致动画方向异常。
            backStack[0] = target
            return
        }
        backStack.clear()
        backStack.add(target)
    }
    val selectedIndex = selectedTopLevelIndex
    val onPrimaryDestinationClick: (TopLevelDestination) -> Unit = { destination ->
        val targetIndex = items.indexOfFirst { it.route == destination.route }.let { if (it == -1) 0 else it }
        selectedTopLevelIndex = targetIndex
        val target = topLevelScreenOf(targetIndex)
        navigateTopLevel(target)
    }
    val topBarTitle = if (currentScreen is AppScreen.ChannelSettings) {
        currentScreen.channelName.ifBlank { textOf(uiLanguage, "渠道详情", "Channel Details") }
    } else {
        screenTitle(uiLanguage, currentScreen)
    }
    val isSecondaryRoute = isAppChannelsScreen || isChannelSettingsScreen || isBlacklistScreen || isAiConfigScreen
    LaunchedEffect(currentTopLevelScreen, isSecondaryRoute) {
        if (!isSecondaryRoute) {
            val latestIndex = topLevelIndexOf(currentTopLevelScreen)
            if (selectedTopLevelIndex != latestIndex) {
                selectedTopLevelIndex = latestIndex
            }
        }
    }
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
        currentScreen == AppScreen.Apps -> appsTopBarStyleState
        currentScreen == AppScreen.Settings -> settingsTopBarStyleState
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
    fun handleNavigationBack(): Boolean {
        if (dismissTransientUi()) return true
        if (popScreen()) return true
        return if (currentScreen != AppScreen.Home) {
            backStack.clear()
            backStack.add(AppScreen.Home)
            true
        } else {
            (context as? ComponentActivity)?.finish()
            true
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
        isAppChannelsScreen || isChannelSettingsScreen -> "apps"
        isBlacklistScreen || isAiConfigScreen -> "settings"
        currentScreen == AppScreen.Apps -> "apps"
        currentScreen == AppScreen.Settings -> "settings"
        else -> "home"
    }
    val activeTopBarScrollBehavior = when (topBarOwnerRoute) {
        "apps" -> appsScrollBehavior
        "settings" -> settingsScrollBehavior
        else -> homeScrollBehavior
    }
    BackHandler {
        if (!dismissTransientUi()) {
            handleNavigationBack()
        }
    }
    val topBarCollapseProgress by remember(
        currentScreen,
        activeTopBarScrollBehavior.state,
    ) {
        derivedStateOf {
            activeTopBarScrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
        }
    }
    val isAppsLargeTitleExpanded by remember(currentScreen, appsScrollBehavior.state) {
        derivedStateOf {
            isAppsPrimaryScreen && appsScrollBehavior.state.collapsedFraction < 0.98f
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            settingsVm.importConfigFromUri(uri)
        }
    }
    val topLevelSaveableStateHolder = rememberSaveableStateHolder()
    val selectedIndexInBar = topLevelIndexOf(currentScreen)

    LaunchedEffect(Unit) {
        homeVm.events.collect {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) {
        appsVm.events.collect {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) {
        settingsVm.events.collect {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) {
        blacklistVm.events.collect {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    fun SceneContent(scene: AppScreen, innerPadding: PaddingValues) {
        CompositionLocalProvider(LocalContentPadding provides innerPadding) {
            when (scene) {
                AppScreen.Home -> {
                    val uiState by homeVm.uiState.collectAsStateWithLifecycle()
                    HomeScreen(
                        uiState = uiState,
                        onRefresh = homeVm::refreshStatus,
                        onSendTest = homeVm::sendTest,
                        modifier = Modifier.nestedScroll(homeScrollBehavior.nestedScrollConnection),
                    )
                }
                AppScreen.Apps -> {
                    AppsScreen(
                        state = appsState,
                        onRefresh = appsVm::refresh,
                        onQueryChange = appsVm::setQuery,
                        onAppEnabledChange = appsVm::setEnabled,
                        onAppSelectedChange = appsVm::toggleSelectedPackage,
                        onSelectAll = appsVm::setSelectedPackages,
                        onOpenAppChannels = { pkg ->
                            appChannelsEnableAllRequestId = 0
                            appChannelsBatchRequestId = 0
                            navigateTo(AppScreen.AppChannels(pkg))
                        },
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
                AppScreen.Settings -> {
                    SettingsScreen(
                        state = settingsState,
                        onToggle = { key, enabled ->
                            if (key == PrefKeys.USE_FLOATING_NAVIGATION_BAR) {
                                useFloatingNavigationBarUi = enabled
                            }
                            settingsVm.updateSwitch(key, enabled)
                        },
                        onMarqueeSpeed = settingsVm::updateMarqueeSpeed,
                        onBigIslandWidth = settingsVm::updateBigIslandMaxWidth,
                        onThemeModeChange = settingsVm::updateThemeMode,
                        onLocaleChange = settingsVm::updateLocale,
                        onHideDesktopIcon = settingsVm::setDesktopIconHidden,
                        onOpenBlacklist = { navigateTo(AppScreen.Blacklist) },
                        onOpenAiConfig = { navigateTo(AppScreen.AiConfig) },
                        onCheckUpdate = { openExternalUrl(context, GITHUB_RELEASE_URL) },
                        onOpenGithub = { openExternalUrl(context, GITHUB_REPO_URL) },
                        onExportToFile = settingsVm::exportConfigToFile,
                        onPickImportFile = {
                            importLauncher.launch(arrayOf("application/json", "text/plain"))
                        },
                        onExportToClipboard = settingsVm::exportConfigToClipboard,
                        onImportFromClipboard = settingsVm::importConfigFromClipboard,
                        modifier = Modifier.nestedScroll(settingsScrollBehavior.nestedScrollConnection),
                    )
                }
                is AppScreen.AppChannels -> {
                    val vm: AppChannelsViewModel = viewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(scene.packageName) {
                        vm.setPackageName(scene.packageName)
                    }
                    AppChannelsScreen(
                        state = state,
                        onRefresh = vm::refresh,
                        onSetAppEnabled = vm::setAppEnabled,
                        onToggleChannel = vm::toggleChannel,
                        onEnableAllChannels = vm::enableAllChannels,
                        onOpenChannelSettings = { channelId, channelName ->
                            navigateTo(
                                AppScreen.ChannelSettings(
                                    packageName = scene.packageName,
                                    channelId = channelId,
                                    channelName = channelName,
                                ),
                            )
                        },
                        onBatchApplyToEnabledChannels = vm::batchApplyToEnabledChannels,
                        enableAllRequestId = appChannelsEnableAllRequestId,
                        batchRequestId = appChannelsBatchRequestId,
                        modifier = Modifier.nestedScroll(appsScrollBehavior.nestedScrollConnection),
                    )
                }
                is AppScreen.ChannelSettings -> {
                    val vm: AppChannelsViewModel = viewModel()
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(scene.packageName) {
                        vm.setPackageName(scene.packageName)
                    }
                    ChannelSettingsScreen(
                        state = state,
                        channelId = scene.channelId,
                        onRefresh = vm::refresh,
                        onSetTemplate = { vm.setTemplate(scene.channelId, it) },
                        onSetTimeout = { vm.setTimeout(scene.channelId, it) },
                        onSetSetting = { setting, value ->
                            vm.setSetting(scene.channelId, setting, value)
                        },
                        onSetHighlightColor = { vm.setHighlightColor(scene.channelId, it) },
                        modifier = Modifier.nestedScroll(appsScrollBehavior.nestedScrollConnection),
                    )
                }
                AppScreen.Blacklist -> {
                    BlacklistScreen(
                        state = blacklistState,
                        onRefresh = blacklistVm::refresh,
                        onQueryChange = blacklistVm::setQuery,
                        onSetBlacklisted = blacklistVm::setBlacklisted,
                        canPullToRefresh = false,
                        modifier = Modifier.nestedScroll(settingsScrollBehavior.nestedScrollConnection),
                    )
                }
                AppScreen.AiConfig -> {
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
    }

    CompositionLocalProvider(LocalUiLanguage provides uiLanguage) {
        MiuixScaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                MiuiBlurredTopBar(
                    modifier = Modifier.fillMaxWidth(),
                    fallbackBackdrop = activeBackdrop,
                    fallbackBlurColors = topBarFallbackBlurColors,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                    MiuixTopAppBar(
                        title = topBarTitle,
                        scrollBehavior = activeTopBarScrollBehavior,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Transparent,
                        defaultWindowInsetsPadding = false,
                        navigationIcon = {
                            when {
                                isSecondaryRoute -> {
                                    MiuixIconButton(onClick = { handleNavigationBack() }) {
                                        Icon(
                                            imageVector = MiuixIcons.Basic.ArrowRight,
                                            contentDescription = textOf(uiLanguage, "返回", "Back"),
                                            modifier = Modifier.rotate(180f),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                                currentScreen == AppScreen.Apps && appsSelectionMode -> {
                                    MiuixIconButton(onClick = { appsExitSelectionRequestId += 1 }) {
                                        FaIcon(
                                            glyph = FaGlyph.Times,
                                            contentDescription = textOf(uiLanguage, "退出多选", "Exit multi-select"),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                                else -> {
                                    Spacer(modifier = Modifier.size(40.dp))
                                }
                            }
                        },
                        actions = {
                            when {
                                isSecondaryRoute -> {
                                    when {
                                        currentScreen is AppScreen.AppChannels -> {
                                            Box {
                                                BackHandler(enabled = showAppChannelsMenu) {
                                                    showAppChannelsMenu = false
                                                }
                                                MiuixIconButton(onClick = { showAppChannelsMenu = true }) {
                                                    Icon(
                                                        imageVector = MiuixIcons.Regular.MoreCircle,
                                                        contentDescription = textOf(uiLanguage, "渠道页更多操作", "More channel actions"),
                                                        tint = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                }
                                                OverlayListPopup(
                                                    show = showAppChannelsMenu,
                                                    alignment = MiuixPopupPositionProvider.Align.End,
                                                    onDismissRequest = { showAppChannelsMenu = false },
                                                    onDismissFinished = {},
                                                ) {
                                                    val menuItems = listOf(
                                                        textOf(uiLanguage, "启用全部渠道", "Enable all channels") to {
                                                            showAppChannelsMenu = false
                                                            appChannelsEnableAllRequestId += 1
                                                        },
                                                        textOf(uiLanguage, "批量设置渠道配置", "Batch edit channel settings") to {
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
                                        }
                                        currentScreen == AppScreen.Blacklist -> {
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
                                                    contentDescription = textOf(uiLanguage, "搜索", "Search"),
                                                    tint = MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                            Box {
                                                BackHandler(enabled = showBlacklistMenu) {
                                                    showBlacklistMenu = false
                                                }
                                                MiuixIconButton(onClick = { showBlacklistMenu = true }) {
                                                    Icon(
                                                        imageVector = MiuixIcons.Regular.MoreCircle,
                                                        contentDescription = textOf(uiLanguage, "黑名单页更多操作", "More blacklist actions"),
                                                        tint = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                }
                                                OverlayListPopup(
                                                    show = showBlacklistMenu,
                                                    alignment = MiuixPopupPositionProvider.Align.End,
                                                    onDismissRequest = { showBlacklistMenu = false },
                                                    onDismissFinished = {},
                                                ) {
                                                    val menuItems = listOf(
                                                        textOf(uiLanguage, "游戏预设", "Game preset") to {
                                                            showBlacklistMenu = false
                                                            blacklistVm.applyGamePreset()
                                                        },
                                                        textOf(uiLanguage, "全部加入", "Add all visible") to {
                                                            showBlacklistMenu = false
                                                            blacklistVm.enableAllVisible()
                                                        },
                                                        textOf(uiLanguage, "全部移除", "Remove all visible") to {
                                                            showBlacklistMenu = false
                                                            blacklistVm.disableAllVisible()
                                                        },
                                                        (if (blacklistState.showSystemApps) {
                                                            textOf(uiLanguage, "隐藏系统应用", "Hide system apps")
                                                        } else {
                                                            textOf(uiLanguage, "显示系统应用", "Show system apps")
                                                        }) to {
                                                            showBlacklistMenu = false
                                                            blacklistVm.setShowSystemApps(!blacklistState.showSystemApps)
                                                        },
                                                        textOf(uiLanguage, "刷新", "Refresh") to {
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
                                }
                                currentScreen == AppScreen.Home -> {
                                    MiuixIconButton(onClick = { openExternalUrl(context, DOCUMENTATION_URL) }) {
                                        Icon(
                                            imageVector = MiuixIcons.Regular.Info,
                                            contentDescription = textOf(uiLanguage, "文档", "Documentation"),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    MiuixIconButton(onClick = { showSponsorDialog = true }) {
                                        Icon(
                                            imageVector = MiuixIcons.Regular.Create,
                                            contentDescription = textOf(uiLanguage, "赞助", "Sponsor"),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    MiuixIconButton(onClick = { showRestartDialog = true }) {
                                        Icon(
                                            imageVector = MiuixIcons.Regular.Refresh,
                                            contentDescription = textOf(uiLanguage, "重启作用域", "Restart scopes"),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                                currentScreen == AppScreen.Apps -> {
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
                                            contentDescription = textOf(uiLanguage, "搜索", "Search"),
                                            tint = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    if (!appsSelectionMode) {
                                        MiuixIconButton(onClick = { appsSelectionRequestId += 1 }) {
                                            Icon(
                                                imageVector = MiuixIcons.Regular.SelectAll,
                                                contentDescription = textOf(uiLanguage, "进入多选", "Enter multi-select"),
                                                tint = MaterialTheme.colorScheme.onSurface,
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
                                                contentDescription = textOf(uiLanguage, "更多操作", "More actions"),
                                                tint = MaterialTheme.colorScheme.onSurface,
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
                                                    (if (appsState.showSystemApps) {
                                                        textOf(uiLanguage, "隐藏系统应用", "Hide system apps")
                                                    } else {
                                                        textOf(uiLanguage, "显示系统应用", "Show system apps")
                                                    }) to {
                                                        showAppsMenu = false
                                                        appsVm.setShowSystemApps(!appsState.showSystemApps)
                                                    },
                                                    textOf(uiLanguage, "开启已选", "Enable selected") to {
                                                        showAppsMenu = false
                                                        appsEnableSelectedRequestId += 1
                                                    },
                                                    textOf(uiLanguage, "关闭已选", "Disable selected") to {
                                                        showAppsMenu = false
                                                        appsDisableSelectedRequestId += 1
                                                    },
                                                    textOf(uiLanguage, "选中已启用", "Select enabled") to {
                                                        showAppsMenu = false
                                                        appsSelectEnabledRequestId += 1
                                                    },
                                                    textOf(uiLanguage, "批量设置渠道配置", "Batch edit channel settings") to {
                                                        showAppsMenu = false
                                                        appsBatchSelectedRequestId += 1
                                                    },
                                                )
                                            } else {
                                                listOf(
                                                    (if (appsState.showSystemApps) {
                                                        textOf(uiLanguage, "隐藏系统应用", "Hide system apps")
                                                    } else {
                                                        textOf(uiLanguage, "显示系统应用", "Show system apps")
                                                    }) to {
                                                        showAppsMenu = false
                                                        appsVm.setShowSystemApps(!appsState.showSystemApps)
                                                    },
                                                    textOf(uiLanguage, "一键开启全部", "Enable all") to {
                                                        showAppsMenu = false
                                                        appsEnableAllRequestId += 1
                                                    },
                                                    textOf(uiLanguage, "一键关闭全部", "Disable all") to {
                                                        showAppsMenu = false
                                                        appsDisableAllRequestId += 1
                                                    },
                                                    textOf(uiLanguage, "刷新", "Refresh") to {
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
                                else -> Unit
                            }
                        },
                    )

                    if (currentScreen == AppScreen.Apps && (isAppsSearchExpanded || appsSelectionMode)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(animationSpec = tween(durationMillis = 240)),
                        ) {
                            AnimatedVisibility(
                                visible = isAppsSearchExpanded,
                                enter = fadeIn(animationSpec = tween(durationMillis = 240)) +
                                    expandVertically(animationSpec = tween(durationMillis = 240)),
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
                                    label = textOf(uiLanguage, "搜索应用 / 包名", "Search app / package"),
                                    useLabelAsPlaceholder = true,
                                    modifier = Modifier
                                        .focusRequester(appsSearchFocusRequester)
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            AnimatedVisibility(
                                visible = appsSelectionMode,
                                enter = fadeIn(animationSpec = tween(durationMillis = 240)) +
                                    expandVertically(animationSpec = tween(durationMillis = 240)),
                                exit = fadeOut(animationSpec = tween(durationMillis = 180)) +
                                    shrinkVertically(animationSpec = tween(durationMillis = 220)),
                                label = "apps_selection_info_visibility",
                            ) {
                                val visiblePackages = appsState.filteredApps.map { it.packageName }.toSet()
                                val allVisibleSelected = visiblePackages.isNotEmpty() &&
                                    visiblePackages.all { appsState.selectedPackages.contains(it) }
                                val selectAllState = if (allVisibleSelected) {
                                    ToggleableState.On
                                } else if (appsState.selectedPackages.isNotEmpty()) {
                                    ToggleableState.Indeterminate
                                } else {
                                    ToggleableState.Off
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val selectedTextColor = if (isAppInDarkTheme()) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Text(
                                        textOf(
                                            uiLanguage,
                                            "已选择 ${appsState.selectedPackages.size} 项",
                                            "Selected ${appsState.selectedPackages.size}",
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = selectedTextColor,
                                    )
                                    MiuixCheckbox(
                                        state = selectAllState,
                                        onClick = {
                                            if (allVisibleSelected) {
                                                appsVm.setSelectedPackages(appsState.selectedPackages - visiblePackages)
                                            } else {
                                                appsVm.setSelectedPackages(appsState.selectedPackages + visiblePackages)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (currentScreen == AppScreen.Blacklist) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(animationSpec = tween(durationMillis = 240)),
                        ) {
                            AnimatedVisibility(
                                visible = isBlacklistSearchExpanded,
                                enter = fadeIn(animationSpec = tween(durationMillis = 240)) +
                                    expandVertically(animationSpec = tween(durationMillis = 240)),
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
                                    label = textOf(uiLanguage, "搜索应用 / 包名", "Search app / package"),
                                    useLabelAsPlaceholder = true,
                                    modifier = Modifier
                                        .focusRequester(blacklistSearchFocusRequester)
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                    }
                }
            },
            bottomBar = {
                if (!isSecondaryRoute) {
                    HyperCeilerNavigationSwitchBar(
                        items = items,
                        selectedIndex = selectedIndexInBar,
                        style = activeNavStyleState,
                        onDestinationClick = onPrimaryDestinationClick,
                        backdrop = activeBackdrop,
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .let {
                        if (activeBackdrop != null) {
                            it.layerBackdrop(activeBackdrop)
                        } else {
                            it
                        }
                    }
                    .consumeWindowInsets(innerPadding),
            ) {
                AnimatedContent(
                    targetState = currentScreen,
                    label = "scene_content_switch",
                    transitionSpec = {
                    val initialDepth = screenDepth(initialState)
                    val targetDepth = screenDepth(targetState)
                    val initialTopIndex = topLevelIndexOf(initialState)
                    val targetTopIndex = topLevelIndexOf(targetState)
                    if (initialDepth == 1 && targetDepth == 1) {
                        val forward = targetTopIndex > initialTopIndex
                        (
                            slideInHorizontally(
                                animationSpec = tween(durationMillis = 340),
                                initialOffsetX = { full -> if (forward) full / 6 else -full / 6 },
                            ) + fadeIn(animationSpec = tween(durationMillis = 340))
                        ) togetherWith (
                            slideOutHorizontally(
                                animationSpec = tween(durationMillis = 260),
                                targetOffsetX = { full -> if (forward) -full / 12 else full / 12 },
                            ) + fadeOut(animationSpec = tween(durationMillis = 220))
                        )
                    } else if (targetDepth > initialDepth) {
                        (
                            slideInHorizontally(
                                animationSpec = tween(durationMillis = 320),
                                initialOffsetX = { full -> full / 3 },
                            ) + fadeIn(animationSpec = tween(durationMillis = 320))
                        ) togetherWith (
                            slideOutHorizontally(
                                animationSpec = tween(durationMillis = 260),
                                targetOffsetX = { full -> -full / 6 },
                            ) + fadeOut(animationSpec = tween(durationMillis = 220))
                        )
                    } else if (targetDepth < initialDepth) {
                        (
                            slideInHorizontally(
                                animationSpec = tween(durationMillis = 300),
                                initialOffsetX = { full -> -full / 4 },
                            ) + fadeIn(animationSpec = tween(durationMillis = 280))
                        ) togetherWith (
                            slideOutHorizontally(
                                animationSpec = tween(durationMillis = 240),
                                targetOffsetX = { full -> full / 3 },
                            ) + fadeOut(animationSpec = tween(durationMillis = 200))
                        )
                    } else {
                        val forward = targetTopIndex >= initialTopIndex
                        (
                            slideInHorizontally(
                                animationSpec = tween(durationMillis = 280),
                                initialOffsetX = { full -> if (forward) full / 5 else -full / 5 },
                            ) + fadeIn(animationSpec = tween(durationMillis = 280))
                        ) togetherWith (
                            slideOutHorizontally(
                                animationSpec = tween(durationMillis = 220),
                                targetOffsetX = { full -> if (forward) -full / 7 else full / 7 },
                            ) + fadeOut(animationSpec = tween(durationMillis = 200))
                        )
                    }
                },
            ) { scene ->
                val topLevel = topLevelScreen(scene)
                if (screenDepth(scene) == 1) {
                    val stateKey = when (topLevel) {
                        AppScreen.Home -> "top_home"
                        AppScreen.Apps -> "top_apps"
                        else -> "top_settings"
                    }
                    topLevelSaveableStateHolder.SaveableStateProvider(stateKey) {
                        SceneContent(scene = scene, innerPadding = innerPadding)
                    }
                } else {
                    SceneContent(scene = scene, innerPadding = innerPadding)
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
}
@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onSendTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notes = listOf(
        textOf("1.此页面仅用于测试是否支持超级岛，并不代表实际效果", "1. This page only checks whether Dynamic Island is supported and does not represent the final effect."),
        textOf("2.请在 HyperCeiler 中关闭系统界面和小米服务框架的焦点通知白名单", "2. Disable the focus notification whitelist for System UI and Xiaomi Service Framework in HyperCeiler."),
        textOf("3.LSPosed 管理器中激活后，必须重启相关作用域软件", "3. After enabling the module in LSPosed Manager, you must restart the related scope apps."),
        textOf("4.支持通用适配，自行勾选合适的模板尝试", "4. Generic adaptation is supported, so try the templates that fit your app."),
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
                    val statusText = when (uiState.moduleActive) {
                        null -> textOf("检测中", "Checking")
                        true -> textOf("已激活", "Active")
                        false -> textOf("未激活", "Inactive")
                    }
                    val statusAccent = when (uiState.moduleActive) {
                        null -> MaterialTheme.colorScheme.outline
                        true -> Color(0xFF38B46A)
                        false -> MaterialTheme.colorScheme.error
                    }
                    val statusHint = when (uiState.moduleActive) {
                        null -> textOf("正在读取作用域状态", "Reading scope status")
                        true -> textOf("模块与作用域工作正常", "Module and scopes are working correctly")
                        false -> textOf("请检查 LSPosed 激活与作用域重启", "Check LSPosed activation and scope restart")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                textOf("模块状态", "Module Status"),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                statusHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(statusAccent.copy(alpha = 0.14f))
                                .border(
                                    width = 1.dp,
                                    color = statusAccent.copy(alpha = 0.42f),
                                    shape = RoundedCornerShape(999.dp),
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = statusAccent,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    HomeStatusInfoRow(
                        label = "LSPosed API",
                        value = uiState.lsposedApiVersion.toString(),
                    )
                    HomeStatusInfoRow(
                        label = textOf("Focus 协议版本", "Focus Protocol"),
                        value = uiState.focusProtocolVersion.toString(),
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MiuixButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                    Text(textOf("刷新状态", "Refresh Status"), color = MaterialTheme.colorScheme.onBackground)
                }
                MiuixButton(onClick = onSendTest, modifier = Modifier.weight(1f)) {
                    Text(textOf("发送测试通知", "Send Test Notification"), color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
        item {
            MiuixSmallTitle(text = textOf("注意事项", "Notes"))
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
private fun HomeStatusInfoRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor,
        )
    }
}

@Composable
private fun SponsorDialog(show: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isDarkTheme = isAppInDarkTheme()
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
        title = textOf("赞助支持", "Support the Project"),
        summary = textOf("赞助作者", "Support the Author"),
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
                text = textOf("支持项目持续更新，感谢你的认可。", "Thank you for supporting the project and its continued updates."),
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
                        contentDescription = textOf("微信赞助二维码", "WeChat sponsor QR code"),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
            } else {
                Text(
                    text = textOf("未找到赞助图片 assets/images/wechat.jpg", "Sponsor image assets/images/wechat.jpg was not found"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MiuixButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(textOf("关闭", "Close"), color = MaterialTheme.colorScheme.onBackground)
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

    val allSelected = restartSystemUi && restartDownloads && restartXmsf

    OverlayDialog(
        show = show,
        title = textOf("选择需要重启的进程", "Select Processes to Restart"),
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
                text = textOf("选择后将依次重启对应进程并刷新岛通知能力。", "The selected processes will be restarted one by one to refresh Dynamic Island support."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScopeCheckboxRow(
                    title = textOf("系统界面", "System UI"),
                    subtitle = "com.android.systemui",
                    checked = restartSystemUi,
                ) { restartSystemUi = !restartSystemUi }
                ScopeCheckboxRow(
                    title = textOf("下载管理", "Download Manager"),
                    subtitle = "com.android.providers.downloads",
                    checked = restartDownloads,
                ) { restartDownloads = !restartDownloads }
                ScopeCheckboxRow(
                    title = textOf("小米服务框架", "Xiaomi Service Framework"),
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
                    Text(
                        if (allSelected) textOf("全不选", "Deselect All") else textOf("全选", "Select All"),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                MiuixButton(
                    onClick = { onConfirm(restartSystemUi, restartDownloads, restartXmsf) },
                    modifier = Modifier.weight(1f),
                    colors = MiuixButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        text = textOf("确定", "Confirm"),
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
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
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
        "system" to textOf("跟随系统", "Follow System"),
        "light" to textOf("浅色", "Light"),
        "dark" to textOf("深色", "Dark"),
    )
    val selectedThemeIndex = themeModeOptions.indexOfFirst { it.first == state.themeMode }.coerceAtLeast(0)

    val localeOptions = listOf(
        "__system__" to textOf("跟随系统", "Follow System"),
        "zh" to textOf("中文", "Chinese"),
        "en" to "English",
        "ja" to "日本語",
        "tr" to "Türkçe",
    )
    val localeValue = state.locale ?: "__system__"
    val selectedLocaleIndex = localeOptions.indexOfFirst { it.first == localeValue }.coerceAtLeast(0)
    val qqCopiedToast = textOf("群号已复制到剪贴板", "Group number copied to clipboard")

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
            SectionTitle(textOf("AI 增强", "AI"))
            SettingsGroupCard {
                SettingsEntryItem(
                    title = textOf("AI 通知摘要", "AI Notification Summary"),
                    subtitle = if (state.aiEnabled) {
                        textOf("已启用 · 点击配置 AI 参数", "Enabled · Tap to configure AI")
                    } else {
                        textOf("已关闭 · 点击进行配置", "Disabled · Tap to configure")
                    },
                    onClick = onOpenAiConfig,
                )
            }
        }

        item {
            SectionTitle(textOf("通知黑名单", "Notification Blacklist"))
            SettingsGroupCard {
                SettingsEntryItem(
                    title = textOf("通知黑名单", "Notification Blacklist"),
                    subtitle = textOf("启动黑名单应用时，停用焦点通知的自动展开功能", "Disable automatic focus expansion when blacklisted apps are opened"),
                    onClick = onOpenBlacklist,
                )
            }
        }

        item {
            SectionTitle(textOf("行为", "Behavior"))
            SettingsGroupCard {
                ToggleItem(
                    textOf("交互触感", "Interaction Haptics"),
                    textOf("为开关、滑块和按钮启用 Hyper 定制震感反馈", "Enable custom Hyper haptics for switches, sliders, and buttons"),
                    state.interactionHaptics,
                ) { onToggle(PrefKeys.INTERACTION_HAPTICS, it) }
                ToggleItem(
                    textOf("下载管理器暂停后保留焦点通知", "Keep Focus Notification After Pause"),
                    textOf("显示一条通知，点击以继续下载，可能导致状态不同步", "Show a notification you can tap to resume downloads; may cause state desync"),
                    state.resumeNotification,
                ) { onToggle(PrefKeys.RESUME_NOTIFICATION, it) }
                ToggleItem(
                    textOf("移除焦点通知白名单", "Remove Focus Notification Whitelist"),
                    textOf("允许所有应用发送焦点通知，无需系统授权", "Allow all apps to send focus notifications without system approval"),
                    state.unlockAllFocus,
                ) { onToggle(PrefKeys.UNLOCK_ALL_FOCUS, it) }
                ToggleItem(
                    textOf("移除焦点通知签名验证", "Bypass Focus Signature Check"),
                    textOf("允许所有应用向手表/手环发送焦点通知，跳过签名校验（需 Hook 小米服务框架）", "Allow all apps to send focus notifications to watches/bands by skipping signature checks (requires hooking Xiaomi Service Framework)"),
                    state.unlockFocusAuth,
                ) { onToggle(PrefKeys.UNLOCK_FOCUS_AUTH, it) }
                ToggleItem(
                    textOf("显示启动欢迎语", "Show Startup Greeting"),
                    textOf("应用启动时在超级岛显示欢迎信息", "Show a welcome message in Dynamic Island when the app launches"),
                    state.showWelcome,
                ) { onToggle(PrefKeys.SHOW_WELCOME, it) }
                ToggleItem(
                    textOf("隐藏桌面图标", "Hide Launcher Icon"),
                    textOf("隐藏启动器中的应用图标，隐藏后可通过 LSPosed 管理器打开", "Hide the launcher icon. After hiding, open the app from LSPosed Manager"),
                    state.hideDesktopIcon,
                    onHideDesktopIcon,
                )
                ToggleItem(
                    textOf("启动时检查更新", "Check for Updates on Launch"),
                    textOf("启动应用时自动检查是否有新版本", "Automatically check for a new version when the app starts"),
                    state.checkUpdateOnLaunch,
                ) { onToggle(PrefKeys.CHECK_UPDATE_ON_LAUNCH, it) }
            }
        }

        item {
            SectionTitle(textOf("渠道默认配置", "Default Channel Settings"))
            SettingsGroupCard {
                ToggleItem(
                    textOf("初次展开", "Expand on First Arrival"),
                    textOf("超级岛初次收到通知后是否展开为焦点通知", "Whether Dynamic Island should expand into a focus notification the first time one arrives"),
                    state.defaultFirstFloat,
                ) { onToggle(PrefKeys.DEFAULT_FIRST_FLOAT, it) }
                ToggleItem(
                    textOf("更新展开", "Expand on Update"),
                    textOf("超级岛更新后是否展开通知", "Whether Dynamic Island should expand when the notification updates"),
                    state.defaultEnableFloat,
                ) { onToggle(PrefKeys.DEFAULT_ENABLE_FLOAT, it) }
                ToggleItem(
                    textOf("消息滚动速度", "Marquee Speed"),
                    "",
                    state.defaultMarquee,
                ) { onToggle(PrefKeys.DEFAULT_MARQUEE, it) }
                ToggleItem(
                    textOf("高亮动态取色", "Dynamic Highlight Color"),
                    textOf("开启后默认使用图标自动取色", "Use icon-based automatic color extraction by default"),
                    state.defaultDynamicHighlightColor,
                ) { onToggle(PrefKeys.DEFAULT_DYNAMIC_HIGHLIGHT_COLOR, it) }
                ToggleItem(
                    textOf("外圈光效", "Outer Glow"),
                    "",
                    state.defaultOuterGlow,
                ) { onToggle(PrefKeys.DEFAULT_OUTER_GLOW, it) }
                ToggleItem(
                    textOf("焦点通知", "Focus Notification"),
                    textOf("替换通知为焦点通知（关闭后显示原始通知）", "Replace notifications with focus notifications; disable to keep original notifications"),
                    state.defaultFocusNotif,
                ) { onToggle(PrefKeys.DEFAULT_FOCUS_NOTIF, it) }
                ToggleItem(
                    textOf("锁屏通知复原", "Restore on Lock Screen"),
                    textOf("锁屏时跳过焦点通知处理，保持原始通知隐私行为", "Skip focus notification handling on the lock screen to preserve the original privacy behavior"),
                    state.defaultRestoreLockscreen,
                ) { onToggle(PrefKeys.DEFAULT_RESTORE_LOCKSCREEN, it) }
                ToggleItem(
                    textOf("大岛图标", "Large Island Icon"),
                    textOf("开启后显示超级岛的大图标（小岛不受影响）", "Show the large icon in Dynamic Island; the small island is unaffected"),
                    state.defaultShowIslandIcon,
                ) { onToggle(PrefKeys.DEFAULT_SHOW_ISLAND_ICON, it) }
                ToggleItem(
                    textOf("状态栏图标", "Status Bar Icon"),
                    textOf("焦点通知打开时，是否强制保留状态栏小图标", "Force the small status bar icon to remain visible when focus notifications are enabled"),
                    state.defaultPreserveSmallIcon,
                ) { onToggle(PrefKeys.DEFAULT_PRESERVE_SMALL_ICON, it) }
            }
        }

        item {
            SectionTitle(textOf("外观", "Appearance"))
            SettingsGroupCard {
                ToggleItem(
                    textOf("使用应用图标", "Use App Icon"),
                    textOf("下载管理器通知使用应用图标", "Use the app icon for Download Manager notifications"),
                    state.useHookAppIcon,
                ) { onToggle(PrefKeys.USE_HOOK_APP_ICON, it) }
                ToggleItem(
                    textOf("图标圆角", "Rounded Icons"),
                    textOf("为通知图标添加圆角效果", "Apply rounded corners to notification icons"),
                    state.roundIcon,
                ) { onToggle(PrefKeys.ROUND_ICON, it) }
                ToggleItem(
                    textOf("悬浮底部导航栏", "Floating Bottom Navigation"),
                    "",
                    state.useFloatingNavigationBar,
                ) { onToggle(PrefKeys.USE_FLOATING_NAVIGATION_BAR, it) }
                SliderItem(
                    title = textOf("消息滚动速度", "Marquee Speed"),
                    subtitle = "",
                    valueText = "${state.marqueeSpeed} px/s",
                    value = state.marqueeSpeed.toFloat(),
                    defaultValue = DEFAULT_MARQUEE_SPEED.toFloat(),
                    valueRange = 20f..500f,
                    steps = 48,
                    onValueChange = { onMarqueeSpeed(it.toInt()) },
                    onResetToDefault = { onMarqueeSpeed(DEFAULT_MARQUEE_SPEED) },
                )
                ToggleSliderItem(
                    textOf("修改超级岛最大宽度", "Adjust Dynamic Island Max Width"),
                    "",
                    state.bigIslandMaxWidthEnabled,
                    valueText = "${state.bigIslandMaxWidth} dp",
                    value = state.bigIslandMaxWidth.toFloat(),
                    defaultValue = DEFAULT_BIG_ISLAND_MAX_WIDTH.toFloat(),
                    valueRange = 50f..500f,
                    steps = 54,
                    onCheckedChange = { onToggle(PrefKeys.BIG_ISLAND_MAX_WIDTH_ENABLED, it) },
                    onValueChange = { onBigIslandWidth(it.toInt()) },
                    onResetToDefault = { onBigIslandWidth(DEFAULT_BIG_ISLAND_MAX_WIDTH) },
                )
                MiuixOverlayDropdownPreference(
                    title = textOf("颜色模式", "Color Mode"),
                    items = themeModeOptions.map { it.second },
                    selectedIndex = selectedThemeIndex,
                    renderInRootScaffold = false,
                    onSelectedIndexChange = { index ->
                        val next = themeModeOptions.getOrNull(index)?.first ?: return@MiuixOverlayDropdownPreference
                        onThemeModeChange(next)
                    },
                )
                MiuixOverlayDropdownPreference(
                    title = textOf("语言", "Language"),
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
            SectionTitle(textOf("配置", "Configuration"))
            SettingsGroupCard {
                SettingsEntryItem(
                    textOf("导出到文件", "Export to File"),
                    textOf("将配置保存为 JSON 文件", "Save the configuration as a JSON file"),
                    onExportToFile,
                )
                SettingsEntryItem(
                    textOf("导出到剪贴板", "Export to Clipboard"),
                    textOf("将配置复制为 JSON 文本", "Copy the configuration as JSON text"),
                    onExportToClipboard,
                )
                SettingsEntryItem(
                    textOf("从文件导入", "Import from File"),
                    textOf("从 JSON 文件恢复配置", "Restore the configuration from a JSON file"),
                    onPickImportFile,
                )
                SettingsEntryItem(
                    textOf("从剪贴板导入", "Import from Clipboard"),
                    textOf("从剪贴板中的 JSON 文本恢复配置", "Restore the configuration from JSON text in the clipboard"),
                    onImportFromClipboard,
                )
            }
        }

        item {
            SectionTitle(textOf("关于", "About"))
            SettingsGroupCard {
                SettingsEntryItem(textOf("检查更新", "Check for Updates"), "", onCheckUpdate)
                SettingsEntryItem("GitHub", "1812z/HyperIsland", onOpenGithub)
                SettingsEntryItem(
                    title = textOf("QQ 交流群", "QQ Group"),
                    subtitle = QQ_GROUP_NUMBER,
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("qq_group", QQ_GROUP_NUMBER))
                        Toast.makeText(context, qqCopiedToast, Toast.LENGTH_SHORT).show()
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
                bigIslandMaxWidth = 320,
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
                        title = screenTitle(
                            UiLanguage.Chinese,
                            when (selectedRoute) {
                                "apps" -> AppScreen.Apps
                                "settings" -> AppScreen.Settings
                                else -> AppScreen.Home
                            },
                        ),
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
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
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
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SliderResetButton(
                    show = showResetButton,
                    onClick = onResetToDefault,
                )
                Text(
                    valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
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
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
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
                    SliderResetButton(
                        show = showResetButton,
                        onClick = onResetToDefault,
                    )
                    Text(
                        valueText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(72.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderResetButton(
    show: Boolean,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        if (!show) return@Box
        MiuixIconButton(
            onClick = onClick,
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Refresh,
                contentDescription = textOf("恢复默认值", "Reset to default"),
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

