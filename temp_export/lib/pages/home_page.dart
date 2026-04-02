import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:package_info_plus/package_info_plus.dart';
import '../controllers/home_controller.dart';
import '../controllers/settings_controller.dart';
import '../controllers/update_controller.dart';
import '../l10n/generated/app_localizations.dart';
import '../widgets/section_label.dart';

const _channel = MethodChannel('io.github.hyperisland/test');

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  late final HomeController _ctrl;
  bool _restarting = false;
  String _version = '';

  @override
  void initState() {
    super.initState();
    _ctrl = HomeController();
    _ctrl.addListener(() {
      if (mounted) setState(() {});
    });
    PackageInfo.fromPlatform().then((info) {
      if (mounted) setState(() => _version = 'v${info.version}');
      if (SettingsController.instance.checkUpdateOnLaunch && mounted) {
        UpdateController.checkAndShow(context, info.version);
      }
    });
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  void _showSponsorDialog() {
    final l10n = AppLocalizations.of(context)!;
    showDialog(
      context: context,
      builder: (ctx) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 4, 0),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      l10n.sponsorSupport,
                      style: const TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  IconButton(
                    onPressed: () => Navigator.pop(ctx),
                    icon: const Icon(Icons.close),
                  ),
                ],
              ),
            ),
            ClipRRect(
              borderRadius: const BorderRadius.vertical(
                bottom: Radius.circular(16),
              ),
              child: Image.asset(
                'assets/images/wechat.jpg',
                fit: BoxFit.contain,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _showRestartDialog() async {
    final l10n = AppLocalizations.of(context)!;
    bool restartSystemUI = true;
    bool restartDownloadManager = true;
    bool restartXmsf = true;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: Text(l10n.restartScope),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              CheckboxListTile(
                title: Text(l10n.systemUI),
                subtitle: const Text('com.android.systemui'),
                value: restartSystemUI,
                onChanged: (v) =>
                    setDialogState(() => restartSystemUI = v ?? false),
                controlAffinity: ListTileControlAffinity.leading,
                contentPadding: EdgeInsets.zero,
              ),
              CheckboxListTile(
                title: Text(l10n.downloadManager),
                subtitle: const Text('com.android.providers.downloads'),
                value: restartDownloadManager,
                onChanged: (v) =>
                    setDialogState(() => restartDownloadManager = v ?? false),
                controlAffinity: ListTileControlAffinity.leading,
                contentPadding: EdgeInsets.zero,
              ),
              CheckboxListTile(
                title: Text(l10n.xmsf),
                subtitle: const Text('com.xiaomi.xmsf'),
                value: restartXmsf,
                onChanged: (v) =>
                    setDialogState(() => restartXmsf = v ?? false),
                controlAffinity: ListTileControlAffinity.leading,
                contentPadding: EdgeInsets.zero,
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: Text(l10n.cancel),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: Text(l10n.confirm),
            ),
          ],
        ),
      ),
    );

    if (confirmed != true) return;
    if (!restartSystemUI && !restartDownloadManager && !restartXmsf) return;

    setState(() => _restarting = true);
    try {
      final commands = <String>[];
      if (restartSystemUI) commands.add('killall com.android.systemui');
      if (restartDownloadManager) {
        commands.add('am force-stop com.android.providers.downloads');
      }
      if (restartXmsf) {
        commands.add('am force-stop com.xiaomi.xmsf');
      }
      await _channel.invokeMethod('restartProcesses', {'commands': commands});
    } on PlatformException catch (e) {
      if (mounted) {
        final l10n = AppLocalizations.of(context)!;
        final msg = (e.code == 'ROOT_ERROR' || e.code == 'ROOT_REQUIRED')
            ? l10n.restartRootRequired
            : l10n.restartFailed(e.message ?? '');
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(msg)));
      }
    } finally {
      if (mounted) setState(() => _restarting = false);
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
            title: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                const Text('HyperIsland'),
                if (_version.isNotEmpty)
                  Text(
                    _version,
                    style: Theme.of(
                      context,
                    ).textTheme.bodySmall?.copyWith(color: cs.onSurfaceVariant),
                  ),
              ],
            ),
            backgroundColor: cs.surface,
            centerTitle: false,
            actions: [
              IconButton(
                tooltip: l10n.sponsorAuthor,
                icon: const Icon(Icons.favorite_border),
                onPressed: _showSponsorDialog,
              ),
              _restarting
                  ? const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 16),
                      child: SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      ),
                    )
                  : IconButton(
                      tooltip: l10n.restartScope,
                      icon: const Icon(Icons.restart_alt),
                      onPressed: _showRestartDialog,
                    ),
            ],
          ),
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                _ModuleStatusCard(
                  active: _ctrl.moduleActive,
                  apiVersion: _ctrl.xposedApiVersion,
                ),
                if (_ctrl.focusProtocolVersion != null &&
                    _ctrl.focusProtocolVersion != 3) ...[
                  const SizedBox(height: 12),
                  _SystemNotSupportedCard(version: _ctrl.focusProtocolVersion!),
                ],
                const SizedBox(height: 16),

                SectionLabel(l10n.notificationTest),
                const SizedBox(height: 8),
                FilledButton.icon(
                  onPressed: _ctrl.isSending ? null : _ctrl.sendTest,
                  icon: const Icon(Icons.notifications_active_outlined),
                  label: Text(l10n.sendTestNotification),
                  style: FilledButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                ),
                const SizedBox(height: 24),

                SectionLabel(l10n.notes),
                const SizedBox(height: 8),
                const _NotesCard(),
                const SizedBox(height: 24),
              ]),
            ),
          ),
        ],
      ),
    );
  }
}

