package io.github.hyperisland.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 极简 Markdown 渲染：标题 / 列表 / 加粗 / 行内代码 / 链接 / 分隔线。
 *
 * 版本更新日志与公告共用这一份实现 —— 两处文案都是人工写在服务端，
 * 支持到「标题 + 列表 + 一点强调」就足够。不引第三方 Markdown 解析器是有意的：
 * 完整实现会把图片、HTML、内联脚本一并打开，在一个弹窗里纯属扩大攻击面。
 *
 * 标题按 `#` 的数量分级（一级 18sp 加粗 / 二级 15.5sp / 三级以下与正文同尺寸），
 * 四级以下不再细分字号 —— 弹窗内高只有 240dp，再小就看不清了。
 *
 * 超出 [maxHeight] 后内部滚动，避免一条长公告把弹窗顶出屏幕。
 */
@Composable
internal fun MarkdownBody(
    source: String,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 240.dp,
) {
    val lines = remember(source) { source.trim().lines() }
    LazyColumn(
        modifier = modifier.fillMaxWidth().heightIn(max = maxHeight),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        items(lines) { rawLine -> MarkdownLine(rawLine.trim()) }
    }
}

@Composable
private fun MarkdownLine(line: String) {
    // 标题必须按 # 的个数分级：只看「行首是不是 #」会让 # 和 ## 渲染成同一个样子，
    // 而公告正文里常见「# 总标题 + ## 分段」的写法，分不出层级等于没写标题。
    val heading = HEADING.matchEntire(line)
    when {
        line.isEmpty() -> Spacer(Modifier.height(3.dp))
        HORIZONTAL_RULE.matches(line) -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MiuixTheme.colorScheme.dividerLine),
        )
        heading != null -> {
            val level = heading.groupValues[1].length
            Text(
                text = markdownText(heading.groupValues[2].trim()),
                // 一级标题上方留一点空隙，否则它和上一条正文看起来是一段
                modifier = Modifier.padding(top = if (level == 1) 4.dp else 0.dp),
                fontSize = when (level) {
                    1 -> 18.sp
                    2 -> 15.5.sp
                    else -> 14.sp
                },
                fontWeight = if (level <= 2) FontWeight.Bold else FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
        line.startsWith("- ") || line.startsWith("* ") -> Text(
            text = markdownText(line.drop(2), prefix = "• "),
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        else -> Text(
            text = markdownText(line),
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/**
 * 行内样式解析。只认三种写法：`**粗体**`、`` `代码` ``、`[文字](链接)`。
 *
 * 链接用 [LinkAnnotation.Url] 而不是自己拼点击区域：BasicText 自带链接命中处理，
 * 这里只管把 url 标上去，跳转由 Compose 的 UriHandler 负责。
 */
private fun markdownText(source: String, prefix: String = ""): AnnotatedString =
    buildAnnotatedString {
        append(prefix)
        var cursor = 0
        for (match in INLINE_STYLE.findAll(source)) {
            if (match.range.first > cursor) append(source.substring(cursor, match.range.first))
            val bold = match.groupValues[1]
            val code = match.groupValues[2]
            val label = match.groupValues[3]
            val url = match.groupValues[4]
            when {
                bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(bold)
                }
                code.isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                    append(code)
                }
                label.isNotEmpty() && url.isNotEmpty() ->
                    withLink(LinkAnnotation.Url(url)) { append(label) }
                // 理论上到不了这里（每个分支至少命中一个分组），留一条兜底避免吞字符
                else -> append(match.value)
            }
            cursor = match.range.last + 1
        }
        if (cursor < source.length) append(source.substring(cursor))
    }

private val INLINE_STYLE = Regex("""\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)]\(([^)]+)\)""")

private val HORIZONTAL_RULE = Regex("""^(?:-{3,}|\*{3,}|_{3,})$""")

/** `#` 数量 + 标题正文。空格可有可无，# 后面什么都不写（如单独的 `###`）不算标题。 */
private val HEADING = Regex("""^(#{1,6})\s*(\S.*)$""")
