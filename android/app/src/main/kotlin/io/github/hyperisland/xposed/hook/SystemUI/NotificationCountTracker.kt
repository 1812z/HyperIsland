package io.github.hyperisland.xposed.hook.SystemUI

import android.service.notification.StatusBarNotification
import android.app.Notification
import io.github.hyperisland.xposed.templates.NotificationCountIslandNotification
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import java.util.concurrent.ConcurrentHashMap

object NotificationCountTracker {
    /** 按原始软件包汇总，避免同一软件不同渠道各自从 1 计数。 */
    data class Scope(val pkg: String)
    private data class Entry(val scope: Scope, val sbn: StatusBarNotification)
    private val entries = ConcurrentHashMap<String, Entry>()
    private val enabledChannels = ConcurrentHashMap.newKeySet<String>()
    private fun channelKey(sbn: StatusBarNotification) =
        "${sbn.packageName}\u0000${sbn.notification?.channelId.orEmpty()}"
    private fun isProxy(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification ?: return false
        val extras = n.extras
        return extras?.getString("hyperisland.owner") == "io.github.hyperisland" ||
            n.channelId == IslandDispatcher.CHANNEL_ID ||
            n.channelId == IslandDispatcher.SILENT_CHANNEL_ID
    }
    private fun isGroupSummary(sbn: StatusBarNotification): Boolean =
        (sbn.notification?.flags ?: 0) and Notification.FLAG_GROUP_SUMMARY != 0
    fun observe(sbn: StatusBarNotification, templateId: String) {
        if (isProxy(sbn) || isGroupSummary(sbn)) {
            entries.remove(sbn.key)
            return
        }
        if (templateId == NotificationCountIslandNotification.TEMPLATE_ID) {
            enabledChannels += channelKey(sbn)
        } else {
            enabledChannels.remove(channelKey(sbn))
            entries.entries.removeIf { it.value.sbn.packageName == sbn.packageName &&
                it.value.sbn.notification?.channelId.orEmpty() == sbn.notification?.channelId.orEmpty() }
        }
        if (enabledChannels.contains(channelKey(sbn))) {
            entries[sbn.key] = Entry(Scope(sbn.packageName), sbn)
        } else {
            entries.remove(sbn.key)
        }
    }
    fun remove(key: String): StatusBarNotification? = entries.remove(key)?.sbn
    fun count(scope: Scope): Int = entries.values.count { it.scope == scope }
    fun representative(scope: Scope): StatusBarNotification? = entries.values.asSequence()
        .filter { it.scope == scope }.maxByOrNull { it.sbn.postTime }?.sbn
    fun clear() { entries.clear(); enabledChannels.clear() }
    fun isEmpty(): Boolean = entries.isEmpty()
    fun counts(): Map<Scope, Int> = entries.values.groupingBy { it.scope }.eachCount()

    fun reconcile(active: Array<*>?, templateResolver: (StatusBarNotification) -> String) {
        val notifications = active.orEmpty().filterIsInstance<StatusBarNotification>()
            .filterNot { isProxy(it) || isGroupSummary(it) }
        val channels = notifications.filter { templateResolver(it) == NotificationCountIslandNotification.TEMPLATE_ID }
            .map(::channelKey).toSet()
        enabledChannels.clear()
        enabledChannels.addAll(channels)
        val fresh = ConcurrentHashMap<String, Entry>()
        notifications.forEach { sbn ->
            if (enabledChannels.contains(channelKey(sbn))) {
                fresh[sbn.key] = Entry(Scope(sbn.packageName), sbn)
            }
        }
        entries.clear()
        entries.putAll(fresh)
    }
}