// ── 页面专属组件 ──────────────────────────────────────────────────────────────

class _ModuleStatusCard extends StatelessWidget {
  final bool? active;
  final int? apiVersion;
  const _ModuleStatusCard({required this.active, this.apiVersion});

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;

    if (active == null) {
      return Card(
        elevation: 0,
        color: cs.surfaceContainerHighest,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Row(
            children: [
              const SizedBox(
                width: 24,
                height: 24,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
              const SizedBox(width: 16),
              Text(l10n.detectingModuleStatus),
            ],
          ),
        ),
      );
    }

    final bool isActive = active!;
    final color = isActive ? Colors.green : cs.error;
    final bgColor = isActive
        ? Colors.green.withValues(alpha: 0.12)
        : cs.errorContainer;

    return Card(
      elevation: 0,
      color: bgColor,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Row(
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.15),
                shape: BoxShape.circle,
              ),
              child: Icon(
                isActive ? Icons.check_circle : Icons.cancel,
                color: color,
                size: 28,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    l10n.moduleStatus,
                    style: Theme.of(context).textTheme.labelLarge?.copyWith(
                      color: color.withValues(alpha: 0.8),
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    isActive ? l10n.activated : l10n.notActivated,
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                      color: color,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  if (isActive && apiVersion != null && apiVersion! > 0) ...[
                    const SizedBox(height: 4),
                    Text(
                      l10n.lsposedApiVersion(apiVersion!),
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: color.withValues(alpha: 0.7),
                      ),
                    ),
                  ],
                  if (!isActive) ...[
                    const SizedBox(height: 4),
                    Text(
                      l10n.enableInLSPosed,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: color.withValues(alpha: 0.7),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SystemNotSupportedCard extends StatelessWidget {
  final int version;
  const _SystemNotSupportedCard({required this.version});

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
    final color = cs.error;

    return Card(
      elevation: 0,
      color: cs.errorContainer,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Row(
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.15),
                shape: BoxShape.circle,
              ),
              child: Icon(Icons.warning_amber_rounded, color: color, size: 28),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    l10n.systemNotSupported,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      color: color,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    l10n.systemNotSupportedSubtitle(version),
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: color.withValues(alpha: 0.8),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _NotesCard extends StatelessWidget {
  const _NotesCard();

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
    final items = [l10n.note1, l10n.note2, l10n.note3, l10n.note4];

    return Card(
      elevation: 0,
      color: cs.surfaceContainerHighest,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: items
              .map(
                (text) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(
                        Icons.arrow_right,
                        size: 20,
                        color: cs.onSurfaceVariant,
                      ),
                      const SizedBox(width: 4),
                      Expanded(
                        child: Text(
                          text,
                          style: Theme.of(context).textTheme.bodyMedium
                              ?.copyWith(color: cs.onSurfaceVariant),
                        ),
                      ),
                    ],
                  ),
                ),
              )
              .toList(),
        ),
      ),
    );
  }
}
