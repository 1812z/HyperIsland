package io.github.hyperisland.xposed.template.core.customization

import android.graphics.drawable.Icon
import io.github.hyperisland.xposed.template.core.contracts.IslandTemplate
import io.github.hyperisland.xposed.template.core.models.IslandViewModel
import io.github.hyperisland.xposed.template.core.models.NotifData
import org.json.JSONObject

data class FocusCustomizationApplyEnv(
    val data: NotifData,
    val vars: Map<String, String>,
    val resolveExpr: (String, Map<String, String>) -> String,
    val normalizeColor: (String) -> String?,
    val roundIcon: (Icon?) -> Icon?,
    val resolveSourceIcon: (String, NotifData) -> Icon?,
)

object FocusCustomizationFieldKeys {
    const val focusTitleExpr = "focus_title_expr"
    const val focusContentExpr = "focus_content_expr"
    const val focusIconMode = "focus_icon_mode"
    const val progressColor = "progress_color"
}

class FocusCustomizationFieldSpec(
    val slot: String,
    val key: String,
    val label: String,
    val type: String,
    private val defaultProvider: (IslandTemplate?) -> String = { "" },
    private val required: Boolean = false,
    private val optionsProvider: (IslandTemplate?) -> List<Map<String, String>> = { emptyList() },
    private val min: Int? = null,
    private val max: Int? = null,
    private val fallbackWhenEmpty: Boolean = false,
    private val applier: (String, FocusCustomizationApplyEnv, IslandViewModel) -> IslandViewModel,
) {
    fun toSchemaField(template: IslandTemplate?): Map<String, Any?> {
        val options = optionsProvider(template)
        return linkedMapOf<String, Any?>(
            "key" to key,
            "label" to label,
            "type" to type,
            "defaultValue" to defaultProvider(template),
            "required" to required,
            "options" to options.takeIf { it.isNotEmpty() },
            "min" to min,
            "max" to max,
        ).filterValues { it != null }
    }

    fun apply(config: JSONObject, template: IslandTemplate?, env: FocusCustomizationApplyEnv, vm: IslandViewModel): IslandViewModel {
        val def = defaultProvider(template)
        val raw = config.optString(key, def).trim()
        val value = if (fallbackWhenEmpty && raw.isEmpty()) def else raw
        return applier(value, env, vm)
    }
}

object FocusCustomizationFieldRegistry {
    const val SLOT_FOCUS_TITLE = "focus_title"
    const val SLOT_FOCUS_CONTENT = "focus_content"
    const val SLOT_FOCUS_ICON = "focus_icon"
    const val SLOT_PROGRESS_COLOR = "progress_color"

    val focusTitleExpr = FocusCustomizationFieldSpec(
        slot = SLOT_FOCUS_TITLE,
        key = FocusCustomizationFieldKeys.focusTitleExpr,
        label = FocusCustomizationFieldKeys.focusTitleExpr,
        type = "text_expr",
        defaultProvider = { it?.defaultFocusTitleExpr ?: "${'$'}{focus_title}" },
        required = true,
        applier = { value, env, vm ->
            if (value.isBlank()) vm.copy(focusTitle = "")
            else {
                val title = env.resolveExpr(value, env.vars).ifEmpty { vm.focusTitle }
                vm.copy(focusTitle = title)
            }
        },
    )

    val focusContentExpr = FocusCustomizationFieldSpec(
        slot = SLOT_FOCUS_CONTENT,
        key = FocusCustomizationFieldKeys.focusContentExpr,
        label = FocusCustomizationFieldKeys.focusContentExpr,
        type = "text_expr",
        defaultProvider = { it?.defaultFocusContentExpr ?: "${'$'}{focus_content}" },
        required = true,
        applier = { value, env, vm ->
            if (value.isBlank()) vm.copy(focusContent = "")
            else {
                val content = env.resolveExpr(value, env.vars).ifEmpty { vm.focusContent }
                vm.copy(focusContent = content)
            }
        },
    )

    val focusIconMode = FocusCustomizationFieldSpec(
        slot = SLOT_FOCUS_ICON,
        key = FocusCustomizationFieldKeys.focusIconMode,
        label = FocusCustomizationFieldKeys.focusIconMode,
        type = "select",
        defaultProvider = { "auto" },
        required = true,
        optionsProvider = { iconSourceOptions() },
        fallbackWhenEmpty = true,
        applier = { value, env, vm ->
            val icon = when (value) {
                "notif_small" -> env.data.notifIcon
                "notif_large" -> env.data.largeIcon ?: env.data.notifIcon
                "app_icon" -> env.data.appIconRaw
                else -> null
            }
            val rounded = env.roundIcon(icon)
            if (rounded != null) vm.copy(focusIcon = rounded) else vm
        },
    )

    val progressColor = colorField(
        slot = SLOT_PROGRESS_COLOR,
        key = FocusCustomizationFieldKeys.progressColor,
        label = FocusCustomizationFieldKeys.progressColor,
        defaultValue = "",
        applier = { color, vm -> vm.copy(progressColor = color) },
    )

    private fun colorField(
        slot: String,
        key: String,
        label: String,
        defaultValue: String,
        applier: (String?, IslandViewModel) -> IslandViewModel,
    ) = FocusCustomizationFieldSpec(
        slot = slot,
        key = key,
        label = label,
        type = "color",
        defaultProvider = { defaultValue },
        applier = { value, env, vm ->
            applier(env.normalizeColor(value), vm)
        },
    )

    private fun iconSourceOptions(): List<Map<String, String>> = listOf(
        mapOf("value" to "auto", "label" to "auto"),
        mapOf("value" to "notif_small", "label" to "notif_small"),
        mapOf("value" to "notif_large", "label" to "notif_large"),
        mapOf("value" to "app_icon", "label" to "app_icon"),
    )
}
