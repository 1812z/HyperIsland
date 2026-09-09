package io.github.hyperisland.xposed.hook.SystemUI

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.WindowManager
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
 * MiLink-only adaptation. SystemUI window animation and Binder transport live in the framework.
 * Keep the launch identity here; do not leak host-specific details into the animation engine.
 */
object LockscreenDeviceCenterHook : BaseHook() {
    private const val TAG = "HyperIsland[LockscreenDeviceCenter]"
    private const val DEVICE_CENTER_ACTIVITY = "com.miui.circulate.world.CirculateWorldActivity"
    private const val EXTRA_LOCKSCREEN_LAUNCH =
        "io.github.hyperisland.extra.LOCKSCREEN_DEVICE_CENTER"

    internal val page = LockscreenActivityPage(
        packageName = "com.milink.service",
        activityName = DEVICE_CENTER_ACTIVITY,
        action = "com.milink.service.deviceworld",
        uri = "milink://com.milink.service/circulate_world",
        launchExtra = EXTRA_LOCKSCREEN_LAUNCH,
    )
    @Volatile private var deviceCenterHooked = false
    private const val GESTURE_UNDECIDED = 0
    private const val GESTURE_PASSTHROUGH = 1
    private const val GESTURE_DISMISSING = 2
    private const val GESTURE_REMOTE = 3
    private const val HORIZONTAL_DIRECTION_RATIO = 1.35f
    private const val DISMISS_DISTANCE_RATIO = 0.28f
    private const val DISMISS_MIN_VELOCITY_DP = 900f

    private val screenOffReceivers =
        Collections.synchronizedMap(WeakHashMap<Activity, BroadcastReceiver>())
    private val swipeStates =
        Collections.synchronizedMap(WeakHashMap<Activity, FullscreenSwipeState>())

    private class FullscreenSwipeState {
        var mode = GESTURE_UNDECIDED
        var downX = 0f
        var downY = 0f
        var tracker: VelocityTracker? = null
        var pendingLongClick: WeakReference<View>? = null
    }

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName == page.packageName) hookDeviceCenter(module, param.defaultClassLoader)
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
        hookLockscreenLongPressSuppression(module, activityClass)
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
     * hands the stream to SystemUI, which moves the actual unocclude animation leash.
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
            )
        }
    }

    /**
     * A child can fire its long-click before a slow drag reaches the horizontal touch slop. Defer
     * that callback until an undecided gesture is released; discard it when horizontal dismissal
     * or vertical pass-through wins, because ACTION_CANCEL cannot undo an action already run.
     */
    private fun hookLockscreenLongPressSuppression(
        module: XposedModule,
        activityClass: Class<*>,
    ) {
        View::class.java.declaredMethods
            .filter { method ->
                method.name == "performLongClick" &&
                    method.returnType == Boolean::class.javaPrimitiveType
            }
            .forEach { method ->
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    val view = chain.thisObject as? View
                    val state = view?.let { findDeferredLongPressState(it, activityClass) }
                    if (view != null && state != null) {
                        state.pendingLongClick = WeakReference(view)
                        true
                    } else {
                        chain.proceed()
                    }
                }
            }
    }

    private fun findDeferredLongPressState(
        view: View,
        activityClass: Class<*>,
    ): FullscreenSwipeState? =
        synchronized(swipeStates) {
            swipeStates.entries.firstOrNull { (activity, state) ->
                activityClass.isInstance(activity) &&
                    activity.isLockscreenLaunch() &&
                    state.mode != GESTURE_PASSTHROUGH &&
                    view.rootView === activity.window.decorView
            }?.value
        }

    private fun handleFullscreenSwipe(
        activity: Activity,
        event: MotionEvent,
        proceed: () -> Any?,
    ): Any? {
        val root = activity.window.decorView
        val state = swipeStates.getOrPut(activity) { FullscreenSwipeState() }
        if (state.mode == GESTURE_REMOTE && event.actionMasked != MotionEvent.ACTION_DOWN) {
            return true
        }
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
                            state.pendingLongClick = null
                        }

                        dx > touchSlop -> {
                            state.mode = GESTURE_PASSTHROUGH
                            state.pendingLongClick = null
                        }

                        dx < -touchSlop && absX > absY * HORIZONTAL_DIRECTION_RATIO -> {
                            state.mode = GESTURE_DISMISSING
                            val cancelEvent = MotionEvent.obtain(event).apply {
                                action = MotionEvent.ACTION_CANCEL
                            }
                            try {
                                activity.window.superDispatchTouchEvent(cancelEvent)
                            } finally {
                                cancelEvent.recycle()
                            }
                            state.pendingLongClick = null
                            clearPressedState(root)
                            if (LockscreenActivityReturn.begin(activity, event, state.downX)) {
                                state.mode = GESTURE_REMOTE
                                recycleSwipeTracker(state)
                                if (!activity.moveTaskToBack(true)) {
                                    LockscreenActivityReturn.abort(activity)
                                    state.mode = GESTURE_PASSTHROUGH
                                }
                                return true
                            }
                        }
                    }
                }
                if (state.mode == GESTURE_DISMISSING) {
                    // Controller unavailable: preserve the official return animation instead
                    // of exposing an occluded wallpaper-only window by translating DecorView.

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
                        state.downX - event.rawX >= root.width * DISMISS_DISTANCE_RATIO ||
                            xVelocity <= -velocityThreshold
                    recycleSwipeTracker(state)
                    recycleSwipeState(activity, state)
                    if (shouldDismiss) activity.moveTaskToBack(true)
                    return true
                }
                val deferredLongClick = if (state.mode == GESTURE_UNDECIDED) {
                    state.pendingLongClick?.get()
                } else {
                    null
                }
                recycleSwipeState(activity, state)
                deferredLongClick?.performLongClick()
                return proceed()
            }

            MotionEvent.ACTION_CANCEL -> {
                if (state.mode == GESTURE_DISMISSING) {
                    recycleSwipeTracker(state)
                    recycleSwipeState(activity, state)
                    return true
                }
                recycleSwipeState(activity, state)
                return proceed()
            }
        }
        return if (state.mode == GESTURE_DISMISSING) true else proceed()
    }

    private fun recycleSwipeState(activity: Activity, state: FullscreenSwipeState) {
        recycleSwipeTracker(state)
        state.pendingLongClick = null
        swipeStates.remove(activity)
    }

    private fun recycleSwipeTracker(state: FullscreenSwipeState) {
        state.tracker?.recycle()
        state.tracker = null
    }

    private fun clearPressedState(view: View) {
        view.cancelLongPress()
        view.isPressed = false
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                clearPressedState(view.getChildAt(index))
            }
        }
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
            root.animate().setUpdateListener(null)
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
