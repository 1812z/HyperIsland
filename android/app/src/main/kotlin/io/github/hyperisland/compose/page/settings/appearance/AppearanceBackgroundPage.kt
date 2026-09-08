package io.github.hyperisland.compose.page.settings.appearance

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.BackgroundPickerDialog
import io.github.hyperisland.compose.component.GlassSamplingDialog
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsAction
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.data.rememberLongPreference
import io.github.hyperisland.compose.data.rememberStringPreference
import io.github.hyperisland.compose.service.IslandBackgroundService
import io.github.hyperisland.compose.service.IslandBackgroundType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight

@Composable
internal fun AppearanceBackgroundPage(
    prefs: FlutterPrefsRepository,
    onOpenMaterial: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    var backgroundDialog by remember { mutableStateOf<IslandBackgroundType?>(null) }
    var selectedBackgroundUri by remember { mutableStateOf<Uri?>(null) }
    var samplingDialog by remember { mutableStateOf(false) }
    val smallBackground = rememberStringPreference(prefs, KEY_BG_SMALL, "")
    val bigBackground = rememberStringPreference(prefs, KEY_BG_BIG, "")
    val expandBackground = rememberStringPreference(prefs, KEY_BG_EXPAND, "")
    val bigMaterial = rememberStringPreference(prefs, KEY_MATERIAL_BIG, "")
    val smallMaterial = rememberStringPreference(prefs, KEY_MATERIAL_SMALL, "")
    val expandMaterial = rememberStringPreference(prefs, KEY_MATERIAL_EXPAND, "")
    val smallFollowBig = rememberBooleanPreference(prefs, KEY_MATERIAL_SMALL_FOLLOW, true)
    val expandFollowBig = rememberBooleanPreference(prefs, KEY_MATERIAL_EXPAND_FOLLOW, true)
    val legacyBlurSmall = rememberBooleanPreference(prefs, KEY_BLUR_SMALL, false)
    val legacyBlurBig = rememberBooleanPreference(prefs, KEY_BLUR_BIG, false)
    val legacyBlurExpand = rememberBooleanPreference(prefs, KEY_BLUR_EXPAND, false)
    val legacyGlassSmall = rememberBooleanPreference(prefs, KEY_GLASS_SMALL, false)
    val legacyGlassBig = rememberBooleanPreference(prefs, KEY_GLASS_BIG, false)
    val legacyGlassExpand = rememberBooleanPreference(prefs, KEY_GLASS_EXPAND, false)
    val legacyLiquidSmall = rememberBooleanPreference(prefs, KEY_LIQUID_SMALL, false)
    val legacyLiquidBig = rememberBooleanPreference(prefs, KEY_LIQUID_BIG, false)
    val legacyLiquidExpand = rememberBooleanPreference(prefs, KEY_LIQUID_EXPAND, false)
    val resolvedSmallMaterial = if (smallFollowBig.value) bigMaterial.value else smallMaterial.value
    val resolvedExpandMaterial = if (expandFollowBig.value) bigMaterial.value else expandMaterial.value
    val hasGlass = listOf(bigMaterial.value, resolvedSmallMaterial, resolvedExpandMaterial)
        .any(::usesGlass) || legacyGlassSmall.value || legacyGlassBig.value || legacyGlassExpand.value
    val hasLiquidGlass = listOf(bigMaterial.value, resolvedSmallMaterial, resolvedExpandMaterial)
        .any(::usesLiquidGlass) || legacyLiquidSmall.value || legacyLiquidBig.value || legacyLiquidExpand.value
    val gyroscope = rememberBooleanPreference(prefs, KEY_GLASS_GYROSCOPE, true)
    val hdrHighlight = rememberBooleanPreference(prefs, KEY_GLASS_HDR, false)
    val captureFps = rememberLongPreference(prefs, KEY_CAPTURE_FPS, 20)
    val captureQuality = rememberLongPreference(prefs, KEY_CAPTURE_QUALITY, 30)
    val chooseBackground = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) selectedBackgroundUri = uri
    }

    fun pathFor(type: IslandBackgroundType): String = when (type) {
        IslandBackgroundType.Small -> smallBackground.value
        IslandBackgroundType.Big -> bigBackground.value
        IslandBackgroundType.Expand -> expandBackground.value
    }

    fun setPath(type: IslandBackgroundType, path: String) {
        when (type) {
            IslandBackgroundType.Small -> smallBackground.value = path
            IslandBackgroundType.Big -> bigBackground.value = path
            IslandBackgroundType.Expand -> expandBackground.value = path
        }
        if (path.isBlank()) prefs.remove(type.preferenceKey) else prefs.putString(type.preferenceKey, path)
    }

    val savedMessage = stringResource(R.string.background_saved)
    val deletedMessage = stringResource(R.string.background_deleted)
    val failedMessage = stringResource(R.string.background_operation_failed)

    AppearanceDetailPage(
        title = stringResource(R.string.appearance_background),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarState) },
    ) {
        item {
            SectionTitle(stringResource(R.string.island_background))
            Card(modifier = Modifier.fillMaxWidth()) {
                BackgroundRow(stringResource(R.string.small_background), smallBackground.value,
                    !usesCustomMaterial(resolvedSmallMaterial) && !legacyBlurSmall.value) {
                    backgroundDialog = IslandBackgroundType.Small; selectedBackgroundUri = null
                }
                BackgroundRow(stringResource(R.string.big_background), bigBackground.value,
                    !usesCustomMaterial(bigMaterial.value) && !legacyBlurBig.value) {
                    backgroundDialog = IslandBackgroundType.Big; selectedBackgroundUri = null
                }
                BackgroundRow(stringResource(R.string.expand_background), expandBackground.value,
                    !usesCustomMaterial(resolvedExpandMaterial) && !legacyBlurExpand.value) {
                    backgroundDialog = IslandBackgroundType.Expand; selectedBackgroundUri = null
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.glass_effect))
            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsAction(
                    title = stringResource(R.string.material_customize),
                    summary = stringResource(R.string.material_customize_summary),
                    endIcon = MiuixIcons.Basic.ArrowRight,
                    onClick = onOpenMaterial,
                )
                PreferenceSwitch(stringResource(R.string.glass_gyroscope), stringResource(R.string.glass_gyroscope_summary), null,
                    gyroscope.value, enabled = hasGlass) { gyroscope.value = it; prefs.putBoolean(KEY_GLASS_GYROSCOPE, it) }
                PreferenceSwitch(stringResource(R.string.glass_hdr), stringResource(R.string.glass_hdr_summary), null,
                    hdrHighlight.value, enabled = hasGlass) { hdrHighlight.value = it; prefs.putBoolean(KEY_GLASS_HDR, it) }
                SettingsAction(
                    title = stringResource(R.string.glass_sampling_settings),
                    summary = stringResource(if (hasLiquidGlass) R.string.glass_sampling_summary else R.string.glass_enable_liquid_first),
                    endIcon = if (hasLiquidGlass) MiuixIcons.Basic.ArrowRight else null,
                    onClick = { if (hasLiquidGlass) samplingDialog = true },
                )
            }
        }
    }

    backgroundDialog?.let { activeBackground ->
        BackgroundPickerDialog(
            show = true,
            title = backgroundTitle(activeBackground),
            currentPath = pathFor(activeBackground),
            selectedUri = selectedBackgroundUri,
            onChoose = { chooseBackground.launch(arrayOf("image/*")) },
            onDelete = {
                scope.launch {
                    val deleted = withContext(Dispatchers.IO) {
                        IslandBackgroundService.delete(activeBackground, pathFor(activeBackground))
                    }
                    if (deleted) { setPath(activeBackground, ""); backgroundDialog = null; selectedBackgroundUri = null }
                    snackbarState.showSnackbar(if (deleted) deletedMessage else failedMessage)
                }
            },
            onDismiss = { backgroundDialog = null; selectedBackgroundUri = null },
            onSave = {
                val uri = selectedBackgroundUri ?: return@BackgroundPickerDialog
                scope.launch {
                    val result = withContext(Dispatchers.IO) { IslandBackgroundService.save(context, uri, activeBackground) }
                    result.onSuccess { setPath(activeBackground, it); backgroundDialog = null; selectedBackgroundUri = null }
                    snackbarState.showSnackbar(if (result.isSuccess) savedMessage else failedMessage)
                }
            },
        )
    }
    GlassSamplingDialog(
        show = samplingDialog,
        initialFps = captureFps.value.toInt(),
        initialQuality = captureQuality.value.toInt(),
        onDismiss = { samplingDialog = false },
    ) { fps, quality ->
        captureFps.value = fps.toLong(); captureQuality.value = quality.toLong()
        prefs.putLong(KEY_CAPTURE_FPS, fps.toLong()); prefs.putLong(KEY_CAPTURE_QUALITY, quality.toLong())
        samplingDialog = false
    }
}

@Composable
private fun BackgroundRow(title: String, path: String, enabled: Boolean, onClick: () -> Unit) {
    SettingsAction(
        title = title,
        summary = stringResource(when { !enabled -> R.string.background_material_conflict; path.isBlank() -> R.string.not_set; else -> R.string.selected }),
        endIcon = if (enabled) MiuixIcons.Basic.ArrowRight else null,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun backgroundTitle(type: IslandBackgroundType): String = stringResource(
    when (type) {
        IslandBackgroundType.Small -> R.string.small_background
        IslandBackgroundType.Big -> R.string.big_background
        IslandBackgroundType.Expand -> R.string.expand_background
    },
)
