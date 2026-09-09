package io.github.hyperisland.xposed.hook.SystemUI

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.ref.WeakReference

/**
 * SystemUI-side Activity negative-page framework: launch, remote leash, scrim and component fade.
 * It receives a page descriptor and knows nothing about the target application's View hierarchy.
 * Launcher Overlay services require a separate transport; they are not Activity pages.
 */
internal object LockscreenActivityPageHook {
    private const val TAG = "HyperIsland[LockscreenActivityPage]"
    private lateinit var page: LockscreenActivityPage
    private const val MOVE_LEFT_CONTROLLER =
        "com.android.keyguard.negative.KeyguardMoveLeftController"
    private const val MAGAZINE_CONTROLLER =
        "com.android.keyguard.magazine.LockScreenMagazineController"
    private const val MAGAZINE_HELPER =
        "com.android.keyguard.magazine.KeyguardMagazineHelper"
    private const val KEYGUARD_MOVE_HELPER =
        "com.android.keyguard.panel.KeyguardMoveHelper"
    @Volatile private var systemUiHooked = false
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

    private fun log(module: XposedModule, message: String) {
        if (io.github.hyperisland.xposed.ConfigManager.isDebugLogEnabled()) {
            module.log(android.util.Log.DEBUG, TAG, message)
        }
    }

    private fun logError(module: XposedModule, message: String) {
        module.log(android.util.Log.ERROR, TAG, message)
    }

    fun install(module: XposedModule, classLoader: ClassLoader, target: LockscreenActivityPage) {
        if (systemUiHooked) return
        page = target
        hookSystemUi(module, classLoader)
    }

    private fun hookSystemUi(module: XposedModule, classLoader: ClassLoader) {
        if (systemUiHooked) return

        val moveController = classLoader.loadClass(MOVE_LEFT_CONTROLLER)
        val magazineController = classLoader.loadClass(MAGAZINE_CONTROLLER)
        val magazineHelper = classLoader.loadClass(MAGAZINE_HELPER)
        LockscreenActivityReturn.install(module, classLoader, magazineHelper, page.packageName)

        hookBooleanResult(module, moveController, "supportMoveToRight")
        hookBooleanResult(module, moveController, "isLeftViewLaunchActivity")
        hookBooleanResult(module, moveController, "isSupportSwipeToLaunchMagazine")

        findMethod(magazineController, "getPreLeftScreenIntent", 0).let { method ->
            module.hook(method).intercept {
                page.createIntent().apply {
                    LockscreenActivityReturn.attachController(this)
                }
            }
        }

        findMethod(magazineHelper, "checkIsMagazineRemoteAnimation", 1).let { method ->
            module.hook(method).intercept { chain ->
                if (remoteTargetPackage(chain.args.getOrNull(0)) == page.packageName) {
                    val target = chain.args[0]!!
                    if (findField(target.javaClass, "mode")?.getInt(target) == 1) {
                        beginExitFade(target)
                    }
                    LockscreenActivityReturn.onRemoteTarget(chain.args[0]!!)
                    true
                } else {
                    chain.proceed()
                }
            }
        }

        hookSystemScrimSuppression(module, classLoader)
        hookKeyguardEntryFade(module, classLoader, magazineHelper)
        systemUiHooked = true
        log(module, "SystemUI negative-one page redirected to ${page.activityName}")
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
                        val x = LockscreenActivityReturn.interactivePosition(chain.args[0])
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
                        if (remoteTargetPackage(opening) != page.packageName) return@runCatching
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
