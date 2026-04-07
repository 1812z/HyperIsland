package io.github.hyperisland.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.hyperisland.data.config.ConfigIoManager
import io.github.hyperisland.data.prefs.PrefKeys
import io.github.hyperisland.data.prefs.SettingsRepository
import io.github.hyperisland.data.prefs.SettingsState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)
    private val configIo = ConfigIoManager(app)

    private val _uiState = MutableStateFlow(SettingsState())
    val uiState: StateFlow<SettingsState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        reload()
    }

    fun reload() {
        _uiState.value = repo.load()
    }

    fun updateSwitch(key: String, value: Boolean) {
        repo.setBoolean(key, value)
        _uiState.update {
            when (key) {
                PrefKeys.SHOW_WELCOME -> it.copy(showWelcome = value)
                PrefKeys.RESUME_NOTIFICATION -> it.copy(resumeNotification = value)
                PrefKeys.USE_HOOK_APP_ICON -> it.copy(useHookAppIcon = value)
                PrefKeys.INTERACTION_HAPTICS -> it.copy(interactionHaptics = value)
                PrefKeys.CHECK_UPDATE_ON_LAUNCH -> it.copy(checkUpdateOnLaunch = value)
                PrefKeys.ROUND_ICON -> it.copy(roundIcon = value)
                PrefKeys.MARQUEE_FEATURE -> it.copy(marqueeFeature = value)
                PrefKeys.BIG_ISLAND_MAX_WIDTH_ENABLED -> it.copy(bigIslandMaxWidthEnabled = value)
                PrefKeys.UNLOCK_ALL_FOCUS -> it.copy(unlockAllFocus = value)
                PrefKeys.UNLOCK_FOCUS_AUTH -> it.copy(unlockFocusAuth = value)
                PrefKeys.DEFAULT_FIRST_FLOAT -> it.copy(defaultFirstFloat = value)
                PrefKeys.DEFAULT_ENABLE_FLOAT -> it.copy(defaultEnableFloat = value)
                PrefKeys.DEFAULT_SHOW_ISLAND_ICON -> it.copy(defaultShowIslandIcon = value)
                PrefKeys.DEFAULT_MARQUEE -> it.copy(defaultMarquee = value)
                PrefKeys.DEFAULT_FOCUS_NOTIF -> it.copy(defaultFocusNotif = value)
                PrefKeys.DEFAULT_PRESERVE_SMALL_ICON -> it.copy(defaultPreserveSmallIcon = value)
                PrefKeys.DEFAULT_RESTORE_LOCKSCREEN -> it.copy(defaultRestoreLockscreen = value)
                else -> it
            }
        }
    }

    fun updateThemeMode(value: String) {
        repo.setString(PrefKeys.THEME_MODE, value)
        _uiState.update { it.copy(themeMode = value) }
    }

    fun updateLocale(value: String?) {
        repo.setString(PrefKeys.LOCALE, value)
        _uiState.update { it.copy(locale = value) }
    }

    fun updateMarqueeSpeed(value: Int) {
        repo.setMarqueeSpeed(value)
        _uiState.update { it.copy(marqueeSpeed = value.coerceIn(20, 500)) }
    }

    fun updateBigIslandMaxWidth(value: Int) {
        repo.setBigIslandMaxWidth(value)
        _uiState.update { it.copy(bigIslandMaxWidth = value.coerceIn(500, 1000)) }
    }

    fun setDesktopIconHidden(hidden: Boolean) {
        viewModelScope.launch {
            val result = runCatching {
                repo.setDesktopIconHidden(hidden)
            }
            if (result.isSuccess) {
                _uiState.update { it.copy(hideDesktopIcon = hidden) }
            } else {
                _events.emit("桌面图标设置失败: ${result.exceptionOrNull()?.message ?: "未知错误"}")
            }
        }
    }

    fun exportConfigToFile() {
        viewModelScope.launch {
            runCatching { configIo.exportToFile() }
                .onSuccess { _events.emit("配置已导出到: $it") }
                .onFailure { _events.emit("导出失败: ${it.message}") }
        }
    }

    fun importConfigFromFile() {
        viewModelScope.launch {
            runCatching { configIo.importFromFile() }
                .onSuccess {
                    reload()
                    _events.emit("配置导入成功，条目数: $it")
                }
                .onFailure { _events.emit("导入失败: ${it.message}") }
        }
    }

    fun importConfigFromUri(uri: Uri) {
        viewModelScope.launch {
            runCatching { configIo.importFromUri(uri) }
                .onSuccess {
                    reload()
                    _events.emit("文件导入成功，条目数: $it")
                }
                .onFailure { _events.emit("文件导入失败: ${it.message}") }
        }
    }

    fun exportConfigToClipboard() {
        viewModelScope.launch {
            runCatching { configIo.exportToClipboard() }
                .onSuccess { _events.emit("配置已复制到剪贴板") }
                .onFailure { _events.emit("复制失败: ${it.message}") }
        }
    }

    fun importConfigFromClipboard() {
        viewModelScope.launch {
            runCatching { configIo.importFromClipboard() }
                .onSuccess {
                    reload()
                    _events.emit("剪贴板导入成功，条目数: $it")
                }
                .onFailure { _events.emit("剪贴板导入失败: ${it.message}") }
        }
    }
}
