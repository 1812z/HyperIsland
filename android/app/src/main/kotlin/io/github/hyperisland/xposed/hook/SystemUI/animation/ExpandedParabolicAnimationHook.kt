package io.github.hyperisland.xposed.hook.SystemUI.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.PathInterpolator
import io.github.hyperisland.data.ExpandedCollapsePreferences as Keys
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.SystemUiReflection.findMethod
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.hypot
import kotlin.math.tanh

/** A temporary whole-island arc, added to stock motion only after a swipe commits collapse. */
internal object ExpandedParabolicAnimationHook {
    private data class Direction(val x: Float, val y: Float, val time: Long)
    private class Flight(val targets: List<WeakReference<View>>) {
        var x = 0f
        var y = 0f
        var animator: ValueAnimator? = null
        fun move(nx: Float, ny: Float) {
            writing.set(true)
            try {
                targets.forEach { it.get()?.let { view ->
                    view.translationX += nx - x
                    view.translationY += ny - y
                } }
                x = nx
                y = ny
            } finally {
                writing.remove()
            }
        }
    }
    private val directions = WeakHashMap<View, Direction>()
    private val flights = WeakHashMap<View, Flight>()
    private val targetFlights = WeakHashMap<View, Flight>()
    private val writing = ThreadLocal<Boolean>()
    private var settersInstalled = false
    private val hooked = Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    private val detach = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) = stop(v)
    }

    fun record(view: View, x: Float, y: Float) {
        if (!x.isFinite() || !y.isFinite()) return
        if (directions.size >= 32 && !directions.containsKey(view)) return
        directions[view] = Direction(x, y, android.os.SystemClock.uptimeMillis())
    }

    private fun stop(view: View) {
        val flight = flights.remove(view) ?: return
        flight.animator?.cancel()
        flight.move(0f, 0f)
        flight.targets.forEach { it.get()?.let { target ->
            if (targetFlights[target] === flight) targetFlights.remove(target)
        } }
        view.removeOnAttachStateChangeListener(detach)
    }

    fun install(module: XposedModule, loader: ClassLoader) {
        runCatching {
            installTranslationHooks(module)
            val clazz = loader.loadClass("miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate")
            synchronized(hooked) { if (hooked.contains(clazz)) return@runCatching }
            val methods = clazz.declaredMethods.filter {
                it.name in setOf("expandedToBigIslandAnimation", "expandedToSmallIslandAnimation") && it.parameterCount == 1
            }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val view = chain.args[0] as? View ?: return@intercept chain.proceed()
                    val direction = directions.remove(view)
                    val result = chain.proceed()
                    runCatching {
                        if (!ExpandedGestureFollowHook.isEnabled() || !ConfigManager.getBoolean(Keys.PARABOLIC, false) ||
                            direction == null || android.os.SystemClock.uptimeMillis() - direction.time > 250L ||
                            hypot(direction.x, direction.y) <= 0f || !view.isAttachedToWindow ||
                            view.resources.configuration.smallestScreenWidthDp >= 600) return@runCatching
                        launch(view, direction)
                    }
                    result
                }
            }
            // Resetting a cancelled swipe must not leave a recent throw direction behind.
            clazz.declaredMethods.filter { it.name == "resetSwipe" && it.parameterCount == 1 }.forEach { method ->
                module.hook(method).intercept { chain ->
                    (chain.args[0] as? View)?.let { directions.remove(it) }
                    chain.proceed()
                }
            }
            synchronized(hooked) { hooked.add(clazz) }
        }
    }

    private fun installTranslationHooks(module: XposedModule) {
        if (settersInstalled) return
        listOf("setTranslationX", "setTranslationY").forEach { name ->
            val setter = View::class.java.getDeclaredMethod(name, Float::class.javaPrimitiveType!!)
            module.hook(setter).intercept { chain ->
                if (writing.get() == true) return@intercept chain.proceed()
                val target = chain.thisObject as? View ?: return@intercept chain.proceed()
                val flight = targetFlights[target] ?: return@intercept chain.proceed()
                val args = chain.args.toTypedArray()
                args[0] = (args[0] as Number).toFloat() + if (name == "setTranslationX") flight.x else flight.y
                chain.proceed(args)
            }
        }
        settersInstalled = true
    }

    private fun launch(view: View, direction: Direction) {
        stop(view)
        if (flights.size >= 32) return
        fun getter(target: Any, name: String) = findMethod(target.javaClass, name)?.invoke(target)
        val background = getter(view, "getBackgroundView") as? View ?: return
        val targets = mutableListOf(background)
        // State collapse can hand drawing to fake content. Move both hosts together.
        (getter(view, "getFakeView") as? View)?.let { targets += it }
        // Glow is drawn in external window containers, so move its effect Views too.
        listOf("getExpandedView", "getBigIslandView").forEach { name ->
            getter(view, name)?.let { glow ->
                listOf("getMGlowEffectUpperView", "getMGlowEffectBottomView").forEach { effect ->
                    (getter(glow, effect) as? View)?.let { if (it !in targets) targets += it }
                }
            }
        }
        val flight = Flight(targets.map { WeakReference(it) })
        flights[view] = flight
        targets.forEach { targetFlights[it] = flight }
        view.addOnAttachStateChangeListener(detach)
        // Do not normalize the gesture into a fixed-distance throw: that turns a tiny
        // diagonal movement into a full-strength launch as soon as a threshold is crossed.
        // Both axes respond continuously in dp, and gentle stays close to the island.
        val density = view.resources.displayMetrics.density
        val (limitDp, response) = when (ConfigManager.getString(Keys.THROW_STRENGTH, "balanced")) {
            "gentle" -> 6f to .04f
            "strong" -> 22f to .12f
            "powerful" -> 36f to .18f
            "maximum" -> 52f to .24f
            else -> 12f to .07f
        }
        val limit = limitDp * density
        val desiredX = limit * tanh(direction.x * response / limit)
        // Stronger horizontal response, retaining the selected tier's distance limit.
        val enhancedX = limit * tanh(direction.x * response * 2f / limit)
        val desiredY = limit * tanh(direction.y * response / limit)
        // The arc has a perpendicular bend, so reserve its 25% envelope too. Keep
        // upward throws within the available top margin instead of exiting the screen.
        val location = IntArray(2)
        background.getLocationOnScreen(location)
        val rectTop = (getter(background, "getActualTop") as? Number)?.toFloat() ?: 0f
        val availableTop = (location[1] + rectTop - 2f * density).coerceAtLeast(0f)
        val upwardEnvelope = (-desiredY).coerceAtLeast(0f) + kotlin.math.abs(desiredX) * .25f
        val fit = if (upwardEnvelope > 0f) (availableTop / upwardEnvelope).coerceIn(0f, 1f) else 1f
        val dx = enhancedX * fit
        val verticalBendX = desiredX * fit
        val dy = desiredY * fit
        val curve = ConfigManager.getString(Keys.CURVE, "balanced")
        val reference = WeakReference(view)
        flight.animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = when (curve) { "snappy" -> 360L; "gentle" -> 520L; else -> 440L }
            interpolator = when (curve) {
                "snappy" -> PathInterpolator(.2f, 0f, .2f, 1f)
                "gentle" -> PathInterpolator(.4f, 0f, .3f, 1f)
                else -> PathInterpolator(.3f, 0f, .25f, 1f)
            }
            addUpdateListener {
                val t = it.animatedValue as Float
                // Outward lobe with a perpendicular bend, ending exactly at stock position.
                val travel = 4f * t * (1f - t)
                val bend = travel * (2f * t - 1f) * .25f
                flight.move(dx * travel - dy * bend, dy * travel + verticalBendX * bend)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    reference.get()?.let { if (flights[it] === flight) stop(it) }
                }
            })
            start()
        }
    }
}
