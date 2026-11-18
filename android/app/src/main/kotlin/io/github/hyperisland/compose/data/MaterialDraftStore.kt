package io.github.hyperisland.compose.data

import android.content.Context

/**
 * 各「岛形态 + 材质类型」的调参记忆槽。
 *
 * 刻意使用独立 SharedPreferences，**不写入** `FlutterSharedPreferences`：
 * 那张表会被同步到 LSPosed RemotePreferences、整包进配置备份，并且
 * `pref_island_material_` 属于预设「外观-背景」的前缀家族，会被快照抓进云端 payload。
 * 这里存的是设置页草稿，只服务于「切材质时恢复上次参数」，不该参与任何配置流转。
 *
 * 键形如 `big_soft_glass`，值仍是 [IslandMaterialConfig] 的 JSON（含 `softSchema`）。
 */
internal class MaterialDraftStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(state: IslandMaterialState, type: IslandMaterialType): IslandMaterialConfig? {
        if (!type.isCustom) return null
        val raw = prefs.getString(key(state, type), null)
        if (raw.isNullOrBlank()) return null
        // 槽里记录的类型与键不一致（手改文件 / 将来改键名）时直接丢弃，避免串味。
        return IslandMaterialConfig.decode(raw).takeIf { it.type == type }
    }

    fun save(state: IslandMaterialState, config: IslandMaterialConfig) {
        if (!config.isCustom) return
        prefs.edit().putString(key(state, config.type), config.toJson().toString()).apply()
    }

    /** 某类型在全部形态下的槽一起清掉（如 HyperOS 3 不支持柔光时）。 */
    fun clearType(type: IslandMaterialType) {
        if (!type.isCustom) return
        val editor = prefs.edit()
        IslandMaterialState.entries.forEach { editor.remove(key(it, type)) }
        editor.apply()
    }

    /** 恢复默认 / 剪贴板导入后作废，避免下次切材质又把旧参数翻出来。 */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(state: IslandMaterialState, type: IslandMaterialType): String =
        "${state.name.lowercase()}_${type.value}"

    private companion object {
        const val PREFS_NAME = "HyperIslandMaterialDrafts"
    }
}
