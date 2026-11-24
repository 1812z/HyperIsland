package io.github.hyperisland.compose.page.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.AppActionTextButton
import io.github.hyperisland.compose.component.AppActionButton
import io.github.hyperisland.compose.component.AppCard
import io.github.hyperisland.compose.component.AppDialog
import io.github.hyperisland.compose.component.BarBackdropContent
import io.github.hyperisland.compose.component.BarBlurHost
import io.github.hyperisland.compose.component.PageTabRow
import io.github.hyperisland.compose.component.PageTopAppBar
import io.github.hyperisland.compose.component.PageTopBarIconButton
import io.github.hyperisland.compose.component.ColorPaletteDialog
import io.github.hyperisland.compose.component.uiGlassBackdrop
import io.github.hyperisland.compose.theme.uiGlassPopupVisuals
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.PreferenceSlider
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsAction
import io.github.hyperisland.compose.component.parseHexColor
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.IslandMaterialConfig
import io.github.hyperisland.compose.data.IslandMaterialSettings
import io.github.hyperisland.compose.data.IslandMaterialState
import io.github.hyperisland.compose.data.IslandMaterialType
import io.github.hyperisland.compose.service.IslandMaterialService
import io.github.hyperisland.compose.service.TestNotificationService
import io.github.hyperisland.utils.HyperOsVersionUtil
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassIconDropdownMenu
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchorContent
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun IslandMaterialPage(
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val service = remember(prefs, context) { IslandMaterialService(prefs, context) }
    val hyperOsMajor = remember { HyperOsVersionUtil.getMajorVersion() }
    var settings by remember { mutableStateOf(service.load()) }
    LaunchedEffect(hyperOsMajor) {
        if (hyperOsMajor == 3 && settings.containsSoftGlass()) {
            settings = service.clearUnsupportedSoftGlass(settings)
        }
    }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { IslandMaterialState.entries.size })
    // 每个 tab 各自持有滚动行为（顺序同 IslandMaterialState.entries：Big / Small / Expand）。
    // 共用一个会让 A 页滚下去的 contentOffset 残留到 B 页，切到位于顶部的 tab 时顶栏仍有玻璃底、仍是收起态。
    // 顶栏 / 分段条读「当前页」那一个，每页列表各挂各的连接，互不干扰。
    val scrollBehaviors = listOf(MiuixScrollBehavior(), MiuixScrollBehavior(), MiuixScrollBehavior())
    val activeScrollBehavior = scrollBehaviors[pagerState.currentPage.coerceIn(scrollBehaviors.indices)]
    val snackbarState = remember { SnackbarHostState() }
    var resetDialog by remember { mutableStateOf(false) }
    var colorState by remember { mutableStateOf<IslandMaterialState?>(null) }

    val copied = stringResource(R.string.material_config_copied)
    val clipboardEmpty = stringResource(R.string.import_empty_clipboard)
    val importSuccess = stringResource(R.string.material_import_success)
    val importFailed = stringResource(R.string.import_unknown_error)
    val softUnsupported = stringResource(R.string.material_soft_glass_os4_only)
    val conflictMessage = stringResource(R.string.material_background_conflict)
    fun showMessage(message: String) { scope.launch { snackbarState.showSnackbar(message) } }

    fun save(state: IslandMaterialState, config: IslandMaterialConfig) {
        val conflict = config.isCustom && service.hasBackgroundConflict(settings, state)
        settings = service.save(settings, state, config)
        if (conflict) showMessage(conflictMessage)
    }

    fun switchType(state: IslandMaterialState, type: IslandMaterialType) {
        if (settings.config(state).type == type) return
        val conflict = type.isCustom && service.hasBackgroundConflict(settings, state)
        settings = service.switchType(settings, state, type)
        if (conflict) showMessage(conflictMessage)
    }

    fun setFollow(state: IslandMaterialState, value: Boolean) {
        val conflict = value && settings.big.isCustom && service.hasBackgroundConflict(settings, state)
        settings = service.setFollow(settings, state, value)
        if (conflict) showMessage(conflictMessage)
    }

    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    val menuExpanded = remember { mutableStateOf(false) }
    val menuAnchor = rememberGlassPopupAnchor()
    val menuEntry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = stringResource(R.string.restore_default),
                onClick = { resetDialog = true },
            ),
            DropdownItem(
                text = stringResource(R.string.export_to_clipboard),
                onClick = {
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            context.getString(R.string.material_customize),
                            service.export(settings),
                        ),
                    )
                    showMessage(copied)
                },
            ),
            DropdownItem(
                text = stringResource(R.string.import_from_clipboard),
                onClick = {
                    val raw = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                    if (raw.isBlank()) {
                        showMessage(clipboardEmpty)
                    } else {
                        runCatching { service.import(raw, allowSoftGlass = hyperOsMajor != 3) }
                            .onSuccess { settings = it; showMessage(importSuccess) }
                            .onFailure {
                                showMessage(
                                    if (it.message == IslandMaterialService.SOFT_GLASS_UNSUPPORTED) softUnsupported
                                    else importFailed,
                                )
                            }
                    }
                },
            ),
        ),
    )

    BarBlurHost {
        Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                PageTopAppBar(
                    title = stringResource(R.string.material_customize),
                    largeTitle = stringResource(R.string.material_customize),
                    scrollBehavior = activeScrollBehavior,
                    navigationIcon = {
                        PageTopBarIconButton(onClick = onBack) {
                            Icon(MiuixIcons.Back, stringResource(R.string.back))
                        }
                    },
                    actions = {
                        PageTopBarIconButton(
                            onClick = { TestNotificationService.sendCustom(context, "", "", true, true) },
                        ) {
                            Icon(
                                ImageVector.vectorResource(R.drawable.ic_test_notification),
                                stringResource(R.string.send_test_notification),
                            )
                        }
                        PageTopBarIconButton(
                            onClick = { menuExpanded.value = true },
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .glassPopupAnchor(
                                    anchor = menuAnchor,
                                    cornerRadius = GlassTopAppBarDefaults.ButtonSize / 2,
                                ),
                        ) {
                            Icon(
                                MiuixIcons.More,
                                stringResource(R.string.list_actions),
                                tint = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.glassPopupAnchorContent(menuAnchor),
                            )
                        }
                    },
                    bottomContent = {
                        PageTabRow(
                            tabs = listOf(
                                stringResource(R.string.material_big_tab),
                                stringResource(R.string.material_small_tab),
                                stringResource(R.string.material_expand_tab),
                            ),
                            selectedIndex = pagerState.currentPage,
                            onSelect = { page ->
                                scope.launch { pagerState.animateScrollToPage(page) }
                            },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        )
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarState) },
        ) { padding ->
            BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val state = IslandMaterialState.entries[page]
                    MaterialStateContent(
                        state = state,
                        settings = settings,
                        hyperOsMajor = hyperOsMajor,
                        topPadding = padding.calculateTopPadding(),
                        scrollBehavior = scrollBehaviors[page],
                        onFollowChange = { setFollow(state, it) },
                        onConfigChange = { save(state, it) },
                        onTypeChange = { switchType(state, it) },
                        onPickColor = { colorState = state },
                    )
                }
            }
        }
        GlassIconDropdownMenu(
            show = menuExpanded.value,
            onDismissRequest = { menuExpanded.value = false },
            anchor = menuAnchor,
            anchorContent = {
                Icon(
                    MiuixIcons.More,
                    stringResource(R.string.list_actions),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            },
            entries = listOf(menuEntry),
            backdrop = uiGlassBackdrop(),
            visuals = uiGlassPopupVisuals(),
        )
        }
    }

    AppDialog(
        show = resetDialog,
        title = stringResource(R.string.restore_default),
        onDismissRequest = { resetDialog = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = stringResource(R.string.restore_default_config_question),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppActionTextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { resetDialog = false },
                    modifier = Modifier.weight(1f),
                )
                AppActionButton(
                    onClick = { settings = service.reset(); resetDialog = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text(stringResource(R.string.confirm)) }
            }
        }
    }

    val activeColorState = colorState
    if (activeColorState != null) {
        val config = settings.config(activeColorState)
        val defaults = IslandMaterialConfig()
        ColorPaletteDialog(
            show = true,
            title = stringResource(R.string.material_blend_color),
            initialColor = config.toPickerColor(),
            onDismiss = { colorState = null },
            onDelete = {
                save(
                    activeColorState,
                    config.copy(
                        blendColor = defaults.blendColor,
                        blendOpacity = defaults.blendOpacity,
                    ),
                )
                colorState = null
            },
        ) { color ->
            save(
                activeColorState,
                config.copy(
                    blendColor = color.toRgbHex(),
                    blendOpacity = (color.alpha * 100f).roundToInt().coerceIn(0, 100),
                ),
            )
            colorState = null
        }
    }
}

