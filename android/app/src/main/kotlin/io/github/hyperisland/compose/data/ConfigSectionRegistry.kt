package io.github.hyperisland.compose.data

import androidx.annotation.StringRes
import io.github.hyperisland.R

/**
 * 配置预设的「配置管理映射表」。
 *
 * 这是配置分类的唯一权威来源：每个 [ConfigSection] 声明它负责的配置键
 * （精确键 [exactKeys] 或前缀家族 [keyPrefixes]），供新建 / 应用预设时快照与还原。
 *
 * 硬性规则：
 * - 新增配置页面或字段时，必须在此把键登记到对应分类，避免分类错误。
 * - 可展开分类只作为分组，本身不参与选择；选择单元是叶子节点 [leafIds]。
 * - 前缀家族必须足够精确，不要用 `pref_` 这种会吞并全部键的前缀。
 */
internal data class ConfigSection(
    val id: String,
    @StringRes val titleRes: Int,
    val exactKeys: List<String> = emptyList(),
    val keyPrefixes: List<String> = emptyList(),
    val children: List<ConfigSection> = emptyList(),
    /** 动态标题（如应用名）；非空时优先于 [titleRes]。 */
    val title: String? = null,
) {
    val expandable: Boolean get() = children.isNotEmpty()

    /** 本节点及其后代覆盖的全部精确键。 */
    val allExactKeys: List<String>
        get() = exactKeys + children.flatMap { it.allExactKeys }

    /** 本节点及其后代覆盖的全部键前缀。 */
    val allKeyPrefixes: List<String>
        get() = keyPrefixes + children.flatMap { it.allKeyPrefixes }

    /** 本节点可被选择的叶子 id：无子节点时为自身，否则为所有后代叶子。 */
    val leafIds: List<String>
        get() = if (children.isEmpty()) listOf(id) else children.flatMap { it.leafIds }

    /** 判断某个配置键是否属于本节点（含子节点）。 */
    fun matches(key: String): Boolean =
        key in allExactKeys || allKeyPrefixes.any(key::startsWith)

    fun find(id: String): ConfigSection? {
        if (this.id == id) return this
        children.forEach { child -> child.find(id)?.let { return it } }
        return null
    }
}

/** 应用级配置前缀：`pref_app_config_<包名>`。 */
internal const val APP_CONFIG_PREFIX = "pref_app_config_"

/**
 * 应用级配置按 `pref_app_config_<包名>` 内部子对象拆分，实现通知与 Toast 的区分：
 * - [Notification] 对应 `notification` / `channels` 子对象；
 * - [Toast] 对应 `toast` 子对象。
 */
internal enum class AppConfigKind(
    val id: String,
    @StringRes val titleRes: Int,
    val subKeys: List<String>,
) {
    Notification("notification", R.string.preset_section_notification, listOf("notification", "channels")),
    Toast("toast", R.string.preset_section_toast, listOf("toast")),
}

private const val APP_LEAF_PREFIX = "appconfig:"

/** 应用级叶子 id，形如 `appconfig:toast:com.example.app`。 */
internal fun appConfigLeafId(kind: AppConfigKind, packageName: String): String =
    "$APP_LEAF_PREFIX${kind.id}:$packageName"

/** 解析应用级叶子 id；非应用级 id 返回 null。 */
internal fun parseAppConfigLeafId(id: String): Pair<AppConfigKind, String>? {
    if (!id.startsWith(APP_LEAF_PREFIX)) return null
    val rest = id.removePrefix(APP_LEAF_PREFIX)
    val separator = rest.indexOf(':')
    if (separator <= 0 || separator == rest.lastIndex) return null
    val kind = AppConfigKind.entries.firstOrNull { it.id == rest.substring(0, separator) } ?: return null
    return kind to rest.substring(separator + 1)
}

