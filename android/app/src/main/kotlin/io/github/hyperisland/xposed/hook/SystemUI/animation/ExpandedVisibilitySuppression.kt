package io.github.hyperisland.xposed.hook.SystemUI.animation

import java.util.WeakHashMap

/**
 * Keeps the first state until suppression ends and reapplies hiding on every update.
 * Targets are stored only as weak keys. Snapshots and callbacks must not retain them.
 */
internal class ExpandedVisibilitySuppression<T : Any, S : Any>(
    private val capture: (T) -> S,
    private val hide: (T) -> Unit,
    private val restore: (T, S) -> Unit,
    private val maxTargets: Int = 128
) {
    private val snapshots = WeakHashMap<T, S>()

    fun update(targets: Collection<T>, active: Boolean) {
        val currentTargets = if (active) targets.toSet() else emptySet()
        val iterator = snapshots.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in currentTargets) {
                val target = entry.key
                val snapshot = entry.value
                iterator.remove()
                restore(target, snapshot)
            }
        }
        for (target in currentTargets) {
            if (!snapshots.containsKey(target)) {
                if (snapshots.size >= maxTargets) continue
                snapshots[target] = capture(target)
            }
            hide(target)
        }
    }

    fun release(target: T) {
        val snapshot = snapshots.remove(target) ?: return
        restore(target, snapshot)
    }

    fun isSuppressed(target: T): Boolean = snapshots.containsKey(target)
}
