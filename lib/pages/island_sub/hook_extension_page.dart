import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../controllers/settings_controller.dart';
import '../../l10n/generated/app_localizations.dart';
import '../../widgets/blur_app_bar.dart';
import '../../widgets/color_picker_dialog.dart';
import '../../widgets/color_value_field.dart';
import '../../widgets/modern_slider.dart';
import '../../services/interaction_haptics.dart';

class HookExtensionPage extends StatefulWidget {
  const HookExtensionPage({super.key});

  @override
  State<HookExtensionPage> createState() => _HookExtensionPageState();
}

class _HookExtensionPageState extends State<HookExtensionPage> {
  static const _platform = MethodChannel('io.github.hyperisland/test');
  final _ctrl = SettingsController.instance;
  late int _uiStateHash;

  int _buildUiStateHash() => Object.hashAll([
    _ctrl.resumeNotification,
    _ctrl.settingsHomeEntry,
    _ctrl.bluetoothIsland,
    _ctrl.bluetoothIslandShowDeviceName,
    _ctrl.bluetoothIslandDisplayDurationSeconds,
    _ctrl.bluetoothIslandOuterGlow,
    _ctrl.bluetoothIslandOuterGlowColor,
    _ctrl.bluetoothIslandWhitelistEnabled,
    _ctrl.bluetoothIslandWhitelistAddresses.length,
    _ctrl.smoothIsland,
    _ctrl.smoothIslandSmoothing,
    _ctrl.unlockAllFocus,
    _ctrl.unlockFocusAuth,
    _ctrl.chargeIsland,
    _ctrl.chargeIslandLeftMode,
    _ctrl.chargeIslandRightMode,
    _ctrl.chargeIslandDurationMode,
    _ctrl.chargeIslandDurationSeconds,
    _ctrl.chargeIslandOuterGlow,
    _ctrl.commandTokenEnabled,
    _ctrl.commandTokenDouyinEnabled,
    _ctrl.commandTokenTimeoutSeconds,
    _ctrl.commandTokenClearClipAfterClick,
    _ctrl.commandTokenDedupWindowSeconds,
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
    if (!await _requestScopesIfEnabled(value, const [
      'com.android.providers.downloads',
      'com.xiaomi.android.app.downloadmanager',
    ])) {
      return;
    }
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
    if (!await _requestScopesIfEnabled(value, const ['com.android.settings'])) {
      return;
    }
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

  Future<void> _onUnlockAllFocusChanged(bool value) async {
    if (!await _requestScopesIfEnabled(value, const ['com.android.systemui'])) {
      return;
    }
    await _ctrl.setUnlockAllFocus(value);
  }

  Future<void> _onSmoothIslandChanged(bool value) async {
    if (!await _requestScopesIfEnabled(value, const ['com.android.systemui'])) {
      return;
    }
    await _ctrl.setSmoothIsland(value);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context)!.restartScopeApp),
          duration: const Duration(seconds: 4),
        ),
      );
    }
  }

  Future<void> _showBluetoothIslandSettings() async {
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => _BluetoothIslandSettingsDialog(
        enabled: _ctrl.bluetoothIsland,
        showDeviceName: _ctrl.bluetoothIslandShowDeviceName,
        displayDurationSeconds: _ctrl.bluetoothIslandDisplayDurationSeconds,
        outerGlow: _ctrl.bluetoothIslandOuterGlow,
        outerGlowColor: _ctrl.bluetoothIslandOuterGlowColor,
        whitelistEnabled: _ctrl.bluetoothIslandWhitelistEnabled,
        whitelistAddresses: _ctrl.bluetoothIslandWhitelistAddresses,
        onApply:
            (
              enabled,
              showDeviceName,
              displayDurationSeconds,
              outerGlow,
              outerGlowColor,
              whitelistEnabled,
              whitelistAddresses,
            ) async {
              final enabledChanged = enabled != _ctrl.bluetoothIsland;
              if (!await _requestScopesIfEnabled(enabled, const [
                'com.android.systemui',
              ])) {
                return false;
              }
              await _ctrl.setBluetoothIsland(enabled);
              await _ctrl.setBluetoothIslandShowDeviceName(showDeviceName);
              await _ctrl.setBluetoothIslandDisplayDurationSeconds(
                displayDurationSeconds,
              );
              await _ctrl.setBluetoothIslandOuterGlow(outerGlow);
              await _ctrl.setBluetoothIslandOuterGlowColor(outerGlowColor);
              await _ctrl.setBluetoothIslandWhitelistEnabled(whitelistEnabled);
              await _ctrl.setBluetoothIslandWhitelistAddresses(
                whitelistAddresses,
              );
              if (mounted && enabledChanged) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(
                      AppLocalizations.of(context)!.restartScopeApp,
                    ),
                    duration: const Duration(seconds: 4),
                  ),
                );
              }
              return true;
            },
      ),
    );
  }

  Future<void> _showChargeIslandSettings() async {
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => _ChargeIslandSettingsDialog(
        enabled: _ctrl.chargeIsland,
        leftMode: _ctrl.chargeIslandLeftMode,
        rightMode: _ctrl.chargeIslandRightMode,
        durationMode: _ctrl.chargeIslandDurationMode,
        durationSeconds: _ctrl.chargeIslandDurationSeconds,
        outerGlow: _ctrl.chargeIslandOuterGlow,
        onApply:
            (
              enabled,
              leftMode,
              rightMode,
              durationMode,
              durationSeconds,
              outerGlow,
            ) async {
              final enabledChanged = enabled != _ctrl.chargeIsland;
              if (!await _requestScopesIfEnabled(enabled, const [
                'com.android.systemui',
              ])) {
                return false;
              }
              await _ctrl.setChargeIsland(enabled);
              await _ctrl.setChargeIslandLeftMode(leftMode);
              await _ctrl.setChargeIslandRightMode(rightMode);
              await _ctrl.setChargeIslandDurationMode(durationMode);
              await _ctrl.setChargeIslandDurationSeconds(durationSeconds);
              await _ctrl.setChargeIslandOuterGlow(outerGlow);
              if (mounted && enabledChanged) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(
                      AppLocalizations.of(context)!.restartScopeApp,
                    ),
                    duration: const Duration(seconds: 4),
                  ),
                );
              }
              return true;
            },
      ),
    );
  }

  Future<void> _showCommandTokenSettings() async {
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => _CommandTokenSettingsDialog(
        enabled: _ctrl.commandTokenEnabled,
        douyinEnabled: _ctrl.commandTokenDouyinEnabled,
        timeoutSeconds: _ctrl.commandTokenTimeoutSeconds,
        dedupWindowSeconds: _ctrl.commandTokenDedupWindowSeconds,
        clearClipAfterClick: _ctrl.commandTokenClearClipAfterClick,
        onApply:
            (
              enabled,
              douyinEnabled,
              timeoutSeconds,
              dedupWindowSeconds,
              clearClipAfterClick,
            ) async {
              final enabledChanged = enabled != _ctrl.commandTokenEnabled;
              if (!await _requestScopesIfEnabled(
                enabled,
                const ['com.android.systemui'],
              )) {
                return false;
              }
              await _ctrl.setCommandTokenEnabled(enabled);
              await _ctrl.setCommandTokenDouyinEnabled(douyinEnabled);
              await _ctrl.setCommandTokenTimeoutSeconds(timeoutSeconds);
              await _ctrl.setCommandTokenDedupWindowSeconds(dedupWindowSeconds);
              await _ctrl.setCommandTokenClearClipAfterClick(
                clearClipAfterClick,
              );
              if (mounted && enabledChanged) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(
                      AppLocalizations.of(context)!.restartScopeApp,
                    ),
                    duration: const Duration(seconds: 4),
                  ),
                );
              }
              return true;
            },
      ),
    );
  }

  Future<void> _onUnlockFocusAuthChanged(bool value) async {
    if (!await _requestScopesIfEnabled(value, const ['com.xiaomi.xmsf'])) {
      return;
    }
    await _ctrl.setUnlockFocusAuth(value);
  }

  Future<bool> _requestScopesIfEnabled(
    bool enabled,
    List<String> packages,
  ) async {
    if (!enabled) return true;
    final fallbackMessage = AppLocalizations.of(
      context,
    )!.xposedScopeRequestFailed;
    try {
      await _platform.invokeMethod('requestXposedScope', {
        'packages': packages,
      });
      return true;
    } on PlatformException catch (e) {
      _showSnackBar(e.message ?? fallbackMessage);
      return false;
    } catch (_) {
      _showSnackBar(fallbackMessage);
      return false;
    }
  }

  void _showSnackBar(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message), duration: const Duration(seconds: 4)),
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
                  clipBehavior: Clip.antiAlias,
                  child: Column(
                    children: [
                      SwitchListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        title: Text(l10n.smoothIslandTitle, style: titleStyle),
                        subtitle: Text(l10n.smoothIslandSubtitle),
                        value: _ctrl.smoothIsland,
                        onChanged: InteractionHaptics.interceptToggle(
                          _onSmoothIslandChanged,
                        ),
                      ),
                      if (_ctrl.smoothIsland) ...[
                        const Divider(height: 1, indent: 16, endIndent: 16),
                        _SmoothingSliderTile(
                          title: l10n.smoothIslandSmoothingTitle,
                          value: _ctrl.smoothIslandSmoothing,
                          onChanged: (value) =>
                              _ctrl.setSmoothIslandSmoothing(value),
                        ),
                      ],
                    ],
                  ),
                ),
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
                      _onUnlockAllFocusChanged,
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  clipBehavior: Clip.antiAlias,
                  child: ListTile(
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 4,
                    ),
                    title: Text(l10n.bluetoothIslandTitle, style: titleStyle),
                    subtitle: Text(
                      l10n.bluetoothIslandSubtitle(
                        _ctrl.bluetoothIsland
                            ? l10n.bluetoothIslandStatusEnabled
                            : l10n.bluetoothIslandStatusDisabled,
                      ),
                    ),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: InteractionHaptics.interceptButton(
                      _showBluetoothIslandSettings,
                    ),
                    onLongPress: InteractionHaptics.interceptButton(
                      _showBluetoothIslandSettings,
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  clipBehavior: Clip.antiAlias,
                  child: ListTile(
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 4,
                    ),
                    title: Text(l10n.chargeIslandTitle, style: titleStyle),
                    subtitle: Text(
                      l10n.chargeIslandSubtitle(
                        _ctrl.chargeIsland
                            ? l10n.bluetoothIslandStatusEnabled
                            : l10n.bluetoothIslandStatusDisabled,
                      ),
                    ),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: InteractionHaptics.interceptButton(
                      _showChargeIslandSettings,
                    ),
                    onLongPress: InteractionHaptics.interceptButton(
                      _showChargeIslandSettings,
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  clipBehavior: Clip.antiAlias,
                  child: ListTile(
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 4,
                    ),
                    title: Text(l10n.commandTokenTitle, style: titleStyle),
                    subtitle: Text(
                      '${l10n.commandTokenSubtitle}（${_ctrl.commandTokenEnabled ? l10n.commandTokenStatusEnabled : l10n.commandTokenStatusDisabled}）',
                    ),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: InteractionHaptics.interceptButton(
                      _showCommandTokenSettings,
                    ),
                    onLongPress: InteractionHaptics.interceptButton(
                      _showCommandTokenSettings,
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
                      _onUnlockFocusAuthChanged,
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

class _SmoothingSliderTile extends StatelessWidget {
  const _SmoothingSliderTile({
    required this.title,
    required this.value,
    required this.onChanged,
  });

  final String title;
  final double value;
  final ValueChanged<double> onChanged;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final titleStyle = Theme.of(context).textTheme.titleMedium;

    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
      title: Row(
        children: [
          Expanded(child: Text(title, style: titleStyle)),
          Text(
            value.toStringAsFixed(2),
            style: Theme.of(
              context,
            ).textTheme.bodySmall?.copyWith(color: cs.onSurfaceVariant),
          ),
        ],
      ),
      subtitle: SliderTheme(
        data: ModernSliderTheme.theme(context),
        child: Slider(
          value: value.clamp(0.0, 1.0),
          min: 0,
          max: 1,
          divisions: 20,
          onChanged: InteractionHaptics.interceptSlider(onChanged),
        ),
      ),
    );
  }
}

class _BluetoothIslandSettingsDialog extends StatefulWidget {
  const _BluetoothIslandSettingsDialog({
    required this.enabled,
    required this.showDeviceName,
    required this.displayDurationSeconds,
    required this.outerGlow,
    required this.outerGlowColor,
    required this.whitelistEnabled,
    required this.whitelistAddresses,
    required this.onApply,
  });

  final bool enabled;
  final bool showDeviceName;
  final int displayDurationSeconds;
  final bool outerGlow;
  final String outerGlowColor;
  final bool whitelistEnabled;
  final List<String> whitelistAddresses;
  final Future<bool> Function(
    bool enabled,
    bool showDeviceName,
    int displayDurationSeconds,
    bool outerGlow,
    String outerGlowColor,
    bool whitelistEnabled,
    List<String> whitelistAddresses,
  )
  onApply;

  @override
  State<_BluetoothIslandSettingsDialog> createState() =>
      _BluetoothIslandSettingsDialogState();
}

class _ChargeIslandSettingsDialog extends StatefulWidget {
  const _ChargeIslandSettingsDialog({
    required this.enabled,
    required this.leftMode,
    required this.rightMode,
    required this.durationMode,
    required this.durationSeconds,
    required this.outerGlow,
    required this.onApply,
  });

  final bool enabled;
  final String leftMode;
  final String rightMode;
  final String durationMode;
  final int durationSeconds;
  final bool outerGlow;
  final Future<bool> Function(
    bool enabled,
    String leftMode,
    String rightMode,
    String durationMode,
    int durationSeconds,
    bool outerGlow,
  )
  onApply;

  @override
  State<_ChargeIslandSettingsDialog> createState() =>
      _ChargeIslandSettingsDialogState();
}

class _ChargeIslandSettingsDialogState
    extends State<_ChargeIslandSettingsDialog> {
  late bool _enabled;
  late String _leftMode;
  late String _rightMode;
  late String _durationMode;
  late bool _outerGlow;
  late final TextEditingController _durationController;

  @override
  void initState() {
    super.initState();
    _enabled = widget.enabled;
    _leftMode = widget.leftMode;
    _rightMode = widget.rightMode;
    _durationMode = widget.durationMode;
    _outerGlow = widget.outerGlow;
    _durationController = TextEditingController(
      text: widget.durationSeconds.toString(),
    );
  }

  @override
  void dispose() {
    _durationController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return AlertDialog(
      title: Text(l10n.chargeIslandSettingsTitle),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(l10n.chargeIslandEnableTitle),
              subtitle: Text(l10n.chargeIslandEnableSubtitle),
              value: _enabled,
              onChanged: (value) => setState(() => _enabled = value),
            ),
            const Divider(height: 24),
            _ChargeIslandModeDropdown(
              title: l10n.chargeIslandLeftModeTitle,
              value: _leftMode,
              onChanged: (value) => setState(() => _leftMode = value),
            ),
            const SizedBox(height: 12),
            _ChargeIslandModeDropdown(
              title: l10n.chargeIslandRightModeTitle,
              value: _rightMode,
              onChanged: (value) => setState(() => _rightMode = value),
            ),
            const SizedBox(height: 12),
            _ChargeIslandDurationDropdown(
              value: _durationMode,
              onChanged: (value) => setState(() => _durationMode = value),
            ),
            if (_durationMode == kChargeIslandDurationCustom) ...[
              const SizedBox(height: 12),
              TextField(
                controller: _durationController,
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                decoration: InputDecoration(
                  labelText: l10n.chargeIslandDurationSecondsTitle,
                  suffixText: l10n.seconds,
                  border: const OutlineInputBorder(),
                  isDense: true,
                ),
              ),
            ],
            const Divider(height: 24),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(l10n.outerGlowTitle),
              subtitle: Text(l10n.chargeIslandOuterGlowSubtitle),
              value: _outerGlow,
              onChanged: (value) => setState(() => _outerGlow = value),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: InteractionHaptics.interceptButton(() {
            Navigator.pop(context);
          }),
          child: Text(l10n.cancel),
        ),
        FilledButton(
          onPressed: InteractionHaptics.interceptButton(() async {
            final applied = await widget.onApply(
              _enabled,
              _leftMode,
              _rightMode,
              _durationMode,
              _parseDurationSeconds(),
              _outerGlow,
            );
            if (!applied) return;
            if (context.mounted) Navigator.pop(context);
          }),
          child: Text(l10n.apply),
        ),
      ],
    );
  }

  int _parseDurationSeconds() {
    final raw = int.tryParse(_durationController.text.trim()) ?? 10;
    return raw.clamp(1, 86400);
  }
}

