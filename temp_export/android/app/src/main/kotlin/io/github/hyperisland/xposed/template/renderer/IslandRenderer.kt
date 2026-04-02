package io.github.hyperisland.xposed.renderer

import android.app.Notification
import android.content.Context
import android.os.Build
import android.os.Bundle
import io.github.hyperisland.xposed.IslandViewModel

/**
 * 灵动岛渲染器接口。
 *
 * 消费 [IslandViewModel]，将 HyperOS 超级岛 JSON 写入 extras。
 * 新增渲染器步骤：实现此接口，在模板的 inject() 中引用即可。
 */
interface IslandRenderer {
    val id: String
    fun render(context: Context, extras: Bundle, vm: IslandViewModel)
}

// ── 共享工具函数 ──────────────────────────────────────────────────────────────

/**
 * 修正 textButton 字段：新库输出 "actionIntent"+"actionIntentType"，
 * HyperOS V3 协议只认 "action"，否则按钮点击无响应。
 *
 * 注意：仅处理按钮字段，不包含任何布局变换逻辑。
 */
fun fixTextButtonJson(jsonParam: String): String =
    try {
        val json = org.json.JSONObject(jsonParam)
        val pv2 = json.optJSONObject("param_v2") ?: return jsonParam
        val btns = pv2.optJSONArray("textButton")
        if (btns != null) {
            for (i in 0 until btns.length()) {
                val btn = btns.getJSONObject(i)
                val key = btn.optString("actionIntent").takeIf { it.isNotEmpty() } ?: continue
                btn.put("action", key)
                btn.remove("actionIntent")
                btn.remove("actionIntentType")
            }
        }
        json.toString()
    } catch (_: Exception) {
        jsonParam
    }

/**
 * 长文本折行样式：将 iconTextInfo 转为 coverInfo，
 * 把超长内容拆为上下两行（content + subContent）显示。
 *
 * 此函数是独立的视觉样式变换，由 [ImageTextWithButtonsWrapRenderer] 调用，
 * 不应混入 [fixTextButtonJson] 或基础渲染器中。
 */
fun wrapLongTextJson(jsonParam: String): String =
    try {
        val json = org.json.JSONObject(jsonParam)
        val pv2 = json.optJSONObject("param_v2") ?: return jsonParam
        val iconTextInfo = pv2.optJSONObject("iconTextInfo") ?: return jsonParam
        val content = iconTextInfo.optString("content", "")
        if (content.isEmpty()) return jsonParam

        var visualLen = 0
        var splitIdx = -1
        for (i in content.indices) {
            visualLen += if (content[i].code > 255) 2 else 1
            if (visualLen >= 36 && splitIdx == -1) splitIdx = i + 1
        }
        if (splitIdx == -1 || splitIdx >= content.length) return jsonParam

        val subContent = content.substring(splitIdx)
        if (subContent.all { it == '.' || it == '…' || it.isWhitespace() }) return jsonParam

        val coverInfo = org.json.JSONObject()
        val animIcon = iconTextInfo.optJSONObject("animIconInfo")
        if (animIcon != null) coverInfo.put("picCover", animIcon.optString("src", ""))
        coverInfo.put("title", iconTextInfo.optString("title", ""))
        coverInfo.put("content", content.substring(0, splitIdx))
        coverInfo.put("subContent", subContent)
        pv2.remove("iconTextInfo")
        pv2.put("coverInfo", coverInfo)
        json.toString()
    } catch (_: Exception) {
        jsonParam
    }

/** 注入 param_v2.updatable 字段。 */
fun injectUpdatable(jsonParam: String, updatable: Boolean): String =
    try {
        val json = org.json.JSONObject(jsonParam)
        val pv2 = json.optJSONObject("param_v2") ?: return jsonParam
        pv2.put("updatable", updatable)
        json.toString()
    } catch (_: Exception) {
        jsonParam
    }

/**
 * 根据渲染器 ID 返回对应的 [IslandRenderer] 实例，未匹配时回退到默认渲染器。
 * 新增渲染器只需在此处注册，所有模板无需修改。
 */
fun resolveRenderer(id: String): IslandRenderer = when (id) {
    ImageTextWithButtonsWrapRenderer.RENDERER_ID -> ImageTextWithButtonsWrapRenderer
    ImageTextWithRightTextButtonRenderer.RENDERER_ID -> ImageTextWithRightTextButtonRenderer
    else -> ImageTextWithButtonsRenderer
}

/** 将 buildResourceBundle() 里嵌套的 "miui.focus.actions" 展开到 extras 顶层。 */
fun flattenActionsToExtras(resourceBundle: Bundle, extras: Bundle) {
    val nested = resourceBundle.getBundle("miui.focus.actions") ?: return
    for (key in nested.keySet()) {
        val action: Notification.Action? = if (Build.VERSION.SDK_INT >= 33)
            nested.getParcelable(key, Notification.Action::class.java)
        else
            @Suppress("DEPRECATION") nested.getParcelable(key)
        if (action != null) extras.putParcelable(key, action)
    }
}
