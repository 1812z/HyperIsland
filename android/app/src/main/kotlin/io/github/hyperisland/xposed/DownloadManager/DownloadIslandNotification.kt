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
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.InProcessController
import io.github.hyperisland.xposed.moduleContext
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.CircularProgressInfo
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.ProgressTextInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo
import io.github.hyperisland.xposed.renderer.ImageTextWithButtonsWrapRenderer
import io.github.hyperisland.xposed.renderer.fixTextButtonJson
import io.github.hyperisland.xposed.renderer.flattenActionsToExtras
import io.github.hyperisland.xposed.renderer.wrapLongTextJson

/**
 * 下载灵动岛通知构建器。
 * 专为 MIUI DownloadManager 系统下载设计，按钮硬编码暂停/恢复/取消，
 * 通过 [InProcessController] 直接操作下载任务。
 *
 * 注意：此构建器由 DownloadHook 直接调用，不经过模板注册表，
 * 因此不实现 [IslandTemplate] 接口，保持独立的参数入口。
 */
object DownloadIslandNotification {

    private enum class IconType { DOWNLOADING }

    /**
     * 在下载进程 (com.android.providers.downloads / com.xiaomi.android.app.downloadmanager)
     * 的包加载阶段调用，确保 [ConfigManager] 在任何通知到来前同步初始化完毕。
     */
    fun init(module: XposedModule) {
        ConfigManager.init(module)
    }

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
        channelId: String = "",
    ) {
        try {
            val renderer = loadRendererSetting(context, packageName, channelId)
            val isComplete  = progress >= 100
            val isMultiFile = Regex("""\d+个文件""").containsMatchIn(title + text + fileName)
            val combined    = title + text
            val isWaiting   = !isComplete &&
                              (combined.contains("等待") || combined.contains("准备中") ||
                               combined.contains("队列") || combined.contains("pending", ignoreCase = true) ||
                               combined.contains("queued", ignoreCase = true))
            val hasValidProgress = progress in 0..100
            val safeProgress = progress.coerceIn(0, 100)
            val shouldShowProgress = !isComplete && !isWaiting && !isPaused && hasValidProgress

            val mc = context.moduleContext()
            val displayTitle = when {
                isComplete        -> mc.getString(R.string.island_download_complete)
                isPaused          -> mc.getString(R.string.island_download_paused)
                isWaiting         -> mc.getString(R.string.island_download_waiting)
                hasValidProgress  -> mc.getString(R.string.island_downloading_progress, safeProgress)
                else              -> mc.getString(R.string.island_downloading)
            }
            val displayContent   = fileName.ifEmpty { text }
            val islandStateTitle = when {
                isComplete -> mc.getString(R.string.island_download_complete)
                isPaused   -> mc.getString(R.string.island_download_paused)
                isWaiting  -> mc.getString(R.string.island_download_waiting)
                else       -> mc.getString(R.string.island_state_downloading)
            }

            val tintColor = when {
                isComplete            -> 0xFF4CAF50.toInt()
                isPaused || isWaiting -> 0xFFFF9800.toInt()
                else                  -> 0xFF2196F3.toInt()
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
            val cancelLabel = if (isMultiFile) mc.getString(R.string.island_action_cancel_all)
                              else             mc.getString(R.string.island_action_cancel)

            val builder = HyperIslandNotification.Builder(context, "download_island", fileName)

            builder.addPicture(HyperPicture("key_download_icon", downloadIcon))

            builder.setIconTextInfo(
                picKey  = "key_download_icon",
                title   = displayTitle,
                content = displayContent,
            )

            builder.setIslandFirstFloat(false)
            builder.setEnableFloat(false)

            if (shouldShowProgress) {
                builder.setSmallIslandCircularProgress("key_download_icon", safeProgress)
            } else {
                builder.setSmallIsland("key_download_icon")
            }

            if (shouldShowProgress) {
                builder.setBigIslandInfo(
                    left = ImageTextInfoLeft(
                        type     = 1,
                        picInfo  = PicInfo(type = 1, pic = "key_download_icon"),
                        textInfo = TextInfo(title = islandStateTitle),
                    ),
                    progressText = ProgressTextInfo(
                        progressInfo = CircularProgressInfo(progress = safeProgress),
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

            if (!isComplete && !isWaiting) {
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
            flattenActionsToExtras(resourceBundle, extras)

            val aodTitle = when {
                isComplete       -> mc.getString(R.string.island_download_complete)
                isPaused         -> mc.getString(R.string.island_download_paused)
                isWaiting        -> mc.getString(R.string.island_download_waiting)
                hasValidProgress -> mc.getString(R.string.island_aod_downloading_progress, safeProgress)
                else             -> mc.getString(R.string.island_downloading)
            }
            val finalJson = try {
                var json = fixTextButtonJson(builder.buildJsonParam())
                if (renderer == ImageTextWithButtonsWrapRenderer.RENDERER_ID) json = wrapLongTextJson(json)
                val obj = org.json.JSONObject(json)
                val pv2 = obj.optJSONObject("param_v2") ?: org.json.JSONObject()
                pv2.put("aodTitle", aodTitle)
                pv2.put("updatable", !isComplete)
                obj.put("param_v2", pv2)
                obj.toString()
            } catch (_: Exception) { builder.buildJsonParam() }
            extras.putString("miui.focus.param", finalJson)

            val stateTag = when {
                isComplete       -> "done"
                isPaused         -> "paused"
                isWaiting        -> "waiting"
                hasValidProgress -> "${safeProgress}%"
                else             -> "unknown"
            }
            Log.d("HyperIsland", "HyperIsland[Download]: Island injected — $fileName ($stateTag)")

        } catch (e: Exception) {
            Log.d("HyperIsland", "HyperIsland[Download]: Island injection error: ${e.message}")
        }
    }

    private fun loadRendererSetting(context: Context, packageName: String, channelId: String): String =
        ConfigManager.getString("pref_channel_renderer_${packageName}_$channelId")
            .takeIf { it.isNotBlank() } ?: "image_text_with_buttons_4"

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
