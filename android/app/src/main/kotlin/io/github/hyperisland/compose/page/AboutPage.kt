package io.github.hyperisland.compose.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hyperisland.BuildConfig
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.LocalRootBottomBarPadding
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsAction
import io.github.hyperisland.compose.component.SettingsActionWithArrow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

@Composable
internal fun AboutPage(
    isActive: Boolean,
    openLegacy: (String) -> Unit,
    onOpenBackupRestore: () -> Unit,
    onOpenReferences: () -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val density = LocalDensity.current
    val heroHeight = screenHeight * HERO_HEIGHT_FRACTION
    val heroHeightPx = with(density) { heroHeight.toPx() }
    val backgroundFadeDistance = with(density) { 389.dp.toPx() }
    val logoFadeStart = heroHeightPx * 0.25f
    val logoFadeDistance = heroHeightPx * 0.35f
    val scrollOffset by remember(listState, heroHeightPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                heroHeightPx
            } else {
                listState.firstVisibleItemScrollOffset.toFloat()
            }
        }
    }
    val reachedListEnd by remember(listState) {
        derivedStateOf {
            !listState.canScrollForward &&
                (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
        }
    }
    val backgroundAlpha = if (reachedListEnd) {
        0f
    } else {
        1f - (scrollOffset / backgroundFadeDistance).coerceIn(0f, 1f)
    }
    val logoProgress = if (reachedListEnd) {
        1f
    } else {
        ((scrollOffset - logoFadeStart) / logoFadeDistance).coerceIn(0f, 1f)
    }
    val logoAlpha = 1f - logoProgress
    val logoScale = 1f - logoProgress * 0.1f
    val snackbarState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.compose_group_number_copied)
    val animationTime = rememberAboutAnimationTime(isActive)
    val darkMode = isSystemInDarkTheme()
    val gradientColors = animatedGradientColors(animationTime, darkMode)
    val backgroundColor = MiuixTheme.colorScheme.background
    val logoBackdrop = if (isRuntimeShaderSupported()) {
        rememberLayerBackdrop {
            drawRect(backgroundColor)
            drawContent()
        }
    } else {
        null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
                AnimatedAboutBackground(
                    animationTime = animationTime,
                    colors = gradientColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight + 180.dp)
                        .alpha(backgroundAlpha)
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                            translationY = -listState.firstVisibleItemScrollOffset * 0.12f
                        }
                        .then(
                            if (logoBackdrop != null) {
                                Modifier.layerBackdrop(logoBackdrop)
                            } else {
                                Modifier
                            },
                        ),
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 0.dp,
                        end = 16.dp,
                        bottom = padding.calculateBottomPadding() + 28.dp + LocalRootBottomBarPadding.current,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.height(heroHeight + DEVELOPER_TOP_GAP))
                            SectionTitle(stringResource(R.string.compose_about_developer))
                            DeveloperCard()
                        }
                    }
                    item {
                        SectionTitle(stringResource(R.string.compose_about_discussion))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            SettingsAction(
                                title = stringResource(R.string.compose_telegram),
                                icon = MiuixIcons.Messages,
                                summary = stringResource(R.string.compose_telegram_summary),
                                endIcon = MiuixIcons.Link,
                                endIconSize = 26.dp,
                            ) {
                                context.openUrl(TELEGRAM_URL)
                            }
                            SettingsAction(
                                title = stringResource(R.string.compose_qq_group),
                                icon = MiuixIcons.Messages,
                                summary = stringResource(R.string.compose_qq_group_summary),
                                endIcon = MiuixIcons.Copy,
                                endIconSize = 26.dp,
                            ) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText(QQ_CLIP_LABEL, QQ_GROUP_NUMBER))
                                scope.launch { snackbarState.showSnackbar(copiedMessage) }
                            }
                        }
                    }
                    item {
                        SectionTitle(stringResource(R.string.compose_about_module))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            SettingsActionWithArrow(
                                title = stringResource(R.string.compose_backup_restore),
                                icon = MiuixIcons.Backup,
                            ) {
                                onOpenBackupRestore()
                            }
                            SettingsAction(stringResource(R.string.compose_check_update_action), MiuixIcons.Update) {
                                openLegacy("/settings")
                            }
                        }
                    }
                    item {
                        SectionTitle(stringResource(R.string.compose_about_project))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            SettingsAction(
                                title = stringResource(R.string.compose_github),
                                icon = MiuixIcons.Info,
                                summary = stringResource(R.string.compose_github_summary),
                                endIcon = MiuixIcons.Link,
                                endIconSize = 26.dp,
                            ) {
                                context.openUrl(GITHUB_URL)
                            }
                            SettingsActionWithArrow(
                                title = stringResource(R.string.compose_references),
                                icon = MiuixIcons.Info,
                                onClick = onOpenReferences,
                            )
                        }
                    }
                }
                AboutHero(
                    animationTime = animationTime,
                    gradientColors = gradientColors,
                    backdrop = logoBackdrop,
                    darkMode = darkMode,
                    logoAlpha = logoAlpha,
                    logoScale = logoScale,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .height(heroHeight)
                        .padding(horizontal = 16.dp),
                )
        }
    }
}

