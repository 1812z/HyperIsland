package io.github.hyperisland

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import org.json.JSONObject
import java.io.File

/**
 * 双职责：
 *  1. 向其他进程暴露模块设置（ContentProvider，供旧版调用路径兼容）。
 *  2. 在每次设置变化时，将全量配置序列化为 JSON 写入
 *     [CONFIG_FILE_NAME]，并设为世界可读，使 ConfigManager（Hook 端）
 *     无需 App 后台运行即可实时读取配置。
 */
class SettingsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "io.github.hyperisland.settings"
        private const val CONFIG_FILE_NAME = "hyperisland_config.json"
    }

    private val prefs by lazy {
        context!!.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    }

    // 必须持有强引用，否则 SharedPreferences 内部弱引用会被 GC 回收
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
        // 1. 通知旧版 ContentObserver（兼容性保留）
        val resolver = context?.contentResolver ?: return@OnSharedPreferenceChangeListener
        resolver.notifyChange(Uri.parse("content://$AUTHORITY/"), null, false)
        val segment = changedKey?.removePrefix("flutter.")?.takeIf { it.isNotBlank() }
        if (segment != null) {
            resolver.notifyChange(Uri.parse("content://$AUTHORITY/$segment"), null, false)
        }
        // 2. 写 JSON 文件供 ConfigManager（FileObserver）热重载
        writeConfigFile()
    }

    override fun onCreate(): Boolean {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        // App 启动时立即写一次，保证 Hook 端即使在 App 关闭期间也能读到最新配置
        writeConfigFile()
        return true
    }

    // ── JSON 配置文件写入 ──────────────────────────────────────────────────────

    /**
     * 将 FlutterSharedPreferences 的全部键值序列化为 JSON，
     * 写入 filesDir/[CONFIG_FILE_NAME] 并设为世界可读。
     *
     * Hook 端（ConfigManager）通过 FileObserver 监控同一文件，
     * 文件 CLOSE_WRITE 时自动重载配置，无需模块后台运行。
     */
    private fun writeConfigFile() {
        try {
            val ctx = context ?: return
            val json = JSONObject()
            for ((key, value) in prefs.all) {
                when (value) {
                    is Boolean -> json.put(key, value)
                    is Int     -> json.put(key, value)
                    is Long    -> json.put(key, value)
                    is Float   -> json.put(key, value.toDouble())
                    is String  -> json.put(key, value)
                    is Set<*>  -> json.put(key, value.joinToString(","))
                    else       -> if (value != null) json.put(key, value.toString())
                }
            }
            val file = File(ctx.filesDir, CONFIG_FILE_NAME)
            file.writeText(json.toString())
            // 使 Hook 进程（SystemUI 等系统进程）可以直接读取，无需 App 后台
            file.setReadable(true, false)
        } catch (_: Exception) {
            // 写文件失败不应影响 App 正常运行
        }
    }

    // ── ContentProvider 查询（兼容性保留） ────────────────────────────────────

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor {
        val segment = uri.lastPathSegment ?: return MatrixCursor(arrayOf("value"))
        val flutterKey = "flutter.$segment"
        val cursor = MatrixCursor(arrayOf("value"))

        if (prefs.contains(flutterKey)) {
            val value = prefs.all[flutterKey]
            when (value) {
                is Boolean -> cursor.newRow().add(if (value) 1 else 0)
                is Int -> cursor.newRow().add(if (segment == "pref_marquee_speed") value.coerceIn(20, 500) else value)
                is Long -> cursor.newRow().add(
                    if (segment == "pref_marquee_speed") value.toInt().coerceIn(20, 500) else value
                )
                is Float -> cursor.newRow().add(value.toDouble())
                is String -> {
                    val normalized = if (segment == "pref_marquee_speed") {
                        value.toIntOrNull()?.coerceIn(20, 500)?.toString() ?: value
                    } else value
                    cursor.newRow().add(normalized)
                }
                is Set<*> -> cursor.newRow().add(value.joinToString(","))
                else -> cursor.newRow().add(value?.toString() ?: "")
            }
            return cursor
        }

        when {
            isStringPref(segment) -> cursor.newRow().add("")
            isIntPref(segment) -> cursor.newRow().add(defaultIntFor(segment))
            isDoublePref(segment) -> cursor.newRow().add(defaultDoubleFor(segment))
            else -> cursor.newRow().add(if (defaultBooleanFor(segment)) 1 else 0)
        }
        return cursor
    }

    private fun isStringPref(segment: String): Boolean {
        return segment == "pref_generic_whitelist" ||
            segment == "pref_app_blacklist" ||
            segment == "pref_ai_url" ||
            segment == "pref_ai_api_key" ||
            segment == "pref_ai_model" ||
            segment == "pref_ai_prompt" ||
            segment == "pref_ai_last_log_json" ||
            segment.startsWith("pref_channels_") ||
            segment.startsWith("pref_channel_template_") ||
            segment.startsWith("pref_channel_icon_") ||
            segment.startsWith("pref_channel_focus_icon_") ||
            segment.startsWith("pref_channel_focus_") ||
            segment.startsWith("pref_channel_preserve_small_icon_") ||
            segment.startsWith("pref_channel_first_float_") ||
            segment.startsWith("pref_channel_enable_float_") ||
            segment.startsWith("pref_channel_timeout_") ||
            segment.startsWith("pref_channel_marquee_") ||
            segment.startsWith("pref_channel_renderer_")
    }

    private fun isIntPref(segment: String): Boolean {
        return segment == "pref_marquee_speed" ||
            segment == "pref_ai_timeout" ||
            segment == "pref_ai_max_tokens"
    }

    private fun isDoublePref(segment: String): Boolean {
        return segment == "pref_ai_temperature"
    }

    private fun defaultIntFor(segment: String): Int {
        return when (segment) {
            "pref_marquee_speed" -> 100
            "pref_ai_timeout" -> 3
            "pref_ai_max_tokens" -> 50
            else -> 0
        }
    }

    private fun defaultDoubleFor(segment: String): Double {
        return when (segment) {
            "pref_ai_temperature" -> 0.1
            else -> 0.0
        }
    }

    private fun defaultBooleanFor(segment: String): Boolean {
        return when (segment) {
            "pref_show_welcome" -> true
            "pref_resume_notification" -> true
            "pref_use_hook_app_icon" -> true
            "pref_interaction_haptics" -> true
            "pref_round_icon" -> true
            "pref_marquee_feature" -> false
            "pref_unlock_all_focus" -> false
            "pref_unlock_focus_auth" -> false
            "pref_check_update_on_launch" -> true
            "pref_default_first_float" -> false
            "pref_default_enable_float" -> false
            "pref_default_marquee" -> false
            "pref_default_focus_notif" -> true
            "pref_default_preserve_small_icon" -> false
            "pref_ai_enabled" -> false
            "pref_ai_prompt_in_user" -> false
            else -> true
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        val segment = uri.lastPathSegment ?: return 0
        val value = values?.get("value") ?: return 0
        val editor = prefs.edit()
        val flutterKey = "flutter.$segment"
        when (value) {
            is String -> editor.putString(flutterKey, value)
            is Boolean -> editor.putBoolean(flutterKey, value)
            is Int -> editor.putInt(flutterKey, value)
            is Long -> editor.putLong(flutterKey, value)
            is Float -> editor.putFloat(flutterKey, value)
            is Double -> editor.putString(flutterKey, value.toString())
            else -> editor.putString(flutterKey, value.toString())
        }
        return if (editor.commit()) 1 else 0
    }
}
