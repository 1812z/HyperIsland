import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:convert';
import '../controllers/settings_controller.dart';
import '../controllers/whitelist_controller.dart';
import '../l10n/generated/app_localizations.dart';
import 'color_picker_dialog.dart';
import 'color_value_field.dart';

// ── 操作模式（sealed class）──────────────────────────────────────────────────

/// 渠道设置弹窗的工作模式。
sealed class ChannelSettingsMode {
  const ChannelSettingsMode();
}

/// 单渠道模式：对单个渠道设置，字段预填当前值，无"不更改"选项。
class SingleChannelMode extends ChannelSettingsMode {
  const SingleChannelMode({
    required this.channelName,
    required this.template,
    required this.renderer,
    required this.iconMode,
    required this.focusNotif,
    required this.preserveSmallIcon,
    required this.showIslandIcon,
    required this.firstFloat,
    required this.enableFloat,
    required this.islandTimeout,
    required this.marquee,
    required this.restoreLockscreen,
    required this.highlightColor,
    required this.dynamicHighlightColor,
    required this.showLeftHighlight,
    required this.showRightHighlight,
    required this.showLeftNarrowFont,
    required this.showRightNarrowFont,
    required this.outerGlow,
    required this.islandOuterGlow,
    required this.islandOuterGlowColor,
    required this.outEffectColor,
    required this.focusCustom,
    required this.islandCustom,
    required this.filterMode,
    required this.whitelistKeywords,
    required this.blacklistKeywords,
  });

  final String channelName;
  final String template;
  final String renderer;
  final String iconMode;
  final String focusNotif;
  final String preserveSmallIcon;
  final String showIslandIcon;
  final String firstFloat;
  final String enableFloat;
  final String islandTimeout;
  final String marquee;
  final String restoreLockscreen;
  final String highlightColor;
  final String dynamicHighlightColor;
  final String showLeftHighlight;
  final String showRightHighlight;
  final String showLeftNarrowFont;
  final String showRightNarrowFont;
  final String outerGlow;
  final String islandOuterGlow;
  final String islandOuterGlowColor;
  final String outEffectColor;
  final String focusCustom;
  final String islandCustom;
  final String filterMode;
  final List<String> whitelistKeywords;
  final List<String> blacklistKeywords;
}

/// 批量模式：对多个渠道批量操作，字段默认"不更改"。
class BatchChannelMode extends ChannelSettingsMode {
  const BatchChannelMode({required this.scope});

  final BatchScope scope;
}

// ── 批量操作范围（sealed class）───────────────────────────────────────────────

/// 批量操作的目标范围。
sealed class BatchScope {
  const BatchScope();
}

/// 单应用模式：对当前应用内的渠道批量操作，可选仅已启用渠道。
class SingleAppScope extends BatchScope {
  const SingleAppScope({
    required this.totalChannels,
    required this.enabledChannels,
  });

  final int totalChannels;
  final int enabledChannels;
}

/// 全局模式：对所有已启用应用的全部渠道批量操作，不需要范围切换。
class GlobalScope extends BatchScope {
  const GlobalScope({required this.subtitle});

  final String subtitle;
}

// ── 返回值 ───────────────────────────────────────────────────────────────────

/// 渠道配置的应用结果。
/// 单渠道模式下 [settings] 的值均非 null；批量模式下 null 表示该项不更改。
/// [onlyEnabled] 仅在 [BatchChannelMode] + [SingleAppScope] 下有意义。
class BatchApplyResult {
  final Map<String, String?> settings;
  final bool onlyEnabled;

  const BatchApplyResult({required this.settings, required this.onlyEnabled});
}

// ── 主体组件 ─────────────────────────────────────────────────────────────────

/// 渠道配置底部弹窗，支持单渠道和批量两种模式。
///
/// 通过静态方法 [show] 打开，返回用户确认的 [BatchApplyResult]（取消时返回 null）。
class BatchChannelSettingsSheet extends StatefulWidget {
  const BatchChannelSettingsSheet({
    super.key,
    required this.mode,
    required this.templateLabels,
    required this.rendererLabels,
    required this.controller,
  });

  final ChannelSettingsMode mode;
  final Map<String, String> templateLabels;
  final Map<String, String> rendererLabels;
  final WhitelistController controller;

  static Future<BatchApplyResult?> show(
    BuildContext context, {
    required ChannelSettingsMode mode,
    required Map<String, String> templateLabels,
    required Map<String, String> rendererLabels,
    required WhitelistController controller,
  }) {
    return showModalBottomSheet<BatchApplyResult>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      builder: (_) => BatchChannelSettingsSheet(
        mode: mode,
        templateLabels: templateLabels,
        rendererLabels: rendererLabels,
        controller: controller,
      ),
    );
  }

  @override
  State<BatchChannelSettingsSheet> createState() =>
      _BatchChannelSettingsSheetState();
}

class _BatchChannelSettingsSheetState extends State<BatchChannelSettingsSheet> {
  AppLocalizations _l10n(BuildContext context) => AppLocalizations.of(context)!;
  SettingsController get _ctrl => SettingsController.instance;

  String _defaultLabel(BuildContext context, bool value) =>
      value ? _l10n(context).optDefaultOn : _l10n(context).optDefaultOff;

  String _outerGlowDefaultLabel(BuildContext context, String value) {
    final l10n = _l10n(context);
    return switch (value) {
      kTriOptOn => l10n.optDefaultOn,
      kTriOptFollowDynamic =>
        '${l10n.optDefault} (${l10n.followDynamicColorLabel})',
      _ => l10n.optDefaultOff,
    };
  }

  String? _template;
  String? _renderer;
  String? _iconMode;
  String? _focusNotif;
  String? _preserveSmallIcon;
  String? _showIslandIcon;
  String? _firstFloat;
  String? _enableFloat;
  String? _islandTimeout;
  String? _marquee;
  String? _restoreLockscreen;
  String? _highlightColor;
  String? _dynamicHighlightColor;
  bool? _showLeftHighlight;
  bool? _showRightHighlight;
  bool? _showLeftNarrowFont;
  bool? _showRightNarrowFont;
  String? _outerGlow;
  String? _islandOuterGlow;
  String? _islandOuterGlowColor;
  String? _outEffectColor;
  String? _focusCustom;
  String? _islandCustom;

  String? _filterMode;
  List<String> _whitelistKeywords = [];
  List<String> _blacklistKeywords = [];

  String get effectiveFilterMode => _filterMode ?? (_isSingle ? 'blacklist' : 'blacklist');

  Map<String, dynamic>? _focusSchema;
  Map<String, dynamic>? _islandSchema;
  bool _loadingFocusSchema = false;
  final Map<String, TextEditingController> _focusControllers = {};
  bool _loadingIslandSchema = false;
  final Map<String, TextEditingController> _islandControllers = {};

  bool _islandExpanded = false;
  bool _islandCustomExpanded = false;
  bool _focusExpanded = false;
  bool _focusCustomExpanded = false;
  bool _filterExpanded = false;

  // 仅 BatchChannelMode + SingleAppScope 下使用
  bool _onlyEnabled = false;

  late final TextEditingController _timeoutController;
  late final TextEditingController _highlightColorController;
  late final TextEditingController _islandOuterGlowColorController;
  late final TextEditingController _outEffectColorController;

  bool get _isSingle => widget.mode is SingleChannelMode;
  bool get _dynamicHighlightEnabled => resolvesDynamicColorMode(
    _dynamicHighlightColor,
    _ctrl.defaultDynamicHighlightColor,
  );

