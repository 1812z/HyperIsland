package io.github.hyperisland.compose.page.settings.extensions.homeassistant

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.homeassistant.HaCondition
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * bottom sheet 的配色与预设页保持一致（同一套语义词，禁止硬编码，跟随深浅色主题）：
 * 浅色 sheet 用淡灰、卡片/输入框用白；深色两者互换，让 sheet 更暗、卡片更亮。
 */
@Composable
internal fun haSheetColor(): Color =
    if (isSystemInDarkTheme()) MiuixTheme.colorScheme.surfaceContainer
    else MiuixTheme.colorScheme.secondaryVariant

@Composable
internal fun haSurfaceColor(): Color =
    if (isSystemInDarkTheme()) MiuixTheme.colorScheme.secondaryVariant
    else MiuixTheme.colorScheme.surfaceContainer

@Composable
internal fun haSurfaceContentColor(): Color =
    if (isSystemInDarkTheme()) MiuixTheme.colorScheme.onSecondaryVariant
    else MiuixTheme.colorScheme.onSurfaceContainer

@Composable
internal fun haCardColors() = CardDefaults.defaultColors(
    color = haSurfaceColor(),
    contentColor = haSurfaceContentColor(),
)

/**
 * TextField 的底色直接交给 Miuix 自己画（默认的 `secondaryContainer` 是白底，会与 Card 融在一起）。
 *
 * - Card 内的输入框传 [haSheetColor]（灰色），与卡片底色区分开；
 * - 直接放在 sheet 上的输入框（订阅名称）传 [haSurfaceColor]，与 Card 同色。
 */
@Composable
internal fun haTextFieldColors(
    containerColor: Color = haSheetColor(),
) = TextFieldDefaults.textFieldColors(
    backgroundColor = containerColor,
    labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
)

@Composable
internal fun haButtonColors() = ButtonDefaults.buttonColors(
    color = haSurfaceColor(),
    contentColor = haSurfaceContentColor(),
)

/** sheet 内容底部留出导航条（小白条）高度。 */
@Composable
internal fun haSheetBottomPadding(extra: Dp = 12.dp): Dp =
    extra + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * 「左标签 / 右内容」信息行。
 *
 * 每行单独一个 Row、左标签定宽：左右各一个 Column 带 spacedBy 时，右侧内容折行会让
 * 两列行高不一致，下一行标签就被顶偏（预设详情卡踩过这个坑）。
 */
@Composable
internal fun HaInfoRow(
    label: String,
    value: String,
    labelWidth: Dp = 72.dp,
    valueColor: Color = haSurfaceContentColor(),
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(labelWidth),
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = value.ifEmpty { "—" },
            modifier = Modifier.weight(1f),
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = valueColor,
        )
    }
}

/** 显示条件的中文/本地化名称，sheet 与列表摘要共用一份映射。 */
@Composable
internal fun HaCondition.label(): String = when (this) {
    HaCondition.Equals -> stringResource(R.string.ha_condition_equals)
    HaCondition.NotEquals -> stringResource(R.string.ha_condition_not_equals)
    HaCondition.Above -> stringResource(R.string.ha_condition_above)
    HaCondition.Below -> stringResource(R.string.ha_condition_below)
    HaCondition.Contains -> stringResource(R.string.ha_condition_contains)
}
