package io.github.hyperisland.compose.data

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.hyperisland.core.service.AppService
import io.github.hyperisland.utils.toBitmap

internal data class InstalledApp(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean,
)

internal class InstalledAppsRepository(private val context: Context) {
    private val appService = AppService()
    private val iconCache = mutableMapOf<String, ImageBitmap?>()

    fun needsAppListPermission(): Boolean = runCatching {
        context.packageManager.getPermissionInfo(APP_LIST_PERMISSION, 0).packageName == MIUI_PERMISSION_MANAGER
    }.getOrDefault(false) && context.checkSelfPermission(APP_LIST_PERMISSION) != PackageManager.PERMISSION_GRANTED

    fun load(): List<InstalledApp> = appService.getInstalledApps(
        packageManager = context.packageManager,
        selfPackageName = context.packageName,
        includeSystem = true,
    ).mapNotNull { item ->
        val packageName = item["packageName"] as? String ?: return@mapNotNull null
        if (packageName in EXCLUDED_PACKAGES) return@mapNotNull null
        InstalledApp(
            packageName = packageName,
            appName = item["appName"] as? String ?: packageName,
            isSystem = item["isSystem"] as? Boolean ?: false,
        )
    }

    fun loadIcon(packageName: String): ImageBitmap? = synchronized(iconCache) {
        if (iconCache.containsKey(packageName)) return@synchronized iconCache[packageName]
        val bitmap = runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(96).asImageBitmap()
        }.getOrNull()
        iconCache[packageName] = bitmap
        bitmap
    }

    private companion object {
        const val APP_LIST_PERMISSION = "com.android.permission.GET_INSTALLED_APPS"
        const val MIUI_PERMISSION_MANAGER = "com.lbe.security.miui"
        val EXCLUDED_PACKAGES = setOf("com.android.providers.downloads.ui", "com.android.systemui")
    }
}
