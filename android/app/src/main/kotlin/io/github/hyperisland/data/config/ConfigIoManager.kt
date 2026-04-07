package io.github.hyperisland.data.config

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import io.github.hyperisland.data.prefs.PrefKeys
import org.json.JSONObject
import java.io.File

class ConfigIoManager(private val context: Context) {
    private val prefs = context.getSharedPreferences(PrefKeys.PREFS_NAME, Context.MODE_PRIVATE)

    fun exportToJson(): String {
        val settings = JSONObject()
        prefs.all
            .filterKeys { it.startsWith("pref_") }
            .forEach { (key, value) ->
                when (value) {
                    is Boolean, is Int, is Long, is Float, is String -> settings.put(key, value)
                }
            }
        val root = JSONObject()
            .put("version", 1)
            .put("settings", settings)
        return root.toString(2)
    }

    fun importFromJson(json: String): Int {
        val root = JSONObject(json)
        val settings = root.optJSONObject("settings") ?: throw IllegalArgumentException("invalid format")
        val editor = prefs.edit()
        var count = 0
        settings.keys().forEach { key ->
            val value = settings.get(key)
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Double -> editor.putFloat(key, value.toFloat())
                is String -> editor.putString(key, value)
            }
            count += 1
        }
        editor.apply()
        return count
    }

    fun exportToFile(): String {
        val dir = context.getExternalFilesDir(null) ?: throw IllegalStateException("no external dir")
        val file = File(dir, "hyperisland_config.json")
        file.writeText(exportToJson())
        return file.absolutePath
    }

    fun importFromFile(): Int {
        val dir = context.getExternalFilesDir(null) ?: throw IllegalStateException("no external dir")
        val file = File(dir, "hyperisland_config.json")
        if (!file.exists()) throw IllegalStateException("config file not found")
        return importFromJson(file.readText())
    }

    fun importFromUri(uri: Uri): Int {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } ?: throw IllegalStateException("无法读取所选文件")
        return importFromJson(text)
    }

    fun exportToClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("hyperisland_config", exportToJson()))
    }

    fun importFromClipboard(): Int {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (text.isBlank()) throw IllegalStateException("clipboard empty")
        return importFromJson(text)
    }
}
