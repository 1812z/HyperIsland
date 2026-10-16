package io.github.hyperisland.compose.page.settings.extensions.homeassistant

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.PreferenceDropdown
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.component.SettingsAction
import io.github.hyperisland.compose.component.SettingsItemMargin
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.compose.page.settings.extensions.KEY_HOME_ASSISTANT
import io.github.hyperisland.compose.page.settings.extensions.KEY_HOME_ASSISTANT_SCREEN_OFF_POWER_SAVE
import io.github.hyperisland.compose.page.settings.extensions.KEY_HOME_ASSISTANT_SUBSCRIPTIONS
import io.github.hyperisland.compose.page.settings.extensions.KEY_HOME_ASSISTANT_TOKEN
import io.github.hyperisland.compose.page.settings.extensions.KEY_HOME_ASSISTANT_URL
import io.github.hyperisland.compose.service.XposedScopeService
import io.github.hyperisland.homeassistant.HaSubscription
import io.github.hyperisland.homeassistant.HomeAssistantContract
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * Home Assistant 拓展设置页。
 *
 * 总开关关闭时下面所有设置一律禁用（含右下角的新增按钮），符合「开启后才进行 Hook 与配置」的约定。
 * 通知链路本身在 SystemUI 进程里跑（见 `HomeAssistantIslandHook`），本页只负责写配置、
 * 热通知 Hook、并额外维持一条只读连接用于实体状态预览。
 */
