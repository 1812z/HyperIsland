package io.github.hyperisland.compose.page.settings.extensions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun SecurityCenterHookPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val actions = rememberHookScopeActions()
    val scopeFailed = stringResource(R.string.ext_scope_failed)
    val restartRequired = stringResource(R.string.restart_scope_app)
    val clipboardToastConversion = rememberBooleanPreference(
        prefs,
        KEY_CLIPBOARD_TOAST_CONVERSION,
        false,
    )
    val clipboardOptimizeIslandStyle = rememberBooleanPreference(
        prefs,
        KEY_CLIPBOARD_OPTIMIZE_ISLAND_STYLE,
        true,
    )
    val showClipboardPermissionDialog = remember { mutableStateOf(false) }

    HookExtensionScaffold(
        title = stringResource(R.string.ext_permission_manager),
        onBack = onBack,
        snackbarHost = { SnackbarHost(actions.snackbar) },
    ) {
        item {
            SectionTitle(stringResource(R.string.config))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.ext_clipboard_toast_conversion),
                    summary = stringResource(R.string.ext_clipboard_toast_conversion_summary),
                    icon = null,
                    checked = clipboardToastConversion.value,
                ) { value ->
                    if (value) {
                        showClipboardPermissionDialog.value = true
                    } else {
                        clipboardToastConversion.value = false
                        prefs.putBoolean(KEY_CLIPBOARD_TOAST_CONVERSION, false)
                        prefs.setToastEnabled(PKG_SECURITY_CENTER, false)
                        actions.show(restartRequired)
                    }
                }
                AnimatedVisibility(clipboardToastConversion.value) {
                    PreferenceSwitch(
                        title = stringResource(R.string.ext_clipboard_optimize_island_style),
                        summary = null,
                        icon = null,
                        checked = clipboardOptimizeIslandStyle.value,
                    ) { value ->
                        clipboardOptimizeIslandStyle.value = value
                        prefs.putBoolean(KEY_CLIPBOARD_OPTIMIZE_ISLAND_STYLE, value)
                    }
                }
            }
        }
    }

    WindowDialog(
        show = showClipboardPermissionDialog.value,
        title = stringResource(R.string.ext_clipboard_permission_dialog_title),
        onDismissRequest = { showClipboardPermissionDialog.value = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.ext_clipboard_permission_dialog_summary))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showClipboardPermissionDialog.value = false },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        if (actions.request(true, listOf(PKG_SECURITY_CENTER), scopeFailed)) {
                            clipboardToastConversion.value = true
                            prefs.putBoolean(KEY_CLIPBOARD_TOAST_CONVERSION, true)
                            prefs.setToastEnabled(PKG_SECURITY_CENTER, true)
                            showClipboardPermissionDialog.value = false
                            actions.show(restartRequired)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text(stringResource(R.string.confirm)) }
            }
        }
    }
}
