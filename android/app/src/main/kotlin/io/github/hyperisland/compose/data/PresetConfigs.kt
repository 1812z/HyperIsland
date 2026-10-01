package io.github.hyperisland.compose.data

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import io.github.hyperisland.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * 一条配置预设。
 *
 * @param id 唯一标识。
 * @param title 标题，长度上限 [PRESET_TITLE_MAX]。
 * @param content 内容描述，长度上限 [PRESET_CONTENT_MAX]。
 * @param author 作者，长度上限 [PRESET_AUTHOR_MAX]。
 * @param downloads 下载量。
 * @param local 是否为本地预设。本地预设可删除、可上传云端；云端预设只能应用。
 * @param createdAt 创建时间，UTC epoch 毫秒（`System.currentTimeMillis()`），不写入本地格式化时间，
 *   保证跨时区 / 跨国家排序一致。
 * @param sections 分节快照：叶子分类 id -> 该分类覆盖的键值 JSON。
 */
internal data class ConfigPreset(
    val id: String,
    val title: String,
    val content: String,
    val author: String,
    val downloads: Long,
    val local: Boolean,
    val createdAt: Long = 0L,
    val sections: Map<String, JSONObject>,
) {
    fun toJson(): JSONObject {
        val sectionsJson = JSONObject()
        sections.forEach { (id, value) -> sectionsJson.put(id, value) }
        return JSONObject()
            .put("id", id)
            .put("title", title)
            .put("content", content)
            .put("author", author)
            .put("downloads", downloads)
            .put("local", local)
            .put("createdAt", createdAt)
            .put("sections", sectionsJson)
    }

    companion object {
        fun fromJson(json: JSONObject): ConfigPreset {
            val sectionsJson = json.optJSONObject("sections") ?: JSONObject()
            val sections = LinkedHashMap<String, JSONObject>()
            sectionsJson.keys().forEach { id ->
                sectionsJson.optJSONObject(id)?.let { sections[id] = it }
            }
            return ConfigPreset(
                id = json.optString("id"),
                title = json.optString("title"),
                content = json.optString("content"),
                author = json.optString("author"),
                downloads = json.optLong("downloads", 0L),
                local = json.optBoolean("local", true),
                createdAt = json.optLong("createdAt", 0L),
                sections = sections,
            )
        }
    }
}

/** 预设排序方式。 */
internal enum class PresetSortOrder(@StringRes val labelRes: Int) {
    Name(R.string.preset_sort_name),
    Downloads(R.string.preset_sort_downloads),
    Date(R.string.preset_sort_date),
}

internal fun sortPresets(presets: List<ConfigPreset>, order: PresetSortOrder): List<ConfigPreset> =
    when (order) {
        PresetSortOrder.Name -> presets.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        PresetSortOrder.Downloads -> presets.sortedByDescending { it.downloads }
        PresetSortOrder.Date -> presets.sortedByDescending { it.createdAt }
    }

internal const val PRESET_TITLE_MAX = 10
internal const val PRESET_CONTENT_MAX = 100
internal const val PRESET_AUTHOR_MAX = 20

/**
 * 本地预设存储与快照 / 还原。
 *
 * 预设数据单独存放在 `HyperIslandPresets`，不写入 `FlutterSharedPreferences`，
 * 避免被同步到 Hook 进程，也避免混入配置备份。快照 / 还原直接读写
 * `FlutterSharedPreferences`，与 [ConfigBackupService] 保持一致的类型处理。
 */
internal object PresetStore {
    private const val PRESETS_PREFS = "HyperIslandPresets"
    private const val KEY_LOCAL = "local_presets"
    private const val KEY_HUB_CACHE = "hub_cache"
    private const val HUB_CACHE_MAX = 100
    private const val FLUTTER_PREFS = "FlutterSharedPreferences"
    private const val FLUTTER_PREFIX = "flutter."
    private const val DOUBLE_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"

