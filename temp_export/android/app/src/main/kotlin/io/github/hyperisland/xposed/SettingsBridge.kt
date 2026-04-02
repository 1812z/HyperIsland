package io.github.hyperisland.xposed

import android.content.Context
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import de.robv.android.xposed.XposedBridge

object SettingsBridge {
    private const val SETTINGS_AUTHORITY = "io.github.hyperisland.settings"

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context?) {
        context?.applicationContext?.let { appContext = it }
    }

    fun getString(key: String, default: String = ""): String {
        val value = queryValue(key) ?: return default
        return when (value) {
            is String -> value
            is Number -> value.toString()
            is Boolean -> if (value) "1" else "0"
            else -> default
        }
    }

    fun getBoolean(key: String, default: Boolean): Boolean {
        val value = queryValue(key) ?: return default
        return when (value) {
            is Boolean -> value
            is Int -> value != 0
            is Long -> value != 0L
            is String -> value == "1" || value.equals("true", ignoreCase = true)
            else -> default
        }
    }

    fun getInt(key: String, default: Int): Int {
        val value = queryValue(key) ?: return default
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }
    }

    fun getDouble(key: String, default: Double): Double {
        val value = queryValue(key) ?: return default
        return when (value) {
            is Double -> value
            is Float -> value.toDouble()
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: default
            else -> default
        }
    }

    fun putString(key: String, value: String): Boolean {
        val ctx = appContext ?: return false
        val uri = Uri.parse("content://$SETTINGS_AUTHORITY/$key")
        return try {
            val values = ContentValues().apply { put("value", value) }
            ctx.contentResolver.update(uri, values, null, null) > 0
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[SettingsBridge]: update failed for $key: ${e.message}")
            false
        }
    }

    private fun queryValue(key: String): Any? {
        val ctx = appContext ?: return null
        val uri = Uri.parse("content://$SETTINGS_AUTHORITY/$key")
        return try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                readCursorValue(cursor)
            }
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[SettingsBridge]: query failed for $key: ${e.message}")
            null
        }
    }

    private fun readCursorValue(cursor: Cursor): Any? {
        val valueIndex = cursor.getColumnIndex("value").takeIf { it >= 0 } ?: 0
        return when (cursor.getType(valueIndex)) {
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(valueIndex)
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(valueIndex)
            Cursor.FIELD_TYPE_STRING -> cursor.getString(valueIndex)
            Cursor.FIELD_TYPE_NULL -> null
            else -> cursor.getString(valueIndex)
        }
    }
}
