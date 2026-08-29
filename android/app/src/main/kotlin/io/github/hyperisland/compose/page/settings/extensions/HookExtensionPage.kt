package io.github.hyperisland.compose.page.settings.extensions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.PreferenceSlider
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsAction
import io.github.hyperisland.compose.component.SettingsItemMargin
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.data.rememberStringPreference
import io.github.hyperisland.utils.HyperOsVersionUtil
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

@Composable
internal fun HookExtensionPage(
    prefs: FlutterPrefsRepository,
    onOpenDetail: (HookExtensionDetail) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scopeFailed = stringResource(R.string.compose_ext_scope_failed)
    val restartRequired = stringResource(R.string.compose_restart_scope_app)
    val enabledText = stringResource(R.string.compose_ext_enabled)
    val disabledText = stringResource(R.string.compose_ext_disabled)

    val settingsEntry = rememberBooleanPreference(prefs, KEY_SETTINGS_HOME_ENTRY, true)
    val settingsIcon = rememberStringPreference(prefs, KEY_SETTINGS_HOME_ENTRY_ICON_STYLE, MODE_DEFAULT)
    val smooth = rememberBooleanPreference(prefs, KEY_SMOOTH_ISLAND, false)
    val smoothSupported = remember { HyperOsVersionUtil.getMajorVersion() != 4 }
    val smoothingState = remember(KEY_SMOOTHING) { mutableFloatStateOf(prefs.getDouble(KEY_SMOOTHING, DEFAULT_SMOOTHING).toFloat()) }
    val unlockAll = rememberBooleanPreference(prefs, KEY_UNLOCK_ALL_FOCUS, false)
    val bluetooth = rememberBooleanPreference(prefs, KEY_BLUETOOTH_ISLAND, false)
    val charge = rememberBooleanPreference(prefs, KEY_CHARGE_ISLAND, false)
    val faceUnlock = rememberBooleanPreference(prefs, KEY_FACE_UNLOCK_ISLAND, false)
    val hideFaceIcon = rememberBooleanPreference(prefs, KEY_HIDE_FACE_UNLOCK_ICON, false)
    val iconAdjustment = rememberBooleanPreference(prefs, KEY_SMALL_ICON_ADJUSTMENT, false)
    val iconOpacityState = remember(KEY_SMALL_ICON_OPACITY) {
        mutableFloatStateOf(prefs.getDouble(KEY_SMALL_ICON_OPACITY, DEFAULT_ICON_OPACITY).toFloat())
    }
    val unlockAuth = rememberBooleanPreference(prefs, KEY_UNLOCK_FOCUS_AUTH, false)
    val resumeNotification = rememberBooleanPreference(prefs, KEY_RESUME_NOTIFICATION, true)

    fun show(message: String) { scope.launch { snackbar.showSnackbar(message) } }
    fun request(enabled: Boolean, packages: List<String>): Boolean {
        if (!enabled) return true
        val result = HookExtensionService.requestScope(context, packages)
        if (result.isFailure) show(result.exceptionOrNull()?.message ?: scopeFailed)
        return result.isSuccess
    }
    fun restart() = show(restartRequired)

    LaunchedEffect(smoothSupported) {
        if (!smoothSupported && smooth.value) {
            smooth.value = false
            prefs.putBoolean(KEY_SMOOTH_ISLAND, false)
        }
    }

    DetailPage(
        title = stringResource(R.string.compose_hook_extension),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) {
        item {
            SectionTitle(stringResource(R.string.compose_ext_system_settings))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.compose_ext_settings_entry),
                    summary = stringResource(R.string.compose_ext_settings_entry_summary),
                    icon = null,
                    checked = settingsEntry.value,
                ) { value ->
                    if (request(value, listOf("com.android.settings"))) {
                        settingsEntry.value = value
                        prefs.putBoolean(KEY_SETTINGS_HOME_ENTRY, value)
                        restart()
                    }
                }
                AnimatedVisibility(settingsEntry.value) {
                    val values = listOf(MODE_DEFAULT, MODE_OUTLINE)
                    PreferenceDropdown(
                        title = stringResource(R.string.compose_ext_icon_style),
                        summary = null,
                        icon = null,
                        items = listOf(
                            stringResource(R.string.compose_default),
                            stringResource(R.string.compose_ext_icon_outline),
                        ),
                        selectedIndex = values.indexOf(settingsIcon.value).coerceAtLeast(0),
                    ) { index ->
                        settingsIcon.value = values[index]
                        prefs.putString(KEY_SETTINGS_HOME_ENTRY_ICON_STYLE, values[index])
                    }
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.compose_ext_system_ui))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.compose_smooth_island),
                    summary = stringResource(R.string.compose_smooth_island_summary),
                    icon = null,
                    checked = smooth.value,
                    enabled = smoothSupported,
                ) { value ->
                    if (request(value, listOf("com.android.systemui"))) {
                        smooth.value = value
                        prefs.putBoolean(KEY_SMOOTH_ISLAND, value)
                        restart()
                    }
                }
                AnimatedVisibility(smooth.value && smoothSupported) {
                    PreferenceSlider(
                        title = stringResource(R.string.compose_ext_smoothing),
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
                        onValueChangeFinished = { prefs.putDouble(KEY_SMOOTHING, smoothingState.floatValue.toDouble()) },
                    )
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.compose_ext_unlock_all_focus),
                    summary = stringResource(R.string.compose_ext_unlock_all_focus_summary),
                    icon = null,
                    checked = unlockAll.value,
                ) { value ->
                    if (request(value, listOf("com.android.systemui"))) {
                        unlockAll.value = value
                        prefs.putBoolean(KEY_UNLOCK_ALL_FOCUS, value)
                        restart()
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsAction(
                    title = stringResource(R.string.compose_bluetooth_island),
                    summary = stringResource(
                        R.string.compose_ext_bluetooth_summary,
                        if (bluetooth.value) enabledText else disabledText,
                    ),
                    endIcon = MiuixIcons.ChevronForward,
                ) { onOpenDetail(HookExtensionDetail.Bluetooth) }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsAction(
                    title = stringResource(R.string.compose_charge_island),
                    summary = stringResource(
                        R.string.compose_ext_charge_summary,
                        if (charge.value) enabledText else disabledText,
                    ),
                    endIcon = MiuixIcons.ChevronForward,
                ) { onOpenDetail(HookExtensionDetail.Charge) }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsAction(
                    title = stringResource(R.string.compose_face_unlock_island),
                    summary = stringResource(
                        R.string.compose_ext_face_summary,
                        if (faceUnlock.value) enabledText else disabledText,
                    ),
                    endIcon = MiuixIcons.ChevronForward,
                ) { onOpenDetail(HookExtensionDetail.FaceUnlock) }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.compose_ext_hide_face_icon),
                    summary = stringResource(R.string.compose_ext_hide_face_icon_summary),
                    icon = null,
                    checked = hideFaceIcon.value,
                ) { value ->
                    if (request(value, listOf("com.android.systemui"))) {
                        hideFaceIcon.value = value
                        prefs.putBoolean(KEY_HIDE_FACE_UNLOCK_ICON, value)
                        restart()
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = stringResource(R.string.compose_ext_small_icon),
                    summary = if (iconAdjustment.value) {
                        stringResource(R.string.compose_ext_small_icon_enabled, (iconOpacityState.floatValue * 100).toInt())
                    } else {
                        stringResource(R.string.compose_ext_small_icon_disabled)
                    },
                    insideMargin = SettingsItemMargin,
                )
                PreferenceSwitch(
                    title = stringResource(R.string.compose_ext_small_icon_toggle),
                    summary = stringResource(R.string.compose_ext_small_icon_toggle_summary),
                    icon = null,
                    checked = iconAdjustment.value,
                ) { value ->
                    if (request(value, listOf("com.android.systemui"))) {
                        iconAdjustment.value = value
                        prefs.putBoolean(KEY_SMALL_ICON_ADJUSTMENT, value)
                        restart()
                    }
                }
                AnimatedVisibility(iconAdjustment.value) {
                    PreferenceSlider(
                        title = stringResource(R.string.compose_ext_opacity),
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
                        onValueChangeFinished = { prefs.putDouble(KEY_SMALL_ICON_OPACITY, iconOpacityState.floatValue.toDouble()) },
                    )
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.compose_ext_xmsf))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.compose_ext_unlock_auth),
                    summary = stringResource(R.string.compose_ext_unlock_auth_summary),
                    icon = null,
                    checked = unlockAuth.value,
                ) { value ->
                    if (request(value, listOf("com.xiaomi.xmsf"))) {
                        unlockAuth.value = value
                        prefs.putBoolean(KEY_UNLOCK_FOCUS_AUTH, value)
                        restart()
                    }
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.compose_ext_download_manager))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.compose_ext_resume_notification),
                    summary = stringResource(R.string.compose_ext_resume_notification_summary),
                    icon = null,
                    checked = resumeNotification.value,
                ) { value ->
                    if (request(value, listOf("com.android.providers.downloads", "com.xiaomi.android.app.downloadmanager"))) {
                        resumeNotification.value = value
                        prefs.putBoolean(KEY_RESUME_NOTIFICATION, value)
                        restart()
                    }
                }
            }
        }
    }
}
