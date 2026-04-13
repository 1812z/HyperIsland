package io.github.hyperisland.xposed.hook

import android.content.Context
import android.os.SystemClock
import io.github.hyperisland.utils.getAppIcon
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import io.github.hyperisland.xposed.islanddispatch.IslandRequest
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.hyperisland.xposed.utils.toRounded
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

/**
 * Hook system_server 的标准 Toast 入队方法（enqueueTextToast），
 * 可按包名单独控制：
 * 1) 是否转发到 HyperIsland 代发（焦点通知 + 超级岛）
 * 2) 是否拦截原始 Toast
 */
object ToastInterceptHook : BaseHook() {

    private const val TAG = "HyperIsland[ToastIntercept]"
    private const val TARGET_CLASS = "com.android.server.notification.NotificationManagerService"
    private const val SELF_PKG = "io.github.hyperisland"
    private const val DEDUPE_WINDOW_MS = 1200L

    private data class ToastRule(
        val forwardEnabled: Boolean,
        val blockOriginal: Boolean,
    )

    private val cachedRules = mutableMapOf<String, ToastRule>()
    private val lastForwardAt = mutableMapOf<String, Long>()
    @Volatile private var hooked = false
    @Volatile private var firstHitLogged = false

    override fun getTag() = TAG

    override fun onConfigChanged() {
        cachedRules.clear()
    }

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (hooked) return
        val classLoader = param.defaultClassLoader
        log(module, "ToastIntercept init: pkg=${param.packageName}")
        val targetClass = try {
            classLoader.loadClass(TARGET_CLASS)
        } catch (e: Throwable) {
            logError(module, "target class not found: ${e.message}")
            return
        }

        val candidates = linkedSetOf<Class<*>>()
        candidates += targetClass
        targetClass.declaredClasses.forEach { candidates += it }

        var hookCount = 0
        val hookNames = setOf("enqueueTextToast", "enqueueToast")
        candidates.forEach { clazz ->
            clazz.declaredMethods
                .filter { it.name in hookNames && it.parameterTypes.any { p -> CharSequence::class.java.isAssignableFrom(p) } }
                .forEach { method ->
                    try {
                        module.hook(method).intercept { chain ->
                            val text = chain.args.firstOrNull { it is CharSequence } as? CharSequence
                            val normalizedText = text?.toString()?.trim().orEmpty()
                            if (normalizedText.isEmpty()) {
                                return@intercept chain.proceed()
                            }

                            val pkg = resolvePackageName(chain.args)
                            if (pkg.isBlank() || pkg == SELF_PKG) {
                                return@intercept chain.proceed()
                            }

                            if (!firstHitLogged) {
                                firstHitLogged = true
                                log(module, "toast hook first hit: method=${method.name}, pkg=$pkg")
                            }

                            val rule = loadRule(pkg)
                            if (!rule.forwardEnabled) {
                                return@intercept chain.proceed()
                            }

                            val dedupeKey = "$pkg|$normalizedText"
                            val now = SystemClock.elapsedRealtime()
                            val last = lastForwardAt[dedupeKey] ?: 0L
                            val shouldForward = (now - last) >= DEDUPE_WINDOW_MS
                            if (shouldForward) {
                                lastForwardAt[dedupeKey] = now
                                val context = resolveContext(chain.thisObject, classLoader)
                                if (context != null) {
                                    forwardAsIsland(pkg, normalizedText, context, module)
                                } else {
                                    logWarn(module, "skip forward: context unavailable for pkg=$pkg")
                                }
                            }

                            if (rule.blockOriginal) {
                                return@intercept null
                            }
                            chain.proceed()
                        }
                        hookCount++
                        log(module, "hooked ${clazz.name}#${method.name}(${method.parameterCount})")
                    } catch (e: Throwable) {
                        logError(module, "hook ${clazz.name}#${method.name} failed: ${e.message}")
                    }
                }
        }

        if (hookCount > 0) {
            hooked = true
            log(module, "hooked enqueueTextToast overloads: $hookCount")
        } else {
            logWarn(module, "no enqueueTextToast overloads found")
        }
    }

    private fun resolvePackageName(args: Iterable<Any?>): String {
        return args
            .filterIsInstance<String>()
            .firstOrNull { it.contains('.') && !it.contains(' ') }
            .orEmpty()
    }

    private fun resolveContext(host: Any?, classLoader: ClassLoader): Context? {
        HookUtils.getContext(classLoader)?.let { return it }
        val fromHost = host?.let { getFieldRecursively(it, "mContext") as? Context }
        if (fromHost != null) return fromHost
        val owner = host?.let { getFieldRecursively(it, "this$0") }
        return owner?.let { getFieldRecursively(it, "mContext") as? Context }
    }

    private fun getFieldRecursively(instance: Any, fieldName: String): Any? {
        var c: Class<*>? = instance.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(fieldName)
                f.isAccessible = true
                return f.get(instance)
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            } catch (_: Throwable) {
                return null
            }
        }
        return null
    }

    private fun loadRule(pkg: String): ToastRule {
        cachedRules[pkg]?.let { return it }
        val forward = ConfigManager.getBoolean("pref_toast_forward_$pkg", false)
        val block = forward && ConfigManager.getBoolean("pref_toast_block_$pkg", false)
        return ToastRule(forwardEnabled = forward, blockOriginal = block).also {
            cachedRules[pkg] = it
        }
    }

    private fun forwardAsIsland(
        pkg: String,
        text: String,
        context: Context,
        module: XposedModule,
    ) {
        try {
            val pm = context.packageManager
            val appName = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrElse { pkg }

            val icon = runCatching {
                pm.getAppIcon(pkg)?.toRounded(context)
            }.getOrNull()

            val timeout = ConfigManager.getInt("pref_channel_timeout_${pkg}_toast", 5)
                .coerceIn(3, 20)
            val firstFloat = ConfigManager.getBoolean("pref_default_first_float", false)
            val enableFloat = ConfigManager.getBoolean("pref_default_enable_float", false)
            val showNotification = ConfigManager.getBoolean("pref_default_focus_notif", true)
            val preserveSmallIcon = ConfigManager.getBoolean("pref_default_preserve_small_icon", false)
            val outerGlow = ConfigManager.getBoolean("pref_default_outer_glow", false)

            IslandDispatcher.post(
                context,
                IslandRequest(
                    title = appName,
                    content = text,
                    icon = icon,
                    timeoutSecs = timeout,
                    firstFloat = firstFloat,
                    enableFloat = enableFloat,
                    showNotification = showNotification,
                    preserveStatusBarSmallIcon = preserveSmallIcon,
                    outerGlow = outerGlow,
                ),
            )
            log(module, "toast forwarded: pkg=$pkg, text=$text")
        } catch (e: Throwable) {
            logError(module, "forward failed: ${e.message}")
        }
    }
}
