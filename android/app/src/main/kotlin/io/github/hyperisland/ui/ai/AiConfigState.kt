package io.github.hyperisland.ui.ai

data class AiConfigState(
    val enabled: Boolean = false,
    val url: String = "",
    val apiKey: String = "",
    val model: String = "",
    val prompt: String = "根据通知信息，提取关键信息，左右分别不超过6汉字12字符",
    val promptInUser: Boolean = false,
    val timeout: Int = 3,
    val temperature: Double = 0.1,
    val maxTokens: Int = 50,
    val testing: Boolean = false,
    val testResult: String? = null,
)
