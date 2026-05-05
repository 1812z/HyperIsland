import 'package:flutter/material.dart';
import '../../controllers/settings_controller.dart';
import '../../l10n/generated/app_localizations.dart';
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
          SliverAppBar(
            backgroundColor: cs.surface,
            title: Text(l10n.hookExtensionSection),
          ),
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            sliver: SliverList(
              delegate: SliverChildListDelegate(
                [
                  const SizedBox(height: 8),
                  _SectionLabel(l10n.hookScopeSystemUI),
                  const SizedBox(height: 8),
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: SwitchListTile(
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 16, vertical: 4,
                      ),
                      title: Text(l10n.unlockAllFocusTitle, style: titleStyle),
                      subtitle: Text(l10n.unlockAllFocusSubtitle),
                      value: _ctrl.unlockAllFocus,
                      onChanged: InteractionHaptics.interceptToggle(
                        (v) => _ctrl.setUnlockAllFocus(v),
                      ),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  _SectionLabel(l10n.hookScopeXMSF),
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
                            horizontal: 16, vertical: 4,
                          ),
                          title: Text(l10n.keepFocusNotifTitle, style: titleStyle),
                          subtitle: Text(l10n.keepFocusNotifSubtitle),
                          value: _ctrl.resumeNotification,
                          onChanged: InteractionHaptics.interceptToggle(
                            _onResumeNotificationChanged,
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
                            horizontal: 16, vertical: 4,
                          ),
                          title: Text(l10n.unlockFocusAuthTitle, style: titleStyle),
                          subtitle: Text(l10n.unlockFocusAuthSubtitle),
                          value: _ctrl.unlockFocusAuth,
                          onChanged: InteractionHaptics.interceptToggle(
                            (v) => _ctrl.setUnlockFocusAuth(v),
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
                  const SizedBox(height: 32),
                ],
                addAutomaticKeepAlives: false,
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