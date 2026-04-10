package io.github.hyperisland.xposed.renderer.image_text_with_buttons

import android.content.Context
import android.os.Bundle
import android.util.Log
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.CircularProgressInfo
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.ProgressTextInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo
import io.github.hyperisland.xposed.hook.FocusNotifStatusBarIconHook
import io.github.hyperisland.xposed.renderer.IslandRenderer
import io.github.hyperisland.xposed.renderer.RendererContext
import io.github.hyperisland.xposed.renderer.fixTextButtonJson
import io.github.hyperisland.xposed.renderer.flattenActionsToExtras
import io.github.hyperisland.xposed.renderer.injectHighlightColor
import io.github.hyperisland.xposed.renderer.injectOutEffectColor
import io.github.hyperisland.xposed.renderer.injectOuterGlow
import io.github.hyperisland.xposed.renderer.injectUpdatable
import io.github.hyperisland.xposed.renderer.wrapLongTextJson
import io.github.hyperisland.xposed.renderer.image_text_with_buttons_wrap.ImageTextWithButtonsWrapRenderer
import io.github.hyperisland.xposed.renderer.image_text_with_right_text_button.ImageTextWithRightTextButtonRenderer
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationFieldRegistry
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationFieldSpec
import io.github.hyperisland.xposed.template.core.models.IslandViewModel

/**
 * 新图文组件+按钮组件4 渲染器。
 *
 * 布局：
 *  - 小岛：图标（+ 可选环形进度）
 *  - 大岛：左侧 图标+文字，右侧 文字 或 环形进度（或不显示右侧）
 *  - 焦点通知：图标 + 标题 + 正文
 *  - 按钮：最多 2 个文字按钮
 *
 * 不包含任何文本折行逻辑，如需折行请使用 [ImageTextWithButtonsWrapRenderer]。
 */
object ImageTextWithButtonsRenderer : IslandRenderer {

    const val RENDERER_ID = "image_text_with_buttons_4"

    override val id = RENDERER_ID
    override val focusCustomizationFields: List<FocusCustomizationFieldSpec> = listOf(
        FocusCustomizationFieldRegistry.focusTitleExpr,
        FocusCustomizationFieldRegistry.focusContentExpr,
        FocusCustomizationFieldRegistry.focusIconMode,
    )
    override val customizationContributor = ImageTextWithButtonsCustomization

    override fun render(context: Context, extras: Bundle, ctx: RendererContext) {
        renderWith(context, extras, ctx, applyWrap = false)
    }

