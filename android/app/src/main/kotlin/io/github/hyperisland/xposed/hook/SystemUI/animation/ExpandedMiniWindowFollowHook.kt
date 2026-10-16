package io.github.hyperisland.xposed.hook.SystemUI.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.View
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.SystemUiReflection.findMethod
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/**
 * Owns the gesture-following translation the stock mini-window drag does not provide on its own.
 *
 * During pull-down tracking the stock [onTrackingFakeViewUpdate] already moves the fake view's
 * top/bottom with friction and fades its alpha. This hook adds a bounded follow on both axes:
 * horizontal `translationX` from raw touch deltas, and vertical `translationY` taken directly from
 * the stock callback's cumulative-drag argument so it never re-derives the system's slop/threshold
 * logic. The whole fake window therefore tracks the finger without fighting the mini-window launch.
 *
 * Gated by the "跟随效果" toggle via [ExpandedGestureFollowHook.isEnabled]. Translations return to a
 * pristine base (captured once per fake view) on release, reset or detach, so a leftover horizontal
 * offset from an interrupted diagonal pull-down can never be re-captured as the next gesture origin.
 */
internal object ExpandedMiniWindowFollowHook {

    /** Pristine translation captured before the module ever wrote to a fake view. */
    private data class Base(val x: Float, val y: Float)

    private class Drag(
        val downX: Float,
        val base: Base,
        var x: Float = 0f,
        var y: Float = 0f,
        var tracking: Boolean = false,
        var reset: ValueAnimator? = null,
    )

    /** Keyed by the fake view that receives [MotionEvent]s (also the translation target). */
    private val drags = WeakHashMap<View, Drag>()
    /** Pristine translations per fake view; bounded and cleaned on detach. */
    private val bases = WeakHashMap<View, Base>()
    private val hooked = Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())

    private val detach = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) = clear(v)
    }

    private fun baseOf(view: View): Base {
        bases[view]?.let { return it }
        val base = Base(view.translationX, view.translationY)
        if (bases.size < 64) bases[view] = base
        return base
    }

    private fun apply(view: View, drag: Drag) {
        view.translationX = drag.base.x + drag.x
        view.translationY = drag.base.y + drag.y
    }

    private fun clear(view: View) {
        val drag = drags.remove(view) ?: return
        drag.reset?.cancel()
        view.translationX = drag.base.x
        view.translationY = drag.base.y
        view.removeOnAttachStateChangeListener(detach)
    }

    fun clearAll() = drags.keys.toList().forEach(::clear)

    fun install(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val clazz = loader.loadClass("miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView")
            synchronized(hooked) { if (hooked.contains(clazz)) return@runCatching }
            val touch = clazz.getDeclaredMethod("handleTouchEvent", MotionEvent::class.java)
            val start = clazz.getDeclaredMethod("onTrackingFakeViewStart")
            val update = clazz.getDeclaredMethod("onTrackingFakeViewUpdate", Float::class.javaPrimitiveType!!)
            val reset = clazz.getDeclaredMethod("onTrackingFakeViewReset")
            module.hook(touch).intercept { chain ->
                val view = chain.thisObject as? View ?: return@intercept chain.proceed()
                val event = chain.args[0] as? MotionEvent ?: return@intercept chain.proceed()
                runCatching {
                    if (!ExpandedGestureFollowHook.isEnabled()) {
                        clear(view)
                    } else {
                        handleTouch(view, clazz, event)
                    }
                }
                val result = chain.proceed()
                runCatching {
                    if (event.actionMasked == MotionEvent.ACTION_UP ||
                        event.actionMasked == MotionEvent.ACTION_CANCEL
                    ) finishGesture(view)
                }
                result
            }
            module.hook(start).intercept { chain ->
                val result = chain.proceed()
                runCatching {
                    val view = chain.thisObject as? View ?: return@runCatching
                    if (!ExpandedGestureFollowHook.isEnabled()) {
                        clear(view)
                        return@runCatching
                    }
                    drags[view]?.let { drag ->
                        drag.tracking = true
                        drag.y = 0f
                        apply(view, drag)
                    }
                }
                result
            }
            module.hook(update).intercept { chain ->
                runCatching {
                    val view = chain.thisObject as? View ?: return@runCatching
                    if (!ExpandedGestureFollowHook.isEnabled()) {
                        clear(view)
                        return@runCatching
                    }
                    val drag = drags[view]?.takeIf { it.tracking } ?: return@runCatching
                    val distance = (chain.args[0] as? Number)?.toFloat() ?: return@runCatching
                    drag.y = ExpandedGestureFollowHook.bounded(view, distance)
                    // Apply before stock tracking can launch the mini-window at its threshold.
                    apply(view, drag)
                }
                chain.proceed()
            }
            module.hook(reset).intercept { chain ->
                runCatching { (chain.thisObject as? View)?.let(::clear) }
                chain.proceed()
            }
            synchronized(hooked) { hooked.add(clazz) }
        }
    }

    private fun handleTouch(view: View, clazz: Class<*>, event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                clear(view)
                val real = findMethod(clazz, "getRealView")?.invoke(view) as? View ?: return
                val state = findMethod(real.javaClass, "getState")?.invoke(real)
                if (state?.javaClass?.simpleName != "Expanded" || drags.size >= 32) return
                val base = baseOf(view)
                // Clear any leftover offset from a previous interrupted gesture before starting fresh.
                // base is the pristine value, not a per-gesture snapshot, so a stale translation can
                // never be re-captured as the "original" position.
                view.translationX = base.x
                view.translationY = base.y
                drags[view] = Drag(event.rawX, base)
                view.addOnAttachStateChangeListener(detach)
            }

            MotionEvent.ACTION_MOVE -> {
                drags[view]?.let { drag ->
                    drag.x = ExpandedGestureFollowHook.bounded(view, event.rawX - drag.downX)
                    if (drag.tracking) apply(view, drag)
                }
            }
        }
    }

    private fun finishGesture(view: View) {
        val drag = drags[view] ?: return
        if (!drag.tracking) {
            clear(view)
            return
        }
        val reference = WeakReference(view)
        drag.reset = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220L
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                reference.get()?.let { target ->
                    if (drags[target] === drag) {
                        target.translationX = drag.base.x + drag.x * (1f - t)
                        target.translationY = drag.base.y + drag.y * (1f - t)
                    }
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    reference.get()?.let { target ->
                        if (drags[target] === drag) clear(target)
                    }
                }
            })
            start()
        }
    }
}
