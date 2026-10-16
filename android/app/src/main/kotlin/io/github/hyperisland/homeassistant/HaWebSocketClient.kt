package io.github.hyperisland.homeassistant

import android.util.Base64
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.SecureRandom
import javax.net.ssl.SSLSocketFactory

/**
 * 极简 WebSocket 客户端，只服务 Home Assistant 的 `/api/websocket`。
 *
 * 为什么不用现成库：
 * - 模块里没有 okhttp/任何 WS 依赖，而这段代码要被注入到 SystemUI 进程，尽量不加体积；
 * - 用裸 [Socket] 走 `ws://` 可以绕开 Android 的明文流量策略（NetworkSecurityPolicy 只约束
 *   HttpURLConnection/OkHttp 等上层栈），局域网 HA 场景下这一点很关键；
 * - 只实现必需子集：握手、文本帧、分片、ping/pong、close，不协商 permessage-deflate。
 *
 * 生命周期是「一次性」的：连上 → 读到断开 → 回调结束。重连与退避由 [HaSession] 负责。
 *
 * **必须有超时与心跳**：TCP 半开（WiFi 切换、路由器清 NAT 表、HA 重启）时读操作会**永远阻塞**，
 * 连接既不断开也不报错，会话线程就卡死在 `latch.await()` 上 —— 表现是「界面还显示已连接，
 * 但再也没有数据，也不会自动重连」。所以这里给读加 [READ_TIMEOUT_MS] 超时，并每
 * [HEARTBEAT_INTERVAL_MS] 发一个 PING（RFC 6455 要求对端回 PONG）：健康连接永远不会触发超时，
 * 失活连接最多 [READ_TIMEOUT_MS] 后必然抛异常并触发重连。
 */
