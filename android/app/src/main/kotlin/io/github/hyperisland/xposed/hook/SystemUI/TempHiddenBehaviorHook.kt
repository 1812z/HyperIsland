package io.github.hyperisland.xposed.hook

import android.app.Application
import android.content.Context
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.SceneBehavior
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object TempHiddenBehaviorHook : BaseHook() {

    private const val TAG = "HyperIsland[TempHiddenBehavior]"
    private const val WINDOW_VIEW_CONTROLLER_CLASS =
        "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController"
    private const val PREF_LANDSCAPE_DISABLE_FULLSCREEN =
        "pref_temp_hide_fullscreen_landscape_disable"

    private val hookedClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val invokingWithFilteredArgs = ThreadLocal.withInitial { false }

    private var appContext: Context? = null

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        hookApplicationOnCreate(module, param)
        hookClasses(module, param.defaultClassLoader)
        io.github.hyperisland.xposed.utils.HookUtils.hookDynamicClassLoaders(
            module,
            ClassLoader.getSystemClassLoader()
        ) { classLoader -> hookClasses(module, classLoader) }
    }

    private fun hookApplicationOnCreate(module: XposedModule, param: PackageLoadedParam) {
        try {
            val method = param.defaultClassLoader
                .loadClass("android.app.Application")
                .getDeclaredMethod("onCreate")
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val app = chain.thisObject as? Application
                if (app != null && appContext == null) {
                    appContext = app.applicationContext
                }
                result
            }
            log(module, "hooked Application.onCreate")
        } catch (e: Throwable) {
            logError(module, "Application.onCreate hook failed: ${e.message}")
        }
    }

    private fun hookClasses(module: XposedModule, classLoader: ClassLoader) {
        val clId = System.identityHashCode(classLoader)
        if (!hookedClassLoaders.add(clId)) return
        var hookedAny = false
        hookedAny = hookWindowViewController(module, classLoader) || hookedAny
        if (!hookedAny) hookedClassLoaders.remove(clId)
    }

    private fun hookWindowViewController(module: XposedModule, classLoader: ClassLoader): Boolean {
        val clazz = runCatching { classLoader.loadClass(WINDOW_VIEW_CONTROLLER_CLASS) }.getOrNull()
            ?: return false
        var count = 0
        hookBooleanMethod(module, clazz, "lockScreen", HideBehavior.SCREEN_LOCKED) { count++ }
        hookBooleanMethod(module, clazz, "notificationAppearance", HideBehavior.NOTIFICATION_CENTER) { count++ }
        hookStatusBarAppearance(module, clazz) { count++ }
        hookBooleanMethod(module, clazz, "screenPinningActive", HideBehavior.SCREEN_PINNING) { count++ }
        hookNotificationPanelExpandHeightChanged(module, clazz) { count++ }
        hookCommandQueueDisable(module, clazz) { count++ }
        log(module, "hooked windowViewController temp hide methods=$count")
        return count > 0
    }

    private fun hookBooleanMethod(
        module: XposedModule,
        clazz: Class<*>,
        methodName: String,
        behavior: HideBehavior,
        onHooked: () -> Unit,
    ) {
        clazz.declaredMethods
            .filter { it.name == methodName && it.parameterCount == 1 && it.parameterTypes[0] == Boolean::class.javaPrimitiveType }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    if (invokingWithFilteredArgs.get()) return@intercept chain.proceed()
                    val original = chain.args.getOrNull(0)
                    val blocked = original == true && !behavior.enabled()
                    log(
                        module,
                        "${clazz.simpleName}.$methodName original=$original behavior=${behavior.name} enabled=${behavior.enabled()} blocked=$blocked"
                    )
                    if (blocked) {
                        invokeWithFilteredArgs(method, chain.thisObject, false)
                    } else {
                        chain.proceed()
                    }
                }
                onHooked()
        }
    }

    private fun hookStatusBarAppearance(
        module: XposedModule,
        clazz: Class<*>,
        onHooked: () -> Unit,
    ) {
        clazz.declaredMethods
            .filter { it.name == "statusBarAppearance" && it.parameterCount == 1 && it.parameterTypes[0] == Boolean::class.javaPrimitiveType }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    if (invokingWithFilteredArgs.get()) return@intercept chain.proceed()
                    val original = chain.args.getOrNull(0) as? Boolean
                    val fullscreenEnabled = HideBehavior.FULLSCREEN.enabled()
                    val landscapeDisable = ConfigManager.getBoolean(
                        PREF_LANDSCAPE_DISABLE_FULLSCREEN,
                        false
                    )
                    val interceptionCandidate = original == true && !fullscreenEnabled
                    val isLandscape = interceptionCandidate && landscapeDisable &&
                        appContext?.let(SceneBehavior::isLandscape) == true
                    val blocked = interceptionCandidate && !isLandscape
                    logWarn(
                        module,
                        "statusBarAppearance received: " +
                            "original=$original fullscreenEnabled=$fullscreenEnabled " +
                            "landscapeDisable=$landscapeDisable isLandscape=$isLandscape " +
                            "blocked=$blocked"
                    )
                    if (blocked) {
                        invokeWithFilteredArgs(method, chain.thisObject, false)
                    } else {
                        chain.proceed()
                    }
                }
                onHooked()
            }
    }

    private fun hookNotificationPanelExpandHeightChanged(
        module: XposedModule,
        clazz: Class<*>,
        onHooked: () -> Unit,
    ) {
        clazz.declaredMethods
            .filter {
                it.name == "notificationPanelExpandHeightChanged" &&
                    it.parameterCount == 2 &&
                    it.parameterTypes[0] == Float::class.javaPrimitiveType &&
                    it.parameterTypes[1] == Float::class.javaPrimitiveType
            }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    if (invokingWithFilteredArgs.get()) return@intercept chain.proceed()
                    val expandHeight = chain.args.getOrNull(0) as? Float
                    val expandThresh = chain.args.getOrNull(1) as? Float
                    val blocked = expandHeight != null && expandHeight > 0f && !HideBehavior.NOTIFICATION_CENTER.enabled()
                    log(module, "DynamicIslandWindowViewController.notificationPanelExpandHeightChanged expandHeight=$expandHeight notificationCenterEnabled=${HideBehavior.NOTIFICATION_CENTER.enabled()} blocked=$blocked")
                    if (blocked && expandThresh != null) {
                        invokeWithFilteredArgs(method, chain.thisObject, 0f, expandThresh)
                    } else {
                        chain.proceed()
                    }
                }
                onHooked()
            }
    }

    private fun hookCommandQueueDisable(
        module: XposedModule,
        clazz: Class<*>,
        onHooked: () -> Unit,
    ) {
        clazz.declaredMethods
            .filter { it.name == "commandQueueDisable" && it.parameterCount == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    if (invokingWithFilteredArgs.get()) return@intercept chain.proceed()
                    val original = chain.args.getOrNull(0) as? Int
                    val notificationIconsDisabled = original != null && (original and 0x200000) != 0
                    val blocked = notificationIconsDisabled && !HideBehavior.NOTIFICATION_CENTER.enabled()
                    log(module, "DynamicIslandWindowViewController.commandQueueDisable original=$original notificationCenterEnabled=${HideBehavior.NOTIFICATION_CENTER.enabled()} blocked=$blocked")
                    if (blocked) {
                        invokeWithFilteredArgs(method, chain.thisObject, original and 0x200000.inv())
                    } else {
                        chain.proceed()
                    }
                }
                onHooked()
            }
    }

    private fun invokeWithFilteredArgs(method: Method, target: Any?, vararg args: Any?): Any? {
        invokingWithFilteredArgs.set(true)
        return try {
            method.isAccessible = true
            method.invoke(target, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        } finally {
            invokingWithFilteredArgs.set(false)
        }
    }

    private enum class HideBehavior(private val prefKey: String) {
        SCREEN_PINNING("pref_temp_hide_screen_pinning"),
        FULLSCREEN("pref_temp_hide_fullscreen"),
        SCREEN_LOCKED("pref_temp_hide_screen_locked"),
        NOTIFICATION_CENTER("pref_temp_hide_notification_center");

        fun enabled(): Boolean = ConfigManager.getBoolean(prefKey, true)
    }
}
