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
import java.util.Collections
import java.util.WeakHashMap

/** Aligns focus expansion with the computed island top, without changing persisted offsets. */
object ExpandedIosAnimationHook : BaseHook() {
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
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var enabled = false
    @Volatile private var contentTopGapDp = 0

    override fun getTag() = "HyperIsland[ExpandedIos]"

    override fun onConfigChanged() {
        enabled = ConfigManager.getString(ExpandedCollapsePreferences.TYPE, "system") == "ios"
        contentTopGapDp = ConfigManager.getInt(ExpandedCollapsePreferences.IOS_CONTENT_TOP_GAP,
            ExpandedCollapsePreferences.DEFAULT_IOS_CONTENT_TOP_GAP.toInt())
            .coerceIn(0, 20)
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
                    if (!supported(owner)) original else {
                        val top = (islandTop.invoke(owner) as Number).toInt()
                        watch(owner)
                        top
                    }
                }.getOrDefault(original)
            }
            module.hook(updateState).intercept { chain ->
                val owner = chain.thisObject as? View
                runCatching { if (owner != null && supported(owner)) {
                    watch(owner)
                    // Raise before the system submits the first expanded animation frame.
                    if (chain.args.firstOrNull()?.javaClass?.simpleName == "Expanded") lift(owner)
                } }
                val result = chain.proceed()
                runCatching { if (owner != null) updateLayer(owner) }
                result
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
            log(module, "installed computed-top alignment for real/fake focus expansion")
        } catch (e: Throwable) {
            logWarn(module, "installation failed: ${e.message}")
        }
    }

    private fun stateName(owner: View, getter: String): String? = runCatching {
        findMethod(owner.javaClass, getter)?.invoke(owner)?.javaClass?.simpleName
    }.getOrNull()

    private fun supported(owner: View): Boolean {
        if (!enabled || owner.resources.configuration.smallestScreenWidthDp >= 600) return false
        // App and miniwindow coordinates belong to the system's window-animation pipeline.
        return stateName(owner, "getState") !in setOf("AppExpanded", "MiniWindowExpanded",
            "SubAppExpanded", "SubMiniWindowExpanded")
    }

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
        applyContentGap(owner)
        val expanding = stateName(owner, "getState") == "Expanded"
        val animating = runCatching {
            findMethod(owner.javaClass, "isAnimating")?.invoke(owner) == true
        }.getOrDefault(false)
        val leaving = stateName(owner, "getLastState") == "Expanded" && animating
        if (supported(owner) && (expanding || leaving)) lift(owner) else restoreOwner(owner)
    }

    private fun contentSlot(owner: View): View? = runCatching {
        val getter = if (owner.javaClass.simpleName == "DynamicIslandContentFakeView") {
            "getFakeExpandedView"
        } else "getExpandedView"
        findMethod(owner.javaClass, getter)?.invoke(owner) as? View
    }.getOrNull()

    private fun applyContentGap(owner: View) {
        val slot = contentSlot(owner) ?: return
        if (!supported(owner) || contentTopGapDp == 0) {
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
