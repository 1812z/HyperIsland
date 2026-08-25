package io.github.hyperisland.xposed.hook.SystemUI

import android.content.res.Resources
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.hyperisland.xposed.utils.ResourceDimenHook
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModule

object BigIslandMinWidthHook : BaseHook() {

    private const val TAG = "HyperIsland[IslandWidthHook]"
    private const val KEY_MAX_WIDTH = "pref_big_island_max_width"
    private const val KEY_MIN_WIDTH = "pref_big_island_min_width"
    private const val KEY_SMALL_WIDTH = "pref_small_island_width"
    private const val KEY_SMALL_OFFSET = "pref_small_island_horizontal_offset"
    private const val BASE_CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private const val PHONE_HELPER_CLASS =
        "miui.systemui.dynamicisland.window.content.helpers.DynamicIslandContentViewPhoneHelper"
    private const val PAD_HELPER_CLASS =
        "miui.systemui.dynamicisland.window.content.helpers.DynamicIslandContentViewPadHelper"

    override fun getTag() = TAG

    private var hookedLegacyCalculateMaxWidthWithSmall = false
    private var hookedModernCalculateMaxWidthWithSmall = false
    private var hookedSetMaxWidth = false
    private var hookedSmallIslandOffset = false
    private val hookedMinWidthClasses = mutableSetOf<String>()
    private val registrationLock = Any()
    private var dynamicClassLoaderCallbackRegistered = false

