package io.github.hyperisland.compose.page.settings.extensions.homeassistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.SettingsAction
import io.github.hyperisland.compose.component.SettingsItemMargin
import io.github.hyperisland.homeassistant.HaCondition
import io.github.hyperisland.homeassistant.HaEntity
import io.github.hyperisland.homeassistant.HaSubscription
import io.github.hyperisland.homeassistant.HaTemplate
import io.github.hyperisland.homeassistant.HaTemplateType
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
internal fun HomeAssistantSubscriptionSheet(
    show: Boolean,
    initial: HaSubscription?,
    entities: List<HaEntity>,
    status: HaUiStatus,
    onDismiss: () -> Unit,
    onDelete: (HaSubscription) -> Unit,
    onSave: (HaSubscription) -> Unit,
    onRetry: () -> Unit,
) {
    var draft by remember(show, initial) { mutableStateOf(initial ?: HaSubscription()) }
    val clipboard = LocalClipboardManager.current
    var pickerTarget by remember(show) { mutableStateOf<String?>(null) }
    var attributeTarget by remember(show) { mutableStateOf<String?>(null) }
    val entityMap = remember(entities) { entities.associateBy(HaEntity::entityId) }
    val selectedEntities = remember(draft, entities) {
        draft.dataEntityIds.mapNotNull(entityMap::get).distinctBy(HaEntity::entityId)
    }
    val placeholders = remember(selectedEntities) { HaTemplate.referencePlaceholders(selectedEntities) }
    val conditionItems = HaCondition.entries
    val condition = draft.conditionType
    val canSave = draft.isValid

    @Composable
    fun entityLabel(id: String, attribute: String): String =
        entityMap[id]?.let { "${it.title} · ${attribute.ifBlank { HaEntity.ATTRIBUTE_STATE }}" }
            ?: stringResource(R.string.ha_entity_none)

    fun pickEntity(target: String) { pickerTarget = target }
    fun pickAttribute(target: String) { attributeTarget = target }

    WindowBottomSheet(
        show = show,
        title = stringResource(if (initial == null) R.string.ha_add else R.string.ha_edit),
        backgroundColor = haSheetColor(),
        insideMargin = DpSize(12.dp, 0.dp),
        startAction = { IconButton(onClick = onDismiss) { Icon(MiuixIcons.Basic.Close, stringResource(R.string.close)) } },
        endAction = { IconButton(onClick = { onSave(draft) }, enabled = canSave) { Icon(MiuixIcons.Basic.Check, stringResource(R.string.save)) } },
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = haSheetBottomPadding(16.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SmallTitle(stringResource(R.string.ha_subscription_name))
                OutlinedHaTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = stringResource(R.string.ha_subscription_name),
                )
            }
            item {
                SmallTitle(stringResource(R.string.ha_trigger_entity))
                Card(colors = haCardColors()) {
                    SettingsAction(
                        title = stringResource(R.string.ha_trigger_entity),
                        summary = entityLabel(draft.triggerEntityId, draft.triggerAttribute),
                        endIcon = MiuixIcons.ChevronForward,
                    ) { pickEntity("trigger") }
                    SettingsAction(
                        title = stringResource(R.string.ha_trigger_attribute),
                        summary = draft.triggerAttribute.ifBlank { stringResource(R.string.ha_attribute_state) },
                        endIcon = MiuixIcons.ChevronForward,
                    ) { pickAttribute("trigger") }
                    PreferenceDropdown(
                        title = stringResource(R.string.ha_condition),
                        summary = condition.label(),
                        icon = null,
                        items = conditionItems.map { it.label() },
                        selectedIndex = conditionItems.indexOf(condition).coerceAtLeast(0),
                    ) { index -> draft = draft.copy(condition = conditionItems[index].id) }
                    OutlinedHaTextField(
                        value = draft.value,
                        onValueChange = { draft = draft.copy(value = it) },
                        label = stringResource(R.string.ha_condition_value),
                        keyboardOptions = if (condition.needsNumber) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                    )
                }
            }
            item {
                SmallTitle(stringResource(R.string.ha_template_summary))
                Card(colors = haCardColors()) {
                    PreferenceDropdown(
                        title = stringResource(R.string.ha_template_type),
                        summary = if (draft.templateType == HaTemplateType.CHARGING) {
                            stringResource(R.string.ha_template_charging)
                        } else {
                            stringResource(R.string.ha_template_custom)
                        },
                        icon = null,
                        items = listOf(stringResource(R.string.ha_template_custom), stringResource(R.string.ha_template_charging)),
                        selectedIndex = if (draft.templateType == HaTemplateType.CHARGING) 1 else 0,
                    ) { index -> draft = draft.copy(templateType = if (index == 1) HaTemplateType.CHARGING else HaTemplateType.CUSTOM) }
                    if (draft.templateType == HaTemplateType.CHARGING) {
                        ChargingSourceRow(stringResource(R.string.ha_charging_power), entityLabel(draft.chargingPowerEntityId, draft.chargingPowerAttribute)) { pickEntity("charging_power") }
                        ChargingSourceRow(stringResource(R.string.ha_charging_current), entityLabel(draft.chargingCurrentEntityId, draft.chargingCurrentAttribute)) { pickEntity("charging_current") }
                        ChargingSourceRow(stringResource(R.string.ha_charging_voltage), entityLabel(draft.chargingVoltageEntityId, draft.chargingVoltageAttribute)) { pickEntity("charging_voltage") }
                    } else {
                        OutlinedHaTextField(value = draft.islandLeftTemplate, onValueChange = { draft = draft.copy(islandLeftTemplate = it) }, label = stringResource(R.string.ha_island_left_template))
                        SourceRow(stringResource(R.string.ha_island_left_entity), entityLabel(draft.islandLeftEntityId, draft.islandLeftAttribute)) { pickEntity("island_left") }
                        OutlinedHaTextField(value = draft.islandRightTemplate, onValueChange = { draft = draft.copy(islandRightTemplate = it) }, label = stringResource(R.string.ha_island_right_template))
                        SourceRow(stringResource(R.string.ha_island_right_entity), entityLabel(draft.islandRightEntityId, draft.islandRightAttribute)) { pickEntity("island_right") }
                    }
                    Text(stringResource(R.string.ha_focus_content), modifier = Modifier.padding(12.dp), fontSize = MiuixTheme.textStyles.subtitle.fontSize)
                    OutlinedHaTextField(value = draft.focusTitleTemplate, onValueChange = { draft = draft.copy(focusTitleTemplate = it) }, label = stringResource(R.string.ha_focus_title_template))
                    SourceRow(stringResource(R.string.ha_focus_title_entity), entityLabel(draft.focusTitleEntityId, draft.focusTitleAttribute)) { pickEntity("focus_title") }
                    OutlinedHaTextField(value = draft.focusContentTemplate, onValueChange = { draft = draft.copy(focusContentTemplate = it) }, label = stringResource(R.string.ha_focus_content_template))
                    SourceRow(stringResource(R.string.ha_focus_content_entity), entityLabel(draft.focusContentEntityId, draft.focusContentAttribute)) { pickEntity("focus_content") }
                    if (placeholders.isNotEmpty()) {
                        LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items((HaTemplate.PLACEHOLDERS + placeholders).distinct(), key = { it }) { placeholder ->
                                TextButton(text = placeholder, onClick = { clipboard.setText(AnnotatedString(placeholder)) })
                            }
                        }
                    }
                }
            }
            item {
                SmallTitle(stringResource(R.string.ha_island_config))
                Card(colors = haCardColors()) {
                    SwitchPreference(title = stringResource(R.string.ha_normal_island), summary = null, checked = draft.islandEnabled, insideMargin = SettingsItemMargin, onCheckedChange = { draft = draft.copy(islandEnabled = it) })
                    SwitchPreference(title = stringResource(R.string.ha_focus_notification), summary = stringResource(R.string.ha_focus_notification_summary), checked = draft.focusEnabled, insideMargin = SettingsItemMargin, onCheckedChange = { draft = draft.copy(focusEnabled = it) })
                    SwitchPreference(title = stringResource(R.string.ha_island_first_float), summary = stringResource(R.string.ha_island_first_float_summary), checked = draft.islandFirstFloat, enabled = draft.islandEnabled, insideMargin = SettingsItemMargin, onCheckedChange = { draft = draft.copy(islandFirstFloat = it) })
                    PreferenceDropdown(title = stringResource(R.string.ha_island_carousel_interval), summary = null, icon = null, items = listOf(1, 3, 5, 10, 15, 30).map { stringResource(R.string.ha_seconds, it) }, selectedIndex = listOf(1, 3, 5, 10, 15, 30).indexOf(draft.islandCarouselInterval).coerceAtLeast(0), enabled = draft.islandEnabled) { draft = draft.copy(islandCarouselInterval = listOf(1, 3, 5, 10, 15, 30)[it]) }
                    SwitchPreference(title = stringResource(R.string.ha_show_island_icon), summary = null, checked = draft.showIslandIcon, enabled = draft.islandEnabled, insideMargin = SettingsItemMargin, onCheckedChange = { draft = draft.copy(showIslandIcon = it) })
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f), colors = haButtonColors()) { Text(stringResource(R.string.cancel)) }
                    if (initial != null) Button(onClick = { onDelete(draft) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(color = haSurfaceColor(), contentColor = MiuixTheme.colorScheme.error)) { Text(stringResource(R.string.delete)) }
                    Button(onClick = { onSave(draft) }, enabled = canSave, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary()) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }

    HaEntityPickerDialog(show = pickerTarget != null, entities = entities, selectedEntityId = selectedIdForTarget(draft, pickerTarget), status = status, onDismiss = { pickerTarget = null }, onSelected = { selected ->
        draft = when (pickerTarget) {
            "trigger" -> draft.copy(triggerEntityId = selected.entityId)
            "island_left" -> draft.copy(islandLeftEntityId = selected.entityId)
            "island_right" -> draft.copy(islandRightEntityId = selected.entityId)
            "focus_title" -> draft.copy(focusTitleEntityId = selected.entityId)
            "focus_content" -> draft.copy(focusContentEntityId = selected.entityId)
            "charging_power" -> draft.copy(chargingPowerEntityId = selected.entityId)
            "charging_current" -> draft.copy(chargingCurrentEntityId = selected.entityId)
            "charging_voltage" -> draft.copy(chargingVoltageEntityId = selected.entityId)
            else -> draft
        }
        pickerTarget = null
    }, onRetry = onRetry)
    HaAttributePickerDialog(show = attributeTarget != null, entity = entityMap[selectedIdForTarget(draft, attributeTarget)], selectedAttribute = selectedAttributeForTarget(draft, attributeTarget), onDismiss = { attributeTarget = null }, onSelected = { attribute ->
        draft = when (attributeTarget) {
            "trigger" -> draft.copy(triggerAttribute = attribute)
            "island_left" -> draft.copy(islandLeftAttribute = attribute)
            "island_right" -> draft.copy(islandRightAttribute = attribute)
            "focus_title" -> draft.copy(focusTitleAttribute = attribute)
            "focus_content" -> draft.copy(focusContentAttribute = attribute)
            "charging_power" -> draft.copy(chargingPowerAttribute = attribute)
            "charging_current" -> draft.copy(chargingCurrentAttribute = attribute)
            "charging_voltage" -> draft.copy(chargingVoltageAttribute = attribute)
            else -> draft
        }
        attributeTarget = null
    })
}

