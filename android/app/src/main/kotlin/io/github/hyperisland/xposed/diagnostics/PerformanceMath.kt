package io.github.hyperisland.xposed.diagnostics

import kotlin.math.roundToInt

internal object PerformanceMath {
    fun selfNanos(elapsed: Long, downstream: Long): Long = (elapsed - downstream).coerceAtLeast(0L)
    fun missedVsyncs(gap: Long, budget: Long): Int =
        if (budget <= 0L || gap < budget * 3 / 2) 0
        else ((gap.toDouble() / budget).roundToInt() - 1).coerceAtLeast(0)
}
