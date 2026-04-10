package io.github.hyperisland.xposed.templates

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import io.github.hyperisland.xposed.log
import io.github.hyperisland.xposed.logError
import io.github.hyperisland.xposed.logWarn
import io.github.hyperisland.xposed.islanddispatch.IslandRequest
import io.github.hyperisland.xposed.template.core.contracts.IslandTemplate
import io.github.hyperisland.xposed.template.core.models.NotifData
import io.github.hyperisland.xposed.template.core.models.IslandViewModel
import io.github.hyperisland.xposed.utils.toRounded
import io.github.hyperisland.xposed.hook.FocusNotifStatusBarIconHook
import io.github.hyperisland.xposed.renderer.ImageTextWithButtonsRenderer
import io.github.hyperisland.xposed.renderer.resolveRenderer
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * AI 增强版通知超级岛。
 * 将通知信息发送给 AI，由 AI 生成大岛左右文本。若 3 秒内未响应，回退到默认逻辑。
 *
 * 消息处理（AI 调用 + [process]）与渲染（[ImageTextWithButtonsRenderer]/[ImageTextWithButtonsWrapRenderer]）分离。
 */
object AINotificationIslandNotification : IslandTemplate {

    private const val TAG = "HyperIsland[AINotifIsland]"
    const val TEMPLATE_ID = "ai_notification_island"

    override val id = TEMPLATE_ID

    private val executor = Executors.newCachedThreadPool()

    override fun inject(context: Context, extras: Bundle, data: NotifData) {
        val aiConfig = loadAiConfig(context)
        val aiText = if (aiConfig.enabled && aiConfig.url.isNotEmpty()) {
            fetchAiText(aiConfig, data)
        } else null

        val leftText  = aiText?.left  ?: data.title
        val rightText = aiText?.right ?: data.subtitle.ifEmpty { data.title }

        log(
            if (aiText != null) "$TAG: AI text — left=$leftText | right=$rightText"
            else "$TAG: fallback text — left=$leftText | right=$rightText"
        )

        if (data.focusNotif == "off") {
            injectViaDispatcher(context, data, leftText, rightText)
            return
        }
        try {
            val vm = process(context, data, leftText, rightText)
            resolveRenderer(data.renderer).render(context, extras, vm)
            //ConfigManager.module()?.log("$TAG: injected — title=${data.title} | left=$leftText | right=$rightText | notifId=${data.notifId}")
        } catch (e: Exception) {
            logError("$TAG: injection error: ${e.message}")
        }
    }

    // ── AI 配置 ────────────────────────────────────────────────────────────────

    private data class AiConfig(
        val enabled: Boolean,
        val url: String,
        val apiKey: String,
        val model: String,
        val prompt: String,
        val timeout: Int,
        val promptInUser: Boolean,
        val temperature: Float,
        val maxTokens: Int,
    )

    private data class AiIslandText(val left: String, val right: String)

    private fun loadAiConfig(context: Context): AiConfig = AiConfig(
        enabled = ConfigManager.getBoolean("pref_ai_enabled", false),
        url     = ConfigManager.getString("pref_ai_url"),
        apiKey  = ConfigManager.getString("pref_ai_api_key"),
        model   = ConfigManager.getString("pref_ai_model"),
        prompt  = ConfigManager.getString("pref_ai_prompt"),
        timeout = ConfigManager.getInt("pref_ai_timeout", 3).coerceIn(3, 15),
        promptInUser = ConfigManager.getBoolean("pref_ai_prompt_in_user", false),
        temperature = ConfigManager.getFloat("pref_ai_temperature", 0.1f).coerceIn(0f, 1f),
        maxTokens = ConfigManager.getInt("pref_ai_max_tokens", 50).coerceIn(10, 500),
    )

    // ── AI 调用（带超时） ──────────────────────────────────────────────────────

