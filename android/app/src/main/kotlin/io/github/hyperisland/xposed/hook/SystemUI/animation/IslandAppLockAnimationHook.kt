package io.github.hyperisland.xposed.hook.SystemUI.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.SurfaceControl
import android.view.animation.DecelerateInterpolator
import io.github.hyperisland.xposed.diagnostics.profiledHook
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.concurrent.atomic.AtomicReference

private const val TRANSIT_OPEN = 1
private const val TRANSIT_TO_FRONT = 3

internal fun isIslandAppLockOpening(
    mode: Int,
    basePackage: String?,
    baseClass: String?,
    topPackage: String?,
    topClass: String?,
): Boolean =
    (mode == TRANSIT_OPEN || mode == TRANSIT_TO_FRONT) &&
        isAppLockComponent(basePackage, baseClass) &&
        ((topPackage == null && topClass == null) || isAppLockComponent(topPackage, topClass))

private fun isAppLockComponent(packageName: String?, className: String?): Boolean =
    packageName == "com.miui.securitycenter" &&
        (className == "com.miui.applicationlock.AppLockActivity" ||
            className == "com.miui.applicationlock.ConfirmAccessControl")

internal object IslandAppLockAnimationHook : BaseHook() {
    private const val REMOTE_RUNNER =
        "com.android.systemui.statusbar.notification.anim.DynamicIslandAnimationRunner\$toRemoteTransition\$1"
    private const val INITIAL_ALPHA = 0.65f
    private const val FADE_MS = 140L
    private const val FINISH_TIMEOUT_MS = 320L

    private val handler = Handler(Looper.getMainLooper())
    private val active = AtomicReference<Fade?>()

