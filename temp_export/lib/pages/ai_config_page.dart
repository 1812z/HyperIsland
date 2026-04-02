import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:font_awesome_flutter/font_awesome_flutter.dart';
import '../controllers/settings_controller.dart';
import '../l10n/generated/app_localizations.dart';
import '../services/interaction_haptics.dart';
import '../widgets/section_label.dart';
import '../widgets/modern_slider.dart';

class AiConfigPage extends StatefulWidget {
  const AiConfigPage({super.key});

  @override
  State<AiConfigPage> createState() => _AiConfigPageState();
}

class _AiConfigPageState extends State<AiConfigPage> {
  final _ctrl = SettingsController.instance;
  static const _defaultAiPrompt =
      '你需要尽可能快的提取关键信息为JSON。'
      'left和right均严禁超过6汉字，仅保留最核心的短语，去除修饰词。'
      '仅返回纯JSON，严禁Markdown标记。'
      '示例：输入：应用包名：com.example.app\\n标题：测试通知\\n正文：这是一条用于测试 AI 提取效果的示例消息'
      '输出：{"left": "测试通知", "right": "测试AI提取"}';

  late final TextEditingController _urlCtrl;
  late final TextEditingController _keyCtrl;
  late final TextEditingController _modelCtrl;
  late final TextEditingController _promptCtrl;

  bool _keyObscured = true;
  bool _testing = false;
  _TestResult? _testResult;

  void _onCtrlChanged() {
    if (mounted) setState(() {});
  }

  @override
  void initState() {
    super.initState();
    _ctrl.addListener(_onCtrlChanged);
    _ctrl.refreshAiLastLog();
    _urlCtrl = TextEditingController(text: _ctrl.aiUrl);
    _keyCtrl = TextEditingController(text: _ctrl.aiApiKey);
    _modelCtrl = TextEditingController(text: _ctrl.aiModel);
    _promptCtrl = TextEditingController(
      text: _ctrl.aiPrompt.isEmpty ? _defaultAiPrompt : _ctrl.aiPrompt,
    );
  }

