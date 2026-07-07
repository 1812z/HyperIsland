package io.github.hyperisland.xposed.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

object IslandDataManager {
    const val MODE_POWER = "power"
    const val MODE_VOLTAGE = "voltage"
    const val MODE_CURRENT = "current"
    const val MODE_LEVEL = "level"
    const val MODE_TEMPERATURE = "temperature"
    const val MODE_CPU_USAGE = "cpu_usage"
    const val MODE_MEMORY_USAGE = "memory_usage"
    const val MODE_MEMORY_USED = "memory_used"
    const val MODE_MEMORY_TOTAL = "memory_total"
    const val MODE_CPU_TEMPERATURE = "cpu_temperature"
    const val MODE_GPU_USAGE = "gpu_usage"
    const val MODE_GPU_FREQUENCY = "gpu_frequency"

    @Volatile private var appContext: Context? = null
    @Volatile private var receiverRegistered = false
    @Volatile private var battery = BatterySnapshot()
    @Volatile private var cpu = CpuSnapshot()
    @Volatile private var memory = MemorySnapshot()
    @Volatile private var gpu = GpuSnapshot()
    @Volatile private var lastPowerRefreshAt = 0L
    @Volatile private var lastSystemRefreshAt = 0L
    @Volatile private var lastCpuTimes: CpuTimes? = null
    @Volatile private var lastRegisterFailed = false
    @Volatile private var notifyScheduled = false
    private val listeners = ConcurrentHashMap.newKeySet<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun register(context: Context) {
        val ctx = context.applicationContext ?: context
        appContext = ctx
        readStickyBatteryIntent(ctx)?.let { updateBatterySnapshot(it) }
        refresh(ctx)
        if (receiverRegistered || lastRegisterFailed) return
        synchronized(this) {
            if (receiverRegistered || lastRegisterFailed) return
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            runCatching {
                val sticky = if (Build.VERSION.SDK_INT >= 33) {
                    ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    ctx.registerReceiver(receiver, filter)
                }
                sticky?.let { updateBatterySnapshot(it) }
                receiverRegistered = true
            }.onFailure {
                lastRegisterFailed = true
            }
        }
    }

    fun refresh() {
        appContext?.let { refresh(it) }
    }

    fun refresh(context: Context) {
        runCatching {
            val now = System.currentTimeMillis()
            if (now - lastPowerRefreshAt < POWER_REFRESH_INTERVAL_MS) return
            lastPowerRefreshAt = now

            readStickyBatteryIntent(context)?.let { updateBatterySnapshot(it) }
            refreshSystemSnapshot()
            val old = battery
            val sysfs = readSysfsBatterySnapshot()
            val managerCurrent = readBatteryManagerCurrent(context)
            val current = sysfs.currentMicroAmp ?: managerCurrent
            if (current == null && sysfs.voltageMilliVolt == null) return
            setBattery(
                old.copy(
                    voltageMilliVolt = sysfs.voltageMilliVolt ?: old.voltageMilliVolt,
                    voltageSource = sysfs.voltageSource ?: old.voltageSource,
                    currentMicroAmp = current ?: old.currentMicroAmp,
                    currentSource = when {
                        sysfs.currentMicroAmp != null -> sysfs.currentSource
                        managerCurrent != null -> "BatteryManager"
                        else -> old.currentSource
                    },
                ),
            )
        }
    }

    fun cacheBatteryStatus(status: Any?) {
        runCatching {
            if (status == null) return
            val levelNumber = readNumber(status, "level") ?: callNumber(status, "getLevel")
            val level = levelNumber?.toDouble() ?: return
            setBattery(battery.copy(levelPercent = level, levelText = formatLevel(level)))
        }
    }

