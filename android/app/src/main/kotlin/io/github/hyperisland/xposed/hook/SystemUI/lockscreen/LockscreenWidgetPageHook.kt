package io.github.hyperisland.xposed.hook.SystemUI.lockscreen

import android.app.PendingIntent
import android.os.SystemClock
import android.graphics.Color
import android.view.ViewGroup
import android.view.View
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.log
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Method

/**
 * Replaces the keyguard negative-one page content with [LockscreenWidgetPageView].
 *
 * The widget page lives in the SystemUI process because that process already holds
 * `android.permission.BIND_APPWIDGET`; a separate app/Activity could not create an
 * [android.appwidget.AppWidgetHost]. The page is added as a child of
 * `MiuiKeyguardMoveLeftViewContainer`, which the stock gesture engine
 * (`KeyguardMoveHelper.setTranslation`) translates frame-by-frame, and whose progress also drives
 * `updateKeyguardInfoBlurRatio` (keyguard foreground blur/dim) and the keyguard component
 * scale/alpha. As a result the keyguard content sinks and blurs exactly in step with the finger and
 * the widget page follows it, with no per-frame work in this hook — no jump can be introduced here.
 *
 * The stock branch selection is forced to the local, in-process page (the same combination the
 * built-in negative page uses when no magazine overlay exists):
 * - `supportMoveToRight` = true       → negative page enabled.
 * - `isLeftViewLaunchActivity` = true → local `KeyguardMagazineHelper`/translation branch, not the
 *   remote `LockScreenMagazineClient` overlay.
 * - `isSupportSwipeToLaunchMagazine` = false → the controller falls through to
 *   `super.onTouchMove` (returns false) so `KeyguardPanelViewInjector` calls `setTranslation`
 *   directly.
 */
internal object LockscreenWidgetPageHook {
    private const val TAG = "HyperIsland[LockscreenWidgetPage]"
    private const val CONTAINER_CLASS =
        "com.android.keyguard.widget.MiuiKeyguardMoveLeftViewContainer"
    private const val LEFT_CONTROLLER_CLASS =
        "com.android.keyguard.negative.KeyguardMoveLeftController"
    private const val RIGHT_CONTROLLER_CLASS =
        "com.android.keyguard.negative.KeyguardMoveRightController"
    private const val BASE_CONTROLLER_CLASS =
        "com.android.keyguard.BaseKeyguardMoveController"
    private const val MAGAZINE_CONTROLLER_CLASS =
        "com.android.keyguard.magazine.LockScreenMagazineController"

    @Volatile private var installed = false
    private var pageRef: WeakReference<LockscreenWidgetPageView>? = null
    private var panelRef: WeakReference<Any>? = null
    private var panelTouchMethod: Method? = null
    private var activityStarterRef: WeakReference<Any>? = null
    private var dependencyRef: WeakReference<Any>? = null
    private var startPendingIntentMethod: Method? = null
    private var systemUiLoader: ClassLoader? = null
    private var moduleRef: WeakReference<XposedModule>? = null
    @Volatile private var widgetClickUntil = 0L
    @Volatile private var widgetLaunchGuard = false
    private var isActivityMethod: Method? = null

    fun install(module: XposedModule, classLoader: ClassLoader) {
        if (installed) return
        moduleRef = WeakReference(module)
        systemUiLoader = classLoader
        val containerClass = classLoader.loadClass(CONTAINER_CLASS)
        val leftControllerClass = classLoader.loadClass(LEFT_CONTROLLER_CLASS)
        val rightControllerClass = classLoader.loadClass(RIGHT_CONTROLLER_CLASS)
        hookPanelInstance(module, classLoader)
        hookActivityStarter(module, classLoader)
        hookDependency(module, classLoader)
        hookLegacyWidgetPendingIntent(module, classLoader)
        hookRemoteViewsStartPendingIntent(module, classLoader)

        hookContainerAttach(module, containerClass)
        hookContainerInflate(module, containerClass)
        hookControllerFlags(module, leftControllerClass)
        hookControllerTouchMove(module, leftControllerClass)
        hookControllerMistouchGuard(module, leftControllerClass)
        hookControllerMistouchGuard(module, rightControllerClass)
        hookBaseMistouchGuard(module, classLoader.loadClass(BASE_CONTROLLER_CLASS))
        hookMagazineLaunch(module, classLoader.loadClass(MAGAZINE_CONTROLLER_CLASS))
        hookPageBlurWithoutDim(module, classLoader)

        installed = true
        log(module, "widget negative page hooks installed")
    }