class _ChargeIslandModeDropdown extends StatelessWidget {
  const _ChargeIslandModeDropdown({
    required this.title,
    required this.value,
    required this.onChanged,
  });

  final String title;
  final String value;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return DropdownButtonFormField<String>(
      initialValue: value,
      decoration: InputDecoration(
        labelText: title,
        border: const OutlineInputBorder(),
        isDense: true,
      ),
      items: [
        DropdownMenuItem(
          value: kChargeIslandModeDefault,
          child: Text(l10n.chargeIslandModeDefault),
        ),
        DropdownMenuItem(
          value: kChargeIslandModePower,
          child: Text(l10n.chargeIslandModePower),
        ),
        DropdownMenuItem(
          value: kChargeIslandModeVoltage,
          child: Text(l10n.chargeIslandModeVoltage),
        ),
        DropdownMenuItem(
          value: kChargeIslandModeCurrent,
          child: Text(l10n.chargeIslandModeCurrent),
        ),
        DropdownMenuItem(
          value: kChargeIslandModeLevel,
          child: Text(l10n.chargeIslandModeLevel),
        ),
        DropdownMenuItem(
          value: kChargeIslandModeTemperature,
          child: Text(l10n.chargeIslandModeTemperature),
        ),
      ],
      onChanged: (value) {
        if (value != null) onChanged(value);
      },
    );
  }
}

