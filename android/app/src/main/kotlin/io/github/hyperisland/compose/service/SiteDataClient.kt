package io.github.hyperisland.compose.service

import android.content.Context
import io.github.hyperisland.BuildConfig
import io.github.hyperisland.compose.data.ChangelogFeed
import io.github.hyperisland.compose.data.Donor
import io.github.hyperisland.compose.data.DonorBoard
import io.github.hyperisland.compose.data.ReleaseEntry
import io.github.hyperisland.compose.data.ReleaseSection
import io.github.hyperisland.compose.data.SiteDataResult
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 站点静态数据客户端：更新日志与捐赠名单。
 *
 * 数据是文档站在**构建期**从 `CHANGELOG.md` / `donors.json` 编译出来的普通 JSON
 * （见 `scripts/gen-site-data.mjs`），所以这里不需要任何后端、鉴权或证书逻辑。
 *
 * 读取顺序固定为「文件缓存 → 内置 assets 基线 → 网络」：
 * 先渲染已有内容，再在后台换新，页面永远不会因为网络慢而空着。
 * 内置基线是为了首装断网时也有完整内容 —— 它随 APK 一起发布，天然不会过期得离谱。
 *
 * 缓存穿透：静态资源的边缘缓存键是完整 URL，请求带 `?t=yyyyMMddHH`
 * 按小时分桶，等于每小时最多回源一次，既拿到新数据又不会被当成刷流量。
 */
internal object SiteDataClient {
    private const val BASE_URL = "https://hyperisland.1812z.top/data"
    private const val CONNECT_TIMEOUT_MILLIS = 10_000
    private const val READ_TIMEOUT_MILLIS = 15_000

    private const val CACHE_DIR = "site-data"
    private const val CACHE_PREFS = "HyperIslandSiteData"
    private const val ASSET_DIR = "site-data"
    private const val CHANGELOG_FILE = "changelog.json"
    private const val DONORS_FILE = "donors.json"

    /** 缓存多久算「够新」：进入页面时不超过这个时长就不再打扰网络。 */
    private const val STALE_AFTER_MILLIS = 6 * 60 * 60 * 1000L

    suspend fun loadChangelog(
        context: Context,
        force: Boolean = false,
    ): SiteDataResult<ChangelogFeed> = load(context, CHANGELOG_FILE, force, ::parseChangelog)

    suspend fun loadDonors(
        context: Context,
        force: Boolean = false,
    ): SiteDataResult<DonorBoard> = load(context, DONORS_FILE, force, ::parseDonorBoard)

    private suspend fun <T> load(
        context: Context,
        name: String,
        force: Boolean,
        parse: (String) -> T,
    ): SiteDataResult<T> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val cachedBody = readCachedBody(appContext, name)
        val cachedValue = cachedBody?.let { body -> runCatching { parse(body) }.getOrNull() }

        if (cachedValue != null && !force && isFresh(appContext, name)) {
            return@withContext SiteDataResult(cachedValue, stale = false, error = null)
        }

