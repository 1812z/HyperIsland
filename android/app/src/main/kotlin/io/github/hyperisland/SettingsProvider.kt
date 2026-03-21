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
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        context?.contentResolver?.notifyChange(
            Uri.parse("content://$AUTHORITY/"), null, false
        )
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
            segment.startsWith("pref_channels_") ||
            segment.startsWith("pref_channel_template_") ||
            segment.startsWith("pref_channel_icon_") ||
            segment.startsWith("pref_channel_focus_") ||
            segment.startsWith("pref_channel_first_float_") ||
            segment.startsWith("pref_channel_enable_float_") ||
            segment.startsWith("pref_channel_timeout_")) {
            cursor.newRow().add(prefs.getString(flutterKey, "") ?: "")
            return cursor
        }

        // 布尔类型的 key，返回 1/0
        val value = if (prefs.contains(flutterKey)) {
            try { if (prefs.getBoolean(flutterKey, true)) 1 else 0 }
            catch (_: ClassCastException) { 1 }
        } else {
            if (segment == "pref_marquee_feature") 0 else 1 // 默认开启
        }
        cursor.newRow().add(value)
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
}
