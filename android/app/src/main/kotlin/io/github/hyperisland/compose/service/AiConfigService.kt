package io.github.hyperisland.compose.service

import io.github.hyperisland.compose.data.AiConfigSettings
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

internal object AiConfigService {
    private val learnedTokenParams = ConcurrentHashMap<String, String>()

    fun fetchModels(
        chatUrl: String,
        apiKey: String,
        unexpectedFormatMessage: String,
    ): List<String> {
        val response = request(
            method = "GET",
            url = deriveModelsUrl(chatUrl),
            apiKey = apiKey,
            timeoutSeconds = 15,
        )
        if (response.code != HttpURLConnection.HTTP_OK) throw IllegalStateException("HTTP ${response.code}")
        val data = JSONObject(response.body).optJSONArray("data")
            ?: throw IllegalStateException(unexpectedFormatMessage)
        return buildList {
            for (index in 0 until data.length()) {
                data.optJSONObject(index)?.optString("id")?.takeIf(String::isNotEmpty)?.let(::add)
            }
        }.distinct().sorted()
    }

    fun testConnection(settings: AiConfigSettings, sampleUserContent: String): String {
        require(settings.url.isNotBlank())
        val response = postWithTokenFallback(settings) { tokenParam ->
            buildPayload(
                settings = settings,
                promptText = "",
                userContent = sampleUserContent,
                tokenParam = tokenParam,
            )
        }
        if (response.code != HttpURLConnection.HTTP_OK) {
            throw IllegalStateException("HTTP ${response.code}\n${response.body}")
        }
        val message = JSONObject(response.body)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
        return message?.optString("content").orEmpty().trim()
    }