    override fun getTag() = "HyperIsland[IslandAppLockAnimation]"

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        val handles = mutableListOf<HookHandle>()
        runCatching {
            val loader = param.defaultClassLoader
            val runner = loader.loadClass(REMOTE_RUNNER)
            val transitionInfo = loader.loadClass("android.window.TransitionInfo")
            val change = loader.loadClass("android.window.TransitionInfo\$Change")
            val finishCallback = loader.loadClass("android.window.IRemoteTransitionFinishedCallback")
            val transactionClass = SurfaceControl.Transaction::class.java
            val start = runner.getDeclaredMethod(
                "startAnimation", IBinder::class.java, transitionInfo, transactionClass, finishCallback,
            )
            val merge = runner.getDeclaredMethod(
                "mergeAnimation", IBinder::class.java, transitionInfo, transactionClass,
                IBinder::class.java, finishCallback,
            )
            val consumed = runner.getDeclaredMethod(
                "onTransitionConsumed", IBinder::class.java, Boolean::class.javaPrimitiveType,
            )
            val finishClass = loader.loadClass("$REMOTE_RUNNER\$startAnimation\$animationFinishedCallback\$1")
            val finish = finishClass.getDeclaredMethod("run")
            val finishInfo = finishClass.getDeclaredField("\$info").apply { isAccessible = true }
            val getChanges = transitionInfo.getMethod("getChanges")
            val getMode = change.getMethod("getMode")
            val getTaskInfo = change.getMethod("getTaskInfo")
            val getLeash = change.getMethod("getLeash")

            handles += module.profiledHook("IslandAppLockAnimationHook", finish).intercept { chain ->
                val fade = active.get()
                val callback = chain.thisObject as? Runnable
                if (fade != null && callback != null && runCatching {
                        finishInfo.get(callback) === fade.transitionInfo && fade.deferFinish(callback)
                    }.getOrDefault(false)
                ) null else chain.proceed()
            }
            handles += module.profiledHook("IslandAppLockAnimationHook", merge).intercept { chain ->
                endFadeFor(chain.args[3])
                chain.proceed()
            }
            handles += module.profiledHook("IslandAppLockAnimationHook", consumed).intercept { chain ->
                endFadeFor(chain.args[0])
                chain.proceed()
            }
            handles += module.profiledHook("IslandAppLockAnimationHook", start).intercept { chain ->
                val token = chain.args[0] as? IBinder ?: return@intercept chain.proceed()
                val info = chain.args[1] ?: return@intercept chain.proceed()
                val transaction = chain.args[2] as? SurfaceControl.Transaction
                    ?: return@intercept chain.proceed()
                val fade = runCatching {
                    if (active.get() != null || !ValueAnimator.areAnimatorsEnabled()) return@runCatching null
                    // The observed app-lock launch is a transition for a single Security Center task.
                    val opening = (getChanges.invoke(info) as? List<*>)?.singleOrNull()
                        ?: return@runCatching null
                    val task = getTaskInfo.invoke(opening) as? ActivityManager.RunningTaskInfo
                        ?: return@runCatching null
                    val base = task.baseActivity
                    val top = task.topActivity
                    val mode = getMode.invoke(opening) as Int
                    if (!isIslandAppLockOpening(mode, base?.packageName, base?.className,
                            top?.packageName, top?.className)
                    ) return@runCatching null
                    val leash = getLeash.invoke(opening) as? SurfaceControl ?: return@runCatching null
                    if (!leash.isValid) return@runCatching null
                    Fade(token, info, leash)
                }.getOrNull() ?: return@intercept chain.proceed()
                if (!active.compareAndSet(null, fade)) return@intercept chain.proceed()

                // WindowManager hands off the drawn prompt here. Start the fade once the
                // stock start transaction carrying its first alpha has been committed.
                try {
                    transaction.addTransactionCommittedListener({ handler.post(it) }, fade::start)
                    transaction.setAlpha(fade.leash, INITIAL_ALPHA)
                } catch (_: Exception) {
                    active.compareAndSet(fade, null)
                    return@intercept chain.proceed()
                }
                handler.postDelayed(fade.timeout, FINISH_TIMEOUT_MS)
                try {
                    chain.proceed()
                } catch (error: Throwable) {
                    runCatching { transaction.setAlpha(fade.leash, 1f) }
                    handler.post { fade.complete() }
                    throw error
                }
            }
            // The stock callback can inline its completion Runnable.
            runCatching {
                val callback = loader.loadClass("$REMOTE_RUNNER\$startAnimation\$3")
                module.deoptimize(callback.getDeclaredMethod("onAnimationFinished"))
            }
        }.onFailure { error ->
            handles.asReversed().forEach { runCatching { it.unhook() } }
            logFailureOnce(module, "install") { "app-lock animation unavailable: ${error.message}" }
        }
    }

    private fun endFadeFor(token: Any?) {
        val fade = active.get() ?: return
        if (fade.token == token) handler.post { fade.complete() }
    }

    private class Fade(
        val token: IBinder,
        val transitionInfo: Any,
        val leash: SurfaceControl,
    ) {
        val timeout = Runnable { complete() }
        private var completed = false
        private var finishCallback: Runnable? = null
        private var animator: ValueAnimator? = null
        private var frameTransaction: SurfaceControl.Transaction? = null

        @Synchronized
        fun deferFinish(callback: Runnable): Boolean {
            if (active.get() !== this) return false
            finishCallback = callback
            return true
        }

        fun start() {
            if (active.get() !== this) return
            runCatching {
                if (!leash.isValid || !ValueAnimator.areAnimatorsEnabled()) {
                    complete()
                    return
                }
                frameTransaction = SurfaceControl.Transaction()
                val fadeAnimator = ValueAnimator.ofFloat(INITIAL_ALPHA, 1f).apply {
                    duration = FADE_MS
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { animation ->
                        runCatching {
                            if (leash.isValid) {
                                frameTransaction?.setAlpha(leash, animation.animatedValue as Float)?.apply()
                            } else {
                                complete()
                            }
                        }.onFailure { complete() }
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) = complete()
                    })
                }
                animator = fadeAnimator
                fadeAnimator.start()
            }.onFailure { complete() }
        }

        fun complete() {
            if (completed) return
            completed = true
            handler.removeCallbacks(timeout)
            animator?.cancel()
            animator = null
            runCatching {
                if (leash.isValid) {
                    val transaction = frameTransaction ?: SurfaceControl.Transaction()
                    frameTransaction = transaction
                    transaction.setAlpha(leash, 1f).apply()
                }
            }
            runCatching { frameTransaction?.close() }
            frameTransaction = null
            val callback = synchronized(this) {
                active.compareAndSet(this, null)
                finishCallback.also { finishCallback = null }
            }
            callback?.run()
        }
    }
}
