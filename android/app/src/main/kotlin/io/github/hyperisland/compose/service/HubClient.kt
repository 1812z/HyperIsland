package io.github.hyperisland.compose.service

import android.content.Context
import io.github.hyperisland.BuildConfig
import io.github.hyperisland.compose.data.ConfigPreset
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID
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

    /** 安装标识的存放位置。与统计服务各自独立，互不影响。 */
    private const val STATE_PREFS = "HyperIslandHub"
    private const val KEY_INSTALL_ID = "install_id"

    fun isHubId(id: String): Boolean = id.startsWith(ID_PREFIX)

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

    /**
     * 拉取配置详情（含正文）。
     *
     * 必须带安装标识：服务端按它给下载量去重，同一台设备反复拉同一份配置只算一次。
     * 缺标识或格式不对会被服务端以 400 install_id_required 拒绝。
     */
    suspend fun detail(context: Context, preset: ConfigPreset): ConfigPreset = withContext(Dispatchers.IO) {
        val id = preset.id.removePrefix(ID_PREFIX)
        val (code, body) = request("GET", "/api/configs/$id", installId = installIdentifier(context))
        if (code !in 200..299) throw HubException(code, body.optString("error"), body.opt("detail"))
        body.toDetailPreset()
    }

    /** 上传本地配置。成功返回服务端响应 `{ id, status }`。 */
    suspend fun upload(preset: ConfigPreset): JSONObject = withContext(Dispatchers.IO) {
        val sections = JSONObject()
        preset.sections.forEach { (id, value) -> sections.put(id, value) }
        // 显式构造 Hub 信封（形态 A）：`payload` 必须是 JSON 对象，不能依赖服务端自动包裹，
        // 否则字段形态有偏差就会被 validateSubmission 判为“payload 必须是 JSON 对象”。
        val body = JSONObject()
            .put("name", preset.title)
            .put("description", preset.content)
            .put("author", preset.author)
            .put("version", preset.version.ifBlank { "1.0.0" })
            .put("payload", JSONObject().put("sections", sections))
        val (code, response) = request("POST", "/api/configs", body = body)
        if (code != HttpURLConnection.HTTP_ACCEPTED) {
            throw HubException(code, response.optString("error"), response.opt("detail"))
        }
        response
    }

    private fun appendItems(root: JSONObject, into: MutableList<ConfigPreset>) {
        val items = root.optJSONArray("items") ?: return
        for (index in 0 until items.length()) {
            items.optJSONObject(index)?.let { into += it.toSummaryPreset() }
        }
    }

    /**
     * 安装标识：首次调用时生成 UUID 并落盘，之后固定不变。
     *
     * 它只用于让服务端统计"多少台设备下载过"，不含任何可识别信息。
     * 清除应用数据或换设备会得到新值（那一份配置会被当成新设备再计一次），属预期行为。
     * 不要为了调整统计数字而主动改变它。
     */
    @Synchronized
    private fun installIdentifier(context: Context): String {
        val prefs = context.applicationContext
            .getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_INSTALL_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALL_ID, fresh).apply()
        return fresh
    }

    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        installId: String? = null,
    ): Pair<Int, JSONObject> {
        val connection = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "HyperIsland/${BuildConfig.VERSION_NAME}")
            if (installId != null) setRequestProperty("X-Install-Id", installId)
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