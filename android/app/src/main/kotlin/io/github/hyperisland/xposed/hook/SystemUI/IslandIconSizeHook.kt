package io.github.hyperisland.xposed.hook.SystemUI

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap

/** Scales icons bound into the stable small and big island regions. */
object IslandIconSizeHook : BaseHook() {
    private const val TAG = "HyperIsland[IslandIconSize]"
    private const val KEY_ICON_SIZE = "pref_island_icon_size"
    private const val CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"

    private data class OriginalSize(val width: Int, val height: Int)

    private val originalSizes = Collections.synchronizedMap(
        WeakHashMap<ImageView, OriginalSize>(),
    )
    private val contentViews = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>()),
    )
    private val hookedClassLoaders = mutableSetOf<Int>()

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookContentView(module, classLoader)
        }
    }

    override fun onConfigChanged() {
        val snapshot = synchronized(contentViews) { contentViews.toList() }
        snapshot.forEach { view -> view.post { applyToContentView(view) } }
    }

    private fun hookContentView(module: XposedModule, classLoader: ClassLoader) {
        val loaderId = System.identityHashCode(classLoader)
        synchronized(hookedClassLoaders) {
            if (loaderId in hookedClassLoaders) return
        }
        try {
            val clazz = classLoader.loadClass(CONTENT_VIEW_CLASS)
            val updateMethods = clazz.declaredMethods.filter {
                it.name == "updateSmallIslandView" || it.name == "updateBigIslandView"
            }
            if (updateMethods.isEmpty()) return
            for (method in updateMethods) {
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val contentView = chain.thisObject as? View ?: return@intercept result
                    contentViews += contentView
                    contentView.post { applyToContentView(contentView) }
                    result
                }
            }
            synchronized(hookedClassLoaders) {
                hookedClassLoaders += loaderId
            }
            log(module, "hooked ${updateMethods.size} island update method(s)")
        } catch (_: ClassNotFoundException) {
        } catch (error: Throwable) {
            logError(module, "failed to hook $CONTENT_VIEW_CLASS: ${error.message}")
        }
    }

    private fun applyToContentView(contentView: View) {
        val percent = ConfigManager.getInt(KEY_ICON_SIZE, 100).coerceIn(50, 150)
        applyToIsland(contentView, "getSmallIslandView", percent)
        applyToIsland(contentView, "getBigIslandView", percent)
    }

    private fun applyToIsland(contentView: View, getterName: String, percent: Int) {
        val island = runCatching {
            val method = contentView.javaClass.methods.firstOrNull {
                it.name == getterName && it.parameterTypes.isEmpty()
            } ?: contentView.javaClass.getDeclaredMethod(getterName).apply {
                isAccessible = true
            }
            method.invoke(contentView) as? ViewGroup
        }.getOrNull() ?: return
        applyToChildren(island, percent)
    }

    private fun applyToChildren(parent: ViewGroup, percent: Int) {
        for (index in 0 until parent.childCount) {
            when (val child = parent.getChildAt(index)) {
                is ImageView -> scaleImageView(child, percent)
                is ViewGroup -> applyToChildren(child, percent)
            }
        }
    }

    private fun scaleImageView(imageView: ImageView, percent: Int) {
        if (imageView.drawable == null) return
        val params = imageView.layoutParams ?: return
        val width = params.width
        val height = params.height
        if (width <= 0 || height <= 0 || kotlin.math.abs(width - height) > 2) return

        val original = synchronized(originalSizes) {
            originalSizes[imageView] ?: OriginalSize(width, height).also {
                originalSizes[imageView] = it
            }
        }
        val targetWidth = (original.width * percent / 100f).toInt().coerceAtLeast(1)
        val targetHeight = (original.height * percent / 100f).toInt().coerceAtLeast(1)
        if (params.width == targetWidth && params.height == targetHeight) return
        params.width = targetWidth
        params.height = targetHeight
        imageView.layoutParams = params
    }
}
