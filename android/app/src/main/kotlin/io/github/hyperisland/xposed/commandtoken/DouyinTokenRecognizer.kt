package io.github.hyperisland.xposed.commandtoken

object DouyinTokenRecognizer : TokenRecognizer {

    override val recognizerId: String = "douyin"

    private const val MAX_TEXT_LENGTH = 500

    private val patterns = listOf(
        // ₤...₤ 特殊符号包裹（唯口令）
        Regex("₤[\\s\\S]+?₤"),
        // ￥...￥ 货币符号包裹，允许内部含空格与任意字符（如 "1￥CZ0001 abcDEF123￥"）
        Regex("1?￥[^￥\\n]{1,60}￥"),
        // v.douyin.com 短链（含或不含 https:// 前缀，路径可含字母数字与短横线）
        Regex("v\\.douyin\\.com/\\S+", RegexOption.IGNORE_CASE),
        // "复制打开抖音" / "复制此链接，打开抖音" / "复制此信息，打开抖音"（此 为可选，覆盖视频分享常见文案）
        Regex("复制此?[链接信息]?.{0,6}打开.{0,4}抖音"),
        // "长按复制...抖音" / "长按识别...抖音"
        Regex("长按(?:复制|识别).{0,8}抖音"),
        // "【抖音】...口令/打开/链接/搜索/复制"
        Regex("【抖音】.{0,60}(?:口令|打开|链接|搜索|复制)"),
        // "打开抖音搜索"（分享文案常见短语）
        Regex("打开抖音搜索"),
    )

    override fun match(text: String): TokenMatchResult? {
        if (text.length > MAX_TEXT_LENGTH) return null
        return patterns.firstOrNull { it.containsMatchIn(text) }?.let {
            TokenMatchResult(
                targetPackage = "com.ss.android.ugc.aweme",
                displayName = "抖音",
                prompt = "打开抖音",
                recognizerId = recognizerId,
            )
        }
    }
}
