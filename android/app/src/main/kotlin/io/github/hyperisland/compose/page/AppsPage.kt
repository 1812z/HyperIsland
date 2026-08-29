package io.github.hyperisland.compose.page

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.CollapsingPage
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsActionWithArrow
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Messages

@Composable
internal fun AppsPage(prefs: FlutterPrefsRepository, openLegacy: (String) -> Unit) {
    val enabledCount = remember { prefs.enabledAppCount() }
    val toastEnabledCount = remember { prefs.toastEnabledAppCount() }
    CollapsingPage(title = stringResource(R.string.compose_nav_apps)) {
        item {
            SectionTitle(stringResource(R.string.compose_island))
            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsActionWithArrow(
                    title = stringResource(R.string.compose_enabled_apps_count, enabledCount),
                    icon = MiuixIcons.GridView,
                    onClick = { openLegacy("/apps") },
                )
            }
        }
        item {
            SectionTitle(stringResource(R.string.compose_toast_adaptation))
            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsActionWithArrow(
                    title = stringResource(R.string.compose_toast_enabled_apps_count, toastEnabledCount),
                    icon = MiuixIcons.Messages,
                    onClick = { openLegacy("/apps") },
                )
            }
        }
    }
}
