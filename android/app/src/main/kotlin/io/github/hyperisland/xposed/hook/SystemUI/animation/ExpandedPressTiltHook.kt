package io.github.hyperisland.xposed.hook.SystemUI.animation

import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import io.github.hyperisland.data.ExpandedCollapsePreferences as Keys
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** Native perspective pressure, leaving shadow rendering and stock scale springs intact. */
object ExpandedPressTiltHook : BaseHook() {
    private class Target(view: View) {
        val view = WeakReference(view)
        val pivotExplicit = view.isPivotSet
        val pivotX = view.pivotX
        val pivotY = view.pivotY
        val rotationX = view.rotationX
        val rotationY = view.rotationY
        val cameraDistance = view.cameraDistance
    }
    private class Press(window: View, owner: View, card: View, val x: Float, val y: Float,
        val dx: Float, val dy: Float, val targets: List<Target>) {
        val window = WeakReference(window)
        val owner = WeakReference(owner)
        val card = WeakReference(card)
        var progress = 0f
        var released = false
        var animator: ValueAnimator? = null
        var preDraw: ViewTreeObserver.OnPreDrawListener? = null
    }
    private val presses = WeakHashMap<View, Press>()
    private val hooked = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()),
    )
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var enabled = false
    private val detach = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {}
        override fun onViewDetachedFromWindow(v: View) {
            presses.values.toList().filter { press ->
                press.window.get() === v || press.targets.any { it.view.get() === v }
            }.forEach(::stop)
        }
    }

    override fun getTag() = "HyperIsland[PressTilt]"
    override fun onConfigChanged() {
        enabled = ConfigManager.getString(Keys.TYPE, "system") == "lively" &&
            ConfigManager.getBoolean(Keys.PRESS_TILT, false)
        if (!enabled) main.post { if (!enabled) presses.values.toList().forEach(::stop) }
    }
    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        onConfigChanged()
        install(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { install(module, it) }
    }
    private fun install(module: XposedModule, loader: ClassLoader) {
        if (!HookUtils.isIslandLoaderReady(loader)) return
        val clazz = runCatching {
            loader.loadClass("miui.systemui.dynamicisland.window.DynamicIslandWindowView")
        }.getOrNull() ?: return
        if (!hooked.add(clazz)) return
        runCatching {
            module.hook(clazz.getMethod("dispatchTouchEvent", MotionEvent::class.java)).intercept { chain ->
                val window = chain.thisObject as? View
                val event = chain.args[0] as? MotionEvent
                if (window != null && event != null && (enabled || presses.containsKey(window))) {
                    runCatching { onTouch(window, event) }.onFailure { error ->
                        presses[window]?.let(::stop)
                        logFailureOnce(module, "pressure") { "pressure unavailable: ${error.message}" }
                    }
                }
                chain.proceed()
            }
        }.onFailure {
            hooked.remove(clazz)
            logFailureOnce(module, clazz.name) { "touch hook unavailable: ${it.message}" }
        }
    }
    private fun onTouch(window: View, event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                presses[window]?.let(::stop)
                if (!enabled || presses.size >= 16) return
                val owner = findOwner(window, event.rawX, event.rawY) ?: return
                val card = getter(owner, "getExpandedView") as? View ?: return
                val location = IntArray(2)
                card.getLocationOnScreen(location)
                fun axis(position: Float, origin: Int, size: Int): Float {
                    val value = ((position - origin) / size * 2f - 1f).coerceIn(-1f, 1f)
                    // Ignore the center band; stock press/rebound handles it.
                    // Ease-out gain keeps side presses visible at the same maximum angle.
                    val amount = ((abs(value) - .20f) / .80f).coerceIn(0f, 1f)
                    return (if (value < 0f) -1f else 1f) *
                        (1f - (1f - amount) * (1f - amount))
                }
                val dx = axis(event.rawX, location[0], card.width)
                val dy = axis(event.rawY, location[1], card.height)
                if (dx == 0f && dy == 0f) return
                val background = getter(owner, "getBackgroundView") as? View ?: return
                val hosts = mutableListOf(background)
                (getter(owner, "getFakeView") as? View)?.let {
                    if (it !== background && !isDescendant(it, background)) hosts += it
                }
                val press = Press(window, owner, card, event.rawX, event.rawY, dx, dy, hosts.map(::Target))
                presses[window] = press
                window.addOnAttachStateChangeListener(detach)
                hosts.forEach { it.addOnAttachStateChangeListener(detach) }
                val reference = WeakReference(press)
                press.preDraw = ViewTreeObserver.OnPreDrawListener {
                    reference.get()?.let { active ->
                        val content = active.owner.get()
                        if (!enabled || content == null || !isExpanded(content)) stop(active)
                        else if (runCatching { update(active) }.isFailure) stop(active)
                    }
                    true
                }
                press.preDraw?.let { window.viewTreeObserver.addOnPreDrawListener(it) }
                animate(press, 1f, false)
            }
            MotionEvent.ACTION_MOVE -> presses[window]?.let {
                val slop = ViewConfiguration.get(window.context).scaledTouchSlop
                if (abs(event.rawX - it.x) > slop || abs(event.rawY - it.y) > slop) release(it, false)
            }
            MotionEvent.ACTION_UP -> presses[window]?.let { release(it, ExpandedLivelyAnimationHook.isReboundEnabled()) }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> presses[window]?.let { release(it, false) }
        }
    }
    private fun release(press: Press, bounce: Boolean) {
        if (press.released) return
        press.released = true
        animate(press, 0f, bounce)
    }
    private fun animate(press: Press, end: Float, bounce: Boolean) {
        press.animator?.cancel()
        val start = press.progress
        val animator = ValueAnimator.ofFloat(0f, 1f)
        press.animator = animator
        animator.duration = if (end == 1f) 180L else if (bounce) 420L else 240L
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener {
            val owner = press.owner.get()
            if (!enabled || owner == null || !isExpanded(owner) || press.window.get()?.isAttachedToWindow != true) {
                stop(press)
                return@addUpdateListener
            }
            val t = it.animatedValue as Float
            val remaining = if (bounce) exp(-7f * t) * (cos(11f * t) + 7f / 11f * sin(11f * t))
                else (1f - t) * (1f - t) * (1f + 2f * t)
            press.progress = if (t == 1f) end else end + (start - end) * remaining
            if (runCatching { update(press) }.isFailure) stop(press)
            if (t == 1f && end == 0f) stop(press)
        }
        animator.start()
    }
    private fun update(press: Press) {
        val card = press.card.get() ?: return stop(press)
        // Use layout coordinates, not getLocationOnScreen after rotation: transformed
        // bounds would feed back into the pivot and make the fixed edge drift.
        val anchorX = if (press.dx > 0f) 0f else if (press.dx < 0f) card.width.toFloat() else card.width / 2f
        val anchorY = if (press.dy > 0f) 0f else if (press.dy < 0f) card.height.toFloat() else card.height / 2f
        val location = IntArray(2)
        card.getLocationInWindow(location)
        press.targets.forEach { target ->
            val host = target.view.get() ?: return@forEach
            var x = anchorX
            var y = anchorY
            var child: View = card
            while (child !== host) {
                val parent = child.parent as? View ?: break
                x += child.left - parent.scrollX
                y += child.top - parent.scrollY
                child = parent
            }
            if (child !== host) {
                val hostLocation = IntArray(2)
                host.getLocationInWindow(hostLocation)
                x = location[0] - hostLocation[0] + anchorX
                y = location[1] - hostLocation[1] + anchorY
            }
            host.pivotX = x
            host.pivotY = y
            host.cameraDistance = 1600f * host.resources.displayMetrics.density
            host.rotationX = target.rotationX - 2f * press.dy * press.progress
            host.rotationY = target.rotationY + 2f * press.dx * press.progress
        }
    }
    private fun stop(press: Press) {
        press.animator?.cancel()
        press.animator = null
        press.window.get()?.let { window ->
            if (presses[window] === press) presses.remove(window)
            window.removeOnAttachStateChangeListener(detach)
            press.preDraw?.let { if (window.viewTreeObserver.isAlive) window.viewTreeObserver.removeOnPreDrawListener(it) }
        }
        press.preDraw = null
        press.targets.forEach { target -> target.view.get()?.let { host ->
            runCatching {
                host.rotationX = target.rotationX
                host.rotationY = target.rotationY
                host.cameraDistance = target.cameraDistance
                if (target.pivotExplicit) {
                    host.pivotX = target.pivotX
                    host.pivotY = target.pivotY
                } else host.resetPivot()
            }
            host.removeOnAttachStateChangeListener(detach)
        } }
    }
    private fun findOwner(view: View, x: Float, y: Float): View? {
        if (view.visibility != View.VISIBLE || view.alpha <= 0f) return null
        if (view.javaClass.simpleName == "DynamicIslandContentView" && isExpanded(view)) {
            val card = getter(view, "getExpandedView") as? View ?: return null
            if (!card.isShown || card.width <= 0 || card.height <= 0) return null
            val location = IntArray(2)
            card.getLocationOnScreen(location)
            if (x >= location[0] && x <= location[0] + card.width && y >= location[1] && y <= location[1] + card.height) return view
        }
        if (view is ViewGroup) for (i in view.childCount - 1 downTo 0) {
            findOwner(view.getChildAt(i), x, y)?.let { return it }
        }
        return null
    }
    private fun isExpanded(view: View) = getter(view, "getState")?.javaClass?.simpleName == "Expanded"
    private fun isDescendant(view: View, ancestor: View): Boolean {
        var parent = view.parent
        while (parent is View) {
            if (parent === ancestor) return true
            parent = parent.parent
        }
        return false
    }
    private fun getter(view: View, name: String): Any? = runCatching { view.javaClass.getMethod(name).invoke(view) }.getOrNull()
}
