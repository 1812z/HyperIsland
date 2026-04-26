import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:url_launcher/url_launcher.dart';
import '../controllers/config_io_controller.dart';
import '../controllers/settings_controller.dart';
import '../controllers/update_controller.dart';
import '../controllers/whitelist_controller.dart';
import '../l10n/generated/app_localizations.dart';
import '../services/interaction_haptics.dart';
import '../services/island_background_service.dart';
import '../widgets/color_picker_dialog.dart';
import '../widgets/color_value_field.dart';
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
  late int _marqueeSpeedDraft;
  late int _bigIslandMaxWidthDraft;
  late int _uiStateHash;
  late int _cornerRadiusDraft;

  int _buildUiStateHash() => Object.hashAll([
    _ctrl.loading,
    _ctrl.aiEnabled,
    _ctrl.resumeNotification,
    _ctrl.unlockAllFocus,
    _ctrl.unlockFocusAuth,
    _ctrl.checkUpdateOnLaunch,
    _ctrl.hideDesktopIcon,
    _ctrl.defaultFirstFloat,
    _ctrl.defaultEnableFloat,
    _ctrl.defaultMarquee,
    _ctrl.defaultDynamicHighlightColor,
    _ctrl.defaultOuterGlow,
    _ctrl.defaultIslandOuterGlow,
    _ctrl.defaultFocusNotif,
    _ctrl.defaultPreserveSmallIcon,
    _ctrl.defaultRestoreLockscreen,
    _ctrl.fullscreenBehavior,
    _ctrl.defaultShowIslandIcon,
    _ctrl.defaultOutEffectColor,
    _ctrl.defaultIslandOuterGlowColor,
    _ctrl.roundIcon,
    _ctrl.marqueeSpeed,
    _ctrl.bigIslandMaxWidthEnabled,
    _ctrl.bigIslandMaxWidth,
    _ctrl.themeMode,
    _ctrl.locale,
    _ctrl.interactionHaptics,
    _ctrl.showWelcome,
    _ctrl.useHookAppIcon,
    _ctrl.islandBgSmallPath,
    _ctrl.islandBgBigPath,
    _ctrl.islandBgExpandPath,
    _ctrl.islandBgCornerRadius,
  ]);

  void _onChanged() {
    if (!mounted) return;
    final nextHash = _buildUiStateHash();
    final nextMarquee = _ctrl.marqueeSpeed;
    final nextMaxWidth = _ctrl.bigIslandMaxWidth;
    final nextCornerRadius = _ctrl.islandBgCornerRadius;
    if (nextHash == _uiStateHash &&
        nextMarquee == _marqueeSpeedDraft &&
        nextMaxWidth == _bigIslandMaxWidthDraft &&
        nextCornerRadius == _cornerRadiusDraft) {
      return;
    }
    setState(() {
      _uiStateHash = nextHash;
      _marqueeSpeedDraft = nextMarquee;
      _bigIslandMaxWidthDraft = nextMaxWidth;
      _cornerRadiusDraft = nextCornerRadius;
    });
  }

  @override
  void initState() {
    super.initState();
    _marqueeSpeedDraft = _ctrl.marqueeSpeed;
    _bigIslandMaxWidthDraft = _ctrl.bigIslandMaxWidth;
    _cornerRadiusDraft = _ctrl.islandBgCornerRadius;
    _uiStateHash = _buildUiStateHash();
    _ctrl.addListener(_onChanged);
  }

  @override
  void dispose() {
    _ctrl.removeListener(_onChanged);
    super.dispose();
  }

  Future<void> _onResumeNotificationChanged(bool value) async {
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
    await _ctrl.setRoundIcon(value);
  }

  Future<void> _onHideDesktopIconChanged(bool value) async {
    await _ctrl.setHideDesktopIcon(value);
  }

  Future<void> _onFullscreenBehaviorChanged(String? value) async {
    if (value == null) return;
    await _ctrl.setFullscreenBehavior(value);
  }

  void _onMarqueeSpeedChanged(double value) {
    final next = value.round();
    if (_marqueeSpeedDraft == next) return;
    setState(() => _marqueeSpeedDraft = next);
  }

  void _onBigIslandMaxWidthChanged(double value) {
    final next = value.round();
    if (_bigIslandMaxWidthDraft == next) return;
    setState(() => _bigIslandMaxWidthDraft = next);
  }

  Future<void> _persistMarqueeSpeed(double value) async {
    final next = value.round();
    if (_ctrl.marqueeSpeed == next) return;
    await _ctrl.setMarqueeSpeed(next);
  }

  Future<void> _persistBigIslandMaxWidth(double value) async {
    final next = value.round();
    if (_ctrl.bigIslandMaxWidth == next) return;
    await _ctrl.setBigIslandMaxWidth(next);
  }

  void _onCornerRadiusChanged(double value) {
    final next = value.round();
    if (_cornerRadiusDraft == next) return;
    setState(() => _cornerRadiusDraft = next);
  }

  Future<void> _persistCornerRadius(double value) async {
    final next = value.round();
    if (_ctrl.islandBgCornerRadius == next) return;
    await _ctrl.setIslandBgCornerRadius(next);
  }

  void _showSnack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(msg), duration: const Duration(seconds: 3)),
    );
  }

  Future<void> _pickIslandBackground(IslandBgType type) async {
    final l10n = AppLocalizations.of(context)!;
    final path = await IslandBackgroundService.pickAndCopyImage(type);
    if (path != null && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(l10n.islandBgImageSelected),
          duration: const Duration(seconds: 2),
        ),
      );
    }
  }

  Future<void> _deleteIslandBackground(IslandBgType type) async {
    final l10n = AppLocalizations.of(context)!;
    final success = await IslandBackgroundService.deleteImage(type);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            success ? l10n.islandBgImageDeleted : l10n.islandBgDeleteFailed,
          ),
          duration: const Duration(seconds: 2),
        ),
      );
    }
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

  String _fullscreenBehaviorLabel(AppLocalizations l10n, String value) {
    return switch (value) {
      'fallback' => l10n.fullscreenBehaviorFallback,
      'expand' => l10n.fullscreenBehaviorExpand,
      _ => l10n.fullscreenBehaviorOff,
    };
  }

  String _outerGlowModeLabel(AppLocalizations l10n, String value) {
    return switch (value) {
      kTriOptOn => l10n.optOn,
      kTriOptFollowDynamic => l10n.followDynamicColorLabel,
      _ => l10n.optOff,
    };
  }

  String _outerGlowDefaultsSubtitle(AppLocalizations l10n) {
    return '${l10n.focusNotificationLabel} ${_outerGlowModeLabel(l10n, _ctrl.defaultOuterGlow)} · '
        '${l10n.islandSection} ${_outerGlowModeLabel(l10n, _ctrl.defaultIslandOuterGlow)}';
  }

  Future<void> _showOuterGlowDefaultsDialog(AppLocalizations l10n) async {
    final focusColorController = TextEditingController(
      text: _ctrl.defaultOutEffectColor,
    );
    final islandColorController = TextEditingController(
      text: _ctrl.defaultIslandOuterGlowColor,
    );
    var focusOuterGlow = _ctrl.defaultOuterGlow;
    var islandOuterGlow = _ctrl.defaultIslandOuterGlow;
    var focusColor = _ctrl.defaultOutEffectColor;
    var islandColor = _ctrl.defaultIslandOuterGlowColor;

    bool isFollowDynamic(String value) => value == kTriOptFollowDynamic;

    Future<void> pickColor({
      required bool isIsland,
      required StateSetter setDialogState,
    }) async {
      final color = await showColorPickerDialog(
        context,
        title: isIsland
            ? '${l10n.outEffectColorLabel} (Island)'
            : '${l10n.outEffectColorLabel} (Focus)',
        initialHex: isIsland ? islandColor : focusColor,
        enableAlpha: true,
      );
      if (color == null) return;
      final hex = colorToArgbHex(color);
      setDialogState(() {
        if (isIsland) {
          islandColor = hex;
          islandColorController.text = hex;
        } else {
          focusColor = hex;
          focusColorController.text = hex;
        }
      });
    }

    final shouldSave = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text(l10n.outerGlowLabel),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SizedBox(height: 16),
                Text(
                  l10n.focusNotificationLabel,
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                DropdownButtonFormField<String>(
                  initialValue: focusOuterGlow,
                  decoration: _dialogFieldDecoration(context),
                  items: _outerGlowModeItems(l10n, includeDefault: false),
                  onChanged: (value) {
                    if (value != null) {
                      setDialogState(() => focusOuterGlow = value);
                    }
                  },
                ),
                const SizedBox(height: 8),
                ColorValueField(
                  controller: focusColorController,
                  enabled: !isFollowDynamic(focusOuterGlow),
                  readOnly: isFollowDynamic(focusOuterGlow),
                  decoration: _dialogFieldDecoration(
                    context,
                    hintText: '#AARRGGBB / #RRGGBB',
                  ),
                  previewColor: parseHexColor(focusColor),
                  previewFallbackColor: Theme.of(context).colorScheme.primary,
                  onChanged: (value) =>
                      setDialogState(() => focusColor = value.trim()),
                  onClear: () => setDialogState(() {
                    focusColor = '';
                    focusColorController.clear();
                  }),
                  onPickColor: () => pickColor(
                    isIsland: false,
                    setDialogState: setDialogState,
                  ),
                ),
                const SizedBox(height: 16),
                Text(
                  l10n.islandSection,
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                const SizedBox(height: 8),
                DropdownButtonFormField<String>(
                  initialValue: islandOuterGlow,
                  decoration: _dialogFieldDecoration(context),
                  items: _outerGlowModeItems(l10n, includeDefault: false),
                  onChanged: (value) {
                    if (value != null) {
                      setDialogState(() => islandOuterGlow = value);
                    }
                  },
                ),
                const SizedBox(height: 8),
                ColorValueField(
                  controller: islandColorController,
                  enabled: !isFollowDynamic(islandOuterGlow),
                  readOnly: isFollowDynamic(islandOuterGlow),
                  decoration: _dialogFieldDecoration(
                    context,
                    hintText: '#AARRGGBB / #RRGGBB',
                  ),
                  previewColor: parseHexColor(islandColor),
                  previewFallbackColor: Theme.of(context).colorScheme.primary,
                  onChanged: (value) =>
                      setDialogState(() => islandColor = value.trim()),
                  onClear: () => setDialogState(() {
                    islandColor = '';
                    islandColorController.clear();
                  }),
                  onPickColor: () =>
                      pickColor(isIsland: true, setDialogState: setDialogState),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(false),
              child: Text(l10n.cancel),
            ),
            FilledButton(
              onPressed: () => Navigator.of(ctx).pop(true),
              child: Text(l10n.apply),
            ),
          ],
        ),
      ),
    );

    focusColorController.dispose();
    islandColorController.dispose();

    if (shouldSave != true) return;
    await _ctrl.setDefaultOuterGlow(focusOuterGlow);
    await _ctrl.setDefaultIslandOuterGlow(islandOuterGlow);
    await _ctrl.setDefaultOutEffectColor(focusColor);
    await _ctrl.setDefaultIslandOuterGlowColor(islandColor);
  }

  InputDecoration _dialogFieldDecoration(
    BuildContext context, {
    String? hintText,
  }) {
    final cs = Theme.of(context).colorScheme;
    return InputDecoration(
      hintText: hintText,
      isDense: true,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
      filled: true,
      fillColor: cs.surfaceContainerHighest,
    );
  }

  List<DropdownMenuItem<String>> _outerGlowModeItems(
    AppLocalizations l10n, {
    required bool includeDefault,
  }) {
    final items = <DropdownMenuItem<String>>[];
    if (includeDefault) {
      items.add(
        DropdownMenuItem<String>(
          value: kTriOptDefault,
          child: Text(l10n.optDefault),
        ),
      );
    }
    items.addAll([
      DropdownMenuItem<String>(value: kTriOptOn, child: Text(l10n.optOn)),
      DropdownMenuItem<String>(value: kTriOptOff, child: Text(l10n.optOff)),
      DropdownMenuItem<String>(
        value: kTriOptFollowDynamic,
        child: Text(l10n.followDynamicColorLabel),
      ),
    ]);
    return items;
  }

  Future<void> _showThemeModeDialog(AppLocalizations l10n) async {
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
      if (!mounted) return;
      _ctrl.setThemeMode(result);
    }
  }

  Future<void> _showLanguageDialog(AppLocalizations l10n) async {
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
      if (!mounted) return;
      _ctrl.setLocale(result);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
    final titleStyle = Theme.of(context).textTheme.titleMedium;

    return Scaffold(
      backgroundColor: cs.surface,
      body: CustomScrollView(
        physics: const ClampingScrollPhysics(),
        slivers: [
          SliverAppBar.large(
            backgroundColor: cs.surface,
            title: Text(l10n.navSettings),
          ),
          if (_ctrl.loading)
            const SliverFillRemaining(
              child: Center(child: CircularProgressIndicator()),
            )
          else
            SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              sliver: SliverList(
                delegate: SliverChildListDelegate(
                  [
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
                    const SizedBox(height: 8),
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
                      child: ListTile(
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
                          ListTile(
                            contentPadding: const EdgeInsets.symmetric(
                              horizontal: 16,
                              vertical: 4,
                            ),
                            title: Text(
                              l10n.fullscreenBehaviorTitle,
                              style: titleStyle,
                            ),
                            subtitle: Text(
                              l10n.fullscreenBehaviorSubtitle,
                              style: Theme.of(context).textTheme.bodySmall
                                  ?.copyWith(color: cs.onSurfaceVariant),
                            ),
                            trailing: DropdownButtonHideUnderline(
                              child: DropdownButton<String>(
                                value: _ctrl.fullscreenBehavior,
                                onChanged: InteractionHaptics.interceptDropdown(
                                  _onFullscreenBehaviorChanged,
                                ),
                                items: [
                                  DropdownMenuItem<String>(
                                    value: 'off',
                                    child: Text(
                                      _fullscreenBehaviorLabel(l10n, 'off'),
                                    ),
                                  ),
                                  DropdownMenuItem<String>(
                                    value: 'fallback',
                                    child: Text(
                                      _fullscreenBehaviorLabel(
                                        l10n,
                                        'fallback',
                                      ),
                                    ),
                                  ),
                                  DropdownMenuItem<String>(
                                    value: 'expand',
                                    child: Text(
                                      _fullscreenBehaviorLabel(l10n, 'expand'),
                                    ),
                                  ),
                                ],
                              ),
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
                              l10n.interactionHapticsTitle,
                              style: titleStyle,
                            ),
                            subtitle: Text(l10n.interactionHapticsSubtitle),
                            value: _ctrl.interactionHaptics,
                            onChanged: InteractionHaptics.interceptToggle(
                              (value) => _ctrl.setInteractionHaptics(value),
                              force: true,
                            ),
                          ),
                          const Divider(height: 1, indent: 16, endIndent: 16),
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
                            onChanged: InteractionHaptics.interceptToggle(
                              _onResumeNotificationChanged,
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
                            onChanged: InteractionHaptics.interceptToggle(
                              (value) => _ctrl.setUnlockAllFocus(value),
                            ),
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
                            onChanged: InteractionHaptics.interceptToggle(
                              (value) => _ctrl.setUnlockFocusAuth(value),
                            ),
                          ),
                          const Divider(height: 1, indent: 16, endIndent: 16),
                          SwitchListTile(
                            contentPadding: const EdgeInsets.symmetric(
                              horizontal: 16,
                              vertical: 4,
                            ),
                            title: Text(
                              l10n.showWelcomeTitle,
                              style: titleStyle,
                            ),
                            subtitle: Text(l10n.showWelcomeSubtitle),
                            value: _ctrl.showWelcome,
                            onChanged: InteractionHaptics.interceptToggle(
                              (value) => _ctrl.setShowWelcome(value),
                            ),
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
                            onChanged: InteractionHaptics.interceptToggle(
                              _onHideDesktopIconChanged,
                            ),
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
                            onChanged: InteractionHaptics.interceptToggle(
                              (value) => _ctrl.setCheckUpdateOnLaunch(value),
                            ),
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
                            title: Text(
                              l10n.firstFloatLabel,
                              style: titleStyle,
                            ),
                            subtitle: Text(l10n.firstFloatLabelSubtitle),
                            value: _ctrl.defaultFirstFloat,
                            onChanged: InteractionHaptics.interceptToggle(
                              (value) => _ctrl.setDefaultFirstFloat(value),
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
                              l10n.updateFloatLabel,
                              style: titleStyle,
                            ),
                            subtitle: Text(l10n.updateFloatLabelSubtitle),
                            value: _ctrl.defaultEnableFloat,
                            onChanged: InteractionHaptics.interceptToggle(
                              (value) => _ctrl.setDefaultEnableFloat(value),
                            ),
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
                            onChanged: InteractionHaptics.interceptToggle(
                              (value) => _ctrl.setDefaultMarquee(value),
                            ),
                          ),
                          const Divider(height: 1, indent: 16, endIndent: 16),
                          SwitchListTile(
                            contentPadding: const EdgeInsets.symmetric(
                              horizontal: 16,
                              vertical: 4,
                            ),
                            title: Text(
                              l10n.dynamicHighlightColorLabel,
                              style: titleStyle,
                            ),
                            subtitle: Text(
                              l10n.dynamicHighlightColorLabelSubtitle,
                            ),
                            value: _ctrl.defaultDynamicHighlightColor,
                            onChanged: InteractionHaptics.interceptToggle(
                              (value) =>
                                  _ctrl.setDefaultDynamicHighlightColor(value),
                            ),
                          ),
                          const Divider(height: 1, indent: 16, endIndent: 16),
                          ListTile(
                            contentPadding: const EdgeInsets.symmetric(
                              horizontal: 16,
                              vertical: 4,
                            ),
                            title: Text(l10n.outerGlowLabel, style: titleStyle),
                            subtitle: Text(_outerGlowDefaultsSubtitle(l10n)),
                            trailing: const Icon(Icons.chevron_right),
                            onTap: InteractionHaptics.interceptButton(
                              () => _showOuterGlowDefaultsDialog(l10n),
                            ),
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
                            onChanged: InteractionHaptics.interceptToggle(
                              (value) => _ctrl.setDefaultFocusNotif(value),
                            ),
                          ),
                          const Divider(height: 1, indent: 16, endIndent: 16),
                          SwitchListTile(
                            contentPadding: const EdgeInsets.symmetric(
                              horizontal: 16,
                              vertical: 4,
                            ),
                            title: Text(
                              l10n.restoreLockscreenTitle,
                              style: titleStyle,
                            ),
                            subtitle: Text(l10n.restoreLockscreenSubtitle),
                            value: _ctrl.defaultRestoreLockscreen,
                            onChanged: InteractionHaptics.interceptToggle(
                              (value) =>
                                  _ctrl.setDefaultRestoreLockscreen(value),
                            ),
                          ),
                          const Divider(height: 1, indent: 16, endIndent: 16),
                          SwitchListTile(
                            contentPadding: const EdgeInsets.symmetric(
                              horizontal: 16,
                              vertical: 4,
                            ),
                            title: Text(
                              l10n.islandIconLabel,
                              style: titleStyle,
                            ),
                            subtitle: Text(l10n.islandIconLabelSubtitle),
                            value: _ctrl.defaultShowIslandIcon,
                            onChanged: InteractionHaptics.interceptToggle(
                              (value) => _ctrl.setDefaultShowIslandIcon(value),
                            ),
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
                            onChanged: InteractionHaptics.interceptToggle(
                              (value) =>
                                  _ctrl.setDefaultPreserveSmallIcon(value),
                            ),
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
                            title: Text(
                              l10n.useAppIconTitle,
                              style: titleStyle,
                            ),
                            subtitle: Text(l10n.useAppIconSubtitle),
                            value: _ctrl.useHookAppIcon,
                            onChanged: InteractionHaptics.interceptToggle(
                              _onUseHookAppIconChanged,
                            ),
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
                            title: Text(l10n.roundIconTitle, style: titleStyle),
                            subtitle: Text(l10n.roundIconSubtitle),
                            value: _ctrl.roundIcon,
                            onChanged: InteractionHaptics.interceptToggle(
                              _onRoundIconChanged,
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
                                  l10n.marqueeSpeedLabel(_marqueeSpeedDraft),
                                  style: Theme.of(context).textTheme.bodySmall
                                      ?.copyWith(color: cs.onSurfaceVariant),
                                ),
                                if (_marqueeSpeedDraft != 100)
                                  SizedBox(
                                    width: 18,
                                    height: 18,
                                    child: IconButton(
                                      icon: const Icon(Icons.refresh, size: 18),
                                      padding: EdgeInsets.zero,
                                      visualDensity: VisualDensity.compact,
                                      onPressed:
                                          InteractionHaptics.interceptButton(
                                            () {
                                              setState(
                                                () => _marqueeSpeedDraft = 100,
                                              );
                                              _ctrl.setMarqueeSpeed(100);
                                            },
                                          ),
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
                                    data: ModernSliderTheme.theme(context),
                                    child: Slider(
                                      value: _marqueeSpeedDraft.toDouble(),
                                      min: 20,
                                      max: 500,
                                      divisions: 48,
                                      onChanged:
                                          InteractionHaptics.interceptSlider(
                                            _onMarqueeSpeedChanged,
                                          ),
                                      onChangeEnd: _persistMarqueeSpeed,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const Divider(height: 1, indent: 16, endIndent: 16),
                          SwitchListTile(
                            contentPadding: const EdgeInsets.symmetric(
                              horizontal: 16,
                              vertical: 2,
                            ),
                            title: Text(
                              l10n.bigIslandMaxWidthTitle,
                              style: titleStyle,
                            ),
                            subtitle: Text(
                              l10n.bigIslandMaxWidthSubtitle,
                              style: Theme.of(context).textTheme.bodySmall
                                  ?.copyWith(color: cs.onSurfaceVariant),
                            ),
                            value: _ctrl.bigIslandMaxWidthEnabled,
                            onChanged: InteractionHaptics.interceptToggle(
                              (value) =>
                                  _ctrl.setBigIslandMaxWidthEnabled(value),
                            ),
                          ),
                          if (_ctrl.bigIslandMaxWidthEnabled)
                            Padding(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 16,
                              ),
                              child: Row(
                                children: [
                                  Text(
                                    l10n.bigIslandMaxWidthLabel(
                                      _bigIslandMaxWidthDraft,
                                    ),
                                    style: Theme.of(context).textTheme.bodySmall
                                        ?.copyWith(color: cs.onSurfaceVariant),
                                  ),
                                  if (_bigIslandMaxWidthDraft != 200)
                                    SizedBox(
                                      width: 18,
                                      height: 18,
                                      child: IconButton(
                                        icon: const Icon(
                                          Icons.refresh,
                                          size: 18,
                                        ),
                                        padding: EdgeInsets.zero,
                                        visualDensity: VisualDensity.compact,
                                        onPressed:
                                            InteractionHaptics.interceptButton(
                                              () {
                                                setState(
                                                  () =>
                                                      _bigIslandMaxWidthDraft =
                                                          200,
                                                );
                                                _ctrl.setBigIslandMaxWidth(200);
                                              },
                                            ),
                                      ),
                                    ),
                                  Expanded(
                                    child: SliderTheme(
                                      data: ModernSliderTheme.theme(context),
                                      child: Slider(
                                        value: _bigIslandMaxWidthDraft
                                            .toDouble(),
                                        min: 50,
                                        max: 500,
                                        divisions: 54,
                                        onChanged:
                                            InteractionHaptics.interceptSlider(
                                              _onBigIslandMaxWidthChanged,
                                            ),
                                        onChangeEnd: _persistBigIslandMaxWidth,
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
                            onTap: InteractionHaptics.interceptButton(
                              () => _showThemeModeDialog(l10n),
                            ),
                          ),
                          const Divider(height: 1, indent: 16, endIndent: 16),
                          ListTile(
                            contentPadding: const EdgeInsets.symmetric(
                              horizontal: 16,
                              vertical: 4,
                            ),
                            title: Text(l10n.languageTitle, style: titleStyle),
                            subtitle: Text(_localeLabel(l10n)),
                            onTap: InteractionHaptics.interceptButton(
                              () => _showLanguageDialog(l10n),
                            ),
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
                      child: SectionLabel(l10n.islandBgSection),
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
                          _IslandBgTile(
                            title: l10n.islandBgSmallTitle,
                            subtitle: l10n.islandBgSmallSubtitle,
                            icon: Icons.panorama_vertical,
                            imagePath: _ctrl.islandBgSmallPath,
                            onTap: () => _pickIslandBackground(IslandBgType.small),
                            onDelete: _ctrl.islandBgSmallPath.isNotEmpty
                                ? () => _deleteIslandBackground(IslandBgType.small)
                                : null,
                          ),
                          const Divider(height: 1, indent: 16, endIndent: 16),
                          _IslandBgTile(
                            title: l10n.islandBgBigTitle,
                            subtitle: l10n.islandBgBigSubtitle,
                            icon: Icons.panorama_vertical,
                            imagePath: _ctrl.islandBgBigPath,
                            onTap: () => _pickIslandBackground(IslandBgType.big),
                            onDelete: _ctrl.islandBgBigPath.isNotEmpty
                                ? () => _deleteIslandBackground(IslandBgType.big)
                                : null,
                          ),
                          const Divider(height: 1, indent: 16, endIndent: 16),
                          _IslandBgTile(
                            title: l10n.islandBgExpandTitle,
                            subtitle: l10n.islandBgExpandSubtitle,
                            icon: Icons.panorama_vertical,
                            imagePath: _ctrl.islandBgExpandPath,
                            onTap: () => _pickIslandBackground(IslandBgType.expand),
                            onDelete: _ctrl.islandBgExpandPath.isNotEmpty
                                ? () => _deleteIslandBackground(IslandBgType.expand)
                                : null,
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
                                    l10n.islandBgCornerRadius,
                                    style: titleStyle,
                                  ),
                                ),
                                Text(
                                  '${_cornerRadiusDraft} dp',
                                  style: Theme.of(context).textTheme.bodySmall
                                      ?.copyWith(color: cs.onSurfaceVariant),
                                ),
                                if (_cornerRadiusDraft != 0)
                                  SizedBox(
                                    width: 18,
                                    height: 18,
                                    child: IconButton(
                                      icon: const Icon(Icons.refresh, size: 18),
                                      padding: EdgeInsets.zero,
                                      visualDensity: VisualDensity.compact,
                                      onPressed:
                                          InteractionHaptics.interceptButton(
                                        () {
                                          setState(
                                            () => _cornerRadiusDraft = 0,
                                          );
                                          _ctrl.setIslandBgCornerRadius(0);
                                        },
                                      ),
                                    ),
                                  ),
                              ],
                            ),
                            subtitle: Row(
                              children: [
                                Text(
                                  l10n.islandBgCornerRadiusHint,
                                  style: Theme.of(context).textTheme.bodySmall
                                      ?.copyWith(color: cs.onSurfaceVariant),
                                ),
                                Expanded(
                                  child: SliderTheme(
                                    data: ModernSliderTheme.theme(context),
                                    child: Slider(
                                      value: _cornerRadiusDraft.toDouble(),
                                      min: 0,
                                      max: 100,
                                      divisions: 20,
                                      onChanged:
                                          InteractionHaptics.interceptSlider(
                                        _onCornerRadiusChanged,
                                      ),
                                      onChangeEnd: _persistCornerRadius,
                                    ),
                                  ),
                                ),
                              ],
                            ),
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
                            onTap: InteractionHaptics.interceptButton(
                              _exportToFile,
                            ),
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
                            onTap: InteractionHaptics.interceptButton(
                              _exportToClipboard,
                            ),
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
                            onTap: InteractionHaptics.interceptButton(
                              _importFromFile,
                            ),
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
                            onTap: InteractionHaptics.interceptButton(
                              _importFromClipboard,
                            ),
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
                            shape: const RoundedRectangleBorder(
                              borderRadius: BorderRadius.vertical(
                                top: Radius.circular(16),
                              ),
                            ),
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
                            onTap: _checkingUpdate
                                ? null
                                : InteractionHaptics.interceptButton(
                                    _doCheckUpdate,
                                  ),
                          ),
                          const Divider(height: 1, indent: 16, endIndent: 16),
                          ListTile(
                            leading: const Icon(Icons.code),
                            title: Text('GitHub', style: titleStyle),
                            subtitle: const Text('1812z/HyperIsland'),
                            trailing: const Icon(Icons.open_in_new, size: 18),
                            onTap: InteractionHaptics.interceptButton(() async {
                              await launchUrl(
                                Uri.parse(
                                  'https://github.com/1812z/HyperIsland',
                                ),
                                mode: LaunchMode.externalApplication,
                              );
                            }),
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
                            onTap: InteractionHaptics.interceptButton(() async {
                              Clipboard.setData(
                                const ClipboardData(text: '1045114341'),
                              );
                              if (!mounted) return;
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(
                                  content: Text(l10n.groupNumberCopied),
                                  duration: const Duration(seconds: 2),
                                ),
                              );
                            }),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 32),
                  ],
                  addAutomaticKeepAlives: false,
                  addSemanticIndexes: true,
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _IslandBgTile extends StatelessWidget {
  const _IslandBgTile({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.imagePath,
    required this.onTap,
    this.onDelete,
  });

  final String title;
  final String subtitle;
  final IconData icon;
  final String imagePath;
  final VoidCallback onTap;
  final VoidCallback? onDelete;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final hasImage = imagePath.isNotEmpty;

    return ListTile(
      contentPadding: const EdgeInsets.symmetric(
        horizontal: 16,
        vertical: 4,
      ),
      leading: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(
          color: cs.surfaceContainerHigh,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: hasImage ? cs.primary : cs.outline.withValues(alpha: 0.3),
            width: hasImage ? 2 : 1,
          ),
        ),
        child: hasImage
            ? ClipRRect(
                borderRadius: BorderRadius.circular(6),
                child: Image.file(
                  File(imagePath),
                  fit: BoxFit.cover,
                  errorBuilder: (_, __, ___) => Icon(
                    icon,
                    color: cs.onSurfaceVariant,
                    size: 24,
                  ),
                ),
              )
            : Icon(
                icon,
                color: cs.onSurfaceVariant,
                size: 24,
              ),
      ),
      title: Text(title),
      subtitle: Text(
        hasImage ? subtitle : AppLocalizations.of(context)!.islandBgNotSet,
        style: Theme.of(context).textTheme.bodySmall
            ?.copyWith(color: cs.onSurfaceVariant),
      ),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (hasImage && onDelete != null)
            IconButton(
              icon: Icon(Icons.delete_outline, color: cs.error),
              onPressed: onDelete,
              visualDensity: VisualDensity.compact,
            ),
          const Icon(Icons.chevron_right),
        ],
      ),
      onTap: onTap,
    );
  }
}

class _RadioOption<T> extends StatelessWidget {
  const _RadioOption(this.label, this.value, {super.key});

  final String label;
  final T value;

  @override
  Widget build(BuildContext context) {
    return RadioListTile<T>(title: Text(label), value: value);
  }
}
