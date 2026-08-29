package io.github.hyperisland.compose.page.settings.extensions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.data.rememberLongPreference
import io.github.hyperisland.compose.data.rememberStringPreference
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TextField

@Composable
internal fun ChargeIslandPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scopeFailed = stringResource(R.string.ext_scope_failed)
    val restartRequired = stringResource(R.string.restart_scope_app)
    val enabled = rememberBooleanPreference(prefs, KEY_CHARGE_ISLAND, false)
    val leftMode = rememberStringPreference(prefs, KEY_CHARGE_LEFT_MODE, MODE_DEFAULT)
    val rightMode = rememberStringPreference(prefs, KEY_CHARGE_RIGHT_MODE, MODE_DEFAULT)
    val durationMode = rememberStringPreference(prefs, KEY_CHARGE_DURATION_MODE, MODE_DEFAULT)
    val durationSeconds = rememberLongPreference(prefs, KEY_CHARGE_DURATION_SECONDS, DEFAULT_CHARGE_DURATION)
    val outerGlow = rememberBooleanPreference(prefs, KEY_CHARGE_OUTER_GLOW, false)
    val durationDraft = remember(durationSeconds.value) { mutableStateOf(durationSeconds.value.toString()) }
    val modeValues = listOf(MODE_DEFAULT, MODE_POWER, MODE_VOLTAGE, MODE_CURRENT, MODE_LEVEL, MODE_TEMPERATURE)
    val modeLabels = listOf(
        stringResource(R.string.default_option),
        stringResource(R.string.ext_power),
        stringResource(R.string.ext_voltage),
        stringResource(R.string.ext_current),
        stringResource(R.string.ext_battery_level),
        stringResource(R.string.ext_battery_temperature),
    )
    val durationValues = listOf(MODE_DEFAULT, DURATION_CUSTOM, DURATION_PERSISTENT)

    fun show(message: String) { scope.launch { snackbar.showSnackbar(message) } }

    DetailPage(
        title = stringResource(R.string.ext_charge_settings),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) {
        item {
            SectionTitle(stringResource(R.string.config))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.ext_charge_enable),
                    summary = stringResource(R.string.ext_charge_enable_summary),
                    icon = null,
                    checked = enabled.value,
                ) { value ->
                    val granted = !value || HookExtensionService.requestScope(
                        context,
                        listOf("com.android.systemui"),
                    ).onFailure { show(it.message ?: scopeFailed) }.isSuccess
                    if (granted) {
                        enabled.value = value
                        prefs.putBoolean(KEY_CHARGE_ISLAND, value)
                        show(restartRequired)
                    }
                }
                PreferenceDropdown(
                    title = stringResource(R.string.ext_charge_left_mode),
                    summary = null,
                    icon = null,
                    items = modeLabels,
                    selectedIndex = modeValues.indexOf(leftMode.value).coerceAtLeast(0),
                ) { index -> leftMode.value = modeValues[index]; prefs.putString(KEY_CHARGE_LEFT_MODE, modeValues[index]) }
                PreferenceDropdown(
                    title = stringResource(R.string.ext_charge_right_mode),
                    summary = null,
                    icon = null,
                    items = modeLabels,
                    selectedIndex = modeValues.indexOf(rightMode.value).coerceAtLeast(0),
                ) { index -> rightMode.value = modeValues[index]; prefs.putString(KEY_CHARGE_RIGHT_MODE, modeValues[index]) }
                PreferenceDropdown(
                    title = stringResource(R.string.ext_duration),
                    summary = null,
                    icon = null,
                    items = listOf(
                        stringResource(R.string.default_option),
                        stringResource(R.string.ext_custom),
                        stringResource(R.string.ext_persistent),
                    ),
                    selectedIndex = durationValues.indexOf(durationMode.value).coerceAtLeast(0),
                ) { index ->
                    durationMode.value = durationValues[index]
                    prefs.putString(KEY_CHARGE_DURATION_MODE, durationValues[index])
                }
                AnimatedVisibility(durationMode.value == DURATION_CUSTOM) {
                    TextField(
                        value = durationDraft.value,
                        onValueChange = { input ->
                            val digits = input.filter(Char::isDigit).take(5)
                            durationDraft.value = digits
                            digits.toLongOrNull()?.coerceIn(1L, 86400L)?.let { value ->
                                durationSeconds.value = value
                                prefs.putLong(KEY_CHARGE_DURATION_SECONDS, value)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.ext_custom_duration),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                    )
                }
                PreferenceSwitch(
                    title = stringResource(R.string.ext_outer_glow),
                    summary = stringResource(R.string.ext_charge_glow_summary),
                    icon = null,
                    checked = outerGlow.value,
                ) { value -> outerGlow.value = value; prefs.putBoolean(KEY_CHARGE_OUTER_GLOW, value) }
            }
        }
    }
}
