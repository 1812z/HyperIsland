package io.github.hyperisland.compose.service

import io.github.hyperisland.BuildConfig
import io.github.hyperisland.compose.data.ConfigPreset
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * HyperIsland-Hub 配置共享中心客户端。
 *
 * 只做上传 / 列表 / 详情三种公开操作，全部匿名，无需凭证。
 * 审核用的 admin 接口不在此实现，令牌不下发客户端。
 */
internal class HubException(
    val code: Int,
    val error: String,
    val detail: Any?,
) : Exception("HTTP $code $error ${detail ?: ""}")

internal object HubClient {
    // 末尾不要带斜杠（与 API 文档一致）。
    private const val BASE = "https://hyperisland-hub.1812z.top"
    private const val CONNECT_TIMEOUT = 10_000
    private const val READ_TIMEOUT = 20_000

    /** 云端预设 id 前缀，避免与本机 UUID 冲突，也用于识别来源。 */
    private const val ID_PREFIX = "hub:"

    fun isHubId(id: String): Boolean = id.startsWith(ID_PREFIX)

    /**
     * 把本地记的云端原始 id（裸 uuid，见 `ConfigPreset.hubId`）还原成列表里带前缀的形式，
     * 便于把「我上传的那条」与云端列表条目对上号。
     */
    fun cloudKey(rawId: String): String = ID_PREFIX + rawId

