import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:font_awesome_flutter/font_awesome_flutter.dart';
import '../controllers/settings_controller.dart';
import '../l10n/generated/app_localizations.dart';
import '../services/interaction_haptics.dart';
import '../widgets/section_label.dart';
import '../widgets/blur_app_bar.dart';
import '../widgets/modern_slider.dart';

class AiConfigPage extends StatefulWidget {
  const AiConfigPage({super.key});

  @override
  State<AiConfigPage> createState() => _AiConfigPageState();
}

class _AiConfigPageState extends State<AiConfigPage> {
  final _ctrl = SettingsController.instance;
  static const _platform = MethodChannel('io.github.hyperisland/test');

  late final TextEditingController _urlCtrl;
  late final TextEditingController _keyCtrl;
  late final TextEditingController _modelCtrl;
  late final TextEditingController _promptCtrl;
  late final TextEditingController _notificationCtrl;

  bool _keyObscured = true;
  bool _testing = false;
  bool _sendingNotification = false;
  bool _sendingAiNotification = false;
  _TestResult? _testResult;
  late int _aiTimeoutDraft;
  late double _aiTemperatureDraft;
  late int _aiMaxTokensDraft;
  late int _aiTriggerCharCountDraft;
  late bool _aiEnabledValue;
  late bool _aiPromptInUserValue;
  bool _localizedDefaultsInitialized = false;

  void _onCtrlChanged() {
    if (!mounted) return;
    final nextTimeout = _ctrl.aiTimeout;
    final nextTemperature = _ctrl.aiTemperature;
    final nextMaxTokens = _ctrl.aiMaxTokens;
    final nextTriggerCharCount = _ctrl.aiTriggerCharCount;
    final nextAiEnabled = _ctrl.aiEnabled;
    final nextPromptInUser = _ctrl.aiPromptInUser;
    if (nextTimeout == _aiTimeoutDraft &&
        nextTemperature == _aiTemperatureDraft &&
        nextMaxTokens == _aiMaxTokensDraft &&
        nextTriggerCharCount == _aiTriggerCharCountDraft &&
        nextAiEnabled == _aiEnabledValue &&
        nextPromptInUser == _aiPromptInUserValue) {
      return;
    }
    setState(() {
      _aiTimeoutDraft = nextTimeout;
      _aiTemperatureDraft = nextTemperature;
      _aiMaxTokensDraft = nextMaxTokens;
      _aiTriggerCharCountDraft = nextTriggerCharCount;
      _aiEnabledValue = nextAiEnabled;
      _aiPromptInUserValue = nextPromptInUser;
    });
  }

