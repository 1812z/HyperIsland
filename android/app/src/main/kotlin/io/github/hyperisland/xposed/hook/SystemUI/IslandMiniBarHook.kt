package io.github.hyperisland.xposed.hook

import android.view.View
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * 焦点通知展开态底部「下拉小白条」（`R.id.mini_window_bar`）行为控制。
 *
 * 小白条位于 `dynamic_island_child_view.xml`，是一根 60.36dp × 3.64dp、圆角 2dp 的 View，
 * 展开态下由 `DynamicIslandBaseContentView.updateMiniBar()` 决定显隐；它同时是「下拉进入小窗」
 * 手势的把手 —— 手势是否生效由 `getMiniBarVisible()` 这个布尔字段控制，而不是 View 的可见性。
 *
 * 四种行为合并为三种（「隐藏」与早期「仅设为透明」实现完全等价，已合并）：
 * - 跟随系统：不动，完全交给系统；
 * - 隐藏（即仅设为透明）：View 可见性沿用系统决定，只把 alpha 置 0；
 *   `miniBarVisible` 字段保持 true，下拉手势依旧生效；
 * - 移除：View 设为 GONE，并把 `miniBarVisible` 置 false，既看不见也不响应下拉手势。
 *
 * 作用域：com.android.systemui（岛内容由 miui.systemui.plugin 插件类加载器加载，故同时挂动态加载器）。
 *
 * 注意：这里只管**焦点通知**（岛内展开态）。普通通知（通知栏 / 悬浮通知行）的小白条是另一套实现，
 * 由 [NotificationMiniBarHook] 负责。
 */
object IslandMiniBarHook : BaseHook() {

    private const val TAG = "HyperIsland[MiniBar]"
    private const val PREF_BEHAVIOR = "pref_island_mini_bar_behavior"

    private const val BEHAVIOR_SYSTEM = "system"
    private const val BEHAVIOR_HIDDEN = "hidden"
    private const val BEHAVIOR_TRANSPARENT = "transparent"
    private const val BEHAVIOR_REMOVED = "removed"

    private const val BASE_CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandBaseContentView"
    private const val CONTENT_VIEW_CLASS =
        "miui.systemui.dynamicisland.window.content.DynamicIslandContentView"

    private val hookedLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val miniBarGetters = ConcurrentHashMap<Class<*>, Optional<Method>>()
    private val miniBarVisibleSetters = ConcurrentHashMap<Class<*>, Optional<Method>>()

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        hookClasses(module, param.defaultClassLoader)
        // 岛内容由插件类加载器加载，需要等插件加载器构造完成后再挂一次。
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookClasses(module, classLoader)
        }
    }

    private fun hookClasses(module: XposedModule, classLoader: ClassLoader) {
        val loaderId = System.identityHashCode(classLoader)
        if (!hookedLoaders.add(loaderId)) return
        val baseClass = runCatching { classLoader.loadClass(BASE_CONTENT_VIEW_CLASS) }.getOrNull()
        val contentClass = runCatching { classLoader.loadClass(CONTENT_VIEW_CLASS) }.getOrNull()
        if (baseClass == null || contentClass == null) {
            hookedLoaders.remove(loaderId)
            return
        }
        val method = runCatching { baseClass.getDeclaredMethod("updateMiniBar", contentClass) }.getOrNull()
        if (method == null) {
            hookedLoaders.remove(loaderId)
            return
        }
        runCatching {
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                runCatching { applyBehavior(chain.thisObject as? View) }
                    .onFailure { logError(module, "apply behavior failed — ${it.message}") }
                result
            }
            log(module) { "hooked ${baseClass.name}.updateMiniBar(${contentClass.simpleName})" }
        }.onFailure {
            hookedLoaders.remove(loaderId)
            logError(module, "updateMiniBar hook failed — ${it.message}")
        }
    }

    /** 每次调用都按当前配置重新应用，天然幂等，切换选项无需重启 SystemUI。 */
    private fun applyBehavior(host: View?) {
        if (host == null) return
        val behavior = ConfigManager.getString(PREF_BEHAVIOR, BEHAVIOR_SYSTEM)
        if (behavior == BEHAVIOR_SYSTEM) {
            miniBarOf(host)?.let { if (it.alpha != 1f) it.alpha = 1f }
            return
        }
        val bar = miniBarOf(host) ?: return
        when (behavior) {
            // 「隐藏」与早期「仅设为透明」等价，合并为同一分支。
            BEHAVIOR_HIDDEN, BEHAVIOR_TRANSPARENT -> {
                // 可见性完全沿用系统（updateMiniBar 只会给 VISIBLE / GONE），只把不透明度清零；
                // miniBarVisible 字段保持 true，所以下拉手势仍然生效。
                bar?.let { it.alpha = if (it.visibility == View.VISIBLE) 0f else 1f }
            }

            BEHAVIOR_REMOVED -> {
                bar?.let {
                    it.alpha = 1f
                    it.visibility = View.GONE
                }
                setMiniBarVisible(host, false)
            }
        }
    }

    private fun miniBarOf(host: View): View? {
        val getter = miniBarGetters.getOrPut(host.javaClass) {
            Optional.ofNullable(
                runCatching { host.javaClass.getMethod("getMiniBar") }.getOrNull(),
            )
        }.orElse(null) ?: return null
        return runCatching { getter.invoke(host) as? View }.getOrNull()
    }

    private fun setMiniBarVisible(host: View, visible: Boolean) {
        val setter = miniBarVisibleSetters.getOrPut(host.javaClass) {
            Optional.ofNullable(
                runCatching {
                    host.javaClass.getMethod("setMiniBarVisible", Boolean::class.javaPrimitiveType)
                }.getOrNull(),
            )
        }.orElse(null) ?: return
        runCatching { setter.invoke(host, visible) }
    }
}