class _ChargeIslandDurationDropdown extends StatelessWidget {
  const _ChargeIslandDurationDropdown({
    required this.value,
    required this.onChanged,
  });

  final String value;
  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return DropdownButtonFormField<String>(
      initialValue: value,
      decoration: InputDecoration(
        labelText: l10n.chargeIslandDurationModeTitle,
        border: const OutlineInputBorder(),
        isDense: true,
      ),
      items: [
        DropdownMenuItem(
          value: kChargeIslandDurationDefault,
          child: Text(l10n.chargeIslandDurationDefault),
        ),
        DropdownMenuItem(
          value: kChargeIslandDurationCustom,
          child: Text(l10n.chargeIslandDurationCustom),
        ),
        DropdownMenuItem(
          value: kChargeIslandDurationPersistent,
          child: Text(l10n.chargeIslandDurationPersistent),
        ),
      ],
      onChanged: (value) {
        if (value != null) onChanged(value);
      },
    );
  }
}

class _BluetoothIslandSettingsDialogState
    extends State<_BluetoothIslandSettingsDialog> {
  late bool _outerGlow;
  late bool _enabled;
  late bool _showDeviceName;
  late bool _whitelistEnabled;
  late List<String> _whitelistAddresses;
  late final TextEditingController _durationController;
  late final TextEditingController _colorController;
  late String _outerGlowColor;

  static const _platform = MethodChannel('io.github.hyperisland/test');

  @override
  void initState() {
    super.initState();
    _enabled = widget.enabled;
    _showDeviceName = widget.showDeviceName;
    _outerGlow = widget.outerGlow;
    _outerGlowColor = widget.outerGlowColor;
    _whitelistEnabled = widget.whitelistEnabled;
    _whitelistAddresses = List.of(widget.whitelistAddresses);
    _durationController = TextEditingController(
      text: widget.displayDurationSeconds.toString(),
    );
    _colorController = TextEditingController(text: _outerGlowColor);
  }

  @override
  void dispose() {
    _durationController.dispose();
    _colorController.dispose();
    super.dispose();
  }

  Future<void> _showDevicePicker() async {
    final l10n = AppLocalizations.of(context)!;
    List<Map<String, String>>? devices;
    String? errorMsg;
    try {
      final raw = await _platform.invokeMethod('getPairedBluetoothDevices');
      if (raw is List) {
        devices = raw
            .whereType<Map>()
            .map((e) => Map<String, String>.from(e))
            .toList();
      }
    } on PlatformException catch (e) {
      errorMsg = e.message ?? l10n.bluetoothIslandLoadDevicesFailed;
    } catch (_) {
      errorMsg = l10n.bluetoothIslandLoadDevicesFailed;
    }
    if (!mounted) return;
    final result = await showDialog<List<String>>(
      context: context,
      builder: (ctx) => _BluetoothDevicePickerDialog(
        devices: devices,
        selectedAddresses: Set.of(_whitelistAddresses),
        errorMsg: errorMsg,
      ),
    );
    if (result != null) {
      setState(() => _whitelistAddresses = result);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
    return AlertDialog(
      title: Text(l10n.bluetoothIslandSettingsTitle),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(l10n.bluetoothIslandEnableTitle),
              subtitle: Text(l10n.bluetoothIslandEnableSubtitle),
              value: _enabled,
              onChanged: (value) => setState(() => _enabled = value),
            ),
            const SizedBox(height: 12),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(l10n.bluetoothIslandShowDeviceNameTitle),
              subtitle: Text(l10n.bluetoothIslandShowDeviceNameSubtitle),
              value: _showDeviceName,
              onChanged: (value) => setState(() => _showDeviceName = value),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _durationController,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              decoration: InputDecoration(
                labelText: l10n.bluetoothIslandDisplayDurationTitle,
                suffixText: l10n.seconds,
                border: const OutlineInputBorder(),
                isDense: true,
              ),
            ),
            const Divider(height: 24),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(l10n.bluetoothIslandWhitelistTitle),
              subtitle: Text(l10n.bluetoothIslandWhitelistSubtitle),
              value: _whitelistEnabled,
              onChanged: (value) => setState(() => _whitelistEnabled = value),
            ),
            if (_whitelistEnabled) ...[
              const SizedBox(height: 8),
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.devices),
                title: Text(l10n.bluetoothIslandWhitelistButton),
                subtitle: Text(
                  l10n.bluetoothIslandWhitelistButtonSubtitle(
                    _whitelistAddresses.length,
                  ),
                ),
                trailing: const Icon(Icons.chevron_right),
                onTap: _showDevicePicker,
              ),
            ] else ...[
              const SizedBox(height: 4),
              Padding(
                padding: const EdgeInsets.only(left: 4),
                child: Text(
                  l10n.bluetoothIslandWhitelistAllHint,
                  style: Theme.of(
                    context,
                  ).textTheme.bodySmall?.copyWith(color: cs.onSurfaceVariant),
                ),
              ),
            ],
            const Divider(height: 24),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(l10n.outerGlowTitle),
              subtitle: Text(l10n.bluetoothIslandOuterGlowSubtitle),
              value: _outerGlow,
              onChanged: (value) => setState(() => _outerGlow = value),
            ),
            const SizedBox(height: 12),
            Text(
              l10n.outerGlowColorTitle,
              style: Theme.of(
                context,
              ).textTheme.bodyMedium?.copyWith(color: cs.onSurfaceVariant),
            ),
            const SizedBox(height: 6),
            ColorValueField(
              controller: _colorController,
              enabled: _outerGlow,
              decoration: const InputDecoration(
                border: OutlineInputBorder(),
                isDense: true,
                hintText: '#AARRGGBB',
              ),
              previewColor: parseHexColor(_outerGlowColor),
              previewFallbackColor: cs.primary,
              onChanged: (value) => setState(() {
                _outerGlowColor = value.trim();
              }),
              onClear: () {
                _colorController.clear();
                setState(() => _outerGlowColor = '');
              },
              onPickColor: () async {
                final color = await showColorPickerDialog(
                  context,
                  initialHex: _outerGlowColor,
                  title: l10n.outerGlowColorTitle,
                  enableAlpha: true,
                );
                if (color == null) return;
                final hex = colorToArgbHex(color);
                _colorController.text = hex;
                setState(() => _outerGlowColor = hex);
              },
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: InteractionHaptics.interceptButton(() {
            Navigator.pop(context);
          }),
          child: Text(AppLocalizations.of(context)!.cancel),
        ),
        FilledButton(
          onPressed: InteractionHaptics.interceptButton(() async {
            final applied = await widget.onApply(
              _enabled,
              _showDeviceName,
              _parseDurationSeconds(),
              _outerGlow,
              _outerGlowColor,
              _whitelistEnabled,
              _whitelistAddresses,
            );
            if (!applied) return;
            if (context.mounted) Navigator.pop(context);
          }),
          child: Text(AppLocalizations.of(context)!.apply),
        ),
      ],
    );
  }

  int _parseDurationSeconds() {
    final raw = int.tryParse(_durationController.text.trim()) ?? 2;
    return raw.clamp(1, 86400);
  }
}