/** 顶层分组，界面上用 SmallTitle（“配置”）统一分割。 */
internal val ConfigSectionGroups: List<ConfigSection> = listOf(
    // 通知 / Toast 为可展开分组：子节点是按应用生成的应用级叶子（见 appConfigSectionTree）。
    ConfigSection(
        id = "notification",
        titleRes = R.string.preset_section_notification,
    ),
    ConfigSection(
        id = "toast",
        titleRes = R.string.preset_section_toast,
    ),
    ConfigSection(
        id = "appearance",
        titleRes = R.string.appearance,
        children = listOf(
            ConfigSection(
                id = "appearance_size",
                titleRes = R.string.appearance_size,
                exactKeys = listOf(
                    "pref_island_height",
                    "pref_island_top_offset",
                    "pref_big_island_max_width",
                    "pref_big_island_min_width",
                    "pref_small_island_width",
                    "pref_small_island_horizontal_offset",
                    "pref_expand_top_gap",
                    "pref_expand_content_top_gap",
                    "pref_island_corner_radius",
                    "pref_expand_corner_radius",
                ),
            ),
            ConfigSection(
                id = "appearance_background",
                titleRes = R.string.appearance_background,
                keyPrefixes = listOf(
                    "pref_island_bg_",
                    "pref_island_material_",
                    "pref_island_blur_",
                    "pref_island_glass_",
                    "pref_island_refraction_",
                ),
            ),
            ConfigSection(
                id = "appearance_text",
                titleRes = R.string.appearance_text,
                exactKeys = listOf(
                    "pref_island_text_scale",
                    "pref_island_text_area_height",
                    "pref_island_text_color_mode",
                    "pref_focus_notification_text_color_mode",
                    "pref_media_notification_text_color_mode",
                ),
            ),
            ConfigSection(
                id = "appearance_icon",
                titleRes = R.string.appearance_icon,
                exactKeys = listOf(
                    "pref_island_icon_size",
                    "pref_round_icon_radius",
                    "pref_round_icon",
                    "pref_island_icon_padding",
                ),
            ),
            ConfigSection(
                id = "appearance_outline",
                titleRes = R.string.appearance_outline,
                exactKeys = listOf(
                    "pref_always_show_island_outline",
                    "pref_always_show_focus_outline",
                    "pref_outer_glow_range",
                    "pref_outer_glow_single_color",
                    "pref_outer_glow_base_color",
                ),
            ),
            ConfigSection(
                id = "appearance_animation",
                titleRes = R.string.appearance_animation,
                keyPrefixes = listOf(
                    "pref_expand_animation_",
                    "pref_expand_collapse_",
                ),
            ),
        ),
    ),
    ConfigSection(
        id = "ai",
        titleRes = R.string.ai_summary,
        // AI 通知：显式排除 pref_ai_api_key，避免泄露密钥。
        exactKeys = listOf(
            "pref_ai_enabled",
            "pref_ai_prompt",
            "pref_ai_prompt_in_user",
            "pref_ai_custom_fields",
            "pref_ai_timeout",
            "pref_ai_temperature",
            "pref_ai_max_tokens",
            "pref_ai_trigger_char_count",
            "pref_ai_url",
            "pref_ai_model",
        ),
    ),
    ConfigSection(
        id = "filter_rules",
        titleRes = R.string.filter_rules,
        exactKeys = listOf(
            "pref_app_blacklist",
            "pref_scene_foreground_packages",
            "pref_scene_excluded_foreground_packages",
        ),
        keyPrefixes = listOf("pref_scene_foreground_"),
    ),
    ConfigSection(
        id = "default_config",
        titleRes = R.string.default_config,
        keyPrefixes = listOf("pref_default_"),
    ),
    ConfigSection(
        id = "hide_behavior",
        titleRes = R.string.hide_behavior,
        keyPrefixes = listOf("pref_temp_hide_"),
    ),
    ConfigSection(
        id = "keep_island",
        titleRes = R.string.always_on_island,
        keyPrefixes = listOf("pref_keep_island_"),
    ),
    ConfigSection(
        id = "other",
        titleRes = R.string.other,
        exactKeys = listOf(
            "pref_scene_dnd",
            "pref_fullscreen_behavior",
            "pref_landscape_behavior",
            "pref_expanded_collapse_action",
            "pref_big_island_collapse_action",
            "pref_island_swipe_ignore_ongoing",
            "pref_marquee_feature",
            "pref_marquee_speed",
        ),
    ),
    ConfigSection(
        id = "extensions",
        titleRes = R.string.hook_extension,
        children = listOf(
            ConfigSection(
                id = "ext_system_ui",
                titleRes = R.string.system_ui,
                exactKeys = listOf(
                    "pref_smooth_island",
                    "pref_smooth_island_smoothing",
                    "pref_unlock_all_focus",
                    "pref_hide_lockscreen_face_unlock_icon",
                    "pref_small_island_icon_adjustment",
                    "pref_small_island_icon_opacity",
                    "pref_wifi_tile_disconnect_only",
                    "pref_lockscreen_device_center",
                ),
                keyPrefixes = listOf(
                    "pref_bluetooth_island",
                    "pref_heart_rate_island",
                    "pref_charge_island",
                    "pref_face_unlock_island",
                    "pref_lockscreen_negative_page_",
                ),
            ),
            ConfigSection(
                id = "ext_settings",
                titleRes = R.string.hook_scope_settings,
                keyPrefixes = listOf("pref_settings_home_entry"),
            ),
            ConfigSection(
                id = "ext_security_center",
                titleRes = R.string.security_center,
                exactKeys = listOf(
                    "pref_clipboard_toast_conversion",
                    "pref_clipboard_optimize_island_style",
                ),
            ),
            ConfigSection(
                id = "ext_xmsf",
                titleRes = R.string.xmsf,
                exactKeys = listOf("pref_unlock_focus_auth"),
            ),
            ConfigSection(
                id = "ext_screen_recorder",
                titleRes = R.string.screen_recorder,
                keyPrefixes = listOf("pref_screen_recorder_"),
            ),
            ConfigSection(
                id = "ext_download_manager",
                titleRes = R.string.download_manager,
                exactKeys = listOf(
                    "pref_resume_notification",
                    "pref_download_show_task_icon",
                ),
            ),
        ),
    ),
)

/** 在整棵注册树中按 id 查找节点（含嵌套）。 */
internal fun findConfigSection(id: String): ConfigSection? =
    ConfigSectionGroups.firstNotNullOfOrNull { it.find(id) }