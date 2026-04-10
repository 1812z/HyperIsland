package io.github.hyperisland.xposed.hook

import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModule

object BigIslandMinWidthHook : BaseHook() {

    private const val TAG = "HyperIsland[IslandWidthHook]"
    private const val DEFAULT_MAX_WIDTH_DP = 600

    override fun getTag() = TAG

    override fun onConfigChanged() {
        hookedCalculateMaxWidthWithSmall = false
        hookedSetMaxWidth = false
    }

    private var hookedCalculateMaxWidthWithSmall = false
    private var hookedSetMaxWidth = false

    private fun dpToPx(dp: Int): Float {
        val density = android.content.res.Resources.getSystem().displayMetrics.density
        return dp * density
    }

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        hookDynamicClassLoaders(module)
    }

    private fun hookContentViewClasses(module: XposedModule, classLoader: ClassLoader) {
        if (hookedCalculateMaxWidthWithSmall && hookedSetMaxWidth) return
        val className = "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
        try {
            val clazz = classLoader.loadClass(className)
            
            // Hook calculateMaxWidthWithSmall - 控制有小岛时的大岛最大宽度
            if (!hookedCalculateMaxWidthWithSmall) {
                val calculateMaxWidthWithSmallMethod = clazz.declaredMethods.firstOrNull { 
                    it.name == "calculateMaxWidthWithSmall" 
                }
                if (calculateMaxWidthWithSmallMethod != null) {
                    module.hook(calculateMaxWidthWithSmallMethod).intercept { chain ->
                        val enabled = ConfigManager.getBoolean("pref_big_island_max_width_enabled", false)
                        if (!enabled) {
                            return@intercept chain.proceed()
                        }
                        
                        val maxWidthDp = ConfigManager.getInt("pref_big_island_max_width", DEFAULT_MAX_WIDTH_DP).coerceIn(100, 1000)
                        val maxWidthPx = dpToPx(maxWidthDp)
                        
                        log(module, "calculateMaxWidthWithSmall returning $maxWidthPx")
                        
                        return@intercept maxWidthPx
                    }
                    hookedCalculateMaxWidthWithSmall = true
                    log(module, "hooked calculateMaxWidthWithSmall on $className")
                }
            }
            
            // Hook setMaxWidth - 控制无小岛时的大岛最大宽度
            if (!hookedSetMaxWidth) {
                val setMaxWidthMethod = clazz.declaredMethods.firstOrNull { it.name == "setMaxWidth" }
                if (setMaxWidthMethod != null) {
                    module.hook(setMaxWidthMethod).intercept { chain ->
                        val enabled = ConfigManager.getBoolean("pref_big_island_max_width_enabled", false)
                        if (!enabled) {
                            return@intercept chain.proceed()
                        }
                        
                        val maxWidthDp = ConfigManager.getInt("pref_big_island_max_width", DEFAULT_MAX_WIDTH_DP).coerceIn(100, 1000)
                        val target = chain.thisObject ?: return@intercept chain.proceed()
                        val maxWidthPx = dpToPx(maxWidthDp)
                        val clockWidth = (chain.args.getOrNull(1) as? Number)?.toFloat() ?: -1f
                        val batteryWidth = (chain.args.getOrNull(2) as? Number)?.toFloat() ?: -1f
                        
                        val maxWidthField = clazz.getDeclaredField("maxWidth")
                        maxWidthField.isAccessible = true
                        maxWidthField.setFloat(target, maxWidthPx)

                        val clockWidthField = clazz.getDeclaredField("clockWidth")
                        clockWidthField.isAccessible = true
                        clockWidthField.setFloat(target, clockWidth)

                        val batteryWidthField = clazz.getDeclaredField("batteryWidth")
                        batteryWidthField.isAccessible = true
                        batteryWidthField.setFloat(target, batteryWidth)

                        log(module, "maxWidth=$maxWidthPx px, clockWidth=$clockWidth, batteryWidth=$batteryWidth")
                        
                        return@intercept null
                    }
                    hookedSetMaxWidth = true
                    log(module, "hooked setMaxWidth on $className")
                }
            }
        } catch (_: ClassNotFoundException) {
        } catch (e: Exception) {
            logError(module, "failed to hook $className: ${e.message}")
        }
    }

    private fun hookDynamicClassLoaders(module: XposedModule) {
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { cl ->
            if (!hookedCalculateMaxWidthWithSmall || !hookedSetMaxWidth) {
                hookContentViewClasses(module, cl)
            }
        }
    }
}
