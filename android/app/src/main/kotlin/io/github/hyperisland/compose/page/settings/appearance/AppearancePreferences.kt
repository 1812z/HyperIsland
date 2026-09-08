package io.github.hyperisland.compose.page.settings.appearance

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.PreferenceSlider
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberLongPreference
import io.github.hyperisland.compose.data.rememberStringPreference
import io.github.hyperisland.compose.service.TestNotificationService
import org.json.JSONObject

@Composable
internal fun AppearanceDetailPage(
    title: String,
    onBack: () -> Unit,
    snackbarHost: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val context = LocalContext.current
    DetailPage(
        title = title,
        onBack = onBack,
        actionIcon = ImageVector.vectorResource(R.drawable.ic_test_notification),
        actionDescription = stringResource(R.string.send_test_notification),
        onAction = { TestNotificationService.sendDefault(context) },
        snackbarHost = snackbarHost,
        content = content,
    )
}

@Composable
internal fun LongPreferenceSlider(
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
    val display = if (draft.toLong() == default && (default == 0L || followSystemAtDefault)) {
        stringResource(R.string.follow_system)
    } else when (unit) {
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
        onReset = { draft = default.toFloat(); state.value = default; prefs.remove(key) },
        onValueChange = { draft = ((it / increment).toInt() * increment).toFloat() },
        onValueChangeFinished = {
            state.value = draft.toLong()
            if (state.value == default) prefs.remove(key) else prefs.putLong(key, state.value)
        },
    )
}

internal enum class SliderUnit { Dp, Percent }

@Composable
internal fun DoublePreferenceSlider(
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
        onReset = { draft = default.toFloat(); stored = default; prefs.remove(key) },
        onValueChange = { draft = if (decimals == 1) (it * 10).toInt() / 10f else it.toInt().toFloat() },
        onValueChangeFinished = {
            stored = draft.toDouble()
            if (stored == default) prefs.remove(key) else prefs.putDouble(key, stored)
        },
    )
}

@Composable
internal fun TextColorPreference(
    prefs: FlutterPrefsRepository,
    key: String,
    titleRes: Int,
    includeBackground: Boolean,
) {
    val values = buildList {
        add("default"); add("black")
        if (includeBackground) { add("follow_background"); add("invert_background") }
        add("follow_status_bar"); add("invert_status_bar")
    }
    val labels = buildList {
        add(stringResource(R.string.default_option)); add(stringResource(R.string.black))
        if (includeBackground) { add(stringResource(R.string.follow_background)); add(stringResource(R.string.invert_background)) }
        add(stringResource(R.string.follow_status_bar)); add(stringResource(R.string.invert_status_bar))
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

internal fun usesCustomMaterial(raw: String): Boolean = materialType(raw) != "default"
internal fun usesLiquidGlass(raw: String): Boolean = materialType(raw) == "liquid_glass"
internal fun usesGlass(raw: String): Boolean = materialType(raw) in setOf("highlight_glass", "liquid_glass", "soft_glass")
private fun materialType(raw: String): String = runCatching {
    JSONObject(raw).optString("type", "default")
}.getOrDefault("default")

internal const val KEY_BG_SMALL = "pref_island_bg_small_path"
internal const val KEY_BG_BIG = "pref_island_bg_big_path"
internal const val KEY_BG_EXPAND = "pref_island_bg_expand_path"
internal const val KEY_MATERIAL_BIG = "pref_island_material_big_config"
internal const val KEY_MATERIAL_SMALL = "pref_island_material_small_config"
internal const val KEY_MATERIAL_EXPAND = "pref_island_material_expand_config"
internal const val KEY_MATERIAL_SMALL_FOLLOW = "pref_island_material_small_follow_big"
internal const val KEY_MATERIAL_EXPAND_FOLLOW = "pref_island_material_expand_follow_big"
internal const val KEY_BLUR_SMALL = "pref_island_blur_small_enabled"
internal const val KEY_BLUR_BIG = "pref_island_blur_big_enabled"
internal const val KEY_BLUR_EXPAND = "pref_island_blur_expand_enabled"
internal const val KEY_GLASS_SMALL = "pref_island_glass_small_enabled"
internal const val KEY_GLASS_BIG = "pref_island_glass_big_enabled"
internal const val KEY_GLASS_EXPAND = "pref_island_glass_expand_enabled"
internal const val KEY_LIQUID_SMALL = "pref_island_refraction_small_enabled"
internal const val KEY_LIQUID_BIG = "pref_island_refraction_big_enabled"
internal const val KEY_LIQUID_EXPAND = "pref_island_refraction_expand_enabled"
internal const val KEY_GLASS_GYROSCOPE = "pref_island_glass_gyroscope"
internal const val KEY_GLASS_HDR = "pref_island_glass_hdr_highlight"
internal const val KEY_CAPTURE_FPS = "pref_island_glass_capture_fps"
internal const val KEY_CAPTURE_QUALITY = "pref_island_glass_capture_quality"
internal const val KEY_ISLAND_HEIGHT = "pref_island_height"
internal const val KEY_ISLAND_TOP_OFFSET = "pref_island_top_offset"
internal const val KEY_BIG_MAX_WIDTH = "pref_big_island_max_width"
internal const val KEY_BIG_MIN_WIDTH = "pref_big_island_min_width"
internal const val KEY_SMALL_WIDTH = "pref_small_island_width"
internal const val KEY_SMALL_OFFSET = "pref_small_island_horizontal_offset"
internal const val KEY_TEXT_SCALE = "pref_island_text_scale"
internal const val KEY_TEXT_AREA_HEIGHT = "pref_island_text_area_height"
internal const val KEY_TEXT_COLOR = "pref_island_text_color_mode"
internal const val KEY_FOCUS_TEXT_COLOR = "pref_focus_notification_text_color_mode"
internal const val KEY_MEDIA_TEXT_COLOR = "pref_media_notification_text_color_mode"
internal const val KEY_ICON_SIZE = "pref_island_icon_size"
internal const val KEY_ROUND_RADIUS = "pref_round_icon_radius"
internal const val KEY_ROUND_ICON = "pref_round_icon"
internal const val KEY_ICON_PADDING = "pref_island_icon_padding"
internal const val KEY_ALWAYS_ISLAND_OUTLINE = "pref_always_show_island_outline"
internal const val KEY_ALWAYS_FOCUS_OUTLINE = "pref_always_show_focus_outline"
internal const val KEY_GLOW_RANGE = "pref_outer_glow_range"
internal const val KEY_GLOW_SINGLE_COLOR = "pref_outer_glow_single_color"
internal const val KEY_GLOW_BASE_COLOR = "pref_outer_glow_base_color"
