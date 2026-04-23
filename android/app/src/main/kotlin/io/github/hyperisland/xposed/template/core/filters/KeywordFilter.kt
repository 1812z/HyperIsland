package io.github.hyperisland.xposed.template.core.filters

import android.util.Log
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.template.core.models.NotifData

object KeywordFilter {

    private const val TAG = "HyperIsland[KeywordFilter]"

    fun shouldBlock(data: NotifData): Boolean {
        val pkg = data.pkg
        val channelId = data.channelId

        val mode = ConfigManager.getString(
            "pref_channel_filter_mode_${pkg}_$channelId",
            "blacklist"
        )

        val whitelistStr = ConfigManager.getString(
            "pref_channel_filter_whitelist_keywords_${pkg}_$channelId"
        )
        val blacklistStr = ConfigManager.getString(
            "pref_channel_filter_blacklist_keywords_${pkg}_$channelId"
        )

        val whitelistKeywords = whitelistStr
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val blacklistKeywords = blacklistStr
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (whitelistKeywords.isEmpty() && blacklistKeywords.isEmpty()) return false

        val text = (data.title + data.subtitle).lowercase()

        val matchesWhitelist = whitelistKeywords.any { kw -> text.contains(kw.lowercase()) }
        val matchesBlacklist = blacklistKeywords.any { kw -> text.contains(kw.lowercase()) }

        val result = when (mode) {
            "whitelist" -> !matchesWhitelist
            else -> matchesBlacklist
        }

        if (result) {
            Log.d(TAG, "$pkg/$channelId blocked: mode=$mode, whitelistMatch=$matchesWhitelist, blacklistMatch=$matchesBlacklist")
        }

        return result
    }
}