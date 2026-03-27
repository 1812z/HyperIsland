package io.github.hyperisland

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * 向其他进程（Xposed Hook）暴露模块设置。
 * Hook 进程通过 ContentResolver.query() 读取，无需跨进程文件访问。
 */
class SettingsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "io.github.hyperisland.settings"
    }

    private val prefs by lazy {
        context!!.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    }

    // 必须持有强引用，否则 SharedPreferences 内部会弱引用导致 GC 回收
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
        val resolver = context?.contentResolver ?: return@OnSharedPreferenceChangeListener
        resolver.notifyChange(Uri.parse("content://$AUTHORITY/"), null, false)
        val segment = changedKey?.removePrefix("flutter.")?.takeIf { it.isNotBlank() } ?: return@OnSharedPreferenceChangeListener
        resolver.notifyChange(Uri.parse("content://$AUTHORITY/$segment"), null, false)
    }

    override fun onCreate(): Boolean {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        return true
    }

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor {
        // URI 格式: content://io.github.hyperisland.settings/<key>
        val segment = uri.lastPathSegment ?: return MatrixCursor(arrayOf("value"))
        val flutterKey = "flutter.$segment"
        val cursor = MatrixCursor(arrayOf("value"))

        // 字符串类型的 key（白名单、黑名单、渠道列表、渠道模板等），直接返回字符串值
        if (segment == "pref_generic_whitelist" ||
            segment == "pref_app_blacklist" ||
            segment == "pref_ai_url" ||
            segment == "pref_ai_api_key" ||
            segment == "pref_ai_model" ||
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
            segment.startsWith("pref_channel_renderer_")) {
            cursor.newRow().add(prefs.getString(flutterKey, "") ?: "")
            return cursor
        }

        // 整数类型的 key，直接返回原始整数值（供 Hook 进程 getInt(0) 读取）
        if (segment == "pref_marquee_speed") {
            val speed = try {
                prefs.getInt(flutterKey, 100)
            } catch (_: ClassCastException) {
                try { prefs.getLong(flutterKey, 100L).toInt() } catch (_: Exception) { 100 }
            }
            cursor.newRow().add(speed.coerceIn(20, 500))
            return cursor
        }

        // 布尔类型的 key，返回 1/0
        val value = if (prefs.contains(flutterKey)) {
            try { if (prefs.getBoolean(flutterKey, true)) 1 else 0 }
            catch (_: ClassCastException) { 1 }
        } else {
            // 以下 key 默认关闭（0）；其余 key 默认开启（1）
            if (segment == "pref_marquee_feature" ||
                segment == "pref_wrap_long_text" ||
                segment == "pref_unlock_all_focus" ||
                segment == "pref_unlock_focus_auth" ||
                segment == "pref_default_first_float" ||
                segment == "pref_default_enable_float" ||
                segment == "pref_default_marquee" ||
                segment == "pref_default_preserve_small_icon" ||
                segment == "pref_ai_enabled") 0 else 1
        }
        cursor.newRow().add(value)
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
}
