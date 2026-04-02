import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart'
    show MarkdownBody, MarkdownStyleSheet;
import 'package:http/http.dart' as http;
import 'package:url_launcher/url_launcher.dart';
import '../l10n/generated/app_localizations.dart';

class UpdateController {
  static const _apiUrl =
      'https://api.github.com/repos/1812z/HyperIsland/releases/latest';

  /// Fetch latest release. Returns update info if [currentVersion] is outdated,
  /// otherwise null. Throws on network / parse errors.
  static Future<({String tag, String url, String changelog})?> fetchIfNewer(
    String currentVersion,
  ) async {
    final resp = await http
        .get(
          Uri.parse(_apiUrl),
          headers: {'Accept': 'application/vnd.github+json'},
        )
        .timeout(const Duration(seconds: 10));
    if (resp.statusCode != 200) return null;
    final data = jsonDecode(resp.body) as Map<String, dynamic>;
    final tag = (data['tag_name'] as String?)?.replaceFirst('v', '') ?? '';
    if (tag.isEmpty || !_isNewer(tag, currentVersion)) return null;
    return (
      tag: tag,
      url: data['html_url'] as String? ?? '',
      changelog: data['body'] as String? ?? '',
    );
  }

  static bool _isNewer(String remote, String current) {
    final r = remote.split('.').map(int.tryParse).toList();
    final c = current.split('.').map(int.tryParse).toList();
    for (var i = 0; i < 3; i++) {
      final rv = i < r.length ? (r[i] ?? 0) : 0;
      final cv = i < c.length ? (c[i] ?? 0) : 0;
      if (rv > cv) return true;
      if (rv < cv) return false;
    }
    return false;
  }

  /// Check for updates and show a dialog if one is found.
  /// [showUpToDate]: show a snackbar when already on the latest version.
  static Future<void> checkAndShow(
    BuildContext context,
    String currentVersion, {
    bool showUpToDate = false,
  }) async {
    try {
      final update = await fetchIfNewer(currentVersion);
      if (!context.mounted) return;
      if (update != null) {
        showUpdateDialog(
          context,
          'v$currentVersion',
          update.tag,
          update.url,
          update.changelog,
        );
      } else if (showUpToDate) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppLocalizations.of(context)!.alreadyLatest),
            duration: const Duration(seconds: 2),
          ),
        );
      }
    } catch (_) {
      // Network errors silently ignored
    }
  }

  static void showUpdateDialog(
    BuildContext context,
    String currentDisplay,
    String newTag,
    String releaseUrl,
    String changelog,
  ) {
    final l10n = AppLocalizations.of(context);
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(l10n!.newVersionFound),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(l10n.currentVersion(currentDisplay)),
            Text(l10n.latestVersion('v$newTag')),
            if (changelog.isNotEmpty) ...[
              const SizedBox(height: 12),
              const Divider(),
              const SizedBox(height: 4),
              ConstrainedBox(
                constraints: const BoxConstraints(maxHeight: 240),
                child: SingleChildScrollView(
                  child: MarkdownBody(
                    data: changelog,
                    styleSheet: MarkdownStyleSheet.fromTheme(
                      Theme.of(ctx),
                    ).copyWith(p: Theme.of(ctx).textTheme.bodySmall),
                    onTapLink: (_, href, __) {
                      if (href != null) {
                        launchUrl(
                          Uri.parse(href),
                          mode: LaunchMode.externalApplication,
                        );
                      }
                    },
                  ),
                ),
              ),
            ],
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text(l10n.later),
          ),
          FilledButton(
            onPressed: () {
              Navigator.pop(ctx);
              launchUrl(
                Uri.parse(releaseUrl),
                mode: LaunchMode.externalApplication,
              );
            },
            child: Text(l10n.goUpdate),
          ),
        ],
      ),
    );
  }
}
