package io.github.hyperisland.xposed.hook.SystemUI

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/**
 * Reuses Xiaomi's lock-screen magazine remote animation for MiLink's device center.
 *
 * SystemUI starts the target as soon as the gesture begins, reparents its animation leash under
 * the keyguard surface, and moves that leash with the finger. The stock implementation only
 * accepts the magazine package, so the target check has to be widened together with the Intent.
 */
object LockscreenDeviceCenterHook : BaseHook() {

    private const val TAG = "HyperIsland[LockscreenDeviceCenter]"
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val MILINK_PACKAGE = "com.milink.service"
    private const val DEVICE_CENTER_ACTIVITY =
        "com.miui.circulate.world.CirculateWorldActivity"
    private const val DEVICE_CENTER_ACTION = "com.milink.service.deviceworld"
    private const val DEVICE_CENTER_URI = "milink://com.milink.service/circulate_world"
    private const val EXTRA_LOCKSCREEN_LAUNCH =
        "io.github.hyperisland.extra.LOCKSCREEN_DEVICE_CENTER"

    private const val MOVE_LEFT_CONTROLLER =
        "com.android.keyguard.negative.KeyguardMoveLeftController"
    private const val MAGAZINE_CONTROLLER =
        "com.android.keyguard.magazine.LockScreenMagazineController"
    private const val MAGAZINE_HELPER =
        "com.android.keyguard.magazine.KeyguardMagazineHelper"

    @Volatile private var systemUiHooked = false
    @Volatile private var deviceCenterHooked = false

