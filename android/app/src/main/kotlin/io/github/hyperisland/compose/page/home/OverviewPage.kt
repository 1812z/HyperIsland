package io.github.hyperisland.compose.page.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hyperisland.R
import io.github.hyperisland.XposedPrefsSyncApp
import io.github.hyperisland.compose.component.CollapsingPage
import io.github.hyperisland.compose.component.SettingsAction
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.service.HomeSystemInfo
import io.github.hyperisland.compose.service.RestartScopeService
import io.github.hyperisland.compose.service.SystemInfoProvider
import io.github.hyperisland.compose.service.TestNotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.window.WindowDialog

internal data class ModuleState(
    val active: Boolean,
    val serviceConnected: Boolean = false,
    val framework: String = "",
    val frameworkVersion: String = "",
    val frameworkVersionCode: Int = 0,
    val apiVersion: Int = 0,
    val hasSystemUiScope: Boolean = false,
)

@Stable
internal class HomeOverviewState(private val prefs: FlutterPrefsRepository) {
    var status by mutableStateOf<ModuleState?>(null)
        private set
    var systemInfo by mutableStateOf<HomeSystemInfo?>(null)
        private set
    var enabledAppCount by mutableStateOf(prefs.enabledAppCount())
        private set
    var toastEnabledAppCount by mutableStateOf(prefs.toastEnabledAppCount())
        private set

    private val refreshMutex = Mutex()

    fun onPreferenceChanged(key: String) {
        if (key == "pref_generic_whitelist") enabledAppCount = prefs.enabledAppCount()
        if (key.startsWith("pref_app_config_")) {
            toastEnabledAppCount = prefs.toastEnabledAppCount()
        }
    }

    suspend fun refresh(context: Context) = refreshMutex.withLock {
        val refreshed = withContext(Dispatchers.IO) {
            val info = SystemInfoProvider.load(context)
            val connected = XposedPrefsSyncApp.awaitReady()
            if (!connected) return@withContext info to ModuleState(active = false)
            val app = context.applicationContext as XposedPrefsSyncApp
            val frameworkInfo = runCatching { app.getFrameworkInfo() }.getOrDefault(emptyMap())
            val apiVersion = (frameworkInfo["apiVersion"] as? Number)?.toInt() ?: 0
            val scopePackages = (frameworkInfo["scope"] as? List<*>)
                ?.filterIsInstance<String>()
                .orEmpty()
            val hasSystemUiScope = SYSTEM_UI_PACKAGE in scopePackages
            info to ModuleState(
                active = apiVersion >= MIN_SUPPORTED_API && hasSystemUiScope,
                serviceConnected = true,
                framework = frameworkInfo["frameworkName"]?.toString().orEmpty(),
                frameworkVersion = frameworkInfo["frameworkVersion"]?.toString().orEmpty(),
                frameworkVersionCode = (frameworkInfo["frameworkVersionCode"] as? Number)?.toInt() ?: 0,
                apiVersion = apiVersion,
                hasSystemUiScope = hasSystemUiScope,
            )
        }
        systemInfo = refreshed.first
        status = refreshed.second
    }
}

@Composable
internal fun rememberHomeOverviewState(prefs: FlutterPrefsRepository): HomeOverviewState {
    val state = remember(prefs) { HomeOverviewState(prefs) }
    DisposableEffect(prefs, state) {
        val removeListener = prefs.addChangeListener(state::onPreferenceChanged)
        onDispose(removeListener)
    }
    return state
}

