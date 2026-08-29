package io.github.hyperisland.compose.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

@Composable
internal fun PreferenceSwitch(
    title: String,
    summary: String?,
    icon: ImageVector?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SwitchPreference(
        title = title,
        summary = summary,
        checked = checked,
        enabled = enabled,
        startAction = icon?.let { image -> { SettingsIcon(image) } },
        insideMargin = SettingsItemMargin,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
internal fun PreferenceDropdown(
    title: String,
    summary: String?,
    icon: ImageVector?,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
) {
    WindowDropdownPreference(
        title = title,
        summary = summary,
        items = items,
        selectedIndex = selectedIndex,
        startAction = icon?.let { image -> { SettingsIcon(image) } },
        insideMargin = SettingsItemMargin,
        onSelectedIndexChange = onSelectedIndexChange,
    )
}

@Composable
internal fun PreferenceSlider(
    title: String,
    summary: String? = null,
    icon: ImageVector?,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    showKeyPoints: Boolean = false,
    keyPoints: List<Float>? = null,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    SliderPreference(
        title = title,
        summary = summary,
        value = value,
        valueText = valueText,
        valueRange = valueRange,
        steps = steps,
        showKeyPoints = showKeyPoints,
        keyPoints = keyPoints,
        startAction = icon?.let { image -> { SettingsIcon(image) } },
        insideMargin = SettingsItemMargin,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
    )
}
