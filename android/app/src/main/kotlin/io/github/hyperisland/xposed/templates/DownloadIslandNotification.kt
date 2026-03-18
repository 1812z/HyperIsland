package io.github.hyperisland.xposed.templates

import android.app.Notification
import android.content.Context
import android.os.Build
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.os.Bundle
import io.github.hyperisland.R
import io.github.hyperisland.xposed.InProcessController
import io.github.hyperisland.xposed.moduleContext
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

/**
 * 下载灵动岛通知构建器。
 * 专为 MIUI DownloadManager 系统下载设计，按钮硬编码暂停/恢复/取消，
 * 通过 [InProcessController] 直接操作下载任务。
 */
object DownloadIslandNotification {

    private enum class IconType { DOWNLOADING }

    fun inject(
        context: Context,
        extras: Bundle,
        title: String,
        text: String,
        progress: Int,
        appName: String,
        fileName: String,
        downloadId: Long,
        packageName: String,
        isPaused: Boolean = false,
        appIcon: Icon? = null,
    ) {
        try {
            val isComplete  = progress >= 100
            val isMultiFile = Regex("""\d+个文件""").containsMatchIn(title + text + fileName)
            val combined    = title + text
            val isWaiting   = !isComplete &&
                              (combined.contains("等待") || combined.contains("准备中") ||
                               combined.contains("队列") || combined.contains("pending", ignoreCase = true) ||
                               combined.contains("queued", ignoreCase = true))

            val mc = context.moduleContext()
            val displayTitle = when {
                isComplete -> mc.getString(R.string.island_download_complete)
                isPaused   -> mc.getString(R.string.island_download_paused)
                isWaiting  -> mc.getString(R.string.island_download_waiting)
                else       -> if (progress >= 0) mc.getString(R.string.island_downloading_progress, progress) else mc.getString(R.string.island_downloading)
            }
            val displayContent   = fileName.ifEmpty { text }
            val islandStateTitle = when {
                isComplete -> mc.getString(R.string.island_download_complete)
                isPaused   -> mc.getString(R.string.island_download_paused)
                isWaiting  -> mc.getString(R.string.island_download_waiting)
                else       -> mc.getString(R.string.island_state_downloading)
            }

            val tintColor = when {
                isComplete            -> 0xFF4CAF50.toInt()  // 绿
                isPaused || isWaiting -> 0xFFFF9800.toInt()  // 橙
                else                  -> 0xFF2196F3.toInt()  // 蓝
            }
            val fallbackIcon = createDownloadIcon(context, tintColor, IconType.DOWNLOADING)
            val downloadIcon = appIcon ?: fallbackIcon

            val primaryIntent = when {
                isPaused && isMultiFile -> InProcessController.resumeAllIntent(context)
                isPaused               -> InProcessController.resumeIntent(context, downloadId)
                isMultiFile            -> InProcessController.pauseAllIntent(context)
                else                   -> InProcessController.pauseIntent(context, downloadId)
            }
            val cancelPendingIntent = if (isMultiFile) InProcessController.cancelAllIntent(context)
                                      else             InProcessController.cancelIntent(context, downloadId)
            val primaryLabel = when {
                isPaused && isMultiFile -> mc.getString(R.string.island_action_resume_all)
                isPaused               -> mc.getString(R.string.island_action_resume)
                isMultiFile            -> mc.getString(R.string.island_action_pause_all)
                else                   -> mc.getString(R.string.island_action_pause)
            }
            val cancelLabel = if (isMultiFile) mc.getString(R.string.island_action_cancel_all) else mc.getString(R.string.island_action_cancel)

            val builder = HyperIslandNotification.Builder(context, "download_island", fileName)

            builder.addPicture(HyperPicture("key_download_icon", downloadIcon))

            builder.setIconTextInfo(
                picKey  = "key_download_icon",
                title   = displayTitle,
                content = displayContent,
            )

            builder.setIslandFirstFloat(false)
            builder.setEnableFloat(false)

            // 小岛：下载中时带环形进度，其他状态仅图标
            if (!isComplete && !isWaiting && !isPaused) {
                builder.setSmallIslandCircularProgress("key_download_icon", progress)
            } else {
                builder.setSmallIsland("key_download_icon")
            }

            // 大岛：下载中时左侧状态+右侧环形进度，其他状态左侧状态+右侧文本
            if (!isComplete && !isWaiting && !isPaused) {
                builder.setBigIslandInfo(
                    left = ImageTextInfoLeft(
                        type     = 1,
                        picInfo  = PicInfo(type = 1, pic = "key_download_icon"),
                        textInfo = TextInfo(title = islandStateTitle),
                    ),
                    progressText = ProgressTextInfo(
                        progressInfo = CircularProgressInfo(progress = progress),
                        textInfo     = TextInfo(title = fileName, narrowFont = true),
                    ),
                )
            } else {
                builder.setBigIslandInfo(
                    left = ImageTextInfoLeft(
                        type     = 1,
                        picInfo  = PicInfo(type = 1, pic = "key_download_icon"),
                        textInfo = TextInfo(title = islandStateTitle),
                    ),
                    right = ImageTextInfoRight(
                        type     = 2,
                        textInfo = TextInfo(title = fileName, narrowFont = true),
                    ),
                )
            }

            // 按钮：下载中/暂停时显示暂停/恢复 + 取消
            if (!isComplete && !isWaiting) {
                // 文本模式（无图标），避免 TextButtonInfo.actionIcon 指向不存在的 pic 键
                val primaryAction = HyperAction(
                    key              = "action_primary",
                    title            = primaryLabel,
                    pendingIntent    = primaryIntent,
                    actionIntentType = 2,
                )
                val cancelAction = HyperAction(
                    key              = "action_cancel",
                    title            = cancelLabel,
                    pendingIntent    = cancelPendingIntent,
                    actionIntentType = 2,
                )
                builder.addHiddenAction(primaryAction)
                builder.addHiddenAction(cancelAction)
                builder.setTextButtons(primaryAction, cancelAction)
            }

            val resourceBundle = builder.buildResourceBundle()
            extras.putAll(resourceBundle)
            // HyperOS 从 extras 顶层查找 action，将嵌套 bundle 展开
            flattenActionsToExtras(resourceBundle, extras)

            // AOD 息屏显示 + updatable
            val aodTitle = when {
                isComplete -> mc.getString(R.string.island_download_complete)
                isPaused   -> mc.getString(R.string.island_aod_paused_progress, progress)
                isWaiting  -> mc.getString(R.string.island_download_waiting)
                else       -> if (progress >= 0) mc.getString(R.string.island_aod_downloading_progress, progress) else mc.getString(R.string.island_downloading)
            }
            // 修正 textButton 字段名 + 注入 aodTitle/updatable
            val finalJson = try {
                val json = org.json.JSONObject(fixTextButtonJson(builder.buildJsonParam()))
                val pv2  = json.optJSONObject("param_v2") ?: org.json.JSONObject()
                pv2.put("aodTitle", aodTitle)
                pv2.put("updatable", !isComplete)
                json.put("param_v2", pv2)
                json.toString()
            } catch (_: Exception) { builder.buildJsonParam() }
            extras.putString("miui.focus.param", finalJson)

            val stateTag = when {
                isComplete -> "done"
                isPaused   -> "paused"
                isWaiting  -> "waiting"
                else       -> "${progress}%"
            }
            XposedBridge.log("HyperIsland[Download]: Island injected — $fileName ($stateTag)")

        } catch (e: Exception) {
            XposedBridge.log("HyperIsland[Download]: Island injection error: ${e.message}")
        }
    }

