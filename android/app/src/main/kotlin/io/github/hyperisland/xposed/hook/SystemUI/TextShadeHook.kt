package io.github.hyperisland.xposed.hook

import android.view.View
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 关闭超级岛文字超长时末尾的灰色渐变遮罩。
 *
 * JADX 中左右文字分别由 IslandTextViewHolder / IslandRightTextViewHolder 管理，
 * 宽度不足时会显示字段 textShade 对应的 island_text_shade View。
 */
object TextShadeHook : BaseHook() {

    private const val TAG = "HyperIsland[TextShade]"
    private const val KEY_BIG_BG = "pref_island_bg_big_path"

    private val hookedClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    @Volatile private var cachedBigBgConfigured: Boolean? = null

    override fun getTag() = TAG

    override fun onConfigChanged() {
        cachedBigBgConfigured = null
    }

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        hookDynamicClassLoaders(module)
    }

    private fun hasBigIslandBackground(): Boolean {
        cachedBigBgConfigured?.let { return it }
        val configured = ConfigManager.getString(KEY_BIG_BG).trim().let { path ->
            path.isNotEmpty() && File(path).exists()
        }
        cachedBigBgConfigured = configured
        return configured
    }

    private fun hookDynamicClassLoaders(module: XposedModule) {
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { cl ->
            hookTextHolderClasses(module, cl)
        }
    }

    private fun hookTextHolderClasses(module: XposedModule, classLoader: ClassLoader) {
        if (!hookedClassLoaders.add(System.identityHashCode(classLoader))) return

        val classNames = arrayOf(
            "miui.systemui.dynamicisland.module.IslandTextViewHolder",
            "miui.systemui.dynamicisland.module.IslandRightTextViewHolder",
        )

        var hookedAny = false
        for (className in classNames) {
            try {
                val clazz = classLoader.loadClass(className)
                hookSetTextVisibility(module, clazz, className)
                hookHideAfterMethod(module, clazz, className, "initLayout")
                hookHideAfterMethod(module, clazz, className, "updateWidth")
                hookedAny = true
            } catch (_: ClassNotFoundException) {
            } catch (e: Throwable) {
                logError(module, "failed to hook $className: ${e.message}")
            }
        }

        if (!hookedAny) {
            hookedClassLoaders.remove(System.identityHashCode(classLoader))
        }
    }

    private fun hookSetTextVisibility(module: XposedModule, clazz: Class<*>, className: String) {
        val method = clazz.declaredMethods.firstOrNull { method ->
            method.name == "setTextVisibility" &&
                method.parameterTypes.size == 4 &&
                method.parameterTypes.all { it == Boolean::class.javaPrimitiveType }
        } ?: return

        module.hook(method).intercept { chain ->
            if (hasBigIslandBackground()) {
                hideTextShade(chain.thisObject)
            }
            val result = chain.proceed()
            if (hasBigIslandBackground()) {
                hideTextShade(chain.thisObject)
            }
            result
        }
        log(module, "hooked setTextVisibility on $className")
    }

    private fun hookHideAfterMethod(
        module: XposedModule,
        clazz: Class<*>,
        className: String,
        methodName: String,
    ) {
        val methods = clazz.declaredMethods.filter { it.name == methodName }
        for (method in methods) {
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (hasBigIslandBackground()) {
                    hideTextShade(chain.thisObject)
                }
                result
            }
        }
        if (methods.isNotEmpty()) {
            log(module, "hooked $methodName on $className")
        }
    }

    private fun hideTextShade(holder: Any?) {
        if (holder == null) return
        try {
            val field = holder.javaClass.getDeclaredField("textShade")
            field.isAccessible = true
            val shade = field.get(holder) as? View ?: return
            shade.visibility = View.GONE
            shade.alpha = 0f
        } catch (_: Throwable) {
        }
    }
}
