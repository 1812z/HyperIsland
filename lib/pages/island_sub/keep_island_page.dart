import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../controllers/settings_controller.dart';
import '../../l10n/generated/app_localizations.dart';
import '../../services/interaction_haptics.dart';
import '../../widgets/blur_app_bar.dart';
import '../../widgets/color_picker_dialog.dart';

class KeepIslandPage extends StatefulWidget {
  const KeepIslandPage({super.key});

  @override
  State<KeepIslandPage> createState() => _KeepIslandPageState();
}

class _KeepIslandPageState extends State<KeepIslandPage> {
  final _ctrl = SettingsController.instance;
  late int _buildHash;

  static const _placeholders = [
    '{battery.power}',
    '{battery.voltage}',
    '{battery.current}',
    '{battery.level}',
    '{battery.temperature}',
    '{cpu.usage}',
    '{memory.usage}',
    '{memory.used}',
    '{memory.total}',
    '{cpu.temperature}',
    '{gpu.usage}',
    '{gpu.frequency}',
  ];

  int _computeHash() => Object.hashAll([
    _ctrl.keepIsland,
    _ctrl.keepIslandAutoHide,
    _ctrl.keepIslandHideLandscape,
    _ctrl.keepIslandHighlightColor,
    _ctrl.keepIslandLeftContent,
    _ctrl.keepIslandRightContent,
  ]);

  @override
  void initState() {
    super.initState();
    _buildHash = _computeHash();
    _ctrl.addListener(_onChanged);
  }

  @override
  void dispose() {
    _ctrl.removeListener(_onChanged);
    super.dispose();
  }

  void _onChanged() {
    if (!mounted) return;
    final nextHash = _computeHash();
    if (nextHash == _buildHash) return;
    setState(() => _buildHash = nextHash);
  }

  Future<void> _editContent({required bool left}) async {
    final initial = left ? _ctrl.keepIslandLeftContent : _ctrl.keepIslandRightContent;
    final l10n = AppLocalizations.of(context)!;
    final title = left ? l10n.keepIslandLeftContentTitle : l10n.keepIslandRightContentTitle;
    final result = await showDialog<String>(
      context: context,
      builder: (context) => _ContentDialog(title: title, initialValue: initial),
    );
    if (result == null) return;
    if (left) {
      await _ctrl.setKeepIslandLeftContent(result);
    } else {
      await _ctrl.setKeepIslandRightContent(result);
    }
  }

