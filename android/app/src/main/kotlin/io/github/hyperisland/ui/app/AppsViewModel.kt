package io.github.hyperisland.ui.app

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

class AppsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppAdaptationRepository(app)

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val enabled = repo.loadEnabledPackages()
            runCatching {
                repo.loadInstalledApps()
            }.onSuccess { apps ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        apps = apps,
                        enabledPackages = enabled,
                    )
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

    fun setQuery(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun setShowSystemApps(value: Boolean) {
        _uiState.update { it.copy(showSystemApps = value) }
    }

    fun setEnabled(packageName: String, enabled: Boolean) {
        val next = _uiState.value.enabledPackages.toMutableSet().apply {
            if (enabled) add(packageName) else remove(packageName)
        }.toSet()
        repo.setEnabledPackages(next)
        _uiState.update { it.copy(enabledPackages = next) }
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
}
