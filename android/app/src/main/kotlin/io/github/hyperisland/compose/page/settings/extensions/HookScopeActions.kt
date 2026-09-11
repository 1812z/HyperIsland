package io.github.hyperisland.compose.page.settings.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import io.github.hyperisland.compose.service.XposedScopeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarHostState

/** Shared snackbar + LSPosed scope request helpers for hook extension pages. */
internal class HookScopeActions(
    private val context: android.content.Context,
    private val scope: CoroutineScope,
    val snackbar: SnackbarHostState,
) {
    fun show(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun request(enabled: Boolean, packages: List<String>, failureMessage: String): Boolean {
        if (!enabled) return true
        val result = XposedScopeService.requestScope(context, packages)
        if (result.isFailure) show(result.exceptionOrNull()?.message ?: failureMessage)
        return result.isSuccess
    }
}

@Composable
internal fun rememberHookScopeActions(): HookScopeActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    return remember(context, scope, snackbar) { HookScopeActions(context, scope, snackbar) }
}
