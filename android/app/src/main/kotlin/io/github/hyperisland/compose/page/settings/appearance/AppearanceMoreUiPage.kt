package io.github.hyperisland.compose.page.settings.appearance

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.PreferenceDropdownWithSummary
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberStringPreference
import top.yukonga.miuix.kmp.basic.Card

/**
 * 「外观 → 其他 → 更多界面修改」。
 *
 * 收纳不属于尺寸 / 背景 / 文字 / 图标 / 描边 / 动画的零散界面改动。
 * 目前只有「通知小白条」，分两套互不相干的实现：
 * - 焦点通知：岛内展开态，`DynamicIslandBaseContentView.updateMiniBar()`；
 * - 普通通知：通知栏 / 悬浮通知行，`ExpandableNotificationRowInjector.updateMiniWindowBar()`。
 */
@Composable
internal fun AppearanceMoreUiPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    AppearanceDetailPage(title = stringResource(R.string.appearance_more_ui), onBack = onBack) {
        item {
            Column {
                SectionTitle(stringResource(R.string.mini_bar_section))
                Card(modifier = Modifier.fillMaxWidth()) {
                    MiniBarBehaviorRow(
                        prefs = prefs,
                        key = KEY_MINI_BAR_BEHAVIOR,
                        titleRes = R.string.mini_bar_focus_title,
                        summaryRes = R.string.mini_bar_focus_summary,
                    )
                    MiniBarBehaviorRow(
                        prefs = prefs,
                        key = KEY_NOTIFICATION_MINI_BAR_BEHAVIOR,
                        titleRes = R.string.mini_bar_normal_title,
                        summaryRes = R.string.mini_bar_normal_summary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniBarBehaviorRow(
    prefs: FlutterPrefsRepository,
    key: String,
    @StringRes titleRes: Int,
    @StringRes summaryRes: Int,
) {
    val stored = rememberStringPreference(prefs, key, MINI_BAR_BEHAVIOR_DEFAULT)
    val selected = MINI_BAR_BEHAVIOR_VALUES
        .indexOf(normalizeMiniBarBehavior(stored.value))
        .coerceAtLeast(0)
    // 选项副标题直接写在选项上，不额外占一行说明。
    val items = listOf(
        stringResource(R.string.follow_system) to null,
        stringResource(R.string.mini_bar_behavior_hidden) to
            stringResource(R.string.mini_bar_behavior_hidden_summary),
        stringResource(R.string.mini_bar_behavior_removed) to
            stringResource(R.string.mini_bar_behavior_removed_summary),
    )
    PreferenceDropdownWithSummary(
        title = stringResource(titleRes),
        summary = stringResource(summaryRes),
        icon = null,
        items = items,
        selectedIndex = selected,
    ) { index ->
        val value = MINI_BAR_BEHAVIOR_VALUES[index]
        stored.value = value
        // 默认值不落盘，保持「未配置 == 跟随系统」的语义。
        if (value == MINI_BAR_BEHAVIOR_DEFAULT) prefs.remove(key) else prefs.putString(key, value)
    }
}

internal const val MINI_BAR_BEHAVIOR_SYSTEM = "system"
internal const val MINI_BAR_BEHAVIOR_HIDDEN = "hidden"
internal const val MINI_BAR_BEHAVIOR_REMOVED = "removed"

/**
 * 早期取值。曾与「隐藏」并列，但两者实现完全等价（都是把小白条设为不可见、不影响交互），
 * 已合并进 [MINI_BAR_BEHAVIOR_HIDDEN]，此常量仅为兼容已落盘的旧配置保留。
 */
internal const val MINI_BAR_BEHAVIOR_TRANSPARENT = "transparent"

/** 下拉选项顺序。 */
internal val MINI_BAR_BEHAVIOR_VALUES = listOf(
    MINI_BAR_BEHAVIOR_SYSTEM,
    MINI_BAR_BEHAVIOR_HIDDEN,
    MINI_BAR_BEHAVIOR_REMOVED,
)

/** 把历史取值折算到当前选项集，避免旧配置在界面上落空。 */
internal fun normalizeMiniBarBehavior(value: String): String =
    if (value == MINI_BAR_BEHAVIOR_TRANSPARENT) MINI_BAR_BEHAVIOR_HIDDEN else value
