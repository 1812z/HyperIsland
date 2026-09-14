package io.github.hyperisland.compose.page.settings.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.PreferenceSlider
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsAction
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.data.rememberStringPreference
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun LockscreenNegativePage(
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
) {
    val actions = rememberHookScopeActions()
    val scopeFailed = stringResource(R.string.ext_scope_failed)
    val restartRequired = stringResource(R.string.restart_scope_app)
    val defaultTitle = stringResource(R.string.lockscreen_widgets_title_default)
    val enabled = rememberBooleanPreference(
        prefs,
        KEY_LOCKSCREEN_NEGATIVE_PAGE_ENABLED,
        prefs.getBoolean(KEY_LOCKSCREEN_DEVICE_CENTER, false) ||
            prefs.getString(KEY_LOCKSCREEN_NEGATIVE_PAGE_MODE, LOCKSCREEN_PAGE_MODE_DEVICE_CENTER) ==
            LOCKSCREEN_PAGE_MODE_WIDGETS,
    )
    val dimEnabled = rememberBooleanPreference(prefs, KEY_LOCKSCREEN_NEGATIVE_PAGE_DIM_ENABLED, true)
    val mode = rememberStringPreference(
        prefs,
        KEY_LOCKSCREEN_NEGATIVE_PAGE_MODE,
        LOCKSCREEN_PAGE_MODE_DEVICE_CENTER,
    )
    val title = rememberStringPreference(prefs, KEY_LOCKSCREEN_NEGATIVE_PAGE_TITLE, defaultTitle)
    var showTitleDialog by remember { mutableStateOf(false) }
    var titleDraft by remember(showTitleDialog, title.value) { mutableStateOf(title.value) }
    val dimAmount = remember(KEY_LOCKSCREEN_NEGATIVE_PAGE_DIM_AMOUNT) {
        mutableFloatStateOf(prefs.getLong(KEY_LOCKSCREEN_NEGATIVE_PAGE_DIM_AMOUNT, 50L).toFloat())
    }

    HookExtensionScaffold(
        title = stringResource(R.string.ext_lockscreen_negative_page),
        onBack = onBack,
        snackbarHost = { SnackbarHost(actions.snackbar) },
        restartPackages = RESTART_SCOPE_SYSTEM_UI,
    ) {
        item {
            SectionTitle(stringResource(R.string.config))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.ext_lockscreen_negative_page_enabled),
                    summary = stringResource(R.string.ext_lockscreen_negative_page_enabled_summary),
                    icon = null,
                    checked = enabled.value,
                ) { value ->
                    if (actions.request(value, listOf(PKG_SYSTEM_UI), scopeFailed)) {
                        enabled.value = value
                        prefs.putBoolean(KEY_LOCKSCREEN_NEGATIVE_PAGE_ENABLED, value)
                        actions.show(restartRequired)
                    }
                }
                PreferenceDropdown(
                    title = stringResource(R.string.ext_lockscreen_negative_page_mode),
                    summary = stringResource(R.string.ext_lockscreen_negative_page_mode_summary),
                    icon = null,
                    enabled = enabled.value,
                    items = listOf(
                        stringResource(R.string.ext_lockscreen_page_device_center),
                        stringResource(R.string.ext_lockscreen_page_widgets),
                    ),
                    selectedIndex = if (mode.value == LOCKSCREEN_PAGE_MODE_WIDGETS) 1 else 0,
                ) { index ->
                    val selected = if (index == 1) LOCKSCREEN_PAGE_MODE_WIDGETS
                    else LOCKSCREEN_PAGE_MODE_DEVICE_CENTER
                    mode.value = selected
                    prefs.putString(KEY_LOCKSCREEN_NEGATIVE_PAGE_MODE, selected)
                    actions.show(restartRequired)
                }
                AnimatedVisibility(
                    visible = enabled.value && mode.value == LOCKSCREEN_PAGE_MODE_WIDGETS,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    SettingsAction(
                        title = stringResource(R.string.ext_lockscreen_negative_page_title),
                        summary = title.value,
                        endIcon = MiuixIcons.ChevronForward,
                    ) { showTitleDialog = true }
                }
                PreferenceSwitch(
                    title = stringResource(R.string.ext_lockscreen_negative_page_dim),
                    summary = stringResource(R.string.ext_lockscreen_negative_page_dim_summary),
                    icon = null,
                    enabled = enabled.value,
                    checked = dimEnabled.value,
                ) { value ->
                    dimEnabled.value = value
                    prefs.putBoolean(KEY_LOCKSCREEN_NEGATIVE_PAGE_DIM_ENABLED, value)
                }
                androidx.compose.animation.AnimatedVisibility(enabled.value && dimEnabled.value) {
                    PreferenceSlider(
                        title = stringResource(R.string.ext_lockscreen_negative_page_dim_amount),
                        icon = null,
                        value = dimAmount.floatValue,
                        valueText = "${dimAmount.floatValue.toInt()}%",
                        valueRange = 0f..100f,
                        steps = 99,
                        onValueChange = { dimAmount.floatValue = it },
                        onValueChangeFinished = {
                            prefs.putLong(
                                KEY_LOCKSCREEN_NEGATIVE_PAGE_DIM_AMOUNT,
                                dimAmount.floatValue.toLong(),
                            )
                        },
                    )
                }
            }
        }
    }

    WindowDialog(
        show = showTitleDialog,
        title = stringResource(R.string.ext_lockscreen_negative_page_title_dialog),
        onDismissRequest = { showTitleDialog = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TextField(
                value = titleDraft,
                onValueChange = { titleDraft = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.ext_lockscreen_negative_page_title_hint),
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showTitleDialog = false },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val value = titleDraft.trim().ifEmpty { defaultTitle }
                        title.value = value
                        prefs.putString(KEY_LOCKSCREEN_NEGATIVE_PAGE_TITLE, value)
                        showTitleDialog = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}
