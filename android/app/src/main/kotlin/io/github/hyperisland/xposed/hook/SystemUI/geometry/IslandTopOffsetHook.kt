package io.github.hyperisland.xposed.hook.SystemUI.geometry

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.Collections
import java.util.WeakHashMap

/**
 * 修改超级岛距屏幕顶部的距离。
 *
 * 不移动 DynamicIslandWindow 的 WindowManager.LayoutParams.y。窗口层偏移太晚，
 * 展开动画仍按原始位置计算，会出现动画结束后整体跳动。
 *
 * 正确入口是内容层的 cutoutY：
 * DynamicIslandSizeRepository.cutoutY -> DynamicIslandBaseContentView.setCutoutY(float)
 * -> calculateBigIslandY() -> DynamicIslandAnimationDelegate 使用计算结果执行动画。
 *
 * 因此在 setCutoutY(float) 后覆盖 cutoutY 缓存并触发原有重算，
 * 让后续布局和动画从源头读到偏移后的位置。
 */
object IslandTopOffsetHook : BaseHook() {

    private const val TAG = "HyperIsland[IslandTopOffset]"
    private const val KEY_TOP_OFFSET = "pref_island_top_offset"
    private const val CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"

    @Volatile private var topOffsetDp = 0.0
    @Volatile private var hookedContentView = false

    private val activeViews = Collections.synchronizedMap(WeakHashMap<Any, Float>())

    override fun getTag() = TAG

    override fun onConfigChanged() {
        loadConfig()
        synchronized(activeViews) {
            activeViews.forEach { (view, originalCutoutY) ->
                applyCutoutY(view, originalCutoutY)
            }
        }
    }

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        loadConfig()
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookContentView(module, classLoader)
        }
    }

    private fun hookContentView(module: XposedModule, classLoader: ClassLoader) {
        if (hookedContentView) return
        try {
            val clazz = classLoader.loadClass(CONTENT_VIEW_CLASS)
            val setCutoutY = clazz.getDeclaredMethod("setCutoutY", Float::class.javaPrimitiveType)
            module.hook(setCutoutY).intercept { chain ->
                val view = chain.thisObject ?: return@intercept chain.proceed()
                val originalCutoutY = (chain.args.getOrNull(0) as? Number)?.toFloat()
                    ?: return@intercept chain.proceed()
                activeViews[view] = originalCutoutY
                val result = chain.proceed()
                applyCutoutY(view, originalCutoutY)
                result
            }
            hookedContentView = true
            log(module) { "hooked $CONTENT_VIEW_CLASS.setCutoutY(float)" }
        } catch (_: ClassNotFoundException) {
        } catch (e: Exception) {
            logError(module, "hookContentView failed: ${e.message}")
        }
    }

    private fun applyCutoutY(view: Any, originalCutoutY: Float) {
        try {
            val adjustedCutoutY = originalCutoutY + topOffsetPx(view)
            setFloatField(view, "cutoutY", adjustedCutoutY)

            val pendingCutoutYField = findField(view.javaClass, "pendingCutoutY")
            pendingCutoutYField?.let { field ->
                field.isAccessible = true
                if (field.get(view) != null) {
                    field.set(view, adjustedCutoutY)
                }
            }

            view.javaClass.declaredMethods.firstOrNull { method ->
                method.name == "calculateBigIslandY" && method.parameterTypes.isEmpty()
            }?.let { method ->
                method.isAccessible = true
                method.invoke(view)
            }
        } catch (_: Exception) {
        }
    }

    private fun setFloatField(instance: Any, name: String, value: Float) {
        val field = findField(instance.javaClass, name) ?: return
        field.isAccessible = true
        field.setFloat(instance, value)
    }

    private fun findField(clazz: Class<*>, name: String): java.lang.reflect.Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun topOffsetPx(view: Any): Float {
        if (topOffsetDp == 0.0) return 0f
        val metrics = findContext(view)?.resources?.displayMetrics
            ?: Resources.getSystem().displayMetrics
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            topOffsetDp.toFloat(),
            metrics
        )
    }

    private fun findContext(view: Any): Context? {
        return view as? Context ?: try {
            view.javaClass.getMethod("getContext").invoke(view) as? Context
        } catch (_: Exception) {
            null
        }
    }

    private fun loadConfig() {
        topOffsetDp = ConfigManager.getDouble(KEY_TOP_OFFSET, 0.0)
    }
}
