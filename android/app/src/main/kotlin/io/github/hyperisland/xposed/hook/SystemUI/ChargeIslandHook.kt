package io.github.hyperisland.xposed.hook

import android.animation.ValueAnimator
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.Collections
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

object ChargeIslandHook : BaseHook() {

    private const val TAG = "HyperIsland[ChargeIsland]"
    private const val PREF_ENABLED = "pref_charge_island"
    private const val PREF_LEFT_MODE = "pref_charge_island_left_mode"
    private const val PREF_RIGHT_MODE = "pref_charge_island_right_mode"
    private const val PREF_DURATION_MODE = "pref_charge_island_duration_mode"
    private const val PREF_DURATION_SECONDS = "pref_charge_island_duration_seconds"
    private const val PREF_OUTER_GLOW = "pref_charge_island_outer_glow"

    private const val MODE_DEFAULT = "default"
    private const val MODE_POWER = IslandDataManager.MODE_POWER
    private const val MODE_VOLTAGE = IslandDataManager.MODE_VOLTAGE
    private const val MODE_CURRENT = IslandDataManager.MODE_CURRENT
    private const val MODE_LEVEL = IslandDataManager.MODE_LEVEL
    private const val MODE_TEMPERATURE = IslandDataManager.MODE_TEMPERATURE
    private const val DURATION_DEFAULT = "default"
    private const val DURATION_CUSTOM = "custom"
    private const val DURATION_PERSISTENT = "persistent"

    private val DEVICE_NOTIFICATION_CLASS_NAMES = listOf(
        // HyperOS 新版本实际包名；保留旧包名用于兼容不同 SystemUI 版本。
        "com.android.systemui.devicenotification.listener.DeviceNotificationListenerImpl",
        "com.android.systemui.statusbar.notification.DeviceNotificationListenerImpl",
    )

