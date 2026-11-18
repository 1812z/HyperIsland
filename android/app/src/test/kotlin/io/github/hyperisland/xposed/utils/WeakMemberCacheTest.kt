package io.github.hyperisland.xposed.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.lang.reflect.Method

class WeakMemberCacheTest {
    class First { fun value() = "first" }
    class Second { fun value() = "second" }

    @Test fun absentOptionalApiIsNotLookedUpOnEveryAnimationFrame() {
        var resolutions = 0
        val cache = WeakMemberCache<Method> { type, name ->
            resolutions++
            runCatching { type.getDeclaredMethod(name) }.getOrNull()
        }
        repeat(1_000) { assertNull(cache.get(First::class.java, "unavailable")) }
        assertEquals(1, resolutions)
        assertEquals("first", cache.get(First::class.java, "value")!!.invoke(First()))
        assertEquals(2, resolutions)
    }

    @Test fun sameMemberNameIsIsolatedByActualClass() {
        val cache = WeakMemberCache<Method> { type, name -> type.getDeclaredMethod(name) }
        val first = cache.get(First::class.java, "value")!!
        val second = cache.get(Second::class.java, "value")!!
        assertEquals("first", first.invoke(First()))
        assertEquals("second", second.invoke(Second()))
        assertSame(first, cache.get(First::class.java, "value"))
    }

    @Test fun capacityEvictionAllowsPreviouslyAbsentApiToBeResolvedAgain() {
        var resolutions = 0
        val cache = WeakMemberCache<Method>(maxClasses = 1) { type, name ->
            resolutions++
            runCatching { type.getDeclaredMethod(name) }.getOrNull()
        }
        assertNull(cache.get(First::class.java, "absent"))
        val second = cache.get(Second::class.java, "value")!!
        assertEquals("second", second.invoke(Second()))
        assertNull(cache.get(First::class.java, "absent"))
        assertEquals(3, resolutions)
    }
}
