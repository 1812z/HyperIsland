package io.github.hyperisland.compose.page.apps.channel

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.NotificationChannelsRepository
import io.github.hyperisland.compose.data.channel.BatchChannelTarget
import io.github.hyperisland.compose.data.channel.ChannelSettingsPatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun BatchChannelSettingsPage(
    target: BatchChannelTarget,
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
) {
    var patch by remember(target) { mutableStateOf(ChannelSettingsPatch()) }
    var applying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val defaults = remember { prefs.defaultConfigSettings() }
    val targetCount = when (target) {
        is BatchChannelTarget.Channels -> target.channelIds.size
        is BatchChannelTarget.Apps -> target.packageNames.size
    }
    val headerText = stringResource(
        when (target) {
            is BatchChannelTarget.Channels -> R.string.compose_batch_channel_scope_channels
            is BatchChannelTarget.Apps -> R.string.compose_batch_channel_scope_apps
        },
        targetCount,
    )

    fun applyPatch() {
        if (!patch.hasChanges || applying) return
        applying = true
        scope.launch {
            withContext(Dispatchers.IO) {
                when (target) {
                    is BatchChannelTarget.Channels -> prefs.applyChannelSettingsPatch(
                        target.packageName,
                        target.channelIds,
                        patch,
                    )

                    is BatchChannelTarget.Apps -> {
                        val channelRepository = NotificationChannelsRepository()
                        target.packageNames.forEach { packageName ->
                            val channels = channelRepository.load(packageName).orEmpty()
                            val enabled = prefs.enabledChannelIds(packageName)
                            val channelIds = if (enabled.isEmpty()) {
                                channels.map { it.id }
                            } else {
                                channels.map { it.id }.filter { it in enabled }
                            }
                            prefs.applyChannelSettingsPatch(packageName, channelIds, patch)
                        }
                    }
                }
            }
            applying = false
            onBack()
        }
    }

    ChannelSettingsFormPage(
        title = stringResource(R.string.compose_batch_channel_settings),
        state = patch,
        defaults = defaults,
        mode = ChannelSettingsFormMode.Batch,
        onStateChange = { patch = it },
        onBack = onBack,
        headerText = headerText,
        footer = {
            Button(
                onClick = ::applyPatch,
                modifier = Modifier.fillMaxWidth(),
                enabled = patch.hasChanges && !applying,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(if (applying) R.string.compose_applying else R.string.compose_apply))
            }
        },
    )
}
