package io.github.hyperisland.compose.page.settings.extensions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.PreferenceSlider
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsAction
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

@Composable
internal fun SystemUiHookPage(
    prefs: FlutterPrefsRepository,
    onOpenDetail: (HookExtensionDetail) -> Unit,
    onBack: () -> Unit,
) {
    val actions = rememberHookScopeActions()
    val scopeFailed = stringResource(R.string.ext_scope_failed)
    val restartRequired = stringResource(R.string.restart_scope_app)
    val enabledText = stringResource(R.string.ext_enabled)
    val disabledText = stringResource(R.string.ext_disabled)

    val smooth = rememberBooleanPreference(prefs, KEY_SMOOTH_ISLAND, false)
    val smoothingState = remember(KEY_SMOOTHING) {
        mutableFloatStateOf(prefs.getDouble(KEY_SMOOTHING, DEFAULT_SMOOTHING).toFloat())
    }
    val lockscreenDeviceCenter = rememberBooleanPreference(prefs, KEY_LOCKSCREEN_DEVICE_CENTER, false)
    val unlockAll = rememberBooleanPreference(prefs, KEY_UNLOCK_ALL_FOCUS, false)
    val bluetooth = rememberBooleanPreference(prefs, KEY_BLUETOOTH_ISLAND, false)
    val heartRate = rememberBooleanPreference(prefs, KEY_HEART_RATE_ISLAND, false)
    val charge = rememberBooleanPreference(prefs, KEY_CHARGE_ISLAND, false)
    val faceUnlock = rememberBooleanPreference(prefs, KEY_FACE_UNLOCK_ISLAND, false)
    val hideFaceIcon = rememberBooleanPreference(prefs, KEY_HIDE_FACE_UNLOCK_ICON, false)
    val iconAdjustment = rememberBooleanPreference(prefs, KEY_SMALL_ICON_ADJUSTMENT, false)
    val iconOpacityState = remember(KEY_SMALL_ICON_OPACITY) {
        mutableFloatStateOf(prefs.getDouble(KEY_SMALL_ICON_OPACITY, DEFAULT_ICON_OPACITY).toFloat())
    }
    val wifiTileDisconnect = rememberBooleanPreference(prefs, KEY_WIFI_TILE_DISCONNECT, false)

    HookExtensionScaffold(
        title = stringResource(R.string.ext_system_ui),
        onBack = onBack,
        snackbarHost = { SnackbarHost(actions.snackbar) },
        restartPackages = RESTART_SCOPE_SYSTEM_UI,
    ) {
        item {
            SectionTitle(stringResource(R.string.config))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.smooth_island),
                    summary = stringResource(R.string.smooth_island_summary),
                    icon = null,
                    checked = smooth.value,
                ) { value ->
                    if (actions.request(value, listOf(PKG_SYSTEM_UI), scopeFailed)) {
                        smooth.value = value
                        prefs.putBoolean(KEY_SMOOTH_ISLAND, value)
                        actions.show(restartRequired)
                    }
                }
                AnimatedVisibility(smooth.value) {
                    PreferenceSlider(
                        title = stringResource(R.string.ext_smoothing),
                        icon = null,
                        value = smoothingState.floatValue,
                        valueText = "%.2f".format(smoothingState.floatValue),
                        valueRange = 0f..1f,
                        steps = 19,
                        resetVisible = smoothingState.floatValue.toDouble() != DEFAULT_SMOOTHING,
                        onReset = {
                            smoothingState.floatValue = DEFAULT_SMOOTHING.toFloat()
                            prefs.remove(KEY_SMOOTHING)
                        },
                        onValueChange = { smoothingState.floatValue = it },
                        onValueChangeFinished = {
                            prefs.putDouble(KEY_SMOOTHING, smoothingState.floatValue.toDouble())
                        },
                    )
                }
                PreferenceSwitch(
                    title = stringResource(R.string.ext_lockscreen_device_center),
                    summary = stringResource(R.string.ext_lockscreen_device_center_summary),
                    icon = null,
                    checked = lockscreenDeviceCenter.value,
                ) { value ->
                    if (actions.request(
                            value,
                            listOf(PKG_SYSTEM_UI, "com.milink.service"),
                            scopeFailed,
                        )
                    ) {
                        lockscreenDeviceCenter.value = value
                        prefs.putBoolean(KEY_LOCKSCREEN_DEVICE_CENTER, value)
                        actions.show(restartRequired)
                    }
                }
                PreferenceSwitch(
                    title = stringResource(R.string.ext_unlock_all_focus),
                    summary = stringResource(R.string.ext_unlock_all_focus_summary),
                    icon = null,
                    checked = unlockAll.value,
                ) { value ->
                    if (actions.request(value, listOf(PKG_SYSTEM_UI), scopeFailed)) {
                        unlockAll.value = value
                        prefs.putBoolean(KEY_UNLOCK_ALL_FOCUS, value)
                        actions.show(restartRequired)
                    }
                }
                PreferenceSwitch(
                    title = stringResource(R.string.ext_hide_face_icon),
                    summary = stringResource(R.string.ext_hide_face_icon_summary),
                    icon = null,
                    checked = hideFaceIcon.value,
                ) { value ->
                    if (actions.request(value, listOf(PKG_SYSTEM_UI), scopeFailed)) {
                        hideFaceIcon.value = value
                        prefs.putBoolean(KEY_HIDE_FACE_UNLOCK_ICON, value)
                        actions.show(restartRequired)
                    }
                }
                PreferenceSwitch(
                    title = stringResource(R.string.ext_small_icon),
                    summary = if (iconAdjustment.value) {
                        stringResource(R.string.ext_small_icon_enabled)
                    } else {
                        stringResource(R.string.ext_small_icon_disabled)
                    },
                    icon = null,
                    checked = iconAdjustment.value,
                ) { value ->
                    if (actions.request(value, listOf(PKG_SYSTEM_UI), scopeFailed)) {
                        iconAdjustment.value = value
                        prefs.putBoolean(KEY_SMALL_ICON_ADJUSTMENT, value)
                        actions.show(restartRequired)
                    }
                }
                AnimatedVisibility(iconAdjustment.value) {
                    PreferenceSlider(
                        title = stringResource(R.string.ext_opacity),
                        icon = null,
                        value = iconOpacityState.floatValue,
                        valueText = "${(iconOpacityState.floatValue * 100).toInt()}%",
                        valueRange = 0f..1f,
                        steps = 19,
                        resetVisible = iconOpacityState.floatValue.toDouble() != DEFAULT_ICON_OPACITY,
                        onReset = {
                            iconOpacityState.floatValue = DEFAULT_ICON_OPACITY.toFloat()
                            prefs.remove(KEY_SMALL_ICON_OPACITY)
                        },
                        onValueChange = { iconOpacityState.floatValue = it },
                        onValueChangeFinished = {
                            prefs.putDouble(KEY_SMALL_ICON_OPACITY, iconOpacityState.floatValue.toDouble())
                        },
                    )
                }
                PreferenceSwitch(
                    title = stringResource(R.string.ext_wifi_tile_disconnect),
                    summary = stringResource(R.string.ext_wifi_tile_disconnect_summary),
                    icon = null,
                    checked = wifiTileDisconnect.value,
                ) { value ->
                    if (actions.request(value, listOf(PKG_SYSTEM_UI), scopeFailed)) {
                        wifiTileDisconnect.value = value
                        prefs.putBoolean(KEY_WIFI_TILE_DISCONNECT, value)
                        actions.show(restartRequired)
                    }
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.ext_island_features))
            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsAction(
                    title = stringResource(R.string.bluetooth_island),
                    summary = stringResource(
                        R.string.ext_bluetooth_summary,
                        if (bluetooth.value) enabledText else disabledText,
                    ),
                    endIcon = MiuixIcons.ChevronForward,
                ) { onOpenDetail(HookExtensionDetail.Bluetooth) }
                SettingsAction(
                    title = stringResource(R.string.heart_rate_island),
                    summary = stringResource(
                        R.string.ext_heart_rate_summary,
                        if (heartRate.value) enabledText else disabledText,
                    ),
                    endIcon = MiuixIcons.ChevronForward,
                ) { onOpenDetail(HookExtensionDetail.HeartRate) }
                SettingsAction(
                    title = stringResource(R.string.charge_island),
                    summary = stringResource(
                        R.string.ext_charge_summary,
                        if (charge.value) enabledText else disabledText,
                    ),
                    endIcon = MiuixIcons.ChevronForward,
                ) { onOpenDetail(HookExtensionDetail.Charge) }
                SettingsAction(
                    title = stringResource(R.string.face_unlock_island),
                    summary = stringResource(
                        R.string.ext_face_summary,
                        if (faceUnlock.value) enabledText else disabledText,
                    ),
                    endIcon = MiuixIcons.ChevronForward,
                ) { onOpenDetail(HookExtensionDetail.FaceUnlock) }
            }
        }
    }
}
