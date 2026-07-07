package io.github.hyperisland.xposed.commandtoken

data class TokenMatchResult(
    val targetPackage: String,
    val displayName: String,
    val prompt: String,
    val recognizerId: String,
)

interface TokenRecognizer {
    /** 识别器唯一 ID，对应子开关 key 后缀，例如 "douyin"。 */
    val recognizerId: String
    fun match(text: String): TokenMatchResult?
}
