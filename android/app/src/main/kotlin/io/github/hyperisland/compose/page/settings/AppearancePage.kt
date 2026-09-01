package io.github.hyperisland.compose.page.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.BackgroundPickerDialog
import io.github.hyperisland.compose.component.ColorPaletteDialog
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.GlassSamplingDialog
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.PreferenceSlider
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsAction
import io.github.hyperisland.compose.component.parseHexColor
import io.github.hyperisland.compose.component.toArgbHex
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.data.rememberLongPreference
import io.github.hyperisland.compose.data.rememberStringPreference
import io.github.hyperisland.compose.service.IslandBackgroundService
import io.github.hyperisland.compose.service.IslandBackgroundType
import io.github.hyperisland.compose.service.TestNotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward

@Composable
internal fun AppearancePage(
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
    var colorDialog by remember { mutableStateOf(false) }

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
    val hasCustomMaterial = listOf(bigMaterial.value, resolvedSmallMaterial, resolvedExpandMaterial)
        .any(::usesCustomMaterial) || legacyBlurSmall.value || legacyBlurBig.value || legacyBlurExpand.value
    val gyroscope = rememberBooleanPreference(prefs, KEY_GLASS_GYROSCOPE, true)
    val hdrHighlight = rememberBooleanPreference(prefs, KEY_GLASS_HDR, false)
    val captureFps = rememberLongPreference(prefs, KEY_CAPTURE_FPS, 20)
    val captureQuality = rememberLongPreference(prefs, KEY_CAPTURE_QUALITY, 30)
    val roundIcon = rememberBooleanPreference(prefs, KEY_ROUND_ICON, true)
    val alwaysIslandOutline = rememberBooleanPreference(prefs, KEY_ALWAYS_ISLAND_OUTLINE, false)
    val alwaysFocusOutline = rememberBooleanPreference(prefs, KEY_ALWAYS_FOCUS_OUTLINE, false)
    val glowSingleColor = rememberBooleanPreference(prefs, KEY_GLOW_SINGLE_COLOR, false)
    val glowBaseColor = rememberStringPreference(prefs, KEY_GLOW_BASE_COLOR, "")

    val chooseBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
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
    fun showMessage(message: String) {
        scope.launch { snackbarState.showSnackbar(message) }
    }

    DetailPage(
        title = stringResource(R.string.appearance),
        onBack = onBack,
        actionIcon = ImageVector.vectorResource(R.drawable.ic_test_notification),
        actionDescription = stringResource(R.string.send_test_notification),
        onAction = { TestNotificationService.sendDefault(context) },
        snackbarHost = { SnackbarHost(snackbarState) },
    ) {
        item {
            SectionTitle(stringResource(R.string.island_dimensions))
            Card(modifier = Modifier.fillMaxWidth()) {
                DoublePreferenceSlider(prefs, KEY_ISLAND_HEIGHT, R.string.island_height, 0.0, 100.0, 0.0)
                DoublePreferenceSlider(prefs, KEY_ISLAND_TOP_OFFSET, R.string.vertical_position, -40.0, 50.0, 0.0)
                LongPreferenceSlider(prefs, KEY_BIG_MAX_WIDTH, R.string.big_max_width, 0, 500, 0, 5)
                LongPreferenceSlider(prefs, KEY_BIG_MIN_WIDTH, R.string.big_min_width, 0, 500, 0, 5)
                LongPreferenceSlider(
                    prefs,
                    KEY_SMALL_WIDTH,
                    R.string.small_island_width,
                    1,
                    100,
                    34,
                    followSystemAtDefault = true,
                )
                LongPreferenceSlider(prefs, KEY_SMALL_OFFSET, R.string.small_island_offset, -10, 50, 0)
            }
        }
        item {
            SectionTitle(stringResource(R.string.island_background))
            Card(modifier = Modifier.fillMaxWidth()) {
                BackgroundRow(
                    title = stringResource(R.string.small_background),
                    path = smallBackground.value,
                    enabled = !usesCustomMaterial(resolvedSmallMaterial) && !legacyBlurSmall.value,
                ) { backgroundDialog = IslandBackgroundType.Small; selectedBackgroundUri = null }
                BackgroundRow(
                    title = stringResource(R.string.big_background),
                    path = bigBackground.value,
                    enabled = !usesCustomMaterial(bigMaterial.value) && !legacyBlurBig.value,
                ) { backgroundDialog = IslandBackgroundType.Big; selectedBackgroundUri = null }
                BackgroundRow(
                    title = stringResource(R.string.expand_background),
                    path = expandBackground.value,
                    enabled = !usesCustomMaterial(resolvedExpandMaterial) && !legacyBlurExpand.value,
                ) { backgroundDialog = IslandBackgroundType.Expand; selectedBackgroundUri = null }
            }
        }
        item {
            SectionTitle(stringResource(R.string.glass_effect))
            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsAction(
                    title = stringResource(R.string.material_customize),
                    summary = stringResource(R.string.material_customize_summary),
                    endIcon = MiuixIcons.ChevronForward,
                ) { onOpenMaterial() }
                PreferenceSwitch(
                    title = stringResource(R.string.glass_gyroscope),
                    summary = stringResource(R.string.glass_gyroscope_summary),
                    icon = null,
                    checked = gyroscope.value,
                    enabled = hasGlass,
                ) { gyroscope.value = it; prefs.putBoolean(KEY_GLASS_GYROSCOPE, it) }
                PreferenceSwitch(
                    title = stringResource(R.string.glass_hdr),
                    summary = stringResource(R.string.glass_hdr_summary),
                    icon = null,
                    checked = hdrHighlight.value,
                    enabled = hasGlass,
                ) { hdrHighlight.value = it; prefs.putBoolean(KEY_GLASS_HDR, it) }
                SettingsAction(
                    title = stringResource(R.string.glass_sampling_settings),
                    summary = stringResource(
                        if (hasLiquidGlass) R.string.glass_sampling_summary
                        else R.string.glass_enable_liquid_first,
                    ),
                    endIcon = if (hasLiquidGlass) MiuixIcons.ChevronForward else null,
                    onClick = { if (hasLiquidGlass) samplingDialog = true },
                )
            }
        }
        item {
            SectionTitle(stringResource(R.string.island_text))
            Card(modifier = Modifier.fillMaxWidth()) {
                LongPreferenceSlider(prefs, KEY_TEXT_SCALE, R.string.island_text_size, 10, 200, 100, unit = SliderUnit.Percent)
                TextColorPreference(prefs, KEY_TEXT_COLOR, R.string.island_text_color, includeBackground = true)
                TextColorPreference(prefs, KEY_FOCUS_TEXT_COLOR, R.string.focus_text_color, includeBackground = false)
                TextColorPreference(prefs, KEY_MEDIA_TEXT_COLOR, R.string.media_text_color, includeBackground = false)
            }
        }
        item {
            SectionTitle(stringResource(R.string.icon))
            Card(modifier = Modifier.fillMaxWidth()) {
                LongPreferenceSlider(prefs, KEY_ICON_SIZE, R.string.icon_size, 50, 150, 100, unit = SliderUnit.Percent)
                LongPreferenceSlider(prefs, KEY_ROUND_RADIUS, R.string.round_icon_radius, 0, 100, 40, unit = SliderUnit.Percent)
                PreferenceSwitch(
                    title = stringResource(R.string.round_icon),
                    summary = stringResource(R.string.round_icon_summary),
                    icon = null,
                    checked = roundIcon.value,
                ) { roundIcon.value = it; if (it) prefs.remove(KEY_ROUND_ICON) else prefs.putBoolean(KEY_ROUND_ICON, false) }
                DoublePreferenceSlider(prefs, KEY_ICON_PADDING, R.string.icon_padding, 0.0, 10.0, 8.0, decimals = 1)
            }
        }
        item {
            SectionTitle(stringResource(R.string.outline_control))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.always_island_outline),
                    summary = null,
                    icon = null,
                    checked = alwaysIslandOutline.value,
                    enabled = !hasCustomMaterial && listOf(smallBackground.value, bigBackground.value, expandBackground.value).all(String::isBlank),
                ) { alwaysIslandOutline.value = it; prefs.putBoolean(KEY_ALWAYS_ISLAND_OUTLINE, it) }
                PreferenceSwitch(
                    title = stringResource(R.string.always_focus_outline),
                    summary = null,
                    icon = null,
                    checked = alwaysFocusOutline.value,
                    enabled = !hasCustomMaterial && listOf(smallBackground.value, bigBackground.value, expandBackground.value).all(String::isBlank),
                ) { alwaysFocusOutline.value = it; prefs.putBoolean(KEY_ALWAYS_FOCUS_OUTLINE, it) }
            }
        }
        item {
            SectionTitle(stringResource(R.string.outer_glow))
            Card(modifier = Modifier.fillMaxWidth()) {
                LongPreferenceSlider(prefs, KEY_GLOW_RANGE, R.string.glow_range, 0, 100, 0, unit = SliderUnit.Percent)
                PreferenceSwitch(
                    title = stringResource(R.string.single_color_glow),
                    summary = null,
                    icon = null,
                    checked = glowSingleColor.value,
                ) { glowSingleColor.value = it; prefs.putBoolean(KEY_GLOW_SINGLE_COLOR, it) }
                AnimatedVisibility(visible = !glowSingleColor.value) {
                    SettingsAction(
                        title = stringResource(R.string.glow_base_color),
                        summary = glowBaseColor.value.ifBlank { stringResource(R.string.default_option) },
                        endIcon = MiuixIcons.ChevronForward,
                    ) { colorDialog = true }
                }
            }
        }
    }

    val activeBackground = backgroundDialog
    if (activeBackground != null) {
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
                    if (deleted) {
                        setPath(activeBackground, "")
                        backgroundDialog = null
                        selectedBackgroundUri = null
                    }
                    showMessage(if (deleted) deletedMessage else failedMessage)
                }
            },
            onDismiss = { backgroundDialog = null; selectedBackgroundUri = null },
            onSave = {
                val uri = selectedBackgroundUri ?: return@BackgroundPickerDialog
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        IslandBackgroundService.save(context, uri, activeBackground)
                    }
                    result.onSuccess {
                        setPath(activeBackground, it)
                        backgroundDialog = null
                        selectedBackgroundUri = null
                    }
                    showMessage(if (result.isSuccess) savedMessage else failedMessage)
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
        captureFps.value = fps.toLong()
        captureQuality.value = quality.toLong()
        prefs.putLong(KEY_CAPTURE_FPS, fps.toLong())
        prefs.putLong(KEY_CAPTURE_QUALITY, quality.toLong())
        samplingDialog = false
    }
    ColorPaletteDialog(
        show = colorDialog,
        title = stringResource(R.string.glow_base_color),
        initialColor = parseHexColor(glowBaseColor.value, Color(0xFF0096FF)),
        onDismiss = { colorDialog = false },
        onDelete = {
            glowBaseColor.value = ""
            prefs.remove(KEY_GLOW_BASE_COLOR)
            colorDialog = false
        },
    ) { color ->
        val value = color.toArgbHex()
        glowBaseColor.value = value
        prefs.putString(KEY_GLOW_BASE_COLOR, value)
        colorDialog = false
    }
}

