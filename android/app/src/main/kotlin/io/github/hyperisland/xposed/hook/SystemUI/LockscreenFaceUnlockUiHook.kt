package io.github.hyperisland.xposed.hook

import android.view.View
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field

/**
 * Hides MIUI's native face-unlock UI on the main lock screen while leaving
 * face authentication and the bouncer retry entry untouched.
 */
object LockscreenFaceUnlockUiHook : BaseHook() {

    private const val TAG = "HyperIsland[LockscreenFaceUi]"
    private const val TARGET_CLASS =
        "com.miui.keyguard.biometrics.faceunlock.MiuiKeyguardFaceUnlockView"
    private const val LOCKSCREEN_VIEW_FIELD = "mIsKeyguardFaceUnlockView"

    @Volatile private var hooked = false
    @Volatile private var lockscreenViewField: Field? = null

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui" || hooked) return

        val viewClass = runCatching {
            param.defaultClassLoader.loadClass(TARGET_CLASS)
        }.getOrElse {
            logError(module, "$TARGET_CLASS not found: ${it.message}")
            return
        }

        val visibilityMethod = runCatching {
            viewClass.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType!!)
        }.getOrElse {
            logError(module, "setVisibility(int) not found: ${it.message}")
            return
        }

        visibilityMethod.isAccessible = true
        module.hook(visibilityMethod).intercept { chain ->
            val requestedVisibility = chain.args[0] as? Int
            val result = chain.proceed()
            if (
                requestedVisibility != View.GONE &&
                isMainLockscreenView(chain.thisObject)
            ) {
                (chain.thisObject as? View)?.visibility = View.GONE
            }
            result
        }

        // This marks the main lock-screen instance and also covers initial inflation.
        runCatching {
            val markMethod = viewClass.getDeclaredMethod(
                "setKeyguardFaceUnlockView",
                Boolean::class.javaPrimitiveType!!,
            )
            markMethod.isAccessible = true
            module.hook(markMethod).intercept { chain ->
                val result = chain.proceed()
                if (chain.args[0] == true) {
                    (chain.thisObject as? View)?.visibility = View.GONE
                }
                result
            }
        }.onFailure {
            logWarn(module, "initial visibility fallback unavailable: ${it.message}")
        }

        hooked = true
        log(module, "native lock-screen face UI hidden; authentication remains enabled")
    }

    private fun isMainLockscreenView(instance: Any?): Boolean {
        if (instance == null) return false
        return runCatching {
            val field = lockscreenViewField ?: findField(instance.javaClass, LOCKSCREEN_VIEW_FIELD)
                .also { lockscreenViewField = it }
            field.getBoolean(instance)
        }.getOrDefault(false)
    }

    private fun findField(clazz: Class<*>, name: String): Field {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException("$name not found in ${clazz.name} hierarchy")
    }
}
