package io.github.hyperisland.compose.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
    // 扁平化展开节点，使用懒列表只创建屏幕内的行。
    // 不再在高度动画过程中分批改变子树尺寸；展开状态、名称及图标缓存均保留。
    val rows by remember(sections) {
        derivedStateOf {
            buildList<Pair<ConfigSection, Int>> {
                fun append(node: ConfigSection, depth: Int) {
                    add(node to depth)
                    if (expanded[node.id] == true) node.children.forEach { append(it, depth + 1) }
                }
                sections.forEach { append(it, 0) }
            }
        }
    }
    LazyColumn(modifier = modifier.fillMaxWidth().heightIn(max = 400.dp)) {
        items(rows, key = { it.first.id }) { (section, depth) ->
            ConfigSectionNode(
                node = section,
                depth = depth,
                expanded = expanded,
                selectedLeafIds = selectedLeafIds,
                onSelectedLeafIdsChange = onSelectedLeafIdsChange,
                counts = counts,
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(180),
                    placementSpec = null,
                    fadeOutSpec = tween(120),
                ),
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
    modifier: Modifier = Modifier,
) {
    val leaves = remember(node) { node.leafIds }
    val selectedCount = leaves.count { it in selectedLeafIds }
    val checkState = when {
        leaves.isEmpty() || selectedCount == 0 -> ToggleableState.Off
        selectedCount == leaves.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val isExpanded = expanded[node.id] == true
    val count = if (node.expandable) leaves.sumOf { counts[it] ?: 0 } else counts[node.id] ?: 0
    val appLeaf = parseAppConfigLeafId(node.id) != null
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(180),
        label = "configSectionArrow",
    )

    fun toggleSelection() {
        val updated = selectedLeafIds.toMutableSet()
        if (selectedCount == leaves.size) leaves.forEach { updated -= it } else leaves.forEach { updated += it }
        onSelectedLeafIdsChange(updated)
    }

    BasicComponent(
        modifier = modifier,
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
                            .rotate(arrowRotation),
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
