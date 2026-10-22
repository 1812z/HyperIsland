package io.github.hyperisland.compose.page.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.PreferenceSwitch
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.data.rememberBooleanPreference
import io.github.hyperisland.data.DebugPreferences
import io.github.hyperisland.data.HookCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Hook 管理：列出模块里所有 [io.github.hyperisland.xposed.hook.BaseHook] 子类，
 * 打开开关即在下次注入时强制停用该 Hook。
 *
 * 名单由 [HookCatalog] 直接扫 dex 得到，新增 Hook 不用改这里；
 * 实际拦截点在 `BaseHook.init`，总开关 `pref_debug_mode` 关闭则整份名单失效。
 */
@Composable
internal fun HookManagerPage(prefs: FlutterPrefsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val debugMode = rememberBooleanPreference(
        prefs,
        DebugPreferences.MODE,
        DebugPreferences.DEFAULT_MODE,
    )
    var hooks by remember { mutableStateOf<List<String>?>(null) }
    var disabled by remember { mutableStateOf(readDisabledHooks(prefs)) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { HookCatalog.load(context) }
        hooks = loaded
        disabled = readDisabledHooks(prefs)
    }

    DetailPage(
        title = stringResource(R.string.hook_manager),
        onBack = onBack,
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                HintText(stringResource(R.string.hook_manager_description))
            }
        }
        item {
            val list = hooks
            Card(modifier = Modifier.fillMaxWidth()) {
                when {
                    list == null -> HintText(stringResource(R.string.hook_manager_loading))
                    list.isEmpty() -> HintText(stringResource(R.string.hook_manager_empty))
                    else -> list.forEach { name ->
                        PreferenceSwitch(
                            title = name,
                            summary = null,
                            icon = null,
                            checked = name in disabled,
                            enabled = debugMode.value,
                        ) { checked ->
                            disabled = writeDisabledHook(prefs, name, checked)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

private fun readDisabledHooks(prefs: FlutterPrefsRepository): Set<String> =
    prefs.getString(DebugPreferences.DISABLED_HOOKS)
        .split(',')
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

private fun writeDisabledHook(
    prefs: FlutterPrefsRepository,
    name: String,
    disabled: Boolean,
): Set<String> {
    val next = readDisabledHooks(prefs).toMutableSet()
    if (disabled) next += name else next -= name
    prefs.putString(DebugPreferences.DISABLED_HOOKS, next.sorted().joinToString(","))
    return next
}
