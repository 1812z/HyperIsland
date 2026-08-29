package io.github.hyperisland.xposed.hook

import android.util.TypedValue
import android.view.View
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.hyperisland.xposed.utils.ResourceDimenHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/** Replaces SystemUI's island height and keeps small-island content centered. */
object IslandDimenHook : BaseHook() {
    private const val TAG = "HyperIsland[islandDimen]"
    private const val KEY_HEIGHT = "pref_island_height"
    private const val HEIGHT_RESOURCE = "island_height"
    private const val DEFAULT_HEIGHT_DP = 34.0
    private const val CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"

    private val hookedClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()),
    )

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        ResourceDimenHook.register(module, HEIGHT_RESOURCE) { resources ->
            val configuredHeight = ConfigManager.getDouble(KEY_HEIGHT, 0.0)
            val heightDp = configuredHeight.takeIf { it > 0.0 } ?: DEFAULT_HEIGHT_DP
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                heightDp.coerceAtMost(100.0).toFloat(),
                resources.displayMetrics,
            )
        }
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookContentView(module, classLoader)
        }
    }

    private fun hookContentView(module: XposedModule, classLoader: ClassLoader) {
        try {
            val clazz = classLoader.loadClass(CONTENT_VIEW_CLASS)
            if (!hookedClasses.add(clazz)) return

            val calculateBigIslandY = clazz.getDeclaredMethod("calculateBigIslandY")
            val getIslandViewHeight = clazz.getDeclaredMethod("getIslandViewHeight")
            val smallViewGetters = listOf(
                clazz.getDeclaredMethod("getSmallIslandView"),
                clazz.getDeclaredMethod("getFakeSmallIsland"),
            )
            module.hook(calculateBigIslandY).intercept { chain ->
                val result = chain.proceed()
                chain.thisObject?.let { contentView ->
                    syncSmallIslandHeights(contentView, getIslandViewHeight, smallViewGetters)
                }
                result
            }
            log(module, "hooked $CONTENT_VIEW_CLASS.calculateBigIslandY()")
        } catch (_: ClassNotFoundException) {
        } catch (e: Exception) {
            logError(module, "hookContentView failed: ${e.message}")
        }
    }

    private fun syncSmallIslandHeights(
        contentView: Any,
        getIslandViewHeight: Method,
        smallViewGetters: List<Method>,
    ) {
        runCatching {
            val height = (getIslandViewHeight.invoke(contentView) as? Number)?.toInt() ?: return
            smallViewGetters.forEach { getter ->
                val view = getter.invoke(contentView) as? View ?: return@forEach
                val layoutParams = view.layoutParams ?: return@forEach
                if (layoutParams.height != height) {
                    layoutParams.height = height
                    view.layoutParams = layoutParams
                }
            }
        }
    }
}
