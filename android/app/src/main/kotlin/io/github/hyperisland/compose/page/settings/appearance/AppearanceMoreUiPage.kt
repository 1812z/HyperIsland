package io.github.hyperisland.compose.page.settings.appearance

import androidx.annotation.StringRes
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.PreferenceDropdownWithSummary
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberStringPreference
import io.github.hyperisland.utils.IslandBehaviorConfig
import top.yukonga.miuix.kmp.basic.Card

/**
 * 「外观 → 其他 → 更多界面修改」。
 *
 * 收纳不属于尺寸 / 背景 / 文字 / 图标 / 描边 / 动画的零散界面改动。
 * 通知小白条分两套互不相干的实现；底部的「岛行为」管理单岛调度：
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
        item {
            IslandBehaviorSection(prefs)
        }
    }
}

@Composable
private fun IslandBehaviorSection(prefs: FlutterPrefsRepository) {
    val context = LocalContext.current
    val restartMessage = stringResource(R.string.restart_scope_app)
    val style = rememberStringPreference(prefs, IslandBehaviorConfig.STYLE_KEY, IslandBehaviorConfig.DEFAULT)
    val color = rememberStringPreference(prefs, IslandBehaviorConfig.COLOR_KEY, IslandBehaviorConfig.FOLLOW_ISLAND)
    Column {
        SectionTitle(stringResource(R.string.island_behavior_section))
        Card(modifier = Modifier.fillMaxWidth()) {
            PreferenceDropdown(
                title = stringResource(R.string.island_behavior_title),
                summary = null,
                icon = null,
                items = listOf(stringResource(R.string.default_option), stringResource(R.string.island_behavior_single)),
                selectedIndex = IslandBehaviorConfig.styles.indexOf(IslandBehaviorConfig.style(style.value)).coerceAtLeast(0),
            ) { index ->
                val next = IslandBehaviorConfig.styles[index]
                if (next != IslandBehaviorConfig.style(style.value)) {
                    prefs.setIslandBehaviorStyle(next)
                    style.value = next
                    Toast.makeText(context, restartMessage, Toast.LENGTH_SHORT).show()
                }
            }
            AnimatedVisibility(
                visible = IslandBehaviorConfig.style(style.value) == IslandBehaviorConfig.SINGLE,
                enter = expandVertically(tween(260), expandFrom = Alignment.Top) +
                    slideInVertically(tween(260)) { -it / 3 } + fadeIn(tween(180)),
                exit = shrinkVertically(tween(220), shrinkTowards = Alignment.Top) +
                    slideOutVertically(tween(220)) { -it / 3 } + fadeOut(tween(150)),
            ) {
                PreferenceDropdown(
                    title = stringResource(R.string.island_indicator_color),
                    summary = null,
                    icon = null,
                    items = listOf(
                        stringResource(R.string.island_indicator_follow),
                        stringResource(R.string.island_indicator_invert),
                        stringResource(R.string.black),
                        stringResource(R.string.island_indicator_white),
                    ),
                    selectedIndex = IslandBehaviorConfig.colors.indexOf(IslandBehaviorConfig.color(color.value)).coerceAtLeast(0),
                ) { index ->
                    val next = IslandBehaviorConfig.colors[index]
                    prefs.setIslandIndicatorColor(next)
                    color.value = next
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
