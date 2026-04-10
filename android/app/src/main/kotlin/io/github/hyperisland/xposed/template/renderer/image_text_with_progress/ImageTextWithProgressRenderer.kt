package io.github.hyperisland.xposed.renderer.image_text_with_progress

import android.content.Context
import android.os.Bundle
import android.util.Log
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.hyperisland.xposed.hook.FocusNotifStatusBarIconHook
import io.github.hyperisland.xposed.renderer.IslandRenderer
import io.github.hyperisland.xposed.renderer.RendererContext
import io.github.hyperisland.xposed.renderer.fixTextButtonJson
import io.github.hyperisland.xposed.renderer.flattenActionsToExtras
import io.github.hyperisland.xposed.renderer.injectHighlightColor
import io.github.hyperisland.xposed.renderer.injectOutEffectColor
import io.github.hyperisland.xposed.renderer.injectOuterGlow
import io.github.hyperisland.xposed.renderer.injectUpdatable
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationFieldRegistry
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationFieldSpec

/**
 * IM图文组件 + 进度组件2 渲染器。
 *
 * 展开态使用 chatInfo + progressInfo：
 * - picProfile: 头像图标资源
 * - appiconPkg: 应用包名（可自定义）
 * - title/content: 主次文本
 * - progressInfo: 进度条
 */
object ImageTextWithProgressRenderer : IslandRenderer {

    const val RENDERER_ID = "image_text_with_progress"

    override val id = RENDERER_ID
    override val focusCustomizationFields: List<FocusCustomizationFieldSpec> = listOf(
        FocusCustomizationFieldRegistry.focusTitleExpr,
        FocusCustomizationFieldRegistry.focusContentExpr,
    )
    override val customizationContributor = ImageTextWithProgressCustomization

    override fun render(context: Context, extras: Bundle, ctx: RendererContext) {
        try {
            val vm = ctx.vm
            val islandIconKey = "key_${vm.templateId}_island"
            val profileKey = "key_${vm.templateId}_profile"
            val payload = ctx.payload as? ImageTextWithProgressPayload
            val profileIcon = payload?.picProfileIcon ?: vm.focusIcon
            val appPkg = payload?.appIconPkg
            val titleColor = payload?.chatTitleColor ?: "#000000"
            val titleColorDark = payload?.chatTitleColorDark ?: "#FFFFFF"
            val contentColor = payload?.chatContentColor ?: "#666666"
            val contentColorDark = payload?.chatContentColorDark ?: "#B3B3B3"
            val progressBarColor = payload?.progressBarColor ?: "#34C759"
            val progressBarColorEnd = payload?.progressBarColorEnd ?: "#30B0C7"

            val builder = io.github.d4viddf.hyperisland_kit.HyperIslandNotification.Builder(
                context,
                vm.templateId,
                vm.focusTitle,
            )

            builder.addPicture(HyperPicture(islandIconKey, vm.islandIcon))
            builder.addPicture(HyperPicture(profileKey, profileIcon))
            builder.setChatInfo(
                title = vm.focusTitle,
                content = vm.focusContent,
                pictureKey = profileKey,
                appPkg = appPkg,
                titleColor = titleColor,
                titleColorDark = titleColorDark,
                contentColor = contentColor,
                contentColorDark = contentColorDark,
            )

            builder.setIslandFirstFloat(vm.firstFloat)
            builder.setEnableFloat(vm.enableFloat)
            builder.setShowNotification(vm.showNotification)
            builder.setIslandConfig(timeout = vm.timeoutSecs)

            val progress = vm.circularProgress
            if (progress != null) {
                builder.setProgressBar(
                    progress = progress.coerceIn(0, 100),
                    color = progressBarColor,
                    colorEnd = progressBarColorEnd,
                    picForwardKey = "",
                    picEndKey = "",
                )
            }

            builder.setSmallIsland(islandIconKey)

            val leftSide = if (!vm.showIslandIcon) {
                io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft(
                    type = 1,
                    textInfo = io.github.d4viddf.hyperisland_kit.models.TextInfo(
                        title = vm.leftTitle,
                        narrowFont = vm.showLeftNarrowFont,
                        showHighlightColor = vm.showLeftHighlightColor,
                    ),
                )
            } else {
                io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft(
                    type = 1,
                    picInfo = io.github.d4viddf.hyperisland_kit.models.PicInfo(type = 1, pic = islandIconKey),
                    textInfo = io.github.d4viddf.hyperisland_kit.models.TextInfo(
                        title = vm.leftTitle,
                        narrowFont = vm.showLeftNarrowFont,
                        showHighlightColor = vm.showLeftHighlightColor,
                    ),
                )
            }
            builder.setBigIslandInfo(
                left = leftSide,
                right = io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight(
                    type = 2,
                    textInfo = io.github.d4viddf.hyperisland_kit.models.TextInfo(
                        title = vm.rightTitle,
                        narrowFont = vm.showRightNarrowFont,
                        showHighlightColor = vm.showRightHighlightColor,
                    ),
                ),
            )

            val resourceBundle = builder.buildResourceBundle()
            extras.putAll(resourceBundle)
            flattenActionsToExtras(resourceBundle, extras)

            var jsonParam = fixTextButtonJson(builder.buildJsonParam())
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

            Log.d("HyperIsland", "HyperIsland[$RENDERER_ID]: rendered template=${vm.templateId}")
        } catch (e: Exception) {
            Log.d("HyperIsland", "HyperIsland[$RENDERER_ID]: render error: ${e.message}")
        }
    }
}
