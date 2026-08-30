package io.github.hyperisland.compose.page.settings.extensions

import android.Manifest
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.ColorPaletteDialog
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsAction
import io.github.hyperisland.compose.component.SettingsItemMargin
import io.github.hyperisland.compose.component.parseHexColor
import io.github.hyperisland.compose.component.toArgbHex
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.data.rememberLongPreference
import io.github.hyperisland.compose.data.rememberStringPreference
import io.github.hyperisland.compose.service.XposedScopeService
import kotlinx.coroutines.launch
import org.json.JSONArray
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun BluetoothIslandPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scopeFailed = stringResource(R.string.ext_scope_failed)
    val loadFailed = stringResource(R.string.ext_bluetooth_load_failed)
    val restartRequired = stringResource(R.string.restart_scope_app)
    val enabled = rememberBooleanPreference(prefs, KEY_BLUETOOTH_ISLAND, false)
    val showName = rememberBooleanPreference(prefs, KEY_BLUETOOTH_SHOW_DEVICE_NAME, true)
    val duration = rememberLongPreference(prefs, KEY_BLUETOOTH_DURATION, DEFAULT_BLUETOOTH_DURATION)
    val durationDraft = remember(duration.value) { mutableStateOf(duration.value.toString()) }
    val whitelistEnabled = rememberBooleanPreference(prefs, KEY_BLUETOOTH_WHITELIST_ENABLED, false)
    val whitelist = remember {
        mutableStateOf(parseAddresses(prefs.getString(KEY_BLUETOOTH_WHITELIST_ADDRESSES)))
    }
    val outerGlow = rememberBooleanPreference(prefs, KEY_BLUETOOTH_OUTER_GLOW, false)
    val glowColor = rememberStringPreference(prefs, KEY_BLUETOOTH_OUTER_GLOW_COLOR, "")
    val showColorDialog = remember { mutableStateOf(false) }
    val showDurationDialog = remember { mutableStateOf(false) }
    val showDeviceDialog = remember { mutableStateOf(false) }
    val devices = remember { mutableStateOf<List<PairedBluetoothDevice>>(emptyList()) }
    val deviceDraft = remember { mutableStateOf<Set<String>>(emptySet()) }

    fun show(message: String) { scope.launch { snackbar.showSnackbar(message) } }
    fun loadDevices() {
        HookExtensionService.pairedBluetoothDevices(context)
            .onSuccess {
                devices.value = it
                deviceDraft.value = whitelist.value.toSet()
                showDeviceDialog.value = true
            }
            .onFailure { show(it.message ?: loadFailed) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) loadDevices() else show(loadFailed) }
    fun openDevices() {
        if (HookExtensionService.hasBluetoothPermission(context)) loadDevices()
        else if (Build.VERSION.SDK_INT >= 31) permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }

    DetailPage(
        title = stringResource(R.string.ext_bluetooth_settings),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) {
        item {
            SectionTitle(stringResource(R.string.config))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.ext_bluetooth_enable),
                    summary = stringResource(R.string.ext_bluetooth_enable_summary),
                    icon = null,
                    checked = enabled.value,
                ) { value ->
                    val granted = !value || XposedScopeService.requestScope(
                        context,
                        listOf("com.android.systemui"),
                    ).onFailure { show(it.message ?: scopeFailed) }.isSuccess
                    if (granted) {
                        enabled.value = value
                        prefs.putBoolean(KEY_BLUETOOTH_ISLAND, value)
                        show(restartRequired)
                    }
                }
                PreferenceSwitch(
                    title = stringResource(R.string.ext_show_device_name),
                    summary = stringResource(R.string.ext_show_device_name_summary),
                    icon = null,
                    checked = showName.value,
                ) { value -> showName.value = value; prefs.putBoolean(KEY_BLUETOOTH_SHOW_DEVICE_NAME, value) }
                SettingsAction(
                    title = stringResource(R.string.ext_display_duration),
                    summary = stringResource(R.string.ext_duration_value, duration.value),
                    endIcon = MiuixIcons.ChevronForward,
                ) {
                    durationDraft.value = duration.value.toString()
                    showDurationDialog.value = true
                }
                PreferenceSwitch(
                    title = stringResource(R.string.ext_device_whitelist),
                    summary = stringResource(
                        if (whitelistEnabled.value) {
                            R.string.ext_device_whitelist_summary
                        } else {
                            R.string.ext_whitelist_all_hint
                        },
                    ),
                    icon = null,
                    checked = whitelistEnabled.value,
                ) { value -> whitelistEnabled.value = value; prefs.putBoolean(KEY_BLUETOOTH_WHITELIST_ENABLED, value) }
                AnimatedVisibility(whitelistEnabled.value) {
                    SettingsAction(
                        title = stringResource(R.string.ext_manage_devices),
                        summary = stringResource(R.string.ext_selected_devices, whitelist.value.size),
                        endIcon = MiuixIcons.ChevronForward,
                    ) { openDevices() }
                }
                PreferenceSwitch(
                    title = stringResource(R.string.ext_outer_glow),
                    summary = stringResource(R.string.ext_bluetooth_glow_summary),
                    icon = null,
                    checked = outerGlow.value,
                ) { value -> outerGlow.value = value; prefs.putBoolean(KEY_BLUETOOTH_OUTER_GLOW, value) }
                AnimatedVisibility(outerGlow.value) {
                    SettingsAction(
                        title = stringResource(R.string.ext_outer_glow_color),
                        summary = glowColor.value.ifBlank { stringResource(R.string.default_option) },
                        endIcon = MiuixIcons.ChevronForward,
                    ) { showColorDialog.value = true }
                }
            }
        }
    }

    ColorPaletteDialog(
        show = showColorDialog.value,
        title = stringResource(R.string.ext_outer_glow_color),
        initialColor = parseHexColor(glowColor.value, Color(0xFF0096FF)),
        onDismiss = { showColorDialog.value = false },
        onDelete = {
            glowColor.value = ""
            prefs.remove(KEY_BLUETOOTH_OUTER_GLOW_COLOR)
            showColorDialog.value = false
        },
    ) { color ->
        glowColor.value = color.toArgbHex()
        prefs.putString(KEY_BLUETOOTH_OUTER_GLOW_COLOR, glowColor.value)
        showColorDialog.value = false
    }

    WindowDialog(
        show = showDurationDialog.value,
        title = stringResource(R.string.ext_display_duration),
        onDismissRequest = { showDurationDialog.value = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextField(
                value = durationDraft.value,
                onValueChange = { input ->
                    durationDraft.value = input.filter(Char::isDigit).take(5)
                },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.ext_display_duration),
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showDurationDialog.value = false },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val value = durationDraft.value.toLongOrNull()
                            ?.coerceIn(1L, 86400L)
                            ?: duration.value
                        duration.value = value
                        durationDraft.value = value.toString()
                        prefs.putLong(KEY_BLUETOOTH_DURATION, value)
                        showDurationDialog.value = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }

    WindowDialog(
        show = showDeviceDialog.value,
        title = stringResource(R.string.ext_choose_devices),
        onDismissRequest = { showDeviceDialog.value = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                if (devices.value.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ext_no_paired_devices),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(devices.value, key = { it.address }) { device ->
                            CheckboxPreference(
                                title = device.name,
                                summary = device.address.takeIf { it != device.name },
                                checked = device.address in deviceDraft.value,
                                checkboxLocation = CheckboxLocation.End,
                                insideMargin = SettingsItemMargin,
                                onCheckedChange = { checked ->
                                    deviceDraft.value = deviceDraft.value.toMutableSet().apply {
                                        if (checked) add(device.address) else remove(device.address)
                                    }
                                },
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
                        whitelist.value = deviceDraft.value.toList()
                        if (whitelist.value.isEmpty()) prefs.remove(KEY_BLUETOOTH_WHITELIST_ADDRESSES)
                        else prefs.putString(KEY_BLUETOOTH_WHITELIST_ADDRESSES, JSONArray(whitelist.value).toString())
                        showDeviceDialog.value = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text(stringResource(R.string.confirm)) }
            }
        }
    }
}

private fun parseAddresses(raw: String): List<String> = runCatching {
    val array = JSONArray(raw)
    List(array.length()) { index -> array.optString(index) }.filter(String::isNotBlank)
}.getOrDefault(emptyList())
