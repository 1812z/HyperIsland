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
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
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
) {
    var query by remember(show) { mutableStateOf("") }
    val keyword = query.trim().lowercase()
    val filtered = remember(entities, keyword) {
        if (keyword.isEmpty()) {
            entities
        } else {
            entities.filter {
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
                TextButton(
                    text = stringResource(R.string.ha_entity_clear),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
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
 */
@Composable
internal fun HaAttributePickerDialog(
    show: Boolean,
    entity: HaEntity?,
    selectedAttribute: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
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
                        val selected = if (isState) selectedAttribute.isEmpty() else name == selectedAttribute
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
