import 'dart:io';

import 'package:file_picker/file_picker.dart';
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
  static const _channel = MethodChannel('io.github.hyperisland/test');

  final _ctrl = SettingsController.instance;
  late int _buildHash;

  static const _batteryPlaceholders = [
    '{battery.power}',
    '{battery.voltage}',
    '{battery.current}',
    '{battery.level}',
    '{battery.temperature}',
  ];
  static const _cpuPlaceholders = ['{cpu.usage}', '{cpu.temperature}'];
  static const _gpuPlaceholders = ['{gpu.usage}', '{gpu.frequency}'];
  static const _memoryPlaceholders = [
    '{memory.usage}',
    '{memory.used}',
    '{memory.total}',
  ];
  static const _timePlaceholders = [
    '{time.HH}',
    '{time.hh}',
    '{time.h}',
    '{time.mm}',
    '{time.ss}',
    '{time.HH:mm}',
    '{time.HH:mm:ss}',
  ];
  static const _weatherPlaceholders = [
    '{weather.location}',
    '{weather.condition}',
    '{weather.temperature}',
  ];
  static const _displayPlaceholders = [
    '{display.refreshRate}',
    '{display.actualRefreshRate}',
  ];

  int _computeHash() => Object.hashAll([
    _ctrl.keepIsland,
    _ctrl.keepIslandAutoHide,
    _ctrl.keepIslandHideLandscape,
    _ctrl.keepIslandHighlightColor,
    _ctrl.keepIslandLeftHighlight,
    _ctrl.keepIslandRightHighlight,
    _ctrl.keepIslandLeftContent,
    _ctrl.keepIslandRightContent,
    _ctrl.keepIslandFocusNotification,
    _ctrl.keepIslandNotificationTitle,
    _ctrl.keepIslandNotificationContent,
    _ctrl.keepIslandShowIslandIcon,
    _ctrl.keepIslandCustomIconPath,
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
    final initial = left
        ? _ctrl.keepIslandLeftContent
        : _ctrl.keepIslandRightContent;
    final l10n = AppLocalizations.of(context)!;
    final title = left
        ? l10n.keepIslandLeftContentTitle
        : l10n.keepIslandRightContentTitle;
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

  Future<void> _editNotificationContent({required bool title}) async {
    if (!_ctrl.keepIslandFocusNotification) return;
    final initial = title
        ? _ctrl.keepIslandNotificationTitle
        : _ctrl.keepIslandNotificationContent;
    final l10n = AppLocalizations.of(context)!;
    final dialogTitle = title
        ? l10n.keepIslandNotificationTitle
        : l10n.keepIslandNotificationContent;
    final result = await showDialog<String>(
      context: context,
      builder: (context) =>
          _ContentDialog(title: dialogTitle, initialValue: initial),
    );
    if (result == null) return;
    if (title) {
      await _ctrl.setKeepIslandNotificationTitle(result);
    } else {
      await _ctrl.setKeepIslandNotificationContent(result);
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

  Future<void> _pickCustomIcon() async {
    final picked = await FilePicker.pickFile(
      type: FileType.custom,
      allowedExtensions: ['jpg', 'jpeg', 'png', 'webp', 'bmp'],
    );
    final sourcePath = picked?.path;
    if (sourcePath == null) return;
    final oldPath = _ctrl.keepIslandCustomIconPath;
    final savedPath = await _channel.invokeMethod<String>(
      'copyImageToModuleDir',
      {
        'sourcePath': sourcePath,
        'destFileName':
            'hyperisland_keep_icon_${DateTime.now().millisecondsSinceEpoch}.png',
      },
    );
    if (savedPath == null) return;
    await _ctrl.setKeepIslandCustomIconPath(savedPath);
    if (oldPath.isNotEmpty && oldPath != savedPath) {
      await _channel.invokeMethod<bool>('deleteImageFromModuleDir', {
        'fileName': File(oldPath).uri.pathSegments.last,
      });
      imageCache.evict(FileImage(File(oldPath)));
    }
    if (!mounted) return;
    imageCache.evict(FileImage(File(savedPath)));
    setState(() {});
  }

  Future<void> _deleteCustomIcon() async {
    final path = _ctrl.keepIslandCustomIconPath;
    if (path.isEmpty) return;
    imageCache.evict(FileImage(File(path)));
    await _ctrl.setKeepIslandCustomIconPath('');
    try {
      await _channel.invokeMethod<bool>('deleteImageFromModuleDir', {
        'fileName': File(path).uri.pathSegments.last,
      });
    } on PlatformException {
      // The setting is cleared even if the stale file cannot be removed.
    }
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
                          borderRadius: BorderRadius.vertical(
                            top: Radius.circular(16),
                          ),
                        ),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      SwitchListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        title: Text(
                          l10n.keepIslandAutoHideTitle,
                          style: titleStyle,
                        ),
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
                        title: Text(
                          l10n.keepIslandHideLandscapeTitle,
                          style: titleStyle,
                        ),
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
                      SwitchListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        title: Text(
                          l10n.keepIslandFocusNotificationTitle,
                          style: titleStyle,
                        ),
                        subtitle: Text(
                          l10n.keepIslandFocusNotificationSubtitle,
                        ),
                        value: _ctrl.keepIslandFocusNotification,
                        onChanged: InteractionHaptics.interceptToggle(
                          (v) => _ctrl.setKeepIslandFocusNotification(v),
                        ),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _ContentTile(
                        title: l10n.keepIslandNotificationTitle,
                        value: _ctrl.keepIslandNotificationTitle,
                        enabled: _ctrl.keepIslandFocusNotification,
                        onTap: () => _editNotificationContent(title: true),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _ContentTile(
                        title: l10n.keepIslandNotificationContent,
                        value: _ctrl.keepIslandNotificationContent,
                        enabled: _ctrl.keepIslandFocusNotification,
                        onTap: () => _editNotificationContent(title: false),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      ListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        title: Text(
                          l10n.keepIslandHighlightColorTitle,
                          style: titleStyle,
                        ),
                        subtitle: Text(l10n.keepIslandHighlightColorSubtitle),
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            if (_ctrl.keepIslandHighlightColor.isNotEmpty)
                              Container(
                                width: 24,
                                height: 24,
                                decoration: BoxDecoration(
                                  color:
                                      parseHexColor(
                                        _ctrl.keepIslandHighlightColor,
                                      ) ??
                                      cs.primary,
                                  borderRadius: BorderRadius.circular(6),
                                  border: Border.all(
                                    color: cs.outline,
                                    width: 1,
                                  ),
                                ),
                              )
                            else
                              Icon(
                                Icons.palette_outlined,
                                color: cs.onSurfaceVariant,
                              ),
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
                            await _ctrl.setKeepIslandHighlightColor(
                              colorToArgbHex(color),
                            );
                          }
                        }),
                      ),
                      Padding(
                        padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              l10n.keepIslandTextHighlightTitle,
                              style: Theme.of(context).textTheme.labelMedium
                                  ?.copyWith(color: cs.onSurfaceVariant),
                            ),
                            Row(
                              children: [
                                Expanded(
                                  child: _HighlightSwitch(
                                    label: l10n.keepIslandHighlightLeft,
                                    value: _ctrl.keepIslandLeftHighlight,
                                    onChanged:
                                        _ctrl.keepIslandHighlightColor.isEmpty
                                        ? null
                                        : (value) =>
                                              _ctrl.setKeepIslandLeftHighlight(
                                                value,
                                              ),
                                  ),
                                ),
                                const SizedBox(width: 16),
                                Expanded(
                                  child: _HighlightSwitch(
                                    label: l10n.keepIslandHighlightRight,
                                    value: _ctrl.keepIslandRightHighlight,
                                    onChanged:
                                        _ctrl.keepIslandHighlightColor.isEmpty
                                        ? null
                                        : (value) =>
                                              _ctrl.setKeepIslandRightHighlight(
                                                value,
                                              ),
                                  ),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      SwitchListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        title: Text(
                          l10n.keepIslandShowIslandIconTitle,
                          style: titleStyle,
                        ),
                        subtitle: Text(l10n.keepIslandShowIslandIconSubtitle),
                        value: _ctrl.keepIslandShowIslandIcon,
                        onChanged: InteractionHaptics.interceptToggle(
                          (v) => _ctrl.setKeepIslandShowIslandIcon(v),
                        ),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _CustomIconTile(
                        imagePath: _ctrl.keepIslandCustomIconPath,
                        onTap: _pickCustomIcon,
                        onDelete: _ctrl.keepIslandCustomIconPath.isEmpty
                            ? null
                            : _deleteCustomIcon,
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                _SectionLabel(l10n.keepIslandPlaceholdersTitle),
                const SizedBox(height: 8),
                Text(
                  l10n.keepIslandPlaceholdersDescription(
                    '{battery.level}',
                    '{cpu.usage}',
                  ),
                  style: Theme.of(
                    context,
                  ).textTheme.bodySmall?.copyWith(color: cs.onSurfaceVariant),
                ),
                const SizedBox(height: 10),
                Column(
                  children: [
                    _PlaceholderCategory(
                      title: l10n.chargeIslandModeLevel,
                      icon: Icons.battery_charging_full,
                      placeholders: _batteryPlaceholders,
                      onCopy: _copyPlaceholder,
                    ),
                    const SizedBox(height: 8),
                    _PlaceholderCategory(
                      title: 'CPU',
                      icon: Icons.developer_board_outlined,
                      placeholders: _cpuPlaceholders,
                      onCopy: _copyPlaceholder,
                    ),
                    const SizedBox(height: 8),
                    _PlaceholderCategory(
                      title: 'GPU',
                      icon: Icons.videogame_asset_outlined,
                      placeholders: _gpuPlaceholders,
                      onCopy: _copyPlaceholder,
                    ),
                    const SizedBox(height: 8),
                    _PlaceholderCategory(
                      title: 'RAM',
                      icon: Icons.memory,
                      placeholders: _memoryPlaceholders,
                      onCopy: _copyPlaceholder,
                    ),
                    const SizedBox(height: 8),
                    _PlaceholderCategory(
                      title: l10n.aiLastLogTimeLabel,
                      icon: Icons.schedule,
                      placeholders: _timePlaceholders,
                      onCopy: _copyPlaceholder,
                    ),
                    const SizedBox(height: 8),
                    _PlaceholderCategory(
                      title: l10n.keepIslandWeatherCategory,
                      icon: Icons.cloud_outlined,
                      placeholders: _weatherPlaceholders,
                      onCopy: _copyPlaceholder,
                    ),
                    const SizedBox(height: 8),
                    _PlaceholderCategory(
                      title: l10n.keepIslandDisplayCategory,
                      icon: Icons.monitor_outlined,
                      placeholders: _displayPlaceholders,
                      onCopy: _copyPlaceholder,
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

class _HighlightSwitch extends StatelessWidget {
  const _HighlightSwitch({
    required this.label,
    required this.value,
    required this.onChanged,
  });

  final String label;
  final bool value;
  final ValueChanged<bool>? onChanged;

  @override
  Widget build(BuildContext context) {
    final enabled = onChanged != null;
    return Row(
      children: [
        Expanded(
          child: Text(
            label,
            style: TextStyle(
              color: enabled ? null : Theme.of(context).disabledColor,
            ),
          ),
        ),
        Switch(
          value: value,
          onChanged: enabled
              ? InteractionHaptics.interceptToggle(onChanged!)
              : null,
        ),
      ],
    );
  }
}

class _PlaceholderCategory extends StatelessWidget {
  const _PlaceholderCategory({
    required this.title,
    required this.icon,
    required this.placeholders,
    required this.onCopy,
  });

  final String title;
  final IconData icon;
  final List<String> placeholders;
  final ValueChanged<String> onCopy;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return ExpansionTile(
      initiallyExpanded: false,
      tilePadding: const EdgeInsets.symmetric(horizontal: 16),
      childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      backgroundColor: cs.surfaceContainerHighest,
      collapsedBackgroundColor: cs.surfaceContainerHighest,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      collapsedShape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
      ),
      leading: Icon(icon, color: cs.primary),
      title: Text(title),
      children: [
        Align(
          alignment: AlignmentDirectional.centerStart,
          child: Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final placeholder in placeholders)
                ActionChip(
                  label: Text(placeholder),
                  onPressed: InteractionHaptics.interceptButton(
                    () => onCopy(placeholder),
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

class _CustomIconTile extends StatelessWidget {
  const _CustomIconTile({
    required this.imagePath,
    required this.onTap,
    this.onDelete,
  });

  final String imagePath;
  final VoidCallback onTap;
  final VoidCallback? onDelete;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
    final hasIcon = imagePath.isNotEmpty;
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      leading: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(
          color: cs.surfaceContainerHigh,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: hasIcon ? cs.primary : cs.outline.withValues(alpha: 0.3),
            width: hasIcon ? 2 : 1,
          ),
        ),
        child: hasIcon
            ? ClipRRect(
                borderRadius: BorderRadius.circular(6),
                child: Image.file(
                  File(imagePath),
                  fit: BoxFit.cover,
                  errorBuilder: (_, __, ___) =>
                      Icon(Icons.image_outlined, color: cs.onSurfaceVariant),
                ),
              )
            : Icon(Icons.image_outlined, color: cs.onSurfaceVariant),
      ),
      title: Text(l10n.keepIslandCustomIconTitle),
      subtitle: Text(
        hasIcon ? l10n.keepIslandCustomIconSelected : l10n.tapToSelectImage,
      ),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (onDelete != null)
            IconButton(
              icon: Icon(Icons.delete_outline, color: cs.error),
              onPressed: onDelete,
              visualDensity: VisualDensity.compact,
            ),
          const Icon(Icons.chevron_right),
        ],
      ),
      onTap: InteractionHaptics.interceptButton(onTap),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(bottom: Radius.circular(16)),
      ),
    );
  }
}

class _ContentTile extends StatelessWidget {
  const _ContentTile({
    required this.title,
    required this.value,
    required this.onTap,
    this.enabled = true,
  });

  final String title;
  final String value;
  final VoidCallback onTap;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = Theme.of(context).colorScheme;
    return ListTile(
      enabled: enabled,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      title: Text(title),
      subtitle: Text(
        value.isEmpty
            ? AppLocalizations.of(context)!.keepIslandDefaultEmpty
            : value,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
        style: theme.textTheme.bodySmall?.copyWith(
          color: enabled ? cs.onSurfaceVariant : theme.disabledColor,
        ),
      ),
      trailing: const Icon(Icons.chevron_right),
      onTap: enabled ? InteractionHaptics.interceptButton(onTap) : null,
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
          hintText: AppLocalizations.of(
            context,
          )!.keepIslandContentHint('{battery.level}'),
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
