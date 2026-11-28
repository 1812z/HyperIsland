package io.github.hyperisland.xposed.hook.SystemUI.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedVisibilitySuppressionTest {
    @Test
    fun repeatedUpdatesPreserveTheOriginalAlpha() {
        val target = Target(alpha = 0.65f)
        val suppression = suppression()

        suppression.update(listOf(target), active = true)
        suppression.update(listOf(target), active = true)
        assertEquals(0f, target.alpha, 0f)
        suppression.update(listOf(target), active = false)

        assertEquals(Visibility.VISIBLE, target.visibility)
        assertEquals(0.65f, target.alpha, 0f)
        assertFalse(suppression.isSuppressed(target))
    }

    @Test
    fun repeatedUpdatesHideTargetsMadeVisibleByTheSystem() {
        val target = Target(alpha = 0.7f)
        val suppression = suppression()
        suppression.update(listOf(target), active = true)
        target.visibility = Visibility.VISIBLE
        target.alpha = 1f

        suppression.update(listOf(target), active = true)

        assertEquals(Visibility.INVISIBLE, target.visibility)
        assertEquals(0f, target.alpha, 0f)
        assertTrue(suppression.isSuppressed(target))
        suppression.release(target)
        assertEquals(Visibility.VISIBLE, target.visibility)
        assertEquals(0.7f, target.alpha, 0f)
    }

    @Test
    fun restoresInitiallyInvisibleTargets() {
        val target = Target(visibility = Visibility.INVISIBLE)
        val suppression = suppression()

        suppression.update(listOf(target), active = true)
        assertEquals(0f, target.alpha, 0f)
        suppression.update(emptyList(), active = false)

        assertEquals(Visibility.INVISIBLE, target.visibility)
        assertEquals(1f, target.alpha, 0f)
    }

    @Test
    fun restoresInitiallyGoneTargets() {
        val target = Target(visibility = Visibility.GONE)
        val suppression = suppression()

        suppression.update(listOf(target), active = true)
        assertEquals(Visibility.INVISIBLE, target.visibility)
        suppression.update(emptyList(), active = false)

        assertEquals(Visibility.GONE, target.visibility)
        assertEquals(1f, target.alpha, 0f)
    }

    @Test
    fun restoresPartialAlphaTargets() {
        val target = Target(alpha = 0.35f)
        val suppression = suppression()

        suppression.update(listOf(target), active = true)
        assertEquals(0f, target.alpha, 0f)
        suppression.release(target)

        assertEquals(Visibility.VISIBLE, target.visibility)
        assertEquals(0.35f, target.alpha, 0f)
    }

    @Test
    fun replacingTargetsRestoresOldTargetsBeforeSuppressingNewTargets() {
        val oldTarget = Target(alpha = 0.6f)
        val newTarget = Target(alpha = 0.8f)
        val suppression = suppression(maxTargets = 1)
        suppression.update(listOf(oldTarget), active = true)

        suppression.update(listOf(newTarget), active = true)

        assertEquals(Visibility.VISIBLE, oldTarget.visibility)
        assertEquals(0.6f, oldTarget.alpha, 0f)
        assertFalse(suppression.isSuppressed(oldTarget))
        assertEquals(Visibility.INVISIBLE, newTarget.visibility)
        assertEquals(0f, newTarget.alpha, 0f)
        assertTrue(suppression.isSuppressed(newTarget))
    }

    @Test
    fun inactiveUpdatesRestoreEverySuppressedTarget() {
        val first = Target(alpha = 0.4f)
        val second = Target(visibility = Visibility.GONE, alpha = 0.8f)
        val suppression = suppression()
        suppression.update(listOf(first, second), active = true)
        assertTrue(suppression.isSuppressed(first))
        assertTrue(suppression.isSuppressed(second))

        suppression.update(listOf(first), active = false)

        assertEquals(Visibility.VISIBLE, first.visibility)
        assertEquals(0.4f, first.alpha, 0f)
        assertEquals(Visibility.GONE, second.visibility)
        assertEquals(0.8f, second.alpha, 0f)
        assertFalse(suppression.isSuppressed(first))
        assertFalse(suppression.isSuppressed(second))
    }

    @Test
    fun releasingADetachedTargetRestoresOnlyThatTarget() {
        val detached = Target(alpha = 0.45f)
        val attached = Target(alpha = 0.9f)
        val suppression = suppression()
        suppression.update(listOf(detached, attached), active = true)

        suppression.release(detached)

        assertEquals(Visibility.VISIBLE, detached.visibility)
        assertEquals(0.45f, detached.alpha, 0f)
        assertFalse(suppression.isSuppressed(detached))
        assertEquals(Visibility.INVISIBLE, attached.visibility)
        assertEquals(0f, attached.alpha, 0f)
        assertTrue(suppression.isSuppressed(attached))
        detached.alpha = 0.2f
        suppression.release(detached)
        assertEquals(0.2f, detached.alpha, 0f)
    }

    @Test
    fun targetLimitPreservesExistingSnapshotsAndLeavesOverflowUntouched() {
        val existing = Target(alpha = 0.7f)
        val overflow = Target(alpha = 0.8f)
        val suppression = suppression(maxTargets = 1)
        suppression.update(listOf(existing), active = true)
        existing.visibility = Visibility.VISIBLE
        existing.alpha = 0.4f

        suppression.update(listOf(overflow, existing), active = true)

        assertEquals(Visibility.VISIBLE, overflow.visibility)
        assertEquals(0.8f, overflow.alpha, 0f)
        assertFalse(suppression.isSuppressed(overflow))
        assertEquals(Visibility.INVISIBLE, existing.visibility)
        assertEquals(0f, existing.alpha, 0f)
        assertTrue(suppression.isSuppressed(existing))
        suppression.update(emptyList(), active = false)
        assertEquals(Visibility.VISIBLE, existing.visibility)
        assertEquals(0.7f, existing.alpha, 0f)
    }

    private fun suppression(maxTargets: Int = 128) = ExpandedVisibilitySuppression<Target, Snapshot>(
        capture = { Snapshot(it.visibility, it.alpha) },
        hide = {
            it.visibility = Visibility.INVISIBLE
            it.alpha = 0f
        },
        restore = { target, snapshot ->
            target.visibility = snapshot.visibility
            target.alpha = snapshot.alpha
        },
        maxTargets = maxTargets
    )

    private enum class Visibility { VISIBLE, INVISIBLE, GONE }

    private class Target(
        var visibility: Visibility = Visibility.VISIBLE,
        var alpha: Float = 1f
    )

    private data class Snapshot(val visibility: Visibility, val alpha: Float)
}
