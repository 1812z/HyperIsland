package io.github.hyperisland.xposed.commandtoken

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 抖音口令识别器单元测试。
 *
 * 运行：`./gradlew :app:testDebugUnitTest --tests "*DouyinTokenRecognizerTest*"`
 */
class DouyinTokenRecognizerTest {

    private val recognizer = DouyinTokenRecognizer

    @Test
    fun `user reported sample token is captured`() {
        // 用户提供的真实抖音视频分享口令
        val sample = "0.53 复制打开抖音，看看【裁决者NEURIX的作品】裁决档案/币圈凉兮 " +
            "币圈天才凉兮：1000元做到4... https://v.douyin.com/r8oph79g2dU/ C@u.fo teb:/ :3pm 05/30"
        val match = recognizer.match(sample)
        assertNotNull("用户提供的口令应被识别", match)
        assertEquals("com.ss.android.ugc.aweme", match!!.targetPackage)
        assertEquals("抖音", match.displayName)
        assertEquals("打开抖音", match.prompt)
        assertEquals("douyin", match.recognizerId)
    }

    @Test
    fun `sample without url still matches via copy-open phrase`() {
        // 移除 URL 后，应通过 "复制打开抖音" 短语匹配（验证 此 可选修复）
        val noUrl = "0.53 复制打开抖音，看看【裁决者NEURIX的作品】裁决档案/币圈凉兮"
        val match = recognizer.match(noUrl)
        assertNotNull("移除 URL 后应通过 '复制打开抖音' 短语匹配", match)
    }

    @Test
    fun `copy open douyin phrase matches`() {
        val text = "复制打开抖音，看看【某某的作品】更多精彩"
        assertNotNull(recognizer.match(text))
    }

    @Test
    fun `copy this link phrase matches`() {
        val text = "【抖音】https://v.douyin.com/iJxxxxx/ 复制此链接，打开抖音搜索，直接观看视频！"
        assertNotNull(recognizer.match(text))
    }

    @Test
    fun `copy this info phrase matches`() {
        val text = "复制此信息，打开【抖音】查看完整内容"
        assertNotNull(recognizer.match(text))
    }

    @Test
    fun `special symbol token matches`() {
        val text = "₤abcDEF123₤ 唯口令，打开抖音"
        assertNotNull(recognizer.match(text))
    }

    @Test
    fun `currency token with space matches`() {
        val text = "1￥CZ0001 abcDEF123￥"
        assertNotNull(recognizer.match(text))
    }

    @Test
    fun `currency token without prefix matches`() {
        val text = "￥abcDEF123￥"
        assertNotNull(recognizer.match(text))
    }

    @Test
    fun `short url with https matches`() {
        val text = "https://v.douyin.com/iJxxxxx/"
        assertNotNull(recognizer.match(text))
    }

    @Test
    fun `short url without scheme matches`() {
        val text = "v.douyin.com/r8oph79g2dU"
        assertNotNull(recognizer.match(text))
    }

    @Test
    fun `bracket prefix with keyword matches`() {
        val text = "【抖音】此条内容已被复制，打开抖音搜索查看完整内容"
        assertNotNull(recognizer.match(text))
    }

    @Test
    fun `long press copy phrase matches`() {
        val text = "长按复制此条信息，打开抖音"
        assertNotNull(recognizer.match(text))
    }

    @Test
    fun `long press identify phrase matches`() {
        val text = "长按识别图中二维码，打开抖音"
        assertNotNull(recognizer.match(text))
    }

    @Test
    fun `open douyin search phrase matches`() {
        val text = "点击链接，打开抖音搜索，直接观看视频"
        assertNotNull(recognizer.match(text))
    }

    @Test
    fun `plain text without token does not match`() {
        val text = "今天天气不错，我们去公园散步吧"
        assertNull(recognizer.match(text))
    }

    @Test
    fun `text mentioning douyin without share phrase does not match`() {
        // 普通聊天中提到「打开抖音」但不包含分享文案的关键结构
        val text = "你打开抖音看一下那个视频"
        // "打开抖音" 单独出现：rule 4 需要 "复制" 前缀，不应匹配
        assertNull(recognizer.match(text))
    }

    @Test
    fun `oversized text does not match`() {
        val text = "复制打开抖音" + "a".repeat(500)
        assertNull(recognizer.match(text))
    }

    @Test
    fun `empty string does not match`() {
        assertNull(recognizer.match(""))
    }

    @Test
    fun `match result fields are correct`() {
        val text = "₤token123₤"
        val match = recognizer.match(text)
        assertNotNull(match)
        match!!
        assertEquals("com.ss.android.ugc.aweme", match.targetPackage)
        assertEquals("抖音", match.displayName)
        assertEquals("打开抖音", match.prompt)
        assertEquals("douyin", match.recognizerId)
    }
}
