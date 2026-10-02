package io.github.hyperisland.compose.page.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.findViewTreeNavigationEventDispatcherOwner
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.ConfigSectionTree
import io.github.hyperisland.compose.component.DetailGridPage
import io.github.hyperisland.compose.data.ConfigPreset
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.PRESET_AUTHOR_MAX
import io.github.hyperisland.compose.data.PRESET_CONTENT_MAX
import io.github.hyperisland.compose.data.PRESET_TITLE_MAX
import io.github.hyperisland.compose.data.PresetSortOrder
import io.github.hyperisland.compose.data.PresetStore
import io.github.hyperisland.compose.data.findConfigSection
import io.github.hyperisland.compose.data.parseAppConfigLeafId
import io.github.hyperisland.compose.data.sortPresets
import io.github.hyperisland.compose.service.HubClient
import io.github.hyperisland.compose.service.HubException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.OverlayIconCascadingDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

private val PresetCardHeight = 144.dp

/**
 * 云端列表的进程级状态：跨越页面返回 / 再次进入保持，避免每次进入都重新拉取。
 * 首次进入自动拉取一次；之后只有下拉刷新才强制重新拉取。
 */
private object PresetCloudState {
    var presets by mutableStateOf<List<ConfigPreset>>(emptyList())
    var loaded by mutableStateOf(false)
    var refreshing by mutableStateOf(false)

    suspend fun ensureLoaded() {
        if (loaded) return
        refresh()
    }

    suspend fun refresh() {
        refreshing = true
        try {
            presets = HubClient.listAll()
            loaded = true
        } finally {
            refreshing = false
        }
    }
}

/**
 * 一键配置预设页面。
 *
 * 本地 + 云端（HyperIsland-Hub）预设统一展示。顶栏 More 菜单可新建配置 / 切换排序；
 * 点击卡片打开应用配置 bottom sheet，云端预设会在打开时下载正文。
 */
