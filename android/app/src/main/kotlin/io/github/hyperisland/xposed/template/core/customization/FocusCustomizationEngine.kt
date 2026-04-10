package io.github.hyperisland.xposed.template.core.customization

import android.content.Context
import io.github.hyperisland.xposed.renderer.EmptyRendererPayload
import io.github.hyperisland.xposed.renderer.RendererPayload
import io.github.hyperisland.xposed.renderer.resolveRenderer
import io.github.hyperisland.xposed.template.core.TemplateRegistry
import io.github.hyperisland.xposed.template.core.contracts.TemplatePlaceholder
import io.github.hyperisland.xposed.template.core.models.IslandViewModel
import io.github.hyperisland.xposed.template.core.models.NotifData
import io.github.hyperisland.xposed.utils.toRounded
import org.json.JSONObject

object FocusCustomizationEngine {

    data class ApplyResult(
        val vm: IslandViewModel,
        val rendererPayload: RendererPayload = EmptyRendererPayload,
    )

    fun buildSchema(templateId: String, rendererId: String): Map<String, Any?> {
        val template = TemplateRegistry.getTemplate(templateId)
        val focusFields = resolveFocusFields(rendererId)
        val slots = focusFields.map { it.slot }.distinct()
        val fields = focusFields.map { it.toSchemaField(template) }

        return mapOf(
            "templateId" to templateId,
            "rendererId" to rendererId,
            "slots" to slots.toList(),
            "placeholders" to placeholders(
                template?.expressionPlaceholders,
                template?.focusExpressionPlaceholders,
            ),
            "functions" to ExpressionResolver.functionDocs(),
            "fields" to fields,
            "configKey" to "focus_custom",
        )
    }

    fun buildIslandSchema(templateId: String): Map<String, Any?> {
        val template = TemplateRegistry.getTemplate(templateId)
        val fields = IslandCustomizationFieldRegistry.schemaFields(template)
        return mapOf(
            "templateId" to templateId,
            "slots" to IslandCustomizationFieldRegistry.slots(),
            "placeholders" to placeholders(
                template?.expressionPlaceholders,
                template?.islandExpressionPlaceholders,
            ),
            "functions" to ExpressionResolver.functionDocs(),
            "fields" to fields,
            "configKey" to "island_custom",
        )
    }

    fun apply(context: Context, data: NotifData, vm: IslandViewModel): ApplyResult {
        val raw = data.focusCustomizationJson?.trim().orEmpty()
        if (raw.isEmpty()) return ApplyResult(vm = vm)

        val template = TemplateRegistry.getTemplate(vm.templateId)
        val renderer = resolveRenderer(data.renderer)
        val focusFields = renderer.focusCustomizationFields
        val contributor = renderer.customizationContributor
        if (focusFields.isEmpty() && contributor == null) return ApplyResult(vm = vm)

        val config = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return ApplyResult(vm = vm)
        }

        val vars = buildFocusVars(data, vm, template)
        val env = FocusCustomizationApplyEnv(
            data = data,
            vars = vars,
            resolveExpr = ExpressionResolver::resolve,
            normalizeColor = ::normalizeColor,
            roundIcon = { it?.toRounded(context) },
            resolveSourceIcon = { mode, src -> resolveSourceIcon(mode, src)?.toRounded(context) },
        )
        var out = vm
        focusFields.forEach { spec ->
            out = spec.apply(config, template, env, out)
        }