    fun cacheBatteryBundle(bundle: android.os.Bundle?) {
        runCatching {
            if (bundle == null) return
            val level = readBundleNumber(bundle, "level", "batteryLevel", "battery_level", "chargeLevel")
            val voltage = readBundleNumber(bundle, "voltage", "batteryVoltage", "battery_voltage")
            val current = readBundleNumber(bundle, "current", "batteryCurrent", "battery_current", "currentNow")
            val temperature = readBundleNumber(bundle, "temperature", "batteryTemperature", "battery_temperature")
            var next = battery
            level?.let { next = next.copy(levelPercent = it.toDouble(), levelText = formatLevel(it.toDouble())) }
            voltage?.let { value ->
                normalizeVoltageToMilliVolt(value)?.let {
                    next = next.copy(voltageMilliVolt = it, voltageSource = "charge_bundle")
                }
            }
            current?.let { value ->
                normalizeCurrentToMicroAmp(value)?.let {
                    next = next.copy(currentMicroAmp = it, currentSource = "charge_bundle")
                }
            }
            temperature?.let { value ->
                normalizeTemperatureToDeciCelsius(value)?.let {
                    next = next.copy(temperatureCentiCelsius = it)
                }
            }
            setBattery(next)
        }
    }

    fun snapshot(): BatterySnapshot = battery

    fun format(mode: String): String? {
        return runCatching {
            refresh()
            val snap = battery
            when (mode) {
                MODE_POWER -> snap.powerWatt()?.let { "${it.roundToInt()}W" }
                MODE_VOLTAGE -> snap.voltageMilliVolt?.let { trimNumber(it / 1000.0, 2) + "V" }
                MODE_CURRENT -> snap.currentMicroAmp?.let { trimNumber(abs(it) / 1000000.0, 2) + "A" }
                MODE_LEVEL -> snap.levelText?.let { "$it%" }
                MODE_TEMPERATURE -> snap.temperatureCentiCelsius?.let { trimNumber(it / 10.0, 1) + "°C" }
                MODE_CPU_USAGE -> cpu.usagePercent?.let { trimNumber(it, 0) + "%" }
                MODE_MEMORY_USAGE -> memory.usagePercent()?.let { trimNumber(it, 0) + "%" }
                MODE_MEMORY_USED -> memory.usedKb?.let { formatBytesFromKb(it) }
                MODE_MEMORY_TOTAL -> memory.totalKb?.let { formatBytesFromKb(it) }
                MODE_CPU_TEMPERATURE -> cpu.temperatureMilliCelsius?.let { formatTemperatureMilliCelsius(it) }
                MODE_GPU_USAGE -> gpu.usagePercent?.let { trimNumber(it, 0) + "%" }
                MODE_GPU_FREQUENCY -> gpu.frequencyHz?.let { formatFrequencyHz(it) }
                else -> null
            }
        }.getOrNull()
    }

    fun keepIslandTexts(): Pair<String, String> {
        return " " to ""
    }

