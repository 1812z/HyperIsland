package io.github.hyperisland.ui.ai

import android.content.Context
import io.github.hyperisland.data.prefs.PrefKeys

class AiConfigRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PrefKeys.PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AiConfigState {
        return AiConfigState(
            enabled = prefs.getBoolean(PrefKeys.AI_ENABLED, false),
            url = prefs.getString(PrefKeys.AI_URL, "") ?: "",
            apiKey = prefs.getString(PrefKeys.AI_API_KEY, "") ?: "",
            model = prefs.getString(PrefKeys.AI_MODEL, "") ?: "",
            prompt = prefs.getString(PrefKeys.AI_PROMPT, "")
                ?.takeIf { it.isNotBlank() }
                ?: "根据通知信息，提取关键信息，左右分别不超过6汉字12字符",
            promptInUser = prefs.getBoolean(PrefKeys.AI_PROMPT_IN_USER, false),
            timeout = prefs.getInt(PrefKeys.AI_TIMEOUT, 3).coerceIn(3, 15),
            temperature = prefs.getFloat(PrefKeys.AI_TEMPERATURE, 0.1f).toDouble().coerceIn(0.0, 1.0),
            maxTokens = prefs.getInt(PrefKeys.AI_MAX_TOKENS, 50).coerceIn(10, 500),
        )
    }

    fun save(state: AiConfigState) {
        prefs.edit()
            .putBoolean(PrefKeys.AI_ENABLED, state.enabled)
            .putString(PrefKeys.AI_URL, state.url)
            .putString(PrefKeys.AI_API_KEY, state.apiKey)
            .putString(PrefKeys.AI_MODEL, state.model)
            .putString(PrefKeys.AI_PROMPT, state.prompt)
            .putBoolean(PrefKeys.AI_PROMPT_IN_USER, state.promptInUser)
            .putInt(PrefKeys.AI_TIMEOUT, state.timeout.coerceIn(3, 15))
            .putFloat(PrefKeys.AI_TEMPERATURE, state.temperature.toFloat().coerceIn(0f, 1f))
            .putInt(PrefKeys.AI_MAX_TOKENS, state.maxTokens.coerceIn(10, 500))
            .apply()
    }

    fun saveLastLogJson(value: String) {
        prefs.edit().putString(PrefKeys.AI_LAST_LOG_JSON, value).apply()
    }
}
