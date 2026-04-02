package io.github.hyperisland

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import java.util.LinkedHashMap

class MainActivity : FlutterActivity() {
    private data class StrictParseResult(
        val channels: List<Map<String, Any?>>,
        val enteredTargetPackage: Boolean,
        val completedTargetPackage: Boolean,
    )

    private data class PackageFragment(
        val content: String,
        val endReason: String,
        val hasClosingTag: Boolean,
    )

    private data class FallbackParseResult(
        val channels: List<Map<String, Any?>>,
        val source: String,
    )

    private val CHANNEL = "io.github.hyperisland/test"
    private val HAPTIC_CHANNEL = "io.github.hyperisland/haptics"
    private val TAG = "HyperIsland"
    private val REQUEST_APP_LIST_PERMISSION = 1002

    private var pendingAppsResult: MethodChannel.Result? = null
    private var pendingAppsIncludeSystem: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isModuleActive()) {
            val prefs = getSharedPreferences("FlutterSharedPreferences", android.content.Context.MODE_PRIVATE)
            val showWelcome = try {
                prefs.getBoolean("flutter.pref_show_welcome", true)
            } catch (e: Exception) { true }

            if (showWelcome) {
                val icon = packageManager.getAppIcon(packageName)
                io.github.hyperisland.xposed.IslandDispatcher.sendBroadcast(
                    this,
                    io.github.hyperisland.xposed.IslandRequest(
                        title            = getString(R.string.island_welcome_title),
                        content          = "HyperIsland",
                        icon             = icon,
                        firstFloat       = false,
                        enableFloat      = false,
                        highlightColor   = "#E040FB",
                        showNotification = false,
                    )
                )
            }
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "showTest" -> {
                    // 通过广播由 SystemUI 发送，无需本地通知权限
                    handleShowTest(result)
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
                        val channels = getNotificationChannelsForPackage(pkg)
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
                        try {
                            val pm = packageManager
                            val bmp = pm.getApplicationIcon(pkg).toBitmap(96)
                            val stream = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.PNG, 90, stream)
                            runOnUiThread { result.success(stream.toByteArray()) }
                        } catch (e: Exception) {
                            runOnUiThread { result.success(null) }
                        }
                    }.start()
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
                        val active = HyperIslandApp.awaitReady()
                        runOnUiThread { result.success(active) }
                    }.start()
                }

                "getLSPosedApiVersion" -> {
                    Thread {
                        val ready = HyperIslandApp.awaitReady()
                        val version = if (ready) HyperIslandApp.getApiVersion() else 0
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
                        val componentName = android.content.ComponentName(
                            packageName,
                            "$packageName.MainActivityAlias"
                        )
                        val newState = if (visible) {
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        } else {
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        }
                        packageManager.setComponentEnabledSetting(
                            componentName,
                            newState,
                            PackageManager.DONT_KILL_APP
                        )
                        result.success(true)
                    } catch (e: Exception) {
                        result.error("ERROR", e.message, null)
                    }
                }

                "isDesktopIconVisible" -> {
                    try {
                        val componentName = android.content.ComponentName(
                            packageName,
                            "$packageName.MainActivityAlias"
                        )
                        val state = packageManager.getComponentEnabledSetting(componentName)
                        val visible = state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
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

    fun isModuleActive(): Boolean = HyperIslandApp.isReady()

    /**
     * 获取指定包的通知渠道列表。
     *
     * 通知渠道持久化在 /data/system/notification_policy.xml，
     * 用 root 直接读取 ABX 原始数据后在应用内解码为文本 XML，再交给 XmlPullParser 解析。
     */
    private fun getNotificationChannelsForPackage(pkg: String): List<Map<String, Any?>>? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return emptyList()
        return tryGetChannelsFromPolicyFile(pkg)
    }

    private fun tryGetChannelsFromPolicyFile(pkg: String): List<Map<String, Any?>>? {
        val xml = try {
            convertAbxPolicyToXml()
        } catch (e: Exception) {
            Log.e(TAG, "convertAbxPolicyToXml failed for $pkg: ${e.message}", e)
            return null
        }
        if (xml.isEmpty()) {
            Log.w(TAG, "convertAbxPolicyToXml: empty (ROOT权限不足?)")
            return null
        }

        val sanitizedXml = sanitizeInvalidXml(xml)
        Log.d(
            TAG,
            "policy xml: ${xml.length} chars, sanitized=${sanitizedXml.length} chars, targetPkg=$pkg"
        )

        return try {
            val strictResult = try {
                parseTextXmlChannels(sanitizedXml, pkg)
            } catch (e: Exception) {
                Log.e(TAG, "strict parse failed for $pkg: ${e.message}")
                null
            }

            if (strictResult != null) {
                Log.d(
                    TAG,
                    "strict parse state: targetPkg=$pkg entered=${strictResult.enteredTargetPackage} completed=${strictResult.completedTargetPackage} count=${strictResult.channels.size}"
                )
                if (strictResult.completedTargetPackage) {
                    logChannelSource(pkg, "strict", strictResult.channels.size)
                    return strictResult.channels
                }
            }

            if (strictResult == null) {
                Log.d(TAG, "fallback parse start: targetPkg=$pkg reason=strict-error")
                val fallbackResult = parseTextXmlChannelsFallback(sanitizedXml, pkg)
                if (fallbackResult != null) {
                    logChannelSource(pkg, fallbackResult.source, fallbackResult.channels.size)
                    return fallbackResult.channels
                }
            }

            logChannelSource(pkg, "empty", 0)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "tryGetChannelsFromPolicyFile parse flow failed for $pkg: ${e.message}", e)
            logChannelSource(pkg, "empty", 0)
            emptyList()
        }
    }

    /**
     * 将 notification_policy.xml（ABX 二进制格式）转换为文本 XML。
     *
     * 保持原有“拿到文本 XML 再解析”的调用边界，但底层不再依赖系统 abx2xml，
     * 而是直接读取源文件字节并交给 [AbxXmlDecoder] 处理。
     */
    private fun convertAbxPolicyToXml(): String {
        cleanupLegacyPolicyTempFiles()

        val policyBytes = try {
            readNotificationPolicyBytes()
        } catch (e: Exception) {
            Log.e(TAG, "readNotificationPolicyBytes failed: ${e.message}", e)
            cleanupLegacyPolicyTempFiles()
            return ""
        }

        if (policyBytes.isEmpty()) {
            cleanupLegacyPolicyTempFiles()
            return ""
        }

        return try {
            val xml = AbxXmlDecoder.decode(policyBytes)
            Log.d(TAG, "local abx2xml ok: abx=${policyBytes.size} bytes, xml=${xml.length} chars")
            xml
        } catch (e: Exception) {
            Log.e(TAG, "AbxXmlDecoder failed: ${e.message}", e)
            ""
        } finally {
            cleanupLegacyPolicyTempFiles()
        }
    }

    /** 直接从系统 notification_policy.xml 读取 ABX 原始字节。 */
    private fun readNotificationPolicyBytes(): ByteArray {
        val input = "/data/system/notification_policy.xml"
        val result = RootShell.run("cat $input")
        if (result.exitCode != 0) {
            Log.d(
                TAG,
                "notification_policy read failed: exit=${result.exitCode}, bytes=${result.stdout.size}, stderr=${result.stderr.take(120)}"
            )
            return byteArrayOf()
        }

        if (!AbxXmlDecoder.isAbx(result.stdout)) {
            Log.d(TAG, "notification_policy read failed: expected ABX, got ${result.stdout.size} bytes")
            return byteArrayOf()
        }

        Log.d(TAG, "notification_policy read ok: ${result.stdout.size} bytes")
        return result.stdout
    }

    /** 清理旧方案遗留的临时 policy 快照文件。 */
    private fun cleanupLegacyPolicyTempFiles() {
        val tempFiles = listOf(
            "/data/local/tmp/.hyp_policy.xml",
            "/data/local/tmp/.hyp_policy_snapshot.abx",
        )
        try {
            val result = RootShell.run("rm -f ${tempFiles.joinToString(separator = " ")} 2>/dev/null")
            if (result.exitCode != 0) {
                Log.d(TAG, "policy temp cleanup failed: exit=${result.exitCode}, stderr=${result.stderr.take(120)}")
            }
        } catch (e: Exception) {
            Log.d(TAG, "policy temp cleanup failed: ${e.message}")
        }
    }

    /** 文本 XML 严格解析。命中目标 package 后在该 package 结束时立即返回。 */
    private fun parseTextXmlChannels(xml: String, targetPkg: String): StrictParseResult {
        val result = mutableListOf<Map<String, Any?>>()
        val parser = android.util.Xml.newPullParser()
        parser.setInput(java.io.StringReader(xml))

        var inTarget = false
        var enteredTarget = false
        var ev = parser.eventType
        while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (ev) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                    "package" -> {
                        val packageName = parser.getAttributeValue(null, "name")
                        if (packageName == targetPkg) {
                            inTarget = true
                            enteredTarget = true
                            Log.d(TAG, "strict parse entered target package: $targetPkg")
                        }
                    }

                    "channel" -> if (inTarget) {
                        buildChannelMap(
                            id = parser.getAttributeValue(null, "id"),
                            name = parser.getAttributeValue(null, "name"),
                            description = parser.getAttributeValue(null, "desc"),
                            importance = parser.getAttributeValue(null, "importance"),
                            importanceInt = parser.getAttributeValue(null, "importance-int"),
                        )?.let(result::add)
                    }
                }

                org.xmlpull.v1.XmlPullParser.END_TAG -> if (parser.name == "package" && inTarget) {
                    if (result.isNotEmpty()) {
                        Log.d(TAG, "strict parse completed target package: $targetPkg, count=${result.size}")
                        return StrictParseResult(
                            channels = result,
                            enteredTargetPackage = enteredTarget,
                            completedTargetPackage = true,
                        )
                    } else {
                        // 该 package 条目无 channel（如工作空间副本），继续查找下一个同名 package
                        Log.d(TAG, "strict parse: $targetPkg entry had no channels, continuing search")
                        inTarget = false
                    }
                }
            }
            ev = parser.next()
        }

        return StrictParseResult(
            channels = result,
            enteredTargetPackage = enteredTarget,
            completedTargetPackage = false,
        )
    }

    private fun parseTextXmlChannelsFallback(xml: String, targetPkg: String): FallbackParseResult? {
        val fragment = extractTargetPackageFragment(xml, targetPkg)
        if (fragment == null) {
            Log.d(TAG, "fallback fragment not found: targetPkg=$targetPkg")
            return null
        }

        Log.d(
            TAG,
            "fallback fragment found: targetPkg=$targetPkg endReason=${fragment.endReason} hasClosingTag=${fragment.hasClosingTag} length=${fragment.content.length}"
        )

        val fragmentChannels = tryParseChannelsFromFragment(fragment)
        if (fragmentChannels == null) return null
        Log.d(TAG, "fallback fragment parser result: targetPkg=$targetPkg count=${fragmentChannels.size}")
        return FallbackParseResult(fragmentChannels, "fallback-fragment")
    }

    private fun extractTargetPackageFragment(xml: String, targetPkg: String): PackageFragment? {
        val pattern = Regex(
            """<package\b[^>]*\bname\s*=\s*(["'])${Regex.escape(targetPkg)}\1[^>]*>"""
        )
        val startMatch = pattern.find(xml) ?: return null
        val startIndex = startMatch.range.first
        if (startMatch.value.trimEnd().endsWith("/>")) {
            return PackageFragment(
                content = startMatch.value,
                endReason = "self-closing",
                hasClosingTag = true,
            )
        }

        val closingTag = "</package>"
        val closingIndex = xml.indexOf(closingTag, startIndex)
        if (closingIndex >= 0) {
            return PackageFragment(
                content = xml.substring(startIndex, closingIndex + closingTag.length),
                endReason = "closing-tag",
                hasClosingTag = true,
            )
        }

        val nextPackageIndex = xml.indexOf("<package", startIndex + startMatch.value.length)
        if (nextPackageIndex >= 0) {
            return PackageFragment(
                content = xml.substring(startIndex, nextPackageIndex),
                endReason = "next-package",
                hasClosingTag = false,
            )
        }

        return PackageFragment(
            content = xml.substring(startIndex),
            endReason = "eof",
            hasClosingTag = false,
        )
    }

    private fun tryParseChannelsFromFragment(fragment: PackageFragment): List<Map<String, Any?>>? {
        val parser = android.util.Xml.newPullParser()
        val wrappedXml = buildString {
            append("<root>")
            append(fragment.content)
            if (!fragment.hasClosingTag) append("</package>")
            append("</root>")
        }

        return try {
            parser.setInput(java.io.StringReader(wrappedXml))
            val channelsById = LinkedHashMap<String, Map<String, Any?>>()
            var ev = parser.eventType
            while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (ev == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "channel") {
                    buildChannelMap(
                        id = parser.getAttributeValue(null, "id"),
                        name = parser.getAttributeValue(null, "name"),
                        description = parser.getAttributeValue(null, "desc"),
                        importance = parser.getAttributeValue(null, "importance"),
                        importanceInt = parser.getAttributeValue(null, "importance-int"),
                    )?.let { channel ->
                        channelsById.putIfAbsent(channel["id"] as String, channel)
                    }
                }
                ev = parser.next()
            }
            channelsById.values.toList()
        } catch (e: Exception) {
            Log.d(TAG, "fallback fragment parser failed: ${e.message}")
            null
        }
    }

    private fun findTagEnd(text: String, startIndex: Int): Int {
        var quote: Char? = null
        var index = startIndex
        while (index < text.length) {
            val ch = text[index]
            if (quote == null) {
                if (ch == '\'' || ch == '"') {
                    quote = ch
                } else if (ch == '>') {
                    return index
                }
            } else if (ch == quote) {
                quote = null
            }
            index += 1
        }
        return -1
    }

    private fun parseXmlAttributes(tag: String): Map<String, String> {
        val attrs = linkedMapOf<String, String>()
        val attrPattern = Regex("""([A-Za-z0-9_:-]+)\s*=\s*(["'])(.*?)\2""")
        attrPattern.findAll(tag).forEach { match ->
            attrs[match.groupValues[1]] = match.groupValues[3]
        }
        return attrs
    }

    private fun sanitizeInvalidXml(xml: String): String {
        var removedEntityRefs = 0
        val sanitizedEntities = Regex("""&#(x[0-9A-Fa-f]+|\d+);""").replace(xml) { match ->
            val raw = match.groupValues[1]
            val codePoint = if (raw.startsWith("x", ignoreCase = true)) {
                raw.substring(1).toIntOrNull(16)
            } else {
                raw.toIntOrNull()
            }
            if (codePoint != null && !isValidXmlCodePoint(codePoint)) {
                removedEntityRefs += 1
                ""
            } else {
                match.value
            }
        }

        var removedRawChars = 0
        val sanitizedText = buildString(sanitizedEntities.length) {
            sanitizedEntities.forEach { ch ->
                if (isValidXmlChar(ch)) {
                    append(ch)
                } else {
                    removedRawChars += 1
                }
            }
        }

        if (removedEntityRefs > 0 || removedRawChars > 0) {
            Log.w(
                TAG,
                "sanitizeInvalidXml removed invalid content: entityRefs=$removedEntityRefs rawChars=$removedRawChars"
            )
        }
        return sanitizedText
    }

    private fun isValidXmlCodePoint(codePoint: Int): Boolean {
        return codePoint == 0x9 ||
            codePoint == 0xA ||
            codePoint == 0xD ||
            codePoint in 0x20..0xD7FF ||
            codePoint in 0xE000..0xFFFD ||
            codePoint in 0x10000..0x10FFFF
    }

    private fun isValidXmlChar(ch: Char): Boolean {
        return isValidXmlCodePoint(ch.code)
    }

    private fun buildChannelMap(
        id: String?,
        name: String?,
        description: String?,
        importance: String?,
        importanceInt: String?,
    ): Map<String, Any?>? {
        val channelId = id?.takeIf { it.isNotEmpty() } ?: return null
        return mapOf(
            "id" to channelId,
            "name" to (name ?: channelId),
            "description" to (description ?: ""),
            "importance" to ((importance ?: importanceInt)?.toIntOrNull() ?: 3),
        )
    }

    private fun logChannelSource(pkg: String, source: String, count: Int) {
        Log.d(TAG, "text XML result: targetPkg=$pkg source=$source count=$count")
    }

    /** 返回已安装应用列表（排除自身），每项含 packageName / appName / icon / isSystem。
     *  includeSystem=false 时仅返回第三方应用。 */
    private fun getInstalledApps(includeSystem: Boolean): List<Map<String, Any>> {
        val pm = packageManager
        return pm.getInstalledApplications(0)
            .filter { app ->
                app.packageName != packageName &&
                (includeSystem || (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0)
            }
            .mapNotNull { app ->
                try {
                    val label    = pm.getApplicationLabel(app).toString()
                    mapOf(
                        "packageName" to app.packageName,
                        "appName"     to label,
                        "isSystem"    to ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
                    )
                } catch (_: Exception) { null }
            }
            .sortedBy { it["appName"] as String }
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
            io.github.hyperisland.xposed.IslandDispatcher.sendBroadcast(
                this,
                io.github.hyperisland.xposed.IslandRequest(
                    title            = getString(R.string.island_welcome_title),
                    content          = "HyperIsland",
                    icon             = icon,
                    firstFloat       = false,
                    highlightColor   = "#E040FB",
                    showNotification = true,
                )
            )
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing test notification", e)
            result.error("ERROR", e.message, null)
        }
    }
}
