package io.github.hyperisland.compose.page.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.ColorPaletteDialog
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.parseHexColor
import io.github.hyperisland.compose.component.toArgbHex
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.InstalledApp
import io.github.hyperisland.compose.data.MediaNotificationSettings
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

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
        title = stringResource(R.string.media_notification),
        onBack = onBack,
        actionIcon = MiuixIcons.Refresh,
        actionDescription = stringResource(R.string.restore_default),
        onAction = { updateSettings(MediaNotificationSettings()) },
    ) {
        item {
            SectionTitle(stringResource(R.string.media_notification))
            Card {
                SwitchPreference(
                    checked = draft.enabled,
                    onCheckedChange = { updateSettings(draft.copy(enabled = it)) },
                    title = stringResource(R.string.media_notification),
                    summary = stringResource(R.string.media_notification_disabled_summary),
                    insideMargin = MEDIA_ITEM_MARGIN,
                )
                SwitchPreference(
                    checked = draft.normalNotification,
                    onCheckedChange = { updateSettings(draft.copy(normalNotification = it)) },
                    title = stringResource(R.string.normal_notification),
                    summary = stringResource(R.string.normal_notification_summary),
                    enabled = draft.enabled,
                    insideMargin = MEDIA_ITEM_MARGIN,
                )
            }
        }
        item {
            SectionTitle(
                stringResource(R.string.glow_section, stringResource(R.string.focus_notification)),
            )
            Card {
                OverlayDropdownPreference(
                    items = outerGlowLabels,
                    selectedIndex = modes.indexOf(draft.outerGlow).coerceAtLeast(0),
                    title = stringResource(R.string.outer_glow),
                    onSelectedIndexChange = { index -> updateSettings(draft.copy(outerGlow = modes[index])) },
                    enabled = draft.enabled,
                    insideMargin = MEDIA_ITEM_MARGIN,
                    renderInRootScaffold = false,
                )
                AnimatedVisibility(visible = draft.enabled && draft.outerGlow == TRI_STATE_ON) {
                    ArrowPreference(
                        title = stringResource(R.string.out_effect_color),
                        summary = draft.outEffectColor.takeIf(String::isNotEmpty),
                        insideMargin = MEDIA_ITEM_MARGIN,
                        onClick = {
                            selectedColor = parseHexColor(draft.outEffectColor)
                            activeColorTarget = ColorTarget.Focus
                        },
                    )
                }
            }
        }
        item {
            SectionTitle(
                stringResource(R.string.glow_section, stringResource(R.string.enable_island)),
            )
            Card {
                OverlayDropdownPreference(
                    items = islandOuterGlowLabels,
                    selectedIndex = modes.indexOf(draft.islandOuterGlow).coerceAtLeast(0),
                    title = stringResource(R.string.outer_glow),
                    onSelectedIndexChange = { index ->
                        updateSettings(draft.copy(islandOuterGlow = modes[index]))
                    },
                    enabled = draft.enabled,
                    insideMargin = MEDIA_ITEM_MARGIN,
                    renderInRootScaffold = false,
                )
                AnimatedVisibility(visible = draft.enabled && draft.islandOuterGlow == TRI_STATE_ON) {
                    ArrowPreference(
                        title = stringResource(R.string.out_effect_color),
                        summary = draft.islandOuterGlowColor.takeIf(String::isNotEmpty),
                        insideMargin = MEDIA_ITEM_MARGIN,
                        onClick = {
                            selectedColor = parseHexColor(draft.islandOuterGlowColor)
                            activeColorTarget = ColorTarget.Island
                        },
                    )
                }
            }
        }
    }

    ColorPaletteDialog(
        show = activeColorTarget != null,
        title = stringResource(R.string.out_effect_color),
        initialColor = selectedColor,
        onDismiss = { activeColorTarget = null },
        onDelete = {
            when (activeColorTarget) {
                ColorTarget.Focus -> updateSettings(draft.copy(outEffectColor = ""))
                ColorTarget.Island -> updateSettings(draft.copy(islandOuterGlowColor = ""))
                null -> Unit
            }
            activeColorTarget = null
        },
        onSave = { color ->
            when (activeColorTarget) {
                ColorTarget.Focus -> updateSettings(draft.copy(outEffectColor = color.toArgbHex()))
                ColorTarget.Island -> updateSettings(draft.copy(islandOuterGlowColor = color.toArgbHex()))
                null -> Unit
            }
            activeColorTarget = null
        },
    )
}

@Composable
private fun glowModeLabels(defaultMode: String): List<String> = listOf(
    stringResource(
        if (defaultMode == TRI_STATE_ON) R.string.default_on else R.string.default_off,
    ),
    stringResource(R.string.enabled_option),
    stringResource(R.string.disabled_option),
    stringResource(R.string.follow_dynamic_color),
)

private val MEDIA_ITEM_MARGIN = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
private const val PREF_DEFAULT_OUTER_GLOW = "pref_default_outer_glow"
private const val PREF_DEFAULT_ISLAND_OUTER_GLOW = "pref_default_island_outer_glow"
private const val TRI_STATE_DEFAULT = "default"
private const val TRI_STATE_ON = "on"
private const val TRI_STATE_OFF = "off"
private const val TRI_STATE_FOLLOW_DYNAMIC = "follow_dynamic"

private enum class ColorTarget { Focus, Island }
