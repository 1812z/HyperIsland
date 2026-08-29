package io.github.hyperisland.compose.page

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.CollapsingPage
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsActionWithArrow
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberStringPreference
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Translate
import top.yukonga.miuix.kmp.icon.extended.Tune

internal enum class SettingsDetail {
    Appearance,
    Theme,
    HideBehavior,
    DefaultConfig,
    AiConfig,
    Misc,
    Other,
    References,
    BackupRestore,
    FilterRules,
    HookExtension,
}

@Composable
internal fun SettingsPage(
    prefs: FlutterPrefsRepository,
    openLegacy: (String) -> Unit,
    onOpenDetail: (SettingsDetail) -> Unit,
) {
    val locale = rememberStringPreference(prefs, KEY_LOCALE, "")
    val localeValues = listOf("", "zh", "en", "ja", "ru", "tr")
    CollapsingPage(
        title = stringResource(R.string.compose_nav_settings),
    ) {
        item {
            SectionTitle(stringResource(R.string.compose_island))
            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsActionWithArrow(stringResource(R.string.compose_appearance), MiuixIcons.Image) {
                    onOpenDetail(SettingsDetail.Appearance)
                }
                SettingsActionWithArrow(stringResource(R.string.compose_ai_summary), MiuixIcons.Messages) {
                    onOpenDetail(SettingsDetail.AiConfig)
                }
                SettingsActionWithArrow(stringResource(R.string.compose_filter_rules), MiuixIcons.Blocklist) {
                    onOpenDetail(SettingsDetail.FilterRules)
                }
                SettingsActionWithArrow(stringResource(R.string.compose_default_config), MiuixIcons.Tune) {
                    onOpenDetail(SettingsDetail.DefaultConfig)
                }
                SettingsActionWithArrow(stringResource(R.string.compose_hide_behavior), MiuixIcons.Hide) {
                    onOpenDetail(SettingsDetail.HideBehavior)
                }
                SettingsActionWithArrow(stringResource(R.string.compose_always_on_island), MiuixIcons.Pin) {
                    openLegacy("/settings/keep-island")
                }
                SettingsActionWithArrow(stringResource(R.string.compose_other), MiuixIcons.More) {
                    onOpenDetail(SettingsDetail.Other)
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.compose_misc))
            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsActionWithArrow(stringResource(R.string.compose_misc), MiuixIcons.Settings) {
                    onOpenDetail(SettingsDetail.Misc)
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.compose_hook_extension))
            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsActionWithArrow(stringResource(R.string.compose_hook_extension), MiuixIcons.Settings) {
                    onOpenDetail(SettingsDetail.HookExtension)
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.compose_appearance))
            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsActionWithArrow(stringResource(R.string.compose_theme), MiuixIcons.Theme) {
                    onOpenDetail(SettingsDetail.Theme)
                }
                PreferenceDropdown(
                    title = stringResource(R.string.compose_language),
                    summary = null,
                    icon = MiuixIcons.Translate,
                    items = listOf(
                        stringResource(R.string.compose_follow_system),
                        stringResource(R.string.compose_chinese),
                        stringResource(R.string.compose_english),
                        stringResource(R.string.compose_japanese),
                        stringResource(R.string.compose_russian),
                        stringResource(R.string.compose_turkish),
                    ),
                    selectedIndex = localeValues.indexOf(locale.value).coerceAtLeast(0),
                ) { index ->
                    val selectedLocale = localeValues[index]
                    locale.value = selectedLocale
                    if (selectedLocale.isBlank()) {
                        locale.value = ""
                        prefs.remove(KEY_LOCALE)
                    } else {
                        prefs.putString(KEY_LOCALE, selectedLocale)
                    }
                }
            }
        }
    }
}
private const val KEY_LOCALE = "pref_locale"