    /**
     * 将 textButton 数组里新库输出的 "actionIntent"+"actionIntentType"
     * 替换为 HyperOS V3 协议所需的 "action" 字段，否则按钮点击无响应。
     */
    private fun fixTextButtonJson(jsonParam: String): String {
        return try {
            val json = org.json.JSONObject(jsonParam)
            val pv2  = json.optJSONObject("param_v2") ?: return jsonParam
            val btns = pv2.optJSONArray("textButton") ?: return jsonParam
            for (i in 0 until btns.length()) {
                val btn = btns.getJSONObject(i)
                val key = btn.optString("actionIntent").takeIf { it.isNotEmpty() } ?: continue
                btn.put("action", key)
                btn.remove("actionIntent")
                btn.remove("actionIntentType")
            }
            json.toString()
        } catch (_: Exception) { jsonParam }
    }

    /** 将 buildResourceBundle() 里嵌套的 "miui.focus.actions" 展开到 extras 顶层 */
    private fun flattenActionsToExtras(resourceBundle: Bundle, extras: Bundle) {
        val nested = resourceBundle.getBundle("miui.focus.actions") ?: return
        for (key in nested.keySet()) {
            val action: Notification.Action? = if (Build.VERSION.SDK_INT >= 33)
                nested.getParcelable(key, Notification.Action::class.java)
            else
                @Suppress("DEPRECATION") nested.getParcelable(key)
            if (action != null) extras.putParcelable(key, action)
        }
    }

    private fun createDownloadIcon(context: Context, color: Int, iconType: IconType): Icon {
        val density = context.resources.displayMetrics.density
        val size    = (48 * density + 0.5f).toInt()
        val bmp     = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas  = Canvas(bmp)
        val paint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style      = Paint.Style.FILL
        }
        val s    = size / 24f
        val path = Path()
        when (iconType) {
            IconType.DOWNLOADING -> {
                path.moveTo(19 * s, 9 * s)
                path.lineTo(15 * s, 9 * s)
                path.lineTo(15 * s, 3 * s)
                path.lineTo(9  * s, 3 * s)
                path.lineTo(9  * s, 9 * s)
                path.lineTo(5  * s, 9 * s)
                path.lineTo(12 * s, 16 * s)
                path.close()
                canvas.drawPath(path, paint)
                val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color  = color
                    style       = Paint.Style.STROKE
                    strokeWidth = 2 * s
                    strokeCap   = Paint.Cap.ROUND
                }
                val r  = 14f * s
                val cx = 12f * s
                val cy = (19f - 14f * Math.cos(Math.toRadians(30.0)).toFloat()) * s
                canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 60f, 60f, false, arcPaint)
            }
        }
        return Icon.createWithBitmap(bmp)
    }
}