@Composable
private fun AboutHero(
    animationTime: Float,
    gradientColors: List<Color>,
    backdrop: LayerBackdrop?,
    darkMode: Boolean,
    logoAlpha: Float,
    logoScale: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .offset(y = HERO_CONTENT_OFFSET)
                .graphicsLayer {
                    alpha = logoAlpha
                    scaleX = logoScale
                    scaleY = logoScale
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BackgroundBlendedArtwork(
                resourceId = R.drawable.about_logo_mark,
                animationTime = animationTime,
                colors = gradientColors,
                backdrop = backdrop,
                darkMode = darkMode,
                blurRadius = 200f,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .size(90.dp),
            )
            Spacer(Modifier.height(20.dp))
            BackgroundBlendedArtwork(
                resourceId = R.drawable.about_wordmark,
                animationTime = animationTime,
                colors = gradientColors,
                backdrop = backdrop,
                darkMode = darkMode,
                blurRadius = 150f,
                shape = RoundedCornerShape(12.dp),
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .width(280.dp)
                    .height(40.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})",
                fontSize = 15.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun BackgroundBlendedArtwork(
    resourceId: Int,
    animationTime: Float,
    colors: List<Color>,
    backdrop: LayerBackdrop?,
    darkMode: Boolean,
    blurRadius: Float,
    shape: Shape,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val blendColors = remember(darkMode) { aboutArtworkBlendColors(darkMode) }
    val fallbackBrush = animatedGradientBrush(animationTime, colors)
    val effectModifier = if (backdrop != null) {
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = blurRadius,
            noiseCoefficient = 0f,
            colors = BlurColors(blendColors = blendColors),
            contentBlendMode = BlendMode.DstIn,
        )
    } else {
        Modifier.colorfulMask(fallbackBrush)
    }
    Image(
        painter = painterResource(resourceId),
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier.then(effectModifier),
    )
}

@Composable
private fun DeveloperCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(),
        onClick = { context.openUrl(DEVELOPER_GITHUB_URL) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.about_developer_avatar),
                contentDescription = stringResource(R.string.compose_developer_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.68f), CircleShape),
            )
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    text = stringResource(R.string.compose_developer_name),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.compose_developer_handle),
                    modifier = Modifier.padding(top = 1.dp),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "›",
                fontSize = 32.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

@Composable
private fun AnimatedAboutBackground(
    animationTime: Float,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawAboutGradientField(
            animationTime = animationTime,
            colors = colors,
            fieldSize = size,
            sampleOrigin = Offset.Zero,
        )
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.White,
                    0.68f to Color.White,
                    1f to Color.Transparent,
                ),
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}

