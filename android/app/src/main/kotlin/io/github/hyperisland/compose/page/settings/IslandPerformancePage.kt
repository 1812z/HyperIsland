package io.github.hyperisland.compose.page.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.github.hyperisland.compose.component.AppCard
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsAction
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.service.IslandPerformanceClient
import io.github.hyperisland.data.DebugPreferences
import io.github.hyperisland.data.IslandPerformanceContract as Contract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun IslandPerformancePage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val debug = rememberBooleanPreference(prefs, DebugPreferences.MODE, false)
    var report by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val errorMessage = stringResource(R.string.debug_performance_unavailable)
    val exportedMessage = stringResource(R.string.debug_performance_exported)
    val exportError = stringResource(R.string.debug_performance_export_error)
    fun request(command: String) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                report = IslandPerformanceClient.request(context, command)
            } catch (_: Exception) {
                error = errorMessage
            } finally {
                busy = false
            }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val raw = pendingExport
        pendingExport = null
        if (uri != null && raw != null) scope.launch {
            val success = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(raw) }
                        ?: error("Output stream unavailable")
                }
            }.isSuccess
            Toast.makeText(context, if (success) exportedMessage else exportError, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(debug.value) { if (debug.value) request(Contract.STATUS) }
    val recording = report?.optBoolean("recording") == true
    DetailPage(title = stringResource(R.string.debug_performance), onBack = onBack) {
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                PerformanceText(stringResource(R.string.debug_performance_description))
                SettingsAction(
                    title = stringResource(if (recording) R.string.debug_performance_stop else R.string.debug_performance_start),
                    enabled = debug.value && !busy,
                    onClick = { request(if (recording) Contract.STOP else Contract.START) },
                )
                SettingsAction(
                    title = stringResource(R.string.debug_performance_refresh),
                    enabled = debug.value && !busy,
                    onClick = { request(Contract.STATUS) },
                )
                SettingsAction(
                    title = stringResource(R.string.debug_performance_export),
                    enabled = report != null && !recording && !busy && (report?.optDouble("durationMs") ?: 0.0) > 0,
                    onClick = {
                        pendingExport = report?.toString(2)
                        export.launch("hyperisland-performance-${System.currentTimeMillis()}.json")
                    },
                )
            }
        }
        error?.let { message -> item { AppCard { PerformanceText(message) } } }
        report?.let { data ->
            item {
                SectionTitle(stringResource(R.string.debug_performance_result))
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    PerformanceText(stringResource(
                        R.string.debug_performance_summary,
                        if (recording) stringResource(R.string.debug_performance_recording) else stringResource(R.string.debug_performance_idle),
                        data.optDouble("durationMs") / 1000.0, data.optInt("frames"),
                        data.optInt("delayedFrames"), data.optInt("missedVsyncs"),
                        data.optDouble("maxGapMs"), data.optDouble("maxDelayMs"), data.optInt("instrumentedMethods"),
                    ))
                    if (data.optInt("frames") == 0) PerformanceText(stringResource(R.string.debug_performance_no_frames))
                }
            }
            val hooks = data.optJSONArray("hooks")
            if (hooks != null && hooks.length() > 0) {
                item { SectionTitle(stringResource(R.string.debug_performance_hot_hooks)) }
                items(minOf(hooks.length(), 12)) { index ->
                    val hook = hooks.getJSONObject(index)
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        PerformanceText(hook.optString("hook"))
                        PerformanceText(stringResource(
                            R.string.debug_performance_hook_summary, hook.optLong("calls"),
                            hook.optDouble("mainSelfMs"), hook.optDouble("maxSelfMs"), hook.optDouble("maxTotalMs"),
                        ))
                    }
                }
            }
        }
        item { AppCard(modifier = Modifier.fillMaxWidth()) { PerformanceText(stringResource(R.string.debug_performance_limits)) } }
    }
}

@Composable
private fun PerformanceText(value: String) {
    Text(
        text = value,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}
