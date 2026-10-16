package io.github.hyperisland.compose.page.settings.extensions.homeassistant

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.SettingsAction
import io.github.hyperisland.compose.component.SettingsItemMargin
import io.github.hyperisland.compose.component.keepisland.KeepIslandContentListDialog
import io.github.hyperisland.compose.service.KeepIslandService
import io.github.hyperisland.homeassistant.HaCondition
import io.github.hyperisland.homeassistant.HaEntity
import io.github.hyperisland.homeassistant.HaSubscription
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
import top.yukonga.miuix.kmp.window.WindowDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var pickerTarget by remember(show) { mutableStateOf<String?>(null) }
    var attributeTarget by remember(show) { mutableStateOf<String?>(null) }
    var placeholderEntityId by remember(show) { mutableStateOf("") }
    var textTarget by remember(show) { mutableStateOf<String?>(null) }
    // 显示条件（比较方式 + 参考值）与显示时长都走弹窗，避免 sheet 上堆表单。
    var conditionDialog by remember(show) { mutableStateOf(false) }
    var durationDialog by remember(show) { mutableStateOf(false) }
    val entityMap = remember(entities) { entities.associateBy(HaEntity::entityId) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 设备图标 / 岛图标共用「常驻岛」那套图片保存机制（同目录、同命名、同权限处理）。
    val pickDeviceIcon = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val path = withContext(Dispatchers.IO) {
                KeepIslandService.saveIcon(context, uri, draft.deviceIconPath).getOrNull()
            }
            if (path != null) draft = draft.copy(deviceIconPath = path)
        }
    }
    val pickIslandIcon = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val path = withContext(Dispatchers.IO) {
                KeepIslandService.saveIcon(context, uri, draft.islandIconPath).getOrNull()
            }
            if (path != null) draft = draft.copy(islandIconPath = path)
        }
    }
    val conditionItems = HaCondition.entries
    val condition = draft.conditionType
    val canSave = draft.isValid

    @Composable
    fun entityLabel(id: String, attribute: String): String =
        entityMap[id]?.let { "${it.title} · ${attribute.ifBlank { HaEntity.ATTRIBUTE_STATE }}" }
            ?: stringResource(R.string.ha_entity_none)

    @Composable
    fun entityTitle(id: String): String =
        entityMap[id]?.title ?: stringResource(R.string.ha_entity_none)

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
                // 这个输入框直接落在 sheet 上（不在 Card 内）：底色取 Card 同色，
                // 且不加左右内缩，宽度与下面的 Card 左右边缘完全对齐。
                HaTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = stringResource(R.string.ha_subscription_name),
                    containerColor = haSurfaceColor(),
                    horizontalPadding = 0.dp,
                )
            }
            item {
                SmallTitle(stringResource(R.string.ha_trigger_entity))
                Card(modifier = Modifier.fillMaxWidth(), colors = haCardColors()) {
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
                    // 比较方式本身就是个下拉，直接用默认组件留在卡片里，不进弹窗。
                    PreferenceDropdown(
                        title = stringResource(R.string.ha_condition),
                        summary = condition.label(),
                        icon = null,
                        items = conditionItems.map { it.label() },
                        selectedIndex = conditionItems.indexOf(condition).coerceAtLeast(0),
                    ) { index -> draft = draft.copy(condition = conditionItems[index].id) }
                    // 参考值走弹窗，弹窗里只有一个输入框。
                    SourceRow(
                        title = stringResource(R.string.ha_condition_value),
                        summary = draft.value.ifBlank { stringResource(R.string.not_set) },
                    ) { conditionDialog = true }
                    // 显示时机：常驻（默认）或自定义秒数。
                    PreferenceDropdown(
                        title = stringResource(R.string.ha_display_mode),
                        summary = if (draft.displaySeconds <= 0) {
                            stringResource(R.string.ha_display_keep)
                        } else {
                            stringResource(R.string.ha_seconds, draft.displaySeconds)
                        },
                        icon = null,
                        items = listOf(
                            stringResource(R.string.ha_display_keep),
                            stringResource(R.string.ha_display_custom),
                        ),
                        selectedIndex = if (draft.displaySeconds <= 0) 0 else 1,
                    ) { index ->
                        draft = if (index == 0) {
                            draft.copy(displaySeconds = 0)
                        } else {
                            // 切到自定义先给个默认值，否则下面那行点开来是空的。
                            draft.copy(displaySeconds = draft.displaySeconds.takeIf { it > 0 } ?: DEFAULT_DISPLAY_SECONDS)
                        }
                    }
                    AnimatedVisibility(visible = draft.displaySeconds > 0) {
                        Column {
                            SourceRow(
                                title = stringResource(R.string.ha_display_seconds),
                                summary = stringResource(R.string.ha_seconds, draft.displaySeconds),
                            ) { durationDialog = true }
                        }
                    }
                }
            }
            item {
                SmallTitle(stringResource(R.string.ha_island_config))
                Card(modifier = Modifier.fillMaxWidth(), colors = haCardColors()) {
                    SwitchPreference(title = stringResource(R.string.ha_island_config), summary = null, checked = draft.islandEnabled, insideMargin = SettingsItemMargin, onCheckedChange = { draft = draft.copy(islandEnabled = it) })
                    // 关掉超级岛时把它的整块配置一起收起（与渠道设置页同款出现/消失动画）。
                    AnimatedVisibility(visible = draft.islandEnabled) {
                        Column {
                            SwitchPreference(title = stringResource(R.string.ha_island_first_float), summary = stringResource(R.string.ha_island_first_float_summary), checked = draft.islandFirstFloat, insideMargin = SettingsItemMargin, onCheckedChange = { draft = draft.copy(islandFirstFloat = it) })
                            PreferenceDropdown(title = stringResource(R.string.ha_island_carousel_interval), summary = null, icon = null, items = listOf(1, 3, 5, 10, 15, 30).map { stringResource(R.string.ha_seconds, it) }, selectedIndex = listOf(1, 3, 5, 10, 15, 30).indexOf(draft.islandCarouselInterval).coerceAtLeast(0)) { draft = draft.copy(islandCarouselInterval = listOf(1, 3, 5, 10, 15, 30)[it]) }
                            SwitchPreference(title = stringResource(R.string.ha_show_island_icon), summary = null, checked = draft.showIslandIcon, insideMargin = SettingsItemMargin, onCheckedChange = { draft = draft.copy(showIslandIcon = it) })
                            // 岛图标来源：默认跟随实体（自动读 mdi 图标），也可以换成自定义图片。
                            AnimatedVisibility(visible = draft.showIslandIcon) {
                                Column {
                                    PreferenceDropdown(
                                        title = stringResource(R.string.ha_island_icon_source),
                                        summary = if (draft.islandIconFollow) stringResource(R.string.ha_island_icon_follow) else stringResource(R.string.ha_island_icon_custom),
                                        icon = null,
                                        items = listOf(stringResource(R.string.ha_island_icon_follow), stringResource(R.string.ha_island_icon_custom)),
                                        selectedIndex = if (draft.islandIconFollow) 0 else 1,
                                    ) { index -> draft = draft.copy(islandIconFollow = index == 0) }
                                    AnimatedVisibility(visible = !draft.islandIconFollow) {
                                        Column {
                                            SourceRow(stringResource(R.string.ha_island_icon_custom), iconFileLabel(draft.islandIconPath)) { pickIslandIcon.launch(arrayOf("image/*")) }
                                        }
                                    }
                                }
                            }
                            // 岛左/岛右只填内容模板，取值直接用 `{实体.属性}` 占位符（配合「选取变量」复制）。
                            SourceRow(stringResource(R.string.ha_island_left_content), draft.islandLeftTemplate) { textTarget = "island_left" }
                            SourceRow(stringResource(R.string.ha_island_right_content), draft.islandRightTemplate) { textTarget = "island_right" }
                        }
                    }
                }
            }
            item {
                SmallTitle(stringResource(R.string.ha_focus_notification))
                Card(modifier = Modifier.fillMaxWidth(), colors = haCardColors()) {
                    SwitchPreference(title = stringResource(R.string.ha_focus_notification), summary = stringResource(R.string.ha_focus_notification_summary), checked = draft.focusEnabled, insideMargin = SettingsItemMargin, onCheckedChange = { draft = draft.copy(focusEnabled = it) })
                    AnimatedVisibility(visible = draft.focusEnabled) {
                        Column {
                            PreferenceDropdown(
                                title = stringResource(R.string.ha_template_type),
                                summary = when (draft.templateType) {
                                    HaTemplateType.CHARGING -> stringResource(R.string.ha_template_charging)
                                    HaTemplateType.DEVICE -> stringResource(R.string.ha_template_device)
                                    else -> stringResource(R.string.ha_template_custom)
                                },
                                icon = null,
                                items = listOf(
                                    stringResource(R.string.ha_template_custom),
                                    stringResource(R.string.ha_template_charging),
                                    stringResource(R.string.ha_template_device),
                                ),
                                selectedIndex = when (draft.templateType) {
                                    HaTemplateType.CHARGING -> 1
                                    HaTemplateType.DEVICE -> 2
                                    else -> 0
                                },
                            ) { index ->
                                draft = draft.copy(
                                    templateType = when (index) {
                                        1 -> HaTemplateType.CHARGING
                                        2 -> HaTemplateType.DEVICE
                                        else -> HaTemplateType.CUSTOM
                                    },
                                )
                            }
                            AnimatedVisibility(visible = draft.templateType == HaTemplateType.CHARGING) {
                                Column {
                                    ChargingSourceRow(stringResource(R.string.ha_charging_power), entityLabel(draft.chargingPowerEntityId, draft.chargingPowerAttribute)) { pickEntity("charging_power") }
                                    ChargingSourceRow(stringResource(R.string.ha_charging_current), entityLabel(draft.chargingCurrentEntityId, draft.chargingCurrentAttribute)) { pickEntity("charging_current") }
                                    ChargingSourceRow(stringResource(R.string.ha_charging_voltage), entityLabel(draft.chargingVoltageEntityId, draft.chargingVoltageAttribute)) { pickEntity("charging_voltage") }
                                }
                            }
                            // 设备面板：3 行文本 + 1~2 个百分比实体 + 面板左侧图标（不影响超级岛图标）。
                            AnimatedVisibility(visible = draft.templateType == HaTemplateType.DEVICE) {
                                Column {
                                    SourceRow(stringResource(R.string.ha_device_line_1), draft.deviceLine1) { textTarget = "device_line_1" }
                                    SourceRow(stringResource(R.string.ha_device_line_2), draft.deviceLine2) { textTarget = "device_line_2" }
                                    SourceRow(stringResource(R.string.ha_device_line_3), draft.deviceLine3) { textTarget = "device_line_3" }
                                    // 底部进度组件数量：1 个时只有左列，右列整块收起。
                                    PreferenceDropdown(
                                        title = stringResource(R.string.ha_device_metric_count),
                                        summary = if (draft.deviceMetricCount <= 1) {
                                            stringResource(R.string.ha_device_metric_count_one)
                                        } else {
                                            stringResource(R.string.ha_device_metric_count_two)
                                        },
                                        icon = null,
                                        items = listOf(
                                            stringResource(R.string.ha_device_metric_count_one),
                                            stringResource(R.string.ha_device_metric_count_two),
                                        ),
                                        selectedIndex = if (draft.deviceMetricCount <= 1) 0 else 1,
                                    ) { index -> draft = draft.copy(deviceMetricCount = if (index == 0) 1 else 2) }
                                    SourceRow(stringResource(R.string.ha_device_metric_left), entityTitle(draft.deviceLeftEntityId)) { pickEntity("device_left") }
                                    AnimatedVisibility(visible = draft.deviceMetricCount > 1) {
                                        Column {
                                            SourceRow(stringResource(R.string.ha_device_metric_right), entityTitle(draft.deviceRightEntityId)) { pickEntity("device_right") }
                                        }
                                    }
                                    // 左侧图标来源：本地自定义图片，或直接取实体图片（image / camera 的 entity_picture）。
                                    PreferenceDropdown(
                                        title = stringResource(R.string.ha_device_icon_source),
                                        summary = if (draft.deviceIconFromEntity) {
                                            stringResource(R.string.ha_device_icon_entity)
                                        } else {
                                            stringResource(R.string.ha_device_icon_custom_image)
                                        },
                                        icon = null,
                                        items = listOf(
                                            stringResource(R.string.ha_device_icon_custom_image),
                                            stringResource(R.string.ha_device_icon_entity),
                                        ),
                                        selectedIndex = if (draft.deviceIconFromEntity) 1 else 0,
                                    ) { index -> draft = draft.copy(deviceIconFromEntity = index == 1) }
                                    AnimatedVisibility(visible = !draft.deviceIconFromEntity) {
                                        Column {
                                            SourceRow(stringResource(R.string.ha_device_icon), iconFileLabel(draft.deviceIconPath)) { pickDeviceIcon.launch(arrayOf("image/*")) }
                                        }
                                    }
                                    AnimatedVisibility(visible = draft.deviceIconFromEntity) {
                                        Column {
                                            SourceRow(stringResource(R.string.ha_device_icon_entity), entityTitle(draft.deviceIconEntityId)) { pickEntity("device_icon") }
                                        }
                                    }
                                }
                            }
                            // 焦点通知同样只填模板，实体取值走占位符。
                            AnimatedVisibility(visible = draft.templateType == HaTemplateType.CUSTOM) {
                                Column {
                                    SourceRow(stringResource(R.string.ha_focus_title_template), draft.focusTitleTemplate) { textTarget = "focus_title" }
                                    SourceRow(stringResource(R.string.ha_focus_content_template), draft.focusContentTemplate) { textTarget = "focus_content" }
                                }
                            }
                        }
                    }
                }
            }
            item {
                SmallTitle(stringResource(R.string.ha_select_variable))
                Card(modifier = Modifier.fillMaxWidth(), colors = haCardColors()) {
                    SourceRow(stringResource(R.string.ha_select_variable), stringResource(R.string.ha_select_variable_summary)) {
                        // 每次打开都从空白开始：变量选取是一次性的复制助手，
                        // 带着上一次选的实体重新打开会多出一个勾选，容易误以为已经配好了。
                        placeholderEntityId = ""
                        pickerTarget = "placeholder"
                    }
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

    HaEntityPickerDialog(
        show = pickerTarget != null,
        entities = entities,
        selectedEntityId = if (pickerTarget == "placeholder") placeholderEntityId else selectedIdForTarget(draft, pickerTarget),
        // 设备面板的两个进度源只接受百分比类型的实体；左侧图标选实体时只列出带图片的实体。
        percentOnly = pickerTarget == "device_left" || pickerTarget == "device_right",
        imageOnly = pickerTarget == "device_icon",
        status = status,
        onDismiss = { pickerTarget = null },
        onSelected = { selected ->
        if (pickerTarget == "placeholder") {
            placeholderEntityId = selected.entityId
            pickerTarget = null
            attributeTarget = "placeholder"
        } else draft = when (pickerTarget) {
            "trigger" -> draft.copy(triggerEntityId = selected.entityId)
            "charging_power" -> draft.copy(chargingPowerEntityId = selected.entityId)
            "charging_current" -> draft.copy(chargingCurrentEntityId = selected.entityId)
            "charging_voltage" -> draft.copy(chargingVoltageEntityId = selected.entityId)
            "device_left" -> draft.copy(deviceLeftEntityId = selected.entityId)
            "device_right" -> draft.copy(deviceRightEntityId = selected.entityId)
            "device_icon" -> draft.copy(deviceIconEntityId = selected.entityId)
            else -> draft
        }
        // 选完实体紧接着选属性（触发实体与充电三项）；百分比实体与图片实体直接取 state，不需要再选属性。
        val target = pickerTarget
        val noAttribute = setOf("placeholder", "device_left", "device_right", "device_icon")
        if (target != null && target !in noAttribute) {
            attributeTarget = target
        }
        pickerTarget = null
    }, onRetry = onRetry)
    HaAttributePickerDialog(show = attributeTarget != null, entity = entityMap[if (attributeTarget == "placeholder") placeholderEntityId else selectedIdForTarget(draft, attributeTarget)], selectedAttribute = selectedAttributeForTarget(draft, attributeTarget),
        // 只有触发属性打开时标出已启用的那一项；充电三项与「选取变量」打开即空白，不自动勾选。
        highlightSelected = attributeTarget == "trigger", onDismiss = { attributeTarget = null }, onSelected = { attribute ->
        if (attributeTarget == "placeholder") {
            val placeholder = "{${placeholderEntityId}.${attribute.ifBlank { HaEntity.ATTRIBUTE_STATE }}}"
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("Home Assistant", placeholder))
            Toast.makeText(context, context.getString(R.string.ha_placeholder_copied), Toast.LENGTH_SHORT).show()
            attributeTarget = null
        } else draft = when (attributeTarget) {
            "trigger" -> draft.copy(triggerAttribute = attribute)
            "charging_power" -> draft.copy(chargingPowerAttribute = attribute)
            "charging_current" -> draft.copy(chargingCurrentAttribute = attribute)
            "charging_voltage" -> draft.copy(chargingVoltageAttribute = attribute)
            else -> draft
        }
        attributeTarget = null
    })

    HaConditionValueDialog(
        show = conditionDialog,
        needsNumber = condition.needsNumber,
        value = draft.value,
        onDismiss = { conditionDialog = false },
        onSave = { nextValue ->
            draft = draft.copy(value = nextValue)
            conditionDialog = false
        },
    )
    HaDurationDialog(
        show = durationDialog,
        seconds = draft.displaySeconds,
        onDismiss = { durationDialog = false },
        onSave = { seconds ->
            draft = draft.copy(displaySeconds = seconds)
            durationDialog = false
        },
    )

    HaTemplateTextDialog(
        show = textTarget != null && textTarget in TEXT_TARGETS,
        title = stringResource(
            when (textTarget) {
                "focus_title" -> R.string.ha_focus_title_template
                "focus_content" -> R.string.ha_focus_content_template
                "device_line_1" -> R.string.ha_device_line_1
                "device_line_2" -> R.string.ha_device_line_2
                else -> R.string.ha_device_line_3
            },
        ),
        initialValue = when (textTarget) {
            "focus_title" -> draft.focusTitleTemplate
            "focus_content" -> draft.focusContentTemplate
            "device_line_1" -> draft.deviceLine1
            "device_line_2" -> draft.deviceLine2
            else -> draft.deviceLine3
        },
        onDismiss = { textTarget = null },
        onSave = { value ->
            draft = when (textTarget) {
                "focus_title" -> draft.copy(focusTitleTemplate = value)
                "focus_content" -> draft.copy(focusContentTemplate = value)
                "device_line_1" -> draft.copy(deviceLine1 = value)
                "device_line_2" -> draft.copy(deviceLine2 = value)
                else -> draft.copy(deviceLine3 = value)
            }
            textTarget = null
        },
    )

    KeepIslandContentListDialog(
        show = textTarget == "island_left",
        title = stringResource(R.string.ha_island_left_content),
        initialValues = draft.islandLeftTemplate.split("||"),
        onDismiss = { textTarget = null },
        onSave = { values ->
            draft = draft.copy(islandLeftTemplate = values.joinToString(" || ") { it.trim() })
            textTarget = null
        },
    )
    KeepIslandContentListDialog(
        show = textTarget == "island_right",
        title = stringResource(R.string.ha_island_right_content),
        initialValues = draft.islandRightTemplate.split("||"),
        onDismiss = { textTarget = null },
        onSave = { values ->
            draft = draft.copy(islandRightTemplate = values.joinToString(" || ") { it.trim() })
            textTarget = null
        },
    )
}