private fun Modifier.colorfulMask(brush: Brush): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithCache {
    onDrawWithContent {
        drawContent()
        drawRect(brush = brush, blendMode = BlendMode.SrcIn)
    }
}

private fun aboutArtworkBlendColors(darkMode: Boolean): List<BlendColorEntry> =
    if (darkMode) {
        listOf(
            BlendColorEntry(Color(0xE6A1A1A1), BlurBlendMode.ColorDodge),
            BlendColorEntry(Color(0x4DE6E6E6), BlurBlendMode.LinearLight),
            BlendColorEntry(Color(0xFF1AF500), BlurBlendMode.Lab),
        )
    } else {
        listOf(
            BlendColorEntry(Color(0xCC4A4A4A), BlurBlendMode.ColorBurn),
            BlendColorEntry(Color(0xFF4F4F4F), BlurBlendMode.LinearLight),
            BlendColorEntry(Color(0xFF1AF200), BlurBlendMode.Lab),
        )
    }

private fun DrawScope.drawAboutGradientField(
    animationTime: Float,
    colors: List<Color>,
    fieldSize: Size,
    sampleOrigin: Offset,
    blendMode: BlendMode = BlendMode.SrcOver,
) {
    val strengthenedColors = colors.map(::strengthenGradientColor)
    val translucentPalette = strengthenedColors.any { it.alpha < 0.8f }
    val radius = fieldSize.maxDimension * 0.62f
    val motionTime = animationTime * BACKGROUND_SPEED
    drawRect(
        brush = Brush.linearGradient(
            colors = strengthenedColors.map { color ->
                color.copy(
                    alpha = if (translucentPalette) {
                        color.alpha * 0.72f
                    } else {
                        0.58f
                    },
                )
            },
            start = Offset(-sampleOrigin.x, -sampleOrigin.y),
            end = Offset(
                fieldSize.width - sampleOrigin.x,
                fieldSize.height - sampleOrigin.y,
            ),
        ),
        blendMode = blendMode,
    )
    val centers = listOf(
        Offset(
            x = fieldSize.width * (0.18f + 0.10f * sin(motionTime)),
            y = fieldSize.height * (0.20f + 0.08f * cos(motionTime * 0.8f)),
        ),
        Offset(
            x = fieldSize.width * (0.82f + 0.10f * cos(motionTime * 0.9f)),
            y = fieldSize.height * (0.78f + 0.10f * sin(motionTime * 0.7f)),
        ),
        Offset(
            x = fieldSize.width * (0.22f + 0.12f * cos(motionTime * 0.65f)),
            y = fieldSize.height * (0.80f + 0.08f * sin(motionTime * 0.85f)),
        ),
        Offset(
            x = fieldSize.width * (0.80f + 0.12f * sin(motionTime * 0.72f)),
            y = fieldSize.height * (0.20f + 0.08f * cos(motionTime * 0.62f)),
        ),
    )
    centers.forEachIndexed { index, globalCenter ->
        val color = strengthenedColors[index]
        val localCenter = globalCenter - sampleOrigin
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(
                        alpha = if (translucentPalette) {
                            color.alpha * 0.96f
                        } else {
                            0.88f
                        },
                    ),
                    color.copy(alpha = 0f),
                ),
                center = localCenter,
                radius = radius,
            ),
            center = localCenter,
            radius = radius,
            blendMode = blendMode,
        )
    }
}

@Composable
private fun rememberAboutAnimationTime(running: Boolean): Float {
    var animationTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var previousFrame = 0L
        while (true) {
            withFrameNanos { frameTime ->
                if (previousFrame != 0L) {
                    val deltaSeconds = (frameTime - previousFrame) / 1_000_000_000f
                    animationTime += deltaSeconds
                }
                previousFrame = frameTime
            }
        }
    }
    return animationTime
}

