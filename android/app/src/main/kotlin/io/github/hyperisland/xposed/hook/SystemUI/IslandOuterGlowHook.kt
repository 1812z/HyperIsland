package io.github.hyperisland.xposed.hook

import android.graphics.Color
import android.os.Bundle
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
        val createdAt: Long,
    )

    private val hookedGlowClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val hookedAnimationClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val hookedFeatureClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val hookedFocusClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val defaultShaderColors = WeakHashMap<Class<*>, FloatArray>()

    @Volatile private var recentOwnedTarget: OwnedGlowTarget? = null

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        hookDynamicClassLoaders(module)
        hookFeatureConfig(module, param.defaultClassLoader)
        hookAnimationController(module, param.defaultClassLoader)
        hookGlowView(module, param.defaultClassLoader)
        hookFocusExtrasBridge(module, param.defaultClassLoader)
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
                    if (mode != GLOW_MODE_AUTO && extras != null && hasOwnedGlowRequest(extras, mode)) {
                        val pkg = extras.getString("hyperisland_source_pkg")
                        val channelId = extras.getString("hyperisland_channel_id")
                            ?: extras.getString("hyperisland_source_channel")
                        if (!pkg.isNullOrBlank() && !channelId.isNullOrBlank()) {
                            recentOwnedTarget = OwnedGlowTarget(
                                pkg = pkg,
                                channelId = channelId,
                                mode = mode,
                                createdAt = System.currentTimeMillis(),
                            )
                        }
                    }

                    val result = chain.proceed()

                    val bigView = invokeNoArg(stateObj ?: return@intercept result, "getBigIslandView")
                    when {
                        isStateTag(stateObj, "BigIsland") && hasOwnedGlowRequest(extras, GLOW_MODE_STATUS) -> {
                            invokeGlowEffectMethod(bigView, "startGlowEffect")
                        }
                        isStateTag(stateObj, "Deleted") -> {
                            invokeGlowEffectMethod(bigView, "stopGlowEffect")
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
                it.parameterCount == 0 && (it.name == "startGlowEffect" || it.name == "startGlowEffect\$miui_dynamicisland_release")
            }
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    applyOwnedGlowColor(chain.thisObject, resolveGlowModeFromGlowView(chain.thisObject))
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
        )) {
            source.getString(key)?.let { target.putString(key, it) }
        }
    }

    private fun hasOwnedGlowRequest(extras: Bundle?, mode: Int): Boolean {
        if (extras == null) return false
        if (extras.getString(OWNER_KEY) != OWNER_VALUE) return false
        return when (mode) {
            GLOW_MODE_STATUS ->
                extras.getString(BIG_EFFECT_KEY) == EFFECT_VALUE
            GLOW_MODE_EXPAND ->
                extras.getString(BIG_EFFECT_KEY) == EFFECT_VALUE ||
                    extras.getString(EFFECT_KEY) == EFFECT_VALUE
            else -> false
        }
    }

    private fun resolveGlowColorConfig(mode: Int, pkg: String, channelId: String): GlowConfig {
        return when (mode) {
            GLOW_MODE_STATUS -> GlowConfig(
                effectEnabled = resolveTriOpt(
                    ConfigManager.getString("pref_channel_island_outer_glow_${pkg}_$channelId", "default"),
                    ConfigManager.getBoolean("pref_default_island_outer_glow", false),
                ),
                colorArgb = parseArgbColor(
                    ConfigManager.getString(
                        "pref_channel_island_outer_glow_color_${pkg}_$channelId",
                        ConfigManager.getString("pref_default_island_outer_glow_color", ""),
                    ),
                ),
            )
            GLOW_MODE_EXPAND -> GlowConfig(
                effectEnabled = resolveTriOpt(
                    ConfigManager.getString("pref_channel_outer_glow_${pkg}_$channelId", "default"),
                    ConfigManager.getBoolean("pref_default_outer_glow", false),
                ),
                colorArgb = parseArgbColor(
                    ConfigManager.getString(
                        "pref_channel_out_effect_color_${pkg}_$channelId",
                        ConfigManager.getString("pref_default_out_effect_color", ""),
                    ),
                ),
            )
            else -> GlowConfig(false, null)
        }
    }

    private fun applyOwnedGlowColor(glowView: Any?, mode: Int) {
        if (glowView == null) return
        if (!shouldApplyOwnedGlowForMode(mode)) return
        val target = recentOwnedTarget ?: return
        if (mode == GLOW_MODE_AUTO || target.mode != mode) return
        if (System.currentTimeMillis() - target.createdAt > RECENT_TTL_MS) return

        val cfg = resolveGlowColorConfig(mode, target.pkg, target.channelId)
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
        setRuntimeShaderLightColors(runtimeShader, targetColors)
    }

    private fun shouldApplyOwnedGlowForMode(mode: Int): Boolean {
        if (mode == GLOW_MODE_AUTO) return false
        val target = recentOwnedTarget ?: return false
        if (System.currentTimeMillis() - target.createdAt > RECENT_TTL_MS) return false
        return target.mode == mode
    }

    private fun resolveTriOpt(value: String?, defaultValue: Boolean): Boolean {
        return when (value?.trim()?.lowercase()) {
            "on" -> true
            "off" -> false
            else -> defaultValue
        }
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
        val text = readStateText(stateObj) ?: return GLOW_MODE_AUTO
        val isBig = text.contains("BigIsland")
        val isExpand = text.contains("Expand")
        return when {
            isExpand && !isBig -> GLOW_MODE_EXPAND
            isBig && !isExpand -> GLOW_MODE_STATUS
            else -> GLOW_MODE_AUTO
        }
    }

    private fun resolveGlowModeFromGlowView(glowView: Any): Int {
        val cls = glowView.javaClass.name
        return when {
            cls.contains(EXPANDED_VIEW_MARKER) -> GLOW_MODE_EXPAND
            cls.contains(BIG_VIEW_MARKER) -> GLOW_MODE_STATUS
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

    private fun setRuntimeShaderLightColors(runtimeShader: Any, colors: FloatArray) {
        val method = (runtimeShader.javaClass.methods + runtimeShader.javaClass.declaredMethods).firstOrNull {
            it.name == "setFloatUniform" &&
                it.parameterCount == 2 &&
                it.parameterTypes.getOrNull(0) == String::class.java &&
                it.parameterTypes.getOrNull(1)?.isArray == true
        } ?: return
        runCatching {
            method.isAccessible = true
            method.invoke(runtimeShader, "uLightColors", colors)
        }
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
