package io.github.hyperisland.xposed.hook

import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import io.github.hyperisland.xposed.islanddispatch.definition.IslandRequest
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.concurrent.ConcurrentHashMap

object KeepIslandHook : BaseHook() {

    private const val TAG = "HyperIsland[KeepIsland]"
    private const val PREF_KEY = "pref_keep_island"

    private const val PREF_KEY_AUTO_HIDE = "pref_keep_island_auto_hide"

    private const val PREF_KEY_HIGHLIGHT_COLOR = "pref_keep_island_highlight_color"

    private const val KEEP_ISLAND_NOTIF_ID = 0x4B494B49

    private const val ANIMATION_CONTROLLER_CLASS =
        "miui.systemui.dynamicisland.anim.DynamicIslandAnimationController"

    private const val KEEP_ISLAND_CHANNEL = "keep_island"

    private const val RESTORE_DELAY_MS = 150L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: android.content.Context? = null
    private var posted = false
    private var suppressed = false

    private var cachedModule: XposedModule? = null

    private val activeRealKeys = ConcurrentHashMap.newKeySet<String>()

    private var restoreRunnable: Runnable? = null

    private val hookedAnimationClassLoaders = ConcurrentHashMap.newKeySet<Int>()

    override fun getTag() = TAG

    override fun onConfigChanged() {
        val autoHide = ConfigManager.getBoolean(PREF_KEY_AUTO_HIDE, true)
        if (!autoHide && suppressed) {
            activeRealKeys.clear()
            cancelPendingRestore()
            val ctx = appContext
            if (ctx != null) postKeepIsland(ctx, restore = true)
        }
        mainHandler.postDelayed({ evaluateKeepIsland() }, 500)
    }

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        cachedModule = module
        log(module, "onInit pkg=${param.packageName}")
        hookApplicationOnCreate(module, param)
        hookAnimationController(module, param.defaultClassLoader)
        hookDynamicClassLoaders(module)
    }

    private fun hookApplicationOnCreate(module: XposedModule, param: PackageLoadedParam) {
        try {
            val method = param.defaultClassLoader
                .loadClass("android.app.Application")
                .getDeclaredMethod("onCreate")
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val app = chain.thisObject as? Application
                if (app != null) {
                    appContext = app.applicationContext
                    mainHandler.postDelayed({ evaluateKeepIsland() }, 3000)
                }
                result
            }
            log(module, "hooked Application.onCreate")
        } catch (e: Throwable) {
            logError(module, "Application.onCreate hook failed: ${e.message}")
        }
    }

    private fun hookDynamicClassLoaders(module: XposedModule) {
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { cl ->
            hookAnimationController(module, cl)
        }
    }

    private fun hookAnimationController(module: XposedModule, classLoader: ClassLoader) {
        val clId = System.identityHashCode(classLoader)
        if (!hookedAnimationClassLoaders.add(clId)) return
        try {
            val clazz = try {
                classLoader.loadClass(ANIMATION_CONTROLLER_CLASS)
            } catch (_: ClassNotFoundException) {
                hookedAnimationClassLoaders.remove(clId)
                //log(module, "animation controller not found in cl=$clId, skipped")
                return
            }
            val methods = clazz.declaredMethods.filter {
                it.name == "onStateChange" && it.parameterCount >= 1
            }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val stateObj = chain.args.getOrNull(0)
                    if (stateObj != null) {
                        handleStateChange(stateObj)
                    }
                    result
                }
            }
            log(module, "hooked onStateChange (cl=$clId, methods=${methods.size})")
        } catch (e: Throwable) {
            hookedAnimationClassLoaders.remove(clId)
            logError(module, "animation controller hook failed cl=$clId: ${e.message}")
        }
    }

    private fun handleStateChange(stateObj: Any) {
        val ctx = appContext ?: return
        val enabled = ConfigManager.getBoolean(PREF_KEY, false)
        if (!enabled) return
        if (!posted && !suppressed) return

        val autoHide = ConfigManager.getBoolean(PREF_KEY_AUTO_HIDE, true)
        if (!autoHide) return

        val stateText = readStateText(stateObj) ?: return

        val extras = extractExtrasFromState(stateObj)
        val sourceChannel = extras?.getString("hyperisland_source_channel")
        val isOwnedByUs = sourceChannel == KEEP_ISLAND_CHANNEL

        val key = extractKeyFromState(stateObj)

        val isBigIsland = stateText.contains("BigIsland")
        val isExpanded = stateText.contains("Expand")
        val isDeleted = stateText.contains("Deleted")

        if (isOwnedByUs) return

        when {
            (isBigIsland || isExpanded) && !isDeleted -> {
                cancelPendingRestore()
                if (key != null) activeRealKeys.add(key)
                if (posted && !suppressed) {
                    cancelKeepIsland(ctx, suppress = true)
                }
            }

            isDeleted || (!isBigIsland && !isExpanded) -> {
                if (key != null) activeRealKeys.remove(key)
                if (suppressed && activeRealKeys.isEmpty()) {
                    scheduleRestore(ctx)
                }
            }
        }
    }

    private fun evaluateKeepIsland() {
        val ctx = appContext ?: return
        val enabled = ConfigManager.getBoolean(PREF_KEY, false)
        if (enabled && !posted && !suppressed) {
            activeRealKeys.clear()
            cancelPendingRestore()
            postKeepIsland(ctx, restore = false)
        } else if (!enabled && (posted || suppressed)) {
            activeRealKeys.clear()
            cancelPendingRestore()
            cancelKeepIsland(ctx, suppress = false)
        }
    }

    private fun postKeepIsland(context: android.content.Context, restore: Boolean) {
        try {
            val highlightColor = ConfigManager.getString(PREF_KEY_HIGHLIGHT_COLOR, "")
                .takeIf { it.isNotBlank() }
            val request = IslandRequest(
                title = " ",
                content = "",
                icon = null,
                notifId = KEEP_ISLAND_NOTIF_ID,
                timeoutSecs = Int.MAX_VALUE,
                firstFloat = false,
                enableFloat = false,
                showNotification = false,
                preserveStatusBarSmallIcon = false,
                isOngoing = true,
                showIslandIcon = false,
                clearBeforePost = true,
                sourcePackage = "io.github.hyperisland",
                sourceChannelId = KEEP_ISLAND_CHANNEL,
                highlightColor = highlightColor,
                showLeftHighlightColor = highlightColor != null,
                showRightHighlightColor = highlightColor != null,
            )
            IslandDispatcher.post(context, request)
            posted = true
            suppressed = false
            cachedModule?.let { log(it, "keep island ${if (restore) "restored" else "posted"}") }
        } catch (e: Exception) {
            cachedModule?.let { logError(it, "keep island post failed: ${e.message}") }
        }
    }

    private fun cancelKeepIsland(context: android.content.Context, suppress: Boolean) {
        try {
            IslandDispatcher.cancel(context, KEEP_ISLAND_NOTIF_ID)
            posted = false
            suppressed = suppress
            cachedModule?.let { log(it, "keep island ${if (suppress) "suppressed" else "cancelled"}") }
        } catch (e: Exception) {
            cachedModule?.let { logError(it, "keep island cancel failed: ${e.message}") }
        }
    }

    private fun scheduleRestore(ctx: android.content.Context) {
        cancelPendingRestore()
        restoreRunnable = Runnable {
            if (suppressed && activeRealKeys.isEmpty()) {
                postKeepIsland(ctx, restore = true)
            }
        }
        mainHandler.postDelayed(restoreRunnable!!, RESTORE_DELAY_MS)
    }

    private fun cancelPendingRestore() {
        restoreRunnable?.let { mainHandler.removeCallbacks(it) }
        restoreRunnable = null
    }

    private fun extractKeyFromState(stateObj: Any): String? {
        val dataObj = extractDynamicData(stateObj)
        if (dataObj != null) {
            invokeNoArg(dataObj, "getKey")?.let { return it as? String }
        }
        val extras = extractExtrasFromState(stateObj)
        extras?.getString("key")?.let { return it }
        extras?.getString("miui.notif.key")?.let { return it }
        return null
    }

    private fun readStateText(stateObj: Any?): String? {
        return invokeNoArg(stateObj ?: return null, "getState")?.toString()
    }

    private fun extractExtrasFromState(stateObj: Any): Bundle? {
        val dataObj = extractDynamicData(stateObj)
        if (dataObj == null) {
            invokeNoArg(stateObj, "getExtras")?.let { if (it is Bundle) return it }
            readFieldValue(stateObj, "extras")?.let { if (it is Bundle) return it }
            readFieldValue(stateObj, "mExtras")?.let { if (it is Bundle) return it }
            return null
        }
        invokeNoArg(dataObj, "getExtras")?.let { if (it is Bundle) return it }
        readFieldValue(dataObj, "extras")?.let { if (it is Bundle) return it }
        readFieldValue(dataObj, "mExtras")?.let { if (it is Bundle) return it }
        return null
    }

    private fun extractDynamicData(stateObj: Any): Any? {
        listOf("getCurrentIslandData", "getIslandData", "getData").forEach { name ->
            invokeNoArg(stateObj, name)?.let { return it }
        }
        return null
    }

    private fun invokeNoArg(target: Any, methodName: String): Any? {
        return runCatching {
            val method = findNoArgMethod(target.javaClass, methodName) ?: return null
            method.isAccessible = true
            method.invoke(target)
        }.getOrNull()
    }

    private fun findNoArgMethod(clazz: Class<*>, name: String): java.lang.reflect.Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods.firstOrNull { it.name == name && it.parameterCount == 0 }
                ?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun readFieldValue(instance: Any, fieldName: String): Any? {
        var current: Class<*>? = instance.javaClass
        while (current != null) {
            try {
                val field = current.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(instance)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }
}