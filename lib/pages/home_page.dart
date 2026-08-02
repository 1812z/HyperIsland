import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_miuix/miuix.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';
import '../controllers/home_controller.dart';
import '../controllers/settings_controller.dart';
import '../controllers/update_controller.dart';
import '../l10n/generated/app_localizations.dart';
import '../services/app_info_service.dart';
import '../services/system_font_weight.dart';
import '../widgets/miuix_page_scaffold.dart';

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
  final _snackbarHost = MiuixSnackbarHostState();
  bool _showSponsor = false;
  bool _showRestart = false;
  bool _showCustomTest = false;
  bool _showVersionUpdated = false;
  String _updatedVersion = '';
  bool _restartSystemUI = false;
  bool _restartDownloadManager = false;
  bool _restartXmsf = false;
  bool _restartSettings = false;
  String _customTitle = '';
  String _customContent = '';
  bool _customClearPrevious = true;
  bool _customEnableFloat = true;
  final _customTitleController = TextEditingController();
  final _customContentController = TextEditingController();
  Completer<bool>? _restartCompleter;
  Completer<void>? _versionUpdatedCompleter;

  @override
  void initState() {
    super.initState();
    _ctrl = HomeController();
    _ctrl.addListener(() {
      if (mounted) setState(() {});
    });
    AppInfoService.getVersion().then((version) async {
      final buildTime = await AppInfoService.getBuildTime();
      if (mounted) setState(() => _version = 'v$version-$buildTime');
      final shouldShowUpdateDialog = await SettingsController.instance
          .syncConfigAppVersion(version);
      if (mounted && shouldShowUpdateDialog) {
        await _showVersionUpdatedDialog(version);
      }
      if (SettingsController.instance.checkUpdateOnLaunch && mounted) {
        UpdateController.checkAndShow(context, version);
      }
    });
  }

  @override
  void dispose() {
    final restartCompleter = _restartCompleter;
    if (restartCompleter != null && !restartCompleter.isCompleted) {
      restartCompleter.complete(false);
    }
    final versionCompleter = _versionUpdatedCompleter;
    if (versionCompleter != null && !versionCompleter.isCompleted) {
      versionCompleter.complete();
    }
    _customTitleController.dispose();
    _customContentController.dispose();
    _ctrl.dispose();
    _snackbarHost.dispose();
    super.dispose();
  }

  List<Widget> _actions(AppLocalizations l10n) => [
    MiuixIconButton(
      onPressed: () => launchUrl(Uri.parse('https://hyperisland.1812z.top/')),
      child: Tooltip(
        message: l10n.documentation,
        child: const Icon(Icons.menu_book_outlined),
      ),
    ),
    MiuixIconButton(
      onPressed: _showSponsorDialog,
      child: Tooltip(
        message: l10n.sponsorAuthor,
        child: const Icon(Icons.favorite_border),
      ),
    ),
    _restarting
        ? const Padding(
            padding: EdgeInsets.symmetric(horizontal: 16),
            child: SizedBox(
              width: 20,
              height: 20,
              child: MiuixCircularProgressIndicator(strokeWidth: 2),
            ),
          )
        : MiuixIconButton(
            onPressed: _showRestartDialog,
            child: Tooltip(
              message: l10n.restartScope,
              child: const Icon(Icons.restart_alt),
            ),
          ),
  ];

  void _showSponsorDialog() {
    setState(() => _showSponsor = true);
  }

  Future<void> _showRestartDialog() async {
    _restartCompleter?.complete(false);
    final completer = Completer<bool>();
    _restartCompleter = completer;
    setState(() {
      _restartSystemUI = false;
      _restartDownloadManager = false;
      _restartXmsf = false;
      _restartSettings = false;
      _showRestart = true;
    });
    final confirmed = await completer.future;
    if (_restartCompleter == completer) _restartCompleter = null;

    if (!confirmed) return;
    if (!_restartSystemUI &&
        !_restartDownloadManager &&
        !_restartXmsf &&
        !_restartSettings) {
      return;
    }

    setState(() => _restarting = true);
    try {
      final commands = <String>[];
      if (_restartSystemUI) commands.add('killall com.android.systemui');
      if (_restartDownloadManager) {
        commands.add('am force-stop com.android.providers.downloads');
      }
      if (_restartXmsf) {
        commands.add('am force-stop com.xiaomi.xmsf');
      }
      if (_restartSettings) {
        commands.add('am force-stop com.android.settings');
      }
      await _channel.invokeMethod('restartProcesses', {'commands': commands});
    } on PlatformException catch (e) {
      if (mounted) {
        final l10n = AppLocalizations.of(context)!;
        final msg = (e.code == 'ROOT_ERROR' || e.code == 'ROOT_REQUIRED')
            ? l10n.restartRootRequired
            : l10n.restartFailed(e.message ?? '');
        _snackbarHost.showSnackbar(msg, withDismissAction: true);
      }
    } finally {
      if (mounted) setState(() => _restarting = false);
    }
  }

  void _showCustomTestDialog() {
    _customTitleController.clear();
    _customContentController.clear();
    setState(() {
      _customTitle = '';
      _customContent = '';
      _customClearPrevious = true;
      _customEnableFloat = true;
      _showCustomTest = true;
    });
  }

  void _closeRestartDialog(bool confirmed) {
    setState(() => _showRestart = false);
    final completer = _restartCompleter;
    if (completer != null && !completer.isCompleted) {
      completer.complete(confirmed);
    }
  }

  void _closeVersionUpdatedDialog() {
    setState(() => _showVersionUpdated = false);
    final completer = _versionUpdatedCompleter;
    if (completer != null && !completer.isCompleted) {
      completer.complete();
    }
  }

  Widget _buildOverlays(AppLocalizations l10n) {
    const donorsUrl = 'https://hyperisland.1812z.top/donors.html';
    const changelogUrl = 'https://hyperisland.1812z.top/CHANGELOG.html';

    return Stack(
      children: [
        MiuixOverlayDialog(
          show: _showSponsor,
          title: l10n.sponsorSupport,
          largeScreen: false,
          maxWidth: double.infinity,
          onDismissRequest: () => setState(() => _showSponsor = false),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(16),
                child: Image.asset(
                  'assets/images/wechat.jpg',
                  fit: BoxFit.contain,
                ),
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  Expanded(
                    child: MiuixButton(
                      onPressed: () => launchUrl(Uri.parse(donorsUrl)),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.format_list_bulleted),
                          const SizedBox(width: 8),
                          MiuixText(l10n.donorList),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: MiuixButton(
                      onPressed: () => setState(() => _showSponsor = false),
                      child: MiuixText(l10n.cancel),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        MiuixOverlayDialog(
          show: _showRestart,
          title: l10n.restartScope,
          largeScreen: false,
          maxWidth: double.infinity,
          onDismissRequest: () => _closeRestartDialog(false),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              MiuixCheckboxPreference(
                title: l10n.systemUI,
                summary: 'com.android.systemui',
                value: _restartSystemUI,
                onChanged: (v) => setState(() => _restartSystemUI = v),
              ),
              MiuixCheckboxPreference(
                title: l10n.downloadManager,
                summary: 'com.android.providers.downloads',
                value: _restartDownloadManager,
                onChanged: (v) => setState(() => _restartDownloadManager = v),
              ),
              MiuixCheckboxPreference(
                title: l10n.xmsf,
                summary: 'com.xiaomi.xmsf',
                value: _restartXmsf,
                onChanged: (v) => setState(() => _restartXmsf = v),
              ),
              MiuixCheckboxPreference(
                title: l10n.hookScopeSettings,
                summary: 'com.android.settings',
                value: _restartSettings,
                onChanged: (v) => setState(() => _restartSettings = v),
              ),
              const SizedBox(height: 16),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  Expanded(
                    child: MiuixButton(
                      onPressed: () => _closeRestartDialog(false),
                      child: MiuixText(l10n.cancel),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: MiuixButton(
                      colors: MiuixButtonDefaults.buttonColorsPrimary(context),
                      onPressed: () => _closeRestartDialog(true),
                      child: MiuixText(l10n.confirm),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        MiuixOverlayDialog(
          show: _showCustomTest,
          title: l10n.customTestNotification,
          largeScreen: false,
          maxWidth: double.infinity,
          onDismissRequest: () => setState(() => _showCustomTest = false),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              MiuixTextField(
                controller: _customTitleController,
                label: l10n.customTestTitle,
                useLabelAsPlaceholder: true,
                singleLine: true,
                onChanged: (v) => _customTitle = v,
              ),
              const SizedBox(height: 12),
              MiuixTextField(
                controller: _customContentController,
                label: l10n.customTestContent,
                useLabelAsPlaceholder: true,
                singleLine: true,
                onChanged: (v) => _customContent = v,
              ),
              const SizedBox(height: 8),
              MiuixCheckboxPreference(
                title: l10n.clearPreviousNotification,
                summary: l10n.clearPreviousNotificationSubtitle,
                value: _customClearPrevious,
                onChanged: (v) => setState(() => _customClearPrevious = v),
              ),
              MiuixCheckboxPreference(
                title: l10n.autoExpandNotification,
                summary: l10n.enableFloatNotificationSubtitle,
                value: _customEnableFloat,
                onChanged: (v) => setState(() => _customEnableFloat = v),
              ),
              const SizedBox(height: 16),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  Expanded(
                    child: MiuixButton(
                      onPressed: () => setState(() => _showCustomTest = false),
                      child: MiuixText(l10n.cancel),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: MiuixButton(
                      colors: MiuixButtonDefaults.buttonColorsPrimary(context),
                      onPressed: () {
                        _ctrl.sendCustomTest(
                          title: _customTitle,
                          content: _customContent,
                          clearPrevious: _customClearPrevious,
                          enableFloat: _customEnableFloat,
                        );
                        setState(() => _showCustomTest = false);
                      },
                      child: MiuixText(l10n.sendTestNotification),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        MiuixOverlayDialog(
          show: _showVersionUpdated,
          title: l10n.versionUpdatedTitle(_updatedVersion),
          largeScreen: false,
          maxWidth: double.infinity,
          onDismissRequest: null,
          content: PopScope(
            canPop: false,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                MiuixText(l10n.versionUpdatedContent),
                const SizedBox(height: 10),
                MiuixCard(
                  onPressed: () => launchUrl(Uri.parse(changelogUrl)),
                  feedbackType: MiuixPressFeedbackType.sink,
                  insideMargin: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 12,
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.history),
                      const SizedBox(width: 10),
                      Expanded(child: MiuixText(l10n.versionUpdatedChangelog)),
                      const Icon(Icons.open_in_new, size: 18),
                    ],
                  ),
                ),
                const SizedBox(height: 10),
                MiuixText(
                  l10n.versionUpdatedStarHint,
                  color: MiuixTheme.of(context).colors.onSurfaceVariantSummary,
                  style: MiuixTheme.of(context).textStyles.body2,
                ),
                const SizedBox(height: 16),
                Row(
                  mainAxisAlignment: MainAxisAlignment.end,
                  children: [
                    Expanded(
                      child: MiuixButton(
                        onPressed: () {
                          _closeVersionUpdatedDialog();
                          _showRestartDialog();
                        },
                        child: MiuixText(l10n.restartScope),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: MiuixButton(
                        colors: MiuixButtonDefaults.buttonColorsPrimary(
                          context,
                        ),
                        onPressed: _closeVersionUpdatedDialog,
                        child: MiuixText(l10n.confirm),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Future<void> _showVersionUpdatedDialog(String version) async {
    _versionUpdatedCompleter?.complete();
    final completer = Completer<void>();
    _versionUpdatedCompleter = completer;
    setState(() {
      _updatedVersion = version;
      _showVersionUpdated = true;
    });
    await completer.future;
    if (_versionUpdatedCompleter == completer) {
      _versionUpdatedCompleter = null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return MiuixPageScaffold(
      title: 'HyperIsland',
      subtitle: _version,
      actions: _actions(l10n),
      snackbarHost: MiuixSnackbarHost(state: _snackbarHost),
      overlay: _buildOverlays(l10n),
      children: [
        _HomeStatusGrid(
          controller: _ctrl,
          onTest: _ctrl.isSending ? null : _ctrl.sendTest,
          onCustomTest: _ctrl.isSending ? null : _showCustomTestDialog,
        ),
        const SizedBox(height: 12),
        _SystemInfoCard(
          moduleVersion: _ctrl.moduleVersion,
          lsposedVersion: _ctrl.lsposedVersion,
          androidVersion: _ctrl.androidVersion,
          systemVersion: _ctrl.systemVersion,
          deviceModel: _ctrl.deviceModel,
        ),
      ],
    );
  }
}

// ── 页面专属组件 ──────────────────────────────────────────────────────────────

class _HomeStatusGrid extends StatelessWidget {
  const _HomeStatusGrid({
    required this.controller,
    required this.onTest,
    required this.onCustomTest,
  });

  final HomeController controller;
  final VoidCallback? onTest;
  final VoidCallback? onCustomTest;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return LayoutBuilder(
      builder: (context, constraints) {
        final statusCard = _ModuleStatusCard(
          controller: controller,
          onPressed: onTest,
          onLongPress: onCustomTest,
        );
        final stats = [
          _StatCard(
            title: l10n.homeApps,
            value: l10n.homeEnabledCount(controller.enabledAppCount),
          ),
          _StatCard(
            title: 'Toast',
            value: l10n.homeEnabledCount(controller.enabledToastCount),
          ),
        ];

        if (constraints.maxWidth >= 600) {
          return SizedBox(
            height: 112,
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Expanded(child: statusCard),
                const SizedBox(width: 12),
                Expanded(child: stats[0]),
                const SizedBox(width: 12),
                Expanded(child: stats[1]),
              ],
            ),
          );
        }

        return SizedBox(
          height: (constraints.maxWidth - 12) / 2,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Expanded(child: statusCard),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  children: [
                    Expanded(child: SizedBox.expand(child: stats[0])),
                    const SizedBox(height: 12),
                    Expanded(child: SizedBox.expand(child: stats[1])),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}

class _ModuleStatusCard extends StatelessWidget {
  const _ModuleStatusCard({
    required this.controller,
    required this.onPressed,
    required this.onLongPress,
  });

  final HomeController controller;
  final VoidCallback? onPressed;
  final VoidCallback? onLongPress;

  @override
  Widget build(BuildContext context) {
    final colors = MiuixTheme.of(context).colors;
    final textStyles = MiuixTheme.of(context).textStyles;
    final l10n = AppLocalizations.of(context)!;
    final active = controller.moduleActive;
    final isActive = active == true;
    final isDark = MiuixTheme.of(context).brightness == Brightness.dark;
    final statusColor = isActive ? const Color(0xFF36D167) : colors.error;
    final background = isActive
        ? const Color(0xFFDFFAE4)
        : colors.errorContainer;
    final reasons = <String>[];
    if ((controller.lsposedApiVersion ?? 0) < 101) {
      reasons.add(
        (controller.lsposedApiVersion ?? 0) == 0
            ? l10n.enableInLSPosed
            : l10n.updateLSPosedRequired,
      );
    }
    if (controller.hasSystemUiScope == false) {
      reasons.add(l10n.enableSystemUiScopeInLSPosed);
    }
    final protocol = controller.focusProtocolVersion;
    if (protocol != null && protocol != 3) {
      reasons.add(l10n.systemNotSupportedSubtitle(protocol));
    }
    final summary = active == null
        ? l10n.detectingModuleStatus
        : isActive
        ? l10n.homeTestHint
        : reasons.isEmpty
        ? l10n.enableInLSPosed
        : reasons.join('\n');

    return MiuixCard(
      colors: MiuixCardColors(
        color: background,
        contentColor: colors.onSurface,
      ),
      onPressed: onPressed,
      onLongPress: onLongPress,
      feedbackType: MiuixPressFeedbackType.tilt,
      insideMargin: const EdgeInsets.all(16),
      child: Stack(
        clipBehavior: Clip.hardEdge,
        children: [
          Positioned(
            right: -14,
            bottom: -18,
            child: Opacity(
              opacity: 0.78,
              child: Icon(
                Icons.notifications_active_rounded,
                size: 112,
                color: statusColor,
              ),
            ),
          ),
          Column(
            mainAxisSize: MainAxisSize.max,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              MiuixText(
                isActive ? l10n.homeModuleActive : l10n.homeModuleInactive,
                style: textStyles.title3,
                color: isActive
                    ? const Color(0xFF101010)
                    : isDark
                    ? Colors.white
                    : const Color(0xFF101010),
                fontWeight: SystemFontWeight.resolve(FontWeight.w600),
              ),
              const SizedBox(height: 3),
              MiuixText(
                summary,
                style: textStyles.footnote1,
                color: isActive ? const Color(0xC72F3A32) : statusColor,
                maxLines: 5,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _StatCard extends StatelessWidget {
  const _StatCard({required this.title, required this.value});

  final String title;
  final String value;

  @override
  Widget build(BuildContext context) {
    final colors = MiuixTheme.of(context).colors;
    return MiuixCard(
      insideMargin: const EdgeInsets.all(14),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          MiuixText(
            title,
            style: MiuixTheme.of(context).textStyles.footnote1,
            color: colors.onSurfaceVariantSummary,
          ),
          const SizedBox(height: 2),
          MiuixText(value, style: MiuixTheme.of(context).textStyles.title3),
        ],
      ),
    );
  }
}

class _SystemInfoCard extends StatelessWidget {
  const _SystemInfoCard({
    required this.moduleVersion,
    required this.lsposedVersion,
    required this.androidVersion,
    required this.systemVersion,
    required this.deviceModel,
  });

  final String moduleVersion;
  final String lsposedVersion;
  final String androidVersion;
  final String systemVersion;
  final String deviceModel;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return MiuixCard(
      insideMargin: const EdgeInsets.all(16),
      child: Column(
        children: [
          _InfoText(title: l10n.homeModuleVersion, content: moduleVersion),
          _InfoText(title: l10n.homeLsposedVersion, content: lsposedVersion),
          _InfoText(title: l10n.homeAndroidVersion, content: androidVersion),
          _InfoText(title: l10n.homeSystemVersion, content: systemVersion),
          _InfoText(
            title: l10n.homeDeviceModel,
            content: deviceModel,
            bottomPadding: 0,
          ),
        ],
      ),
    );
  }
}

class _InfoText extends StatelessWidget {
  const _InfoText({
    required this.title,
    required this.content,
    this.bottomPadding = 20,
  });

  final String title;
  final String content;
  final double bottomPadding;

  @override
  Widget build(BuildContext context) {
    final theme = MiuixTheme.of(context);
    return Padding(
      padding: EdgeInsets.only(bottom: bottomPadding),
      child: Align(
        alignment: Alignment.centerLeft,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            MiuixText(title, style: theme.textStyles.headline1),
            const SizedBox(height: 2),
            MiuixText(
              content.isEmpty ? '-' : content,
              style: theme.textStyles.body2,
              color: theme.colors.onSurfaceVariantSummary,
            ),
          ],
        ),
      ),
    );
  }
}
