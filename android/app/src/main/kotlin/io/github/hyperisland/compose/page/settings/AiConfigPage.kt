package io.github.hyperisland.compose.page.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.hyperisland.R
import io.github.hyperisland.compose.component.AiCustomFieldsDialog
import io.github.hyperisland.compose.component.AiModelPickerDialog
import io.github.hyperisland.compose.component.DetailPage
import io.github.hyperisland.compose.component.SectionTitle
import io.github.hyperisland.compose.data.AiConfigSettings
import io.github.hyperisland.compose.data.FlutterPrefsRepository
import io.github.hyperisland.compose.service.AiConfigService
import io.github.hyperisland.compose.service.TestNotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
internal fun AiConfigPage(
    prefs: FlutterPrefsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }
    val initial = remember { prefs.aiConfigSettings() }
    val defaultPrompt = stringResource(R.string.compose_ai_default_prompt)
    var saved by remember { mutableStateOf(initial) }
    var draft by remember {
        mutableStateOf(initial.copy(prompt = initial.prompt.ifBlank { defaultPrompt }))
    }
    var notificationContent by remember {
        mutableStateOf(context.getString(R.string.compose_ai_default_notification))
    }
    var keyObscured by remember { mutableStateOf(true) }
    var showModels by remember { mutableStateOf(false) }
    var showCustomFields by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var sendingNormal by remember { mutableStateOf(false) }
    var sendingAi by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<AiTestResult?>(null) }

    val urlEmpty = stringResource(R.string.compose_ai_url_empty)
    val savedMessage = stringResource(R.string.compose_ai_saved)
    val notificationSent = stringResource(R.string.compose_ai_notification_sent)
    val aiNotificationSent = stringResource(R.string.compose_ai_ai_notification_sent)
    val defaultNotification = stringResource(R.string.compose_ai_default_notification)
    val testNotificationTitle = stringResource(R.string.compose_ai_test_notification_title)
    val testSample = stringResource(R.string.compose_ai_test_sample)
    val jsonOnlyInstruction = stringResource(R.string.compose_ai_json_only)
    val jsonLeft = stringResource(R.string.compose_ai_json_left)
    val jsonRight = stringResource(R.string.compose_ai_json_right)
    val thinkingError = stringResource(R.string.compose_ai_thinking_error)
    val invalidJsonError = stringResource(R.string.compose_ai_invalid_json)
    val emptyJsonError = stringResource(R.string.compose_ai_empty_json)

    fun persistImmediate(update: (AiConfigSettings) -> AiConfigSettings) {
        draft = update(draft)
        saved = update(saved)
        prefs.setAiConfigSettings(saved)
    }

    fun save() {
        val value = draft.copy(
            url = draft.url.trim(),
            apiKey = draft.apiKey.trim(),
            model = draft.model.trim(),
            prompt = draft.prompt.trim(),
        )
        draft = value
        saved = value
        prefs.setAiConfigSettings(value)
        scope.launch { snackbarState.showSnackbar(savedMessage) }
    }

    fun testConnection() {
        if (draft.url.isBlank()) {
            testResult = AiTestResult(false, urlEmpty)
            return
        }
        scope.launch {
            testing = true
            testResult = null
            val result = runCatching {
                withContext(Dispatchers.IO) { AiConfigService.testConnection(draft, testSample) }
            }
            testResult = result.fold(
                onSuccess = { AiTestResult(true, it) },
                onFailure = { AiTestResult(false, it.message ?: it.toString()) },
            )
            testing = false
        }
    }

    fun sendNotification(useAi: Boolean) {
        if (useAi && draft.url.isBlank()) {
            scope.launch { snackbarState.showSnackbar(urlEmpty) }
            return
        }
        scope.launch {
            if (useAi) sendingAi = true else sendingNormal = true
            runCatching {
                val content = notificationContent.trim().ifEmpty { defaultNotification }
                if (useAi) {
                    val (title, body) = withContext(Dispatchers.IO) {
                        AiConfigService.requestNotificationText(
                            settings = draft,
                            defaultPrompt = defaultPrompt,
                            userContent = context.getString(
                                R.string.compose_ai_notification_user_content,
                                content,
                            ),
                            jsonOnlyInstruction = jsonOnlyInstruction,
                            leftDescription = jsonLeft,
                            rightDescription = jsonRight,
                            thinkingError = thinkingError,
                            invalidJsonError = invalidJsonError,
                            emptyJsonError = emptyJsonError,
                        )
                    }
                    TestNotificationService.sendCustom(context, title, body, true, true)
                } else {
                    TestNotificationService.sendCustom(
                        context,
                        testNotificationTitle,
                        content,
                        true,
                        true,
                    )
                }
            }.onSuccess {
                snackbarState.showSnackbar(if (useAi) aiNotificationSent else notificationSent)
            }.onFailure {
                snackbarState.showSnackbar(it.message ?: it.toString())
            }
            if (useAi) sendingAi = false else sendingNormal = false
        }
    }

    val warningColors = if (MiuixTheme.colorScheme.background.luminance() > 0.5f) {
        CardDefaults.defaultColors(
            color = Color(0xFFFFF3D6),
            contentColor = Color(0xFF704D00),
        )
    } else {
        CardDefaults.defaultColors(
            color = Color(0xFF3A2D12),
            contentColor = Color(0xFFFFD978),
        )
    }

    DetailPage(
        title = stringResource(R.string.compose_ai_summary),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarState) },
    ) {
        item {
            SectionTitle(stringResource(R.string.compose_ai_section))
            Card {
                SwitchPreference(
                    checked = draft.enabled,
                    onCheckedChange = { enabled -> persistImmediate { it.copy(enabled = enabled) } },
                    title = stringResource(R.string.compose_ai_enabled),
                    summary = stringResource(R.string.compose_ai_enabled_summary),
                    insideMargin = ITEM_MARGIN,
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = warningColors,
                showIndication = true,
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AI_TUTORIAL_URL)))
                },
            ) {
                Text(
                    text = stringResource(R.string.compose_ai_warning),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        item {
            SectionTitle(stringResource(R.string.compose_ai_api_section))
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TextField(
                        value = draft.url,
                        onValueChange = { draft = draft.copy(url = it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.compose_ai_url),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                    )
                    TextField(
                        value = draft.apiKey,
                        onValueChange = { draft = draft.copy(apiKey = it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.compose_ai_api_key),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        visualTransformation = if (keyObscured) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        trailingIcon = {
                            IconButton(onClick = { keyObscured = !keyObscured }) {
                                Icon(
                                    if (keyObscured) MiuixIcons.Show else MiuixIcons.Hide,
                                    contentDescription = stringResource(R.string.compose_ai_api_key),
                                )
                            }
                        },
                    )
                    TextField(
                        value = draft.model,
                        onValueChange = { draft = draft.copy(model = it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.compose_ai_model),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                if (draft.url.isBlank()) {
                                    scope.launch { snackbarState.showSnackbar(urlEmpty) }
                                } else {
                                    showModels = true
                                }
                            }) {
                                Icon(
                                    MiuixIcons.Search,
                                    contentDescription = stringResource(R.string.compose_ai_model_picker_title),
                                )
                            }
                        },
                    )
                    TextField(
                        value = draft.prompt,
                        onValueChange = { draft = draft.copy(prompt = it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.compose_ai_prompt),
                        useLabelAsPlaceholder = true,
                        minLines = 2,
                        maxLines = 10,
                    )
                }
                ArrowPreference(
                    title = stringResource(R.string.compose_ai_custom_fields),
                    summary = stringResource(R.string.compose_ai_custom_fields_summary),
                    insideMargin = ITEM_MARGIN,
                    onClick = { showCustomFields = true },
                )
                SwitchPreference(
                    checked = draft.promptInUser,
                    onCheckedChange = { enabled ->
                        persistImmediate { it.copy(promptInUser = enabled) }
                    },
                    title = stringResource(R.string.compose_ai_prompt_in_user),
                    summary = stringResource(R.string.compose_ai_prompt_in_user_summary),
                    insideMargin = ITEM_MARGIN,
                )
                SliderPreference(
                    value = draft.timeout.toFloat(),
                    onValueChange = {
                        draft = draft.copy(timeout = it.roundToInt().coerceIn(3, 15))
                    },
                    title = stringResource(R.string.compose_ai_timeout),
                    valueText = stringResource(R.string.compose_ai_timeout_value, draft.timeout),
                    valueRange = 3f..15f,
                    steps = 11,
                    insideMargin = ITEM_MARGIN,
                    onValueChangeFinished = {
                        val value = draft.timeout
                        persistImmediate { it.copy(timeout = value) }
                    },
                )
                SliderPreference(
                    value = draft.triggerCharCount.toFloat(),
                    onValueChange = {
                        val value = ((it / 5f).roundToInt() * 5).coerceIn(0, 100)
                        draft = draft.copy(triggerCharCount = value)
                    },
                    title = stringResource(R.string.compose_ai_trigger_count),
                    summary = stringResource(
                        if (draft.triggerCharCount == 0) {
                            R.string.compose_ai_trigger_always
                        } else {
                            R.string.compose_ai_trigger_count_summary
                        },
                    ),
                    valueText = draft.triggerCharCount.toString(),
                    valueRange = 0f..100f,
                    steps = 19,
                    insideMargin = ITEM_MARGIN,
                    onValueChangeFinished = {
                        val value = draft.triggerCharCount
                        persistImmediate { it.copy(triggerCharCount = value) }
                    },
                )
                SliderPreference(
                    value = draft.temperature.toFloat(),
                    onValueChange = {
                        draft = draft.copy(
                            temperature = ((it * 10).roundToInt() / 10.0).coerceIn(0.0, 1.0),
                        )
                    },
                    title = stringResource(R.string.compose_ai_temperature),
                    summary = stringResource(R.string.compose_ai_temperature_summary),
                    valueText = "%.1f".format(draft.temperature),
                    valueRange = 0f..1f,
                    steps = 9,
                    insideMargin = ITEM_MARGIN,
                    onValueChangeFinished = {
                        val value = draft.temperature
                        persistImmediate { it.copy(temperature = value) }
                    },
                )
                SliderPreference(
                    value = draft.maxTokens.toFloat(),
                    onValueChange = {
                        draft = draft.copy(maxTokens = it.roundToInt().coerceIn(20, 100))
                    },
                    title = stringResource(R.string.compose_ai_max_tokens),
                    summary = stringResource(R.string.compose_ai_max_tokens_summary),
                    valueText = draft.maxTokens.toString(),
                    valueRange = 20f..100f,
                    steps = 79,
                    insideMargin = ITEM_MARGIN,
                    onValueChangeFinished = {
                        val value = draft.maxTokens
                        persistImmediate { it.copy(maxTokens = value) }
                    },
                )
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = ::testConnection,
                            enabled = !testing,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (testing) CircularProgressIndicator(size = 18.dp)
                            else Text(stringResource(R.string.compose_ai_test_connection))
                        }
                        Button(
                            onClick = ::save,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text(stringResource(R.string.compose_save))
                        }
                    }
                    testResult?.let { result ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.defaultColors(
                                color = if (result.success) {
                                    MiuixTheme.colorScheme.primaryContainer
                                } else {
                                    MiuixTheme.colorScheme.errorContainer
                                },
                                contentColor = if (result.success) {
                                    MiuixTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MiuixTheme.colorScheme.onErrorContainer
                                },
                            ),
                        ) {
                            Text(
                                text = result.message,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionTitle(stringResource(R.string.compose_notification_test))
            Card(insideMargin = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TextField(
                        value = notificationContent,
                        onValueChange = { notificationContent = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.compose_ai_notification_content),
                        useLabelAsPlaceholder = true,
                        minLines = 2,
                        maxLines = 4,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = { sendNotification(false) },
                            enabled = !sendingNormal && !sendingAi,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (sendingNormal) CircularProgressIndicator(size = 18.dp)
                            else Text(stringResource(R.string.compose_ai_send_notification))
                        }
                        Button(
                            onClick = { sendNotification(true) },
                            enabled = !sendingNormal && !sendingAi,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            if (sendingAi) CircularProgressIndicator(size = 18.dp)
                            else Text(stringResource(R.string.compose_ai_send_ai_notification))
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer,
                    contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Text(
                    text = stringResource(R.string.compose_ai_tips),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }

    AiModelPickerDialog(
        show = showModels,
        chatUrl = draft.url,
        apiKey = draft.apiKey,
        currentModel = draft.model,
        onDismiss = { showModels = false },
        onSelected = {
            draft = draft.copy(model = it)
            showModels = false
        },
    )
    AiCustomFieldsDialog(
        show = showCustomFields,
        initialJson = draft.customFields,
        onDismiss = { showCustomFields = false },
        onSave = { json ->
            persistImmediate { it.copy(customFields = json) }
            showCustomFields = false
        },
    )
}

private data class AiTestResult(val success: Boolean, val message: String)

private val ITEM_MARGIN = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
private const val AI_TUTORIAL_URL =
    "https://hyperisland.1812z.top/getting-started.html#ai-%E6%80%BB%E7%BB%93%E9%85%8D%E7%BD%AE"
