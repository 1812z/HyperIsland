package io.github.hyperisland.core.service

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import io.github.hyperisland.utils.toBitmap
import java.io.ByteArrayOutputStream

internal class AppService {
    fun getInstalledApps(
        packageManager: PackageManager,
        selfPackageName: String,
        includeSystem: Boolean,
    ): List<Map<String, Any>> {
        return packageManager.getInstalledApplications(0)
            .filter { app ->
                app.packageName != selfPackageName &&
                    (includeSystem || (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0)
            }
            .mapNotNull { app ->
                try {
                    val label = packageManager.getApplicationLabel(app).toString()
                    mapOf(
                        "packageName" to app.packageName,
                        "appName" to label,
                        "isSystem" to ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0),
                    )
                } catch (_: Exception) {
                    null
                }
            }
            .sortedBy { it["appName"] as String }
    }

    fun getAppIconBytes(
        packageManager: PackageManager,
        packageName: String,
        iconSize: Int = 96,
    ): ByteArray? {
        return try {
            val bmp = packageManager.getApplicationIcon(packageName).toBitmap(iconSize)
            ByteArrayOutputStream().use { stream ->
                bmp.compress(Bitmap.CompressFormat.PNG, 90, stream)
                stream.toByteArray()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun setDesktopIconVisible(
        packageManager: PackageManager,
        packageName: String,
        visible: Boolean,
    ) {
        val componentName = ComponentName(packageName, "$packageName.MainActivityAlias")
        val newState = if (visible) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        packageManager.setComponentEnabledSetting(
            componentName,
            newState,
            PackageManager.DONT_KILL_APP,
        )
    }

    fun isDesktopIconVisible(
        packageManager: PackageManager,
        packageName: String,
    ): Boolean {
        val componentName = ComponentName(packageName, "$packageName.MainActivityAlias")
        val state = packageManager.getComponentEnabledSetting(componentName)
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
}
