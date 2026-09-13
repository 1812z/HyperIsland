package io.github.hyperisland.xposed.hook.SystemUI

import android.app.Notification
import android.service.notification.StatusBarNotification
import io.github.hyperisland.xposed.templates.NotificationCountIslandNotification
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher

/** Counts source notification posts and updates separately for each channel. */
object NotificationCountTracker {
    data class Scope(val pkg: String, val channelId: String)
    data class Entry(val scope: Scope, val sbn: StatusBarNotification, val posts: Int, val order: Long)
    private val entries = HashMap<String, Entry>()
    private val notificationIds = HashMap<Scope, Int>()
    private val assignedIds = HashMap<Int, Scope>()
    private var nextOrder = 0L

    fun scopeOf(sbn: StatusBarNotification) = Scope(sbn.packageName, sbn.notification?.channelId.orEmpty())

    private fun isTrackable(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification ?: return false
        return n.extras?.getString("hyperisland.owner") != "io.github.hyperisland" &&
            n.channelId != IslandDispatcher.CHANNEL_ID &&
            n.channelId != IslandDispatcher.SILENT_CHANNEL_ID &&
            n.flags and Notification.FLAG_GROUP_SUMMARY == 0
    }

    /** Only the concrete listener's posted callback increments an existing key. */
    @Synchronized fun posted(sbn: StatusBarNotification, templateId: String) {
        if (!isTrackable(sbn) || templateId != NotificationCountIslandNotification.TEMPLATE_ID) {
            entries.remove(sbn.key)
            return
        }
        val scope = scopeOf(sbn)
        val previous = entries[sbn.key]?.takeIf { it.scope == scope }
        entries[sbn.key] = Entry(scope, sbn, (previous?.posts ?: 0) + 1, ++nextOrder)
    }

    /** Bean generation may repeat; it must never create or increment a count. */
    @Synchronized fun ensureTracked(sbn: StatusBarNotification, templateId: String) {
        // Counting is driven exclusively by onNotificationPosted. This method is
        // intentionally a no-op to avoid counting generateInnerNotifBean twice.
    }

    @Synchronized fun remove(key: String): Entry? = entries.remove(key)
    @Synchronized fun count(scope: Scope): Int = entries.values.sumOf { if (it.scope == scope) it.posts else 0 }
    @Synchronized fun representative(scope: Scope): StatusBarNotification? = entries.values.asSequence()
        .filter { it.scope == scope }.maxByOrNull { it.order }?.sbn
    @Synchronized fun clear() { entries.clear(); nextOrder = 0L }

    /** A snapshot can restore active keys, but cannot recover updates before a SystemUI restart. */
    @Synchronized
    fun reconcile(active: Array<*>?, templateResolver: (StatusBarNotification) -> String) {
        val previous = HashMap(entries)
        entries.clear()
        active.orEmpty().filterIsInstance<StatusBarNotification>().forEach { sbn ->
            if (!isTrackable(sbn) || templateResolver(sbn) != NotificationCountIslandNotification.TEMPLATE_ID) return@forEach
            val scope = scopeOf(sbn)
            val old = previous[sbn.key]?.takeIf { it.scope == scope }
            entries[sbn.key] = Entry(scope, sbn, old?.posts ?: 1, old?.order ?: ++nextOrder)
        }
    }

    /** Stable independent dispatcher ID per channel; resolve rare hash collisions in process. */
    @Synchronized fun notificationId(scope: Scope): Int {
        notificationIds[scope]?.let { return it }
        var id = 0x60000000 or (scope.hashCode() and 0x0fffffff)
        while (assignedIds[id] != null && assignedIds[id] != scope) {
            id = 0x60000000 or ((id + 1) and 0x0fffffff)
        }
        notificationIds[scope] = id
        assignedIds[id] = scope
        return id
    }
}
