package io.github.hyperisland.xposed.template.core.customization

import android.content.Context
import io.github.hyperisland.xposed.template.core.models.IslandViewModel
import io.github.hyperisland.xposed.template.core.models.NotifData
import io.github.hyperisland.xposed.utils.toRounded
import org.json.JSONObject

object FocusCustomizationEngine {

    const val SLOT_FOCUS_TITLE = "focus_title"
    const val SLOT_FOCUS_CONTENT = "focus_content"
    const val SLOT_FOCUS_ICON = "focus_icon"
    const val SLOT_PROGRESS = "progress"
    const val SLOT_ISLAND_LEFT = "island_left"
    const val SLOT_ISLAND_RIGHT = "island_right"

    private const val KEY_TITLE_EXPR = "focus_title_expr"
    private const val KEY_CONTENT_EXPR = "focus_content_expr"
    private const val KEY_ICON_MODE = "focus_icon_mode"
    private const val KEY_PROGRESS_VALUE = "progress_value"
    private const val KEY_ISLAND_LEFT_EXPR = "island_left_expr"
    private const val KEY_ISLAND_RIGHT_EXPR = "island_right_expr"

    private const val MAX_EXPR_LEN = 320

    fun buildSchema(templateId: String, rendererId: String): Map<String, Any?> {
        val slots = rendererSlots(rendererId)
        val fields = mutableListOf<Map<String, Any?>>()

        if (slots.contains(SLOT_FOCUS_TITLE)) {
            fields += mapOf(
                "key" to KEY_TITLE_EXPR,
                "label" to "焦点标题表达式",
                "type" to "text_expr",
                "defaultValue" to "${'$'}{title}",
                "required" to true,
            )
        }

        if (slots.contains(SLOT_FOCUS_CONTENT)) {
            fields += mapOf(
                "key" to KEY_CONTENT_EXPR,
                "label" to "焦点正文表达式",
                "type" to "text_expr",
                "defaultValue" to "${'$'}{subtitle_or_title}",
                "required" to true,
            )
        }

        if (slots.contains(SLOT_FOCUS_ICON)) {
            fields += mapOf(
                "key" to KEY_ICON_MODE,
                "label" to "焦点图标来源",
                "type" to "select",
                "defaultValue" to "auto",
                "required" to true,
                "options" to listOf(
                    mapOf("value" to "auto", "label" to "自动"),
                    mapOf("value" to "notif_small", "label" to "通知小图标"),
                    mapOf("value" to "notif_large", "label" to "通知大图标"),
                    mapOf("value" to "app_icon", "label" to "应用图标"),
                ),
            )
        }

        if (slots.contains(SLOT_PROGRESS) && templateSupportsProgress(templateId)) {
            fields += mapOf(
                "key" to KEY_PROGRESS_VALUE,
                "label" to "进度覆盖",
                "type" to "number",
                "defaultValue" to "",
                "required" to false,
                "min" to 0,
                "max" to 100,
            )
        }

        return mapOf(
            "templateId" to templateId,
            "rendererId" to rendererId,
            "slots" to slots.toList(),
            "placeholders" to placeholders(templateId),
            "functions" to expressionFunctions(),
            "fields" to fields,
            "configKey" to "focus_custom",
        )
    }

    fun buildIslandSchema(templateId: String): Map<String, Any?> {
        val fields = listOf(
            mapOf(
                "key" to KEY_ISLAND_LEFT_EXPR,
                "label" to "超级岛左侧表达式",
                "type" to "text_expr",
                "defaultValue" to "${'$'}{title}",
                "required" to true,
            ),
            mapOf(
                "key" to KEY_ISLAND_RIGHT_EXPR,
                "label" to "超级岛右侧表达式",
                "type" to "text_expr",
                "defaultValue" to "${'$'}{subtitle_or_title}",
                "required" to true,
            ),
        )
        return mapOf(
            "templateId" to templateId,
            "slots" to listOf(SLOT_ISLAND_LEFT, SLOT_ISLAND_RIGHT),
            "placeholders" to placeholders(templateId),
            "functions" to expressionFunctions(),
            "fields" to fields,
            "configKey" to "island_custom",
        )
    }

    fun apply(context: Context, data: NotifData, vm: IslandViewModel): IslandViewModel {
        val raw = data.focusCustomizationJson?.trim().orEmpty()
        if (raw.isEmpty()) return vm

        val slots = rendererSlots(data.renderer)
        if (slots.isEmpty()) return vm

        val config = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return vm
        }

