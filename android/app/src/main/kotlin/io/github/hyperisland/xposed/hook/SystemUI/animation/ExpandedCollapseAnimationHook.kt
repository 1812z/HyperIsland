package io.github.hyperisland.xposed.hook.SystemUI.animation

import io.github.hyperisland.data.ExpandedCollapsePreferences as Keys
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap

/** Rewrites alpha and blur authored by expanded collapse gestures in either axis. */
object ExpandedCollapseAnimationHook : BaseHook() {
    private data class Config(
        val enabled: Boolean = false,
        val transparencyStart: Float = 0f,
        val transparencyEnd: Float = .8f,
        val blurStart: Float = 0f,
        val blurEnd: Float = .8f,
    )

    private data class Gesture(val config: Config, var progress: Float = 0f)
    private val gesture = ThreadLocal<Gesture>()
    private val hookedClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()),
    )
    @Volatile private var config = Config()

    override fun getTag() = "HyperIsland[ExpandedCollapse]"

    override fun onConfigChanged() {
        fun percent(key: String) = ConfigManager.getInt(key, Keys.defaultPercent(key).toInt())
            .coerceIn(0, 100) / 100f
        config = Config(ConfigManager.getBoolean(Keys.ENABLED, false),
            percent(Keys.TRANSPARENCY_START), percent(Keys.TRANSPARENCY_END),
            percent(Keys.BLUR_START), percent(Keys.BLUR_END))
    }

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        onConfigChanged()
        install(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) {
            install(module, it)
        }
    }

    private fun install(module: XposedModule, loader: ClassLoader) {
        // OS4 extracted the swipe code; OS3 retains it on the animation delegate.
        val candidates = listOf("anim.ui.animator.IslandSwipeAnimator",
            "anim.p110ui.animator.IslandSwipeAnimator", "anim.p120ui.animator.IslandSwipeAnimator",
            "anim.DynamicIslandAnimationDelegate")
        for (name in candidates) {
            val clazz = runCatching {
                Class.forName("miui.systemui.dynamicisland.$name", false, loader)
            }.getOrNull() ?: continue
            if (hookedClasses.contains(clazz)) continue
            try {
                val floatType = Float::class.javaPrimitiveType!!
                // SystemUI routes both left and right swipes through swipeLeftExpandedAnimation.
                // Discover each entry independently so an absent horizontal API on older
                // builds does not prevent installing the vertical gesture customization.
                val methods = listOf("swipeUpExpandedAnimation", "swipeLeftExpandedAnimation")
                    .mapNotNull { runCatching { clazz.getDeclaredMethod(it, floatType) }.getOrNull() }
                if (methods.isEmpty()) continue
                val calculate = clazz.getDeclaredMethod("calculateSwipeAlpha", floatType)
                val delegate = Class.forName(
                    "miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate", false, loader)
                val companion = delegate.getDeclaredField("Companion").get(null)
                val alpha = companion.javaClass.getMethod("getEXPANDED_ALPHA").invoke(companion)
                val blur = companion.javaClass.getMethod("getEXPANDED_BLUR").invoke(companion)
                val state = Class.forName("miuix.animation.controller.AnimState", false, loader)
                val property = Class.forName("miuix.animation.property.FloatProperty", false, loader)
                val add = state.getDeclaredMethod("add", property, floatType, LongArray::class.java)
                if (hookedClasses.add(state)) {
                    module.hook(add).intercept { chain ->
                        val active = gesture.get() ?: return@intercept chain.proceed()
                        val cfg = active.config
                        val value = when (chain.args[0]) {
                            alpha -> 1f - interpolate(cfg.transparencyStart, cfg.transparencyEnd, active.progress)
                            blur -> interpolate(cfg.blurStart, cfg.blurEnd, active.progress)
                            else -> return@intercept chain.proceed()
                        }
                        val args = chain.args.toTypedArray()
                        args[1] = value
                        chain.proceed(args)
                    }
                }
                if (!hookedClasses.add(clazz)) continue
                module.hook(calculate).intercept { chain ->
                    val result = chain.proceed()
                    // Stock alpha goes 1 -> .2. Normalize that same curve, without coupling
                    // the configured alpha and blur (stock uses blur = 1 - alpha).
                    (result as? Float)?.let { alphaValue ->
                        gesture.get()?.progress = ((1f - alphaValue) / .8f).coerceIn(0f, 1f)
                    }
                    result
                }
                methods.forEach { method ->
                    module.hook(method).intercept { chain ->
                        val snapshot = config
                        if (!snapshot.enabled) return@intercept chain.proceed()
                        val previous = gesture.get()
                        gesture.set(Gesture(snapshot))
                        try {
                            chain.proceed()
                        } finally {
                            if (previous == null) gesture.remove() else gesture.set(previous)
                        }
                    }
                }
                log(module) { "installed on ${clazz.name}" }
            } catch (e: NoSuchMethodException) {
                // A delegate without swipe methods is expected on OS4.
            } catch (e: Throwable) {
                logWarn(module, "${clazz.name}: ${e.message}")
            }
        }
    }

    private fun interpolate(start: Float, end: Float, progress: Float) =
        start + (end - start) * progress
}
