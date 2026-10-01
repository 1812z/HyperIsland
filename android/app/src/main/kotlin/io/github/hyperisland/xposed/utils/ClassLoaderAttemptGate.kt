package io.github.hyperisland.xposed.utils

import java.util.WeakHashMap

/** Records attempts before lookup/installation, including failures and reentrant calls. */
internal class ClassLoaderAttemptGate {
    private val attempted = WeakHashMap<ClassLoader, Boolean>()

    @Synchronized
    fun enter(loader: ClassLoader): Boolean {
        if (attempted.containsKey(loader)) return false
        // Do not evict live entries: eviction would re-enable failed hot-path attempts.
        if (attempted.size >= 256) return false
        attempted[loader] = true
        return true
    }
}
