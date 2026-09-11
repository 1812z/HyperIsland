package io.github.hyperisland.compose.page.settings.extensions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.data.rememberStringPreference
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SnackbarHost

@Composable
internal fun SystemSettingsHookPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val actions = rememberHookScopeActions()
    val scopeFailed = stringResource(R.string.ext_scope_failed)
    val restartRequired = stringResource(R.string.restart_scope_app)

    val settingsEntry = rememberBooleanPreference(prefs, KEY_SETTINGS_HOME_ENTRY, true)
    val settingsEntryPosition = rememberStringPreference(
        prefs,
        KEY_SETTINGS_HOME_ENTRY_POSITION,
        SETTINGS_POSITION_TOP,
    )
    val settingsEntrySameGroup = rememberBooleanPreference(
        prefs,
        KEY_SETTINGS_HOME_ENTRY_SAME_GROUP,
        true,
    )
    val settingsIcon = rememberStringPreference(prefs, KEY_SETTINGS_HOME_ENTRY_ICON_STYLE, MODE_DEFAULT)

    HookExtensionScaffold(
        title = stringResource(R.string.ext_system_settings),
        onBack = onBack,
        snackbarHost = { SnackbarHost(actions.snackbar) },
        restartPackages = RESTART_SCOPE_SETTINGS,
    ) {
        item {
            SectionTitle(stringResource(R.string.config))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.ext_settings_entry),
                    summary = stringResource(R.string.ext_settings_entry_summary),
                    icon = null,
                    checked = settingsEntry.value,
                ) { value ->
                    if (actions.request(value, listOf(PKG_SETTINGS), scopeFailed)) {
                        settingsEntry.value = value
                        prefs.putBoolean(KEY_SETTINGS_HOME_ENTRY, value)
                        actions.show(restartRequired)
                    }
                }
                AnimatedVisibility(settingsEntry.value) {
                    PreferenceSwitch(
                        title = stringResource(R.string.ext_settings_entry_same_group),
                        summary = stringResource(R.string.ext_settings_entry_same_group_summary),
                        icon = null,
                        checked = settingsEntrySameGroup.value,
                    ) { value ->
                        settingsEntrySameGroup.value = value
                        prefs.putBoolean(KEY_SETTINGS_HOME_ENTRY_SAME_GROUP, value)
                        actions.show(restartRequired)
                    }
                }
                AnimatedVisibility(settingsEntry.value) {
                    val values = listOf(
                        SETTINGS_POSITION_TOP,
                        SETTINGS_POSITION_MIDDLE,
                        SETTINGS_POSITION_BOTTOM,
                    )
                    PreferenceDropdown(
                        title = stringResource(R.string.ext_settings_entry_position),
                        summary = null,
                        icon = null,
                        items = listOf(
                            stringResource(R.string.ext_settings_entry_position_top),
                            stringResource(R.string.ext_settings_entry_position_middle),
                            stringResource(R.string.ext_settings_entry_position_bottom),
                        ),
                        selectedIndex = values.indexOf(settingsEntryPosition.value).coerceAtLeast(0),
                    ) { index ->
                        settingsEntryPosition.value = values[index]
                        prefs.putString(KEY_SETTINGS_HOME_ENTRY_POSITION, values[index])
                        actions.show(restartRequired)
                    }
                }
                AnimatedVisibility(settingsEntry.value) {
                    val values = listOf(MODE_DEFAULT, MODE_OUTLINE)
                    PreferenceDropdown(
                        title = stringResource(R.string.ext_icon_style),
                        summary = null,
                        icon = null,
                        items = listOf(
                            stringResource(R.string.default_option),
                            stringResource(R.string.ext_icon_outline),
                        ),
                        selectedIndex = values.indexOf(settingsIcon.value).coerceAtLeast(0),
                    ) { index ->
                        settingsIcon.value = values[index]
                        prefs.putString(KEY_SETTINGS_HOME_ENTRY_ICON_STYLE, values[index])
                    }
                }
            }
        }
    }
}
