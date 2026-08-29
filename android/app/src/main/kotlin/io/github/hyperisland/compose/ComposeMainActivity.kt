package io.github.hyperisland.compose

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.navigation.HyperIslandApp
import io.github.hyperisland.compose.page.onboarding.OnboardingPage
import io.github.hyperisland.compose.theme.HyperIslandTheme

class ComposeMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = FlutterPrefsRepository(this)
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        val systemInDarkTheme =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        val useDarkSystemBars = when (prefs.getString("pref_theme_mode", "system")) {
            "light" -> false
            "dark" -> true
            else -> systemInDarkTheme
        }
        val systemBarStyle = if (useDarkSystemBars) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        // Explicit light/dark styles keep the gesture area transparent instead of
        // enabling Android's automatic navigation-bar contrast scrim.
        enableEdgeToEdge(
            statusBarStyle = systemBarStyle,
            navigationBarStyle = systemBarStyle,
        )
        setContent {
            val repository = remember { FlutterPrefsRepository(this) }
            var onboardingCompleted by remember {
                mutableStateOf(repository.getBoolean("pref_onboarding_completed", false))
            }
            HyperIslandTheme(repository) {
                if (onboardingCompleted) {
                    HyperIslandApp(repository)
                } else {
                    OnboardingPage(
                        prefs = repository,
                        showCloseButton = false,
                        onFinished = { onboardingCompleted = true },
                    )
                }
            }
        }
    }
}
