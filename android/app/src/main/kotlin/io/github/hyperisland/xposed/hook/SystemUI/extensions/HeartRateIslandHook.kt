package io.github.hyperisland.xposed.hook.SystemUI.extensions

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
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
import android.os.SystemClock
import androidx.core.graphics.createBitmap
import io.github.hyperisland.R
import io.github.hyperisland.xposed.ConfigManager
import io.github.hyperisland.xposed.hook.BaseHook
import io.github.hyperisland.xposed.islanddispatch.IslandDispatcher
import io.github.hyperisland.xposed.islanddispatch.definition.HeartRateIslandContract
import io.github.hyperisland.xposed.islanddispatch.definition.IslandRequest
import io.github.hyperisland.xposed.utils.moduleContext
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.util.UUID

/** Connects to the standard BLE Heart Rate Service and mirrors measurements to an island. */
object HeartRateIslandHook : BaseHook() {

    private const val TAG = "HyperIsland[HeartRateIsland]"
    private const val PREF_ENABLED = "pref_heart_rate_island"
    private const val PREF_READ_MODE = "pref_heart_rate_island_read_mode"
    private const val PREF_DEVICE_ADDRESS = "pref_heart_rate_island_device_address"
    private const val PREF_SHOW_UNIT = "pref_heart_rate_island_show_unit"
    private const val READ_MODE_BROADCAST = "heart_rate_broadcast"
    private const val NOTIF_ID = 0x48495254
    private const val ISLAND_TIMEOUT_SECONDS = 3
    private const val MIN_UPDATE_INTERVAL_MS = 1_000L
    private const val SETUP_RETRY_DELAY_MS = 60_000L

