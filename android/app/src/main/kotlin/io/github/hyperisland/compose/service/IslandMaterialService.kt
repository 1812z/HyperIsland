package io.github.hyperisland.compose.service

import android.content.Context
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.IslandMaterialConfig
import io.github.hyperisland.compose.data.IslandMaterialSettings
import io.github.hyperisland.compose.data.IslandMaterialState
import io.github.hyperisland.compose.data.IslandMaterialType
import io.github.hyperisland.compose.data.MaterialDraftStore
import org.json.JSONObject

internal class IslandMaterialService(
    private val prefs: FlutterPrefsRepository,
    context: Context,
) {
    private val drafts = MaterialDraftStore(context)

    fun load(): IslandMaterialSettings = IslandMaterialSettings(
        big = IslandMaterialConfig.decode(prefs.getString(KEY_BIG)),
        small = IslandMaterialConfig.decode(prefs.getString(KEY_SMALL)),
        expand = IslandMaterialConfig.decode(prefs.getString(KEY_EXPAND)),
        smallFollowBig = prefs.getBoolean(KEY_SMALL_FOLLOW, true),
        expandFollowBig = prefs.getBoolean(KEY_EXPAND_FOLLOW, true),
    )

    fun save(settings: IslandMaterialSettings, state: IslandMaterialState, config: IslandMaterialConfig): IslandMaterialSettings {
        if (config.isCustom) clearBackgroundConflict(settings, state)
        val updated = when (state) {
            IslandMaterialState.Big -> settings.copy(big = config)
            IslandMaterialState.Small -> settings.copy(small = config)
            IslandMaterialState.Expand -> settings.copy(expand = config)
        }
        prefs.putString(configKey(state), config.toJson().toString())
        syncLegacy(state, config)
        if (state == IslandMaterialState.Big) {
            if (updated.smallFollowBig) syncLegacy(IslandMaterialState.Small, config)
            if (updated.expandFollowBig) syncLegacy(IslandMaterialState.Expand, config)
        }
        return updated
    }

    /**
     * 切换某个形态的材质类型，并让每种材质各自记住自己的参数。
     *
     * 真源始终只有一份（`pref_island_material_<state>_config`），语义完全不变，
     * Hook 端与同步链路无需任何改动。切走时把当前参数写进本地草稿槽，
     * 切回时优先读槽，只有从没调过该材质才用 [IslandMaterialConfig.seed]。
     */
    fun switchType(
        settings: IslandMaterialSettings,
        state: IslandMaterialState,
        type: IslandMaterialType,
    ): IslandMaterialSettings {
        val current = settings.config(state)
        if (current.type == type) return settings
        drafts.save(state, current)
        val restored = drafts.load(state, type) ?: IslandMaterialConfig.seed(type)
        return save(settings, state, restored)
    }

    fun setFollow(settings: IslandMaterialSettings, state: IslandMaterialState, follow: Boolean): IslandMaterialSettings {
        require(state != IslandMaterialState.Big)
        val updated = when (state) {
            IslandMaterialState.Small -> settings.copy(smallFollowBig = follow)
            IslandMaterialState.Expand -> settings.copy(expandFollowBig = follow)
            IslandMaterialState.Big -> settings
        }
        prefs.putBoolean(if (state == IslandMaterialState.Small) KEY_SMALL_FOLLOW else KEY_EXPAND_FOLLOW, follow)
        syncLegacy(state, if (follow) updated.big else updated.config(state))
        if (follow && updated.big.isCustom) clearBackgroundConflict(updated, state)
        return updated
    }

    fun reset(): IslandMaterialSettings {
        var settings = load()
        IslandMaterialState.entries.forEach { state -> settings = save(settings, state, IslandMaterialConfig()) }
        settings = setFollow(settings, IslandMaterialState.Small, true)
        settings = setFollow(settings, IslandMaterialState.Expand, true)
        // 恢复默认后旧草稿必须一起作废，否则下次切材质又会把自定义参数翻出来。
        drafts.clear()
        return settings
    }

    fun export(settings: IslandMaterialSettings): String = JSONObject().apply {
        put("type", CLIPBOARD_TYPE)
        put("version", CLIPBOARD_VERSION)
        put("big", settings.big.toJson())
        put("small", settings.small.toJson())
        put("expand", settings.expand.toJson())
        put("smallFollowBig", settings.smallFollowBig)
        put("expandFollowBig", settings.expandFollowBig)
    }.toString(2)

    fun import(raw: String, allowSoftGlass: Boolean): IslandMaterialSettings {
        val root = JSONObject(raw)
        require(root.optString("type") == CLIPBOARD_TYPE && root.optInt("version") == CLIPBOARD_VERSION)
        val big = IslandMaterialConfig.fromJson(root.getJSONObject("big"))
        val small = IslandMaterialConfig.fromJson(root.getJSONObject("small"))
        val expand = IslandMaterialConfig.fromJson(root.getJSONObject("expand"))
        require(allowSoftGlass || listOf(big, small, expand).none { it.type == IslandMaterialType.SoftGlass }) {
            SOFT_GLASS_UNSUPPORTED
        }
        var settings = load()
        settings = save(settings, IslandMaterialState.Big, big)
        settings = save(settings, IslandMaterialState.Small, small)
        settings = save(settings, IslandMaterialState.Expand, expand)
        settings = setFollow(settings, IslandMaterialState.Small, root.getBoolean("smallFollowBig"))
        settings = setFollow(settings, IslandMaterialState.Expand, root.getBoolean("expandFollowBig"))
        // 导入是一整套材质参数，本地记忆槽跟着重来，否则切换材质会覆盖刚导入的值。
        drafts.clear()
        return settings
    }

    fun clearUnsupportedSoftGlass(settings: IslandMaterialSettings): IslandMaterialSettings {
        var updated = settings
        IslandMaterialState.entries.forEach { state ->
            if (updated.config(state).type == IslandMaterialType.SoftGlass) {
                updated = save(updated, state, IslandMaterialConfig())
            }
        }
        // 降级到 HyperOS 3 后柔光已不可选，残留的柔光槽没有意义。
        drafts.clearType(IslandMaterialType.SoftGlass)
        return updated
    }

    fun hasBackgroundConflict(settings: IslandMaterialSettings, state: IslandMaterialState): Boolean = when (state) {
        IslandMaterialState.Big -> prefs.getString(KEY_BG_BIG).isNotBlank() ||
            (settings.smallFollowBig && prefs.getString(KEY_BG_SMALL).isNotBlank()) ||
            (settings.expandFollowBig && prefs.getString(KEY_BG_EXPAND).isNotBlank())
        IslandMaterialState.Small -> prefs.getString(KEY_BG_SMALL).isNotBlank()
        IslandMaterialState.Expand -> prefs.getString(KEY_BG_EXPAND).isNotBlank()
    }

    private fun clearBackgroundConflict(settings: IslandMaterialSettings, state: IslandMaterialState) {
        fun clear(key: String) { if (prefs.getString(key).isNotBlank()) prefs.remove(key) }
        when (state) {
            IslandMaterialState.Big -> {
                clear(KEY_BG_BIG)
                if (settings.smallFollowBig) clear(KEY_BG_SMALL)
                if (settings.expandFollowBig) clear(KEY_BG_EXPAND)
            }
            IslandMaterialState.Small -> clear(KEY_BG_SMALL)
            IslandMaterialState.Expand -> clear(KEY_BG_EXPAND)
        }
    }

    private fun syncLegacy(state: IslandMaterialState, config: IslandMaterialConfig) {
        val moduleBlur = config.type in setOf(
            IslandMaterialType.Gaussian,
            IslandMaterialType.HighlightGlass,
            IslandMaterialType.LiquidGlass,
        )
        prefs.putBoolean(legacyKey("blur", state, "enabled"), moduleBlur)
        prefs.putLong(legacyKey("blur", state, "radius"), config.blur.toLong())
        prefs.putString(legacyKey("blur", state, "color"), config.blendColor)
        prefs.putBoolean(
            legacyKey("glass", state, "enabled"),
            config.type == IslandMaterialType.HighlightGlass || config.type == IslandMaterialType.LiquidGlass,
        )
        prefs.putBoolean(
            "pref_island_refraction_${state.valueName}_enabled",
            config.type == IslandMaterialType.LiquidGlass,
        )
    }

    private fun legacyKey(group: String, state: IslandMaterialState, field: String): String =
        "pref_island_${group}_${state.valueName}_$field"

    private fun configKey(state: IslandMaterialState): String = when (state) {
        IslandMaterialState.Big -> KEY_BIG
        IslandMaterialState.Small -> KEY_SMALL
        IslandMaterialState.Expand -> KEY_EXPAND
    }

    private val IslandMaterialState.valueName: String get() = name.lowercase()

    companion object {
        const val SOFT_GLASS_UNSUPPORTED = "soft_glass_unsupported"
        private const val CLIPBOARD_TYPE = "hyperisland_material_config"
        private const val CLIPBOARD_VERSION = 1
        private const val KEY_BIG = "pref_island_material_big_config"
        private const val KEY_SMALL = "pref_island_material_small_config"
        private const val KEY_EXPAND = "pref_island_material_expand_config"
        private const val KEY_SMALL_FOLLOW = "pref_island_material_small_follow_big"
        private const val KEY_EXPAND_FOLLOW = "pref_island_material_expand_follow_big"
        private const val KEY_BG_SMALL = "pref_island_bg_small_path"
        private const val KEY_BG_BIG = "pref_island_bg_big_path"
        private const val KEY_BG_EXPAND = "pref_island_bg_expand_path"
    }
}
