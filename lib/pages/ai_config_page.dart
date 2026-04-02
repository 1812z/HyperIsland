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
  static const _defaultAiPrompt = '根据通知信息，提取关键信息，左右分别不超过6汉字12字符';

  late final TextEditingController _urlCtrl;
  late final TextEditingController _keyCtrl;
  late final TextEditingController _modelCtrl;
  late final TextEditingController _promptCtrl;

  bool _keyObscured = true;
  bool _testing = false;
  _TestResult? _testResult;
  late int _aiTimeoutDraft;
  late double _aiTemperatureDraft;
  late int _aiMaxTokensDraft;
  late bool _aiEnabledValue;
  late bool _aiPromptInUserValue;

  void _onCtrlChanged() {
    if (!mounted) return;
    final nextTimeout = _ctrl.aiTimeout;
    final nextTemperature = _ctrl.aiTemperature;
    final nextMaxTokens = _ctrl.aiMaxTokens;
    final nextAiEnabled = _ctrl.aiEnabled;
    final nextPromptInUser = _ctrl.aiPromptInUser;
    if (nextTimeout == _aiTimeoutDraft &&
        nextTemperature == _aiTemperatureDraft &&
        nextMaxTokens == _aiMaxTokensDraft &&
        nextAiEnabled == _aiEnabledValue &&
        nextPromptInUser == _aiPromptInUserValue) {
      return;
    }
    setState(() {
      _aiTimeoutDraft = nextTimeout;
      _aiTemperatureDraft = nextTemperature;
      _aiMaxTokensDraft = nextMaxTokens;
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
    _promptCtrl = TextEditingController(
      text: _ctrl.aiPrompt.isEmpty ? _defaultAiPrompt : _ctrl.aiPrompt,
    );
    _aiTimeoutDraft = _ctrl.aiTimeout;
    _aiTemperatureDraft = _ctrl.aiTemperature;
    _aiMaxTokensDraft = _ctrl.aiMaxTokens;
    _aiEnabledValue = _ctrl.aiEnabled;
    _aiPromptInUserValue = _ctrl.aiPromptInUser;
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
          behavior: SnackBarBehavior.floating,
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

  Future<void> _onAiEnabledChanged(bool value) async {
    if (_aiEnabledValue == value) return;
    await InteractionHaptics.toggle();
    setState(() => _aiEnabledValue = value);
    await _ctrl.setAiEnabled(value);
  }

  Future<void> _onAiPromptInUserChanged(bool value) async {
    if (_aiPromptInUserValue == value) return;
    await InteractionHaptics.toggle();
    setState(() => _aiPromptInUserValue = value);
    await _ctrl.setAiPromptInUser(value);
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
                    value: _aiEnabledValue,
                    onChanged: _onAiEnabledChanged,
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
                        SwitchListTile(
                          contentPadding: EdgeInsets.zero,
                          title: Text(l10n.aiPromptInUserTitle),
                          subtitle: Text(l10n.aiPromptInUserSubtitle),
                          value: _aiPromptInUserValue,
                          onChanged: _onAiPromptInUserChanged,
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
                                    '${_aiTimeoutDraft}s',
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
                            label: '${_aiTimeoutDraft}s',
                            onChanged: (v) async {
                              await InteractionHaptics.sliderTick();
                              _onTimeoutChanged(v);
                            },
                            onChangeEnd: _persistTimeout,
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
                            onChanged: (v) async {
                              await InteractionHaptics.sliderTick();
                              _onTemperatureChanged(v);
                            },
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
                            onChanged: (v) async {
                              await InteractionHaptics.sliderTick();
                              _onMaxTokensChanged(v);
                            },
                            onChangeEnd: _persistMaxTokens,
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
