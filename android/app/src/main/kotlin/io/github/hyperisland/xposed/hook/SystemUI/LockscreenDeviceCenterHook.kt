package io.github.hyperisland.xposed.hook.SystemUI

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
    private const val GESTURE_REMOTE = 3
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
    private var entryFadeOwner: WeakReference<Any>? = null
    private var entryFadeWidth = 0f
    private var entryFadeScreenOffRegistered = false
    private var entryFadeAvailable = false
    private var exitFadeLeash: WeakReference<Any>? = null
    private var exitFadeProgress = 0f
    private val entryFadeViews = ArrayList<EntryFadeView>(16)

    private class EntryFadeView(view: View, val originalAlpha: Float) {
        val viewRef = WeakReference(view)
        var appliedAlpha = originalAlpha
    }
    private var getEntryTransitionAlpha: Method? = null
    private var setEntryTransitionAlpha: Method? = null

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
        LockscreenDeviceCenterReturn.install(module, classLoader, magazineHelper)

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
                    LockscreenDeviceCenterReturn.attachController(this)
                }
            }
        }

        findMethod(magazineHelper, "checkIsMagazineRemoteAnimation", 1).let { method ->
            module.hook(method).intercept { chain ->
                if (remoteTargetPackage(chain.args.getOrNull(0)) == MILINK_PACKAGE) {
                    val target = chain.args[0]!!
                    if (findField(target.javaClass, "mode")?.getInt(target) == 1) {
                        beginExitFade(target)
                    }
                    LockscreenDeviceCenterReturn.onRemoteTarget(chain.args[0]!!)
                    true
                } else {
                    chain.proceed()
                }
            }
        }

        hookSystemScrimSuppression(module, classLoader)
        hookKeyguardEntryFade(module, classLoader, magazineHelper)
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
     * setTranslation makes the page visible even when the distance is unchanged and consequently
     * skips updateKeyguardInfoBlurRatio. Clean up that path as well, including zero/reset frames.
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
            fun hideMagazineLayers(helper: Any?) {
                if (helper == null) return
                runCatching {
                    (leftViewField?.get(helper) as? View)?.apply {
                        // This is the unused magazine View, not the remote MiLink leash or the
                        // keyguard root. Alpha also protects against a later VISIBLE-only write.
                        alpha = 0f
                        visibility = View.INVISIBLE
                    }
                    (leftViewBgField?.get(helper) as? View)?.apply {
                        alpha = 0f
                        visibility = View.INVISIBLE
                    }
                }.onFailure { error ->
                    logError(module, "failed to hide magazine layers: ${error.message}")
                }
            }
            module.hook(findMethod(moveHelperClass, "setTranslation", 5)).intercept { chain ->
                hideMagazineLayers(chain.thisObject)
                try {
                    chain.proceed()
                } finally {
                    // Also runs when stock code skips the blur update, takes an early return,
                    // or starts its reset animator. Geometry and animation state stay stock.
                    hideMagazineLayers(chain.thisObject)
                }
            }
            module.hook(blurMethod).intercept { chain ->
                val translation = (chain.args.getOrNull(0) as? Number)?.toFloat() ?: 0f
                try {
                    // Preserve the stock front-scrim cleanup at zero and outside right-swipe.
                    if (translation <= 0f) chain.proceed() else null
                } finally {
                    hideMagazineLayers(chain.thisObject)
                }
            }
        }.onFailure { error ->
            logError(module, "failed to suppress SystemUI magazine scrim: ${error.message}")
        }
    }

    /** Fade only the original keyguard components, never the root containing the remote leash. */
    private fun hookKeyguardEntryFade(
        module: XposedModule,
        classLoader: ClassLoader,
        helperClass: Class<*>,
    ) {
        runCatching {
            getEntryTransitionAlpha = View::class.java.getDeclaredMethod("getTransitionAlpha")
                .apply { isAccessible = true }
            setEntryTransitionAlpha = View::class.java.getDeclaredMethod(
                "setTransitionAlpha", Float::class.javaPrimitiveType,
            ).apply { isAccessible = true }
            val openingField = findField(helperClass, "mOpeningTarget") ?: error("opening target missing")
            val leashField = findField(openingField.type, "leash") ?: error("opening leash missing")
            val injectorField = findField(helperClass, "mKeyguardPanelViewInjector")
                ?: error("panel injector missing")
            val injectorClass = classLoader.loadClass("com.android.keyguard.injector.KeyguardPanelViewInjector")
            val getController = findMethod(injectorClass, "getKeyguardPanelViewController", 0)
            val moveField = findField(injectorClass, "keyguardMoveHelper") ?: error("move helper missing")
            val getWidth = findMethod(classLoader.loadClass(KEYGUARD_MOVE_HELPER), "getScreenWidth", 0)

            module.hook(findMethod(helperClass, "setLeashPositionOnRtFrameCallback", 2)).intercept { chain ->
                if (!entryFadeAvailable) return@intercept chain.proceed()
                runCatching {
                    if (exitFadeLeash?.get() === chain.args[0] && entryFadeWidth > 0f) {
                        // The return controller replaces stock Folme positions. Read its actual
                        // finger position so hook ordering cannot make alpha run ahead of it.
                        val x = LockscreenDeviceCenterReturn.interactivePosition(chain.args[0])
                            ?: (chain.args[1] as Number).toFloat()
                        exitFadeProgress = (-x / entryFadeWidth).coerceIn(0f, 1f)
                        val t = exitFadeProgress
                        applyComponentFade(t * t * (3f - 2f * t))
                        return@runCatching
                    }
                    val helper = chain.thisObject ?: return@runCatching
                    val opening = openingField.get(helper) ?: return@runCatching
                    if (leashField.get(opening) !== chain.args[0]) return@runCatching
                    if (entryFadeOwner?.get() !== opening) {
                        if (remoteTargetPackage(opening) != MILINK_PACKAGE) return@runCatching
                        restoreEntryFade()
                        entryFadeOwner = WeakReference(opening)
                        val injector = injectorField.get(helper) ?: return@runCatching
                        val controller = getController.invoke(injector) ?: return@runCatching
                        val moveHelper = moveField.get(injector) ?: return@runCatching
                        entryFadeWidth = (getWidth.invoke(moveHelper) as Number).toFloat()
                        val items = findField(controller.javaClass, "mobileKeyGuardViews")
                            ?.get(controller) as? Iterable<*> ?: return@runCatching
                        val candidates = items.take(32).mapNotNull { item ->
                            if (item == null || findField(item.javaClass, "needAlpha")?.getBoolean(item) != true) null
                            else findField(item.javaClass, "view")?.get(item) as? View
                        }.distinct()
                        // Clock and info-layer entries may be nested. Apply one multiplier per
                        // branch, otherwise a child would receive the fade twice.
                        candidates.filter { view ->
                            var parent = view.parent
                            var nested = false
                            while (parent is View) {
                                if (parent in candidates) { nested = true; break }
                                parent = parent.parent
                            }
                            !nested
                        }.forEach { view ->
                            val original = (getEntryTransitionAlpha!!.invoke(view) as Number).toFloat()
                            entryFadeViews.add(EntryFadeView(view, original))
                        }
                        if (!entryFadeScreenOffRegistered) {
                            val context = findField(helperClass, "mContext")?.get(helper) as? Context
                            context?.registerReceiver(object : BroadcastReceiver() {
                                override fun onReceive(context: Context?, intent: Intent?) {
                                    if (intent?.action == Intent.ACTION_SCREEN_OFF) restoreEntryFade()
                                }
                            }, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
                            entryFadeScreenOffRegistered = context != null
                        }
                    }
                    if (entryFadeWidth > 0f) {
                        val x = (chain.args[1] as Number).toFloat()
                        val progress = (1f + x / entryFadeWidth).coerceIn(0f, 1f)
                        val t = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
                        val multiplier = 1f - t * t * (3f - 2f * t)
                        applyComponentFade(multiplier)
                    }
                }.onFailure { error ->
                    entryFadeAvailable = false
                    restoreEntryFade()
                    logError(module, "entry component fade failed: ${error.message}")
                }
                chain.proceed()
            }
            module.hook(findMethod(helperClass, "resetWhenBackToKeyguard", 0)).intercept { chain ->
                restoreEntryFade()
                chain.proceed()
            }
            module.hook(findMethod(helperClass, "setState", 1)).intercept { chain ->
                val state = chain.args[0].toString()
                if (state == "FINISHED_HIDE_MAGAZINE" || state == "BACK_TO_KEYGUARD" ||
                    (state == "UNOCCLUDE_ANIMATING" && exitFadeLeash?.get() == null)
                ) restoreEntryFade()
                // Do not restore on FINISHED_SHOW/IDLE: the root is about to be hidden, and
                // restoring here would recreate the exact last-frame blur flash being fixed.
                chain.proceed()
            }
            module.hook(findMethod(helperClass, "resetStatus", 1)).intercept { chain ->
                val result = chain.proceed()
                if (chain.args[0].toString().contains("FINISH_UNOCCLUDE")) {
                    if (exitFadeProgress >= 0.999f) restoreEntryFade()
                    // Cancelled interactive return ends at zero: keep the components faded
                    // under MiLink until the next entry/return, instead of flashing them on.
                    else exitFadeLeash = null
                }
                result
            }
            val controllerClass = classLoader.loadClass("com.android.keyguard.panel.KeyguardPanelViewController")
            val showing = findField(controllerClass, "keyguardShowing") ?: error("keyguard showing missing")
            controllerClass.declaredMethods.filter { it.name == "updateVisibility" }.forEach { method ->
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    if (chain.thisObject != null && !showing.getBoolean(chain.thisObject)) restoreEntryFade()
                    chain.proceed()
                }
            }
            entryFadeAvailable = true
        }.onFailure { error ->
            restoreEntryFade()
            logError(module, "entry fade unavailable: ${error.message}")
        }
    }

    private fun beginExitFade(target: Any) {
        if (!entryFadeAvailable || entryFadeViews.isEmpty()) return
        runCatching {
            val leash = findField(target.javaClass, "leash")?.get(target) ?: return
            if (exitFadeLeash?.get() === leash) return
            exitFadeLeash = WeakReference(leash)
            exitFadeProgress = 0f
            applyComponentFade(0f)
        }.onFailure { restoreEntryFade() }
    }

    private fun applyComponentFade(multiplier: Float) {
        entryFadeViews.forEach { item ->
            val view = item.viewRef.get() ?: return@forEach
            val alpha = item.originalAlpha * multiplier
            if (alpha != item.appliedAlpha) {
                setEntryTransitionAlpha!!.invoke(view, alpha)
                item.appliedAlpha = alpha
            }
        }
    }

    private fun restoreEntryFade() {
        entryFadeViews.forEach { item ->
            item.viewRef.get()?.let { view ->
                runCatching {
                    if (item.appliedAlpha != item.originalAlpha) {
                        setEntryTransitionAlpha?.invoke(view, item.originalAlpha)
                    }
                }
            }
        }
        entryFadeViews.clear()
        entryFadeOwner = null
        entryFadeWidth = 0f
        exitFadeLeash = null
        exitFadeProgress = 0f
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
                            if (LockscreenDeviceCenterReturn.begin(activity, event, state.downX)) {
                                state.mode = GESTURE_REMOTE
                                recycleSwipeTracker(state)
                                if (!activity.moveTaskToBack(true)) {
                                    LockscreenDeviceCenterReturn.abort(activity)
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
