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
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.concurrent.ConcurrentHashMap

object ClipboardToastHook : BaseHook() {
    private const val TAG = "HyperIsland[ClipboardToast]"
    private const val SECURITY_CENTER_PACKAGE = "com.miui.securitycenter"
    private const val MODULE_PACKAGE = "io.github.hyperisland"
    private const val TYPE_ACCESS_CLIP_NOTIFICATION = 1
    private const val KEY_OPTIMIZE_ISLAND_STYLE = "pref_clipboard_optimize_island_style"
    private const val TOAST_UTIL_CLASS = "com.hyperos.security.utility.ToastUtil"

    private val hookedToastUtilClasses = ConcurrentHashMap.newKeySet<Class<*>>()

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        module.log(Log.INFO, TAG, "initializing in ${param.packageName}")
        hookSecurityCenterToast(module, param.defaultClassLoader)
        hookApplicationAttach(module)
        HookUtils.hookDynamicClassLoaders(
            module,
            ClassLoader.getSystemClassLoader(),
        ) { classLoader ->
            hookSecurityCenterToast(module, classLoader)
        }
        hookWindowAddView(module, param.defaultClassLoader)
    }

    private fun hookApplicationAttach(module: XposedModule) {
        val method = Application::class.java.getDeclaredMethod(
            "attach",
            Context::class.java,
        ).apply { isAccessible = true }
        module.hook(method).intercept { chain ->
            val result = chain.proceed()
            val context = chain.args.firstOrNull() as? Context ?: return@intercept result
            hookSecurityCenterToast(module, context.classLoader)
            result
        }
    }

    private fun hookSecurityCenterToast(module: XposedModule, classLoader: ClassLoader) {
        val clazz = runCatching {
            Class.forName(TOAST_UTIL_CLASS, false, classLoader)
        }.getOrNull() ?: return
        if (!hookedToastUtilClasses.add(clazz)) return
        val methods = clazz.declaredMethods.filter { method ->
            method.name == "showToast" &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType
        }
        val contextField = runCatching {
            clazz.getDeclaredField("mContext").apply { isAccessible = true }
        }.getOrElse {
            hookedToastUtilClasses.remove(clazz)
            module.log(Log.WARN, TAG, "ToastUtil context unavailable: ${it.message}")
            return
        }
        if (methods.isEmpty()) {
            hookedToastUtilClasses.remove(clazz)
            module.log(Log.WARN, TAG, "ToastUtil showToast method unavailable")
            return
        }
        //module.log(Log.INFO, TAG, "ToastUtil showToast candidates=${methods.size}")
        methods.forEach { method ->
            method.isAccessible = true
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
                val context = runCatching { contextField.get(owner) as? Context }
                    .getOrNull() ?: return@intercept chain.proceed()
                if (!sendClipboardIsland(module, context, packageName)) {
                    return@intercept chain.proceed()
                }
                null
            }
        }
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
        //log(module, "sent clipboard island: package=$packageName, app=$appName")
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
        //module.log(Log.INFO, TAG, "WindowManagerImpl addView candidates=${methods.size}")
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
