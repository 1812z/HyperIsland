package io.github.hyperisland.compose.page.apps.toast

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.toast.ToastSettingsPatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun BatchToastSettingsPage(
    packageNames: Set<String>,
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
) {
    var patch by remember(packageNames) { mutableStateOf(ToastSettingsPatch()) }
    var applying by remember { mutableStateOf(false) }
    val defaults = remember { prefs.defaultConfigSettings() }
    val scope = rememberCoroutineScope()

    fun applyPatch() {
        if (!patch.hasChanges || applying) return
        applying = true
        scope.launch {
            withContext(Dispatchers.IO) {
                prefs.applyToastSettingsPatch(packageNames, patch)
            }
            applying = false
            onBack()
        }
    }

    ToastSettingsFormPage(
        title = stringResource(R.string.batch_toast_settings),
        state = patch,
        defaults = defaults,
        mode = ToastSettingsFormMode.Batch,
        onStateChange = { patch = it },
        onBack = onBack,
        headerText = stringResource(R.string.batch_toast_scope_apps, packageNames.size),
        footer = {
            Button(
                onClick = ::applyPatch,
                modifier = Modifier.fillMaxWidth(),
                enabled = patch.hasChanges && !applying,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(if (applying) R.string.applying else R.string.apply))
            }
        },
    )
}
