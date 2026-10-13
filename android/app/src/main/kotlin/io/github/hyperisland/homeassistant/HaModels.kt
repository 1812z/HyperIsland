package io.github.hyperisland.homeassistant

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Home Assistant 数据模型；同时被设置页与 SystemUI Hook 复用。 */
data class HaEntity(
    val entityId: String,
    val state: String,
    val friendlyName: String,
    val unit: String,
    val attributes: Map<String, String>,
) {
    val title: String get() = friendlyName.ifBlank { entityId }
    val displayValue: String get() = if (unit.isBlank()) state else "$state $unit"
    val icon: String get() = attributes["icon"].orEmpty()

    fun valueOf(attribute: String): String = when {
        attribute.isBlank() || attribute == ATTRIBUTE_STATE -> state
        else -> attributes[attribute].orEmpty()
    }

    companion object {
        const val ATTRIBUTE_STATE = "state"

        fun fromJson(json: JSONObject): HaEntity {
            val attributesJson = json.optJSONObject("attributes")
            val attributes = LinkedHashMap<String, String>()
            attributesJson?.keys()?.forEach { key ->
                val value = attributesJson.opt(key)
                if (value != null && value != JSONObject.NULL) attributes[key] = stringify(value)
            }
            return HaEntity(
                entityId = json.optString("entity_id"),
                state = json.optString("state"),
                friendlyName = attributes["friendly_name"].orEmpty(),
                unit = attributes["unit_of_measurement"].orEmpty(),
                attributes = attributes,
            )
        }

        private fun stringify(value: Any): String = when (value) {
            is String -> value
            is Double -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
            else -> value.toString()
        }
    }
}

enum class HaCondition(val id: String, private val numeric: Boolean = false) {
    Equals("equals"), NotEquals("not_equals"), Above("above", true), Below("below", true),
    Contains("contains");

    val needsValue: Boolean get() = true
    val needsNumber: Boolean get() = numeric

    fun matches(current: String, expected: String): Boolean = when (this) {
        Equals -> current.equals(expected, ignoreCase = true)
        NotEquals -> !current.equals(expected, ignoreCase = true)
        Above -> compare(current, expected) { a, b -> a > b }
        Below -> compare(current, expected) { a, b -> a < b }
        Contains -> expected.isEmpty() || current.contains(expected, true)
    }

    private inline fun compare(current: String, expected: String, p: (Double, Double) -> Boolean): Boolean {
        val a = current.trim().toDoubleOrNull() ?: return false
        val b = expected.trim().toDoubleOrNull() ?: return false
        return p(a, b)
    }

    companion object {
        fun of(id: String): HaCondition = entries.firstOrNull { it.id == id } ?: Equals
    }
}

object HaTemplateType {
    const val CUSTOM = "custom"
    const val CHARGING = "charging"
}