    fun renderExpression(expression: String): String {
        if (expression.isBlank()) return ""
        return PLACEHOLDER_PATTERN.replace(expression) { match ->
            when (match.value) {
                "{battery.power}" -> format(MODE_POWER)
                "{battery.voltage}" -> format(MODE_VOLTAGE)
                "{battery.current}" -> format(MODE_CURRENT)
                "{battery.level}" -> format(MODE_LEVEL)
                "{battery.temperature}" -> format(MODE_TEMPERATURE)
                "{cpu.usage}" -> format(MODE_CPU_USAGE)
                "{memory.usage}" -> format(MODE_MEMORY_USAGE)
                "{memory.used}" -> format(MODE_MEMORY_USED)
                "{memory.total}" -> format(MODE_MEMORY_TOTAL)
                "{cpu.temperature}" -> format(MODE_CPU_TEMPERATURE)
                "{gpu.usage}" -> format(MODE_GPU_USAGE)
                "{gpu.frequency}" -> format(MODE_GPU_FREQUENCY)
                else -> null
            } ?: ""
        }
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                updateBatterySnapshot(intent)
                refresh(context)
            }
        }
    }

    private fun readStickyBatteryIntent(context: Context): Intent? = runCatching {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(null, filter)
        }
    }.getOrNull()

    private fun updateBatterySnapshot(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).takeIf { it > 0 } ?: 100
        val percent = if (level >= 0) level * 100.0 / scale else null
        val voltageMilliVolt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0).takeIf { it > 0 }
        val temperatureCentiCelsius = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }
        setBattery(
            battery.copy(
                levelPercent = percent,
                levelText = percent?.let { formatLevel(it) },
                voltageMilliVolt = voltageMilliVolt,
                voltageSource = voltageMilliVolt?.let { "BATTERY_CHANGED" },
                temperatureCentiCelsius = temperatureCentiCelsius,
            ),
        )
    }

    private fun setBattery(next: BatterySnapshot) {
        if (next == battery) return
        battery = next
        scheduleNotifyListeners()
    }

    private fun scheduleNotifyListeners() {
        if (notifyScheduled) return
        notifyScheduled = true
        mainHandler.postDelayed({
            notifyScheduled = false
            listeners.forEach { listener -> runCatching { listener() } }
        }, NOTIFY_INTERVAL_MS)
    }

    private fun readBatteryManagerCurrent(context: Context): Int? {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        val currentMicroAmp = runCatching {
            manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrDefault(Int.MIN_VALUE)
        return currentMicroAmp.takeIf { it != Int.MIN_VALUE && it != 0 }
    }

    private fun refreshSystemSnapshot() {
        val now = System.currentTimeMillis()
        if (now - lastSystemRefreshAt < SYSTEM_REFRESH_INTERVAL_MS) return
        lastSystemRefreshAt = now
        readCpuTimes()?.let { current ->
            val previous = lastCpuTimes
            lastCpuTimes = current
            if (previous != null) {
                val totalDelta = current.total - previous.total
                val idleDelta = current.idle - previous.idle
                if (totalDelta > 0L && idleDelta >= 0L) {
                    setCpu(CpuSnapshot(((totalDelta - idleDelta).toDouble() * 100.0 / totalDelta).coerceIn(0.0, 100.0)))
                }
            }
        }
        readCpuTemperatureMilliCelsius()?.let { temperature ->
            setCpu(cpu.copy(temperatureMilliCelsius = temperature))
        }
        readGpuSnapshot()?.let { setGpu(it) }
        readMemorySnapshot()?.let { setMemory(it) }
    }

    private fun setCpu(next: CpuSnapshot) {
        if (next == cpu) return
        cpu = next
        scheduleNotifyListeners()
    }

    private fun setGpu(next: GpuSnapshot) {
        if (next == gpu) return
        gpu = next
        scheduleNotifyListeners()
    }

    private fun setMemory(next: MemorySnapshot) {
        if (next == memory) return
        memory = next
        scheduleNotifyListeners()
    }

    private fun readCpuTimes(): CpuTimes? = runCatching {
        val line = File("/proc/stat").bufferedReader().use { it.readLine() } ?: return null
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.firstOrNull() != "cpu" || parts.size < 5) return null
        val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
        if (values.size < 4) return null
        val idle = values.getOrElse(3) { 0L } + values.getOrElse(4) { 0L }
        CpuTimes(total = values.sum(), idle = idle)
    }.getOrNull()

    private fun readMemorySnapshot(): MemorySnapshot? = runCatching {
        val values = readKeyValueFile(File("/proc/meminfo"))
        val total = values["MemTotal"]?.toKbOrNull() ?: return null
        val available = values["MemAvailable"]?.toKbOrNull()
            ?: estimateAvailableMemoryKb(values)
            ?: return null
        MemorySnapshot(totalKb = total, availableKb = available.coerceIn(0L, total))
    }.getOrNull()

    private fun estimateAvailableMemoryKb(values: Map<String, String>): Long? {
        val free = values["MemFree"]?.toKbOrNull() ?: return null
        val buffers = values["Buffers"]?.toKbOrNull() ?: 0L
        val cached = values["Cached"]?.toKbOrNull() ?: 0L
        val reclaimable = values["SReclaimable"]?.toKbOrNull() ?: 0L
        val shmem = values["Shmem"]?.toKbOrNull() ?: 0L
        return free + buffers + cached + reclaimable - shmem
    }

    private fun readCpuTemperatureMilliCelsius(): Int? {
        CPU_THERMAL_TYPE_KEYWORDS.forEach { keyword ->
            findThermalZoneTemperature(keyword)?.let { return it }
        }
        CPU_THERMAL_FALLBACK_PATHS.forEach { path ->
            readTemperatureMilliCelsius(File(path))?.let { return it }
        }
        return null
    }

    private fun findThermalZoneTemperature(keyword: String): Int? = runCatching {
        val thermalDir = File("/sys/class/thermal")
        val zones = thermalDir.listFiles()
            ?.filter { it.name.startsWith("thermal_zone") }
            .orEmpty()
        for (zone in zones) {
            val type = readTextFile(File(zone, "type"))?.lowercase(Locale.US) ?: continue
            if (type.contains(keyword)) {
                val temperature = readTemperatureMilliCelsius(File(zone, "temp"))
                if (temperature != null) return@runCatching temperature
            }
        }
        null
    }.getOrNull()

    private fun readGpuSnapshot(): GpuSnapshot? = runCatching {
        val usage = readGpuUsagePercent()
        val frequency = readFirstLong(GPU_FREQUENCY_PATHS)?.let { normalizeFrequencyHz(it) }
        if (usage == null && frequency == null) {
            null
        } else {
            gpu.copy(
                usagePercent = usage ?: gpu.usagePercent,
                frequencyHz = frequency ?: gpu.frequencyHz,
            )
        }
    }.getOrNull()

    private fun readGpuUsagePercent(): Double? {
        readFirstNumber(GPU_BUSY_PERCENT_PATHS)?.let { return it.coerceIn(0.0, 100.0) }
        for (path in GPU_BUSY_TIME_PATHS) {
            val parts = readTextFile(File(path))
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.mapNotNull { it.toLongOrNull() }
                ?: continue
            if (parts.size >= 2 && parts[1] > 0L) {
                return (parts[0].toDouble() * 100.0 / parts[1].toDouble()).coerceIn(0.0, 100.0)
            }
        }
        return null
    }

    private fun readSysfsBatterySnapshot(): SysfsBatterySnapshot {
        SYSFS_POWER_SUPPLY_NAMES.forEach { name ->
            val dir = File("/sys/class/power_supply/$name")
            val fromUevent = readPowerSupplyUevent(dir, name)
            if (fromUevent.currentMicroAmp != null || fromUevent.voltageMilliVolt != null) return fromUevent

            val current = readLongFile(File(dir, "current_now"))
            val voltage = readLongFile(File(dir, "voltage_now"))
            if (current != null || voltage != null) {
                return SysfsBatterySnapshot(
                    currentMicroAmp = current?.toIntOrNull(),
                    currentSource = current?.let { "$name/current_now" },
                    voltageMilliVolt = voltage?.toMilliVoltOrNull(),
                    voltageSource = voltage?.let { "$name/voltage_now" },
                )
            }
        }
        return SysfsBatterySnapshot()
    }

    private fun readPowerSupplyUevent(dir: File, name: String): SysfsBatterySnapshot {
        val values = readKeyValueFile(File(dir, "uevent"))
        val current = values["POWER_SUPPLY_CURRENT_NOW"]
            ?: values["POWER_SUPPLY_BATT_CURRENT_NOW"]
            ?: values["POWER_SUPPLY_CONSTANT_CHARGE_CURRENT"]
        val voltage = values["POWER_SUPPLY_VOLTAGE_NOW"]
            ?: values["POWER_SUPPLY_BATT_VOLTAGE_NOW"]
        return SysfsBatterySnapshot(
            currentMicroAmp = current?.toIntOrNull(),
            currentSource = current?.let { "$name/uevent" },
            voltageMilliVolt = voltage?.toLongOrNull()?.toMilliVoltOrNull(),
            voltageSource = voltage?.let { "$name/uevent" },
        )
    }

    private fun readKeyValueFile(file: File): Map<String, String> = runCatching {
        if (!file.canRead()) return emptyMap()
        file.readLines().mapNotNull { line ->
            val equalIndex = line.indexOf('=')
            val colonIndex = line.indexOf(':')
            val index = when {
                equalIndex > 0 && colonIndex > 0 -> minOf(equalIndex, colonIndex)
                equalIndex > 0 -> equalIndex
                colonIndex > 0 -> colonIndex
                else -> -1
            }
            if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun readLongFile(file: File): Long? = runCatching {
        if (!file.canRead()) return null
        file.readText().trim().toLongOrNull()
    }.getOrNull()

    private fun readTextFile(file: File): String? = runCatching {
        if (!file.canRead()) return null
        file.readText().trim()
    }.getOrNull()

    private fun readFirstLong(paths: List<String>): Long? {
        paths.forEach { path ->
            readLongFile(File(path))?.let { return it }
        }
        return null
    }

    private fun readFirstNumber(paths: List<String>): Double? {
        paths.forEach { path ->
            val text = readTextFile(File(path)) ?: return@forEach
            NUMBER_PATTERN.find(text)?.value?.toDoubleOrNull()?.let { return it }
        }
        return null
    }

    private fun Long.toIntOrNull(): Int? = takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

    private fun Long.toMilliVoltOrNull(): Int? {
        if (this <= 0L) return null
        val milliVolt = if (this > 100000L) this / 1000L else this
        return milliVolt.toIntOrNull()
    }

    private fun BatterySnapshot.powerWatt(): Double? {
        val voltage = voltageMilliVolt ?: return null
        val current = currentMicroAmp ?: return null
        return abs(current.toDouble()) * voltage.toDouble() / 1000000000.0
    }

    private fun trimNumber(value: Double, maxDecimals: Int): String {
        if (maxDecimals <= 0) return String.format(Locale.US, "%.0f", value)
        val formatted = String.format(Locale.US, "%.${maxDecimals}f", value)
        return formatted.trimEnd('0').trimEnd('.')
    }

    private fun formatBytesFromKb(kb: Long): String {
        val mb = kb / 1024.0
        return if (mb >= 1024.0) {
            trimNumber(mb / 1024.0, 1) + "GB"
        } else {
            trimNumber(mb, 0) + "MB"
        }
    }

    private fun formatTemperatureMilliCelsius(value: Int): String {
        return trimNumber(value / 1000.0, 1) + "°C"
    }

    private fun formatFrequencyHz(value: Long): String {
        return if (value >= 1000000000L) {
            trimNumber(value / 1000000000.0, 2) + "GHz"
        } else {
            trimNumber(value / 1000000.0, 0) + "MHz"
        }
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

    private fun readBundleNumber(bundle: android.os.Bundle, vararg keys: String): Number? {
        keys.forEach { key ->
            if (!bundle.containsKey(key)) return@forEach
            val value = bundle.get(key)
            when (value) {
                is Number -> return value
                is String -> value.toDoubleOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun String.toKbOrNull(): Long? {
        val parts = trim().split(Regex("\\s+"))
        val value = parts.firstOrNull()?.toLongOrNull() ?: return null
        val unit = parts.getOrNull(1)?.lowercase(Locale.US)
        return when (unit) {
            null, "kb" -> value
            "mb" -> value * 1024L
            "gb" -> value * 1024L * 1024L
            else -> value
        }
    }

    private fun readTemperatureMilliCelsius(file: File): Int? {
        val raw = readLongFile(file) ?: return null
        if (raw == 0L) return null
        val milliCelsius = when {
            abs(raw) > 1000000L -> raw / 1000L
            abs(raw) < 1000L -> raw * 1000L
            else -> raw
        }
        return milliCelsius.toIntOrNull()
    }

    private fun normalizeFrequencyHz(raw: Long): Long? {
        if (raw <= 0L) return null
        return when {
            raw < 10000L -> raw * 1000000L
            raw < 10000000L -> raw * 1000L
            else -> raw
        }
    }

    private fun normalizeVoltageToMilliVolt(value: Number): Int? {
        val raw = value.toDouble()
        if (raw <= 0.0) return null
        val mv = when {
            raw > 100000.0 -> raw / 1000.0
            raw < 100.0 -> raw * 1000.0
            else -> raw
        }
        return mv.roundToInt().takeIf { it > 0 }
    }

    private fun normalizeCurrentToMicroAmp(value: Number): Int? {
        val raw = value.toDouble()
        if (raw == 0.0) return null
        val ua = if (abs(raw) < 1000.0) raw * 1000000.0 else raw
        return ua.roundToInt().takeIf { it != 0 }
    }

    private fun normalizeTemperatureToDeciCelsius(value: Number): Int? {
        val raw = value.toDouble()
        val deci = if (abs(raw) < 100.0) raw * 10.0 else raw
        return deci.roundToInt()
    }

    data class BatterySnapshot(
        val levelPercent: Double? = null,
        val levelText: String? = null,
        val voltageMilliVolt: Int? = null,
        val voltageSource: String? = null,
        val currentMicroAmp: Int? = null,
        val currentSource: String? = null,
        val temperatureCentiCelsius: Int? = null,
    ) {
        fun toLogString(): String {
            val powerWatt = if (voltageMilliVolt != null && currentMicroAmp != null) {
                (abs(currentMicroAmp.toDouble()) * voltageMilliVolt.toDouble() / 1000000000.0).roundToInt()
            } else {
                null
            }
            return "level=$levelText voltageMv=$voltageMilliVolt voltageSource=$voltageSource currentUa=$currentMicroAmp currentSource=$currentSource temp=${temperatureCentiCelsius?.let { it / 10.0 }} powerW=$powerWatt"
        }
    }

    data class CpuSnapshot(
        val usagePercent: Double? = null,
        val temperatureMilliCelsius: Int? = null,
    )

    data class GpuSnapshot(
        val usagePercent: Double? = null,
        val frequencyHz: Long? = null,
    )

    data class MemorySnapshot(
        val totalKb: Long? = null,
        val availableKb: Long? = null,
    ) {
        val usedKb: Long?
            get() {
                val total = totalKb ?: return null
                val available = availableKb ?: return null
                return (total - available).coerceAtLeast(0L)
            }

        fun usagePercent(): Double? {
            val total = totalKb ?: return null
            if (total <= 0L) return null
            val used = usedKb ?: return null
            return used.toDouble() * 100.0 / total
        }
    }

    private data class CpuTimes(
        val total: Long,
        val idle: Long,
    )

    private data class SysfsBatterySnapshot(
        val currentMicroAmp: Int? = null,
        val currentSource: String? = null,
        val voltageMilliVolt: Int? = null,
        val voltageSource: String? = null,
    )

    private val SYSFS_POWER_SUPPLY_NAMES = listOf("bms", "battery")
    private val PLACEHOLDER_PATTERN = Regex("\\{[a-zA-Z0-9_.]+\\}")
    private val NUMBER_PATTERN = Regex("-?\\d+(?:\\.\\d+)?")
    private val CPU_THERMAL_TYPE_KEYWORDS = listOf("cpu", "soc", "apss", "tsens_tz_sensor")
    private val CPU_THERMAL_FALLBACK_PATHS = listOf(
        "/sys/class/thermal/thermal_zone0/temp",
        "/sys/devices/virtual/thermal/thermal_zone0/temp",
    )
    private val GPU_BUSY_PERCENT_PATHS = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
        "/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/gpu_busy_percentage",
    )
    private val GPU_BUSY_TIME_PATHS = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpubusy",
        "/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/gpubusy",
    )
    private val GPU_FREQUENCY_PATHS = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpuclk",
        "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
        "/sys/class/devfreq/kgsl-3d0/cur_freq",
        "/sys/class/devfreq/1c00000.qcom,kgsl-3d0/cur_freq",
    )
    private const val POWER_REFRESH_INTERVAL_MS = 1000L
    private const val SYSTEM_REFRESH_INTERVAL_MS = 1000L
    private const val NOTIFY_INTERVAL_MS = 1000L
}
