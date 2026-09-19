package io.github.hyperisland.xposed.hook.PermissionManager

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import io.github.hyperisland.utils.getAppIcon
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import io.github.hyperisland.xposed.islanddispatch.definition.IslandRequest
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

object ClipboardToastHook : BaseHook() {
    private const val TAG = "HyperIsland[ClipboardToast]"
    private const val SECURITY_CENTER_PACKAGE = "com.miui.securitycenter"
    private const val MODULE_PACKAGE = "io.github.hyperisland"
    private const val TYPE_ACCESS_CLIP_NOTIFICATION = 1
    private const val KEY_OPTIMIZE_ISLAND_STYLE = "pref_clipboard_optimize_island_style"

    /** 旧版安全服务（未混淆构建）的 ToastUtil 类名，命中即无需 DexKit 扫描。 */
    private val TOAST_UTIL_CLASSES = arrayOf(
        // Newer Security Center builds (pre-R8).
        "com.hyperos.security.utility.ToastUtil",
        // HyperOS 3 Permission Manager / LBE builds.
        "com.lbe.security.utility.ToastUtil",
    )

    /** ToastUtil 展示方法体内的稳定日志串；新版 R8 改名后仅该类使用。 */
    private const val ANCHOR_SHOW_TOAST_LOG = "showToastInternal  for "

    /** 关闭按钮广播 action，作为类级备用锚点。 */
    private const val ANCHOR_CLOSE_TIP_ACTION = "permission.intent.action.CLIPBOARD_CLOSE_TIP"

    private val hookedToastMethods = ConcurrentHashMap.newKeySet<Method>()

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        val processName = runCatching { Application.getProcessName() }
            .getOrNull()
            .orEmpty()
        if (!isToastProcess(param.packageName, processName)) {
            safeLog(
                module,
                Log.INFO,
                "skip process: package=${param.packageName}, process=$processName",
            )
            return
        }

