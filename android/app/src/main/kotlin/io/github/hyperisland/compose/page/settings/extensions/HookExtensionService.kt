package io.github.hyperisland.compose.page.settings.extensions

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

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
}
