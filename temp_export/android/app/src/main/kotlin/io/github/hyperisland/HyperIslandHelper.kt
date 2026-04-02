package io.github.hyperisland

import android.content.Context
import android.util.Log
import io.github.hyperisland.xposed.IslandDispatcher
import io.github.hyperisland.xposed.IslandRequest

/**
 * HyperIsland 应用侧超级岛发送入口。
 *
 * 通过 [IslandDispatcher.sendBroadcast] 将请求发往 SystemUI 进程，
 * 由 SystemUI（system UID）实际发出通知，绕过 HyperOS 对前台应用的岛抑制。
 */
object HyperIslandHelper {
    private const val TAG = "HyperIslandHelper"

    fun sendIslandNotification(
        context: Context,
        title: String,
        content: String,
    ) {
        try {
            // 取应用自身图标（Icon.createWithBitmap，跨进程安全）
            val icon = context.packageManager.getAppIcon(context.packageName)
            IslandDispatcher.sendBroadcast(
                context,
                IslandRequest(
                    title   = title,
                    content = content,
                    icon    = icon,
                )
            )
            Log.d(TAG, "Island request sent: $title | $content")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send island request", e)
        }
    }
}