@Composable
private fun HaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    containerColor: Color = haSheetColor(),
    horizontalPadding: Dp = 12.dp,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        // 统一宽度；水平内缩可调：Card 内的输入框内缩 12dp，直接落在 sheet 上的不内缩（与 Card 边缘对齐）。
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 6.dp),
        label = label,
        useLabelAsPlaceholder = true,
        singleLine = true,
        colors = haTextFieldColors(containerColor),
        keyboardOptions = keyboardOptions,
    )
}

@Composable
private fun HaTemplateTextDialog(
    show: Boolean,
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var value by remember(show, initialValue) { mutableStateOf(initialValue) }
    WindowDialog(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.ha_template_input_hint),
                useLabelAsPlaceholder = true,
                minLines = 2,
                maxLines = 4,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onSave(value) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

/** 自定义显示时长的默认值（秒）：切到「自定义」时先填这个，用户再改。 */
private const val DEFAULT_DISPLAY_SECONDS = 10

/** 显示时长上限（24 小时）：防止手滑输入一个天文数字把岛钉死。 */
private const val MAX_DISPLAY_SECONDS = 86400

/**
 * 显示条件的「参考值」编辑。
 *
 * 弹窗里只放一个输入框 + 默认的取消/保存（比较方式留在 sheet 的下拉里）。
 * 全部走 Miuix 默认组件：不覆写对话框底色、不给输入框套自定义颜色、不套 Card。
 */
@Composable
private fun HaConditionValueDialog(
    show: Boolean,
    needsNumber: Boolean,
    value: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(show, value) { mutableStateOf(value) }
    WindowDialog(
        show = show,
        title = stringResource(R.string.ha_condition_value),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.ha_condition_value),
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = if (needsNumber) {
                    KeyboardOptions(keyboardType = KeyboardType.Number)
                } else {
                    KeyboardOptions.Default
                },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onSave(text) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

/** 显示时长编辑（秒）：1 ~ [MAX_DISPLAY_SECONDS]，输入非法时禁用保存。 */
@Composable
private fun HaDurationDialog(
    show: Boolean,
    seconds: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var text by remember(show, seconds) { mutableStateOf(seconds.takeIf { it > 0 }?.toString().orEmpty()) }
    val parsed = text.trim().toIntOrNull()
    val valid = parsed != null && parsed in 1..MAX_DISPLAY_SECONDS
    WindowDialog(
        show = show,
        title = stringResource(R.string.ha_display_seconds),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextField(
                value = text,
                onValueChange = { input -> text = input.filter(Char::isDigit).take(5) },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.ha_display_seconds_hint, MAX_DISPLAY_SECONDS),
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { parsed?.let(onSave) },
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun SourceRow(title: String, summary: String, onClick: () -> Unit) {
    SettingsAction(title = title, summary = summary, endIcon = MiuixIcons.ChevronForward, onClick = onClick)
}

@Composable
private fun ChargingSourceRow(title: String, summary: String, onClick: () -> Unit) = SourceRow(title, summary, onClick)

/** 走文本编辑对话框的目标：焦点通知的标题/内容，以及设备面板的 3 行文本。 */
private val TEXT_TARGETS = setOf(
    "focus_title",
    "focus_content",
    "device_line_1",
    "device_line_2",
    "device_line_3",
)

/** 已选图片显示文件名，未选时显示「点击选择文件」。 */
@Composable
private fun iconFileLabel(path: String): String =
    path.takeIf(String::isNotBlank)?.substringAfterLast('/')
        ?: stringResource(R.string.click_select_file)

private fun selectedIdForTarget(s: HaSubscription, target: String?): String = when (target) {
    "trigger" -> s.triggerEntityId
    "charging_power" -> s.chargingPowerEntityId
    "charging_current" -> s.chargingCurrentEntityId
    "charging_voltage" -> s.chargingVoltageEntityId
    "device_left" -> s.deviceLeftEntityId
    "device_right" -> s.deviceRightEntityId
    "device_icon" -> s.deviceIconEntityId
    else -> ""
}

private fun selectedAttributeForTarget(s: HaSubscription, target: String?): String = when (target) {
    "trigger" -> s.triggerAttribute
    "charging_power" -> s.chargingPowerAttribute
    "charging_current" -> s.chargingCurrentAttribute
    "charging_voltage" -> s.chargingVoltageAttribute
    else -> ""
}