@Composable
private fun BackgroundRow(title: String, path: String, enabled: Boolean, onClick: () -> Unit) {
    SettingsAction(
        title = title,
        summary = stringResource(
            when {
                !enabled -> R.string.background_material_conflict
                path.isBlank() -> R.string.not_set
                else -> R.string.selected
            },
        ),
        endIcon = if (enabled) MiuixIcons.ChevronForward else null,
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

@Composable
private fun LongPreferenceSlider(
    prefs: FlutterPrefsRepository,
    key: String,
    titleRes: Int,
    minimum: Long,
    maximum: Long,
    default: Long,
    increment: Long = 1,
    unit: SliderUnit = SliderUnit.Dp,
    followSystemAtDefault: Boolean = false,
) {
    val state = rememberLongPreference(prefs, key, default)
    var draft by remember(key) { mutableFloatStateOf(state.value.toFloat()) }
    val display = if (
        draft.toLong() == default && (default == 0L || followSystemAtDefault)
    ) stringResource(R.string.follow_system)
    else when (unit) {
        SliderUnit.Dp -> stringResource(R.string.dp_value, draft.toInt())
        SliderUnit.Percent -> stringResource(R.string.percent_value, draft.toInt())
    }
    PreferenceSlider(
        title = stringResource(titleRes),
        icon = null,
        value = draft,
        valueText = display,
        valueRange = minimum.toFloat()..maximum.toFloat(),
        steps = (((maximum - minimum) / increment) - 1).coerceAtLeast(0).toInt(),
        resetVisible = draft.toLong() != default,
        onReset = {
            draft = default.toFloat()
            state.value = default
            prefs.remove(key)
        },
        onValueChange = { draft = ((it / increment).toInt() * increment).toFloat() },
        onValueChangeFinished = {
            state.value = draft.toLong()
            if (state.value == default) prefs.remove(key) else prefs.putLong(key, state.value)
        },
    )
}

private enum class SliderUnit { Dp, Percent }

@Composable
private fun DoublePreferenceSlider(
    prefs: FlutterPrefsRepository,
    key: String,
    titleRes: Int,
    minimum: Double,
    maximum: Double,
    default: Double,
    decimals: Int = 0,
) {
    var stored by remember(key) { mutableStateOf(prefs.getDouble(key, default)) }
    var draft by remember(key) { mutableFloatStateOf(stored.toFloat()) }
    val valueText = if (draft == 0f && default == 0.0) stringResource(R.string.follow_system)
    else if (decimals == 1) stringResource(R.string.dp_decimal_value, draft)
    else stringResource(R.string.dp_value, draft.toInt())
    PreferenceSlider(
        title = stringResource(titleRes),
        icon = null,
        value = draft,
        valueText = valueText,
        valueRange = minimum.toFloat()..maximum.toFloat(),
        steps = ((maximum - minimum) * if (decimals == 1) 10 else 1).toInt() - 1,
        resetVisible = draft.toDouble() != default,
        onReset = {
            draft = default.toFloat()
            stored = default
            prefs.remove(key)
        },
        onValueChange = { draft = if (decimals == 1) (it * 10).toInt() / 10f else it.toInt().toFloat() },
        onValueChangeFinished = {
            stored = draft.toDouble()
            if (stored == default) prefs.remove(key) else prefs.putDouble(key, stored)
        },
    )
}

@Composable
private fun TextColorPreference(
    prefs: FlutterPrefsRepository,
    key: String,
    titleRes: Int,
    includeBackground: Boolean,
) {
    val values = buildList {
        add("default")
        add("black")
        if (includeBackground) {
            add("follow_background")
            add("invert_background")
        }
        add("follow_status_bar")
        add("invert_status_bar")
    }
    val labels = buildList {
        add(stringResource(R.string.default_option))
        add(stringResource(R.string.black))
        if (includeBackground) {
            add(stringResource(R.string.follow_background))
            add(stringResource(R.string.invert_background))
        }
        add(stringResource(R.string.follow_status_bar))
        add(stringResource(R.string.invert_status_bar))
    }
    val state = rememberStringPreference(prefs, key, "default")
    PreferenceDropdown(
        title = stringResource(titleRes),
        summary = null,
        icon = null,
        items = labels,
        selectedIndex = values.indexOf(state.value).coerceAtLeast(0),
    ) { index ->
        val value = values[index]
        state.value = value
        if (value == "default") prefs.remove(key) else prefs.putString(key, value)
    }
}

private fun materialType(raw: String): String = runCatching {
    JSONObject(raw).optString("type", "default")
}.getOrDefault("default")

private fun usesCustomMaterial(raw: String): Boolean = materialType(raw) != "default"
private fun usesLiquidGlass(raw: String): Boolean = materialType(raw) == "liquid_glass"
private fun usesGlass(raw: String): Boolean = materialType(raw) in setOf("highlight_glass", "liquid_glass", "soft_glass")

private const val KEY_BG_SMALL = "pref_island_bg_small_path"
private const val KEY_BG_BIG = "pref_island_bg_big_path"
private const val KEY_BG_EXPAND = "pref_island_bg_expand_path"
private const val KEY_MATERIAL_BIG = "pref_island_material_big_config"
private const val KEY_MATERIAL_SMALL = "pref_island_material_small_config"
private const val KEY_MATERIAL_EXPAND = "pref_island_material_expand_config"
private const val KEY_MATERIAL_SMALL_FOLLOW = "pref_island_material_small_follow_big"
private const val KEY_MATERIAL_EXPAND_FOLLOW = "pref_island_material_expand_follow_big"
private const val KEY_BLUR_SMALL = "pref_island_blur_small_enabled"
private const val KEY_BLUR_BIG = "pref_island_blur_big_enabled"
private const val KEY_BLUR_EXPAND = "pref_island_blur_expand_enabled"
private const val KEY_GLASS_SMALL = "pref_island_glass_small_enabled"
private const val KEY_GLASS_BIG = "pref_island_glass_big_enabled"
private const val KEY_GLASS_EXPAND = "pref_island_glass_expand_enabled"
private const val KEY_LIQUID_SMALL = "pref_island_refraction_small_enabled"
private const val KEY_LIQUID_BIG = "pref_island_refraction_big_enabled"
private const val KEY_LIQUID_EXPAND = "pref_island_refraction_expand_enabled"
private const val KEY_GLASS_GYROSCOPE = "pref_island_glass_gyroscope"
private const val KEY_GLASS_HDR = "pref_island_glass_hdr_highlight"
private const val KEY_CAPTURE_FPS = "pref_island_glass_capture_fps"
private const val KEY_CAPTURE_QUALITY = "pref_island_glass_capture_quality"
private const val KEY_ISLAND_HEIGHT = "pref_island_height"
private const val KEY_ISLAND_TOP_OFFSET = "pref_island_top_offset"
private const val KEY_BIG_MAX_WIDTH = "pref_big_island_max_width"
private const val KEY_BIG_MIN_WIDTH = "pref_big_island_min_width"
private const val KEY_SMALL_WIDTH = "pref_small_island_width"
private const val KEY_SMALL_OFFSET = "pref_small_island_horizontal_offset"
private const val KEY_TEXT_SCALE = "pref_island_text_scale"
private const val KEY_TEXT_COLOR = "pref_island_text_color_mode"
private const val KEY_FOCUS_TEXT_COLOR = "pref_focus_notification_text_color_mode"
private const val KEY_MEDIA_TEXT_COLOR = "pref_media_notification_text_color_mode"
private const val KEY_ICON_SIZE = "pref_island_icon_size"
private const val KEY_ROUND_RADIUS = "pref_round_icon_radius"
private const val KEY_ROUND_ICON = "pref_round_icon"
private const val KEY_ICON_PADDING = "pref_island_icon_padding"
private const val KEY_ALWAYS_ISLAND_OUTLINE = "pref_always_show_island_outline"
private const val KEY_ALWAYS_FOCUS_OUTLINE = "pref_always_show_focus_outline"
private const val KEY_GLOW_RANGE = "pref_outer_glow_range"
private const val KEY_GLOW_SINGLE_COLOR = "pref_outer_glow_single_color"
private const val KEY_GLOW_BASE_COLOR = "pref_outer_glow_base_color"
