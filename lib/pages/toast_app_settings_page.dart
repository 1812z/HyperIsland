import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../controllers/settings_controller.dart';
import '../controllers/whitelist_controller.dart';
import '../l10n/generated/app_localizations.dart';
import '../services/app_cache_service.dart';
import '../widgets/color_picker_dialog.dart';
import '../widgets/color_value_field.dart';
import '../widgets/toast_settings_panel.dart';

class ToastAppSettingsPage extends StatefulWidget {
  const ToastAppSettingsPage({
    super.key,
    required this.app,
    required this.controller,
  });

  final AppInfo app;
  final WhitelistController controller;

  @override
  State<ToastAppSettingsPage> createState() => _ToastAppSettingsPageState();
}

class _ToastAppSettingsPageState extends State<ToastAppSettingsPage> {
  SettingsController get _ctrl => SettingsController.instance;

  bool _loading = true;
  bool _forwardEnabled = false;
  bool _blockOriginal = false;
  bool _showNotification = false;
  bool _showIslandIcon = true;

  String _firstFloat = kTriOptDefault;
  String _marquee = kTriOptDefault;
  String _timeout = '5';
  String _highlightColor = '';
  String _outEffectColor = '';
  String _islandOuterGlow = kTriOptDefault;
  String _islandOuterGlowColor = '';
  String _dynamicHighlightColor = kTriOptDefault;
  bool _showLeftHighlight = false;
  bool _showRightHighlight = false;
  String _outerGlow = kTriOptDefault;

  late final TextEditingController _timeoutController;
  late final TextEditingController _highlightColorController;
  late final TextEditingController _outEffectColorController;
  late final TextEditingController _islandOuterGlowColorController;

  bool get _dynamicHighlightEnabled {
    return resolvesDynamicColorMode(
      _dynamicHighlightColor,
      _ctrl.defaultDynamicHighlightColor,
    );
  }

  bool get _hasHighlightSource {
    return _dynamicHighlightEnabled || _highlightColor.trim().isNotEmpty;
  }

  bool get _outerGlowFollowDynamic {
    return _outerGlow == kTriOptFollowDynamic ||
        (_outerGlow == kTriOptDefault &&
            _ctrl.defaultOuterGlow == kTriOptFollowDynamic);
  }

  bool get _islandOuterGlowFollowDynamic {
    return _islandOuterGlow == kTriOptFollowDynamic ||
        (_islandOuterGlow == kTriOptDefault &&
            _ctrl.defaultIslandOuterGlow == kTriOptFollowDynamic);
  }

  @override
  void initState() {
    super.initState();
    _timeoutController = TextEditingController();
    _highlightColorController = TextEditingController();
    _outEffectColorController = TextEditingController();
    _islandOuterGlowColorController = TextEditingController();
    _load();
  }

