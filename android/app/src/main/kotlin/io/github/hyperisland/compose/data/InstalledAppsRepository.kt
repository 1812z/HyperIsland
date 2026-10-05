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

    fun needsAppListPermission(): Boolean = runCatching {
        context.packageManager.getPermissionInfo(APP_LIST_PERMISSION, 0).packageName == MIUI_PERMISSION_MANAGER
    }.getOrDefault(false) && context.checkSelfPermission(APP_LIST_PERMISSION) != PackageManager.PERMISSION_GRANTED

    fun cachedApps(): List<InstalledApp> = synchronized(cacheLock) { appCache }

    fun load(forceRefresh: Boolean = false): List<InstalledApp> = synchronized(cacheLock) {
        if (!forceRefresh && appCache.isNotEmpty()) return@synchronized appCache
        appCache = appService.getInstalledApps(
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
        appCache
    }

    fun cachedIcon(packageName: String): ImageBitmap? = synchronized(iconCache) {
        iconCache[packageName]
    }

    fun loadIcon(packageName: String): ImageBitmap? {
        synchronized(iconCache) {
            if (iconCache.containsKey(packageName)) return iconCache[packageName]
        }
        // PackageManager / drawable 解码不能占用缓存锁，否则主线程 cachedIcon 也会被阻塞。
        val bitmap = runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(96).asImageBitmap()
        }.getOrNull()
        synchronized(iconCache) {
            iconCache[packageName] = bitmap
            while (iconCache.size > 512) iconCache.remove(iconCache.keys.first())
        }
        return bitmap
    }

    private companion object {
        const val APP_LIST_PERMISSION = "com.android.permission.GET_INSTALLED_APPS"
        const val MIUI_PERMISSION_MANAGER = "com.lbe.security.miui"
        val EXCLUDED_PACKAGES = setOf("com.android.providers.downloads.ui", "com.android.systemui")
        val cacheLock = Any()
        var appCache: List<InstalledApp> = emptyList()
        val iconCache = LinkedHashMap<String, ImageBitmap?>(512, 0.75f, true)
    }
}
