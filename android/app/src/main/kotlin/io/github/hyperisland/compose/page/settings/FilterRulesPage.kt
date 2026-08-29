package io.github.hyperisland.compose.page.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.BarBackdropContent
import io.github.hyperisland.compose.component.BarBlurHost
import io.github.hyperisland.compose.component.BlurredBar
import io.github.hyperisland.compose.component.LocalBarBlurEnabled
import io.github.hyperisland.compose.component.LocalRootBottomBarPadding
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.InstalledApp
import io.github.hyperisland.compose.data.InstalledAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
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
    val snackbarState = remember { SnackbarHostState() }
    var apps by remember { mutableStateOf(appsRepository.cachedApps()) }
    var initialLoading by remember { mutableStateOf(apps.isEmpty()) }
    var refreshing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSystemApps by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }

    val presetResult = stringResource(R.string.compose_filter_preset_result)
    val resetResult = stringResource(R.string.compose_filter_reset_result)

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
    val filteredApps = remember(apps, query, showSystemApps, selectedTab, revision) {
        val normalized = query.trim().lowercase()
        apps.asSequence()
            .filter { app ->
                val isConfigured = if (selectedTab == 0) app.packageName in configured
                else app.packageName in excluded
                showSystemApps || !app.isSystem || isConfigured
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

    val menuEntry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = stringResource(R.string.compose_filter_preset_games),
                onClick = {
                    scope.launch {
                        val gamePackages = withContext(Dispatchers.IO) {
                            apps.map { it.packageName }.filter(appsRepository::isGame)
                        }
                        val count = prefs.applyForegroundPreset(gamePackages)
                        revision++
                        snackbarState.showSnackbar(presetResult.format(count))
                    }
                },
                icon = { modifier -> Icon(MiuixIcons.GridView, null, modifier) },
            ),
            DropdownItem(
                text = stringResource(
                    if (showSystemApps) R.string.compose_hide_system_apps
                    else R.string.compose_show_system_apps,
                ),
                selected = showSystemApps,
                onClick = { showSystemApps = !showSystemApps },
                icon = { modifier -> Icon(MiuixIcons.Filter, null, modifier) },
            ),
            DropdownItem(
                text = stringResource(R.string.compose_refresh_list),
                onClick = { loadApps(true) },
                icon = { modifier -> Icon(MiuixIcons.Refresh, null, modifier) },
            ),
            DropdownItem(
                text = stringResource(R.string.compose_restore_default),
                onClick = {
                    val count = prefs.resetForegroundRules()
                    revision++
                    scope.launch { snackbarState.showSnackbar(resetResult.format(count)) }
                },
                icon = { modifier -> Icon(MiuixIcons.Refresh, null, modifier) },
            ),
        ),
    )

    BarBlurHost(enabled = LocalBarBlurEnabled.current) {
        Scaffold(
            topBar = {
                BlurredBar(topGradient = true) {
                    TopAppBar(
                        title = stringResource(R.string.compose_filter_rules),
                        largeTitle = stringResource(R.string.compose_filter_rules),
                        color = Color.Transparent,
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(MiuixIcons.Back, stringResource(R.string.compose_back))
                            }
                        },
                        actions = {
                            OverlayIconDropdownMenu(entry = menuEntry) {
                                Icon(MiuixIcons.More, stringResource(R.string.compose_list_actions))
                            }
                        },
                    )
                }
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
                        item {
                            TabRow(
                                tabs = listOf(
                                    stringResource(R.string.compose_filter_foreground_tab),
                                    stringResource(R.string.compose_filter_exclusion_tab),
                                ),
                                selectedTabIndex = selectedTab,
                                onTabSelected = { selectedTab = it },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                        item {
                            SectionTitle(
                                stringResource(
                                    if (selectedTab == 0) R.string.compose_filter_foreground_description
                                    else R.string.compose_filter_exclusion_description,
                                ),
                            )
                        }
                        item {
                            SearchBar(
                                inputField = {
                                    InputField(
                                        query = query,
                                        onQueryChange = { query = it },
                                        onSearch = {},
                                        expanded = searchExpanded,
                                        onExpandedChange = { searchExpanded = it },
                                        label = stringResource(R.string.compose_search_apps),
                                    )
                                },
                                onExpandedChange = { searchExpanded = it },
                                expanded = searchExpanded,
                                outsideEndAction = {
                                    TextButton(
                                        text = stringResource(R.string.compose_cancel),
                                        onClick = { searchExpanded = false },
                                    )
                                },
                            ) {}
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
                                            if (query.isEmpty()) R.string.compose_no_apps_found
                                            else R.string.compose_no_matching_apps,
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
                }
            }
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
    Card(modifier = Modifier.fillMaxWidth()) {
        if (exclusionMode) {
            SwitchPreference(
                checked = excluded,
                onCheckedChange = onExcludedChange,
                title = app.appName,
                summary = app.packageName,
                startAction = startAction,
                insideMargin = ROW_MARGIN,
            )
        } else {
            val actions = listOf("default", "small_only", "expand", "suppress")
            WindowDropdownPreference(
                title = app.appName,
                summary = app.packageName,
                items = listOf(
                    stringResource(R.string.compose_filter_action_default),
                    stringResource(R.string.compose_filter_action_small_only),
                    stringResource(R.string.compose_filter_action_expand),
                    stringResource(R.string.compose_filter_action_suppress),
                ),
                selectedIndex = actions.indexOf(action).coerceAtLeast(0),
                startAction = startAction,
                insideMargin = ROW_MARGIN,
                onSelectedIndexChange = { onActionChange(actions[it]) },
            )
        }
    }
}

private val ROW_MARGIN = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
private const val APP_LIST_PERMISSION = "com.android.permission.GET_INSTALLED_APPS"
