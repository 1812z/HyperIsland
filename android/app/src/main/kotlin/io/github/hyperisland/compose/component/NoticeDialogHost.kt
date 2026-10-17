package io.github.hyperisland.compose.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.service.AppNotice
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.window.WindowDialog

/** 「确定」解锁前的强制等待秒数。产品约定，不做成配置项。 */
private const val COUNTDOWN_SECONDS = 5

/**
 * 公告弹窗。
 *
 * 交互口径：
 *   - 正文是 Markdown（复用版本更新弹窗的渲染器），标题固定「公告」，由服务端下发内容。
 *   - 底部只有一个强调色按钮，倒计时 [COUNTDOWN_SECONDS] 秒内禁用。
 *   - 倒计时没走完时**任何**关闭方式都不生效（返回键、点遮罩同样被挡），
 *     否则「禁用按钮」就只是个摆设 —— WindowDialog 的所有关闭路径都走 onDismissRequest，
 *     这里传 null 即可整体锁住。
 *   - 确认后回调 [onConfirm]：调用方负责记下哈希并收起弹窗，本组件不持有业务状态。
 */
@Composable
internal fun NoticeDialogHost(
    notice: AppNotice?,
    onConfirm: (AppNotice) -> Unit,
) {
    val hash = notice?.hash
    // 换一条公告（哈希变了）就重新计时；同一条公告在卸载重进时也从头计时
    var remaining by remember(hash) { mutableIntStateOf(COUNTDOWN_SECONDS) }

    LaunchedEffect(hash) {
        if (hash == null) return@LaunchedEffect
        remaining = COUNTDOWN_SECONDS
        while (remaining > 0) {
            delay(1_000L)
            remaining -= 1
        }
    }

    val dismissNotice: () -> Unit = { if (notice != null) onConfirm(notice) }

    WindowDialog(
        show = notice != null,
        title = stringResource(R.string.notice_title),
        onDismissRequest = if (remaining > 0) null else dismissNotice,
    ) {
        val shown = notice
        if (shown != null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarkdownBody(shown.content, maxHeight = 320.dp)
                Button(
                    onClick = { onConfirm(shown) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    enabled = remaining <= 0,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        if (remaining > 0) {
                            stringResource(R.string.notice_confirm_countdown, remaining)
                        } else {
                            stringResource(R.string.confirm)
                        },
                    )
                }
            }
        }
    }
}