    private val heartRateServiceUuid = uuid16(0x180D)
    private val heartRateMeasurementUuid = uuid16(0x2A37)
    private val clientConfigUuid = uuid16(0x2902)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var moduleRef: XposedModule? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var activeGatt: BluetoothGatt? = null
    @Volatile private var activeGattAutoConnect = false
    @Volatile private var connected = false
    @Volatile private var initialized = false
    @Volatile private var runtimeEnabled = false
    @Volatile private var runtimeReadMode = READ_MODE_BROADCAST
    @Volatile private var runtimeDeviceAddress = ""
    @Volatile private var runtimeShowUnit = false
    private var latestBpm = 0
    private var lastPostElapsed = 0L
    private var measurementScheduled = false
    private val heartRateIcon by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Icon.createWithBitmap(createHeartRateBitmap())
    }

    private val setupRetryRunnable = Runnable { connectConfiguredDevice(autoConnect = true) }
    private val measurementRunnable = Runnable {
        measurementScheduled = false
        val bpm = latestBpm
        if (bpm > 0) postHeartRate(bpm)
    }

    override fun getTag() = TAG

    override fun onInit(module: XposedModule, param: PackageLoadedParam) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true
        }
        moduleRef = module
        runCatching {
            val method = param.defaultClassLoader
                .loadClass("android.app.Application")
                .getDeclaredMethod("onCreate")
            module.hook(method).intercept { chain ->
                val result = chain.proceed()
                val app = chain.thisObject as? android.app.Application
                if (app != null && appContext == null) initialize(app)
                result
            }
            log(module, "hooked Application.onCreate")
        }.onFailure {
            initialized = false
            logError(module, "hook failed: ${it.message}")
        }
    }

    private fun initialize(context: Context) {
        val applicationContext = context.applicationContext ?: context
        appContext = applicationContext
        IslandDispatcher.register(applicationContext, moduleRef ?: return)
        runtimeEnabled = ConfigManager.getBoolean(PREF_ENABLED, false)
        runtimeReadMode = ConfigManager.getString(PREF_READ_MODE, READ_MODE_BROADCAST)
        runtimeDeviceAddress = ConfigManager.getString(PREF_DEVICE_ADDRESS, "").trim()
        runtimeShowUnit = ConfigManager.getBoolean(PREF_SHOW_UNIT, false)
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            applicationContext.registerReceiver(
                bluetoothStateReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            @Suppress("DEPRECATION")
            applicationContext.registerReceiver(bluetoothStateReceiver, filter)
        }
        val configFilter = IntentFilter(HeartRateIslandContract.ACTION_CONFIG_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            applicationContext.registerReceiver(
                configReceiver,
                configFilter,
                IslandDispatcher.PERM,
                mainHandler,
                Context.RECEIVER_EXPORTED,
            )
        } else {
            @Suppress("DEPRECATION")
            applicationContext.registerReceiver(
                configReceiver,
                configFilter,
                IslandDispatcher.PERM,
                mainHandler,
            )
        }
        connectConfiguredDevice()
    }

    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val previousEnabled = runtimeEnabled
            val previousReadMode = runtimeReadMode
            val previousDeviceAddress = runtimeDeviceAddress
            runtimeEnabled = intent.getBooleanExtra(
                HeartRateIslandContract.EXTRA_ENABLED,
                runtimeEnabled,
            )
            runtimeReadMode = intent.getStringExtra(HeartRateIslandContract.EXTRA_READ_MODE)
                ?: runtimeReadMode
            runtimeDeviceAddress = intent.getStringExtra(
                HeartRateIslandContract.EXTRA_DEVICE_ADDRESS,
            )?.trim() ?: runtimeDeviceAddress
            runtimeShowUnit = intent.getBooleanExtra(
                HeartRateIslandContract.EXTRA_SHOW_UNIT,
                runtimeShowUnit,
            )
            val forceReconnect = intent.getBooleanExtra(
                HeartRateIslandContract.EXTRA_FORCE_RECONNECT,
                false,
            )
            mainHandler.removeCallbacks(setupRetryRunnable)
            if (!runtimeEnabled) {
                closeGatt()
                cancelIsland()
                moduleRef?.let { log(it, "heart rate island disabled at runtime") }
            } else if (forceReconnect || !previousEnabled ||
                previousReadMode != runtimeReadMode ||
                previousDeviceAddress != runtimeDeviceAddress
            ) {
                moduleRef?.let {
                    log(it, "heart rate config changed, reconnect address=$runtimeDeviceAddress")
                }
                closeGatt()
                cancelIsland()
                connectConfiguredDevice(autoConnect = false)
            }
        }
    }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) ==
                BluetoothAdapter.STATE_ON
            ) {
                connectConfiguredDevice(autoConnect = false)
            } else {
                mainHandler.removeCallbacks(setupRetryRunnable)
                closeGatt()
                cancelIsland()
            }
        }
    }

    @Suppress("MissingPermission")
    private fun connectConfiguredDevice(autoConnect: Boolean = false) {
        mainHandler.removeCallbacks(setupRetryRunnable)
        val context = appContext ?: return
        if (!runtimeEnabled || runtimeReadMode != READ_MODE_BROADCAST) {
            closeGatt()
            return
        }
        val address = runtimeDeviceAddress
        if (address.isEmpty()) {
            moduleRef?.let { log(it, "heart rate device is not configured") }
            return
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) return

        closeGatt()
        runCatching {
            val device = adapter.getRemoteDevice(address)
            activeGattAutoConnect = autoConnect
            activeGatt = if (Build.VERSION.SDK_INT >= 23) {
                device.connectGatt(
                    context,
                    autoConnect,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE,
                )
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, autoConnect, gattCallback)
            }
            moduleRef?.let {
                val mode = if (autoConnect) "auto" else "direct"
                log(it, "connecting heart rate device address=$address mode=$mode")
            }
        }.onFailure {
            activeGattAutoConnect = false
            moduleRef?.let { module -> logError(module, "connect failed: ${it.message}") }
            scheduleSetupRetry()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @Suppress("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (activeGatt !== gatt) {
                runCatching { gatt.close() }
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connected = true
                mainHandler.removeCallbacks(setupRetryRunnable)
                moduleRef?.let { log(it, "heart rate device connected") }
                if (!gatt.discoverServices()) {
                    failAndRetrySetup(gatt, "failed to start heart rate service discovery")
                }
            } else {
                handleConnectionEnded(gatt, status, newState)
            }
        }

        @Suppress("MissingPermission", "DEPRECATION")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failAndRetrySetup(gatt, "heart rate service discovery failed status=$status")
                return
            }
            val characteristic = gatt.getService(heartRateServiceUuid)
                ?.getCharacteristic(heartRateMeasurementUuid)
            if (characteristic == null) {
                failAndRetrySetup(gatt, "standard Heart Rate Service not found")
                return
            }
            if (!gatt.setCharacteristicNotification(characteristic, true)) {
                failAndRetrySetup(gatt, "failed to enable heart rate notifications")
                return
            }
            val descriptor = characteristic.getDescriptor(clientConfigUuid)
            if (descriptor == null) {
                failAndRetrySetup(gatt, "heart rate notification descriptor not found")
                return
            }
            val writeStarted = if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                    BluetoothGatt.GATT_SUCCESS
            } else {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
            moduleRef?.let { log(it, "heart rate notifications enabled=$writeStarted") }
            if (!writeStarted) {
                failAndRetrySetup(gatt, "failed to start heart rate descriptor write")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (activeGatt === gatt && descriptor.uuid == clientConfigUuid &&
                status != BluetoothGatt.GATT_SUCCESS
            ) {
                failAndRetrySetup(gatt, "heart rate descriptor write failed status=$status")
            }
        }

        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (activeGatt === gatt && connected && characteristic.uuid == heartRateMeasurementUuid) {
                handleMeasurement(characteristic.value ?: return)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (activeGatt === gatt && connected && characteristic.uuid == heartRateMeasurementUuid) {
                handleMeasurement(value)
            }
        }
    }

    private fun handleMeasurement(value: ByteArray) {
        val bpm = parseHeartRate(value) ?: return
        mainHandler.post {
            if (!connected) return@post
            latestBpm = bpm
            if (measurementScheduled) return@post
            val elapsed = SystemClock.elapsedRealtime()
            val delay = (MIN_UPDATE_INTERVAL_MS - (elapsed - lastPostElapsed)).coerceAtLeast(0L)
            if (delay == 0L) {
                postHeartRate(latestBpm)
            } else {
                measurementScheduled = true
                mainHandler.postDelayed(measurementRunnable, delay)
            }
        }
    }

    private fun parseHeartRate(value: ByteArray): Int? {
        if (value.size < 2) return null
        val isSixteenBit = value[0].toInt() and 0x01 != 0
        val bpm = if (isSixteenBit) {
            if (value.size < 3) return null
            (value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8)
        } else {
            value[1].toInt() and 0xFF
        }
        return bpm.takeIf { it in 1..400 }
    }

    private fun postHeartRate(bpm: Int) {
        val context = appContext ?: return
        lastPostElapsed = SystemClock.elapsedRealtime()
        val moduleContext = context.moduleContext()
        val heartRateText = if (runtimeShowUnit) {
            moduleContext.getString(R.string.heart_rate_bpm, bpm)
        } else {
            bpm.toString()
        }
        IslandDispatcher.post(
            context,
            IslandRequest(
                title = "",
                content = heartRateText,
                focusTitle = moduleContext.getString(R.string.heart_rate_island),
                focusContent = heartRateText,
                icon = heartRateIcon,
                notifId = NOTIF_ID,
                timeoutSecs = ISLAND_TIMEOUT_SECONDS,
                firstFloat = false,
                enableFloat = false,
                showNotification = false,
                preserveStatusBarSmallIcon = false,
                isOngoing = true,
                sourcePackage = "com.android.systemui",
                sourceChannelId = "heart_rate",
            ),
        )
    }

    private fun scheduleSetupRetry() {
        if (!runtimeEnabled) return
        mainHandler.removeCallbacks(setupRetryRunnable)
        mainHandler.postDelayed(setupRetryRunnable, SETUP_RETRY_DELAY_MS)
    }

    private fun handleConnectionEnded(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (activeGatt !== gatt) return
        connected = false
        mainHandler.post { cancelIsland() }
        if (!runtimeEnabled || runtimeReadMode != READ_MODE_BROADCAST) {
            closeGatt(gatt)
            return
        }
        if (activeGattAutoConnect) {
            moduleRef?.let {
                log(
                    it,
                    "heart rate device offline status=$status state=$newState; " +
                        "waiting for Bluetooth auto-connect",
                )
            }
            return
        }
        moduleRef?.let {
            log(
                it,
                "heart rate direct connection ended status=$status state=$newState; " +
                    "registering Bluetooth auto-connect",
            )
        }
        mainHandler.post {
            if (activeGatt !== gatt || !runtimeEnabled) return@post
            closeGatt(gatt)
            connectConfiguredDevice(autoConnect = true)
        }
    }

    private fun failAndRetrySetup(gatt: BluetoothGatt, message: String) {
        if (activeGatt !== gatt) return
        moduleRef?.let { logError(it, "$message; retrying setup in 60 seconds") }
        closeGatt(gatt)
        mainHandler.post { cancelIsland() }
        scheduleSetupRetry()
    }

    private fun cancelIsland() {
        mainHandler.removeCallbacks(measurementRunnable)
        measurementScheduled = false
        latestBpm = 0
        lastPostElapsed = 0L
        appContext?.let { IslandDispatcher.cancel(it, NOTIF_ID) }
    }

    @Suppress("MissingPermission")
    private fun closeGatt(expected: BluetoothGatt? = null) {
        val gatt = activeGatt ?: return
        if (expected != null && expected !== gatt) return
        activeGatt = null
        activeGattAutoConnect = false
        connected = false
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
    }

    private fun createHeartRateBitmap(): Bitmap {
        val bitmap = createBitmap(96, 96)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val path = Path().apply {
            moveTo(48f, 84f)
            cubicTo(42f, 77f, 13f, 56f, 13f, 34f)
            cubicTo(13f, 20f, 24f, 11f, 36f, 11f)
            cubicTo(43f, 11f, 48f, 15f, 48f, 15f)
            cubicTo(48f, 15f, 53f, 11f, 60f, 11f)
            cubicTo(72f, 11f, 83f, 20f, 83f, 34f)
            cubicTo(83f, 56f, 54f, 77f, 48f, 84f)
            close()
        }
        canvas.drawPath(path, paint)
        return bitmap
    }

    private fun uuid16(value: Int): UUID =
        UUID.fromString("0000${value.toString(16).padStart(4, '0')}-0000-1000-8000-00805f9b34fb")
}