@Composable
internal fun HomeAssistantPage(
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scopeFailed = stringResource(R.string.ext_scope_failed)

    fun show(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    val enabled = rememberBooleanPreference(prefs, KEY_HOME_ASSISTANT, false)
    // 息屏省电：默认关闭。开启后 Hook 会在息屏时停收 WS 事件，并在息屏满 1 分钟后断开连接。
    val screenOffPowerSave = rememberBooleanPreference(
        prefs,
        KEY_HOME_ASSISTANT_SCREEN_OFF_POWER_SAVE,
        false,
    )
    // 地址 / 令牌用草稿态：输入过程中不落盘、也不重连，避免每敲一个字就断一次连接。
    var savedUrl by remember { mutableStateOf(prefs.getString(KEY_HOME_ASSISTANT_URL, "")) }
    var savedToken by remember { mutableStateOf(prefs.getString(KEY_HOME_ASSISTANT_TOKEN, "")) }
    var draftUrl by remember { mutableStateOf(savedUrl) }
    var draftToken by remember { mutableStateOf(savedToken) }
    var tokenObscured by remember { mutableStateOf(true) }

    var subscriptionsRaw by remember {
        mutableStateOf(prefs.getString(KEY_HOME_ASSISTANT_SUBSCRIPTIONS, ""))
    }
    val subscriptions = remember(subscriptionsRaw) { HaSubscription.parseList(subscriptionsRaw) }

    val connection = rememberHomeAssistantConnection(enabled.value, savedUrl, savedToken)

    var sheetShown by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HaSubscription?>(null) }
    var pendingDelete by remember { mutableStateOf<HaSubscription?>(null) }

    /** 热更新 Hook：值随 extra 一起带过去，避免与 RemotePreferences 同步产生竞态。 */
    fun notifyRuntime(
        enabledValue: Boolean = enabled.value,
        urlValue: String = savedUrl,
        tokenValue: String = savedToken,
        subscriptionList: List<HaSubscription> = subscriptions,
        screenOffPowerSaveValue: Boolean = screenOffPowerSave.value,
    ) {
        context.sendBroadcast(
            Intent(HomeAssistantContract.ACTION_CONFIG_CHANGED).apply {
                setPackage("com.android.systemui")
                putExtra(HomeAssistantContract.EXTRA_ENABLED, enabledValue)
                putExtra(HomeAssistantContract.EXTRA_URL, urlValue)
                putExtra(HomeAssistantContract.EXTRA_TOKEN, tokenValue)
                putExtra(
                    HomeAssistantContract.EXTRA_SUBSCRIPTIONS,
                    HaSubscription.encode(subscriptionList),
                )
                putExtra(
                    HomeAssistantContract.EXTRA_SCREEN_OFF_POWER_SAVE,
                    screenOffPowerSaveValue,
                )
            },
        )
    }

    fun persistServer(url: String, token: String) {
        val nextUrl = url.trim()
        val nextToken = token.trim()
        draftUrl = nextUrl
        draftToken = nextToken
        savedUrl = nextUrl
        savedToken = nextToken
        prefs.putString(KEY_HOME_ASSISTANT_URL, nextUrl)
        prefs.putString(KEY_HOME_ASSISTANT_TOKEN, nextToken)
    }

    fun persistSubscriptions(list: List<HaSubscription>) {
        val raw = HaSubscription.encode(list)
        subscriptionsRaw = raw
        prefs.putString(KEY_HOME_ASSISTANT_SUBSCRIPTIONS, raw)
        notifyRuntime(subscriptionList = list)
    }

    val statusText = when (connection.status) {
        HaUiStatus.Disabled -> stringResource(R.string.ha_status_disabled)
        HaUiStatus.Unconfigured -> stringResource(R.string.ha_status_unconfigured)
        HaUiStatus.Connecting -> stringResource(R.string.ha_status_connecting)
        HaUiStatus.Connected -> stringResource(R.string.ha_status_connected, connection.entities.size)
        HaUiStatus.AuthFailed -> stringResource(R.string.ha_status_auth_failed)
        HaUiStatus.Failed -> stringResource(R.string.ha_status_failed)
    }
    val statusDisplay = buildString {
        append(statusText)
        connection.detail?.takeIf { it.isNotBlank() }?.let { append('\n').append(it) }
        // 只有「已连接但一个实体都没拿到」时把服务端回执摊出来，平时保持干净。
        if (connection.status == HaUiStatus.Connected && connection.entities.isEmpty()) {
            connection.diagnostics?.takeIf { it.isNotBlank() }?.let { append('\n').append(it) }
        }
    }

    DetailPage(
        title = stringResource(R.string.ext_home_assistant),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (enabled.value) {
                // 位置交给 Scaffold（FabPosition.End），它已经处理了 FabSpacing 与导航条 inset。
                FloatingActionButton(
                    onClick = {
                        editing = null
                        sheetShown = true
                    },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Add,
                        contentDescription = stringResource(R.string.ha_add),
                        tint = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        },
        // 总开关打开时右下角才有「+」悬浮按钮；把列表底部留白抬到按钮之上，
        // 否则最后一条订阅会被按钮压住（FAB 本体 60dp + 12dp 间距）。
        bottomContentPadding = if (enabled.value) 96.dp else 28.dp,
    ) {
        item {
            SectionTitle(stringResource(R.string.config))
            Card(modifier = Modifier.fillMaxWidth()) {
                PreferenceSwitch(
                    title = stringResource(R.string.ext_home_assistant_enable),
                    summary = stringResource(R.string.ext_home_assistant_enable_summary),
                    icon = null,
                    checked = enabled.value,
                ) { value ->
                    val granted = !value || XposedScopeService.requestScope(
                        context,
                        listOf("com.android.systemui"),
                    ).onFailure { show(it.message ?: scopeFailed) }.isSuccess
                    if (granted) {
                        enabled.value = value
                        prefs.putBoolean(KEY_HOME_ASSISTANT, value)
                        // 打开总开关时顺手把草稿存下来，否则 Hook 拿到的还是空地址。
                        if (value) persistServer(draftUrl, draftToken)
                        notifyRuntime(
                            enabledValue = value,
                            urlValue = draftUrl.trim(),
                            tokenValue = draftToken.trim(),
                        )
                        if (value && draftUrl.isBlank()) {
                            show(context.getString(R.string.ha_url_empty))
                        }
                    }
                }
                AnimatedVisibility(enabled.value) {
                    Column {
                        PreferenceSwitch(
                            title = stringResource(R.string.ha_screen_off_power_save),
                            summary = stringResource(R.string.ha_screen_off_power_save_summary),
                            icon = null,
                            checked = screenOffPowerSave.value,
                        ) { value ->
                            screenOffPowerSave.value = value
                            prefs.putBoolean(KEY_HOME_ASSISTANT_SCREEN_OFF_POWER_SAVE, value)
                            notifyRuntime(screenOffPowerSaveValue = value)
                        }
                        TextField(
                            value = draftUrl,
                            onValueChange = { draftUrl = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 6.dp),
                            label = stringResource(R.string.ha_server),
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                        )
                        Text(
                            text = stringResource(R.string.ha_server_hint),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 18.dp),
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        TextField(
                            value = draftToken,
                            onValueChange = { draftToken = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 6.dp),
                            label = stringResource(R.string.ha_token),
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            visualTransformation = if (tokenObscured) {
                                PasswordVisualTransformation()
                            } else {
                                VisualTransformation.None
                            },
                            trailingIcon = {
                                IconButton(onClick = { tokenObscured = !tokenObscured }) {
                                    Icon(
                                        if (tokenObscured) MiuixIcons.Show else MiuixIcons.Hide,
                                        contentDescription = stringResource(R.string.ha_token),
                                    )
                                }
                            },
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Button(
                                onClick = {
                                    persistServer(draftUrl, draftToken)
                                    notifyRuntime(
                                        urlValue = draftUrl.trim(),
                                        tokenValue = draftToken.trim(),
                                    )
                                    show(context.getString(R.string.ha_saved))
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            ) {
                                Text(stringResource(R.string.save))
                            }
                            Button(
                                onClick = {
                                    persistServer(draftUrl, draftToken)
                                    connection.reconnect(draftUrl.trim(), draftToken.trim())
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.ha_reconnect))
                            }
                        }
                        BasicComponent(
                            title = stringResource(R.string.ha_connection),
                            summary = statusDisplay,
                            insideMargin = SettingsItemMargin,
                        )
                    }
                }
            }
        }

        item {
            SectionTitle(stringResource(R.string.ha_subscriptions))
            Card(modifier = Modifier.fillMaxWidth()) {
                if (subscriptions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ha_subscriptions_empty),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    subscriptions.forEach { subscription ->
                        SubscriptionRow(
                            subscription = subscription,
                            
                            enabled = enabled.value,
                            onToggle = { checked ->
                                persistSubscriptions(
                                    subscriptions.map {
                                        if (it.id == subscription.id) {
                                            it.copy(enabled = checked)
                                        } else {
                                            it
                                        }
                                    },
                                )
                            },
                            onClick = {
                                editing = subscription
                                sheetShown = true
                            },
                        )
                    }
                }
            }
        }
    }

    // sheet 与确认框必须是同一父组合下的兄弟节点，才是两个独立窗口叠在一起。
    HomeAssistantSubscriptionSheet(
        show = sheetShown,
        initial = editing,
        entities = connection.entities,
        status = connection.status,
        onDismiss = { sheetShown = false },
        onDelete = { subscription -> pendingDelete = subscription },
        onSave = { subscription ->
            val exists = subscriptions.any { it.id == subscription.id }
            val next = if (exists) {
                subscriptions.map { if (it.id == subscription.id) subscription else it }
            } else {
                subscriptions + subscription
            }
            persistSubscriptions(next)
            sheetShown = false
            show(context.getString(R.string.ha_saved))
        },
        onRetry = { connection.reconnect() },
    )

    WindowDialog(
        show = pendingDelete != null,
        title = stringResource(R.string.delete),
        summary = stringResource(R.string.ha_delete_confirm),
        onDismissRequest = { pendingDelete = null },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    val target = pendingDelete
                    if (target != null) {
                        persistSubscriptions(subscriptions.filterNot { it.id == target.id })
                    }
                    pendingDelete = null
                    sheetShown = false
                },
                modifier = Modifier.weight(1f),
                colors = haButtonColors(),
            ) {
                Text(stringResource(R.string.confirm))
            }
            Button(
                onClick = { pendingDelete = null },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun SubscriptionRow(
    subscription: HaSubscription,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val title = subscription.name.ifBlank { stringResource(R.string.ha_subscription_unnamed) }

    BasicComponent(
        title = title,
        summary = null,
        endActions = {
            Switch(
                checked = subscription.enabled,
                onCheckedChange = onToggle,
                enabled = enabled,
            )
        },
        insideMargin = SettingsItemMargin,
        enabled = enabled,
        onClick = onClick,
    )
}
