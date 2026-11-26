package io.github.hyperisland.xposed.hook.SystemUI.animation

import android.app.ActivityManager
import android.app.PendingIntent
import android.os.IBinder
import android.os.UserHandle
import android.util.ArrayMap
import android.view.SurfaceControl
import io.github.hyperisland.xposed.diagnostics.profiledHook
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.WeakHashMap

private const val TRANSIT_OPEN = 1
private const val TRANSIT_TO_FRONT = 3
private const val WINDOWING_MODE_FREEFORM = 5

internal fun isIslandAppLockOpening(
    mode: Int,
    basePackage: String?,
    baseClass: String?,
    topPackage: String?,
    topClass: String?,
    launchPackage: String,
    launchUser: Int,
    behindAppLockPackage: String?,
    taskUser: Int,
): Boolean =
    (mode == TRANSIT_OPEN || mode == TRANSIT_TO_FRONT) &&
        launchUser == taskUser && launchPackage.isNotBlank() && launchPackage == behindAppLockPackage &&
        isAppLockComponent(basePackage, baseClass) &&
        ((topPackage == null && topClass == null) || isAppLockComponent(topPackage, topClass))

private fun isAppLockComponent(packageName: String?, className: String?): Boolean =
    packageName == "com.miui.securitycenter" &&
        (className == "com.miui.applicationlock.AppLockActivity" ||
            className == "com.miui.applicationlock.ConfirmAccessControl")

internal object IslandAppLockAnimationHook : BaseHook() {
    private const val NOTIFICATION = "com.android.systemui.statusbar.notification"
    private const val ANIMATION_RUNNER = "$NOTIFICATION.anim.DynamicIslandAnimationRunner"
    private const val REMOTE_RUNNER = "$ANIMATION_RUNNER\$toRemoteTransition\$1"
    private const val OWNER = "IslandAppLockAnimationHook"

    private data class Launch(val packageName: String, val userId: Int)

    private class OpeningTarget(
        val runner: Any,
        val change: Any,
        val info: Any,
        val transaction: SurfaceControl.Transaction,
        val target: Any,
        val leashMap: ArrayMap<SurfaceControl, SurfaceControl>,
    )

    private val opening = ThreadLocal<Launch>()
    private val launches = WeakHashMap<Any, Launch>()
    private val prepared = ThreadLocal<OpeningTarget>()

