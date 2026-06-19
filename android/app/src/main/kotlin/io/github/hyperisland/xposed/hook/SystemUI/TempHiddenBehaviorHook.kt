package io.github.hyperisland.xposed.hook

import io.github.hyperisland.xposed.ConfigManager
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.concurrent.ConcurrentHashMap

object TempHiddenBehaviorHook : BaseHook() {

    private const val TAG = "HyperIsland[TempHiddenBehavior]"
    private const val WINDOW_STATE_CLASS = "miui.systemui.dynamicisland.window.DynamicIslandWindowState"
    private const val WINDOW_VIEW_CONTROLLER_CLASS =
        "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController"
    private const val WINDOW_VIEW_CLASS = "miui.systemui.dynamicisland.window.DynamicIslandWindowView"
    private const val EVENT_COORDINATOR_CLASS =
        "miui.systemui.dynamicisland.event.DynamicIslandEventCoordinator"
    private const val DESKTOP_STATE_CONTROLLER_CLASS =
        "miui.systemui.dynamicisland.window.DynamicIslandDesktopStateController"

    private val hookedClassLoaders = ConcurrentHashMap.newKeySet<Int>()

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        hookClasses(module, param.defaultClassLoader)
        io.github.hyperisland.xposed.utils.HookUtils.hookDynamicClassLoaders(
            module,
            ClassLoader.getSystemClassLoader()
        ) { classLoader -> hookClasses(module, classLoader) }
    }

    private fun hookClasses(module: XposedModule, classLoader: ClassLoader) {
        val clId = System.identityHashCode(classLoader)
        if (!hookedClassLoaders.add(clId)) return
        var hookedAny = false
        hookedAny = hookWindowState(module, classLoader) || hookedAny
        hookedAny = hookWindowView(module, classLoader) || hookedAny
        hookedAny = hookEventCoordinator(module, classLoader) || hookedAny
        hookedAny = hookWindowViewController(module, classLoader) || hookedAny
        hookedAny = hookDesktopStateController(module, classLoader) || hookedAny
        if (!hookedAny) hookedClassLoaders.remove(clId)
    }

    private fun hookWindowState(module: XposedModule, classLoader: ClassLoader): Boolean {
        val clazz = runCatching { classLoader.loadClass(WINDOW_STATE_CLASS) }.getOrNull() ?: return false
        var count = 0
        clazz.declaredMethods
            .filter { it.name == "isTempHidden" && it.parameterCount == 1 }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val typeName = currentTypeName(chain.thisObject)
                    val blocked = shouldBlockWindowState(chain.thisObject, typeName)
                    log(
                        module,
                        "isTempHidden result=$result type=$typeName states=${windowStateSummary(chain.thisObject)} blocked=$blocked arg=${chain.args.getOrNull(0)}"
                    )
                    if (result == true && blocked) {
                        clearBlockedStates(chain.thisObject)
                        false
                    } else {
                        result
                    }
                }
                count++
            }
        log(module, "hooked windowState isTempHidden methods=$count")
        return count > 0
    }

    private fun hookWindowView(module: XposedModule, classLoader: ClassLoader): Boolean {
        val clazz = runCatching { classLoader.loadClass(WINDOW_VIEW_CLASS) }.getOrNull()
            ?: return false
        var count = 0
        clazz.declaredMethods
            .filter { it.name == "onIslandTempHide" && it.parameterCount == 2 }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val hide = chain.args.getOrNull(0) as? Boolean
                    val typeName = chain.args.getOrNull(1)?.toString()
                    val blocked = hide == true && typeName != null && shouldBlockType(typeName)
                    log(module, "onIslandTempHide hide=$hide type=$typeName blocked=$blocked")
                    if (blocked) null else chain.proceed()
                }
                count++
            }
        clazz.declaredMethods
            .filter { it.name == "collapse" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val reason = chain.args.getOrNull(0) as? String
                    val blocked = shouldBlockCollapse(reason)
                    log(module, "collapse reason=$reason blocked=$blocked")
                    if (blocked) null else chain.proceed()
                }
                count++
            }
        clazz.declaredMethods
            .filter { it.name == "hideAllElementSurface" && it.parameterCount == 0 }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val blocked = !HideBehavior.SCREEN_LOCKED.enabled()
                    log(module, "hideAllElementSurface blocked=$blocked")
                    if (blocked) null else chain.proceed()
                }
                count++
            }
        hookBooleanMethod(module, clazz, "updateStatusBarVisible", HideBehavior.FULLSCREEN) { count++ }
        log(module, "hooked windowView temp hide methods=$count")
        return count > 0
    }

    private fun hookEventCoordinator(module: XposedModule, classLoader: ClassLoader): Boolean {
        val clazz = runCatching { classLoader.loadClass(EVENT_COORDINATOR_CLASS) }.getOrNull()
            ?: return false
        var count = 0
        clazz.declaredMethods
            .filter { it.name == "updateStatusBarVisible" && it.parameterCount == 1 && it.parameterTypes[0] == Boolean::class.javaPrimitiveType }
            .forEach { method ->
                module.hook(method).intercept { chain ->
                    val visible = chain.args.getOrNull(0) as? Boolean
                    val blocked = visible == false && !HideBehavior.FULLSCREEN.enabled()
                    log(module, "EventCoordinator.updateStatusBarVisible visible=$visible fullscreenEnabled=${HideBehavior.FULLSCREEN.enabled()} blocked=$blocked")
                    if (blocked) null else chain.proceed()
                }
                count++
            }
        log(module, "hooked eventCoordinator status bar methods=$count")
        return count > 0
    }

    private fun hookWindowViewController(module: XposedModule, classLoader: ClassLoader): Boolean {
        val clazz = runCatching { classLoader.loadClass(WINDOW_VIEW_CONTROLLER_CLASS) }.getOrNull()
            ?: return false
        var count = 0
        hookBooleanMethod(module, clazz, "lockScreen", HideBehavior.SCREEN_LOCKED) { count++ }
        hookBooleanMethod(module, clazz, "notificationAppearance", HideBehavior.NOTIFICATION_CENTER) { count++ }
        hookBooleanMethod(module, clazz, "controlCenterExpanded", HideBehavior.CONTROL_CENTER) { count++ }
        hookBooleanMethod(module, clazz, "statusBarAppearance", HideBehavior.FULLSCREEN) { count++ }
        hookBooleanMethod(module, clazz, "screenPinningActive", HideBehavior.SCREEN_PINNING) { count++ }
        log(module, "hooked windowViewController temp hide methods=$count")
        return count > 0
    }

    private fun hookDesktopStateController(module: XposedModule, classLoader: ClassLoader): Boolean {
        val clazz = runCatching { classLoader.loadClass(DESKTOP_STATE_CONTROLLER_CLASS) }.getOrNull()
            ?: return false
        var count = 0
        hookBooleanMethod(module, clazz, "runDesktopAnim", HideBehavior.DESKTOP_ANIMATING) { count++ }
        log(module, "hooked desktopStateController temp hide methods=$count")
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
                    val original = chain.args.getOrNull(0)
                    val blocked = original == true && !behavior.enabled()
                    log(
                        module,
                        "${clazz.simpleName}.$methodName original=$original behavior=${behavior.name} enabled=${behavior.enabled()} blocked=$blocked"
                    )
                    if (blocked) {
                        chain.args[0] = false
                    }
                    chain.proceed()
                }
                onHooked()
        }
    }

    private fun currentTypeName(windowState: Any?): String? {
        return invokeNoArg(windowState, "getTempHiddenType")?.toString()
    }

    private fun shouldBlockType(typeName: String): Boolean {
        return when (typeName) {
            "SCREEN_LOCKED" -> !HideBehavior.SCREEN_LOCKED.enabled()
            "BOUNCER_SHOWING" -> !HideBehavior.BOUNCER_SHOWING.enabled()
            "SCREEN_PINNING_ACTIVE" -> !HideBehavior.SCREEN_PINNING.enabled()
            "DESKTOP_ANIMATING" -> !HideBehavior.DESKTOP_ANIMATING.enabled()
            "STATUS_BAR_DISAPPEARANCE" -> !HideBehavior.FULLSCREEN.enabled()
            "NOTIFICATION_APPEARANCE",
            "NOTIFICATION_SWIPE_TO_APPEARANCE",
            "SHOW_NOTIFICATION_ICONS" -> !HideBehavior.NOTIFICATION_CENTER.enabled()
            "CONTROL_CENTER_EXPANDED",
            "CONTROL_CENTER_SWIPE_TO_APPEARANCE" -> !HideBehavior.CONTROL_CENTER.enabled()
            else -> false
        }
    }

    private fun shouldBlockWindowState(windowState: Any?, typeName: String?): Boolean {
        return when {
            getStateValue(windowState, "getScreenLocked") == true -> !HideBehavior.SCREEN_LOCKED.enabled()
            getStateValue(windowState, "getBouncerShowing") == true -> !HideBehavior.BOUNCER_SHOWING.enabled()
            getStateValue(windowState, "getScreenPinning") == true -> !HideBehavior.SCREEN_PINNING.enabled()
            getStateValue(windowState, "getDeskTopAnimating") == true -> !HideBehavior.DESKTOP_ANIMATING.enabled()
            getStateValue(windowState, "getStatusBarDisappearance") == true -> !HideBehavior.FULLSCREEN.enabled()
            getStateValue(windowState, "getStatusBarViewShowing") == false -> !HideBehavior.FULLSCREEN.enabled()
            getStateValue(windowState, "getNotificationAppearance") == true -> !HideBehavior.NOTIFICATION_CENTER.enabled()
            getStateValue(windowState, "getNotificationPanelSwipeToAppearance") == true -> !HideBehavior.NOTIFICATION_CENTER.enabled()
            getStateValue(windowState, "getShowNotificationIcons") == false -> !HideBehavior.NOTIFICATION_CENTER.enabled()
            getStateValue(windowState, "getControlCenterExpanded") == true -> !HideBehavior.CONTROL_CENTER.enabled()
            getStateValue(windowState, "getControlCenterPanelSwipeToAppearance") == true -> !HideBehavior.CONTROL_CENTER.enabled()
            typeName != null -> shouldBlockType(typeName)
            else -> false
        }
    }

    private fun windowStateSummary(windowState: Any?): String {
        return listOf(
            "screenLocked=${getStateValue(windowState, "getScreenLocked")}",
            "bouncer=${getStateValue(windowState, "getBouncerShowing")}",
            "pinning=${getStateValue(windowState, "getScreenPinning")}",
            "desktop=${getStateValue(windowState, "getDeskTopAnimating")}",
            "statusGone=${getStateValue(windowState, "getStatusBarDisappearance")}",
            "statusShowing=${getStateValue(windowState, "getStatusBarViewShowing")}",
            "notif=${getStateValue(windowState, "getNotificationAppearance")}",
            "notifSwipe=${getStateValue(windowState, "getNotificationPanelSwipeToAppearance")}",
            "showIcons=${getStateValue(windowState, "getShowNotificationIcons")}",
            "cc=${getStateValue(windowState, "getControlCenterExpanded")}",
            "ccSwipe=${getStateValue(windowState, "getControlCenterPanelSwipeToAppearance")}",
        ).joinToString(prefix = "[", postfix = "]")
    }

    private fun getStateValue(target: Any?, getterName: String): Boolean? {
        val state = invokeNoArg(target, getterName) ?: return null
        return invokeNoArg(state, "getValue") as? Boolean
    }

    private fun clearBlockedStates(windowState: Any?) {
        if (!HideBehavior.SCREEN_LOCKED.enabled()) {
            setStateValue(windowState, "getScreenLocked", false)
        }
        if (!HideBehavior.BOUNCER_SHOWING.enabled()) {
            setStateValue(windowState, "getBouncerShowing", false)
        }
        if (!HideBehavior.SCREEN_PINNING.enabled()) {
            setStateValue(windowState, "getScreenPinning", false)
        }
        if (!HideBehavior.DESKTOP_ANIMATING.enabled()) {
            setStateValue(windowState, "getDeskTopAnimating", false)
        }
        if (!HideBehavior.FULLSCREEN.enabled()) {
            setStateValue(windowState, "getStatusBarDisappearance", false)
            setStateValue(windowState, "getStatusBarViewShowing", true)
        }
        if (!HideBehavior.NOTIFICATION_CENTER.enabled()) {
            setStateValue(windowState, "getNotificationAppearance", false)
            setStateValue(windowState, "getNotificationPanelSwipeToAppearance", false)
            setStateValue(windowState, "getShowNotificationIcons", true)
        }
        if (!HideBehavior.CONTROL_CENTER.enabled()) {
            setStateValue(windowState, "getControlCenterExpanded", false)
            setStateValue(windowState, "getControlCenterPanelSwipeToAppearance", false)
        }
    }

    private fun setStateValue(target: Any?, getterName: String, value: Boolean) {
        val state = invokeNoArg(target, getterName) ?: return
        runCatching {
            state.javaClass.methods.firstOrNull { method ->
                method.name == "setValue" && method.parameterCount == 1
            }?.invoke(state, value)
        }
    }

    private fun shouldBlockCollapse(reason: String?): Boolean {
        return when (reason) {
            "cc expand" -> !HideBehavior.CONTROL_CENTER.enabled()
            "lockscreen" -> !HideBehavior.SCREEN_LOCKED.enabled()
            else -> false
        }
    }

    private fun invokeNoArg(target: Any?, methodName: String): Any? {
        if (target == null) return null
        return runCatching {
            target.javaClass.methods.firstOrNull { it.name == methodName && it.parameterCount == 0 }
                ?.invoke(target)
        }.getOrNull()
    }

    private enum class HideBehavior(private val prefKey: String) {
        SCREEN_PINNING("pref_temp_hide_screen_pinning"),
        BOUNCER_SHOWING("pref_temp_hide_bouncer_showing"),
        DESKTOP_ANIMATING("pref_temp_hide_desktop_animating"),
        FULLSCREEN("pref_temp_hide_fullscreen"),
        SCREEN_LOCKED("pref_temp_hide_screen_locked"),
        NOTIFICATION_CENTER("pref_temp_hide_notification_center"),
        CONTROL_CENTER("pref_temp_hide_control_center");

        fun enabled(): Boolean = ConfigManager.getBoolean(prefKey, true)
    }
}