  bool _isFollowDynamicGlow(String? mode, String defaultMode) {
    return mode == kTriOptFollowDynamic ||
        (mode == kTriOptDefault && defaultMode == kTriOptFollowDynamic);
  }

  List<DropdownMenuItem<String?>> _outerGlowItems(
    BuildContext context, {
    required String defaultMode,
  }) {
    final l10n = _l10n(context);
    return [
      DropdownMenuItem<String?>(
        value: kTriOptDefault,
        child: Text(_outerGlowDefaultLabel(context, defaultMode)),
      ),
      DropdownMenuItem<String?>(value: kTriOptOn, child: Text(l10n.optOn)),
      DropdownMenuItem<String?>(value: kTriOptOff, child: Text(l10n.optOff)),
      DropdownMenuItem<String?>(
        value: kTriOptFollowDynamic,
        child: Text(l10n.followDynamicColorLabel),
      ),
    ];
  }

  @override
  void initState() {
    super.initState();
    if (widget.mode case SingleChannelMode m) {
      _template = m.template;
      _renderer = m.renderer;
      _iconMode = m.iconMode;
      _focusNotif = m.focusNotif;
      _preserveSmallIcon = m.preserveSmallIcon;
      _showIslandIcon = m.showIslandIcon;
      _firstFloat = m.firstFloat;
      _enableFloat = m.enableFloat;
      _islandTimeout = m.islandTimeout;
      _marquee = m.marquee;
      _restoreLockscreen = m.restoreLockscreen;
      _highlightColor = m.highlightColor;
      _dynamicHighlightColor = m.dynamicHighlightColor;
      _showLeftHighlight = m.showLeftHighlight == kTriOptOn;
      _showRightHighlight = m.showRightHighlight == kTriOptOn;
      _showLeftNarrowFont = m.showLeftNarrowFont == kTriOptOn;
      _showRightNarrowFont = m.showRightNarrowFont == kTriOptOn;
      _outerGlow = m.outerGlow;
      _islandOuterGlow = m.islandOuterGlow;
      _islandOuterGlowColor = m.islandOuterGlowColor;
      _outEffectColor = m.outEffectColor;
      _focusCustom = m.focusCustom;
      _islandCustom = m.islandCustom;
      _filterMode = m.filterMode;
      _whitelistKeywords = List.from(m.whitelistKeywords);
      _blacklistKeywords = List.from(m.blacklistKeywords);
      _timeoutController = TextEditingController(text: m.islandTimeout);
      _highlightColorController = TextEditingController(text: m.highlightColor);
      _islandOuterGlowColorController = TextEditingController(
        text: m.islandOuterGlowColor,
      );
      _outEffectColorController = TextEditingController(text: m.outEffectColor);
    } else {
      _timeoutController = TextEditingController();
      _highlightColorController = TextEditingController();
      _islandOuterGlowColorController = TextEditingController();
      _outEffectColorController = TextEditingController();
    }
    _loadFocusSchema();
  }

