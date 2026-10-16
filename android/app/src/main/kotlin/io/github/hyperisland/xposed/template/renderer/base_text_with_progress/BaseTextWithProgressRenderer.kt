package io.github.hyperisland.xposed.renderer.base_text_with_progress

import android.content.Context
import android.os.Bundle
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo
import io.github.hyperisland.xposed.hook.FocusNotifStatusBarIconHook
import io.github.hyperisland.xposed.logDebug
import io.github.hyperisland.xposed.renderer.IslandRenderer
import io.github.hyperisland.xposed.renderer.RendererContext
import io.github.hyperisland.xposed.renderer.fixTextButtonJson
import io.github.hyperisland.xposed.renderer.flattenActionsToExtras
import io.github.hyperisland.xposed.renderer.injectAodConfig
import io.github.hyperisland.xposed.renderer.injectHighlightColor
import io.github.hyperisland.xposed.renderer.injectOutEffectColor
import io.github.hyperisland.xposed.renderer.injectOuterGlow
import io.github.hyperisland.xposed.renderer.injectUpdatable
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationFieldRegistry
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationFieldSpec

/**
 * 文本组件1 + 识别图形组件1 + 进度组件2 渲染器。
 *
 * 展开态（焦点通知）使用 baseInfo + picInfo + progressInfo：
 * - baseInfo(type=1)：主要文本 + 次要文本；
 * - picInfo(type=1)：右侧识别图形（应用图标）；
 * - progressInfo：纯净版进度条（不传任何 picXXX 图标字段）。
 *
 * 大岛 / 小岛与 [io.github.hyperisland.xposed.renderer.image_text_with_progress.ImageTextWithProgressRenderer]
 * 保持一致的排版：小岛图标，大岛左侧图标 + 文本、右侧文本。无按钮组件，适合不需要远程回调用例。
 */
object BaseTextWithProgressRenderer : IslandRenderer {

    const val RENDERER_ID = "base_text_with_progress"

    override val id = RENDERER_ID
    override val focusCustomizationFields: List<FocusCustomizationFieldSpec> = listOf(
        FocusCustomizationFieldRegistry.focusTitleExpr,
        FocusCustomizationFieldRegistry.focusContentExpr,
        FocusCustomizationFieldRegistry.focusIconMode,
    )
    override val customizationContributor = BaseTextWithProgressCustomization

    override fun render(context: Context, extras: Bundle, ctx: RendererContext) {
        try {
            val vm = ctx.vm
            val payload = ctx.payload as? BaseTextWithProgressPayload
            val islandIconKey = "key_${vm.templateId}_island"
            val focusIconKey = "key_${vm.templateId}_focus"
            val aodIconKey = "miui.focus.pic_aod"

            val builder = HyperIslandNotification.Builder(context, vm.templateId, vm.focusTitle)
            builder.addPicture(HyperPicture(islandIconKey, vm.islandIcon))
            builder.addPicture(HyperPicture(focusIconKey, vm.focusIcon))
            builder.addPicture(HyperPicture(aodIconKey, vm.aodIcon ?: vm.islandIcon))

            // 文本组件1（baseInfo, type=1）
            builder.setBaseInfo(
                title = vm.focusTitle,
                content = vm.focusContent,
                type = 1,
            )
            // 识别图形组件1（picInfo, type=1）
            builder.setPicInfo(type = 1, picKey = focusIconKey)
            // 进度组件2（progressInfo 纯净版：不传任何 picXXX）
            vm.circularProgress?.let { progress ->
                builder.setProgressBar(
                    progress = progress.coerceIn(0, 100),
                    color = payload?.progressBarColor ?: "#34C759",
                    colorEnd = payload?.progressBarColorEnd ?: "#30B0C7",
                )
            }

            builder.setIslandFirstFloat(vm.firstFloat)
            builder.setEnableFloat(vm.enableFloat)
            builder.setShowNotification(vm.showNotification)
            builder.setIslandConfig(timeout = vm.timeoutSecs, dismissible = vm.dismissIsland)

            // 小岛 + 大岛（islandEnabled=false 时不构建 areas，岛由 dismissIsland 关闭）
            if (vm.islandEnabled) {
                builder.setSmallIsland(islandIconKey)

                val leftSide = if (!vm.showIslandIcon) {
                    ImageTextInfoLeft(
                        type = 1,
                        textInfo = TextInfo(
                            title = vm.leftTitle,
                            narrowFont = vm.showLeftNarrowFont,
                            showHighlightColor = vm.showLeftHighlightColor,
                        ),
                    )
                } else {
                    ImageTextInfoLeft(
                        type = 1,
                        picInfo = PicInfo(type = 1, pic = islandIconKey),
                        textInfo = TextInfo(
                            title = vm.leftTitle,
                            narrowFont = vm.showLeftNarrowFont,
                            showHighlightColor = vm.showLeftHighlightColor,
                        ),
                    )
                }
                builder.setBigIslandInfo(
                    left = leftSide,
                    right = ImageTextInfoRight(
                        type = 2,
                        textInfo = TextInfo(
                            title = vm.rightTitle,
                            narrowFont = vm.showRightNarrowFont,
                            showHighlightColor = vm.showRightHighlightColor,
                        ),
                    ),
                )
            }

            val resourceBundle = builder.buildResourceBundle()
            val pictures = resourceBundle.getBundle("miui.focus.pics") ?: Bundle()
            pictures.putParcelable(aodIconKey, vm.aodIcon ?: vm.islandIcon)
            resourceBundle.putBundle("miui.focus.pics", pictures)
            extras.putAll(resourceBundle)
            flattenActionsToExtras(resourceBundle, extras)

            var jsonParam = fixTextButtonJson(builder.buildJsonParam())
            jsonParam = injectUpdatable(jsonParam, vm.updatable)
            if (vm.islandEnabled) jsonParam = injectHighlightColor(jsonParam, vm.highlightColor)
            jsonParam = injectOuterGlow(jsonParam, vm.outerGlow)
            jsonParam = injectOutEffectColor(jsonParam, vm.outEffectColor)
            jsonParam = injectAodConfig(jsonParam, vm.aodTitle, aodIconKey)
            extras.putString("miui.focus.param", jsonParam)

            if (vm.setFocusProxy && vm.showNotification) {
                extras.putBoolean("hyperisland_focus_proxy", true)
            }
            if (vm.preserveStatusBarSmallIcon && vm.showNotification) {
                extras.putBoolean("hyperisland_preserve_status_bar_small_icon", true)
                FocusNotifStatusBarIconHook.markDirectProxyPosted(vm.timeoutSecs)
            }

            logDebug("HyperIsland") { "HyperIsland[$RENDERER_ID]: rendered template=${vm.templateId}" }
        } catch (e: Exception) {
            logDebug("HyperIsland") { "HyperIsland[$RENDERER_ID]: render error: ${e.message}" }
        }
    }
}