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
    val domain: String get() = entityId.substringBefore('.', "")

    /**
     * 实体图片地址（**相对路径**，绝对地址由 Hook 侧补 base）。
     *
     * 优先级：
     * 1. 服务端下发的 `entity_picture` —— image / camera 都会给，形如
     *    `/api/image_proxy/<id>?token=<access_token>`，最可靠；
     * 2. 没有该属性时按官方代理接口自己拼，令牌取 `access_token` 属性；
     * 3. 连令牌都没有就返回不带查询串的接口地址 —— 请求带上 `Authorization: Bearer`
     *    同样能过鉴权（官方 REST 示例就是这么调的）。
     *
     * ⚠️ **绝对不能用 `state` 当令牌**（曾经的 bug）：image 实体的 state 是
     * `image_last_updated` 时间戳，camera 的 state 是 idle / recording 之类，
     * 真正的令牌只存在于 `access_token` 属性里（HA 核心拼 `entity_picture` 用的也是它）。
     */
    val entityPictureUrl: String
        get() {
            attributes["entity_picture"]?.takeIf(String::isNotBlank)?.let { return it }
            val token = attributes["access_token"].orEmpty()
            val path = when (domain) {
                "image" -> "/api/image_proxy/$entityId"
                "camera" -> "/api/camera_proxy/$entityId"
                else -> return ""
            }
            return if (token.isBlank()) path else "$path?token=$token"
        }

    /**
     * 图片版本号，Hook 侧拿它当缓存失效条件：**图片没换就一次都不重新下载**。
     *
     * HA 的 image 实体每次换图都会刷新 `image_last_updated`（并且它本身就是该实体的 `state`），
     * 这个变化会随 `state_changed` 推过来，所以「图片变了才更新」是可以做到的 ——
     * 不需要靠定时轮询。
     *
     * 其它域（camera 等）没有这个语义，返回空串，由 Hook 侧的 TTL 兜底。
     */
    val imageVersion: String
        get() {
            attributes["image_last_updated"]?.takeIf(String::isNotBlank)?.let { return it }
            // 少数集成不下发该属性，但 image 实体的 state 就是同一个时间戳，可作等价物。
            return if (domain == "image" && state.isNotBlank() &&
                state != STATE_UNAVAILABLE && state != STATE_UNKNOWN
            ) {
                state
            } else {
                ""
            }
        }

    fun valueOf(attribute: String): String = when {
        attribute.isBlank() || attribute == ATTRIBUTE_STATE -> state
        else -> attributes[attribute].orEmpty()
    }

    companion object {
        const val ATTRIBUTE_STATE = "state"
        const val STATE_UNAVAILABLE = "unavailable"
        const val STATE_UNKNOWN = "unknown"

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
            ).also {
                // icon is intentionally retained in attributes; the SystemUI side logs the
                // complete resolution chain when the subscription posts.
            }
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
    /** 设备面板：RemoteViews 布局（3 行文本 + 2 个百分比进度）。 */
    const val DEVICE = "device"
}

