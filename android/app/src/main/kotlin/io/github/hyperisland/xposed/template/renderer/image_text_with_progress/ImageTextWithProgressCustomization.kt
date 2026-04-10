package io.github.hyperisland.xposed.renderer.image_text_with_progress

import android.graphics.drawable.Icon
import io.github.hyperisland.xposed.renderer.RendererPayload
import io.github.hyperisland.xposed.renderer.RendererCustomizationContributor
import io.github.hyperisland.xposed.template.core.contracts.IslandTemplate
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationApplyEnv
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationFieldSpec
import org.json.JSONObject

data class ImageTextWithProgressPayload(
    val picProfileIcon: Icon? = null,
    val appIconPkg: String? = null,
    val chatTitleColor: String = "#000000",
    val chatTitleColorDark: String = "#FFFFFF",
    val chatContentColor: String = "#666666",
    val chatContentColorDark: String = "#B3B3B3",
    val progressBarColor: String = "#34C759",
    val progressBarColorEnd: String = "#30B0C7",
) : RendererPayload

object ImageTextWithProgressCustomization : RendererCustomizationContributor {
    const val SLOT_FOCUS_PIC_PROFILE = "focus_pic_profile"
    const val SLOT_FOCUS_APP_ICON_PKG = "focus_app_icon_pkg"
    const val SLOT_CHAT_TITLE_COLOR = "chat_title_color"
    const val SLOT_CHAT_TITLE_COLOR_DARK = "chat_title_color_dark"
    const val SLOT_CHAT_CONTENT_COLOR = "chat_content_color"
    const val SLOT_CHAT_CONTENT_COLOR_DARK = "chat_content_color_dark"
    const val SLOT_PROGRESS_BAR_COLOR = "progress_bar_color"
    const val SLOT_PROGRESS_BAR_COLOR_END = "progress_bar_color_end"

    private const val KEY_FOCUS_PIC_PROFILE_MODE = "focus_pic_profile_mode"
    private const val KEY_FOCUS_APP_ICON_PKG = "focus_app_icon_pkg"
    private const val KEY_CHAT_TITLE_COLOR = "chat_title_color"
    private const val KEY_CHAT_TITLE_COLOR_DARK = "chat_title_color_dark"
    private const val KEY_CHAT_CONTENT_COLOR = "chat_content_color"
    private const val KEY_CHAT_CONTENT_COLOR_DARK = "chat_content_color_dark"
    private const val KEY_PROGRESS_BAR_COLOR = "progress_bar_color"
    private const val KEY_PROGRESS_BAR_COLOR_END = "progress_bar_color_end"

    override val fields: List<FocusCustomizationFieldSpec> = listOf(
        FocusCustomizationFieldSpec(
            slot = SLOT_FOCUS_PIC_PROFILE,
            key = KEY_FOCUS_PIC_PROFILE_MODE,
            label = KEY_FOCUS_PIC_PROFILE_MODE,
            type = "select",
            defaultProvider = { "auto" },
            required = true,
            optionsProvider = { iconSourceOptions() },
            fallbackWhenEmpty = true,
            applier = { _, _, vm -> vm },
        ),
        FocusCustomizationFieldSpec(
            slot = SLOT_FOCUS_APP_ICON_PKG,
            key = KEY_FOCUS_APP_ICON_PKG,
            label = KEY_FOCUS_APP_ICON_PKG,
            type = "text",
            applier = { _, _, vm -> vm },
        ),
        colorField(
            slot = SLOT_CHAT_TITLE_COLOR,
            key = KEY_CHAT_TITLE_COLOR,
            defaultValue = "#000000",
        ),
        colorField(
            slot = SLOT_CHAT_TITLE_COLOR_DARK,
            key = KEY_CHAT_TITLE_COLOR_DARK,
            defaultValue = "#FFFFFF",
        ),
        colorField(
            slot = SLOT_CHAT_CONTENT_COLOR,
            key = KEY_CHAT_CONTENT_COLOR,
            defaultValue = "#666666",
        ),
        colorField(
            slot = SLOT_CHAT_CONTENT_COLOR_DARK,
            key = KEY_CHAT_CONTENT_COLOR_DARK,
            defaultValue = "#B3B3B3",
        ),
        colorField(
            slot = SLOT_PROGRESS_BAR_COLOR,
            key = KEY_PROGRESS_BAR_COLOR,
            defaultValue = "#34C759",
        ),
        colorField(
            slot = SLOT_PROGRESS_BAR_COLOR_END,
            key = KEY_PROGRESS_BAR_COLOR_END,
            defaultValue = "#30B0C7",
        ),
    )

    override fun buildPayload(config: JSONObject, template: IslandTemplate?, env: FocusCustomizationApplyEnv): RendererPayload {
        val picMode = readConfig(config, KEY_FOCUS_PIC_PROFILE_MODE, "auto", fallbackWhenEmpty = true)
        val picProfileIcon = env.resolveSourceIcon(picMode, env.data)
        val appPkg = readConfig(config, KEY_FOCUS_APP_ICON_PKG).ifEmpty { env.data.pkg }
        val titleColor = readColor(config, KEY_CHAT_TITLE_COLOR, "#000000", env)
        val titleColorDark = readColor(config, KEY_CHAT_TITLE_COLOR_DARK, "#FFFFFF", env)
        val contentColor = readColor(config, KEY_CHAT_CONTENT_COLOR, "#666666", env)
        val contentColorDark = readColor(config, KEY_CHAT_CONTENT_COLOR_DARK, "#B3B3B3", env)
        val progressBarColor = readColor(config, KEY_PROGRESS_BAR_COLOR, "#34C759", env)
        val progressBarColorEnd = readColor(config, KEY_PROGRESS_BAR_COLOR_END, "#30B0C7", env)

        return ImageTextWithProgressPayload(
            picProfileIcon = picProfileIcon,
            appIconPkg = appPkg,
            chatTitleColor = titleColor,
            chatTitleColorDark = titleColorDark,
            chatContentColor = contentColor,
            chatContentColorDark = contentColorDark,
            progressBarColor = progressBarColor,
            progressBarColorEnd = progressBarColorEnd,
        )
    }

    private fun readConfig(config: JSONObject, key: String, defaultValue: String = "", fallbackWhenEmpty: Boolean = false): String {
        val raw = config.optString(key, defaultValue).trim()
        return if (fallbackWhenEmpty && raw.isEmpty()) defaultValue else raw
    }

    private fun readColor(config: JSONObject, key: String, defaultValue: String, env: FocusCustomizationApplyEnv): String {
        return env.normalizeColor(readConfig(config, key, defaultValue, fallbackWhenEmpty = true)) ?: defaultValue
    }

    private fun colorField(slot: String, key: String, defaultValue: String): FocusCustomizationFieldSpec {
        return FocusCustomizationFieldSpec(
            slot = slot,
            key = key,
            label = key,
            type = "color",
            defaultProvider = { defaultValue },
            applier = { _, _, vm -> vm },
        )
    }

    private fun iconSourceOptions(): List<Map<String, String>> = listOf(
        mapOf("value" to "auto", "label" to "auto"),
        mapOf("value" to "notif_small", "label" to "notif_small"),
        mapOf("value" to "notif_large", "label" to "notif_large"),
        mapOf("value" to "app_icon", "label" to "app_icon"),
    )
}