  @override
  void dispose() {
    _timeoutController.dispose();
    _highlightColorController.dispose();
    _islandOuterGlowColorController.dispose();
    _outEffectColorController.dispose();
    for (final c in _focusControllers.values) {
      c.dispose();
    }
    for (final c in _islandControllers.values) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _loadFocusSchema() async {
    final template = _template;
    final renderer = _renderer;
    if (template == null || renderer == null) return;
    setState(() => _loadingFocusSchema = true);
    final schema = await widget.controller.getFocusCustomizationSchema(
      template,
      renderer,
    );
    if (!mounted) return;
    final merged = await widget.controller.mergeFocusCustomizationDefaults(
      template,
      renderer,
      _focusCustom,
    );
    if (!mounted) return;

    final json = _decodeJson(merged);
    _rebuildFocusControllers(schema, json);

    setState(() {
      _focusSchema = schema;
      _focusCustom = merged;
      _loadingFocusSchema = false;
    });
    _loadIslandSchema();
  }

  Future<void> _loadIslandSchema() async {
    final template = _template;
    if (template == null) return;
    setState(() => _loadingIslandSchema = true);
    final schema = await widget.controller.getIslandCustomizationSchema(
      template,
    );
    if (!mounted) return;
    final merged = await widget.controller.mergeIslandCustomizationDefaults(
      template,
      _islandCustom,
    );
    if (!mounted) return;

    final json = _decodeJson(merged);
    _rebuildIslandControllers(schema, json);
    setState(() {
      _islandSchema = schema;
      _islandCustom = merged;
      _loadingIslandSchema = false;
    });
  }

  Map<String, dynamic> _decodeJson(String raw) {
    try {
      final decoded = raw.isEmpty ? {} : (jsonDecode(raw) as Map);
      return decoded.cast<String, dynamic>();
    } catch (_) {
      return <String, dynamic>{};
    }
  }

  void _rebuildFocusControllers(
    Map<String, dynamic>? schema,
    Map<String, dynamic> json,
  ) {
    for (final c in _focusControllers.values) {
      c.dispose();
    }
    _focusControllers.clear();

    final fields = (schema?['fields'] as List?)?.cast<Map>() ?? const [];
    for (final field in fields) {
      final type = (field['type'] ?? '').toString();
      final key = (field['key'] ?? '').toString();
      if (key.isEmpty) continue;
      if (type == 'text_expr' ||
          type == 'text' ||
          type == 'regex' ||
          type == 'number' ||
          type == 'color' ||
          type == 'select') {
        final def = (field['defaultValue'] ?? '').toString();
        final value = (json[key] ?? def).toString();
        _focusControllers[key] = TextEditingController(text: value);
      }
    }
  }

  void _syncFocusCustomFromControllers() {
    final map = <String, dynamic>{};
    _focusControllers.forEach((key, ctl) {
      map[key] = ctl.text;
    });
    _focusCustom = jsonEncode(map);
  }

  void _rebuildIslandControllers(
    Map<String, dynamic>? schema,
    Map<String, dynamic> json,
  ) {
    for (final c in _islandControllers.values) {
      c.dispose();
    }
    _islandControllers.clear();
    final fields = (schema?['fields'] as List?)?.cast<Map>() ?? const [];
    for (final field in fields) {
      final key = (field['key'] ?? '').toString();
      if (key.isEmpty) continue;
      final def = (field['defaultValue'] ?? '').toString();
      final value = (json[key] ?? def).toString();
      _islandControllers[key] = TextEditingController(text: value);
    }
  }

  void _syncIslandCustomFromControllers() {
    final map = <String, dynamic>{};
    _islandControllers.forEach((key, ctl) {
      map[key] = ctl.text;
    });
    _islandCustom = jsonEncode(map);
  }

  Widget _buildFocusCustomizationFields() {
    final l10n = AppLocalizations.of(context)!;
    final schema = _focusSchema;
    if (schema == null) {
      return const SizedBox.shrink();
    }

    final placeholders =
        (schema['placeholders'] as List?)?.cast<Map>() ?? const <Map>[];
    final functions =
        (schema['functions'] as List?)?.cast<Map>() ?? const <Map>[];
    final fields = (schema['fields'] as List?)?.cast<Map>() ?? const <Map>[];

    if (fields.isEmpty) {
      return const SizedBox.shrink();
    }

    final children = <Widget>[];

    if (placeholders.isNotEmpty) {
      final tips = placeholders
          .map((p) {
            final key = (p['key'] ?? '').toString();
            if (key.isEmpty) return null;
            return _formatPlaceholderTip(key);
          })
          .whereType<String>()
          .toList();
      children.add(
        _SettingField(
          label: l10n.availablePlaceholdersLabel,
          child: _buildPlaceholderButtons(tips),
        ),
      );
      children.add(const SizedBox(height: 10));
    }

    if (functions.isNotEmpty) {
      final tips = functions
          .map((f) => _formatFunctionExample((f['example'] ?? '').toString()))
          .where((e) => e.isNotEmpty)
          .join('\n');
      if (tips.isNotEmpty) {
        children.add(
          _SettingField(
            label: l10n.expressionFunctionsLabel,
            child: SelectableText(tips),
          ),
        );
        children.add(const SizedBox(height: 10));
      }
    }

    for (final field in fields) {
      final key = (field['key'] ?? '').toString();
      final label = _localizedFieldLabel(
        key,
        (field['label'] ?? key).toString(),
        l10n,
      );
      final type = (field['type'] ?? '').toString();
      if (key.isEmpty) continue;

      if (type == 'select') {
        final options =
            (field['options'] as List?)?.cast<Map>() ?? const <Map>[];
        final items = options
            .map(
              (o) => DropdownMenuItem<String?>(
                value: (o['value'] ?? '').toString(),
                child: Text(
                  _localizedOptionLabel(
                    key,
                    (o['value'] ?? '').toString(),
                    (o['label'] ?? o['value'] ?? '').toString(),
                    l10n,
                  ),
                ),
              ),
            )
            .toList();
        final ctl = _focusControllers.putIfAbsent(
          key,
          () => TextEditingController(
            text: (field['defaultValue'] ?? '').toString(),
          ),
        );
        children.add(
          _BatchSettingRow(
            label: label,
            value: ctl.text,
            showNotChange: false,
            items: items,
            onChanged: (v) {
              setState(() {
                ctl.text = v ?? '';
                _syncFocusCustomFromControllers();
              });
            },
          ),
        );
      } else if (type == 'color') {
        final options =
            (field['options'] as List?)?.cast<Map>() ?? const <Map>[];
        final items = options
            .map(
              (o) => DropdownMenuItem<String>(
                value: (o['value'] ?? '').toString(),
                child: Text(
                  _localizedOptionLabel(
                    key,
                    (o['value'] ?? '').toString(),
                    (o['label'] ?? o['value'] ?? '').toString(),
                    l10n,
                  ),
                ),
              ),
            )
            .toList();
        final presetItems = items.isEmpty ? null : items;
        final ctl = _focusControllers.putIfAbsent(
          key,
          () => TextEditingController(
            text: (field['defaultValue'] ?? '').toString(),
          ),
        );
        children.add(
          _SettingField(
            label: label,
            child: ColorValueField(
              controller: ctl,
              decoration: _fieldDecoration(context),
              previewColor: _parseColor(ctl.text),
              previewFallbackColor: Theme.of(context).colorScheme.primary,
              onChanged: (_) => setState(_syncFocusCustomFromControllers),
              onPickColor: () async {
                final color = await showColorPickerDialog(context);
                if (color != null) {
                  ctl.text = colorToHex(color);
                  setState(_syncFocusCustomFromControllers);
                }
              },
              presetItems: presetItems,
              onPresetSelected: presetItems == null
                  ? null
                  : (v) {
                      ctl.text = v;
                      setState(_syncFocusCustomFromControllers);
                    },
            ),
          ),
        );
      } else {
        final ctl = _focusControllers.putIfAbsent(
          key,
          () => TextEditingController(
            text: (field['defaultValue'] ?? '').toString(),
          ),
        );
        final isNumber = type == 'number';
        children.add(
          _SettingField(
            label: label,
            child: TextFormField(
              controller: ctl,
              keyboardType: isNumber
                  ? TextInputType.number
                  : TextInputType.text,
              inputFormatters: isNumber
                  ? [FilteringTextInputFormatter.digitsOnly]
                  : null,
              decoration: _fieldDecoration(context),
              onChanged: (_) {
                setState(_syncFocusCustomFromControllers);
              },
            ),
          ),
        );
      }
      children.add(const SizedBox(height: 10));
    }

    if (children.isNotEmpty) {
      children.removeLast();
    }

    return Column(children: children);
  }

  Widget _buildIslandCustomizationFields() {
    final l10n = AppLocalizations.of(context)!;
    final schema = _islandSchema;
    if (schema == null) return const SizedBox.shrink();
    final placeholders =
        (schema['placeholders'] as List?)?.cast<Map>() ?? const <Map>[];
    final functions =
        (schema['functions'] as List?)?.cast<Map>() ?? const <Map>[];
    final fields = (schema['fields'] as List?)?.cast<Map>() ?? const <Map>[];
    if (fields.isEmpty) return const SizedBox.shrink();

    final children = <Widget>[];
    if (placeholders.isNotEmpty) {
      final tips = placeholders
          .map((p) {
            final key = (p['key'] ?? '').toString();
            if (key.isEmpty) return null;
            return _formatPlaceholderTip(key);
          })
          .whereType<String>()
          .toList();
      children.add(
        _SettingField(
          label: l10n.availablePlaceholdersLabel,
          child: _buildPlaceholderButtons(tips),
        ),
      );
      children.add(const SizedBox(height: 10));
    }
    if (functions.isNotEmpty) {
      final tips = functions
          .map((f) => _formatFunctionExample((f['example'] ?? '').toString()))
          .where((e) => e.isNotEmpty)
          .join('\n');
      if (tips.isNotEmpty) {
        children.add(
          _SettingField(
            label: l10n.expressionFunctionsLabel,
            child: SelectableText(tips),
          ),
        );
        children.add(const SizedBox(height: 10));
      }
    }

    for (final field in fields) {
      final key = (field['key'] ?? '').toString();
      final label = _localizedFieldLabel(
        key,
        (field['label'] ?? key).toString(),
        l10n,
      );
      if (key.isEmpty) continue;
      final ctl = _islandControllers.putIfAbsent(
        key,
        () => TextEditingController(
          text: (field['defaultValue'] ?? '').toString(),
        ),
      );
      children.add(
        _SettingField(
          label: label,
          child: TextFormField(
            controller: ctl,
            decoration: _fieldDecoration(context),
            onChanged: (_) => setState(_syncIslandCustomFromControllers),
          ),
        ),
      );
      children.add(const SizedBox(height: 10));
    }
    if (children.isNotEmpty) children.removeLast();
    return Column(children: children);
  }

  Color? _parseColor(String? hex) => parseHexColor(hex);

  String _formatPlaceholderTip(String key) {
    return '\${$key}';
  }

  Widget _buildPlaceholderButtons(List<String> placeholders) {
    if (placeholders.isEmpty) return const SizedBox.shrink();
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: placeholders
          .map(
            (placeholder) => OutlinedButton(
              onPressed: () => _copyToClipboard(placeholder),
              style: OutlinedButton.styleFrom(
                visualDensity: VisualDensity.compact,
                padding: const EdgeInsets.symmetric(
                  horizontal: 10,
                  vertical: 6,
                ),
              ),
              child: Text(placeholder),
            ),
          )
          .toList(),
    );
  }