/** 一条 Home Assistant 订阅规则。 */
data class HaSubscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val triggerEntityId: String = "",
    val triggerAttribute: String = "",
    val condition: String = HaCondition.Equals.id,
    val value: String = "",
    /**
     * 显示时长（秒）。**0 = 常驻**：条件满足就一直显示，条件不再满足立刻撤掉；
     * 大于 0 则投递后自动消失（计时交给系统侧的岛超时，我们不额外起定时器）。
     */
    val displaySeconds: Int = 0,
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
    // ---- 设备面板模板（DEVICE）：3 行自定义文本 + 1~2 个百分比实体 + 左侧图标 ----
    val deviceLine1: String = "{name}",
    val deviceLine2: String = "{state}",
    val deviceLine3: String = "",
    val deviceLeftEntityId: String = "",
    val deviceRightEntityId: String = "",
    /** 左侧图标来源：true = 用 [deviceIconEntityId] 的实体图片（image/camera），false = 用本地 [deviceIconPath]。 */
    val deviceIconFromEntity: Boolean = false,
    val deviceIconEntityId: String = "",
    val deviceIconPath: String = "",
    /** 底部百分比组件数量：1 或 2。 */
    val deviceMetricCount: Int = 2,
    /** 超级岛图标来源：true = 跟随实体（自动读实体 icon），false = 用 [islandIconPath] 自定义图片。 */
    val islandIconFollow: Boolean = true,
    val islandIconPath: String = "",
    val showIslandIcon: Boolean = true,
    val enabled: Boolean = true,
) {
    val conditionType: HaCondition get() = HaCondition.of(condition)
    /** 面板左侧图标实际使用的图片实体；来源选了「自定义图片」时为空。 */
    val deviceImageEntityId: String get() = if (deviceIconFromEntity) deviceIconEntityId else ""
    val notifId: Int get() = HaNotifIds.of(id)
    val isValid: Boolean get() = triggerEntityId.isNotBlank()
    val dataEntityIds: Set<String>
        get() {
            // 触发实体、充电三项、设备面板的百分比实体由界面直接选择；其余取值全部写在内容模板里，靠扫描模板得到。
            val explicit = listOf(
                triggerEntityId,
                chargingPowerEntityId,
                chargingCurrentEntityId,
                chargingVoltageEntityId,
                deviceLeftEntityId,
                deviceRightEntityId,
                deviceIconEntityId,
            )
            val referenced = HaTemplate.referencedEntityIds(
                islandLeftTemplate,
                islandRightTemplate,
                focusTitleTemplate,
                focusContentTemplate,
                deviceLine1,
                deviceLine2,
                deviceLine3,
            )
            return (explicit + referenced).filter(String::isNotBlank).toSet()
        }
    fun toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("triggerEntityId", triggerEntityId)
        put("triggerAttribute", triggerAttribute)
        put("condition", condition)
        put("value", value)
        put("displaySeconds", displaySeconds)
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
        put("deviceLine1", deviceLine1)
        put("deviceLine2", deviceLine2)
        put("deviceLine3", deviceLine3)
        put("deviceLeftEntityId", deviceLeftEntityId)
        put("deviceRightEntityId", deviceRightEntityId)
        put("deviceIconFromEntity", deviceIconFromEntity)
        put("deviceIconEntityId", deviceIconEntityId)
        put("deviceIconPath", deviceIconPath)
        put("deviceMetricCount", deviceMetricCount)
        put("islandIconFollow", islandIconFollow)
        put("islandIconPath", islandIconPath)
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
                // 老配置没有这个字段 → 0（常驻），与升级前的实际行为一致。
                displaySeconds = json.optInt("displaySeconds", 0).coerceAtLeast(0),
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
                deviceLine1 = json.optString("deviceLine1", "{name}"),
                deviceLine2 = json.optString("deviceLine2", "{state}"),
                deviceLine3 = json.optString("deviceLine3"),
                deviceLeftEntityId = json.optString("deviceLeftEntityId"),
                deviceRightEntityId = json.optString("deviceRightEntityId"),
                deviceIconFromEntity = json.optBoolean("deviceIconFromEntity", false),
                deviceIconEntityId = json.optString("deviceIconEntityId"),
                deviceIconPath = json.optString("deviceIconPath"),
                deviceMetricCount = json.optInt("deviceMetricCount", 2).coerceIn(1, 2),
                islandIconFollow = json.optBoolean("islandIconFollow", true),
                islandIconPath = json.optString("islandIconPath"),
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

    /** 模板里 `{实体.属性}` 形式的实体 id（`{value}` / `{name}` 这类裸占位符不算）。 */
    private val ENTITY_IN_TOKEN = Regex("""^([a-z_]+\.[a-z0-9_]+)(\..*)?$""")

    const val DEFAULT_TITLE = "{name}"
    const val DEFAULT_CONTENT = "{value}"
    val PLACEHOLDERS = listOf("{name}", "{state}", "{value}", "{unit}", "{entity}", "{raw}")

    /**
     * 扫描模板里被引用的实体 id —— 决定要向 Home Assistant 订阅哪些实体。
     *
     * 数据源不再由界面上的实体选择器提供，而是直接写在内容模板里（`{sensor.x.state}`），
     * 所以必须反向把模板引用的实体收集出来，否则这些实体不会被订阅、取值为空。
     */
    fun referencedEntityIds(vararg templates: String): List<String> =
        templates.flatMap { template ->
            TOKEN.findAll(template).mapNotNull { match ->
                ENTITY_IN_TOKEN.matchEntire(match.groupValues[1])?.groupValues?.get(1)
            }
        }.distinct()

    fun referencePlaceholders(entities: List<HaEntity>): List<String> = entities.flatMap { entity ->
        (listOf("state") + entity.attributes.keys).distinct().map { attribute -> "{${entity.entityId}.$attribute}" }
    }.distinct()

    fun title(s: HaSubscription, entity: HaEntity, entities: Map<String, HaEntity> = emptyMap()): String =
        render(s.focusTitleTemplate.ifBlank { DEFAULT_TITLE }, entity, s.focusTitleAttribute, entities)

    fun content(s: HaSubscription, entity: HaEntity, entities: Map<String, HaEntity> = emptyMap()): String =
        render(s.focusContentTemplate.ifBlank { DEFAULT_CONTENT }, entity, s.focusContentAttribute, entities)

    fun islandTexts(s: HaSubscription, entities: Map<String, HaEntity>, index: Int = 0): Pair<String, String> {
        // 使用 `||` 分隔多个轮播项，便于在普通输入框中手动编辑。
        val leftTemplates = s.islandLeftTemplate.split("||").map(String::trim).filter(String::isNotBlank).ifEmpty { listOf("") }
        val rightTemplates = s.islandRightTemplate.split("||").map(String::trim).filter(String::isNotBlank).ifEmpty { listOf("") }
        val left = renderSource(leftTemplates[index % leftTemplates.size], s.islandLeftEntityId, s.islandLeftAttribute, entities, s.triggerEntityId)
        val right = renderSource(rightTemplates[index % rightTemplates.size], s.islandRightEntityId, s.islandRightAttribute, entities, s.triggerEntityId)
        return left to right
    }

    fun focusTexts(s: HaSubscription, entities: Map<String, HaEntity>): Pair<String, String> {
        if (s.templateType == HaTemplateType.CHARGING) {
            val power = sourceValue(entities, s.chargingPowerEntityId, s.chargingPowerAttribute)
            val current = sourceValue(entities, s.chargingCurrentEntityId, s.chargingCurrentAttribute)
            val voltage = sourceValue(entities, s.chargingVoltageEntityId, s.chargingVoltageAttribute)
            return "充电" to listOf(power, current, voltage).filter(String::isNotBlank).joinToString(" · ")
        }
        return renderSource(s.focusTitleTemplate, s.focusTitleEntityId, s.focusTitleAttribute, entities, s.triggerEntityId) to
            renderSource(s.focusContentTemplate, s.focusContentEntityId, s.focusContentAttribute, entities, s.triggerEntityId)
    }

    private fun renderSource(template: String, entityId: String, attribute: String, entities: Map<String, HaEntity>, fallbackEntityId: String = ""): String {
        // 界面已不再提供「数据源实体」，裸占位符（{value}/{state}/{name}）回退到触发实体。
        val entity = entities[entityId] ?: entities[fallbackEntityId] ?: entities.values.firstOrNull() ?: return ""
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
    /**
     * 设备面板的 3 行自定义文本。
     *
     * 模板可以混用裸占位符（`{name}` / `{state}` / `{value}`，取触发实体）和全限定占位符
     * （`{sensor.x.state}`），与超级岛/焦点通知的内容模板是同一套规则。
     */
    fun deviceLines(s: HaSubscription, entities: Map<String, HaEntity>): List<String> =
        listOf(s.deviceLine1, s.deviceLine2, s.deviceLine3).map { template ->
            if (template.isBlank()) "" else renderSource(template, "", "", entities, s.triggerEntityId)
        }

    /** 只有单位是 `%` 的实体才允许作为设备面板的进度源。 */
    fun isPercent(entity: HaEntity): Boolean = entity.unit.trim() == "%"

    /** 百分比实体的状态 → 0..100 的进度值；不是百分比或解析失败时返回 null。 */
    fun percentOf(entity: HaEntity): Float? {
        if (!isPercent(entity)) return null
        val value = entity.state.trim().toFloatOrNull() ?: return null
        return value.coerceIn(0f, 100f)
    }

    fun signature(title: String, content: String): String = "$title\u0000$content"
    fun describe(condition: HaCondition, value: String): String = value
}
