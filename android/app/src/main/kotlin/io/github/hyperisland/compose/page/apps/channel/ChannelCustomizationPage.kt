package io.github.hyperisland.compose.page.apps.channel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.ColorPaletteDialog
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.parseHexColor
import io.github.hyperisland.compose.component.toArgbHex
import io.github.hyperisland.compose.data.channel.ChannelCustomizationTarget
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationEngine
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
internal fun ChannelCustomizationPage(
    target: ChannelCustomizationTarget,
    template: String,
    renderer: String,
    rawConfig: String,
    onSave: (String) -> Unit,
    onBack: () -> Unit,
) {
    val schema = remember(target, template, renderer) {
        when (target) {
            ChannelCustomizationTarget.Island -> FocusCustomizationEngine.buildIslandSchema(template)
            ChannelCustomizationTarget.Focus -> FocusCustomizationEngine.buildSchema(template, renderer)
            ChannelCustomizationTarget.Aod -> FocusCustomizationEngine.buildAodSchema(template)
        }
    }
    val merged = remember(target, template, renderer, rawConfig) {
        when (target) {
            ChannelCustomizationTarget.Island ->
                FocusCustomizationEngine.mergeIslandWithDefaults(template, rawConfig)
            ChannelCustomizationTarget.Focus ->
                FocusCustomizationEngine.mergeWithDefaults(template, renderer, rawConfig)
            ChannelCustomizationTarget.Aod ->
                FocusCustomizationEngine.mergeAodWithDefaults(template, rawConfig)
        }
    }
    val fields = remember(schema) { schemaFields(schema) }
    val values = remember(target, template, renderer, rawConfig) {
        val json = runCatching { JSONObject(merged) }.getOrElse { JSONObject() }
        mutableStateMapOf<String, String>().apply {
            fields.forEach { field -> put(field.key, json.optString(field.key, field.defaultValue)) }
        }
    }
    val context = LocalContext.current
    var showReferences by remember { mutableStateOf(false) }
    var colorField by remember { mutableStateOf<CustomizationField?>(null) }

    DetailPage(
        title = stringResource(
            when (target) {
                ChannelCustomizationTarget.Island -> R.string.channel_island_customization
                ChannelCustomizationTarget.Focus -> R.string.channel_focus_customization
                ChannelCustomizationTarget.Aod -> R.string.channel_aod_customization
            },
        ),
        onBack = onBack,
    ) {
        item {
            SectionTitle(stringResource(R.string.channel_expression_reference))
            Card {
                BasicComponent(
                    title = stringResource(R.string.channel_expression_reference),
                    summary = stringResource(R.string.channel_expression_reference_summary),
                    onClick = { showReferences = true },
                )
            }
        }
        item {
            SectionTitle(stringResource(R.string.channel_customization_fields))
            Card(modifier = Modifier.fillMaxWidth()) {
                fields.forEach { field ->
                    when (field.type) {
                        "select" -> PreferenceDropdown(
                            title = customizationFieldLabel(field.key, field.label),
                            summary = null,
                            icon = null,
                            items = field.options.map { optionLabel(field.key, it.value, it.label) },
                            selectedIndex = field.options.indexOfFirst { it.value == values[field.key] }
                                .coerceAtLeast(0),
                            insideMargin = CUSTOM_FIELD_MARGIN,
                        ) { index -> values[field.key] = field.options[index].value }
                        "color" -> ArrowPreference(
                            title = customizationFieldLabel(field.key, field.label),
                            summary = values[field.key]?.takeIf(String::isNotEmpty),
                            insideMargin = CUSTOM_FIELD_MARGIN,
                            onClick = { colorField = field },
                        )
                        else -> Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(customizationFieldLabel(field.key, field.label))
                            TextField(
                                value = values[field.key].orEmpty(),
                                onValueChange = { value ->
                                    values[field.key] = if (field.type == "number") {
                                        value.filter(Char::isDigit)
                                    } else {
                                        value
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = field.type == "number",
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (field.type == "number") {
                                        KeyboardType.Number
                                    } else {
                                        KeyboardType.Text
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.restore_default),
                    onClick = { fields.forEach { values[it.key] = it.defaultValue } },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val overrides = JSONObject()
                        fields.forEach { field ->
                            val value = values[field.key].orEmpty()
                            if (value != field.defaultValue) overrides.put(field.key, value)
                        }
                        onSave(if (overrides.length() == 0) "" else overrides.toString())
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text(stringResource(R.string.apply)) }
            }
        }
    }

    val activeColorField = colorField
    ColorPaletteDialog(
        show = activeColorField != null,
        title = activeColorField?.let { customizationFieldLabel(it.key, it.label) }.orEmpty(),
        initialColor = parseHexColor(activeColorField?.let { values[it.key].orEmpty() }.orEmpty()),
        onDismiss = { colorField = null },
        onDelete = {
            activeColorField?.let { values[it.key] = "" }
            colorField = null
        },
        onSave = { color ->
            activeColorField?.let { values[it.key] = color.toArgbHex() }
            colorField = null
        },
    )

    WindowBottomSheet(
        show = showReferences,
        title = stringResource(R.string.channel_expression_reference),
        onDismissRequest = { showReferences = false },
    ) {
        val placeholders = (schema["placeholders"] as? List<*>)
            .orEmpty().mapNotNull { (it as? Map<*, *>)?.get("key")?.toString() }
        val functions = (schema["functions"] as? List<*>)
            .orEmpty().mapNotNull { (it as? Map<*, *>)?.get("example")?.toString() }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            if (placeholders.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.channel_available_placeholders)) }
                item {
                    Card {
                        placeholders.forEach { key ->
                            val expression = "\${$key}"
                            BasicComponent(
                                title = expression,
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText(expression, expression))
                                },
                            )
                        }
                    }
                }
            }
            if (functions.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.channel_expression_functions)) }
                item {
                    Card {
                        functions.forEach { example ->
                            BasicComponent(
                                title = example,
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText(example, example))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class CustomizationField(
    val key: String,
    val label: String,
    val type: String,
    val defaultValue: String,
    val options: List<CustomizationOption>,
)

private data class CustomizationOption(val value: String, val label: String)

private fun schemaFields(schema: Map<String, Any?>): List<CustomizationField> =
    (schema["fields"] as? List<*>).orEmpty().mapNotNull { raw ->
        val field = raw as? Map<*, *> ?: return@mapNotNull null
        val key = field["key"]?.toString().orEmpty()
        if (key.isEmpty()) return@mapNotNull null
        CustomizationField(
            key = key,
            label = field["label"]?.toString() ?: key,
            type = field["type"]?.toString().orEmpty(),
            defaultValue = field["defaultValue"]?.toString().orEmpty(),
            options = (field["options"] as? List<*>).orEmpty().mapNotNull { optionRaw ->
                val option = optionRaw as? Map<*, *> ?: return@mapNotNull null
                val value = option["value"]?.toString().orEmpty()
                CustomizationOption(value, option["label"]?.toString() ?: value)
            },
        )
    }

@Composable
private fun customizationFieldLabel(key: String, fallback: String): String = when (key) {
    "focus_title_expr" -> stringResource(R.string.channel_focus_title_expression)
    "focus_content_expr" -> stringResource(R.string.channel_focus_content_expression)
    "focus_icon_mode" -> stringResource(R.string.channel_focus_icon_source)
    "focus_pic_profile_mode" -> stringResource(R.string.channel_focus_picture_source)
    "focus_app_icon_pkg" -> stringResource(R.string.channel_focus_app_icon_package)
    "focus_app_icon_pkg_mode" -> stringResource(R.string.channel_focus_secondary_icon_source)
    "progress_color" -> stringResource(R.string.channel_progress_color)
    "progress_bar_color" -> stringResource(R.string.channel_progress_bar_color)
    "progress_bar_color_end" -> stringResource(R.string.channel_progress_bar_end_color)
    "chat_title_color" -> stringResource(R.string.channel_chat_title_color)
    "chat_title_color_dark" -> stringResource(R.string.channel_chat_title_dark_color)
    "chat_content_color" -> stringResource(R.string.channel_chat_content_color)
    "chat_content_color_dark" -> stringResource(R.string.channel_chat_content_dark_color)
    "island_left_expr" -> stringResource(R.string.channel_island_left_expression)
    "island_right_expr" -> stringResource(R.string.channel_island_right_expression)
    "aodTitle" -> stringResource(R.string.channel_aod_expression)
    "aodPic" -> stringResource(R.string.channel_aod_icon_source)
    else -> fallback
}

@Composable
private fun optionLabel(fieldKey: String, value: String, fallback: String): String = when {
    fieldKey in ICON_FIELDS && value == "auto" -> stringResource(R.string.icon_auto)
    fieldKey in ICON_FIELDS && value == "notif_small" -> stringResource(R.string.icon_notification_small)
    fieldKey in ICON_FIELDS && value == "notif_large" -> stringResource(R.string.icon_notification_large)
    fieldKey in ICON_FIELDS && value == "app_icon" -> stringResource(R.string.icon_app)
    else -> fallback
}

private val ICON_FIELDS = setOf("focus_icon_mode", "focus_pic_profile_mode", "focus_app_icon_pkg_mode", "aodPic")
private val CUSTOM_FIELD_MARGIN = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