        val rendererPayload = contributor?.buildPayload(config, template, env) ?: EmptyRendererPayload
        return ApplyResult(vm = out, rendererPayload = rendererPayload)
    }

    fun applyIsland(data: NotifData, vm: IslandViewModel): IslandViewModel {
        val text = resolveIslandText(
            data = data,
            templateId = vm.templateId,
            defaultLeft = vm.leftTitle,
            defaultRight = vm.rightTitle,
            stateLabel = vm.leftTitle,
            vm = vm,
        )
        return vm.copy(leftTitle = text.first, rightTitle = text.second)
    }

    fun resolveIslandText(
        data: NotifData,
        templateId: String,
        defaultLeft: String,
        defaultRight: String,
        stateLabel: String = defaultLeft,
        vm: IslandViewModel? = null,
        extraVars: Map<String, String> = emptyMap(),
    ): Pair<String, String> {
        val raw = data.islandCustomizationJson?.trim().orEmpty()
        if (raw.isEmpty()) return defaultLeft to defaultRight

        val template = TemplateRegistry.getTemplate(templateId)
        val config = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return defaultLeft to defaultRight
        }

        val vars = buildIslandVars(data, vm, defaultLeft, defaultRight, stateLabel, template)
            .toMutableMap()
        extraVars.forEach { (k, v) ->
            if (k.isNotBlank()) vars[k] = v
        }
        return IslandCustomizationFieldRegistry.resolveText(
            config = config,
            template = template,
            vars = vars,
            defaultLeft = defaultLeft,
            defaultRight = defaultRight,
        )
    }

    fun mergeWithDefaults(templateId: String, rendererId: String, rawConfig: String?): String {
        val schema = buildSchema(templateId, rendererId)
        val fields = (schema["fields"] as? List<*>)
            ?.mapNotNull { it as? Map<*, *> }
            .orEmpty()
        val current = try {
            if (rawConfig.isNullOrBlank()) JSONObject() else JSONObject(rawConfig)
        } catch (_: Exception) {
            JSONObject()
        }
        val merged = JSONObject()
        fields.forEach { f ->
            val key = f["key"] as? String ?: return@forEach
            val def = f["defaultValue"] as? String ?: ""
            merged.put(key, current.optString(key, def))
        }
        return merged.toString()
    }

    fun mergeIslandWithDefaults(templateId: String, rawConfig: String?): String {
        val schema = buildIslandSchema(templateId)
        val fields = (schema["fields"] as? List<*>)
            ?.mapNotNull { it as? Map<*, *> }
            .orEmpty()
        val current = try {
            if (rawConfig.isNullOrBlank()) JSONObject() else JSONObject(rawConfig)
        } catch (_: Exception) {
            JSONObject()
        }
        val merged = JSONObject()
        fields.forEach { f ->
            val key = f["key"] as? String ?: return@forEach
            val def = f["defaultValue"] as? String ?: ""
            merged.put(key, current.optString(key, def))
        }
        return merged.toString()
    }

    private fun placeholders(
        base: List<TemplatePlaceholder>?,
        extra: List<TemplatePlaceholder>? = null,
    ): List<Map<String, String>> {
        val merged = mutableListOf<TemplatePlaceholder>()
        (base ?: emptyList()).forEach { p ->
            if (merged.none { it.key == p.key }) merged += p
        }
        (extra ?: emptyList()).forEach { p ->
            if (merged.none { it.key == p.key }) merged += p
        }
        return merged.map { mapOf("key" to it.key, "label" to it.label) }
    }

    private fun buildFocusVars(
        data: NotifData,
        vm: IslandViewModel,
        template: io.github.hyperisland.xposed.template.core.contracts.IslandTemplate?,
    ): Map<String, String> {
        val rawSubtitleOrTitle = if (data.subtitle.isNotEmpty()) data.subtitle else data.title
        val subtitleOrTitle = if (vm.focusContent.isNotEmpty()) vm.focusContent else vm.focusTitle
        val base = linkedMapOf(
            "title" to vm.focusTitle,
            "subtitle" to vm.focusContent,
            "subtitle_or_title" to subtitleOrTitle,
            "raw_title" to data.title,
            "raw_subtitle" to data.subtitle,
            "raw_subtitle_or_title" to rawSubtitleOrTitle,
            "pkg" to data.pkg,
            "channel_id" to data.channelId,
            "progress" to data.progress.coerceIn(0, 100).toString(),
            "left_title" to vm.leftTitle,
            "right_title" to vm.rightTitle,
            "focus_title" to vm.focusTitle,
            "focus_content" to vm.focusContent,
            "state_label" to vm.leftTitle,
        )
        template?.focusExpressionVars(data, vm)?.forEach { (k, v) ->
            if (k.isNotBlank()) base[k] = v
        }
        return base
    }

    private fun buildIslandVars(
        data: NotifData,
        vm: IslandViewModel?,
        leftTitle: String,
        rightTitle: String,
        stateLabel: String,
        template: io.github.hyperisland.xposed.template.core.contracts.IslandTemplate?,
    ): Map<String, String> {
        val rawSubtitleOrTitle = if (data.subtitle.isNotEmpty()) data.subtitle else data.title
        val subtitleOrTitle = if (rightTitle.isNotEmpty()) rightTitle else leftTitle
        val base = linkedMapOf(
            "title" to leftTitle,
            "subtitle" to rightTitle,
            "subtitle_or_title" to subtitleOrTitle,
            "raw_title" to data.title,
            "raw_subtitle" to data.subtitle,
            "raw_subtitle_or_title" to rawSubtitleOrTitle,
            "pkg" to data.pkg,
            "channel_id" to data.channelId,
            "progress" to data.progress.coerceIn(0, 100).toString(),
            "left_title" to leftTitle,
            "right_title" to rightTitle,
            "state_label" to stateLabel,
        )
        if (vm != null) {
            template?.islandExpressionVars(data, vm)?.forEach { (k, v) ->
                if (k.isNotBlank()) base[k] = v
            }
        }
        return base
    }

    private fun readString(config: JSONObject, key: String): String =
        config.optString(key, "").trim()

    private fun resolveFocusFields(rendererId: String): List<FocusCustomizationFieldSpec> {
        val renderer = resolveRenderer(rendererId)
        return (renderer.focusCustomizationFields + (renderer.customizationContributor?.fields ?: emptyList()))
            .distinctBy { it.key }
    }

    private fun normalizeColor(raw: String): String? {
        if (raw.isEmpty()) return null
        val value = raw.uppercase()
        val normalized = if (value.startsWith("#")) value else "#$value"
        return if (Regex("^#([0-9A-F]{6}|[0-9A-F]{8})$").matches(normalized)) normalized else null
    }

    private fun resolveSourceIcon(mode: String, data: NotifData) = when (mode) {
        "notif_small" -> data.notifIcon ?: data.largeIcon ?: data.appIconRaw
        "notif_large" -> data.largeIcon ?: data.notifIcon ?: data.appIconRaw
        "app_icon" -> data.appIconRaw ?: data.largeIcon ?: data.notifIcon
        else -> data.largeIcon ?: data.notifIcon ?: data.appIconRaw
    }

}
