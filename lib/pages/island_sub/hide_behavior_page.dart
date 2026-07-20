import 'package:flutter/material.dart';

import '../../controllers/settings_controller.dart';
import '../../l10n/generated/app_localizations.dart';
import '../../services/interaction_haptics.dart';
import '../../widgets/blur_app_bar.dart';

class HideBehaviorPage extends StatefulWidget {
  const HideBehaviorPage({super.key});

  @override
  State<HideBehaviorPage> createState() => _HideBehaviorPageState();
}

class _HideBehaviorPageState extends State<HideBehaviorPage> {
  final _ctrl = SettingsController.instance;

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

  Future<void> _onTempHideBehaviorEnabledChanged(bool value) async {
    await _ctrl.setTempHideBehaviorEnabled(value);
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(AppLocalizations.of(context)!.restartScopeApp),
        duration: const Duration(seconds: 4),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
    final titleStyle = Theme.of(context).textTheme.titleMedium;

    return Scaffold(
      backgroundColor: cs.surface,
      body: BlurAppBarHost(
        title: l10n.hideBehaviorTitle,
        physics: const ClampingScrollPhysics(),
        slivers: [
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                const SizedBox(height: 8),
                Text(
                  l10n.hideBehaviorDescription,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: cs.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 12),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  child: _SwitchTile(
                    title: l10n.hideBehaviorMasterSwitch,
                    subtitle: l10n.hideBehaviorMasterSwitchSubtitle,
                    value: _ctrl.tempHideBehaviorEnabled,
                    onChanged: _onTempHideBehaviorEnabledChanged,
                    titleStyle: titleStyle,
                  ),
                ),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  clipBehavior: Clip.antiAlias,
                  child: Column(
                    children: [
                      _SwitchTile(
                        title: l10n.hideBehaviorScreenPinning,
                        subtitle: l10n.hideBehaviorScreenPinningSubtitle,
                        value: _ctrl.tempHideScreenPinning,
                        onChanged: _ctrl.tempHideBehaviorEnabled
                            ? _ctrl.setTempHideScreenPinning
                            : null,
                        titleStyle: titleStyle,
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _SwitchTile(
                        title: l10n.hideBehaviorBouncerShowing,
                        subtitle: l10n.hideBehaviorBouncerShowingSubtitle,
                        value: _ctrl.tempHideBouncerShowing,
                        onChanged: _ctrl.tempHideBehaviorEnabled
                            ? _ctrl.setTempHideBouncerShowing
                            : null,
                        titleStyle: titleStyle,
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _SwitchTile(
                        title: l10n.hideBehaviorFullscreen,
                        subtitle: l10n.hideBehaviorFullscreenSubtitle,
                        value: _ctrl.tempHideFullscreen,
                        onChanged: _ctrl.tempHideBehaviorEnabled
                            ? _ctrl.setTempHideFullscreen
                            : null,
                        titleStyle: titleStyle,
                      ),
                      _SwitchTile(
                        title: l10n.hideBehaviorFullscreenLandscapeDisable,
                        subtitle:
                            l10n.hideBehaviorFullscreenLandscapeDisableSubtitle,
                        value: _ctrl.tempHideFullscreenLandscapeDisable,
                        onChanged: _ctrl.tempHideBehaviorEnabled &&
                                !_ctrl.tempHideFullscreen
                            ? _ctrl.setTempHideFullscreenLandscapeDisable
                            : null,
                        titleStyle: titleStyle,
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _SwitchTile(
                        title: l10n.hideBehaviorScreenLocked,
                        subtitle: l10n.hideBehaviorScreenLockedSubtitle,
                        value: _ctrl.tempHideScreenLocked,
                        onChanged: _ctrl.tempHideBehaviorEnabled
                            ? _ctrl.setTempHideScreenLocked
                            : null,
                        titleStyle: titleStyle,
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _SwitchTile(
                        title: l10n.hideBehaviorNotificationCenter,
                        subtitle: l10n.hideBehaviorNotificationCenterSubtitle,
                        value: _ctrl.tempHideNotificationCenter,
                        onChanged: _ctrl.tempHideBehaviorEnabled
                            ? _ctrl.setTempHideNotificationCenter
                            : null,
                        titleStyle: titleStyle,
                      ),
                    ],
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

class _SwitchTile extends StatelessWidget {
  const _SwitchTile({
    required this.title,
    required this.subtitle,
    required this.value,
    required this.onChanged,
    required this.titleStyle,
  });

  final String title;
  final String subtitle;
  final bool value;
  final ValueChanged<bool>? onChanged;
  final TextStyle? titleStyle;

  @override
  Widget build(BuildContext context) {
    return SwitchListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      title: Text(title, style: titleStyle),
      subtitle: Text(subtitle),
      value: value,
      onChanged: onChanged == null
          ? null
          : InteractionHaptics.interceptToggle(onChanged!),
    );
  }
}
