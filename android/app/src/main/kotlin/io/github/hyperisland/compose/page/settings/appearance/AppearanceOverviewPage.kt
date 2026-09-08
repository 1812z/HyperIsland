package io.github.hyperisland.compose.page.settings.appearance

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.SettingsAction
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight

internal enum class AppearanceSection { Size, Background, Text, Icon, Outline }

@Composable
internal fun AppearanceOverviewPage(
    onOpenSection: (AppearanceSection) -> Unit,
    onBack: () -> Unit,
) {
    AppearanceDetailPage(title = stringResource(R.string.appearance), onBack = onBack) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                AppearanceSection.entries.forEach { section ->
                    SettingsAction(
                        title = appearanceSectionTitle(section),
                        summary = appearanceSectionSummary(section),
                        endIcon = MiuixIcons.Basic.ArrowRight,
                    ) { onOpenSection(section) }
                }
            }
        }
    }
}

@Composable
private fun appearanceSectionTitle(section: AppearanceSection): String = stringResource(
    when (section) {
        AppearanceSection.Size -> R.string.appearance_size
        AppearanceSection.Background -> R.string.appearance_background
        AppearanceSection.Text -> R.string.appearance_text
        AppearanceSection.Icon -> R.string.appearance_icon
        AppearanceSection.Outline -> R.string.appearance_outline
    },
)

@Composable
private fun appearanceSectionSummary(section: AppearanceSection): String = stringResource(
    when (section) {
        AppearanceSection.Size -> R.string.appearance_size_summary
        AppearanceSection.Background -> R.string.appearance_background_summary
        AppearanceSection.Text -> R.string.appearance_text_summary
        AppearanceSection.Icon -> R.string.appearance_icon_summary
        AppearanceSection.Outline -> R.string.appearance_outline_summary
    },
)