private fun animatedGradientBrush(
    animationTime: Float,
    colors: List<Color>,
): Brush {
    val center = Offset(310f, 90f)
    val vector = Offset(
        x = cos(animationTime * BACKGROUND_SPEED) * 620f,
        y = sin(animationTime * BACKGROUND_SPEED) * 180f,
    )
    val opaqueColors = colors.map { it.copy(alpha = 1f) }
    return Brush.linearGradient(
        colors = opaqueColors + opaqueColors.first(),
        start = center - vector,
        end = center + vector,
    )
}

private fun strengthenGradientColor(color: Color): Color {
    val average = (color.red + color.green + color.blue) / 3f
    val saturation = 1.18f
    val brightnessOffset = 0.015f
    return Color(
        red = (average + (color.red - average) * saturation - brightnessOffset).coerceIn(0f, 1f),
        green = (average + (color.green - average) * saturation - brightnessOffset).coerceIn(0f, 1f),
        blue = (average + (color.blue - average) * saturation - brightnessOffset).coerceIn(0f, 1f),
        alpha = color.alpha,
    )
}

private fun animatedGradientColors(
    animationTime: Float,
    dark: Boolean,
): List<Color> {
    val palettes = if (dark) DarkGradientPalettes else LightGradientPalettes
    val segmentValue = animationTime / COLOR_INTERPOLATION_SECONDS
    val segment = floor(segmentValue).toInt() % 4
    val rawProgress = segmentValue - floor(segmentValue)
    val progress = rawProgress * rawProgress * (3f - 2f * rawProgress)
    val start = when (segment) {
        0 -> palettes[1]
        1 -> palettes[0]
        2 -> palettes[1]
        else -> palettes[2]
    }
    val end = when (segment) {
        0 -> palettes[0]
        1 -> palettes[1]
        2 -> palettes[2]
        else -> palettes[1]
    }
    return start.indices.map { index -> lerp(start[index], end[index], progress) }
}

private fun Context.openUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private const val GITHUB_URL = "https://github.com/1812z/HyperIsland"
private const val DEVELOPER_GITHUB_URL = "https://github.com/1812z"
private const val TELEGRAM_URL = "https://t.me/HyperIsland_Module"
private const val QQ_GROUP_NUMBER = "1045114341"
private const val QQ_CLIP_LABEL = "QQ"
private const val BACKGROUND_SPEED = 0.12f
private const val COLOR_INTERPOLATION_SECONDS = 12f
private const val HERO_HEIGHT_FRACTION = 0.60f
private val DEVELOPER_TOP_GAP = 16.dp
private val HERO_CONTENT_OFFSET = 30.dp

private val LightGradientPalettes = listOf(
    listOf(Color(1f, 0.90f, 0.94f), Color(1f, 0.84f, 0.89f), Color(0.97f, 0.73f, 0.82f), Color(0.64f, 0.65f, 0.98f)),
    listOf(Color(0.58f, 0.74f, 1f), Color(1f, 0.90f, 0.93f), Color(0.74f, 0.76f, 1f), Color(0.97f, 0.77f, 0.84f)),
    listOf(Color(0.98f, 0.86f, 0.90f), Color(0.60f, 0.73f, 0.98f), Color(0.92f, 0.93f, 1f), Color(0.56f, 0.69f, 1f)),
)

private val DarkGradientPalettes = listOf(
    listOf(Color(0.20f, 0.06f, 0.88f, 0.40f), Color(0.30f, 0.14f, 0.55f, 0.50f), Color(0f, 0.64f, 0.96f, 0.50f), Color(0.11f, 0.16f, 0.83f, 0.40f)),
    listOf(Color(0.07f, 0.15f, 0.79f, 0.50f), Color(0.62f, 0.21f, 0.67f, 0.50f), Color(0.06f, 0.25f, 0.84f, 0.50f), Color(0f, 0.20f, 0.78f, 0.50f)),
    listOf(Color(0.58f, 0.30f, 0.74f, 0.40f), Color(0.27f, 0.18f, 0.60f, 0.50f), Color(0.66f, 0.26f, 0.62f, 0.50f), Color(0.12f, 0.16f, 0.70f, 0.60f)),
)
