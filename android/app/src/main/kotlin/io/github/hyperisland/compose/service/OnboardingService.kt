package io.github.hyperisland.compose.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import io.github.hyperisland.utils.DevelopmentEnvironmentInfoProvider
import io.github.hyperisland.utils.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class OnboardingStatus(
    val lsposedActive: Boolean,
    val rootGranted: Boolean,
    val appListGranted: Boolean,
    val protocolVersion: Int,
    val androidSdkVersion: Int,
) {
    val requirementsMet: Boolean
        get() = lsposedActive && rootGranted && appListGranted &&
            protocolVersion >= 3 && androidSdkVersion >= 35
}

internal class OnboardingService(private val context: Context) {
    suspend fun checkStatus(): OnboardingStatus = withContext(Dispatchers.IO) {
        OnboardingStatus(
            lsposedActive = DevelopmentEnvironmentInfoProvider.isModuleActive(context),
            rootGranted = runCatching { RootShell.run("id").exitCode == 0 }.getOrDefault(false),
            appListGranted = hasAppListPermission(),
            protocolVersion = Settings.System.getInt(
                context.contentResolver,
                "notification_focus_protocol",
                0,
            ),
            androidSdkVersion = Build.VERSION.SDK_INT,
        )
    }

    fun hasAppListPermission(): Boolean {
        if (!supportsMiuiAppListPermission()) return true
        return ContextCompat.checkSelfPermission(context, APP_LIST_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun supportsMiuiAppListPermission(): Boolean = runCatching {
        context.packageManager.getPermissionInfo(APP_LIST_PERMISSION, 0).packageName == MIUI_PERMISSION_MANAGER
    }.getOrDefault(false)

    fun enableEmbeddedFocusUnlock() {
        XposedScopeService.requestScope(
            context,
            listOf(SYSTEM_UI_PACKAGE, XMSF_PACKAGE),
        ).getOrThrow()
    }

    companion object {
        const val APP_LIST_PERMISSION = "com.android.permission.GET_INSTALLED_APPS"
        private const val MIUI_PERMISSION_MANAGER = "com.lbe.security.miui"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val XMSF_PACKAGE = "com.xiaomi.xmsf"
    }
}
