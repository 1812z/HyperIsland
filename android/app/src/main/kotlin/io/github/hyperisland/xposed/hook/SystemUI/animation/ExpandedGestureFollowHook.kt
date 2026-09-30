package io.github.hyperisland.xposed.hook.SystemUI.animation

import android.view.View
import io.github.hyperisland.data.ExpandedCollapsePreferences
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.tanh

/** Adds bounded cross-axis movement to the system's expanded upward-swipe AnimState. */
object ExpandedGestureFollowHook : BaseHook() {
    private data class Offset(val x: Float, val y: Float)
    private val offset = ThreadLocal<Offset>()
    private val rawSwipe = ThreadLocal<Offset>()
    private val hooked = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()),
    )
    @Volatile private var enabled = false

    override fun getTag() = "HyperIsland[GestureFollow]"
    override fun onConfigChanged() {
        enabled = ConfigManager.getBoolean(ExpandedCollapsePreferences.GESTURE_FOLLOW, false)
    }

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        onConfigChanged()
        install(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { install(module, it) }
    }

    private fun install(module: XposedModule, loader: ClassLoader) {
        installRawSwipe(module, loader)
        ExpandedMiniWindowFollowHook.install(module, loader)
        ExpandedParabolicAnimationHook.install(module, loader)
        val candidates = listOf("anim.ui.animator.IslandSwipeAnimator",
            "anim.p110ui.animator.IslandSwipeAnimator", "anim.p120ui.animator.IslandSwipeAnimator",
            "anim.DynamicIslandAnimationDelegate")
        for (name in candidates) {
            val clazz = runCatching {
                Class.forName("miui.systemui.dynamicisland.$name", false, loader)
            }.getOrNull() ?: continue
            if (hooked.contains(clazz)) continue
            runCatching {
                val swipe = clazz.declaredMethods.firstOrNull {
                    it.name == "onSwipe" && it.parameterCount == 5 &&
                        it.parameterTypes[3] == Float::class.javaPrimitiveType &&
                        it.parameterTypes[4] == Float::class.javaPrimitiveType
                } ?: return@runCatching
                // Verify this is the implementation that builds the vertical gesture state.
                clazz.getDeclaredMethod("swipeUpExpandedAnimation", Float::class.javaPrimitiveType!!)
                val delegate = loader.loadClass("miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate")
                val companion = delegate.getDeclaredField("Companion").get(null)
                val containerX = companion.javaClass.getMethod("getCONTAINER_X").invoke(companion)
                val containerY = companion.javaClass.getMethod("getCONTAINER_TRANS_Y").invoke(companion)
                val state = loader.loadClass("miuix.animation.controller.AnimState")
                val property = loader.loadClass("miuix.animation.property.FloatProperty")
                val add = state.getDeclaredMethod("add", property, Float::class.javaPrimitiveType!!, LongArray::class.java)
                val addInt = state.getDeclaredMethod("add", property, Int::class.javaPrimitiveType!!, LongArray::class.java)
                if (hooked.add(state)) {
                    module.hook(add).intercept { chain ->
                        val movement = offset.get() ?: return@intercept chain.proceed()
                        val shift = when (chain.args[0]) {
                            containerX -> movement.x
                            containerY -> movement.y
                            else -> return@intercept chain.proceed()
                        }
                        val args = chain.args.toTypedArray()
                        args[1] = (args[1] as Number).toFloat() + shift
                        chain.proceed(args)
                    }
                    module.hook(addInt).intercept { chain ->
                        val movement = offset.get() ?: return@intercept chain.proceed()
                        val shift = when (chain.args[0]) {
                            containerX -> movement.x
                            containerY -> movement.y
                            else -> return@intercept chain.proceed()
                        }
                        if (shift == 0f) return@intercept chain.proceed()
                        // Stock CONTAINER_TRANS_Y is an integer expression during side swipes.
                        // Route it through the float overload to retain subpixel following,
                        // suspending the scope so the float hook cannot add the offset twice.
                        offset.remove()
                        try {
                            add.invoke(chain.thisObject, chain.args[0],
                                (chain.args[1] as Number).toFloat() + shift, chain.args[2])
                        } finally {
                            offset.set(movement)
                        }
                    }
                }
                if (!hooked.add(clazz)) return@runCatching
                module.hook(swipe).intercept { chain ->
                    val x = runCatching {
                        if (!enabled) return@runCatching null
                        val view = chain.args[0] as? View ?: return@runCatching null
                        if (chain.args[1]?.javaClass?.simpleName != "Expanded" ||
                            chain.args[2]?.javaClass?.simpleName != "BigIsland") return@runCatching null
                        // ExpandedStateHandler zeroes X before dispatching an upward swipe.
                        val dx = rawSwipe.get()?.x ?: (chain.args[3] as Number).toFloat()
                        val rawY = rawSwipe.get()?.y ?: (chain.args[4] as Number).toFloat()
                        val dy = (chain.args[4] as Number).toFloat()
                        val dispatchedX = (chain.args[3] as Number).toFloat()
                        if (!dx.isFinite() || !rawY.isFinite() || !dy.isFinite()) {
                            return@runCatching null
                        }
                        when {
                            abs(dispatchedX) > abs(dy) -> Offset(0f, bounded(view, rawY))
                            dy < 0f -> Offset(bounded(view, dx), 0f)
                            else -> return@runCatching null
                        }
                    }.getOrNull() ?: return@intercept chain.proceed()
                    val previous = offset.get()
                    offset.set(x)
                    try {
                        // CONTAINER_X moves content, outline, outer background and glow together.
                        // System reset/transition states remain free to animate back to zero.
                        chain.proceed()
                    } finally {
                        if (previous == null) offset.remove() else offset.set(previous)
                    }
                }
                log(module, "installed on ${clazz.name}")
            }.onFailure { log(module, "${clazz.name}: ${it.message}") }
        }
    }

    private fun installRawSwipe(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val clazz = loader.loadClass("miui.systemui.dynamicisland.event.handler.ExpandedStateHandler")
            if (hooked.contains(clazz)) return@runCatching
            val method = clazz.declaredMethods.firstOrNull {
                it.name == "onSwipe" && it.parameterCount == 6 &&
                    it.parameterTypes[0] == Float::class.javaPrimitiveType &&
                    it.parameterTypes[1] == Float::class.javaPrimitiveType
            } ?: return@runCatching
            module.hook(method).intercept { chain ->
                if (!enabled) return@intercept chain.proceed()
                val x = chain.args[0] as? Float ?: return@intercept chain.proceed()
                val y = chain.args[1] as? Float ?: return@intercept chain.proceed()
                (chain.args[4] as? View)?.let { ExpandedParabolicAnimationHook.record(it, x, y) }
                val previous = rawSwipe.get()
                rawSwipe.set(Offset(x, y))
                try {
                    chain.proceed()
                } finally {
                    if (previous == null) rawSwipe.remove() else rawSwipe.set(previous)
                }
            }
            hooked.add(clazz)
            log(module, "capturing raw swipe on ${clazz.name}")
        }.onFailure { log(module, "raw swipe hook unavailable: ${it.message}") }
    }

    internal fun isEnabled() = enabled
    internal fun bounded(view: View, distance: Float): Float {
        if (!distance.isFinite()) return 0f
        val limit = 16f * view.resources.displayMetrics.density
        return limit * tanh(distance * .2f / limit)
    }
}
