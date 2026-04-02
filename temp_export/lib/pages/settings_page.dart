import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:url_launcher/url_launcher.dart';
import '../controllers/config_io_controller.dart';
import '../controllers/settings_controller.dart';
import '../controllers/update_controller.dart';
import '../l10n/generated/app_localizations.dart';
import '../services/interaction_haptics.dart';
import '../widgets/section_label.dart';
import '../widgets/modern_slider.dart';
import 'ai_config_page.dart';
import 'blacklist_page.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  final _ctrl = SettingsController.instance;
  bool _checkingUpdate = false;

  void _onChanged() {
    if (mounted) setState(() {});
  }

  @override
  void initState() {
    super.initState();
    _ctrl.addListener(_onChanged);
  }

  @override
  void dispose() {
    _ctrl.removeListener(_onChanged);
    super.dispose();
  }

  Future<void> _onResumeNotificationChanged(bool value) async {
    await InteractionHaptics.toggle();
    await _ctrl.setResumeNotification(value);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context)!.restartScopeApp),
          duration: const Duration(seconds: 4),
        ),
      );
    }
  }

  Future<void> _onUseHookAppIconChanged(bool value) async {
    await InteractionHaptics.toggle();
    await _ctrl.setUseHookAppIcon(value);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context)!.restartScopeApp),
          duration: const Duration(seconds: 4),
        ),
      );
    }
  }

  Future<void> _onRoundIconChanged(bool value) async {
    await InteractionHaptics.toggle();
    await _ctrl.setRoundIcon(value);
  }

  Future<void> _onMarqueeSpeedChanged(double value) async {
    await InteractionHaptics.sliderTick();
    _ctrl.setMarqueeSpeed(value.round());
  }

  void _showSnack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(msg), duration: const Duration(seconds: 3)),
    );
  }

  String _localizeConfigIOError(AppLocalizations l10n, ConfigIOError error) {
    return switch (error) {
      ConfigIOError.invalidFormat => l10n.errorInvalidFormat,
      ConfigIOError.noStorageDirectory => l10n.errorNoStorageDir,
      ConfigIOError.noFileSelected => l10n.errorNoFileSelected,
      ConfigIOError.noFilePath => l10n.errorNoFilePath,
      ConfigIOError.emptyClipboard => l10n.errorEmptyClipboard,
    };
  }

  Future<void> _exportToFile() async {
    final l10n = AppLocalizations.of(context)!;
    await InteractionHaptics.button();
    try {
      final path = await ConfigIOController.exportToFile();
      _showSnack(l10n.exportedTo(path));
    } on ConfigIOException catch (e) {
      _showSnack(l10n.exportFailed(_localizeConfigIOError(l10n, e.error)));
    } catch (e) {
      _showSnack(l10n.exportFailed(e.toString()));
    }
  }

  Future<void> _exportToClipboard() async {
    final l10n = AppLocalizations.of(context)!;
    await InteractionHaptics.button();
    try {
      await ConfigIOController.exportToClipboard();
      _showSnack(l10n.configCopied);
    } on ConfigIOException catch (e) {
      _showSnack(l10n.exportFailed(_localizeConfigIOError(l10n, e.error)));
    } catch (e) {
      _showSnack(l10n.exportFailed(e.toString()));
    }
  }

  Future<void> _importFromFile() async {
    final l10n = AppLocalizations.of(context)!;
    await InteractionHaptics.button();
    try {
      final count = await ConfigIOController.importFromFile();
      _showSnack(l10n.importSuccess(count));
    } on ConfigIOException catch (e) {
      _showSnack(l10n.importFailed(_localizeConfigIOError(l10n, e.error)));
    } catch (e) {
      _showSnack(l10n.importFailed(e.toString()));
    }
  }

  Future<void> _importFromClipboard() async {
    final l10n = AppLocalizations.of(context)!;
    await InteractionHaptics.button();
    try {
      final count = await ConfigIOController.importFromClipboard();
      _showSnack(l10n.importSuccess(count));
    } on ConfigIOException catch (e) {
      _showSnack(l10n.importFailed(_localizeConfigIOError(l10n, e.error)));
    } catch (e) {
      _showSnack(l10n.importFailed(e.toString()));
    }
  }

  Future<void> _showExportOptions(AppLocalizations l10n) async {
    await InteractionHaptics.button();
    if (!mounted) return;
    await showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.upload_file_outlined),
              title: Text(l10n.exportToFile),
              subtitle: Text(l10n.exportToFileSubtitle),
              onTap: () async {
                Navigator.of(sheetContext).pop();
                await _exportToFile();
              },
            ),
            ListTile(
              leading: const Icon(Icons.copy_outlined),
              title: Text(l10n.exportToClipboard),
              subtitle: Text(l10n.exportToClipboardSubtitle),
              onTap: () async {
                Navigator.of(sheetContext).pop();
                await _exportToClipboard();
              },
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _showImportOptions(AppLocalizations l10n) async {
    await InteractionHaptics.button();
    if (!mounted) return;
    await showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.download_outlined),
              title: Text(l10n.importFromFile),
              subtitle: Text(l10n.importFromFileSubtitle),
              onTap: () async {
                Navigator.of(sheetContext).pop();
                await _importFromFile();
              },
            ),
            ListTile(
              leading: const Icon(Icons.paste_outlined),
              title: Text(l10n.importFromClipboard),
              subtitle: Text(l10n.importFromClipboardSubtitle),
              onTap: () async {
                Navigator.of(sheetContext).pop();
                await _importFromClipboard();
              },
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _doCheckUpdate() async {
    await InteractionHaptics.button();
    setState(() => _checkingUpdate = true);
    try {
      final info = await PackageInfo.fromPlatform();
      if (mounted) {
        await UpdateController.checkAndShow(
          context,
          info.version,
          showUpToDate: true,
        );
      }
    } finally {
      if (mounted) setState(() => _checkingUpdate = false);
    }
  }

  String _themeModeLabel(AppLocalizations l10n) => switch (_ctrl.themeMode) {
    ThemeMode.light => l10n.themeModeLight,
    ThemeMode.dark => l10n.themeModeDark,
    ThemeMode.system => l10n.themeModeSystem,
  };

  String _localeLabel(AppLocalizations l10n) {
    if (_ctrl.locale == null) return l10n.languageAuto;
    return switch (_ctrl.locale!.languageCode) {
      'zh' => l10n.languageZh,
      'en' => l10n.languageEn,
      'ja' => l10n.languageJa,
      'tr' => l10n.languageTr,
      _ => _ctrl.locale!.languageCode,
    };
  }

  Future<void> _showThemeModeDialog(AppLocalizations l10n) async {
    await InteractionHaptics.button();
    if (!mounted) return;
    final result = await showDialog<ThemeMode>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: Text(l10n.themeModeTitle),
        children: [
          RadioGroup<ThemeMode>(
            groupValue: _ctrl.themeMode,
            onChanged: (v) => Navigator.of(ctx).pop(v),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                _RadioOption<ThemeMode>(l10n.themeModeSystem, ThemeMode.system),
                _RadioOption<ThemeMode>(l10n.themeModeLight, ThemeMode.light),
                _RadioOption<ThemeMode>(l10n.themeModeDark, ThemeMode.dark),
              ],
            ),
          ),
        ],
      ),
    );
    if (result != null) {
      await InteractionHaptics.button();
      if (!mounted) return;
      _ctrl.setThemeMode(result);
    }
  }

  Future<void> _showLanguageDialog(AppLocalizations l10n) async {
    await InteractionHaptics.button();
    if (!mounted) return;
    final result = await showDialog<Locale?>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: Text(l10n.languageTitle),
        children: [
          RadioGroup<Locale?>(
            groupValue: _ctrl.locale,
            onChanged: (v) => Navigator.of(ctx).pop(v),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                _RadioOption<Locale?>(l10n.languageAuto, null),
                _RadioOption<Locale?>(l10n.languageZh, const Locale('zh')),
                _RadioOption<Locale?>(l10n.languageEn, const Locale('en')),
                _RadioOption<Locale?>(l10n.languageJa, const Locale('ja')),
                _RadioOption<Locale?>(l10n.languageTr, const Locale('tr')),
              ],
            ),
          ),
        ],
      ),
    );
    if (result != _ctrl.locale) {
      await InteractionHaptics.button();
      if (!mounted) return;
      _ctrl.setLocale(result);
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
            title: Text(l10n.navSettings),
            backgroundColor: cs.surface,
            centerTitle: false,
          ),
          if (_ctrl.loading)
            const SliverFillRemaining(
              child: Center(child: CircularProgressIndicator()),
            )
          else
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
                    child: ListTile(
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 4,
                      ),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                      leading: const Icon(Icons.psychology_outlined),
                      title: Text(l10n.aiConfigTitle),
                      subtitle: Text(
                        _ctrl.aiEnabled
                            ? l10n.aiConfigSubtitleEnabled
                            : l10n.aiConfigSubtitleDisabled,
                      ),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () async {
                        if (!context.mounted) return;
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (context) => const AiConfigPage(),
                          ),
                        );
                      },
                    ),
                  ),
                  const SizedBox(height: 24),
                  SectionLabel(l10n.navBlacklist),
                  const SizedBox(height: 8),
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Column(
                      children: [
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          shape: const RoundedRectangleBorder(
                            borderRadius: BorderRadius.vertical(
                              top: Radius.circular(16),
                            ),
                          ),
                          leading: const Icon(Icons.block),
                          title: Text(l10n.navBlacklist),
                          subtitle: Text(l10n.navBlacklistSubtitle),
                          trailing: const Icon(Icons.chevron_right),
                          onTap: () async {
                            if (!context.mounted) return;
                            Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (context) => const BlacklistPage(),
                              ),
                            );
                          },
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),
                  SectionLabel(l10n.behaviorSection),
                  const SizedBox(height: 8),
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Column(
                      children: [
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(l10n.interactionHapticsTitle),
                          subtitle: Text(l10n.interactionHapticsSubtitle),
                          value: _ctrl.interactionHaptics,
                          onChanged: (value) async {
                            await InteractionHaptics.toggle(force: true);
                            await _ctrl.setInteractionHaptics(value);
                          },
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
                          title: Text(l10n.keepFocusNotifTitle),
                          subtitle: Text(l10n.keepFocusNotifSubtitle),
                          value: _ctrl.resumeNotification,
                          onChanged: _onResumeNotificationChanged,
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(l10n.unlockAllFocusTitle),
                          subtitle: Text(l10n.unlockAllFocusSubtitle),
                          value: _ctrl.unlockAllFocus,
                          onChanged: (value) async {
                            await InteractionHaptics.toggle();
                            await _ctrl.setUnlockAllFocus(value);
                          },
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(l10n.unlockFocusAuthTitle),
                          subtitle: Text(l10n.unlockFocusAuthSubtitle),
                          value: _ctrl.unlockFocusAuth,
                          onChanged: (value) async {
                            await InteractionHaptics.toggle();
                            await _ctrl.setUnlockFocusAuth(value);
                          },
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(l10n.showWelcomeTitle),
                          subtitle: Text(l10n.showWelcomeSubtitle),
                          value: _ctrl.showWelcome,
                          onChanged: (value) async {
                            await InteractionHaptics.toggle();
                            await _ctrl.setShowWelcome(value);
                          },
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(l10n.checkUpdateOnLaunchTitle),
                          subtitle: Text(l10n.checkUpdateOnLaunchSubtitle),
                          value: _ctrl.checkUpdateOnLaunch,
                          onChanged: (value) async {
                            await InteractionHaptics.toggle();
                            await _ctrl.setCheckUpdateOnLaunch(value);
                          },
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.vertical(
                              bottom: Radius.circular(16),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),
                  SectionLabel(l10n.defaultConfigSection),
                  const SizedBox(height: 8),
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Column(
                      children: [
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(l10n.firstFloatLabel),
                          subtitle: Text(l10n.firstFloatLabelSubtitle),
                          value: _ctrl.defaultFirstFloat,
                          onChanged: (value) async {
                            await InteractionHaptics.toggle();
                            await _ctrl.setDefaultFirstFloat(value);
                          },
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
                          title: Text(l10n.updateFloatLabel),
                          subtitle: Text(l10n.updateFloatLabelSubtitle),
                          value: _ctrl.defaultEnableFloat,
                          onChanged: (value) async {
                            await InteractionHaptics.toggle();
                            await _ctrl.setDefaultEnableFloat(value);
                          },
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(l10n.marqueeChannelTitle),
                          subtitle: Text(l10n.marqueeChannelTitleSubtitle),
                          value: _ctrl.defaultMarquee,
                          onChanged: (value) async {
                            await InteractionHaptics.toggle();
                            await _ctrl.setDefaultMarquee(value);
                          },
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(l10n.focusNotificationLabel),
                          subtitle: Text(l10n.focusNotificationLabelSubtitle),
                          value: _ctrl.defaultFocusNotif,
                          onChanged: (value) async {
                            await InteractionHaptics.toggle();
                            await _ctrl.setDefaultFocusNotif(value);
                          },
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(l10n.preserveStatusBarSmallIconLabel),
                          subtitle: Text(
                            l10n.preserveStatusBarSmallIconLabelSubtitle,
                          ),
                          value: _ctrl.defaultPreserveSmallIcon,
                          onChanged: (value) async {
                            await InteractionHaptics.toggle();
                            await _ctrl.setDefaultPreserveSmallIcon(value);
                          },
                          shape: const RoundedRectangleBorder(
                            borderRadius: BorderRadius.vertical(
                              bottom: Radius.circular(16),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),
                  SectionLabel(l10n.appearanceSection),
                  const SizedBox(height: 8),
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Column(
                      children: [
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(l10n.useAppIconTitle),
                          subtitle: Text(l10n.useAppIconSubtitle),
                          value: _ctrl.useHookAppIcon,
                          onChanged: _onUseHookAppIconChanged,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(16),
                          ),
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(l10n.roundIconTitle),
                          subtitle: Text(l10n.roundIconSubtitle),
                          value: _ctrl.roundIcon,
                          onChanged: _onRoundIconChanged,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(16),
                          ),
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        Padding(
                          padding: const EdgeInsets.fromLTRB(20, 8, 20, 4),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                mainAxisAlignment:
                                    MainAxisAlignment.spaceBetween,
                                children: [
                                  Text(
                                    '${l10n.marqueeChannelTitle}|${l10n.marqueeSpeedTitle}',
                                    style: Theme.of(
                                      context,
                                    ).textTheme.bodyMedium,
                                  ),
                                  Row(
                                    children: [
                                      Text(
                                        l10n.marqueeSpeedLabel(
                                          _ctrl.marqueeSpeed,
                                        ),
                                        style: Theme.of(context)
                                            .textTheme
                                            .bodyLarge
                                            ?.copyWith(
                                              color: cs.primary,
                                              fontWeight: FontWeight.bold,
                                            ),
                                      ),
                                      Opacity(
                                        opacity: _ctrl.marqueeSpeed != 100
                                            ? 1.0
                                            : 0.0,
                                        child: IconButton(
                                          icon: const Icon(
                                            Icons.refresh,
                                            size: 16,
                                          ),
                                          padding: EdgeInsets.zero,
                                          visualDensity: VisualDensity.compact,
                                          onPressed: _ctrl.marqueeSpeed != 100
                                              ? () async {
                                                  await InteractionHaptics.button();
                                                  await _ctrl.setMarqueeSpeed(
                                                    100,
                                                  );
                                                }
                                              : null,
                                        ),
                                      ),
                                    ],
                                  ),
                                ],
                              ),
                              SliderTheme(
                                data: ModernSliderTheme.theme(context),
                                child: Slider(
                                  value: _ctrl.marqueeSpeed.toDouble(),
                                  min: 20,
                                  max: 500,
                                  divisions: 48,
                                  label: l10n.marqueeSpeedLabel(
                                    _ctrl.marqueeSpeed,
                                  ),
                                  onChanged: _onMarqueeSpeedChanged,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(l10n.themeModeTitle),
                          subtitle: Text(_themeModeLabel(l10n)),
                          onTap: () => _showThemeModeDialog(l10n),
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(l10n.languageTitle),
                          subtitle: Text(_localeLabel(l10n)),
                          onTap: () => _showLanguageDialog(l10n),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.vertical(
                              bottom: Radius.circular(16),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),
                  SectionLabel(l10n.configSection),
                  const SizedBox(height: 8),
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Column(
                      children: [
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          shape: const RoundedRectangleBorder(
                            borderRadius: BorderRadius.vertical(
                              top: Radius.circular(16),
                            ),
                          ),
                          leading: const Icon(Icons.upload_file_outlined),
                          title: Text(l10n.exportConfig),
                          subtitle: Text(l10n.exportConfigSubtitle),
                          onTap: () => _showExportOptions(l10n),
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          leading: const Icon(Icons.download_outlined),
                          shape: const RoundedRectangleBorder(
                            borderRadius: BorderRadius.vertical(
                              bottom: Radius.circular(16),
                            ),
                          ),
                          title: Text(l10n.importConfig),
                          subtitle: Text(l10n.importConfigSubtitle),
                          onTap: () => _showImportOptions(l10n),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 24),
                  SectionLabel(l10n.aboutSection),
                  const SizedBox(height: 8),
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Column(
                      children: [
                        ListTile(
                          leading: const Icon(Icons.system_update_outlined),
                          title: Text(l10n.checkUpdate),
                          trailing: _checkingUpdate
                              ? const SizedBox(
                                  width: 20,
                                  height: 20,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                  ),
                                )
                              : null,
                          onTap: _checkingUpdate ? null : _doCheckUpdate,
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        ListTile(
                          shape: const RoundedRectangleBorder(
                            borderRadius: BorderRadius.vertical(
                              top: Radius.circular(16),
                            ),
                          ),
                          leading: const Icon(Icons.code),
                          title: const Text('GitHub'),
                          subtitle: const Text('1812z/HyperIsland'),
                          trailing: const Icon(Icons.open_in_new, size: 18),
                          onTap: () async {
                            await InteractionHaptics.button();
                            await launchUrl(
                              Uri.parse('https://github.com/1812z/HyperIsland'),
                              mode: LaunchMode.externalApplication,
                            );
                          },
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        ListTile(
                          shape: const RoundedRectangleBorder(
                            borderRadius: BorderRadius.vertical(
                              bottom: Radius.circular(16),
                            ),
                          ),
                          leading: const Icon(Icons.group_outlined),
                          title: Text(l10n.qqGroup),
                          subtitle: const Text('1045114341'),
                          trailing: const Icon(Icons.copy, size: 18),
                          onTap: () async {
                            await InteractionHaptics.button();
                            if (!context.mounted) return;
                            Clipboard.setData(
                              const ClipboardData(text: '1045114341'),
                            );
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(l10n.groupNumberCopied),
                                duration: const Duration(seconds: 2),
                              ),
                            );
                          },
                        ),
                      ],
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

/// Generic radio option for SimpleDialog — pops the dialog with [value].
class _RadioOption<T> extends StatelessWidget {
  const _RadioOption(this.label, this.value, {super.key});

  final String label;
  final T value;

  @override
  Widget build(BuildContext context) {
    return RadioListTile<T>(title: Text(label), value: value);
  }
}
