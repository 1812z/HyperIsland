package io.github.hyperisland.compose.page.settings.extensions

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
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
import io.github.hyperisland.compose.data.rememberStringPreference
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState

@Composable
internal fun FaceUnlockIslandPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scopeFailed = stringResource(R.string.ext_scope_failed)
    val restartRequired = stringResource(R.string.restart_scope_app)
    val enabled = rememberBooleanPreference(prefs, KEY_FACE_UNLOCK_ISLAND, false)
    val firstFloat = rememberBooleanPreference(prefs, KEY_FACE_UNLOCK_FIRST_FLOAT, true)
    val animation = rememberStringPreference(prefs, KEY_FACE_UNLOCK_ANIMATION, MODE_DEFAULT)
    val keep = rememberBooleanPreference(prefs, KEY_FACE_UNLOCK_KEEP, false)
    val animationValues = listOf(MODE_DEFAULT, ANIMATION_LOCK)

    fun show(message: String) { scope.launch { snackbar.showSnackbar(message) } }

    DetailPage(
        title = stringResource(R.string.ext_face_settings),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) {
        item {
            SectionTitle(stringResource(R.string.config))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.ext_face_enable),
                    summary = stringResource(R.string.ext_face_enable_summary),
                    icon = null,
                    checked = enabled.value,
                ) { value ->
                    val granted = !value || HookExtensionService.requestScope(
                        context,
                        listOf("com.android.systemui"),
                    ).onFailure { show(it.message ?: scopeFailed) }.isSuccess
                    if (granted) {
                        enabled.value = value
                        prefs.putBoolean(KEY_FACE_UNLOCK_ISLAND, value)
                        show(restartRequired)
                    }
                }
                PreferenceSwitch(
                    title = stringResource(R.string.ext_face_first_float),
                    summary = stringResource(R.string.ext_face_first_float_summary),
                    icon = null,
                    checked = firstFloat.value,
                    enabled = enabled.value,
                ) { value -> firstFloat.value = value; prefs.putBoolean(KEY_FACE_UNLOCK_FIRST_FLOAT, value) }
                PreferenceDropdown(
                    title = stringResource(R.string.ext_animation_style),
                    summary = stringResource(R.string.ext_animation_style_summary),
                    icon = null,
                    items = listOf(stringResource(R.string.default_option), stringResource(R.string.ext_lock)),
                    selectedIndex = animationValues.indexOf(animation.value).coerceAtLeast(0),
                    enabled = enabled.value,
                ) { index ->
                    animation.value = animationValues[index]
                    prefs.putString(KEY_FACE_UNLOCK_ANIMATION, animationValues[index])
                }
                PreferenceSwitch(
                    title = stringResource(R.string.ext_face_keep),
                    summary = stringResource(R.string.ext_face_keep_summary),
                    icon = null,
                    checked = keep.value,
                    enabled = enabled.value,
                ) { value -> keep.value = value; prefs.putBoolean(KEY_FACE_UNLOCK_KEEP, value) }
            }
        }
    }
}
