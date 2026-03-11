package com.example.hyperisland.xposed

import android.app.Notification
import android.app.NotificationManager
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Field

/**
 * Xposed Hook类
 * 用于Hook小米下载管理器并显示灵动岛通知
 */
class DownloadHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "HyperIsland_Island"

        // 反射获取extras字段
        private var extrasField: Field? = null

        init {
            try {
                extrasField = Notification::class.java.getDeclaredField("extras")
                extrasField?.isAccessible = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.log("========================================")
        XposedBridge.log("HyperIsland: Loading package: ${lpparam.packageName}")
        XposedBridge.log("========================================")

        try {
            val nmClass = lpparam.classLoader.loadClass("android.app.NotificationManager")

            // Hook notify(String tag, int id, Notification n) - 三参数版本
            try {
                XposedHelpers.findAndHookMethod(
                    nmClass,
                    "notify",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    Notification::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            handleNotification(param.args[2] as? Notification, lpparam)
                        }
                    }
                )
                XposedBridge.log("HyperIsland: Hooked notify(String, int, Notification)")
            } catch (e: Throwable) {
                XposedBridge.log("HyperIsland: notify(String,int,Notification) not found: ${e.message}")
            }

            // Hook notify(int id, Notification n) - 两参数版本
            try {
                XposedHelpers.findAndHookMethod(
                    nmClass,
                    "notify",
                    Int::class.javaPrimitiveType,
                    Notification::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            handleNotification(param.args[1] as? Notification, lpparam)
                        }
                    }
                )
                XposedBridge.log("HyperIsland: Hooked notify(int, Notification)")
            } catch (e: Throwable) {
                XposedBridge.log("HyperIsland: notify(int,Notification) not found: ${e.message}")
            }

        } catch (e: Throwable) {
            XposedBridge.log("HyperIsland: Error hooking: ${e.message}")
        }
    }

    private fun handleNotification(notif: Notification?, lpparam: XC_LoadPackage.LoadPackageParam) {
        if (notif == null) return

        try {
            // 使用反射获取extras
            val extras = extrasField?.get(notif) as? Bundle ?: return

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            // 检查是否是下载相关的通知
            val isDownload = title.contains("正在下载") ||
                title.contains("下载", ignoreCase = true) ||
                title.contains("download", ignoreCase = true) ||
                text.contains("加速", ignoreCase = true) ||
                text.contains("下载", ignoreCase = true) ||
                extras.containsKey("progress")

            if (isDownload) {
                XposedBridge.log("")
                XposedBridge.log("╔════════════════════════════════════════╗")
                XposedBridge.log("║   🎯 DOWNLOAD NOTIFICATION FOUND!      ║")
                XposedBridge.log("╠════════════════════════════════════════╣")
                XposedBridge.log("║ Package: ${lpparam.packageName}")
                XposedBridge.log("║ Title:   $title")
                XposedBridge.log("║ Text:    $text")
                XposedBridge.log("╚════════════════════════════════════════╝")
                XposedBridge.log("")

                // 注入灵动岛参数
                IslandInjector.inject(notif, lpparam, title, text, extras)
            }
        } catch (e: Throwable) {
            // 忽略错误
        }
    }

    /**
     * 灵动岛注入器
     */
    object IslandInjector {

        fun inject(notif: Notification, lpparam: XC_LoadPackage.LoadPackageParam, title: String, text: String, extras: Bundle) {
            try {
                XposedBridge.log("HyperIsland: Starting injection...")

                val progress = extras.getInt("progress", 0)
                XposedBridge.log("HyperIsland: progress = $progress")

                // 获取application context
                val context = getContext(lpparam)
                if (context == null) {
                    XposedBridge.log("HyperIsland: ❌ Failed to get context")
                    return
                }
                XposedBridge.log("HyperIsland: ✅ Got context: ${context.javaClass.name}")

                // 构建灵动岛参数
                val islandParams = buildIslandParams(title, text, progress)
                XposedBridge.log("HyperIsland: ✅ Built island params, length=${islandParams.length}")

                // 创建图标Bundle
                val picsBundle = Bundle()
                val iconId = context.resources.getIdentifier("stat_sys_download", "drawable", "android")
                XposedBridge.log("HyperIsland: Looking for icon, id=$iconId")

                if (iconId != 0) {
                    val icon = Icon.createWithResource(context, iconId)
                    picsBundle.putParcelable("miui.focus.pic_ticker", icon)
                    XposedBridge.log("HyperIsland: ✅ Created icon")
                } else {
                    XposedBridge.log("HyperIsland: ⚠️ Icon not found, using system icon")
                    try {
                        val icon = Icon.createWithResource(context, android.R.drawable.stat_sys_download)
                        picsBundle.putParcelable("miui.focus.pic_ticker", icon)
                        XposedBridge.log("HyperIsland: ✅ Created system icon")
                    } catch (e: Exception) {
                        XposedBridge.log("HyperIsland: ❌ Failed to create system icon")
                    }
                }

                // 关键：使用反射直接修改extras
                extras.putBundle("miui.focus.pics", picsBundle)
                XposedBridge.log("HyperIsland: ✅ Put miui.focus.pics")

                extras.putString("miui.focus.param", islandParams)
                XposedBridge.log("HyperIsland: ✅ Put miui.focus.param")

                extras.putInt("miui.focus.type", 2)
                XposedBridge.log("HyperIsland: ✅ Put miui.focus.type = 2")

                // 也尝试直接设置notification.extras（双重保险）
                try {
                    val notificationExtrasField = Notification::class.java.getDeclaredField("extras")
                    notificationExtrasField.isAccessible = true
                    notificationExtrasField.set(notif, extras)
                    XposedBridge.log("HyperIsland: ✅ Set notification.extras via reflection")
                } catch (e: Exception) {
                    XposedBridge.log("HyperIsland: ⚠️ Could not set extras via reflection: ${e.message}")
                }

                XposedBridge.log("HyperIsland: ✅✅✅ INJECTION COMPLETE ✅✅✅")
                XposedBridge.log("HyperIsland: Modified notification extras with island params")

            } catch (e: Exception) {
                XposedBridge.log("HyperIsland: ❌ Injection error: ${e.message}")
                e.printStackTrace()
            }
        }

        private fun getContext(lpparam: XC_LoadPackage.LoadPackageParam): android.content.Context? {
            return try {
                val activityThread = lpparam.classLoader.loadClass("android.app.ActivityThread")
                val currentApp = activityThread.getMethod("currentApplication").invoke(null)
                currentApp as? android.content.Context
            } catch (e: Exception) {
                XposedBridge.log("HyperIsland: Context error: ${e.message}")
                // 尝试另一种方式获取context
                try {
                    val activityThread = lpparam.classLoader.loadClass("android.app.ActivityThread")
                    val getSystemContext = activityThread.getMethod("getSystemContext")
                    val systemContext = getSystemContext.invoke(null) as? android.content.Context
                    systemContext?.applicationContext
                } catch (e2: Exception) {
                    null
                }
            }
        }

        private fun buildIslandParams(title: String, content: String, progress: Int): String {
            val paramV2 = org.json.JSONObject().apply {
                put("protocol", 1)
                put("business", "download")
                put("enableFloat", true)
                put("updatable", progress >= 0 && progress < 100)

                // 状态栏数据
                put("ticker", title)
                put("tickerPic", "miui.focus.pic_ticker")

                // 息屏AOD数据
                put("aodTitle", if (progress > 0) "下载中 $progress%" else title)
                put("aodPic", "miui.focus.pic_ticker")

                // 岛数据
                val paramIsland = org.json.JSONObject().apply {
                    put("islandProperty", 1)

                    // 大岛内容
                    val bigIslandArea = org.json.JSONObject().apply {
                        val imageTextInfoLeft = org.json.JSONObject().apply {
                            put("type", 1)

                            val picInfo = org.json.JSONObject().apply {
                                put("type", 1)
                                put("pic", "miui.focus.pic_ticker")
                            }
                            put("picInfo", picInfo)

                            val textInfo = org.json.JSONObject().apply {
                                put("frontTitle", if (progress >= 100) "完成" else "下载中")
                                put("title", if (progress >= 0) "$progress%" else content)
                                put("content", "正在下载")
                                put("useHighLight", progress >= 100)
                            }
                            put("textInfo", textInfo)
                        }
                        put("imageTextInfoLeft", imageTextInfoLeft)
                    }
                    put("bigIslandArea", bigIslandArea)

                    // 小岛内容
                    val smallIslandArea = org.json.JSONObject().apply {
                        val picInfo = org.json.JSONObject().apply {
                            put("type", 1)
                            put("pic", "miui.focus.pic_ticker")
                        }
                        put("picInfo", picInfo)

                        val textInfo = org.json.JSONObject().apply {
                            put("title", if (progress >= 0) "$progress%" else "下载")
                        }
                        put("textInfo", textInfo)
                    }
                    put("smallIslandArea", smallIslandArea)
                }
                put("param_island", paramIsland)

                // 焦点通知数据
                val baseInfo = org.json.JSONObject().apply {
                    put("title", title)
                    put("content", content)
                    put("colorTitle", "#006EFF")
                    put("type", 2)
                }
                put("baseInfo", baseInfo)

                // 提示信息
                val hintInfo = org.json.JSONObject().apply {
                    put("type", 1)
                    put("title", if (progress >= 100) "下载完成" else "下载中")
                }
                put("hintInfo", hintInfo)
            }

            val root = org.json.JSONObject().apply {
                put("param_v2", paramV2)
            }

            return root.toString()
        }
    }
}
