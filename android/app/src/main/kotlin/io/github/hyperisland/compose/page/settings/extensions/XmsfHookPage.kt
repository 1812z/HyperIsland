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
internal fun XmsfHookPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val actions = rememberHookScopeActions()
    val scopeFailed = stringResource(R.string.ext_scope_failed)
    val restartRequired = stringResource(R.string.restart_scope_app)
    val unlockAuth = rememberBooleanPreference(prefs, KEY_UNLOCK_FOCUS_AUTH, false)

    HookExtensionScaffold(
        title = stringResource(R.string.ext_xmsf),
        onBack = onBack,
        snackbarHost = { SnackbarHost(actions.snackbar) },
    ) {
        item {
            SectionTitle(stringResource(R.string.config))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.ext_unlock_auth),
                    summary = stringResource(R.string.ext_unlock_auth_summary),
                    icon = null,
                    checked = unlockAuth.value,
                ) { value ->
                    if (actions.request(value, listOf(PKG_XMSF), scopeFailed)) {
                        unlockAuth.value = value
                        prefs.putBoolean(KEY_UNLOCK_FOCUS_AUTH, value)
                        actions.show(restartRequired)
                    }
                }
            }
        }
    }
}