@Composable
internal fun PresetConfigPage(
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }

    var reload by remember { mutableIntStateOf(0) }
    var sortOrder by remember { mutableStateOf(PresetSortOrder.Date) }
    val localPresets = remember(reload, context) { PresetStore.loadLocal(context) }
    val cloudPresets = PresetCloudState.presets

    val cloudLoadFailed = stringResource(R.string.preset_cloud_load_failed)
    fun refreshCloud() {
        scope.launch {
            runCatching { PresetCloudState.refresh() }
                .onFailure { snackbarState.showSnackbar(cloudLoadFailed) }
        }
    }

    // 从剪贴板导入复制的预设 JSON（与「复制」输出的格式一致）到本地预设。
    fun importFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
        val preset = text?.takeIf { it.isNotBlank() }
            ?.let { runCatching { ConfigPreset.fromJson(JSONObject(it)) }.getOrNull() }
            ?.takeIf { it.title.isNotBlank() }
        when {
            text.isNullOrBlank() -> scope.launch {
                snackbarState.showSnackbar(context.getString(R.string.preset_import_empty))
            }
            preset == null -> scope.launch {
                snackbarState.showSnackbar(context.getString(R.string.preset_import_failed))
            }
            else -> {
                val local = preset.copy(id = UUID.randomUUID().toString(), local = true, downloads = 0L)
                PresetStore.saveLocal(context, local)
                reload++
                scope.launch {
                    snackbarState.showSnackbar(context.getString(R.string.preset_import_success, local.title))
                }
            }
        }
    }
    // 仅在软件本次启动后首次进入页面时自动拉取；再次进入复用进程内列表，手动下拉才刷新。
    LaunchedEffect(Unit) {
        runCatching { PresetCloudState.ensureLoaded() }
            .onFailure { snackbarState.showSnackbar(cloudLoadFailed) }
    }

    val localSorted = remember(localPresets, sortOrder) { sortPresets(localPresets, sortOrder) }
    val otherSorted = remember(cloudPresets, sortOrder) {
        sortPresets(cloudPresets, sortOrder)
    }

    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val keyword = searchQuery.trim()
    val localFiltered = remember(localSorted, keyword) {
        if (keyword.isBlank()) localSorted else localSorted.filter { it.matchesQuery(keyword) }
    }
    val otherFiltered = remember(otherSorted, keyword) {
        if (keyword.isBlank()) otherSorted else otherSorted.filter { it.matchesQuery(keyword) }
    }

    var showNewSheet by remember { mutableStateOf(false) }
    var applyTarget by remember { mutableStateOf<ConfigPreset?>(null) }
    var applySheetShown by remember { mutableStateOf(false) }
    // 应用成功后待弹出的提示条数；等 bottom sheet 关闭动画结束再展示。
    var appliedCount by remember { mutableStateOf<Int?>(null) }
    // 上传成功后待弹出到页面底部的提示；同样等 sheet 关闭动画结束再展示。
    var uploadResult by remember { mutableStateOf<String?>(null) }

    val newConfigLabel = stringResource(R.string.preset_new_config)
    val importLabel = stringResource(R.string.preset_import_clipboard)
    val sortLabel = stringResource(R.string.preset_sort)
    val nameLabel = stringResource(R.string.preset_sort_name)
    val downloadsLabel = stringResource(R.string.preset_sort_downloads)
    val dateLabel = stringResource(R.string.preset_sort_date)
    val menuEntries = remember(newConfigLabel, importLabel, sortLabel, nameLabel, downloadsLabel, dateLabel, sortOrder) {
        listOf(
            DropdownEntry(
                items = listOf(
                    DropdownItem(text = newConfigLabel, onClick = { showNewSheet = true }),
                    DropdownItem(text = importLabel, onClick = { importFromClipboard() }),
                ),
            ),
            DropdownEntry(
                items = listOf(
                    DropdownItem(
                        text = sortLabel,
                        children = listOf(
                            DropdownItem(
                                text = nameLabel,
                                selected = sortOrder == PresetSortOrder.Name,
                                onClick = { sortOrder = PresetSortOrder.Name },
                            ),
                            DropdownItem(
                                text = downloadsLabel,
                                selected = sortOrder == PresetSortOrder.Downloads,
                                onClick = { sortOrder = PresetSortOrder.Downloads },
                            ),
                            DropdownItem(
                                text = dateLabel,
                                selected = sortOrder == PresetSortOrder.Date,
                                onClick = { sortOrder = PresetSortOrder.Date },
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    DetailGridPage(
        title = stringResource(R.string.preset_page_title),
        onBack = onBack,
        actions = {
            OverlayIconCascadingDropdownMenu(entries = menuEntries) {
                Icon(MiuixIcons.More, stringResource(R.string.list_actions))
            }
        },
        snackbarHost = { SnackbarHost(snackbarState) },
        isRefreshing = PresetCloudState.refreshing,
        onRefresh = { refreshCloud() },
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchBar(
                inputField = {
                    InputField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = {},
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        label = stringResource(R.string.preset_search),
                    )
                },
                onExpandedChange = { searchExpanded = it },
                expanded = searchExpanded,
                outsideEndAction = {
                    Text(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .clickable(interactionSource = null, indication = null) {
                                searchExpanded = false
                            },
                        text = stringResource(R.string.cancel),
                        color = MiuixTheme.colorScheme.primary,
                    )
                },
            ) {}
        }
        // 本地配置置顶，且不显示下载量。
        items(localFiltered, key = { it.id }) { preset ->
            PresetConfigCard(
                preset = preset,
                showDownloads = false,
                onClick = {
                    applyTarget = preset
                    applySheetShown = true
                },
            )
        }
        if (localFiltered.isNotEmpty() && otherFiltered.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        items(otherFiltered, key = { it.id }) { preset ->
            PresetConfigCard(
                preset = preset,
                showDownloads = true,
                onClick = {
                    applyTarget = preset
                    applySheetShown = true
                },
            )
        }
    }

    NewPresetBottomSheet(
        show = showNewSheet,
        onDismiss = { showNewSheet = false },
        onSave = { title, content, author, selectedLeafIds ->
            val preset = ConfigPreset(
                id = UUID.randomUUID().toString(),
                title = title,
                content = content,
                author = author,
                downloads = 0L,
                local = true,
                createdAt = System.currentTimeMillis(),
                sections = PresetStore.snapshot(context, selectedLeafIds),
            )
            PresetStore.saveLocal(context, preset)
            reload++
            showNewSheet = false
        },
    )

    applyTarget?.let { preset ->
        ApplyPresetBottomSheet(
            show = applySheetShown,
            preset = preset,
            onDismiss = { applySheetShown = false },
            onDismissFinished = {
                applyTarget = null
                appliedCount?.let { count ->
                    appliedCount = null
                    scope.launch {
                        snackbarState.showSnackbar(context.getString(R.string.preset_applied_count, count))
                    }
                }
                uploadResult?.let { message ->
                    uploadResult = null
                    scope.launch { snackbarState.showSnackbar(message) }
                }
            },
            onDelete = {
                PresetStore.deleteLocal(context, preset.id)
                reload++
                applySheetShown = false
            },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(preset.title, preset.toJson().toString(2)),
                )
            },
            onApply = { effective, selectedSectionIds ->
                PresetStore.apply(context, effective, selectedSectionIds)
                appliedCount = effective.sections
                    .filterKeys { it in selectedSectionIds }
                    .values
                    .sumOf { it.length() }
                applySheetShown = false
            },
            onSaveContent = { updated ->
                PresetStore.saveLocal(context, updated)
                reload++
            },
            onUploadResult = { message ->
                uploadResult = message
                applySheetShown = false
            },
        )
    }
}

/**
 * 设置页顶部的预设入口卡片。使用 Miuix 默认（信息）卡片配色，不指定颜色。
 */
@Composable
internal fun PresetEntryCard(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        showIndication = true,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MiuixIcons.Help,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MiuixTheme.textStyles.body1,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                modifier = Modifier.size(width = 10.dp, height = 16.dp),
            )
        }
    }
}

