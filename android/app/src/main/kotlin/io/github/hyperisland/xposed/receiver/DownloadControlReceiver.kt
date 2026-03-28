package io.github.hyperisland.xposed

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.Log

/**
 * 下载控制接收器
 * 用于处理灵动岛中的暂停和取消按钮点击事件
 */
class DownloadControlReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "HyperIsland_Control"
        const val ACTION_CONTROL = "io.github.hyperisland.DOWNLOAD_CONTROL"
        const val EXTRA_ACTION = "action"
        const val EXTRA_DOWNLOAD_ID = "downloadId"
        const val EXTRA_FILE_NAME = "fileName"
        const val EXTRA_PACKAGE_NAME = "packageName"

        const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"
        const val ACTION_CANCEL = "cancel"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "未知文件"
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""

        Log.d("HyperIsland", "╔════════════════════════════════════════╗")
        Log.d("HyperIsland", "║   🎮 DOWNLOAD CONTROL ACTION RECEIVED  ║")
        Log.d("HyperIsland", "╠════════════════════════════════════════╣")
        Log.d("HyperIsland", "║ Action: $action")
        Log.d("HyperIsland", "║ DownloadID: $downloadId")
        Log.d("HyperIsland", "║ File: $fileName")
        Log.d("HyperIsland", "║ Package: $packageName")
        Log.d("HyperIsland", "╚════════════════════════════════════════╝")

        when (action) {
            ACTION_PAUSE -> {
                handlePause(context, downloadId, fileName, packageName)
            }
            ACTION_RESUME -> {
                handleResume(context, downloadId, fileName, packageName)
            }
            ACTION_CANCEL -> {
                handleCancel(context, downloadId, fileName, packageName)
            }
        }
    }

    /**
     * 处理暂停下载
     */
    private fun handlePause(context: Context, downloadId: Long, fileName: String, packageName: String) {
        Log.d("HyperIsland", "HyperIsland: 📥 Attempting to pause download: $downloadId")

        try {
            // 方法1: 使用反射调用 DownloadManager 的 pause 方法
            if (downloadId > 0) {
                pauseDownloadViaReflection(context, downloadId)
            } else {
                // 方法2: 通过 ContentProvider 更新下载状态
                pauseDownloadViaProvider(context, packageName, fileName)
            }

            Log.d("HyperIsland", "HyperIsland: ✅ Pause command sent for: $fileName")

            // 可选：发送通知反馈
            // showFeedbackNotification(context, "已暂停", fileName)

        } catch (e: Exception) {
            Log.d("HyperIsland", "HyperIsland: ❌ Error pausing download: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 处理恢复下载
     */
    private fun handleResume(context: Context, downloadId: Long, fileName: String, packageName: String) {
        Log.d("HyperIsland", "HyperIsland: ▶️ Attempting to resume download: $downloadId")

        try {
            // 方法1: 使用反射调用 DownloadManager 的 resume 方法
            if (downloadId > 0) {
                resumeDownloadViaReflection(context, downloadId)
            } else {
                // 方法2: 通过 ContentProvider 更新下载状态
                resumeDownloadViaProvider(context, packageName, fileName)
            }

            Log.d("HyperIsland", "HyperIsland: ✅ Resume command sent for: $fileName")

        } catch (e: Exception) {
            Log.d("HyperIsland", "HyperIsland: ❌ Error resuming download: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 处理取消下载
     */
    private fun handleCancel(context: Context, downloadId: Long, fileName: String, packageName: String) {
        Log.d("HyperIsland", "HyperIsland: ❌ Attempting to cancel download: $downloadId")

        try {
            // 方法1: 使用 DownloadManager 的 remove 方法
            if (downloadId > 0) {
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                downloadManager?.remove(downloadId)
                Log.d("HyperIsland", "HyperIsland: ✅ Download cancelled via DownloadManager: $downloadId")
            } else {
                // 方法2: 通过 ContentProvider 删除下载
                cancelDownloadViaProvider(context, packageName, fileName)
            }

            Log.d("HyperIsland", "HyperIsland: ✅ Cancel command sent for: $fileName")

            // 可选：移除通知
            // removeNotification(context, packageName)

        } catch (e: Exception) {
            Log.d("HyperIsland", "HyperIsland: ❌ Error cancelling download: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 通过反射暂停下载
     * Hook DownloadProvider 的 pauseDownload 方法
     */
    private fun pauseDownloadViaReflection(context: Context, downloadId: Long) {
        try {
            // 尝试获取 DownloadProvider 并调用暂停方法
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager

            // 反射调用隐藏的 pause 方法
            val pauseMethod = downloadManager?.javaClass?.getDeclaredMethod("pause", Long::class.java)
            pauseMethod?.isAccessible = true
            pauseMethod?.invoke(downloadManager, downloadId)

            Log.d("HyperIsland", "HyperIsland: Paused via reflection: $downloadId")
        } catch (e: Exception) {
            Log.d("HyperIsland", "HyperIsland: Reflection pause failed: ${e.message}")
            // 尝试通过 ContentProvider 暂停
            pauseDownloadViaProvider(context, "", downloadId.toString())
        }
    }

    /**
     * 通过反射恢复下载
     */
    private fun resumeDownloadViaReflection(context: Context, downloadId: Long) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager

            // 反射调用隐藏的 resume 方法
            val resumeMethod = downloadManager?.javaClass?.getDeclaredMethod("resume", Long::class.java)
            resumeMethod?.isAccessible = true
            resumeMethod?.invoke(downloadManager, downloadId)

            Log.d("HyperIsland", "HyperIsland: Resumed via reflection: $downloadId")
        } catch (e: Exception) {
            Log.d("HyperIsland", "HyperIsland: Reflection resume failed: ${e.message}")
            // 尝试通过 ContentProvider 恢复
            resumeDownloadViaProvider(context, "", downloadId.toString())
        }
    }

    /**
     * 通过 ContentProvider 暂停下载
     * 更新下载状态为 STATUS_PAUSED
     */
    private fun pauseDownloadViaProvider(context: Context, packageName: String, identifier: String) {
        try {
            // 构建下载 URI
            val downloadsUri = android.net.Uri.parse("content://downloads/my_downloads")

            // 查询下载
            val query = if (identifier.isNotEmpty() && identifier.toLongOrNull() != null) {
                context.contentResolver.query(
                    downloadsUri,
                    arrayOf("_id", "status"),
                    "_id = ?",
                    arrayOf(identifier),
                    null
                )
            } else {
                // 按包名和标题查询
                context.contentResolver.query(
                    downloadsUri,
                    arrayOf("_id", "status"),
                    "title LIKE ?",
                    arrayOf("%$identifier%"),
                    null
                )
            }

            query?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex("_id")
                    val id = cursor.getLong(idIndex)

                    // 更新状态为暂停 (STATUS_PAUSED = 194, CONTROL_PAUSED = 1)
                    val values = android.content.ContentValues().apply {
                        put("status", DownloadManager.STATUS_PAUSED)
                        put("control", 1) // CONTROL_PAUSED
                    }

                    val updated = context.contentResolver.update(
                        downloadsUri.buildUpon().appendPath(id.toString()).build(),
                        values,
                        null,
                        null
                    )

                    Log.d("HyperIsland", "HyperIsland: Paused via provider: $id (updated: $updated)")
                }
            }

        } catch (e: Exception) {
            Log.d("HyperIsland", "HyperIsland: Provider pause failed: ${e.message}")
        }
    }

    /**
     * 通过 ContentProvider 恢复下载
     */
    private fun resumeDownloadViaProvider(context: Context, packageName: String, identifier: String) {
        try {
            val downloadsUri = android.net.Uri.parse("content://downloads/my_downloads")

            val query = if (identifier.isNotEmpty() && identifier.toLongOrNull() != null) {
                context.contentResolver.query(
                    downloadsUri,
                    arrayOf("_id", "status"),
                    "_id = ?",
                    arrayOf(identifier),
                    null
                )
            } else {
                context.contentResolver.query(
                    downloadsUri,
                    arrayOf("_id", "status"),
                    "title LIKE ?",
                    arrayOf("%$identifier%"),
                    null
                )
            }

            query?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIndex = cursor.getColumnIndex("_id")
                    val id = cursor.getLong(idIndex)

                    // 更新状态为运行中 (STATUS_RUNNING = 192, CONTROL_RUN = 0)
                    val values = android.content.ContentValues().apply {
                        put("status", DownloadManager.STATUS_RUNNING)
                        put("control", 0) // CONTROL_RUN
                    }

                    val updated = context.contentResolver.update(
                        downloadsUri.buildUpon().appendPath(id.toString()).build(),
                        values,
                        null,
                        null
                    )

                    Log.d("HyperIsland", "HyperIsland: Resumed via provider: $id (updated: $updated)")
                }
            }

        } catch (e: Exception) {
            Log.d("HyperIsland", "HyperIsland: Provider resume failed: ${e.message}")
        }
    }

    /**
     * 通过 ContentProvider 取消下载
     */
    private fun cancelDownloadViaProvider(context: Context, packageName: String, fileName: String) {
        try {
            val downloadsUri = android.net.Uri.parse("content://downloads/my_downloads")

            // 查询要取消的下载
            val query = context.contentResolver.query(
                downloadsUri,
                arrayOf("_id"),
                "title LIKE ?",
                arrayOf("%$fileName%"),
                null
            )

            query?.use { cursor ->
                while (cursor.moveToNext()) {
                    val idIndex = cursor.getColumnIndex("_id")
                    val id = cursor.getLong(idIndex)

                    // 删除下载记录
                    val deleted = context.contentResolver.delete(
                        downloadsUri.buildUpon().appendPath(id.toString()).build(),
                        null,
                        null
                    )

                    Log.d("HyperIsland", "HyperIsland: Cancelled via provider: $id (deleted: $deleted)")
                }
            }

        } catch (e: Exception) {
            Log.d("HyperIsland", "HyperIsland: Provider cancel failed: ${e.message}")
        }
    }

    /**
     * 移除通知
     */
    private fun removeNotification(context: Context, packageName: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager

            // 这里的通知ID可能需要根据实际情况调整
            // 可以通过 ActiveNotifications 查找匹配的通知
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                notificationManager?.cancel(packageName.hashCode())
            }

            Log.d("HyperIsland", "HyperIsland: Notification removed for: $packageName")
        } catch (e: Exception) {
            Log.d("HyperIsland", "HyperIsland: Error removing notification: ${e.message}")
        }
    }
}
