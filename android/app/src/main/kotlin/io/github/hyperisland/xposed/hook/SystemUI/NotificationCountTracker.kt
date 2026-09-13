package io.github.hyperisland.xposed.hook.SystemUI

import android.service.notification.StatusBarNotification
import io.github.hyperisland.xposed.templates.NotificationCountIslandNotification
import java.util.concurrent.ConcurrentHashMap

object NotificationCountTracker {
    data class Scope(val pkg: String, val channelId: String)
    private data class Entry(val scope: Scope, val sbn: StatusBarNotification)
    private val entries = ConcurrentHashMap<String, Entry>()
    fun observe(sbn: StatusBarNotification, templateId: String) {
        if (templateId == NotificationCountIslandNotification.TEMPLATE_ID)
            entries[sbn.key] = Entry(Scope(sbn.packageName, sbn.notification?.channelId.orEmpty()), sbn)
    }
    fun remove(key: String): StatusBarNotification? = entries.remove(key)?.sbn
    fun count(scope: Scope): Int = entries.values.count { it.scope == scope }
    fun representative(scope: Scope): StatusBarNotification? = entries.values.asSequence()
        .filter { it.scope == scope }.maxByOrNull { it.sbn.postTime }?.sbn
    fun clear() = entries.clear()
}
