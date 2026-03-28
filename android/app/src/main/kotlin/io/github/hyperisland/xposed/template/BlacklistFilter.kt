package io.github.hyperisland.xposed

import android.app.ActivityManager
import android.content.Context
import android.util.Log

/**
 * 应用黑名单过滤器。
 * 在通知进入模板前检查前台应用是否在黑名单中：
 *  - 命中 → 返回禁用浮动的 [NotifData] 副本（岛正常展示，不自动展开）
 *  - 未命中 → 返回原始 [NotifData]
 */
object BlacklistFilter {

    fun applyTo(context: Context, data: NotifData): NotifData? {
        val blacklistStr = ConfigManager.getString("pref_app_blacklist")

        if (blacklistStr.isEmpty()) return data

        val foregroundApp = try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            am.getRunningTasks(1).firstOrNull()?.topActivity?.packageName ?: ""
        } catch (e: Exception) {
            Log.d("HyperIsland", "HyperIsland[Blacklist]: getRunningTasks failed: ${e.message}")
            ""
        }

        val isBlacklisted = foregroundApp.isNotEmpty() && blacklistStr.split(",").contains(foregroundApp)
        Log.d("HyperIsland", "HyperIsland[Blacklist]: foreground=$foregroundApp, isBlacklisted=$isBlacklisted")

        if (!isBlacklisted) return data

        Log.d("HyperIsland", "HyperIsland[Blacklist]: $foregroundApp skipped float")
        return data.copy(firstFloat = "off", enableFloatMode = "off")
    }
}
