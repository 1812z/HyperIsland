package io.github.hyperisland.compose.page.settings.extensions

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.RestartScopeDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh

@Composable
internal fun HookExtensionScaffold(
    title: String,
    onBack: () -> Unit,
    snackbarHost: @Composable () -> Unit = {},
    restartPackages: Set<String>? = null,
    content: LazyListScope.() -> Unit,
) {
    var showRestartDialog by remember { mutableStateOf(false) }

    DetailPage(
        title = title,
        onBack = onBack,
        actionIcon = MiuixIcons.Refresh,
        actionDescription = stringResource(R.string.restart_scope),
        onAction = { showRestartDialog = true },
        snackbarHost = snackbarHost,
        content = content,
    )

    RestartScopeDialog(
        show = showRestartDialog,
        onDismiss = { showRestartDialog = false },
        allowedPackages = restartPackages,
        preselectedPackages = restartPackages.orEmpty(),
    )
}
