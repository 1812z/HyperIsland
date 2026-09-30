package io.github.hyperisland.xposed.hook.SystemUI.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.MotionEvent
import android.view.VelocityTracker
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
    private val trackers = WeakHashMap<View, VelocityTracker>()
    private val releases = WeakHashMap<View, Direction>()
    private val touchDetach = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) {
            trackers.remove(v)?.recycle()
            releases.remove(v)
            v.removeOnAttachStateChangeListener(this)
        }
    }
    private val flights = WeakHashMap<View, Flight>()
    private val targetFlights = WeakHashMap<View, Flight>()
    private val writing = ThreadLocal<Boolean>()
    private var settersInstalled = false
    private val hooked = Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    private val detach = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) = stop(v)
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
        installVelocityTracking(module, loader)
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
                    var host: View? = view
                    var direction: Direction? = null
                    while (host != null) {
                        releases.remove(host)?.let { direction = it }
                        host = host.parent as? View
                    }
                    val release = direction
                    val result = chain.proceed()
                    runCatching {
                        if (!ExpandedGestureFollowHook.isEnabled() || !ConfigManager.getBoolean(Keys.PARABOLIC, false) ||
                            release == null || android.os.SystemClock.uptimeMillis() - release.time > 250L ||
                            hypot(release.x, release.y) <= 0f || !view.isAttachedToWindow ||
                            view.resources.configuration.smallestScreenWidthDp >= 600) return@runCatching
                        launch(view, release)
                    }
                    result
                }
            }
            // Resetting a cancelled swipe must not leave a recent throw direction behind.
            clazz.declaredMethods.filter { it.name == "resetSwipe" && it.parameterCount == 1 }.forEach { method ->
                module.hook(method).intercept { chain ->
                    var host = chain.args[0] as? View
                    while (host != null) {
                        releases.remove(host)
                        host = host.parent as? View
                    }
                    chain.proceed()
                }
            }
            synchronized(hooked) { hooked.add(clazz) }
        }
    }

    private fun installVelocityTracking(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val clazz = loader.loadClass("miui.systemui.dynamicisland.window.DynamicIslandWindowView")
            synchronized(hooked) { if (hooked.contains(clazz)) return@runCatching }
            // Capture before SystemUI dispatches UP and commits its asynchronous state change.
            val method = clazz.getMethod("dispatchTouchEvent", MotionEvent::class.java)
            module.hook(method).intercept { chain ->
                runCatching {
                    val window = chain.thisObject as? View ?: return@runCatching
                    if (!clazz.isInstance(window)) return@runCatching
                    val event = chain.args[0] as? MotionEvent ?: return@runCatching
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        trackers.remove(window)?.recycle()
                        releases.remove(window)
                        window.removeOnAttachStateChangeListener(touchDetach)
                        if (ExpandedGestureFollowHook.isEnabled() && trackers.size < 32) {
                            trackers[window] = VelocityTracker.obtain()
                            window.addOnAttachStateChangeListener(touchDetach)
                        }
                    }
                    val tracker = trackers[window] ?: return@runCatching
                    tracker.addMovement(event)
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        tracker.computeCurrentVelocity(1000)
                        val id = event.getPointerId(event.actionIndex)
                        releases[window] = Direction(tracker.getXVelocity(id), tracker.getYVelocity(id),
                            android.os.SystemClock.uptimeMillis())
                    }
                    if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        trackers.remove(window)?.recycle()
                        if (event.actionMasked == MotionEvent.ACTION_CANCEL) releases.remove(window)
                    }
                }
                chain.proceed()
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
        // Velocity is px/s; project 80ms of release momentum into the existing distance tiers.
        val projectedX = direction.x * .08f
        val projectedY = direction.y * .08f
        val desiredX = limit * tanh(projectedX * response / limit)
        // Stronger horizontal response, retaining the selected tier's distance limit.
        val horizontalLimit = limit * 1.5f
        val enhancedX = horizontalLimit * tanh(projectedX * response * 4f / horizontalLimit)
        val desiredY = limit * tanh(projectedY * response / limit)
        // The arc has a perpendicular bend, so reserve its 25% envelope too. Keep
        // upward throws within the available top margin instead of exiting the screen.
        val location = IntArray(2)
        background.getLocationOnScreen(location)
        val rectTop = (getter(background, "getActualTop") as? Number)?.toFloat() ?: 0f
        val availableTop = (location[1] + rectTop - 2f * density).coerceAtLeast(0f)
        val upwardEnvelope = (-desiredY).coerceAtLeast(0f) + kotlin.math.abs(desiredX) * .25f
        val fit = if (upwardEnvelope > 0f) (availableTop / upwardEnvelope).coerceIn(0f, 1f) else 1f
        // Top clearance only constrains vertical motion. Applying it to X also
        // suppresses horizontal throws near the status bar even with a fast release.
        val dx = enhancedX
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
