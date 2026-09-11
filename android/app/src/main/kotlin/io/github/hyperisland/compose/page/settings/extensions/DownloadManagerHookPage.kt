package io.github.hyperisland.compose.page.settings.extensions

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SnackbarHost

@Composable
internal fun DownloadManagerHookPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val actions = rememberHookScopeActions()
    val scopeFailed = stringResource(R.string.ext_scope_failed)
    val restartRequired = stringResource(R.string.restart_scope_app)
    val resumeNotification = rememberBooleanPreference(prefs, KEY_RESUME_NOTIFICATION, true)
    val downloadShowTaskIcon = rememberBooleanPreference(prefs, KEY_DOWNLOAD_SHOW_TASK_ICON, true)

    HookExtensionScaffold(
        title = stringResource(R.string.ext_download_manager),
        onBack = onBack,
        snackbarHost = { SnackbarHost(actions.snackbar) },
    ) {
        item {
            SectionTitle(stringResource(R.string.config))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.ext_resume_notification),
                    summary = stringResource(R.string.ext_resume_notification_summary),
                    icon = null,
                    checked = resumeNotification.value,
                ) { value ->
                    if (actions.request(value, PKG_DOWNLOAD_MANAGER, scopeFailed)) {
                        resumeNotification.value = value
                        prefs.putBoolean(KEY_RESUME_NOTIFICATION, value)
                        actions.show(restartRequired)
                    }
                }
                PreferenceSwitch(
                    title = stringResource(R.string.ext_download_show_task_icon),
                    summary = stringResource(R.string.ext_download_show_task_icon_summary),
                    icon = null,
                    checked = downloadShowTaskIcon.value,
                ) { value ->
                    if (actions.request(value, PKG_DOWNLOAD_MANAGER, scopeFailed)) {
                        downloadShowTaskIcon.value = value
                        prefs.putBoolean(KEY_DOWNLOAD_SHOW_TASK_ICON, value)
                        actions.show(restartRequired)
                    }
                }
            }
        }
    }
}
