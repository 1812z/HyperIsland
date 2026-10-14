package io.github.hyperisland.compose.page.settings.extensions.homeassistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.state.ToggleableState
import io.github.hyperisland.R
import io.github.hyperisland.homeassistant.HaEntity
import io.github.hyperisland.homeassistant.HaTemplate
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 实体选择器：列表来自 HA 的实时快照（设置页会保持一条 WS 连接），
 * 所以这里看到的状态 / 单位就是当前真实值，可以直接对照着配条件。
 */
@Composable
internal fun HaEntityPickerDialog(
    show: Boolean,
    entities: List<HaEntity>,
    selectedEntityId: String,
    status: HaUiStatus,
    onDismiss: () -> Unit,
    onSelected: (HaEntity) -> Unit,
    onRetry: () -> Unit,
    /** 仅列出单位是百分比的实体（设备面板的进度源用）。 */
    percentOnly: Boolean = false,
    /** 仅列出能提供图片的实体（image / camera，设备面板左侧图标用）。 */
    imageOnly: Boolean = false,
) {
    var query by remember(show) { mutableStateOf("") }
    val keyword = query.trim().lowercase()
    val filtered = remember(entities, keyword, percentOnly, imageOnly) {
        val scoped = when {
            percentOnly -> entities.filter(HaTemplate::isPercent)
            imageOnly -> entities.filter { it.entityPictureUrl.isNotBlank() }
            else -> entities
        }
        if (keyword.isEmpty()) {
            scoped
        } else {
            scoped.filter {
                it.entityId.lowercase().contains(keyword) || it.title.lowercase().contains(keyword)
            }
        }
    }

    WindowDialog(
        show = show,
        title = stringResource(R.string.ha_entity_pick),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (entities.isNotEmpty()) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.ha_entity_search),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
            }
            if (percentOnly || imageOnly) {
                Text(
                    text = stringResource(if (imageOnly) R.string.ha_image_only else R.string.ha_percent_only),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                )
            }
            when {
                entities.isEmpty() -> {
                    Text(
                        text = status.emptyHint(),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ha_reconnect))
                    }
                }
                filtered.isEmpty() -> Text(
                    text = stringResource(R.string.ha_entity_no_match),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                else -> Card(modifier = Modifier.fillMaxWidth()) {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(filtered, key = { it.entityId }) { entity ->
                            BasicComponent(
                                title = entity.title,
                                summary = "${entity.entityId} · ${entity.displayValue}",
                                insideMargin = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                                onClick = { onSelected(entity) },
                                endActions = {
                                    Checkbox(state = if (entity.entityId == selectedEntityId) ToggleableState.On else ToggleableState.Off, onClick = { onSelected(entity) })
                                },
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

/**
 * 属性选择器：留空（默认 `state`）或挑选实体里的任一属性，
 * 例如 `sensor.phone` 的 `battery_level`。同时展示每个属性当前的值。
 *
 * `highlightSelected` 决定要不要把「当前生效的属性」显示为选中：
 * - 触发属性：勾选当前生效的那一项（含 state，用户要能一眼看出订阅真正在监听什么）；
 * - 其余入口（充电三项、选取变量复制占位符）：一律不勾选，避免带着上一条配置的残留勾选造成误导。
 */
@Composable
internal fun HaAttributePickerDialog(
    show: Boolean,
    entity: HaEntity?,
    selectedAttribute: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
    /** 仅触发属性传 true；其它入口一律 false（打开即空白）。 */
    highlightSelected: Boolean = false,
) {
    val entries = remember(entity) {
        buildList {
            add(HaEntity.ATTRIBUTE_STATE to (entity?.state.orEmpty()))
            entity?.attributes?.forEach { (key, value) -> add(key to value) }
        }
    }

    WindowDialog(
        show = show,
        title = stringResource(R.string.ha_attribute_pick),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(entries, key = { it.first }) { (name, value) ->
                        val isState = name == HaEntity.ATTRIBUTE_STATE
                        // 只有触发属性需要标出「当前生效的那一项」，其余入口打开时不勾选任何东西。
                        // 未选属性 = 听 state，所以触发属性在属性为空时要勾在「状态（state）」这一行上。
                        val selected = highlightSelected && if (isState) selectedAttribute.isBlank() else name == selectedAttribute
                        BasicComponent(
                            title = if (isState) stringResource(R.string.ha_attribute_state) else name,
                            summary = value.takeIf { it.isNotEmpty() },
                            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                            onClick = { onSelected(if (isState) "" else name) },
                            endActions = {
                                Checkbox(state = if (selected) ToggleableState.On else ToggleableState.Off, onClick = { onSelected(if (isState) "" else name) })
                            },
                        )
                    }
                }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(R.string.close))
            }
        }
    }
}

/** 状态对应的空列表提示文案。 */
@Composable
internal fun HaUiStatus.emptyHint(): String = when (this) {
    HaUiStatus.Disabled -> stringResource(R.string.ha_status_disabled)
    HaUiStatus.Unconfigured -> stringResource(R.string.ha_status_unconfigured)
    HaUiStatus.Connecting -> stringResource(R.string.ha_status_connecting)
    HaUiStatus.AuthFailed -> stringResource(R.string.ha_status_auth_failed)
    HaUiStatus.Failed -> stringResource(R.string.ha_status_failed)
    HaUiStatus.Connected -> stringResource(R.string.ha_entity_empty)
}

/** 列表行尾的「已选」标记。 */
@Composable
private fun SelectedBadge() {
    Text(
        text = stringResource(R.string.selected),
        color = MiuixTheme.colorScheme.primary,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
    )
}
