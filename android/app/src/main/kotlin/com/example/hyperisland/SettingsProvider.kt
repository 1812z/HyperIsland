package com.example.hyperisland

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * 向其他进程（Xposed Hook）暴露模块设置。
 * Hook 进程通过 ContentResolver.query() 读取，无需跨进程文件访问。
 */
class SettingsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.example.hyperisland.settings"
    }

    private val prefs by lazy {
        context!!.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    }

    override fun onCreate() = true

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor {
        // URI 格式: content://com.example.hyperisland.settings/<key>
        val segment = uri.lastPathSegment ?: return MatrixCursor(arrayOf("value"))
        val flutterKey = "flutter.$segment"
        val cursor = MatrixCursor(arrayOf("value"))

        // 字符串类型的 key（白名单、渠道列表、渠道模板等），直接返回字符串值
        if (segment == "pref_generic_whitelist" ||
            segment.startsWith("pref_channels_") ||
            segment.startsWith("pref_channel_template_") ||
            segment.startsWith("pref_channel_icon_") ||
            segment.startsWith("pref_channel_focus_") ||
            segment.startsWith("pref_channel_first_float_") ||
            segment.startsWith("pref_channel_enable_float_")) {
            cursor.newRow().add(prefs.getString(flutterKey, "") ?: "")
            return cursor
        }

        // 布尔类型的 key，返回 1/0
        val value = if (prefs.contains(flutterKey)) {
            try { if (prefs.getBoolean(flutterKey, true)) 1 else 0 }
            catch (_: ClassCastException) { 1 }
        } else {
            1 // 默认开启
        }
        cursor.newRow().add(value)
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
}
