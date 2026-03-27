package io.github.hyperisland.xposed.renderer

import android.content.Context
import android.os.Bundle
import de.robv.android.xposed.XposedBridge
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.CircularProgressInfo
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.ProgressTextInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo
import io.github.hyperisland.xposed.IslandViewModel
import io.github.hyperisland.xposed.hook.FocusNotifStatusBarIconHook

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

    override fun render(context: Context, extras: Bundle, vm: IslandViewModel) {
        renderWith(context, extras, vm, applyWrap = false)
    }

    /** 供 [ImageTextWithButtonsWrapRenderer] 复用，避免重复布局代码。 */
    internal fun renderWith(context: Context, extras: Bundle, vm: IslandViewModel, applyWrap: Boolean) {
        try {
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
            val leftSide = ImageTextInfoLeft(
                type     = 1,
                picInfo  = PicInfo(type = 1, pic = iconKey),
                textInfo = TextInfo(title = vm.leftTitle),
            )
            when {
                vm.circularProgress != null -> builder.setBigIslandInfo(
                    left = leftSide,
                    progressText = ProgressTextInfo(
                        progressInfo = CircularProgressInfo(progress = vm.circularProgress),
                        textInfo     = TextInfo(title = vm.rightTitle, narrowFont = true),
                    ),
                )
                vm.showRightSide -> builder.setBigIslandInfo(
                    left  = leftSide,
                    right = ImageTextInfoRight(
                        type     = 2,
                        textInfo = TextInfo(title = vm.rightTitle, narrowFont = true),
                    ),
                )
                else -> builder.setBigIslandInfo(left = leftSide)
            }

            // 按钮（showNotification=false 时不添加）
            val effectiveActions = vm.actions.take(2)
            if (effectiveActions.isNotEmpty() && vm.showNotification) {
                val hyperActions = effectiveActions.mapIndexed { index, action ->
                    HyperAction(
                        key              = "action_${vm.templateId}_$index",
                        title            = action.title ?: "",
                        pendingIntent    = action.actionIntent,
                        actionIntentType = 2,
                    )
                }
                hyperActions.forEach { builder.addHiddenAction(it) }
                builder.setTextButtons(*hyperActions.toTypedArray())
            }

            val resourceBundle = builder.buildResourceBundle()
            extras.putAll(resourceBundle)
            flattenActionsToExtras(resourceBundle, extras)

            var jsonParam = fixTextButtonJson(builder.buildJsonParam())
            if (applyWrap) jsonParam = wrapLongTextJson(jsonParam)
            jsonParam = injectUpdatable(jsonParam, vm.updatable)
            extras.putString("miui.focus.param", jsonParam)

            if (vm.setFocusProxy && vm.showNotification) {
                extras.putBoolean("hyperisland_focus_proxy", true)
            }
            if (vm.preserveStatusBarSmallIcon && vm.showNotification) {
                extras.putBoolean("hyperisland_preserve_status_bar_small_icon", true)
                FocusNotifStatusBarIconHook.markDirectProxyPosted(vm.timeoutSecs)
            }

            val rendererTag = if (applyWrap) ImageTextWithButtonsWrapRenderer.RENDERER_ID else RENDERER_ID
            XposedBridge.log("HyperIsland[$rendererTag]: rendered template=${vm.templateId}")
        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[$RENDERER_ID]: render error: ${e.message}")
        }
    }
}
