package com.example.hyperisland.xposed

import android.app.Notification
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import com.hyperfocus.api.FocusApi
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Field
import java.util.regex.Pattern

/**
 * Xposed Hook类 - 使用 HyperFocusApi 库
 * 用于Hook下载管理器并显示灵动岛通知
 */
class DownloadHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "HyperIsland_Island"

        // 反射获取extras字段
        private var extrasField: Field? = null

        // 存储已处理的通知，避免重复处理
        private val processedNotifications = mutableMapOf<String, NotificationInfo>()

        data class NotificationInfo(
            var lastProgress: Int,
            var lastProcessTime: Long,
            var appName: String
        )

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
            hookNotifyMethod(nmClass, lpparam, hasTag = true)

            // Hook notify(int id, Notification n) - 两参数版本
            hookNotifyMethod(nmClass, lpparam, hasTag = false)

        } catch (e: Throwable) {
            XposedBridge.log("HyperIsland: Error hooking: ${e.message}")
        }
    }

    private fun hookNotifyMethod(nmClass: Class<*>, lpparam: XC_LoadPackage.LoadPackageParam, hasTag: Boolean) {
        try {
            val paramTypes = if (hasTag) {
                arrayOf(String::class.java, Int::class.javaPrimitiveType, Notification::class.java)
            } else {
                arrayOf(Int::class.javaPrimitiveType, Notification::class.java)
            }

            XposedHelpers.findAndHookMethod(
                nmClass,
                "notify",
                *paramTypes,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val tag = if (hasTag) param.args[0] as? String else null
                        val id = if (hasTag) param.args[1] as Int else param.args[0] as Int
                        val notif = if (hasTag) param.args[2] as Notification else param.args[1] as Notification

                        handleNotification(notif, lpparam, id, tag)
                    }
                }
            )
            XposedBridge.log("HyperIsland: Hooked notify(${if (hasTag) "String, int, Notification" else "int, Notification"})")
        } catch (e: Throwable) {
            XposedBridge.log("HyperIsland: notify method not found: ${e.message}")
        }
    }

    private fun handleNotification(notif: Notification?, lpparam: XC_LoadPackage.LoadPackageParam, id: Int, tag: String?) {
        if (notif == null) return

        try {
            val extras = extrasField?.get(notif) as? Bundle ?: return

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            // 检查是否是下载相关的通知
            val isDownload = isDownloadNotification(title, text, extras)
            if (!isDownload) return

            // 从包名提取应用名（保留原有逻辑）
            val appName = lpparam.packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }

            // 从通知中提取下载的文件名（新增）
            val fileName = extractFileName(title, text, extras)

            // 创建唯一标识
            val key = "${lpparam.packageName}_${tag ?: "null"}_$id"
            val currentTime = System.currentTimeMillis()

            // 解析进度
            val progress = extractProgress(title, text, extras)

            // 获取或创建通知信息
            val info = processedNotifications.getOrPut(key) {
                NotificationInfo(progress, currentTime, appName)
            }

            // 防抖：进度相同时，1秒内不重复处理
            if (info.lastProgress == progress && currentTime - info.lastProcessTime < 1000) {
                return
            }

            // 更新记录
            info.lastProgress = progress
            info.lastProcessTime = currentTime
            info.appName = appName

            // 清理旧记录
            processedNotifications.entries.removeIf { currentTime - it.value.lastProcessTime > 10000 }

            XposedBridge.log("")
            XposedBridge.log("╔════════════════════════════════════════╗")
            XposedBridge.log("║   🎯 DOWNLOAD NOTIFICATION FOUND!      ║")
            XposedBridge.log("╠════════════════════════════════════════╣")
            XposedBridge.log("║ Package: $appName")
            XposedBridge.log("║ File:    $fileName")
            XposedBridge.log("║ Title:   $title")
            XposedBridge.log("║ Text:    $text")
            XposedBridge.log("║ Progress: $progress%")
            XposedBridge.log("╚════════════════════════════════════════╝")
            XposedBridge.log("")

            // 注入灵动岛参数（使用fileName代替appName显示）
            IslandInjector.inject(notif, lpparam, title, text, extras, progress, appName, fileName)

        } catch (e: Throwable) {
            XposedBridge.log("HyperIsland: Error handling notification: ${e.message}")
        }
    }

    private fun isDownloadNotification(title: String, text: String, extras: Bundle): Boolean {
        return title.contains("正在下载") ||
            title.contains("下载", ignoreCase = true) ||
            title.contains("download", ignoreCase = true) ||
            text.contains("加速", ignoreCase = true) ||
            text.contains("下载", ignoreCase = true) ||
            extras.containsKey("progress")
    }

    private fun extractProgress(title: String, text: String, extras: Bundle): Int {
        // 方法1: 直接从extras获取
        val progress = extras.getInt("progress", -1)
        if (progress >= 0) return progress

        // 方法2: 从android.progress获取
        val androidProgress = extras.getInt("android.progress", -1)
        if (androidProgress >= 0) return androidProgress

        // 方法3: 从percent获取
        val percentProgress = extras.getInt("percent", -1)
        if (percentProgress >= 0) return percentProgress

        // 方法4: 从文本中解析百分比
        val pattern = Pattern.compile("(\\d+)%")
        val matcher = pattern.matcher(title + text)
        if (matcher.find()) {
            return matcher.group(1)?.toIntOrNull() ?: -1
        }

        return -1
    }

    /**
     * 从通知中提取文件名
     * 支持多种格式：
     * 1. "正在下载 文件名.扩展名"
     * 2. "文件名.扩展名 下载中"
     * 3. extras 中的 android.title 或 android.text
     */
    private fun extractFileName(title: String, text: String, extras: Bundle): String {
        // 优先从 title 中提取
        val fileNameFromTitle = extractFileNameFromText(title)
        if (fileNameFromTitle.isNotEmpty()) {
            return fileNameFromTitle
        }

        // 其次从 text 中提取
        val fileNameFromText = extractFileNameFromText(text)
        if (fileNameFromText.isNotEmpty()) {
            return fileNameFromText
        }

        // 尝试从 extras 的其他字段获取
        val extraFileName = extras.getString("android.title") ?: extras.getString("android.text")
        if (extraFileName != null) {
            val fileName = extractFileNameFromText(extraFileName)
            if (fileName.isNotEmpty()) {
                return fileName
            }
        }

        // 都没找到，返回 "下载文件"
        return "下载文件"
    }

    /**
     * 从文本中提取文件名
     * 匹配常见文件名模式，如: 文件名.apk, 文件名.zip, 文件名.jpg 等
     */
    private fun extractFileNameFromText(text: String): String {
        if (text.isEmpty()) return ""

        // 移除常见的下载前缀
        var cleanText = text
        val prefixes = listOf("正在下载", "下载中", "下载", "Downloading", "Download")
        for (prefix in prefixes) {
            if (cleanText.startsWith(prefix)) {
                cleanText = cleanText.substring(prefix.length).trim()
                break
            }
        }

        // 移除常见的下载后缀
        val suffixes = listOf("下载中", "下载中...", "下载", "下载...", "Downloading", "Download")
        for (suffix in suffixes) {
            if (cleanText.endsWith(suffix)) {
                cleanText = cleanText.substring(0, cleanText.length - suffix.length).trim()
                break
            }
        }

        // 使用正则表达式匹配文件名（支持扩展名）
        // 匹配格式: 文件名.扩展名 或 文件名
        val fileNamePattern = Pattern.compile(
            "([\\u4e00-\\u9fa5\\w\\s\\-_.]+(?:\\.[a-zA-Z0-9]{2,5})?)",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = fileNamePattern.matcher(cleanText)

        if (matcher.find()) {
            var fileName = matcher.group(1)?.trim() ?: ""
            // 限制文件名长度，避免太长
            if (fileName.length > 30) {
                fileName = fileName.substring(0, 27) + "..."
            }
            return fileName
        }

        // 如果没有匹配到，返回清理后的文本（限制长度）
        return if (cleanText.length > 30) {
            cleanText.substring(0, 27) + "..."
        } else {
            cleanText
        }
    }

    /**
     * 灵动岛注入器 - 使用 HyperFocusApi 库
     */
    object IslandInjector {

        fun inject(
            notif: Notification,
            lpparam: XC_LoadPackage.LoadPackageParam,
            title: String,
            text: String,
            extras: Bundle,
            progress: Int,
            appName: String,
            fileName: String
        ) {
            try {
                val context = getContext(lpparam) ?: run {
                    XposedBridge.log("HyperIsland: ❌ Failed to get context")
                    return
                }

                XposedBridge.log("HyperIsland: ✅ Got context")

                // 使用 FocusApi
                val focusApi = FocusApi

                // 创建下载图标
                val downloadIcon = Icon.createWithResource(
                    context,
                    when {
                        progress >= 100 -> android.R.drawable.stat_sys_download_done
                        else -> android.R.drawable.stat_sys_download
                    }
                )

                // 创建图标Bundle
                val picsBundle = Bundle().apply {
                    putAll(focusApi.addpics("downloadIcon", downloadIcon))
                }

                // 构建通知内容
                val displayTitle = if (progress >= 0 && progress < 100) "下载中 $progress%" else title
                val displayContent = if (progress >= 100) "下载完成" else text

                // 创建 baseInfo - 用于焦点通知展开时显示
                val baseInfo = focusApi.baseinfo(
                    title = displayTitle,
                    colorTitle = "#006EFF",
                    content = displayContent,
                    colorContent = "#666666",
                    basetype = 1,
                    subContent = fileName,  // 使用文件名代替应用名
                    colorSubContent = "#999999"
                )

                // 创建 hintInfo - 用于摘要态显示
                val hintInfo = focusApi.hintInfo(
                    type = 1,
                    title = buildHintTitle(progress, fileName),  // 使用文件名
                    colortitle = "#006EFF",
                    content = displayContent,
                    colorContent = "#666666",
                    actionInfo = org.json.JSONObject()  // 空的actionInfo
                )

                // 创建 progressInfo - 进度条信息
                val progressInfo = if (progress >= 0 && progress < 100) {
                    focusApi.progressInfo(
                        progress = progress,
                        colorProgress = "#006EFF",
                        colorProgressEnd = "#00C853"
                    )
                } else null

                // 发送焦点通知
                val apiBundle = focusApi.sendFocus(
                    title = title,
                    cancel = progress >= 100,  // 完成后可取消
                    baseInfo = baseInfo,
                    hintInfo = hintInfo,
                    progressInfo = progressInfo,
                    addpics = picsBundle,
                    enableFloat = false,
                    picbg = null,  // 不使用自定义背景
                    picInfo = null,
                    picbgtype = 0,
                    picInfotype = 0,
                    ticker = displayTitle,
                    picticker = downloadIcon
                )

                // 添加所有参数到通知extras
                extras.putAll(apiBundle)

                XposedBridge.log("HyperIsland: ✅✅✅ INJECTION COMPLETE ✅✅✅")
                XposedBridge.log("HyperIsland: Progress=$progress%, App=$appName, File=$fileName")

            } catch (e: Exception) {
                XposedBridge.log("HyperIsland: ❌ Injection error: ${e.message}")
                e.printStackTrace()
            }
        }

        private fun buildHintTitle(progress: Int, fileName: String): String {
            return when {
                progress >= 100 -> "$fileName 下载完成"
                progress >= 0 -> "$fileName 下载中 $progress%"
                else -> "$fileName 准备中"
            }
        }

        private fun getContext(lpparam: XC_LoadPackage.LoadPackageParam): android.content.Context? {
            return try {
                val activityThread = lpparam.classLoader.loadClass("android.app.ActivityThread")
                val currentApp = activityThread.getMethod("currentApplication").invoke(null)
                currentApp as? android.content.Context
            } catch (e: Exception) {
                XposedBridge.log("HyperIsland: Context error: ${e.message}")
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
    }
}