    private fun hookDependency(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val dependency = loader.loadClass("com.android.systemui.Dependency")
            dependency.declaredConstructors.forEach { constructor ->
                constructor.isAccessible = true
                module.hook(constructor).intercept { chain ->
                    val result = chain.proceed()
                    dependencyRef = WeakReference(chain.thisObject)
                    result
                }
            }
        }.onFailure { log(module, "dependency instance hook unavailable: ${it.message}") }
    }

    private fun hookActivityStarter(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val starterClass = loader.loadClass("com.android.systemui.statusbar.phone.ActivityStarterImpl")
            val candidates = starterClass.declaredMethods.filter {
                it.name == "startPendingIntentDismissingKeyguard" &&
                    it.parameterTypes.firstOrNull() == PendingIntent::class.java
            }
            startPendingIntentMethod = candidates
                .sortedBy { it.parameterCount }
                .firstOrNull()
                ?.also { it.isAccessible = true }
            module.log("$TAG: ActivityStarter overloads=${candidates.map { it.parameterCount }}")
            startPendingIntentMethod?.let { method ->
                module.hook(method).intercept { chain ->
                    activityStarterRef = WeakReference(chain.thisObject)
                    chain.proceed()
                }
            }
            starterClass.declaredConstructors.forEach { constructor ->
                constructor.isAccessible = true
                module.hook(constructor).intercept { chain ->
                    val result = chain.proceed()
                    activityStarterRef = WeakReference(chain.thisObject)
                    result
                }
            }
        }.onFailure { log(module, "activity starter hook unavailable: ${it.message}") }
    }

    /** Legacy RemoteViews clicks call PendingIntent.send directly instead of InteractionHandler. */
    private fun hookLegacyWidgetPendingIntent(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val pendingIntentClass = loader.loadClass("android.app.PendingIntent")
            val methods = pendingIntentClass.declaredMethods
                .filter { it.name == "send" }
            module.log("$TAG: legacy PendingIntent.send candidates=${methods.size}")
            methods.forEach { method ->
                    method.isAccessible = true
                    module.hook(method).intercept { chain ->
                        // Fast path first: outside a widget click this must stay as cheap as
                        // possible, because every SystemUI notification click goes through here.
                        if (SystemClock.uptimeMillis() > widgetClickUntil) return@intercept chain.proceed()
                        val pendingIntent = chain.thisObject as? PendingIntent
                            ?: return@intercept chain.proceed()
                        val isActivity = isActivityPendingIntent(pendingIntent)
                        runtimeLog("legacy PendingIntent.send activity=$isActivity")
                        val target = if (isActivity && !widgetLaunchGuard) {
                            activityStarterRef?.get() ?: resolveActivityStarter()
                        } else null
                        val starter = startPendingIntentMethod
                        if (target != null && starter != null) {
                            widgetClickUntil = 0L
                            widgetLaunchGuard = true
                            runCatching { invokeActivityStarter(starter, target, pendingIntent) }
                                .onFailure { runtimeLog("legacy widget activity launch failed: ${it.message}") }
                            widgetLaunchGuard = false
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                }
        }.onFailure { log(module, "legacy widget PendingIntent hook unavailable: ${it.message}") }
    }

    /** Android 35 RemoteViews dispatches widget clicks through this hidden static method. */
    private fun hookRemoteViewsStartPendingIntent(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val remoteViews = loader.loadClass("android.widget.RemoteViews")
            val methods = remoteViews.declaredMethods.filter { method ->
                method.name == "startPendingIntent" &&
                    java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.size >= 2 &&
                    View::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                    method.parameterTypes[1] == PendingIntent::class.java
            }
            module.log("$TAG: RemoteViews.startPendingIntent candidates=${methods.size}")
            methods.forEach { method ->
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    val active = SystemClock.uptimeMillis() <= widgetClickUntil
                    val pendingIntent = chain.args.getOrNull(1) as? PendingIntent
                    val activity = runCatching {
                        pendingIntent != null &&
                            PendingIntent::class.java.getMethod("isActivity").invoke(pendingIntent) as Boolean
                    }.getOrDefault(false)
                    val inlineControl = pendingIntent?.let { isInlineControlPendingIntent(it) } == true
                    if (active) runtimeLog("RemoteViews.startPendingIntent activity=$activity inline=$inlineControl")
                    if (active && activity && !inlineControl && pendingIntent != null && !widgetLaunchGuard) {
                        val target = activityStarterRef?.get() ?: resolveActivityStarter()
                        val starter = startPendingIntentMethod
                        if (target != null && starter != null) {
                            widgetClickUntil = 0L
                            widgetLaunchGuard = true
                            runCatching { invokeActivityStarter(starter, target, pendingIntent) }
                                .onSuccess { runtimeLog("RemoteViews activity launch invoked") }
                                .onFailure { runtimeLog("RemoteViews activity launch failed: ${it.message}") }
                            widgetLaunchGuard = false
                            true
                        } else {
                            runtimeLog("RemoteViews activity starter unavailable")
                            chain.proceed()
                        }
                    } else {
                        chain.proceed()
                    }
                }
            }
        }.onFailure { log(module, "RemoteViews click hook unavailable: ${it.message}") }
    }

    /** Preserve the stock page blur while removing only its opaque/dimming color layer. */
    private fun hookPageBlurWithoutDim(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val helper = loader.loadClass("com.android.keyguard.panel.KeyguardMoveHelper")
            val field = helper.getDeclaredField("mFrontScrimView").apply { isAccessible = true }
            val method = helper.getDeclaredMethod(
                "updateKeyguardInfoBlurRatio",
                Float::class.javaPrimitiveType,
            ).apply { isAccessible = true }
            val clearBlendMethod = runCatching {
                loader.loadClass("com.miui.systemui.util.MiBlurCompat").getDeclaredMethod(
                    "clearMiBackgroundBlendColorCompat",
                    View::class.java,
                ).apply { isAccessible = true }
            }.getOrNull()
            fun applyCustomScrim(instance: Any?, translation: Float, skipZero: Boolean = false) {
                if (!isWidgetMode()) return
                val scrim = runCatching { field.get(instance) as? View }.getOrNull() ?: return
                // reset() calls setTranslation(0, ...) once to start its Folme return animation.
                // That zero is only the animation target, not the current frame. Do not erase the
                // current scrim before Folme supplies the first real position.
                if (skipZero && translation == 0f) return
                val dimEnabled = ConfigManager.getBoolean(
                    "pref_lockscreen_negative_page_dim_enabled",
                    true,
                )
                val dimAmount = ConfigManager.getInt(
                    "pref_lockscreen_negative_page_dim_amount",
                    50,
                ).coerceIn(0, 100)
                // Keep the stock SystemUI scrim untouched for the default setting. In
                // particular, reset() starts its return Folme with a zero target and the stock
                // implementation must be allowed to manage that transition itself.
                if (dimEnabled && dimAmount == 50) return
                val screenWidth = scrim.resources.displayMetrics.widthPixels.toFloat()
                    .coerceAtLeast(1f)
                // During the return path SystemUI may pass the offset from the center as a
                // negative value. Its actual left-page position is width + offset.
                val pagePosition = if (translation < 0f) screenWidth + translation else translation
                val progress = (pagePosition / screenWidth).coerceIn(0f, 1f)
                val alpha = if (dimEnabled) {
                    (dimAmount * progress * 255 / 100).toInt()
                } else {
                    0
                }
                scrim.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
                runCatching { clearBlendMethod?.invoke(null, scrim) }
            }

            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (isWidgetMode()) {
                    // Keep updating on the way back as well. The helper reports the current
                    // horizontal position, so the scrim follows both the finger and settle
                    // animation instead of disappearing when the return starts.
                    applyCustomScrim(
                        chain.thisObject,
                        (chain.args.getOrNull(0) as? Number)?.toFloat() ?: 0f,
                    )
                }
                result
            }

            val setTranslation = helper.declaredMethods.firstOrNull {
                it.name == "setTranslation" && it.parameterCount == 5
            }
            if (setTranslation != null) {
                setTranslation.isAccessible = true
                module.hook(setTranslation).intercept { chain ->
                    val result = chain.proceed()
                    // Apply after stock translation so the system keeps ownership of its blur
                    // and animation, while this setting follows every actual position frame.
                    applyCustomScrim(
                        chain.thisObject,
                        (chain.args.getOrNull(0) as? Number)?.toFloat() ?: 0f,
                        skipZero = true,
                    )
                    result
                }
            }

        }.onFailure { log(module, "page blur hook unavailable: ${it.message}") }
    }

    private fun hookPanelInstance(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val injector = loader.loadClass("com.android.keyguard.injector.KeyguardPanelViewInjector")
            panelTouchMethod = injector.declaredMethods.firstOrNull {
                it.name == "onTouchEvent" && it.parameterCount == 7
            }?.also { it.isAccessible = true }
            panelTouchMethod?.let { method ->
                // While the widget picker is visible it is a modal surface. The panel helper
                // otherwise treats the upper/lower touch bands as keyguard gestures and sends
                // CANCEL when a scroll crosses its boundary, so the picker only scrolls from a
                // narrow strip. Mark the scope guards as consumed by the panel and let the child
                // view receive the complete stream instead.
                module.hook(method).intercept { chain ->
                    if (pageRef?.get()?.isPickerVisible() == true) {
                        chain.args[4] = true
                        chain.args[5] = true
                        chain.args[6] = true
                    }
                    chain.proceed()
                }
            }
            injector.declaredConstructors.forEach { constructor ->
                constructor.isAccessible = true
                module.hook(constructor).intercept { chain ->
                    val result = chain.proceed()
                    panelRef = WeakReference(chain.thisObject)
                    result
                }
            }
        }.onFailure { log(module, "panel instance hook unavailable: ${it.message}") }
    }

    /** Feed a confirmed horizontal stream into the stock KeyguardMoveHelper state machine. */
    internal fun forwardHorizontal(event: android.view.MotionEvent, downX: Float, downY: Float) {
        if (!isWidgetMode() || pageRef?.get()?.acceptsPageGesture() != true) return
        val target = panelRef?.get() ?: return
        val method = panelTouchMethod ?: return
        runCatching {
            // KeyguardMoveHelper consumes root/screen coordinates. The page receives events in
            // its translated local space; forwarding that directly makes X oscillate as the
            // negative page follows the finger. Normalize every event to raw screen coordinates.
            val copy = android.view.MotionEvent.obtain(event).apply {
                setLocation(event.rawX, event.rawY)
            }
            method.invoke(target, copy, 0, downX, downY, false, false, false)
            copy.recycle()
        }
    }

    internal fun isPickerVisible(): Boolean = pageRef?.get()?.isPickerVisible() == true

    /** Route app launches through keyguard; execute inline widget controls through RemoteViews. */
    internal fun handleWidgetInteraction(view: View, pendingIntent: PendingIntent, response: Any?): Boolean {
        val isActivity = isActivityPendingIntent(pendingIntent)
        val inlineControl = isInlineControlPendingIntent(pendingIntent)
        runtimeLog("interaction handler received activity=$isActivity inline=$inlineControl creator=${pendingIntent.creatorPackage}")
        if (!isActivity || inlineControl) return dispatchRemoteViewsPendingIntent(view, pendingIntent, response)
        val target = activityStarterRef?.get() ?: resolveActivityStarter()
        val method = startPendingIntentMethod
        if (target == null || method == null) {
            runtimeLog("activity starter unavailable")
            return false
        }
        return runCatching {
            widgetClickUntil = 0L
            widgetLaunchGuard = true
            invokeActivityStarter(method, target, pendingIntent)
            widgetLaunchGuard = false
            runtimeLog("startPendingIntentDismissingKeyguard invoked")
            true
        }.onFailure {
            widgetLaunchGuard = false
            runtimeLog("failed to start widget activity: ${it.message}")
        }
            .getOrDefault(false)
    }

    private fun isInlineControlPendingIntent(pendingIntent: PendingIntent): Boolean = runCatching {
        val creator = pendingIntent.creatorPackage.orEmpty().lowercase()
        val intent = PendingIntent::class.java.getDeclaredMethod("getIntent").apply { isAccessible = true }
            .invoke(pendingIntent) as? android.content.Intent
        val action = intent?.action.orEmpty().uppercase()
        creator.contains("music") || creator.contains("player") ||
            action.contains("MEDIA_") || action.contains("PLAYBACK") ||
            action.contains("PLAY") || action.contains("PAUSE") || action.contains("NEXT") ||
            action.contains("PREVIOUS") || action.contains("TOGGLE")
    }.getOrDefault(false)

    private fun dispatchRemoteViewsPendingIntent(
        view: View,
        pendingIntent: PendingIntent,
        response: Any?,
    ): Boolean = runCatching {
        val options = response?.let {
            it.javaClass.getMethod("getLaunchOptions", View::class.java).invoke(it, view)
        }
        val remoteViews = Class.forName("android.widget.RemoteViews")
        val method = remoteViews.declaredMethods.first {
            it.name == "startPendingIntent" &&
                java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                it.parameterTypes.size >= 3 &&
                View::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                it.parameterTypes[1] == PendingIntent::class.java
        }.apply { isAccessible = true }
        widgetLaunchGuard = true
        try {
            method.invoke(null, view, pendingIntent, options) as? Boolean ?: true
        } finally {
            widgetLaunchGuard = false
        }
    }.onFailure { runtimeLog("RemoteViews default interaction failed: ${it.message}") }
        .getOrDefault(false)

    internal fun markWidgetClick() {
        // RemoteViews posts the click instead of running it inline, so PendingIntent.send can
        // land after ACTION_UP returns. Keep the marker alive for that dispatch window; the
        // movement/long-press paths and the post-UP clear below all cancel it.
        widgetClickUntil = SystemClock.uptimeMillis() + WIDGET_CLICK_WINDOW_MS
        runtimeLog("widget click marker armed")
    }

    internal fun clearWidgetClick() {
        widgetClickUntil = 0L
    }

    /** `PendingIntent.isActivity()` is hidden; resolve it once and reuse the handle. */
    private fun isActivityPendingIntent(pendingIntent: PendingIntent): Boolean = runCatching {
        val method = isActivityMethod ?: PendingIntent::class.java
            .getDeclaredMethod("isActivity")
            .apply { isAccessible = true }
            .also { isActivityMethod = it }
        method.invoke(pendingIntent) as Boolean
    }.getOrDefault(false)

    internal fun runtimeLog(message: String) {
        moduleRef?.get()?.log("$TAG: $message")
    }

    /** Invoke whichever ActivityStarter overload this SystemUI build exposes. */
    private fun invokeActivityStarter(method: Method, target: Any, pendingIntent: PendingIntent) {
        val compatibleMethod = if (method.declaringClass.isInstance(target)) {
            method
        } else {
            generateSequence(target.javaClass) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
                .firstOrNull {
                    it.name == method.name &&
                        it.parameterTypes.firstOrNull() == PendingIntent::class.java
                }?.apply { isAccessible = true } ?: method
        }
        val parameters = compatibleMethod.parameterTypes
        val args = arrayOfNulls<Any>(parameters.size)
        if (parameters.isNotEmpty()) args[0] = pendingIntent
        for (index in 1 until parameters.size) {
            args[index] = when {
                !parameters[index].isPrimitive -> null
                parameters[index] == Boolean::class.javaPrimitiveType -> false
                parameters[index] == Char::class.javaPrimitiveType -> '\u0000'
                parameters[index] == Byte::class.javaPrimitiveType -> 0.toByte()
                parameters[index] == Short::class.javaPrimitiveType -> 0.toShort()
                parameters[index] == Long::class.javaPrimitiveType -> 0L
                parameters[index] == Float::class.javaPrimitiveType -> 0f
                parameters[index] == Double::class.javaPrimitiveType -> 0.0
                else -> 0
            }
        }
        compatibleMethod.invoke(target, *args)
    }

    private fun resolveActivityStarter(): Any? = runCatching {
        val loader = systemUiLoader ?: return@runCatching null
        val dependency = loader.loadClass("com.android.systemui.Dependency")
        val starterApi = loader.loadClass("com.android.systemui.plugins.ActivityStarter")
        val names = setOf("get", "getDependency", "getInstance", "getLazy", "getDependencyInner", "createDependency")
        val candidates = dependency.declaredMethods.filter { it.name in names }
        for (getter in candidates) {
            runCatching {
                getter.isAccessible = true
                val args = arrayOfNulls<Any>(getter.parameterCount)
                getter.parameterTypes.forEachIndexed { index, type ->
                    args[index] = when {
                        type == Class::class.java -> starterApi
                        type == String::class.java -> starterApi.name
                        index == 0 && !type.isPrimitive -> starterApi
                        !type.isPrimitive -> null
                        type == Boolean::class.javaPrimitiveType -> false
                        type == Long::class.javaPrimitiveType -> 0L
                        type == Float::class.javaPrimitiveType -> 0f
                        type == Double::class.javaPrimitiveType -> 0.0
                        else -> 0
                    }
                }
                val receiver = if (java.lang.reflect.Modifier.isStatic(getter.modifiers)) null else {
                    dependencyRef?.get() ?: dependency.declaredFields.firstOrNull {
                        java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                            dependency.isAssignableFrom(it.type)
                    }?.apply { isAccessible = true }?.get(null)
                }
                val result = getter.invoke(receiver, *args)
                if (result != null && starterApi.isInstance(result)) {
                    activityStarterRef = WeakReference(result)
                    return@runCatching
                }
            }
            if (activityStarterRef?.get() != null) return@runCatching activityStarterRef?.get()
        }
        runtimeLog("Dependency getter unavailable methods=${candidates.map { it.toGenericString() }}")
        null
    }.onFailure { runtimeLog("unable to resolve ActivityStarter from Dependency: ${it.message}") }
        .getOrNull()

    /** Attach the page as soon as the keyguard container joins the hierarchy. */
    private fun hookContainerAttach(module: XposedModule, containerClass: Class<*>) {
        val onAttached = containerClass.declaredMethods
            .firstOrNull { it.name == "onAttachedToWindow" } ?: return
        onAttached.isAccessible = true
        module.hook(onAttached).intercept { chain ->
            chain.proceed()
            (chain.thisObject as? ViewGroup)?.let { syncWidgetPage(module, it) }
        }
    }

    /**
     * MiUI (re)inflates magazine / control-center content into the container, for example on a
     * region change or when leaving safemode. Re-assert the widget page on top afterwards so the
     * stock content never covers the widgets or steals their touches.
     */
    private fun hookContainerInflate(module: XposedModule, containerClass: Class<*>) {
        val inflate = containerClass.declaredMethods
            .firstOrNull { it.name == "inflateLeftView" } ?: return
        inflate.isAccessible = true
        module.hook(inflate).intercept { chain ->
            val result = chain.proceed()
            (chain.thisObject as? ViewGroup)?.let { syncWidgetPage(module, it) }
            result
        }
    }

    /** Force the controller onto the local translation path; see the class comment. */
    private fun hookControllerFlags(module: XposedModule, controllerClass: Class<*>) {
        overrideBoolean(module, controllerClass, "supportMoveToRight") { true }
        overrideBoolean(module, controllerClass, "isLeftViewLaunchActivity") { true }
        overrideBoolean(module, controllerClass, "isSupportSwipeToLaunchMagazine") { false }
    }

    /** Let the stock panel helper consume both directions and update translation/blur per frame. */
    private fun hookControllerTouchMove(module: XposedModule, controllerClass: Class<*>) {
        val method = controllerClass.declaredMethods.firstOrNull {
            it.name == "onTouchMove" && it.parameterCount == 2 &&
                it.parameterTypes[0] == Float::class.javaPrimitiveType &&
                it.parameterTypes[1] == Float::class.javaPrimitiveType
        } ?: return
        method.isAccessible = true
        module.hook(method).intercept { chain ->
            if (isWidgetMode()) {
                // AppWidgetService rejects every query before credential unlock. Consume the
                // controller gesture in that state so a left swipe cannot enter the page.
                if (pageRef?.get()?.acceptsPageGesture() != true) true else false
            } else {
                chain.proceed()
            }
        }
    }

    /** The stock base controller's mistake-touch guard blocks the first reverse swipe from the
     * widget page. SystemUI's own velocity/progress settle logic remains enabled below it. */
    private fun hookControllerMistouchGuard(module: XposedModule, controllerClass: Class<*>) {
        controllerClass.declaredConstructors.forEach { constructor ->
            constructor.isAccessible = true
            module.hook(constructor).intercept { chain ->
                val result = chain.proceed()
                if (isWidgetMode()) {
                    (chain.thisObject as? Any)?.let { instance ->
                        runCatching {
                            val field = instance.javaClass.superclass
                                ?.getDeclaredField("mEnableErrorTips")
                            field?.isAccessible = true
                            field?.setBoolean(instance, false)
                        }
                    }
                }
                result
            }
        }
    }

    private fun hookBaseMistouchGuard(module: XposedModule, baseClass: Class<*>) {
        val move = baseClass.declaredMethods.firstOrNull {
            it.name == "onTouchMove" && it.parameterCount == 2
        } ?: return
        move.isAccessible = true
        module.hook(move).intercept { chain ->
            if (isWidgetMode() && chain.thisObject.javaClass.name.startsWith("com.android.keyguard.negative.")) {
                runCatching {
                    val field = baseClass.getDeclaredField("mEnableErrorTips")
                    field.isAccessible = true
                    field.setBoolean(chain.thisObject, false)
                }
            }
            chain.proceed()
        }
    }

    /**
     * `isLeftViewLaunchActivity()` returning true makes the settle animation ask the magazine
     * controller to launch its own page. Suppress that while widget mode is active.
     */
    private fun hookMagazineLaunch(module: XposedModule, magazineClass: Class<*>) {
        val start = magazineClass.declaredMethods
            .firstOrNull { it.name == "startMagazineLeftActivity" } ?: return
        start.isAccessible = true
        module.hook(start).intercept {
            if (isWidgetMode()) null else it.proceed()
        }
    }

    private fun overrideBoolean(
        module: XposedModule,
        clazz: Class<*>,
        name: String,
        value: () -> Boolean,
    ) {
        val method = clazz.declaredMethods.firstOrNull { it.name == name && it.parameterCount == 0 }
            ?: return
        method.isAccessible = true
        module.hook(method).intercept {
            if (isWidgetMode()) value() else it.proceed()
        }
    }

    private fun syncWidgetPage(module: XposedModule, container: ViewGroup) {
        if (!isWidgetMode()) return
        // The dragged card may extend beyond the scroll viewport and page edge. Keep the
        // negative-page host from cutting off that translated child while it follows the finger.
        container.clipChildren = false
        container.clipToPadding = false
        for (index in 0 until container.childCount) {
            val child = container.getChildAt(index)
            if (child.tag != PAGE_TAG) child.visibility = View.GONE
        }
        val existing = container.findViewWithTag<View>(PAGE_TAG)
        if (existing != null) {
            existing.bringToFront()
            return
        }
        val page = LockscreenWidgetPageView(container.context).apply { tag = PAGE_TAG }
        container.addView(
            page,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        pageRef = WeakReference(page)
        log(module, "widget page attached")
    }

    internal fun isWidgetMode(): Boolean {
        if (FORCE_WIDGETS) return true
        return ConfigManager.getString(
            NEGATIVE_PAGE_MODE_KEY,
            LockscreenNegativePageMode.DEVICE_CENTER,
        ) == LockscreenNegativePageMode.WIDGETS
    }

    internal fun isNegativePageEnabled(): Boolean =
        ConfigManager.getBoolean(
            "pref_lockscreen_negative_page_enabled",
            ConfigManager.getBoolean("pref_lockscreen_device_center", false) || isWidgetMode(),
        )

    internal fun isDeviceCenterMode(): Boolean =
        isNegativePageEnabled() && !isWidgetMode()

    private fun log(module: XposedModule, message: String) {
        module.log("$TAG: $message")
    }

    /** Hardcoded to widgets for the current test round; set false to follow the saved mode. */
    internal const val FORCE_WIDGETS = false

    /** How long a widget touch keeps the PendingIntent redirection marker alive. */
    internal const val WIDGET_CLICK_WINDOW_MS = 1500L

    /** Grace period after ACTION_UP for a posted RemoteViews click to run. */
    internal const val WIDGET_CLICK_CLEAR_DELAY_MS = 600L
    private const val NEGATIVE_PAGE_MODE_KEY = "pref_lockscreen_negative_page_mode"
    private const val PAGE_TAG = LockscreenWidgetPageView.PAGE_TAG
}

/** Negative-one page mode values, shared without pulling Compose into the hook. */
internal object LockscreenNegativePageMode {
    const val DEVICE_CENTER = "device_center"
    const val WIDGETS = "widgets"
}
