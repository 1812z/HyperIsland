package io.github.hyperisland.xposed.hook

import android.view.View
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * 普通通知「下拉小白条」行为控制（通知栏行 / 悬浮通知行）。
 *
 * 与岛内的焦点通知小白条是**两套完全独立的实现**：
 * 这里是 `heads_up_mini_window_bar.xml` 合并进 `status_bar_notification_row` 的
 * `R.id.mini_window_bar`，由 `ExpandableNotificationRowInjector.updateMiniWindowBar()` 决定显隐，
 * 手势是否生效则看 `injector.canSlide`（`AppMiniWindowRowTouchHelper` 在 ACTION_DOWN 时读取）。
 *
 * 三种行为合并后（「隐藏」与早期「仅设为透明」实现完全等价，已合并）：
 * - 跟随系统：不动；
 * - 隐藏（即仅设为透明）：可见性沿用系统，只把 alpha 置 0，交互完全不变；
 * - 移除：View 设为 GONE，并把 `canSlide` / `miniBarVisible` 置 false，既看不见也不响应手势。
 *
 * 作用域：com.android.systemui（系统界面）
 */
object NotificationMiniBarHook : BaseHook() {

    private const val TAG = "HyperIsland[NotifMiniBar]"
    private const val PREF_BEHAVIOR = "pref_notification_mini_bar_behavior"

    private const val BEHAVIOR_SYSTEM = "system"
    private const val BEHAVIOR_HIDDEN = "hidden"
    private const val BEHAVIOR_TRANSPARENT = "transparent"
    private const val BEHAVIOR_REMOVED = "removed"

    private const val INJECTOR_CLASS =
        "com.android.systemui.statusbar.notification.row.ExpandableNotificationRowInjector"

    private val hookedLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val miniBarGetters = ConcurrentHashMap<Class<*>, Optional<Method>>()
    private val canSlideFields = ConcurrentHashMap<Class<*>, Optional<Field>>()
    private val miniBarVisibleFields = ConcurrentHashMap<Class<*>, Optional<Field>>()

    /**
     * 通知行数量多且 [updateMiniWindowBar] 调用频繁，行为值缓存一次，
     * 配置变更时由 [onConfigChanged] 刷新，避免每行都走一次 RemotePreferences。
     */
    @Volatile
    private var cachedBehavior = BEHAVIOR_SYSTEM

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        cachedBehavior = ConfigManager.getString(PREF_BEHAVIOR, BEHAVIOR_SYSTEM)
        hookClasses(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookClasses(module, classLoader)
        }
    }

    override fun onConfigChanged() {
        cachedBehavior = ConfigManager.getString(PREF_BEHAVIOR, BEHAVIOR_SYSTEM)
    }

    private fun hookClasses(module: XposedModule, classLoader: ClassLoader) {
        val loaderId = System.identityHashCode(classLoader)
        if (!hookedLoaders.add(loaderId)) return
        val clazz = runCatching { classLoader.loadClass(INJECTOR_CLASS) }.getOrNull()
        if (clazz == null) {
            hookedLoaders.remove(loaderId)
            return
        }
        val mainMethod = runCatching { clazz.getDeclaredMethod("updateMiniWindowBar") }.getOrNull()
        if (mainMethod == null) {
            hookedLoaders.remove(loaderId)
            return
        }
        runCatching {
            module.hook(mainMethod).intercept { chain ->
                val result = chain.proceed()
                runCatching { applyBehavior(chain.thisObject) }
                    .onFailure { logError(module, "apply behavior failed — ${it.message}") }
                result
            }
            // updateFreeformTipView 也会重写 canSlide，移除行为下需要一并压回 false。
            clazz.declaredMethods
                .firstOrNull { it.name == "updateFreeformTipView" && it.parameterCount == 0 }
                ?.let { method ->
                    module.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        if (cachedBehavior == BEHAVIOR_REMOVED) {
                            runCatching {
                                setBooleanField(chain.thisObject, canSlideFields, "canSlide", false)
                            }.onFailure { logError(module, "force canSlide failed — ${it.message}") }
                        }
                        result
                    }
                }
            log(module) { "hooked ${clazz.name}.updateMiniWindowBar()" }
        }.onFailure {
            hookedLoaders.remove(loaderId)
            logError(module, "updateMiniWindowBar hook failed — ${it.message}")
        }
    }

    /** 每次调用都按当前配置重新应用，天然幂等，切换选项无需重启 SystemUI。 */
    private fun applyBehavior(injector: Any?) {
        if (injector == null) return
        val behavior = cachedBehavior
        val bar = miniBarOf(injector)
        when (behavior) {
            // 「隐藏」与早期「仅设为透明」等价，合并为同一分支。
            BEHAVIOR_HIDDEN, BEHAVIOR_TRANSPARENT -> {
                if (bar != null) {
                    // 可见性完全沿用系统（系统只会给 VISIBLE / INVISIBLE），只把不透明度清零；
                    // canSlide 保持不变，下拉手势仍然生效。
                    bar.alpha = if (bar.visibility == View.VISIBLE) 0f else 1f
                }
            }

            BEHAVIOR_REMOVED -> {
                bar?.let {
                    it.alpha = 1f
                    it.visibility = View.GONE
                }
                setBooleanField(injector, canSlideFields, "canSlide", false)
                setBooleanField(injector, miniBarVisibleFields, "miniBarVisible", false)
            }

            else -> bar?.let { if (it.alpha != 1f) it.alpha = 1f }
        }
    }

    private fun miniBarOf(injector: Any): View? {
        val getter = miniBarGetters.getOrPut(injector.javaClass) {
            Optional.ofNullable(
                runCatching { injector.javaClass.getMethod("getMiniBar") }.getOrNull(),
            )
        }.orElse(null) ?: return null
        return runCatching { getter.invoke(injector) as? View }.getOrNull()
    }

    private fun setBooleanField(
        instance: Any?,
        cache: ConcurrentHashMap<Class<*>, Optional<Field>>,
        name: String,
        value: Boolean,
    ) {
        if (instance == null) return
        val field = cache.getOrPut(instance.javaClass) {
            Optional.ofNullable(
                runCatching {
                    instance.javaClass.getDeclaredField(name).apply { isAccessible = true }
                }.getOrNull(),
            )
        }.orElse(null) ?: return
        field.setBoolean(instance, value)
    }
}