@Composable
internal fun OverviewPage(
    state: HomeOverviewState,
    isActive: Boolean,
    onOpenApps: () -> Unit,
    onOpenToastApps: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCustomTest by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    val statusAlert = homeStatusAlert(state.status, state.systemInfo)

    LaunchedEffect(isActive) {
        if (isActive) state.refresh(context)
    }
    CollapsingPage(
        title = "HyperIsland",
        actionIcon = MiuixIcons.Refresh,
        actionDescription = stringResource(R.string.compose_restart_scope),
        onAction = { showRestartDialog = true },
        horizontalContentPadding = 12.dp,
        topContentPadding = 12.dp,
        bottomContentPadding = 16.dp,
    ) {
        item {
            StatusGrid(
                status = state.status,
                appVersion = state.systemInfo?.appVersion,
                enabledAppCount = state.enabledAppCount,
                toastEnabledAppCount = state.toastEnabledAppCount,
                onSendTest = { TestNotificationService.sendDefault(context) },
                onCustomTest = { showCustomTest = true },
                onOpenApps = onOpenApps,
                onOpenToastApps = onOpenToastApps,
            )
        }
        if (statusAlert != null) {
            item { HomeStatusAlertCard(statusAlert) }
        }
        item { InfoCard(state.systemInfo, state.status) }
        item {
            Card {
                SettingsAction(
                    title = stringResource(R.string.compose_support_development),
                    summary = stringResource(R.string.compose_support_development_summary),
                    endIcon = MiuixIcons.Link,
                    endIconSize = 26.dp,
                    onClick = { context.openUrl(DONATION_URL) },
                )
                SettingsAction(
                    title = stringResource(R.string.compose_documentation),
                    summary = stringResource(R.string.compose_documentation_summary),
                    endIcon = MiuixIcons.Link,
                    endIconSize = 26.dp,
                    onClick = { context.openUrl(DOCUMENTATION_URL) },
                )
            }
        }
    }

    CustomTestDialog(
        show = showCustomTest,
        enabled = state.status?.active == true,
        onDismiss = { showCustomTest = false },
        onSend = { title, content, clearPrevious, enableFloat ->
            TestNotificationService.sendCustom(
                context = context,
                title = title,
                content = content,
                clearPrevious = clearPrevious,
                enableFloat = enableFloat,
            )
            showCustomTest = false
        },
    )
    RestartScopeDialog(
        show = showRestartDialog,
        onDismiss = { showRestartDialog = false },
    )
}

private data class HomeStatusAlert(
    val title: String,
    val message: String,
    val warning: Boolean = false,
)

@Composable
private fun homeStatusAlert(status: ModuleState?, info: HomeSystemInfo?): HomeStatusAlert? {
    if (status == null || info == null) return null
    return when {
        !status.serviceConnected -> HomeStatusAlert(
            title = stringResource(R.string.compose_lsposed_service_unavailable),
            message = stringResource(R.string.compose_lsposed_service_unavailable_summary),
        )
        status.apiVersion < MIN_SUPPORTED_API -> HomeStatusAlert(
            title = stringResource(R.string.compose_lsposed_version_unsupported),
            message = stringResource(R.string.compose_update_lsposed),
        )
        !status.hasSystemUiScope -> HomeStatusAlert(
            title = stringResource(R.string.compose_systemui_scope_missing),
            message = stringResource(R.string.compose_enable_systemui_scope),
        )
        info.focusProtocolVersion != REQUIRED_FOCUS_PROTOCOL -> HomeStatusAlert(
            title = stringResource(R.string.compose_system_not_supported),
            message = stringResource(
                R.string.compose_system_not_supported_summary,
                info.focusProtocolVersion,
                REQUIRED_FOCUS_PROTOCOL,
            ),
        )
        info.androidSdkVersion == ANDROID_15_SDK -> HomeStatusAlert(
            title = stringResource(R.string.compose_android_15_limited),
            message = stringResource(R.string.compose_android_15_limited_summary),
            warning = true,
        )
        else -> null
    }
}

@Composable
private fun HomeStatusAlertCard(alert: HomeStatusAlert) {
    val isLight = MiuixTheme.colorScheme.background.luminance() > 0.5f
    val backgroundColor = when {
        !alert.warning -> MiuixTheme.colorScheme.errorContainer
        isLight -> WarningBackgroundLight
        else -> WarningBackgroundDark
    }
    val contentColor = when {
        !alert.warning -> MiuixTheme.colorScheme.onErrorContainer
        isLight -> WarningContentLight
        else -> WarningContentDark
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = backgroundColor,
            contentColor = contentColor,
        ),
        showIndication = false,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = alert.title,
                color = contentColor,
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = alert.message,
                color = contentColor.copy(alpha = 0.82f),
                style = MiuixTheme.textStyles.body2,
            )
        }
    }
}

