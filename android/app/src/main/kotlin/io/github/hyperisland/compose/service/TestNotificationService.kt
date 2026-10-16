package io.github.hyperisland.compose.service

import android.app.BroadcastOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.hyperisland.R
import io.github.hyperisland.utils.getAppIcon
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import io.github.hyperisland.xposed.islanddispatch.definition.IslandRequest

internal object TestNotificationService {
    fun sendWelcome(context: Context) {
        sendWithReset(
            context,
            IslandRequest(
                title = context.getString(R.string.island_welcome_title),
                content = "HyperIsland",
                icon = context.packageManager.getAppIcon(context.packageName),
                firstFloat = false,
                enableFloat = false,
                highlightColor = "#E040FB",
                showNotification = false,
                islandOuterGlow = true,
            ),
        )
    }

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
        expandFirst: Boolean = false,
    ) {
        IslandDispatcher.sendBroadcast(
            context,
            IslandRequest(
                title = title.ifEmpty { context.getString(R.string.island_welcome_title) },
                content = content.ifEmpty { "HyperIsland" },
                icon = context.packageManager.getAppIcon(context.packageName),
                firstFloat = expandFirst,
                enableFloat = enableFloat,
                clearBeforePost = clearPrevious,
                highlightColor = "#E040FB",
                showNotification = true,
            ),
        )
    }

    private fun sendWithReset(context: Context, request: IslandRequest) {
        val appContext = context.applicationContext
        // 必须先算出这次代发实际落到的通知槽位再取消：可见代发与「仅上岛」的代发槽位不同，
        // 用 request.notifId 会清错槽位（见 IslandDispatcher.resolveNotificationId）。
        val cancelIntent = Intent(IslandDispatcher.ACTION_CANCEL).apply {
            setPackage("com.android.systemui")
            putExtra(IslandDispatcher.EXTRA_NOTIF_ID, IslandDispatcher.resolveNotificationId(request))
        }
        val postAfterCancel = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                IslandDispatcher.sendBroadcast(appContext, request)
            }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val options = BroadcastOptions.makeBasic()
                .setShareIdentityEnabled(true)
                .toBundle()
            appContext.sendOrderedBroadcast(
                cancelIntent,
                null,
                options,
                postAfterCancel,
                null,
                0,
                null,
                null,
            )
        } else {
            appContext.sendOrderedBroadcast(
                cancelIntent,
                IslandDispatcher.PERM,
                postAfterCancel,
                null,
                0,
                null,
                null,
            )
        }
    }
}