    private val hookedClassLoaders = ConcurrentHashMap.newKeySet<Int>()
    private val hookedMethods = Collections.newSetFromMap(WeakHashMap<Method, Boolean>())
    private val modelAccessorsByClass: ConcurrentMap<Class<*>, ModelAccessors> = ConcurrentHashMap()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var lastSnapshotLogAt = 0L
    @Volatile private var chargeLeftHolderRef: WeakReference<Any>? = null
    @Volatile private var chargeRightHolderRef: WeakReference<Any>? = null
    @Volatile private var lastLeftViewText: String? = null
    @Volatile private var lastRightViewText: String? = null
    private val dataChangedListener = {
        logSnapshotIfNeeded()
        updateChargeViews()
    }

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        debug(module, "init package=${param.packageName} defaultCl=${param.defaultClassLoader}")
        IslandDataManager.addListener(dataChangedListener)
        hookApplicationOnCreate(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            debug(module, "dynamic classloader created: $classLoader")
            hookChargeModel(module, classLoader)
            hookIslandTextHolder(module, classLoader)
        }
        hookChargeModel(module, param.defaultClassLoader)
        hookIslandTextHolder(module, param.defaultClassLoader)
    }

    private fun hookApplicationOnCreate(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val method = classLoader.loadClass("android.app.Application").getDeclaredMethod("onCreate")
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? Context)?.let {
                    runCatching { IslandDataManager.register(it) }
                    debug(module, "island data manager registered")
                }
                result
            }
        }.onFailure { error ->
            logError(module, "failed to hook Application.onCreate: ${error.message}")
        }
    }

    private fun hookChargeModel(module: XposedModule, classLoader: ClassLoader) {
        val clId = System.identityHashCode(classLoader)
        if (!hookedClassLoaders.add(clId)) return

        val clazz = DEVICE_NOTIFICATION_CLASS_NAMES.firstNotNullOfOrNull { className ->
            runCatching { classLoader.loadClass(className) }.getOrNull()
        } ?: run {
            debug(
                module,
                "DeviceNotificationListenerImpl not found in cl=$clId loader=$classLoader names=$DEVICE_NOTIFICATION_CLASS_NAMES",
            )
            return
        }
        debug(module, "DeviceNotificationListenerImpl found: ${clazz.name} cl=$clId")
        hookHandleDeviceNotification(module, clazz)
        hookChargeNumberAnimation(module, clazz)

        val chargeMethods = clazz.declaredMethods.filter { it.name == "structModelForCharge" }
        if (chargeMethods.isEmpty()) {
            val candidateMethods = clazz.declaredMethods
                .filter { it.name.contains("charge", ignoreCase = true) || it.name.contains("battery", ignoreCase = true) }
                .joinToString { "${it.name}(${it.parameterTypes.joinToString { type -> type.simpleName }})" }
            logWarn(module, "structModelForCharge not found in ${clazz.name}; candidates=$candidateMethods")
            return
        }
        debug(module, "found ${chargeMethods.size} structModelForCharge method(s) in ${clazz.name}")

        chargeMethods
            .forEach { method ->
                synchronized(hookedMethods) {
                    if (!hookedMethods.add(method)) return@forEach
                }
                method.isAccessible = true
                module.hook(method).intercept { chain ->
                    val model = chain.proceed()
                    if (ConfigManager.getBoolean(PREF_ENABLED, false)) {
                        IslandDataManager.cacheBatteryBundle(chain.args.getOrNull(0) as? Bundle)
                        IslandDataManager.cacheBatteryStatus(chain.args.getOrNull(2))
                        IslandDataManager.refresh()
                        replaceChargeModel(model, module) ?: model
                    } else {
                        model
                    }
                }
            debug(module, "hooked ${clazz.name}.${method.name} params=${method.parameterTypes.joinToString { it.name }}")
            }
    }

    private fun hookIslandTextHolder(module: XposedModule, classLoader: ClassLoader) {
        listOf(
            "miui.systemui.dynamicisland.module.IslandImageTextViewHolder",
            "miui.systemui.dynamicisland.module.IslandIconFixedWidthTextHolder",
            "miui.systemui.dynamicisland.module.IslandImageTextView4Holder",
        ).forEach { className ->
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: return@forEach
            clazz.declaredMethods
                .filter { it.name == "bind" && it.parameterTypes.size == 2 }
                .forEach { method ->
                    var shouldHook = false
                    synchronized(hookedMethods) {
                        shouldHook = hookedMethods.add(method)
                    }
                    if (!shouldHook) return@forEach
                    method.isAccessible = true
                    module.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        val key = callString(chain.args.getOrNull(1), "getKey")
                        if (key == "charge") {
                            if (className.endsWith("IslandImageTextView4Holder")) {
                                chargeRightHolderRef = WeakReference(chain.thisObject)
                            } else {
                                chargeLeftHolderRef = WeakReference(chain.thisObject)
                            }
                            updateChargeViews(module)
                        }
                        result
                    }
                    debug(module, "hooked $className.${method.name} for charge text view refresh")
                }
        }
    }

    private fun hookChargeNumberAnimation(module: XposedModule, clazz: Class<*>) {
        val methods = clazz.declaredMethods.filter { it.name == "startAnimationForChargeNumber" }
        if (methods.isEmpty()) {
            logWarn(module, "startAnimationForChargeNumber not found in ${clazz.name}")
            return
        }
        methods.forEach { method ->
            synchronized(hookedMethods) {
                if (!hookedMethods.add(method)) return@forEach
            }
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (ConfigManager.getBoolean(PREF_ENABLED, false)) {
                    val duration = resolveChargeAnimationDurationMillis() ?: return@intercept result
                    val animator = readField(chain.thisObject, "valueAnimator") as? ValueAnimator
                    animator?.duration = duration
                    debug(module, "charge number animation duration overridden: ${duration}ms")
                }
                result
            }
            debug(module, "hooked ${clazz.name}.${method.name} for animation duration")
        }
    }

    private fun hookHandleDeviceNotification(module: XposedModule, clazz: Class<*>) {
        val methods = clazz.declaredMethods.filter { method ->
            method.name == "handleDeviceNotification" &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == Bundle::class.java
        }
        if (methods.isEmpty()) {
            logWarn(module, "handleDeviceNotification(Bundle, ...) not found in ${clazz.name}")
            return
        }
        methods.forEach { method ->
            synchronized(hookedMethods) {
                if (!hookedMethods.add(method)) return@forEach
            }
            method.isAccessible = true
            module.hook(method).intercept { chain ->
                val bundle = chain.args.getOrNull(0) as? Bundle
                if (ConfigManager.getBoolean(PREF_ENABLED, false) && bundle?.getString("notifyId") == "charge") {
                    applyChargeDuration(bundle, module, "handleDeviceNotification")
                }
                chain.proceed()
            }
            debug(module, "hooked ${clazz.name}.${method.name} for final duration override")
        }
    }

    private fun applyChargeDuration(bundle: Bundle?, module: XposedModule, source: String) {
        if (bundle == null) return
        val oldDuration = bundle.getLong("duration", -1L)
        val duration = resolveChargeDurationMillis() ?: return
        bundle.putLong("duration", duration)
        if (oldDuration != duration) {
            debug(module, "charge duration overridden by $source: ${oldDuration}ms -> ${duration}ms")
        }
    }

    private fun resolveChargeDurationMillis(): Long? {
        return when (ConfigManager.getString(PREF_DURATION_MODE, DURATION_DEFAULT)) {
            DURATION_CUSTOM -> ConfigManager.getInt(PREF_DURATION_SECONDS, 10)
                .coerceIn(1, 86400)
                .toLong() * 1000L
            // 最大 int 毫秒约 24 天；充电断开时 SystemUI 原逻辑仍会移除。
            DURATION_PERSISTENT -> Int.MAX_VALUE.toLong()
            else -> null
        }
    }

    private fun resolveChargeAnimationDurationMillis(): Long? {
        return when (ConfigManager.getString(PREF_DURATION_MODE, DURATION_DEFAULT)) {
            DURATION_CUSTOM -> ConfigManager.getInt(PREF_DURATION_SECONDS, 10)
                .coerceIn(1, 86400)
                .toLong() * 1000L
            // 常驻不能拉长电量动画，否则 SystemUI 的模型刷新会被拖慢，真实功率也不再更新。
            else -> null
        }
    }

    private fun replaceChargeModel(model: Any?, module: XposedModule): Any? {
        if (model == null) return model
        val leftMode = ConfigManager.getString(PREF_LEFT_MODE, MODE_DEFAULT)
        val rightMode = ConfigManager.getString(PREF_RIGHT_MODE, MODE_DEFAULT)
        val outerGlow = ConfigManager.getBoolean(PREF_OUTER_GLOW, false)
        if (leftMode == MODE_DEFAULT && rightMode == MODE_DEFAULT && !outerGlow) return model

        debug(
            module,
            "replaceChargeModel left=$leftMode right=$rightMode snapshot=${IslandDataManager.snapshot().toLogString()} model=$model",
        )

        var nextModel = model
        if (leftMode != MODE_DEFAULT) {
            formatMode(leftMode)?.let { replacement ->
                val currentModel = nextModel
                val copied = runCatching {
                    copySideText(module, currentModel, left = true, pattern = POWER_PATTERN, replacement = replacement)
                }.onFailure { error ->
                    logError(module, "left replacement failed: ${error.message}")
                }.getOrDefault(currentModel)
                debug(module, "left replacement mode=$leftMode value=$replacement replaced=${copied !== currentModel}")
                nextModel = copied
            } ?: debug(module, "left replacement skipped: no value for mode=$leftMode snapshot=${IslandDataManager.snapshot().toLogString()}")
        }
        if (rightMode != MODE_DEFAULT) {
            formatMode(rightMode)?.let { replacement ->
                val currentModel = nextModel
                val copied = runCatching {
                    copySideText(module, currentModel, left = false, pattern = LEVEL_PATTERN, replacement = replacement)
                }.onFailure { error ->
                    logError(module, "right replacement failed: ${error.message}")
                }.getOrDefault(currentModel)
                debug(module, "right replacement mode=$rightMode value=$replacement replaced=${copied !== currentModel}")
                nextModel = copied
            } ?: debug(module, "right replacement skipped: no value for mode=$rightMode snapshot=${IslandDataManager.snapshot().toLogString()}")
        }
        nextModel = copyGlowEffect(nextModel, outerGlow, module)
        debug(module, "replaceChargeModel result=$nextModel")
        return nextModel
    }

    private fun copyGlowEffect(model: Any, outerGlow: Boolean, module: XposedModule): Any {
        val accessors = modelAccessorsByClass.getOrPut(model.javaClass) { ModelAccessors.from(model) }
        val glowEffect = accessors.getGlowEffect.invoke(model)
        val newGlowEffect = when {
            glowEffect != null -> accessors.glowAccessors(glowEffect.javaClass).copy.invoke(glowEffect, outerGlow)
            outerGlow -> accessors.createGlowEffect(true)
            else -> null
        }
        if (newGlowEffect == glowEffect) return model
        val currentLeft = accessors.getLeft.invoke(model)
        val currentRight = accessors.getRight.invoke(model)
        debug(module, "charge outer glow overridden: enable=$outerGlow")
        return accessors.copy.invoke(model, currentLeft, currentRight, newGlowEffect)
    }

    private fun copySideText(module: XposedModule, model: Any, left: Boolean, pattern: Regex, replacement: String): Any {
        // DeviceNotificationModel 是 Kotlin data class，字段为 private final；用 copy 链生成新模型更稳。
        val accessors = modelAccessorsByClass.getOrPut(model.javaClass) { ModelAccessors.from(model) }
        val side = if (left) accessors.getLeft.invoke(model) else accessors.getRight.invoke(model)
        if (side == null) return model
        val sideAccessors = accessors.sideAccessors(side.javaClass)
        val textParams = sideAccessors.getTextParams.invoke(side) ?: return model
        val textAccessors = accessors.textAccessors(textParams.javaClass)
        val originalText = textAccessors.getText.invoke(textParams) as? String ?: return model
        if (!pattern.containsMatchIn(originalText)) return model
        val newText = pattern.replaceFirst(originalText, replacement)
        if (newText == originalText) return model
        debug(module, "copySideText side=${if (left) "left" else "right"} '$originalText' -> '$newText'")

        val textColor = textAccessors.getTextColor.invoke(textParams) as? Int
        val turnAnim = textAccessors.getTurnAnim.invoke(textParams) as? Boolean
        val newTextParams = textAccessors.copy.invoke(textParams, newText, textColor, turnAnim)

        val iconParams = sideAccessors.getIconParams.invoke(side)
        val newSide = sideAccessors.copy.invoke(side, newTextParams, iconParams)

        val currentLeft = accessors.getLeft.invoke(model)
        val currentRight = accessors.getRight.invoke(model)
        val glowEffect = accessors.getGlowEffect.invoke(model)
        return accessors.copy.invoke(
            model,
            if (left) newSide else currentLeft,
            if (left) currentRight else newSide,
            glowEffect,
        )
    }

    private fun updateChargeViews(module: XposedModule? = null) {
        if (!ConfigManager.getBoolean(PREF_ENABLED, false)) return
        updateChargeLeftView(module)
        updateChargeRightView(module)
    }

    private fun updateChargeLeftView(module: XposedModule? = null) {
        val leftMode = ConfigManager.getString(PREF_LEFT_MODE, MODE_DEFAULT)
        if (leftMode == MODE_DEFAULT) return
        val text = formatMode(leftMode) ?: return
        if (text == lastLeftViewText) return
        val holder = chargeLeftHolderRef?.get() ?: return
        mainHandler.post {
            runCatching {
                if (setHolderText(holder, text)) {
                    lastLeftViewText = text
                    module?.let { debug(it, "charge left view refreshed: $text") }
                }
            }.onFailure { error ->
                module?.let { logWarn(it, "charge left view refresh failed: ${error.message}") }
            }
        }
    }

    private fun updateChargeRightView(module: XposedModule? = null) {
        val rightMode = ConfigManager.getString(PREF_RIGHT_MODE, MODE_DEFAULT)
        if (rightMode == MODE_DEFAULT) return
        val text = formatMode(rightMode) ?: return
        if (text == lastRightViewText) return
        val holder = chargeRightHolderRef?.get() ?: return
        mainHandler.post {
            runCatching {
                if (setRightHolderText(holder, text)) {
                    lastRightViewText = text
                    module?.let { debug(it, "charge right view refreshed: $text") }
                }
            }.onFailure { error ->
                module?.let { logWarn(it, "charge right view refresh failed: ${error.message}") }
            }
        }
    }

    private fun setHolderText(holder: Any, text: String): Boolean {
        val textHolder = readField(holder, "textViewHolder") ?: holder
        val title = readField(textHolder, "title") ?: return false
        val content = readField(textHolder, "content")
        if (readField(textHolder, "fixedContainer") != null && content != null) {
            val parts = splitValueAndUnit(text)
            val titleUpdated = setTextIfChanged(title, parts.first)
            val contentUpdated = setTextIfChanged(content, parts.second)
            return titleUpdated || contentUpdated
        }
        return setTextIfChanged(title, text)
    }

    private fun setRightHolderText(holder: Any, text: String): Boolean {
        val chargeView = readField(holder, "chargeView")
        val chargeTextView = runCatching {
            chargeView?.javaClass?.getMethod("getTextView")?.invoke(chargeView)
        }.getOrNull()
        val powerSaveView = readField(holder, "powerSaveView")
        return listOfNotNull(chargeTextView, powerSaveView)
            .map { setTextIfChanged(it, text) }
            .any { it }
    }

    private fun setTextIfChanged(view: Any, text: String): Boolean {
        val current = callString(view, "getText")
        return if (current != null && current != text) {
            callSetText(view, text)
            true
        } else {
            false
        }
    }

    private fun splitValueAndUnit(text: String): Pair<String, String> {
        val match = Regex("^(-?\\d+(?:\\.\\d+)?)(.*)$").matchEntire(text)
        return if (match == null) {
            text to ""
        } else {
            match.groupValues[1] to match.groupValues[2]
        }
    }

    private fun debug(module: XposedModule, message: String) {
        log(module, message)
    }

    private fun logSnapshotIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastSnapshotLogAt < 3000L) return
        lastSnapshotLogAt = now
        ConfigManager.module()?.let { log(it, "battery snapshot ${IslandDataManager.snapshot().toLogString()}") }
    }

    private fun formatMode(mode: String): String? {
        return IslandDataManager.format(mode)
    }

    private fun callString(obj: Any?, methodName: String): String? = runCatching {
        obj?.javaClass?.getMethod(methodName)?.invoke(obj)?.toString()
    }.getOrNull()

    private fun callSetText(obj: Any, text: String) {
        val method = obj.javaClass.methods.firstOrNull { method ->
            method.name == "setText" && method.parameterTypes.size == 1 &&
                CharSequence::class.java.isAssignableFrom(method.parameterTypes[0])
        } ?: return
        method.invoke(obj, text)
    }

    private fun readField(obj: Any?, fieldName: String): Any? {
        if (obj == null) return null
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null) {
            runCatching {
                val field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(obj)
            }
            clazz = clazz.superclass
        }
        return null
    }

    private class ModelAccessors(
        val getLeft: Method,
        val getRight: Method,
        val getGlowEffect: Method,
        val copy: Method,
        private val sideAccessorsByClass: ConcurrentMap<Class<*>, SideAccessors> = ConcurrentHashMap(),
        private val textAccessorsByClass: ConcurrentMap<Class<*>, TextAccessors> = ConcurrentHashMap(),
        private val glowAccessorsByClass: ConcurrentMap<Class<*>, GlowAccessors> = ConcurrentHashMap(),
    ) {
        // 动画期间会每帧刷新电量，反射 Method 只按类缓存一次。
        fun sideAccessors(clazz: Class<*>): SideAccessors =
            sideAccessorsByClass.getOrPut(clazz) { SideAccessors.from(clazz) }

        fun textAccessors(clazz: Class<*>): TextAccessors =
            textAccessorsByClass.getOrPut(clazz) { TextAccessors.from(clazz) }

        fun glowAccessors(clazz: Class<*>): GlowAccessors =
            glowAccessorsByClass.getOrPut(clazz) { GlowAccessors.from(clazz) }

        fun createGlowEffect(enable: Boolean): Any? = runCatching {
            copy.parameterTypes[2].getConstructor(Boolean::class.javaPrimitiveType).newInstance(enable)
        }.getOrNull()

        companion object {
            fun from(model: Any): ModelAccessors {
                val clazz = model.javaClass
                return ModelAccessors(
                    getLeft = clazz.publicMethod("getLeft"),
                    getRight = clazz.publicMethod("getRight"),
                    getGlowEffect = clazz.publicMethod("getGlowEffect"),
                    copy = clazz.copyMethod(3),
                )
            }
        }
    }

    private class SideAccessors(
        val getTextParams: Method,
        val getIconParams: Method,
        val copy: Method,
    ) {
        companion object {
            fun from(clazz: Class<*>): SideAccessors = SideAccessors(
                getTextParams = clazz.publicMethod("getTextParams"),
                getIconParams = clazz.publicMethod("getIconParams"),
                copy = clazz.copyMethod(2),
            )
        }
    }

    private class TextAccessors(
        val getText: Method,
        val getTextColor: Method,
        val getTurnAnim: Method,
        val copy: Method,
    ) {
        companion object {
            fun from(clazz: Class<*>): TextAccessors = TextAccessors(
                getText = clazz.publicMethod("getText"),
                getTextColor = clazz.publicMethod("getTextColor"),
                getTurnAnim = clazz.publicMethod("getTurnAnim"),
                copy = clazz.copyMethod(3),
            )
        }
    }

    private class GlowAccessors(val copy: Method) {
        companion object {
            fun from(clazz: Class<*>): GlowAccessors = GlowAccessors(
                copy = clazz.copyMethod(1),
            )
        }
    }

    private fun Class<*>.publicMethod(name: String): Method =
        getMethod(name).apply { isAccessible = true }

    private fun Class<*>.copyMethod(parameterCount: Int): Method =
        declaredMethods
            .first { it.name == "copy" && it.parameterTypes.size == parameterCount }
            .apply { isAccessible = true }

    private val POWER_PATTERN = Regex("\\d+(?:\\.\\d+)?\\s*(?:W|w)(?:\\s*max)?")
    private val LEVEL_PATTERN = Regex("\\d+(?:\\.\\d+)?\\s*%")
}