        safeLog(
            module,
            Log.INFO,
            "initializing in package=${param.packageName}, process=$processName",
        )
        val toastHooked = installHook(module, "ToastUtil") {
            hookSecurityCenterToast(module, param.defaultClassLoader)
        }
        if (toastHooked != true) {
            installHook(module, "WindowManager.addView fallback") {
                hookWindowAddView(module, param.defaultClassLoader)
                true
            }
        }
    }

    /**
     * 剪贴板提示可能出现的位置：
     * - 主进程与 :ui —— 旧版本路径；
     * - .remote —— HyperOS 3 新版将 PrivacyCenterProvider（AppOps 剪贴板回调，
     *   ToastUtil 的触发方）迁移至 com.miui.securitycenter.remote 进程。
     */
    private fun isToastProcess(packageName: String, processName: String): Boolean {
        if (processName.isEmpty()) return true
        return processName == packageName ||
            processName == "$packageName:ui" ||
            processName == "$packageName.remote"
    }

    private inline fun <T> installHook(
        module: XposedModule,
        name: String,
        block: () -> T,
    ): T? = try {
        block()
    } catch (t: Throwable) {
        safeLog(
            module,
            Log.ERROR,
            "$name hook failed: ${Log.getStackTraceString(t)}",
        )
        null
    }

    private fun safeLog(module: XposedModule, priority: Int, message: String) {
        try {
            module.log(priority, TAG, message)
        } catch (_: Throwable) {
            // A diagnostic failure must never abort package initialization.
        }
    }

    private fun hookSecurityCenterToast(module: XposedModule, classLoader: ClassLoader): Boolean {
        if (hookToastUtilByName(module, classLoader)) return true
        return hookToastUtilByDexKit(module, classLoader)
    }

    private fun hookToastUtilByName(module: XposedModule, classLoader: ClassLoader): Boolean {
        var hooked = false
        TOAST_UTIL_CLASSES.forEach { className ->
            val clazz = runCatching {
                Class.forName(className, false, classLoader)
            }.getOrNull() ?: return@forEach
            val methods = clazz.declaredMethods
                .filter { it.name == "showToast" && it.isShowToastShape() }
            if (methods.isEmpty() || !hasContextField(clazz)) {
                return@forEach
            }
            methods.forEach { hookShowToastMethod(module, it) }
            hooked = true
        }
        return hooked
    }

    /**
     * 新版安全服务开启 R8 混淆后，ToastUtil 类名（如 l3.f）与 showToast 方法名
     * （如 m42102o / m42098l）随每次构建漂移，但方法体内的日志字符串不变。
     * 优先以展示方法内的日志串定位 (String, int) 方法；失败再以关闭按钮的
     * 广播 action 定位类，挂钩其公开 (String, int) 方法。
     */
    private fun hookToastUtilByDexKit(module: XposedModule, classLoader: ClassLoader): Boolean =
        runCatching {
            System.loadLibrary("dexkit")
            DexKitBridge.create(classLoader, false).use { bridge ->
                val showMethods = bridge.findMethod {
                    matcher {
                        usingStrings(ANCHOR_SHOW_TOAST_LOG)
                    }
                }.mapNotNull { it.getMethodInstance(classLoader) }
                    .filter { it.isShowToastShape() }
                if (showMethods.isNotEmpty()) {
                    showMethods.forEach { hookShowToastMethod(module, it) }
                    return@use true
                }
                safeLog(module, Log.INFO, "log anchor miss; trying class anchor")
                bridge.findClass {
                    matcher {
                        usingStrings(ANCHOR_CLOSE_TIP_ACTION)
                    }
                }.mapNotNull { classData ->
                    runCatching { classData.getInstance(classLoader) }.getOrNull()
                }.flatMap { clazz ->
                    clazz.declaredMethods.filter {
                        it.isShowToastShape() && Modifier.isPublic(it.modifiers)
                    }
                }.onEach { hookShowToastMethod(module, it) }
                    .isNotEmpty()
            }
        }.getOrElse {
            safeLog(module, Log.WARN, "DexKit ToastUtil match failed: ${it.message}")
            false
        }

    private fun Method.isShowToastShape(): Boolean =
        parameterTypes.size == 2 &&
            parameterTypes[0] == String::class.java &&
            parameterTypes[1] == Int::class.javaPrimitiveType

    private fun hasContextField(clazz: Class<*>): Boolean =
        clazz.declaredFields.any { Context::class.java.isAssignableFrom(it.type) }

    private fun hookShowToastMethod(module: XposedModule, method: Method) {
        if (!hookedToastMethods.add(method)) return
        method.isAccessible = true
        safeLog(module, Log.INFO, "hooking ${method.declaringClass.name}.${method.name}")
        runCatching {
            module.hook(method).intercept { chain ->
                if (!ConfigManager.getBoolean(KEY_OPTIMIZE_ISLAND_STYLE, true)) {
                    return@intercept chain.proceed()
                }
                val packageName = chain.args.getOrNull(0) as? String
                    ?: return@intercept chain.proceed()
                val type = chain.args.getOrNull(1) as? Int
                    ?: return@intercept chain.proceed()
                if (type != TYPE_ACCESS_CLIP_NOTIFICATION) return@intercept chain.proceed()

                val owner = chain.thisObject ?: return@intercept chain.proceed()
                val context = runCatching { contextOf(owner) }.getOrNull()
                    ?: return@intercept chain.proceed()
                if (!sendClipboardIsland(module, context, packageName)) {
                    return@intercept chain.proceed()
                }
                null
            }
        }.onFailure {
            hookedToastMethods.remove(method)
            safeLog(module, Log.WARN, "hook ${method.name} failed: ${it.message}")
        }
    }

    private fun contextOf(owner: Any): Context? {
        val clazz = owner.javaClass
        val field = clazz.declaredFields.firstOrNull {
            it.name == "mContext" && Context::class.java.isAssignableFrom(it.type)
        } ?: clazz.declaredFields.firstOrNull {
            Context::class.java.isAssignableFrom(it.type)
        } ?: return null
        field.isAccessible = true
        return field.get(owner) as? Context
    }

    private fun sendClipboardIsland(
        module: XposedModule,
        context: Context,
        packageName: String,
    ): Boolean = runCatching {
        val packageManager = context.packageManager
        val appName = runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString().trim()
        }.getOrNull().orEmpty().ifEmpty { packageName }
        val icon = packageManager.getAppIcon(packageName)
            ?: packageManager.getAppIcon(context.packageName)
            ?: packageManager.getAppIcon(SECURITY_CENTER_PACKAGE)
        val content = runCatching {
            context.createPackageContext(MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY)
                .getString(io.github.hyperisland.R.string.clipboard_read_content)
        }.getOrDefault("读取了剪贴板")
        IslandDispatcher.sendBroadcast(
            context,
            IslandRequest(
                title = appName,
                content = content,
                icon = icon,
                showNotification = false,
                preserveStatusBarSmallIcon = false,
                sourcePackage = packageName,
                firstFloat = false,
                enableFloat = false
            ),
        )
        true
    }.getOrElse {
        module.log(Log.ERROR, TAG, "clipboard island failed for $packageName: ${it.message}")
        false
    }

    private fun hookWindowAddView(module: XposedModule, classLoader: ClassLoader) {
        val clazz = Class.forName("android.view.WindowManagerImpl", false, classLoader)
        val methods = clazz.declaredMethods.filter { method ->
            method.name == "addView" &&
                method.parameterTypes.size == 2 &&
                View::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                ViewGroup.LayoutParams::class.java.isAssignableFrom(method.parameterTypes[1])
        }
        methods.forEach { method ->
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                val view = chain.args.getOrNull(0) as? View ?: return@intercept chain.proceed()
                val params = chain.args.getOrNull(1) as? WindowManager.LayoutParams
                    ?: return@intercept chain.proceed()
                if (params.type != WindowManager.LayoutParams.TYPE_SYSTEM_ALERT) {
                    return@intercept chain.proceed()
                }

                val messageView = view.findViewById<TextView>(android.R.id.message)
                val message = messageView?.text?.toString()?.trim().orEmpty()
                if (message.isEmpty()) return@intercept chain.proceed()

                if (ConfigManager.getBoolean(KEY_OPTIMIZE_ISLAND_STYLE, true)) {
                    val packageName = resolveReaderPackage(view.context, message)
                    if (packageName != null && sendClipboardIsland(module, view.context, packageName)) {
                        return@intercept null
                    }
                }

                Handler(Looper.getMainLooper()).post {
                    runCatching {
                        Toast.makeText(view.context, message, Toast.LENGTH_SHORT).show()
                    }.onSuccess {
                        log(module, "converted clipboard overlay: text=$message")
                    }.onFailure {
                        module.log(Log.ERROR, TAG, "window fallback failed: ${it.message}")
                    }
                }
                null
            }
        }
    }

    private fun resolveReaderPackage(context: Context, message: String): String? {
        val packageManager = context.packageManager
        return runCatching {
            packageManager.getInstalledApplications(0)
                .asSequence()
                .mapNotNull { appInfo ->
                    val label = runCatching {
                        packageManager.getApplicationLabel(appInfo).toString().trim()
                    }.getOrNull().orEmpty()
                    if (label.isEmpty() || !message.startsWith(label)) null
                    else appInfo.packageName to label.length
                }
                .maxByOrNull { it.second }
                ?.first
        }.getOrNull()
    }
}
