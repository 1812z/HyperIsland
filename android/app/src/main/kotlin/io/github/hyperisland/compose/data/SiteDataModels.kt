package io.github.hyperisland.compose.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 站点静态数据的模型层。
 *
 * 数据结构与 `scripts/gen-site-data.mjs` 的产物一一对应（`schema` 目前恒为 1）。
 * 这里刻意不做版本号比较、也不拼下载地址 —— 那些都是构建期算好的，
 * 客户端只负责「显示」和「跳转」，两端逻辑越少越不容易错位。
 */
internal data class ReleaseSection(
    val title: String,
    val items: List<String>,
)

internal data class ReleaseEntry(
    val version: String,
    val date: String,
    val downloadUrl: String,
    val sections: List<ReleaseSection>,
) {
    /** 收起态那行摘要：所有条目用 ` · ` 串起来，交给单行 ellipsis 去截断。 */
    val summary: String
        get() = sections.flatMap { it.items }.joinToString(" · ")

    /**
     * 展开态交给 Markdown 渲染的源码。
     *
     * 用 `## 分类` + `- 条目` 还原成 Markdown，而不是写一套「分段 → Compose」的
     * 渲染逻辑：条目里本来就带 `**粗体**`、反引号和链接，交给现成的 MarkdownLines
     * 处理，等于少维护一个行内解析器。
     */
    val markdown: String
        get() = sections
            // 空分段很常见（`## 优化 & 修复` 后面一条都没写），留着会渲染成一个孤零零的标题
            .filter { it.items.isNotEmpty() }
            .joinToString("\n\n") { section ->
                buildString {
                    if (section.title.isNotBlank()) {
                        append("## ").append(section.title).append('\n')
                    }
                    section.items.forEach { append("- ").append(it).append('\n') }
                }.trim()
            }
}

internal data class ChangelogFeed(
    val generatedAt: String,
    val latest: String,
    val releases: List<ReleaseEntry>,
) {
    /** 下载按钮的目标：列表第一条（最新发布版），而不是本地安装的版本。 */
    val newest: ReleaseEntry? get() = releases.firstOrNull()
}

internal data class Donor(
    val name: String,
    val amount: String,
    val date: String,
    val message: String,
)

internal data class DonorBoard(
    val generatedAt: String,
    val count: Int,
    val total: String,
    val donors: List<Donor>,
) {
    /**
     * 网页是时间正序（老 → 新），App 反过来：进来先看到最近谁支持了。
     * 只在展示层反转，模型保持与服务端一致，避免两端口径打架。
     */
    val recentFirst: List<Donor> get() = donors.asReversed()
}

/** 数据来源是缓存 / 内置基线时为 true，用于在页面顶部提示「可能不是最新」。 */
internal data class SiteDataResult<T>(
    val data: T?,
    val stale: Boolean,
    val error: String?,
)

/** `generatedAt` 是服务端 UTC 时刻，展示成本地时间；解析不了就不显示。 */
internal fun formatGeneratedAt(raw: String): String = runCatching {
    Instant.parse(raw).atZone(ZoneId.systemDefault()).format(GENERATED_AT_FORMAT)
}.getOrDefault("")

private val GENERATED_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
