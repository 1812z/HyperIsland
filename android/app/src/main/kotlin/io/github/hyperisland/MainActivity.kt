package io.github.hyperisland

import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.hyperisland.core.data.NotificationChannelRepository
import io.github.hyperisland.core.service.AppService
import io.github.hyperisland.xposed.template.core.customization.FocusCustomizationEngine
import io.github.hyperisland.utils.InteractionHaptics
import io.github.hyperisland.utils.getAppIcon
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "io.github.hyperisland/test"
    private val HAPTIC_CHANNEL = "io.github.hyperisland/haptics"
    private val TAG = "HyperIsland"
    private val REQUEST_APP_LIST_PERMISSION = 1002
    private val notificationChannelRepository = NotificationChannelRepository(TAG)
    private val appService = AppService()

    private var pendingAppsResult: MethodChannel.Result? = null
    private var pendingAppsIncludeSystem: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread {
            val active = XposedPrefsSyncApp.awaitReady()
            if (!active) return@Thread

            val prefs = getSharedPreferences("FlutterSharedPreferences", android.content.Context.MODE_PRIVATE)
            val showWelcome = try {
                prefs.getBoolean("flutter.pref_show_welcome", true)
            } catch (e: Exception) { true }
            if (!showWelcome) return@Thread

            val icon = packageManager.getAppIcon(packageName)
            sendIslandWithReset(
                io.github.hyperisland.xposed.islanddispatch.IslandRequest(
                    title            = getString(R.string.island_welcome_title),
                    content          = "HyperIsland",
                    icon             = icon,
                    firstFloat       = false,
                    enableFloat      = false,
                    highlightColor   = "#E040FB",
                    showNotification = false,
                    islandOuterGlow  = true
                )
            )
        }.start()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "showTest" -> {
                    handleShowTest(result)
                }

                "showCustomTest" -> {
                    val title = call.argument<String>("title") ?: ""
                    val content = call.argument<String>("content") ?: ""
                    val clearPrevious = call.argument<Boolean>("clearPrevious") ?: true
                    val enableFloat = call.argument<Boolean>("enableFloat") ?: true
                    handleShowCustomTest(result, title, content, clearPrevious, enableFloat)
                }

                "getInstalledApps" -> {
                    val includeSystem = call.argument<Boolean>("includeSystem") ?: false
                    if (isMiuiAppListPermissionSupported() && !checkAppListPermission()) {
                        pendingAppsResult = result
                        pendingAppsIncludeSystem = includeSystem
                        requestAppListPermission()
                    } else {
                        Thread {
                            val apps = getInstalledApps(includeSystem)
                            runOnUiThread { result.success(apps) }
                        }.start()
                    }
                }

                "getNotificationChannels" -> {
                    val pkg = call.argument<String>("packageName") ?: ""
                    Thread {
                        val channels = notificationChannelRepository.getNotificationChannelsForPackage(pkg)
                        runOnUiThread {
                            if (channels == null) {
                                result.error("ROOT_REQUIRED", "无法读取通知渠道，请检查ROOT权限", null)
                            } else {
                                result.success(channels)
                            }
                        }
                    }.start()
                }

                "getAppIcon" -> {
                    val pkg = call.argument<String>("packageName") ?: ""
                    Thread {
                        val bytes = appService.getAppIconBytes(packageManager, pkg)
                        runOnUiThread { result.success(bytes) }
                    }.start()
                }

                "getFocusCustomizationSchema" -> {
                    val templateId = call.argument<String>("templateId") ?: "notification_island"
                    val rendererId = call.argument<String>("rendererId") ?: "image_text_with_buttons_4"
                    val schema = FocusCustomizationEngine.buildSchema(templateId, rendererId)
                    result.success(schema)
                }

                "mergeFocusCustomizationDefaults" -> {
                    val templateId = call.argument<String>("templateId") ?: "notification_island"
                    val rendererId = call.argument<String>("rendererId") ?: "image_text_with_buttons_4"
                    val rawConfig = call.argument<String>("config")
                    val merged = FocusCustomizationEngine.mergeWithDefaults(templateId, rendererId, rawConfig)
                    result.success(merged)
                }

                "getIslandCustomizationSchema" -> {
                    val templateId = call.argument<String>("templateId") ?: "notification_island"
                    val schema = FocusCustomizationEngine.buildIslandSchema(templateId)
                    result.success(schema)
                }

                "mergeIslandCustomizationDefaults" -> {
                    val templateId = call.argument<String>("templateId") ?: "notification_island"
                    val rawConfig = call.argument<String>("config")
                    val merged = FocusCustomizationEngine.mergeIslandWithDefaults(templateId, rawConfig)
                    result.success(merged)
                }

                "restartProcesses" -> {
                    val commands = call.argument<List<String>>("commands") ?: emptyList()
                    Thread {
                        try {
                            // 通过 stdin 写命令，兼容 Magisk / KernelSU / APatch
                            val process = Runtime.getRuntime().exec("su")
                            val writer = java.io.DataOutputStream(process.outputStream)
                            for (cmd in commands) {
                                writer.writeBytes("$cmd\n")
                            }
                            writer.writeBytes("exit\n")
                            writer.flush()
                            val exitCode = process.waitFor()
                            if (exitCode != 0) {
                                runOnUiThread {
                                    result.error("ROOT_REQUIRED", "Root permission denied (exit $exitCode)", null)
                                }
                            } else {
                                runOnUiThread { result.success(true) }
                            }
                        } catch (e: Exception) {
                            runOnUiThread { result.error("ROOT_ERROR", e.message, null) }
                        }
                    }.start()
                }

                "isModuleActive" -> {
                    Thread {
                        val active = XposedPrefsSyncApp.awaitReady()
                        runOnUiThread { result.success(active) }
                    }.start()
                }

                "getLSPosedApiVersion" -> {
                    Thread {
                        val ready = XposedPrefsSyncApp.awaitReady()
                        val version = if (ready) XposedPrefsSyncApp.getApiVersion() else 0
                        runOnUiThread { result.success(version) }
                    }.start()
                }

                "getFocusProtocolVersion" -> {
                    val version = Settings.System.getInt(
                        contentResolver,
                        "notification_focus_protocol",
                        0
                    )
                    result.success(version)
                }

                "setDesktopIconVisible" -> {
                    val visible = call.argument<Boolean>("visible") ?: true
                    try {
                        appService.setDesktopIconVisible(packageManager, packageName, visible)
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("ERROR", e.message, null)
                    }
                }

                "isDesktopIconVisible" -> {
                    try {
                        val visible = appService.isDesktopIconVisible(packageManager, packageName)
                        result.success(visible)
                    } catch (e: Exception) {
                        result.error("ERROR", e.message, null)
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, HAPTIC_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "button" -> result.success(InteractionHaptics.performButton(this))
                    "toggle" -> result.success(InteractionHaptics.performToggle(this))
                    "sliderTick" -> result.success(InteractionHaptics.performSliderTick(this))
                    else -> result.notImplemented()
                }
            }
    }

    fun isModuleActive(): Boolean = XposedPrefsSyncApp.isReady()

    /** 返回已安装应用列表（排除自身），每项含 packageName / appName / icon / isSystem。
     *  includeSystem=false 时仅返回第三方应用。 */
    private fun getInstalledApps(includeSystem: Boolean): List<Map<String, Any>> {
        return appService.getInstalledApps(
            packageManager = packageManager,
            selfPackageName = packageName,
            includeSystem = includeSystem,
        )
    }

    private companion object {
        const val PERM_GET_INSTALLED_APPS = "com.android.permission.GET_INSTALLED_APPS"
        const val PERM_MANAGER_MIUI       = "com.lbe.security.miui"
    }

    /** 返回 MIUI 是否支持动态申请获取应用列表权限。 */
    private fun isMiuiAppListPermissionSupported(): Boolean {
        return try {
            val info = packageManager.getPermissionInfo(PERM_GET_INSTALLED_APPS, 0)
            info != null && info.packageName == PERM_MANAGER_MIUI
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun checkAppListPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, PERM_GET_INSTALLED_APPS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAppListPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(PERM_GET_INSTALLED_APPS),
            REQUEST_APP_LIST_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_APP_LIST_PERMISSION) {
            // 无论是否授权，都执行查询（未授权时返回有限列表）
            val r = pendingAppsResult
            val incSys = pendingAppsIncludeSystem
            pendingAppsResult = null
            if (r != null) {
                Thread {
                    val apps = getInstalledApps(incSys)
                    runOnUiThread { r.success(apps) }
                }.start()
            }
        }

    }

    private fun handleShowTest(result: MethodChannel.Result) {
        try {
            val icon = packageManager.getAppIcon(packageName)
            sendIslandWithReset(
                io.github.hyperisland.xposed.islanddispatch.IslandRequest(
                    title            = getString(R.string.island_welcome_title),
                    content          = "HyperIsland",
                    icon             = icon,
                    firstFloat       = false,
                    highlightColor   = "#E040FB",
                    showNotification = true,
                    islandOuterGlow  = true
                )
            )
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing test notification", e)
            result.error("ERROR", e.message, null)
        }
    }

    private fun handleShowCustomTest(
        result: MethodChannel.Result,
        customTitle: String,
        customContent: String,
        clearPrevious: Boolean,
        enableFloat: Boolean,
    ) {
        try {
            val icon = packageManager.getAppIcon(packageName)
            val title = customTitle.ifEmpty { getString(R.string.island_welcome_title) }
            val content = customContent.ifEmpty { "HyperIsland" }
            val request = io.github.hyperisland.xposed.islanddispatch.IslandRequest(
                title            = title,
                content          = content,
                icon             = icon,
                firstFloat       = false,
                enableFloat      = enableFloat,
                clearBeforePost  = clearPrevious,
                highlightColor   = "#E040FB",
                showNotification = true,
            )
            io.github.hyperisland.xposed.islanddispatch.IslandDispatcher.sendBroadcast(this, request)
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing custom test notification", e)
            result.error("ERROR", e.message, null)
        }
    }

    private fun sendIslandWithReset(request: io.github.hyperisland.xposed.islanddispatch.IslandRequest) {
        val cancelIntent = Intent(io.github.hyperisland.xposed.islanddispatch.IslandDispatcher.ACTION_CANCEL).apply {
            putExtra(
                io.github.hyperisland.xposed.islanddispatch.IslandDispatcher.EXTRA_NOTIF_ID,
                request.notifId
            )
        }
        sendOrderedBroadcast(
            cancelIntent,
            io.github.hyperisland.xposed.islanddispatch.IslandDispatcher.PERM,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    io.github.hyperisland.xposed.islanddispatch.IslandDispatcher.sendBroadcast(this@MainActivity, request)
                }
            },
            null,
            0,
            null,
            null
        )
    }
}
