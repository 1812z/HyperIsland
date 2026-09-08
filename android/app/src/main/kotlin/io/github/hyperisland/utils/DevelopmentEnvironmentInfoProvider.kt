package io.github.hyperisland.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import io.github.hyperisland.BuildConfig
import io.github.hyperisland.XposedPrefsSyncApp

data class PackageVersionInfo(
    val versionName: String,
    val versionCode: String,
)

data class DevelopmentEnvironmentInfo(
    val appVersionName: String,
    val appVersionCode: Int,
    val androidApi: Int,
    val androidRelease: String,
    val deviceModel: String,
    val romIncremental: String,
    val hyperOsVersionName: String,
    val systemUi: PackageVersionInfo,
    val systemUiPlugin: PackageVersionInfo,
    val focusProtocolVersion: Int,
    val xposedFrameworkName: String,
    val xposedFrameworkVersion: String,
    val moduleActive: Boolean,
    val newUser: Boolean,
)

/** Provides the compatibility information used by diagnostics and anonymous analytics. */
object DevelopmentEnvironmentInfoProvider {
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val SYSTEM_UI_PLUGIN_PACKAGE = "miui.systemui.plugin"
    private val EMPTY_PACKAGE_VERSION = PackageVersionInfo("unknown", "unknown")

    @JvmStatic
    fun load(context: Context, isNewUser: Boolean): DevelopmentEnvironmentInfo {
        val appContext = context.applicationContext
        return DevelopmentEnvironmentInfo(
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            androidApi = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE.orEmpty().ifBlank { "unknown" },
            deviceModel = SystemPropertyReader.get("ro.product.marketname")
                .ifBlank { Build.MODEL.orEmpty().trim() }
                .ifBlank { "unknown" },
            romIncremental = SystemPropertyReader.get("ro.build.version.incremental")
                .ifBlank { Build.VERSION.INCREMENTAL.orEmpty().trim() }
                .ifBlank { "unknown" },
            hyperOsVersionName = SystemPropertyReader.get("ro.mi.os.version.name")
                .ifBlank { "unknown" },
            systemUi = packageVersion(appContext, SYSTEM_UI_PACKAGE),
            systemUiPlugin = packageVersion(appContext, SYSTEM_UI_PLUGIN_PACKAGE),
            focusProtocolVersion = Settings.System.getInt(
                appContext.contentResolver,
                "notification_focus_protocol",
                0,
            ),
            xposedFrameworkName = XposedPrefsSyncApp.getFrameworkName().ifBlank { "unknown" },
            xposedFrameworkVersion = XposedPrefsSyncApp.getFrameworkVersion().ifBlank { "unknown" },
            moduleActive = isModuleActive(appContext),
            newUser = isNewUser,
        )
    }

    @JvmStatic
    fun packageVersion(context: Context, packageName: String): PackageVersionInfo = runCatching {
        val info = context.packageManager.getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
        PackageVersionInfo(
            versionName = info.versionName.orEmpty().ifBlank { "unknown" },
            versionCode = info.longVersionCode.toString(),
        )
    }.getOrDefault(EMPTY_PACKAGE_VERSION)

    @JvmStatic
    fun isModuleActive(context: Context): Boolean {
        if (!XposedPrefsSyncApp.isReady()) return false
        if (!isFrameworkVersionSupported(XposedPrefsSyncApp.getFrameworkVersion())) return false
        val app = context.applicationContext as? XposedPrefsSyncApp ?: return false
        return runCatching { SYSTEM_UI_PACKAGE in app.getCurrentScope() }.getOrDefault(false)
    }

    private fun isFrameworkVersionSupported(version: String): Boolean {
        val parts = version.split('.', '-', '_')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return false
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return major > 2 || major == 2 && minor >= 0
    }
}