    private fun dpToPx(dp: Int): Float {
        val density = Resources.getSystem().displayMetrics.density
        return dp * density
    }

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        registerDimensionResources(module)
        hookDynamicClassLoaders(module)
    }

    private fun registerDimensionResources(module: XposedModule) {
        ResourceDimenHook.registerDp(
            module,
            "small_island_width",
            KEY_SMALL_WIDTH,
            34,
            1..100,
        )
    }

    private fun hookContentViewClasses(module: XposedModule, classLoader: ClassLoader) {
        synchronized(registrationLock) {
            hookBaseContentView(module, classLoader)
            hookModernMaxWidthWithSmall(module, classLoader)
            hookGetBigIslandMinWidth(module, classLoader)
        }
    }

    private fun hookBaseContentView(module: XposedModule, classLoader: ClassLoader) {
        try {
            val clazz = classLoader.loadClass(BASE_CONTENT_VIEW_CLASS)

            if (!hookedSmallIslandOffset) {
                val getSpaceMethod = clazz.declaredMethods.firstOrNull {
                    it.name == "getSpace" &&
                        it.returnType == Int::class.javaPrimitiveType &&
                        it.parameterTypes.isEmpty()
                }
                if (getSpaceMethod != null) {
                    module.hook(getSpaceMethod).intercept { chain ->
                        val systemSpace = chain.proceed() as Int
                        val offsetDp = ConfigManager.getInt(KEY_SMALL_OFFSET, 0).coerceIn(-10, 50)
                        if (offsetDp == 0) {
                            return@intercept systemSpace
                        }
                        (systemSpace + dpToPx(offsetDp).toInt()).coerceAtLeast(0)
                    }
                    hookedSmallIslandOffset = true
                    log(module, "hooked dual-island spacing on $BASE_CONTENT_VIEW_CLASS")
                }
            }

            // 旧版 SystemUI 将双岛宽度计算放在 BaseContentView 中。
            if (!hookedLegacyCalculateMaxWidthWithSmall) {
                val calculateMaxWidthWithSmallMethod = clazz.declaredMethods.firstOrNull {
                    it.name == "calculateMaxWidthWithSmall" &&
                        it.returnType == Float::class.javaPrimitiveType
                }
                if (calculateMaxWidthWithSmallMethod != null) {
                    module.hook(calculateMaxWidthWithSmallMethod).intercept { chain ->
                        val maxWidthDp = ConfigManager.getInt(KEY_MAX_WIDTH, 0)
                        if (maxWidthDp <= 0) {
                            return@intercept chain.proceed()
                        }

                        val maxWidthPx = dpToPx(maxWidthDp.coerceIn(1, 1000))

                        return@intercept maxWidthPx
                    }
                    hookedLegacyCalculateMaxWidthWithSmall = true
                    log(module, "hooked legacy calculateMaxWidthWithSmall on $BASE_CONTENT_VIEW_CLASS")
                }
            }

            // Hook setMaxWidth - 控制无小岛时的大岛最大宽度
            if (!hookedSetMaxWidth) {
                val setMaxWidthMethod = clazz.declaredMethods.firstOrNull {
                    it.name == "setMaxWidth" && it.parameterTypes.size == 3 &&
                        it.parameterTypes.all { type -> type == Float::class.javaPrimitiveType }
                } ?: clazz.declaredMethods.firstOrNull {
                    it.name == "setMaxWidth" &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == Float::class.javaPrimitiveType
                }
                if (setMaxWidthMethod != null) {
                    val maxWidthField = clazz.getDeclaredField("maxWidth").apply { isAccessible = true }
                    val clockWidthField = if (setMaxWidthMethod.parameterTypes.size == 3) {
                        clazz.getDeclaredField("clockWidth").apply { isAccessible = true }
                    } else {
                        null
                    }
                    val batteryWidthField = if (setMaxWidthMethod.parameterTypes.size == 3) {
                        clazz.getDeclaredField("batteryWidth").apply { isAccessible = true }
                    } else {
                        null
                    }
                    module.hook(setMaxWidthMethod).intercept { chain ->
                        val maxWidthDp = ConfigManager.getInt(KEY_MAX_WIDTH, 0)
                        if (maxWidthDp <= 0) {
                            return@intercept chain.proceed()
                        }

                        val maxWidthDpClamped = maxWidthDp.coerceIn(1, 1000)
                        val target = chain.thisObject ?: return@intercept chain.proceed()
                        val maxWidthPx = dpToPx(maxWidthDpClamped)
                        maxWidthField.setFloat(target, maxWidthPx)

                        if (clockWidthField != null && batteryWidthField != null) {
                            val clockWidth = (chain.args.getOrNull(1) as? Number)?.toFloat() ?: -1f
                            val batteryWidth = (chain.args.getOrNull(2) as? Number)?.toFloat() ?: -1f
                            clockWidthField.setFloat(target, clockWidth)
                            batteryWidthField.setFloat(target, batteryWidth)
                        }
                        return@intercept null
                    }
                    hookedSetMaxWidth = true
                    log(
                        module,
                        "hooked setMaxWidth(${setMaxWidthMethod.parameterTypes.size} args) on $BASE_CONTENT_VIEW_CLASS",
                    )
                }
            }
        } catch (_: ClassNotFoundException) {
        } catch (e: Exception) {
            logError(module, "failed to hook $BASE_CONTENT_VIEW_CLASS: ${e.message}")
        }
    }

    private fun hookModernMaxWidthWithSmall(module: XposedModule, classLoader: ClassLoader) {
        if (hookedModernCalculateMaxWidthWithSmall) return
        try {
            val clazz = classLoader.loadClass(PHONE_HELPER_CLASS)
            val method = clazz.declaredMethods.firstOrNull {
                it.name == "calculateMaxWidthWithSmall" &&
                    it.returnType == Float::class.javaPrimitiveType &&
                    it.parameterTypes.size == 2 &&
                    it.parameterTypes[0].name ==
                        "miui.systemui.dynamicisland.model.IslandContentViewCalculationParams"
            } ?: return
            val paramsClass = method.parameterTypes[0]
            val getSmallIslandViewWidth = paramsClass.getMethod("getSmallIslandViewWidth")
            val getSpace = paramsClass.getMethod("getSpace")

            module.hook(method).intercept { chain ->
                val maxWidthDp = ConfigManager.getInt(KEY_MAX_WIDTH, 0)
                if (maxWidthDp <= 0) {
                    return@intercept chain.proceed()
                }

                val params = chain.args.getOrNull(0) ?: return@intercept chain.proceed()
                val smallWidth = (getSmallIslandViewWidth.invoke(params) as Number).toInt()
                val space = (getSpace.invoke(params) as Number).toInt()
                // 新版方法限制的是大岛、小岛和间距的组合宽度。
                val combinedMaxWidth =
                    dpToPx(maxWidthDp.coerceIn(1, 1000)) + smallWidth + space

                combinedMaxWidth
            }
            hookedModernCalculateMaxWidthWithSmall = true
            log(module, "hooked modern calculateMaxWidthWithSmall on $PHONE_HELPER_CLASS")
        } catch (_: ClassNotFoundException) {
        } catch (e: Exception) {
            logError(module, "failed to hook calculateMaxWidthWithSmall on $PHONE_HELPER_CLASS: ${e.message}")
        }
    }

    private fun hookGetBigIslandMinWidth(module: XposedModule, classLoader: ClassLoader) {
        val helperClasses = listOf(PHONE_HELPER_CLASS, PAD_HELPER_CLASS)
        for (helperClassName in helperClasses) {
            if (helperClassName in hookedMinWidthClasses) continue
            try {
                val helperClazz = classLoader.loadClass(helperClassName)
                val getMinWidthMethod = helperClazz.declaredMethods.firstOrNull {
                    it.name == "getBigIslandMinWidth" &&
                        it.returnType == Int::class.javaPrimitiveType &&
                        it.parameterTypes.isEmpty()
                } ?: continue
                module.hook(getMinWidthMethod).intercept { chain ->
                    val minWidthDp = ConfigManager.getInt(KEY_MIN_WIDTH, 0)
                    if (minWidthDp <= 0) {
                        return@intercept chain.proceed()
                    }
                    val maxWidthDp = ConfigManager.getInt(KEY_MAX_WIDTH, 0)
                    val effectiveMinWidthDp = if (maxWidthDp > 0) {
                        minWidthDp.coerceAtMost(maxWidthDp)
                    } else {
                        minWidthDp
                    }
                    val minWidthPx = dpToPx(effectiveMinWidthDp.coerceIn(1, 1000)).toInt()
                    return@intercept minWidthPx
                }
                hookedMinWidthClasses += helperClassName
                log(module, "hooked getBigIslandMinWidth on $helperClassName")
            } catch (_: ClassNotFoundException) {
            } catch (e: Exception) {
                logError(module, "failed to hook getBigIslandMinWidth on $helperClassName: ${e.message}")
            }
        }
    }

    private fun hookDynamicClassLoaders(module: XposedModule) {
        synchronized(registrationLock) {
            if (dynamicClassLoaderCallbackRegistered) return
            dynamicClassLoaderCallbackRegistered = true
        }
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { cl ->
            hookContentViewClasses(module, cl)
        }
    }
}
