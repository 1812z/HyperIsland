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
    private val TAG = "HyperIsland"
    private val REQUEST_APP_LIST_PERMISSION = 1002

    private var pendingAppsResult: MethodChannel.Result? = null
    private var pendingAppsIncludeSystem: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isModuleActive()) {
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
                    result.success(isModuleActive())
                }

                "getFocusProtocolVersion" -> {
                    val version = Settings.System.getInt(
                        contentResolver,
                        "notification_focus_protocol",
                        0
                    )
                    result.success(version)
                }

                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    // 默认返回 false；LSPosed Hook 加载后会将此方法替换为返回 true
    fun isModuleActive(): Boolean = false

    /**
     * 获取指定包的通知渠道列表。
     *
     * 通知渠道持久化在 /data/system/notification_policy.xml，
     * 用 root 读取后用 XmlPullParser 解析，无需调用任何受限 API。
     */
    private fun getNotificationChannelsForPackage(pkg: String): List<Map<String, Any?>>? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return emptyList()
        return tryGetChannelsFromPolicyFile(pkg)
    }

    private fun tryGetChannelsFromPolicyFile(pkg: String): List<Map<String, Any?>>? {
        val xml = try {
            convertAbxPolicyToXml()
        } catch (e: Exception) {
            Log.e(TAG, "convertAbxPolicyToXml failed for $pkg: ${e.message}")
            return null
        }
        if (xml.isEmpty()) {
            Log.w(TAG, "convertAbxPolicyToXml: empty (ROOT权限不足?)")
            return null
        }

        val containsTarget = xml.contains(pkg)
        val targetIndex = xml.indexOf(pkg)
        val sanitizedXml = sanitizeInvalidXml(xml)
        Log.d(
            TAG,
            "policy xml: ${xml.length} chars, sanitized=${sanitizedXml.length} chars, targetPkg=$pkg, containsTarget=$containsTarget, targetIndex=$targetIndex"
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

            if (strictResult == null || (!strictResult.completedTargetPackage && containsTarget)) {
                Log.d(TAG, "fallback parse start: targetPkg=$pkg reason=${if (strictResult == null) "strict-error" else "strict-miss"}")
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
     * 调用系统内置 abx2xml 命令将 notification_policy.xml（ABX 二进制格式）转换为文本 XML。
     * abx2xml 在 Android 12+ 设备上由系统提供（/system/bin/abx2xml）。
     */
    private fun convertAbxPolicyToXml(): String {
        val input = "/data/system/notification_policy.xml"
        val tmp   = "/data/local/tmp/.hyp_policy.xml"

        // 依次尝试几种调用姿势，兼容不同 ROM 和 Android 版本
        val cmds = listOf(
            "abx2xml $input /dev/stdout 2>/dev/null",
            "abx2xml $input - 2>/dev/null",
            "abx2xml $input $tmp 2>/dev/null && cat $tmp; rm -f $tmp",
        )

        for (cmd in cmds) {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val out  = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                if (out.length > 50 && out.contains('<')) {
                    Log.d(TAG, "abx2xml ok: ${out.length} chars")
                    return out
                }
            } catch (e: Exception) {
                Log.d(TAG, "abx2xml attempt failed: ${e.message}")
            }
        }
        return ""
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
        if (fragmentChannels != null) {
            Log.d(TAG, "fallback fragment parser result: targetPkg=$targetPkg count=${fragmentChannels.size}")
            if (fragmentChannels.isNotEmpty() || !fragment.content.contains("<channel")) {
                return FallbackParseResult(fragmentChannels, "fallback-fragment")
            }
        }

        val scannedChannels = scanChannelsFromFragment(fragment.content)
        Log.d(TAG, "fallback channel scan result: targetPkg=$targetPkg count=${scannedChannels.size}")
        return FallbackParseResult(scannedChannels, "fallback-scan")
    }

    private fun extractTargetPackageFragment(xml: String, targetPkg: String): PackageFragment? {
        val pattern = Regex(
            """<package\b[^>]*\bname\s*=\s*(["'])${Regex.escape(targetPkg)}\1[^>]*>"""
        )
        val startMatch = pattern.find(xml) ?: return null
        val startIndex = startMatch.range.first
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

    private fun scanChannelsFromFragment(fragment: String): List<Map<String, Any?>> {
        val channelsById = LinkedHashMap<String, Map<String, Any?>>()
        var searchIndex = 0
        while (searchIndex < fragment.length) {
            val startIndex = findNextChannelTagStart(fragment, searchIndex)
            if (startIndex < 0) break
            val endIndex = findTagEnd(fragment, startIndex)
            if (endIndex < 0) break

            val tag = fragment.substring(startIndex, endIndex + 1)
            val attrs = parseXmlAttributes(tag)
            buildChannelMap(
                id = attrs["id"],
                name = attrs["name"],
                description = attrs["desc"],
                importance = attrs["importance"],
                importanceInt = attrs["importance-int"],
            )?.let { channel ->
                channelsById.putIfAbsent(channel["id"] as String, channel)
            }
            searchIndex = endIndex + 1
        }
        return channelsById.values.toList()
    }

    private fun findNextChannelTagStart(text: String, fromIndex: Int): Int {
        var searchIndex = fromIndex
        while (searchIndex < text.length) {
            val startIndex = text.indexOf("<channel", searchIndex)
            if (startIndex < 0) return -1
            val nextCharIndex = startIndex + "<channel".length
            val nextChar = text.getOrNull(nextCharIndex)
            if (nextChar == null || nextChar.isWhitespace() || nextChar == '>' || nextChar == '/') {
                return startIndex
            }
            searchIndex = nextCharIndex
        }
        return -1
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
                    val bmp = pm.getApplicationIcon(app.packageName).toBitmap(96)
                    val stream = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.PNG, 90, stream)
                    mapOf(
                        "packageName" to app.packageName,
                        "appName"     to label,
                        "icon"        to stream.toByteArray(),
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
