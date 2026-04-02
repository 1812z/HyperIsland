package io.github.hyperisland.xposed.templates

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import de.robv.android.xposed.XposedBridge
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo
import io.github.hyperisland.xposed.IslandDispatcher
import io.github.hyperisland.xposed.IslandRequest
import io.github.hyperisland.xposed.IslandTemplate
import io.github.hyperisland.xposed.IslandViewModel
import io.github.hyperisland.xposed.NotifData
import io.github.hyperisland.xposed.SettingsBridge
import io.github.hyperisland.xposed.hook.FocusNotifStatusBarIconHook
import io.github.hyperisland.xposed.renderer.ImageTextWithButtonsRenderer
import io.github.hyperisland.xposed.renderer.resolveRenderer
import io.github.hyperisland.xposed.toRounded
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * AI 增强版通知超级岛。
 * 将通知信息发送给 AI，由 AI 生成大岛左右文本。若 3 秒内未响应，回退到默认逻辑。
 *
 * 消息处理（AI 调用 + [process]）与渲染（[ImageTextWithButtonsRenderer]/[ImageTextWithButtonsWrapRenderer]）分离。
 */
object AINotificationIslandNotification : IslandTemplate {

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

        if (aiText != null) {
            XposedBridge.log("HyperIsland[AINotifIsland]: AI text — left=$leftText | right=$rightText")
        } else {
            XposedBridge.log("HyperIsland[AINotifIsland]: Using fallback text — left=$leftText | right=$rightText")
        }

