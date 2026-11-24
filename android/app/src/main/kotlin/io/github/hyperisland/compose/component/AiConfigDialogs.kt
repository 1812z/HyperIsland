package io.github.hyperisland.compose.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.service.AiConfigService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AiModelPickerDialog(
    show: Boolean,
    chatUrl: String,
    apiKey: String,
    currentModel: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    val unexpectedFormatMessage = stringResource(R.string.ai_model_picker_unexpected_format)
    var models by remember(show) { mutableStateOf<List<String>?>(null) }
    var error by remember(show) { mutableStateOf<String?>(null) }
    var query by remember(show) { mutableStateOf("") }
    var revision by remember(show) { mutableIntStateOf(0) }

    LaunchedEffect(show, chatUrl, apiKey, revision) {
        if (!show || chatUrl.isBlank()) return@LaunchedEffect
        models = null
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                AiConfigService.fetchModels(chatUrl, apiKey, unexpectedFormatMessage)
            }
        }.onSuccess { models = it }
            .onFailure { error = it.message ?: it.toString() }
    }

    val filtered = remember(models, query) {
        val normalized = query.trim().lowercase()
        models.orEmpty().filter { normalized.isEmpty() || it.lowercase().contains(normalized) }
    }

    AppDialog(
        show = show,
        title = stringResource(R.string.ai_model_picker_title),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (models != null) {
                AppTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.ai_model_picker_search),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                )
            }
            when {
                error != null -> {
                    Text(
                        text = stringResource(R.string.ai_model_picker_error),
                        color = MiuixTheme.colorScheme.error,
                    )
                    Text(
                        text = error.orEmpty(),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    AppActionButton(onClick = { revision++ }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.ai_model_picker_retry))
                    }
                }
                models == null -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
                filtered.isEmpty() -> Text(
                    text = stringResource(R.string.ai_model_picker_empty),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                else -> AppCard {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(filtered, key = { it }) { model ->
                            BasicComponent(
                                title = model,
                                summary = if (model == currentModel) {
                                    stringResource(R.string.selected)
                                } else {
                                    null
                                },
                                insideMargin = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                                onClick = { onSelected(model) },
                            )
                        }
                    }
                }
            }
            AppActionTextButton(
                text = stringResource(R.string.ai_model_picker_close),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun AiCustomFieldsDialog(
    show: Boolean,
    initialJson: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var fields by remember(show, initialJson) { mutableStateOf(parseCustomFields(initialJson)) }
    var error by remember(show) { mutableStateOf(false) }

    fun save() {
        val result = JSONObject()
        val valid = runCatching {
            fields.forEach { field ->
                val key = field.key.trim()
                if (key.isEmpty()) error("empty_key")
                result.put(key, JSONTokener(field.value.trim()).nextValue())
            }
        }.isSuccess
        if (valid) onSave(result.toString()) else error = true
    }

    AppDialog(
        show = show,
        title = stringResource(R.string.ai_custom_fields_dialog),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.ai_custom_fields_description),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppActionTextButton(
                    text = stringResource(R.string.ai_custom_fields_reset),
                    onClick = {
                        fields = parseCustomFields(DEFAULT_CUSTOM_FIELDS)
                        error = false
                    },
                    modifier = Modifier.weight(1f),
                )
                AppActionButton(
                    onClick = {
                        fields = fields + AiCustomField("", "false")
                        error = false
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.ai_custom_field_add))
                }
            }
            if (fields.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(fields.indices.toList(), key = { it }) { index ->
                        val field = fields[index]
                        AppCard(insideMargin = PaddingValues(12.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppTextField(
                                        value = field.key,
                                        onValueChange = { value ->
                                            fields = fields.toMutableList().also {
                                                it[index] = field.copy(key = value)
                                            }
                                            error = false
                                        },
                                        modifier = Modifier.weight(1f),
                                        label = stringResource(R.string.ai_custom_field_name),
                                        useLabelAsPlaceholder = true,
                                        singleLine = true,
                                    )
                                    IconButton(onClick = {
                                        fields = fields.filterIndexed { itemIndex, _ -> itemIndex != index }
                                        error = false
                                    }) {
                                        Icon(
                                            MiuixIcons.Close,
                                            stringResource(R.string.ai_custom_field_delete),
                                        )
                                    }
                                }
                                AppTextField(
                                    value = field.value,
                                    onValueChange = { value ->
                                        fields = fields.toMutableList().also {
                                            it[index] = field.copy(value = value)
                                        }
                                        error = false
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = stringResource(R.string.ai_custom_field_value),
                                    useLabelAsPlaceholder = true,
                                    maxLines = 3,
                                )
                            }
                        }
                    }
                }
            }
            if (error) {
                Text(
                    text = stringResource(R.string.ai_custom_fields_error),
                    color = MiuixTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppActionTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                AppActionButton(
                    onClick = ::save,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

private fun parseCustomFields(raw: String): List<AiCustomField> = runCatching {
    val root = JSONObject(raw)
    buildList {
        root.keys().forEach { key ->
            add(AiCustomField(key, jsonValueToString(root.get(key))))
        }
    }
}.getOrDefault(emptyList())

private fun jsonValueToString(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "null"
    is String -> JSONObject.quote(value)
    else -> value.toString()
}

private data class AiCustomField(val key: String, val value: String)
private const val DEFAULT_CUSTOM_FIELDS = "{\"enable_thinking\":false}"
