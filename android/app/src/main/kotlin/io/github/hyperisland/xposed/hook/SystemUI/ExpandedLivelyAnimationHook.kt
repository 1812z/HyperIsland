package io.github.hyperisland.xposed.hook.SystemUI

import android.view.View
import io.github.hyperisland.data.ExpandedCollapsePreferences
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap

/** Synchronized content/outline morph with configurable nonlinear spring and rebound. */
object ExpandedLivelyAnimationHook : BaseHook() {
    private data class Transition(
        val expanding: Boolean, val bounce: Boolean, val curve: String, val keepContentSize: Boolean,
    )
    private val transition = ThreadLocal<Transition>()
    private val hooked = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()),
    )
    @Volatile private var animationType = "system"
    @Volatile private var rebound = true
    @Volatile private var curve = "balanced"
    @Volatile private var keepContentSize = false

    override fun getTag() = "HyperIsland[ExpandedLively]"
    override fun onConfigChanged() {
        animationType = ConfigManager.getString(ExpandedCollapsePreferences.TYPE, "system")
        curve = ConfigManager.getString(ExpandedCollapsePreferences.CURVE, "balanced")
        keepContentSize = ConfigManager.getBoolean(ExpandedCollapsePreferences.KEEP_CONTENT_SIZE, false)
        rebound = !keepContentSize && ConfigManager.getBoolean(ExpandedCollapsePreferences.REBOUND, true)
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
        val delegate = runCatching {
            Class.forName("miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate", false, loader)
        }.getOrNull() ?: return
        if (hooked.contains(delegate)) return
        try {
            val executor = sequenceOf("ui", "p110ui", "p120ui").mapNotNull {
                runCatching { Class.forName(
                    "miui.systemui.dynamicisland.anim.$it.animator.IslandTransitionExecutor", false, loader,
                ) }.getOrNull()
            }.firstOrNull() ?: return
            val state = Class.forName("miuix.animation.controller.AnimState", false, loader)
            val cfgClass = Class.forName("miuix.animation.base.AnimConfig", false, loader)
            val property = Class.forName("miuix.animation.property.FloatProperty", false, loader)
            val ease = Class.forName("miuix.animation.utils.EaseManager", false, loader)
            val easeStyle = Class.forName("miuix.animation.utils.EaseManager\$EaseStyle", false, loader)
            val companion = delegate.getDeclaredField("Companion").get(null)
            fun prop(name: String): Any = companion.javaClass.getMethod("get$name").invoke(companion)
            val scaleX = prop("EXPANDED_SCALE_X")
            val scaleY = prop("EXPANDED_SCALE_Y")
            val transY = prop("EXPANDED_TRANS_Y")
            val bigScale = prop("BIG_ISLAND_SCALE")
            val bigTransY = prop("BIG_ISLAND_TRANS_Y")
            val smallTransY = prop("SMALL_ISLAND_TRANS_Y")
            val expandedAlpha = prop("EXPANDED_ALPHA")
            val expandedBlur = prop("EXPANDED_BLUR")
            val geometry = listOf("CONTAINER_TRANS_Y", "CONTAINER_CLIP_START_PROGRESS",
                "CONTAINER_CLIP_END_PROGRESS", "CONTAINER_CLIP_TOP_PROGRESS",
                "CONTAINER_CLIP_BOTTOM_PROGRESS").map(::prop)
            val copyState = state.getMethod("set", state)
            val stateConstructor = state.getConstructor()
            val add = state.getMethod("add", property, Float::class.javaPrimitiveType, LongArray::class.java)
            val configCopy = cfgClass.getConstructor(cfgClass)
            val special = cfgClass.getMethod("setSpecial", property, easeStyle, FloatArray::class.java)
            val delayedSpecial = cfgClass.getMethod("setSpecial", property, easeStyle,
                Long::class.javaPrimitiveType, FloatArray::class.java)
            val getStyle = ease.getMethod("getStyle", Int::class.javaPrimitiveType, FloatArray::class.java)
            val execute = executor.declaredMethods.firstOrNull {
                it.name == "execute" && it.parameterCount == 10 && it.parameterTypes[0] == state
            } ?: return
            val names = mapOf("bigIslandToExpandedAnimation" to true,
                "smallIslandToExpandedAnimation" to true, "expandedToBigIslandAnimation" to false,
                "expandedToSmallIslandAnimation" to false)
            val sources = delegate.declaredMethods.filter {
                it.name in names && it.parameterCount == 1
            }
            if (sources.isEmpty()) return
            if (!hooked.add(delegate)) return
            module.hook(execute).intercept { chain ->
                val active = transition.get() ?: return@intercept chain.proceed()
                // Preserve the system's no-animation path (rotation, initialization, etc.).
                if (chain.args[3] == true) return@intercept chain.proceed()
                val args = chain.args.toTypedArray()
                val prepared = runCatching {
                    if (active.keepContentSize) {
                        // Independent copies keep the system's cached endpoint states intact.
                        val target = stateConstructor.newInstance()
                        copyState.invoke(target, args[0])
                        val initial = stateConstructor.newInstance()
                        args[2]?.let { copyState.invoke(initial, it) }
                        listOf(scaleX, scaleY, bigScale).forEach { key ->
                            add.invoke(target, key, 1f, longArrayOf())
                            add.invoke(initial, key, 1f, longArrayOf())
                        }
                        args[0] = target
                        args[2] = initial
                    }
                    // Keep the system's geometry-derived scale and translation endpoints.
                    // Content and outline use the SAME spring, including its overshoot.
                    val config = args[1]?.let { configCopy.newInstance(it) }
                        ?: cfgClass.getConstructor().newInstance()
                    // One response for both content and outline prevents split rebounds.
                    val response = when (active.curve) {
                        "snappy" -> if (active.bounce) .40f else .36f
                        "gentle" -> if (active.bounce) .48f else .52f
                        else -> .44f
                    }
                    val damping = if (!active.bounce) 1f else when (active.curve) {
                        "snappy" -> .70f
                        "gentle" -> .74f
                        else -> .72f
                    }
                    val spring = getStyle.invoke(null, -2,
                        floatArrayOf(damping,
                            response + if (active.expanding) 0f else .04f))
                    (geometry + listOf(transY, scaleX, scaleY, bigScale, bigTransY, smallTransY)).forEach {
                        special.invoke(config, it, spring, floatArrayOf())
                    }
                    // A monotonic fade preserves readability while geometry springs. On
                    // collapse, briefly let the movement register before content fades away.
                    // Non-rebound mode also needs the selected pacing on alpha/blur:
                    // fixed fade timing masked geometry differences, especially when
                    // keep-content-size disables content scaling altogether.
                    val fadeResponse = if (active.bounce) .30f else response * .75f
                    val fade = getStyle.invoke(null, -2, floatArrayOf(1f, fadeResponse))
                    val fadeDelay = if (active.bounce) {
                        if (active.expanding) 45L else 85L
                    } else {
                        (response * if (active.expanding) 100f else 80f).toLong()
                    }
                    listOf(expandedAlpha, expandedBlur).forEach {
                        delayedSpecial.invoke(config, it, fade,
                            fadeDelay,
                            floatArrayOf())
                    }
                    args[1] = config
                }.isSuccess
                if (prepared) chain.proceed(args) else chain.proceed()
            }
            sources.forEach { method ->
                module.hook(method).intercept { chain ->
                    val view = chain.args.firstOrNull() as? View
                    // Phone notification morphs only. App launch/miniwindows retain their own
                    // fake-view window animation and do not enter these four source methods.
                    val type = animationType
                    if (type != "lively" || view == null ||
                        view.resources.configuration.smallestScreenWidthDp >= 600
                    ) return@intercept chain.proceed()
                    val previous = transition.get()
                    transition.set(Transition(names.getValue(method.name), rebound, curve, keepContentSize))
                    try {
                        chain.proceed()
                    } finally {
                        if (previous == null) transition.remove() else transition.set(previous)
                    }
                }
            }
            log(module, "installed: synchronized content and outline spring morph")
        } catch (e: Throwable) {
            logWarn(module, "installation failed: ${e.message}")
        }
    }
}