        if (data.focusNotif == "off") {
            injectViaDispatcher(context, data, leftText, rightText)
            return
        }
        try {
            val vm = process(context, data, leftText, rightText)
            resolveRenderer(data.renderer).render(context, extras, vm)
            XposedBridge.log(
                "HyperIsland[AINotifIsland]: Injected — title=${data.title} | left=$leftText | right=$rightText | notifId=${data.notifId}"
            )
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[AINotifIsland]: Injection error: ${e.message}")
        }
    }

    // ── AI 配置 ────────────────────────────────────────────────────────────────

    private data class AiConfig(
        val enabled: Boolean,
        val url: String,
        val apiKey: String,
        val model: String,
        val prompt: String,
        val promptInUser: Boolean,
        val timeout: Int,
        val temperature: Double,
        val maxTokens: Int,
    )

    private data class AiIslandText(val left: String, val right: String)

    private data class AiRequestLog(
        val timestamp: Long,
        val source: String,
        val url: String,
        val model: String,
        val requestBody: String,
        val responseBody: String = "",
        val error: String = "",
        val statusCode: Int? = null,
        val durationMs: Long? = null,
    )

    private fun loadAiConfig(context: Context): AiConfig {
        SettingsBridge.init(context)
        return AiConfig(
            enabled = SettingsBridge.getBoolean("pref_ai_enabled", false),
            url     = SettingsBridge.getString("pref_ai_url"),
            apiKey  = SettingsBridge.getString("pref_ai_api_key"),
            model   = SettingsBridge.getString("pref_ai_model"),
            prompt  = SettingsBridge.getString("pref_ai_prompt"),
            promptInUser = SettingsBridge.getBoolean("pref_ai_prompt_in_user", false),
            timeout      = SettingsBridge.getInt("pref_ai_timeout", 3).clamp(1, 10),
            temperature  = SettingsBridge.getDouble("pref_ai_temperature", 0.1),
            maxTokens    = SettingsBridge.getInt("pref_ai_max_tokens", 50),
        )
    }

    private fun Int.clamp(min: Int, max: Int): Int = if (this < min) min else if (this > max) max else this

    // ── AI 调用（带超时） ──────────────────────────────────────────────────────

    private fun fetchAiText(config: AiConfig, data: NotifData): AiIslandText? {
        val startMs = System.currentTimeMillis()
        val requestBody = buildRequestBody(config, data)
        persistAiLog(
            AiRequestLog(
                timestamp = startMs,
                source = "notification",
                url = config.url,
                model = config.model.ifEmpty { "gpt-4o-mini" },
                requestBody = requestBody,
            )
        )
        val future: Future<AiIslandText?> = executor.submit<AiIslandText?> {
            callAiApi(config, data, requestBody, startMs)
        }
        return try {
            future.get(config.timeout.toLong(), TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            persistAiLog(
                AiRequestLog(
                    timestamp = startMs,
                    source = "notification",
                    url = config.url,
                    model = config.model.ifEmpty { "gpt-4o-mini" },
                    requestBody = requestBody,
                    error = "Timeout after ${config.timeout}s",
                    durationMs = System.currentTimeMillis() - startMs,
                )
            )
            XposedBridge.log("HyperIsland[AINotifIsland]: AI request timed out, falling back")
            null
        } catch (e: Exception) {
            persistAiLog(
                AiRequestLog(
                    timestamp = startMs,
                    source = "notification",
                    url = config.url,
                    model = config.model.ifEmpty { "gpt-4o-mini" },
                    requestBody = requestBody,
                    error = e.message ?: e.javaClass.simpleName,
                    durationMs = System.currentTimeMillis() - startMs,
                )
            )
            XposedBridge.log("HyperIsland[AINotifIsland]: AI request error: ${e.message}")
            null
        }
    }

    private fun callAiApi(config: AiConfig, data: NotifData, requestBody: String, startMs: Long): AiIslandText? {
        val conn = (URL(config.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (config.apiKey.isNotEmpty()) setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            connectTimeout = config.timeout * 1000
            readTimeout    = config.timeout * 1000
            doOutput       = true
        }
        XposedBridge.log("HyperIsland[AINotifIsland]: POST ${config.url}")
        return try {
            conn.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val rawResponse = if (code == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "HTTP $code"
            }
            persistAiLog(
                AiRequestLog(
                    timestamp = startMs,
                    source = "notification",
                    url = config.url,
                    model = config.model.ifEmpty { "gpt-4o-mini" },
                    requestBody = requestBody,
                    responseBody = rawResponse,
                    error = if (code == HttpURLConnection.HTTP_OK) "" else "HTTP $code",
                    statusCode = code,
                    durationMs = System.currentTimeMillis() - startMs,
                )
            )
            if (code == HttpURLConnection.HTTP_OK) parseAiResponse(rawResponse) else null
        } catch (e: Exception) {
            persistAiLog(
                AiRequestLog(
                    timestamp = startMs,
                    source = "notification",
                    url = config.url,
                    model = config.model.ifEmpty { "gpt-4o-mini" },
                    requestBody = requestBody,
                    error = e.message ?: e.javaClass.simpleName,
                    durationMs = System.currentTimeMillis() - startMs,
                )
            )
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun persistAiLog(log: AiRequestLog) {
        try {
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(log.timestamp))
            val payload = JSONObject()
                .put("timestamp", iso)
                .put("source", log.source)
                .put("url", log.url)
                .put("model", log.model)
                .put("requestBody", log.requestBody)
                .put("responseBody", log.responseBody)
                .put("error", log.error)
                .put("statusCode", log.statusCode)
                .put("durationMs", log.durationMs)
                .toString()
            SettingsBridge.putString("pref_ai_last_log_json", payload)
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[AINotifIsland]: Failed to persist AI log: ${e.message}")
        }
    }

    private fun buildRequestBody(config: AiConfig, data: NotifData): String {
        val defaultPrompt = "你需要尽可能快的提取关键信息为JSON。left和right均严禁超过6汉字，仅保留最核心的短语，去除修饰词。仅返回纯JSON，严禁Markdown标记。示例：输入：应用包名：com.example.app\\n标题：测试通知\\n正文：这是一条用于测试 AI 提取效果的示例消息输出：{\"left\": \"测试通知\", \"right\": \"测试AI提取\"}"
        val prompt = if (config.prompt.isNotEmpty()) config.prompt else defaultPrompt

        val userContent = buildString {
            append("应用包名：${data.pkg}\n")
            append("标题：${data.title}\n")
            if (data.subtitle.isNotEmpty()) append("正文：${data.subtitle}")
        }

        val messages = org.json.JSONArray().apply {
            if (config.promptInUser) {
                put(JSONObject().put("role", "user").put("content", prompt))
                put(JSONObject().put("role", "user").put("content", userContent))
            } else {
                put(JSONObject().put("role", "system").put("content", prompt))
                put(JSONObject().put("role", "user").put("content", userContent))
            }
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
            XposedBridge.log("HyperIsland[AINotifIsland]: Failed to parse AI response: ${e.message}")
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
            XposedBridge.log("HyperIsland[AINotifIsland]: Dispatcher error: ${e.message}")
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
