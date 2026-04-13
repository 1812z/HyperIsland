import 'package:flutter/material.dart';

import '../l10n/generated/app_localizations.dart';

class ToastSettingsPanel extends StatelessWidget {
  const ToastSettingsPanel({
    super.key,
    required this.forwardEnabled,
    required this.blockOriginal,
    required this.showNotification,
    required this.showIslandIcon,
    this.onForwardEnabledChanged,
    this.onBlockOriginalChanged,
    this.onShowNotificationChanged,
    this.onShowIslandIconChanged,
    this.showHint = true,
  });

  final bool forwardEnabled;
  final bool blockOriginal;
  final bool showNotification;
  final bool showIslandIcon;
  final ValueChanged<bool>? onForwardEnabledChanged;
  final ValueChanged<bool>? onBlockOriginalChanged;
  final ValueChanged<bool>? onShowNotificationChanged;
  final ValueChanged<bool>? onShowIslandIconChanged;
  final bool showHint;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final cs = Theme.of(context).colorScheme;

    return Container(
      decoration: BoxDecoration(
        color: cs.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        children: [
          SwitchListTile(
            value: forwardEnabled,
            onChanged: onForwardEnabledChanged,
            title: Text(l10n.toastForwardTitle),
            subtitle: Text(l10n.toastForwardSubtitle),
          ),
          _Divider(cs: cs),
          SwitchListTile(
            value: blockOriginal,
            onChanged: forwardEnabled ? onBlockOriginalChanged : null,
            title: Text(l10n.toastBlockOriginalTitle),
            subtitle: Text(l10n.toastBlockOriginalSubtitle),
          ),
          _Divider(cs: cs),
          SwitchListTile(
            value: showNotification,
            onChanged: forwardEnabled ? onShowNotificationChanged : null,
            title: Text(l10n.toastShowNotificationTitle),
            subtitle: Text(l10n.toastShowNotificationSubtitle),
          ),
          _Divider(cs: cs),
          SwitchListTile(
            value: showIslandIcon,
            onChanged: forwardEnabled ? onShowIslandIconChanged : null,
            title: Text(l10n.toastShowIslandIconTitle),
            subtitle: Text(l10n.toastShowIslandIconSubtitle),
          ),
          if (showHint)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Text(
                  l10n.toastStandardOnlyHint,
                  style: Theme.of(
                    context,
                  ).textTheme.bodySmall?.copyWith(color: cs.onSurfaceVariant),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _Divider extends StatelessWidget {
  const _Divider({required this.cs});

  final ColorScheme cs;

  @override
  Widget build(BuildContext context) {
    return Divider(
      height: 1,
      thickness: 1,
      indent: 12,
      endIndent: 12,
      color: cs.outlineVariant.withValues(alpha: 0.4),
    );
  }
}
