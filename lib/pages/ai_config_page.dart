import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import '../controllers/settings_controller.dart';
import '../l10n/generated/app_localizations.dart';
import '../widgets/section_label.dart';

class AiConfigPage extends StatefulWidget {
  const AiConfigPage({super.key});

  @override
  State<AiConfigPage> createState() => _AiConfigPageState();
}

class _AiConfigPageState extends State<AiConfigPage> {
  final _ctrl = SettingsController.instance;

  late final TextEditingController _urlCtrl;
  late final TextEditingController _keyCtrl;
  late final TextEditingController _modelCtrl;

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
    _urlCtrl = TextEditingController(text: _ctrl.aiUrl);
    _keyCtrl = TextEditingController(text: _ctrl.aiApiKey);
    _modelCtrl = TextEditingController(text: _ctrl.aiModel);
  }

  @override
  void dispose() {
    _ctrl.removeListener(_onCtrlChanged);
    _urlCtrl.dispose();
    _keyCtrl.dispose();
    _modelCtrl.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    await _ctrl.setAiUrl(_urlCtrl.text.trim());
    await _ctrl.setAiApiKey(_keyCtrl.text.trim());
    await _ctrl.setAiModel(_modelCtrl.text.trim());
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context)!.aiConfigSaved),
          duration: const Duration(seconds: 2),
        ),
      );
    }
  }

  Future<void> _test() async {
    final url = _urlCtrl.text.trim();
    final key = _keyCtrl.text.trim();
    final model = _modelCtrl.text.trim();

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
      final body = jsonEncode({
        'model': model.isEmpty ? 'gpt-4o-mini' : model,
        'messages': [
          {
            'role': 'user',
            'content': 'Reply with exactly: {"left":"test","right":"ok"}',
          },
        ],
        'max_tokens': 30,
        'temperature': 0,
      });

      final response = await http
          .post(
            Uri.parse(url),
            headers: {
              'Content-Type': 'application/json',
              'Accept': 'application/json',
              if (key.isNotEmpty) 'Authorization': 'Bearer $key',
            },
            body: body,
          )
          .timeout(const Duration(seconds: 10));

      if (response.statusCode == 200) {
        final json = jsonDecode(response.body) as Map<String, dynamic>;
        final content =
            (json['choices'] as List?)?.firstOrNull?['message']?['content']
                as String? ??
            '';
        setState(() => _testResult = _TestResult.ok(content.trim()));
      } else {
        setState(
          () => _testResult = _TestResult.fail(
            'HTTP ${response.statusCode}\n${response.body}',
          ),
        );
      }
    } on Exception catch (e) {
      setState(() => _testResult = _TestResult.fail(e.toString()));
    } finally {
      if (mounted) setState(() => _testing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;

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
                // Enable toggle
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
                    onChanged: _ctrl.setAiEnabled,
                  ),
                ),
                const SizedBox(height: 24),

                // API parameters
                SectionLabel(l10n.aiApiSection),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const SizedBox(height: 8),
                        // URL
                        TextField(
                          controller: _urlCtrl,
                          decoration: InputDecoration(
                            labelText: l10n.aiUrlLabel,
                            hintText: l10n.aiUrlHint,
                            border: const OutlineInputBorder(),
                            prefixIcon: const Icon(Icons.link),
                          ),
                          keyboardType: TextInputType.url,
                          autocorrect: false,
                        ),
                        const SizedBox(height: 16),
                        // API Key
                        TextField(
                          controller: _keyCtrl,
                          obscureText: _keyObscured,
                          decoration: InputDecoration(
                            labelText: l10n.aiApiKeyLabel,
                            hintText: l10n.aiApiKeyHint,
                            border: const OutlineInputBorder(),
                            prefixIcon: const Icon(Icons.key),
                            suffixIcon: IconButton(
                              icon: Icon(
                                _keyObscured
                                    ? Icons.visibility_off
                                    : Icons.visibility,
                              ),
                              onPressed: () =>
                                  setState(() => _keyObscured = !_keyObscured),
                            ),
                          ),
                          autocorrect: false,
                        ),
                        const SizedBox(height: 16),
                        // Model
                        TextField(
                          controller: _modelCtrl,
                          decoration: InputDecoration(
                            labelText: l10n.aiModelLabel,
                            hintText: l10n.aiModelHint,
                            border: const OutlineInputBorder(),
                            prefixIcon: const Icon(Icons.psychology_outlined),
                          ),
                          autocorrect: false,
                        ),
                        const SizedBox(height: 16),
                        // Buttons row
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
                                    : const Icon(Icons.wifi_tethering),
                                label: Text(l10n.aiTestButton),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: FilledButton.icon(
                                onPressed: _save,
                                icon: const Icon(Icons.save_outlined),
                                label: Text(l10n.aiConfigSaveButton),
                              ),
                            ),
                          ],
                        ),
                        // Test result
                        if (_testResult != null) ...[
                          const SizedBox(height: 12),
                          _TestResultCard(result: _testResult!),
                        ],
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                // Tips
                Card(
                  elevation: 0,
                  color: cs.secondaryContainer,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(
                          Icons.info_outline,
                          color: cs.onSecondaryContainer,
                          size: 20,
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
}

// ── 测试结果 ─────────────────────────────────────────────────────────────────

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