  @override
  void dispose() {
    _timeoutController.dispose();
    _highlightColorController.dispose();
    _outEffectColorController.dispose();
    _islandOuterGlowColorController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final pkg = widget.app.packageName;
    final forwardEnabledFuture = widget.controller.getToastForwardEnabled(pkg);
    final blockOriginalFuture = widget.controller.getToastBlockOriginal(pkg);
    final showNotificationFuture = widget.controller.getToastShowNotification(
      pkg,
    );
    final showIslandIconFuture = widget.controller.getToastShowIslandIcon(pkg);
    final firstFloatFuture = widget.controller.getToastFirstFloat(pkg);
    final marqueeFuture = widget.controller.getToastMarquee(pkg);
    final timeoutFuture = widget.controller.getToastTimeout(pkg);
    final highlightColorFuture = widget.controller.getToastHighlightColor(pkg);
    final dynamicHighlightColorFuture = widget.controller
        .getToastDynamicHighlightColor(pkg);
    final showLeftHighlightFuture = widget.controller.getToastShowLeftHighlight(
      pkg,
    );
    final showRightHighlightFuture = widget.controller
        .getToastShowRightHighlight(pkg);
    final outerGlowFuture = widget.controller.getToastOuterGlow(pkg);
    final outEffectColorFuture = widget.controller.getToastOutEffectColor(pkg);
    final islandOuterGlowFuture = widget.controller.getToastIslandOuterGlow(
      pkg,
    );
    final islandOuterGlowColorFuture = widget.controller
        .getToastIslandOuterGlowColor(pkg);

    final forwardEnabled = await forwardEnabledFuture;
    final blockOriginal = await blockOriginalFuture;
    final showNotification = await showNotificationFuture;
    final showIslandIcon = await showIslandIconFuture;
    final firstFloat = await firstFloatFuture;
    final marquee = await marqueeFuture;
    final timeout = await timeoutFuture;
    final highlightColor = await highlightColorFuture;
    final dynamicHighlightColor = await dynamicHighlightColorFuture;
    final showLeftHighlight = await showLeftHighlightFuture;
    final showRightHighlight = await showRightHighlightFuture;
    final outerGlow = await outerGlowFuture;
    final outEffectColor = await outEffectColorFuture;
    final islandOuterGlow = await islandOuterGlowFuture;
    final islandOuterGlowColor = await islandOuterGlowColorFuture;

    await widget.controller.setToastEnableFloat(pkg, kTriOptOff);
    await widget.controller.setToastPreserveSmallIcon(pkg, kTriOptOff);

    if (!mounted) return;
    setState(() {
      _forwardEnabled = forwardEnabled;
      _blockOriginal = blockOriginal;
      _showNotification = showNotification;
      _showIslandIcon = showIslandIcon;
      _firstFloat = firstFloat;
      _marquee = marquee;
      _timeout = timeout;
      _highlightColor = highlightColor;
      _dynamicHighlightColor = dynamicHighlightColor;
      _showLeftHighlight = showLeftHighlight == kTriOptOn;
      _showRightHighlight = showRightHighlight == kTriOptOn;
      _outerGlow = outerGlow;
      _outEffectColor = outEffectColor;
      _islandOuterGlow = islandOuterGlow;
      _islandOuterGlowColor = islandOuterGlowColor;
      _timeoutController.text = timeout;
      _highlightColorController.text = highlightColor;
      _outEffectColorController.text = outEffectColor;
      _islandOuterGlowColorController.text = islandOuterGlowColor;
      _loading = false;
    });
  }

  Future<void> _setForwardEnabled(bool value) async {
    setState(() => _forwardEnabled = value);
    await widget.controller.setToastForwardEnabled(
      widget.app.packageName,
      value,
    );
    if (!value && _blockOriginal) {
      setState(() => _blockOriginal = false);
      await widget.controller.setToastBlockOriginal(
        widget.app.packageName,
        false,
      );
    }
  }

  Future<void> _setBlockOriginal(bool value) async {
    setState(() => _blockOriginal = value);
    await widget.controller.setToastBlockOriginal(
      widget.app.packageName,
      value,
    );
  }

  Future<void> _setShowNotification(bool value) async {
    if (!_forwardEnabled && value) return;
    setState(() => _showNotification = value);
    await widget.controller.setToastShowNotification(
      widget.app.packageName,
      value,
    );
  }

  Future<void> _setShowIslandIcon(bool value) async {
    if (!_forwardEnabled) return;
    setState(() => _showIslandIcon = value);
    await widget.controller.setToastShowIslandIcon(
      widget.app.packageName,
      value,
    );
  }

  Future<void> _setFirstFloat(String value) async {
    setState(() => _firstFloat = value);
    await widget.controller.setToastFirstFloat(widget.app.packageName, value);
  }

  Future<void> _setMarquee(String value) async {
    setState(() => _marquee = value);
    await widget.controller.setToastMarquee(widget.app.packageName, value);
  }

  Future<void> _persistTimeout(String raw) async {
    final trimmed = raw.trim();
    final n = int.tryParse(trimmed);
    if (n == null || n < 1 || n > 20) return;
    final value = n.toString();
    if (value == _timeout) return;
    setState(() => _timeout = value);
    await widget.controller.setToastTimeout(widget.app.packageName, value);
  }

  Future<void> _setHighlightColor(String value) async {
    final normalized = value.trim();
    if (normalized == _highlightColor) return;
    setState(() => _highlightColor = normalized);
    await widget.controller.setToastHighlightColor(
      widget.app.packageName,
      normalized,
    );
  }

  Future<void> _setDynamicHighlightColor(String value) async {
    setState(() => _dynamicHighlightColor = value);
    await widget.controller.setToastDynamicHighlightColor(
      widget.app.packageName,
      value,
    );
  }

  Future<void> _setShowLeftHighlight(bool value) async {
    setState(() => _showLeftHighlight = value);
    await widget.controller.setToastShowLeftHighlight(
      widget.app.packageName,
      value ? kTriOptOn : kTriOptOff,
    );
  }