  Future<void> _copyPlaceholder(String value) async {
    await Clipboard.setData(ClipboardData(text: value));
    if (!mounted) return;
    final l10n = AppLocalizations.of(context)!;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(l10n.keepIslandPlaceholderCopied(value))),
    );
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final titleStyle = Theme.of(context).textTheme.titleMedium;
    final l10n = AppLocalizations.of(context)!;

    return Scaffold(
      backgroundColor: cs.surface,
      body: BlurAppBarHost(
        title: l10n.keepIslandTitle,
        physics: const ClampingScrollPhysics(),
        slivers: [
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  child: Column(
                    children: [
                      SwitchListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        title: Text(l10n.keepIslandTitle, style: titleStyle),
                        subtitle: Text(l10n.keepIslandSubtitle),
                        value: _ctrl.keepIsland,
                        onChanged: InteractionHaptics.interceptToggle(
                          (v) => _ctrl.setKeepIsland(v),
                        ),
                        shape: const RoundedRectangleBorder(
                          borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
                        ),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      SwitchListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        title: Text(l10n.keepIslandAutoHideTitle, style: titleStyle),
                        subtitle: Text(l10n.keepIslandAutoHideSubtitle),
                        value: _ctrl.keepIslandAutoHide,
                        onChanged: InteractionHaptics.interceptToggle(
                          (v) => _ctrl.setKeepIslandAutoHide(v),
                        ),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      SwitchListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        title: Text(l10n.keepIslandHideLandscapeTitle, style: titleStyle),
                        subtitle: Text(l10n.keepIslandHideLandscapeSubtitle),
                        value: _ctrl.keepIslandHideLandscape,
                        onChanged: InteractionHaptics.interceptToggle(
                          (v) => _ctrl.setKeepIslandHideLandscape(v),
                        ),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _ContentTile(
                        title: l10n.keepIslandLeftContentTitle,
                        value: _ctrl.keepIslandLeftContent,
                        onTap: () => _editContent(left: true),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _ContentTile(
                        title: l10n.keepIslandRightContentTitle,
                        value: _ctrl.keepIslandRightContent,
                        onTap: () => _editContent(left: false),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      ListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        title: Text(l10n.keepIslandHighlightColorTitle, style: titleStyle),
                        subtitle: Text(l10n.keepIslandHighlightColorSubtitle),
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            if (_ctrl.keepIslandHighlightColor.isNotEmpty)
                              Container(
                                width: 24,
                                height: 24,
                                decoration: BoxDecoration(
                                  color: parseHexColor(_ctrl.keepIslandHighlightColor) ?? cs.primary,
                                  borderRadius: BorderRadius.circular(6),
                                  border: Border.all(color: cs.outline, width: 1),
                                ),
                              )
                            else
                              Icon(Icons.palette_outlined, color: cs.onSurfaceVariant),
                            const SizedBox(width: 8),
                            if (_ctrl.keepIslandHighlightColor.isNotEmpty)
                              SizedBox(
                                width: 18,
                                height: 18,
                                child: IconButton(
                                  icon: const Icon(Icons.refresh, size: 18),
                                  padding: EdgeInsets.zero,
                                  visualDensity: VisualDensity.compact,
                                  onPressed: InteractionHaptics.interceptButton(
                                    () => _ctrl.setKeepIslandHighlightColor(''),
                                  ),
                                ),
                              ),
                          ],
                        ),
                        onTap: InteractionHaptics.interceptButton(() async {
                          final color = await showColorPickerDialog(
                            context,
                            initialHex: _ctrl.keepIslandHighlightColor.isEmpty
                                ? null
                                : _ctrl.keepIslandHighlightColor,
                            title: l10n.keepIslandHighlightColorTitle,
                            enableAlpha: true,
                          );
                          if (color != null) {
                            await _ctrl.setKeepIslandHighlightColor(colorToArgbHex(color));
                          }
                        }),
                        shape: const RoundedRectangleBorder(
                          borderRadius: BorderRadius.vertical(bottom: Radius.circular(16)),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                _SectionLabel(l10n.keepIslandPlaceholdersTitle),
                const SizedBox(height: 8),
                Text(
                  l10n.keepIslandPlaceholdersDescription('{battery.level}', '{cpu.usage}'),
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(color: cs.onSurfaceVariant),
                ),
                const SizedBox(height: 10),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final placeholder in _placeholders)
                      ActionChip(
                        label: Text(placeholder),
                        onPressed: InteractionHaptics.interceptButton(
                          () => _copyPlaceholder(placeholder),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 32),
              ], addAutomaticKeepAlives: false),
            ),
          ),
        ],
      ),
    );
  }
}

class _ContentTile extends StatelessWidget {
  const _ContentTile({required this.title, required this.value, required this.onTap});

  final String title;
  final String value;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      title: Text(title),
      subtitle: Text(
        value.isEmpty ? AppLocalizations.of(context)!.keepIslandDefaultEmpty : value,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
        style: Theme.of(context).textTheme.bodySmall?.copyWith(color: cs.onSurfaceVariant),
      ),
      trailing: const Icon(Icons.chevron_right),
      onTap: InteractionHaptics.interceptButton(onTap),
    );
  }
}

class _ContentDialog extends StatefulWidget {
  const _ContentDialog({required this.title, required this.initialValue});

  final String title;
  final String initialValue;

  @override
  State<_ContentDialog> createState() => _ContentDialogState();
}

class _ContentDialogState extends State<_ContentDialog> {
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialValue);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text(widget.title),
      content: TextField(
        controller: _controller,
        autofocus: true,
        maxLines: 3,
        decoration: InputDecoration(
          hintText: AppLocalizations.of(context)!.keepIslandContentHint('{battery.level}'),
          border: OutlineInputBorder(),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(AppLocalizations.of(context)!.cancel),
        ),
        TextButton(
          onPressed: () => Navigator.pop(context, ''),
          child: Text(AppLocalizations.of(context)!.clear),
        ),
        FilledButton(
          onPressed: () => Navigator.pop(context, _controller.text),
          child: Text(AppLocalizations.of(context)!.save),
        ),
      ],
    );
  }
}

class _SectionLabel extends StatelessWidget {
  final String text;
  const _SectionLabel(this.text);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 18),
      child: Text(
        text,
        style: Theme.of(context).textTheme.titleSmall?.copyWith(
          color: Theme.of(context).colorScheme.primary,
          fontWeight: FontWeight.bold,
          letterSpacing: 0.5,
        ),
      ),
    );
  }
}