class _BluetoothDevicePickerDialog extends StatefulWidget {
  const _BluetoothDevicePickerDialog({
    required this.devices,
    required this.selectedAddresses,
    this.errorMsg,
  });

  final List<Map<String, String>>? devices;
  final Set<String> selectedAddresses;
  final String? errorMsg;

  @override
  State<_BluetoothDevicePickerDialog> createState() =>
      _BluetoothDevicePickerDialogState();
}

class _BluetoothDevicePickerDialogState
    extends State<_BluetoothDevicePickerDialog> {
  late Set<String> _selected;

  @override
  void initState() {
    super.initState();
    _selected = Set.of(widget.selectedAddresses);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return AlertDialog(
      title: Text(l10n.bluetoothIslandWhitelistDialogTitle),
      content: SizedBox(width: double.maxFinite, child: _buildContent(l10n)),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(l10n.cancel),
        ),
        FilledButton(
          onPressed: () => Navigator.pop(context, _selected.toList()),
          child: Text(l10n.confirm),
        ),
      ],
    );
  }

  Widget _buildContent(AppLocalizations l10n) {
    if (widget.errorMsg != null) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 16),
        child: Text(widget.errorMsg!),
      );
    }
    final devices = widget.devices;
    if (devices == null || devices.isEmpty) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 16),
        child: Text(l10n.bluetoothIslandWhitelistEmpty),
      );
    }
    return SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: devices.map((device) {
          final addr = device['address'] ?? '';
          final name = device['name'] ?? addr;
          return CheckboxListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(name, overflow: TextOverflow.ellipsis),
            subtitle: addr != name ? Text(addr) : null,
            value: _selected.contains(addr),
            onChanged: (checked) {
              setState(() {
                if (checked == true) {
                  _selected.add(addr);
                } else {
                  _selected.remove(addr);
                }
              });
            },
          );
        }).toList(),
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

