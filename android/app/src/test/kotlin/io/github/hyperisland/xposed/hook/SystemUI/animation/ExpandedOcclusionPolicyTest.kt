package io.github.hyperisland.xposed.hook.SystemUI.animation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedOcclusionPolicyTest {
    @Test
    fun usesShortFadeForSuppression() {
        assertEquals(120L, ExpandedOcclusionPolicy.FADE_DURATION_MS)
    }

    @Test
    fun customTopSpacingSuppressesImmediatelyWhenExpanded() {
        assertTrue(ExpandedOcclusionPolicy.shouldSuppress(hasCustomTopSpacing = true, isExpanded = true))
    }

    @Test
    fun defaultTopSpacingDoesNotSuppressExpandedContent() {
        assertFalse(ExpandedOcclusionPolicy.shouldSuppress(hasCustomTopSpacing = false, isExpanded = true))
    }

    @Test
    fun leavingExpandedRestoresSuppressedContent() {
        assertFalse(ExpandedOcclusionPolicy.shouldSuppress(hasCustomTopSpacing = true, isExpanded = false))
    }
}
