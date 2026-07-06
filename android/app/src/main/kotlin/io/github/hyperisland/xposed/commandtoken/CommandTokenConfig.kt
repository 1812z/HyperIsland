package io.github.hyperisland.xposed.commandtoken

import io.github.hyperisland.xposed.ConfigManager

object CommandTokenConfig {

    @Volatile private var enabled: Boolean? = null
    @Volatile private var douyinEnabled: Boolean? = null
    @Volatile private var timeoutSeconds: Int? = null
    @Volatile private var clearClipAfterClick: Boolean? = null
    @Volatile private var dedupWindowSeconds: Int? = null

    init {
        ConfigManager.addChangeListener { invalidateCache() }
    }

    fun isEnabled(): Boolean {
        enabled?.let { return it }
        val v = ConfigManager.getBoolean("pref_command_token_enabled", false)
        enabled = v
        return v
    }

    fun isDouyinEnabled(): Boolean {
        douyinEnabled?.let { return it }
        val v = ConfigManager.getBoolean("pref_command_token_douyin_enabled", true)
        douyinEnabled = v
        return v
    }

    fun timeoutSeconds(): Int {
        timeoutSeconds?.let { return it }
        val v = ConfigManager.getInt("pref_command_token_timeout_seconds", 8).coerceIn(1, 30)
        timeoutSeconds = v
        return v
    }

    fun clearClipAfterClick(): Boolean {
        clearClipAfterClick?.let { return it }
        val v = ConfigManager.getBoolean("pref_command_token_clear_clip_after_click", false)
        clearClipAfterClick = v
        return v
    }

    fun dedupWindowSeconds(): Int {
        dedupWindowSeconds?.let { return it }
        val v = ConfigManager.getInt("pref_command_token_dedup_window_seconds", 30).coerceIn(5, 300)
        dedupWindowSeconds = v
        return v
    }

    fun isRecognizerEnabled(recognizerId: String): Boolean = when (recognizerId) {
        "douyin" -> isDouyinEnabled()
        else -> true
    }

    fun invalidateCache() {
        enabled = null
        douyinEnabled = null
        timeoutSeconds = null
        clearClipAfterClick = null
        dedupWindowSeconds = null
    }
}
