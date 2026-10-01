package io.github.hyperisland.xposed.template.core.filters

import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.logDebug
import io.github.hyperisland.xposed.template.core.models.NotifData

object KeywordFilter {

    private const val TAG = "HyperIsland[KeywordFilter]"
    private const val GROUP_SUMMARY_TITLE = "新消息 GroupSummary"
    private const val GROUP_SUMMARY_BODY = "你有一条新消息"

    fun shouldBlock(data: NotifData): Boolean {
        val pkg = data.pkg
        val channelId = data.channelId

        val normalizedTitle = normalizeNotificationText(data.title)
        val normalizedSubtitle = normalizeNotificationText(data.subtitle)
        if (normalizedTitle == GROUP_SUMMARY_TITLE && normalizedSubtitle == GROUP_SUMMARY_BODY) {
            return true
        }

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
            "whitelist" -> !matchesWhitelist || matchesBlacklist
            else -> matchesBlacklist
        }

        if (result) {
            logDebug(TAG) {
                "$pkg/$channelId blocked: mode=$mode, whitelistMatch=$matchesWhitelist, blacklistMatch=$matchesBlacklist"
            }
        }

        return result
    }

    private fun normalizeNotificationText(value: String): String = value
        .replace('\u00A0', ' ')
        .replace("\u200B", "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
