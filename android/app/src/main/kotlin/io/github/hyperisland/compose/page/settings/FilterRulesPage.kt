package io.github.hyperisland.compose.page.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.PageSearchField
import io.github.hyperisland.compose.component.AppCard
import io.github.hyperisland.compose.component.BarBackdropContent
import io.github.hyperisland.compose.component.BarBlurHost
import io.github.hyperisland.compose.component.GlassOptionPopup
import io.github.hyperisland.compose.component.PageTopAppBar
import io.github.hyperisland.compose.component.PageTopBarIconButton
import io.github.hyperisland.compose.component.uiGlassBackdrop
import io.github.hyperisland.compose.theme.uiGlassPopupVisuals
import io.github.hyperisland.compose.component.LocalRootBottomBarPadding
import io.github.hyperisland.compose.component.PageTabRow
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.InstalledApp
import io.github.hyperisland.compose.data.InstalledAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.glass.GlassIconDropdownMenu
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchorContent
import top.yukonga.miuix.kmp.glass.glassPopupAnchorValue
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun FilterRulesPage(
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appsRepository = remember(context) { InstalledAppsRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val menuExpanded = remember { mutableStateOf(false) }
    val menuAnchor = rememberGlassPopupAnchor()
    val snackbarState = remember { SnackbarHostState() }
    var apps by remember { mutableStateOf(appsRepository.cachedApps()) }
    var initialLoading by remember { mutableStateOf(apps.isEmpty()) }
    var refreshing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showConfiguredApps by remember { mutableStateOf(true) }
    var showUnconfiguredApps by remember { mutableStateOf(true) }
    var showSystemApps by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }

    val resetResult = stringResource(R.string.filter_reset_result)

    fun loadApps(forceRefresh: Boolean) {
        scope.launch {
            if (forceRefresh) refreshing = true else initialLoading = apps.isEmpty()
            try {
                val loaded = withContext(Dispatchers.IO) {
                    runCatching { appsRepository.load(forceRefresh) }.getOrNull()
                }
                if (loaded != null) apps = loaded
            } finally {
                initialLoading = false
                refreshing = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { loadApps(false) }
    LaunchedEffect(Unit) {
        if (apps.isEmpty()) {
            if (appsRepository.needsAppListPermission()) permissionLauncher.launch(APP_LIST_PERMISSION)
            else loadApps(false)
        }
    }

    val configured = remember(revision) { prefs.configuredForegroundPackages() }
    val excluded = remember(revision) { prefs.foregroundExcludedPackages() }
    val filteredApps = remember(
        apps,
        query,
        showConfiguredApps,
        showUnconfiguredApps,
        showSystemApps,
        selectedTab,
        revision,
    ) {
        val normalized = query.trim().lowercase()
        apps.asSequence()
            .filter { app ->
                val isConfigured = if (selectedTab == 0) app.packageName in configured
                else app.packageName in excluded
                (isConfigured && showConfiguredApps || !isConfigured && showUnconfiguredApps) &&
                    (showSystemApps || !app.isSystem)
            }
            .filter {
                normalized.isEmpty() || it.appName.lowercase().contains(normalized) ||
                    it.packageName.lowercase().contains(normalized)
            }
            .sortedWith(
                compareByDescending<InstalledApp> {
                    if (selectedTab == 0) it.packageName in configured else it.packageName in excluded
                }.thenBy { it.appName.lowercase() },
            )
            .toList()
    }

    val menuEntries = listOf(
        DropdownEntry(
            items = listOf(
                DropdownItem(
                    text = stringResource(R.string.restore_default),
                    onClick = {
                        val count = prefs.resetForegroundRules()
                        revision++
                        scope.launch { snackbarState.showSnackbar(resetResult.format(count)) }
                    },
                ),
            ),
        ),
        DropdownEntry(
            items = listOf(
                DropdownItem(
                    text = stringResource(R.string.filter_apps),
                    children = listOf(
                        DropdownItem(
                            text = stringResource(R.string.filter_configured_apps),
                            selected = showConfiguredApps,
                            onClick = { showConfiguredApps = !showConfiguredApps },
                        ),
                        DropdownItem(
                            text = stringResource(R.string.filter_unconfigured_apps),
                            selected = showUnconfiguredApps,
                            onClick = { showUnconfiguredApps = !showUnconfiguredApps },
                        ),
                        DropdownItem(
                            text = stringResource(R.string.filter_system_apps),
                            selected = showSystemApps,
                            onClick = { showSystemApps = !showSystemApps },
                        ),
                    ),
                ),
            ),
        ),
    )

    val filterItems: LazyListScope.() -> Unit = {
        item {
            SectionTitle(
                stringResource(
                    if (selectedTab == 0) R.string.filter_foreground_description
                    else R.string.filter_exclusion_description,
                ),
            )
        }
        if (initialLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (filteredApps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(
                            if (query.isEmpty()) R.string.no_apps_found
                            else R.string.no_matching_apps,
                        ),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        } else {
            items(filteredApps, key = { it.packageName }) { app ->
                val icon by produceState<ImageBitmap?>(null, app.packageName) {
                    value = withContext(Dispatchers.IO) {
                        appsRepository.loadIcon(app.packageName)
                    }
                }
                FilterAppRow(
                    app = app,
                    icon = icon,
                    exclusionMode = selectedTab == 1,
                    action = prefs.foregroundAction(app.packageName),
                    excluded = app.packageName in excluded,
                    onActionChange = { action ->
                        prefs.setForegroundAction(app.packageName, action)
                        revision++
                    },
                    onExcludedChange = { value ->
                        prefs.setForegroundExcluded(app.packageName, value)
                        revision++
                    },
                )
            }
        }
    }

    BarBlurHost {
        Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                PageTopAppBar(
                    title = stringResource(R.string.filter_rules),
                    largeTitle = stringResource(R.string.filter_rules),
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        PageTopBarIconButton(onClick = onBack) {
                            Icon(MiuixIcons.Back, stringResource(R.string.back))
                        }
                    },
                    actions = {
                        PageTopBarIconButton(
                            onClick = { menuExpanded.value = true },
                            modifier = Modifier.glassPopupAnchor(
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PageSearchField(
                                label = stringResource(R.string.search_apps),
                                query = query,
                                onQueryChange = { query = it },
                            )
                            PageTabRow(
                                tabs = listOf(
                                    stringResource(R.string.filter_foreground_tab),
                                    stringResource(R.string.filter_exclusion_tab),
                                ),
                                selectedIndex = selectedTab,
                                onSelect = { selectedTab = it },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarState) },
        ) { padding ->
            BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                PullToRefresh(
                    isRefreshing = refreshing,
                    onRefresh = { loadApps(true) },
                    topAppBarScrollBehavior = scrollBehavior,
                    contentPadding = PaddingValues(top = padding.calculateTopPadding()),
                    modifier = Modifier.fillMaxSize(),
                    refreshTexts = listOf("", "", "", ""),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = padding.calculateTopPadding(),
                            end = 12.dp,
                            bottom = 28.dp + LocalRootBottomBarPadding.current,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        filterItems()
                    }
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
            entries = menuEntries,
            backdrop = uiGlassBackdrop(),
            visuals = uiGlassPopupVisuals(),
        )
        }
    }
}

@Composable
private fun FilterAppRow(
    app: InstalledApp,
    icon: ImageBitmap?,
    exclusionMode: Boolean,
    action: String,
    excluded: Boolean,
    onActionChange: (String) -> Unit,
    onExcludedChange: (Boolean) -> Unit,
) {
    val startAction: (@Composable () -> Unit)? = icon?.let { bitmap ->
        {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.padding(end = 14.dp).size(42.dp),
            )
        }
    }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        if (exclusionMode) {
            FilterAppPreference(
                app = app,
                startAction = startAction,
                role = Role.Switch,
                onClick = { onExcludedChange(!excluded) },
                endActions = {
                    Switch(
                        checked = excluded,
                        onCheckedChange = onExcludedChange,
                    )
                },
            )
        } else {
            val actions = listOf("default", "small_only", "expand", "suppress")
            FilterActionPreference(
                app = app,
                labels = listOf(
                    stringResource(R.string.filter_action_default),
                    stringResource(R.string.filter_action_small_only),
                    stringResource(R.string.filter_action_expand),
                    stringResource(R.string.filter_action_suppress),
                ),
                selectedIndex = actions.indexOf(action).coerceAtLeast(0),
                startAction = startAction,
                onSelectedIndexChange = { onActionChange(actions[it]) },
            )
        }
    }
}

@Composable
private fun FilterAppPreference(
    app: InstalledApp,
    startAction: (@Composable () -> Unit)?,
    role: Role,
    onClick: () -> Unit,
    endActions: @Composable RowScope.() -> Unit,
) {
    BasicComponent(
        startAction = startAction,
        endActions = endActions,
        insideMargin = ROW_MARGIN,
        onClick = onClick,
        role = role,
    ) {
        AppIdentityText(app)
    }
}

@Composable
private fun FilterActionPreference(
    app: InstalledApp,
    labels: List<String>,
    selectedIndex: Int,
    startAction: (@Composable () -> Unit)?,
    onSelectedIndexChange: (Int) -> Unit,
) {
    val expanded = remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val anchor = rememberGlassPopupAnchor()
    val hapticFeedback = LocalHapticFeedback.current
    val actionColor = MiuixTheme.colorScheme.onSurfaceVariantActions

    BasicComponent(
        modifier = Modifier.onGloballyPositioned { bounds = it.boundsInWindow() },
        interactionSource = remember { MutableInteractionSource() },
        startAction = startAction,
        endActions = {
            Row(
                modifier = Modifier.glassPopupAnchorValue(anchor),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = labels[selectedIndex],
                    modifier = Modifier.padding(end = 8.dp).weight(1f, fill = false),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = actionColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DropdownArrowEndAction(actionColor = actionColor)
            }
        },
        insideMargin = ROW_MARGIN,
        onClick = {
            expanded.value = !expanded.value
            if (expanded.value) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
            }
        },
        role = Role.DropdownList,
    ) {
        AppIdentityText(app)
    }

    GlassOptionPopup(expanded, bounds, anchor) {
        labels.forEachIndexed { index, label ->
            GlassPopupItem(
                text = label,
                selected = index == selectedIndex,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    expanded.value = false
                    onSelectedIndexChange(index)
                },
            )
        }
    }
}

@Composable
private fun AppIdentityText(app: InstalledApp) {
    Text(
        text = app.appName,
        fontSize = MiuixTheme.textStyles.headline1.fontSize,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Text(
        text = app.packageName,
        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private val ROW_MARGIN = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
private const val APP_LIST_PERMISSION = "com.android.permission.GET_INSTALLED_APPS"
