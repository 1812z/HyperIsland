package io.github.hyperisland.ui.blacklist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BlacklistViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BlacklistRepository(app)

    private val _uiState = MutableStateFlow(BlacklistUiState())
    val uiState: StateFlow<BlacklistUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val blacklisted = repo.loadBlacklistedPackages()
            runCatching { repo.loadApps() }
                .onSuccess { apps ->
                    _uiState.update {
                        it.copy(loading = false, apps = apps, blacklistedPackages = blacklisted)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(loading = false, blacklistedPackages = blacklisted, error = e.message ?: "加载失败")
                    }
                }
        }
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun setShowSystemApps(value: Boolean) {
        _uiState.update { it.copy(showSystemApps = value) }
    }

    fun setBlacklisted(packageName: String, enabled: Boolean) {
        val next = _uiState.value.blacklistedPackages.toMutableSet().apply {
            if (enabled) add(packageName) else remove(packageName)
        }.toSet()
        repo.saveBlacklistedPackages(next)
        _uiState.update { it.copy(blacklistedPackages = next) }
    }

    fun enableAllVisible() {
        val visible = currentVisibleApps().map { it.packageName }
        val next = _uiState.value.blacklistedPackages.toMutableSet().apply { addAll(visible) }.toSet()
        repo.saveBlacklistedPackages(next)
        _uiState.update { it.copy(blacklistedPackages = next) }
    }

    fun disableAllVisible() {
        val visible = currentVisibleApps().map { it.packageName }.toSet()
        val next = _uiState.value.blacklistedPackages.toMutableSet().apply { removeAll(visible) }.toSet()
        repo.saveBlacklistedPackages(next)
        _uiState.update { it.copy(blacklistedPackages = next) }
    }

    fun applyGamePreset() {
        val state = _uiState.value
        val (next, added) = repo.applyGamePreset(state.apps, state.blacklistedPackages)
        if (added > 0) {
            repo.saveBlacklistedPackages(next)
            _uiState.update { it.copy(blacklistedPackages = next) }
        }
        viewModelScope.launch {
            _events.emit("已新增 $added 个游戏到黑名单")
        }
    }

    private fun currentVisibleApps() = _uiState.value.apps.filter { app ->
        val state = _uiState.value
        val matchSystem = state.showSystemApps || !app.isSystem || state.blacklistedPackages.contains(app.packageName)
        val q = state.query.trim().lowercase()
        val matchQuery = q.isBlank() || app.appName.lowercase().contains(q) || app.packageName.lowercase().contains(q)
        matchSystem && matchQuery
    }
}
