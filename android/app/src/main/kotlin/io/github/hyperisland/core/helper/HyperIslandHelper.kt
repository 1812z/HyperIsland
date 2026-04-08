package io.github.hyperisland.core.helper

import android.content.Context
import android.util.Log
import io.github.hyperisland.utils.getAppIcon
import io.github.hyperisland.xposed.IslandDispatcher
import io.github.hyperisland.xposed.IslandRequest

object HyperIslandHelper {
    private const val TAG = "HyperIslandHelper"

    fun sendIslandNotification(
        context: Context,
        title: String,
        content: String,
    ) {
        try {
            val icon = context.packageManager.getAppIcon(context.packageName)
            IslandDispatcher.sendBroadcast(
                context,
                IslandRequest(
                    title = title,
                    content = content,
                    icon = icon,
                ),
            )
            Log.d(TAG, "Island request sent: $title | $content")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send island request", e)
        }
    }
}