@Composable
private fun MaterialStateContent(
    state: IslandMaterialState,
    settings: IslandMaterialSettings,
    hyperOsMajor: Int,
    topPadding: androidx.compose.ui.unit.Dp,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior,
    onFollowChange: (Boolean) -> Unit,
    onConfigChange: (IslandMaterialConfig) -> Unit,
    onTypeChange: (IslandMaterialType) -> Unit,
    onPickColor: () -> Unit,
) {
    val follow = settings.followsBig(state)
    val config = settings.config(state)
    val effective = settings.effective(state)
    val types = IslandMaterialType.entries.filter { hyperOsMajor != 3 || it != IslandMaterialType.SoftGlass }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(start = 16.dp, top = topPadding + 12.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state != IslandMaterialState.Big) {
            item {
                SectionTitle(stringResource(R.string.material_follow_section))
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    PreferenceSwitch(
                        title = stringResource(R.string.material_follow_big),
                        summary = stringResource(R.string.material_follow_big_summary),
                        icon = null,
                        checked = follow,
                        onCheckedChange = onFollowChange,
                    )
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.material_type))
            if (follow) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(
                            R.string.material_following_type,
                            materialTypeLabel(effective.type),
                        ),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                    )
                }
            } else {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    PreferenceDropdown(
                        title = stringResource(R.string.material_type),
                        summary = null,
                        icon = null,
                        items = types.map { materialTypeLabel(it) },
                        selectedIndex = types.indexOf(effective.type).coerceAtLeast(0),
                    ) { index -> onTypeChange(types[index]) }
                }
            }
        }
        if (!follow && config.isCustom) {
            item {
                SectionTitle(stringResource(R.string.material_blur_section))
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    IntegerMaterialSlider(
                        title = stringResource(R.string.material_blur),
                        value = config.blur,
                        range = 0..100,
                        defaultValue = DEFAULT_MATERIAL.blur,
                    ) { onConfigChange(config.copy(blur = it)) }
                }
            }
            if (config.type == IslandMaterialType.HighlightGlass || config.type == IslandMaterialType.LiquidGlass) {
                item {
                    SectionTitle(stringResource(R.string.glass_customize))
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        IntegerMaterialSlider(stringResource(R.string.glass_edge_width), config.edgeThickness, 4..40, DEFAULT_MATERIAL.edgeThickness) { onConfigChange(config.copy(edgeThickness = it)) }
                        IntegerMaterialSlider(stringResource(R.string.glass_refraction), config.refraction, 0..40, DEFAULT_MATERIAL.refraction) { onConfigChange(config.copy(refraction = it)) }
                        IntegerMaterialSlider(stringResource(R.string.glass_highlight), config.reflectionStrength, 0..100, DEFAULT_MATERIAL.reflectionStrength) { onConfigChange(config.copy(reflectionStrength = it)) }
                        IntegerMaterialSlider(stringResource(R.string.glass_shadow), config.darker, 0..100, DEFAULT_MATERIAL.darker) { onConfigChange(config.copy(darker = it)) }
                        IntegerMaterialSlider(stringResource(R.string.glass_light_direction), config.lightDirection, 0..359, DEFAULT_MATERIAL.lightDirection) { onConfigChange(config.copy(lightDirection = it)) }
                        IntegerMaterialSlider(stringResource(R.string.glass_dispersion), config.dispersion, 0..100, DEFAULT_MATERIAL.dispersion) { onConfigChange(config.copy(dispersion = it)) }
                    }
                }
            }
            if (config.type == IslandMaterialType.SoftGlass) {
                item {
                    SectionTitle(stringResource(R.string.material_lighting_section))
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        DecimalMaterialSlider(stringResource(R.string.material_soft_light), config.softLight, DEFAULT_MATERIAL.softLight) { onConfigChange(config.copy(softLight = it)) }
                        DecimalMaterialSlider(stringResource(R.string.material_saturation), config.saturation, DEFAULT_MATERIAL.saturation) { onConfigChange(config.copy(saturation = it)) }
                        DecimalMaterialSlider(stringResource(R.string.material_brightness), config.brightness, DEFAULT_MATERIAL.brightness) { onConfigChange(config.copy(brightness = it)) }
                        DecimalMaterialSlider(stringResource(R.string.material_darker), config.softDarker, DEFAULT_MATERIAL.softDarker) { onConfigChange(config.copy(softDarker = it)) }
                        DecimalMaterialSlider(stringResource(R.string.material_transparency), config.transparency, DEFAULT_MATERIAL.transparency) { onConfigChange(config.copy(transparency = it)) }
                        DecimalMaterialSlider(stringResource(R.string.material_burn), config.burn, DEFAULT_MATERIAL.burn) { onConfigChange(config.copy(burn = it)) }
                    }
                }
                item {
                    SectionTitle(stringResource(R.string.material_refraction_section))
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        DecimalMaterialSlider(stringResource(R.string.material_refraction), config.softRefraction, DEFAULT_MATERIAL.softRefraction) { onConfigChange(config.copy(softRefraction = it)) }
                        DecimalMaterialSlider(stringResource(R.string.material_edge_thickness), config.softEdgeThickness, DEFAULT_MATERIAL.softEdgeThickness) { onConfigChange(config.copy(softEdgeThickness = it)) }
                        DecimalMaterialSlider(stringResource(R.string.material_shape_edge_width), config.softShapeEdgeWidth, DEFAULT_MATERIAL.softShapeEdgeWidth) { onConfigChange(config.copy(softShapeEdgeWidth = it)) }
                        DecimalMaterialSlider(stringResource(R.string.material_shape_edge_power), config.softShapeEdgePower, DEFAULT_MATERIAL.softShapeEdgePower) { onConfigChange(config.copy(softShapeEdgePower = it)) }
                        DecimalMaterialSlider(stringResource(R.string.material_refraction_brightness), config.softReflectionLighten, DEFAULT_MATERIAL.softReflectionLighten) { onConfigChange(config.copy(softReflectionLighten = it)) }
                        DecimalMaterialSlider(stringResource(R.string.material_reflection_strength), config.softReflection, DEFAULT_MATERIAL.softReflection) { onConfigChange(config.copy(softReflection = it)) }
                        DecimalMaterialSlider(stringResource(R.string.material_directional_light), config.directionalLightIntensity, DEFAULT_MATERIAL.directionalLightIntensity) { onConfigChange(config.copy(directionalLightIntensity = it)) }
                    }
                }
                item {
                    SectionTitle(stringResource(R.string.material_background_section))
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        DecimalMaterialSlider(stringResource(R.string.material_background_saturation), config.backgroundSaturation, DEFAULT_MATERIAL.backgroundSaturation) { onConfigChange(config.copy(backgroundSaturation = it)) }
                        DecimalMaterialSlider(stringResource(R.string.material_background_brightness), config.backgroundBrightness, DEFAULT_MATERIAL.backgroundBrightness) { onConfigChange(config.copy(backgroundBrightness = it)) }
                    }
                }
            }
            item {
                SectionTitle(stringResource(R.string.material_blend_section))
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    SettingsAction(
                        title = stringResource(R.string.material_blend_color),
                        summary = config.toPickerColor().toArgbHex(),
                        endIcon = MiuixIcons.ChevronForward,
                        onClick = onPickColor,
                    )
                    AnimatedVisibility(visible = config.type == IslandMaterialType.SoftGlass) {
                        PreferenceSwitch(
                            title = stringResource(R.string.material_highlight_switch),
                            summary = null,
                            icon = null,
                            checked = config.highlight,
                        ) { onConfigChange(config.copy(highlight = it)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntegerMaterialSlider(
    title: String,
    value: Int,
    range: IntRange,
    defaultValue: Int,
    onChangeFinished: (Int) -> Unit,
) {
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    PreferenceSlider(
        title = title,
        icon = null,
        value = draft,
        valueText = draft.toInt().toString(),
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first - 1).coerceAtLeast(0),
        // 与公共组件一致：值不等于默认值时露出「恢复默认」按钮。
        resetVisible = value != defaultValue,
        onReset = {
            draft = defaultValue.toFloat()
            onChangeFinished(defaultValue)
        },
        onValueChange = { draft = it.toInt().toFloat() },
        onValueChangeFinished = { onChangeFinished(draft.toInt()) },
    )
}

@Composable
private fun DecimalMaterialSlider(
    title: String,
    value: Double,
    defaultValue: Double,
    onChangeFinished: (Double) -> Unit,
) {
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    PreferenceSlider(
        title = title,
        icon = null,
        value = draft,
        valueText = decimalDisplay(draft),
        valueRange = -50f..50f,
        steps = 999,
        // 与公共组件一致：值不等于默认值时露出「恢复默认」按钮。
        resetVisible = value != defaultValue,
        onReset = {
            draft = defaultValue.toFloat()
            onChangeFinished(defaultValue)
        },
        onValueChange = { draft = (it * 10).toInt() / 10f },
        onValueChangeFinished = { onChangeFinished(((draft * 10).toInt() / 10.0)) },
    )
}

@Composable
private fun materialTypeLabel(type: IslandMaterialType): String = stringResource(
    when (type) {
        IslandMaterialType.Default -> R.string.material_default
        IslandMaterialType.Gaussian -> R.string.material_gaussian
        IslandMaterialType.HighlightGlass -> R.string.material_highlight_glass
        IslandMaterialType.LiquidGlass -> R.string.material_liquid_glass
        IslandMaterialType.SoftGlass -> R.string.material_soft_glass
    },
)

private fun IslandMaterialSettings.containsSoftGlass(): Boolean =
    listOf(big, small, expand).any { it.type == IslandMaterialType.SoftGlass }

private fun decimalDisplay(value: Float): String {
    val hundredths = String.format(Locale.ROOT, "%.2f", value)
    return if (hundredths.endsWith("0")) String.format(Locale.ROOT, "%.1f", value) else hundredths
}

/**
 * 各滑条「恢复默认」的目标值。
 *
 * 直接取 [IslandMaterialConfig] 的字段默认值，避免在调用点再抄一遍字面量——
 * 将来改默认值时只需改数据类，滑条与序列化两边自动一致。
 */
private val DEFAULT_MATERIAL = IslandMaterialConfig()

private fun Color.toRgbHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

private fun Color.toArgbHex(): String = "#%08X".format(toArgb())

private fun IslandMaterialConfig.toPickerColor(): Color =
    parseHexColor(blendColor, Color.Black).copy(alpha = blendOpacity.coerceIn(0, 100) / 100f)
