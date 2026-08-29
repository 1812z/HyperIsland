package io.github.hyperisland.compose.component.keepisland

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun KeepIslandContentListDialog(
    show: Boolean,
    title: String,
    initialValues: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var values by remember(show, initialValues) {
        mutableStateOf(initialValues.ifEmpty { listOf("") })
    }
    WindowDialog(show = show, title = title, onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 330.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(values) { index, value ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextField(
                            value = value,
                            onValueChange = { next ->
                                values = values.toMutableList().also { it[index] = next }
                            },
                            modifier = Modifier.weight(1f),
                            label = stringResource(R.string.compose_keep_island_carousel_item, index + 1),
                            useLabelAsPlaceholder = true,
                            minLines = 1,
                            maxLines = 2,
                        )
                        IconButton(
                            onClick = {
                                values = if (values.size == 1) listOf("")
                                else values.filterIndexed { itemIndex, _ -> itemIndex != index }
                            },
                        ) {
                            Icon(MiuixIcons.Close, stringResource(R.string.compose_delete))
                        }
                    }
                }
            }
            TextButton(
                text = stringResource(R.string.compose_keep_island_add_content),
                onClick = { values = values + "" },
            )
            DialogActions(
                onDismiss = onDismiss,
                onSave = { onSave(values) },
            )
        }
    }
}

@Composable
internal fun KeepIslandTextDialog(
    show: Boolean,
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(show, initialValue) { mutableStateOf(initialValue) }
    WindowDialog(show = show, title = title, onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.compose_keep_island_content_hint, "{battery.level}"),
                useLabelAsPlaceholder = true,
                minLines = 2,
                maxLines = 4,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.compose_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.compose_clear),
                    onClick = { onSave("") },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onSave(value) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.compose_save))
                }
            }
        }
    }
}

@Composable
internal fun KeepIslandIntervalDialog(
    show: Boolean,
    initialValue: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var value by remember(show, initialValue) { mutableStateOf(initialValue.toString()) }
    val normalized = value.toIntOrNull()?.coerceIn(1, 6000)
    WindowDialog(
        show = show,
        title = stringResource(R.string.compose_keep_island_carousel_interval),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextField(
                value = value,
                onValueChange = { next -> value = next.filter(Char::isDigit).take(4) },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.compose_keep_island_carousel_interval_summary),
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.compose_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { normalized?.let(onSave) },
                    enabled = normalized != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.compose_save))
                }
            }
        }
    }
}

@Composable
private fun DialogActions(
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(
            text = stringResource(R.string.compose_cancel),
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(stringResource(R.string.compose_save))
        }
    }
}
