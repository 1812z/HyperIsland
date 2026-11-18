package io.github.hyperisland.xposed.utils

import java.lang.ref.WeakReference
import java.lang.reflect.Member
import java.util.WeakHashMap

/** Cache hits and absent members without retaining plugin Classes through their Members. */
internal class WeakMemberCache<T : Member>(
    private val maxClasses: Int = 64,
    private val maxMembers: Int = 32,
    private val resolve: (Class<*>, String) -> T?,
) {
    private val classes = WeakHashMap<Class<*>, LinkedHashMap<String, WeakReference<T>?>>()

    init { require(maxClasses > 0 && maxMembers > 0) }

    @Synchronized
    fun get(type: Class<*>, name: String): T? {
        var members = classes[type]
        if (members != null && members.containsKey(name)) {
            val reference = members[name] ?: return null
            reference.get()?.let { return it }
        }
        val result = resolve(type, name)
        if (members == null) {
            if (classes.size >= maxClasses) classes.keys.iterator().also { it.next(); it.remove() }
            members = LinkedHashMap()
            classes[type] = members
        }
        if (members.size >= maxMembers && !members.containsKey(name)) {
            members.keys.iterator().also { it.next(); it.remove() }
        }
        members[name] = result?.let(::WeakReference)
        return result
    }
}
