package io.github.hyperisland.compose.page.settings.extensions

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

internal data class PairedBluetoothDevice(val address: String, val name: String)

internal object HookExtensionService {
    fun hasBluetoothPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED

    fun pairedBluetoothDevices(context: Context): Result<List<PairedBluetoothDevice>> = runCatching {
        check(hasBluetoothPermission(context))
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return@runCatching emptyList()
        if (!adapter.isEnabled) return@runCatching emptyList()
        @Suppress("MissingPermission")
        adapter.bondedDevices.orEmpty().map { device ->
            val address = device.address.orEmpty()
            PairedBluetoothDevice(
                address = address,
                name = runCatching { device.name.orEmpty() }.getOrDefault("").ifBlank { address },
            )
        }.sortedBy { it.name.lowercase() }
    }

    fun hasHeartRateScanPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || (
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED && hasBluetoothPermission(context)
        )

    @Suppress("MissingPermission")
    suspend fun scanHeartRateDevices(
        context: Context,
        durationMillis: Long = 8_000L,
    ): Result<List<PairedBluetoothDevice>> = runCatching {
        check(hasHeartRateScanPermission(context))
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter ?: error("Bluetooth unavailable")
        check(adapter.isEnabled) { "Bluetooth disabled" }
        val scanner = adapter.bluetoothLeScanner ?: error("Bluetooth LE scanner unavailable")
        suspendCancellableCoroutine { continuation ->
            val mainHandler = Handler(Looper.getMainLooper())
            val found = linkedMapOf<String, PairedBluetoothDevice>()
            lateinit var callback: ScanCallback

            fun stopAndResume(error: Throwable? = null) {
                runCatching { scanner.stopScan(callback) }
                mainHandler.removeCallbacksAndMessages(callback)
                if (!continuation.isActive) return
                if (error != null) continuation.resumeWith(Result.failure(error))
                else continuation.resume(found.values.sortedBy { it.name.lowercase() })
            }

            callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val address = result.device.address.orEmpty()
                    if (address.isBlank()) return
                    val name = result.scanRecord?.deviceName
                        ?.takeIf(String::isNotBlank)
                        ?: runCatching { result.device.name }.getOrNull()
                            ?.takeIf(String::isNotBlank)
                        ?: address
                    found[address] = PairedBluetoothDevice(address, name)
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
                }

                override fun onScanFailed(errorCode: Int) {
                    stopAndResume(IllegalStateException("BLE scan failed: $errorCode"))
                }
            }

            continuation.invokeOnCancellation {
                mainHandler.post { runCatching { scanner.stopScan(callback) } }
            }
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
                .build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(listOf(filter), settings, callback)
            mainHandler.postAtTime(
                { stopAndResume() },
                callback,
                android.os.SystemClock.uptimeMillis() + durationMillis,
            )
        }
    }

    private val HEART_RATE_SERVICE_UUID: UUID =
        UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
}
