package io.github.hyperisland.compose.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import io.github.hyperisland.R

/**
 * 一键配置预设。
 *
 * @param id 预设唯一标识，用作列表 key，也用于后续远程下发时去重。
 * @param title 卡片标题。
 * @param subtitle 卡片副标题。
 * @param author 作者名，展示在卡片底部左侧（Contacts 图标）。
 * @param downloads 下载量，展示在卡片底部右侧（Download 图标）。
 * @param apply 应用预设时执行的动作，直接写入 [FlutterPrefsRepository]。
 */
internal data class PresetConfig(
    val id: String,
    val title: String,
    val subtitle: String,
    val author: String,
    val downloads: Long,
    val apply: (FlutterPrefsRepository) -> Unit,
)

/**
 * 内置预设列表。
 *
 * 目前只内置一个空预设用于打通 UI。后续远程预设合并到这里，或与 [rememberBuiltInPresets]
 * 的结果拼接后一起展示。
 */
@Composable
internal fun rememberBuiltInPresets(): List<PresetConfig> {
    val testTitle = stringResource(R.string.preset_test_title)
    val testSubtitle = stringResource(R.string.preset_test_subtitle)
    val testAuthor = stringResource(R.string.preset_test_author)
    return remember(testTitle, testSubtitle, testAuthor) {
        listOf(
            PresetConfig(
                id = "test",
                title = testTitle,
                subtitle = testSubtitle,
                author = testAuthor,
                downloads = 0L,
                // 测试预设：不做任何修改。
                apply = { },
            ),
        )
    }
}
