package io.github.hyperisland.compose.page.apps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.InstalledApp
import io.github.hyperisland.compose.data.toast.ToastSettingsPatch
import io.github.hyperisland.compose.data.toast.toFullPatch
import io.github.hyperisland.compose.data.toast.withPatch
import io.github.hyperisland.compose.page.apps.toast.ToastSettingsFormMode
import io.github.hyperisland.compose.page.apps.toast.ToastSettingsFormPage

@Composable
internal fun ToastSettingsPage(
    app: InstalledApp,
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
) {
    var settings by remember(app.packageName) {
        mutableStateOf(prefs.toastAppSettings(app.packageName))
    }
    var formState by remember(app.packageName) { mutableStateOf(settings.toFullPatch()) }
    val defaults = remember { prefs.defaultConfigSettings() }

    fun update(value: ToastSettingsPatch) {
        val updated = settings.withPatch(value)
        settings = updated
        formState = updated.toFullPatch()
        prefs.setToastAppSettings(app.packageName, updated)
    }

    ToastSettingsFormPage(
        title = app.appName,
        state = formState,
        defaults = defaults,
        mode = ToastSettingsFormMode.Single,
        onStateChange = ::update,
        onBack = onBack,
    )
}