class _CommandTokenSettingsDialog extends StatefulWidget {
  const _CommandTokenSettingsDialog({
    required this.enabled,
    required this.douyinEnabled,
    required this.timeoutSeconds,
    required this.dedupWindowSeconds,
    required this.clearClipAfterClick,
    required this.onApply,
  });

  final bool enabled;
  final bool douyinEnabled;
  final int timeoutSeconds;
  final int dedupWindowSeconds;
  final bool clearClipAfterClick;
  final Future<bool> Function(
    bool enabled,
    bool douyinEnabled,
    int timeoutSeconds,
    int dedupWindowSeconds,
    bool clearClipAfterClick,
  )
  onApply;

  @override
  State<_CommandTokenSettingsDialog> createState() =>
      _CommandTokenSettingsDialogState();
}

class _CommandTokenSettingsDialogState
    extends State<_CommandTokenSettingsDialog> {
  late bool _enabled;
  late bool _douyinEnabled;
  late bool _clearClipAfterClick;
  late final TextEditingController _timeoutController;
  late final TextEditingController _dedupController;

  @override
  void initState() {
    super.initState();
    _enabled = widget.enabled;
    _douyinEnabled = widget.douyinEnabled;
    _clearClipAfterClick = widget.clearClipAfterClick;
    _timeoutController = TextEditingController(
      text: widget.timeoutSeconds.toString(),
    );
    _dedupController = TextEditingController(
      text: widget.dedupWindowSeconds.toString(),
    );
  }

  @override
  void dispose() {
    _timeoutController.dispose();
    _dedupController.dispose();
    super.dispose();
  }

  int _parseTimeout() {
    final raw = int.tryParse(_timeoutController.text.trim()) ?? 8;
    return raw.clamp(1, 30);
  }

  int _parseDedup() {
    final raw = int.tryParse(_dedupController.text.trim()) ?? 30;
    return raw.clamp(5, 300);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return AlertDialog(
      title: Text(l10n.commandTokenSettingsTitle),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(l10n.commandTokenEnableTitle),
              subtitle: Text(l10n.commandTokenEnableSubtitle),
              value: _enabled,
              onChanged: (value) => setState(() => _enabled = value),
            ),
            if (_enabled) ...[
              const Divider(height: 24),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(l10n.commandTokenDouyinEnableTitle),
                subtitle: Text(l10n.commandTokenDouyinEnableSubtitle),
                value: _douyinEnabled,
                onChanged: (value) => setState(() => _douyinEnabled = value),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: _timeoutController,
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                decoration: InputDecoration(
                  labelText: l10n.commandTokenTimeoutTitle,
                  suffixText: l10n.seconds,
                  border: const OutlineInputBorder(),
                  isDense: true,
                ),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: _dedupController,
                keyboardType: TextInputType.number,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                decoration: InputDecoration(
                  labelText: l10n.commandTokenDedupTitle,
                  suffixText: l10n.seconds,
                  border: const OutlineInputBorder(),
                  isDense: true,
                ),
              ),
              const SizedBox(height: 16),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(l10n.commandTokenClearClipTitle),
                subtitle: Text(l10n.commandTokenClearClipSubtitle),
                value: _clearClipAfterClick,
                onChanged: (value) =>
                    setState(() => _clearClipAfterClick = value),
              ),
            ],
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: InteractionHaptics.interceptButton(() {
            Navigator.pop(context);
          }),
          child: Text(l10n.cancel),
        ),
        FilledButton(
          onPressed: InteractionHaptics.interceptButton(() async {
            final applied = await widget.onApply(
              _enabled,
              _douyinEnabled,
              _parseTimeout(),
              _parseDedup(),
              _clearClipAfterClick,
            );
            if (!applied) return;
            if (context.mounted) Navigator.pop(context);
          }),
          child: Text(l10n.apply),
        ),
      ],
    );
  }
}