/** 一条 Home Assistant 订阅规则。 */
data class HaSubscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val triggerEntityId: String = "",
    val triggerAttribute: String = "",
    val condition: String = HaCondition.Equals.id,
    val value: String = "",
    val islandEnabled: Boolean = true,
    val focusEnabled: Boolean = false,
    val islandFirstFloat: Boolean = false,
    val islandLeftTemplate: String = "{value}",
    val islandLeftEntityId: String = "",
    val islandLeftAttribute: String = "",
    val islandRightTemplate: String = "",
    val islandRightEntityId: String = "",
    val islandRightAttribute: String = "",
    val islandCarouselInterval: Int = 5,
    val focusTitleTemplate: String = "{name}",
    val focusTitleEntityId: String = "",
    val focusTitleAttribute: String = "",
    val focusContentTemplate: String = "{value}",
    val focusContentEntityId: String = "",
    val focusContentAttribute: String = "",
    val templateType: String = HaTemplateType.CUSTOM,
    val chargingPowerEntityId: String = "",
    val chargingPowerAttribute: String = "",
    val chargingCurrentEntityId: String = "",
    val chargingCurrentAttribute: String = "",
    val chargingVoltageEntityId: String = "",
    val chargingVoltageAttribute: String = "",
    val showIslandIcon: Boolean = true,
    val enabled: Boolean = true,
) {
    val conditionType: HaCondition get() = HaCondition.of(condition)
    val notifId: Int get() = HaNotifIds.of(id)
    val isValid: Boolean get() = triggerEntityId.isNotBlank()
    val dataEntityIds: Set<String>
        get() = listOf(
            triggerEntityId,
            islandLeftEntityId,
            islandRightEntityId,
            focusTitleEntityId,
            focusContentEntityId,
            chargingPowerEntityId,
            chargingCurrentEntityId,
            chargingVoltageEntityId,
        ).filter(String::isNotBlank).toSet()
    fun toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("triggerEntityId", triggerEntityId)
        put("triggerAttribute", triggerAttribute)
        put("condition", condition)
        put("value", value)
        put("islandEnabled", islandEnabled)
        put("focusEnabled", focusEnabled)
        put("islandFirstFloat", islandFirstFloat)
        put("islandLeftTemplate", islandLeftTemplate)
        put("islandLeftEntityId", islandLeftEntityId)
        put("islandLeftAttribute", islandLeftAttribute)
        put("islandRightTemplate", islandRightTemplate)
        put("islandRightEntityId", islandRightEntityId)
        put("islandRightAttribute", islandRightAttribute)
        put("islandCarouselInterval", islandCarouselInterval)
        put("focusTitleTemplate", focusTitleTemplate)
        put("focusTitleEntityId", focusTitleEntityId)
        put("focusTitleAttribute", focusTitleAttribute)
        put("focusContentTemplate", focusContentTemplate)
        put("focusContentEntityId", focusContentEntityId)
        put("focusContentAttribute", focusContentAttribute)
        put("templateType", templateType)
        put("chargingPowerEntityId", chargingPowerEntityId)
        put("chargingPowerAttribute", chargingPowerAttribute)
        put("chargingCurrentEntityId", chargingCurrentEntityId)
        put("chargingCurrentAttribute", chargingCurrentAttribute)
        put("chargingVoltageEntityId", chargingVoltageEntityId)
        put("chargingVoltageAttribute", chargingVoltageAttribute)
        put("showIslandIcon", showIslandIcon)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(json: JSONObject): HaSubscription = HaSubscription(
                id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = json.optString("name"),
                triggerEntityId = json.optString("triggerEntityId"),
                triggerAttribute = json.optString("triggerAttribute"),
                condition = json.optString("condition", HaCondition.Equals.id),
                value = json.optString("value"),
                islandEnabled = json.optBoolean("islandEnabled", true),
                focusEnabled = json.optBoolean("focusEnabled", false),
                islandFirstFloat = json.optBoolean("islandFirstFloat", false),
                islandLeftTemplate = json.optString("islandLeftTemplate", "{value}"),
                islandLeftEntityId = json.optString("islandLeftEntityId"),
                islandLeftAttribute = json.optString("islandLeftAttribute"),
                islandRightTemplate = json.optString("islandRightTemplate"),
                islandRightEntityId = json.optString("islandRightEntityId"),
                islandRightAttribute = json.optString("islandRightAttribute"),
                islandCarouselInterval = json.optInt("islandCarouselInterval", 5).coerceIn(1, 6000),
                focusTitleTemplate = json.optString("focusTitleTemplate", "{name}"),
                focusTitleEntityId = json.optString("focusTitleEntityId"),
                focusTitleAttribute = json.optString("focusTitleAttribute"),
                focusContentTemplate = json.optString("focusContentTemplate", "{value}"),
                focusContentEntityId = json.optString("focusContentEntityId"),
                focusContentAttribute = json.optString("focusContentAttribute"),
                templateType = json.optString("templateType", HaTemplateType.CUSTOM),
                chargingPowerEntityId = json.optString("chargingPowerEntityId"),
                chargingPowerAttribute = json.optString("chargingPowerAttribute"),
                chargingCurrentEntityId = json.optString("chargingCurrentEntityId"),
                chargingCurrentAttribute = json.optString("chargingCurrentAttribute"),
                chargingVoltageEntityId = json.optString("chargingVoltageEntityId"),
                chargingVoltageAttribute = json.optString("chargingVoltageAttribute"),
                showIslandIcon = json.optBoolean("showIslandIcon", true),
                enabled = json.optBoolean("enabled", true),
            )

        fun parseList(raw: String): List<HaSubscription> = runCatching {
            val array = JSONArray(raw)
            buildList { for (i in 0 until array.length()) array.optJSONObject(i)?.let { add(fromJson(it)) } }
        }.getOrDefault(emptyList())

        fun encode(list: List<HaSubscription>): String = JSONArray().apply {
            list.forEach { put(it.toJson()) }
        }.toString()
    }
}

object HaNotifIds {
    private const val BASE = 0x48410000
    fun of(subscriptionId: String): Int = BASE or (subscriptionId.hashCode() and 0xFFFF)
}

/** 支持 {name}/{value} 等内置变量，也支持 {实体id.属性} 的跨实体占位符。 */
object HaTemplate {
    private val TOKEN = Regex("""\{([^{}]+)\}""")
    const val DEFAULT_TITLE = "{name}"
    const val DEFAULT_CONTENT = "{value}"
    val PLACEHOLDERS = listOf("{name}", "{state}", "{value}", "{unit}", "{entity}", "{raw}")

