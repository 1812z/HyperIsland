package io.github.hyperisland.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.draw.clip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val DEFAULT_AI_TIMEOUT = 3
private const val DEFAULT_AI_TEMPERATURE = 0.1
private const val DEFAULT_AI_MAX_TOKENS = 50

@Composable
fun AiConfigScreen(
    state: AiConfigState,
    onUpdate: (AiConfigState) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var keyObscured by remember { mutableStateOf(true) }

    val contentPadding = io.github.hyperisland.ui.LocalContentPadding.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .overScrollVertical()
            .scrollEndHaptic(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = contentPadding.calculateTopPadding() + 16.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("AI 增强", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            MiuixCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("启用 AI 摘要")
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("由 AI 生成超级岛左右文本，超时或失败时自动回退", style = MaterialTheme.typography.bodySmall)
                    }
                    MiuixSwitch(
                        checked = state.enabled,
                        onCheckedChange = { onUpdate(state.copy(enabled = it)) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            if (state.enabled) {
                Text("API 参数", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                MiuixCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MiuixTextField(
                            value = state.url,
                            onValueChange = { onUpdate(state.copy(url = it)) },
                            label = "API 地址（必须完整）",
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        MiuixTextField(
                            value = state.apiKey,
                            onValueChange = { onUpdate(state.copy(apiKey = it)) },
                            label = "API 密钥",
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (keyObscured) PasswordVisualTransformation() else VisualTransformation.None,
                            trailingIcon = {
                                MiuixIconButton(onClick = { keyObscured = !keyObscured }) {
                                    Icon(
                                        imageVector = if (keyObscured) MiuixIcons.Regular.Show else MiuixIcons.Regular.Hide,
                                        contentDescription = if (keyObscured) "显示密钥" else "隐藏密钥",
                                    )
                                }
                            },
                        )
                        MiuixTextField(
                            value = state.model,
                            onValueChange = { onUpdate(state.copy(model = it)) },
                            label = "模型",
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        MiuixTextField(
                            value = state.prompt,
                            onValueChange = { onUpdate(state.copy(prompt = it)) },
                            label = "系统提示词",
                            useLabelAsPlaceholder = true,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 8,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("提示词放在用户消息")
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("某些模型不支持系统指令，开启后将提示词放在用户消息中", style = MaterialTheme.typography.bodySmall)
                            }
                            MiuixSwitch(
                                checked = state.promptInUser,
                                onCheckedChange = { onUpdate(state.copy(promptInUser = it)) },
                            )
                        }

                        SliderItem(
                            title = "AI 响应超时",
                            subtitle = "",
                            valueText = "${state.timeout}s",
                            value = state.timeout.toFloat(),
                            defaultValue = DEFAULT_AI_TIMEOUT.toFloat(),
                            range = 3f..15f,
                            steps = 11,
                            onValueChange = { onUpdate(state.copy(timeout = it.toInt().coerceIn(3, 15))) },
                            onResetToDefault = { onUpdate(state.copy(timeout = DEFAULT_AI_TIMEOUT)) },
                        )
                        SliderItem(
                            title = "采样温度 (Temperature)",
                            subtitle = "控制回答的随机性。0 为准确，1 则更具创意",
                            valueText = String.format("%.1f", state.temperature),
                            value = state.temperature.toFloat(),
                            defaultValue = DEFAULT_AI_TEMPERATURE.toFloat(),
                            range = 0f..1f,
                            steps = 10,
                            onValueChange = { onUpdate(state.copy(temperature = it.toDouble().coerceIn(0.0, 1.0))) },
                            onResetToDefault = { onUpdate(state.copy(temperature = DEFAULT_AI_TEMPERATURE)) },
                        )
                        SliderItem(
                            title = "最大 Token 数 (Max Tokens)",
                            subtitle = "限制 AI 生成回答的最大长度",
                            valueText = state.maxTokens.toString(),
                            value = state.maxTokens.toFloat(),
                            defaultValue = DEFAULT_AI_MAX_TOKENS.toFloat(),
                            range = 20f..100f,
                            steps = 80,
                            onValueChange = { onUpdate(state.copy(maxTokens = it.toInt().coerceIn(20, 100))) },
                            onResetToDefault = { onUpdate(state.copy(maxTokens = DEFAULT_AI_MAX_TOKENS)) },
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            MiuixButton(onClick = onTest, enabled = !state.testing, modifier = Modifier.weight(1f)) {
                                Text(if (state.testing) "测试中..." else "测试连接")
                            }
                            MiuixButton(onClick = onSave, modifier = Modifier.weight(1f)) {
                                Text("保存")
                            }
                        }

                        state.testResult?.let {
                            TestResultCard(text = it)
                        }
                    }
                }
            }

            MiuixCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "AI 会接收每条通知的应用包名、标题、正文，并返回短左文案（来源）与短右文案（内容）。兼容 OpenAI 格式 API（如 DeepSeek、Claude）。无响应时会自动回退默认逻辑。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SliderItem(
    title: String,
    subtitle: String,
    valueText: String,
    value: Float,
    defaultValue: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onResetToDefault: () -> Unit,
) {
    val showResetButton = kotlin.math.abs(value - defaultValue) > 0.0001f
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    if (showResetButton) {
                        MiuixIconButton(
                            onClick = onResetToDefault,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Refresh,
                                contentDescription = "恢复默认值",
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
                Text(
                    valueText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(64.dp),
                )
            }
        }
        MiuixSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TestResultCard(text: String) {
    val isSuccess = text.isNotBlank() && !text.startsWith("HTTP ") && !text.contains("Exception")
    val bg = if (isSuccess) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val fg = if (isSuccess) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, shape = MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(if (isSuccess) "测试结果（成功）" else "测试结果（失败）", color = fg, fontWeight = FontWeight.SemiBold)
        Text(text, color = fg, style = MaterialTheme.typography.bodySmall)
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun AiConfigScreenPreview() {
    MiuixTheme {
        MaterialTheme {
            AiConfigScreen(
                state = AiConfigState(
                    enabled = true,
                    url = "https://api.example.com/v1/chat/completions",
                    apiKey = "sk-******",
                    model = "gpt-4o-mini",
                    prompt = "根据通知信息，提取关键信息，左右分别不超过6汉字12字符",
                    promptInUser = false,
                    timeout = 6,
                    temperature = 0.2,
                    maxTokens = 60,
                    testing = false,
                    testResult = "连接成功：示例结果",
                ),
                onUpdate = {},
                onSave = {},
                onTest = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
