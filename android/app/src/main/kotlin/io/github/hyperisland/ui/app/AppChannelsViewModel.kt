package io.github.hyperisland.ui.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppChannelsViewModel(
    app: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(app) {
    private val repo = AppAdaptationRepository(app)
    private var packageName: String = savedStateHandle["packageName"] ?: ""
    private var refreshJob: Job? = null

    private val _uiState = MutableStateFlow(AppChannelsUiState(packageName = packageName))
    val uiState: StateFlow<AppChannelsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun setPackageName(value: String) {
        if (value.isBlank() || value == packageName) return
        packageName = value
        savedStateHandle["packageName"] = value
        _uiState.value = AppChannelsUiState(packageName = value, loading = true)
        refresh()
    }

    fun refresh() {
        if (packageName.isBlank()) {
            _uiState.update { it.copy(loading = false, error = "包名为空") }
            return
        }
        val currentPackage = packageName
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val channelsResult = runCatching { repo.loadChannels(currentPackage) }
            val channels = channelsResult.getOrNull()
            if (channels == null) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        packageName = currentPackage,
                        error = "无法读取通知渠道，请确认 Root 权限",
                    )
                }
                return@launch
            }

            val enabled = repo.getEnabledChannels(currentPackage)
            val appItem = repo.loadAppItem(currentPackage)
            val appEnabled = repo.isAppEnabled(currentPackage)
            val templateMap = channels.associate { ch ->
                ch.id to repo.getChannelTemplate(currentPackage, ch.id)
            }
            val timeoutMap = channels.associate { ch ->
                ch.id to repo.getChannelTimeout(currentPackage, ch.id)
            }
            val extrasMap = channels.associate { ch ->
                ch.id to repo.getChannelExtras(currentPackage, ch.id)
            }
            if (currentPackage != packageName) return@launch
            _uiState.update {
                it.copy(
                    packageName = currentPackage,
                    loading = false,
                    appName = appItem?.appName ?: currentPackage,
                    appIcon = appItem?.icon ?: byteArrayOf(),
                    appEnabled = appEnabled,
                    channels = channels,
                    enabledChannels = enabled,
                    channelTemplates = templateMap,
                    channelTimeout = timeoutMap,
                    channelExtras = extrasMap,
                )
            }
        }
    }

    fun setAppEnabled(enabled: Boolean) {
        repo.setAppEnabled(packageName, enabled)
        _uiState.update { it.copy(appEnabled = enabled) }
    }

    fun toggleChannel(channelId: String, value: Boolean) {
        val all = _uiState.value.channels.map { it.id }
        val current = _uiState.value.enabledChannels

        val next = if (current.isEmpty()) {
            if (value) return
            all.filter { it != channelId }.toSet()
        } else {
            current.toMutableSet().apply {
                if (value) add(channelId) else remove(channelId)
            }.let { set ->
                if (set.size == all.size) emptySet() else set
            }
        }

        repo.setEnabledChannels(packageName, next)
        _uiState.update { it.copy(enabledChannels = next) }
    }

    fun enableAllChannels() {
        repo.setEnabledChannels(packageName, emptySet())
        _uiState.update { it.copy(enabledChannels = emptySet()) }
    }

    fun setTemplate(channelId: String, template: String) {
        repo.setChannelTemplate(packageName, channelId, template)
        _uiState.update { it.copy(channelTemplates = it.channelTemplates + (channelId to template)) }
    }

    fun setTimeout(channelId: String, timeout: String) {
        val normalized = timeout.toIntOrNull()?.coerceIn(1, 30)?.toString() ?: "5"
        repo.setChannelTimeout(packageName, channelId, normalized)
        _uiState.update { it.copy(channelTimeout = it.channelTimeout + (channelId to normalized)) }
    }

    fun setSetting(channelId: String, setting: String, value: String) {
        val current = _uiState.value.channelExtras[channelId] ?: return
        val next = when (setting) {
            "icon" -> current.copy(icon = value)
            "focus" -> {
                if (value == "off") {
                    repo.setChannelSetting(packageName, channelId, "preserve_small_icon", "off")
                    current.copy(focus = value, preserveSmallIcon = "off")
                } else {
                    current.copy(focus = value)
                }
            }
            "preserve_small_icon" -> current.copy(preserveSmallIcon = value)
            "show_island_icon" -> current.copy(showIslandIcon = value)
            "first_float" -> current.copy(firstFloat = value)
            "enable_float" -> current.copy(enableFloat = value)
            "marquee" -> current.copy(marquee = value)
            "renderer" -> current.copy(renderer = value)
            "restore_lockscreen" -> current.copy(restoreLockscreen = value)
            "dynamic_highlight_color" -> current.copy(dynamicHighlightColor = value)
            "show_left_highlight" -> current.copy(showLeftHighlight = value)
            "show_right_highlight" -> current.copy(showRightHighlight = value)
            "show_left_narrow_font" -> current.copy(showLeftNarrowFont = value)
            "show_right_narrow_font" -> current.copy(showRightNarrowFont = value)
            "outer_glow" -> current.copy(outerGlow = value)
            "out_effect_color" -> current.copy(outEffectColor = value)
            "focus_custom" -> current.copy(focusCustom = value)
            "island_custom" -> current.copy(islandCustom = value)
            else -> return
        }
        repo.setChannelSetting(packageName, channelId, setting, value)
        _uiState.update { it.copy(channelExtras = it.channelExtras + (channelId to next)) }
    }

    fun setHighlightColor(channelId: String, color: String) {
        repo.setChannelSetting(packageName, channelId, "highlight_color", color.trim())
        val current = _uiState.value.channelExtras[channelId] ?: return
        _uiState.update {
            it.copy(channelExtras = it.channelExtras + (channelId to current.copy(highlightColor = color.trim())))
        }
    }

    fun batchApplyToEnabledChannels(settings: Map<String, String>) {
        val state = _uiState.value
        val ids = if (state.enabledChannels.isEmpty()) {
            state.channels.map { it.id }
        } else {
            state.enabledChannels.toList()
        }
        repo.batchApplyChannelSettings(packageName, ids, settings)
        refresh()
    }
}