    fun requestNotificationText(
        settings: AiConfigSettings,
        defaultPrompt: String,
        userContent: String,
        jsonOnlyInstruction: String,
        leftDescription: String,
        rightDescription: String,
        thinkingError: String,
        invalidJsonError: String,
        emptyJsonError: String,
    ): Pair<String, String> {
        require(settings.url.isNotBlank())
        val prompt = settings.prompt.trim().ifEmpty { defaultPrompt }
        val jsonExample = JSONObject()
            .put("left", leftDescription)
            .put("right", rightDescription)
            .toString()
        val messages = if (settings.promptInUser) {
            JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("content", listOf(prompt, "", jsonOnlyInstruction, jsonExample, "", userContent).joinToString("\n")),
            )
        } else {
            JSONArray()
                .put(
                    JSONObject()
                        .put("role", "system")
                        .put("content", listOf(prompt, jsonOnlyInstruction, jsonExample).joinToString("\n")),
                )
                .put(JSONObject().put("role", "user").put("content", userContent))
        }
        val response = postWithTokenFallback(settings) { tokenParam ->
            buildPayload(settings, messages, tokenParam)
        }
        if (response.code != HttpURLConnection.HTTP_OK) {
            throw IllegalStateException("HTTP ${response.code}\n${response.body}")
        }
        val responseJson = JSONObject(response.body)
        if (isThinkingResponse(responseJson)) throw IllegalStateException(thinkingError)
        val aiText = responseJson.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
            .trim()
        val cleanText = aiText
            .replaceFirst(Regex("^```json\\s*"), "")
            .replaceFirst(Regex("^```\\s*"), "")
            .replaceFirst(Regex("\\s*```$"), "")
            .trim()
        val result = runCatching { JSONObject(cleanText) }
            .getOrElse { throw IllegalStateException(invalidJsonError) }
        val left = result.optString("left").trim()
        val right = result.optString("right").trim()
        if (left.isEmpty() || right.isEmpty()) throw IllegalStateException(emptyJsonError)
        return left to right
    }

    private fun buildPayload(
        settings: AiConfigSettings,
        promptText: String,
        userContent: String,
        tokenParam: String,
    ): JSONObject {
        val messages = JSONArray()
        if (promptText.isNotEmpty()) {
            messages.put(
                JSONObject()
                    .put("role", if (settings.promptInUser) "user" else "system")
                    .put("content", promptText),
            )
        }
        messages.put(JSONObject().put("role", "user").put("content", userContent))
        return buildPayload(settings, messages, tokenParam)
    }

    private fun buildPayload(
        settings: AiConfigSettings,
        messages: JSONArray,
        tokenParam: String,
    ): JSONObject = JSONObject()
        .put("model", effectiveModel(settings.model))
        .put("messages", messages)
        .put(tokenParam, settings.maxTokens)
        .put("temperature", settings.temperature)
        .also { payload ->
            customFields(settings.customFields).let { fields ->
                fields.keys().forEach { key -> payload.put(key, fields.get(key)) }
            }
        }

    private fun postWithTokenFallback(
        settings: AiConfigSettings,
        payload: (String) -> JSONObject,
    ): HttpResponse {
        val normalizedModel = effectiveModel(settings.model).trim().lowercase().substringAfterLast('/')
        val cacheKey = "${settings.url.trim()}\u0000$normalizedModel"
        val firstParam = learnedTokenParams[cacheKey] ?: tokenParamName(normalizedModel)
        var response = post(settings, payload(firstParam))
        if (isUnsupportedTokenParam(response, firstParam)) {
            val alternate = if (firstParam == "max_tokens") "max_completion_tokens" else "max_tokens"
            response = post(settings, payload(alternate))
            if (response.code == HttpURLConnection.HTTP_OK) learnedTokenParams[cacheKey] = alternate
        }
        return response
    }

    private fun post(settings: AiConfigSettings, payload: JSONObject): HttpResponse = request(
        method = "POST",
        url = settings.url.trim(),
        apiKey = settings.apiKey.trim(),
        timeoutSeconds = settings.timeout,
        body = payload.toString(),
    )

    private fun request(
        method: String,
        url: String,
        apiKey: String,
        timeoutSeconds: Int,
        body: String? = null,
    ): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = timeoutSeconds * 1_000
            connection.readTimeout = timeoutSeconds * 1_000
            connection.setRequestProperty("Accept", "application/json")
            if (apiKey.isNotEmpty()) connection.setRequestProperty("Authorization", "Bearer $apiKey")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            HttpResponse(code, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private fun customFields(raw: String): JSONObject = runCatching { JSONObject(raw) }
        .getOrDefault(JSONObject())
        .apply {
            remove("max_tokens")
            remove("max_completion_tokens")
        }

    private fun isUnsupportedTokenParam(response: HttpResponse, sentParam: String): Boolean {
        if (response.code != HttpURLConnection.HTTP_BAD_REQUEST) return false
        val error = runCatching { JSONObject(response.body).optJSONObject("error") }.getOrNull()
        val param = error?.optString("param").orEmpty().lowercase()
        val code = error?.optString("code").orEmpty().lowercase()
        val message = (error?.optString("message") ?: response.body).lowercase()
        val unsupported = listOf(
            "unsupported",
            "not supported",
            "unknown parameter",
            "unrecognized",
            "not allowed",
            "use max_",
        ).any(message::contains) || code.contains("unsupported") || code.contains("unknown")
        return unsupported && (param == sentParam || message.contains(sentParam))
    }

    private fun isThinkingResponse(root: JSONObject): Boolean {
        val choice = root.optJSONArray("choices")?.optJSONObject(0)
        val message = choice?.optJSONObject("message")
        val logprobs = choice?.optJSONObject("logprobs")
        val usage = root.optJSONObject("usage")
        val completion = usage?.optJSONObject("completion_tokens_details")
        val output = usage?.optJSONObject("output_tokens_details")
        return listOf(
            message?.opt("reasoning_content"),
            message?.opt("reasoning"),
            message?.opt("reasoning_details"),
            message?.opt("thinking"),
            choice?.opt("reasoning_content"),
            choice?.opt("reasoning"),
            logprobs?.opt("reasoning_content"),
            completion?.opt("reasoning_tokens"),
            output?.opt("reasoning_tokens"),
        ).any(::hasThinkingValue)
    }

    private fun hasThinkingValue(value: Any?): Boolean = when (value) {
        null, JSONObject.NULL, false -> false
        is String -> value.isNotBlank()
        is Number -> value.toDouble() > 0.0
        is JSONArray -> value.length() > 0
        is JSONObject -> value.length() > 0
        else -> true
    }

    private fun tokenParamName(model: String): String = if (
        listOf("o1", "o3", "o4", "gpt-5").any(model::startsWith)
    ) {
        "max_completion_tokens"
    } else {
        "max_tokens"
    }

    private fun effectiveModel(model: String): String = model.trim().ifEmpty { "gpt-4o-mini" }

    private fun deriveModelsUrl(chatUrl: String): String {
        val url = chatUrl.trim().trimEnd('/')
        val lower = url.lowercase()
        val chatIndex = lower.lastIndexOf("/chat/completions")
        if (chatIndex >= 0) return url.substring(0, chatIndex) + "/models"
        val versionIndex = lower.indexOf("/v1/")
        if (versionIndex >= 0) return url.substring(0, versionIndex + 3) + "/models"
        return "$url/models"
    }

    private data class HttpResponse(val code: Int, val body: String)
}