    private val targetDecorViews = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val screenOffReceivers =
        Collections.synchronizedMap(WeakHashMap<Activity, BroadcastReceiver>())

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        when (param.packageName) {
            SYSTEM_UI_PACKAGE -> hookSystemUi(module, param.defaultClassLoader)
            MILINK_PACKAGE -> hookDeviceCenter(module, param.defaultClassLoader)
        }
    }

    private fun hookSystemUi(module: XposedModule, classLoader: ClassLoader) {
        if (systemUiHooked) return

        val moveController = classLoader.loadClass(MOVE_LEFT_CONTROLLER)
        val magazineController = classLoader.loadClass(MAGAZINE_CONTROLLER)
        val magazineHelper = classLoader.loadClass(MAGAZINE_HELPER)

        hookBooleanResult(module, moveController, "supportMoveToRight")
        hookBooleanResult(module, moveController, "isLeftViewLaunchActivity")
        hookBooleanResult(module, moveController, "isSupportSwipeToLaunchMagazine")

        findMethod(magazineController, "getPreLeftScreenIntent", 0).let { method ->
            module.hook(method).intercept {
                Intent(DEVICE_CENTER_ACTION).apply {
                    component = ComponentName(MILINK_PACKAGE, DEVICE_CENTER_ACTIVITY)
                    data = Uri.parse(DEVICE_CENTER_URI)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("from", "keyguard")
                    putExtra("entry_source", "swipe")
                    putExtra(EXTRA_LOCKSCREEN_LAUNCH, true)
                }
            }
        }

        findMethod(magazineHelper, "checkIsMagazineRemoteAnimation", 1).let { method ->
            module.hook(method).intercept { chain ->
                if (remoteTargetPackage(chain.args.getOrNull(0)) == MILINK_PACKAGE) {
                    true
                } else {
                    chain.proceed()
                }
            }
        }

        systemUiHooked = true
        log(module, "SystemUI negative-one page redirected to $DEVICE_CENTER_ACTIVITY")
    }

    private fun hookDeviceCenter(module: XposedModule, classLoader: ClassLoader) {
        if (deviceCenterHooked) return

        val activityClass = classLoader.loadClass(DEVICE_CENTER_ACTIVITY)
        hookBlurRatioControllers(module, classLoader)
        val onCreate = activityClass.getDeclaredMethod("onCreate", Bundle::class.java).apply {
            isAccessible = true
        }
        module.hook(onCreate).intercept { chain ->
            val activity = chain.thisObject as? Activity
            if (activity?.isLockscreenLaunch() == true) {
                sanitizeDeviceCenterWindow(activity)
            }
            val result = chain.proceed()
            if (activity?.isLockscreenLaunch() == true) {
                prepareDeviceCenterFrame(activity)
                registerScreenOffReceiver(activity)
            }
            result
        }

        hookWindowSanitizer(module, activityClass, "onStart", 0)
        hookWindowSanitizer(module, activityClass, "onResume", 0)
        hookWindowSanitizer(module, activityClass, "onWindowFocusChanged", 1)
        hookImmediateFinish(module, activityClass)
        hookNewIntent(module, activityClass)
        hookActivityCleanup(module, activityClass)

        deviceCenterHooked = true
        log(module, "$DEVICE_CENTER_ACTIVITY allowed to occlude keyguard")
    }

    private fun hookWindowSanitizer(
        module: XposedModule,
        activityClass: Class<*>,
        methodName: String,
        parameterCount: Int,
    ) {
        val method = findMethodInHierarchy(activityClass, methodName, parameterCount) ?: return
        module.hook(method).intercept { chain ->
            val activity = chain.thisObject as? Activity
            if (activity != null && activityClass.isInstance(activity) && activity.isLockscreenLaunch()) {
                prepareDeviceCenterFrame(activity)
            }
            val result = chain.proceed()
            if (activity != null && activityClass.isInstance(activity) && activity.isLockscreenLaunch()) {
                prepareDeviceCenterFrame(activity)
            }
            result
        }
    }

    /**
     * MiLink's Activity base class overrides finish() to fade its blur layer first and only calls
     * the framework finish up to two seconds later. Bypass that override for this Activity only;
     * finishing the dedicated task immediately starts Keyguard's unocclude remote transition,
     * whose closing leash is then moved left by SystemUI's stock spring animation.
     */
    private fun hookImmediateFinish(module: XposedModule, activityClass: Class<*>) {
        val finishMethod = findOverrideInSuperclasses(activityClass, "finish", 0) ?: return
        module.hook(finishMethod).intercept { chain ->
            val activity = chain.thisObject as? Activity
            if (
                activity == null ||
                !activityClass.isInstance(activity) ||
                !activity.isLockscreenLaunch()
            ) {
                return@intercept chain.proceed()
            }
            prepareDeviceCenterFrame(activity)
            activity.finishAndRemoveTask()
            null
        }
    }

    private fun prepareDeviceCenterFrame(activity: Activity) {
        sanitizeDeviceCenterWindow(activity)
        activity.window.decorView.let { decor ->
            targetDecorViews[decor] = true
            decor.animate().cancel()
            decor.alpha = 1f
        }
        val contentId = activity.resources.getIdentifier(
            "activity_content_view",
            "id",
            MILINK_PACKAGE,
        )
        if (contentId != 0) {
            activity.findViewById<android.view.View>(contentId)?.let { content ->
                content.animate().cancel()
                content.alpha = 1f
            }
        }
    }

    /**
     * MiLink keeps this callback name with @Keep. Discover its owner through BlurUtils' declared
     * classes, then pin only the marked Activity's decor blur at the fully-rendered ratio. This
     * preserves the stock material without its independent time-based fade.
     */
    private fun hookBlurRatioControllers(module: XposedModule, classLoader: ClassLoader) {
        val blurUtils = runCatching {
            classLoader.loadClass("com.miui.circulate.world.utils.BlurUtils")
        }.getOrNull() ?: return
        val controllerClasses = buildList {
            blurUtils.declaredClasses.forEach { nested ->
                add(nested)
                nested.superclass?.let { add(it) }
            }
        }.distinct()
        controllerClasses.flatMap { it.declaredMethods.asIterable() }
            .filter {
                it.name == "setBlurRatio" &&
                    it.parameterCount == 1 &&
                    it.parameterTypes[0] == Float::class.javaPrimitiveType
            }
            .distinctBy { "${it.declaringClass.name}#${it.name}" }
            .forEach { method ->
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    val decor = findViewField(chain.thisObject)
                    if (decor != null && targetDecorViews.containsKey(decor)) {
                        chain.args[0] = 1f
                    }
                    chain.proceed()
                }
            }
    }

    private fun findViewField(instance: Any?): View? {
        if (instance == null) return null
        var current: Class<*>? = instance.javaClass
        while (current != null) {
            current.declaredFields.firstOrNull { View::class.java.isAssignableFrom(it.type) }
                ?.let { field ->
                    field.isAccessible = true
                    return field.get(instance) as? View
                }
            current = current.superclass
        }
        return null
    }

    private fun registerScreenOffReceiver(activity: Activity) {
        if (screenOffReceivers.containsKey(activity)) return
        val activityRef = WeakReference(activity)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val target = activityRef.get()
                if (
                    intent?.action == Intent.ACTION_SCREEN_OFF &&
                    target != null &&
                    !target.isDestroyed
                ) {
                    target.finishAndRemoveTask()
                }
            }
        }
        activity.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            Context.RECEIVER_NOT_EXPORTED,
        )
        screenOffReceivers[activity] = receiver
    }

    private fun hookNewIntent(module: XposedModule, activityClass: Class<*>) {
        val onNewIntent = findMethodInHierarchy(activityClass, "onNewIntent", 1) ?: return
        module.hook(onNewIntent).intercept { chain ->
            val result = chain.proceed()
            val activity = chain.thisObject as? Activity
            if (activity != null && activityClass.isInstance(activity)) {
                if (activity.isLockscreenLaunch()) {
                    prepareDeviceCenterFrame(activity)
                    registerScreenOffReceiver(activity)
                } else {
                    targetDecorViews.remove(activity.window.decorView)
                    screenOffReceivers.remove(activity)?.let { receiver ->
                        runCatching { activity.unregisterReceiver(receiver) }
                    }
                    activity.setShowWhenLocked(false)
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                }
            }
            result
        }
    }

    private fun hookActivityCleanup(module: XposedModule, activityClass: Class<*>) {
        val onDestroy = findMethodInHierarchy(activityClass, "onDestroy", 0) ?: return
        module.hook(onDestroy).intercept { chain ->
            val activity = chain.thisObject as? Activity
            if (activity != null && activityClass.isInstance(activity)) {
                targetDecorViews.remove(activity.window.decorView)
                screenOffReceivers.remove(activity)?.let { receiver ->
                    runCatching { activity.unregisterReceiver(receiver) }
                }
            }
            chain.proceed()
        }
    }

    @Suppress("DEPRECATION")
    private fun sanitizeDeviceCenterWindow(activity: Activity) {
        activity.setShowWhenLocked(true)
        activity.window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            attributes = attributes.apply {
                windowAnimations = 0
            }
        }
    }

    private fun Activity.isLockscreenLaunch(): Boolean =
        intent?.getBooleanExtra(EXTRA_LOCKSCREEN_LAUNCH, false) == true

    private fun hookBooleanResult(module: XposedModule, clazz: Class<*>, name: String) {
        module.hook(findMethod(clazz, name, 0)).intercept { true }
    }

    private fun remoteTargetPackage(target: Any?): String? {
        if (target == null) return null
        val taskInfo = findField(target.javaClass, "taskInfo")?.get(target) ?: return null
        return sequenceOf("baseActivity", "realActivity", "topActivity")
            .mapNotNull { fieldName ->
                (findField(taskInfo.javaClass, fieldName)?.get(taskInfo) as? ComponentName)
                    ?.packageName
            }
            .firstOrNull(String::isNotEmpty)
    }

    private fun findMethod(clazz: Class<*>, name: String, parameterCount: Int): Method =
        clazz.declaredMethods.firstOrNull {
            it.name == name && it.parameterCount == parameterCount
        }?.apply { isAccessible = true }
            ?: throw NoSuchMethodException("${clazz.name}.$name/$parameterCount")

    private fun findMethodInHierarchy(
        clazz: Class<*>,
        name: String,
        parameterCount: Int,
    ): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods.firstOrNull {
                it.name == name && it.parameterCount == parameterCount
            }?.let { return it.apply { isAccessible = true } }
            current = current.superclass
        }
        return null
    }

    private fun findOverrideInSuperclasses(
        clazz: Class<*>,
        name: String,
        parameterCount: Int,
    ): Method? {
        var current = clazz.superclass
        while (current != null && current != Activity::class.java) {
            current.declaredMethods.firstOrNull {
                it.name == name && it.parameterCount == parameterCount
            }?.let { return it.apply { isAccessible = true } }
            current = current.superclass
        }
        return null
    }

    private fun findField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }
}