internal class HaWebSocketClient(
    private val rawUrl: String,
    private val accessToken: String,
    private val listener: Listener,
) {

    interface Listener {
        /** 服务端返回 `auth_ok`。 */
        fun onAuthenticated()

        /** 每一条已认证之后的 JSON 文本消息。 */
        fun onJson(message: org.json.JSONObject)

        /** 连接异常结束；[authFailed] 表示服务端明确拒绝令牌。 */
        fun onFailure(message: String, authFailed: Boolean)

        /** 主动 [stop] 导致的结束。 */
        fun onStopped()
    }

    private val writeLock = Any()
    private val pending = ArrayDeque<String>()

    @Volatile private var socket: Socket? = null
    @Volatile private var output: OutputStream? = null
    @Volatile private var authenticated = false
    @Volatile private var stopped = false
    @Volatile private var finished = false
    private var thread: Thread? = null
    private var heartbeatThread: Thread? = null

    fun start() {
        if (thread != null) return
        thread = Thread({ run() }, THREAD_NAME).apply {
            isDaemon = true
            start()
        }
    }

    /** 主动关闭；[Listener.onStopped] 会被回调一次。 */
    fun stop() {
        stopped = true
        runCatching { socket?.close() }
    }

    /** 发送一条 JSON 文本；认证前先入队，`auth_ok` 后按序补发。 */
    fun send(text: String) {
        synchronized(writeLock) {
            if (stopped) return
            if (!authenticated) {
                pending.addLast(text)
                return
            }
        }
        writeText(text)
    }

    // ── 连接与协议 ─────────────────────────────────────────────────────────────

    private fun run() {
        var target: Target? = null
        try {
            val resolved = resolveTarget(rawUrl)
            target = resolved

            val connection = if (resolved.secure) {
                SSLSocketFactory.getDefault().createSocket()
            } else {
                Socket()
            }
            connection.tcpNoDelay = true
            connection.connect(InetSocketAddress(resolved.host, resolved.port), CONNECT_TIMEOUT_MS)
            socket = connection
            // 握手阶段同样要限时：服务端接了 TCP 却不回 101 时，裸读会永远阻塞，
            // 会话线程就卡死在 latch.await() 上了（这正是「断了不重连」的成因之一）。
            connection.soTimeout = HANDSHAKE_TIMEOUT_MS

            val input = BufferedInputStream(connection.getInputStream())
            val stream = BufferedOutputStream(connection.getOutputStream())
            output = stream

            handshake(input, stream, resolved)
            // 认证帧也必须走 WS 帧封装（且客户端帧要加掩码）。
            // 之前这里直接往 TCP 流里写裸 JSON，HA 会判为协议错误并立刻 close(1002)，
            // 表现就是「服务端关闭连接」。
            writeFrame(OPCODE_TEXT, authFrame().toByteArray(Charsets.UTF_8))

            // 握手完成，进入长连接：放宽读超时到心跳窗口，并起心跳线程。
            connection.soTimeout = READ_TIMEOUT_MS
            startHeartbeat()

            readLoop(input)
            if (!stopped) fail("连接已断开", authFailed = false)
        } catch (t: Throwable) {
            if (stopped) {
                notifyStopped()
            } else {
                fail(describe(t, target), t is HaAuthException)
            }
        } finally {
            heartbeatThread?.interrupt()
            heartbeatThread = null
            runCatching { socket?.close() }
            socket = null
            output = null
        }
    }

    /**
     * 解析用户填写的地址。
     *
     * HA 用户习惯直接粘贴浏览器地址栏里的 `http(s)://host:port`，所以这里把 http/https
     * 一并映射成 ws/wss —— 否则 `https://…` 会因为「不支持的协议」直接在握手前失败。
     * 没写协议头时按 `http://` 处理。
     */
    private fun resolveTarget(raw: String): Target {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("地址为空")
        val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val uri = runCatching { URI(withScheme) }
            .getOrElse { throw IllegalArgumentException("地址格式不正确：$trimmed") }
        val secure = when (uri.scheme?.lowercase()) {
            "https", "wss" -> true
            "http", "ws" -> false
            else -> throw IllegalArgumentException(
                "地址协议不支持：${uri.scheme}（请用 http(s):// 或 ws(s)://）",
            )
        }
        val host = uri.host ?: authorityHost(uri)
        if (host.isNullOrBlank()) throw IllegalArgumentException("地址缺少主机名：$trimmed")
        val port = if (uri.port > 0) uri.port else if (secure) 443 else 80
        return Target(host, port, websocketPath(uri.path.orEmpty()), secure)
    }

    /** `URI.host` 对含下划线 / 中文的域名返回 null，退化成手工解析 authority。 */
    private fun authorityHost(uri: URI): String? {
        val authority = uri.authority ?: uri.schemeSpecificPart ?: return null
        return authority.substringAfterLast('@').substringBefore(':').takeIf { it.isNotBlank() }
    }

    /** 握手被拒时解释 HTTP 状态码，避免只丢一行原始状态行给用户。 */
    private fun explainHandshake(statusLine: String): String = when {
        statusLine.contains(" 404") ->
            "握手失败：服务端返回 404$statusLine —— 地址能连上但 /api/websocket 不存在，" +
                "检查端口是否指向 HA 本体，或反向代理是否转发了该路径"
        statusLine.contains(" 401") || statusLine.contains(" 403") ->
            "握手失败：服务端返回 401/403（$statusLine）—— 反向代理的访问控制拦住了请求"
        statusLine.contains(" 502") || statusLine.contains(" 503") ->
            "握手失败：网关错误（$statusLine）—— HA 本体可能没起来"
        else -> "握手失败：$statusLine"
    }

    /** 把异常翻译成用户能直接看懂的一句话（设置页会把这段文字原样显示出来）。 */
    private fun describe(t: Throwable, target: Target?): String {
        val where = target?.let { "（$it）" } ?: ""
        return when (t) {
            is HaAuthException -> t.message ?: "认证失败"
            is javax.net.ssl.SSLPeerUnverifiedException ->
                "TLS 证书校验失败$where：${t.message.orEmpty()}"
            is javax.net.ssl.SSLHandshakeException ->
                "TLS 握手失败$where：${t.message.orEmpty()}（确认该端口是否真的是 https）"
            is javax.net.ssl.SSLException ->
                "TLS 错误$where：${t.message.orEmpty()}（该端口可能不是 https，试试 http://）"
            is java.net.UnknownHostException -> "无法解析主机名$where：${t.message.orEmpty()}"
            is java.net.SocketTimeoutException ->
                if (authenticated) {
                    "心跳超时$where：${READ_TIMEOUT_MS / 1000} 秒内没收到服务端任何数据（连接已失活，自动重连）"
                } else {
                    "连接超时$where"
                }
            is java.net.ConnectException -> "无法连接$where：${t.message.orEmpty()}"
            is IllegalArgumentException -> t.message ?: "地址不正确"
            else -> (t.message ?: t.javaClass.simpleName) + where
        }
    }

    /** 解析后的连接目标，只用于日志 / 报错里回显「实际连的是哪儿」。 */
    private data class Target(val host: String, val port: Int, val path: String, val secure: Boolean) {
        override fun toString(): String = "${if (secure) "wss" else "ws"}://$host:$port$path"
    }

    /** 允许用户直接粘贴 `ws://host:8123` 或完整的 `.../api/websocket`。 */
    private fun websocketPath(path: String): String {
        val normalized = if (path.isBlank()) "" else path.trimEnd('/')
        return if (normalized.endsWith(WEBSOCKET_PATH)) normalized else normalized + WEBSOCKET_PATH
    }

    private fun authFrame(): String =
        "{\"type\":\"auth\",\"access_token\":${org.json.JSONObject.quote(accessToken)}}"

    /**
     * 心跳线程：定期发 PING，服务端必须回 PONG（RFC 6455）。
     *
     * 它的价值不在 PONG 本身，而在于**让 [socket] 的读超时永远不会静默发生**：
     * 连接健康时每个心跳间隔内都有帧进出，读不会超时；
     * 连接已失活（半开）时读会在 [READ_TIMEOUT_MS] 后抛 `SocketTimeoutException`，
     * 由 [run] 转成一次失败回调，交给 [HaSession] 退避重连。
     *
     * 写入失败（socket 已关）同样直接判失败，不必等读超时。
     */
    private fun startHeartbeat() {
        if (heartbeatThread != null) return
        heartbeatThread = Thread({
            while (!stopped && !finished) {
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
                if (stopped || finished) break
                // 认证前不发心跳：协议层虽然已经可用，但还没确认令牌，先让认证流程自己超时。
                if (!authenticated) continue
                try {
                    writeFrame(OPCODE_PING, HEARTBEAT_PAYLOAD)
                } catch (t: Throwable) {
                    fail("心跳发送失败：${t.message ?: t.javaClass.simpleName}", authFailed = false)
                    break
                }
            }
        }, HEARTBEAT_THREAD_NAME).apply {
            isDaemon = true
            start()
        }
    }

    private fun handshake(input: InputStream, stream: OutputStream, target: Target) {
        val random = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val key = Base64.encodeToString(random, Base64.NO_WRAP)
        val defaultPort = if (target.secure) 443 else 80
        val hostHeader = if (target.port == defaultPort) {
            target.host
        } else {
            "${target.host}:${target.port}"
        }
        val request = buildString {
            append("GET ").append(target.path).append(" HTTP/1.1\r\n")
            append("Host: ").append(hostHeader).append("\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Key: ").append(key).append("\r\n")
            append("Sec-WebSocket-Version: 13\r\n")
            append("\r\n")
        }
        stream.write(request.toByteArray(Charsets.US_ASCII))
        stream.flush()

        val statusLine = readLine(input) ?: throw EOFException("握手无响应")
        // 只有「令牌被拒」（auth_invalid）才算不可重试；握手失败通常是地址/网络问题，应当继续退避重连。
        if (!statusLine.contains(" 101")) throw IllegalStateException(explainHandshake(statusLine))
        // 丢弃响应头，遇到空行结束。Sec-WebSocket-Accept 不做校验：只连用户自填的地址。
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
        }
    }

    private fun readLoop(input: InputStream) {
        val message = ByteArrayOutputStream()
        while (!stopped) {
            val first = input.read()
            if (first < 0) throw EOFException("连接已断开")
            val fin = first and 0x80 != 0
            val opcode = first and 0x0F
            val second = input.read()
            if (second < 0) throw EOFException("连接已断开")
            val masked = second and 0x80 != 0
            var length = (second and 0x7F).toLong()
            if (length == 126L) {
                length = readUnsigned(input, 2)
            } else if (length == 127L) {
                length = readUnsigned(input, 8)
            }
            if (length < 0 || length > MAX_PAYLOAD_BYTES) {
                throw IllegalStateException("帧负载过大: $length")
            }
            val maskKey = if (masked) readFully(input, 4) else null
            val payload = readFully(input, length.toInt())
            if (maskKey != null) {
                for (index in payload.indices) {
                    payload[index] = (payload[index].toInt() xor maskKey[index and 3].toInt()).toByte()
                }
            }
            when (opcode) {
                OPCODE_TEXT, OPCODE_BINARY, OPCODE_CONTINUATION -> {
                    if (message.size() + payload.size > MAX_PAYLOAD_BYTES) {
                        throw IllegalStateException("消息过大")
                    }
                    message.write(payload)
                    if (fin) {
                        val text = message.toString(Charsets.UTF_8.name())
                        message.reset()
                        dispatch(text)
                    }
                }
                OPCODE_PING -> writeFrame(OPCODE_PONG, payload)
                OPCODE_PONG -> Unit
                OPCODE_CLOSE -> throw EOFException(describeClose(payload))
                else -> Unit
            }
        }
    }

    /**
     * 解析服务端 CLOSE 帧的状态码与原因。
     *
     * 「服务端关闭连接」这种没有细节的报错在排查时分不出是令牌被拒（1008）、
     * 帧格式错误（1002）还是 HA 重启（1001），所以这里把 code 翻译成人话。
     */
    private fun describeClose(payload: ByteArray): String {
        if (payload.size < 2) return "服务端关闭连接"
        val code = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        val reason = if (payload.size > 2) {
            String(payload, 2, payload.size - 2, Charsets.UTF_8).trim().take(REASON_MAX_CHARS)
        } else {
            ""
        }
        return buildString {
            append("服务端关闭连接（").append(code)
            CLOSE_REASONS[code]?.let { append(' ').append(it) }
            append(')')
            if (reason.isNotEmpty()) append('：').append(reason)
        }
    }

    private fun dispatch(text: String) {
        val json = runCatching { org.json.JSONObject(text) }.getOrNull() ?: return
        when (json.optString("type")) {
            "auth_required" -> Unit
            "auth_ok" -> {
                authenticated = true
                flushPending()
                listener.onAuthenticated()
            }
            "auth_invalid" -> {
                throw HaAuthException(json.optString("message").ifBlank { "认证失败" })
            }
        }
        listener.onJson(json)
    }

    private fun flushPending() {
        val queued = synchronized(writeLock) {
            if (pending.isEmpty()) return
            pending.toList().also { pending.clear() }
        }
        queued.forEach(::writeText)
    }

    private fun writeText(text: String) = writeFrame(OPCODE_TEXT, text.toByteArray(Charsets.UTF_8))

    /** 客户端 → 服务端的帧必须加掩码（RFC 6455）。 */
    private fun writeFrame(opcode: Int, payload: ByteArray) {
        val stream = output ?: return
        val frame = ByteArrayOutputStream(payload.size + 14)
        frame.write(0x80 or opcode)
        when {
            payload.size < 126 -> frame.write(0x80 or payload.size)
            payload.size < 0x10000 -> {
                frame.write(0x80 or 126)
                frame.write((payload.size ushr 8) and 0xFF)
                frame.write(payload.size and 0xFF)
            }
            else -> {
                frame.write(0x80 or 127)
                var shift = 56
                while (shift >= 0) {
                    frame.write(((payload.size.toLong() ushr shift) and 0xFF).toInt())
                    shift -= 8
                }
            }
        }
        val mask = ByteArray(4).also { SecureRandom().nextBytes(it) }
        frame.write(mask)
        for (index in payload.indices) {
            frame.write((payload[index].toInt() xor mask[index and 3].toInt()) and 0xFF)
        }
        synchronized(writeLock) {
            stream.write(frame.toByteArray())
            stream.flush()
        }
    }

    private fun fail(message: String, authFailed: Boolean) {
        if (finished) return
        finished = true
        listener.onFailure(message, authFailed)
    }

    private fun notifyStopped() {
        if (finished) return
        finished = true
        listener.onStopped()
    }

    // ── 字节读取 ───────────────────────────────────────────────────────────────

    private fun readFully(input: InputStream, count: Int): ByteArray {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(buffer, offset, count - offset)
            if (read < 0) throw EOFException("连接已断开")
            offset += read
        }
        return buffer
    }

    private fun readUnsigned(input: InputStream, bytes: Int): Long {
        val raw = readFully(input, bytes)
        var value = 0L
        raw.forEach { value = (value shl 8) or (it.toLong() and 0xFF) }
        return value
    }

    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte < 0) return buffer.takeIf { it.size() > 0 }?.toString(Charsets.ISO_8859_1.name())
            if (byte == '\n'.code) break
            if (byte != '\r'.code) buffer.write(byte)
            if (buffer.size() > MAX_HEADER_LINE) throw IllegalStateException("响应头过长")
        }
        return buffer.toString(Charsets.ISO_8859_1.name())
    }

    /** 认证被拒：重连没有意义，交回调用方展示错误。 */
    class HaAuthException(message: String) : IllegalStateException(message)

    private companion object {
        const val THREAD_NAME = "HyperIsland-HA-WS"
        const val HEARTBEAT_THREAD_NAME = "HyperIsland-HA-Ping"
        const val WEBSOCKET_PATH = "/api/websocket"
        const val CONNECT_TIMEOUT_MS = 10_000

        /** 握手（发请求 → 读到 101 + 空行）的最长等待。 */
        const val HANDSHAKE_TIMEOUT_MS = 15_000

        /**
         * 长连接的读超时：超过这个时间没有任何字节进来就判定连接失活。
         *
         * 必须显著大于 [HEARTBEAT_INTERVAL_MS]，否则健康连接也会被误判。
         */
        const val READ_TIMEOUT_MS = 75_000

        /** 心跳间隔；HA（aiohttp）会自动回 PONG。 */
        const val HEARTBEAT_INTERVAL_MS = 25_000L

        val HEARTBEAT_PAYLOAD = ByteArray(0)
        const val MAX_PAYLOAD_BYTES = 8 * 1024 * 1024
        const val MAX_HEADER_LINE = 8 * 1024
        const val OPCODE_CONTINUATION = 0x0
        const val OPCODE_TEXT = 0x1
        const val OPCODE_BINARY = 0x2
        const val OPCODE_CLOSE = 0x8
        const val OPCODE_PING = 0x9
        const val OPCODE_PONG = 0xA
        const val REASON_MAX_CHARS = 80

        /** RFC 6455 关闭码；只收录排查时真会遇到的几个。 */
        val CLOSE_REASONS = mapOf(
            1000 to "正常关闭",
            1001 to "服务端下线或网关断开",
            1002 to "协议错误（客户端帧格式不被接受）",
            1003 to "收到不支持的数据类型",
            1008 to "策略原因拒绝（令牌或权限问题）",
            1009 to "消息过大",
            1011 to "服务端内部错误",
        )
    }
}
