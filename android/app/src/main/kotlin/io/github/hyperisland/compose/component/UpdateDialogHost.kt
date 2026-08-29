package io.github.hyperisland.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                    ReleaseNotes(available.update.changelog)
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
private fun ReleaseNotes(changelog: String) {
    val lines = changelog.trim().lines()
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        items(lines) { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Spacer(Modifier.height(3.dp))
                line.startsWith("#") -> Text(
                    text = markdownPlainText(line.trimStart('#').trim()),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                line.startsWith("- ") || line.startsWith("* ") -> Text(
                    text = "• ${markdownPlainText(line.drop(2))}",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                else -> Text(
                    text = markdownPlainText(line),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
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

private fun markdownPlainText(source: String): String = source
    .replace(MARKDOWN_LINK) { match ->
        "${match.groupValues[1]} (${match.groupValues[2]})"
    }
    .replace("**", "")
    .replace("__", "")
    .replace("`", "")

private val MARKDOWN_LINK = Regex("""\[([^]]+)]\(([^)]+)\)""")