@Composable
private fun StatusGrid(
    status: ModuleState?,
    appVersion: String?,
    enabledAppCount: Int,
    toastEnabledAppCount: Int,
    onSendTest: () -> Unit,
    onCustomTest: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenToastApps: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusCard(status, appVersion, Modifier.weight(1f).height(112.dp), onSendTest, onCustomTest)
                StatCard(
                    title = stringResource(R.string.compose_enabled_app_islands),
                    value = enabledAppCount.toString(),
                    modifier = Modifier.weight(1f).height(112.dp),
                    onClick = onOpenApps,
                )
                StatCard(
                    title = stringResource(R.string.compose_enabled_toast_islands),
                    value = toastEnabledAppCount.toString(),
                    modifier = Modifier.weight(1f).height(112.dp),
                    onClick = onOpenToastApps,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusCard(status, appVersion, Modifier.weight(1f).aspectRatio(1f), onSendTest, onCustomTest)
                Column(
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        title = stringResource(R.string.compose_enabled_app_islands),
                        value = enabledAppCount.toString(),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenApps,
                    )
                    StatCard(
                        title = stringResource(R.string.compose_enabled_toast_islands),
                        value = toastEnabledAppCount.toString(),
                        modifier = Modifier.weight(1f),
                        onClick = onOpenToastApps,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    status: ModuleState?,
    appVersion: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val active = status?.active == true
    val statusColor = if (active) ActiveColor else InactiveColor
    val statusBackground = if (active) ActiveBackground else InactiveBackground
    Card(
        modifier = modifier,
        colors = CardDefaults.defaultColors(color = statusBackground),
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = active,
        onClick = onClick.takeIf { active },
        onLongPress = onLongPress.takeIf { active },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxSize().offset(27.dp, 31.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    modifier = Modifier.size(110.dp),
                    painter = painterResource(R.drawable.ic_check_circle_outline),
                    contentDescription = null,
                    tint = statusColor.copy(alpha = 0.78f),
                )
            }
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = stringResource(
                        if (active) R.string.compose_activated else R.string.compose_not_activated,
                    ),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF101010),
                )
                Text(
                    text = stringResource(
                        R.string.compose_software_version,
                        appVersion.orEmpty().ifBlank { stringResource(R.string.compose_unknown) },
                    ),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (active) Color(0xFF101010) else statusColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.Tilt,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun RestartScopeDialog(show: Boolean, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val scopeOptions = listOf(
        SYSTEM_UI_PACKAGE to stringResource(R.string.compose_system_ui),
        DOWNLOADS_PACKAGE to stringResource(R.string.compose_download_manager),
        XMSF_PACKAGE to stringResource(R.string.compose_xmsf),
        SETTINGS_PACKAGE to stringResource(R.string.compose_hook_scope_settings),
    )
    var selectedPackages by remember(show) {
        mutableStateOf(scopeOptions.map { it.first }.toSet())
    }
    var restarting by remember(show) { mutableStateOf(false) }
    var error by remember(show) { mutableStateOf<String?>(null) }
    val rootRequired = stringResource(R.string.compose_restart_root_required)

    WindowDialog(
        show = show,
        title = stringResource(R.string.compose_restart_scope),
        summary = stringResource(R.string.compose_restart_scope_summary),
        onDismissRequest = { if (!restarting) onDismiss() },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            scopeOptions.forEach { (packageName, label) ->
                val checked = packageName in selectedPackages
                RestartScopeRow(
                    label = label,
                    checked = checked,
                    enabled = !restarting,
                    onClick = {
                        selectedPackages = if (checked) {
                            selectedPackages - packageName
                        } else {
                            selectedPackages + packageName
                        }
                    },
                )
            }
        }
        error?.let {
            Text(
                it,
                color = InactiveColor,
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                text = stringResource(R.string.compose_cancel),
                enabled = !restarting,
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.compose_confirm),
                enabled = !restarting && selectedPackages.isNotEmpty(),
                onClick = {
                    val commands = buildList {
                        if (SYSTEM_UI_PACKAGE in selectedPackages) add("killall $SYSTEM_UI_PACKAGE")
                        if (DOWNLOADS_PACKAGE in selectedPackages) add("am force-stop $DOWNLOADS_PACKAGE")
                        if (XMSF_PACKAGE in selectedPackages) add("am force-stop $XMSF_PACKAGE")
                        if (SETTINGS_PACKAGE in selectedPackages) add("am force-stop $SETTINGS_PACKAGE")
                    }
                    restarting = true
                    error = null
                    scope.launch {
                        RestartScopeService.restart(commands)
                            .onSuccess { onDismiss() }
                            .onFailure { error = rootRequired }
                        restarting = false
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun RestartScopeRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.headline1,
        )
        Spacer(Modifier.width(12.dp))
        Checkbox(
            state = ToggleableState(checked),
            onClick = onClick,
            enabled = enabled,
        )
    }
}

private fun Context.openUrl(url: String) {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

@Composable
private fun InfoCard(info: HomeSystemInfo?, status: ModuleState?) {
    val unknown = stringResource(R.string.compose_unknown)
    val appVersion = info?.let { "${it.appVersion} (${it.appVersionCode})" }.orEmpty().ifBlank { unknown }
    val frameworkVersion = status
        ?.takeIf { it.serviceConnected }
        ?.let {
            stringResource(
                R.string.compose_framework_details,
                it.framework.ifBlank { unknown },
                it.frameworkVersion.ifBlank { unknown },
                it.frameworkVersionCode,
                it.apiVersion,
            )
        }
        ?: unknown
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            InfoText(stringResource(R.string.compose_system_version), info?.systemVersion.orEmpty().ifBlank { unknown })
            InfoText(stringResource(R.string.compose_app_version), appVersion)
            InfoText(stringResource(R.string.compose_xposed_framework), frameworkVersion)
            InfoText(stringResource(R.string.compose_device_model), info?.deviceModel.orEmpty().ifBlank { unknown }, 0.dp)
        }
    }
}

@Composable
private fun InfoText(title: String, content: String, bottomPadding: androidx.compose.ui.unit.Dp = 24.dp) {
    Text(
        text = title,
        fontSize = MiuixTheme.textStyles.headline1.fontSize,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurface,
    )
    Text(
        text = content,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(top = 2.dp, bottom = bottomPadding),
    )
}

@Composable
private fun CustomTestDialog(
    show: Boolean,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSend: (String, String, Boolean, Boolean) -> Unit,
) {
    var title by remember(show) { mutableStateOf("") }
    var content by remember(show) { mutableStateOf("") }
    var clearPrevious by remember(show) { mutableStateOf(true) }
    var enableFloat by remember(show) { mutableStateOf(true) }
    WindowDialog(
        show = show,
        title = stringResource(R.string.compose_custom_test_notification),
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.compose_custom_test_title),
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            TextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.compose_custom_test_content),
                useLabelAsPlaceholder = true,
                minLines = 2,
                maxLines = 4,
            )
            SwitchPreference(
                checked = clearPrevious,
                onCheckedChange = { clearPrevious = it },
                title = stringResource(R.string.compose_clear_previous_notification),
                summary = stringResource(R.string.compose_clear_previous_notification_summary),
            )
            SwitchPreference(
                checked = enableFloat,
                onCheckedChange = { enableFloat = it },
                title = stringResource(R.string.compose_expand_notification),
                summary = stringResource(R.string.compose_enable_float_summary),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.compose_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onSend(title, content, clearPrevious, enableFloat) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.compose_send_test_notification))
                }
            }
        }
    }
}

private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
private const val DOWNLOADS_PACKAGE = "com.android.providers.downloads"
private const val XMSF_PACKAGE = "com.xiaomi.xmsf"
private const val SETTINGS_PACKAGE = "com.android.settings"
private const val DONATION_URL = "https://hyperisland.1812z.top/donors.html"
private const val DOCUMENTATION_URL = "https://hyperisland.1812z.top/"
private const val MIN_SUPPORTED_API = 101
private const val REQUIRED_FOCUS_PROTOCOL = 3
private const val ANDROID_15_SDK = 35
private val ActiveColor = Color(0xFF36D167)
private val ActiveBackground = Color(0xFFDFFAE4)
private val InactiveColor = Color(0xFFFF5A52)
private val InactiveBackground = Color(0xFFFFE5E3)
private val WarningBackgroundLight = Color(0xFFFFF3D6)
private val WarningContentLight = Color(0xFF704D00)
private val WarningBackgroundDark = Color(0xFF3A2D12)
private val WarningContentDark = Color(0xFFFFD978)
