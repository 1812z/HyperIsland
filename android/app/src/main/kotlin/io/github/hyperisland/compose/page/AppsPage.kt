package io.github.hyperisland.compose.page

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.BarBackdropContent
import io.github.hyperisland.compose.component.BarBlurHost
import io.github.hyperisland.compose.component.BlurredBar
import io.github.hyperisland.compose.component.LocalBarBlurEnabled
import io.github.hyperisland.compose.component.LocalRootBottomBarPadding
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.InstalledApp
import io.github.hyperisland.compose.data.InstalledAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppsPage(
    prefs: FlutterPrefsRepository,
    onOpenChannels: (InstalledApp) -> Unit,
    onOpenToastSettings: (InstalledApp) -> Unit,
) {
    val context = LocalContext.current
    val appsRepository = remember(context) { InstalledAppsRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    var allApps by remember { mutableStateOf(appsRepository.cachedApps()) }
    var initialLoading by remember { mutableStateOf(allApps.isEmpty()) }
    var refreshing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableIntStateOf(0) }
    var showSystemApps by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPackages by remember { mutableStateOf(emptySet<String>()) }
    var configRevision by remember { mutableIntStateOf(0) }

    fun loadApps(forceRefresh: Boolean) {
        scope.launch {
            if (forceRefresh) refreshing = true else initialLoading = allApps.isEmpty()
            try {
                val loadedApps = withContext(Dispatchers.IO) {
                    runCatching { appsRepository.load(forceRefresh) }.getOrNull()
                }
                if (loadedApps != null) allApps = loadedApps
            } finally {
                initialLoading = false
                refreshing = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        loadApps(forceRefresh = false)
    }
    LaunchedEffect(Unit) {
        if (allApps.isEmpty()) {
            if (appsRepository.needsAppListPermission()) permissionLauncher.launch(APP_LIST_PERMISSION)
            else loadApps(forceRefresh = false)
        }
    }
    DisposableEffect(prefs) {
        val removeListener = prefs.addChangeListener { key ->
            if (key == "pref_generic_whitelist" || key.startsWith("pref_app_config_")) configRevision++
        }
        onDispose(removeListener)
    }

    val enabledPackages = remember(configRevision) { prefs.enabledPackages() }
    val toastEnabledPackages = remember(allApps, configRevision) {
        allApps.asSequence().filter { prefs.isToastEnabled(it.packageName) }.map { it.packageName }.toSet()
    }
    val activeEnabledPackages = if (selectedMode == 0) enabledPackages else toastEnabledPackages
    val filteredApps = remember(allApps, query, showSystemApps, selectedMode, configRevision) {
        val normalizedQuery = query.trim().lowercase()
        allApps.asSequence()
            .filter { showSystemApps || !it.isSystem || it.packageName in activeEnabledPackages }
            .filter {
                normalizedQuery.isEmpty() || it.appName.lowercase().contains(normalizedQuery) ||
                    it.packageName.lowercase().contains(normalizedQuery)
            }
            .sortedWith(compareByDescending<InstalledApp> {
                it.packageName in activeEnabledPackages
            }.thenBy { it.appName.lowercase() })
            .toList()
    }
    val allVisibleSelected = filteredApps.isNotEmpty() && filteredApps.all { it.packageName in selectedPackages }

    fun leaveSelectionMode() {
        selectionMode = false
        selectedPackages = emptySet()
    }

    fun setSelectedEnabled(enabled: Boolean) {
        if (selectedMode == 0) prefs.setAppsEnabled(selectedPackages, enabled)
        else prefs.setToastEnabled(selectedPackages, enabled)
        leaveSelectionMode()
    }

    val normalMenuEntry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = stringResource(R.string.compose_show_system_apps),
                selected = showSystemApps,
                onClick = { showSystemApps = !showSystemApps },
                icon = { modifier -> Icon(MiuixIcons.Filter, null, modifier = modifier) },
            ),
            DropdownItem(
                text = stringResource(R.string.compose_refresh_list),
                onClick = { loadApps(forceRefresh = true) },
                icon = { modifier -> Icon(MiuixIcons.Refresh, null, modifier = modifier) },
            ),
            DropdownItem(
                text = stringResource(R.string.compose_enable_all),
                onClick = {
                    val packages = filteredApps.map { it.packageName }
                    if (selectedMode == 0) prefs.setAppsEnabled(packages, true)
                    else prefs.setToastEnabled(packages, true)
                },
                icon = { modifier -> Icon(MiuixIcons.SelectAll, null, modifier = modifier) },
            ),
            DropdownItem(
                text = stringResource(R.string.compose_disable_all),
                onClick = {
                    val packages = filteredApps.map { it.packageName }
                    if (selectedMode == 0) prefs.setAppsEnabled(packages, false)
                    else prefs.setToastEnabled(packages, false)
                },
                icon = { modifier -> Icon(MiuixIcons.Blocklist, null, modifier = modifier) },
            ),
        ),
    )
    val selectionMenuEntry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = stringResource(R.string.compose_select_enabled_apps),
                onClick = { selectedPackages = filteredApps.map { it.packageName }.filter { it in activeEnabledPackages }.toSet() },
                icon = { modifier -> Icon(MiuixIcons.SelectAll, null, modifier = modifier) },
            ),
            DropdownItem(
                text = stringResource(R.string.compose_batch_enable),
                enabled = selectedPackages.isNotEmpty(),
                onClick = { setSelectedEnabled(true) },
                icon = { modifier -> Icon(MiuixIcons.SelectAll, null, modifier = modifier) },
            ),
            DropdownItem(
                text = stringResource(R.string.compose_batch_disable),
                enabled = selectedPackages.isNotEmpty(),
                onClick = { setSelectedEnabled(false) },
                icon = { modifier -> Icon(MiuixIcons.Blocklist, null, modifier = modifier) },
            ),
        ),
    )

    val pageBlurEnabled = LocalBarBlurEnabled.current
    BarBlurHost(enabled = pageBlurEnabled) {
        Scaffold(
        topBar = {
            BlurredBar(topGradient = true) {
                TopAppBar(
                title = if (selectionMode) {
                    stringResource(R.string.compose_selected_count, selectedPackages.size)
                } else {
                    stringResource(R.string.compose_app_adaptation)
                },
                largeTitle = if (selectionMode) {
                    stringResource(R.string.compose_selected_count, selectedPackages.size)
                } else {
                    stringResource(R.string.compose_app_adaptation)
                },
                color = Color.Transparent,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (selectionMode) {
                        IconButton(onClick = ::leaveSelectionMode) {
                            Icon(MiuixIcons.Close, stringResource(R.string.compose_cancel_selection))
                        }
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(
                            onClick = {
                                selectedPackages = if (allVisibleSelected) {
                                    selectedPackages - filteredApps.map { it.packageName }.toSet()
                                } else {
                                    selectedPackages + filteredApps.map { it.packageName }
                                }
                            },
                        ) {
                            Icon(
                                MiuixIcons.SelectAll,
                                stringResource(
                                    if (allVisibleSelected) R.string.compose_deselect_all else R.string.compose_select_all,
                                ),
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                selectionMode = true
                                selectedPackages = emptySet()
                            },
                            enabled = !initialLoading && !refreshing,
                        ) {
                            Icon(MiuixIcons.SelectAll, stringResource(R.string.compose_multi_select))
                        }
                    }
                    OverlayIconDropdownMenu(
                        entry = if (selectionMode) selectionMenuEntry else normalMenuEntry,
                    ) {
                        Icon(MiuixIcons.More, stringResource(R.string.compose_list_actions))
                    }
                },
            )
            }
        },
        ) { padding ->
            BarBackdropContent(modifier = Modifier.fillMaxSize()) {
                PullToRefresh(
            isRefreshing = refreshing,
            onRefresh = { loadApps(forceRefresh = true) },
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
                item {
                    TabRow(
                        tabs = listOf(
                            stringResource(R.string.compose_notification_mode),
                            stringResource(R.string.compose_toast_mode),
                        ),
                        selectedTabIndex = selectedMode,
                        onTabSelected = {
                            selectedMode = it
                            leaveSelectionMode()
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                if (!initialLoading && filteredApps.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (query.isEmpty()) stringResource(R.string.compose_no_apps_found)
                                else stringResource(R.string.compose_no_matching_apps),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                } else {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val enabled = if (selectedMode == 0) {
                            app.packageName in enabledPackages
                        } else {
                            app.packageName in toastEnabledPackages
                        }
                        val icon by produceState<ImageBitmap?>(null, app.packageName) {
                            value = withContext(Dispatchers.IO) { appsRepository.loadIcon(app.packageName) }
                        }
                        AppRow(
                            app = app,
                            enabled = enabled,
                            icon = icon,
                            selectionMode = selectionMode,
                            selected = app.packageName in selectedPackages,
                            onEnabledChange = { value ->
                                if (selectedMode == 0) prefs.setAppEnabled(app.packageName, value)
                                else prefs.setToastEnabled(app.packageName, value)
                            },
                            onClick = {
                                if (selectionMode) {
                                    selectedPackages = if (app.packageName in selectedPackages) {
                                        selectedPackages - app.packageName
                                    } else {
                                        selectedPackages + app.packageName
                                    }
                                } else {
                                    if (selectedMode == 0) {
                                        onOpenChannels(app)
                                    } else {
                                        onOpenToastSettings(app)
                                    }
                                }
                            },
                            onLongPress = {
                                selectionMode = true
                                selectedPackages = selectedPackages + app.packageName
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
private fun AppRow(
    app: InstalledApp,
    enabled: Boolean,
    icon: ImageBitmap?,
    selectionMode: Boolean,
    selected: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), onLongPress = onLongPress) {
        BasicComponent(
            startAction = icon?.let { bitmap ->
                {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 14.dp).size(42.dp),
                    )
                }
            },
            endActions = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectionMode) {
                        Checkbox(
                            state = if (selected) ToggleableState.On else ToggleableState.Off,
                            onClick = onClick,
                        )
                    } else {
                        Switch(checked = enabled, onCheckedChange = onEnabledChange)
                        Icon(
                            imageVector = MiuixIcons.ChevronForward,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            modifier = Modifier.padding(start = 8.dp).size(18.dp),
                        )
                    }
                }
            },
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            onClick = onClick,
        ) {
            Text(
                text = app.appName,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
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
    }
}

private const val APP_LIST_PERMISSION = "com.android.permission.GET_INSTALLED_APPS"
