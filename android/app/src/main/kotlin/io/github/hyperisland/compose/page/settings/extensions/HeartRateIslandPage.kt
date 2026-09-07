package io.github.hyperisland.compose.page.settings.extensions

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsAction
import io.github.hyperisland.compose.component.SettingsItemMargin
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.data.rememberStringPreference
import io.github.hyperisland.compose.service.XposedScopeService
import io.github.hyperisland.xposed.islanddispatch.definition.HeartRateIslandContract
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun HeartRateIslandPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scopeFailed = stringResource(R.string.ext_scope_failed)
    val loadFailed = stringResource(R.string.ext_heart_rate_scan_failed)
    val restartRequired = stringResource(R.string.restart_scope_app)
    val enabled = rememberBooleanPreference(prefs, KEY_HEART_RATE_ISLAND, false)
    val readMode = rememberStringPreference(
        prefs,
        KEY_HEART_RATE_READ_MODE,
        HEART_RATE_READ_MODE_BROADCAST,
    )
    val deviceAddress = rememberStringPreference(prefs, KEY_HEART_RATE_DEVICE_ADDRESS, "")
    val deviceName = rememberStringPreference(prefs, KEY_HEART_RATE_DEVICE_NAME, "")
    val showUnit = rememberBooleanPreference(prefs, KEY_HEART_RATE_SHOW_UNIT, false)
    val showDeviceDialog = remember { mutableStateOf(false) }
    val isScanning = remember { mutableStateOf(false) }
    val devices = remember { mutableStateOf<List<PairedBluetoothDevice>>(emptyList()) }
    val selectedDraft = remember { mutableStateOf("") }

    fun show(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun notifyRuntime(
        enabledValue: Boolean = enabled.value,
        readModeValue: String = readMode.value,
        addressValue: String = deviceAddress.value,
        showUnitValue: Boolean = showUnit.value,
        forceReconnect: Boolean = false,
    ) {
        context.sendBroadcast(
            Intent(HeartRateIslandContract.ACTION_CONFIG_CHANGED).apply {
                setPackage("com.android.systemui")
                putExtra(HeartRateIslandContract.EXTRA_ENABLED, enabledValue)
                putExtra(HeartRateIslandContract.EXTRA_READ_MODE, readModeValue)
                putExtra(HeartRateIslandContract.EXTRA_DEVICE_ADDRESS, addressValue)
                putExtra(HeartRateIslandContract.EXTRA_SHOW_UNIT, showUnitValue)
                putExtra(HeartRateIslandContract.EXTRA_FORCE_RECONNECT, forceReconnect)
            },
        )
    }

    fun scanDevices() {
        showDeviceDialog.value = true
        isScanning.value = true
        devices.value = emptyList()
        selectedDraft.value = deviceAddress.value
        scope.launch {
            HookExtensionService.scanHeartRateDevices(context)
                .onSuccess { devices.value = it }
                .onFailure { show(it.message ?: loadFailed) }
            isScanning.value = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> if (result.values.all { it }) scanDevices() else show(loadFailed) }

    fun openDevices() {
        if (HookExtensionService.hasHeartRateScanPermission(context)) scanDevices()
        else if (Build.VERSION.SDK_INT >= 31) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ),
            )
        }
    }

    HookExtensionScaffold(
        title = stringResource(R.string.ext_heart_rate_settings),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) {
        item {
            SectionTitle(stringResource(R.string.config))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.ext_heart_rate_enable),
                    summary = stringResource(R.string.ext_heart_rate_enable_summary),
                    icon = null,
                    checked = enabled.value,
                ) { value ->
                    val granted = !value || XposedScopeService.requestScope(
                        context,
                        listOf("com.android.systemui"),
                    ).onFailure { show(it.message ?: scopeFailed) }.isSuccess
                    if (granted) {
                        enabled.value = value
                        prefs.putBoolean(KEY_HEART_RATE_ISLAND, value)
                        if (!value) showDeviceDialog.value = false
                        notifyRuntime(enabledValue = value)
                        if (value) show(restartRequired)
                    }
                }
                AnimatedVisibility(enabled.value) {
                    Column {
                        PreferenceDropdown(
                            title = stringResource(R.string.ext_heart_rate_read_mode),
                            summary = stringResource(R.string.ext_heart_rate_read_mode_summary),
                            icon = null,
                            items = listOf(stringResource(R.string.ext_heart_rate_broadcast)),
                            selectedIndex = 0,
                        ) {
                            readMode.value = HEART_RATE_READ_MODE_BROADCAST
                            prefs.putString(KEY_HEART_RATE_READ_MODE, readMode.value)
                            notifyRuntime(readModeValue = readMode.value)
                        }
                        AnimatedVisibility(readMode.value == HEART_RATE_READ_MODE_BROADCAST) {
                            SettingsAction(
                                title = stringResource(R.string.ext_heart_rate_device),
                                summary = if (isScanning.value) {
                                    stringResource(R.string.ext_heart_rate_scanning)
                                } else if (deviceAddress.value.isBlank()) {
                                    stringResource(R.string.ext_heart_rate_device_not_set)
                                } else {
                                    deviceName.value.ifBlank { deviceAddress.value }
                                },
                                endIcon = MiuixIcons.ChevronForward,
                                enabled = !isScanning.value,
                            ) { openDevices() }
                        }
                        PreferenceSwitch(
                            title = stringResource(R.string.ext_heart_rate_show_unit),
                            summary = stringResource(R.string.ext_heart_rate_show_unit_summary),
                            icon = null,
                            checked = showUnit.value,
                        ) { value ->
                            showUnit.value = value
                            prefs.putBoolean(KEY_HEART_RATE_SHOW_UNIT, value)
                            notifyRuntime(showUnitValue = value)
                        }
                    }
                }
            }
        }
    }

    WindowDialog(
        show = showDeviceDialog.value,
        title = stringResource(R.string.ext_heart_rate_choose_device),
        onDismissRequest = { showDeviceDialog.value = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                if (isScanning.value) {
                    Text(
                        text = stringResource(R.string.ext_heart_rate_scanning_hint),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (devices.value.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ext_heart_rate_no_broadcast_devices),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(devices.value, key = { it.address }) { device ->
                            RadioButtonPreference(
                                title = device.name,
                                summary = device.address.takeIf { it != device.name },
                                selected = selectedDraft.value == device.address,
                                radioButtonLocation = RadioButtonLocation.End,
                                insideMargin = SettingsItemMargin,
                                onClick = { selectedDraft.value = device.address },
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showDeviceDialog.value = false },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val selected = devices.value.firstOrNull {
                            it.address == selectedDraft.value
                        }
                        if (selected == null) {
                            deviceAddress.value = ""
                            deviceName.value = ""
                            prefs.remove(KEY_HEART_RATE_DEVICE_ADDRESS)
                            prefs.remove(KEY_HEART_RATE_DEVICE_NAME)
                        } else {
                            deviceAddress.value = selected.address
                            deviceName.value = selected.name
                            prefs.putString(KEY_HEART_RATE_DEVICE_ADDRESS, selected.address)
                            prefs.putString(KEY_HEART_RATE_DEVICE_NAME, selected.name)
                        }
                        showDeviceDialog.value = false
                        notifyRuntime(
                            addressValue = deviceAddress.value,
                            forceReconnect = true,
                        )
                        show(context.getString(R.string.ext_heart_rate_connecting))
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isScanning.value,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text(stringResource(R.string.confirm)) }
            }
        }
    }
}