  Future<void> _copyToClipboard(String text) async {
    await Clipboard.setData(ClipboardData(text: text));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(_l10n(context).configCopied),
        duration: const Duration(seconds: 1),
      ),
    );
  }

  String _formatFunctionExample(String raw) {
    return raw
        .replaceAll('\\\\', '\\')
        .replaceAll(r'\${', r'${')
        .replaceAll(r'\(', '(')
        .replaceAll(r'\)', ')')
        .replaceAll(r'\,', ',')
        .replaceAll(r'\"', '"')
        .replaceAll(r"\'", "'")
        .trim();
  }

  String _localizedFieldLabel(
    String key,
    String fallback,
    AppLocalizations l10n,
  ) {
    final localized = switch (key) {
      'focus_title_expr' => l10n.focusTitleExprLabel,
      'focus_content_expr' => l10n.focusContentExprLabel,
      'focus_icon_mode' => l10n.focusIconSourceLabel,
      'focus_pic_profile_mode' => l10n.focusPicProfileSourceLabel,
      'focus_app_icon_pkg' => l10n.focusAppIconPkgLabel,
      'focus_app_icon_pkg_mode' => l10n.focusSecondaryIconSourceLabel,
      'progress_color' => l10n.progressColorLabel,
      'progress_bar_color' => l10n.progressBarColorLabel,
      'progress_bar_color_end' => l10n.progressBarColorEndLabel,
      'chat_title_color' => l10n.chatTitleColorLabel,
      'chat_title_color_dark' => l10n.chatTitleColorDarkLabel,
      'chat_content_color' => l10n.chatContentColorLabel,
      'chat_content_color_dark' => l10n.chatContentColorDarkLabel,
      'action_1_bg_color' => l10n.action1BgColorLabel,
      'action_1_bg_color_dark' => l10n.action1BgColorDarkLabel,
      'action_1_title_color' => l10n.action1TitleColorLabel,
      'action_1_title_color_dark' => l10n.action1TitleColorDarkLabel,
      'action_2_bg_color' => l10n.action2BgColorLabel,
      'action_2_bg_color_dark' => l10n.action2BgColorDarkLabel,
      'action_2_title_color' => l10n.action2TitleColorLabel,
      'action_2_title_color_dark' => l10n.action2TitleColorDarkLabel,
      'island_left_expr' => l10n.islandLeftExprLabel,
      'island_right_expr' => l10n.islandRightExprLabel,
      _ => fallback,
    };
    return localized;
  }

  String _localizedOptionLabel(
    String fieldKey,
    String value,
    String fallback,
    AppLocalizations l10n,
  ) {
    if (fieldKey == 'focus_icon_mode' ||
        fieldKey == 'focus_pic_profile_mode' ||
        fieldKey == 'focus_app_icon_pkg_mode') {
      switch (value) {
        case kIconModeAuto:
          return l10n.iconModeAuto;
        case kIconModeNotifSmall:
          return l10n.iconModeNotifSmall;
        case kIconModeNotifLarge:
          return l10n.iconModeNotifLarge;
        case kIconModeAppIcon:
          return l10n.iconModeAppIcon;
      }
    }
    return fallback;
  }

  bool get _hasAnyChange =>
      _isSingle ||
      _template != null ||
      _renderer != null ||
      _iconMode != null ||
      _focusNotif != null ||
      _preserveSmallIcon != null ||
      _showIslandIcon != null ||
      _firstFloat != null ||
      _enableFloat != null ||
      _islandTimeout != null ||
      _marquee != null ||
      _restoreLockscreen != null ||
      _highlightColor != null ||
      _dynamicHighlightColor != null ||
      _showLeftHighlight != null ||
      _showRightHighlight != null ||
      _showLeftNarrowFont != null ||
      _showRightNarrowFont != null ||
      _outerGlow != null ||
      _islandOuterGlow != null ||
      _islandOuterGlowColor != null ||
      _outEffectColor != null ||
      _focusCustom != null ||
      _islandCustom != null ||
      _filterMode != null ||
      _whitelistKeywords.isNotEmpty ||
      _blacklistKeywords.isNotEmpty;

  String _title(AppLocalizations l10n) => switch (widget.mode) {
    SingleChannelMode m => m.channelName,
    BatchChannelMode _ => l10n.batchChannelSettings,
  };

  String _subtitle(AppLocalizations l10n) => switch (widget.mode) {
    SingleChannelMode _ => l10n.channelSettings,
    BatchChannelMode(scope: final s) => switch (s) {
      SingleAppScope(:final totalChannels, :final enabledChannels) =>
        _onlyEnabled
            ? l10n.applyToEnabledChannels(enabledChannels)
            : l10n.applyToAllChannels(totalChannels),
      GlobalScope(:final subtitle) => subtitle,
    },
  };

  void _submit() {
    Navigator.pop(
      context,
      BatchApplyResult(
        settings: {
          'template': _template,
          'renderer': _renderer,
          'icon': _iconMode,
          'focus': _focusNotif,
          'preserve_small_icon': _focusNotif == kTriOptOff
              ? kTriOptOff
              : _preserveSmallIcon,
          'show_island_icon': _showIslandIcon,
          'first_float': _firstFloat,
          'enable_float': _enableFloat,
          'timeout': _islandTimeout,
          'marquee': _marquee,
          'restore_lockscreen': _restoreLockscreen,
          'highlight_color': _isSingle
              ? (_highlightColor ?? '')
              : _highlightColor,
          'dynamic_highlight_color': _isSingle
              ? (_dynamicHighlightColor ?? kTriOptDefault)
              : _dynamicHighlightColor,
          'show_left_highlight': _showLeftHighlight == null
              ? null
              : (_showLeftHighlight! ? kTriOptOn : kTriOptOff),
          'show_right_highlight': _showRightHighlight == null
              ? null
              : (_showRightHighlight! ? kTriOptOn : kTriOptOff),
          'show_left_narrow_font': _showLeftNarrowFont == null
              ? null
              : (_showLeftNarrowFont! ? kTriOptOn : kTriOptOff),
          'show_right_narrow_font': _showRightNarrowFont == null
              ? null
              : (_showRightNarrowFont! ? kTriOptOn : kTriOptOff),
          'outer_glow': _isSingle ? (_outerGlow ?? kTriOptDefault) : _outerGlow,
          'island_outer_glow': _isSingle
              ? (_islandOuterGlow ?? kTriOptDefault)
              : _islandOuterGlow,
          'island_outer_glow_color': _isSingle
              ? (_islandOuterGlowColor ?? '')
              : _islandOuterGlowColor,
          'out_effect_color': _isSingle
              ? (_outEffectColor ?? '')
              : _outEffectColor,
          'focus_custom': _focusCustom,
          'island_custom': _islandCustom,
          'filter_mode': _isSingle ? (_filterMode ?? 'blacklist') : _filterMode,
          'whitelist_keywords': _isSingle ? _whitelistKeywords.join(',') : (_whitelistKeywords.isNotEmpty ? _whitelistKeywords.join(',') : null),
          'blacklist_keywords': _isSingle ? _blacklistKeywords.join(',') : (_blacklistKeywords.isNotEmpty ? _blacklistKeywords.join(',') : null),
        },
        onlyEnabled: switch (widget.mode) {
          BatchChannelMode(scope: SingleAppScope()) => _onlyEnabled,
          _ => false,
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    final l10n = AppLocalizations.of(context)!;
    final titleBottomPadding = 12.0;
    final contentTopPadding = 12.0;
    final contentBottomPadding = 4.0;
    final sectionTitleGap = 6.0;
    final rowGap = 10.0;
    final blockGap = 16.0;
    final scopeGap = 12.0;
    final endGap = 20.0;
    final hasHighlightColor =
        _dynamicHighlightEnabled ||
        (_highlightColor?.trim().isNotEmpty ?? false);

    return _KeyboardInsetPadding(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // ── 拖拽把手 ────────────────────────────────────────────────────
          Container(
            margin: const EdgeInsets.symmetric(vertical: 12),
            width: 32,
            height: 4,
            decoration: BoxDecoration(
              color: cs.onSurfaceVariant.withValues(alpha: 0.4),
              borderRadius: BorderRadius.circular(2),
            ),
          ),

          // ── 标题区 ──────────────────────────────────────────────────────
          Padding(
            padding: EdgeInsets.fromLTRB(24, 0, 24, titleBottomPadding),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _title(l10n),
                  style: text.titleLarge,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 2),
                Text(
                  _subtitle(l10n),
                  style: text.bodySmall?.copyWith(color: cs.onSurfaceVariant),
                ),
              ],
            ),
          ),

          const Divider(height: 1),

          // ── 可滚动内容区 ─────────────────────────────────────────────────
          Flexible(
            child: SingleChildScrollView(
              keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
              padding: EdgeInsets.fromLTRB(
                24,
                contentTopPadding,
                24,
                contentBottomPadding,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // 范围切换卡片（仅 BatchChannelMode + SingleAppScope）
                  if (widget.mode case BatchChannelMode(
                    scope: SingleAppScope(
                      :final totalChannels,
                      :final enabledChannels,
                    ),
                  )) ...[
                    _ScopeToggleCard(
                      totalChannels: totalChannels,
                      enabledChannels: enabledChannels,
                      value: _onlyEnabled,
                      onChanged: enabledChannels > 0
                          ? (v) => setState(() => _onlyEnabled = v)
                          : null,
                    ),
                    SizedBox(height: scopeGap),
                    const Divider(height: 1),
                    SizedBox(height: scopeGap),
                  ],

                  // ── 模板 & 样式设置 ────────────────────────────────────
                  _SectionLabel(l10n.template),
                  SizedBox(height: sectionTitleGap),
                  _BatchSettingRow(
                    label: l10n.template,
                    value: _template,
                    showNotChange: !_isSingle,
                    items: widget.templateLabels.entries
                        .map(
                          (e) => DropdownMenuItem<String?>(
                            value: e.key,
                            child: Text(e.value),
                          ),
                        )
                        .toList(),
                    onChanged: (v) {
                      setState(() => _template = v);
                      _loadFocusSchema();
                    },
                  ),
                  SizedBox(height: rowGap),
                  _BatchSettingRow(
                    label: l10n.rendererLabel,
                    value: _renderer,
                    showNotChange: !_isSingle,
                    items: widget.rendererLabels.entries
                        .map(
                          (e) => DropdownMenuItem<String?>(
                            value: e.key,
                            child: Text(e.value),
                          ),
                        )
                        .toList(),
                    onChanged: (v) {
                      setState(() => _renderer = v);
                      _loadFocusSchema();
                    },
                  ),
                  SizedBox(height: blockGap),

                  SizedBox(height: blockGap),

                  _ExpandableSection(
                    title: l10n.islandSection,
                    icon: Icons.smart_display_rounded,
                    expanded: _islandExpanded,
                    onToggle: () => setState(() => _islandExpanded = !_islandExpanded),
                    children: [
                      SizedBox(height: sectionTitleGap),
                      _BatchSettingRow(
                        label: l10n.islandIcon,
                        value: _iconMode,
                        showNotChange: !_isSingle,
                        items: [
                          DropdownMenuItem(
                            value: kIconModeAuto,
                            child: Text(l10n.iconModeAuto),
                          ),
                          DropdownMenuItem(
                            value: kIconModeNotifSmall,
                            child: Text(l10n.iconModeNotifSmall),
                          ),
                          DropdownMenuItem(
                            value: kIconModeNotifLarge,
                            child: Text(l10n.iconModeNotifLarge),
                          ),
                          DropdownMenuItem(
                            value: kIconModeAppIcon,
                            child: Text(l10n.iconModeAppIcon),
                          ),
                        ],
                        onChanged: (v) => setState(() => _iconMode = v),
                      ),
                      SizedBox(height: rowGap),
                      _BatchSettingRow(
                        label: l10n.islandIconLabel,
                        value: _showIslandIcon,
                        showNotChange: !_isSingle,
                        items: [
                          DropdownMenuItem(
                            value: kTriOptDefault,
                            child: Text(
                              _defaultLabel(context, _ctrl.defaultShowIslandIcon),
                            ),
                          ),
                          DropdownMenuItem(
                            value: kTriOptOn,
                            child: Text(l10n.optOn),
                          ),
                          DropdownMenuItem(
                            value: kTriOptOff,
                            child: Text(l10n.optOff),
                          ),
                        ],
                        onChanged: (v) => setState(() => _showIslandIcon = v),
                      ),
                      SizedBox(height: rowGap),
                      _BatchSettingRow(
                        label: l10n.firstFloatLabel,
                        value: _firstFloat,
                        showNotChange: !_isSingle,
                        items: [
                          DropdownMenuItem(
                            value: kTriOptDefault,
                            child: Text(
                              _defaultLabel(context, _ctrl.defaultFirstFloat),
                            ),
                          ),
                          DropdownMenuItem(
                            value: kTriOptOn,
                            child: Text(l10n.optOn),
                          ),
                          DropdownMenuItem(
                            value: kTriOptOff,
                            child: Text(l10n.optOff),
                          ),
                        ],
                        onChanged: (v) => setState(() => _firstFloat = v),
                      ),
                      SizedBox(height: rowGap),
                      _BatchSettingRow(
                        label: l10n.updateFloatLabel,
                        value: _enableFloat,
                        showNotChange: !_isSingle,
                        items: [
                          DropdownMenuItem(
                            value: kTriOptDefault,
                            child: Text(
                              _defaultLabel(context, _ctrl.defaultEnableFloat),
                            ),
                          ),
                          DropdownMenuItem(
                            value: kTriOptOn,
                            child: Text(l10n.optOn),
                          ),
                          DropdownMenuItem(
                            value: kTriOptOff,
                            child: Text(l10n.optOff),
                          ),
                        ],
                        onChanged: (v) => setState(() => _enableFloat = v),
                      ),
                      SizedBox(height: rowGap),
                      _BatchSettingRow(
                        label: l10n.marqueeChannelTitle,
                        value: _marquee,
                        showNotChange: !_isSingle,
                        items: [
                          DropdownMenuItem(
                            value: kTriOptDefault,
                            child: Text(
                              _defaultLabel(context, _ctrl.defaultMarquee),
                            ),
                          ),
                          DropdownMenuItem(
                            value: kTriOptOn,
                            child: Text(l10n.optOn),
                          ),
                          DropdownMenuItem(
                            value: kTriOptOff,
                            child: Text(l10n.optOff),
                          ),
                        ],
                        onChanged: (v) => setState(() => _marquee = v),
                      ),
                      SizedBox(height: rowGap),
                      _SettingField(
                        label: l10n.autoDisappear,
                        child: TextFormField(
                          controller: _timeoutController,
                          keyboardType: TextInputType.number,
                          textInputAction: TextInputAction.done,
                          scrollPadding: EdgeInsets.zero,
                          onTapOutside: (_) {
                            FocusManager.instance.primaryFocus?.unfocus();
                          },
                          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                          decoration: _fieldDecoration(
                            context,
                            hintText: _isSingle ? null : l10n.noChange,
                            suffixText: _islandTimeout != null
                                ? l10n.seconds
                                : null,
                          ),
                          onChanged: (v) {
                            final trimmed = v.trim();
                            final n = int.tryParse(trimmed);
                            final valid = trimmed.isNotEmpty && n != null && n >= 1;
                            setState(() {
                              if (valid) {
                                _islandTimeout = trimmed;
                              } else if (!_isSingle) {
                                _islandTimeout = null;
                              }
                            });
                          },
                        ),
                      ),
                      SizedBox(height: rowGap),
                      _BatchSettingRow(
                        label: l10n.outerGlowLabel,
                        value: _islandOuterGlow,
                        showNotChange: !_isSingle,
                        items: _outerGlowItems(
                          context,
                          defaultMode: _ctrl.defaultIslandOuterGlow,
                        ),
                        onChanged: (v) => setState(() => _islandOuterGlow = v),
                      ),
                      SizedBox(height: rowGap),
                      _SettingField(
                        label: l10n.outEffectColorLabel,
                        child: ColorValueField(
                          controller: _islandOuterGlowColorController,
                          decoration: _fieldDecoration(
                            context,
                            hintText: _isSingle
                                ? '#AARRGGBB / #RRGGBB'
                                : l10n.noChange,
                          ),
                          previewColor: _parseColor(_islandOuterGlowColor),
                          previewFallbackColor: cs.primary,
                          enabled: !_isFollowDynamicGlow(
                            _islandOuterGlow,
                            _ctrl.defaultIslandOuterGlow,
                          ),
                          readOnly: _isFollowDynamicGlow(
                            _islandOuterGlow,
                            _ctrl.defaultIslandOuterGlow,
                          ),
                          onChanged: (v) {
                            final trimmed = v.trim();
                            setState(() {
                              _islandOuterGlowColor = trimmed.isNotEmpty
                                  ? trimmed
                                  : null;
                            });
                          },
                          onClear: () {
                            _islandOuterGlowColorController.clear();
                            setState(() => _islandOuterGlowColor = null);
                          },
                          onPickColor: () async {
                            final color = await showColorPickerDialog(
                              context,
                              title: '${l10n.outEffectColorLabel} (Island)',
                              initialHex: _islandOuterGlowColor,
                              enableAlpha: true,
                            );
                            if (color != null) {
                              final hex = colorToArgbHex(color);
                              _islandOuterGlowColorController.text = hex;
                              setState(() => _islandOuterGlowColor = hex);
                            }
                          },
                        ),
                      ),
                      SizedBox(height: rowGap),
                      _SettingField(
                        label: l10n.highlightColorLabel,
                        child: ColorValueField(
                          controller: _highlightColorController,
                          enabled: !_dynamicHighlightEnabled,
                          readOnly: _dynamicHighlightEnabled,
                          decoration: _fieldDecoration(
                            context,
                            hintText: _isSingle
                                ? l10n.highlightColorHint
                                : l10n.noChange,
                          ),
                          previewColor: _parseColor(_highlightColor),
                          previewFallbackColor: cs.primary,
                          onChanged: _dynamicHighlightEnabled
                              ? null
                              : (v) {
                                  final trimmed = v.trim();
                                  setState(() {
                                    _highlightColor = trimmed.isNotEmpty
                                        ? trimmed
                                        : null;
                                  });
                                },
                          onClear: () {
                            _highlightColorController.clear();
                            setState(() => _highlightColor = null);
                          },
                          onPickColor: () async {
                            final color = await showColorPickerDialog(
                              context,
                              initialHex: _highlightColor,
                              title: l10n.highlightColorLabel,
                              enableAlpha: true,
                            );
                            if (color != null) {
                              final hex = colorToArgbHex(color);
                              _highlightColorController.text = hex;
                              setState(() => _highlightColor = hex);
                            }
                          },
                        ),
                      ),
                      SizedBox(height: rowGap),
                      _BatchSettingRow(
                        label: l10n.dynamicHighlightColorLabel,
                        value: _dynamicHighlightColor,
                        showNotChange: !_isSingle,
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
                        onChanged: (v) => setState(() {
                          _dynamicHighlightColor = v;
                        }),
                      ),
                      SizedBox(height: rowGap),
                      _SettingField(
                        label: l10n.textHighlightLabel,
                        child: Row(
                          children: [
                            Expanded(
                              child: _HighlightSwitch(
                                label: l10n.showLeftHighlightShort,
                                value: _showLeftHighlight,
                                showNotChange: !_isSingle,
                                onChanged: hasHighlightColor
                                    ? (v) => setState(() => _showLeftHighlight = v)
                                    : null,
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: _HighlightSwitch(
                                label: l10n.showRightHighlightShort,
                                value: _showRightHighlight,
                                showNotChange: !_isSingle,
                                onChanged: hasHighlightColor
                                    ? (v) => setState(() => _showRightHighlight = v)
                                    : null,
                              ),
                            ),
                          ],
                        ),
                      ),
                      SizedBox(height: rowGap),
                      _SettingField(
                        label: l10n.narrowFontLabel,
                        child: Row(
                          children: [
                            Expanded(
                              child: _HighlightSwitch(
                                label: l10n.showLeftHighlightShort,
                                value: _showLeftNarrowFont,
                                showNotChange: !_isSingle,
                                onChanged: (v) =>
                                    setState(() => _showLeftNarrowFont = v),
                              ),
                            ),
                            const SizedBox(width: 12),
                            Expanded(
                              child: _HighlightSwitch(
                                label: l10n.showRightHighlightShort,
                                value: _showRightNarrowFont,
                                showNotChange: !_isSingle,
                                onChanged: (v) =>
                                    setState(() => _showRightNarrowFont = v),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  SizedBox(height: blockGap),

                  if (_isSingle)
                    _ExpandableSection(
                      title: l10n.islandExpressionCustomizationSection,
                      icon: Icons.code_rounded,
                      expanded: _islandCustomExpanded,
                      onToggle: () => setState(() => _islandCustomExpanded = !_islandCustomExpanded),
                      children: [
                        SizedBox(height: sectionTitleGap),
                        if (_loadingIslandSchema)
                          const Padding(
                            padding: EdgeInsets.symmetric(vertical: 8),
                            child: LinearProgressIndicator(minHeight: 2),
                          )
                        else
                          _buildIslandCustomizationFields(),
                      ],
                    ),
                  if (_isSingle) SizedBox(height: blockGap),

                  _ExpandableSection(
                    title: l10n.focusNotificationLabel,
                    icon: Icons.notifications_rounded,
                    expanded: _focusExpanded,
                    onToggle: () => setState(() => _focusExpanded = !_focusExpanded),
                    children: [
                      SizedBox(height: sectionTitleGap),
                      _BatchSettingRow(
                        label: l10n.focusNotificationLabel,
                        value: _focusNotif,
                        showNotChange: !_isSingle,
                        items: [
                          DropdownMenuItem(
                            value: kTriOptDefault,
                            child: Text(
                              _defaultLabel(context, _ctrl.defaultFocusNotif),
                            ),
                          ),
                          DropdownMenuItem(
                            value: kTriOptOn,
                            child: Text(l10n.optOn),
                          ),
                          DropdownMenuItem(
                            value: kTriOptOff,
                            child: Text(l10n.optOff),
                          ),
                        ],
                        onChanged: (v) => setState(() {
                          _focusNotif = v;
                          if (v == kTriOptOff) _preserveSmallIcon = kTriOptOff;
                        }),
                      ),
                      SizedBox(height: rowGap),
                      _BatchSettingRow(
                        label: l10n.preserveStatusBarSmallIconLabel,
                        value: _focusNotif == kTriOptOff
                            ? kTriOptOff
                            : _preserveSmallIcon,
                        showNotChange: !_isSingle,
                        items: [
                          DropdownMenuItem(
                            value: kTriOptDefault,
                            child: Text(
                              _defaultLabel(
                                context,
                                _ctrl.defaultPreserveSmallIcon,
                              ),
                            ),
                          ),
                          DropdownMenuItem(
                            value: kTriOptOn,
                            child: Text(l10n.optOn),
                          ),
                          DropdownMenuItem(
                            value: kTriOptOff,
                            child: Text(l10n.optOff),
                          ),
                        ],
                        onChanged: _focusNotif == kTriOptOff
                            ? null
                            : (v) => setState(() => _preserveSmallIcon = v),
                      ),
                      SizedBox(height: rowGap),
                      _BatchSettingRow(
                        label: l10n.restoreLockscreenTitle,
                        value: _restoreLockscreen,
                        showNotChange: !_isSingle,
                        items: [
                          DropdownMenuItem(
                            value: kTriOptDefault,
                            child: Text(
                              _defaultLabel(
                                context,
                                _ctrl.defaultRestoreLockscreen,
                              ),
                            ),
                          ),
                          DropdownMenuItem(
                            value: kTriOptOn,
                            child: Text(l10n.optOn),
                          ),
                          DropdownMenuItem(
                            value: kTriOptOff,
                            child: Text(l10n.optOff),
                          ),
                        ],
                        onChanged: (v) => setState(() => _restoreLockscreen = v),
                      ),
                      SizedBox(height: rowGap),
                      _BatchSettingRow(
                        label: l10n.outerGlowLabel,
                        value: _outerGlow,
                        showNotChange: !_isSingle,
                        items: _outerGlowItems(
                          context,
                          defaultMode: _ctrl.defaultOuterGlow,
                        ),
                        onChanged: (v) => setState(() => _outerGlow = v),
                      ),
                      SizedBox(height: rowGap),
                      _SettingField(
                        label: l10n.outEffectColorLabel,
                        child: ColorValueField(
                          controller: _outEffectColorController,
                          decoration: _fieldDecoration(
                            context,
                            hintText: _isSingle
                                ? '#AARRGGBB / #RRGGBB'
                                : l10n.noChange,
                          ),
                          previewColor: _parseColor(_outEffectColor),
                          previewFallbackColor: cs.primary,
                          enabled: !_isFollowDynamicGlow(
                            _outerGlow,
                            _ctrl.defaultOuterGlow,
                          ),
                          readOnly: _isFollowDynamicGlow(
                            _outerGlow,
                            _ctrl.defaultOuterGlow,
                          ),
                          onChanged: (v) {
                            final trimmed = v.trim();
                            setState(() {
                              _outEffectColor = trimmed.isNotEmpty ? trimmed : null;
                            });
                          },
                          onClear: () {
                            _outEffectColorController.clear();
                            setState(() => _outEffectColor = null);
                          },
                          onPickColor: () async {
                            final color = await showColorPickerDialog(
                              context,
                              initialHex: _outEffectColor,
                              title: '${l10n.outEffectColorLabel} (Focus)',
                              enableAlpha: true,
                            );
                            if (color != null) {
                              final hex = colorToArgbHex(color);
                              _outEffectColorController.text = hex;
                              setState(() => _outEffectColor = hex);
                            }
                          },
                        ),
                      ),
                    ],
                  ),
                  SizedBox(height: blockGap),

                  if (_isSingle)
                    _ExpandableSection(
                      title: l10n.focusExpressionCustomizationSection,
                      icon: Icons.data_object_rounded,
                      expanded: _focusCustomExpanded,
                      onToggle: () => setState(() => _focusCustomExpanded = !_focusCustomExpanded),
                      children: [
                        SizedBox(height: sectionTitleGap),
                        if (_loadingFocusSchema)
                          const Padding(
                            padding: EdgeInsets.symmetric(vertical: 8),
                            child: LinearProgressIndicator(minHeight: 2),
                          )
                        else
                          _buildFocusCustomizationFields(),
                      ],
                    ),
                  SizedBox(height: blockGap),

                  _ExpandableSection(
                    title: l10n.filterRulesSection,
                    icon: Icons.filter_list_rounded,
                    expanded: _filterExpanded,
                    onToggle: () => setState(() => _filterExpanded = !_filterExpanded),
                    children: [
                      SizedBox(height: sectionTitleGap),
                      _BatchSettingRow(
                        label: l10n.filterModeLabel,
                        value: _filterMode ?? (_isSingle ? 'blacklist' : null),
                        showNotChange: !_isSingle,
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
                        onChanged: (v) => setState(() => _filterMode = v),
                      ),
                      SizedBox(height: rowGap),
                      _KeywordListEditor(
                        label: l10n.whitelistKeywordsLabel,
                        keywords: _whitelistKeywords,
                        enabled: effectiveFilterMode == 'whitelist',
                        onAdd: (kw) => setState(() => _whitelistKeywords = [..._whitelistKeywords, kw]),
                        onRemove: (kw) => setState(() => _whitelistKeywords = _whitelistKeywords.where((k) => k != kw).toList()),
                        hintText: l10n.addKeyword,
                      ),
                      SizedBox(height: rowGap),
                      _KeywordListEditor(
                        label: l10n.blacklistKeywordsLabel,
                        keywords: _blacklistKeywords,
                        enabled: true,
                        onAdd: (kw) => setState(() => _blacklistKeywords = [..._blacklistKeywords, kw]),
                        onRemove: (kw) => setState(() => _blacklistKeywords = _blacklistKeywords.where((k) => k != kw).toList()),
                        hintText: l10n.addKeyword,
                      ),
                      SizedBox(height: rowGap),
                      if (effectiveFilterMode == 'whitelist')
                        Padding(
                          padding: const EdgeInsets.only(top: 4),
                          child: Text(
                            l10n.keywordFilterPriority,
                            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                              color: Theme.of(context).colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ),
                    ],
                  ),
                  SizedBox(height: endGap),
                ],
              ),
            ),
          ),

          // ── 底部按钮区 ───────────────────────────────────────────────────
          Padding(
            padding: EdgeInsets.fromLTRB(
              24,
              8,
              24,
              16 + MediaQuery.paddingOf(context).bottom,
            ),
            child: Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => Navigator.pop(context),
                    style: OutlinedButton.styleFrom(
                      minimumSize: const Size.fromHeight(48),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    child: Text(l10n.cancel),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton(
                    onPressed: _hasAnyChange ? _submit : null,
                    style: FilledButton.styleFrom(
                      minimumSize: const Size.fromHeight(48),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    child: Text(l10n.apply),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _KeyboardInsetPadding extends StatelessWidget {
  const _KeyboardInsetPadding({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.viewInsetsOf(context).bottom;
    return AnimatedPadding(
      duration: const Duration(milliseconds: 180),
      curve: Curves.easeOut,
      padding: EdgeInsets.only(bottom: bottomInset),
      child: child,
    );
  }
}

// ── 分组标题 ──────────────────────────────────────────────────────────────────

class _SectionLabel extends StatelessWidget {
  const _SectionLabel(this.label);
  final String label;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Align(
      alignment: Alignment.centerLeft,
      child: Text(
        label,
        style: Theme.of(context).textTheme.labelMedium?.copyWith(
          color: cs.primary,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

// ── 可折叠分组 ──────────────────────────────────────────────────────────────────

class _ExpandableSection extends StatefulWidget {
  const _ExpandableSection({
    required this.title,
    required this.icon,
    required this.expanded,
    required this.onToggle,
    required this.children,
  });

  final String title;
  final IconData icon;
  final bool expanded;
  final VoidCallback onToggle;
  final List<Widget> children;

  @override
  State<_ExpandableSection> createState() => _ExpandableSectionState();
}

class _ExpandableSectionState extends State<_ExpandableSection>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl;
  late final Animation<double> _heightFactor;
  late final Animation<double> _fade;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      duration: const Duration(milliseconds: 300),
      vsync: this,
    );
    _heightFactor = CurvedAnimation(parent: _ctrl, curve: Curves.easeOutCubic);
    _fade = CurvedAnimation(
      parent: _ctrl,
      curve: Interval(0.2, 1.0, curve: Curves.easeOutCubic),
    );
    if (widget.expanded) _ctrl.value = 1.0;
  }

  @override
  void didUpdateWidget(covariant _ExpandableSection old) {
    super.didUpdateWidget(old);
    if (widget.expanded != old.expanded) {
      if (widget.expanded) {
        _ctrl.forward();
      } else {
        _ctrl.reverse();
      }
    }
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: cs.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Material(
            color: Colors.transparent,
            borderRadius: BorderRadius.vertical(
              top: const Radius.circular(16),
              bottom: widget.expanded ? Radius.zero : const Radius.circular(16),
            ),
            child: InkWell(
              borderRadius: BorderRadius.vertical(
                top: const Radius.circular(16),
                bottom: widget.expanded ? Radius.zero : const Radius.circular(16),
              ),
              onTap: widget.onToggle,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 14, 12, 14),
                child: Row(
                  children: [
                    Icon(widget.icon, color: cs.primary, size: 20),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Text(
                        widget.title,
                        style: text.titleSmall?.copyWith(
                          color: cs.primary,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                    AnimatedRotation(
                      duration: const Duration(milliseconds: 300),
                      curve: Curves.easeOutCubic,
                      turns: widget.expanded ? 0.5 : 0,
                      child: Icon(
                        Icons.expand_more_rounded,
                        color: cs.primary,
                        size: 22,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          ClipRect(
            child: AnimatedBuilder(
              animation: _heightFactor,
              builder: (_, child) {
                return Align(
                  alignment: Alignment.topCenter,
                  heightFactor: _heightFactor.value,
                  child: child,
                );
              },
              child: FadeTransition(
                opacity: _fade,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: widget.children,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ── 应用范围切换卡片 ──────────────────────────────────────────────────────────

class _ScopeToggleCard extends StatelessWidget {
  const _ScopeToggleCard({
    required this.totalChannels,
    required this.enabledChannels,
    required this.value,
    required this.onChanged,
  });

  final int totalChannels;
  final int enabledChannels;
  final bool value;
  final ValueChanged<bool>? onChanged;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    final l10n = AppLocalizations.of(context)!;
    final active = onChanged != null;

    return Material(
      color: cs.surfaceContainerHighest,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: active ? () => onChanged!(!value) : null,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      l10n.onlyEnabledChannels,
                      style: text.bodyMedium?.copyWith(
                        color: active
                            ? null
                            : cs.onSurface.withValues(alpha: 0.38),
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      l10n.enabledChannelsCount(enabledChannels, totalChannels),
                      style: text.bodySmall?.copyWith(
                        color: active
                            ? cs.onSurfaceVariant
                            : cs.onSurface.withValues(alpha: 0.28),
                      ),
                    ),
                  ],
                ),
              ),
              Switch(value: value, onChanged: onChanged),
            ],
          ),
        ),
      ),
    );
  }
}

// ── 设置行（下拉框）──────────────────────────────────────────────────────────

class _BatchSettingRow extends StatelessWidget {
  const _BatchSettingRow({
    required this.label,
    required this.value,
    required this.items,
    required this.onChanged,
    this.showNotChange = true,
  });

  final String label;
  final String? value;
  final List<DropdownMenuItem<String?>> items;
  final ValueChanged<String?>? onChanged;
  final bool showNotChange;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;

    return _SettingField(
      label: label,
      child: DropdownButtonFormField<String?>(
        key: ValueKey(value),
        initialValue: value,
        isExpanded: true,
        items: [
          if (showNotChange)
            DropdownMenuItem<String?>(value: null, child: Text(l10n.noChange)),
          ...items,
        ],
        onChanged: onChanged,
        decoration: _fieldDecoration(context),
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
    fillColor: cs.surfaceContainerHighest,
  );
}

class _KeywordListEditor extends StatefulWidget {
  const _KeywordListEditor({
    required this.label,
    required this.keywords,
    required this.enabled,
    required this.onAdd,
    required this.onRemove,
    required this.hintText,
  });

  final String label;
  final List<String> keywords;
  final bool enabled;
  final ValueChanged<String> onAdd;
  final ValueChanged<String> onRemove;
  final String hintText;

  @override
  State<_KeywordListEditor> createState() => _KeywordListEditorState();
}

class _KeywordListEditorState extends State<_KeywordListEditor> {
  late final TextEditingController _addController;

  @override
  void initState() {
    super.initState();
    _addController = TextEditingController();
  }

  @override
  void dispose() {
    _addController.dispose();
    super.dispose();
  }

void _addKeyword() {
    if (!widget.enabled) return;
    final kw = _addController.text.trim();
    if (kw.isEmpty) return;
    if (widget.keywords.contains(kw)) return;
    widget.onAdd(kw);
    _addController.clear();
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    final enabled = widget.enabled;

    return _SettingField(
      label: widget.label,
      child: Opacity(
        opacity: enabled ? 1.0 : 0.45,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _addController,
                    enabled: enabled,
                    decoration: _fieldDecoration(
                      context,
                      hintText: widget.hintText,
                    ),
                    textInputAction: TextInputAction.done,
                    onSubmitted: enabled ? (_) => _addKeyword() : null,
                  ),
                ),
                const SizedBox(width: 8),
IconButton(
                  onPressed: enabled ? _addKeyword : null,
                  icon: const Icon(Icons.add_rounded),
                  style: IconButton.styleFrom(
                    backgroundColor: cs.primaryContainer,
                    foregroundColor: cs.onPrimaryContainer,
                  ),
                ),
              ],
            ),
            if (widget.keywords.isNotEmpty) ...[
              const SizedBox(height: 8),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: widget.keywords.map((kw) {
                  return InputChip(
                    label: Text(kw, style: text.bodySmall),
                    onDeleted: enabled ? () => widget.onRemove(kw) : null,
                    deleteIconColor: cs.error,
                    materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                  );
                }).toList(),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _HighlightSwitch extends StatelessWidget {
  const _HighlightSwitch({
    required this.label,
    required this.value,
    required this.onChanged,
    this.showNotChange = true,
  });

  final String label;
  final bool? value;
  final ValueChanged<bool?>? onChanged;
  final bool showNotChange;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final enabled = onChanged != null;

    return Material(
      color: enabled
          ? cs.surfaceContainerHighest
          : cs.surfaceContainerHighest.withValues(alpha: 0.45),
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onChanged == null
            ? null
            : () {
                if (showNotChange && value == null) {
                  onChanged!(true);
                } else {
                  onChanged!(!value!);
                }
              },
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                label,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: enabled ? null : cs.onSurfaceVariant,
                ),
              ),
              const SizedBox(width: 8),
              Switch(
                value: value ?? false,
                onChanged: onChanged == null ? null : (v) => onChanged!(v),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
