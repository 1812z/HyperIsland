package io.github.hyperisland.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.service.AppUpdate
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

internal sealed interface UpdateDialogState {
    data class Available(
        val currentVersion: String,
        val update: AppUpdate,
    ) : UpdateDialogState

    data object Failure : UpdateDialogState
}

@Composable
internal fun UpdateDialogHost(
    state: UpdateDialogState?,
    onDismiss: () -> Unit,
    onViewUpdate: (String) -> Unit,
) {
    val available = state as? UpdateDialogState.Available
    WindowDialog(
        show = available != null,
        title = stringResource(R.string.new_version_found),
        onDismissRequest = onDismiss,
    ) {
        if (available != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.current_version, "v${available.currentVersion}"))
                Text(stringResource(R.string.latest_version, "v${available.update.version}"))
                if (available.update.changelog.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MiuixTheme.colorScheme.dividerLine),
                    )
                    MarkdownBody(available.update.changelog, maxHeight = 240.dp)
                }
                DialogActions(
                    onCancel = onDismiss,
                    onConfirm = { onViewUpdate(available.update.releaseUrl) },
                )
            }
        }
    }

    WindowDialog(
        show = state == UpdateDialogState.Failure,
        title = stringResource(R.string.update_check_failed),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = stringResource(R.string.update_check_failed_message),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(R.string.confirm))
            }
        }
    }
}

@Composable
private fun DialogActions(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(
            text = stringResource(R.string.cancel),
            onClick = onCancel,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(stringResource(R.string.view))
        }
    }
}
