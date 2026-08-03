package io.github.hyperisland.xposed.hook

import android.content.Context
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.ArrayDeque
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

object FaceUnlockStateHook : BaseHook() {

    private const val TAG = "HyperIsland[FaceUnlockState]"

    private val monitorClassNames = listOf(
        "com.android.keyguard.KeyguardUpdateMonitor",
        "com.android.systemui.keyguard.KeyguardUpdateMonitor",
    )
    private val repositoryCallbackClassNames = listOf(
        "com.android.systemui.deviceentry.data.repository.DeviceEntryFaceAuthRepositoryImpl\$faceAuthCallback\$1",
    )
    private val sessionStartMethods = setOf(
        "startListeningForFace",
        "requestFaceAuth",
        "requestFaceAuthentication",
    )
    private val authenticationActivityMethods = setOf(
        "handleFaceAcquired",
        "onFaceAcquired",
    )
    private val successMethods = setOf(
        "handleFaceAuthenticated",
        "onFaceAuthenticated",
    )
    private val failedMethods = setOf(
        "handleFaceAuthFailed",
        "onFaceAuthFailed",
        "handleFaceAuthenticationFailed",
        "handleFaceAuthFailure",
        "onFaceAuthenticationFailed",
        "onFaceAuthFailure",
        "onBiometricAuthFailed",
    )
    private val errorMethods = setOf(
        "handleFaceError",
        "onFaceAuthError",
    )
    private val stopMethods = setOf(
        "stopListeningForFace",
        "cancelFaceAuth",
        "cancelFaceAuthentication",
    )

    private val hookedClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val hookedMethods = Collections.newSetFromMap(WeakHashMap<Method, Boolean>())

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return