@Composable
private fun OutlinedHaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).border(BorderStroke(1.dp, MiuixTheme.colorScheme.outline), RoundedCornerShape(12.dp)),
        label = label,
        useLabelAsPlaceholder = true,
        singleLine = true,
        colors = haTextFieldColors(),
        keyboardOptions = keyboardOptions,
    )
}

@Composable
private fun SourceRow(title: String, summary: String, onClick: () -> Unit) {
    SettingsAction(title = title, summary = summary, endIcon = MiuixIcons.ChevronForward, onClick = onClick)
}

@Composable
private fun ChargingSourceRow(title: String, summary: String, onClick: () -> Unit) = SourceRow(title, summary, onClick)

private fun selectedIdForTarget(s: HaSubscription, target: String?): String = when (target) {
    "trigger" -> s.triggerEntityId
    "island_left" -> s.islandLeftEntityId
    "island_right" -> s.islandRightEntityId
    "focus_title" -> s.focusTitleEntityId
    "focus_content" -> s.focusContentEntityId
    "charging_power" -> s.chargingPowerEntityId
    "charging_current" -> s.chargingCurrentEntityId
    "charging_voltage" -> s.chargingVoltageEntityId
    else -> ""
}

private fun selectedAttributeForTarget(s: HaSubscription, target: String?): String = when (target) {
    "trigger" -> s.triggerAttribute
    "island_left" -> s.islandLeftAttribute
    "island_right" -> s.islandRightAttribute
    "focus_title" -> s.focusTitleAttribute
    "focus_content" -> s.focusContentAttribute
    "charging_power" -> s.chargingPowerAttribute
    "charging_current" -> s.chargingCurrentAttribute
    "charging_voltage" -> s.chargingVoltageAttribute
    else -> ""
}
