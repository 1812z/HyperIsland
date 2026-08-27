import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../l10n/generated/app_localizations.dart';

const _channel = MethodChannel('io.github.hyperisland/test');

Future<void> showRestartScopeDialog(
  BuildContext context, {
  ValueChanged<bool>? onRestartingChanged,
}) async {
  final l10n = AppLocalizations.of(context)!;
  var restartSystemUI = false;
  var restartDownloadManager = false;
  var restartXmsf = false;
  var restartSettings = false;

  final confirmed = await showDialog<bool>(
    context: context,
    builder: (dialogContext) => StatefulBuilder(
      builder: (dialogContext, setDialogState) => AlertDialog(
        title: Text(l10n.restartScope),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CheckboxListTile(
              title: Text(l10n.systemUI),
              subtitle: const Text('com.android.systemui'),
              value: restartSystemUI,
              onChanged: (value) =>
                  setDialogState(() => restartSystemUI = value ?? false),
              controlAffinity: ListTileControlAffinity.leading,
              contentPadding: EdgeInsets.zero,
            ),
            CheckboxListTile(
              title: Text(l10n.downloadManager),
              subtitle: const Text('com.android.providers.downloads'),
              value: restartDownloadManager,
              onChanged: (value) =>
                  setDialogState(() => restartDownloadManager = value ?? false),
              controlAffinity: ListTileControlAffinity.leading,
              contentPadding: EdgeInsets.zero,
            ),
            CheckboxListTile(
              title: Text(l10n.xmsf),
              subtitle: const Text('com.xiaomi.xmsf'),
              value: restartXmsf,
              onChanged: (value) =>
                  setDialogState(() => restartXmsf = value ?? false),
              controlAffinity: ListTileControlAffinity.leading,
              contentPadding: EdgeInsets.zero,
            ),
            CheckboxListTile(
              title: Text(l10n.hookScopeSettings),
              subtitle: const Text('com.android.settings'),
              value: restartSettings,
              onChanged: (value) =>
                  setDialogState(() => restartSettings = value ?? false),
              controlAffinity: ListTileControlAffinity.leading,
              contentPadding: EdgeInsets.zero,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: Text(l10n.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: Text(l10n.confirm),
          ),
        ],
      ),
    ),
  );

  if (confirmed != true || !context.mounted) return;
  final commands = <String>[
    if (restartSystemUI) 'killall com.android.systemui',
    if (restartDownloadManager) 'am force-stop com.android.providers.downloads',
    if (restartXmsf) 'am force-stop com.xiaomi.xmsf',
    if (restartSettings) 'am force-stop com.android.settings',
  ];
  if (commands.isEmpty) return;

  onRestartingChanged?.call(true);
  try {
    await _channel.invokeMethod('restartProcesses', {'commands': commands});
  } on PlatformException catch (error) {
    if (!context.mounted) return;
    final message = error.code == 'ROOT_ERROR' || error.code == 'ROOT_REQUIRED'
        ? l10n.restartRootRequired
        : l10n.restartFailed(error.message ?? '');
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  } finally {
    onRestartingChanged?.call(false);
  }
}
