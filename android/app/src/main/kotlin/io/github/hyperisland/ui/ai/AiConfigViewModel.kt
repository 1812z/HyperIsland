package io.github.hyperisland.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AiConfigViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AiConfigRepository(app)

    private val _uiState = MutableStateFlow(repo.load())
    val uiState: StateFlow<AiConfigState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun update(block: (AiConfigState) -> AiConfigState) {
        _uiState.update(block)
    }

    fun setState(state: AiConfigState) {
        _uiState.value = state
    }

    fun save() {
        repo.save(_uiState.value)
        viewModelScope.launch { _events.emit("AI 配置已保存") }
    }

    fun testConnection() {
        val state = _uiState.value
        if (state.url.isBlank()) {
            viewModelScope.launch { _events.emit("请先填写 AI URL") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(testing = true, testResult = null) }
            val reqBody = buildRequestBody(state)
            val result = runCatching {
                val conn = (URL(state.url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    if (state.apiKey.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer ${state.apiKey}")
                    }
                    connectTimeout = state.timeout * 1000
                    readTimeout = state.timeout * 1000
                    doOutput = true
                }
                conn.outputStream.use { it.write(reqBody.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val body = if (code in 200..299) {
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                }
                conn.disconnect()
                code to body
            }

            if (result.isSuccess) {
                val (code, body) = result.getOrThrow()
                val preview = if (code == 200) {
                    parseResponsePreview(body)
                } else {
                    "HTTP $code\n$body"
                }
                repo.saveLastLogJson(
                    JSONObject()
                        .put("timestamp", java.time.Instant.now().toString())
                        .put("source", "compose_test")
                        .put("url", state.url)
                        .put("model", state.model.ifBlank { "gpt-4o-mini" })
                        .put("requestBody", reqBody)
                        .put("responseBody", body)
                        .put("error", if (code == 200) "" else "HTTP $code")
                        .put("statusCode", code)
                        .toString(),
                )
                _uiState.update { it.copy(testing = false, testResult = preview) }
            } else {
                val msg = result.exceptionOrNull()?.toString() ?: "请求失败"
                _uiState.update { it.copy(testing = false, testResult = msg) }
            }
        }
    }

    private fun buildRequestBody(state: AiConfigState): String {
        val userContent = "应用包名：com.example.app\\n标题：测试通知\\n正文：这是一条用于测试 AI 提取效果的示例消息"
        val messages = JSONArray()
        if (state.promptInUser) {
            messages.put(JSONObject().put("role", "user").put("content", state.prompt))
        } else {
            messages.put(JSONObject().put("role", "system").put("content", state.prompt))
        }
        messages.put(JSONObject().put("role", "user").put("content", userContent))

        return JSONObject()
            .put("model", state.model.ifBlank { "gpt-4o-mini" })
            .put("messages", messages)
            .put("max_tokens", state.maxTokens)
            .put("temperature", state.temperature)
            .toString()
    }

    private fun parseResponsePreview(body: String): String {
        return runCatching {
            val root = JSONObject(body)
            root.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        }.getOrElse { body }
    }
}