        val fetchResult = runCatching { fetch(appContext, name) }
        val fetchedValue = fetchResult.getOrNull()?.let { body -> runCatching { parse(body) }.getOrNull() }
        when {
            fetchedValue != null -> SiteDataResult(fetchedValue, stale = false, error = null)
            cachedValue != null -> SiteDataResult(
                data = cachedValue,
                stale = true,
                error = fetchResult.exceptionOrNull()?.let(::describeError),
            )
            else -> SiteDataResult(
                data = null,
                stale = true,
                error = fetchResult.exceptionOrNull()?.let(::describeError),
            )
        }
    }

    /** 文件缓存优先，读不到（首装 / 被清 / 磁盘写坏成空文件）再退到内置基线。 */
    private fun readCachedBody(context: Context, name: String): String? {
        val file = File(File(context.filesDir, CACHE_DIR), name)
        if (file.isFile) {
            val cached = runCatching { file.readText() }.getOrNull()
            if (!cached.isNullOrBlank()) return cached
        }
        return runCatching {
            context.assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun isFresh(context: Context, name: String): Boolean {
        val fetchedAt = cachePrefs(context).getLong(name, 0L)
        return fetchedAt > 0L && System.currentTimeMillis() - fetchedAt < STALE_AFTER_MILLIS
    }

    private fun fetch(context: Context, name: String): String {
        val connection = (URL("$BASE_URL/$name?t=${hourBucket()}").openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "HyperIsland/${BuildConfig.VERSION_NAME}")
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP $code $name")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            if (body.isBlank()) throw IOException("empty body $name")
            writeCache(context, name, body)
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun writeCache(context: Context, name: String, body: String) {
        // 缓存写失败不影响本次展示，只是下次还得回源，所以整段吞掉异常。
        runCatching {
            val dir = File(context.filesDir, CACHE_DIR)
            if (!dir.isDirectory && !dir.mkdirs()) return@runCatching
            File(dir, name).writeText(body)
            cachePrefs(context).edit().putLong(name, System.currentTimeMillis()).apply()
        }
    }

    private fun cachePrefs(context: Context) =
        context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)

    private fun hourBucket(): String = HOUR_BUCKET_FORMAT.format(LocalDateTime.now())

    private fun describeError(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName

    private fun parseChangelog(body: String): ChangelogFeed {
        val root = JSONObject(body)
        val array = root.optJSONArray("releases")
        val releases = ArrayList<ReleaseEntry>(array?.length() ?: 0)
        if (array != null) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val version = item.text("version")
                if (version.isEmpty()) continue
                releases += ReleaseEntry(
                    version = version,
                    date = item.text("date"),
                    downloadUrl = item.text("downloadUrl"),
                    sections = parseSections(item.optJSONArray("sections")),
                )
            }
        }
        return ChangelogFeed(
            generatedAt = root.text("generatedAt"),
            latest = root.text("latest").ifEmpty { releases.firstOrNull()?.version.orEmpty() },
            releases = releases,
        )
    }

    private fun parseSections(array: JSONArray?): List<ReleaseSection> {
        if (array == null) return emptyList()
        val sections = ArrayList<ReleaseSection>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val itemsArray = item.optJSONArray("items")
            val items = ArrayList<String>(itemsArray?.length() ?: 0)
            if (itemsArray != null) {
                for (itemIndex in 0 until itemsArray.length()) {
                    // 条目里可能有空行（历史记录里 `##` 分段后偶尔留空），过滤掉免得渲染出空行
                    val text = itemsArray.optString(itemIndex).trim()
                    if (text.isNotEmpty()) items += text
                }
            }
            sections += ReleaseSection(title = item.text("title"), items = items)
        }
        return sections
    }

    private fun parseDonorBoard(body: String): DonorBoard {
        val root = JSONObject(body)
        val array = root.optJSONArray("donors")
        val donors = ArrayList<Donor>(array?.length() ?: 0)
        if (array != null) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                donors += Donor(
                    name = item.text("name"),
                    amount = item.text("amount"),
                    date = item.text("date"),
                    message = item.text("message"),
                )
            }
        }
        return DonorBoard(
            generatedAt = root.text("generatedAt"),
            count = root.optInt("count", donors.size),
            total = root.text("total"),
            donors = donors,
        )
    }
}

/**
 * 取字符串字段。
 *
 * 不用 `optString`：Android 的实现在字段值是 JSON `null` 时会返回字符串 `"null"`，
 * 让「没这个字段」和「字段是空」在 UI 上表现成两种鬼东西。这里统一成空串。
 */
private fun JSONObject.text(key: String): String {
    val value = opt(key) ?: return ""
    if (value == JSONObject.NULL) return ""
    return value.toString().trim()
}

/** 缓存穿透用的时间桶格式；`DateTimeFormatter` 不可变且线程安全，可长期复用。 */
private val HOUR_BUCKET_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH")
