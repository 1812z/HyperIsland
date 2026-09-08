package io.github.hyperisland

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.component.PrivacyConsentDialog
import io.github.hyperisland.compose.navigation.HyperIslandApp
import io.github.hyperisland.compose.page.onboarding.OnboardingPage
import io.github.hyperisland.compose.service.AnalyticsService
import io.github.hyperisland.compose.service.PrivacyConsentStore
import io.github.hyperisland.compose.service.TestNotificationService
import io.github.hyperisland.compose.theme.HyperIslandTheme
import java.util.concurrent.atomic.AtomicBoolean

/** 纯 Android/Compose 应用入口；保留原类名以兼容系统设置入口和外部显式 Intent。 */
class MainActivity : ComponentActivity() {
    private val backgroundTasksStarted = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = FlutterPrefsRepository(this)
        val isNewUser = !prefs.getBoolean("pref_onboarding_completed", false)
        val privacyPolicyAcceptedAtLaunch = PrivacyConsentStore.isAccepted(this)
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
        enableEdgeToEdge(
            statusBarStyle = systemBarStyle,
            navigationBarStyle = systemBarStyle,
        )
        setContent {
            val repository = remember { FlutterPrefsRepository(this) }
            var privacyPolicyAccepted by remember {
                mutableStateOf(privacyPolicyAcceptedAtLaunch)
            }
            var onboardingCompleted by remember {
                mutableStateOf(repository.getBoolean("pref_onboarding_completed", false))
            }
            HyperIslandTheme(repository) {
                when {
                    !onboardingCompleted -> OnboardingPage(
                        prefs = repository,
                        showCloseButton = false,
                        onPrivacyAccepted = {
                            privacyPolicyAccepted = true
                            startBackgroundTasks(prefs, isNewUser)
                        },
                        onFinished = { onboardingCompleted = true },
                    )
                    privacyPolicyAccepted -> HyperIslandApp(repository)
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surface),
                        )
                        PrivacyConsentDialog(
                            show = true,
                            onViewPolicy = ::openPrivacyPolicy,
                            onAccept = {
                                if (PrivacyConsentStore.accept(this)) {
                                    privacyPolicyAccepted = true
                                    startBackgroundTasks(prefs, isNewUser)
                                }
                            },
                            onReject = ::finishAffinity,
                        )
                    }
                }
            }
        }

        if (privacyPolicyAcceptedAtLaunch) {
            startBackgroundTasks(prefs, isNewUser)
        }
    }

    private fun startBackgroundTasks(prefs: FlutterPrefsRepository, isNewUser: Boolean) {
        if (!backgroundTasksStarted.compareAndSet(false, true)) return
        Thread {
            val xposedReady = XposedPrefsSyncApp.awaitReady()
            AnalyticsService.trackEnvironmentSnapshot(applicationContext, isNewUser)
            if (xposedReady && prefs.getBoolean("pref_show_welcome", true)) {
                TestNotificationService.sendWelcome(applicationContext)
            }
        }.start()
    }

    private fun openPrivacyPolicy() {
        val language = resources.configuration.locales.get(0).language
        val path = if (language.equals("zh", ignoreCase = true)) "privacy" else "en/privacy"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$PRIVACY_POLICY_BASE_URL/$path")))
    }

    private companion object {
        const val PRIVACY_POLICY_BASE_URL = "https://hyperisland.1812z.top"
    }
}