  @override
  void initState() {
    super.initState();
    _ctrl.addListener(_onCtrlChanged);
    _urlCtrl = TextEditingController(text: _ctrl.aiUrl);
    _keyCtrl = TextEditingController(text: _ctrl.aiApiKey);
    _modelCtrl = TextEditingController(text: _ctrl.aiModel);
    _promptCtrl = TextEditingController(text: _ctrl.aiPrompt);
    _notificationCtrl = TextEditingController();
    _aiTimeoutDraft = _ctrl.aiTimeout;
    _aiTemperatureDraft = _ctrl.aiTemperature;
    _aiMaxTokensDraft = _ctrl.aiMaxTokens;
    _aiTriggerCharCountDraft = _ctrl.aiTriggerCharCount;
    _aiEnabledValue = _ctrl.aiEnabled;
    _aiPromptInUserValue = _ctrl.aiPromptInUser;
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_localizedDefaultsInitialized) return;
    final l10n = AppLocalizations.of(context)!;
    if (_promptCtrl.text.isEmpty) {
      _promptCtrl.text = l10n.aiPromptDefault;
    }
    _notificationCtrl.text = l10n.aiDefaultNotificationText;
    _localizedDefaultsInitialized = true;
  }

  @override
  void dispose() {
    _ctrl.removeListener(_onCtrlChanged);
    _urlCtrl.dispose();
    _keyCtrl.dispose();
    _modelCtrl.dispose();
    _promptCtrl.dispose();
    _notificationCtrl.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final nextUrl = _urlCtrl.text.trim();
    final nextKey = _keyCtrl.text.trim();
    final nextModel = _modelCtrl.text.trim();
    final nextPrompt = _promptCtrl.text.trim();

    if (nextUrl != _ctrl.aiUrl) await _ctrl.setAiUrl(nextUrl);
    if (nextKey != _ctrl.aiApiKey) await _ctrl.setAiApiKey(nextKey);
    if (nextModel != _ctrl.aiModel) await _ctrl.setAiModel(nextModel);
    if (nextPrompt != _ctrl.aiPrompt) await _ctrl.setAiPrompt(nextPrompt);
    if (_aiTimeoutDraft != _ctrl.aiTimeout) {
      await _ctrl.setAiTimeout(_aiTimeoutDraft);
    }
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context)!.aiConfigSaved),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
        ),
      );
    }
  }

  void _onTimeoutChanged(double value) {
    final next = value.round();
    if (_aiTimeoutDraft == next) return;
    setState(() => _aiTimeoutDraft = next);
  }

  Future<void> _persistTimeout(double value) async {
    final next = value.round();
    if (_ctrl.aiTimeout == next) return;
    await _ctrl.setAiTimeout(next);
  }

  void _onTemperatureChanged(double value) {
    if (_aiTemperatureDraft == value) return;
    setState(() => _aiTemperatureDraft = value);
  }

  Future<void> _persistTemperature(double value) async {
    if (_ctrl.aiTemperature == value) return;
    await _ctrl.setAiTemperature(value);
  }

  void _onMaxTokensChanged(double value) {
    final next = value.round();
    if (_aiMaxTokensDraft == next) return;
    setState(() => _aiMaxTokensDraft = next);
  }

  Future<void> _persistMaxTokens(double value) async {
    final next = value.round();
    if (_ctrl.aiMaxTokens == next) return;
    await _ctrl.setAiMaxTokens(next);
  }

  void _onTriggerCharCountChanged(double value) {
    final next = value.round();
    if (_aiTriggerCharCountDraft == next) return;
    setState(() => _aiTriggerCharCountDraft = next);
  }

  Future<void> _persistTriggerCharCount(double value) async {
    final next = value.round();
    if (_ctrl.aiTriggerCharCount == next) return;
    await _ctrl.setAiTriggerCharCount(next);
  }

  Future<void> _onAiEnabledChanged(bool value) async {
    if (_aiEnabledValue == value) return;
    setState(() => _aiEnabledValue = value);
    await _ctrl.setAiEnabled(value);
  }

  Future<void> _onAiPromptInUserChanged(bool value) async {
    if (_aiPromptInUserValue == value) return;
    setState(() => _aiPromptInUserValue = value);
    await _ctrl.setAiPromptInUser(value);
  }

  String _effectiveModel(String model) => model.isEmpty ? 'gpt-4o-mini' : model;

  /// Derive the `/models` endpoint from the chat completions URL.
  ///
  /// Handles common shapes:
  /// - `https://host/v1/chat/completions` -> `https://host/v1/models`
  /// - `https://host/v1/something`        -> `https://host/v1/models`
  /// - `https://host/v1`                  -> `https://host/v1/models`
  /// - anything else                      -> append `/models`
  String _deriveModelsUrl(String chatUrl) {
    var u = chatUrl.trim();
    while (u.endsWith('/')) {
      u = u.substring(0, u.length - 1);
    }
    if (u.isEmpty) return '';
    final lower = u.toLowerCase();
    final chatIdx = lower.lastIndexOf('/chat/completions');
    if (chatIdx >= 0) {
      return '${u.substring(0, chatIdx)}/models';
    }
    final v1Idx = lower.indexOf('/v1/');
    if (v1Idx >= 0) {
      return '${u.substring(0, v1Idx + 3)}/models';
    }
    if (lower.endsWith('/v1')) {
      return '$u/models';
    }
    return '$u/models';
  }

  Future<void> _pickModel() async {
    final l10n = AppLocalizations.of(context)!;
    final url = _urlCtrl.text.trim();
    if (url.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(l10n.aiTestUrlEmpty),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
        ),
      );
      return;
    }
    final selected = await showDialog<String>(
      context: context,
      builder: (_) => _ModelPickerDialog(
        modelsUrl: _deriveModelsUrl(url),
        apiKey: _keyCtrl.text.trim(),
        currentModel: _modelCtrl.text.trim(),
      ),
    );
    if (selected != null && mounted) {
      setState(() => _modelCtrl.text = selected);
    }
  }

  Map<String, dynamic> _buildRequestPayload({
    required String model,
    required String promptText,
    required String userContent,
  }) {
    final effectiveModel = _effectiveModel(model);
    return <String, dynamic>{
      'model': effectiveModel,
      'messages': [
        if (!_ctrl.aiPromptInUser && promptText.isNotEmpty)
          {'role': 'system', 'content': promptText},
        if (_ctrl.aiPromptInUser && promptText.isNotEmpty)
          {'role': 'user', 'content': promptText},
        {'role': 'user', 'content': userContent},
      ],
      'max_tokens': _ctrl.aiMaxTokens,
      'temperature': _ctrl.aiTemperature,
      'enable_thinking': false,
      'thinking': {'type': 'disabled'},
    };
  }

  Future<void> _test() async {
    final url = _urlCtrl.text.trim();
    final key = _keyCtrl.text.trim();
    final model = _modelCtrl.text.trim();
    final effectiveModel = _effectiveModel(model);
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
      final sampleUserContent = AppLocalizations.of(
        context,
      )!.aiTestSampleUserContent;
      requestBody = jsonEncode(
        _buildRequestPayload(
          model: model,
          promptText: '',
          userContent: sampleUserContent,
        ),
      );
      await _ctrl.saveAiLastLog(
        AiLogEntry(
          timestamp: requestTime,
          source: 'settings_test',
          url: url,
          model: effectiveModel,
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
            model: effectiveModel,
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
            model: effectiveModel,
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
          model: effectiveModel,
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

  Future<String> _requestAiNotificationText(String content) async {
    final l10n = AppLocalizations.of(context)!;
    final url = _urlCtrl.text.trim();
    final key = _keyCtrl.text.trim();
    final model = _modelCtrl.text.trim();
    if (url.isEmpty) {
      throw Exception(l10n.aiTestUrlEmpty);
    }

    final promptText = _promptCtrl.text.trim().isEmpty
        ? l10n.aiPromptDefault
        : _promptCtrl.text.trim();
    final userContent = l10n.aiNotificationUserContent(content);
    final jsonExample = jsonEncode({
      'left': l10n.aiJsonLeftDescription,
      'right': l10n.aiJsonRightDescription,
    });
    final messages = _ctrl.aiPromptInUser
        ? [
            {
              'role': 'user',
              'content': [
                promptText,
                '',
                l10n.aiJsonOnlyInstruction,
                jsonExample,
                '',
                userContent,
              ].join('\n'),
            },
          ]
        : [
            {
              'role': 'system',
              'content': [
                promptText,
                l10n.aiJsonOnlyInstruction,
                jsonExample,
              ].join('\n'),
            },
            {'role': 'user', 'content': userContent},
          ];
    final requestBody = jsonEncode(<String, dynamic>{
      'model': _effectiveModel(model),
      'messages': messages,
      'max_tokens': _ctrl.aiMaxTokens,
      'temperature': _ctrl.aiTemperature,
      'enable_thinking': false,
      'thinking': {'type': 'disabled'},
    });
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

    if (response.statusCode != 200) {
      throw Exception('HTTP ${response.statusCode}\n${response.body}');
    }
    final json = jsonDecode(response.body) as Map<String, dynamic>;
    return ((json['choices'] as List?)?.firstOrNull?['message']?['content']
                as String? ??
            '')
        .trim();
  }

  (String, String) _splitAiNotificationText(String aiText) {
    final cleanText = aiText
        .replaceFirst(RegExp(r'^```json\s*'), '')
        .replaceFirst(RegExp(r'^```\s*'), '')
        .replaceFirst(RegExp(r'\s*```$'), '')
        .trim();
    try {
      final json = jsonDecode(cleanText) as Map<String, dynamic>;
      final left = (json['left'] as String? ?? '').trim();
      final right = (json['right'] as String? ?? '').trim();
      if (left.isNotEmpty && right.isNotEmpty) {
        return (left, right);
      }
    } on Exception {
      throw Exception(AppLocalizations.of(context)!.aiInvalidJsonError);
    }
    throw Exception(AppLocalizations.of(context)!.aiEmptyJsonError);
  }

  Future<void> _sendNotification({required bool useAi}) async {
    final l10n = AppLocalizations.of(context)!;
    final rawContent = _notificationCtrl.text.trim();
    final content = rawContent.isEmpty
        ? l10n.aiDefaultNotificationText
        : rawContent;
    setState(() {
      if (useAi) {
        _sendingAiNotification = true;
      } else {
        _sendingNotification = true;
      }
    });

    try {
      final (title, body) = useAi
          ? _splitAiNotificationText(await _requestAiNotificationText(content))
          : (l10n.aiTestNotificationTitle, content);
      await _platform.invokeMethod('showCustomTest', {
        'title': title,
        'content': body,
        'clearPrevious': true,
        'enableFloat': true,
      });
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            useAi ? l10n.aiAiNotificationSent : l10n.aiNotificationSent,
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
        ),
      );
    } on Exception catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(e.toString()),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
        ),
      );
    } finally {
      if (mounted) {
        setState(() {
          if (useAi) {
            _sendingAiNotification = false;
          } else {
            _sendingNotification = false;
          }
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
    final textTheme = Theme.of(context).textTheme;

    return Scaffold(
      backgroundColor: cs.surface,
      body: BlurAppBarHost(
        title: l10n.aiConfigTitle,
        largeTitle: true,
        slivers: [
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                SectionLabel(l10n.aiConfigSection),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
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
                    value: _aiEnabledValue,
                    onChanged: InteractionHaptics.interceptToggle(
                      _onAiEnabledChanged,
                    ),
                  ),
                ),
                const SizedBox(height: 24),

                SectionLabel(l10n.aiApiSection),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
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
                            onPressed: InteractionHaptics.interceptButton(() {
                              setState(() => _keyObscured = !_keyObscured);
                            }),
                          ),
                        ),
                        const SizedBox(height: 16),
                        _buildTextField(
                          controller: _modelCtrl,
                          label: l10n.aiModelLabel,
                          hint: l10n.aiModelHint,
                          icon: FontAwesomeIcons.lightbulb,
                          suffix: IconButton(
                            tooltip: l10n.aiModelPickerTitle,
                            icon: const FaIcon(
                              FontAwesomeIcons.magnifyingGlass,
                              size: 16,
                            ),
                            onPressed: InteractionHaptics.interceptButton(
                              _pickModel,
                            ),
                          ),
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
                        SwitchListTile(
                          contentPadding: EdgeInsets.zero,
                          title: Text(l10n.aiPromptInUserTitle),
                          subtitle: Text(l10n.aiPromptInUserSubtitle),
                          value: _aiPromptInUserValue,
                          onChanged: InteractionHaptics.interceptToggle(
                            _onAiPromptInUserChanged,
                          ),
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
                                  Text(
                                    l10n.aiTimeoutLabel(_aiTimeoutDraft),
                                    style: textTheme.bodySmall?.copyWith(
                                      color: cs.onSurfaceVariant,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                        SliderTheme(
                          data: ModernSliderTheme.theme(context),
                          child: Slider(
                            value: _aiTimeoutDraft.toDouble(),
                            min: 2,
                            max: 15,
                            divisions: 13,
                            label: l10n.aiTimeoutLabel(_aiTimeoutDraft),
                            onChanged: InteractionHaptics.interceptSlider(
                              _onTimeoutChanged,
                            ),
                            onChangeEnd: _persistTimeout,
                          ),
                        ),

                        const SizedBox(height: 16),
                        Row(
                          children: [
                            const FaIcon(FontAwesomeIcons.textWidth, size: 18),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    l10n.aiTriggerCharCountTitle,
                                    style: textTheme.titleMedium,
                                  ),
                                  Text(
                                    _aiTriggerCharCountDraft == 0
                                        ? l10n.aiTriggerCharCountAlways
                                        : l10n.aiTriggerCharCountSubtitle,
                                    style: textTheme.bodySmall?.copyWith(
                                      color: cs.onSurfaceVariant,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            Text(
                              '$_aiTriggerCharCountDraft',
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
                            value: _aiTriggerCharCountDraft.toDouble(),
                            min: 0,
                            max: 100,
                            divisions: 20,
                            label: '$_aiTriggerCharCountDraft',
                            onChanged: InteractionHaptics.interceptSlider(
                              _onTriggerCharCountChanged,
                            ),
                            onChangeEnd: _persistTriggerCharCount,
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
                              _aiTemperatureDraft.toStringAsFixed(1),
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
                            value: _aiTemperatureDraft,
                            min: 0,
                            max: 1,
                            divisions: 10,
                            label: _aiTemperatureDraft.toStringAsFixed(1),
                            onChanged: InteractionHaptics.interceptSlider(
                              _onTemperatureChanged,
                            ),
                            onChangeEnd: _persistTemperature,
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
                              '$_aiMaxTokensDraft',
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
                            value: _aiMaxTokensDraft.toDouble(),
                            min: 20,
                            max: 100,
                            divisions: 80,
                            label: '$_aiMaxTokensDraft',
                            onChanged: InteractionHaptics.interceptSlider(
                              _onMaxTokensChanged,
                            ),
                            onChangeEnd: _persistMaxTokens,
                          ),
                        ),

                        const SizedBox(height: 16),
                        Row(
                          children: [
                            Expanded(
                              child: OutlinedButton.icon(
                                onPressed: _testing
                                    ? null
                                    : InteractionHaptics.interceptButton(_test),
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
                                ),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: FilledButton.icon(
                                onPressed: InteractionHaptics.interceptButton(
                                  _save,
                                ),
                                icon: const FaIcon(
                                  FontAwesomeIcons.floppyDisk,
                                  size: 16,
                                ),
                                label: Text(l10n.aiConfigSaveButton),
                                style: FilledButton.styleFrom(
                                  padding: const EdgeInsets.symmetric(
                                    vertical: 12,
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

                SectionLabel(l10n.notificationTest),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _buildTextField(
                          controller: _notificationCtrl,
                          label: l10n.aiNotificationContentLabel,
                          hint: l10n.aiDefaultNotificationText,
                          icon: FontAwesomeIcons.bell,
                          minLines: 2,
                          maxLines: 4,
                        ),
                        const SizedBox(height: 16),
                        Row(
                          children: [
                            Expanded(
                              child: OutlinedButton.icon(
                                onPressed:
                                    _sendingNotification ||
                                        _sendingAiNotification
                                    ? null
                                    : InteractionHaptics.interceptButton(
                                        () => _sendNotification(useAi: false),
                                      ),
                                icon: _sendingNotification
                                    ? const SizedBox(
                                        width: 16,
                                        height: 16,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2,
                                        ),
                                      )
                                    : const FaIcon(
                                        FontAwesomeIcons.paperPlane,
                                        size: 16,
                                      ),
                                label: Text(l10n.aiSendNotificationButton),
                                style: OutlinedButton.styleFrom(
                                  padding: const EdgeInsets.symmetric(
                                    vertical: 12,
                                  ),
                                ),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: FilledButton.icon(
                                onPressed:
                                    _sendingNotification ||
                                        _sendingAiNotification
                                    ? null
                                    : InteractionHaptics.interceptButton(
                                        () => _sendNotification(useAi: true),
                                      ),
                                icon: _sendingAiNotification
                                    ? const SizedBox(
                                        width: 16,
                                        height: 16,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2,
                                        ),
                                      )
                                    : const FaIcon(
                                        FontAwesomeIcons.robot,
                                        size: 16,
                                      ),
                                label: Text(l10n.aiSendAiNotificationButton),
                                style: FilledButton.styleFrom(
                                  padding: const EdgeInsets.symmetric(
                                    vertical: 12,
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                Card(
                  elevation: 0,
                  color: cs.secondaryContainer.withValues(alpha: 0.5),
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

/// Dialog that fetches the model list from an OpenAI-compatible
class _ModelPickerDialog extends StatefulWidget {
  const _ModelPickerDialog({
    required this.modelsUrl,
    required this.apiKey,
    required this.currentModel,
  });

  final String modelsUrl;
  final String apiKey;
  final String currentModel;

  @override
  State<_ModelPickerDialog> createState() => _ModelPickerDialogState();
}

class _ModelPickerDialogState extends State<_ModelPickerDialog> {
  final _searchCtrl = TextEditingController();
  List<String> _models = const [];
  bool _loading = true;
  String? _error;
  String _query = '';

  @override
  void initState() {
    super.initState();
    _fetch();
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  Future<void> _fetch() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final response = await http
          .get(
            Uri.parse(widget.modelsUrl),
            headers: {
              'Accept': 'application/json',
              if (widget.apiKey.isNotEmpty)
                'Authorization': 'Bearer ${widget.apiKey}',
            },
          )
          .timeout(const Duration(seconds: 15));
      if (response.statusCode != 200) {
        throw Exception('HTTP ${response.statusCode}');
      }
      final decoded = jsonDecode(response.body);
      final data = decoded is Map ? decoded['data'] : null;
      if (data is! List) {
        throw Exception('Unexpected response format');
      }
      final models = <String>[];
      for (final item in data) {
        if (item is Map) {
          final id = item['id'];
          if (id is String && id.isNotEmpty) models.add(id);
        }
      }
      models.sort();
      if (!mounted) return;
      setState(() {
        _models = models;
        _loading = false;
      });
    } on Exception catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  List<String> get _filtered {
    if (_query.isEmpty) return _models;
    final q = _query.toLowerCase();
    return _models.where((m) => m.toLowerCase().contains(q)).toList();
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
    final filtered = _filtered;
    final canSearch = !_loading && _error == null;

    return AlertDialog(
      title: Row(
        children: [
          FaIcon(
            FontAwesomeIcons.magnifyingGlass,
            size: 18,
            color: cs.primary,
          ),
          const SizedBox(width: 8),
          Expanded(child: Text(l10n.aiModelPickerTitle)),
        ],
      ),
      content: SizedBox(
        width: double.maxFinite,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (canSearch) ...[
              TextField(
                controller: _searchCtrl,
                decoration: InputDecoration(
                  hintText: l10n.aiModelPickerSearchHint,
                  prefixIcon: const Icon(Icons.search, size: 20),
                  isDense: true,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                onChanged: (v) => setState(() => _query = v),
              ),
              const SizedBox(height: 8),
            ],
            if (_loading)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 32),
                child: Center(child: CircularProgressIndicator()),
              )
            else if (_error != null)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 16),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      l10n.aiModelPickerFetchError,
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      _error!,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: cs.error,
                        fontFamily: 'monospace',
                      ),
                      textAlign: TextAlign.center,
                    ),
                    const SizedBox(height: 12),
                    OutlinedButton.icon(
                      onPressed: InteractionHaptics.interceptButton(_fetch),
                      icon: const Icon(Icons.refresh, size: 18),
                      label: Text(l10n.aiModelPickerRetry),
                    ),
                  ],
                ),
              )
            else if (filtered.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 24),
                child: Text(
                  l10n.aiModelPickerEmpty,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: cs.onSurfaceVariant,
                  ),
                  textAlign: TextAlign.center,
                ),
              )
            else
              ConstrainedBox(
                constraints: BoxConstraints(
                  maxHeight: MediaQuery.of(context).size.height * 0.5,
                ),
                child: ListView.separated(
                  shrinkWrap: true,
                  itemCount: filtered.length,
                  separatorBuilder: (_, __) => Divider(
                    height: 1,
                    color: cs.outlineVariant.withValues(alpha: 0.5),
                  ),
                  itemBuilder: (context, index) {
                    final m = filtered[index];
                    final selected = m == widget.currentModel;
                    return ListTile(
                      dense: true,
                      title: Text(
                        m,
                        style: const TextStyle(
                          fontFamily: 'monospace',
                          fontSize: 13,
                        ),
                      ),
                      trailing: selected
                          ? FaIcon(
                              FontAwesomeIcons.check,
                              size: 14,
                              color: cs.primary,
                            )
                          : null,
                      onTap: InteractionHaptics.interceptButton(
                        () => Navigator.of(context).pop(m),
                      ),
                    );
                  },
                ),
              ),
          ],
        ),
      ),
      actions: [
        FilledButton.tonal(
          onPressed: InteractionHaptics.interceptButton(
            () => Navigator.of(context).pop(),
          ),
          child: Text(l10n.aiModelPickerClose),
        ),
      ],
    );
  }
}
