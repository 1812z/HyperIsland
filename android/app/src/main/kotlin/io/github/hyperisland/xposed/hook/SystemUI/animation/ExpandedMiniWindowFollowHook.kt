package io.github.hyperisland.xposed.hook.SystemUI.animation

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.MotionEvent
import android.view.View
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.SystemUiReflection.findMethod
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/** Owns only the additional cross-axis translation during the stock mini-window drag. */
internal object ExpandedMiniWindowFollowHook {
    private data class Drag(val downX: Float, var x: Float, val originalX: Float,
        var tracking: Boolean = false, var reset: ValueAnimator? = null)
    private val drags = WeakHashMap<View, Drag>()
    private val hooked = Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    private val detach = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) = clear(v)
    }

    private fun clear(view: View) {
        val drag = drags.remove(view) ?: return
        drag.reset?.cancel()
        view.translationX = drag.originalX
        view.removeOnAttachStateChangeListener(detach)
    }

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
                    } else if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        clear(view)
                        val real = findMethod(clazz, "getRealView")?.invoke(view) as? View
                        val state = real?.let { findMethod(it.javaClass, "getState")?.invoke(it) }
                        if (state?.javaClass?.simpleName == "Expanded" && drags.size < 32) {
                            drags[view] = Drag(event.rawX, 0f, view.translationX)
                            view.addOnAttachStateChangeListener(detach)
                        }
                    } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                        drags[view]?.let { drag ->
                            drag.x = ExpandedGestureFollowHook.bounded(view, event.rawX - drag.downX)
                            if (drag.tracking) view.translationX = drag.originalX + drag.x
                        }
                    }
                }
                val result = chain.proceed()
                runCatching {
                    if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        val drag = drags[view]
                        if (drag?.tracking == true) {
                            val reference = WeakReference(view)
                            drag.reset = ValueAnimator.ofFloat(drag.x, 0f).apply {
                                duration = 220L
                                addUpdateListener { animator ->
                                    reference.get()?.let { target ->
                                        if (drags[target] === drag) {
                                            target.translationX = drag.originalX + (animator.animatedValue as Float)
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
                        } else clear(view)
                    }
                }
                result
            }
            module.hook(start).intercept { chain ->
                val result = chain.proceed()
                runCatching {
                    val view = chain.thisObject as? View ?: return@runCatching
                    drags[view]?.let { drag ->
                        drag.tracking = true
                        view.translationX = drag.originalX + drag.x
                    }
                }
                result
            }
            module.hook(update).intercept { chain ->
                runCatching {
                    val view = chain.thisObject as? View ?: return@runCatching
                    drags[view]?.takeIf { it.tracking }?.let { drag ->
                        // Apply before stock tracking can launch the mini-window at its threshold.
                        view.translationX = drag.originalX + drag.x
                    }
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
}
