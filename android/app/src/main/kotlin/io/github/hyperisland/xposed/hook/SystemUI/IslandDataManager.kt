package io.github.hyperisland.xposed.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
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

    @Volatile private var appContext: Context? = null
    @Volatile private var receiverRegistered = false
    @Volatile private var battery = BatterySnapshot()
    @Volatile private var lastPowerRefreshAt = 0L
    private val listeners = ConcurrentHashMap.newKeySet<() -> Unit>()

    fun register(context: Context) {
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
            refresh(ctx)
            receiverRegistered = true
        }
    }

    fun refresh() {
        appContext?.let { refresh(it) }
    }

    fun refresh(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastPowerRefreshAt < POWER_REFRESH_INTERVAL_MS) return
        lastPowerRefreshAt = now

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

    fun cacheBatteryStatus(status: Any?) {
        if (status == null) return
        val levelNumber = readNumber(status, "level") ?: callNumber(status, "getLevel")
        val level = levelNumber?.toDouble() ?: return
        setBattery(battery.copy(levelPercent = level, levelText = formatLevel(level)))
    }

    fun snapshot(): BatterySnapshot = battery

    fun format(mode: String): String? {
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

    fun keepIslandTexts(): Pair<String, String> {
        val title = format(MODE_POWER) ?: format(MODE_LEVEL) ?: " "
        val content = listOfNotNull(format(MODE_CURRENT), format(MODE_TEMPERATURE))
            .joinToString(" ")
        return title to content
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
        listeners.forEach { listener -> runCatching { listener() } }
    }

    private fun readBatteryManagerCurrent(context: Context): Int? {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        val currentMicroAmp = runCatching {
            manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrDefault(Int.MIN_VALUE)
        return currentMicroAmp.takeIf { it != Int.MIN_VALUE && it != 0 }
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
            val index = line.indexOf('=')
            if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun readLongFile(file: File): Long? = runCatching {
        if (!file.canRead()) return null
        file.readText().trim().toLongOrNull()
    }.getOrNull()

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

    private data class SysfsBatterySnapshot(
        val currentMicroAmp: Int? = null,
        val currentSource: String? = null,
        val voltageMilliVolt: Int? = null,
        val voltageSource: String? = null,
    )

    private val SYSFS_POWER_SUPPLY_NAMES = listOf("bms", "battery")
    private const val POWER_REFRESH_INTERVAL_MS = 1000L
}