  Future<void> _setShowRightHighlight(bool value) async {
    setState(() => _showRightHighlight = value);
    await widget.controller.setToastShowRightHighlight(
      widget.app.packageName,
      value ? kTriOptOn : kTriOptOff,
    );
  }

  Future<void> _setOuterGlow(String value) async {
    setState(() => _outerGlow = value);
    await widget.controller.setToastOuterGlow(widget.app.packageName, value);
  }

  Future<void> _setOutEffectColor(String value) async {
    final normalized = value.trim();
    if (normalized == _outEffectColor) return;
    setState(() => _outEffectColor = normalized);
    await widget.controller.setToastOutEffectColor(
      widget.app.packageName,
      normalized,
    );
  }

  Future<void> _setIslandOuterGlow(String value) async {
    setState(() => _islandOuterGlow = value);
    await widget.controller.setToastIslandOuterGlow(
      widget.app.packageName,
      value,
    );
  }

  Future<void> _setIslandOuterGlowColor(String value) async {
    final normalized = value.trim();
    if (normalized == _islandOuterGlowColor) return;
    setState(() => _islandOuterGlowColor = normalized);
    await widget.controller.setToastIslandOuterGlowColor(
      widget.app.packageName,
      normalized,
    );
  }

  String _defaultLabel(BuildContext context, bool enabled) {
    final l10n = AppLocalizations.of(context)!;
    return enabled ? l10n.optDefaultOn : l10n.optDefaultOff;
  }

  Color? _parseColor(String? hex) => parseHexColor(hex);

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final cs = Theme.of(context).colorScheme;
    final controlsEnabled = _forwardEnabled;

