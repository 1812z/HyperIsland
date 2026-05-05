import 'package:flutter/material.dart';
import '../../controllers/settings_controller.dart';
import '../../l10n/generated/app_localizations.dart';
import '../../services/interaction_haptics.dart';

class MiscPage extends StatefulWidget {
  const MiscPage({super.key});

  @override
  State<MiscPage> createState() => _MiscPageState();
}

class _MiscPageState extends State<MiscPage> {
  final _ctrl = SettingsController.instance;
  late int _uiStateHash;

  int _buildUiStateHash() => Object.hashAll([
        _ctrl.interactionHaptics,
        _ctrl.showWelcome,
        _ctrl.hideDesktopIcon,
        _ctrl.checkUpdateOnLaunch,
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

  Future<void> _onHideDesktopIconChanged(bool value) async {
    await _ctrl.setHideDesktopIcon(value);
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
            title: Text(l10n.miscSection),
          ),
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            sliver: SliverList(
              delegate: SliverChildListDelegate(
                [
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
                          title: Text(l10n.interactionHapticsTitle,
                              style: titleStyle),
                          subtitle: Text(l10n.interactionHapticsSubtitle),
                          value: _ctrl.interactionHaptics,
                          onChanged: InteractionHaptics.interceptToggle(
                            (value) => _ctrl.setInteractionHaptics(value),
                            force: true,
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
                          title:
                              Text(l10n.showWelcomeTitle, style: titleStyle),
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
                          title: Text(l10n.hideDesktopIconTitle,
                              style: titleStyle),
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
                          title: Text(l10n.checkUpdateOnLaunchTitle,
                              style: titleStyle),
                          subtitle: Text(l10n.checkUpdateOnLaunchSubtitle),
                          value: _ctrl.checkUpdateOnLaunch,
                          onChanged: InteractionHaptics.interceptToggle(
                            (value) => _ctrl.setCheckUpdateOnLaunch(value),
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
