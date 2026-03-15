package com.example.hyperisland

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodCall
import com.example.hyperisland.xposed.registeredTemplates
import java.io.ByteArrayOutputStream

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.example.hyperisland/test"
    private val TAG = "HyperIsland"
    private val REQUEST_NOTIFICATION_PERMISSION = 1001

    private var pendingResult: MethodChannel.Result? = null
    private var pendingCall: MethodCall? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestNotificationPermission()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "showProgress" -> {
                    if (checkNotificationPermission()) {
                        handleShowProgress(call, result)
                    } else {
                        pendingResult = result
                        pendingCall = call
                        requestNotificationPermission()
                    }
                }

                "showComplete" -> {
                    if (checkNotificationPermission()) {
                        handleShowComplete(call, result)
                    } else {
                        pendingResult = result
                        pendingCall = call
                        requestNotificationPermission()
                    }
                }

                "showFailed" -> {
                    if (checkNotificationPermission()) {
                        handleShowFailed(call, result)
                    } else {
                        pendingResult = result
                        pendingCall = call
                        requestNotificationPermission()
                    }
                }

                "showIndeterminate" -> {
                    if (checkNotificationPermission()) {
                        handleShowIndeterminate(call, result)
                    } else {
                        pendingResult = result
                        pendingCall = call
                        requestNotificationPermission()
                    }
                }

                "showCustom" -> {
                    if (checkNotificationPermission()) {
                        handleShowCustom(call, result)
                    } else {
                        pendingResult = result
                        pendingCall = call
                        requestNotificationPermission()
                    }
                }

                "getTemplates" -> {
                    result.success(registeredTemplates)
                }

                "getInstalledApps" -> {
                    Thread {
                        val apps = getInstalledUserApps()
                        runOnUiThread { result.success(apps) }
                    }.start()
                }

                "getNotificationChannels" -> {
                    val pkg = call.argument<String>("packageName") ?: ""
                    Thread {
                        val channels = getNotificationChannelsForPackage(pkg)
                        runOnUiThread { result.success(channels) }
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
                            process.waitFor()
                            runOnUiThread { result.success(true) }
                        } catch (e: Exception) {
                            runOnUiThread { result.error("ROOT_ERROR", e.message, null) }
                        }
                    }.start()
                }

                "isModuleActive" -> {
                    result.success(isModuleActive())
                }

                "checkPermission" -> {
                    result.success(checkNotificationPermission())
                }

                "requestPermission" -> {
                    requestNotificationPermission()
                    result.success(null)
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
    private fun getNotificationChannelsForPackage(pkg: String): List<Map<String, Any?>> {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return emptyList()
        return tryGetChannelsFromPolicyFile(pkg)
    }

    private fun tryGetChannelsFromPolicyFile(pkg: String): List<Map<String, Any?>> {
        return try {
            val xml = convertAbxPolicyToXml()
            if (xml.isEmpty()) { Log.w(TAG, "convertAbxPolicyToXml: empty"); return emptyList() }
            Log.d(TAG, "policy xml: ${xml.length} chars")
            parseTextXmlChannels(xml.toByteArray(Charsets.UTF_8), pkg)
        } catch (e: Exception) {
            Log.e(TAG, "tryGetChannelsFromPolicyFile: ${e.message}")
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

    /** 文本 XML 解析（Android 8-11）。属性名可能带 -int / -bool 后缀。 */
    private fun parseTextXmlChannels(bytes: ByteArray, targetPkg: String): List<Map<String, Any?>> {
        val result = mutableListOf<Map<String, Any?>>()
        try {
            val parser = android.util.Xml.newPullParser()
            parser.setInput(java.io.ByteArrayInputStream(bytes), "UTF-8")
            var inTarget = false
            var ev = parser.eventType
            while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                        "package" -> inTarget = parser.getAttributeValue(null, "name") == targetPkg
                        "channel" -> if (inTarget) {
                            val id = parser.getAttributeValue(null, "id") ?: ""
                            if (id.isNotEmpty()) result.add(mapOf(
                                "id"          to id,
                                "name"        to (parser.getAttributeValue(null, "name") ?: id),
                                "description" to (parser.getAttributeValue(null, "desc") ?: ""),
                                "importance"  to (
                                    (parser.getAttributeValue(null, "importance")
                                        ?: parser.getAttributeValue(null, "importance-int"))
                                        ?.toIntOrNull() ?: 3),
                            ))
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG ->
                        if (parser.name == "package") inTarget = false
                }
                ev = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseTextXmlChannels: ${e.message}")
        }
        Log.d(TAG, "text XML: ${result.size} channels for $targetPkg")
        return result
    }

    /**
     * 最小 ABX（Android Binary XML）解析器，用于读取 notification_policy.xml。
     *
     * 格式（AOSP BinaryXmlSerializer）：
     *   "ABX\0" (4 bytes magic, 已在调用方跳过)
     *   event_byte = (XmlPullParser.TYPE << 4) | 0x1
     *     0x01 START_DOC  0x11 END_DOC  0x21 START_TAG  0x31 END_TAG  0x41 TEXT
     *   String ref (0x0A): short idx [若 idx==table.size 则继续读 short len + UTF-8]
     *   String raw (0x0D): short len + UTF-8  (非 interned，不入表)
     *   Attr value: type byte + 对应数据（INT=0x04→4字节，BOOL=0x02/03→无数据…）
     *   注意：ABX 属性名无 -int/-bool 后缀，类型由 value type byte 编码。
     */
    private fun parseAbxChannels(data: ByteArray, targetPkg: String): List<Map<String, Any?>> {
        val result = mutableListOf<Map<String, Any?>>()
        val strings = mutableListOf<String>()
        // 跳过 4 字节 magic
        val buf = java.io.DataInputStream(java.io.ByteArrayInputStream(data, 4, data.size - 4))
        var inTarget = false

        fun readStr(): String {
            val t = buf.read() // -1 on EOF
            if (t == -1) throw java.io.EOFException()
            return when (t) {
                0x0A -> { // STRING_INTERNED
                    val idx = buf.readShort().toInt() and 0xFFFF
                    if (idx < strings.size) strings[idx]
                    else {
                        val len = buf.readShort().toInt() and 0xFFFF
                        String(ByteArray(len).also { buf.readFully(it) }, Charsets.UTF_8)
                            .also { strings.add(it) }
                    }
                }
                0x0D -> { // STRING (non-interned)
                    val len = buf.readShort().toInt() and 0xFFFF
                    String(ByteArray(len).also { buf.readFully(it) }, Charsets.UTF_8)
                }
                else -> { Log.w(TAG, "readStr: unexpected 0x${t.toString(16)}"); "" }
            }
        }

        fun readVal(): String {
            val t = buf.read()
            if (t == -1) throw java.io.EOFException()
            return when (t) {
                0x01 -> ""
                0x02 -> "true"
                0x03 -> "false"
                0x04 -> buf.readInt().toString()
                0x05 -> buf.readInt().toString(16)
                0x06 -> buf.readLong().toString()
                0x07 -> buf.readLong().toString(16)
                0x08 -> buf.readFloat().toString()
                0x09 -> buf.readDouble().toString()
                0x0A -> {
                    val idx = buf.readShort().toInt() and 0xFFFF
                    if (idx < strings.size) strings[idx]
                    else {
                        val len = buf.readShort().toInt() and 0xFFFF
                        String(ByteArray(len).also { buf.readFully(it) }, Charsets.UTF_8)
                            .also { strings.add(it) }
                    }
                }
                0x0B, 0x0C -> { // BYTES_HEX / BYTES_BASE64: skip bytes
                    val len = buf.readShort().toInt() and 0xFFFF
                    ByteArray(len).also { buf.readFully(it) }
                    ""
                }
                0x0D -> {
                    val len = buf.readShort().toInt() and 0xFFFF
                    String(ByteArray(len).also { buf.readFully(it) }, Charsets.UTF_8)
                }
                else -> { Log.w(TAG, "readVal: unknown 0x${t.toString(16)}"); "" }
            }
        }

        try {
            while (true) {
                val evRaw = buf.read() // -1 on EOF
                if (evRaw == -1) break
                when ((evRaw shr 4) and 0x0F) {
                    0 -> Unit  // START_DOCUMENT
                    1 -> break // END_DOCUMENT
                    2 -> {     // START_TAG
                        readStr() // namespace（忽略）
                        val name = readStr()
                        val attrCount = buf.readShort().toInt() and 0xFFFF
                        val attrs = HashMap<String, String>(attrCount)
                        repeat(attrCount) {
                            readStr() // attr namespace（忽略）
                            attrs[readStr()] = readVal()
                        }
                        // 诊断：打印前 5 个元素名及其属性
                        if (result.isEmpty() && (name == "package" || name == "channel" ||
                                name == "ranking" || name == "notification-policy")) {
                            Log.d(TAG, "ABX tag=[$name] attrs=$attrs")
                        }
                        when (name) {
                            "package" -> {
                                inTarget = attrs["name"] == targetPkg
                                Log.d(TAG, "ABX package name=[${attrs["name"]}] inTarget=$inTarget target=$targetPkg")
                            }
                            "channel" -> if (inTarget) {
                                val id = attrs["id"] ?: ""
                                if (id.isNotEmpty()) result.add(mapOf(
                                    "id"          to id,
                                    "name"        to (attrs["name"] ?: id),
                                    "description" to (attrs["desc"] ?: ""),
                                    "importance"  to (attrs["importance"]?.toIntOrNull() ?: 3),
                                ))
                            }
                        }
                    }
                    3 -> { // END_TAG
                        readStr() // namespace
                        if (readStr() == "package") inTarget = false
                    }
                    4 -> readStr() // TEXT: skip content
                    else -> Log.w(TAG, "ABX unknown event 0x${evRaw.toString(16)}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseAbxChannels stopped: ${e.message} (${result.size} found)")
        }
        Log.d(TAG, "ABX: ${result.size} channels for $targetPkg")
        return result
    }

    /** 返回所有已安装应用（排除自身），每项含 packageName / appName / icon（PNG bytes）。*/
    private fun getInstalledUserApps(): List<Map<String, Any>> {
        val pm = packageManager
        return pm.getInstalledApplications(0)
            .filter { it.packageName != packageName && (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .mapNotNull { app ->
                try {
                    val label    = pm.getApplicationLabel(app).toString()
                    val drawable = pm.getApplicationIcon(app.packageName)
                    val size = 96
                    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(canvas)
                    val stream = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.PNG, 90, stream)
                    mapOf(
                        "packageName" to app.packageName,
                        "appName"     to label,
                        "icon"        to stream.toByteArray()
                    )
                } catch (_: Exception) { null }
            }
            .sortedBy { it["appName"] as String }
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED

            if (granted && pendingCall != null && pendingResult != null) {
                // 重新执行被挂起的调用
                when (pendingCall!!.method) {
                    "showProgress" -> handleShowProgress(pendingCall!!, pendingResult!!)
                    "showComplete" -> handleShowComplete(pendingCall!!, pendingResult!!)
                    "showFailed" -> handleShowFailed(pendingCall!!, pendingResult!!)
                    "showIndeterminate" -> handleShowIndeterminate(pendingCall!!, pendingResult!!)
                    "showCustom" -> handleShowCustom(pendingCall!!, pendingResult!!)
                }
                pendingResult = null
                pendingCall = null
            } else if (!granted) {
                pendingResult?.success(false)
                pendingResult = null
                pendingCall = null
                Log.w(TAG, "Notification permission denied")
            }
        }
    }

    private fun handleShowProgress(call: MethodCall, result: MethodChannel.Result) {
        try {
            val title = call.argument<String>("title") ?: "下载中"
            val fileName = call.argument<String>("fileName") ?: ""
            val progress = call.argument<Int>("progress") ?: 0
            val speed = call.argument<String>("speed") ?: ""
            val remainingTime = call.argument<String>("remainingTime") ?: ""

            val success = HyperIslandHelper.showDownloadProgress(
                this,
                title,
                fileName,
                progress,
                speed,
                remainingTime
            )
            result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing progress", e)
            result.error("ERROR", e.message, null)
        }
    }

    private fun handleShowComplete(call: MethodCall, result: MethodChannel.Result) {
        try {
            val title = call.argument<String>("title") ?: "下载完成"
            val fileName = call.argument<String>("fileName") ?: ""

            val success = HyperIslandHelper.showDownloadComplete(
                this,
                title,
                fileName
            )
            result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing complete", e)
            result.error("ERROR", e.message, null)
        }
    }

    private fun handleShowFailed(call: MethodCall, result: MethodChannel.Result) {
        try {
            val title = call.argument<String>("title") ?: "下载失败"
            val fileName = call.argument<String>("fileName") ?: ""
            val error = call.argument<String>("error") ?: ""

            val success = HyperIslandHelper.showDownloadFailed(
                this,
                title,
                fileName,
                error
            )
            result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing failed", e)
            result.error("ERROR", e.message, null)
        }
    }

    private fun handleShowIndeterminate(call: MethodCall, result: MethodChannel.Result) {
        try {
            val title = call.argument<String>("title") ?: ""
            val content = call.argument<String>("content") ?: ""

            val success = HyperIslandHelper.showIndeterminateProgress(
                this,
                title,
                content
            )
            result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing indeterminate", e)
            result.error("ERROR", e.message, null)
        }
    }

    private fun handleShowCustom(call: MethodCall, result: MethodChannel.Result) {
        try {
            val type = call.argument<String>("type") ?: "custom"
            val title = call.argument<String>("title") ?: ""
            val content = call.argument<String>("content") ?: ""
            val icon = call.argument<String>("icon")

            val success = HyperIslandHelper.showCustomFocus(
                this,
                type,
                title,
                content,
                icon
            )
            result.success(success)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing custom", e)
            result.error("ERROR", e.message, null)
        }
    }
}
