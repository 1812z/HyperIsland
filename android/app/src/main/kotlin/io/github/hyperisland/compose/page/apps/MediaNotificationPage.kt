package io.github.hyperisland.compose.page.apps

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.InstalledApp
import io.github.hyperisland.compose.data.MediaNotificationSettings
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun MediaNotificationPage(
    app: InstalledApp,
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
) {
    var draft by remember(app.packageName) {
        mutableStateOf(prefs.mediaNotificationSettings(app.packageName))
    }
    var activeColorTarget by remember { mutableStateOf<ColorTarget?>(null) }
    var selectedColor by remember { mutableStateOf(Color.Red) }
    val defaultOuterGlow = prefs.getString(PREF_DEFAULT_OUTER_GLOW, TRI_STATE_OFF)
    val defaultIslandOuterGlow = prefs.getString(PREF_DEFAULT_ISLAND_OUTER_GLOW, TRI_STATE_OFF)
    val modes = remember { listOf(TRI_STATE_DEFAULT, TRI_STATE_ON, TRI_STATE_OFF, TRI_STATE_FOLLOW_DYNAMIC) }
    val outerGlowLabels = glowModeLabels(defaultOuterGlow)
    val islandOuterGlowLabels = glowModeLabels(defaultIslandOuterGlow)

    fun updateSettings(value: MediaNotificationSettings) {
        draft = value
        prefs.setMediaNotificationSettings(app.packageName, value)
    }

    DetailPage(
        title = stringResource(R.string.compose_media_notification),
        onBack = onBack,
        actionIcon = MiuixIcons.Refresh,
        actionDescription = stringResource(R.string.compose_restore_default),
        onAction = { updateSettings(MediaNotificationSettings()) },
    ) {
        item {
            SectionTitle(stringResource(R.string.compose_media_notification))
            Card {
                SwitchPreference(
                    checked = draft.enabled,
                    onCheckedChange = { updateSettings(draft.copy(enabled = it)) },
                    title = stringResource(R.string.compose_media_notification),
                    summary = stringResource(R.string.compose_media_notification_disabled_summary),
                    insideMargin = MEDIA_ITEM_MARGIN,
                )
                SwitchPreference(
                    checked = draft.normalNotification,
                    onCheckedChange = { updateSettings(draft.copy(normalNotification = it)) },
                    title = stringResource(R.string.compose_normal_notification),
                    summary = stringResource(R.string.compose_normal_notification_summary),
                    enabled = draft.enabled,
                    insideMargin = MEDIA_ITEM_MARGIN,
                )
            }
        }
        item {
            SectionTitle(
                stringResource(R.string.compose_glow_section, stringResource(R.string.compose_focus_notification)),
            )
            Card {
                OverlayDropdownPreference(
                    items = outerGlowLabels,
                    selectedIndex = modes.indexOf(draft.outerGlow).coerceAtLeast(0),
                    title = stringResource(R.string.compose_outer_glow),
                    onSelectedIndexChange = { index -> updateSettings(draft.copy(outerGlow = modes[index])) },
                    enabled = draft.enabled,
                    insideMargin = MEDIA_ITEM_MARGIN,
                    renderInRootScaffold = false,
                )
                AnimatedVisibility(visible = draft.enabled && draft.outerGlow == TRI_STATE_ON) {
                    ArrowPreference(
                        title = stringResource(R.string.compose_out_effect_color),
                        summary = draft.outEffectColor.takeIf(String::isNotEmpty),
                        insideMargin = MEDIA_ITEM_MARGIN,
                        onClick = {
                            selectedColor = parseHexColor(draft.outEffectColor) ?: Color.Red
                            activeColorTarget = ColorTarget.Focus
                        },
                    )
                }
            }
        }
        item {
            SectionTitle(
                stringResource(R.string.compose_glow_section, stringResource(R.string.compose_enable_island)),
            )
            Card {
                OverlayDropdownPreference(
                    items = islandOuterGlowLabels,
                    selectedIndex = modes.indexOf(draft.islandOuterGlow).coerceAtLeast(0),
                    title = stringResource(R.string.compose_outer_glow),
                    onSelectedIndexChange = { index ->
                        updateSettings(draft.copy(islandOuterGlow = modes[index]))
                    },
                    enabled = draft.enabled,
                    insideMargin = MEDIA_ITEM_MARGIN,
                    renderInRootScaffold = false,
                )
                AnimatedVisibility(visible = draft.enabled && draft.islandOuterGlow == TRI_STATE_ON) {
                    ArrowPreference(
                        title = stringResource(R.string.compose_out_effect_color),
                        summary = draft.islandOuterGlowColor.takeIf(String::isNotEmpty),
                        insideMargin = MEDIA_ITEM_MARGIN,
                        onClick = {
                            selectedColor = parseHexColor(draft.islandOuterGlowColor) ?: Color.Red
                            activeColorTarget = ColorTarget.Island
                        },
                    )
                }
            }
        }
    }

    WindowDialog(
        show = activeColorTarget != null,
        title = stringResource(R.string.compose_out_effect_color),
        onDismissRequest = { activeColorTarget = null },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ColorPalette(
                color = selectedColor,
                onColorChanged = { newColor -> selectedColor = newColor },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.compose_cancel),
                    onClick = { activeColorTarget = null },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        when (activeColorTarget) {
                            ColorTarget.Focus -> updateSettings(
                                draft.copy(outEffectColor = selectedColor.toArgbHex()),
                            )
                            ColorTarget.Island -> updateSettings(
                                draft.copy(islandOuterGlowColor = selectedColor.toArgbHex()),
                            )
                            null -> Unit
                        }
                        activeColorTarget = null
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.compose_save))
                }
            }
        }
    }
}

@Composable
private fun glowModeLabels(defaultMode: String): List<String> = listOf(
    stringResource(
        if (defaultMode == TRI_STATE_ON) R.string.compose_default_on else R.string.compose_default_off,
    ),
    stringResource(R.string.compose_enabled_option),
    stringResource(R.string.compose_disabled_option),
    stringResource(R.string.compose_follow_dynamic_color),
)

private fun parseHexColor(value: String): Color? = runCatching {
    if (!HEX_COLOR.matches(value)) return@runCatching null
    Color(AndroidColor.parseColor(value))
}.getOrNull()

private fun Color.toArgbHex(): String = "#%08X".format(toArgb())

private val MEDIA_ITEM_MARGIN = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
private val HEX_COLOR = Regex("^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
private const val PREF_DEFAULT_OUTER_GLOW = "pref_default_outer_glow"
private const val PREF_DEFAULT_ISLAND_OUTER_GLOW = "pref_default_island_outer_glow"
private const val TRI_STATE_DEFAULT = "default"
private const val TRI_STATE_ON = "on"
private const val TRI_STATE_OFF = "off"
private const val TRI_STATE_FOLLOW_DYNAMIC = "follow_dynamic"

private enum class ColorTarget { Focus, Island }
