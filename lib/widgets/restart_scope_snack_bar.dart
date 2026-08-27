import 'package:flutter/material.dart';

import '../l10n/generated/app_localizations.dart';
import 'restart_scope_dialog.dart';

void showRestartScopeSnackBar(BuildContext context) {
  final l10n = AppLocalizations.of(context)!;
  final messenger = ScaffoldMessenger.of(context);
  messenger.clearSnackBars();
  messenger.showSnackBar(
    SnackBar(
      content: Text(l10n.restartScopeApp),
      duration: const Duration(seconds: 4),
      action: SnackBarAction(
        label: l10n.restartScope,
        onPressed: () {
          if (context.mounted) {
            showRestartScopeDialog(context);
          }
        },
      ),
    ),
  );
}
