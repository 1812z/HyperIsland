package io.github.hyperisland.compose.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.data.ConfigSection
import io.github.hyperisland.compose.data.InstalledAppsRepository
import io.github.hyperisland.compose.data.parseAppConfigLeafId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 配置分类选择树（新建配置与应用配置共用）。
 *
 * - 行样式复用 [BasicComponent]（设置行高度），左侧固定 24dp 槽位保证文本对齐：
 *   可展开节点放箭头（展开旋转 90°），应用级叶子放应用图标，其余留空。
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
    val count = if (node.expandable) leaves.sumOf { counts[it] ?: 0 } else counts[node.id] ?: 0
    val appLeaf = parseAppConfigLeafId(node.id) != null

    fun toggleSelection() {
        val updated = selectedLeafIds.toMutableSet()
        if (selectedCount == leaves.size) leaves.forEach { updated -= it } else leaves.forEach { updated += it }
        onSelectedLeafIdsChange(updated)
    }

    BasicComponent(
        title = node.title ?: stringResource(node.titleRes),
        summary = if (count > 0) stringResource(R.string.preset_section_count, count) else null,
        startAction = {
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
                } else if (appLeaf) {
                    AppLeafIcon(node.id)
                }
            }
        },
        endActions = {
            Checkbox(
                state = checkState,
                onClick = { toggleSelection() },
            )
        },
        insideMargin = PaddingValues(
            start = (16 + depth * 20).dp,
            top = 4.dp,
            end = 16.dp,
            bottom = 4.dp,
        ),
        onClick = {
            if (node.expandable) expanded[node.id] = !isExpanded else toggleSelection()
        },
    )

    AnimatedVisibility(
        visible = node.expandable && isExpanded,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        Column {
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
}

/** 应用级叶子前面的应用图标；未安装或加载失败时不占位内容。 */
@Composable
private fun AppLeafIcon(id: String) {
    val packageName = parseAppConfigLeafId(id)?.second ?: return
    val context = LocalContext.current
    val repository = remember { InstalledAppsRepository(context) }
    val icon by produceState<ImageBitmap?>(
        initialValue = repository.cachedIcon(packageName),
        packageName,
    ) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { repository.loadIcon(packageName) }
        }
    }
    val current = icon
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
    }
}