/**
 * 单个预设卡片：固定高度，标题、副标题、分割线、作者与下载量。
 */
@Composable
internal fun PresetConfigCard(
    preset: ConfigPreset,
    showDownloads: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(PresetCardHeight),
        showIndication = true,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text(
                text = preset.title,
                style = MiuixTheme.textStyles.headline2,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = preset.content,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PresetMetaItem(
                    icon = MiuixIcons.Contacts,
                    text = preset.author,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (showDownloads) {
                    Spacer(Modifier.width(8.dp))
                    PresetMetaItem(
                        icon = MiuixIcons.Download,
                        text = formatDownloadCount(preset.downloads),
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetMetaItem(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** bottom sheet 内容底部统一留出导航条（小白条）高度。 */
@Composable
private fun sheetBottomPadding(extra: Dp = 12.dp): Dp {
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return extra + navBar
}

/**
 * 新建配置 bottom sheet：顶部配置信息（标题 / 内容 / 作者）+ 配置分类选择树。
 */
@Composable
private fun NewPresetBottomSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, author: String, selectedLeafIds: Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val sectionCounts = remember(show) { PresetStore.sectionKeyCounts(context) }
    val sectionTree = remember(show) { PresetStore.appConfigSectionTree(context) }
    var titleInput by remember(show) { mutableStateOf("") }
    var contentInput by remember(show) { mutableStateOf("") }
    var authorInput by remember(show) { mutableStateOf("") }
    var selectedLeafIds by remember(show) { mutableStateOf(emptySet<String>()) }

    WindowBottomSheet(
        show = show,
        title = stringResource(R.string.preset_new_config),
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = sheetBottomPadding(16.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SmallTitle(stringResource(R.string.preset_info_section))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextField(
                            value = titleInput,
                            onValueChange = { if (it.length <= PRESET_TITLE_MAX) titleInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = stringResource(R.string.preset_field_title),
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                        )
                        TextField(
                            value = contentInput,
                            onValueChange = { if (it.length <= PRESET_CONTENT_MAX) contentInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = stringResource(R.string.preset_field_content),
                            useLabelAsPlaceholder = true,
                            singleLine = false,
                            maxLines = 3,
                        )
                        TextField(
                            value = authorInput,
                            onValueChange = { if (it.length <= PRESET_AUTHOR_MAX) authorInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = stringResource(R.string.preset_field_author),
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                        )
                    }
                }
            }
            item {
                SmallTitle(stringResource(R.string.config))
                Card(modifier = Modifier.fillMaxWidth()) {
                    ConfigSectionTree(
                        sections = sectionTree,
                        selectedLeafIds = selectedLeafIds,
                        onSelectedLeafIdsChange = { selectedLeafIds = it },
                        counts = sectionCounts,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { onSave(titleInput.trim(), contentInput.trim(), authorInput.trim(), selectedLeafIds) },
                        enabled = titleInput.isNotBlank() && selectedLeafIds.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }
}

/**
 * 应用配置 bottom sheet：介绍 + 分节多选（显示条数）+ 底部操作。
 *
 * - 上「介绍」下「配置」，中间用分割线隔开；不显示作者。
 * - 本地预设有编辑 / 上传 / 复制 / 删除 / 应用；云端预设有查看 / 取消 / 应用。
 * - 云端预设打开时自动下载正文（详情接口会累加下载量），命中本地缓存则不下载。
 * - 「查看 / 编辑」进入配置内容视图，本地可改值并保存。
 */
@Composable
private fun ApplyPresetBottomSheet(
    show: Boolean,
    preset: ConfigPreset,
    onDismiss: () -> Unit,
    onDismissFinished: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onApply: (ConfigPreset, Set<String>) -> Unit,
    onSaveContent: (ConfigPreset) -> Unit,
    onUploadResult: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val needsDownload = HubClient.isHubId(preset.id) && preset.sections.isEmpty()
    var resolved by remember(preset) { mutableStateOf<ConfigPreset?>(null) }
    var downloading by remember(preset) { mutableStateOf(needsDownload) }
    var downloadFailed by remember(preset) { mutableStateOf(false) }
    var downloadAttempt by remember(preset) { mutableIntStateOf(0) }

    var uploading by remember(preset) { mutableStateOf(false) }

    var showContent by remember(preset) { mutableStateOf(false) }
    var savedMessage by remember(preset) { mutableStateOf(false) }
    val draft = remember(preset) { mutableStateMapOf<String, String>() }

    LaunchedEffect(preset, downloadAttempt) {
        if (!needsDownload) return@LaunchedEffect
        // 命中本地缓存就不再联网，避免每次点进来都下载。缓存序列化放到 IO 线程。
        val cached = withContext(Dispatchers.IO) {
            PresetStore.cachedHubPreset(context, preset.id, preset.version)
        }
        if (cached != null) {
            resolved = cached
            downloading = false
            return@LaunchedEffect
        }
        downloading = true
        downloadFailed = false
        runCatching { HubClient.detail(context, preset) }
            .onSuccess { detail ->
                withContext(Dispatchers.IO) { PresetStore.cacheHubPreset(context, detail) }
                resolved = detail
            }
            .onFailure { downloadFailed = true }
        downloading = false
    }

    val effective = resolved ?: preset
    val includedIds = remember(effective) { effective.sections.keys.toList() }
    var selectedIds by remember(effective) { mutableStateOf(includedIds.toSet()) }
    // 云端列表 / 详情返回的是 ISO8601 字符串，HubClient 已转为 epoch 毫秒；这里再转本地可读时间。
    val createdLabel = remember(preset.createdAt) { formatPresetTime(preset.createdAt) }

    fun openContent() {
        draft.clear()
        effective.sections.forEach { (sectionId, values) ->
            values.keys().forEach { key ->
                draft[draftKey(sectionId, key)] = displayValue(values.opt(key))
            }
        }
        savedMessage = false
        showContent = true
    }

    WindowBottomSheet(
        show = show,
        title = preset.title,
        // 内容视图下返回 / 点击外部逐级返回详情，而不是直接关闭整个 bottom sheet。
        onDismissRequest = { if (showContent) showContent = false else onDismiss() },
        onDismissFinished = onDismissFinished,
    ) {
        // 预测性返回手势会先让 sheet 自身滑出，无法通过 onDismissRequest 拦截；
        // 这里在内容视图时抢先注册返回处理器，使返回逐级回到详情。
        ContentBackInterceptor(enabled = showContent) { showContent = false }
        AnimatedContent(
            targetState = showContent,
            transitionSpec = {
                if (targetState) {
                    (slideInHorizontally { it } + fadeIn(tween(300, easing = FastOutSlowInEasing))) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut(tween(200, easing = FastOutSlowInEasing)))
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn(tween(300, easing = FastOutSlowInEasing))) togetherWith
                        (slideOutHorizontally { it } + fadeOut(tween(200, easing = FastOutSlowInEasing)))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "presetSheetContent",
        ) { contentShown ->
            if (contentShown) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = sheetBottomPadding(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    effective.sections.entries.forEachIndexed { index, (sectionId, values) ->
                        if (index > 0) {
                            item(key = "divider_$sectionId") {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                        item(key = "title_$sectionId") {
                            // 与 SmallTitle 同样的字号 / 颜色，仅改为居中。
                            Text(
                                text = configSectionTitle(sectionId),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 28.dp, vertical = 8.dp),
                                style = MiuixTheme.textStyles.subtitle,
                                color = MiuixTheme.colorScheme.onBackgroundVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        item(key = "card_$sectionId") {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    values.keys().forEach { key ->
                                        val draftRef = draftKey(sectionId, key)
                                        ContentEntryRow(
                                            entryKey = key,
                                            value = draft[draftRef] ?: displayValue(values.opt(key)),
                                            editable = effective.local,
                                            onValueChange = { draft[draftRef] = it },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            savedMessage.takeIf { it }?.let {
                                Text(
                                    text = stringResource(R.string.preset_saved),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                TextButton(
                                    text = stringResource(R.string.back),
                                    onClick = { showContent = false },
                                    modifier = Modifier.weight(1f),
                                )
                                if (effective.local) {
                                    Button(
                                        onClick = {
                                            onSaveContent(buildEdited(effective, draft))
                                            savedMessage = true
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColorsPrimary(),
                                    ) {
                                        Text(stringResource(R.string.save))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = sheetBottomPadding(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(
                                modifier = Modifier.width(IntrinsicSize.Max),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (preset.content.isNotBlank()) {
                                    Text(
                                        text = "${stringResource(R.string.preset_field_description)}:",
                                        style = MiuixTheme.textStyles.body1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                if (createdLabel.isNotBlank()) {
                                    Text(
                                        text = "${stringResource(R.string.preset_field_time)}:",
                                        style = MiuixTheme.textStyles.body1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (preset.content.isNotBlank()) {
                                    Text(
                                        text = preset.content,
                                        style = MiuixTheme.textStyles.body1,
                                        color = MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                                if (createdLabel.isNotBlank()) {
                                    Text(
                                        text = createdLabel,
                                        style = MiuixTheme.textStyles.body1,
                                        color = MiuixTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                    item { HorizontalDivider() }

                    when {
                        downloading -> item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.preset_downloading),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }

                        downloadFailed -> item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.preset_download_failed),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.error,
                                )
                                Button(
                                    onClick = { downloadAttempt++ },
                                    colors = ButtonDefaults.buttonColorsPrimary(),
                                ) {
                                    Text(stringResource(R.string.preset_retry))
                                }
                            }
                        }

                        else -> {
                            if (includedIds.isNotEmpty()) {
                                item {
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column {
                                            includedIds.forEach { id ->
                                                val checked = id in selectedIds
                                                SelectableSectionRow(
                                                    // 跨版本预设可能带当前注册表没有的分节 id，回退用原始 id 展示。
                                                    title = configSectionTitle(id),
                                                    count = appSectionCount(id, effective.sections[id]),
                                                    checked = checked,
                                                    onToggle = {
                                                        selectedIds = if (checked) selectedIds - id else selectedIds + id
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // 查看 / 编辑：进入配置内容视图。
                                    Button(
                                        onClick = ::openContent,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(),
                                    ) {
                                        Text(
                                            stringResource(
                                                if (preset.local) R.string.preset_edit else R.string.preset_view,
                                            ),
                                        )
                                    }
                                    if (preset.local) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        uploading = true
                                                        val message = runCatching { HubClient.upload(preset) }
                                                            .fold(
                                                                onSuccess = {
                                                                    context.getString(R.string.preset_upload_success)
                                                                },
                                                                onFailure = { error ->
                                                                    uploadErrorMessage(context, error)
                                                                },
                                                            )
                                                        uploading = false
                                                        onUploadResult(message)
                                                    }
                                                },
                                                enabled = !uploading,
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(),
                                            ) {
                                                Text(
                                                    if (uploading) {
                                                        stringResource(R.string.preset_uploading)
                                                    } else {
                                                        stringResource(R.string.preset_upload)
                                                    },
                                                )
                                            }
                                            Button(
                                                onClick = onCopy,
                                                enabled = !uploading,
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(),
                                            ) {
                                                Text(stringResource(R.string.preset_copy))
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            TextButton(
                                                text = stringResource(R.string.preset_delete),
                                                onClick = onDelete,
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.textButtonColors(
                                                    textColor = MiuixTheme.colorScheme.error,
                                                ),
                                            )
                                            Button(
                                                onClick = { onApply(effective, selectedIds) },
                                                enabled = selectedIds.isNotEmpty(),
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColorsPrimary(),
                                            ) {
                                                Text(stringResource(R.string.preset_apply))
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            TextButton(
                                                text = stringResource(R.string.cancel),
                                                onClick = onDismiss,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Button(
                                                onClick = { onApply(effective, selectedIds) },
                                                enabled = selectedIds.isNotEmpty(),
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColorsPrimary(),
                                            ) {
                                                Text(stringResource(R.string.preset_apply))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 搜索匹配：标题 / 说明 / 作者任一包含关键字即命中（忽略大小写）。 */
private fun ConfigPreset.matchesQuery(keyword: String): Boolean =
    title.contains(keyword, ignoreCase = true) ||
        content.contains(keyword, ignoreCase = true) ||
        author.contains(keyword, ignoreCase = true)

/** 把 epoch 毫秒转成本地可读时间；解析失败或未设置时返回空串。 */
private fun formatPresetTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    return runCatching {
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrDefault("")
}

private fun uploadErrorMessage(context: Context, error: Throwable): String = when {
    error is HubException && error.code == 429 -> {
        val detail = error.detail as? JSONObject
        context.getString(
            R.string.preset_upload_quota,
            detail?.optInt("used") ?: 0,
            detail?.optInt("limit") ?: 0,
        )
    }
    error is HubException && error.code == 422 && error.detail is String -> error.detail
    else -> context.getString(R.string.preset_upload_failed)
}

/** 草稿 key：sectionId + 配置键。 */
private fun draftKey(sectionId: String, key: String): String = "$sectionId|$key"

private fun displayValue(value: Any?): String = when (value) {
    null, JSONObject.NULL -> ""
    is String -> value
    else -> value.toString()
}

/** 按原类型把编辑后的文本还原成配置值，避免把布尔 / 数字写成字符串。 */
private fun coerceValue(original: Any?, text: String): Any = when (original) {
    is Boolean -> text.trim().equals("true", ignoreCase = true)
    is Int -> text.trim().toIntOrNull() ?: original
    is Long -> text.trim().toLongOrNull() ?: original
    is Double -> text.trim().toDoubleOrNull() ?: original
    is Float -> text.trim().toFloatOrNull() ?: original
    // 应用级叶子（通知 / Toast）以子对象 JSON 形式存放，编辑后需解析回 JSON。
    is JSONObject -> runCatching { JSONObject(text) }.getOrDefault(original)
    else -> text
}

/** 应用级叶子不显示「xx 条配置」，只显示应用名。 */
private fun appSectionCount(id: String, values: JSONObject?): Int =
    if (parseAppConfigLeafId(id) != null) 0 else values?.length() ?: 0

/** 分节标题：应用级叶子显示为「通知 / Toast · 应用名」，其余用注册表标题。 */
@Composable
private fun configSectionTitle(id: String): String {
    val context = LocalContext.current
    parseAppConfigLeafId(id)?.let { (kind, packageName) ->
        val label = remember(packageName) { PresetStore.appLabel(context, packageName) }
        return stringResource(kind.titleRes) + " · " + label
    }
    return findConfigSection(id)?.let { stringResource(it.titleRes) } ?: id
}

private fun buildEdited(base: ConfigPreset, draft: Map<String, String>): ConfigPreset {
    val sections = LinkedHashMap<String, JSONObject>()
    base.sections.forEach { (sectionId, values) ->
        val edited = JSONObject()
        values.keys().forEach { key ->
            val original = values.opt(key)
            val text = draft[draftKey(sectionId, key)] ?: displayValue(original)
            edited.put(key, coerceValue(original, text))
        }
        sections[sectionId] = edited
    }
    return base.copy(sections = sections)
}

/**
 * 内容视图激活时注册导航事件返回处理器，抢在 [WindowBottomSheet] 自身的返回处理之前接管返回。
 *
 * WindowBottomSheet 的预测性返回会先把 sheet 滑出视口再回调 onDismissRequest，
 * 因此只能在导航事件层面提前拦截，才能实现逐级返回详情。
 */
@Composable
private fun ContentBackInterceptor(enabled: Boolean, onBack: () -> Unit) {
    val view = LocalView.current
    val currentOnBack by rememberUpdatedState(onBack)
    val dispatcher = remember(view) {
        view.findViewTreeNavigationEventDispatcherOwner()?.navigationEventDispatcher
    }
    // 订阅后只注册一次；用 isBackEnabled 控制是否抢占返回，避免无法从 dispatcher 注销。
    val handler = remember(dispatcher) {
        dispatcher?.let {
            object : NavigationEventHandler<NavigationEventInfo>(
                initialInfo = NavigationEventInfo.None,
                isBackEnabled = enabled,
            ) {
                override fun onBackCompleted() {
                    currentOnBack()
                }
            }
        }
    }
    DisposableEffect(dispatcher, handler) {
        // 用 OVERLAY 优先级抢在 bottom sheet 自身的默认返回处理之前。
        if (dispatcher != null && handler != null) {
            dispatcher.addHandler(handler, NavigationEventDispatcher.PRIORITY_OVERLAY)
        }
        onDispose { handler?.remove() }
    }
    SideEffect {
        handler?.isBackEnabled = enabled
    }
}

@Composable
private fun ContentEntryRow(
    entryKey: String,
    value: String,
    editable: Boolean,
    onValueChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = entryKey,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(4.dp))
        if (editable) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        } else {
            Text(
                text = value,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
            )
        }
    }
}

@Composable
private fun SelectableSectionRow(
    title: String,
    count: Int,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceContainer,
        )
        if (count > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.preset_section_count, count),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Checkbox(
            state = ToggleableState(checked),
            onClick = onToggle,
        )
    }
}

private fun formatDownloadCount(count: Long): String =
    if (count < 1000) {
        count.toString()
    } else {
        String.format(java.util.Locale.ROOT, "%.1fk", count / 1000.0)
    }