        val vars = buildVars(data, vm)
        var out = vm

        if (slots.contains(SLOT_FOCUS_TITLE)) {
            val titleExpr = readString(config, KEY_TITLE_EXPR).ifEmpty { "${'$'}{title}" }
            val title = resolveExpr(titleExpr, vars).ifEmpty { vm.focusTitle }
            out = out.copy(focusTitle = title)
        }

        if (slots.contains(SLOT_FOCUS_CONTENT)) {
            val contentExpr = readString(config, KEY_CONTENT_EXPR).ifEmpty { "${'$'}{subtitle_or_title}" }
            val content = resolveExpr(contentExpr, vars).ifEmpty { vm.focusContent }
            out = out.copy(focusContent = content)
        }

        if (slots.contains(SLOT_FOCUS_ICON)) {
            val iconMode = readString(config, KEY_ICON_MODE).ifEmpty { "auto" }
            val icon = when (iconMode) {
                "notif_small" -> data.notifIcon
                "notif_large" -> data.largeIcon ?: data.notifIcon
                "app_icon" -> data.appIconRaw
                else -> null
            }?.toRounded(context)
            if (icon != null) out = out.copy(focusIcon = icon)
        }

        if (slots.contains(SLOT_PROGRESS) && templateSupportsProgress(vm.templateId)) {
            val progressRaw = readString(config, KEY_PROGRESS_VALUE)
            if (progressRaw.isNotEmpty()) {
                val progress = progressRaw.toIntOrNull()?.coerceIn(0, 100)
                if (progress != null) out = out.copy(circularProgress = progress)
            }
        }

