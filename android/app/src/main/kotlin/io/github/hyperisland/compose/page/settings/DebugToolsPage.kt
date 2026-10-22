package io.github.hyperisland.compose.page.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.BarBackdropContent
import io.github.hyperisland.compose.component.BarBlurHost
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.LocalBarBlurEnabled
import io.github.hyperisland.compose.component.PredictiveNavigationBackdrop
import io.github.hyperisland.compose.component.PredictiveNavigationBackHandler
import io.github.hyperisland.compose.component.PredictiveNavigationLayer
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsAction
import io.github.hyperisland.compose.component.SettingsActionWithArrow
import io.github.hyperisland.compose.component.predictiveNavigationBackground
import io.github.hyperisland.compose.component.rememberPredictiveNavigationLayerState
import io.github.hyperisland.compose.component.requiresBackdropCapture
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.data.rememberLongPreference
import io.github.hyperisland.compose.theme.DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT
import io.github.hyperisland.compose.theme.PREF_PREDICTIVE_BACK_MAX_TRANSLATION
import io.github.hyperisland.data.DebugPreferences
import io.github.hyperisland.xposed.islanddispatch.definition.IslandDispatchContract
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 调试工具：向 SystemUI 进程发送调试指令，由 Hook 端执行。
 *
 * 顶部总开关关闭时 Hook 端不注册任何调试 Hook，下面的指令一律不可用，
 * 所以普通用户开着这个页面也不会有额外开销。
 *
 * Hook 管理是这里的二级层（[showHookManager]），走与设置详情页同一套
 * 手势返回 / 进出场动画，返回落在调试工具页而不是直接退出到设置页。
 */
@Composable
internal fun DebugToolsPage(
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val debugMode = rememberBooleanPreference(
        prefs,
        DebugPreferences.MODE,
        DebugPreferences.DEFAULT_MODE,
    )
    var shiftCount by remember { mutableIntStateOf(0) }
    var showHookManager by remember { mutableStateOf(false) }
    val hookManagerNavigation = rememberPredictiveNavigationLayerState()
    val predictiveBackMaxTranslation = rememberLongPreference(
        prefs,
        PREF_PREDICTIVE_BACK_MAX_TRANSLATION,
        DEFAULT_PREDICTIVE_BACK_TRANSLATION_PERCENT,
    )
    val blurBarsEnabled = LocalBarBlurEnabled.current

    PredictiveNavigationBackHandler(
        visible = showHookManager,
        enabled = showHookManager,
        state = hookManagerNavigation,
        maxTranslationPercent = predictiveBackMaxTranslation.value,
        onDismiss = { showHookManager = false },
    )

    BarBlurHost(
        enabled = blurBarsEnabled,
        captureForEffects = hookManagerNavigation.requiresBackdropCapture(showHookManager),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .predictiveNavigationBackground(hookManagerNavigation),
                ) {
                    DetailPage(
                        title = stringResource(R.string.debug_tools),
                        onBack = onBack,
                    ) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                PreferenceSwitch(
                                    title = stringResource(R.string.debug_mode),
                                    summary = stringResource(R.string.debug_mode_summary),
                                    icon = null,
                                    checked = debugMode.value,
                                ) { checked ->
                                    debugMode.value = checked
                                    prefs.putBoolean(DebugPreferences.MODE, checked)
                                }
                            }
                        }
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                SettingsActionWithArrow(
                                    title = stringResource(R.string.hook_manager),
                                    icon = null,
                                    enabled = debugMode.value,
                                    onClick = { showHookManager = true },
                                )
                            }
                        }
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(R.string.debug_tools_description),
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        item {
                            SectionTitle(stringResource(R.string.debug_island_section))
                            Card(modifier = Modifier.fillMaxWidth()) {
                                SettingsAction(
                                    title = stringResource(R.string.debug_burn_in_shift),
                                    icon = null,
                                    summary = stringResource(R.string.debug_burn_in_shift_summary),
                                    enabled = debugMode.value,
                                ) {
                                    val direction = shiftCount % BURN_IN_DIRECTION_COUNT
                                    context.requestBurnInShift()
                                    shiftCount++
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.debug_burn_in_shift_toast,
                                            context.getString(burnInDirectionRes(direction)),
                                        ),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                    }
                }
            }
            PredictiveNavigationBackdrop(
                state = hookManagerNavigation,
                modifier = Modifier.fillMaxSize(),
            )
            PredictiveNavigationLayer(
                visible = showHookManager,
                state = hookManagerNavigation,
                maxTranslationPercent = predictiveBackMaxTranslation.value,
            ) {
                HookManagerPage(
                    prefs = prefs,
                    onBack = { showHookManager = false },
                )
            }
        }
    }
}

private fun Context.requestBurnInShift() {
    sendBroadcast(
        Intent(IslandDispatchContract.ACTION_DEBUG_BURN_IN_SHIFT).setPackage(SYSTEM_UI_PACKAGE),
        IslandDispatchContract.PERM,
    )
}

private fun burnInDirectionRes(direction: Int): Int = when (direction) {
    0 -> R.string.debug_burn_in_direction_left
    1 -> R.string.debug_burn_in_direction_up
    2 -> R.string.debug_burn_in_direction_right
    else -> R.string.debug_burn_in_direction_down
}

private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
private const val BURN_IN_DIRECTION_COUNT = 4
