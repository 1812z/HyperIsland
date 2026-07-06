package io.github.hyperisland.xposed.hook.SystemUI

import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.hyperisland.xposed.commandtoken.CommandTokenConfig
import io.github.hyperisland.xposed.commandtoken.CommandTokenDispatcher
import io.github.hyperisland.xposed.commandtoken.TokenRecognizerRegistry
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.concurrent.ConcurrentHashMap

object CommandTokenHook : BaseHook() {

    private const val TAG = "HyperIsland[CommandToken]"

    @Volatile private var lastText: String = ""
    private val lastDispatchAt = ConcurrentHashMap<String, Long>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var clipboardManager: ClipboardManager? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var listenerRegistered = false
    @Volatile private var moduleRef: XposedModule? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val cm = clipboardManager ?: return@OnPrimaryClipChangedListener
        val ctx = appContext
        mainHandler.post { handleClipboardChange(ctx, cm) }
    }

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        moduleRef = module
        log(module, "CommandToken hook init, deferring clipboard registration to Application.onCreate")
        // SystemUI 进程在 onPackageLoaded 阶段 Application 尚未创建，
        // ActivityThread.currentApplication() 会返回 null（参考 ChargeIslandHook）。
        // 因此 hook Application.onCreate，在其中拿 Context 与 ClipboardManager。
        hookApplicationOnCreate(module, param.defaultClassLoader)
    }

    private fun hookApplicationOnCreate(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val method = classLoader.loadClass("android.app.Application").getDeclaredMethod("onCreate")
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? Context)?.let { ctx ->
                    onApplicationCreated(ctx, module)
                }
                result
            }
            log(module, "CommandToken: Application.onCreate hooked successfully")
        }.onFailure { error ->
            logError(module, "failed to hook Application.onCreate: ${error.message}")
        }
    }

    private fun onApplicationCreated(context: Context, module: XposedModule) {
        if (clipboardManager != null) {
            log(module, "CommandToken: onApplicationCreated already initialized, skip")
            return
        }
        val ctx = context.applicationContext ?: context
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (cm == null) {
            logWarn(module, "clipboard manager unavailable after Application.onCreate")
            return
        }
        appContext = ctx
        clipboardManager = cm
        log(module, "CommandToken clipboard manager ready")
        if (CommandTokenConfig.isEnabled()) {
            log(module, "CommandToken: enabled=true, registering listener now")
            registerListener(cm)
        } else {
            log(module, "CommandToken: enabled=false, listener deferred until enabled via config change")
        }
    }

    override fun onConfigChanged() {
        CommandTokenConfig.invalidateCache()
        val cm = clipboardManager
        val enabled = CommandTokenConfig.isEnabled()
        moduleRef?.let { log(it, "CommandToken: onConfigChanged enabled=$enabled listenerRegistered=$listenerRegistered cmReady=${cm != null}") }
        if (cm == null) return
        if (enabled) {
            if (!listenerRegistered) registerListener(cm)
        } else {
            if (listenerRegistered) {
                cm.removePrimaryClipChangedListener(clipListener)
                listenerRegistered = false
                lastText = ""
                moduleRef?.let { log(it, "CommandToken: listener unregistered") }
            }
        }
    }

    private fun registerListener(cm: ClipboardManager) {
        if (listenerRegistered) {
            moduleRef?.let { log(it, "CommandToken: registerListener already registered, skip") }
            return
        }
        cm.addPrimaryClipChangedListener(clipListener)
        listenerRegistered = true
        moduleRef?.let { log(it, "CommandToken clipboard listener registered") }
    }

    private fun handleClipboardChange(context: Context?, cm: ClipboardManager) {
        if (context == null) return
        val text = runCatching {
            cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
        }.getOrNull()
        if (text.isNullOrEmpty()) return
        if (text == lastText) return
        lastText = text
        if (!CommandTokenConfig.isEnabled()) return
        val now = SystemClock.elapsedRealtime()
        val last = lastDispatchAt[text] ?: 0L
        val dedupMs = CommandTokenConfig.dedupWindowSeconds() * 1000L
        if (now - last < dedupMs) return
        lastDispatchAt[text] = now
        for (recognizer in TokenRecognizerRegistry.all()) {
            if (!CommandTokenConfig.isRecognizerEnabled(recognizer.recognizerId)) continue
            val match = recognizer.match(text)
            if (match != null) {
                CommandTokenDispatcher.dispatch(context, match)
                break
            }
        }
    }
}
