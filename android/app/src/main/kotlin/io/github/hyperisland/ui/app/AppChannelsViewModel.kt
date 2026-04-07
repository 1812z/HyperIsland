package io.github.hyperisland.ui.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
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

    private val _uiState = MutableStateFlow(AppChannelsUiState(packageName = packageName))
    val uiState: StateFlow<AppChannelsUiState> = _uiState.asStateFlow()

    private val templates = listOf(
        "notification_island",
        "notification_island_lite",
        "download_lite",
        "ai_notification_island",
    )
    private val iconModes = listOf("auto", "notif_small", "notif_large", "app_icon")
    private val triStates = listOf("default", "on", "off")
    private val renderers = listOf(
        "image_text_with_buttons_4",
        "image_text_with_buttons_4_wrap",
        "image_text_with_right_text_button",
    )

    init {
        refresh()
    }

    fun setPackageNameIfEmpty(value: String) {
        if (packageName.isNotBlank() || value.isBlank()) return
        packageName = value
        _uiState.update { it.copy(packageName = value, error = null) }
        refresh()
    }

    fun refresh() {
        if (packageName.isBlank()) {
            _uiState.update { it.copy(loading = false, error = "包名为空") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val channelsResult = runCatching { repo.loadChannels(packageName) }
            val channels = channelsResult.getOrNull()
            if (channels == null) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = "无法读取通知渠道，请确认 Root 权限",
                    )
                }
                return@launch
            }

            val enabled = repo.getEnabledChannels(packageName)
            val templateMap = channels.associate { ch ->
                ch.id to repo.getChannelTemplate(packageName, ch.id)
            }
            val timeoutMap = channels.associate { ch ->
                ch.id to repo.getChannelTimeout(packageName, ch.id)
            }
            val extrasMap = channels.associate { ch ->
                ch.id to repo.getChannelExtras(packageName, ch.id)
            }
            _uiState.update {
                it.copy(
                    loading = false,
                    channels = channels,
                    enabledChannels = enabled,
                    channelTemplates = templateMap,
                    channelTimeout = timeoutMap,
                    channelExtras = extrasMap,
                )
            }
        }
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

    fun cycleTemplate(channelId: String) {
        val current = _uiState.value.channelTemplates[channelId] ?: templates.first()
        val idx = templates.indexOf(current).takeIf { it >= 0 } ?: 0
        val next = templates[(idx + 1) % templates.size]
        repo.setChannelTemplate(packageName, channelId, next)
        _uiState.update { it.copy(channelTemplates = it.channelTemplates + (channelId to next)) }
    }

    fun setTimeout(channelId: String, timeout: String) {
        val normalized = timeout.toIntOrNull()?.coerceIn(1, 30)?.toString() ?: "5"
        repo.setChannelTimeout(packageName, channelId, normalized)
        _uiState.update { it.copy(channelTimeout = it.channelTimeout + (channelId to normalized)) }
    }

    fun cycleSetting(channelId: String, setting: String) {
        val current = _uiState.value.channelExtras[channelId] ?: return
        val next = when (setting) {
            "icon" -> current.copy(icon = nextOf(iconModes, current.icon))
            "focus_icon" -> current.copy(focusIcon = nextOf(iconModes, current.focusIcon))
            "focus" -> current.copy(focus = nextOf(triStates, current.focus))
            "preserve_small_icon" -> current.copy(preserveSmallIcon = nextOf(triStates, current.preserveSmallIcon))
            "show_island_icon" -> current.copy(showIslandIcon = nextOf(triStates, current.showIslandIcon))
            "first_float" -> current.copy(firstFloat = nextOf(triStates, current.firstFloat))
            "enable_float" -> current.copy(enableFloat = nextOf(triStates, current.enableFloat))
            "marquee" -> current.copy(marquee = nextOf(triStates, current.marquee))
            "renderer" -> current.copy(renderer = nextOf(renderers, current.renderer))
            "restore_lockscreen" -> current.copy(restoreLockscreen = nextOf(triStates, current.restoreLockscreen))
            "show_left_highlight" -> current.copy(showLeftHighlight = nextOf(listOf("off", "on"), current.showLeftHighlight))
            "show_right_highlight" -> current.copy(showRightHighlight = nextOf(listOf("off", "on"), current.showRightHighlight))
            else -> return
        }
        val value = when (setting) {
            "icon" -> next.icon
            "focus_icon" -> next.focusIcon
            "focus" -> next.focus
            "preserve_small_icon" -> next.preserveSmallIcon
            "show_island_icon" -> next.showIslandIcon
            "first_float" -> next.firstFloat
            "enable_float" -> next.enableFloat
            "marquee" -> next.marquee
            "renderer" -> next.renderer
            "restore_lockscreen" -> next.restoreLockscreen
            "show_left_highlight" -> next.showLeftHighlight
            "show_right_highlight" -> next.showRightHighlight
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

    private fun nextOf(options: List<String>, current: String): String {
        val idx = options.indexOf(current).takeIf { it >= 0 } ?: 0
        return options[(idx + 1) % options.size]
    }
}
