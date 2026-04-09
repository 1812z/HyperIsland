package io.github.hyperisland.ui.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

class AppsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppAdaptationRepository(app)
    private var iconLoadJob: Job? = null

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        iconLoadJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val enabled = repo.loadEnabledPackages()
            runCatching {
                repo.loadInstalledApps(includeIcons = false)
            }.onSuccess { apps ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        apps = apps,
                        enabledPackages = enabled,
                    )
                }
                updateFilteredApps()
                iconLoadJob = viewModelScope.launch {
                    preloadIconsInBackground(apps)
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "加载应用列表失败",
                        enabledPackages = enabled,
                    )
                }
            }
        }
    }

    private suspend fun preloadIconsInBackground(apps: List<AppItem>) {
        if (apps.isEmpty()) return
        val updated = apps.toMutableList()
        val indexByPackage = apps.mapIndexed { index, app -> app.packageName to index }.toMap()
        val state = _uiState.value
        val prioritizedPackages = filterApps(
            apps = apps,
            query = state.query,
            showSystemApps = state.showSystemApps,
            alwaysVisiblePackages = state.enabledPackages,
            prioritizedPackages = state.enabledPackages,
        ).map { it.packageName }
        val remainingPackages = apps.asSequence()
            .map { it.packageName }
            .filterNot { it in prioritizedPackages }
            .toList()
        val packagesToLoad = prioritizedPackages + remainingPackages

        suspend fun publishBatch(packageNames: List<String>) {
            coroutineContext.ensureActive()
            if (packageNames.isEmpty()) return
            val icons = repo.loadAppIcons(packageNames)
            if (icons.isEmpty()) return
            var changed = false
            icons.forEach { (packageName, icon) ->
                val index = indexByPackage[packageName] ?: return@forEach
                if (!updated[index].icon.contentEquals(icon)) {
                    updated[index] = updated[index].copy(icon = icon)
                    changed = true
                }
            }
            if (changed) {
                val snapshot = updated.toList()
                _uiState.update { current ->
                    if (current.apps.isEmpty()) current else current.copy(apps = snapshot)
                }
                updateFilteredApps()
            }
        }

        publishBatch(packagesToLoad.take(INITIAL_ICON_BATCH_SIZE))
        packagesToLoad
            .drop(INITIAL_ICON_BATCH_SIZE)
            .chunked(ICON_BATCH_SIZE)
            .forEach { batch ->
                publishBatch(batch)
            }
    }

    fun setQuery(value: String) {
        _uiState.update { it.copy(query = value) }
        updateFilteredApps()
    }

    fun setShowSystemApps(value: Boolean) {
        _uiState.update { it.copy(showSystemApps = value) }
        updateFilteredApps()
    }

    private fun updateFilteredApps() {
        _uiState.update { state ->
            val filtered = filterApps(
                apps = state.apps,
                query = state.query,
                showSystemApps = state.showSystemApps,
                alwaysVisiblePackages = state.enabledPackages,
                prioritizedPackages = state.enabledPackages,
            )
            state.copy(filteredApps = filtered)
        }
    }

    fun setEnabled(packageName: String, enabled: Boolean) {
        val next = _uiState.value.enabledPackages.toMutableSet().apply {
            if (enabled) add(packageName) else remove(packageName)
        }.toSet()
        repo.setEnabledPackages(next)
        _uiState.update { it.copy(enabledPackages = next) }
        updateFilteredApps()
    }

    fun batchApplyToAllEnabledApps(settings: Map<String, String>) {
        val enabledCount = _uiState.value.enabledPackages.size
        if (enabledCount == 0) {
            viewModelScope.launch { _events.emit("当前没有已启用应用") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(applying = true) }
            repo.batchApplyToAllEnabledApps(settings) { done, total ->
                viewModelScope.launch {
                    _events.emit("批量进度: $done/$total")
                }
            }
            _uiState.update { it.copy(applying = false) }
            _events.emit("全局批量应用完成（$enabledCount 个应用）")
        }
    }

    fun batchApplyToSelectedApps(packages: Set<String>, settings: Map<String, String>) {
        val selected = packages.filter { it.isNotBlank() }
        if (selected.isEmpty()) {
            viewModelScope.launch { _events.emit("请先选择应用") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(applying = true) }
            val total = selected.size
            selected.forEachIndexed { index, pkg ->
                runCatching {
                    val channels = repo.loadChannels(pkg).orEmpty()
                    if (channels.isEmpty()) return@runCatching
                    val enabledChannels = repo.getEnabledChannels(pkg)
                    val ids = if (enabledChannels.isEmpty()) {
                        channels.map { it.id }
                    } else {
                        enabledChannels.toList()
                    }
                    if (ids.isNotEmpty()) {
                        repo.batchApplyChannelSettings(pkg, ids, settings)
                    }
                }
                _events.emit("批量进度: ${index + 1}/$total")
            }
            _uiState.update { it.copy(applying = false) }
            _events.emit("已对 $total 个已选应用应用渠道配置")
        }
    }

    fun setSelectedPackages(packages: Set<String>) {
        _uiState.update { it.copy(selectedPackages = packages) }
    }

    fun toggleSelectedPackage(packageName: String) {
        _uiState.update { state ->
            val next = state.selectedPackages.toMutableSet().apply {
                if (contains(packageName)) remove(packageName) else add(packageName)
            }
            state.copy(selectedPackages = next)
        }
    }

    fun clearSelectedPackages() {
        _uiState.update { it.copy(selectedPackages = emptySet()) }
    }

    private companion object {
        const val INITIAL_ICON_BATCH_SIZE = 24
        const val ICON_BATCH_SIZE = 48
    }
}
