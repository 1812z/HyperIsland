package io.github.hyperisland.xposed.diagnostics

import io.github.libxposed.api.XposedInterface.Chain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.lang.reflect.Executable

class TimedChainTest {
    private class FakeChain : Chain {
        var now = 0L
        var receiver: Any? = null
        var forwarded: Array<out Any?> = emptyArray()
        var throwNext = false
        override fun getExecutable(): Executable = String::class.java.getMethod("length")
        override fun getThisObject(): Any? = receiver
        override fun getArgs(): List<Any?> = forwarded.toList()
        override fun getArg(index: Int): Any? = forwarded[index]
        override fun proceed(): Any? {
            now += 10L
            if (throwNext) throw IllegalStateException("native failure")
            return "original result"
        }
        override fun proceed(args: Array<out Any?>): Any? { forwarded = args; return proceed() }
        override fun proceedWith(thisObject: Any): Any? { receiver = thisObject; return proceed() }
        override fun proceedWith(thisObject: Any, args: Array<out Any?>): Any? {
            receiver = thisObject
            return proceed(args)
        }
    }

    @Test fun allProceedFormsPreserveArgumentsAndResultsAndAccumulateDownstreamTime() {
        val original = FakeChain()
        val chain = TimedChain(original) { original.now }
        val receiver = Any()
        val args = arrayOf<Any?>(1, null, "value")
        assertEquals("original result", chain.proceed())
        assertEquals("original result", chain.proceed(args))
        assertEquals("original result", chain.proceedWith(receiver))
        assertEquals("original result", chain.proceedWith(receiver, args))
        assertSame(receiver, original.receiver)
        assertSame(args, original.forwarded)
        assertEquals(40L, chain.downstreamNanos)
    }

    @Test fun failedNativeCallsAreStillSubtractedWithoutSwallowingTheException() {
        val original = FakeChain().apply { throwNext = true }
        val chain = TimedChain(original) { original.now }
        try {
            chain.proceed()
            fail("Native exception must propagate")
        } catch (error: IllegalStateException) {
            assertEquals("native failure", error.message)
        }
        assertEquals(10L, chain.downstreamNanos)
    }
}
