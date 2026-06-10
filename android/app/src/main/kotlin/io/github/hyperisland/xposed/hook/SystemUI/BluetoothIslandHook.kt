package io.github.hyperisland.xposed.hook

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.github.hyperisland.R
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import io.github.hyperisland.xposed.islanddispatch.definition.IslandRequest
import io.github.hyperisland.xposed.utils.moduleContext
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.concurrent.ConcurrentHashMap
import androidx.core.graphics.createBitmap

object BluetoothIslandHook : BaseHook() {

    private const val TAG = "HyperIsland[BluetoothIsland]"
    private const val PREF_ENABLED = "pref_bluetooth_island"
    private const val PREF_SHOW_DEVICE_NAME = "pref_bluetooth_island_show_device_name"
    private const val PREF_OUTER_GLOW = "pref_bluetooth_island_outer_glow"
    private const val PREF_OUTER_GLOW_COLOR = "pref_bluetooth_island_outer_glow_color"
    private const val NOTIF_ID = 0x48494254
    private const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    private const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    private const val DEVICE_NAME_UPDATE_DELAY_MS = 1500L
    private const val DEVICE_NAME_TIMEOUT_SECS = 1
    private const val BATTERY_TIMEOUT_SECS = 2

    @Volatile private var registered = false
    @Volatile private var moduleRef: XposedModule? = null
    @Volatile private var lastConnected = false
    @Volatile private var lastBattery = -1
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingNameRefreshes = ConcurrentHashMap<String, Runnable>()
    private val batteryByDevice = ConcurrentHashMap<String, Int>()
    private val deviceNameByDevice = ConcurrentHashMap<String, String>()

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        try {
            val method = param.defaultClassLoader
                .loadClass("android.app.Application")
                .getDeclaredMethod("onCreate")
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val app = chain.thisObject as? android.app.Application
                if (app != null) registerReceiver(app, module)
                result
            }
            log(module, "hooked Application.onCreate")
        } catch (e: Throwable) {
            logError(module, "hook failed: ${e.message}")
        }
    }

    private fun registerReceiver(context: Context, module: XposedModule) {
        moduleRef = module
        if (registered) return
        synchronized(this) {
            if (registered) return
            val appContext = context.applicationContext ?: context
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(ACTION_BATTERY_LEVEL_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(receiver, filter)
            }
            IslandDispatcher.register(appContext, module)
            registered = true
            logWarn(module, "bluetooth receiver registered, enabled=${ConfigManager.getBoolean(PREF_ENABLED, false)}")
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            val enabled = ConfigManager.getBoolean(PREF_ENABLED, false)
            val showDeviceName = ConfigManager.getBoolean(PREF_SHOW_DEVICE_NAME, true)
            val showDeviceNameStored = ConfigManager.contains(PREF_SHOW_DEVICE_NAME)
            moduleRef?.let {
                logWarn(
                    it,
                    "received bluetooth broadcast: action=$action enabled=$enabled " +
                        "showDeviceName=$showDeviceName stored=$showDeviceNameStored",
                )
            }
            if (!enabled) return
            if (action == ACTION_BATTERY_LEVEL_CHANGED) {
                val battery = readBatteryLevel(intent)
                cacheBattery(intent, battery)
                val deviceName = resolveDeviceName(intent)
                val key = deviceKey(intent, deviceName)
                val hasPendingNameRefresh = pendingNameRefreshes.containsKey(key)
                moduleRef?.let {
                    logWarn(
                        it,
                        "bluetooth battery broadcast: battery=$battery key=$key " +
                            "deviceName=$deviceName pendingNameRefresh=$hasPendingNameRefresh",
                    )
                }
                if (showDeviceName && hasPendingNameRefresh) {
                    if (battery in 0..100) lastBattery = battery
                    moduleRef?.let {
                        logWarn(
                            it,
                            "skip bluetooth battery update while device name is showing: key=$key cachedBattery=$lastBattery",
                        )
                    }
                    return
                }
                if (lastConnected && battery in 0..100) {
                    lastBattery = battery
                    moduleRef?.let { logWarn(it, "bluetooth battery changed: battery=$battery") }
                    postBluetoothIsland(
                        context = context,
                        connected = true,
                        battery = battery,
                        deviceName = deviceName,
                        rightTextOverride = null,
                        clearBeforePost = false,
                        timeoutSecs = BATTERY_TIMEOUT_SECS,
                    )
                } else {
                    moduleRef?.let {
                        logWarn(
                            it,
                            "skip bluetooth battery update: connected=$lastConnected battery=$battery key=$key",
                        )
                    }
                }
                return
            }
            val connected = when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> true
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> false
                else -> return
            }
            val deviceName = resolveDeviceName(intent)
            cacheDeviceName(intent, deviceName)
            lastConnected = connected
            val battery = readBatteryLevel(intent).takeIf { it in 0..100 }
                ?: readCachedBattery(intent)
                ?: lastBattery
            if (battery in 0..100) lastBattery = battery
            val key = deviceKey(intent, deviceName)
            moduleRef?.let {
                logWarn(
                    it,
                    "bluetooth state changed: connected=$connected battery=$battery " +
                        "deviceName=$deviceName key=$key showDeviceName=$showDeviceName",
                )
            }
            if (connected && battery !in 0..100 && !showDeviceName) {
                moduleRef?.let {
                    logWarn(
                        it,
                        "skip bluetooth connected island: battery unknown deviceName=$deviceName key=$key",
                    )
                }
                return
            }
            if (showDeviceName && connected) {
                moduleRef?.let {
                    logWarn(
                        it,
                        "post bluetooth device name first: deviceName=$deviceName key=$key battery=$battery",
                    )
                }
                postBluetoothIsland(
                    context = context,
                    connected = connected,
                    battery = battery,
                    deviceName = deviceName,
                    rightTextOverride = deviceName,
                    clearBeforePost = true,
                    timeoutSecs = DEVICE_NAME_TIMEOUT_SECS,
                )
                scheduleStatusRefresh(context, key, connected, battery, deviceName)
            } else {
                pendingNameRefreshes.remove(key)?.let { mainHandler.removeCallbacks(it) }
                moduleRef?.let {
                    logWarn(
                        it,
                        "post bluetooth status directly: connected=$connected deviceName=$deviceName key=$key battery=$battery",
                    )
                }
                postBluetoothIsland(
                    context = context,
                    connected = connected,
                    battery = battery,
                    deviceName = deviceName,
                    rightTextOverride = if (connected) null else deviceName,
                    clearBeforePost = true,
                    timeoutSecs = BATTERY_TIMEOUT_SECS,
                )
            }
        }
    }

    private fun readBatteryLevel(intent: Intent): Int {
        val fromExtra = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
        if (fromExtra in 0..100) return fromExtra
        val device = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        return device?.let { readBatteryLevelByReflection(it) } ?: -1
    }

    private fun cacheBattery(intent: Intent, battery: Int) {
        if (battery !in 0..100) return
        val device = getBluetoothDevice(intent) ?: return
        device.address?.let { batteryByDevice[it] = battery }
    }

    private fun cacheDeviceName(intent: Intent, deviceName: String) {
        if (deviceName.isBlank()) return
        val device = getBluetoothDevice(intent) ?: return
        device.address?.let { deviceNameByDevice[it] = deviceName }
    }

    private fun readCachedBattery(intent: Intent): Int? {
        val device = getBluetoothDevice(intent) ?: return null
        return device.address?.let { batteryByDevice[it] }
    }

    private fun readCachedDeviceName(intent: Intent): String? {
        val device = getBluetoothDevice(intent) ?: return null
        return device.address?.let { deviceNameByDevice[it] }
    }

    private fun resolveDeviceName(intent: Intent): String {
        val device = getBluetoothDevice(intent)
        val fromDevice = device?.name?.takeIf { it.isNotBlank() }
        val fromCache = readCachedDeviceName(intent)
        val resolved = fromDevice ?: fromCache ?: device?.address ?: "Bluetooth"
        moduleRef?.let {
            logWarn(
                it,
                "resolve bluetooth device name: resolved=$resolved fromDevice=$fromDevice " +
                    "fromCache=$fromCache address=${device?.address}",
            )
        }
        return resolved
    }

    private fun deviceKey(intent: Intent, deviceName: String): String {
        return getBluetoothDevice(intent)?.address ?: deviceName
    }

    private fun scheduleStatusRefresh(
        context: Context,
        key: String,
        connected: Boolean,
        battery: Int,
        deviceName: String,
    ) {
        pendingNameRefreshes.remove(key)?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            pendingNameRefreshes.remove(key)
            val refreshBattery = if (connected && lastBattery in 0..100) lastBattery else battery
            moduleRef?.let {
                logWarn(
                    it,
                    "delayed bluetooth status refresh: key=$key connected=$connected " +
                        "battery=$battery refreshBattery=$refreshBattery deviceName=$deviceName",
                )
            }
            postBluetoothIsland(
                context = context,
                connected = connected,
                battery = refreshBattery,
                deviceName = deviceName,
                rightTextOverride = null,
                clearBeforePost = false,
                timeoutSecs = BATTERY_TIMEOUT_SECS,
            )
        }
        pendingNameRefreshes[key] = runnable
        moduleRef?.let {
            logWarn(
                it,
                "schedule bluetooth status refresh: key=$key delayMs=$DEVICE_NAME_UPDATE_DELAY_MS " +
                    "pendingCount=${pendingNameRefreshes.size}",
            )
        }
        mainHandler.postDelayed(runnable, DEVICE_NAME_UPDATE_DELAY_MS)
    }

    private fun getBluetoothDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun readBatteryLevelByReflection(device: BluetoothDevice): Int {
        return try {
            (device.javaClass.getMethod("getBatteryLevel").invoke(device) as? Int)
                ?.takeIf { it in 0..100 }
                ?: -1
        } catch (e: Throwable) {
            moduleRef?.let { logWarn(it, "read battery level failed: ${e.message}") }
            -1
        }
    }

    private fun postBluetoothIsland(
        context: Context,
        connected: Boolean,
        battery: Int,
        deviceName: String,
        rightTextOverride: String?,
        clearBeforePost: Boolean,
        timeoutSecs: Int,
    ) {
        val mc = context.moduleContext()
        val title = if (connected) mc.getString(R.string.bluetooth_connected)
                    else mc.getString(R.string.bluetooth_disconnected)
        val content = rightTextOverride
            ?: if (battery in 0..100) mc.getString(R.string.bluetooth_battery, "$battery%")
               else mc.getString(R.string.bluetooth_battery_unknown)
        moduleRef?.let {
            logWarn(
                it,
                    "posting bluetooth island: title=$title right=$content connected=$connected " +
                    "battery=$battery deviceName=$deviceName override=${rightTextOverride != null} " +
                    "clearBeforePost=$clearBeforePost timeoutSecs=$timeoutSecs",
            )
        }
        val outerGlow = ConfigManager.getBoolean(PREF_OUTER_GLOW, false)
        val outerGlowColor = ConfigManager.getString(PREF_OUTER_GLOW_COLOR, "")
        IslandDispatcher.post(
            context,
            IslandRequest(
                title = title,
                content = content,
                icon = createBluetoothIcon(),
                notifId = NOTIF_ID,
                timeoutSecs = timeoutSecs,
                firstFloat = false,
                enableFloat = false,
                isOngoing = true,
                showNotification = false,
                preserveStatusBarSmallIcon = false,
                outerGlow = outerGlow,
                islandOuterGlow = outerGlow,
                outEffectColor = outerGlowColor,
                islandOuterGlowColor = outerGlowColor,
                sourcePackage = "com.android.systemui",
                sourceChannelId = "bluetooth",
                clearBeforePost = clearBeforePost,
            ),
        )
    }

    private fun createBluetoothIcon(): Icon {
        return Icon.createWithBitmap(createBluetoothBitmap())
    }

    private fun createBluetoothBitmap(): Bitmap {
        val size = 96
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        // Material Bluetooth glyph scaled from the 24dp system icon path.
        val path = Path().apply {
            moveTo(70.84f, 30.84f)
            lineTo(48f, 8f)
            lineTo(44f, 8f)
            lineTo(44f, 38.36f)
            lineTo(25.64f, 20f)
            lineTo(20f, 25.64f)
            lineTo(42.36f, 48f)
            lineTo(20f, 70.36f)
            lineTo(25.64f, 76f)
            lineTo(44f, 57.64f)
            lineTo(44f, 88f)
            lineTo(48f, 88f)
            lineTo(70.84f, 65.16f)
            lineTo(53.68f, 48f)
            close()

            moveTo(52f, 23.32f)
            lineTo(59.52f, 30.84f)
            lineTo(52f, 38.36f)
            close()

            moveTo(59.52f, 65.16f)
            lineTo(52f, 72.68f)
            lineTo(52f, 57.64f)
            close()
        }
        canvas.drawPath(path, iconPaint)
        return bitmap
    }
}
