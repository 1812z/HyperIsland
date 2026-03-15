package com.example.hyperisland.xposed.templates

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.os.Bundle
import com.example.hyperisland.xposed.InProcessController
import com.xzakota.hyper.notification.focus.FocusNotification
import de.robv.android.xposed.XposedBridge

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

            val displayTitle = when {
                isComplete -> "下载完成"
                isPaused   -> "已暂停"
                isWaiting  -> "等待中"
                else       -> if (progress >= 0) "下载中 $progress%" else "下载中"
            }
            val displayContent   = fileName.ifEmpty { text }
            val islandStateTitle = when {
                isComplete -> "下载完成"
                isPaused   -> "已暂停"
                isWaiting  -> "等待中"
                else       -> "下载中"
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
                isPaused && isMultiFile -> "全部恢复"
                isPaused               -> "恢复"
                isMultiFile            -> "全部暂停"
                else                   -> "暂停"
            }
            val cancelLabel    = if (isMultiFile) "全部取消" else "取消"
            val primaryIconRes = if (isPaused) android.R.drawable.ic_media_play
                                 else          android.R.drawable.ic_media_pause

            val islandExtras = FocusNotification.buildV3 {
                val downloadIconKey = createPicture("key_download_icon", downloadIcon)

                islandFirstFloat = false
                enableFloat      = false
                updatable        = !isComplete

                island {
                    islandProperty = 1
                    bigIslandArea {
                        imageTextInfoLeft {
                            type = 1
                            picInfo {
                                type = 1
                                pic  = downloadIconKey
                            }
                            textInfo {
                                this.title = islandStateTitle
                            }
                        }
                        progressTextInfo {
                            textInfo {
                                this.title = fileName
                                narrowFont = true
                            }
                            if (!isComplete && !isWaiting) {
                                progressInfo {
                                    this.progress = progress
                                }
                            }
                        }
                    }
                    smallIslandArea {
                        picInfo {
                            type = 1
                            pic  = downloadIconKey
                        }
                    }
                }

                iconTextInfo {
                    this.title = displayTitle
                    content    = displayContent
                    animIconInfo {
                        type = 0
                        src  = downloadIconKey
                    }
                }

                if (!isComplete && !isWaiting) {
                    textButton {
                        addActionInfo {
                            val primaryAction = Notification.Action.Builder(
                                Icon.createWithResource(context, primaryIconRes),
                                primaryLabel,
                                primaryIntent,
                            ).build()
                            action      = createAction("action_primary", primaryAction)
                            actionTitle = primaryLabel
                        }
                        addActionInfo {
                            val cancelAction = Notification.Action.Builder(
                                Icon.createWithResource(context, android.R.drawable.ic_delete),
                                cancelLabel,
                                cancelPendingIntent,
                            ).build()
                            action      = createAction("action_cancel", cancelAction)
                            actionTitle = cancelLabel
                        }
                    }
                }
            }

            extras.putAll(islandExtras)

            // AOD 息屏显示
            val aodTitle = when {
                isComplete -> "下载完成"
                isPaused   -> "已暂停 $progress%"
                isWaiting  -> "等待中"
                else       -> if (progress >= 0) "下载中 $progress%" else "下载中"
            }
            val existingParam = extras.getString("miui.focus.param")
            if (existingParam != null) {
                try {
                    val json = org.json.JSONObject(existingParam)
                    val pv2  = json.optJSONObject("param_v2") ?: org.json.JSONObject()
                    pv2.put("aodTitle", aodTitle)
                    json.put("param_v2", pv2)
                    extras.putString("miui.focus.param", json.toString())
                } catch (_: Exception) {}
            }

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
