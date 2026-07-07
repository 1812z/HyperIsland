package io.github.hyperisland.xposed.commandtoken

import android.app.PendingIntent
import android.content.Context
import io.github.hyperisland.utils.getAppIcon
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import io.github.hyperisland.xposed.islanddispatch.IslandRequest
import io.github.hyperisland.xposed.log
import io.github.hyperisland.xposed.logError
import io.github.hyperisland.xposed.logWarn
import io.github.hyperisland.xposed.utils.toRounded

object CommandTokenDispatcher {

    fun dispatch(context: Context, match: TokenMatchResult) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(match.targetPackage)
            if (launchIntent == null) {
                logWarn("CommandToken: target package not installed: ${match.targetPackage}")
                return
            }

            val icon = runCatching {
                context.packageManager.getAppIcon(match.targetPackage)?.toRounded(context)
            }.getOrNull()

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            IslandDispatcher.post(
                context,
                IslandRequest(
                    title = match.displayName,
                    content = match.prompt,
                    icon = icon,
                    contentIntent = pendingIntent,
                    timeoutSecs = CommandTokenConfig.timeoutSeconds(),
                    firstFloat = true,
                    enableFloat = true,
                    showNotification = false,
                    preserveStatusBarSmallIcon = false,
                    sourcePackage = match.targetPackage,
                    sourceChannelId = "command_token",
                    showIslandIcon = true,
                    isOngoing = false,
                ),
            )
            log("CommandToken: dispatched token for ${match.targetPackage}")
        } catch (e: Throwable) {
            logError("CommandToken: dispatch failed: ${e.message}")
        }
    }
}