    return Scaffold(
      backgroundColor: cs.surface,
      appBar: AppBar(title: Text(widget.app.appName)),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
              children: [
                ToastSettingsPanel(
                  forwardEnabled: _forwardEnabled,
                  blockOriginal: _blockOriginal,
                  showNotification: _showNotification,
                  showIslandIcon: _showIslandIcon,
                  onForwardEnabledChanged: _setForwardEnabled,
                  onBlockOriginalChanged: _setBlockOriginal,
                  onShowNotificationChanged: _setShowNotification,
                  onShowIslandIconChanged: _setShowIslandIcon,
                  allowIndependentBlockOriginal: true,
                ),
                const SizedBox(height: 12),
                _SectionCard(
                  title: l10n.islandSection,
                  children: [
                    _TriOptSegmented(
                      label: l10n.firstFloatLabel,
                      subtitle: l10n.firstFloatLabelSubtitle,
                      value: _firstFloat,
                      enabled: controlsEnabled,
                      defaultLabel: _defaultLabel(
                        context,
                        _ctrl.defaultFirstFloat,
                      ),
                      onChanged: _setFirstFloat,
                    ),
                    const SizedBox(height: 10),
                    _TriOptSegmented(
                      label: l10n.marqueeChannelTitle,
                      subtitle: l10n.marqueeChannelTitleSubtitle,
                      value: _marquee,
                      enabled: controlsEnabled,
                      defaultLabel: _defaultLabel(
                        context,
                        _ctrl.defaultMarquee,
                      ),
                      onChanged: _setMarquee,
                    ),
                    const SizedBox(height: 10),
                    _SettingField(
                      label: l10n.autoDisappear,
                      child: TextFormField(
                        controller: _timeoutController,
                        enabled: controlsEnabled,
                        keyboardType: TextInputType.number,
                        inputFormatters: [
                          FilteringTextInputFormatter.digitsOnly,
                        ],
                        textInputAction: TextInputAction.done,
                        onTapOutside: (_) {
                          FocusManager.instance.primaryFocus?.unfocus();
                          _persistTimeout(_timeoutController.text);
                        },
                        onFieldSubmitted: _persistTimeout,
                        decoration: _fieldDecoration(
                          context,
                          suffixText: l10n.seconds,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                _SectionCard(
                  title: l10n.highlightColorLabel,
                  children: [
                    _SettingField(
                      label: l10n.highlightColorLabel,
                      child: ColorValueField(
                        controller: _highlightColorController,
                        enabled: controlsEnabled && !_dynamicHighlightEnabled,
                        readOnly: _dynamicHighlightEnabled,
                        decoration: _fieldDecoration(
                          context,
                          hintText: l10n.highlightColorHint,
                        ),
                        previewColor: _parseColor(_highlightColor),
                        previewFallbackColor: cs.primary,
                        onChanged: _setHighlightColor,
                        onClear: () {
                          _highlightColorController.clear();
                          _setHighlightColor('');
                        },
                        onPickColor: () async {
                          final color = await showColorPickerDialog(
                            context,
                            initialHex: _highlightColor,
                            title: l10n.highlightColorLabel,
                            enableAlpha: true,
                          );
                          if (color == null) return;
                          final hex = colorToArgbHex(color);
                          _highlightColorController.text = hex;
                          await _setHighlightColor(hex);
                        },
                      ),
                    ),
                    const SizedBox(height: 10),
                    _SettingField(
                      label:
                          '${l10n.dynamicHighlightColorLabel}\n${l10n.dynamicHighlightColorLabelSubtitle}',
                      child: DropdownButtonFormField<String>(
                        initialValue: _dynamicHighlightColor,
                        decoration: _fieldDecoration(context),
                        items: [
                          DropdownMenuItem(
                            value: kTriOptDefault,
                            child: Text(
                              _defaultLabel(
                                context,
                                _ctrl.defaultDynamicHighlightColor,
                              ),
                            ),
                          ),
                          DropdownMenuItem(
                            value: kTriOptOff,
                            child: Text(l10n.optOff),
                          ),
                          DropdownMenuItem(
                            value: kTriOptOn,
                            child: Text(l10n.optOn),
                          ),
                          DropdownMenuItem(
                            value: 'dark',
                            child: Text(l10n.dynamicHighlightModeDark),
                          ),
                          DropdownMenuItem(
                            value: 'darker',
                            child: Text(l10n.dynamicHighlightModeDarker),
                          ),
                        ],
                        onChanged: controlsEnabled
                            ? (v) {
                                if (v != null) {
                                  _setDynamicHighlightColor(v);
                                }
                              }
                            : null,
                      ),
                    ),
                    const SizedBox(height: 10),
                    _SettingField(
                      label: l10n.textHighlightLabel,
                      child: Row(
                        children: [
                          Expanded(
                            child: SwitchListTile(
                              dense: true,
                              contentPadding: const EdgeInsets.symmetric(
                                horizontal: 8,
                              ),
                              title: Text(l10n.showLeftHighlightShort),
                              value: _showLeftHighlight,
                              onChanged: controlsEnabled && _hasHighlightSource
                                  ? _setShowLeftHighlight
                                  : null,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: SwitchListTile(
                              dense: true,
                              contentPadding: const EdgeInsets.symmetric(
                                horizontal: 8,
                              ),
                              title: Text(l10n.showRightHighlightShort),
                              value: _showRightHighlight,
                              onChanged: controlsEnabled && _hasHighlightSource
                                  ? _setShowRightHighlight
                                  : null,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                _SectionCard(
                  title: l10n.outerGlowLabel,
                  children: [
                    _TriOptSegmented(
                      label: l10n.outerGlowLabel,
                      value: _outerGlow,
                      enabled: controlsEnabled,
                      defaultLabel: _outerGlowDefaultLabel(
                        context,
                        _ctrl.defaultOuterGlow,
                      ),
                      extraOptions: [
                        ButtonSegment<String>(
                          value: kTriOptFollowDynamic,
                          label: Text(l10n.followDynamicColorLabel),
                        ),
                      ],
                      onChanged: _setOuterGlow,
                    ),
                    const SizedBox(height: 10),
                    _SettingField(
                      label: l10n.outEffectColorLabel,
                      child: ColorValueField(
                        controller: _outEffectColorController,
                        enabled: controlsEnabled && !_outerGlowFollowDynamic,
                        readOnly: _outerGlowFollowDynamic,
                        decoration: _fieldDecoration(
                          context,
                          hintText: '#AARRGGBB / #RRGGBB',
                        ),
                        previewColor: _parseColor(_outEffectColor),
                        previewFallbackColor: cs.primary,
                        onChanged: _setOutEffectColor,
                        onClear: () {
                          _outEffectColorController.clear();
                          _setOutEffectColor('');
                        },
                        onPickColor: () async {
                          final color = await showColorPickerDialog(
                            context,
                            initialHex: _outEffectColor,
                            title: l10n.outEffectColorLabel,
                            enableAlpha: true,
                          );
                          if (color == null) return;
                          final hex = colorToArgbHex(color);
                          _outEffectColorController.text = hex;
                          await _setOutEffectColor(hex);
                        },
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                _SectionCard(
                  title: '${l10n.outerGlowLabel} (${l10n.islandSection})',
                  children: [
                    _TriOptSegmented(
                      label: '${l10n.outerGlowLabel} (${l10n.islandSection})',
                      value: _islandOuterGlow,
                      enabled: controlsEnabled,
                      defaultLabel: _outerGlowDefaultLabel(
                        context,
                        _ctrl.defaultIslandOuterGlow,
                      ),
                      extraOptions: [
                        ButtonSegment<String>(
                          value: kTriOptFollowDynamic,
                          label: Text(l10n.followDynamicColorLabel),
                        ),
                      ],
                      onChanged: _setIslandOuterGlow,
                    ),
                    const SizedBox(height: 10),
                    _SettingField(
                      label:
                          '${l10n.outEffectColorLabel} (${l10n.islandSection})',
                      child: ColorValueField(
                        controller: _islandOuterGlowColorController,
                        enabled:
                            controlsEnabled && !_islandOuterGlowFollowDynamic,
                        readOnly: _islandOuterGlowFollowDynamic,
                        decoration: _fieldDecoration(
                          context,
                          hintText: '#AARRGGBB / #RRGGBB',
                        ),
                        previewColor: _parseColor(_islandOuterGlowColor),
                        previewFallbackColor: cs.primary,
                        onChanged: _setIslandOuterGlowColor,
                        onClear: () {
                          _islandOuterGlowColorController.clear();
                          _setIslandOuterGlowColor('');
                        },
                        onPickColor: () async {
                          final color = await showColorPickerDialog(
                            context,
                            initialHex: _islandOuterGlowColor,
                            title:
                                '${l10n.outEffectColorLabel} (${l10n.islandSection})',
                            enableAlpha: true,
                          );
                          if (color == null) return;
                          final hex = colorToArgbHex(color);
                          _islandOuterGlowColorController.text = hex;
                          await _setIslandOuterGlowColor(hex);
                        },
                      ),
                    ),
                  ],
                ),
              ],
            ),
    );
  }
}

class _SectionCard extends StatelessWidget {
  const _SectionCard({required this.title, required this.children});

  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.fromLTRB(12, 10, 12, 12),
      decoration: BoxDecoration(
        color: cs.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: 8),
          ...children,
        ],
      ),
    );
  }
}

class _SettingField extends StatelessWidget {
  const _SettingField({required this.label, required this.child});

  final String label;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: Theme.of(
            context,
          ).textTheme.bodyMedium?.copyWith(color: cs.onSurfaceVariant),
        ),
        const SizedBox(height: 6),
        child,
      ],
    );
  }
}

class _TriOptSegmented extends StatelessWidget {
  const _TriOptSegmented({
    required this.label,
    required this.value,
    required this.enabled,
    required this.defaultLabel,
    required this.onChanged,
    this.subtitle,
    this.extraOptions = const [],
  });

  final String label;
  final String? subtitle;
  final String value;
  final bool enabled;
  final String defaultLabel;
  final ValueChanged<String> onChanged;
  final List<ButtonSegment<String>> extraOptions;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return _SettingField(
      label: subtitle == null ? label : '$label\n$subtitle',
      child: SegmentedButton<String>(
        segments: [
          ButtonSegment<String>(
            value: kTriOptDefault,
            label: Text(defaultLabel),
          ),
          ButtonSegment<String>(value: kTriOptOn, label: Text(l10n.optOn)),
          ButtonSegment<String>(value: kTriOptOff, label: Text(l10n.optOff)),
          ...extraOptions,
        ],
        selected: {value},
        showSelectedIcon: false,
        onSelectionChanged: enabled ? (s) => onChanged(s.first) : null,
      ),
    );
  }
}

InputDecoration _fieldDecoration(
  BuildContext context, {
  String? hintText,
  String? suffixText,
}) {
  final cs = Theme.of(context).colorScheme;
  return InputDecoration(
    hintText: hintText,
    suffixText: suffixText,
    isDense: true,
    contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
    border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
    filled: true,
    fillColor: cs.surface,
  );
}

String _outerGlowDefaultLabel(BuildContext context, String value) {
  final l10n = AppLocalizations.of(context)!;
  return switch (value) {
    kTriOptOn => l10n.optDefaultOn,
    kTriOptFollowDynamic =>
      '${l10n.optDefault} (${l10n.followDynamicColorLabel})',
    _ => l10n.optDefaultOff,
  };
}
