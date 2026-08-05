package io.github.hyperisland.xposed.hook

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
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
    private val focusPreHandlerClassNames = listOf(
        "miui.systemui.notification.focus.FocusNotifPreHandler",
    )
    private val dynamicIslandWindowControllerClassNames = listOf(
        "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController",
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
    @Volatile private var retainedFaceIslandKey: String? = null

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
        focusPreHandlerClassNames.forEach { className ->
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: return@forEach
            hookPluginFaceCleanup(module, clazz)
        }
        dynamicIslandWindowControllerClassNames.forEach { className ->
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: return@forEach
            hookPluginIslandTimeout(module, clazz)
            hookPluginIslandRemoval(module, clazz)
        }
        islandIconViewHolderClassNames.forEach { className ->
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: return@forEach
            hookPluginSmallFaceCleanup(module, clazz)
        }
    }

    private fun hookPluginFaceCleanup(module: XposedModule, clazz: Class<*>) {
        clazz.declaredMethods
            .filter { it.name == "clearFaceRecognition" }
            .forEach { method ->
                synchronized(hookedMethods) {
                    if (!hookedMethods.add(method)) return@forEach
                }
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    val faceType = chain.args.getOrNull(1) as? String
                    if (
                        faceType == "face_recognition_success" &&
                        FaceUnlockFocusController.shouldKeepPluginFaceSuccess()
                    ) {
                        freezeLottieAtFinalFrame(chain.args.getOrNull(2))
                        log(module, "keeping plugin face success on its final Lottie frame")
                        return@intercept null
                    }
                    chain.proceed()
                }
            }
    }

    private fun hookPluginIslandTimeout(module: XposedModule, clazz: Class<*>) {
        clazz.declaredMethods
            .filter { it.name == "setCancelTimeout" && it.parameterCount == 2 }
            .forEach { method ->
                synchronized(hookedMethods) {
                    if (!hookedMethods.add(method)) return@forEach
                }
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    if (!FaceUnlockFocusController.shouldKeepPluginFaceSuccess()) {
                        return@intercept chain.proceed()
                    }
                    val islandData = chain.args.getOrNull(0) ?: return@intercept chain.proceed()
                    val islandTemplate = chain.args.getOrNull(1) ?: return@intercept chain.proceed()
                    val business = runCatching {
                        islandTemplate.javaClass.getMethod("getBusiness").invoke(islandTemplate) as? String
                    }.getOrNull()
                    if (business != "face_recognition") return@intercept chain.proceed()

                    val key = runCatching {
                        islandData.javaClass.getMethod("getKey").invoke(islandData) as? String
                    }.getOrNull() ?: return@intercept chain.proceed()
                    retainedFaceIslandKey = key
                    val timeoutCancelled = runCatching {
                        val safeguards = clazz.getMethod("getDynamicIslandSafeguardsController")
                            .invoke(chain.thisObject)
                        val cancelMethod = safeguards.javaClass.getDeclaredMethod(
                            "cancelDelayDeleted",
                            String::class.java,
                        ).apply { isAccessible = true }
                        cancelMethod.invoke(safeguards, key)
                    }.isSuccess
                    if (!timeoutCancelled) return@intercept chain.proceed()

                    log(module, "disabled face recognition island timeout while keyguard is showing")
                    null
                }
            }
    }

    private fun hookPluginSmallFaceCleanup(module: XposedModule, clazz: Class<*>) {
        clazz.declaredMethods
            .filter { it.name == "clearFaceRecognition" && it.parameterCount == 1 }
            .forEach { method ->
                synchronized(hookedMethods) {
                    if (!hookedMethods.add(method)) return@forEach
                }
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    if (!FaceUnlockFocusController.shouldKeepPluginFaceSuccess()) {
                        return@intercept chain.proceed()
                    }
                    val picInfo = runCatching {
                        clazz.getMethod("getPicInfo").invoke(chain.thisObject)
                    }.getOrNull() ?: return@intercept chain.proceed()
                    val pic = runCatching {
                        picInfo.javaClass.getMethod("getPic").invoke(picInfo) as? String
                    }.getOrNull()
                    if (pic != "face_recognition_success_small") {
                        return@intercept chain.proceed()
                    }

                    val lottie = runCatching {
                        clazz.getDeclaredField("lottieBigView")
                            .apply { isAccessible = true }
                            .get(chain.thisObject)
                    }.getOrNull()
                    freezeLottieAtFinalFrame(lottie)
                    log(module, "keeping small face success on its final Lottie frame")
                    null
                }
            }
    }

    private fun hookPluginIslandRemoval(module: XposedModule, clazz: Class<*>) {
        clazz.declaredMethods
            .filter {
                it.name == "removeDynamicIslandView" &&
                    it.parameterTypes.firstOrNull() == String::class.java
            }
            .forEach { method ->
                synchronized(hookedMethods) {
                    if (!hookedMethods.add(method)) return@forEach
                }
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    val key = chain.args.firstOrNull() as? String
                    if (
                        key != null &&
                        key == retainedFaceIslandKey &&
                        FaceUnlockFocusController.shouldKeepPluginFaceSuccess()
                    ) {
                        log(module, "blocked removal of retained face recognition island")
                        return@intercept null
                    }
                    chain.proceed()
                }
            }
    }

    private fun freezeLottieAtFinalFrame(lottie: Any?) {
        if (lottie == null) return
        runCatching {
            lottie.javaClass.getMethod("setProgress", Float::class.javaPrimitiveType!!)
                .invoke(lottie, 1f)
            lottie.javaClass.getMethod("pauseAnimation").invoke(lottie)
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
                        retainedFaceIslandKey = null
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
            val context = HookUtils.getContext(clazz.classLoader)
            if (before) context?.let { callback(it, chain.args) }
            val result = chain.proceed()
            if (!before) context?.let { callback(it, chain.args) }
            result
        }
    }
}
