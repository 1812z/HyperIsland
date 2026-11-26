package io.github.hyperisland.xposed.hook

import io.github.hyperisland.xposed.hook.SystemUI.outline.SmallIslandGlowSlot
import io.github.hyperisland.xposed.hook.SystemUI.outline.selectSmallIslandGlowSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmallIslandGlowSlotTest {
    @Test
    fun fakeSmallIslandTakesOverWhenItsOpacityReachesTheRealIsland() {
        assertEquals(SmallIslandGlowSlot.REAL, selectSmallIslandGlowSlot(0.7f, 0.3f))
        assertEquals(SmallIslandGlowSlot.FAKE, selectSmallIslandGlowSlot(0.5f, 0.5f))
        assertEquals(SmallIslandGlowSlot.FAKE, selectSmallIslandGlowSlot(0f, 1f))
    }

    @Test
    fun hiddenSmallIslandReleasesTheEffect() {
        assertNull(selectSmallIslandGlowSlot(0f, 0f))
    }
}
