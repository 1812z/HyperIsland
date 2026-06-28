package io.github.hyperisland.xposed.hook

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.BatteryManager
import android.os.Build
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.utils.HookUtils
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.math.abs
import kotlin.math.roundToInt

object ChargeIslandHook : BaseHook() {

    private const val TAG = "HyperIsland[ChargeIsland]"
    private const val PREF_ENABLED = "pref_charge_island"
    private const val PREF_LEFT_MODE = "pref_charge_island_left_mode"
    private const val PREF_RIGHT_MODE = "pref_charge_island_right_mode"
    private const val PREF_DURATION_MODE = "pref_charge_island_duration_mode"
    private const val PREF_DURATION_SECONDS = "pref_charge_island_duration_seconds"
    private const val PREF_OUTER_GLOW = "pref_charge_island_outer_glow"

    private const val MODE_DEFAULT = "default"
    private const val MODE_POWER = "power"
    private const val MODE_VOLTAGE = "voltage"
    private const val MODE_CURRENT = "current"
    private const val MODE_LEVEL = "level"
    private const val MODE_TEMPERATURE = "temperature"
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

    @Volatile private var appContext: Context? = null
    @Volatile private var receiverRegistered = false
    @Volatile private var battery = BatterySnapshot()
    @Volatile private var lastSnapshotLogAt = 0L

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (param.packageName != "com.android.systemui") return
        debug(module, "init package=${param.packageName} defaultCl=${param.defaultClassLoader}")
        hookApplicationOnCreate(module, param.defaultClassLoader)
        HookUtils.hookDynamicClassLoaders(module, ClassLoader.getSystemClassLoader()) { classLoader ->
            debug(module, "dynamic classloader created: $classLoader")
            hookChargeModel(module, classLoader)
        }
        hookChargeModel(module, param.defaultClassLoader)
    }

    private fun hookApplicationOnCreate(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val method = classLoader.loadClass("android.app.Application").getDeclaredMethod("onCreate")
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                (chain.thisObject as? Context)?.let { registerBatteryReceiver(it, module) }
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
                        cacheBatteryStatus(chain.args.getOrNull(2))
                        appContext?.let { refreshBatterySnapshot(it) }
                        replaceChargeModel(model, module) ?: model
                    } else {
                        model
                    }
                }
                debug(module, "hooked ${clazz.name}.${method.name} params=${method.parameterTypes.joinToString { it.name }}")
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
                    val duration = resolveChargeDurationMillis() ?: return@intercept result
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

    private fun registerBatteryReceiver(context: Context, module: XposedModule) {
        if (receiverRegistered) return
        synchronized(this) {
            if (receiverRegistered) return
            val ctx = context.applicationContext ?: context
            appContext = ctx
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val sticky = if (Build.VERSION.SDK_INT >= 33) {
                ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                ctx.registerReceiver(receiver, filter)
            }
            sticky?.let { updateBatterySnapshot(it) }
            refreshBatterySnapshot(ctx)
            receiverRegistered = true
            debug(module, "battery receiver registered")
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                updateBatterySnapshot(intent)
            }
        }
    }

    private fun refreshBatterySnapshot(context: Context) {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return
        val currentMicroAmp = runCatching {
            manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrDefault(Int.MIN_VALUE)
        if (currentMicroAmp == Int.MIN_VALUE || currentMicroAmp == 0) return
        val old = battery
        battery = old.copy(currentMicroAmp = currentMicroAmp)
        logSnapshotIfNeeded()
    }

    private fun updateBatterySnapshot(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).takeIf { it > 0 } ?: 100
        val percent = if (level >= 0) level * 100.0 / scale else null
        val voltageMilliVolt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0).takeIf { it > 0 }
        val temperatureCentiCelsius = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }
        battery = battery.copy(
            levelPercent = percent,
            levelText = percent?.let { formatLevel(it) },
            voltageMilliVolt = voltageMilliVolt,
            temperatureCentiCelsius = temperatureCentiCelsius,
        )
        logSnapshotIfNeeded()
    }

    private fun cacheBatteryStatus(status: Any?) {
        if (status == null) return
        val levelNumber = readNumber(status, "level") ?: callNumber(status, "getLevel")
        val level = levelNumber?.toDouble()
        if (level != null) {
            battery = battery.copy(levelPercent = level, levelText = formatLevel(level))
            logSnapshotIfNeeded()
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
            "replaceChargeModel left=$leftMode right=$rightMode snapshot=${battery.toLogString()} model=$model",
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
            } ?: debug(module, "left replacement skipped: no value for mode=$leftMode snapshot=${battery.toLogString()}")
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
            } ?: debug(module, "right replacement skipped: no value for mode=$rightMode snapshot=${battery.toLogString()}")
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

    private fun debug(module: XposedModule, message: String) {
        log(module, message)
    }

    private fun logSnapshotIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastSnapshotLogAt < 3000L) return
        lastSnapshotLogAt = now
        ConfigManager.module()?.let { log(it, "battery snapshot ${battery.toLogString()}") }
    }

    private fun formatMode(mode: String): String? {
        val snap = battery
        return when (mode) {
            MODE_POWER -> snap.powerWatt()?.let { "${it.roundToInt()}W" }
            MODE_VOLTAGE -> snap.voltageMilliVolt?.let { trimNumber(it / 1000.0, 2) + "V" }
            MODE_CURRENT -> snap.currentMicroAmp?.let { trimNumber(abs(it) / 1000000.0, 2) + "A" }
            MODE_LEVEL -> snap.levelText?.let { "$it%" }
            MODE_TEMPERATURE -> snap.temperatureCentiCelsius?.let { trimNumber(it / 10.0, 1) + "°C" }
            else -> null
        }
    }

    private fun BatterySnapshot.powerWatt(): Double? {
        val voltage = voltageMilliVolt ?: return null
        val current = currentMicroAmp ?: return null
        return abs(current.toDouble()) * voltage.toDouble() / 1000000000.0
    }

    private fun trimNumber(value: Double, maxDecimals: Int): String {
        val formatted = String.format(Locale.US, "%.${maxDecimals}f", value)
        return formatted.trimEnd('0').trimEnd('.')
    }

    private fun formatLevel(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.roundToInt().toString()
        } else {
            trimNumber(value, 2)
        }
    }

    private fun readNumber(obj: Any, fieldName: String): Number? {
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null) {
            runCatching {
                val field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(obj) as? Number
            }
            clazz = clazz.superclass
        }
        return null
    }

    private fun callNumber(obj: Any, methodName: String): Number? = runCatching {
        obj.javaClass.getMethod(methodName).invoke(obj) as? Number
    }.getOrNull()

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

    private data class BatterySnapshot(
        val levelPercent: Double? = null,
        val levelText: String? = null,
        val voltageMilliVolt: Int? = null,
        val currentMicroAmp: Int? = null,
        val temperatureCentiCelsius: Int? = null,
    ) {
        fun toLogString(): String {
            val powerWatt = if (voltageMilliVolt != null && currentMicroAmp != null) {
                (abs(currentMicroAmp.toDouble()) * voltageMilliVolt.toDouble() / 1000000000.0).roundToInt()
            } else {
                null
            }
            return "level=$levelText voltageMv=$voltageMilliVolt currentUa=$currentMicroAmp temp=${temperatureCentiCelsius?.let { it / 10.0 }} powerW=$powerWatt"
        }
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