        hookMonitorClasses(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            hookMonitorClasses(module, classLoader)
        }
    }

    private fun hookMonitorClasses(module: XposedModule, classLoader: ClassLoader) {
        if (!hookedClassLoaders.add(System.identityHashCode(classLoader))) return

        if (hookRepositoryCallback(module, classLoader)) return

        monitorClassNames.forEach { className ->
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: return@forEach
            hookMethods(
                module,
                clazz,
                sessionStartMethods,
                FaceUnlockFocusController.FaceState.AUTHENTICATING,
            )
            hookMethods(
                module,
                clazz,
                authenticationActivityMethods,
                FaceUnlockFocusController.FaceState.AUTHENTICATING,
                authenticationActivity = true,
            )
            hookMethods(
                module,
                clazz,
                setOf("handleFaceHelp"),
                FaceUnlockFocusController.FaceState.AUTHENTICATING,
            )
            hookMethods(module, clazz, successMethods, FaceUnlockFocusController.FaceState.SUCCESS)
            hookFaceMethods(module, clazz, failedMethods, FaceUnlockFocusController.FaceState.FAILED)
            hookErrorMethods(module, clazz)
            hookMethods(
                module,
                clazz,
                stopMethods,
                FaceUnlockFocusController.FaceState.STOPPED,
                runningStopped = true,
            )
            hookFaceRunningState(module, clazz)
            hookFaceAuthenticationCallbacks(module, clazz)
            log(module, "face monitor hooked: ${clazz.name}")
        }
    }

    private fun hookRepositoryCallback(module: XposedModule, classLoader: ClassLoader): Boolean {
        repositoryCallbackClassNames.forEach { className ->
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: return@forEach
            hookMethods(
                module,
                clazz,
                setOf("onAuthenticationAcquired", "onAuthenticationHelp"),
                FaceUnlockFocusController.FaceState.AUTHENTICATING,
            )
            hookMethods(
                module,
                clazz,
                setOf("onAuthenticationSucceeded"),
                FaceUnlockFocusController.FaceState.SUCCESS,
            )
            hookMethods(
                module,
                clazz,
                setOf("onAuthenticationFailed"),
                FaceUnlockFocusController.FaceState.FAILED,
            )
            hookRepositoryErrorMethods(module, clazz)
            log(module, "face repository callback hooked: ${clazz.name}")
            return true
        }
        return false
    }

    private fun hookRepositoryErrorMethods(module: XposedModule, clazz: Class<*>) {
        clazz.declaredMethods
            .filter { it.name == "onAuthenticationError" }
            .forEach { method ->
                synchronized(hookedMethods) {
                    if (!hookedMethods.add(method)) return@forEach
                }
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val errorCode = chain.args.firstOrNull { it is Int } as? Int
                    val state = when (errorCode) {
                        FACE_ERROR_HW_UNAVAILABLE,
                        FACE_ERROR_UNABLE_TO_PROCESS -> FaceUnlockFocusController.FaceState.AUTHENTICATING
                        FACE_ERROR_CANCELED,
                        FACE_ERROR_USER_CANCELED -> FaceUnlockFocusController.FaceState.STOPPED
                        else -> FaceUnlockFocusController.FaceState.FAILED
                    }
                    dispatch(
                        module,
                        clazz.classLoader,
                        state,
                        "${clazz.simpleName}.${method.name}($errorCode)",
                    )
                    result
                }
            }
    }

    private fun hookFaceAuthenticationCallbacks(module: XposedModule, monitorClass: Class<*>) {
        collectDeclaredClasses(monitorClass).forEach { callbackClass ->
            val superClassName = callbackClass.superclass?.name.orEmpty()
            val isFaceCallback = superClassName.contains("FaceManager\$AuthenticationCallback") ||
                callbackClass.name.contains("face", ignoreCase = true)
            if (!isFaceCallback) return@forEach

            hookMethods(
                module,
                callbackClass,
                setOf("onAuthenticationSucceeded"),
                FaceUnlockFocusController.FaceState.SUCCESS,
            )
            hookMethods(
                module,
                callbackClass,
                setOf("onAuthenticationFailed"),
                FaceUnlockFocusController.FaceState.FAILED,
            )
            hookMethods(
                module,
                callbackClass,
                setOf("onAuthenticationAcquired"),
                FaceUnlockFocusController.FaceState.AUTHENTICATING,
                authenticationActivity = true,
            )
            hookErrorMethods(module, callbackClass, setOf("onAuthenticationError"))
            log(module, "face callback hooked: ${callbackClass.name}")
        }
    }

    private fun collectDeclaredClasses(root: Class<*>): List<Class<*>> {
        val result = ArrayList<Class<*>>()
        val pending = ArrayDeque<Class<*>>()
        root.declaredClasses.forEach(pending::addLast)
        while (pending.isNotEmpty()) {
            val clazz = pending.removeFirst()
            result.add(clazz)
            clazz.declaredClasses.forEach(pending::addLast)
        }
        return result
    }

    private fun hookMethods(
        module: XposedModule,
        clazz: Class<*>,
        names: Set<String>,
        state: FaceUnlockFocusController.FaceState,
        runningStopped: Boolean = false,
        authenticationActivity: Boolean = false,
    ) {
        clazz.declaredMethods
            .filter { it.name in names }
            .forEach { method ->
                synchronized(hookedMethods) {
                    if (!hookedMethods.add(method)) return@forEach
                }
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    if (state == FaceUnlockFocusController.FaceState.SUCCESS) {
                        dispatch(
                            module,
                            clazz.classLoader,
                            state,
                            "${clazz.simpleName}.${method.name}",
                            runningStopped,
                            authenticationActivity,
                        )
                    }
                    val result = chain.proceed()
                    if (state != FaceUnlockFocusController.FaceState.SUCCESS) {
                        dispatch(
                            module,
                            clazz.classLoader,
                            state,
                            "${clazz.simpleName}.${method.name}",
                            runningStopped,
                            authenticationActivity,
                        )
                    }
                    result
                }
            }
    }

    private fun hookFaceMethods(
        module: XposedModule,
        clazz: Class<*>,
        names: Set<String>,
        state: FaceUnlockFocusController.FaceState,
    ) {
        clazz.declaredMethods
            .filter { it.name in names }
            .forEach { method ->
                synchronized(hookedMethods) {
                    if (!hookedMethods.add(method)) return@forEach
                }
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (isFaceCallback(chain.args)) {
                        dispatch(module, clazz.classLoader, state, "${clazz.simpleName}.${method.name}")
                    }
                    result
                }
            }
    }

    private fun hookFaceRunningState(module: XposedModule, clazz: Class<*>) {
        clazz.declaredMethods
            .filter { it.name == "setFaceRunningState" }
            .forEach { method ->
                synchronized(hookedMethods) {
                    if (!hookedMethods.add(method)) return@forEach
                }
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    when (chain.args.firstOrNull { it is Int } as? Int) {
                        FACE_RUNNING_STATE_STOPPED -> dispatch(
                            module,
                            clazz.classLoader,
                            FaceUnlockFocusController.FaceState.STOPPED,
                            "${clazz.simpleName}.${method.name}(0)",
                            runningStopped = true,
                        )
                        FACE_RUNNING_STATE_RUNNING -> dispatch(
                            module,
                            clazz.classLoader,
                            FaceUnlockFocusController.FaceState.AUTHENTICATING,
                            "${clazz.simpleName}.${method.name}(1)",
                        )
                    }
                    result
                }
            }
    }

    private fun hookErrorMethods(
        module: XposedModule,
        clazz: Class<*>,
        names: Set<String> = errorMethods,
    ) {
        clazz.declaredMethods
            .filter { it.name in names }
            .forEach { method ->
                synchronized(hookedMethods) {
                    if (!hookedMethods.add(method)) return@forEach
                }
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (!isFaceCallback(chain.args) && !isFaceAuthenticationCallback(clazz)) {
                        return@intercept result
                    }
                    val errorCode = chain.args.firstOrNull { it is Int } as? Int
                    val state = if (errorCode == FACE_ERROR_CANCELED || errorCode == FACE_ERROR_USER_CANCELED) {
                        FaceUnlockFocusController.FaceState.STOPPED
                    } else {
                        FaceUnlockFocusController.FaceState.FAILED
                    }
                    dispatch(
                        module,
                        clazz.classLoader,
                        state,
                        "${clazz.simpleName}.${method.name}($errorCode)",
                    )
                    result
                }
            }
    }

    private fun isFaceCallback(args: List<*>): Boolean {
        val biometricSource = args.firstOrNull {
            it?.javaClass?.name == "android.hardware.biometrics.BiometricSourceType"
        } ?: return true
        return biometricSource.toString().equals("FACE", ignoreCase = true)
    }

    private fun isFaceAuthenticationCallback(clazz: Class<*>): Boolean =
        clazz.superclass?.name.orEmpty().contains("FaceManager\$AuthenticationCallback")

    private fun dispatch(
        module: XposedModule,
        classLoader: ClassLoader,
        state: FaceUnlockFocusController.FaceState,
        source: String,
        runningStopped: Boolean = false,
        authenticationActivity: Boolean = false,
    ) {
        val context: Context = HookUtils.getContext(classLoader) ?: return
        log(module, "face state=$state source=$source")
        if (authenticationActivity) {
            FaceUnlockFocusController.onFaceAuthenticationActivity(context)
        } else if (runningStopped) {
            FaceUnlockFocusController.onFaceRunningStopped(context)
        } else {
            FaceUnlockFocusController.onFaceState(context, state)
        }
    }

    private const val FACE_ERROR_CANCELED = 5
    private const val FACE_ERROR_USER_CANCELED = 10
    private const val FACE_ERROR_HW_UNAVAILABLE = 1
    private const val FACE_ERROR_UNABLE_TO_PROCESS = 2
    private const val FACE_RUNNING_STATE_STOPPED = 0
    private const val FACE_RUNNING_STATE_RUNNING = 1
}