    fun referencePlaceholders(entities: List<HaEntity>): List<String> = entities.flatMap { entity ->
        (listOf("state") + entity.attributes.keys).distinct().map { attribute -> "{${entity.entityId}.$attribute}" }
    }.distinct()

    fun title(s: HaSubscription, entity: HaEntity, entities: Map<String, HaEntity> = emptyMap()): String =
        render(s.focusTitleTemplate.ifBlank { DEFAULT_TITLE }, entity, s.focusTitleAttribute, entities)

    fun content(s: HaSubscription, entity: HaEntity, entities: Map<String, HaEntity> = emptyMap()): String =
        render(s.focusContentTemplate.ifBlank { DEFAULT_CONTENT }, entity, s.focusContentAttribute, entities)

    fun islandTexts(s: HaSubscription, entities: Map<String, HaEntity>, index: Int = 0): Pair<String, String> {
        if (s.templateType == HaTemplateType.CHARGING) {
            val power = sourceValue(entities, s.chargingPowerEntityId, s.chargingPowerAttribute)
            val current = sourceValue(entities, s.chargingCurrentEntityId, s.chargingCurrentAttribute)
            val voltage = sourceValue(entities, s.chargingVoltageEntityId, s.chargingVoltageAttribute)
            return "充电" to listOf(power, current, voltage).filter(String::isNotBlank).joinToString(" · ")
        }
        // 使用 `||` 分隔多个轮播项，便于在普通输入框中手动编辑。
        val leftTemplates = s.islandLeftTemplate.split("||").map(String::trim).filter(String::isNotBlank).ifEmpty { listOf("") }
        val rightTemplates = s.islandRightTemplate.split("||").map(String::trim).filter(String::isNotBlank).ifEmpty { listOf("") }
        val left = renderSource(leftTemplates[index % leftTemplates.size], s.islandLeftEntityId, s.islandLeftAttribute, entities)
        val right = renderSource(rightTemplates[index % rightTemplates.size], s.islandRightEntityId, s.islandRightAttribute, entities)
        return left to right
    }

    fun focusTexts(s: HaSubscription, entities: Map<String, HaEntity>): Pair<String, String> {
        if (s.templateType == HaTemplateType.CHARGING) {
            val power = sourceValue(entities, s.chargingPowerEntityId, s.chargingPowerAttribute)
            val current = sourceValue(entities, s.chargingCurrentEntityId, s.chargingCurrentAttribute)
            val voltage = sourceValue(entities, s.chargingVoltageEntityId, s.chargingVoltageAttribute)
            return "充电" to listOf(power, current, voltage).filter(String::isNotBlank).joinToString(" · ")
        }
        return renderSource(s.focusTitleTemplate, s.focusTitleEntityId, s.focusTitleAttribute, entities) to
            renderSource(s.focusContentTemplate, s.focusContentEntityId, s.focusContentAttribute, entities)
    }

    private fun renderSource(template: String, entityId: String, attribute: String, entities: Map<String, HaEntity>): String {
        val entity = entities[entityId] ?: entities.values.firstOrNull() ?: return ""
        return render(template, entity, attribute, entities)
    }

    private fun sourceValue(entities: Map<String, HaEntity>, entityId: String, attribute: String): String =
        entities[entityId]?.let { displayValue(it, attribute) }.orEmpty()

    fun render(template: String, entity: HaEntity, attribute: String, entities: Map<String, HaEntity> = emptyMap()): String =
        TOKEN.replace(template) { match ->
            val token = match.groupValues[1]
            when (token) {
                "name" -> entity.title
                "entity" -> entity.entityId
                "state" -> entity.state
                "value" -> displayValue(entity, attribute)
                "raw" -> entity.valueOf(attribute)
                "unit" -> entity.unit
                else -> resolveReference(token, entities, entity)
            }
        }.trim()

    private fun resolveReference(token: String, entities: Map<String, HaEntity>, fallback: HaEntity): String {
        val candidates = (entities.keys + fallback.entityId).distinct().sortedByDescending { it.length }
        val id = candidates.firstOrNull { token == it || token.startsWith("$it.") } ?: return ""
        val attr = token.removePrefix(id).removePrefix(".")
        return (entities[id] ?: fallback).valueOf(attr)
    }

    fun displayValue(entity: HaEntity, attribute: String): String =
        if (attribute.isBlank() || attribute == HaEntity.ATTRIBUTE_STATE) entity.displayValue else entity.valueOf(attribute)
    fun signature(title: String, content: String): String = "$title\u0000$content"
    fun describe(condition: HaCondition, value: String): String = value
}
