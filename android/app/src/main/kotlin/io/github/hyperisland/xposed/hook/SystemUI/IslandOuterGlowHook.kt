package io.github.hyperisland.xposed.hook

import android.graphics.Color
import android.os.Bundle
import io.github.hyperisland.utils.getAppIcon
import io.github.hyperisland.utils.resolveDynamicHighlightColor
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

object IslandOuterGlowHook : BaseHook() {

    private const val TAG = "HyperIsland[IslandOuterGlow]"
    private const val OWNER_KEY = "hyperisland.owner"
    private const val OWNER_VALUE = "io.github.hyperisland"
    private const val BIG_EFFECT_KEY = "miui.bigIsland.effect.src"
    private const val EFFECT_KEY = "miui.effect.src"
    private const val EFFECT_VALUE = "outer_glow"

    private const val FEATURE_CONFIG_CLASS = "miui.systemui.dynamicisland.DynamicFeatureConfig"
    private const val ANIMATION_CONTROLLER_CLASS = "miui.systemui.dynamicisland.anim.DynamicIslandAnimationController"
    private const val GLOW_VIEW_CLASS = "miui.systemui.dynamicisland.view.DynamicGlowEffectView"
    private const val FOCUS_CONTROLLER_CLASS = "miui.systemui.notification.focus.FocusNotificationController"
    private const val AVOID_BURN_IN_HELPER_CLASS = "miui.systemui.dynamicisland.display.AvoidScreenBurnInHelper"
    private const val LIGHT_BG_SHADER_FIELD = "U_LIGHT_COLORS"
    private const val BIG_VIEW_MARKER = "DynamicIslandBigIslandView"
    private const val EXPANDED_VIEW_MARKER = "DynamicIslandExpandedView"
    private const val LIGHT_COLOR_ARRAY_SIZE = 33
    private const val RECENT_TTL_MS = 2500L

    private const val GLOW_MODE_AUTO = 0
    private const val GLOW_MODE_STATUS = 1
    private const val GLOW_MODE_EXPAND = 2

    private data class GlowConfig(
        val effectEnabled: Boolean,
        val colorArgb: Int?,
    )

    private data class OwnedGlowTarget(
        val pkg: String,
        val channelId: String,
        val mode: Int,
        val focusGlowEnabled: Boolean,
        val islandGlowEnabled: Boolean,
        val focusOutEffectColor: String?,
        val islandOuterGlowColor: String?,
        val forcedGlobal: Boolean = false,
        val createdAt: Long,
    )

    private data class MediaGlowRequest(
        val pkg: String,
        val notificationKey: String?,
        val islandEnabled: Boolean,
        val focusEnabled: Boolean,
        val islandColor: String?,
        val focusColor: String?,
        val updatedAt: Long,
    )

    private val hookedGlowClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val hookedAnimationClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val hookedFeatureClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val hookedFocusClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val hookedAvoidBurnInClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val defaultShaderColors = WeakHashMap<Class<*>, FloatArray>()
    private val glowTargets = WeakHashMap<Any, OwnedGlowTarget>()
    private val defaultGlowRanges = WeakHashMap<Any, Float>()
    private val mediaGlowRequests = ConcurrentHashMap<String, MediaGlowRequest>()

    @Volatile private var recentOwnedTarget: OwnedGlowTarget? = null
    @Volatile private var statusGlowShowing = false

    fun recordMediaGlowRequest(
        pkg: String,
        notificationKey: String?,
        islandEnabled: Boolean,
        focusEnabled: Boolean,
        islandColor: String?,
        focusColor: String?,
        module: XposedModule,
    ) {
        val request = MediaGlowRequest(
            pkg = pkg,
            notificationKey = notificationKey,
            islandEnabled = islandEnabled,
            focusEnabled = focusEnabled,
            islandColor = islandColor,
            focusColor = focusColor,
            updatedAt = System.currentTimeMillis(),
        )
        if (islandEnabled || focusEnabled) {
            mediaGlowRequests[pkg] = request
        } else {
            mediaGlowRequests.remove(pkg)
        }
        log(module, "media glow recorded: pkg=$pkg island=$islandEnabled focus=$focusEnabled")
    }

