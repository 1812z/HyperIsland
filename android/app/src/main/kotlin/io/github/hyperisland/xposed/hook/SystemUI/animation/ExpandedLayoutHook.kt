package io.github.hyperisland.xposed.hook.SystemUI.animation

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import io.github.hyperisland.data.ExpandedCollapsePreferences
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.SystemUiReflection.findMethod
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/** Aligns focus expansion with the computed island top, without changing persisted offsets. */
object ExpandedLayoutHook : BaseHook() {
    private const val BASE = "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private const val WINDOW = "miui.systemui.dynamicisland.window.DynamicIslandWindowView"
    private val hooked = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()),
    )
    private data class Watch(
        val preDraw: ViewTreeObserver.OnPreDrawListener,
        val attach: View.OnAttachStateChangeListener,
        var observer: WeakReference<ViewTreeObserver>? = null,
    )
    private val watches = WeakHashMap<View, Watch>()
    private val lifted = WeakHashMap<View, Float>()
    private data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)
    private val originalPadding = WeakHashMap<View, Padding>()
    private data class FixedHeight(var original: Int, var applied: Int)
    private val fixedHeights = WeakHashMap<View, FixedHeight>()
    private val excludedStates = setOf(
        "AppExpanded", "MiniWindowExpanded", "SubAppExpanded", "SubMiniWindowExpanded",
    )
    private data class Accessors(
        val state: Method?,
        val lastState: Method?,
        val animating: Method?,
        val expanded: Method?,
        val fakeExpanded: Method?,
    )
    private val accessors = WeakHashMap<Class<*>, Accessors>()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var enabled = false
    @Volatile private var contentTopGapDp = 0
    @Volatile private var topGapDp = -1

    override fun getTag() = "HyperIsland[ExpandedLayout]"

    override fun onConfigChanged() {
        topGapDp = ConfigManager.getInt(ExpandedCollapsePreferences.TOP_GAP,
            ExpandedCollapsePreferences.DEFAULT_TOP_GAP.toInt())
        contentTopGapDp = ConfigManager.getInt(ExpandedCollapsePreferences.CONTENT_TOP_GAP,
            ExpandedCollapsePreferences.DEFAULT_CONTENT_TOP_GAP.toInt())
        enabled = topGapDp >= 0 || contentTopGapDp > 0
        mainHandler.post {
            runCatching {
            if (!enabled) {
                lifted.keys.toList().forEach(::restore)
                originalPadding.keys.toList().forEach(::restorePadding)
            }
            watches.keys.toList().forEach { applyContentGap(it); it.requestLayout(); it.invalidate() }
            }
        }
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
        val base = runCatching { Class.forName(BASE, false, loader) }.getOrNull() ?: return
        if (hooked.contains(base)) return
        try {
            val expandedY = base.getDeclaredMethod("getExpandedViewY")
            val islandTop = base.getDeclaredMethod("getIslandViewMarginTop")
            if (expandedY.returnType != Int::class.javaPrimitiveType ||
                islandTop.returnType != Int::class.javaPrimitiveType
            ) return
            // Resolve state/animation APIs before changing either positioning or ordering.
            base.getDeclaredMethod("getState")
            base.getDeclaredMethod("getLastState")
            if (findMethod(base, "isAnimating") == null) return
            val updateState = base.declaredMethods.firstOrNull {
                it.name == "updateDarkLightMode" && it.parameterCount == 4
            } ?: return
            if (!hooked.add(base)) return
            module.hook(expandedY).intercept { chain ->
                val original = chain.proceed()
                val owner = chain.thisObject as? View ?: return@intercept original
                // Read the computed margin, not cutoutY, a resource constant or a stored dp
                // offset: island height, rotation and IslandTopOffsetHook already feed it.
                runCatching {
                    if (!supported(owner) || topGapDp < 0) original else {
                        val top = (islandTop.invoke(owner) as Number).toInt()
                        watch(owner)
                        top + (topGapDp * owner.resources.displayMetrics.density + .5f).toInt()
                    }
                }.getOrDefault(original)
            }
            module.hook(updateState).intercept { chain ->
                val owner = chain.thisObject as? View
                runCatching { if (owner != null && supported(owner)) {
                    watch(owner)
                    // Raise before the system submits the first expanded animation frame.
                    if (topGapDp >= 0 && chain.args.firstOrNull()?.javaClass?.simpleName == "Expanded") lift(owner)
                } }
                val result = chain.proceed()
                runCatching { if (owner != null) updateLayer(owner) }
                result
            }
            // updateExpandedSize() writes the measured expanded_view height back into the
            // notification child/fake View. Our top gap lives in expanded_view's padding, so that
            // measured total already contains the gap. Without compensation the child's fixed
            // height grows by the gap on every update: the content stays put while the bottom
            // blank area accumulates exactly once per new message.
            val updateExpandedSize = base.declaredMethods.firstOrNull {
                it.name == "updateExpandedSize" &&
                    it.parameterCount == 3 &&
                    it.parameterTypes.getOrNull(0) == Int::class.javaPrimitiveType &&
                    it.parameterTypes.getOrNull(1) == Int::class.javaPrimitiveType
            }
            if (updateExpandedSize != null) {
                module.hook(updateExpandedSize).intercept { chain ->
                    val owner = chain.thisObject as? View
                    val result = chain.proceed()
                    runCatching {
                        if (owner != null) compensateExpandedHeight(owner, chain.args.getOrNull(2))
                    }
                    result
                }
            }
            // updateExpandedView replaces the notification child before SystemUI measures
            // its height. Apply padding here rather than changing measured-height fields.
            listOf("DynamicIslandContentView", "DynamicIslandContentFakeView").forEach { name ->
                val clazz = runCatching {
                    Class.forName("miui.systemui.dynamicisland.window.content.$name", false, loader)
                }.getOrNull() ?: return@forEach
                clazz.declaredMethods.filter { it.name == "updateExpandedView" }.forEach { method ->
                    module.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        runCatching { (chain.thisObject as? View)?.let { owner ->
                            watch(owner)
                            applyContentGap(owner)
                        } }
                        result
                    }
                }
            }
            log(module) { "installed computed-top alignment for real/fake focus expansion" }
        } catch (e: Throwable) {
            logWarn(module, "installation failed: ${e.message}")
        }
    }

    private fun stateName(owner: View, getter: String): String? = runCatching {
        accessor(owner, getter)?.invoke(owner)?.javaClass?.simpleName
    }.getOrNull()

    private fun accessor(owner: View, getter: String): Method? {
        val clazz = owner.javaClass
        val cached = accessors[clazz]
        if (cached != null) {
            return when (getter) {
                "getState" -> cached.state
                "getLastState" -> cached.lastState
                "isAnimating" -> cached.animating
                "getExpandedView" -> cached.expanded
                "getFakeExpandedView" -> cached.fakeExpanded
                else -> findMethod(clazz, getter)
            }
        }
        if (accessors.size >= 64) return findMethod(clazz, getter)
        val resolved = Accessors(
            state = runCatching { findMethod(clazz, "getState") }.getOrNull(),
            lastState = runCatching { findMethod(clazz, "getLastState") }.getOrNull(),
            animating = runCatching { findMethod(clazz, "isAnimating") }.getOrNull(),
            expanded = runCatching { findMethod(clazz, "getExpandedView") }.getOrNull(),
            fakeExpanded = runCatching { findMethod(clazz, "getFakeExpandedView") }.getOrNull(),
        )
        accessors[clazz] = resolved
        return when (getter) {
            "getState" -> resolved.state
            "getLastState" -> resolved.lastState
            "isAnimating" -> resolved.animating
            "getExpandedView" -> resolved.expanded
            "getFakeExpandedView" -> resolved.fakeExpanded
            else -> findMethod(clazz, getter)
        }
    }

    private fun supported(owner: View): Boolean {
        if (!enabled || owner.resources.configuration.smallestScreenWidthDp >= 600) return false
        // App and miniwindow coordinates belong to the system's window-animation pipeline.
        return isSupportedState(stateName(owner, "getState"))
    }

    private fun isSupportedState(state: String?): Boolean = state !in excludedStates

    private fun watch(owner: View) {
        if (watches.containsKey(owner)) return
        // A bounded weak registry; listeners never capture their owning View.
        if (watches.size >= 64) return
        val ref = WeakReference(owner)
        val preDraw = ViewTreeObserver.OnPreDrawListener {
            runCatching { ref.get()?.let(::updateLayer) }
            true
        }
        val attach = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                runCatching { register(v) }
            }
            override fun onViewDetachedFromWindow(v: View) {
                runCatching {
                restoreOwner(v)
                contentSlot(v)?.let(::restorePadding)
                val watch = watches.remove(v) ?: return@runCatching
                watch.observer?.get()?.takeIf { it.isAlive }?.removeOnPreDrawListener(watch.preDraw)
                v.removeOnAttachStateChangeListener(this)
                }
            }
        }
        watches[owner] = Watch(preDraw, attach)
        owner.addOnAttachStateChangeListener(attach)
        if (owner.isAttachedToWindow) register(owner)
    }

    private fun register(owner: View) {
        val watch = watches[owner] ?: return
        val observer = owner.viewTreeObserver
        if (!observer.isAlive || watch.observer?.get() === observer) return
        watch.observer?.get()?.takeIf { it.isAlive }?.removeOnPreDrawListener(watch.preDraw)
        observer.addOnPreDrawListener(watch.preDraw)
        watch.observer = WeakReference(observer)
    }

    private fun updateLayer(owner: View) {
        val state = stateName(owner, "getState")
        if (!enabled || owner.resources.configuration.smallestScreenWidthDp >= 600 ||
            !isSupportedState(state)
        ) {
            applyContentGap(owner)
            restoreOwner(owner)
            return
        }
        applyContentGap(owner, supportedOverride = true)
        val expanding = state == "Expanded"
        val animating = runCatching {
            accessor(owner, "isAnimating")?.invoke(owner) == true
        }.getOrDefault(false)
        val leaving = stateName(owner, "getLastState") == "Expanded" && animating
        if (topGapDp >= 0 && (expanding || leaving)) lift(owner) else restoreOwner(owner)
    }

    private fun contentSlot(owner: View): View? = runCatching {
        val getter = if (owner.javaClass.simpleName == "DynamicIslandContentFakeView") {
            "getFakeExpandedView"
        } else "getExpandedView"
        val slot = accessor(owner, getter)?.invoke(owner) as? View
        slot
    }.getOrNull()

    private fun applyContentGap(owner: View, supportedOverride: Boolean? = null) {
        val slot = contentSlot(owner) ?: return
        if (supportedOverride != true && !supported(owner) || contentTopGapDp == 0) {
            restorePadding(slot)
            return
        }
        val saved = originalPadding[slot] ?: run {
            if (originalPadding.size >= 64) return
            Padding(slot.paddingLeft, slot.paddingTop, slot.paddingRight, slot.paddingBottom)
                .also { originalPadding[slot] = it }
        }
        val extra = (contentTopGapDp * slot.resources.displayMetrics.density + .5f).toInt()
        val top = saved.top + extra
        // Fixed-height hosts do not grow when padding changes (common for media cards).
        // Expand only their outer slot; keep notification child dimensions untouched.
        val params = slot.layoutParams
        if (params != null && params.height > 0) {
            val height = fixedHeights[slot] ?: FixedHeight(params.height, params.height)
                .also { fixedHeights[slot] = it }
            if (params.height != height.applied) height.original = params.height
            val desired = height.original + extra
            if (params.height != desired) {
                params.height = desired
                slot.layoutParams = params
            }
            height.applied = desired
        } else {
            fixedHeights.remove(slot)
        }
        if (slot.paddingTop != top) {
            slot.setPadding(saved.left, top, saved.right, saved.bottom)
        }
    }

    private fun restorePadding(slot: View) {
        fixedHeights.remove(slot)?.let { saved ->
            slot.layoutParams?.let { params ->
                if (params.height == saved.applied) {
                    params.height = saved.original
                    slot.layoutParams = params
                }
            }
        }
        originalPadding.remove(slot)?.let {
            slot.setPadding(it.left, it.top, it.right, it.bottom)
        }
    }

    /**
     * updateExpandedSize() writes expanded_view's measured height back into the notification
     * child and fake View. That measured height already includes the gap padding applied to the
     * slot, so without compensation the child's fixed height grows by the gap on every content
     * update. Strip the gap from the child/fake View heights so the slot stays
     * wrap_content = child + padding and the bottom blank area stops accumulating.
     */
    private fun compensateExpandedHeight(owner: View, data: Any?) {
        if (!enabled || contentTopGapDp <= 0 || !supported(owner)) return
        val slot = contentSlot(owner) ?: return
        val saved = originalPadding[slot] ?: return
        val extra = slot.paddingTop - saved.top
        if (extra <= 0) return
        val dataClass = data?.javaClass ?: return
        sequenceOf("getView", "getFakeView").forEach { getterName ->
            val child = findMethod(dataClass, getterName)
                ?.let { runCatching { it.invoke(data) as? View }.getOrNull() }
                ?: return@forEach
            val params = child.layoutParams ?: return@forEach
            if (params.height > 0) {
                val corrected = params.height - extra
                if (corrected > 0 && params.height != corrected) {
                    params.height = corrected
                    child.layoutParams = params
                }
            }
        }
    }

    private fun layerRoots(owner: View): List<View> {
        // Lift the sibling root at the window level, including its outer background, not
        // merely expanded_view inside a lower-Z island. Treat fake root independently.
        val roots = mutableListOf<View>()
        var current = owner
        repeat(8) {
            val parent = current.parent as? View ?: return roots
            if (parent.javaClass.name == WINDOW) {
                roots.add(current)
                return roots
            }
            current = parent
        }
        return roots
    }

    private fun lift(owner: View) {
        layerRoots(owner).forEach { root ->
            val parent = root.parent as? ViewGroup ?: return@forEach
            if (!lifted.containsKey(root)) {
                if (lifted.size >= 64) return@forEach
                lifted[root] = root.translationZ
            }
            var otherZ = 0f
            for (i in 0 until parent.childCount) {
                val sibling = parent.getChildAt(i)
                if (sibling !== root && !lifted.containsKey(sibling)) otherZ = maxOf(otherZ, sibling.z)
            }
            // translationZ changes ordering without adding elevation/shadows.
            root.translationZ = maxOf(lifted.getValue(root),
                otherZ + owner.resources.displayMetrics.density - root.elevation)
        }
    }

    private fun restoreOwner(owner: View) = layerRoots(owner).forEach(::restore)
    private fun restore(root: View) {
        lifted.remove(root)?.let { root.translationZ = it }
    }
}
