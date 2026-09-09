package io.github.hyperisland.xposed.hook.SystemUI

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.PathInterpolator
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs

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

    private const val GESTURE_UNDECIDED = 0
    private const val GESTURE_PASSTHROUGH = 1
    private const val GESTURE_DISMISSING = 2
    private const val HORIZONTAL_DIRECTION_RATIO = 1.35f
    private const val DISMISS_DISTANCE_RATIO = 0.28f
    private const val DISMISS_MIN_VELOCITY_DP = 900f

    private const val MOVE_LEFT_CONTROLLER =
        "com.android.keyguard.negative.KeyguardMoveLeftController"
    private const val MAGAZINE_CONTROLLER =
        "com.android.keyguard.magazine.LockScreenMagazineController"
    private const val MAGAZINE_HELPER =
        "com.android.keyguard.magazine.KeyguardMagazineHelper"
    private const val KEYGUARD_MOVE_HELPER =
        "com.android.keyguard.panel.KeyguardMoveHelper"

    @Volatile private var systemUiHooked = false
    @Volatile private var deviceCenterHooked = false

    private val screenOffReceivers =
        Collections.synchronizedMap(WeakHashMap<Activity, BroadcastReceiver>())
    private val swipeStates =
        Collections.synchronizedMap(WeakHashMap<Activity, FullscreenSwipeState>())

    private class FullscreenSwipeState {
        var mode = GESTURE_UNDECIDED
        var downX = 0f
        var downY = 0f
        var tracker: VelocityTracker? = null
    }

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

        hookSystemScrimSuppression(module, classLoader)

        systemUiHooked = true
        log(module, "SystemUI negative-one page redirected to $DEVICE_CENTER_ACTIVITY")
    }

    /**
     * The magazine gesture normally darkens and blurs SystemUI's front scrim while revealing the
     * remote Activity. MiLink already draws its own full-page blur, so applying both produces a
     * delayed black layer under its translucent window. Keep KeyguardMoveHelper's translation
     * path intact and suppress only that positive-distance scrim update. SystemUI also translates
     * its original magazine page alongside the remote leash; that page owns another dark blur
     * background, so hide the page and its low-end left_view_bg fallback in the same frame.
     */
    private fun hookSystemScrimSuppression(
        module: XposedModule,
        classLoader: ClassLoader,
    ) {
        runCatching {
            val moveHelperClass = classLoader.loadClass(KEYGUARD_MOVE_HELPER)
            val blurMethod = findMethod(moveHelperClass, "updateKeyguardInfoBlurRatio", 1)
            val leftViewField = findField(moveHelperClass, "mLeftView")
            val leftViewBgField = findField(moveHelperClass, "mLeftViewBg")
            module.hook(blurMethod).intercept { chain ->
                val translation = (chain.args.getOrNull(0) as? Number)?.toFloat() ?: 0f
                if (translation <= 0f) {
                    return@intercept chain.proceed()
                }
                (leftViewField?.get(chain.thisObject) as? View)?.visibility = View.INVISIBLE
                (leftViewBgField?.get(chain.thisObject) as? View)?.apply {
                    alpha = 0f
                    visibility = View.INVISIBLE
                }
                null
            }
        }.onFailure { error ->
            logError(module, "failed to suppress SystemUI magazine scrim: ${error.message}")
        }
    }

    private fun hookDeviceCenter(module: XposedModule, classLoader: ClassLoader) {
        if (deviceCenterHooked) return

        val activityClass = classLoader.loadClass(DEVICE_CENTER_ACTIVITY)
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
                registerScreenOffReceiver(activity)
            }
            result
        }

        hookRetainedFinish(module, activityClass)
        hookFullscreenSwipeToDismiss(module, activityClass)
        hookSwipeVisualReset(module, activityClass)
        hookNewIntent(module, activityClass)
        hookActivityCleanup(module, activityClass)

        deviceCenterHooked = true
        log(module, "$DEVICE_CENTER_ACTIVITY allowed to occlude keyguard")
    }

    /**
     * MiLink's Activity base class overrides finish() to fade its blur layer first and destroys the
     * Activity afterwards. Keep the lock-screen instance alive instead: moving its singleTask to
     * the back starts Keyguard's unocclude transition while retaining the view hierarchy and list
     * position for the next swipe.
     */
    private fun hookRetainedFinish(module: XposedModule, activityClass: Class<*>) {
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
            sanitizeDeviceCenterWindow(activity)
            if (!activity.moveTaskToBack(true)) {
                return@intercept chain.proceed()
            }
            null
        }
    }

    /**
     * Children receive touch normally until the movement direction is clear. A vertical gesture
     * remains entirely with the device list; a leftward horizontal gesture cancels the child and
     * moves the complete DecorView, which also owns MiLink's blur material.
     */
    private fun hookFullscreenSwipeToDismiss(
        module: XposedModule,
        activityClass: Class<*>,
    ) {
        val dispatchTouchEvent = Activity::class.java.getDeclaredMethod(
            "dispatchTouchEvent",
            MotionEvent::class.java,
        ).apply { isAccessible = true }
        module.hook(dispatchTouchEvent).intercept { chain ->
            val activity = chain.thisObject as? Activity
            val event = chain.args.getOrNull(0) as? MotionEvent
            if (
                activity == null ||
                event == null ||
                !activityClass.isInstance(activity) ||
                !activity.isLockscreenLaunch()
            ) {
                return@intercept chain.proceed()
            }
            handleFullscreenSwipe(
                activity = activity,
                event = event,
                proceed = { chain.proceed() },
                replaceEvent = { replacement -> chain.args[0] = replacement },
            )
        }
    }

    private fun handleFullscreenSwipe(
        activity: Activity,
        event: MotionEvent,
        proceed: () -> Any?,
        replaceEvent: (MotionEvent) -> Unit,
    ): Any? {
        val root = activity.window.decorView
        val state = swipeStates.getOrPut(activity) { FullscreenSwipeState() }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                root.animate().setListener(null)
                root.animate().cancel()
                root.translationX = 0f
                state.tracker?.recycle()
                state.tracker = VelocityTracker.obtain().also { it.addMovement(event) }
                state.mode = GESTURE_UNDECIDED
                state.downX = event.rawX
                state.downY = event.rawY
                return proceed()
            }

            MotionEvent.ACTION_MOVE -> {
                state.tracker?.addMovement(event)
                val dx = event.rawX - state.downX
                val dy = event.rawY - state.downY
                if (state.mode == GESTURE_UNDECIDED) {
                    val absX = abs(dx)
                    val absY = abs(dy)
                    val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop.toFloat()
                    when {
                        absY > touchSlop && absY > absX -> {
                            state.mode = GESTURE_PASSTHROUGH
                        }

                        dx > touchSlop -> {
                            state.mode = GESTURE_PASSTHROUGH
                        }

                        dx < -touchSlop && absX > absY * HORIZONTAL_DIRECTION_RATIO -> {
                            state.mode = GESTURE_DISMISSING
                            val cancelEvent = MotionEvent.obtain(event).apply {
                                action = MotionEvent.ACTION_CANCEL
                            }
                            try {
                                replaceEvent(cancelEvent)
                                proceed()
                            } finally {
                                replaceEvent(event)
                                cancelEvent.recycle()
                            }
                        }
                    }
                }
                if (state.mode == GESTURE_DISMISSING) {
                    root.translationX = dx.coerceIn(-root.width.toFloat(), 0f)
                    return true
                }
                return proceed()
            }

            MotionEvent.ACTION_UP -> {
                state.tracker?.addMovement(event)
                if (state.mode == GESTURE_DISMISSING) {
                    state.tracker?.computeCurrentVelocity(1000)
                    val xVelocity = state.tracker?.xVelocity ?: 0f
                    val velocityThreshold =
                        DISMISS_MIN_VELOCITY_DP * activity.resources.displayMetrics.density
                    val shouldDismiss =
                        -root.translationX >= root.width * DISMISS_DISTANCE_RATIO ||
                            xVelocity <= -velocityThreshold
                    recycleSwipeTracker(state)
                    if (shouldDismiss) {
                        animatePageToBackground(activity, root, xVelocity)
                    } else {
                        animatePageBack(activity, root)
                    }
                    return true
                }
                recycleSwipeState(activity, state)
                return proceed()
            }

            MotionEvent.ACTION_CANCEL -> {
                if (state.mode == GESTURE_DISMISSING) {
                    recycleSwipeTracker(state)
                    animatePageBack(activity, root)
                    return true
                }
                recycleSwipeState(activity, state)
                return proceed()
            }
        }
        return if (state.mode == GESTURE_DISMISSING) true else proceed()
    }

    private fun animatePageBack(activity: Activity, root: View) {
        root.animate()
            .translationX(0f)
            .setDuration(220L)
            .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    root.animate().setListener(null)
                    swipeStates.remove(activity)
                }
            })
            .start()
    }

    private fun animatePageToBackground(activity: Activity, root: View, xVelocity: Float) {
        val width = root.width.coerceAtLeast(1)
        val remaining = (width + root.translationX).coerceAtLeast(0f)
        val velocityDuration = if (xVelocity < -1f) {
            (remaining / -xVelocity * 1000f).toLong()
        } else {
            220L
        }
        root.animate()
            .translationX(-width.toFloat())
            .setDuration(velocityDuration.coerceIn(120L, 260L))
            .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
            .setListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    root.animate().setListener(null)
                    swipeStates.remove(activity)
                    if (!cancelled && !activity.isDestroyed) {
                        if (!activity.moveTaskToBack(true)) {
                            root.translationX = 0f
                            activity.finish()
                        }
                    }
                }
            })
            .start()
    }

    private fun recycleSwipeState(activity: Activity, state: FullscreenSwipeState) {
        recycleSwipeTracker(state)
        swipeStates.remove(activity)
    }

    private fun recycleSwipeTracker(state: FullscreenSwipeState) {
        state.tracker?.recycle()
        state.tracker = null
    }

    private fun hookSwipeVisualReset(module: XposedModule, activityClass: Class<*>) {
        val onStart = findMethodInHierarchy(activityClass, "onStart", 0) ?: return
        module.hook(onStart).intercept { chain ->
            val activity = chain.thisObject as? Activity
            if (activity != null && activityClass.isInstance(activity)) {
                resetSwipeVisual(activity)
            }
            chain.proceed()
        }
    }

    private fun resetSwipeVisual(activity: Activity) {
        activity.window.decorView.let { root ->
            root.animate().setListener(null)
            root.animate().cancel()
            root.translationX = 0f
        }
        swipeStates.remove(activity)?.let(::recycleSwipeTracker)
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
                    resetSwipeVisual(target)
                    if (!target.moveTaskToBack(true)) {
                        target.finishAndRemoveTask()
                    }
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
            val activity = chain.thisObject as? Activity
            val newIntent = chain.args.getOrNull(0) as? Intent
            if (activity != null && activityClass.isInstance(activity) && newIntent != null) {
                resetSwipeVisual(activity)
                activity.intent = newIntent
            }
            val result = chain.proceed()
            if (activity != null && activityClass.isInstance(activity)) {
                if (newIntent.isLockscreenLaunch()) {
                    sanitizeDeviceCenterWindow(activity)
                    registerScreenOffReceiver(activity)
                } else {
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
                resetSwipeVisual(activity)
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
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    }

    private fun Activity.isLockscreenLaunch(): Boolean =
        intent.isLockscreenLaunch()

    private fun Intent?.isLockscreenLaunch(): Boolean =
        this?.getBooleanExtra(EXTRA_LOCKSCREEN_LAUNCH, false) == true

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
