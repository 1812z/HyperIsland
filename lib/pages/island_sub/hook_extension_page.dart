import 'package:flutter/material.dart';
import '../../controllers/settings_controller.dart';
import '../../l10n/generated/app_localizations.dart';
import '../../widgets/blur_app_bar.dart';
import '../../services/interaction_haptics.dart';

class HookExtensionPage extends StatefulWidget {
  const HookExtensionPage({super.key});

  @override
  State<HookExtensionPage> createState() => _HookExtensionPageState();
}

class _HookExtensionPageState extends State<HookExtensionPage> {
  final _ctrl = SettingsController.instance;
  late int _uiStateHash;

  int _buildUiStateHash() => Object.hashAll([
    _ctrl.resumeNotification,
    _ctrl.settingsHomeEntry,
    _ctrl.unlockAllFocus,
    _ctrl.unlockFocusAuth,
  ]);

  void _onChanged() {
    if (!mounted) return;
    final nextHash = _buildUiStateHash();
    if (nextHash == _uiStateHash) return;
    setState(() => _uiStateHash = nextHash);
  }

  @override
  void initState() {
    super.initState();
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

  Future<void> _onSettingsHomeEntryChanged(bool value) async {
    await _ctrl.setSettingsHomeEntry(value);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context)!.restartScopeApp),
          duration: const Duration(seconds: 4),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
    final titleStyle = Theme.of(context).textTheme.titleMedium;

    return Scaffold(
      backgroundColor: cs.surface,
      body: BlurAppBarHost(
        title: l10n.hookExtensionSection,
        physics: const ClampingScrollPhysics(),
        slivers: [
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                const SizedBox(height: 8),
                _SectionLabel(l10n.hookScopeSettings),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  child: SwitchListTile(
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 4,
                    ),
                    title: Text(l10n.settingsHomeEntryTitle, style: titleStyle),
                    subtitle: Text(l10n.settingsHomeEntrySubtitle),
                    value: _ctrl.settingsHomeEntry,
                    onChanged: InteractionHaptics.interceptToggle(
                      _onSettingsHomeEntryChanged,
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                _SectionLabel(l10n.hookScopeSystemUI),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  child: SwitchListTile(
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 4,
                    ),
                    title: Text(l10n.unlockAllFocusTitle, style: titleStyle),
                    subtitle: Text(l10n.unlockAllFocusSubtitle),
                    value: _ctrl.unlockAllFocus,
                    onChanged: InteractionHaptics.interceptToggle(
                      (v) => _ctrl.setUnlockAllFocus(v),
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                _SectionLabel(l10n.hookScopeXMSF),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  child: SwitchListTile(
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 4,
                    ),
                    title: Text(l10n.unlockFocusAuthTitle, style: titleStyle),
                    subtitle: Text(l10n.unlockFocusAuthSubtitle),
                    value: _ctrl.unlockFocusAuth,
                    onChanged: InteractionHaptics.interceptToggle(
                      (v) => _ctrl.setUnlockFocusAuth(v),
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                _SectionLabel(l10n.downloadManagerSection),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  child: SwitchListTile(
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 4,
                    ),
                    title: Text(l10n.keepFocusNotifTitle, style: titleStyle),
                    subtitle: Text(l10n.keepFocusNotifSubtitle),
                    value: _ctrl.resumeNotification,
                    onChanged: InteractionHaptics.interceptToggle(
                      _onResumeNotificationChanged,
                    ),
                  ),
                ),
                const SizedBox(height: 32),
              ], addAutomaticKeepAlives: false),
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
