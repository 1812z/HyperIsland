package io.github.hyperisland.xposed.hook

import android.content.Context
import android.os.SystemClock
import io.github.hyperisland.utils.getAppIcon
import io.github.hyperisland.utils.resolveDynamicHighlightColor
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import io.github.hyperisland.xposed.islanddispatch.IslandRequest
import io.github.hyperisland.xposed.utils.SceneBehavior
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.hyperisland.xposed.utils.toRounded
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.concurrent.ConcurrentHashMap

/**
 * 在 SystemUI 的 ToastUI 层拦截标准文本 Toast，并按应用配置转发到超级岛。
 * 仅处理 CharSequence 文本 Toast，自定义视图 Toast 会被忽略。
 */
object ToastUiInterceptHook : BaseHook() {

    private const val TAG = "HyperIsland[ToastUIIntercept]"
    private const val TARGET_TOAST_UI_CLASS = "com.android.systemui.toast.ToastUI"
    private const val TARGET_COMMAND_QUEUE_CLASS = "com.android.systemui.statusbar.CommandQueue"
    private const val SELF_PKG = "io.github.hyperisland"
    private const val DEDUPE_WINDOW_MS = 1200L

    private data class ToastRule(
        val forwardEnabled: Boolean,
        val blockOriginal: Boolean,
        val showNotification: Boolean,
        val showIslandIcon: Boolean,
        val firstFloat: Boolean,
        val enableFloat: Boolean,
        val timeoutSecs: Int,
        val overrideMarqueeTimeout: Boolean,
        val highlightColor: String?,
        val dynamicHighlightMode: String,
        val showLeftHighlightColor: Boolean,
        val showRightHighlightColor: Boolean,
        val outerGlowMode: String,
        val islandOuterGlowMode: String,
        val islandOuterGlowColor: String?,
        val outEffectColor: String?,
        val filterMode: String,
        val whitelistKeywords: List<String>,
        val blacklistKeywords: List<String>,
    )

    private val cachedRules = ConcurrentHashMap<String, ToastRule>()
    private val lastForwardAt = ConcurrentHashMap<String, Long>()
    private val hookedClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    @Volatile private var hooked = false

    override fun getTag() = TAG

    override fun onConfigChanged() {
        cachedRules.clear()
    }

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        val cl = param.defaultClassLoader
        val clId = System.identityHashCode(cl)
        if (hookedClassLoaders.contains(clId)) return
        hookedClassLoaders.add(clId)
        log(module, "ToastUI intercept init")

        var total = 0
        total += hookCommandQueue(module, cl)
        total += hookToastUi(module, cl)

