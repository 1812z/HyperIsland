package com.example.hyperisland.xposed

import android.app.Notification
import android.graphics.drawable.Icon
import android.os.Bundle
import android.content.Context
import com.xzakota.hyper.notification.focus.FocusNotification
import de.robv.android.xposed.XposedBridge

/**
 * 下载灵动岛通知构建器
 * 使用 FocusNotification.buildV3 DSL 构建小米超级岛通知
 */
object DownloadIslandNotification {

    fun inject(
        context: Context,
        extras: Bundle,
        title: String,
        text: String,
        progress: Int,
        appName: String,
        fileName: String,
        downloadId: Long,
        packageName: String
    ) {
        try {
            val isComplete = progress >= 100
            val displayTitle = if (progress in 0..99) "$fileName%下载中 $progress%" else title
            val displayContent = if (isComplete) "下载完成" else text.ifEmpty { fileName }

            val downloadIconRes = if (isComplete) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_sys_download
            val tintColor = if (isComplete) 0xFF4CAF50.toInt() else 0xFF2196F3.toInt()
            val downloadIcon = Icon.createWithResource(context, downloadIconRes).apply { setTint(tintColor) }

            val pausePendingIntent  = InProcessController.pauseIntent(context, downloadId)
            val cancelPendingIntent = InProcessController.cancelIntent(context, downloadId)

            val islandExtras = FocusNotification.buildV3 {
                val downloadIconKey = createPicture("key_download_icon", downloadIcon)

                islandFirstFloat = false
                enableFloat = false
                updatable = true
                //ticker = displayTitle

                // 小米岛 摘要态
                island {
                    islandProperty = 1
                    bigIslandArea {
                        imageTextInfoLeft {
                            type = 1
                            picInfo {
                                type = 1
                                pic = downloadIconKey
                            }
                            textInfo {
                                this.title = if (isComplete) "下载完成" else "下载中"
                            }
                        }
                        progressTextInfo {
                            textInfo {
                                this.title = fileName
                                narrowFont = true
                            }
                            progressInfo {
                                this.progress = progress
                            }
                        }
                    }
                    smallIslandArea {
                        picInfo {
                            type = 1
                            pic = downloadIconKey
                        }
                    }
                }

                // 焦点通知 展开态
                iconTextInfo {
                    this.title = displayTitle
                    content = displayContent
                    animIconInfo {
                        type = 0
                        src = downloadIconKey
                    }
                }


                // 操作按钮（下载完成时不显示按钮）
                if (!isComplete) {
                    textButton {
                        addActionInfo {
                            val pauseAction = Notification.Action.Builder(
                                Icon.createWithResource(context, android.R.drawable.ic_media_pause),
                                "暂停",
                                pausePendingIntent
                            ).build()
                            action = createAction("action_pause", pauseAction)
                            actionTitle = "暂停"
                        }
                        addActionInfo {
                            val cancelAction = Notification.Action.Builder(
                                Icon.createWithResource(context, android.R.drawable.ic_delete),
                                "取消",
                                cancelPendingIntent
                            ).build()
                            action = createAction("action_cancel", cancelAction)
                            actionTitle = "取消"
                        }
                    }
                }
            }

            extras.putAll(islandExtras)

            // AOD 息屏显示：合并进已有的 miui.focus.param
            val aodTitle = if (isComplete) "下载完成" else "下载中 $progress%"
            val existingParam = extras.getString("miui.focus.param")
            if (existingParam != null) {
                try {
                    val json = org.json.JSONObject(existingParam)
                    val pv2 = json.optJSONObject("param_v2") ?: org.json.JSONObject()
                    pv2.put("aodTitle", aodTitle)
                    json.put("param_v2", pv2)
                    extras.putString("miui.focus.param", json.toString())
                } catch (_: Exception) {}
            }

            XposedBridge.log("HyperIsland: Island injected — $fileName ($progress%)")

        } catch (e: Exception) {
            XposedBridge.log("HyperIsland: Island injection error: ${e.message}")
        }
    }

}