        return out
    }

    fun applyIsland(data: NotifData, vm: IslandViewModel): IslandViewModel {
        val text = resolveIslandText(data, vm.leftTitle, vm.rightTitle, vm.leftTitle)
        return vm.copy(leftTitle = text.first, rightTitle = text.second)
    }

    fun resolveIslandText(
        data: NotifData,
        defaultLeft: String,
        defaultRight: String,
        stateLabel: String = defaultLeft,
    ): Pair<String, String> {
        val raw = data.islandCustomizationJson?.trim().orEmpty()
        if (raw.isEmpty()) return defaultLeft to defaultRight

        val config = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return defaultLeft to defaultRight
        }

        val vars = buildIslandVars(data, defaultLeft, defaultRight, stateLabel)
        val leftExpr = readString(config, KEY_ISLAND_LEFT_EXPR).ifEmpty { "${'$'}{title}" }
        val rightExpr = readString(config, KEY_ISLAND_RIGHT_EXPR).ifEmpty { "${'$'}{subtitle_or_title}" }

        val left = resolveExpr(leftExpr, vars).ifEmpty { defaultLeft }
        val right = resolveExpr(rightExpr, vars).ifEmpty { defaultRight }
        return left to right
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

    private fun rendererSlots(rendererId: String): Set<String> = when (rendererId) {
        "image_text_with_right_text_button" -> setOf(
            SLOT_FOCUS_TITLE,
            SLOT_FOCUS_CONTENT,
            SLOT_FOCUS_ICON,
            SLOT_PROGRESS,
        )
        "image_text_with_buttons_4_wrap" -> setOf(
            SLOT_FOCUS_TITLE,
            SLOT_FOCUS_CONTENT,
            SLOT_FOCUS_ICON,
            SLOT_PROGRESS,
        )
        else -> setOf(
            SLOT_FOCUS_TITLE,
            SLOT_FOCUS_CONTENT,
            SLOT_FOCUS_ICON,
            SLOT_PROGRESS,
        )
    }

    private fun placeholders(templateId: String): List<Map<String, String>> {
        val base = mutableListOf(
            mapOf("key" to "title", "label" to "通知标题"),
            mapOf("key" to "subtitle", "label" to "通知正文"),
            mapOf("key" to "subtitle_or_title", "label" to "正文(空则标题)"),
            mapOf("key" to "pkg", "label" to "包名"),
            mapOf("key" to "channel_id", "label" to "渠道ID"),
        )
        if (templateSupportsProgress(templateId)) {
            base += mapOf("key" to "progress", "label" to "通知进度")
            base += mapOf("key" to "state_label", "label" to "下载状态文本")
        }
        return base
    }

    private fun templateSupportsProgress(templateId: String): Boolean =
        templateId == "generic_progress" || templateId == "download_lite"

    private fun buildVars(data: NotifData, vm: IslandViewModel): Map<String, String> {
        return buildIslandVars(data, vm.leftTitle, vm.rightTitle, vm.leftTitle)
    }

    private fun buildIslandVars(
        data: NotifData,
        leftTitle: String,
        rightTitle: String,
        stateLabel: String,
    ): Map<String, String> {
        val subtitleOrTitle = if (data.subtitle.isNotEmpty()) data.subtitle else data.title
        return linkedMapOf(
            "title" to data.title,
            "subtitle" to data.subtitle,
            "subtitle_or_title" to subtitleOrTitle,
            "pkg" to data.pkg,
            "channel_id" to data.channelId,
            "progress" to data.progress.coerceIn(0, 100).toString(),
            "left_title" to leftTitle,
            "right_title" to rightTitle,
            "state_label" to stateLabel,
        )
    }

    private fun readString(config: JSONObject, key: String): String =
        config.optString(key, "").trim()

    private fun resolveExpr(expr: String, vars: Map<String, String>): String {
        val safeExpr = expr.take(MAX_EXPR_LEN)
        val tokenRegex = Regex("\\$\\{([^}]*)}")
        return tokenRegex.replace(safeExpr) { m ->
            evaluateToken(m.groupValues[1], vars)
        }
    }

    private fun evaluateToken(rawToken: String, vars: Map<String, String>): String {
        val token = rawToken.trim()
        if (token.isEmpty()) return ""
        if (!token.contains('(') || !token.endsWith(')')) {
            return vars[token] ?: ""
        }
        val fnName = token.substringBefore('(').trim()
        val argsBody = token.substringAfter('(', "").removeSuffix(")")
        val args = splitArgs(argsBody)
        return when (fnName) {
            "replace" -> {
                if (args.size < 3) return ""
                val source = evalArg(args[0], vars)
                val pattern = evalArg(args[1], vars)
                val replacement = evalArg(args[2], vars)
                try {
                    Regex(pattern).replace(source, replacement)
                } catch (_: Exception) {
                    source
                }
            }
            "regex" -> {
                if (args.size < 2) return ""
                val source = evalArg(args[0], vars)
                val pattern = evalArg(args[1], vars)
                val group = args.getOrNull(2)?.let { evalArg(it, vars).toIntOrNull() } ?: 0
                try {
                    val mr = Regex(pattern).find(source) ?: return ""
                    if (group in 0 until mr.groupValues.size) mr.groupValues[group] else ""
                } catch (_: Exception) {
                    ""
                }
            }
            "trim" -> {
                if (args.isEmpty()) return ""
                evalArg(args[0], vars).trim()
            }
            else -> vars[token] ?: ""
        }
    }

    private fun evalArg(arg: String, vars: Map<String, String>): String {
        val trimmed = arg.trim()
        if (trimmed.isEmpty()) return ""
        if ((trimmed.startsWith('"') && trimmed.endsWith('"')) ||
            (trimmed.startsWith('\'') && trimmed.endsWith('\''))
        ) {
            return trimmed.substring(1, trimmed.length - 1)
        }
        return if (trimmed.contains("${'$'}{")) resolveExpr(trimmed, vars) else (vars[trimmed] ?: trimmed)
    }

    private fun splitArgs(raw: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuote = false
        var quoteChar = '"'
        var depth = 0
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (inQuote) {
                sb.append(ch)
                if (ch == quoteChar && (i == 0 || raw[i - 1] != '\\')) {
                    inQuote = false
                }
                i++
                continue
            }
            when (ch) {
                '\'', '"' -> {
                    inQuote = true
                    quoteChar = ch
                    sb.append(ch)
                }
                '(' -> {
                    depth++
                    sb.append(ch)
                }
                ')' -> {
                    depth = maxOf(0, depth - 1)
                    sb.append(ch)
                }
                ',' -> {
                    if (depth == 0) {
                        result += sb.toString().trim()
                        sb.setLength(0)
                    } else {
                        sb.append(ch)
                    }
                }
                else -> sb.append(ch)
            }
            i++
        }
        val tail = sb.toString().trim()
        if (tail.isNotEmpty()) result += tail
        return result
    }

    private fun expressionFunctions(): List<Map<String, String>> = listOf(
        mapOf("name" to "replace", "example" to "replace(title, \"regex\", \"new\")"),
        mapOf("name" to "regex", "example" to "regex(subtitle, \"(id\\\\d+)\", 1)"),
        mapOf("name" to "trim", "example" to "trim(subtitle_or_title)"),
    )

}
