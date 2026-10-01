package io.github.hyperisland.compose.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.data.ConfigSection
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 配置分类选择树。
 *
 * - 可展开节点默认收起，左侧固定宽度箭头槽位，箭头旋转 90° 表示展开；叶子节点箭头槽位留空，
 *   保证文本左对齐。
 * - 右侧为三态复选框：全选为选中，全不选为空，部分选中为半选。
 * - 点击可展开节点整行展开 / 收起；点击叶子节点整行切换选择。
 */
@Composable
internal fun ConfigSectionTree(
    sections: List<ConfigSection>,
    selectedLeafIds: Set<String>,
    onSelectedLeafIdsChange: (Set<String>) -> Unit,
    counts: Map<String, Int> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    Column(modifier = modifier.fillMaxWidth()) {
        sections.forEach { section ->
            ConfigSectionNode(
                node = section,
                depth = 0,
                expanded = expanded,
                selectedLeafIds = selectedLeafIds,
                onSelectedLeafIdsChange = onSelectedLeafIdsChange,
                counts = counts,
            )
        }
    }
}

@Composable
private fun ConfigSectionNode(
    node: ConfigSection,
    depth: Int,
    expanded: MutableMap<String, Boolean>,
    selectedLeafIds: Set<String>,
    onSelectedLeafIdsChange: (Set<String>) -> Unit,
    counts: Map<String, Int>,
) {
    val leaves = node.leafIds
    val selectedCount = leaves.count { it in selectedLeafIds }
    val checkState = when {
        leaves.isEmpty() || selectedCount == 0 -> ToggleableState.Off
        selectedCount == leaves.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val isExpanded = expanded[node.id] == true
    val count = if (node.expandable) node.leafIds.sumOf { counts[it] ?: 0 } else counts[node.id] ?: 0

    fun toggleSelection() {
        val updated = selectedLeafIds.toMutableSet()
        if (selectedCount == leaves.size) leaves.forEach { updated -= it } else leaves.forEach { updated += it }
        onSelectedLeafIdsChange(updated)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (node.expandable) expanded[node.id] = !isExpanded else toggleSelection()
            }
            .padding(start = ((depth * 20) + 8).dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (node.expandable) {
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(if (isExpanded) 90f else 0f),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = node.title ?: stringResource(node.titleRes),
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
        )
        if (count > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.preset_section_count, count),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Checkbox(
            state = checkState,
            onClick = { toggleSelection() },
        )
    }

    if (node.expandable && isExpanded) {
        node.children.forEach { child ->
            ConfigSectionNode(
                node = child,
                depth = depth + 1,
                expanded = expanded,
                selectedLeafIds = selectedLeafIds,
                onSelectedLeafIdsChange = onSelectedLeafIdsChange,
                counts = counts,
            )
        }
    }
}