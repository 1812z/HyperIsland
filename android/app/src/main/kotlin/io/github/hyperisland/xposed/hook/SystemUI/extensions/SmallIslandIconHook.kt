package io.github.hyperisland.xposed.hook.SystemUI.extensions

import android.view.View
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap

/** Adjusts only the icon container used by the SMALL island module. */
object SmallIslandIconHook : BaseHook() {
    private const val TAG = "HyperIsland[SmallIslandIcon]"
    private const val KEY_ENABLED = "pref_small_island_icon_adjustment"
    private const val KEY_OPACITY = "pref_small_island_icon_opacity"
    private const val ICON_HOLDER_CLASS =
        "miui.systemui.dynamicisland.module.IslandIconViewHolder"
    private const val SMALL_ISLAND_ICON_MODULE = "modulePicSmallIsland"

    private val hookedClasses = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Class<*>, Boolean>()),
    )
    private val trackedContainers = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<View, Boolean>()),
    )

    @Volatile private var enabled = false
    @Volatile private var opacity = 0.5f

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        loadConfig()
        if (!enabled || param.packageName != "com.android.systemui") return
        HookUtils.hookDynamicClassLoaders(
            module,
            ClassLoader.getSystemClassLoader(),
        ) { classLoader ->
            hookIconHolder(module, classLoader)
        }
        hookIconHolder(module, param.defaultClassLoader)
    }

    override fun onConfigChanged() {
        loadConfig()
        val containers = synchronized(trackedContainers) { trackedContainers.toList() }
        containers.forEach { container ->
            container.post { applyOpacity(container) }
        }
    }

    private fun hookIconHolder(module: XposedModule, classLoader: ClassLoader) {
        val holderClass = try {
            Class.forName(ICON_HOLDER_CLASS, false, classLoader)
        } catch (_: ClassNotFoundException) {
            return
        } catch (error: Throwable) {
            logError(module, "failed to load $ICON_HOLDER_CLASS: ${error.message}")
            return
        }
        if (!hookedClasses.add(holderClass)) return

        try {
            val iconContainerField = holderClass.getDeclaredField("iconContainer").apply {
                isAccessible = true
            }
            val getModule = holderClass.getMethod("getModule")
            val bindMethods = holderClass.declaredMethods.filter { method ->
                method.name == "bind" && method.parameterCount == 2
            }
            bindMethods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    runCatching {
                        val holder = chain.thisObject
                        if (holder != null && getModule.invoke(holder) == SMALL_ISLAND_ICON_MODULE) {
                            (iconContainerField.get(holder) as? View)?.let { container ->
                                trackedContainers += container
                                applyOpacity(container)
                            }
                        }
                    }
                    result
                }
            }
            log(module, "hooked precise small-island icon bind=${bindMethods.size}")
        } catch (error: Throwable) {
            hookedClasses.remove(holderClass)
            logError(module, "failed to hook $ICON_HOLDER_CLASS: ${error.message}")
        }
    }

    private fun applyOpacity(container: View) {
        val target = if (enabled) opacity else 1f
        if (container.alpha != target) container.alpha = target
    }

    private fun loadConfig() {
        enabled = ConfigManager.getBoolean(KEY_ENABLED, false)
        opacity = ConfigManager.getFloat(KEY_OPACITY, 0.5f).coerceIn(0f, 1f)
    }
}