    fun removeMediaGlowRequest(pkg: String, notificationKey: String?) {
        mediaGlowRequests.computeIfPresent(pkg) { _, request ->
            request.takeUnless { notificationKey == null || request.notificationKey == notificationKey }
        }
    }

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        hookDynamicClassLoaders(module)
        hookFeatureConfig(module, param.defaultClassLoader)
        hookAnimationController(module, param.defaultClassLoader)
        hookGlowView(module, param.defaultClassLoader)
        hookFocusExtrasBridge(module, param.defaultClassLoader)
        hookAvoidBurnInHelper(module, param.defaultClassLoader)
    }

    override fun onConfigChanged() {
        synchronized(defaultShaderColors) {
            defaultShaderColors.clear()
        }
    }

    private fun hookDynamicClassLoaders(module: XposedModule) {
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { cl ->
            hookFeatureConfig(module, cl)
            hookAnimationController(module, cl)
            hookGlowView(module, cl)
            hookFocusExtrasBridge(module, cl)
            hookAvoidBurnInHelper(module, cl)
        }
    }

    private fun hookAvoidBurnInHelper(module: XposedModule, classLoader: ClassLoader) {
        val clId = System.identityHashCode(classLoader)
        if (!hookedAvoidBurnInClassLoaders.add(clId)) return
        try {
            val clazz = classLoader.loadClass(AVOID_BURN_IN_HELPER_CLASS)
            val methods = clazz.declaredMethods.filter {
                it.name == "updateViewForAvoidingScreenBurnIn" && it.parameterCount == 2
            }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val mode = resolveGlowModeFromContentView(chain.args.getOrNull(0))
                    if (mode == GLOW_MODE_STATUS && statusGlowShowing) {
                        null
                    } else {
                        chain.proceed()
                    }
                }
            }
            if (methods.isNotEmpty()) log(module, "hooked dynamic island avoid-screen-burn-in translation on ${clazz.name}")
        } catch (_: Throwable) {
        }
    }

    private fun hookFeatureConfig(module: XposedModule, classLoader: ClassLoader) {
        val clId = System.identityHashCode(classLoader)
        if (!hookedFeatureClassLoaders.add(clId)) return
        try {
            val clazz = classLoader.loadClass(FEATURE_CONFIG_CLASS)
            val method = clazz.declaredMethods.firstOrNull {
                it.name == "getFEATURE_DYNAMIC_ISLAND_SHADER" && it.parameterCount == 0
            } ?: return
            module.hook(method).intercept { true }
            log(module, "hooked shader feature flag on ${clazz.name}")
        } catch (_: Throwable) {
        }
    }

    private fun hookAnimationController(module: XposedModule, classLoader: ClassLoader) {
        val clId = System.identityHashCode(classLoader)
        if (!hookedAnimationClassLoaders.add(clId)) return
        try {
            val clazz = classLoader.loadClass(ANIMATION_CONTROLLER_CLASS)
            val methods = clazz.declaredMethods.filter { it.name == "onStateChange" && it.parameterCount >= 1 }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val stateObj = chain.args.getOrNull(0)
                    val mode = resolveStrictGlowMode(stateObj)
                    val extras = extractExtrasFromAnimationState(stateObj)
                    val channelForLog = extras?.getString("hyperisland_channel_id")
                        ?: extras?.getString("hyperisland_source_channel")
//                    if (mode == GLOW_MODE_STATUS || channelForLog == "media") {
//                        log(
//                            module,
//                            "big island state probe: mode=$mode state=${readStateText(stateObj)} data=${extractDynamicData(stateObj)?.javaClass?.name} extrasKeys=${formatBundleKeys(extras)} owner=${extras?.getString(OWNER_KEY)} channel=$channelForLog miuiPkg=${extras?.getString("miui.pkg.name")} miuiKey=${extras?.getString("miui.key")} mediaFocus=${extras?.containsKey("miui.focus.param.media")} big=${extras?.getString(BIG_EFFECT_KEY)} effect=${extras?.getString(EFFECT_KEY)} color=${extras?.getString("hyperisland_island_outer_glow_color")}",
//                        )
//                    }
                    val hasOwnedRequest = mode != GLOW_MODE_AUTO && hasOwnedGlowRequest(extras, mode)
                    if (hasOwnedRequest && extras != null) {
                        val pkg = extras.getString("hyperisland_source_pkg")
                        val channelId = extras.getString("hyperisland_channel_id")
                            ?: extras.getString("hyperisland_source_channel")
                        if (!pkg.isNullOrBlank() && !channelId.isNullOrBlank()) {
                            recentOwnedTarget = OwnedGlowTarget(
                                pkg = pkg,
                                channelId = channelId,
                                mode = mode,
                                focusGlowEnabled = extras.getString(EFFECT_KEY) == EFFECT_VALUE,
                                islandGlowEnabled = extras.getString(BIG_EFFECT_KEY) == EFFECT_VALUE,
                                focusOutEffectColor = extras.getString("hyperisland_focus_out_effect_color"),
                                islandOuterGlowColor = extras.getString("hyperisland_island_outer_glow_color"),
                                createdAt = System.currentTimeMillis(),
                            )
//                            if (channelId == "media") {
//                                log(
//                                    module,
//                                    "media glow target matched: mode=$mode pkg=$pkg islandGlow=${extras.getString(BIG_EFFECT_KEY)} color=${extras.getString("hyperisland_island_outer_glow_color")}",
//                                )
//                            }
                        }
                    }
                    val mediaFocusTarget = if (mode == GLOW_MODE_EXPAND && !hasOwnedRequest) {
                        resolveMediaFocusGlowTarget(extras, classLoader)
                    } else {
                        null
                    }
                    val forcedTarget = if (mode != GLOW_MODE_AUTO && !hasOwnedRequest && mediaFocusTarget == null) {
                        resolveForcedGlobalGlowTarget(extras, mode)
                    } else {
                        null
                    }
                    if (mediaFocusTarget != null) {
                        recentOwnedTarget = mediaFocusTarget
                    } else if (forcedTarget != null) {
                        recentOwnedTarget = forcedTarget
                    }
                    if (mode == GLOW_MODE_EXPAND && (channelForLog == "media" || mediaFocusTarget != null)) {
                        log(
                            module,
                            "media focus state: owned=$hasOwnedRequest cached=${mediaFocusTarget != null} forced=${forcedTarget != null} effect=${extras?.getString(EFFECT_KEY)} focusColor=${extras?.getString("hyperisland_focus_out_effect_color")} dynamicColor=${extras?.getString("hyperisland_dynamic_glow_color")}",
                        )
                    }

                    val result = chain.proceed()

                    val glowView = resolveGlowView(stateObj ?: return@intercept result, mode)
                    recentOwnedTarget?.takeIf { it.mode == mode }?.let { target ->
                        if (glowView != null) synchronized(glowTargets) { glowTargets[glowView] = target }
                    }
                    val mediaRequest = if (isStateTag(stateObj, "BigIsland")) {
                        resolveMediaGlowRequest(stateObj, extras)
                    } else {
                        null
                    }
                    when {
                        isStateTag(stateObj, "BigIsland") && hasOwnedGlowRequest(extras, GLOW_MODE_STATUS) -> {
                            invokeGlowEffectMethod(glowView, "startGlowEffect")
                        }
                        isStateTag(stateObj, "BigIsland") && mediaRequest != null -> {
                            recentOwnedTarget = OwnedGlowTarget(
                                pkg = mediaRequest.pkg,
                                channelId = "media",
                                mode = GLOW_MODE_STATUS,
                                focusGlowEnabled = mediaRequest.focusEnabled,
                                islandGlowEnabled = mediaRequest.islandEnabled,
                                focusOutEffectColor = mediaRequest.focusColor,
                                islandOuterGlowColor = mediaRequest.islandColor,
                                createdAt = System.currentTimeMillis(),
                            )
                            if (glowView != null) synchronized(glowTargets) {
                                glowTargets[glowView] = recentOwnedTarget!!
                            }
                            log(module, "media glow forced start: pkg=${mediaRequest.pkg} island=${mediaRequest.islandEnabled} color=${mediaRequest.islandColor}")
                            invokeGlowEffectMethod(glowView, "startGlowEffect")
                        }
                        mode == GLOW_MODE_EXPAND && hasOwnedRequest -> {
                            log(module, "focus glow start: channel=$channelForLog view=${glowView?.javaClass?.name}")
                            invokeGlowEffectMethod(glowView, "startGlowEffect")
                        }
                        mediaFocusTarget != null -> {
                            log(
                                module,
                                "media focus cached start: pkg=${mediaFocusTarget.pkg} color=${mediaFocusTarget.focusOutEffectColor} view=${glowView?.javaClass?.name}",
                            )
                            invokeGlowEffectMethod(glowView, "startGlowEffect")
                        }
                        forcedTarget != null -> {
                            invokeGlowEffectMethod(glowView, "startGlowEffect")
                        }
                        isStateTag(stateObj, "Deleted") -> {
                            invokeGlowEffectMethod(invokeNoArg(stateObj, "getBigIslandView"), "stopGlowEffect")
                            statusGlowShowing = false
                        }
                    }
                    result
                }
            }
            if (methods.isNotEmpty()) log(module, "hooked animation controller on ${clazz.name}")
        } catch (_: Throwable) {
        }
    }

    private fun hookGlowView(module: XposedModule, classLoader: ClassLoader) {
        val clId = System.identityHashCode(classLoader)
        if (!hookedGlowClassLoaders.add(clId)) return
        try {
            val clazz = classLoader.loadClass(GLOW_VIEW_CLASS)
            val methods = clazz.declaredMethods.filter {
                it.parameterCount == 0 && (
                    it.name == "startGlowEffect" ||
                        it.name == "startGlowEffect\$miui_dynamicisland_release" ||
                        it.name == "stopGlowEffect" ||
                        it.name == "stopGlowEffect\$miui_dynamicisland_release"
                    )
            }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val mode = resolveGlowModeFromGlowView(chain.thisObject)
                    if (method.name.startsWith("startGlowEffect")) {
                        applyGlowAppearance(chain.thisObject, module)
                        //logGlowViewProbe(module, chain.thisObject, mode)
                        applyOwnedGlowColor(chain.thisObject, mode, module)
                        if (mode == GLOW_MODE_STATUS && isOwnedGlowActiveForMode(mode)) {
                            statusGlowShowing = true
                        }
                    } else if (mode == GLOW_MODE_STATUS) {
                        statusGlowShowing = false
                    }
                    chain.proceed()
                }
            }
            if (methods.isNotEmpty()) log(module, "hooked glow view on ${clazz.name}")
        } catch (_: Throwable) {
        }
    }

    private fun hookFocusExtrasBridge(module: XposedModule, classLoader: ClassLoader) {
        val clId = System.identityHashCode(classLoader)
        if (!hookedFocusClassLoaders.add(clId)) return
        try {
            val clazz = classLoader.loadClass(FOCUS_CONTROLLER_CLASS)
            val methods = clazz.declaredMethods.filter {
                it.name == "setUpDynamicIslandDataBundle" &&
                    it.parameterCount == 1 &&
                    it.parameterTypes.firstOrNull()?.name == "android.service.notification.StatusBarNotification"
            }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val sbn = chain.args.firstOrNull() as? android.service.notification.StatusBarNotification
                        ?: return@intercept result
                    val sourceExtras = sbn.notification?.extras ?: return@intercept result
                    val targetBundle = result as? Bundle ?: return@intercept result
                    bridgeEffectExtras(sourceExtras, targetBundle)
                    bridgeDynamicGlowColor(sbn, targetBundle, classLoader)
                    val channelId = targetBundle.getString("hyperisland_channel_id")
                        ?: targetBundle.getString("hyperisland_source_channel")
                    if (sourceExtras.containsKey("miui.focus.param.media") || channelId == "media") {
                        log(
                            module,
                            "media glow bridge: pkg=${sbn.packageName} channel=$channelId big=${targetBundle.getString(BIG_EFFECT_KEY)} effect=${targetBundle.getString(EFFECT_KEY)} islandColor=${targetBundle.getString("hyperisland_island_outer_glow_color")} focusColor=${targetBundle.getString("hyperisland_focus_out_effect_color")} dynamicColor=${targetBundle.getString("hyperisland_dynamic_glow_color")}",
                        )
                    }
                    result
                }
            }
            if (methods.isNotEmpty()) log(module, "hooked focus extras bridge on ${clazz.name}")
        } catch (_: Throwable) {
        }
    }

    private fun bridgeEffectExtras(source: Bundle, target: Bundle) {
        for (key in arrayOf(
            BIG_EFFECT_KEY,
            EFFECT_KEY,
            OWNER_KEY,
            "hyperisland_source_pkg",
            "hyperisland_channel_id",
            "hyperisland_source_channel",
            "hyperisland_focus_out_effect_color",
            "hyperisland_island_outer_glow_color",
            "hyperisland_dynamic_glow_color",
        )) {
            source.getString(key)?.let { target.putString(key, it) }
        }
    }

    private fun hasOwnedGlowRequest(extras: Bundle?, mode: Int): Boolean {
        if (extras == null) return false
        val channelId = extras.getString("hyperisland_channel_id")
            ?: extras.getString("hyperisland_source_channel")
        if (extras.getString(OWNER_KEY) != OWNER_VALUE && channelId != "media") return false
        return when (mode) {
            GLOW_MODE_STATUS ->
                extras.getString(BIG_EFFECT_KEY) == EFFECT_VALUE
            GLOW_MODE_EXPAND ->
                extras.getString(EFFECT_KEY) == EFFECT_VALUE
            else -> false
        }
    }

    private fun resolveGlowColorConfig(mode: Int, target: OwnedGlowTarget): GlowConfig {
        val pkg = target.pkg
        val channelId = target.channelId
        return when (mode) {
            GLOW_MODE_STATUS -> GlowConfig(
                effectEnabled = if (target.forcedGlobal) {
                    target.islandGlowEnabled
                } else when (channelId) {
                    "toast", "media" -> target.islandGlowEnabled
                    else -> resolveGlowEnabled(
                        ConfigManager.getString("pref_channel_island_outer_glow_${pkg}_$channelId", "default"),
                        ConfigManager.getString("pref_default_island_outer_glow", "off"),
                    )
                },
                colorArgb = parseArgbColor(
                    target.islandOuterGlowColor ?: when (channelId) {
                        "media" -> ConfigManager.getString(
                            "pref_media_island_outer_glow_color_$pkg",
                            ConfigManager.getString("pref_default_island_outer_glow_color", ""),
                        )
                        else -> resolveGlowColorValue(
                            mode = ConfigManager.getString("pref_channel_island_outer_glow_${pkg}_$channelId", "default"),
                            fallbackMode = ConfigManager.getString("pref_default_island_outer_glow", "off"),
                            manualColor = ConfigManager.getString(
                                "pref_channel_island_outer_glow_color_${pkg}_$channelId",
                                ConfigManager.getString("pref_default_island_outer_glow_color", ""),
                            ),
                            dynamicColor = ConfigManager.getString(
                                "pref_channel_highlight_color_${pkg}_$channelId",
                                "",
                            ),
                        )
                    },
                ),
            )
            GLOW_MODE_EXPAND -> GlowConfig(
                effectEnabled = if (target.forcedGlobal) {
                    target.focusGlowEnabled
                } else if (channelId == "toast" || channelId == "media") {
                    target.focusGlowEnabled
                } else {
                    resolveGlowEnabled(
                        ConfigManager.getString("pref_channel_outer_glow_${pkg}_$channelId", "default"),
                        ConfigManager.getString("pref_default_outer_glow", "off"),
                    )
                },
                colorArgb = parseArgbColor(
                    target.focusOutEffectColor ?: if (channelId == "media") {
                        ConfigManager.getString(
                            "pref_media_out_effect_color_$pkg",
                            ConfigManager.getString("pref_default_out_effect_color", ""),
                        )
                    } else resolveGlowColorValue(
                        mode = ConfigManager.getString("pref_channel_outer_glow_${pkg}_$channelId", "default"),
                        fallbackMode = ConfigManager.getString("pref_default_outer_glow", "off"),
                        manualColor = ConfigManager.getString(
                            "pref_channel_out_effect_color_${pkg}_$channelId",
                            ConfigManager.getString("pref_default_out_effect_color", ""),
                        ),
                        dynamicColor = ConfigManager.getString(
                            "pref_channel_highlight_color_${pkg}_$channelId",
                            "",
                        ),
                    ),
                ),
            )
            else -> GlowConfig(false, null)
        }
    }

    private fun resolveForcedGlobalGlowTarget(extras: Bundle?, mode: Int): OwnedGlowTarget? {
        val pkg = resolveSourcePkg(extras) ?: ""
        val channelId = resolveSourceChannelId(extras) ?: ""
        val enabled = when (mode) {
            GLOW_MODE_STATUS -> resolveForcedGlowEnabled(
                channelMode = channelSettingOrDefault(
                    pkg,
                    channelId,
                    "pref_channel_island_outer_glow",
                ),
                defaultMode = ConfigManager.getString("pref_default_island_outer_glow", "off"),
                globalKey = "pref_default_force_island_outer_glow",
            )
            GLOW_MODE_EXPAND -> resolveForcedGlowEnabled(
                channelMode = channelSettingOrDefault(
                    pkg,
                    channelId,
                    "pref_channel_outer_glow",
                ),
                defaultMode = ConfigManager.getString("pref_default_outer_glow", "off"),
                globalKey = "pref_default_force_outer_glow",
            )
            else -> false
        }
        if (!enabled) return null
        val effectiveMode = when (mode) {
            GLOW_MODE_STATUS -> resolveConfiguredGlowMode(
                channelSettingOrDefault(pkg, channelId, "pref_channel_island_outer_glow"),
                ConfigManager.getString("pref_default_island_outer_glow", "off"),
            )
            GLOW_MODE_EXPAND -> resolveConfiguredGlowMode(
                channelSettingOrDefault(pkg, channelId, "pref_channel_outer_glow"),
                ConfigManager.getString("pref_default_outer_glow", "off"),
            )
            else -> "off"
        }
        val dynamicColor = extras?.getString("hyperisland_dynamic_glow_color")
        return OwnedGlowTarget(
            pkg = pkg,
            channelId = channelId,
            mode = mode,
            focusGlowEnabled = mode == GLOW_MODE_EXPAND,
            islandGlowEnabled = mode == GLOW_MODE_STATUS,
            focusOutEffectColor = dynamicColor.takeIf {
                mode == GLOW_MODE_EXPAND && effectiveMode == "follow_dynamic"
            },
            islandOuterGlowColor = dynamicColor.takeIf {
                mode == GLOW_MODE_STATUS && effectiveMode == "follow_dynamic"
            },
            forcedGlobal = true,
            createdAt = System.currentTimeMillis(),
        )
    }

    private fun resolveForcedGlowEnabled(
        channelMode: String,
        defaultMode: String,
        globalKey: String,
    ): Boolean {
        return when (channelMode.trim().lowercase()) {
            "on", "follow_dynamic" -> true
            "off" -> false
            else -> defaultMode.trim().lowercase() != "off" &&
                ConfigManager.getBoolean(globalKey, false)
        }
    }

    private fun channelSettingOrDefault(pkg: String, channelId: String, keyPrefix: String): String {
        if (pkg.isBlank() || channelId.isBlank()) return "default"
        return ConfigManager.getString("${keyPrefix}_${pkg}_$channelId", "default")
    }

    private fun resolveConfiguredGlowMode(channelMode: String, defaultMode: String): String {
        return when (channelMode.trim().lowercase()) {
            "on", "off", "follow_dynamic" -> channelMode.trim().lowercase()
            else -> defaultMode.trim().lowercase()
        }
    }

    private fun applyOwnedGlowColor(glowView: Any?, mode: Int, module: XposedModule) {
        if (glowView == null) return
        if (!shouldApplyOwnedGlowForMode(mode)) return
        val target = synchronized(glowTargets) { glowTargets[glowView] }
            ?: recentOwnedTarget
            ?: return
        if (mode == GLOW_MODE_AUTO || target.mode != mode) return
        if (System.currentTimeMillis() - target.createdAt > RECENT_TTL_MS) return

        val cfg = resolveGlowColorConfig(mode, target)
        val shader = resolveLightBgShader(glowView) ?: return
        val runtimeShader = resolveRuntimeShader(shader) ?: return
        val shaderClass = shader.javaClass
        val base = obtainDefaultLightColors(shaderClass) ?: readInstanceLightColors(shader) ?: return
        cacheDefaultLightColors(shaderClass, base)
        val targetColors = if (!cfg.effectEnabled || cfg.colorArgb == null) {
            base
        } else {
            rebuildLightShaderArray(base, cfg.colorArgb)
        }
        val applied = setRuntimeShaderLightColors(runtimeShader, targetColors)
        if (mode == GLOW_MODE_EXPAND || target.channelId == "media") {
            log(
                module,
                "glow color apply: mode=$mode target=${target.pkg}/${target.channelId} forced=${target.forcedGlobal} enabled=${cfg.effectEnabled} color=${cfg.colorArgb?.let { String.format("#%08X", it) }} shaderApplied=$applied",
            )
        }
    }

    private fun resolveMediaGlowRequest(stateObj: Any?, extras: Bundle?): MediaGlowRequest? {
        val isMediaState = isMediaAnimationState(stateObj, extras)
        if (!isMediaState) return null
        val pkg = resolveSourcePkg(extras)
        if (pkg.isNullOrBlank()) return null
        return mediaGlowRequests[pkg]?.takeIf { it.islandEnabled }
    }

    private fun applyGlowAppearance(glowView: Any, module: XposedModule) {
        val range = ConfigManager.getInt("pref_outer_glow_range", 0).coerceIn(0, 100)
        val container = invokeNoArg(glowView, "getMContainer")
        if (container == null) {
            log(
                module,
                "glow range apply: range=$range container=null view=${glowView.javaClass.name}",
            )
            return
        }
        val defaultRange = synchronized(defaultGlowRanges) {
            defaultGlowRanges.getOrPut(container) {
                (invokeNoArg(container, "getSizeOfGlowArea") as? Number)?.toFloat() ?: Float.NaN
            }
        }
        val appliedRange = if (range == 0 || defaultRange.isNaN()) {
            defaultRange
        } else {
            defaultRange * range / 100f
        }
        val rangeApplied = !appliedRange.isNaN() && invokeFloatSetter(
            container,
            "setSizeOfGlowArea",
            appliedRange,
        )
        log(
            module,
            "glow range apply: range=$range area=$defaultRange->$appliedRange applied=$rangeApplied view=${glowView.javaClass.name}",
        )
    }

    private fun resolveMediaFocusGlowTarget(
        extras: Bundle?,
        classLoader: ClassLoader,
    ): OwnedGlowTarget? {
        val pkg = resolveSourcePkg(extras) ?: return null
        val sbn = resolveStatusBarNotification(extras)
        val request = mediaGlowRequests[pkg]?.takeIf {
            it.focusEnabled
        }
        val isMedia = request != null || extras?.containsKey("miui.focus.param.media") == true ||
            sbn?.notification?.let {
                it.extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION) ||
                    it.extras.getString(android.app.Notification.EXTRA_TEMPLATE)
                        ?.contains("MediaStyle", ignoreCase = true) == true
            } == true
        if (!isMedia) return null
        val focusMode = resolveConfiguredGlowMode(
            ConfigManager.getString("pref_media_outer_glow_$pkg", "default"),
            ConfigManager.getString("pref_default_outer_glow", "off"),
        )
        val focusEnabled = request?.focusEnabled ?: (focusMode != "off")
        if (!focusEnabled) return null
        val focusColor = request?.focusColor ?: when (focusMode) {
            "follow_dynamic" -> resolveDynamicGlowColorFromSbn(extras, classLoader, pkg)
            else -> ConfigManager.getString(
                "pref_media_out_effect_color_$pkg",
                ConfigManager.getString("pref_default_out_effect_color", ""),
            ).takeIf { it.isNotBlank() }
        }
        return OwnedGlowTarget(
            pkg = pkg,
            channelId = "media",
            mode = GLOW_MODE_EXPAND,
            focusGlowEnabled = true,
            islandGlowEnabled = request?.islandEnabled == true,
            focusOutEffectColor = focusColor,
            islandOuterGlowColor = request?.islandColor,
            createdAt = System.currentTimeMillis(),
        )
    }

    private fun resolveDynamicGlowColorFromSbn(
        extras: Bundle?,
        classLoader: ClassLoader,
        pkg: String,
    ): String? {
        val context = HookUtils.getContext(classLoader) ?: return null
        val notification = resolveStatusBarNotification(extras)?.notification
        val icon = notification?.getLargeIcon()
            ?: notification?.smallIcon
            ?: context.packageManager.getAppIcon(pkg)
            ?: return null
        return icon.resolveDynamicHighlightColor(context, "on")
    }

    @Suppress("DEPRECATION")
    private fun resolveStatusBarNotification(extras: Bundle?): android.service.notification.StatusBarNotification? {
        if (extras == null) return null
        return runCatching {
            extras.getParcelable("miui.sbn") as? android.service.notification.StatusBarNotification
        }.getOrNull()
    }

    private fun isMediaAnimationState(stateObj: Any?, extras: Bundle?): Boolean {
        if (extras != null) {
            val channelId = extras.getString("hyperisland_channel_id")
                ?: extras.getString("hyperisland_source_channel")
            if (channelId == "media" || extras.containsKey("miui.focus.param.media")) return true
            resolveSourcePkg(extras)?.let { pkg ->
                val request = mediaGlowRequests[pkg]
                if (request?.islandEnabled == true || request?.focusEnabled == true) return true
            }
        }
        return false
    }

    private fun resolveSourcePkg(extras: Bundle?): String? {
        if (extras == null) return null
        return extras.getString("hyperisland_source_pkg")
            ?: extras.getString("miui.pkg.name")
    }

    private fun resolveSourceChannelId(extras: Bundle?): String? {
        if (extras == null) return null
        return extras.getString("hyperisland_channel_id")
            ?: extras.getString("hyperisland_source_channel")
            ?: extras.getString("miui.channel.id")
            ?: extras.getString("android.channelId")
    }

    private fun formatBundleKeys(bundle: Bundle?): String {
        if (bundle == null) return "null"
        return bundle.keySet().joinToString(prefix = "[", postfix = "]", limit = 40, truncated = "...")
    }

    private fun logGlowViewProbe(module: XposedModule, glowView: Any?, mode: Int) {
        if (glowView == null) return
        val target = recentOwnedTarget
        log(
            module,
            "glow view probe: mode=$mode class=${glowView.javaClass.name} target=${target?.pkg}/${target?.channelId} targetMode=${target?.mode}",
        )
        logObjectShape(module, "glowView", glowView)
        invokeNoArg(glowView, "getMContainer")?.let { container ->
            logObjectShape(module, "glowContainer", container)
            resolveLightBgShader(glowView)?.let { shader ->
                logObjectShape(module, "glowShader", shader)
                resolveRuntimeShader(shader)?.let { runtimeShader ->
                    logObjectShape(module, "runtimeShader", runtimeShader)
                }
            }
        }
    }

    private fun logObjectShape(module: XposedModule, label: String, obj: Any) {
        val cls = obj.javaClass
        val fields = cls.declaredFields.joinToString(limit = 24, truncated = "...") { field ->
            "${field.name}:${field.type.simpleName}"
        }
        val methods = cls.declaredMethods
            .filter { it.parameterCount == 0 }
            .joinToString(limit = 24, truncated = "...") { method ->
                "${method.name}():${method.returnType.simpleName}"
            }
        log(module, "$label shape: class=${cls.name} fields=[$fields] noArgMethods=[$methods]")
    }

    private fun shouldApplyOwnedGlowForMode(mode: Int): Boolean {
        if (mode == GLOW_MODE_AUTO) return false
        val target = recentOwnedTarget ?: return false
        if (System.currentTimeMillis() - target.createdAt > RECENT_TTL_MS) return false
        return target.mode == mode
    }

    private fun isOwnedGlowActiveForMode(mode: Int): Boolean {
        if (mode == GLOW_MODE_AUTO) return false
        val target = recentOwnedTarget ?: return false
        if (System.currentTimeMillis() - target.createdAt > RECENT_TTL_MS) return false
        if (target.mode != mode) return false
        return resolveGlowColorConfig(mode, target).effectEnabled
    }

    private fun resolveGlowEnabled(value: String?, defaultValue: String): Boolean {
        return when (value?.trim()?.lowercase()) {
            "on", "follow_dynamic" -> true
            "off" -> false
            else -> when (defaultValue.trim().lowercase()) {
                "on", "follow_dynamic" -> true
                else -> false
            }
        }
    }

    private fun resolveGlowColorValue(
        mode: String?,
        fallbackMode: String,
        manualColor: String?,
        dynamicColor: String?,
    ): String? {
        val resolvedMode = when (mode?.trim()?.lowercase()) {
            "on", "off", "follow_dynamic" -> mode.trim().lowercase()
            else -> fallbackMode.trim().lowercase()
        }
        return if (resolvedMode == "follow_dynamic") dynamicColor else manualColor
    }

    private fun parseArgbColor(raw: String?): Int? {
        val normalized = normalizeColorString(raw) ?: return null
        return runCatching { Color.parseColor(normalized) }.getOrNull()
    }

    private fun normalizeColorString(raw: String?): String? {
        val cleaned = raw?.trim()?.removePrefix("#") ?: return null
        if (cleaned.isBlank()) return null
        return when (cleaned.length) {
            6 -> "#FF${cleaned.uppercase()}"
            8 -> "#${cleaned.uppercase()}"
            else -> null
        }
    }

    private fun isStateTag(stateObj: Any?, tag: String): Boolean =
        readStateText(stateObj)?.contains(tag) == true

    private fun resolveStrictGlowMode(stateObj: Any?): Int {
        val state = invokeNoArg(stateObj ?: return GLOW_MODE_AUTO, "getState") ?: return GLOW_MODE_AUTO
        return when {
            state.javaClass.simpleName == "Expanded" -> GLOW_MODE_EXPAND
            state.javaClass.simpleName == "BigIsland" -> GLOW_MODE_STATUS
            else -> GLOW_MODE_AUTO
        }
    }

    private fun bridgeDynamicGlowColor(
        sbn: android.service.notification.StatusBarNotification,
        target: Bundle,
        classLoader: ClassLoader,
    ) {
        if (!target.getString("hyperisland_dynamic_glow_color").isNullOrBlank()) return
        val context = HookUtils.getContext(classLoader) ?: return
        val notification = sbn.notification ?: return
        val sourcePkg = target.getString("hyperisland_source_pkg")
            ?: target.getString("miui.pkg.name")
            ?: sbn.packageName
        val channelId = target.getString("hyperisland_channel_id")
            ?: target.getString("hyperisland_source_channel").orEmpty()
        val focusMode = if (channelId == "media") {
            resolveConfiguredGlowMode(
                ConfigManager.getString("pref_media_outer_glow_$sourcePkg", "default"),
                ConfigManager.getString("pref_default_outer_glow", "off"),
            )
        } else {
            resolveConfiguredGlowMode(
                channelSettingOrDefault(sourcePkg, channelId, "pref_channel_outer_glow"),
                ConfigManager.getString("pref_default_outer_glow", "off"),
            )
        }
        if (focusMode != "follow_dynamic") return
        val icon = notification.getLargeIcon()
            ?: notification.smallIcon
            ?: context.packageManager.getAppIcon(sourcePkg)
            ?: return
        icon.resolveDynamicHighlightColor(context, "on")?.let { color ->
            target.putString("hyperisland_dynamic_glow_color", color)
        }
    }

    private fun resolveGlowView(contentView: Any, mode: Int): Any? {
        return when (mode) {
            GLOW_MODE_STATUS -> invokeNoArg(contentView, "getBigIslandView")
            GLOW_MODE_EXPAND -> invokeNoArg(contentView, "getExpandedView")
            else -> null
        }
    }

    private fun resolveGlowModeFromGlowView(glowView: Any): Int {
        val cls = glowView.javaClass.name
        return when {
            cls.contains(EXPANDED_VIEW_MARKER) -> GLOW_MODE_EXPAND
            cls.contains(BIG_VIEW_MARKER) -> GLOW_MODE_STATUS
            shouldApplyOwnedGlowForMode(GLOW_MODE_STATUS) -> GLOW_MODE_STATUS
            shouldApplyOwnedGlowForMode(GLOW_MODE_EXPAND) -> GLOW_MODE_EXPAND
            else -> GLOW_MODE_AUTO
        }
    }

    private fun resolveGlowModeFromContentView(view: Any?): Int {
        val stateText = invokeNoArg(view ?: return GLOW_MODE_AUTO, "getState")?.toString()
        return when {
            stateText?.contains("BigIsland") == true || stateText?.contains("SmallIsland") == true -> GLOW_MODE_STATUS
            stateText?.contains("Expand") == true -> GLOW_MODE_EXPAND
            else -> GLOW_MODE_AUTO
        }
    }

    private fun extractExtrasFromAnimationState(stateObj: Any?): Bundle? {
        val dataObj = extractDynamicData(stateObj) ?: return null
        invokeNoArg(dataObj, "getExtras")?.let { if (it is Bundle) return it }
        readFieldValue(dataObj, "extras")?.let { if (it is Bundle) return it }
        readFieldValue(dataObj, "mExtras")?.let { if (it is Bundle) return it }
        return null
    }

    private fun extractDynamicData(stateObj: Any?): Any? {
        if (stateObj == null) return null
        listOf("getCurrentIslandData", "getIslandData", "getData").forEach { name ->
            invokeNoArg(stateObj, name)?.let { return it }
        }
        return null
    }

    private fun readStateText(stateObj: Any?): String? {
        return invokeNoArg(stateObj ?: return null, "getState")?.toString()
    }

    private fun invokeGlowEffectMethod(view: Any?, baseMethodName: String) {
        if (view == null) return
        findNoArgMethod(view.javaClass, baseMethodName)?.let {
            runCatching {
                it.isAccessible = true
                it.invoke(view)
            }
            return
        }
        findNoArgMethod(view.javaClass, "$baseMethodName\$miui_dynamicisland_release")?.let {
            runCatching {
                it.isAccessible = true
                it.invoke(view)
            }
            return
        }
        invokeNoArg(view, "getGlowEffectView")?.let {
            invokeGlowEffectMethod(it, baseMethodName)
            return
        }
    }

    private fun resolveLightBgShader(glowView: Any): Any? {
        val container = invokeNoArg(glowView, "getMContainer") ?: return null
        invokeNoArg(container, "getMShader\$hyper_widget_1_0_8_pluginRelease")?.let { return it }
        container.javaClass.declaredMethods.forEach { method ->
            if (method.parameterCount == 0 && method.name.contains("getMShader")) {
                runCatching {
                    method.isAccessible = true
                    method.invoke(container)
                }.getOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun resolveRuntimeShader(shader: Any): Any? {
        invokeNoArg(shader, "getMTextureShader")?.let { return it }
        shader.javaClass.declaredMethods.forEach { method ->
            if (method.parameterCount == 0 && method.name.contains("getMTextureShader")) {
                runCatching {
                    method.isAccessible = true
                    method.invoke(shader)
                }.getOrNull()?.let { return it }
            }
        }
        invokeNoArg(shader, "getRuntimeShader")?.let { return it }
        invokeNoArg(shader, "getMRuntimeShader")?.let { return it }
        return readFieldValue(shader, "mRuntimeShader")
    }

    private fun obtainDefaultLightColors(shaderClass: Class<*>): FloatArray? {
        synchronized(defaultShaderColors) {
            defaultShaderColors[shaderClass]?.let { return it.copyOf() }
        }
        return readStaticLightColors(findLightColorField(shaderClass, preferStatic = true))
            ?.also { cacheDefaultLightColors(shaderClass, it) }
    }

    private fun cacheDefaultLightColors(shaderClass: Class<*>, colors: FloatArray) {
        synchronized(defaultShaderColors) {
            if (!defaultShaderColors.containsKey(shaderClass)) {
                defaultShaderColors[shaderClass] = colors.copyOf()
            }
        }
    }

    private fun readStaticLightColors(field: Field?): FloatArray? {
        return runCatching { (field?.get(null) as? FloatArray)?.copyOf() }.getOrNull()
    }

    private fun readInstanceLightColors(shader: Any): FloatArray? {
        val field = findLightColorField(shader.javaClass, preferStatic = false) ?: return null
        return runCatching { (field.get(shader) as? FloatArray)?.copyOf() }.getOrNull()
    }

    private fun findLightColorField(clazz: Class<*>, preferStatic: Boolean): Field? {
        runCatching {
            val exact = clazz.getDeclaredField(LIGHT_BG_SHADER_FIELD)
            exact.isAccessible = true
            return exact
        }
        val fields = clazz.declaredFields.filter {
            it.type == FloatArray::class.java && it.name.contains("LIGHT", ignoreCase = true)
        }
        val preferred = fields.firstOrNull { java.lang.reflect.Modifier.isStatic(it.modifiers) == preferStatic }
        return (preferred ?: fields.firstOrNull())?.apply { isAccessible = true }
    }

    private fun setRuntimeShaderLightColors(runtimeShader: Any, colors: FloatArray): Boolean {
        val method = (runtimeShader.javaClass.methods + runtimeShader.javaClass.declaredMethods).firstOrNull {
            it.name == "setFloatUniform" &&
                it.parameterCount == 2 &&
                it.parameterTypes.getOrNull(0) == String::class.java &&
                it.parameterTypes.getOrNull(1)?.isArray == true
        } ?: return false
        return runCatching {
            method.isAccessible = true
            method.invoke(runtimeShader, "uLightColors", colors)
            true
        }.getOrDefault(false)
    }

    private fun rebuildLightShaderArray(base: FloatArray, argb: Int): FloatArray {
        val template = normalizeTemplatePalette(base)
        val output = FloatArray(template.size)
        val seedHsv = FloatArray(3)
        Color.colorToHSV(argb, seedHsv)
        val anchorHsv = extractStopHsv(template, 2)
        val ranges = calcTemplateSvMinMax(template)
        val stopCount = template.size / 3
        for (i in 0 until stopCount) {
            val tplHsv = extractStopHsv(template, i)
            val hue = wrapHue(seedHsv[0] + shortestHueDelta(anchorHsv[0], tplHsv[0]) * 0.45f)
            val sat = clamp01(
                seedHsv[1] *
                    (0.72f + 0.38f * normalize01(tplHsv[1], ranges[0], ranges[1])) *
                    (tplHsv[1] / maxOf(anchorHsv[1], 0.01f)),
            )
            val value = clamp01(
                seedHsv[2] *
                    (0.62f + 0.48f * normalize01(tplHsv[2], ranges[2], ranges[3])) *
                    (tplHsv[2] / maxOf(anchorHsv[2], 0.01f)),
            )
            val color = Color.HSVToColor(Color.alpha(argb), floatArrayOf(hue, sat, value))
            val baseIndex = i * 3
            output[baseIndex] = Color.red(color) / 255f
            output[baseIndex + 1] = Color.green(color) / 255f
            output[baseIndex + 2] = Color.blue(color) / 255f
        }
        return output
    }

    private fun normalizeTemplatePalette(base: FloatArray): FloatArray {
        if (base.size >= LIGHT_COLOR_ARRAY_SIZE) return base.copyOf(LIGHT_COLOR_ARRAY_SIZE)
        return floatArrayOf(
            0.502f, 0.525f, 1.0f,
            1.0f, 0.827f, 0.702f,
            1.0f, 0.525f, 0.208f,
            0.518f, 0.494f, 1.0f,
            0.071f, 0.412f, 0.949f,
            0.502f, 0.525f, 1.0f,
            1.0f, 0.827f, 0.702f,
            1.0f, 0.525f, 0.208f,
            0.518f, 0.494f, 1.0f,
            0.071f, 0.412f, 0.949f,
            1.0f, 0.525f, 0.208f,
        )
    }

    private fun extractStopHsv(rgb33: FloatArray, stopIndex: Int): FloatArray {
        val idx = stopIndex * 3
        val hsv = FloatArray(3)
        Color.RGBToHSV(
            (clamp01(rgb33[idx]) * 255f).toInt(),
            (clamp01(rgb33[idx + 1]) * 255f).toInt(),
            (clamp01(rgb33[idx + 2]) * 255f).toInt(),
            hsv,
        )
        return hsv
    }

    private fun calcTemplateSvMinMax(rgb33: FloatArray): FloatArray {
        var minS = 1f
        var maxS = 0f
        var minV = 1f
        var maxV = 0f
        val stopCount = rgb33.size / 3
        for (i in 0 until stopCount) {
            val hsv = extractStopHsv(rgb33, i)
            minS = minOf(minS, hsv[1])
            maxS = maxOf(maxS, hsv[1])
            minV = minOf(minV, hsv[2])
            maxV = maxOf(maxV, hsv[2])
        }
        return floatArrayOf(minS, maxS, minV, maxV)
    }

    private fun normalize01(x: Float, min: Float, max: Float): Float {
        val diff = max - min
        if (diff <= 1e-6f) return 0.5f
        return clamp01((x - min) / diff)
    }

    private fun shortestHueDelta(from: Float, to: Float): Float {
        var delta = (to - from) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    private fun wrapHue(value: Float): Float {
        var hue = value % 360f
        if (hue < 0f) hue += 360f
        return hue
    }

    private fun clamp01(value: Float): Float = value.coerceIn(0f, 1f)

    private fun invokeNoArg(target: Any, methodName: String): Any? {
        return runCatching {
            val method = findNoArgMethod(target.javaClass, methodName) ?: return null
            method.isAccessible = true
            method.invoke(target)
        }.getOrNull()
    }

    private fun invokeFloatSetter(target: Any, methodName: String, value: Float): Boolean {
        var current: Class<*>? = target.javaClass
        while (current != null) {
            val method = current.declaredMethods.firstOrNull {
                it.name == methodName &&
                    it.parameterCount == 1 &&
                    it.parameterTypes[0] == Float::class.javaPrimitiveType
            }
            if (method != null) {
                return runCatching {
                    method.isAccessible = true
                    method.invoke(target, value)
                    true
                }.getOrDefault(false)
            }
            current = current.superclass
        }
        return false
    }

    private fun findNoArgMethod(clazz: Class<*>, name: String): Method? {
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods.firstOrNull { it.name == name && it.parameterCount == 0 }?.let { return it }
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
