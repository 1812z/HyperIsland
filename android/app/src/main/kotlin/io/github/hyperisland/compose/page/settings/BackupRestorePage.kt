package io.github.hyperisland.compose.page.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.service.ConfigBackupService
import io.github.hyperisland.compose.service.InvalidConfigException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun BackupRestorePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var busyAction by remember { mutableStateOf<BackupAction?>(null) }
    var confirmCleanup by remember { mutableStateOf<CleanupAction?>(null) }

    val invalidFormat = stringResource(R.string.backup_error_invalid_format)
    val noFileSelected = stringResource(R.string.backup_error_no_file_selected)
    val emptyClipboard = stringResource(R.string.backup_error_empty_clipboard)
    val exportFailed = stringResource(R.string.backup_export_failed)
    val importFailed = stringResource(R.string.backup_import_failed)
    val exported = stringResource(R.string.backup_exported)
    val copied = stringResource(R.string.backup_copied)
    val imported = stringResource(R.string.backup_imported)
    val cleaned = stringResource(R.string.backup_cleaned)
    val cleanFailed = stringResource(R.string.backup_clean_failed)

    fun errorText(error: Throwable): String = when (error) {
        is InvalidConfigException -> invalidFormat
        else -> error.message ?: error.toString()
    }

    fun showMessage(message: String) {
        scope.launch { snackbarState.showSnackbar(message) }
    }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val json = pendingExport
        pendingExport = null
        if (uri == null || json == null) {
            busyAction = null
            if (uri == null) showMessage(noFileSelected)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")
                        ?.bufferedWriter(Charsets.UTF_8)
                        ?.use { it.write(json) }
                        ?: error("Cannot open output stream")
                }
            }
            busyAction = null
            showMessage(result.fold({ exported }, { "$exportFailed：${errorText(it)}" }))
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            busyAction = null
            showMessage(noFileSelected)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val json = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: error("Cannot open input stream")
                    ConfigBackupService.importJson(context, json)
                }
            }
            busyAction = null
            showMessage(result.fold({ imported.format(it) }, { "$importFailed：${errorText(it)}" }))
        }
    }

    fun exportFile() {
        if (busyAction != null) return
        busyAction = BackupAction.ExportFile
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { ConfigBackupService.exportJson(context) } }
                .onSuccess {
                    pendingExport = it
                    createDocument.launch("hyperisland_config.json")
                }
                .onFailure {
                    busyAction = null
                    showMessage("$exportFailed：${errorText(it)}")
                }
        }
    }

    fun exportClipboard() {
        if (busyAction != null) return
        busyAction = BackupAction.ExportClipboard
        scope.launch {
            val result = runCatching {
                val json = withContext(Dispatchers.IO) { ConfigBackupService.exportJson(context) }
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("HyperIsland", json))
            }
            busyAction = null
            showMessage(result.fold({ copied }, { "$exportFailed：${errorText(it)}" }))
        }
    }

    fun importClipboard() {
        if (busyAction != null) return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val raw = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (raw.isEmpty()) {
            showMessage(emptyClipboard)
            return
        }
        busyAction = BackupAction.ImportClipboard
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { ConfigBackupService.importJson(context, raw) }
            }
            busyAction = null
            showMessage(result.fold({ imported.format(it) }, { "$importFailed：${errorText(it)}" }))
        }
    }

    fun clean(action: CleanupAction) {
        confirmCleanup = null
        if (busyAction != null) return
        busyAction = action.busyAction
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    when (action) {
                        CleanupAction.Uninstalled -> ConfigBackupService.cleanUninstalled(context)
                        CleanupAction.Disabled -> ConfigBackupService.cleanDisabled(context)
                    }
                }
            }
            busyAction = null
            showMessage(result.fold({ cleaned.format(it) }, { "$cleanFailed：${errorText(it)}" }))
        }
    }

    DetailPage(
        title = stringResource(R.string.backup_restore),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarState) },
    ) {
        item {
            SectionTitle(stringResource(R.string.config))
            Card(modifier = Modifier.fillMaxWidth()) {
                BackupRow(
                    title = stringResource(R.string.backup_export_file),
                    summary = stringResource(R.string.backup_export_file_summary),
                    loading = busyAction == BackupAction.ExportFile,
                    enabled = busyAction == null,
                    onClick = ::exportFile,
                )
                BackupRow(
                    title = stringResource(R.string.backup_export_clipboard),
                    summary = stringResource(R.string.backup_export_clipboard_summary),
                    loading = busyAction == BackupAction.ExportClipboard,
                    enabled = busyAction == null,
                    onClick = ::exportClipboard,
                )
                BackupRow(
                    title = stringResource(R.string.backup_import_file),
                    summary = stringResource(R.string.backup_import_file_summary),
                    loading = busyAction == BackupAction.ImportFile,
                    enabled = busyAction == null,
                    onClick = {
                        busyAction = BackupAction.ImportFile
                        openDocument.launch(arrayOf("application/json", "text/json", "text/plain"))
                    },
                )
                BackupRow(
                    title = stringResource(R.string.backup_import_clipboard),
                    summary = stringResource(R.string.backup_import_clipboard_summary),
                    loading = busyAction == BackupAction.ImportClipboard,
                    enabled = busyAction == null,
                    onClick = ::importClipboard,
                )
            }
        }
        item {
            SectionTitle(stringResource(R.string.backup_cleanup_section))
            Card(modifier = Modifier.fillMaxWidth()) {
                BackupRow(
                    title = stringResource(R.string.backup_clean_uninstalled),
                    summary = stringResource(R.string.backup_clean_uninstalled_summary),
                    loading = busyAction == BackupAction.CleanUninstalled,
                    enabled = busyAction == null,
                    onClick = { confirmCleanup = CleanupAction.Uninstalled },
                )
                BackupRow(
                    title = stringResource(R.string.backup_clean_disabled),
                    summary = stringResource(R.string.backup_clean_disabled_summary),
                    loading = busyAction == BackupAction.CleanDisabled,
                    enabled = busyAction == null,
                    onClick = { confirmCleanup = CleanupAction.Disabled },
                )
            }
        }
    }

    val cleanup = confirmCleanup
    WindowDialog(
        show = cleanup != null,
        title = cleanup?.let { stringResource(it.title) }.orEmpty(),
        onDismissRequest = { confirmCleanup = null },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = cleanup?.let { stringResource(it.message) }.orEmpty(),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { confirmCleanup = null },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { cleanup?.let(::clean) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    }
}

@Composable
private fun BackupRow(
    title: String,
    summary: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        summary = summary,
        enabled = enabled,
        endActions = {
            if (loading) CircularProgressIndicator(size = 20.dp)
        },
        onClick = onClick,
    )
}

private enum class BackupAction {
    ExportFile,
    ExportClipboard,
    ImportFile,
    ImportClipboard,
    CleanUninstalled,
    CleanDisabled,
}

private enum class CleanupAction(
    val title: Int,
    val message: Int,
    val busyAction: BackupAction,
) {
    Uninstalled(
        R.string.backup_clean_uninstalled,
        R.string.backup_clean_uninstalled_confirm,
        BackupAction.CleanUninstalled,
    ),
    Disabled(
        R.string.backup_clean_disabled,
        R.string.backup_clean_disabled_confirm,
        BackupAction.CleanDisabled,
    ),
}