    /** 供 [ImageTextWithButtonsWrapRenderer] 和 [ImageTextWithRightTextButtonRenderer] 复用，避免重复布局代码。 */
    internal fun renderWith(context: Context, extras: Bundle, ctx: RendererContext, applyWrap: Boolean, maxButtons: Int = 2, useActionsButton: Boolean = false) {
        try {
            val vm = ctx.vm
            val payload = ctx.payload as? ImageTextWithButtonsPayload
            val iconKey      = "key_${vm.templateId}_island"
            val focusIconKey = "key_${vm.templateId}_focus"

            val builder = HyperIslandNotification.Builder(context, vm.templateId, vm.focusTitle)

            builder.addPicture(HyperPicture(iconKey,      vm.islandIcon))
            builder.addPicture(HyperPicture(focusIconKey, vm.focusIcon))

            builder.setIconTextInfo(
                picKey  = focusIconKey,
                title   = vm.focusTitle,
                content = vm.focusContent,
            )

            builder.setIslandFirstFloat(vm.firstFloat)
            builder.setEnableFloat(vm.enableFloat)
            builder.setShowNotification(vm.showNotification)
            builder.setIslandConfig(timeout = vm.timeoutSecs)

            // 小岛
            if (vm.circularProgress != null) {
                builder.setSmallIslandCircularProgress(iconKey, vm.circularProgress)
            } else {
                builder.setSmallIsland(iconKey)
            }

            // 大岛
            val leftSide = if (!vm.showIslandIcon) {
                ImageTextInfoLeft(
                    type     = 1,
                    textInfo = TextInfo(title = vm.leftTitle, narrowFont = vm.showLeftNarrowFont, showHighlightColor = vm.showLeftHighlightColor),
                )
            } else {
                ImageTextInfoLeft(
                    type     = 1,
                    picInfo  = PicInfo(type = 1, pic = iconKey),
                    textInfo = TextInfo(title = vm.leftTitle, narrowFont = vm.showLeftNarrowFont, showHighlightColor = vm.showLeftHighlightColor),
                )
            }
            when {
                vm.circularProgress != null -> builder.setBigIslandInfo(
                    left = leftSide,
                    progressText = progressTextInfoFor(vm),
                )
                else -> builder.setBigIslandInfo(
                    left  = leftSide,
                    right = ImageTextInfoRight(
                        type     = 2,
                        textInfo = TextInfo(title = vm.rightTitle, narrowFont = vm.showRightNarrowFont, showHighlightColor = vm.showRightHighlightColor),
                    ),
                )
            }

            // 按钮（showNotification=false 时不添加）
            val effectiveActions = vm.actions.take(maxButtons)
            if (effectiveActions.isNotEmpty() && vm.showNotification) {
                if (useActionsButton) {
                    // 按钮组件1 type=2：右侧文字按钮，无图标，仅支持 1 个
                    val action = effectiveActions.first()
                    builder.addAction(HyperAction(
                        key              = "action_${vm.templateId}_0",
                        title            = action.title ?: "",
                        pendingIntent    = action.actionIntent,
                        actionIntentType = 2,
                        bgColor = payload?.action1BgColor,
                        bgColorDark = payload?.action1BgColorDark,
                        titleColor = payload?.action1TitleColor,
                        titleColorDark = payload?.action1TitleColorDark,
                    ))
                } else {
                    // 按钮组件4：textButton，最多 maxButtons 个
                    val hyperActions = effectiveActions.mapIndexed { index, action ->
                        val bgColor = if (index == 0) payload?.action1BgColor else payload?.action2BgColor
                        val bgColorDark = if (index == 0) payload?.action1BgColorDark else payload?.action2BgColorDark
                        val titleColor = if (index == 0) payload?.action1TitleColor else payload?.action2TitleColor
                        val titleColorDark = if (index == 0) payload?.action1TitleColorDark else payload?.action2TitleColorDark
                        HyperAction(
                            key              = "action_${vm.templateId}_$index",
                            title            = action.title ?: "",
                            pendingIntent    = action.actionIntent,
                            actionIntentType = 2,
                            bgColor = bgColor,
                            bgColorDark = bgColorDark,
                            titleColor = titleColor,
                            titleColorDark = titleColorDark,
                        )
                    }
                    hyperActions.forEach { builder.addHiddenAction(it) }
                    builder.setTextButtons(*hyperActions.toTypedArray())
                }
            }

            val resourceBundle = builder.buildResourceBundle()
            extras.putAll(resourceBundle)
            flattenActionsToExtras(resourceBundle, extras)

            var jsonParam = fixTextButtonJson(builder.buildJsonParam())
            if (applyWrap) jsonParam = wrapLongTextJson(jsonParam)
            jsonParam = injectProgressColor(jsonParam, vm.progressColor)
            jsonParam = injectUpdatable(jsonParam, vm.updatable)
            jsonParam = injectHighlightColor(jsonParam, vm.highlightColor)
            jsonParam = injectOuterGlow(jsonParam, vm.outerGlow)
            jsonParam = injectOutEffectColor(jsonParam, vm.outEffectColor)
            extras.putString("miui.focus.param", jsonParam)

            if (vm.setFocusProxy && vm.showNotification) {
                extras.putBoolean("hyperisland_focus_proxy", true)
            }
            if (vm.preserveStatusBarSmallIcon && vm.showNotification) {
                extras.putBoolean("hyperisland_preserve_status_bar_small_icon", true)
                FocusNotifStatusBarIconHook.markDirectProxyPosted(vm.timeoutSecs)
            }

            val rendererTag = when {
                applyWrap          -> ImageTextWithButtonsWrapRenderer.RENDERER_ID
                useActionsButton   -> ImageTextWithRightTextButtonRenderer.RENDERER_ID
                else               -> RENDERER_ID
            }
            Log.d("HyperIsland", "HyperIsland[$rendererTag]: rendered template=${vm.templateId}")
        } catch (e: Exception) {
            Log.d("HyperIsland", "HyperIsland[$RENDERER_ID]: render error: ${e.message}")
        }
    }
}

fun injectProgressColor(jsonParam: String, progressColor: String?): String {
    if (progressColor.isNullOrBlank()) return jsonParam
    return try {
        val json = org.json.JSONObject(jsonParam)
        val pv2 = json.optJSONObject("param_v2") ?: return jsonParam
        val progressText = pv2.optJSONObject("progressTextInfo") ?: return jsonParam
        val progressInfo = progressText.optJSONObject("progressInfo") ?: return jsonParam
        progressInfo.put("colorProgress", progressColor)
        progressInfo.put("colorProgressEnd", progressColor)
        json.toString()
    } catch (_: Exception) {
        jsonParam
    }
}

fun injectImChatInfo(
    jsonParam: String,
    picProfileKey: String,
    picProfileDarkKey: String?,
    title: String,
    content: String,
    progress: Int?,
    progressColor: String?,
): String = try {
    val json = org.json.JSONObject(jsonParam)
    val pv2 = json.optJSONObject("param_v2") ?: return jsonParam
    val chatInfo = org.json.JSONObject().apply {
        put("picProfile", picProfileKey)
        if (!picProfileDarkKey.isNullOrBlank()) {
            put("picProfileDark", picProfileDarkKey)
        }
        put("title", title)
        put("content", content)
        if (progress != null) {
            val p = org.json.JSONObject().apply {
                put("progress", progress)
                progressColor?.let {
                    put("colorProgress", it)
                    put("colorProgressEnd", it)
                }
            }
            put("progressInfo", p)
        }
    }
    pv2.remove("iconTextInfo")
    pv2.put("chatInfo", chatInfo)
    json.toString()
} catch (_: Exception) {
    jsonParam
}

fun progressTextInfoFor(vm: IslandViewModel): ProgressTextInfo {
    val p = vm.circularProgress ?: 0
    return ProgressTextInfo(
        progressInfo = CircularProgressInfo(progress = p),
        textInfo = TextInfo(
            title = vm.rightTitle,
            narrowFont = vm.showRightNarrowFont,
            showHighlightColor = vm.showRightHighlightColor,
        ),
    )
}
