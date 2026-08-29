package io.github.hyperisland.compose.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.hyperisland.R
import io.github.hyperisland.utils.getAppIcon
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import io.github.hyperisland.xposed.islanddispatch.definition.IslandRequest

internal object TestNotificationService {
    fun sendDefault(context: Context) {
        val request = IslandRequest(
            title = context.getString(R.string.island_welcome_title),
            content = "HyperIsland",
            icon = context.packageManager.getAppIcon(context.packageName),
            firstFloat = false,
            highlightColor = "#E040FB",
            showNotification = true,
            islandOuterGlow = true,
            outerGlow = true,
        )
        sendWithReset(context, request)
    }

    fun sendCustom(
        context: Context,
        title: String,
        content: String,
        clearPrevious: Boolean,
        enableFloat: Boolean,
    ) {
        IslandDispatcher.sendBroadcast(
            context,
            IslandRequest(
                title = title.ifEmpty { context.getString(R.string.island_welcome_title) },
                content = content.ifEmpty { "HyperIsland" },
                icon = context.packageManager.getAppIcon(context.packageName),
                firstFloat = false,
                enableFloat = enableFloat,
                clearBeforePost = clearPrevious,
                highlightColor = "#E040FB",
                showNotification = true,
            ),
        )
    }

    private fun sendWithReset(context: Context, request: IslandRequest) {
        val appContext = context.applicationContext
        val cancelIntent = Intent(IslandDispatcher.ACTION_CANCEL).apply {
            putExtra(IslandDispatcher.EXTRA_NOTIF_ID, request.notifId)
        }
        appContext.sendOrderedBroadcast(
            cancelIntent,
            IslandDispatcher.PERM,
            object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    IslandDispatcher.sendBroadcast(appContext, request)
                }
            },
            null,
            0,
            null,
            null,
        )
    }
}
