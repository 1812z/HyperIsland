package io.github.hyperisland.compose.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import io.github.hyperisland.R

private const val SPLASH_EXIT_MS = 260

/** 与系统启动窗口共用图标和背景，在壁纸就绪后整层淡出。 */
@Composable
internal fun StartupSplashOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = EnterTransition.None,
        exit = fadeOut(tween(SPLASH_EXIT_MS, easing = FastOutSlowInEasing)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 与系统启动窗口的 windowSplashScreenBackground 同一个资源，衔接时不会有色差。
                .background(colorResource(R.color.splash_background)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_startup_splash),
                contentDescription = null,
                modifier = Modifier.requiredSize(dimensionResource(R.dimen.splash_icon_canvas_size)),
            )
        }
    }
}
