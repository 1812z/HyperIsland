package io.github.hyperisland.xposed.hook.SystemUI.extensions

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
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
        }
    }

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
            val context = HookUtils.getContext(clazz.classLoader)
            if (before) context?.let { callback(it, chain.args) }
            val result = chain.proceed()
            if (!before) context?.let { callback(it, chain.args) }
            result
        }
    }
}
