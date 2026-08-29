package io.github.hyperisland.compose.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.CollapsingPage
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsAction
import io.github.hyperisland.compose.component.SettingsActionWithArrow
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberStringPreference
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Translate
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Update

internal enum class SettingsDetail { Theme, HideBehavior, Misc, Other, References }

@Composable
internal fun SettingsPage(
    prefs: FlutterPrefsRepository,
    openLegacy: (String) -> Unit,
    onOpenDetail: (SettingsDetail) -> Unit,
) {
    val context = LocalContext.current
    val locale = rememberStringPreference(prefs, KEY_LOCALE, "")
    val localeValues = listOf("", "zh", "en", "ja", "ru", "tr")
    val snackbarState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.compose_group_number_copied)
    CollapsingPage(
        title = stringResource(R.string.compose_nav_settings),
        snackbarHost = { SnackbarHost(snackbarState) },
    ) {
        item {
            SectionTitle(stringResource(R.string.compose_island))
            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsActionWithArrow(stringResource(R.string.compose_appearance), MiuixIcons.Image) {
                    openLegacy("/settings/appearance")
                }
                SettingsActionWithArrow(stringResource(R.string.compose_ai_summary), MiuixIcons.Messages) {
                    openLegacy("/settings/ai")
                }
                SettingsActionWithArrow(stringResource(R.string.compose_filter_rules), MiuixIcons.Blocklist) {
                    openLegacy("/settings/filter-rules")
                }
                SettingsActionWithArrow(stringResource(R.string.compose_default_config), MiuixIcons.Tune) {
                    openLegacy("/settings/default")
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
                    openLegacy("/settings/hook-extension")
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.compose_backup_restore))
            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsActionWithArrow(stringResource(R.string.compose_backup_restore), MiuixIcons.Backup) {
                    openLegacy("/settings/backup-restore")
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
        item {
            SectionTitle(stringResource(R.string.compose_about))
            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsAction(stringResource(R.string.compose_check_update_action), MiuixIcons.Update) {
                    openLegacy("/settings")
                }
                SettingsAction(
                    title = stringResource(R.string.compose_github),
                    icon = MiuixIcons.Info,
                    summary = stringResource(R.string.compose_github_summary),
                    endIcon = MiuixIcons.Link,
                ) {
                    context.openUrl(GITHUB_URL)
                }
                SettingsAction(
                    title = stringResource(R.string.compose_telegram),
                    icon = MiuixIcons.Messages,
                    summary = stringResource(R.string.compose_telegram_summary),
                    endIcon = MiuixIcons.Link,
                ) {
                    context.openUrl(TELEGRAM_URL)
                }
                SettingsAction(
                    title = stringResource(R.string.compose_qq_group),
                    icon = MiuixIcons.Messages,
                    summary = stringResource(R.string.compose_qq_group_summary),
                    endIcon = MiuixIcons.Copy,
                ) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(QQ_CLIP_LABEL, QQ_GROUP_NUMBER))
                    scope.launch { snackbarState.showSnackbar(copiedMessage) }
                }
                SettingsActionWithArrow(stringResource(R.string.compose_references), MiuixIcons.Info) {
                    onOpenDetail(SettingsDetail.References)
                }
            }
        }
    }
}

private fun Context.openUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private const val GITHUB_URL = "https://github.com/1812z/HyperIsland"
private const val TELEGRAM_URL = "https://t.me/HyperIsland_Module"
private const val QQ_GROUP_NUMBER = "1045114341"
private const val QQ_CLIP_LABEL = "QQ"
private const val KEY_LOCALE = "pref_locale"
