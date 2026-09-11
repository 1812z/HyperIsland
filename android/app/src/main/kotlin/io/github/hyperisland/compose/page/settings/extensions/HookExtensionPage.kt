package io.github.hyperisland.compose.page.settings.extensions

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.SettingsItemMargin
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.utils.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AppRecording
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class HookAppEntry(
    val detail: HookExtensionDetail,
    val packages: List<String>,
    @StringRes val title: Int,
    val fallbackIcon: ImageVector,
)

@Composable
internal fun HookExtensionPage(
    prefs: FlutterPrefsRepository,
    onOpenDetail: (HookExtensionDetail) -> Unit,
    onBack: () -> Unit,
) {
    val entries = remember {
        listOf(
            HookAppEntry(
                detail = HookExtensionDetail.SystemUi,
                packages = listOf(PKG_SYSTEM_UI),
                title = R.string.ext_system_ui,
                fallbackIcon = MiuixIcons.Layers,
            ),
            HookAppEntry(
                detail = HookExtensionDetail.SystemSettings,
                packages = listOf(PKG_SETTINGS),
                title = R.string.ext_system_settings,
                fallbackIcon = MiuixIcons.Settings,
            ),
            HookAppEntry(
                detail = HookExtensionDetail.SecurityCenter,
                packages = listOf(PKG_SECURITY_CENTER),
                title = R.string.ext_permission_manager,
                fallbackIcon = MiuixIcons.Blocklist,
            ),
            HookAppEntry(
                detail = HookExtensionDetail.Xmsf,
                packages = listOf(PKG_XMSF),
                title = R.string.ext_xmsf,
                fallbackIcon = MiuixIcons.Community,
            ),
            HookAppEntry(
                detail = HookExtensionDetail.ScreenRecorder,
                packages = listOf(PKG_SCREEN_RECORDER),
                title = R.string.screen_recorder_dialog_title,
                fallbackIcon = MiuixIcons.AppRecording,
            ),
            HookAppEntry(
                detail = HookExtensionDetail.DownloadManager,
                packages = listOf(PKG_DOWNLOAD_MANAGER),
                title = R.string.ext_download_manager,
                fallbackIcon = MiuixIcons.Download,
            ),
        )
    }

    HookExtensionScaffold(
        title = stringResource(R.string.hook_extension),
        onBack = onBack,
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                entries.forEach { entry ->
                    HookAppRow(
                        entry = entry,
                        onClick = { onOpenDetail(entry.detail) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HookAppRow(
    entry: HookAppEntry,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, entry.packages) {
        value = withContext(Dispatchers.IO) {
            entry.packages.firstNotNullOfOrNull { packageName ->
                loadAppIcon(context, packageName)
            }
        }
    }
    val fallbackTitle = stringResource(entry.title)
    val appLabel = remember(entry.packages) {
        entry.packages.firstNotNullOfOrNull { packageName -> loadAppLabel(context, packageName) }
    }
    val title = appLabel ?: fallbackTitle
    val summary = entry.packages.firstOrNull { packageName ->
        runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.isSuccess
    } ?: entry.packages.first()

    BasicComponent(
        startAction = {
            Box(
                modifier = Modifier.padding(end = 16.dp).size(AppIconSize),
                contentAlignment = Alignment.Center,
            ) {
                val currentIcon = icon
                if (currentIcon != null) {
                    Image(
                        bitmap = currentIcon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = entry.fallbackIcon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                }
            }
        },
        endActions = {
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        },
        insideMargin = SettingsItemMargin,
        onClick = onClick,
    ) {
        Text(
            text = title,
            fontSize = MiuixTheme.textStyles.headline1.fontSize,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = summary,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun loadAppIcon(context: Context, packageName: String): ImageBitmap? = runCatching {
    context.packageManager.getApplicationIcon(packageName).toBitmap(APP_ICON_SIZE).asImageBitmap()
}.getOrNull()

private fun loadAppLabel(context: Context, packageName: String): String? = runCatching {
    val packageManager = context.packageManager
    packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
}.getOrNull()

private const val APP_ICON_SIZE = 144
private val AppIconSize = 40.dp
