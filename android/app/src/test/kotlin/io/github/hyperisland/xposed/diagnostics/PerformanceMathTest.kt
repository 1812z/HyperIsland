package io.github.hyperisland.xposed.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class PerformanceMathTest {
    @Test fun downstreamWorkIsNotBlamedOnTheWrappingHook() {
        assertEquals(500_000L, PerformanceMath.selfNanos(30_500_000L, 30_000_000L))
        assertEquals(0L, PerformanceMath.selfNanos(20L, 21L))
    }

    @Test fun estimateUsesActualRefreshBudgetInsteadOfAssumingSixtyHertz() {
        val gap = 33_333_334L
        assertEquals(1, PerformanceMath.missedVsyncs(gap, 16_666_667L))
        assertEquals(3, PerformanceMath.missedVsyncs(gap, 8_333_333L))
        assertEquals(0, PerformanceMath.missedVsyncs(16_900_000L, 16_666_667L))
        assertEquals(0, PerformanceMath.missedVsyncs(gap, 0L))
    }
}
