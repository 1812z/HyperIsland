import 'package:flutter/material.dart';
import '../../controllers/settings_controller.dart';
import '../../controllers/whitelist_controller.dart';
import '../../l10n/generated/app_localizations.dart';
import '../../services/interaction_haptics.dart';
import '../../widgets/blur_app_bar.dart';
import '../../widgets/color_picker_dialog.dart';
import '../../widgets/color_value_field.dart';

class DefaultConfigPage extends StatefulWidget {
  const DefaultConfigPage({super.key});

  @override
  State<DefaultConfigPage> createState() => _DefaultConfigPageState();
}

class _DefaultConfigPageState extends State<DefaultConfigPage> {
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

  InputDecoration _dialogFieldDecoration(
    BuildContext context, {
    String? hintText,
  }) {
    return InputDecoration(
      hintText: hintText,
      isDense: true,
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
    );
  }

  List<DropdownMenuItem<String>> _outerGlowModeItems(AppLocalizations l10n) {
    return [
      DropdownMenuItem<String>(value: kTriOptOn, child: Text(l10n.optOn)),
      DropdownMenuItem<String>(value: kTriOptOff, child: Text(l10n.optOff)),
      DropdownMenuItem<String>(
        value: kTriOptFollowDynamic,
        child: Text(l10n.followDynamicColorLabel),
      ),
    ];
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
    var focusForceOuterGlow =
        focusOuterGlow != kTriOptOff && _ctrl.defaultForceOuterGlow;
    var islandForceOuterGlow =
        islandOuterGlow != kTriOptOff && _ctrl.defaultForceIslandOuterGlow;
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
                  items: _outerGlowModeItems(l10n),
                  onChanged: (value) {
                    if (value != null) {
                      setDialogState(() {
                        focusOuterGlow = value;
                        if (value == kTriOptOff) focusForceOuterGlow = false;
                      });
                    }
                  },
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(l10n.forceOuterGlowLabel),
                  subtitle: Text(l10n.forceFocusOuterGlowSubtitle),
                  value: focusOuterGlow != kTriOptOff && focusForceOuterGlow,
                  onChanged: focusOuterGlow == kTriOptOff
                      ? null
                      : (value) =>
                            setDialogState(() => focusForceOuterGlow = value),
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
                  items: _outerGlowModeItems(l10n),
                  onChanged: (value) {
                    if (value != null) {
                      setDialogState(() {
                        islandOuterGlow = value;
                        if (value == kTriOptOff) islandForceOuterGlow = false;
                      });
                    }
                  },
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(l10n.forceOuterGlowLabel),
                  subtitle: Text(l10n.forceIslandOuterGlowSubtitle),
                  value: islandOuterGlow != kTriOptOff && islandForceOuterGlow,
                  onChanged: islandOuterGlow == kTriOptOff
                      ? null
                      : (value) =>
                            setDialogState(() => islandForceOuterGlow = value),
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
    await _ctrl.setDefaultForceOuterGlow(focusForceOuterGlow);
    await _ctrl.setDefaultForceIslandOuterGlow(islandForceOuterGlow);
    await _ctrl.setDefaultOutEffectColor(focusColor);
    await _ctrl.setDefaultIslandOuterGlowColor(islandColor);
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
    final titleStyle = Theme.of(context).textTheme.titleMedium;

    return Scaffold(
      backgroundColor: cs.surface,
      body: BlurAppBarHost(
        title: l10n.defaultConfigSection,
        physics: const ClampingScrollPhysics(),
        slivers: [
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
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
                        onChanged: InteractionHaptics.interceptToggle(
                          (v) => _ctrl.setDefaultFirstFloat(v),
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
                        title: Text(l10n.aodTextSwitchLabel, style: titleStyle),
                        subtitle: const Text('开启后息屏显示显示通知文本'),
                        value: _ctrl.defaultAodText,
                        onChanged: InteractionHaptics.interceptToggle(
                          (v) => _ctrl.setDefaultAodText(v),
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
                        onChanged: InteractionHaptics.interceptToggle(
                          (v) => _ctrl.setDefaultEnableFloat(v),
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
                          (v) => _ctrl.setDefaultMarquee(v),
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
                        subtitle: Text(l10n.dynamicHighlightColorLabelSubtitle),
                        value: _ctrl.defaultDynamicHighlightColor,
                        onChanged: InteractionHaptics.interceptToggle(
                          (v) => _ctrl.setDefaultDynamicHighlightColor(v),
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
                          (v) => _ctrl.setDefaultFocusNotif(v),
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
                          (v) => _ctrl.setDefaultRestoreLockscreen(v),
                        ),
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
                        onChanged: InteractionHaptics.interceptToggle(
                          (v) => _ctrl.setDefaultShowIslandIcon(v),
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
                          (v) => _ctrl.setDefaultPreserveSmallIcon(v),
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
              ], addAutomaticKeepAlives: false),
            ),
          ),
        ],
      ),
    );
  }
}
