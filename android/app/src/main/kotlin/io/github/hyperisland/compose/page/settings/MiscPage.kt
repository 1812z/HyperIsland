package io.github.hyperisland.compose.page.settings

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsActionWithArrow
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.core.service.AppService
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AppRecording
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Update

@Composable
internal fun MiscPage(
    prefs: FlutterPrefsRepository,
    onOpenOnboarding: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val showWelcome = rememberBooleanPreference(prefs, KEY_SHOW_WELCOME, true)
    val hideDesktopIcon = rememberBooleanPreference(prefs, KEY_HIDE_DESKTOP_ICON, false)
    val checkUpdate = rememberBooleanPreference(prefs, KEY_CHECK_UPDATE, true)
    val debugLog = rememberBooleanPreference(prefs, KEY_DEBUG_LOG, false)

    DetailPage(title = stringResource(R.string.compose_misc), onBack = onBack) {
        item {
            SectionTitle(stringResource(R.string.compose_misc))
            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsActionWithArrow(
                    stringResource(R.string.compose_open_onboarding),
                    MiuixIcons.AppRecording,
                    stringResource(R.string.compose_open_onboarding_summary),
                ) { onOpenOnboarding() }
                PreferenceSwitch(
                    stringResource(R.string.compose_show_welcome),
                    stringResource(R.string.compose_show_welcome_summary),
                    MiuixIcons.Show,
                    showWelcome.value,
                ) { showWelcome.value = it; prefs.putBoolean(KEY_SHOW_WELCOME, it) }
                PreferenceSwitch(
                    stringResource(R.string.compose_hide_desktop_icon),
                    stringResource(R.string.compose_hide_desktop_icon_summary),
                    MiuixIcons.Hide,
                    hideDesktopIcon.value,
                ) {
                    hideDesktopIcon.value = it
                    prefs.putBoolean(KEY_HIDE_DESKTOP_ICON, it)
                    context.setDesktopIconVisible(!it)
                }
                PreferenceSwitch(
                    stringResource(R.string.compose_check_update),
                    stringResource(R.string.compose_check_update_summary),
                    MiuixIcons.Update,
                    checkUpdate.value,
                ) { checkUpdate.value = it; prefs.putBoolean(KEY_CHECK_UPDATE, it) }
                PreferenceSwitch(
                    stringResource(R.string.compose_debug_log),
                    stringResource(R.string.compose_debug_log_summary),
                    MiuixIcons.Settings,
                    debugLog.value,
                ) { debugLog.value = it; prefs.putBoolean(KEY_DEBUG_LOG, it) }
            }
        }
    }
}

private fun Context.setDesktopIconVisible(visible: Boolean) {
    runCatching { AppService().setDesktopIconVisible(packageManager, packageName, visible) }
}

private const val KEY_SHOW_WELCOME = "pref_show_welcome"
private const val KEY_HIDE_DESKTOP_ICON = "pref_hide_desktop_icon"
private const val KEY_CHECK_UPDATE = "pref_check_update_on_launch"
private const val KEY_DEBUG_LOG = "pref_debug_log"
