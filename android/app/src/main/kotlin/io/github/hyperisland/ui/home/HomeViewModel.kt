package io.github.hyperisland.ui.home

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.hyperisland.HyperIslandApp
import io.github.hyperisland.HyperIslandHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val ready = HyperIslandApp.awaitReady()
            val apiVersion = if (ready) HyperIslandApp.getApiVersion() else 0
            val active = ready && apiVersion >= 101
            val focusProtocol = Settings.System.getInt(
                getApplication<Application>().contentResolver,
                "notification_focus_protocol",
                0,
            )
            _uiState.update {
                it.copy(
                    moduleActive = active,
                    lsposedApiVersion = apiVersion,
                    focusProtocolVersion = focusProtocol,
                )
            }
        }
    }

    fun sendTest() {
        viewModelScope.launch {
            runCatching {
                HyperIslandHelper.sendIslandNotification(
                    getApplication(),
                    title = "测试通知",
                    content = "这是一条 HyperIsland 测试通知",
                )
            }.onSuccess {
                _events.emit("已发送测试通知")
            }.onFailure { e ->
                _events.emit("发送失败: ${e.message ?: "未知错误"}")
            }
        }
    }

    fun restartScopes(
        restartSystemUi: Boolean,
        restartDownloadManager: Boolean,
        restartXmsf: Boolean,
    ) {
        val commands = buildList {
            if (restartSystemUi) add("killall com.android.systemui")
            if (restartDownloadManager) add("am force-stop com.android.providers.downloads")
            if (restartXmsf) add("am force-stop com.xiaomi.xmsf")
        }
        if (commands.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(restarting = true) }
            val result = runCatching {
                val process = Runtime.getRuntime().exec("su")
                val writer = process.outputStream.bufferedWriter()
                commands.forEach { cmd ->
                    writer.write(cmd)
                    writer.newLine()
                }
                writer.write("exit")
                writer.newLine()
                writer.flush()
                writer.close()
                process.waitFor()
            }

            _uiState.update { it.copy(restarting = false) }
            if (result.getOrNull() == 0) {
                _events.emit("作用域重启命令已执行")
            } else {
                _events.emit("重启失败，请确认 Root 权限")
            }
        }
    }
}