    override fun getTag() = "HyperIsland[$OWNER]"

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        val handles = mutableListOf<HookHandle>()
        runCatching {
            val loader = param.defaultClassLoader
            val controller = loader.loadClass("$NOTIFICATION.DynamicIslandWindowAnimController")
            val runner = loader.loadClass(ANIMATION_RUNNER)
            val remote = loader.loadClass(REMOTE_RUNNER)
            val infoClass = loader.loadClass("android.window.TransitionInfo")
            val changeClass = loader.loadClass("android.window.TransitionInfo\$Change")
            val finishCallback = loader.loadClass("android.window.IRemoteTransitionFinishedCallback")
            val legacyFinishCallback = loader.loadClass("android.view.IRemoteAnimationFinishedCallback")
            val targetClass = loader.loadClass("android.view.RemoteAnimationTarget")
            val targetArray = java.lang.reflect.Array.newInstance(targetClass, 0).javaClass
            val transactionClass = SurfaceControl.Transaction::class.java
            val intType = Int::class.javaPrimitiveType!!
            val open = controller.getDeclaredMethod("openFreeform", String::class.java, PendingIntent::class.java)
            val remoteConstructor = remote.getDeclaredConstructor(runner)
            val start = remote.getDeclaredMethod(
                "startAnimation", IBinder::class.java, infoClass, transactionClass, finishCallback,
            )
            val startRunner = runner.getDeclaredMethod(
                "onAnimationStart", intType, targetArray, targetArray, targetArray, legacyFinishCallback,
            )
            val nativeTarget = loader.loadClass("com.android.wm.shell.shared.TransitionUtil").getMethod(
                "newTarget", changeClass, intType, infoClass, transactionClass, ArrayMap::class.java,
            )
            val owner = remote.getDeclaredField("this\$0").apply { isAccessible = true }
            val exit = runner.getDeclaredField("isFreeformExitAnim").apply { isAccessible = true }
            val finishBridge = loader.loadClass("$REMOTE_RUNNER\$startAnimation\$3")
            val finishRunnable = finishBridge.getDeclaredField("\$animationFinishedCallback")
                .apply { isAccessible = true }
            val finishLeashMap = finishRunnable.type.getDeclaredField("\$leashMap").apply { isAccessible = true }
            val targetLeash = targetClass.getField("leash")
            val changes = infoClass.getMethod("getChanges")
            val mode = changeClass.getMethod("getMode")
            val taskInfo = changeClass.getMethod("getTaskInfo")
            val changeLeash = changeClass.getMethod("getLeash")
            val task = ActivityManager.RunningTaskInfo::class.java
            val behindPackage = task.getField("mBehindAppLockPkg")
            val taskUser = task.getField("userId")
            val windowingMode = task.getMethod("getWindowingMode")
            val userIdentifier = UserHandle::class.java.getMethod("getIdentifier")

            check(module.deoptimize(start)) { "cannot deoptimize App Lock remote transition" }

            handles += module.profiledHook(OWNER, open).intercept { chain ->
                val launch = runCatching {
                    val intent = chain.args[1] as? PendingIntent ?: return@runCatching null
                    val creator = intent.creatorPackage ?: return@runCatching null
                    Launch(creator, userIdentifier.invoke(intent.creatorUserHandle) as Int)
                }.getOrNull()
                // Proxy notifications can use a SystemUI controller with an app-owned PendingIntent.
                val previous = opening.get()
                opening.set(launch)
                try {
                    chain.proceed()
                } finally {
                    if (previous == null) opening.remove() else opening.set(previous)
                }
            }
            handles += module.profiledHook(OWNER, remoteConstructor).intercept { chain ->
                val launch = opening.get()
                val result = chain.proceed()
                val remoteRunner = chain.thisObject
                if (launch != null && remoteRunner != null) synchronized(launches) {
                    if (launches.size < 64) launches[remoteRunner] = launch
                }
                result
            }
            handles += module.profiledHook(OWNER, nativeTarget).intercept { chain ->
                val target = prepared.get()
                if (target != null && chain.args[0] === target.change &&
                    chain.args[2] === target.info && chain.args[3] === target.transaction
                ) {
                    // If this ROM selected the same change itself, reuse the prepared leash.
                    @Suppress("UNCHECKED_CAST")
                    (chain.args[4] as? ArrayMap<SurfaceControl, SurfaceControl>)?.putAll(target.leashMap)
                    target.target
                } else {
                    chain.proceed()
                }
            }
            handles += module.profiledHook(OWNER, startRunner).intercept { chain ->
                val target = prepared.get()
                if (target == null || chain.thisObject !== target.runner ||
                    !(chain.args[1] as? Array<*>).isNullOrEmpty()
                ) return@intercept chain.proceed()

                val args = chain.args.toTypedArray()
                args[1] = java.lang.reflect.Array.newInstance(targetClass, 1).also {
                    java.lang.reflect.Array.set(it, 0, target.target)
                }
                // Hand the wrapper to the same bookkeeping used for stock-selected targets.
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    val stockLeashes = finishLeashMap.get(finishRunnable.get(chain.args[4]))
                        as? ArrayMap<SurfaceControl, SurfaceControl>
                    stockLeashes?.putAll(target.leashMap)
                }
                log(module) { "using App Lock task for native island animation" }
                chain.proceed(args)
            }
            handles += module.profiledHook(OWNER, start).intercept { chain ->
                val launch = synchronized(launches) { launches.remove(chain.thisObject) }
                    ?: return@intercept chain.proceed()
                val info = chain.args[1] ?: return@intercept chain.proceed()
                val transaction = chain.args[2] as? SurfaceControl.Transaction
                    ?: return@intercept chain.proceed()
                val target = runCatching {
                    val nativeRunner = owner.get(chain.thisObject) ?: return@runCatching null
                    if (exit.getBoolean(nativeRunner)) return@runCatching null
                    val change = (changes.invoke(info) as? List<*>)?.singleOrNull()
                        ?: return@runCatching null
                    val taskData = taskInfo.invoke(change) as? ActivityManager.RunningTaskInfo
                        ?: return@runCatching null
                    if (windowingMode.invoke(taskData) != WINDOWING_MODE_FREEFORM) return@runCatching null
                    val base = taskData.baseActivity
                    val top = taskData.topActivity
                    if (!isIslandAppLockOpening(
                            mode.invoke(change) as Int,
                            base?.packageName, base?.className, top?.packageName, top?.className,
                            launch.packageName, launch.userId,
                            behindPackage.get(taskData) as? String, taskUser.getInt(taskData),
                        )
                    ) return@runCatching null
                    val leash = changeLeash.invoke(change) as? SurfaceControl ?: return@runCatching null
                    if (!leash.isValid) return@runCatching null

                    val leashMap = ArrayMap<SurfaceControl, SurfaceControl>()
                    var merged = false
                    try {
                        SurfaceControl.Transaction().use { stage ->
                            val native = nativeTarget.invoke(null, change, 1, info, stage, leashMap)
                                ?: return@runCatching null
                            if ((targetLeash.get(native) as? SurfaceControl)?.isValid != true) {
                                return@runCatching null
                            }
                            // Commit the native opening geometry with Shell's first transaction.
                            transaction.merge(stage)
                            merged = true
                            OpeningTarget(nativeRunner, change, info, transaction, native, leashMap)
                        }
                    } finally {
                        if (!merged) leashMap[leash]?.takeIf { it !== leash }?.release()
                    }
                }.onFailure { error ->
                    logFailureOnce(module, "target") { "App Lock animation target unavailable: ${error.message}" }
                }.getOrNull() ?: return@intercept chain.proceed()
                val previous = prepared.get()
                prepared.set(target)
                try {
                    chain.proceed()
                } finally {
                    if (previous == null) prepared.remove() else prepared.set(previous)
                }
            }
        }.onFailure { error ->
            handles.asReversed().forEach { runCatching { it.unhook() } }
            logFailureOnce(module, "install") { "app-lock animation unavailable: ${error.message}" }
        }
    }
}
