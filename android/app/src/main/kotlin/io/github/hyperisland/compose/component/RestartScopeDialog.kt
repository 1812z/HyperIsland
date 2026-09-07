package io.github.hyperisland.compose.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.service.RestartScopeService
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun RestartScopeDialog(show: Boolean, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val scopeOptions = listOf(
        SYSTEM_UI_PACKAGE to stringResource(R.string.system_ui),
        DOWNLOADS_PACKAGE to stringResource(R.string.download_manager),
        XMSF_PACKAGE to stringResource(R.string.xmsf),
        SETTINGS_PACKAGE to stringResource(R.string.hook_scope_settings),
        SCREEN_RECORDER_PACKAGE to stringResource(R.string.screen_recorder),
        SECURITY_CENTER_PACKAGE to stringResource(R.string.security_center),
    )
    var selectedPackages by remember(show) { mutableStateOf(emptySet<String>()) }
    var restarting by remember(show) { mutableStateOf(false) }
    var error by remember(show) { mutableStateOf<String?>(null) }
    val rootRequired = stringResource(R.string.restart_root_required)

    WindowDialog(
        show = show,
        title = stringResource(R.string.restart_scope),
        summary = stringResource(R.string.restart_scope_summary),
        onDismissRequest = { if (!restarting) onDismiss() },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            scopeOptions.forEach { (packageName, label) ->
                val checked = packageName in selectedPackages
                RestartScopeRow(
                    label = label,
                    checked = checked,
                    enabled = !restarting,
                    onClick = {
                        selectedPackages = if (checked) {
                            selectedPackages - packageName
                        } else {
                            selectedPackages + packageName
                        }
                    },
                )
            }
        }
        error?.let {
            Text(
                it,
                color = RestartErrorColor,
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = stringResource(R.string.cancel),
                enabled = !restarting,
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.confirm),
                enabled = !restarting && selectedPackages.isNotEmpty(),
                onClick = {
                    val commands = buildList {
                        if (SYSTEM_UI_PACKAGE in selectedPackages) add("killall $SYSTEM_UI_PACKAGE")
                        if (DOWNLOADS_PACKAGE in selectedPackages) add("am force-stop $DOWNLOADS_PACKAGE")
                        if (XMSF_PACKAGE in selectedPackages) add("am force-stop $XMSF_PACKAGE")
                        if (SETTINGS_PACKAGE in selectedPackages) add("am force-stop $SETTINGS_PACKAGE")
                        if (SCREEN_RECORDER_PACKAGE in selectedPackages) {
                            add("am force-stop $SCREEN_RECORDER_PACKAGE")
                        }
                        if (SECURITY_CENTER_PACKAGE in selectedPackages) {
                            add("am force-stop $SECURITY_CENTER_PACKAGE")
                        }
                    }
                    restarting = true
                    error = null
                    scope.launch {
                        RestartScopeService.restart(commands)
                            .onSuccess { onDismiss() }
                            .onFailure { error = rootRequired }
                        restarting = false
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun RestartScopeRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.headline1,
        )
        Spacer(Modifier.width(12.dp))
        Checkbox(
            state = ToggleableState(checked),
            onClick = onClick,
            enabled = enabled,
        )
    }
}

private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
private const val DOWNLOADS_PACKAGE = "com.android.providers.downloads"
private const val XMSF_PACKAGE = "com.xiaomi.xmsf"
private const val SETTINGS_PACKAGE = "com.android.settings"
private const val SCREEN_RECORDER_PACKAGE = "com.miui.screenrecorder"
private const val SECURITY_CENTER_PACKAGE = "com.miui.securitycenter"
private val RestartErrorColor = Color(0xFFFF5A52)
