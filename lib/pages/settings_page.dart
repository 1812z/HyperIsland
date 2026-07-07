import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';
import '../controllers/settings_controller.dart';
import '../controllers/update_controller.dart';
import '../l10n/generated/app_localizations.dart';
import '../widgets/blur_app_bar.dart';
import '../services/interaction_haptics.dart';
import '../services/app_info_service.dart';
import 'island_sub/island_appearance_page.dart';
import 'island_sub/keep_island_page.dart';
import 'island_sub/island_other_page.dart';
import 'island_sub/misc_page.dart';
import 'island_sub/backup_restore_page.dart';
import 'island_sub/hook_extension_page.dart';
import 'island_sub/default_config_page.dart';
import 'island_sub/hide_behavior_page.dart';
import 'island_sub/theme_page.dart';
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

  void _onChanged() {
    if (!mounted) return;
    setState(() {});
  }

  // --- 语言 ---
  String _localeLabel(AppLocalizations l10n) {
    if (_ctrl.locale == null) return l10n.languageAuto;
    return switch (_ctrl.locale!.languageCode) {
      'zh' => l10n.languageZh,
      'en' => l10n.languageEn,
      'ja' => l10n.languageJa,
      'ru' => l10n.languageRu,
      'tr' => l10n.languageTr,
      _ => _ctrl.locale!.languageCode,
    };
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
                RadioListTile<Locale?>(
                  title: Text(l10n.languageAuto),
                  value: null,
                ),
                RadioListTile<Locale?>(
                  title: Text(l10n.languageZh),
                  value: const Locale('zh'),
                ),
                RadioListTile<Locale?>(
                  title: Text(l10n.languageEn),
                  value: const Locale('en'),
                ),
                RadioListTile<Locale?>(
                  title: Text(l10n.languageJa),
                  value: const Locale('ja'),
                ),
                RadioListTile<Locale?>(
                  title: Text(l10n.languageRu),
                  value: const Locale('ru'),
                ),
                RadioListTile<Locale?>(
                  title: Text(l10n.languageTr),
                  value: const Locale('tr'),
                ),
              ],
            ),
          ),
        ],
      ),
    );
    if (result != _ctrl.locale && mounted) {
      _ctrl.setLocale(result);
    }
  }

  // --- 检查更新 ---
  Future<void> _doCheckUpdate() async {
    setState(() => _checkingUpdate = true);
    try {
      final version = await AppInfoService.getVersion();
      if (mounted) {
        await UpdateController.checkAndShow(
          context,
          version,
          showUpToDate: true,
        );
      }
    } finally {
      if (mounted) setState(() => _checkingUpdate = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;

    final bottomPad = _ctrl.blurBars ? 80.0 : 0.0;

    return Scaffold(
      backgroundColor: cs.surface,
      body: BlurAppBarHost(
        title: l10n.navSettings,
        largeTitle: true,
        physics: const ClampingScrollPhysics(),
        bottomPadding: bottomPad,
        slivers: [
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            sliver: SliverList(
              delegate: SliverChildListDelegate(
                [
                  const SizedBox(height: 8),
                  // 岛
                  _SectionLabel(l10n.islandSection),
                  const SizedBox(height: 8),
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    child: Column(
                      children: [
                        _MenuTile(
                          icon: Icons.palette_outlined,
                          title: l10n.appearanceSection,
                          isFirst: true,
                          onTap: InteractionHaptics.interceptButton(
                            () => Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => const IslandAppearancePage(),
                              ),
                            ),
                          ),
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        _MenuTile(
                          icon: Icons.psychology_outlined,
                          title: l10n.aiConfigTitle,
                          onTap: InteractionHaptics.interceptButton(
                            () => Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => const AiConfigPage(),
                              ),
                            ),
                          ),
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        _MenuTile(
                          icon: Icons.rule_folder_outlined,
                          title: l10n.filterRulesSection,
                          onTap: InteractionHaptics.interceptButton(
                            () => Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => const BlacklistPage(),
                              ),
                            ),
                          ),
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        _MenuTile(
                          icon: Icons.tune,
                          title: l10n.defaultConfigSection,
                          onTap: InteractionHaptics.interceptButton(
                            () => Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => const DefaultConfigPage(),
                              ),
                            ),
                          ),
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        _MenuTile(
                          icon: Icons.visibility_off_outlined,
                          title: l10n.hideBehaviorTitle,
                          onTap: InteractionHaptics.interceptButton(
                            () => Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => const HideBehaviorPage(),
                              ),
                            ),
                          ),
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        _MenuTile(
                          icon: Icons.push_pin_outlined,
                          title: '常驻岛',
                          onTap: InteractionHaptics.interceptButton(
                            () => Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => const KeepIslandPage(),
                              ),
                            ),
                          ),
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        _MenuTile(
                          icon: Icons.more_horiz,
                          title: l10n.islandOtherSection,
                          isLast: true,
                          onTap: InteractionHaptics.interceptButton(
                            () => Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => const IslandOtherPage(),
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 8),
                  // 杂项
                  _SectionLabel(l10n.miscSection),
                  const SizedBox(height: 8),
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    child: _MenuTile(
                      icon: Icons.miscellaneous_services_outlined,
                      title: l10n.miscSection,
                      isFirst: true,
                      isLast: true,
                      onTap: InteractionHaptics.interceptButton(
                        () => Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => const MiscPage(),
                          ),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  // Hook拓展
                  _SectionLabel(l10n.hookExtensionSection),
                  const SizedBox(height: 8),
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    child: _MenuTile(
                      icon: Icons.extension_outlined,
                      title: l10n.hookExtensionSection,
                      isFirst: true,
                      isLast: true,
                      onTap: InteractionHaptics.interceptButton(
                        () => Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => const HookExtensionPage(),
                          ),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  // 备份与恢复
                  _SectionLabel(l10n.backupRestoreSection),
                  const SizedBox(height: 8),
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    child: _MenuTile(
                      icon: Icons.restore_outlined,
                      title: l10n.backupRestoreSection,
                      isFirst: true,
                      isLast: true,
                      onTap: InteractionHaptics.interceptButton(
                        () => Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => const BackupRestorePage(),
                          ),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  _SectionLabel(l10n.appearanceSection),
                  const SizedBox(height: 8),
                  // 颜色模式 + 语言
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    child: Column(
                      children: [
                        _MenuTile(
                          icon: Icons.color_lens_outlined,
                          title: l10n.themePageTitle,
                          isFirst: true,
                          onTap: InteractionHaptics.interceptButton(
                            () => Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => const ThemePage(),
                              ),
                            ),
                          ),
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          leading: const Icon(Icons.language),
                          title: Text(
                            l10n.languageTitle,
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          subtitle: Text(_localeLabel(l10n)),
                          trailing: const Icon(Icons.chevron_right),
                          shape: const RoundedRectangleBorder(
                            borderRadius: BorderRadius.vertical(
                              bottom: Radius.circular(16),
                            ),
                          ),
                          onTap: InteractionHaptics.interceptButton(
                                () => _showLanguageDialog(l10n),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 8),
                  _SectionLabel(l10n.aboutSection),
                  const SizedBox(height: 8),
                  // 检查更新 / GitHub / QQ交流群
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    child: Column(
                      children: [
                        ListTile(
                          shape: const RoundedRectangleBorder(
                            borderRadius: BorderRadius.vertical(
                              top: Radius.circular(16),
                            ),
                          ),
                          leading: const Icon(Icons.system_update_outlined),
                          title: Text(
                            l10n.checkUpdate,
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
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
                          title: Text(
                            'GitHub',
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          subtitle: const Text('1812z/HyperIsland'),
                          trailing:
                              const Icon(Icons.open_in_new, size: 18),
                          onTap: InteractionHaptics.interceptButton(
                            () async {
                              await launchUrl(
                                Uri.parse(
                                  'https://github.com/1812z/HyperIsland',
                                ),
                                mode: LaunchMode.externalApplication,
                              );
                            },
                          ),
                        ),
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        ListTile(
                          leading: const Icon(Icons.telegram),
                          title: Text(
                            'Telegram',
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          subtitle: const Text('HyperIsland_Module'),
                          trailing:
                          const Icon(Icons.open_in_new, size: 18),
                          onTap: InteractionHaptics.interceptButton(
                                () async {
                              await launchUrl(
                                Uri.parse(
                                  'https://t.me/HyperIsland_Module',
                                ),
                                mode: LaunchMode.externalApplication,
                              );
                            },
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
                          title: Text(
                            l10n.qqGroup,
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          subtitle: const Text('1045114341'),
                          trailing: const Icon(Icons.copy, size: 18),
                          onTap: InteractionHaptics.interceptButton(
                            () async {
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
                            },
                          ),
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

class _MenuTile extends StatelessWidget {
  const _MenuTile({
    required this.icon,
    required this.title,
    required this.onTap,
    this.isFirst = false,
    this.isLast = false,
  });

  final IconData icon;
  final String title;
  final VoidCallback? onTap;
  final bool isFirst;
  final bool isLast;

  @override
  Widget build(BuildContext context) {
    BorderRadius? borderRadius;
    if (isFirst && isLast) {
      borderRadius = BorderRadius.circular(16);
    } else if (isFirst) {
      borderRadius = const BorderRadius.vertical(top: Radius.circular(16));
    } else if (isLast) {
      borderRadius = const BorderRadius.vertical(bottom: Radius.circular(16));
    }

    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      shape: borderRadius != null
          ? RoundedRectangleBorder(borderRadius: borderRadius)
          : null,
      leading: Icon(icon),
      title: Text(title, style: Theme.of(context).textTheme.titleMedium),
      trailing: const Icon(Icons.chevron_right),
      onTap: onTap,
    );
  }
}