        hooked = hooked || total > 0
        if (total == 0) logWarn(module, "no toast methods hooked in SystemUI")
    }

    private fun hookCommandQueue(module: XposedModule, classLoader: ClassLoader): Int {
        val clazz = try {
            classLoader.loadClass(TARGET_COMMAND_QUEUE_CLASS)
        } catch (e: Throwable) {
            logWarn(module, "CommandQueue not found: ${e.message}")
            return 0
        }
        var count = 0
        clazz.declaredMethods
            .filter { it.name == "showToast" && it.parameterTypes.any { p -> CharSequence::class.java.isAssignableFrom(p) } }
            .forEach { method ->
                try {
                    module.hook(method).intercept { chain ->
                        val pkg = chain.args.getOrNull(1) as? String ?: ""
                        val text = chain.args.getOrNull(3) as? CharSequence
                        val handled = handleToastEvent(module, classLoader, chain.thisObject, pkg, text)
                        if (handled) null else chain.proceed()
                    }
                    count++
                    log(module, "hooked CommandQueue#showToast(${method.parameterCount})")
                } catch (e: Throwable) {
                    logError(module, "hook CommandQueue#showToast failed: ${e.message}")
                }
            }
        return count
    }

    private fun hookToastUi(module: XposedModule, classLoader: ClassLoader): Int {
        val clazz = try {
            classLoader.loadClass(TARGET_TOAST_UI_CLASS)
        } catch (e: Throwable) {
            logWarn(module, "ToastUI not found: ${e.message}")
            return 0
        }
        var count = 0
        clazz.declaredMethods
            .filter { it.name == "showToast" && it.parameterTypes.any { p -> CharSequence::class.java.isAssignableFrom(p) } }
            .forEach { method ->
                try {
                    module.hook(method).intercept { chain ->
                        val pkg = chain.args.getOrNull(1) as? String ?: ""
                        val text = chain.args.getOrNull(3) as? CharSequence
                        val handled = handleToastEvent(module, classLoader, chain.thisObject, pkg, text)
                        if (handled) null else chain.proceed()
                    }
                    count++
                    log(module, "hooked ToastUI#showToast(${method.parameterCount})")
                } catch (e: Throwable) {
                    logError(module, "hook ToastUI#showToast failed: ${e.message}")
                }
            }
        return count
    }

    private fun handleToastEvent(
        module: XposedModule,
        classLoader: ClassLoader,
        host: Any?,
        pkg: String,
        text: CharSequence?,
    ): Boolean {
        val normalizedText = text?.toString()?.trim().orEmpty()
        if (pkg.isBlank() || normalizedText.isEmpty() || pkg == SELF_PKG) return false

        val rule = loadRule(pkg)

        // 过滤逻辑：拦截和转发共用，不在黑名单/不在白名单的不处理
        val hasKeywords = rule.whitelistKeywords.isNotEmpty() || rule.blacklistKeywords.isNotEmpty()
        val shouldFilter = (rule.blockOriginal || rule.forwardEnabled) && hasKeywords
        if (shouldFilter && !shouldForwardText(normalizedText, rule)) {
            return true  // 匹配过滤规则，拦截此 toast
        }

        if (!rule.forwardEnabled) {
            // 只开拦截：只有匹配黑名单的才拦截（上面已 return true），其余一律放行
            return false
        }

        val dedupeKey = "$pkg|$normalizedText"
        val now = SystemClock.elapsedRealtime()
        val last = lastForwardAt[dedupeKey] ?: 0L
        if (now - last >= DEDUPE_WINDOW_MS) {
            lastForwardAt[dedupeKey] = now
            val context = resolveContext(host, classLoader)
            if (context != null) {
                forwardAsIsland(context, pkg, normalizedText, rule, module)
            } else {
                logWarn(module, "skip toast forward: context unavailable")
            }
        }
        return rule.blockOriginal
    }

    private fun resolveContext(host: Any?, classLoader: ClassLoader): Context? {
        HookUtils.getContext(classLoader)?.let { return it }
        val direct = host?.let { getFieldRecursively(it, "mContext") as? Context }
        if (direct != null) return direct
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
        val block = ConfigManager.getBoolean("pref_toast_block_$pkg", false)
        val showNotification = ConfigManager.getBoolean(
            "pref_toast_show_notification_$pkg",
            false,
        )
        val showIslandIcon = ConfigManager.getBoolean(
            "pref_toast_show_island_icon_$pkg",
            true,
        )
        val defaultFirstFloat = ConfigManager.getBoolean("pref_default_first_float", false)
        val defaultEnableFloat = ConfigManager.getBoolean("pref_default_enable_float", false)
        val defaultDynamicHighlightColor = ConfigManager.getBoolean(
            "pref_default_dynamic_highlight_color",
            false,
        )
        val defaultOuterGlow = ConfigManager.getString("pref_default_outer_glow", "off")
        val defaultIslandOuterGlow = ConfigManager.getString("pref_default_island_outer_glow", "off")

        val firstFloat = resolveTriOpt(
            ConfigManager.getString("pref_toast_first_float_$pkg", "default"),
            defaultFirstFloat,
        )
        val enableFloat = resolveTriOpt(
            ConfigManager.getString("pref_toast_enable_float_$pkg", "default"),
            defaultEnableFloat,
        )
        val clampedTimeout = ConfigManager.getString("pref_toast_timeout_$pkg", "5")
            .toIntOrNull()
            ?.coerceIn(1, 20)
            ?: 5
        val marqueeEnabled = resolveTriOpt(
            ConfigManager.getString("pref_toast_marquee_$pkg", "default"),
            ConfigManager.getBoolean("pref_default_marquee", false),
        )
        val marqueeAutoHide = ConfigManager.getString(
            "pref_toast_marquee_auto_hide_$pkg",
            "default",
        ).let { value ->
            if (value == "default") {
                ConfigManager.getString("pref_default_marquee_auto_hide", "off")
            } else {
                value
            }
        }

        val manualHighlightColor = ConfigManager.getString("pref_toast_highlight_color_$pkg", "")
            .trim()
            .ifBlank { null }
        val dynamicHighlightRaw = ConfigManager.getString(
            "pref_toast_dynamic_highlight_color_$pkg",
            "default",
        )
        val dynamicHighlightMode = when (dynamicHighlightRaw) {
            "on", "off", "dark", "darker" -> dynamicHighlightRaw
            else -> if (defaultDynamicHighlightColor) "on" else "off"
        }
        val showLeftHighlight = ConfigManager.getString(
            "pref_toast_show_left_highlight_$pkg",
            "off",
        ) == "on"
        val showRightHighlight = ConfigManager.getString(
            "pref_toast_show_right_highlight_$pkg",
            "off",
        ) == "on"

        val outerGlowMode = resolveGlowMode(
            ConfigManager.getString("pref_toast_outer_glow_$pkg", "default"),
            defaultOuterGlow,
        )
        val islandOuterGlowMode = resolveGlowMode(
            ConfigManager.getString("pref_toast_island_outer_glow_$pkg", "default"),
            defaultIslandOuterGlow,
        )
        val outEffectColor = ConfigManager.getString("pref_toast_out_effect_color_$pkg", "")
            .trim()
            .ifBlank { null }
        val islandOuterGlowColor = ConfigManager
            .getString("pref_toast_island_outer_glow_color_$pkg", "")
            .trim()
            .ifBlank { null }
        val filterMode = when (ConfigManager.getString("pref_toast_filter_mode_$pkg", "blacklist")) {
            "whitelist" -> "whitelist"
            else -> "blacklist"
        }
        val whitelistKeywords = parseKeywords(
            ConfigManager.getString("pref_toast_filter_whitelist_keywords_$pkg", "")
        )
        val blacklistKeywords = parseKeywords(
            ConfigManager.getString("pref_toast_filter_blacklist_keywords_$pkg", "")
        )
        return ToastRule(
            forwardEnabled = forward,
            blockOriginal = block,
            showNotification = showNotification,
            showIslandIcon = showIslandIcon,
            firstFloat = firstFloat,
            enableFloat = enableFloat,
            timeoutSecs = clampedTimeout,
            overrideMarqueeTimeout = marqueeEnabled &&
                marqueeAutoHide in setOf("1_override", "2_override"),
            highlightColor = manualHighlightColor,
            dynamicHighlightMode = dynamicHighlightMode,
            showLeftHighlightColor = showLeftHighlight,
            showRightHighlightColor = showRightHighlight,
            outerGlowMode = outerGlowMode,
            islandOuterGlowMode = islandOuterGlowMode,
            islandOuterGlowColor = islandOuterGlowColor,
            outEffectColor = outEffectColor,
            filterMode = filterMode,
            whitelistKeywords = whitelistKeywords,
            blacklistKeywords = blacklistKeywords,
        ).also {
            cachedRules[pkg] = it
        }
    }

    private fun resolveGlowMode(value: String?, defaultValue: String): String {
        return when (value?.trim()?.lowercase()) {
            "on", "off", "follow_dynamic" -> value.trim().lowercase()
            else -> defaultValue.trim().lowercase()
        }
    }

    private fun resolveTriOpt(value: String?, defaultValue: Boolean): Boolean {
        return when (value?.trim()?.lowercase()) {
            "on" -> true
            "off" -> false
            else -> defaultValue
        }
    }

    private fun parseKeywords(raw: String): List<String> {
        return raw.splitToSequence('\n', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    private fun shouldForwardText(text: String, rule: ToastRule): Boolean {
        if (rule.whitelistKeywords.isEmpty() && rule.blacklistKeywords.isEmpty()) return true
        if (rule.blacklistKeywords.any { text.contains(it, ignoreCase = true) }) return false
        if (rule.filterMode == "whitelist") {
            return rule.whitelistKeywords.any { text.contains(it, ignoreCase = true) }
        }
        return true
    }

    private fun resolveHighlightColor(
        context: Context?,
        icon: android.graphics.drawable.Icon?,
        manualHighlightColor: String?,
        dynamicMode: String,
    ): String? {
        val mode = dynamicMode.trim().lowercase()
        if (mode != "on" && mode != "dark" && mode != "darker") {
            return manualHighlightColor
        }
        val source = icon ?: return manualHighlightColor
        val safeContext = context ?: return manualHighlightColor
        return source.resolveDynamicHighlightColor(safeContext, mode)
            ?: manualHighlightColor
    }

    private fun forwardAsIsland(
        context: Context,
        pkg: String,
        text: String,
        rule: ToastRule,
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

            val resolvedHighlightColor = resolveHighlightColor(
                context = context,
                icon = icon,
                manualHighlightColor = rule.highlightColor,
                dynamicMode = rule.dynamicHighlightMode,
            )
            val resolvedOutEffectColor = when (rule.outerGlowMode) {
                "follow_dynamic" -> resolvedHighlightColor
                else -> rule.outEffectColor
            }
            val resolvedIslandOuterGlowColor = when (rule.islandOuterGlowMode) {
                "follow_dynamic" -> resolvedHighlightColor
                else -> rule.islandOuterGlowColor
            }

            val sceneDecision = SceneBehavior.resolve(
                context = context,
                surface = SceneBehavior.Surface.TOAST,
                sourcePackage = pkg,
            )
            if (sceneDecision.shouldSuppress) return

            IslandDispatcher.post(
                context,
                IslandRequest(
                    title = appName,
                    content = text,
                    icon = icon,
                    timeoutSecs = if (rule.overrideMarqueeTimeout) Int.MAX_VALUE else rule.timeoutSecs,
                    firstFloat = sceneDecision.applyToBoolean(rule.firstFloat),
                    enableFloat = sceneDecision.applyToBoolean(rule.enableFloat),
                    showNotification = rule.showNotification,
                    showIslandIcon = rule.showIslandIcon,
                    preserveStatusBarSmallIcon = false,
                    highlightColor = resolvedHighlightColor,
                    showLeftHighlightColor = rule.showLeftHighlightColor,
                    showRightHighlightColor = rule.showRightHighlightColor,
                    outerGlow = rule.outerGlowMode != "off",
                    islandOuterGlow = rule.islandOuterGlowMode != "off",
                    islandOuterGlowColor = resolvedIslandOuterGlowColor,
                    outEffectColor = resolvedOutEffectColor,
                    sourcePackage = pkg,
                    sourceChannelId = "toast",
                ),
            )
            //log(module, "toast forwarded in SystemUI: pkg=$pkg")
        } catch (e: Throwable) {
            logError(module, "forward in SystemUI failed: ${e.message}")
        }
    }
}
