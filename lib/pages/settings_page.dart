import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:url_launcher/url_launcher.dart';
import '../controllers/config_io_controller.dart';
import '../controllers/settings_controller.dart';
import '../controllers/update_controller.dart';
import '../l10n/generated/app_localizations.dart';
import '../widgets/section_label.dart';
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
    await _ctrl.setResumeNotification(value);
  }

  Future<void> _onRoundIconChanged(bool value) async {
    await _ctrl.setRoundIcon(value);
  }

  Future<void> _onHideDesktopIconChanged(bool value) async {
    await _ctrl.setHideDesktopIcon(value);
  }

  void _onMarqueeSpeedChanged(double value) {
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
    try {
      final count = await ConfigIOController.importFromClipboard();
      _showSnack(l10n.importSuccess(count));
    } on ConfigIOException catch (e) {
      _showSnack(l10n.importFailed(_localizeConfigIOError(l10n, e.error)));
    } catch (e) {
      _showSnack(l10n.importFailed(e.toString()));
    }
  }

  Future<void> _doCheckUpdate() async {
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
    final result = await showDialog<ThemeMode>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: Text(l10n.themeModeTitle),
        children: [
          _RadioOption(l10n.themeModeSystem, ThemeMode.system, _ctrl.themeMode),
          _RadioOption(l10n.themeModeLight, ThemeMode.light, _ctrl.themeMode),
          _RadioOption(l10n.themeModeDark, ThemeMode.dark, _ctrl.themeMode),
        ],
      ),
    );
    if (result != null) _ctrl.setThemeMode(result);
  }

  Future<void> _showLanguageDialog(AppLocalizations l10n) async {
    final result = await showDialog<Locale?>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: Text(l10n.languageTitle),
        children: [
          _RadioOption<Locale?>(l10n.languageAuto, null, _ctrl.locale),
          _RadioOption<Locale?>(
            l10n.languageZh,
            const Locale('zh'),
            _ctrl.locale,
          ),
          _RadioOption<Locale?>(
            l10n.languageEn,
            const Locale('en'),
            _ctrl.locale,
          ),
          _RadioOption<Locale?>(
            l10n.languageJa,
            const Locale('ja'),
            _ctrl.locale,
          ),
          _RadioOption<Locale?>(
            l10n.languageTr,
            const Locale('tr'),
            _ctrl.locale,
          ),
        ],
      ),
    );
    if (result != _ctrl.locale) _ctrl.setLocale(result);
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
    const titleStyle = TextStyle(fontWeight: FontWeight.w500);

    return Scaffold(
      backgroundColor: cs.surface,
      body: CustomScrollView(
        slivers: [
          SliverAppBar(
            pinned: true,
            floating: true,
            expandedHeight: 130,
            backgroundColor: cs.surface,
            flexibleSpace: FlexibleSpaceBar(
              titlePadding: const EdgeInsets.only(left: 26, bottom: 16),
              title: Text(
                l10n.navSettings,
                style: TextStyle(fontSize: 22, fontWeight: FontWeight.w400),
              ),
            ),
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
                  Padding(
                    padding: const EdgeInsets.only(left: 18, top: 8),
                    child: SectionLabel(l10n.aiConfigSection),
                  ),
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
                      title: Text(l10n.aiConfigTitle, style: titleStyle),
                      subtitle: Text(
                        _ctrl.aiEnabled
                            ? l10n.aiConfigSubtitleEnabled
                            : l10n.aiConfigSubtitleDisabled,
                      ),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () => Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => const AiConfigPage(),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 8), // 修改2: 减小间距以保持一致性
                  Padding(
                    padding: const EdgeInsets.only(left: 18),
                    child: SectionLabel(l10n.navBlacklist),
                  ),
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
                          title: Text(l10n.navBlacklist, style: titleStyle),
                          subtitle: Text(l10n.navBlacklistSubtitle),
                          trailing: const Icon(Icons.chevron_right),
                          onTap: () {
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
                  const SizedBox(height: 8),
                  Padding(
                    padding: const EdgeInsets.only(left: 18),
                    child: SectionLabel(l10n.behaviorSection),
                  ),
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
                          title: Text(
                            l10n.keepFocusNotifTitle,
                            style: titleStyle,
                          ),
                          subtitle: Text(l10n.keepFocusNotifSubtitle),
                          value: _ctrl.resumeNotification,
                          onChanged: _onResumeNotificationChanged,
                          shape: RoundedRectangleBorder(
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
                            l10n.unlockAllFocusTitle,
                            style: titleStyle,
                          ),
                          subtitle: Text(l10n.unlockAllFocusSubtitle),
                          value: _ctrl.unlockAllFocus,
                          onChanged: _ctrl.setUnlockAllFocus,
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(
                            l10n.unlockFocusAuthTitle,
                            style: titleStyle,
                          ),
                          subtitle: Text(l10n.unlockFocusAuthSubtitle),
                          value: _ctrl.unlockFocusAuth,
                          onChanged: _ctrl.setUnlockFocusAuth,
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(
                            l10n.checkUpdateOnLaunchTitle,
                            style: titleStyle,
                          ),
                          subtitle: Text(l10n.checkUpdateOnLaunchSubtitle),
                          value: _ctrl.checkUpdateOnLaunch,
                          onChanged: _ctrl.setCheckUpdateOnLaunch,
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(
                            l10n.hideDesktopIconTitle,
                            style: titleStyle,
                          ),
                          subtitle: Text(l10n.hideDesktopIconSubtitle),
                          value: _ctrl.hideDesktopIcon,
                          onChanged: _onHideDesktopIconChanged,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.vertical(
                              bottom: Radius.circular(16),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 8),
                  Padding(
                    padding: const EdgeInsets.only(left: 18),
                    child: SectionLabel(l10n.defaultConfigSection),
                  ),
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
                          title: Text(l10n.firstFloatLabel, style: titleStyle),
                          subtitle: Text(l10n.firstFloatLabelSubtitle),
                          value: _ctrl.defaultFirstFloat,
                          onChanged: _ctrl.setDefaultFirstFloat,
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
                          title: Text(l10n.updateFloatLabel, style: titleStyle),
                          subtitle: Text(l10n.updateFloatLabelSubtitle),
                          value: _ctrl.defaultEnableFloat,
                          onChanged: _ctrl.setDefaultEnableFloat,
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(
                            l10n.marqueeChannelTitle,
                            style: titleStyle,
                          ),
                          subtitle: Text(l10n.marqueeChannelTitleSubtitle),
                          value: _ctrl.defaultMarquee,
                          onChanged: _ctrl.setDefaultMarquee,
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(
                            l10n.focusNotificationLabel,
                            style: titleStyle,
                          ),
                          subtitle: Text(l10n.focusNotificationLabelSubtitle),
                          value: _ctrl.defaultFocusNotif,
                          onChanged: _ctrl.setDefaultFocusNotif,
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(
                            l10n.preserveStatusBarSmallIconLabel,
                            style: titleStyle,
                          ),
                          subtitle: Text(
                            l10n.preserveStatusBarSmallIconLabelSubtitle,
                          ),
                          value: _ctrl.defaultPreserveSmallIcon,
                          onChanged: _ctrl.setDefaultPreserveSmallIcon,
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        SwitchListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(l10n.islandIconLabel, style: titleStyle),
                          subtitle: Text(l10n.islandIconLabelSubtitle),
                          value: _ctrl.defaultShowIslandIcon,
                          onChanged: _ctrl.setDefaultShowIslandIcon,
                          shape: const RoundedRectangleBorder(
                            borderRadius: BorderRadius.vertical(
                              bottom: Radius.circular(16),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 8),
                  Padding(
                    padding: const EdgeInsets.only(left: 18),
                    child: SectionLabel(l10n.appearanceSection),
                  ),
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
                          title: Text(l10n.roundIconTitle, style: titleStyle),
                          subtitle: Text(l10n.roundIconSubtitle),
                          value: _ctrl.roundIcon,
                          onChanged: _onRoundIconChanged,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(16),
                          ),
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 2,
                          ),
                          title: Row(
                            children: [
                              Expanded(
                                child: Text(
                                  l10n.marqueeChannelTitle,
                                  style: titleStyle,
                                ),
                              ),
                              Text(
                                l10n.marqueeSpeedLabel(_ctrl.marqueeSpeed),
                                style: Theme.of(context).textTheme.bodySmall
                                    ?.copyWith(color: cs.onSurfaceVariant),
                              ),
                              if (_ctrl.marqueeSpeed != 100)
                                SizedBox(
                                  width: 18,
                                  height: 18,
                                  child: IconButton(
                                    icon: const Icon(Icons.refresh, size: 18),
                                    padding: EdgeInsets.zero,
                                    visualDensity: VisualDensity.compact,
                                    onPressed: () => _ctrl.setMarqueeSpeed(100),
                                  ),
                                ),
                            ],
                          ),
                          subtitle: Row(
                            children: [
                              Text(
                                l10n.marqueeSpeedTitle,
                                style: Theme.of(context).textTheme.bodySmall
                                    ?.copyWith(color: cs.onSurfaceVariant),
                              ),
                              Expanded(
                                child: SliderTheme(
                                  data: Theme.of(context).sliderTheme.copyWith(
                                    trackHeight: 3,
                                    thumbShape: const RoundSliderThumbShape(
                                      enabledThumbRadius: 6,
                                    ),
                                    overlayShape: const RoundSliderOverlayShape(
                                      overlayRadius: 14,
                                    ),
                                    activeTrackColor: cs.primary,
                                    inactiveTrackColor: cs.primary.withValues(
                                      alpha: 0.24,
                                    ),
                                    thumbColor: cs.primary,
                                  ),
                                  child: Slider(
                                    value: _ctrl.marqueeSpeed.toDouble(),
                                    min: 20,
                                    max: 500,
                                    divisions: 48,
                                    onChanged: _onMarqueeSpeedChanged,
                                  ),
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
                          title: Text(l10n.themeModeTitle, style: titleStyle),
                          subtitle: Text(_themeModeLabel(l10n)),
                          onTap: () => _showThemeModeDialog(l10n),
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          title: Text(l10n.languageTitle, style: titleStyle),
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
                  const SizedBox(height: 8),
                  Padding(
                    padding: const EdgeInsets.only(left: 18),
                    child: SectionLabel(l10n.configSection),
                  ),
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
                          title: Text(l10n.exportToFile, style: titleStyle),
                          subtitle: Text(l10n.exportToFileSubtitle),
                          onTap: _exportToFile,
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          leading: const Icon(Icons.copy_outlined),
                          title: Text(
                            l10n.exportToClipboard,
                            style: titleStyle,
                          ),
                          subtitle: Text(l10n.exportToClipboardSubtitle),
                          onTap: _exportToClipboard,
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          leading: const Icon(Icons.download_outlined),
                          title: Text(l10n.importFromFile, style: titleStyle),
                          subtitle: Text(l10n.importFromFileSubtitle),
                          onTap: _importFromFile,
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          shape: const RoundedRectangleBorder(
                            borderRadius: BorderRadius.vertical(
                              bottom: Radius.circular(16),
                            ),
                          ),
                          leading: const Icon(Icons.paste_outlined),
                          title: Text(
                            l10n.importFromClipboard,
                            style: titleStyle,
                          ),
                          subtitle: Text(l10n.importFromClipboardSubtitle),
                          onTap: _importFromClipboard,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 8),
                  Padding(
                    padding: const EdgeInsets.only(left: 18),
                    child: SectionLabel(l10n.aboutSection),
                  ),
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
                          title: Text(l10n.checkUpdate, style: titleStyle),
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
                          title: const Text('GitHub', style: titleStyle),
                          subtitle: const Text('1812z/HyperIsland'),
                          trailing: const Icon(Icons.open_in_new, size: 18),
                          onTap: () => launchUrl(
                            Uri.parse('https://github.com/1812z/HyperIsland'),
                            mode: LaunchMode.externalApplication,
                          ),
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        ListTile(
                          shape: const RoundedRectangleBorder(
                            borderRadius: BorderRadius.vertical(
                              bottom: Radius.circular(16),
                            ),
                          ),
                          leading: const Icon(Icons.group_outlined),
                          title: Text(l10n.qqGroup, style: titleStyle),
                          subtitle: const Text('1045114341'),
                          trailing: const Icon(Icons.copy, size: 18),
                          onTap: () {
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
  const _RadioOption(this.label, this.value, this.groupValue, {super.key});

  final String label;
  final T value;
  final T groupValue;

  @override
  Widget build(BuildContext context) {
    return RadioListTile<T>(
      title: Text(label),
      value: value,
      groupValue: groupValue,
      onChanged: (_) => Navigator.of(context).pop(value),
    );
  }
}
