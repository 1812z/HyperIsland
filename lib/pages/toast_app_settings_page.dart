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
  String _enableFloat = kTriOptDefault;
  String _marquee = kTriOptDefault;
  String _marqueeAutoHide = kTriOptDefault;
  String _timeout = '5';
  String _highlightColor = '';
  String _outEffectColor = '';
  String _islandOuterGlow = kTriOptDefault;
  String _islandOuterGlowColor = '';
  String _dynamicHighlightColor = kTriOptDefault;
  bool _showLeftHighlight = false;
  bool _showRightHighlight = false;
  String _outerGlow = kTriOptDefault;
  String _filterMode = 'blacklist';
  List<String> _whitelistKeywords = [];
  List<String> _blacklistKeywords = [];

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

  bool get _marqueeAutoHideEnabled {
    return switch (_marquee) {
      kTriOptOn => true,
      kTriOptOff => false,
      _ => _ctrl.defaultMarquee,
    };
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
    final enableFloatFuture = widget.controller.getToastEnableFloat(pkg);
    final marqueeFuture = widget.controller.getToastMarquee(pkg);
    final marqueeAutoHideFuture = widget.controller.getToastMarqueeAutoHide(
      pkg,
    );
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
    final filterModeFuture = widget.controller.getToastFilterMode(pkg);
    final whitelistKeywordsFuture = widget.controller.getToastWhitelistKeywords(
      pkg,
    );
    final blacklistKeywordsFuture = widget.controller.getToastBlacklistKeywords(
      pkg,
    );

    final forwardEnabled = await forwardEnabledFuture;
    final blockOriginal = await blockOriginalFuture;
    final showNotification = await showNotificationFuture;
    final showIslandIcon = await showIslandIconFuture;
    final firstFloat = await firstFloatFuture;
    final enableFloat = await enableFloatFuture;
    final marquee = await marqueeFuture;
    final marqueeAutoHide = await marqueeAutoHideFuture;
    final timeout = await timeoutFuture;
    final highlightColor = await highlightColorFuture;
    final dynamicHighlightColor = await dynamicHighlightColorFuture;
    final showLeftHighlight = await showLeftHighlightFuture;
    final showRightHighlight = await showRightHighlightFuture;
    final outerGlow = await outerGlowFuture;
    final outEffectColor = await outEffectColorFuture;
    final islandOuterGlow = await islandOuterGlowFuture;
    final islandOuterGlowColor = await islandOuterGlowColorFuture;
    final filterMode = await filterModeFuture;
    final whitelistKeywords = await whitelistKeywordsFuture;
    final blacklistKeywords = await blacklistKeywordsFuture;

    if (!mounted) return;
    setState(() {
      _forwardEnabled = forwardEnabled;
      _blockOriginal = blockOriginal;
      _showNotification = showNotification;
      _showIslandIcon = showIslandIcon;
      _firstFloat = firstFloat;
      _enableFloat = enableFloat;
      _marquee = marquee;
      _marqueeAutoHide = marqueeAutoHide;
      _timeout = timeout;
      _highlightColor = highlightColor;
      _dynamicHighlightColor = dynamicHighlightColor;
      _showLeftHighlight = showLeftHighlight == kTriOptOn;
      _showRightHighlight = showRightHighlight == kTriOptOn;
      _outerGlow = outerGlow;
      _outEffectColor = outEffectColor;
      _islandOuterGlow = islandOuterGlow;
      _islandOuterGlowColor = islandOuterGlowColor;
      _filterMode = filterMode;
      _whitelistKeywords = whitelistKeywords;
      _blacklistKeywords = blacklistKeywords;
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

  Future<void> _setEnableFloat(String value) async {
    setState(() => _enableFloat = value);
    await widget.controller.setToastEnableFloat(widget.app.packageName, value);
  }

  Future<void> _setMarquee(String value) async {
    setState(() => _marquee = value);
    await widget.controller.setToastMarquee(widget.app.packageName, value);
  }

  Future<void> _setMarqueeAutoHide(String value) async {
    setState(() => _marqueeAutoHide = value);
    await widget.controller.setToastMarqueeAutoHide(
      widget.app.packageName,
      value,
    );
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

  Future<void> _setFilterMode(String value) async {
    setState(() => _filterMode = value);
    await widget.controller.setToastFilterMode(widget.app.packageName, value);
  }

  Future<void> _setWhitelistKeywords(List<String> keywords) async {
    setState(() => _whitelistKeywords = keywords);
    await widget.controller.setToastWhitelistKeywords(
      widget.app.packageName,
      keywords,
    );
  }

  Future<void> _setBlacklistKeywords(List<String> keywords) async {
    setState(() => _blacklistKeywords = keywords);
    await widget.controller.setToastBlacklistKeywords(
      widget.app.packageName,
      keywords,
    );
  }

  String _defaultLabel(BuildContext context, bool enabled) {
    final l10n = AppLocalizations.of(context)!;
    return enabled ? l10n.optDefaultOn : l10n.optDefaultOff;
  }

  String _marqueeAutoHideLabel(BuildContext context, String value) {
    final l10n = AppLocalizations.of(context)!;
    return switch (value) {
      '1' => l10n.marqueeAutoHideOnce,
      '2' => l10n.marqueeAutoHideTwice,
      '1_override' => l10n.marqueeAutoHideOnceOverride,
      '2_override' => l10n.marqueeAutoHideTwiceOverride,
      kTriOptDefault =>
        '${l10n.optDefault} (${_marqueeAutoHideLabel(context, _ctrl.defaultMarqueeAutoHide)})',
      _ => l10n.off,
    };
  }

  Color? _parseColor(String? hex) => parseHexColor(hex);

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final cs = Theme.of(context).colorScheme;
    final islandEnabled = _forwardEnabled;
    final filterEnabled = _forwardEnabled || _blockOriginal;

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
                  onForwardEnabledChanged: _setForwardEnabled,
                  onBlockOriginalChanged: _setBlockOriginal,
                  onShowNotificationChanged: _setShowNotification,
                  allowIndependentBlockOriginal: true,
                ),
                const SizedBox(height: 12),
                ToastSettingsSection(
                  title: l10n.islandSection,
                  icon: Icons.smart_display_rounded,
                  initiallyExpanded: false,
                  children: [
                    ToastTriOptDropdown(
                      label: l10n.islandIconLabel,
                      subtitle: l10n.islandIconLabelSubtitle,
                      value: _showIslandIcon ? kTriOptOn : kTriOptOff,
                      enabled: islandEnabled,
                      defaultLabel: l10n.optDefault,
                      onChanged: (v) {
                        if (v != null) _setShowIslandIcon(v != kTriOptOff);
                      },
                    ),
                    const SizedBox(height: 10),
                    ToastTriOptDropdown(
                      label: l10n.firstFloatLabel,
                      subtitle: l10n.firstFloatLabelSubtitle,
                      value: _firstFloat,
                      enabled: islandEnabled,
                      defaultLabel: _defaultLabel(
                        context,
                        _ctrl.defaultFirstFloat,
                      ),
                      onChanged: (v) {
                        if (v != null) _setFirstFloat(v);
                      },
                    ),
                    const SizedBox(height: 10),
                    ToastTriOptDropdown(
                      label: l10n.updateFloatLabel,
                      subtitle: l10n.updateFloatLabelSubtitle,
                      value: _enableFloat,
                      enabled: islandEnabled,
                      defaultLabel: _defaultLabel(
                        context,
                        _ctrl.defaultEnableFloat,
                      ),
                      onChanged: (v) {
                        if (v != null) _setEnableFloat(v);
                      },
                    ),
                    const SizedBox(height: 10),
                    ToastTriOptDropdown(
                      label: l10n.marqueeChannelTitle,
                      subtitle: l10n.marqueeChannelTitleSubtitle,
                      value: _marquee,
                      enabled: islandEnabled,
                      defaultLabel: _defaultLabel(
                        context,
                        _ctrl.defaultMarquee,
                      ),
                      onChanged: (v) {
                        if (v != null) _setMarquee(v);
                      },
                    ),
                    const SizedBox(height: 10),
                    Opacity(
                      opacity: islandEnabled && _marqueeAutoHideEnabled
                          ? 1
                          : 0.45,
                      child: ToastSettingField(
                        label:
                            '${l10n.marqueeAutoHideTitle}\n${l10n.marqueeAutoHideSubtitle}',
                        child: DropdownButtonFormField<String>(
                          key: ValueKey(_marqueeAutoHide),
                          initialValue: _marqueeAutoHide,
                          isExpanded: true,
                          decoration: toastFieldDecoration(context),
                          items: [
                            DropdownMenuItem(
                              value: kTriOptDefault,
                              child: Text(
                                _marqueeAutoHideLabel(context, kTriOptDefault),
                              ),
                            ),
                            DropdownMenuItem(
                              value: kTriOptOff,
                              child: Text(l10n.off),
                            ),
                            DropdownMenuItem(
                              value: '1',
                              child: Text(l10n.marqueeAutoHideOnce),
                            ),
                            DropdownMenuItem(
                              value: '2',
                              child: Text(l10n.marqueeAutoHideTwice),
                            ),
                            DropdownMenuItem(
                              value: '1_override',
                              child: Text(l10n.marqueeAutoHideOnceOverride),
                            ),
                            DropdownMenuItem(
                              value: '2_override',
                              child: Text(l10n.marqueeAutoHideTwiceOverride),
                            ),
                          ],
                          onChanged: islandEnabled && _marqueeAutoHideEnabled
                              ? (v) {
                                  if (v != null) _setMarqueeAutoHide(v);
                                }
                              : null,
                        ),
                      ),
                    ),
                    const SizedBox(height: 10),
                    ToastSettingField(
                      label: l10n.autoDisappear,
                      child: TextFormField(
                        controller: _timeoutController,
                        enabled: islandEnabled,
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
                        decoration: toastFieldDecoration(
                          context,
                          suffixText: l10n.seconds,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                ToastSettingsSection(
                  title: l10n.highlightColorLabel,
                  icon: Icons.palette_rounded,
                  initiallyExpanded: false,
                  children: [
                    ToastSettingField(
                      label: l10n.highlightColorLabel,
                      child: ColorValueField(
                        controller: _highlightColorController,
                        enabled: islandEnabled && !_dynamicHighlightEnabled,
                        readOnly: _dynamicHighlightEnabled,
                        decoration: toastFieldDecoration(
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
                    ToastSettingField(
                      label:
                          '${l10n.dynamicHighlightColorLabel}\n${l10n.dynamicHighlightColorLabelSubtitle}',
                      child: DropdownButtonFormField<String>(
                        initialValue: _dynamicHighlightColor,
                        decoration: toastFieldDecoration(context),
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
                        onChanged: islandEnabled
                            ? (v) {
                                if (v != null) {
                                  _setDynamicHighlightColor(v);
                                }
                              }
                            : null,
                      ),
                    ),
                    const SizedBox(height: 10),
                    ToastSettingField(
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
                              onChanged: islandEnabled && _hasHighlightSource
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
                              onChanged: islandEnabled && _hasHighlightSource
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
                ToastSettingsSection(
                  title: l10n.outerGlowLabel,
                  icon: Icons.flare_rounded,
                  initiallyExpanded: false,
                  children: [
                    ToastTriOptDropdown(
                      label: l10n.outerGlowLabel,
                      value: _outerGlow,
                      enabled: islandEnabled,
                      defaultLabel: _outerGlowDefaultLabel(
                        context,
                        _ctrl.defaultOuterGlow,
                      ),
                      includeFollowDynamic: true,
                      onChanged: (v) {
                        if (v != null) _setOuterGlow(v);
                      },
                    ),
                    const SizedBox(height: 10),
                    ToastSettingField(
                      label: l10n.outEffectColorLabel,
                      child: ColorValueField(
                        controller: _outEffectColorController,
                        enabled: islandEnabled && !_outerGlowFollowDynamic,
                        readOnly: _outerGlowFollowDynamic,
                        decoration: toastFieldDecoration(
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
                ToastSettingsSection(
                  title: '${l10n.outerGlowLabel} (${l10n.islandSection})',
                  icon: Icons.blur_on_rounded,
                  initiallyExpanded: false,
                  children: [
                    ToastTriOptDropdown(
                      label: '${l10n.outerGlowLabel} (${l10n.islandSection})',
                      value: _islandOuterGlow,
                      enabled: islandEnabled,
                      defaultLabel: _outerGlowDefaultLabel(
                        context,
                        _ctrl.defaultIslandOuterGlow,
                      ),
                      includeFollowDynamic: true,
                      onChanged: (v) {
                        if (v != null) _setIslandOuterGlow(v);
                      },
                    ),
                    const SizedBox(height: 10),
                    ToastSettingField(
                      label:
                          '${l10n.outEffectColorLabel} (${l10n.islandSection})',
                      child: ColorValueField(
                        controller: _islandOuterGlowColorController,
                        enabled:
                            islandEnabled && !_islandOuterGlowFollowDynamic,
                        readOnly: _islandOuterGlowFollowDynamic,
                        decoration: toastFieldDecoration(
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
                const SizedBox(height: 12),
                ToastSettingsSection(
                  title: l10n.filterRulesSection,
                  icon: Icons.filter_list_rounded,
                  initiallyExpanded: false,
                  children: [
                    ToastSettingField(
                      label: l10n.filterModeLabel,
                      child: DropdownButtonFormField<String>(
                        key: ValueKey(_filterMode),
                        initialValue: _filterMode,
                        isExpanded: true,
                        decoration: toastFieldDecoration(context),
                        items: [
                          DropdownMenuItem(
                            value: 'blacklist',
                            child: Text(l10n.filterModeBlacklist),
                          ),
                          DropdownMenuItem(
                            value: 'whitelist',
                            child: Text(l10n.filterModeWhitelist),
                          ),
                        ],
                        onChanged: filterEnabled
                            ? (v) {
                                if (v != null) _setFilterMode(v);
                              }
                            : null,
                      ),
                    ),
                    const SizedBox(height: 10),
                    ToastKeywordListEditor(
                      label: l10n.whitelistKeywordsLabel,
                      keywords: _whitelistKeywords,
                      enabled: filterEnabled && _filterMode == 'whitelist',
                      hintText: l10n.addKeyword,
                      onAdd: (kw) =>
                          _setWhitelistKeywords([..._whitelistKeywords, kw]),
                      onRemove: (kw) => _setWhitelistKeywords(
                        _whitelistKeywords.where((k) => k != kw).toList(),
                      ),
                    ),
                    const SizedBox(height: 10),
                    ToastKeywordListEditor(
                      label: l10n.blacklistKeywordsLabel,
                      keywords: _blacklistKeywords,
                      enabled: filterEnabled,
                      hintText: l10n.addKeyword,
                      onAdd: (kw) =>
                          _setBlacklistKeywords([..._blacklistKeywords, kw]),
                      onRemove: (kw) => _setBlacklistKeywords(
                        _blacklistKeywords.where((k) => k != kw).toList(),
                      ),
                    ),
                    if (_filterMode == 'whitelist') ...[
                      const SizedBox(height: 10),
                      Text(
                        l10n.keywordFilterPriority,
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: cs.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ],
                ),
              ],
            ),
    );
  }
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