    private fun fetchAiText(config: AiConfig, data: NotifData): AiIslandText? {
        val future: Future<AiIslandText?> = executor.submit<AiIslandText?> {
            callAiApi(config, data)
        }
        return try {
            future.get(config.timeout.toLong(), TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            logWarn("$TAG: AI request timed out, falling back")
            null
        } catch (e: Exception) {
            logError("$TAG: AI request error: ${e.message}")
            null
        }
    }

    private fun callAiApi(config: AiConfig, data: NotifData): AiIslandText? {
        val requestBody = buildRequestBody(config, data)
        val conn = (URL(config.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (config.apiKey.isNotEmpty()) setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            connectTimeout = config.timeout * 1000
            readTimeout    = config.timeout * 1000
            doOutput       = true
        }
        //log("$TAG: POST ${config.url}")
        return try {
            conn.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val errorBody = try { conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "" } catch (_: Exception) { "" }
                logError("$TAG: HTTP $code — $errorBody")
                return null
            }
            parseAiResponse(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun buildRequestBody(config: AiConfig, data: NotifData): String {
        val defaultPrompt = "根据通知信息，提取关键信息，左右分别不超过6汉字12字符"
        val userPrompt = if (config.prompt.isNotEmpty()) config.prompt else defaultPrompt

        val userContent = buildString {
            append("应用包名：${data.pkg}\n")
            append("标题：${data.title}\n")
            if (data.subtitle.isNotEmpty()) append("正文：${data.subtitle}")
        }

        val messages = org.json.JSONArray()

        if (config.promptInUser) {
            // 提示词放在用户消息中
            val combinedUserContent = buildString {
                append(userPrompt)
                append("\n\n仅返回如下 JSON，不得包含任何其他文字或代码块：\n")
                append("{\"left\":\"左侧文本（谁发的）\",\"right\":\"右侧文本（总结）\"}\n\n")
                append(userContent)
            }
            messages.put(JSONObject().put("role", "user").put("content", combinedUserContent))
        } else {
            // 提示词放在系统消息中（默认）
            val systemPrompt = """
$userPrompt
仅返回如下 JSON，不得包含任何其他文字或代码块：
{"left":"左侧文本(谁发的)","right":"右侧文本（总结）"}
""".trimIndent()
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
            messages.put(JSONObject().put("role", "user").put("content", userContent))
        }

        return JSONObject()
            .put("model", config.model.ifEmpty { "gpt-4o-mini" })
            .put("messages", messages)
            .put("max_tokens", config.maxTokens)
            .put("temperature", config.temperature)
            .toString()
    }

    private fun parseAiResponse(responseText: String): AiIslandText? {
        return try {
            val root    = JSONObject(responseText)
            val content = root.getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message").getString("content").trim()
            val jsonStr = content.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val result  = JSONObject(jsonStr)
            val left    = result.optString("left",  "").trim()
            val right   = result.optString("right", "").trim()
            if (left.isEmpty() && right.isEmpty()) null
            else AiIslandText(left.ifEmpty { "通知" }, right.ifEmpty { "新消息" })
        } catch (e: Exception) {
            logError("$TAG: failed to parse AI response: ${e.message}")
            null
        }
    }

    // ── Dispatcher 路径（focusNotif == "off"）────────────────────────────────

    private fun injectViaDispatcher(
        context: Context,
        data: NotifData,
        leftText: String,
        rightText: String,
    ) {
        try {
            val fallbackIcon = Icon.createWithResource(context, android.R.drawable.ic_dialog_info)
            val displayIcon  = resolveIcon(data, data.iconMode, fallbackIcon).toRounded(context)
            IslandDispatcher.post(
                context,
                IslandRequest(
                    title            = leftText,
                    content          = rightText,
                    icon             = displayIcon,
                    timeoutSecs      = data.islandTimeout,
                    firstFloat       = data.firstFloat == "on",
                    enableFloat      = data.enableFloatMode == "on",
                    showNotification = false,
                    preserveStatusBarSmallIcon = data.preserveStatusBarSmallIcon != "off",
                    contentIntent    = data.contentIntent,
                    isOngoing        = data.isOngoing,
                    actions          = data.actions.take(2),
                ),
            )
        } catch (e: Exception) {
            logError("$TAG: dispatcher error: ${e.message}")
        }
    }

    // ── 消息处理 ──────────────────────────────────────────────────────────────

    fun process(
        context: Context,
        data: NotifData,
        leftText: String  = data.title,
        rightText: String = data.subtitle.ifEmpty { data.title },
    ): IslandViewModel {
        val fallbackIcon     = Icon.createWithResource(context, android.R.drawable.ic_dialog_info)
        val islandIcon       = resolveIcon(data, data.iconMode,      fallbackIcon).toRounded(context)
        val focusIcon        = resolveIcon(data, data.focusIconMode,  fallbackIcon).toRounded(context)
        val showNotification = data.focusNotif != "off"

        return IslandViewModel(
            templateId        = TEMPLATE_ID,
            leftTitle         = leftText,
            rightTitle        = rightText,
            focusTitle        = data.title,
            focusContent      = data.subtitle.ifEmpty { data.title },
            islandIcon        = islandIcon,
            focusIcon         = focusIcon,
            circularProgress  = null,
            showRightSide     = true,
            actions           = data.actions,
            updatable         = data.isOngoing,
            showNotification  = showNotification,
            setFocusProxy     = showNotification,
            preserveStatusBarSmallIcon = showNotification && data.preserveStatusBarSmallIcon != "off",
            firstFloat        = data.firstFloat == "on",
            enableFloat       = data.enableFloatMode == "on",
            timeoutSecs       = data.islandTimeout,
            isOngoing         = data.isOngoing,
            showIslandIcon    = data.showIslandIcon == "on",
            highlightColor    = data.highlightColor,
            showLeftHighlightColor = data.showLeftHighlightColor,
            showRightHighlightColor = data.showRightHighlightColor,
            showLeftNarrowFont = data.showLeftNarrowFont,
            showRightNarrowFont = data.showRightNarrowFont,
            outerGlow = data.outerGlow,
        )
    }

    // ── 图标解析 ──────────────────────────────────────────────────────────────

    private fun resolveIcon(data: NotifData, mode: String?, fallback: Icon): Icon =
        when (mode) {
            "notif_small" -> data.notifIcon ?: fallback
            "notif_large" -> data.largeIcon ?: data.notifIcon ?: fallback
            "app_icon"    -> data.appIconRaw ?: fallback
            else          -> data.largeIcon ?: data.notifIcon ?: fallback
        }
}