  @override
  void dispose() {
    _ctrl.removeListener(_onCtrlChanged);
    _urlCtrl.dispose();
    _keyCtrl.dispose();
    _modelCtrl.dispose();
    _promptCtrl.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    await InteractionHaptics.button();
    await _ctrl.setAiUrl(_urlCtrl.text.trim());
    await _ctrl.setAiApiKey(_keyCtrl.text.trim());
    await _ctrl.setAiModel(_modelCtrl.text.trim());
    await _ctrl.setAiPrompt(_promptCtrl.text.trim());
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context)!.aiConfigSaved),
          behavior: SnackBarBehavior.floating,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
        ),
      );
    }
  }

  Future<void> _test() async {
    await InteractionHaptics.button();
    final url = _urlCtrl.text.trim();
    final key = _keyCtrl.text.trim();
    final model = _modelCtrl.text.trim();
    final requestTime = DateTime.now();
    String requestBody = '';

    if (url.isEmpty) {
      setState(
        () => _testResult = _TestResult.fail(
          AppLocalizations.of(context)!.aiTestUrlEmpty,
        ),
      );
      return;
    }

    setState(() {
      _testing = true;
      _testResult = null;
    });

    try {
      final promptText = _promptCtrl.text.trim();
      const sampleUserContent =
          '应用包名：com.example.app\n标题：测试通知\n正文：这是一条用于测试 AI 提取效果的示例消息';
      requestBody = jsonEncode({
        'model': model.isEmpty ? 'gpt-4o-mini' : model,
        'messages': [
          if (!_ctrl.aiPromptInUser && promptText.isNotEmpty)
            {'role': 'system', 'content': promptText},
          if (_ctrl.aiPromptInUser && promptText.isNotEmpty)
            {'role': 'user', 'content': promptText},
          {'role': 'user', 'content': sampleUserContent},
        ],
        'max_tokens': _ctrl.aiMaxTokens,
        'temperature': _ctrl.aiTemperature,
      });
      await _ctrl.saveAiLastLog(
        AiLogEntry(
          timestamp: requestTime,
          source: 'settings_test',
          url: url,
          model: model.isEmpty ? 'gpt-4o-mini' : model,
          requestBody: requestBody,
          responseBody: '',
          error: '',
          statusCode: null,
          durationMs: null,
        ),
      );

      final response = await http
          .post(
            Uri.parse(url),
            headers: {
              'Content-Type': 'application/json',
              'Accept': 'application/json',
              if (key.isNotEmpty) 'Authorization': 'Bearer $key',
            },
            body: requestBody,
          )
          .timeout(Duration(seconds: _ctrl.aiTimeout));

      if (response.statusCode == 200) {
        final json = jsonDecode(response.body) as Map<String, dynamic>;
        final content =
            (json['choices'] as List?)?.firstOrNull?['message']?['content']
                as String? ??
            '';
        await _ctrl.saveAiLastLog(
          AiLogEntry(
            timestamp: requestTime,
            source: 'settings_test',
            url: url,
            model: model.isEmpty ? 'gpt-4o-mini' : model,
            requestBody: requestBody,
            responseBody: response.body,
            error: '',
            statusCode: response.statusCode,
            durationMs: DateTime.now().difference(requestTime).inMilliseconds,
          ),
        );
        setState(() => _testResult = _TestResult.ok(content.trim()));
      } else {
        await _ctrl.saveAiLastLog(
          AiLogEntry(
            timestamp: requestTime,
            source: 'settings_test',
            url: url,
            model: model.isEmpty ? 'gpt-4o-mini' : model,
            requestBody: requestBody,
            responseBody: response.body,
            error: 'HTTP ${response.statusCode}',
            statusCode: response.statusCode,
            durationMs: DateTime.now().difference(requestTime).inMilliseconds,
          ),
        );
        setState(
          () => _testResult = _TestResult.fail(
            'HTTP ${response.statusCode}\n${response.body}',
          ),
        );
      }
    } on Exception catch (e) {
      await _ctrl.saveAiLastLog(
        AiLogEntry(
          timestamp: requestTime,
          source: 'settings_test',
          url: url,
          model: model.isEmpty ? 'gpt-4o-mini' : model,
          requestBody: requestBody,
          responseBody: '',
          error: e.toString(),
          statusCode: null,
          durationMs: DateTime.now().difference(requestTime).inMilliseconds,
        ),
      );
      setState(() => _testResult = _TestResult.fail(e.toString()));
    } finally {
      if (mounted) setState(() => _testing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
    final textTheme = Theme.of(context).textTheme;

    return Scaffold(
      backgroundColor: cs.surface,
      body: CustomScrollView(
        slivers: [
          SliverAppBar.large(
            title: Text(l10n.aiConfigTitle),
            backgroundColor: cs.surface,
            centerTitle: false,
          ),
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                SectionLabel(l10n.aiConfigSection),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: SwitchListTile(
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 4,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    title: Text(l10n.aiEnabledTitle),
                    subtitle: Text(l10n.aiEnabledSubtitle),
                    value: _ctrl.aiEnabled,
                    onChanged: (value) async {
                      await InteractionHaptics.toggle();
                      await _ctrl.setAiEnabled(value);
                    },
                  ),
                ),
                const SizedBox(height: 24),

                SectionLabel(l10n.aiApiSection),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _buildTextField(
                          controller: _urlCtrl,
                          label: l10n.aiUrlLabel,
                          hint: l10n.aiUrlHint,
                          icon: FontAwesomeIcons.link,
                        ),
                        const SizedBox(height: 16),
                        _buildTextField(
                          controller: _keyCtrl,
                          label: l10n.aiApiKeyLabel,
                          hint: l10n.aiApiKeyHint,
                          icon: FontAwesomeIcons.key,
                          obscure: _keyObscured,
                          suffix: IconButton(
                            icon: FaIcon(
                              _keyObscured
                                  ? FontAwesomeIcons.eyeSlash
                                  : FontAwesomeIcons.eye,
                              size: 16,
                            ),
                            onPressed: () async {
                              await InteractionHaptics.button();
                              setState(() => _keyObscured = !_keyObscured);
                            },
                          ),
                        ),
                        const SizedBox(height: 16),
                        _buildTextField(
                          controller: _modelCtrl,
                          label: l10n.aiModelLabel,
                          hint: l10n.aiModelHint,
                          icon: FontAwesomeIcons.lightbulb,
                        ),
                        const SizedBox(height: 16),
                        _buildTextField(
                          controller: _promptCtrl,
                          label: l10n.aiPromptLabel,
                          hint: l10n.aiPromptHint,
                          icon: FontAwesomeIcons.penToSquare,
                          minLines: 1,
                          maxLines: 10,
                        ),
                        const SizedBox(height: 16),
                        Row(
                          children: [
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    l10n.aiPromptInUserTitle,
                                    style: textTheme.titleMedium,
                                  ),
                                  Text(
                                    l10n.aiPromptInUserSubtitle,
                                    style: textTheme.bodySmall?.copyWith(
                                      color: cs.onSurfaceVariant,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            Switch(
                              value: _ctrl.aiPromptInUser,
                              onChanged: (value) async {
                                await InteractionHaptics.toggle();
                                await _ctrl.setAiPromptInUser(value);
                              },
                            ),
                          ],
                        ),
                        const SizedBox(height: 24),

                        Row(
                          children: [
                            const FaIcon(FontAwesomeIcons.clock, size: 18),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    l10n.aiTimeoutTitle,
                                    style: textTheme.titleMedium,
                                  ),
                                ],
                              ),
                            ),
                            Text(
                              l10n.aiTimeoutLabel(_ctrl.aiTimeout),
                              style: textTheme.bodyLarge?.copyWith(
                                color: cs.primary,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ],
                        ),
                        SliderTheme(
                          data: ModernSliderTheme.theme(context),
                          child: Slider(
                            value: _ctrl.aiTimeout.toDouble(),
                            min: 1,
                            max: 10,
                            divisions: 9,
                            label: '${_ctrl.aiTimeout}s',
                            onChanged: (v) async {
                              await InteractionHaptics.sliderTick();
                              await _ctrl.setAiTimeout(v.toInt());
                            },
                          ),
                        ),

                        const SizedBox(height: 16),
                        Row(
                          children: [
                            const FaIcon(
                              FontAwesomeIcons.temperatureHalf,
                              size: 18,
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    l10n.aiTemperatureTitle,
                                    style: textTheme.titleMedium,
                                  ),
                                  Text(
                                    l10n.aiTemperatureSubtitle,
                                    style: textTheme.bodySmall?.copyWith(
                                      color: cs.onSurfaceVariant,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            Text(
                              _ctrl.aiTemperature.toStringAsFixed(1),
                              style: textTheme.bodyLarge?.copyWith(
                                color: cs.primary,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ],
                        ),
                        SliderTheme(
                          data: ModernSliderTheme.theme(context),
                          child: Slider(
                            value: _ctrl.aiTemperature,
                            min: 0,
                            max: 1,
                            divisions: 10,
                            label: _ctrl.aiTemperature.toStringAsFixed(1),
                            onChanged: (v) async {
                              await InteractionHaptics.sliderTick();
                              await _ctrl.setAiTemperature(v);
                            },
                          ),
                        ),

                        const SizedBox(height: 16),
                        Row(
                          children: [
                            const FaIcon(FontAwesomeIcons.coins, size: 18),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    l10n.aiMaxTokensTitle,
                                    style: textTheme.titleMedium,
                                  ),
                                  Text(
                                    l10n.aiMaxTokensSubtitle,
                                    style: textTheme.bodySmall?.copyWith(
                                      color: cs.onSurfaceVariant,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            Text(
                              '${_ctrl.aiMaxTokens}',
                              style: textTheme.bodyLarge?.copyWith(
                                color: cs.primary,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ],
                        ),
                        SliderTheme(
                          data: ModernSliderTheme.theme(context),
                          child: Slider(
                            value: _ctrl.aiMaxTokens.toDouble(),
                            min: 10,
                            max: 500,
                            divisions: 49,
                            label: '${_ctrl.aiMaxTokens}',
                            onChanged: (v) async {
                              await InteractionHaptics.sliderTick();
                              await _ctrl.setAiMaxTokens(v.toInt());
                            },
                          ),
                        ),

                        const SizedBox(height: 16),
                        Row(
                          children: [
                            Expanded(
                              child: OutlinedButton.icon(
                                onPressed: _testing ? null : _test,
                                icon: _testing
                                    ? const SizedBox(
                                        width: 16,
                                        height: 16,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2,
                                        ),
                                      )
                                    : const FaIcon(
                                        FontAwesomeIcons.radiation,
                                        size: 16,
                                      ),
                                label: Text(l10n.aiTestButton),
                                style: OutlinedButton.styleFrom(
                                  padding: const EdgeInsets.symmetric(
                                    vertical: 12,
                                  ),
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(12),
                                  ),
                                ),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: FilledButton.icon(
                                onPressed: _save,
                                icon: const FaIcon(
                                  FontAwesomeIcons.floppyDisk,
                                  size: 16,
                                ),
                                label: Text(l10n.aiConfigSaveButton),
                                style: FilledButton.styleFrom(
                                  padding: const EdgeInsets.symmetric(
                                    vertical: 12,
                                  ),
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(12),
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
                        if (_testResult != null) ...[
                          const SizedBox(height: 12),
                          _TestResultCard(result: _testResult!),
                        ],
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                _AiLastLogCard(
                  log: _ctrl.aiLastLog,
                  onRefresh: _ctrl.refreshAiLastLog,
                ),
                const SizedBox(height: 16),

                Card(
                  elevation: 0,
                  color: cs.secondaryContainer.withValues(alpha: 0.5),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        FaIcon(
                          FontAwesomeIcons.circleInfo,
                          color: cs.onSecondaryContainer,
                          size: 18,
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Text(
                            l10n.aiConfigTips,
                            style: Theme.of(context).textTheme.bodySmall
                                ?.copyWith(color: cs.onSecondaryContainer),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 32),
              ]),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTextField({
    required TextEditingController controller,
    required String label,
    required String hint,
    required FaIconData icon,
    bool obscure = false,
    int? minLines,
    int? maxLines = 1,
    Widget? suffix,
  }) {
    final cs = Theme.of(context).colorScheme;
    return TextField(
      controller: controller,
      obscureText: obscure,
      minLines: minLines,
      maxLines: maxLines,
      decoration: InputDecoration(
        labelText: label,
        hintText: hint,
        prefixIcon: Padding(
          padding: const EdgeInsets.all(12),
          child: FaIcon(icon, size: 18),
        ),
        suffixIcon: suffix,
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: cs.outlineVariant),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: cs.primary, width: 2),
        ),
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 16,
        ),
        alignLabelWithHint: true,
      ),
      autocorrect: false,
    );
  }
}

class _AiLastLogCard extends StatefulWidget {
  const _AiLastLogCard({required this.log, required this.onRefresh});

  final AiLogEntry? log;
  final Future<void> Function() onRefresh;

  @override
  State<_AiLastLogCard> createState() => _AiLastLogCardState();
}

class _AiLastLogCardState extends State<_AiLastLogCard> {
  _AiLogViewMode _mode = _AiLogViewMode.rendered;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final cs = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    final log = widget.log;
    final rawJson = log == null
        ? ''
        : const JsonEncoder.withIndent('  ').convert(log.toJson());
    final parsedRequest = log == null ? null : _tryParseJson(log.requestBody);
    final parsedResponse = log == null ? null : _tryParseJson(log.responseBody);
    final usage = _extractUsage(parsedResponse);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: cs.surfaceContainer,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(l10n.aiLastLogTitle, style: textTheme.titleMedium),
              ),
              IconButton(
                tooltip: l10n.aiLastLogCopy,
                onPressed: log == null
                    ? null
                    : () async {
                        await InteractionHaptics.button();
                        await _copyLog(rawJson);
                      },
                icon: const FaIcon(FontAwesomeIcons.copy, size: 14),
              ),
              IconButton(
                tooltip: l10n.refreshList,
                onPressed: () async {
                  await InteractionHaptics.button();
                  await widget.onRefresh();
                },
                icon: const FaIcon(FontAwesomeIcons.arrowsRotate, size: 14),
              ),
            ],
          ),
          Text(
            l10n.aiLastLogSubtitle,
            style: textTheme.bodySmall?.copyWith(color: cs.onSurfaceVariant),
          ),
          const SizedBox(height: 12),
          if (log == null)
            Text(
              l10n.aiLastLogEmpty,
              style: textTheme.bodySmall?.copyWith(color: cs.onSurfaceVariant),
            )
          else ...[
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: cs.surfaceBright,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: cs.outlineVariant),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: _buildSummaryItem(
                          context,
                          label: l10n.aiLastLogSourceLabel,
                          value: _sourceLabel(l10n, log.source),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: _buildSummaryItem(
                          context,
                          label: l10n.aiLastLogStatusLabel,
                          value: _statusText(log),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      Expanded(
                        child: _buildSummaryItem(
                          context,
                          label: l10n.aiLastLogDurationLabel,
                          value: log.durationMs == null
                              ? '-'
                              : '${log.durationMs} ms',
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: _buildSummaryItem(
                          context,
                          label: '总token',
                          value: usage['total_tokens'] ?? '-',
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  _buildSummaryItem(
                    context,
                    label: l10n.aiLastLogTimeLabel,
                    value: _formatTime(log.timestamp),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            SegmentedButton<_AiLogViewMode>(
              segments: [
                ButtonSegment<_AiLogViewMode>(
                  value: _AiLogViewMode.rendered,
                  label: Text(l10n.aiLastLogRendered),
                  icon: const Icon(Icons.auto_awesome_outlined, size: 16),
                ),
                ButtonSegment<_AiLogViewMode>(
                  value: _AiLogViewMode.raw,
                  label: Text(l10n.aiLastLogRaw),
                  icon: const Icon(Icons.code, size: 16),
                ),
              ],
              selected: {_mode},
              onSelectionChanged: (value) async {
                await InteractionHaptics.button();
                setState(() => _mode = value.first);
              },
            ),
            const SizedBox(height: 12),
            if (_mode == _AiLogViewMode.rendered)
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _buildRenderedRequest(
                    context,
                    log,
                    parsedRequest,
                    usage['prompt_tokens'],
                  ),
                  const SizedBox(height: 12),
                  _buildRenderedResponse(
                    context,
                    log,
                    parsedResponse,
                    usage['completion_tokens'],
                  ),
                  if (log.error.isNotEmpty) ...[
                    const SizedBox(height: 12),
                    _buildSection(
                      context,
                      title: l10n.aiLastLogError,
                      content: log.error,
                      emphasize: true,
                    ),
                  ],
                ],
              )
            else
              _buildCodeBlock(context, rawJson),
          ],
        ],
      ),
    );
  }

  Future<void> _copyLog(String rawJson) async {
    final l10n = AppLocalizations.of(context)!;
    await Clipboard.setData(ClipboardData(text: rawJson));
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(l10n.aiLastLogCopied)));
  }

  Widget _buildRenderedRequest(
    BuildContext context,
    AiLogEntry log,
    Map<String, dynamic>? request,
    String? promptTokens,
  ) {
    final l10n = AppLocalizations.of(context)!;
    final messages = ((request?['messages'] as List?) ?? const [])
        .whereType<Map>()
        .map((item) => Map<String, dynamic>.from(item))
        .toList();

    return _buildSectionContainer(
      context,
      title: l10n.aiLastLogRequest,
      children: [
        if (messages.isNotEmpty) ...[
          _buildInlineSectionHeader(
            context,
            title: l10n.aiLastLogMessages,
            chipText: promptTokens == null
                ? null
                : 'prompt_tokens: $promptTokens',
          ),
          const SizedBox(height: 8),
          for (final message in messages)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: _buildMessageBubble(
                context,
                role: (message['role'] ?? '').toString(),
                content: (message['content'] ?? '').toString(),
              ),
            ),
        ] else if (log.requestBody.isNotEmpty) ...[
          if (promptTokens != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: _buildMetaChip(context, 'prompt_tokens: $promptTokens'),
            ),
          _buildCodeBlock(context, log.requestBody),
        ],
        if (request != null) ...[
          const SizedBox(height: 12),
          _buildRequestMetaSection(context, log, request),
        ],
      ],
    );
  }

  Widget _buildRenderedResponse(
    BuildContext context,
    AiLogEntry log,
    Map<String, dynamic>? response,
    String? completionTokens,
  ) {
    final l10n = AppLocalizations.of(context)!;
    final content = _extractAssistantContent(response);
    final parsedContent = _tryParseJson(content);

    return _buildSectionContainer(
      context,
      title: l10n.aiLastLogResponse,
      trailing: completionTokens == null
          ? null
          : _buildMetaChip(context, 'completion_tokens: $completionTokens'),
      children: [
        if (log.statusCode != null)
          _buildKeyValue(context, l10n.aiLastLogHttpCode, '${log.statusCode}'),
        if (parsedContent != null) ...[
          const SizedBox(height: 12),
          _buildResponsePreview(
            context,
            left: (parsedContent['left'] ?? '').toString(),
            right: (parsedContent['right'] ?? '').toString(),
          ),
        ] else if (content.isNotEmpty) ...[
          const SizedBox(height: 12),
          _buildSection(
            context,
            title: l10n.aiLastLogAssistantContent,
            content: _formatAssistantContent(content),
          ),
        ],
      ],
    );
  }

  Widget _buildResponsePreview(
    BuildContext context, {
    required String left,
    required String right,
  }) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: cs.primaryContainer.withValues(alpha: 0.45),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildPreviewLine(
            context,
            icon: Icons.west_rounded,
            label: AppLocalizations.of(context)!.aiLastLogLeftText,
            value: left,
          ),
          const SizedBox(height: 10),
          _buildPreviewLine(
            context,
            icon: Icons.east_rounded,
            label: AppLocalizations.of(context)!.aiLastLogRightText,
            value: right,
          ),
        ],
      ),
    );
  }

  Widget _buildPreviewLine(
    BuildContext context, {
    required IconData icon,
    required String label,
    required String value,
  }) {
    final cs = Theme.of(context).colorScheme;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: 28,
          height: 28,
          decoration: BoxDecoration(
            color: cs.primary.withValues(alpha: 0.12),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Icon(icon, size: 16, color: cs.primary),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: Theme.of(
                  context,
                ).textTheme.labelMedium?.copyWith(color: cs.onSurfaceVariant),
              ),
              const SizedBox(height: 2),
              SelectableText(
                value.isEmpty ? '-' : value,
                style: Theme.of(
                  context,
                ).textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildSectionContainer(
    BuildContext context, {
    required String title,
    required List<Widget> children,
    Widget? trailing,
  }) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: cs.surfaceBright,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: cs.outlineVariant),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Text(
                  title,
                  style: Theme.of(context).textTheme.titleSmall,
                ),
              ),
              if (trailing != null) ...[const SizedBox(width: 8), trailing],
            ],
          ),
          const SizedBox(height: 12),
          ...children,
        ],
      ),
    );
  }

  Widget _buildInlineSectionHeader(
    BuildContext context, {
    required String title,
    String? chipText,
  }) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Expanded(
          child: Text(title, style: Theme.of(context).textTheme.titleSmall),
        ),
        if (chipText != null) _buildMetaChip(context, chipText),
      ],
    );
  }

  Widget _buildRequestMetaSection(
    BuildContext context,
    AiLogEntry log,
    Map<String, dynamic> request,
  ) {
    final l10n = AppLocalizations.of(context)!;
    final cs = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;

    return Theme(
      data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
      child: Container(
        decoration: BoxDecoration(
          color: cs.surfaceContainerHighest.withValues(alpha: 0.35),
          borderRadius: BorderRadius.circular(10),
        ),
        child: ExpansionTile(
          tilePadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 2),
          childrenPadding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
          shape: const Border(),
          collapsedShape: const Border(),
          title: Text(l10n.aiApiSection, style: textTheme.titleSmall),
          subtitle: Text(
            '${l10n.aiModelLabel} · ${request['model'] ?? log.model}',
            style: textTheme.bodySmall?.copyWith(color: cs.onSurfaceVariant),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          children: [
            _buildKeyValue(context, l10n.aiUrlLabel, log.url),
            _buildKeyValue(
              context,
              l10n.aiModelLabel,
              (request['model'] ?? log.model).toString(),
            ),
            if (request['temperature'] != null)
              _buildKeyValue(
                context,
                l10n.aiTemperatureTitle,
                request['temperature'].toString(),
              ),
            if (request['max_tokens'] != null)
              _buildKeyValue(
                context,
                l10n.aiMaxTokensTitle,
                request['max_tokens'].toString(),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildSection(
    BuildContext context, {
    required String title,
    required String content,
    bool emphasize = false,
  }) {
    final cs = Theme.of(context).colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: Theme.of(context).textTheme.labelLarge),
        const SizedBox(height: 6),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: emphasize ? cs.errorContainer : cs.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(10),
          ),
          child: SelectableText(
            content,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: emphasize ? cs.onErrorContainer : cs.onSurface,
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildCodeBlock(BuildContext context, String content) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: cs.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(10),
      ),
      child: SelectionArea(
        child: Text(
          content,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
            fontFamily: 'monospace',
            color: cs.onSurface,
          ),
        ),
      ),
    );
  }

  Widget _buildKeyValue(BuildContext context, String label, String value) {
    final cs = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 108,
            child: Text(
              label,
              style: Theme.of(
                context,
              ).textTheme.bodySmall?.copyWith(color: cs.onSurfaceVariant),
            ),
          ),
          Expanded(
            child: SelectableText(
              value.isEmpty ? '-' : value,
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMessageBubble(
    BuildContext context, {
    required String role,
    required String content,
  }) {
    final cs = Theme.of(context).colorScheme;
    final isSystem = role == 'system';
    final isUser = role == 'user';
    final bg = isSystem
        ? cs.secondaryContainer
        : isUser
        ? cs.primaryContainer
        : cs.surfaceContainerHighest;
    final fg = isSystem
        ? cs.onSecondaryContainer
        : isUser
        ? cs.onPrimaryContainer
        : cs.onSurface;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(10),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            role.toUpperCase(),
            style: Theme.of(context).textTheme.labelMedium?.copyWith(
              color: fg,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 6),
          SelectableText(
            content,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(color: fg),
          ),
        ],
      ),
    );
  }

  Widget _buildMetaChip(BuildContext context, String text) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: cs.surfaceBright,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: cs.outlineVariant),
      ),
      child: Text(text, style: Theme.of(context).textTheme.labelMedium),
    );
  }

  Widget _buildSummaryItem(
    BuildContext context, {
    required String label,
    required String value,
  }) {
    final cs = Theme.of(context).colorScheme;
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: textTheme.labelMedium?.copyWith(color: cs.onSurfaceVariant),
        ),
        const SizedBox(height: 2),
        Text(
          value,
          style: textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600),
        ),
      ],
    );
  }

  Map<String, dynamic>? _tryParseJson(String raw) {
    if (raw.isEmpty) return null;
    try {
      return Map<String, dynamic>.from(jsonDecode(raw) as Map);
    } catch (_) {
      final cleaned = raw
          .trim()
          .replaceFirst(RegExp(r'^```json\s*'), '')
          .replaceFirst(RegExp(r'^```\s*'), '')
          .replaceFirst(RegExp(r'\s*```$'), '');
      try {
        return Map<String, dynamic>.from(jsonDecode(cleaned) as Map);
      } catch (_) {
        return null;
      }
    }
  }

  String _extractAssistantContent(Map<String, dynamic>? response) {
    if (response == null) return '';
    final choices = response['choices'];
    if (choices is! List || choices.isEmpty) return '';
    final first = choices.first;
    if (first is! Map) return '';
    final message = first['message'];
    if (message is! Map) return '';
    return (message['content'] ?? '').toString().trim();
  }

  Map<String, String> _extractUsage(Map<String, dynamic>? response) {
    if (response == null) return const {};
    final usage = response['usage'];
    if (usage is! Map) return const {};
    final map = Map<String, dynamic>.from(usage);
    final result = <String, String>{};
    for (final key in ['prompt_tokens', 'completion_tokens', 'total_tokens']) {
      if (map[key] != null) {
        result[key] = map[key].toString();
      }
    }
    return result;
  }

  String _formatAssistantContent(String content) {
    final parsed = _tryParseJson(content);
    if (parsed == null) return content;
    return const JsonEncoder.withIndent('  ').convert(parsed);
  }

  String _statusText(AiLogEntry log) {
    if (log.error.isNotEmpty) return log.error;
    if (log.statusCode != null) return 'HTTP ${log.statusCode}';
    return 'Pending';
  }

  String _sourceLabel(AppLocalizations l10n, String source) {
    return switch (source) {
      'notification' => l10n.aiLastLogSourceNotification,
      'settings_test' => l10n.aiLastLogSourceSettingsTest,
      _ => source,
    };
  }

  String _formatTime(DateTime time) {
    final y = time.year.toString().padLeft(4, '0');
    final m = time.month.toString().padLeft(2, '0');
    final d = time.day.toString().padLeft(2, '0');
    final hh = time.hour.toString().padLeft(2, '0');
    final mm = time.minute.toString().padLeft(2, '0');
    final ss = time.second.toString().padLeft(2, '0');
    return '$y-$m-$d $hh:$mm:$ss';
  }
}

enum _AiLogViewMode { rendered, raw }

class _TestResult {
  final bool success;
  final String message;
  const _TestResult.ok(this.message) : success = true;
  const _TestResult.fail(this.message) : success = false;
}

class _TestResultCard extends StatelessWidget {
  const _TestResultCard({required this.result});
  final _TestResult result;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final color = result.success ? cs.primaryContainer : cs.errorContainer;
    final onColor = result.success
        ? cs.onPrimaryContainer
        : cs.onErrorContainer;
    final icon = result.success
        ? Icons.check_circle_outline
        : Icons.error_outline;

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: color,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: onColor, size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              result.message,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: onColor,
                fontFamily: 'monospace',
              ),
            ),
          ),
        ],
      ),
    );
  }
}
