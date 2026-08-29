package io.github.hyperisland.compose.page.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun HideBehaviorPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val masterState = rememberBooleanPreference(prefs, KEY_MASTER, false)
    val screenPinningState = rememberBooleanPreference(prefs, KEY_SCREEN_PINNING, true)
    val bouncerState = rememberBooleanPreference(prefs, KEY_BOUNCER, true)
    val fullscreenState = rememberBooleanPreference(prefs, KEY_FULLSCREEN, true)
    val landscapeState = rememberBooleanPreference(prefs, KEY_FULLSCREEN_LANDSCAPE_DISABLE, false)
    val screenLockedState = rememberBooleanPreference(prefs, KEY_SCREEN_LOCKED, true)
    val notificationCenterState = rememberBooleanPreference(prefs, KEY_NOTIFICATION_CENTER, true)
    val foregroundAppState = rememberBooleanPreference(prefs, KEY_FOREGROUND_APP, true)
    val master by masterState
    val snackbarState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val restartMessage = stringResource(R.string.compose_restart_scope_app)

    DetailPage(
        title = stringResource(R.string.compose_hide_behavior),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarState) },
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.compose_hide_behavior_description),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        item {
            SectionTitle(stringResource(R.string.compose_hide_behavior))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.compose_hide_behavior_master),
                    summary = stringResource(R.string.compose_hide_behavior_master_summary),
                    icon = MiuixIcons.Settings,
                    checked = master,
                ) {
                    masterState.value = it
                    prefs.putBoolean(KEY_MASTER, it)
                    scope.launch { snackbarState.showSnackbar(restartMessage) }
                }
                PreferenceSwitch(
                    stringResource(R.string.compose_hide_behavior_screen_pinning),
                    stringResource(R.string.compose_hide_behavior_screen_pinning_summary),
                    MiuixIcons.Pin,
                    screenPinningState.value,
                    master,
                ) { screenPinningState.value = it; prefs.putBoolean(KEY_SCREEN_PINNING, it) }
                PreferenceSwitch(
                    stringResource(R.string.compose_hide_behavior_bouncer),
                    stringResource(R.string.compose_hide_behavior_bouncer_summary),
                    MiuixIcons.Lock,
                    bouncerState.value,
                    master,
                ) { bouncerState.value = it; prefs.putBoolean(KEY_BOUNCER, it) }
                PreferenceSwitch(
                    stringResource(R.string.compose_hide_behavior_fullscreen),
                    stringResource(R.string.compose_hide_behavior_fullscreen_summary),
                    MiuixIcons.ScreenMirroring,
                    fullscreenState.value,
                    master,
                ) { fullscreenState.value = it; prefs.putBoolean(KEY_FULLSCREEN, it) }
                PreferenceSwitch(
                    stringResource(R.string.compose_hide_behavior_landscape_disable),
                    stringResource(R.string.compose_hide_behavior_landscape_disable_summary),
                    MiuixIcons.ScreenMirroring,
                    landscapeState.value,
                    master && !fullscreenState.value,
                ) { landscapeState.value = it; prefs.putBoolean(KEY_FULLSCREEN_LANDSCAPE_DISABLE, it) }
                PreferenceSwitch(
                    stringResource(R.string.compose_hide_behavior_screen_locked),
                    stringResource(R.string.compose_hide_behavior_screen_locked_summary),
                    MiuixIcons.Lock,
                    screenLockedState.value,
                    master,
                ) { screenLockedState.value = it; prefs.putBoolean(KEY_SCREEN_LOCKED, it) }
                PreferenceSwitch(
                    stringResource(R.string.compose_hide_behavior_notification_center),
                    stringResource(R.string.compose_hide_behavior_notification_center_summary),
                    MiuixIcons.Hide,
                    notificationCenterState.value,
                    master,
                ) { notificationCenterState.value = it; prefs.putBoolean(KEY_NOTIFICATION_CENTER, it) }
                PreferenceSwitch(
                    stringResource(R.string.compose_hide_behavior_foreground_app),
                    stringResource(R.string.compose_hide_behavior_foreground_app_summary),
                    MiuixIcons.Show,
                    foregroundAppState.value,
                    master,
                ) { foregroundAppState.value = it; prefs.putBoolean(KEY_FOREGROUND_APP, it) }
            }
        }
    }
}

private const val KEY_MASTER = "pref_temp_hide_behavior_enabled"
private const val KEY_SCREEN_PINNING = "pref_temp_hide_screen_pinning"
private const val KEY_BOUNCER = "pref_temp_hide_bouncer_showing"
private const val KEY_FULLSCREEN = "pref_temp_hide_fullscreen"
private const val KEY_FULLSCREEN_LANDSCAPE_DISABLE = "pref_temp_hide_fullscreen_landscape_disable"
private const val KEY_SCREEN_LOCKED = "pref_temp_hide_screen_locked"
private const val KEY_NOTIFICATION_CENTER = "pref_temp_hide_notification_center"
private const val KEY_FOREGROUND_APP = "pref_temp_hide_foreground_app"
