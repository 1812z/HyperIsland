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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import io.github.hyperisland.compose.component.AppModalHost
import io.github.hyperisland.compose.component.StartupSplashOverlay
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.component.PrivacyConsentDialog
import io.github.hyperisland.compose.navigation.HyperIslandApp
import io.github.hyperisland.compose.page.onboarding.OnboardingPage
import io.github.hyperisland.compose.service.AnalyticsService
import io.github.hyperisland.compose.service.PrivacyConsentStore
import io.github.hyperisland.compose.service.TestNotificationService
import io.github.hyperisland.compose.theme.AppWallpaperBackground
import io.github.hyperisland.compose.theme.HyperIslandTheme
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

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
            // 启动遮罩：壁纸解码完成（onShellReady）且至少展示 SPLASH_MIN_SHOW_MS 之后淡出。
            // 只有冷启动（没有恢复状态）才显示；配置变更 / 进程恢复重建时不该闪一下启动页。
            val showStartupSplash = savedInstanceState == null
            var shellReady by remember { mutableStateOf(!showStartupSplash) }
            var splashMinElapsed by remember { mutableStateOf(!showStartupSplash) }
            LaunchedEffect(Unit) {
                delay(SPLASH_MIN_SHOW_MS)
                splashMinElapsed = true
            }
            LaunchedEffect(Unit) {
                // 壁纸解码抛非 IOException 时 busy 会一直是 true，兜底放行，别把用户卡在启动页。
                delay(SPLASH_MAX_WAIT_MS)
                shellReady = true
            }
            HyperIslandTheme(
                repository = repository,
                onShellReady = { shellReady = true },
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AppModalHost {
                        when {
                            !onboardingCompleted -> OnboardingPage(
                                prefs = repository,
                                showCloseButton = false,
                                onPrivacyAccepted = {
                                    privacyPolicyAccepted = true
                                    trackAppLaunch(isNewUser)
                                    startBackgroundTasks(prefs)
                                },
                                onFinished = { onboardingCompleted = true },
                            )
                            privacyPolicyAccepted -> HyperIslandApp(repository)
                            else -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surface),
                                ) {
                                    AppWallpaperBackground(Modifier.matchParentSize())
                                }
                                PrivacyConsentDialog(
                                    show = true,
                                    onViewPolicy = ::openPrivacyPolicy,
                                    onAccept = {
                                        if (PrivacyConsentStore.accept(this@MainActivity)) {
                                            privacyPolicyAccepted = true
                                            trackAppLaunch(isNewUser)
                                            startBackgroundTasks(prefs)
                                        }
                                    },
                                    onReject = ::finishAffinity,
                                )
                            }
                        }
                    }
                    StartupSplashOverlay(visible = !(shellReady && splashMinElapsed))
                }
            }
        }

        if (privacyPolicyAcceptedAtLaunch) {
            startBackgroundTasks(prefs)
        }
    }

    override fun onStart() {
        super.onStart()
        if (PrivacyConsentStore.isAccepted(this)) {
            val prefs = FlutterPrefsRepository(this)
            trackAppLaunch(!prefs.getBoolean("pref_onboarding_completed", false))
        }
    }

    private fun trackAppLaunch(isNewUser: Boolean) {
        Thread {
            XposedPrefsSyncApp.awaitReady()
            AnalyticsService.trackEnvironmentSnapshot(applicationContext, isNewUser)
        }.start()
    }

    private fun startBackgroundTasks(prefs: FlutterPrefsRepository) {
        if (!backgroundTasksStarted.compareAndSet(false, true)) return
        Thread {
            val xposedReady = XposedPrefsSyncApp.awaitReady()
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

        /** 启动遮罩最短展示时长：壁纸已就绪时也不至于一闪而过。 */
        const val SPLASH_MIN_SHOW_MS = 300L

        /** 启动遮罩最长停留时间：壁纸迟迟不就绪也要放行。 */
        const val SPLASH_MAX_WAIT_MS = 1200L
    }
}