    fun loadLocal(context: Context): List<ConfigPreset> {
        val raw = presetsPrefs(context).getString(KEY_LOCAL, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                ConfigPreset.fromJson(array.optJSONObject(index) ?: JSONObject())
            }.filter { it.id.isNotEmpty() }
        }.getOrDefault(emptyList())
    }

    fun saveLocal(context: Context, preset: ConfigPreset) {
        val updated = loadLocal(context).toMutableList()
        updated.removeAll { it.id == preset.id }
        updated.add(0, preset.copy(local = true))
        persist(context, updated)
    }

    fun deleteLocal(context: Context, id: String) {
        persist(context, loadLocal(context).filterNot { it.id == id })
    }

    /** 按选中的叶子分类抓取当前配置值。 */
    fun snapshot(context: Context, leafIds: Set<String>): Map<String, JSONObject> {
        val prefs = flutterPrefs(context)
        val entries = logicalEntries(prefs)
        val roots = appConfigRoots(prefs)
        val result = LinkedHashMap<String, JSONObject>()
        leafIds.forEach { id ->
            // 应用级叶子：只抓取该应用对应子对象（通知 / Toast），实现两者区分。
            parseAppConfigLeafId(id)?.let { (kind, packageName) ->
                val root = roots[packageName] ?: return@forEach
                val partial = JSONObject()
                kind.subKeys.forEach { sub ->
                    root.optJSONObject(sub)?.let { partial.put(sub, it) }
                }
                if (partial.length() > 0) {
                    result[id] = JSONObject().put(APP_CONFIG_PREFIX + packageName, partial)
                }
                return@forEach
            }
            val section = findConfigSection(id) ?: return@forEach
            val values = JSONObject()
            entries.forEach { (key, value) ->
                if (section.matches(key)) values.put(key, exportValue(value))
            }
            result[id] = values
        }
        return result
    }

    /** 把预设中选中的分节写回配置。 */
    fun apply(context: Context, preset: ConfigPreset, selectedSectionIds: Set<String>) {
        if (selectedSectionIds.isEmpty()) return
        val prefs = flutterPrefs(context)
        val editor = prefs.edit()
        preset.sections.forEach { (id, values) ->
            if (id !in selectedSectionIds) return@forEach
            // 应用级叶子：把该应用的对应子对象合并回 `pref_app_config_<包名>`，保留另一类配置。
            parseAppConfigLeafId(id)?.let { (kind, packageName) ->
                val prefKey = APP_CONFIG_PREFIX + packageName
                val partial = values.optJSONObject(prefKey) ?: return@forEach
                val existing = runCatching { JSONObject(prefs.getString(storageKey(prefKey), null) ?: "{}") }
                    .getOrNull() ?: JSONObject()
                kind.subKeys.forEach { existing.remove(it) }
                kind.subKeys.forEach { sub -> partial.optJSONObject(sub)?.let { existing.put(sub, it) } }
                if (existing.length() == 0) editor.remove(storageKey(prefKey))
                else editor.putString(storageKey(prefKey), existing.toString())
                return@forEach
            }
            values.keys().forEach { key ->
                when (val value = values.opt(key)) {
                    is Boolean -> editor.putBoolean(storageKey(key), value)
                    is Int, is Long -> editor.putLong(storageKey(key), (value as Number).toLong())
                    is Double, is Float -> editor.putString(storageKey(key), DOUBLE_PREFIX + (value as Number).toDouble())
                    is String -> editor.putString(storageKey(key), value)
                }
            }
        }
        editor.apply()
    }

    /**
     * 构建带应用级子节点的配置分类树：通知 / Toast 展开为「按应用」的叶子（仅显示应用名）。
     */
    fun appConfigSectionTree(context: Context): List<ConfigSection> {
        val roots = appConfigRoots(flutterPrefs(context))
        val notificationApps = ArrayList<ConfigSection>()
        val toastApps = ArrayList<ConfigSection>()
        roots.forEach { (packageName, root) ->
            val label = appLabel(context, packageName)
            if (root.has("notification") || root.has("channels")) {
                notificationApps += appLeafSection(AppConfigKind.Notification, packageName, label)
            }
            if (root.has("toast")) {
                toastApps += appLeafSection(AppConfigKind.Toast, packageName, label)
            }
        }
        notificationApps.sortBy { it.title?.lowercase() }
        toastApps.sortBy { it.title?.lowercase() }
        return ConfigSectionGroups.map { section ->
            when (section.id) {
                "notification" -> section.copy(children = notificationApps)
                "toast" -> section.copy(children = toastApps)
                else -> section
            }
        }
    }

    /** 读取应用显示名，失败时回退包名。 */
    fun appLabel(context: Context, packageName: String): String = runCatching {
        val packageManager = context.packageManager
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private fun appLeafSection(kind: AppConfigKind, packageName: String, label: String): ConfigSection =
        ConfigSection(
            id = appConfigLeafId(kind, packageName),
            titleRes = kind.titleRes,
            title = label,
        )

    private fun appConfigRoots(prefs: SharedPreferences): Map<String, JSONObject> {
        val result = LinkedHashMap<String, JSONObject>()
        logicalEntries(prefs).forEach { (key, value) ->
            if (!key.startsWith(APP_CONFIG_PREFIX) || value !is String) return@forEach
            runCatching { JSONObject(value) }.getOrNull()?.let {
                result[key.removePrefix(APP_CONFIG_PREFIX)] = it
            }
        }
        return result
    }

    /** 统计每个叶子分类当前匹配的配置键数量，用于界面展示「xx 条配置」。 */
    fun sectionKeyCounts(context: Context): Map<String, Int> {
        val keys = logicalEntries(flutterPrefs(context)).keys
        val result = LinkedHashMap<String, Int>()
        ConfigSectionGroups.forEach { top ->
            top.leafIds.forEach { leafId ->
                val section = findConfigSection(leafId) ?: return@forEach
                result[leafId] = keys.count(section::matches)
            }
        }
        return result
    }

    /**
     * 读取已缓存的云端预设正文。命中即刷新访问时间（LRU），避免每次点进去都重新下载。
     */
    fun cachedHubPreset(context: Context, id: String): ConfigPreset? {
        val entries = loadHubCache(context).toMutableList()
        val index = entries.indexOfFirst { it.preset.id == id }
        if (index < 0) return null
        val touched = entries[index].copy(cachedAt = System.currentTimeMillis())
        entries[index] = touched
        persistHubCache(context, entries)
        return touched.preset
    }

    /** 缓存云端预设正文，最多保留 [HUB_CACHE_MAX] 份，超出按访问时间淘汰最旧的。 */
    fun cacheHubPreset(context: Context, preset: ConfigPreset) {
        val entries = loadHubCache(context).toMutableList()
        entries.removeAll { it.preset.id == preset.id }
        entries.add(0, HubCacheEntry(System.currentTimeMillis(), preset))
        while (entries.size > HUB_CACHE_MAX) entries.removeAt(entries.size - 1)
        persistHubCache(context, entries)
    }

    private fun loadHubCache(context: Context): List<HubCacheEntry> {
        val raw = presetsPrefs(context).getString(KEY_HUB_CACHE, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val entry = array.optJSONObject(index) ?: continue
                    val presetJson = entry.optJSONObject("preset") ?: continue
                    add(HubCacheEntry(entry.optLong("cachedAt", 0L), ConfigPreset.fromJson(presetJson)))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistHubCache(context: Context, entries: List<HubCacheEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("cachedAt", entry.cachedAt)
                    .put("preset", entry.preset.toJson()),
            )
        }
        presetsPrefs(context).edit().putString(KEY_HUB_CACHE, array.toString()).apply()
    }

    private fun persist(context: Context, presets: List<ConfigPreset>) {
        val array = JSONArray()
        presets.forEach { array.put(it.toJson()) }
        presetsPrefs(context).edit().putString(KEY_LOCAL, array.toString()).apply()
    }

    private fun presetsPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PRESETS_PREFS, Context.MODE_PRIVATE)

    private fun flutterPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FLUTTER_PREFS, Context.MODE_PRIVATE)

    private fun logicalEntries(prefs: SharedPreferences): Map<String, Any?> = prefs.all
        .filterKeys { it.startsWith(FLUTTER_PREFIX) }
        .mapKeys { it.key.removePrefix(FLUTTER_PREFIX) }

    private fun exportValue(value: Any?): Any = if (value is String && value.startsWith(DOUBLE_PREFIX)) {
        value.removePrefix(DOUBLE_PREFIX).toDoubleOrNull() ?: value
    } else {
        value ?: JSONObject.NULL
    }

    private fun storageKey(key: String): String =
        if (key.startsWith(FLUTTER_PREFIX)) key else FLUTTER_PREFIX + key

    private data class HubCacheEntry(val cachedAt: Long, val preset: ConfigPreset)
}