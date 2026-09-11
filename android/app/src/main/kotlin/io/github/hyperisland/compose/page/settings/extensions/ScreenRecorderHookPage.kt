package io.github.hyperisland.compose.page.settings.extensions

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsItemMargin
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.data.rememberStringPreference
import io.github.hyperisland.compose.service.XposedScopeService
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ScreenRecorderHookPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actions = rememberHookScopeActions()
    val scopeFailed = stringResource(R.string.ext_scope_failed)
    val restartRequired = stringResource(R.string.restart_scope_app)

    val screenRecorderIsland = rememberBooleanPreference(prefs, KEY_SCREEN_RECORDER_ISLAND, false)
    val screenRecorderImmediateStart = rememberBooleanPreference(
        prefs,
        KEY_SCREEN_RECORDER_IMMEDIATE_START,
        false,
    )
    val screenRecorderIconStyle = rememberStringPreference(
        prefs,
        KEY_SCREEN_RECORDER_ICON_STYLE,
        SCREEN_RECORDER_ICON_VOICE_RECORDER,
    )
    val scopeRequestPending = remember { mutableStateOf(false) }
    val warningColors = if (MiuixTheme.colorScheme.background.luminance() > 0.5f) {
        CardDefaults.defaultColors(
            color = Color(0xFFFFF3D6),
            contentColor = Color(0xFF704D00),
        )
    } else {
        CardDefaults.defaultColors(
            color = Color(0xFF3A2D12),
            contentColor = Color(0xFFFFD978),
        )
    }

    HookExtensionScaffold(
        title = stringResource(R.string.screen_recorder_dialog_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(actions.snackbar) },
    ) {
        item {
            SectionTitle(stringResource(R.string.config))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.ext_screen_recorder_island),
                    summary = stringResource(R.string.ext_screen_recorder_island_summary),
                    icon = null,
                    checked = screenRecorderIsland.value,
                    enabled = !scopeRequestPending.value,
                ) { value ->
                    if (!value) {
                        screenRecorderIsland.value = false
                        prefs.putBoolean(KEY_SCREEN_RECORDER_ISLAND, false)
                        actions.show(restartRequired)
                    } else {
                        scopeRequestPending.value = true
                        XposedScopeService.requestScope(
                            context = context,
                            packages = listOf(PKG_SCREEN_RECORDER),
                        ) { result ->
                            scope.launch {
                                scopeRequestPending.value = false
                                result.onSuccess {
                                    screenRecorderIsland.value = true
                                    prefs.putBoolean(KEY_SCREEN_RECORDER_ISLAND, true)
                                    actions.show(restartRequired)
                                }.onFailure {
                                    actions.show(it.message ?: scopeFailed)
                                }
                            }
                        }
                    }
                }
                AnimatedVisibility(screenRecorderIsland.value) {
                    val values = listOf(
                        SCREEN_RECORDER_ICON_VOICE_RECORDER,
                        SCREEN_RECORDER_ICON_SCREEN_RECORDER,
                    )
                    PreferenceDropdown(
                        title = stringResource(R.string.ext_screen_recorder_icon_style),
                        summary = stringResource(R.string.ext_screen_recorder_icon_style_summary),
                        icon = null,
                        items = listOf(
                            stringResource(R.string.ext_screen_recorder_icon_voice_recorder),
                            stringResource(R.string.ext_screen_recorder_icon_screen_recorder),
                        ),
                        selectedIndex = values.indexOf(screenRecorderIconStyle.value).coerceAtLeast(0),
                    ) { index ->
                        screenRecorderIconStyle.value = values[index]
                        prefs.putString(KEY_SCREEN_RECORDER_ICON_STYLE, values[index])
                    }
                }
                AnimatedVisibility(screenRecorderIsland.value) {
                    PreferenceSwitch(
                        title = stringResource(R.string.ext_screen_recorder_immediate_start),
                        summary = stringResource(R.string.ext_screen_recorder_immediate_start_summary),
                        icon = null,
                        checked = screenRecorderImmediateStart.value,
                    ) { value ->
                        screenRecorderImmediateStart.value = value
                        prefs.putBoolean(KEY_SCREEN_RECORDER_IMMEDIATE_START, value)
                    }
                }
            }
        }
        item {
            AnimatedVisibility(screenRecorderIsland.value) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = warningColors,
                    showIndication = true,
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(SCREEN_RECORDER_DOWNLOAD_URL)),
                        )
                    },
                ) {
                    BasicComponent(
                        title = stringResource(R.string.ext_screen_recorder_compatibility_title),
                        summary = stringResource(R.string.ext_screen_recorder_compatibility_summary),
                        insideMargin = SettingsItemMargin,
                    )
                }
            }
        }
    }
}

private const val SCREEN_RECORDER_DOWNLOAD_URL =
    "https://hyperisland.1812z.top/downloads.html#system-software"
