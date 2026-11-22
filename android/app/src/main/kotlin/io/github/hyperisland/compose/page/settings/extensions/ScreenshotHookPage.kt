package io.github.hyperisland.compose.page.settings.extensions

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.AppCard
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.page.settings.appearance.LongPreferenceSlider
import io.github.hyperisland.compose.page.settings.appearance.SliderUnit
import io.github.hyperisland.screenshot.ScreenshotContract
import io.github.hyperisland.compose.service.XposedScopeService
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHost

/**
 * 截屏 Hook 拓展：截屏期间临时隐藏超级岛。
 *
 * 走官方隐藏岛链路 `DynamicIslandWindowViewController.statusBarAppearance(boolean)`，
 * 不遍历岛的 View、不改子 View 的可见性，最终由系统状态流派发到
 * `DynamicIslandWindowView.onIslandTempHide(...)`，柔光玻璃租约按系统语义暂停 / 恢复。
 */
@Composable
internal fun ScreenshotHookPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actions = rememberHookScopeActions()
    val scopeFailed = stringResource(R.string.ext_scope_failed)
    val restartRequired = stringResource(R.string.restart_scope_app)

    val tempHide = rememberBooleanPreference(prefs, KEY_SCREENSHOT_TEMP_HIDE, false)
    val scopeRequestPending = remember { mutableStateOf(false) }

    HookExtensionScaffold(
        title = stringResource(R.string.ext_screenshot),
        onBack = onBack,
        snackbarHost = { SnackbarHost(actions.snackbar) },
        restartPackages = RESTART_SCOPE_SCREENSHOT,
    ) {
        item {
            SectionTitle(stringResource(R.string.config))
            AppCard(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.ext_screenshot_temp_hide),
                    summary = stringResource(R.string.ext_screenshot_temp_hide_summary),
                    icon = null,
                    checked = tempHide.value,
                    enabled = !scopeRequestPending.value,
                ) { value ->
                    if (!value) {
                        tempHide.value = false
                        prefs.putBoolean(KEY_SCREENSHOT_TEMP_HIDE, false)
                        actions.show(restartRequired)
                    } else {
                        scopeRequestPending.value = true
                        XposedScopeService.requestScope(
                            context = context,
                            packages = listOf(PKG_SCREENSHOT),
                        ) { result ->
                            scope.launch {
                                scopeRequestPending.value = false
                                result.onSuccess {
                                    tempHide.value = true
                                    prefs.putBoolean(KEY_SCREENSHOT_TEMP_HIDE, true)
                                    actions.show(restartRequired)
                                }.onFailure {
                                    actions.show(it.message ?: scopeFailed)
                                }
                            }
                        }
                    }
                }
                LongPreferenceSlider(
                    prefs = prefs,
                    key = KEY_SCREENSHOT_LEAD_MS,
                    titleRes = R.string.ext_screenshot_lead,
                    minimum = ScreenshotContract.MIN_LEAD_MS.toLong(),
                    maximum = ScreenshotContract.MAX_LEAD_MS.toLong(),
                    default = ScreenshotContract.DEFAULT_LEAD_MS.toLong(),
                    increment = 10,
                    unit = SliderUnit.Milliseconds,
                    showDefaultAsSystem = false,
                )
            }
        }
    }
}
