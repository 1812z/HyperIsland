package io.github.hyperisland.xposed.hook.SystemUI.extensions

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

object KeyguardUnlockStateHook : BaseHook() {

    private const val TAG = "HyperIsland[KeyguardUnlockState]"

    private val controllerClassNames = listOf(
        "com.android.systemui.statusbar.policy.KeyguardStateControllerImpl",
        "com.android.systemui.statusbar.policy.MiuiKeyguardStateControllerImpl",
    )
    private val monitorClassNames = listOf(
        "com.android.keyguard.KeyguardUpdateMonitor",
        "com.android.systemui.keyguard.KeyguardUpdateMonitor",
    )
    private val islandIconViewHolderClassNames = listOf(
        "miui.systemui.dynamicisland.module.IslandIconViewHolder",
    )
    private val hookedClassLoaders = Collections.newSetFromMap(
        WeakHashMap<ClassLoader, Boolean>(),
    )
    private val hookedMethods = Collections.newSetFromMap(WeakHashMap<Method, Boolean>())
    @Volatile private var initialized = false
    @Volatile private var screenReceiverRegistered = false

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui" || initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true
        }
        hookClasses(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookClasses(module, classLoader)
        }
    }

    fun registerScreenReceiver(context: Context) {
        if (screenReceiverRegistered) return
        synchronized(this) {
            if (screenReceiverRegistered) return
            val appContext = context.applicationContext ?: context
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        Intent.ACTION_SCREEN_OFF ->
                            FaceUnlockFocusController.onScreenOff(appContext)
                        Intent.ACTION_SCREEN_ON ->
                            FaceUnlockFocusController.onScreenOn(appContext)
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            screenReceiverRegistered = true
        }
    }

    private fun hookClasses(module: XposedModule, classLoader: ClassLoader) {
        synchronized(hookedClassLoaders) {
            if (!hookedClassLoaders.add(classLoader)) return
        }
        controllerClassNames.forEach { className ->
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: return@forEach
            hookGoingAwayMethods(module, clazz)
            hookShowingMethods(module, clazz, setOf("notifyKeyguardState"))
        }
        monitorClassNames.forEach { className ->
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: return@forEach
            hookShowingMethods(module, clazz, setOf("setKeyguardShowing"))
        }
        islandIconViewHolderClassNames.forEach { className ->
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: return@forEach
            hookStaticFaceExpandGeometry(module, clazz)
            hookFaceSuccessCleanup(module, clazz)
        }
        runCatching {
            classLoader.loadClass("miui.systemui.notification.focus.FocusNotifPreHandler")
        }.getOrNull()?.let { hookExpandedFaceSuccessCleanup(module, it) }
    }

    private fun hookFaceSuccessCleanup(module: XposedModule, clazz: Class<*>) {
        val getPicInfo = runCatching { clazz.getMethod("getPicInfo") }.getOrNull() ?: return
        val getPic = runCatching { getPicInfo.returnType.getMethod("getPic") }.getOrNull() ?: return
        clazz.declaredMethods.filter {
            it.name == "clearFaceRecognition" && it.parameterCount == 1 &&
                it.parameterTypes[0].name ==
                "com.android.systemui.plugins.miui.dynamicisland.DynamicIslandData"
        }.forEach { method ->
            val getExtras = runCatching {
                method.parameterTypes[0].getMethod("getExtras")
            }.getOrNull() ?: return@forEach
            synchronized(hookedMethods) {
                if (!hookedMethods.add(method)) return@forEach
            }
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                val hold = runCatching {
                    val data = chain.args.firstOrNull() ?: return@runCatching false
                    val extras = getExtras.invoke(data) as? Bundle ?: return@runCatching false
                    val sbn = extras.getParcelable("miui.sbn", StatusBarNotification::class.java)
                        ?: return@runCatching false
                    val picInfo = getPicInfo.invoke(chain.thisObject) ?: return@runCatching false
                    getPic.invoke(picInfo) == "face_recognition_success_small" &&
                        FaceUnlockFocusController.shouldHoldFaceSuccess(sbn)
                }.onFailure {
                    logError(module, "face success cleanup identity check failed: ${it.message}")
                }.getOrDefault(false)
                if (hold) {
                    log(module) { "hold face success island: skip clearFaceRecognition" }
                    null
                } else {
                    chain.proceed()
                }
            }
        }
    }

    private fun hookExpandedFaceSuccessCleanup(module: XposedModule, clazz: Class<*>) {
        clazz.declaredMethods.filter {
            it.name == "playAnimation" && it.parameterCount == 5 &&
                it.parameterTypes[1] == StatusBarNotification::class.java &&
                it.parameterTypes[2] == String::class.java &&
                it.parameterTypes[3].name == "com.airbnb.lottie.LottieAnimationView"
        }.forEach { method ->
            val binding = runCatching {
                val lottie = method.parameterTypes[3]
                val utils = clazz.classLoader.loadClass("miui.systemui.util.LottieResUtils")
                val resource = utils.getMethod("getLottieRes", String::class.java, Int::class.javaPrimitiveType)
                    .invoke(utils.getField("INSTANCE").get(null), "face_recognition_success", -1) as Int
                require(resource != 0)
                StaticFaceBinding(
                    resource, utils.getField("IMAGE_ASSETS_FOLDER").get(null) as String,
                    lottie.getMethod("setImageAssetsFolder", String::class.java),
                    lottie.getMethod("setAnimation", Int::class.javaPrimitiveType),
                    lottie.getMethod("pauseAnimation"),
                    lottie.getMethod("setProgress", Float::class.javaPrimitiveType),
                )
            }.onFailure {
                logError(module, "manual face success binding unavailable: ${it.message}")
            }.getOrNull() ?: return@forEach
            synchronized(hookedMethods) {
                if (!hookedMethods.add(method)) return@forEach
            }
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                val sbn = chain.args[1] as? StatusBarNotification
                if (chain.args[2] != "face_recognition_success" || sbn == null ||
                    !FaceUnlockFocusController.shouldUseStaticManualExpansion(sbn)
                ) return@intercept chain.proceed()
                val view = chain.args[3] ?: return@intercept chain.proceed()
                val bound = runCatching {
                    binding.assetsMethod.invoke(view, binding.assets)
                    binding.animation.invoke(view, binding.resource)
                    binding.pause.invoke(view)
                    binding.progress.invoke(view, 1f)
                }.onFailure {
                    logError(module, "manual face success final frame failed: ${it.message}")
                }.isSuccess
                if (bound) null else chain.proceed()
            }
        }
        clazz.declaredMethods.filter {
            it.name == "clearFaceRecognition" && it.parameterCount == 4 &&
                it.parameterTypes[0] == StatusBarNotification::class.java &&
                it.parameterTypes[1] == String::class.java
        }.forEach { method ->
            synchronized(hookedMethods) {
                if (!hookedMethods.add(method)) return@forEach
            }
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                val sbn = chain.args[0] as? StatusBarNotification
                if (chain.args[1] == "face_recognition_success" && sbn != null &&
                    FaceUnlockFocusController.shouldHoldFaceSuccess(sbn)
                ) {
                    // Also guard callbacks from the old animated view after the static update.
                    log(module) { "hold expanded face success: skip notification auto-cancel" }
                    null
                } else {
                    chain.proceed()
                }
            }
        }
    }

    private data class StaticFaceBinding(
        val resource: Int,
        val assets: String,
        val assetsMethod: Method,
        val animation: Method,
        val pause: Method,
        val progress: Method,
    )

    private fun hookStaticFaceExpandGeometry(module: XposedModule, clazz: Class<*>) {
        clazz.declaredMethods
            .filter { it.name == "setFixIcon" && it.parameterCount == 1 }
            .forEach { method ->
                synchronized(hookedMethods) {
                    if (!hookedMethods.add(method)) return@forEach
                }
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val picInfo = runCatching {
                        clazz.getMethod("getPicInfo").invoke(chain.thisObject)
                    }.getOrNull()
                    val pic = runCatching {
                        picInfo?.javaClass?.getMethod("getPic")?.invoke(picInfo) as? String
                    }.getOrNull()
                    if (pic != "hyperisland_face_success_expand") return@intercept result

                    val imageView = runCatching {
                        clazz.getDeclaredField("fixIcon")
                            .apply { isAccessible = true }
                            .get(chain.thisObject) as? android.view.View
                    }.getOrNull() ?: return@intercept result
                    val size = (26f * imageView.resources.displayMetrics.density).toInt()
                    val params = imageView.layoutParams
                    params.width = size
                    params.height = size
                    if (params is ViewGroup.MarginLayoutParams) {
                        params.marginStart = 0
                        params.marginEnd = 0
                    }
                    if (params is FrameLayout.LayoutParams) {
                        params.gravity = Gravity.CENTER_VERTICAL
                    }
                    imageView.layoutParams = params
                    result
                }
            }
    }

    private fun hookGoingAwayMethods(module: XposedModule, clazz: Class<*>) {
        clazz.declaredMethods
            .filter { it.name == "notifyKeyguardGoingAway" }
            .forEach { method ->
                hookMethod(module, clazz, method, before = true) { context, args ->
                    if (args.getOrNull(0) == true) {
                        FaceUnlockFocusController.onKeyguardGoingAway(context)
                    }
                }
            }
    }

    private fun hookShowingMethods(
        module: XposedModule,
        clazz: Class<*>,
        names: Set<String>,
    ) {
        clazz.declaredMethods
            .filter { it.name in names }
            .forEach { method ->
                hookMethod(module, clazz, method, before = false) { context, args ->
                    val showing = args.getOrNull(0) as? Boolean ?: return@hookMethod
                    if (showing) {
                        FaceUnlockFocusController.onKeyguardLocked(context)
                    } else {
                        FaceUnlockFocusController.onKeyguardHidden(context)
                    }
                }
            }
    }

    private fun hookMethod(
        module: XposedModule,
        clazz: Class<*>,
        method: Method,
        before: Boolean,
        callback: (Context, List<*>) -> Unit,
    ) {
        synchronized(hookedMethods) {
            if (!hookedMethods.add(method)) return
        }
        method.isAccessible = true
        module.hook(method).intercept { chain ->
            val context = clazz.classLoader?.let(HookUtils::getContext)
            if (before) context?.let { callback(it, chain.args) }
            val result = chain.proceed()
            if (!before) context?.let { callback(it, chain.args) }
            result
        }
    }
}
