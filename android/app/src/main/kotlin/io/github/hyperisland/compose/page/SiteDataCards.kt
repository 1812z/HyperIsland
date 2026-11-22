package io.github.hyperisland.compose.page

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hyperisland.compose.component.AppCard
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 更新日志页与捐赠页共用的状态卡片。
 *
 * 两个页面的加载 / 空 / 失败 / 缓存提示长得一样，写成一份，
 * 免得以后调一处样式漏掉另一处。
 */
@Composable
internal fun SiteDataMessageCard(
    text: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    showProgress: Boolean = false,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showProgress) {
                CircularProgressIndicator(size = 20.dp)
                Spacer(Modifier.width(14.dp))
            }
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                fontSize = 13.5.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            if (actionText != null && onAction != null) {
                Spacer(Modifier.width(10.dp))
                AccentTextAction(text = actionText, onClick = onAction)
            }
        }
    }
}

/**
 * 强调色的文字按钮。
 *
 * 不用 Miuix 的 `TextButton`：它在卡片正文里自带一整块按钮高度，
 * 撑得行高难看，而这里要的就是「贴着文本的一小串强调色字」。
 * 点击区靠 `padding` 的先后顺序保证 —— `clickable` 在 `padding` 之前，
 * 命中范围才包含四周留白。
 */
@Composable
internal fun AccentTextAction(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.primary,
    )
}

/** 下载 / 文档统一走系统浏览器：站内下载不走应用内 WebView，避免被当成套壳。 */
internal fun Context.openInBrowser(url: String) {
    if (url.isBlank()) return
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
