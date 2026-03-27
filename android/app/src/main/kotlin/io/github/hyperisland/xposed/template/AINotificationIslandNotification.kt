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
import io.github.hyperisland.xposed.hook.FocusNotifStatusBarIconHook
import io.github.hyperisland.xposed.renderer.ImageTextWithButtonsRenderer
import io.github.hyperisland.xposed.renderer.ImageTextWithButtonsWrapRenderer
import io.github.hyperisland.xposed.toRounded
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
            val renderer = if (data.renderer == ImageTextWithButtonsWrapRenderer.RENDERER_ID) ImageTextWithButtonsWrapRenderer
                           else ImageTextWithButtonsRenderer
            renderer.render(context, extras, vm)
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
    )

    private data class AiIslandText(val left: String, val right: String)

    private fun loadAiConfig(context: Context): AiConfig {
        fun readString(key: String): String = try {
            val uri = android.net.Uri.parse("content://io.github.hyperisland.settings/$key")
            context.contentResolver.query(uri, null, null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else "" } ?: ""
        } catch (_: Exception) { "" }

        fun readBool(key: String): Boolean = try {
            val uri = android.net.Uri.parse("content://io.github.hyperisland.settings/$key")
            context.contentResolver.query(uri, null, null, null, null)
                ?.use { if (it.moveToFirst()) it.getInt(0) != 0 else false } ?: false
        } catch (_: Exception) { false }

        return AiConfig(
            enabled = readBool("pref_ai_enabled"),
            url     = readString("pref_ai_url"),
            apiKey  = readString("pref_ai_api_key"),
            model   = readString("pref_ai_model"),
        )
    }

    // ── AI 调用（带超时） ──────────────────────────────────────────────────────

    private fun fetchAiText(config: AiConfig, data: NotifData): AiIslandText? {
        val future: Future<AiIslandText?> = executor.submit<AiIslandText?> {
            callAiApi(config, data)
        }
        return try {
            future.get(3, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            XposedBridge.log("HyperIsland[AINotifIsland]: AI request timed out, falling back")
            null
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[AINotifIsland]: AI request error: ${e.message}")
            null
        }
    }

    private fun callAiApi(config: AiConfig, data: NotifData): AiIslandText? {
        val requestBody = buildRequestBody(config.model, data)
        val conn = (URL(config.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (config.apiKey.isNotEmpty()) setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            connectTimeout = 2500
            readTimeout    = 2500
            doOutput       = true
        }
        XposedBridge.log("HyperIsland[AINotifIsland]: POST ${config.url}")
        return try {
            conn.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val errorBody = try { conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "" } catch (_: Exception) { "" }
                XposedBridge.log("HyperIsland[AINotifIsland]: HTTP $code — $errorBody")
                return null
            }
            parseAiResponse(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun buildRequestBody(model: String, data: NotifData): String {
        val systemPrompt = """
你是一个 Android 通知摘要助手。
根据通知信息，提取关键信息，仅返回如下 JSON，不得包含任何其他文字或代码块：
{"left":"通知来源（优先提取谁发的而不是应用名称，6字以内）","right":"核心内容（不超过6汉字或者12数字字母）"}
""".trimIndent()

        val userContent = buildString {
            append("应用包名：${data.pkg}\n")
            append("标题：${data.title}\n")
            if (data.subtitle.isNotEmpty()) append("正文：${data.subtitle}")
        }

        val messages = org.json.JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            put(JSONObject().put("role", "user").put("content", userContent))
        }
        return JSONObject()
            .put("model", model.ifEmpty { "gpt-4o-mini" })
            .put("messages", messages)
            .put("max_tokens", 80)
            .put("temperature", 0.1)
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
