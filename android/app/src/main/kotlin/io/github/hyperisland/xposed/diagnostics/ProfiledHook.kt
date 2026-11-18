package io.github.hyperisland.xposed.diagnostics

import android.os.Trace
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.HookBuilder
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable

/** Same hook/priority/exception semantics; a single volatile check when capture is off. */
internal fun XposedModule.profiledHook(owner: String, executable: Executable): HookBuilder {
    val builder = hook(executable)
    val label = "$owner:${executable.declaringClass.simpleName}.${executable.name}(${executable.parameterTypes.joinToString { it.simpleName }})"
    IslandPerformanceMonitor.register(label)
    return object : HookBuilder by builder {
        override fun setPriority(priority: Int): HookBuilder = apply { builder.setPriority(priority) }
        override fun setExceptionMode(mode: ExceptionMode): HookBuilder = apply { builder.setExceptionMode(mode) }
        override fun setId(id: String?): HookBuilder = apply { builder.setId(id) }
        override fun intercept(hooker: Hooker): HookHandle = builder.intercept { chain ->
            val session = IslandPerformanceMonitor.currentSession
                ?: return@intercept hooker.intercept(chain)
            val timed = TimedChain(chain)
            val tracing = Trace.isEnabled()
            if (tracing) Trace.beginSection("HI:$label".take(127))
            val start = System.nanoTime()
            try {
                hooker.intercept(timed)
            } finally {
                val end = System.nanoTime()
                if (tracing) Trace.endSection()
                // Diagnostics must never replace an original return value or exception.
                runCatching {
                    IslandPerformanceMonitor.recordHook(session, label, start, end, timed.downstreamNanos)
                }
            }
        }
    }
}

internal class TimedChain(
    private val original: Chain,
    private val clock: () -> Long = System::nanoTime,
) : Chain by original {
    var downstreamNanos = 0L
        private set

    private inline fun proceedTimed(block: () -> Any?): Any? {
        val start = clock()
        try {
            return block()
        } finally {
            downstreamNanos += clock() - start
        }
    }

    override fun proceed(): Any? = proceedTimed { original.proceed() }
    override fun proceed(args: Array<out Any?>): Any? = proceedTimed { original.proceed(args) }
    override fun proceedWith(thisObject: Any): Any? = proceedTimed { original.proceedWith(thisObject) }
    override fun proceedWith(thisObject: Any, args: Array<out Any?>): Any? =
        proceedTimed { original.proceedWith(thisObject, args) }
}
