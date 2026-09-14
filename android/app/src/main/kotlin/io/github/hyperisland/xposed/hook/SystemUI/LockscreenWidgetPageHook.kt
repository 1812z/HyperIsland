package io.github.hyperisland.xposed.hook.SystemUI

import android.view.View
import android.view.ViewGroup
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.log
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference

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
    private const val MAGAZINE_CONTROLLER_CLASS =
        "com.android.keyguard.magazine.LockScreenMagazineController"

    @Volatile private var installed = false
    private var pageRef: WeakReference<LockscreenWidgetPageView>? = null

    fun install(module: XposedModule, classLoader: ClassLoader) {
        if (installed) return
        val containerClass = classLoader.loadClass(CONTAINER_CLASS)
        val leftControllerClass = classLoader.loadClass(LEFT_CONTROLLER_CLASS)

        hookContainerAttach(module, containerClass)
        hookContainerInflate(module, containerClass)
        hookControllerFlags(module, leftControllerClass)
        hookControllerTouchMove(module, leftControllerClass)
        hookMagazineLaunch(module, classLoader.loadClass(MAGAZINE_CONTROLLER_CLASS))

        installed = true
        log(module, "widget negative page hooks installed")
    }

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
