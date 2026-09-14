package io.github.hyperisland.xposed.hook.SystemUI

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

    fun install(module: XposedModule, classLoader: ClassLoader) {
        if (installed) return
        val containerClass = classLoader.loadClass(CONTAINER_CLASS)
        val leftControllerClass = classLoader.loadClass(LEFT_CONTROLLER_CLASS)
        val rightControllerClass = classLoader.loadClass(RIGHT_CONTROLLER_CLASS)
        hookPanelInstance(module, classLoader)

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

    /** Preserve the stock page blur while removing only its opaque/dimming color layer. */
    private fun hookPageBlurWithoutDim(module: XposedModule, loader: ClassLoader) {
        runCatching {
            val helper = loader.loadClass("com.android.keyguard.panel.KeyguardMoveHelper")
            val field = helper.getDeclaredField("mFrontScrimView").apply { isAccessible = true }
            val method = helper.getDeclaredMethod(
                "updateKeyguardInfoBlurRatio",
                Float::class.javaPrimitiveType,
            ).apply { isAccessible = true }
            val blendMethod = View::class.java.getDeclaredMethod(
                "setMiBackgroundBlendColors",
                IntArray::class.java,
                Float::class.javaPrimitiveType,
            ).apply { isAccessible = true }
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (isWidgetMode() && (chain.args.getOrNull(0) as? Number)?.toFloat()?.let { it > 0f } == true) {
                    val scrim = field.get(chain.thisObject) as? View
                    if (scrim != null) {
                        scrim.setBackgroundColor(Color.TRANSPARENT)
                        runCatching { blendMethod.invoke(scrim, null, 0f) }
                    }
                }
                result
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

    private fun log(module: XposedModule, message: String) {
        module.log("$TAG: $message")
    }

    /** Hardcoded to widgets for the current test round; set false to follow the saved mode. */
    internal const val FORCE_WIDGETS = false
    private const val NEGATIVE_PAGE_MODE_KEY = "pref_lockscreen_negative_page_mode"
    private const val PAGE_TAG = LockscreenWidgetPageView.PAGE_TAG
}

/** Negative-one page mode values, shared without pulling Compose into the hook. */
internal object LockscreenNegativePageMode {
    const val DEVICE_CENTER = "device_center"
    const val WIDGETS = "widgets"
}
