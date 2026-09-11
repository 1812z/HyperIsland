package io.github.hyperisland.compose.component

import androidx.annotation.StringRes
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

internal data class RestartScopeTarget(
    val packageName: String,
    @StringRes val label: Int,
    val command: String,
)

internal val RestartScopeTargets = listOf(
    RestartScopeTarget(
        packageName = "com.android.systemui",
        label = R.string.system_ui,
        command = "killall com.android.systemui",
    ),
    RestartScopeTarget(
        packageName = "com.milink.service",
        label = R.string.milink_service,
        command = "am force-stop com.milink.service",
    ),
    RestartScopeTarget(
        packageName = "com.android.settings",
        label = R.string.hook_scope_settings,
        command = "am force-stop com.android.settings",
    ),
    RestartScopeTarget(
        packageName = "com.xiaomi.xmsf",
        label = R.string.xmsf,
        command = "am force-stop com.xiaomi.xmsf",
    ),
    RestartScopeTarget(
        packageName = "com.android.providers.downloads",
        label = R.string.download_manager,
        command = "am force-stop com.android.providers.downloads",
    ),
    RestartScopeTarget(
        packageName = "com.miui.screenrecorder",
        label = R.string.screen_recorder,
        command = "am force-stop com.miui.screenrecorder",
    ),
    RestartScopeTarget(
        packageName = "com.miui.securitycenter",
        label = R.string.security_center,
        command = "am force-stop com.miui.securitycenter",
    ),
)

/**
 * 重启作用域弹窗。
 *
 * @param allowedPackages null 表示显示全部作用域（主页面）；非 null 时仅显示列表内作用域。
 * @param preselectedPackages 进入页面时默认勾选的作用域，会与 [allowedPackages] 求交集。
 */
@Composable
internal fun RestartScopeDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    allowedPackages: Set<String>? = null,
    preselectedPackages: Set<String> = emptySet(),
) {
    val scope = rememberCoroutineScope()
    val targets = remember(allowedPackages) {
        if (allowedPackages == null) {
            RestartScopeTargets
        } else {
            RestartScopeTargets.filter { it.packageName in allowedPackages }
        }
    }
    var selectedPackages by remember(show, targets) {
        mutableStateOf(preselectedPackages.intersect(targets.map { it.packageName }.toSet()))
    }
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
            targets.forEach { target ->
                val checked = target.packageName in selectedPackages
                RestartScopeRow(
                    label = stringResource(target.label),
                    checked = checked,
                    enabled = !restarting,
                    onClick = {
                        selectedPackages = if (checked) {
                            selectedPackages - target.packageName
                        } else {
                            selectedPackages + target.packageName
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
                    val commands = targets
                        .filter { it.packageName in selectedPackages }
                        .map { it.command }
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

private val RestartErrorColor = Color(0xFFFF5A52)
