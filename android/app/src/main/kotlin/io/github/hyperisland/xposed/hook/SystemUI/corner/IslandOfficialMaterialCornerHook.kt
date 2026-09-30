package io.github.hyperisland.xposed.hook.SystemUI.corner

import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.IslandBlurRuntime
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.SystemUiReflection.findMethod
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.lifecycle.IslandStateResolver
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.model.IslandType
import io.github.hyperisland.xposed.hook.SystemUI.BackGround.Blur.model.MaterialType
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap
import java.lang.ref.WeakReference

/** Clips stock material hosts without clearing stock blend colors or replacing their renderer. */
object IslandOfficialMaterialCornerHook : BaseHook() {
    private const val BACKGROUND = "miui.systemui.dynamicisland.DynamicIslandBackgroundView"
    private const val CONTENT = "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private val hookedClasses = Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>())
    private val originalClipping = WeakHashMap<View, Boolean>()
    private val detachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) = restoreClipping(v)
    }

    override fun getTag() = "HyperIsland[OfficialMaterialCorner]"

    override fun onConfigChanged() {
        val views = synchronized(originalClipping) { originalClipping.keys.map { WeakReference(it) } }
        views.forEach { reference ->
            reference.get()?.post {
                reference.get()?.let { view ->
                    val type = IslandStateResolver.forView(view)
                    if (type == null || radius(view, type) == null) restoreClipping(view)
                    view.invalidateOutline()
                }
            }
        }
    }

    private fun restoreClipping(view: View) {
        val original = synchronized(originalClipping) { originalClipping.remove(view) } ?: return
        view.clipToOutline = original
        view.removeOnAttachStateChangeListener(detachListener)
    }

    private fun radius(view: View, type: IslandType): Float? {
        if (IslandBlurRuntime.configStore.materialFor(type).type != MaterialType.DEFAULT) return null
        return IslandCornerHook.configuredPx(view, type == IslandType.EXPAND)
    }

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        // The small stock material host is a FrameLayout, rather than a plugin View class.
        val setter = View::class.java.getDeclaredMethod("setOutlineProvider", ViewOutlineProvider::class.java)
        module.hook(setter).intercept { chain ->
            val replacement = runCatching {
                val view = chain.thisObject as? View ?: return@runCatching null
                val name = view.resources.getResourceEntryName(view.id)
                if (name != "small_island_view") return@runCatching null
                var parent = view.parent as? View
                var islandParent = false
                repeat(4) {
                    if (parent?.javaClass?.name?.startsWith("miui.systemui.dynamicisland.") == true) {
                        islandParent = true
                    }
                    parent = parent?.parent as? View
                }
                if (!islandParent) return@runCatching null
                val provider = chain.args[0] as? ViewOutlineProvider ?: return@runCatching null
                if (provider is StockCornerProvider) return@runCatching null
                StockCornerProvider(provider)
            }.getOrNull()
            if (replacement == null) chain.proceed() else chain.proceed(arrayOf(replacement))
        }
        hookPlugin(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) {
            hookPlugin(module, it)
        }
    }

    private class StockCornerProvider(private val original: ViewOutlineProvider) : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            original.getOutline(view, outline)
            runCatching {
                val radius = radius(view, IslandType.SMALL) ?: return@runCatching
                val rect = Rect()
                if (outline.getRect(rect)) {
                    outline.setRoundRect(rect, radius.coerceAtMost(minOf(rect.width(), rect.height()) / 2f))
                }
            }
        }
    }

    private fun hookPlugin(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val clazz = loader.loadClass(BACKGROUND)
            synchronized(hookedClasses) { if (!hookedClasses.add(clazz)) return@runCatching }
            val draw = clazz.getDeclaredMethod("onDraw", Canvas::class.java)
            module.hook(draw).intercept { chain ->
                val canvas = chain.args[0] as? Canvas ?: return@intercept chain.proceed()
                val clip = runCatching {
                    val host = chain.thisObject as? ViewGroup ?: return@runCatching null
                    val content = (0 until host.childCount).asSequence().map(host::getChildAt)
                        .firstOrNull { it.javaClass.name.contains("DynamicIslandContentView") }
                        ?: return@runCatching null
                    val state = findMethod(content.javaClass, "getState")?.invoke(content)
                    val type = IslandStateResolver.fromState(state) ?: return@runCatching null
                    val radius = radius(host, type) ?: return@runCatching null
                    fun coordinate(name: String) =
                        (findMethod(clazz, name)?.invoke(host) as? Number)?.toFloat()
                            ?: error("Missing $name")
                    val stroke = coordinate("getStokeWidth")
                    val rect = RectF(
                        coordinate("getActualLeft") - stroke,
                        coordinate("getActualTop") - stroke,
                        coordinate("getActualWidth") + stroke,
                        coordinate("getActualHeight") + stroke,
                    )
                    if (rect.isEmpty) return@runCatching null
                    val outerRadius = (radius + stroke).coerceIn(0f, minOf(rect.width(), rect.height()) / 2f)
                    Path().apply { addRoundRect(rect, outerRadius, outerRadius, Path.Direction.CW) }
                }.getOrNull()
                if (clip == null) return@intercept chain.proceed()
                val save = canvas.save()
                try {
                    canvas.clipPath(clip)
                    chain.proceed()
                } finally {
                    canvas.restoreToCount(save)
                }
            }
        }.onFailure { log(module, "background corner hook unavailable: ${it.message}") }
        runCatching {
            val clazz = loader.loadClass(CONTENT)
            synchronized(hookedClasses) { if (!hookedClasses.add(clazz)) return@runCatching }
            val update = clazz.getDeclaredMethod("updateBackgroundBg", View::class.java, Boolean::class.javaPrimitiveType!!)
            module.hook(update).intercept { chain ->
                val result = chain.proceed()
                runCatching {
                    val view = chain.args[0] as? View ?: return@runCatching
                    val type = IslandStateResolver.forView(view) ?: return@runCatching
                    if (radius(view, type) == null) {
                        restoreClipping(view)
                        return@runCatching
                    }
                    if (type == IslandType.SMALL) {
                        view.outlineProvider?.takeUnless { it is StockCornerProvider }?.let {
                            view.outlineProvider = StockCornerProvider(it)
                        }
                    }
                    // Bionics already enables clipping; Classic MiBlur may leave it disabled.
                    // Preserve the provider's RenderNode positioning and animation geometry.
                    synchronized(originalClipping) {
                        if (!originalClipping.containsKey(view)) {
                            if (originalClipping.size >= 64) return@runCatching
                            originalClipping[view] = view.clipToOutline
                            view.addOnAttachStateChangeListener(detachListener)
                        }
                    }
                    view.clipToOutline = true
                    view.invalidateOutline()
                }
                result
            }
        }.onFailure { log(module, "material corner hook unavailable: ${it.message}") }
    }
}