    /** 拉取全部已发布配置的列表元数据（不含正文）。列表不累加下载量。 */
    suspend fun listAll(): List<ConfigPreset> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ConfigPreset>()
        val (firstCode, first) = request("GET", "/api/configs?page=1")
        if (firstCode !in 200..299) return@withContext result
        appendItems(first, result)
        val pages = first.optInt("pages", 1)
        for (page in 2..pages) {
            val (code, body) = request("GET", "/api/configs?page=$page")
            if (code in 200..299) appendItems(body, result)
        }
        result
    }

    /** 拉取配置详情（含正文）。此接口会在服务端累加下载量。 */
    suspend fun detail(preset: ConfigPreset): ConfigPreset = withContext(Dispatchers.IO) {
        val id = preset.id.removePrefix(ID_PREFIX)
        val (code, body) = request("GET", "/api/configs/$id")
        if (code !in 200..299) throw HubException(code, body.optString("error"), body.opt("detail"))
        body.toDetailPreset()
    }

    /**
     * 上传本地配置。
     *
     * [deviceId] 走 `X-Device-Id` 请求头：放 body 会被写进配置正文并随列表外泄，
     * 放查询串会进访问日志与边缘缓存键，自定义头才是私有通道。服务端只存它的加盐哈希。
     *
     * [configId] 非空即更新既有配置（服务端校验归属，更新后同样要重新审核）；
     * 为空则新建。成功返回服务端响应 `{ id, status, updated?, revision? }`。
     */
    suspend fun upload(
        preset: ConfigPreset,
        deviceId: String,
        configId: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val sections = JSONObject()
        preset.sections.forEach { (id, value) -> sections.put(id, value) }
        // 显式构造 Hub 信封（形态 A）：`payload` 必须是 JSON 对象，不能依赖服务端自动包裹，
        // 否则字段形态有偏差就会被 validateSubmission 判为“payload 必须是 JSON 对象”。
        val body = JSONObject()
            .put("name", preset.title)
            .put("description", preset.content)
            .put("author", preset.author)
            // 刻意不传 version：版本号由服务端维护（新建 = 1，更新时内容变了才 +1），
            // 客户端传什么都不作数。响应里会把服务端定下的值带回来。
            .put("payload", JSONObject().put("sections", sections))
        // 更新通道：configId 必须在服务端归一化之前从 body 顶层摘除，因此这里也只放在顶层。
        configId?.takeIf { it.isNotBlank() }?.let { body.put("configId", it) }
        val (code, response) = request("POST", "/api/configs", body = body, deviceId = deviceId)
        if (code != HttpURLConnection.HTTP_ACCEPTED) {
            throw HubException(code, response.optString("error"), response.opt("detail"))
        }
        response
    }

    /**
     * 自查「自己上传的」配置走到哪一步了：审核中 / 已发布 / 已被拒绝（含拒绝原因）。
     *
     * [id] 是上传时服务端签发的原始 uuid（不带 [ID_PREFIX]），[deviceId] 走 `X-Device-Id` 头，
     * 服务端据此校验归属——只有当初上传它的那台设备查得到。与公开详情不同，它**不累加下载量**，
     * 也看得到尚未过审的条目（那几个状态在公开列表里根本不存在）。
     */
    suspend fun myConfig(id: String, deviceId: String): JSONObject = withContext(Dispatchers.IO) {
        val (code, body) = request("GET", "/api/my-configs/$id", deviceId = deviceId)
        if (code !in 200..299) throw HubException(code, body.optString("error"), body.opt("detail"))
        body
    }

    /**
     * 拉取当前公告。
     *
     * 版本范围与系统版本的筛选全在服务端完成（见 Hub 的 `_lib/notice.js`），
     * 这里不做任何本地判断：没有公告时服务端返回 `notice: null`，那是正常结果不是错误。
     *
     * [hyperOs] 传系统属性原文即可，取不到就传 "unknown" —— 服务端对未知版本一律放行，
     * 所以在客户端"猜"一个版本号只会让筛选结果偏离预期。
     */
    suspend fun notice(appVersion: String, hyperOs: String): AppNotice? = withContext(Dispatchers.IO) {
        val query = "/api/notice?app=" + encodeQuery(appVersion) + "&hyperos=" + encodeQuery(hyperOs)
        val (code, body) = request("GET", query)
        if (code !in 200..299) return@withContext null
        val node = body.optJSONObject("notice") ?: return@withContext null
        // optString 会把 JSON null 变成字符串 "null"，两个关键字段都必须先判空
        if (node.isNull("hash") || node.isNull("content")) return@withContext null
        val hash = node.optString("hash").trim()
        val content = node.optString("content")
        if (hash.isEmpty() || content.isBlank()) return@withContext null
        AppNotice(hash = hash, content = content, hyperOs = node.optString("os").trim())
    }

    private fun encodeQuery(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun appendItems(root: JSONObject, into: MutableList<ConfigPreset>) {
        val items = root.optJSONArray("items") ?: return
        for (index in 0 until items.length()) {
            items.optJSONObject(index)?.let { into += it.toSummaryPreset() }
        }
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        deviceId: String? = null,
    ): Pair<Int, JSONObject> {
        val connection = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "HyperIsland/${BuildConfig.VERSION_NAME}")
            if (deviceId != null) setRequestProperty("X-Device-Id", deviceId)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        }
        return try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            code to runCatching { JSONObject(text) }.getOrElse { JSONObject() }
        } finally {
            connection.disconnect()
        }
    }

    /** 列表条目：只有元数据，正文需再请求 [detail]。 */
    private fun JSONObject.toSummaryPreset(): ConfigPreset = ConfigPreset(
        id = ID_PREFIX + optString("id"),
        title = optString("name"),
        content = optString("description"),
        author = optString("author"),
        downloads = optLong("downloads", 0L),
        local = false,
        createdAt = parseTime(optString("publishedAt")),
        version = optString("version"),
        sections = emptyMap(),
    )

    /** 详情条目：携带 `payload.sections` 正文。 */
    private fun JSONObject.toDetailPreset(): ConfigPreset {
        val sections = LinkedHashMap<String, JSONObject>()
        val payload = objectOrNull("payload")
        val sectionsNode = payload?.objectOrNull("sections")
        when {
            sectionsNode != null -> sectionsNode.keys().forEach { key ->
                sectionsNode.optJSONObject(key)?.let { sections[key] = it }
            }
            // 兼容直接把分组挂在 payload 顶层的形态。
            payload != null -> payload.keys().forEach { key ->
                payload.optJSONObject(key)?.let { sections[key] = it }
            }
        }
        return ConfigPreset(
            id = ID_PREFIX + optString("id"),
            title = optString("name"),
            content = optString("description"),
            author = optString("author"),
            downloads = optLong("downloads", 0L),
            local = false,
            createdAt = parseTime(optString("publishedAt")),
            version = optString("version"),
            sections = sections,
        )
    }

    /** 读取对象字段；若被双重编码成字符串也能解析，避免 payload 形态差异导致丢失正文。 */
    private fun JSONObject.objectOrNull(key: String): JSONObject? {
        optJSONObject(key)?.let { return it }
        val raw = optString(key, "")
        return if (raw.isBlank()) null else runCatching { JSONObject(raw) }.getOrNull()
    }

    /** ISO8601（UTC）转 epoch 毫秒，失败返回 0。 */
    private fun parseTime(value: String): Long =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
}