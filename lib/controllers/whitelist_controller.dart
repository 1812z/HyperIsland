import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../l10n/generated/app_localizations.dart';
import '../services/app_cache_service.dart';

const _channel = MethodChannel('io.github.hyperisland/test');
const kPrefGenericWhitelist = 'pref_generic_whitelist';
String _prefToastForwardKey(String packageName) =>
    'pref_toast_forward_$packageName';
String _prefToastBlockKey(String packageName) =>
    'pref_toast_block_$packageName';
String _prefToastShowNotificationKey(String packageName) =>
    'pref_toast_show_notification_$packageName';
String _prefToastShowIslandIconKey(String packageName) =>
    'pref_toast_show_island_icon_$packageName';

/// 可用的灵动岛通知模板标识符。
const kTemplateGenericProgress = 'generic_progress';
const kTemplateNotificationIsland = 'notification_island';
const kTemplateDownload = 'download';
const kTemplateAiNotificationIsland = 'ai_notification_island';

/// 可用的灵动岛渲染器（样式）标识符。
const kRendererImageTextWithButtons4 = 'image_text_with_buttons_4';
const kRendererImageTextWithButtons4Wrap = 'image_text_with_buttons_4_wrap';
const kRendererImageTextWithRightTextButton =
    'image_text_with_right_text_button';
const kRendererImageTextWithProgress = 'image_text_with_progress';

// 图标模式选项（图标样式 & 焦点图标共用）
const kIconModeAuto = 'auto';
const kIconModeNotifSmall = 'notif_small';
const kIconModeNotifLarge = 'notif_large';
const kIconModeAppIcon = 'app_icon';

// 三态选项（焦点通知 / 初次展开 / 更新展开）
const kTriOptDefault = 'default';
const kTriOptOn = 'on';
const kTriOptOff = 'off';

class ChannelInfo {
  final String id;
  final String name;
  final String description;
  final int importance;

  const ChannelInfo({
    required this.id,
    required this.name,
    required this.description,
    required this.importance,
  });
}

class WhitelistController extends ChangeNotifier {
  static String _normalizeTemplateId(String template) {
    switch (template) {
      case 'notification_island':
        return kTemplateNotificationIsland;
      case 'download_lite':
        return kTemplateGenericProgress;
      default:
        return template;
    }
  }

  List<AppInfo> _allApps = [];
  // 稳定列表：切换开关时不重排，仅 _resort() 时更新
  List<AppInfo> _sortedApps = [];
  List<AppInfo> _filteredApps = [];
  Set<String> enabledPackages = {};
  bool loading = true;
  String _searchQuery = '';
  bool showSystemApps = false;
  final Map<String, bool> _toastForwardEnabled = {};

  WhitelistController() {
    _load();
  }

  void _resort() {
    _sortedApps = List<AppInfo>.from(_allApps)
      ..sort((a, b) {
        final aOn = enabledPackages.contains(a.packageName);
        final bOn = enabledPackages.contains(b.packageName);
        if (aOn != bOn) return aOn ? -1 : 1;
        return a.appName.compareTo(b.appName);
      });
    _rebuildFilteredApps();
  }

  void _rebuildFilteredApps() {
    final q = _searchQuery.trim().toLowerCase();
    Iterable<AppInfo> source = showSystemApps
        ? _sortedApps
        : _sortedApps.where(
            (a) => !a.isSystem || enabledPackages.contains(a.packageName),
          );
    if (q.isNotEmpty) {
      source = source.where(
        (a) => a.appNameLower.contains(q) || a.packageNameLower.contains(q),
      );
    }
    _filteredApps = source is List<AppInfo> ? source : source.toList();
  }

  List<AppInfo> get filteredApps => _filteredApps;

  Future<void> refresh() => _load();

  Future<void> _load() async {
    loading = true;
    notifyListeners();

    try {
      final prefs = await SharedPreferences.getInstance();
      final csv = prefs.getString(kPrefGenericWhitelist) ?? '';
      enabledPackages = csv.isEmpty
          ? {}
          : csv.split(',').where((s) => s.isNotEmpty).toSet();

      _allApps = await AppCacheService.instance.getApps();
      _toastForwardEnabled
        ..clear()
        ..addEntries(
          _allApps.map(
            (app) => MapEntry(
              app.packageName,
              prefs.getBool(_prefToastForwardKey(app.packageName)) ?? false,
            ),
          ),
        );
      _resort();
    } catch (e) {
      debugPrint('WhitelistController._load error: $e');
    }

    loading = false;
    notifyListeners();
  }

  Future<void> setEnabled(String packageName, bool enabled) async {
    final changed = enabled
        ? enabledPackages.add(packageName)
        : enabledPackages.remove(packageName);
    if (!changed) return;

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kPrefGenericWhitelist, enabledPackages.join(','));
    _rebuildFilteredApps();
    notifyListeners();
  }

  void setSearch(String query) {
    if (_searchQuery == query) return;
    _searchQuery = query;
    _rebuildFilteredApps();
    notifyListeners();
  }

  void setShowSystemApps(bool value) {
    if (showSystemApps == value) return;
    showSystemApps = value;
    _rebuildFilteredApps();
    notifyListeners();
  }

  Future<void> enableAll() async {
    var changed = false;
    for (final a in filteredApps) {
      if (enabledPackages.add(a.packageName)) {
        changed = true;
      }
    }
    if (!changed) return;

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kPrefGenericWhitelist, enabledPackages.join(','));
    _rebuildFilteredApps();
    notifyListeners();
  }

  Future<void> disableAll() async {
    var changed = false;
    for (final a in filteredApps) {
      if (enabledPackages.remove(a.packageName)) {
        changed = true;
      }
    }
    if (!changed) return;

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kPrefGenericWhitelist, enabledPackages.join(','));
    _rebuildFilteredApps();
    notifyListeners();
  }

  /// 批量开启或关闭指定包，仅保存一次 prefs。
  Future<void> setEnabledBatch(List<String> packages, bool enabled) async {
    var changed = false;
    for (final pkg in packages) {
      if (enabled) {
        if (enabledPackages.add(pkg)) {
          changed = true;
        }
      } else {
        if (enabledPackages.remove(pkg)) {
          changed = true;
        }
      }
    }
    if (!changed) return;

    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kPrefGenericWhitelist, enabledPackages.join(','));
    _rebuildFilteredApps();
    notifyListeners();
  }

  // ── 渠道管理 ──────────────────────────────────────────────────────────────

  /// 获取指定包的通知渠道列表（调用原生）。
  /// 若读取失败（ROOT权限不足），抛出 [PlatformException]，code 为 'ROOT_REQUIRED'。
  Future<List<ChannelInfo>> getChannels(String packageName) async {
    try {
      final rawList =
          await _channel.invokeMethod<List<dynamic>>(
            'getNotificationChannels',
            {'packageName': packageName},
          ) ??
          [];
      return rawList.map((raw) {
        final map = Map<String, dynamic>.from(raw as Map);
        return ChannelInfo(
          id: map['id'] as String,
          name: map['name'] as String? ?? map['id'] as String,
          description: map['description'] as String? ?? '',
          importance: map['importance'] as int? ?? 3,
        );
      }).toList();
    } on PlatformException {
      rethrow;
    } catch (e) {
      debugPrint('getChannels error: $e');
      return [];
    }
  }

  /// 读取已保存的启用渠道 ID 集合。空集合表示对全部渠道生效。
  Future<Set<String>> getEnabledChannels(String packageName) async {
    final prefs = await SharedPreferences.getInstance();
    final csv = prefs.getString('pref_channels_$packageName') ?? '';
    return csv.isEmpty ? {} : csv.split(',').where((s) => s.isNotEmpty).toSet();
  }

  /// 保存启用渠道 ID 集合。空集合表示对全部渠道生效。
  Future<void> setEnabledChannels(
    String packageName,
    Set<String> channelIds,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('pref_channels_$packageName', channelIds.join(','));
  }

  Future<bool> getToastForwardEnabled(String packageName) async {
    final cached = _toastForwardEnabled[packageName];
    if (cached != null) return cached;
    final prefs = await SharedPreferences.getInstance();
    final value = prefs.getBool(_prefToastForwardKey(packageName)) ?? false;
    _toastForwardEnabled[packageName] = value;
    return value;
  }

  bool isToastForwardEnabledSync(String packageName) {
    return _toastForwardEnabled[packageName] ?? false;
  }

  Future<void> setToastForwardEnabled(String packageName, bool enabled) async {
    _toastForwardEnabled[packageName] = enabled;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_prefToastForwardKey(packageName), enabled);
    notifyListeners();
  }

  int get toastEnabledCount {
    return _allApps
        .where((a) => isToastForwardEnabledSync(a.packageName))
        .length;
  }

  Future<void> setToastEnabledBatch(List<String> packages, bool enabled) async {
    if (packages.isEmpty) return;
    final prefs = await SharedPreferences.getInstance();
    for (final pkg in packages) {
      _toastForwardEnabled[pkg] = enabled;
      await prefs.setBool(_prefToastForwardKey(pkg), enabled);
    }
    notifyListeners();
  }

  Future<void> enableAllToast() async {
    await setToastEnabledBatch(
      filteredApps.map((a) => a.packageName).toList(),
      true,
    );
  }

  Future<void> disableAllToast() async {
    await setToastEnabledBatch(
      filteredApps.map((a) => a.packageName).toList(),
      false,
    );
  }

  Future<bool> getToastBlockOriginal(String packageName) async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_prefToastBlockKey(packageName)) ?? true;
  }

  Future<void> setToastBlockOriginal(String packageName, bool enabled) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_prefToastBlockKey(packageName), enabled);
  }

  Future<bool> getToastShowNotification(String packageName) async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_prefToastShowNotificationKey(packageName)) ?? false;
  }

  Future<void> setToastShowNotification(
    String packageName,
    bool enabled,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_prefToastShowNotificationKey(packageName), enabled);
  }

  Future<bool> getToastShowIslandIcon(String packageName) async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_prefToastShowIslandIconKey(packageName)) ?? true;
  }

  Future<void> setToastShowIslandIcon(String packageName, bool enabled) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_prefToastShowIslandIconKey(packageName), enabled);
  }

  Future<void> setToastSettingsBatch(
    List<String> packages, {
    bool? forwardEnabled,
    bool? blockOriginal,
    bool? showNotification,
    bool? showIslandIcon,
  }) async {
    if (packages.isEmpty) return;
    final prefs = await SharedPreferences.getInstance();
    for (final pkg in packages) {
      if (forwardEnabled != null) {
        _toastForwardEnabled[pkg] = forwardEnabled;
        await prefs.setBool(_prefToastForwardKey(pkg), forwardEnabled);
      }
      if (blockOriginal != null) {
        await prefs.setBool(_prefToastBlockKey(pkg), blockOriginal);
      }
      if (showNotification != null) {
        await prefs.setBool(
          _prefToastShowNotificationKey(pkg),
          showNotification,
        );
      }
      if (showIslandIcon != null) {
        await prefs.setBool(_prefToastShowIslandIconKey(pkg), showIslandIcon);
      }
    }
    if (forwardEnabled != null) {
      notifyListeners();
    }
  }

  /// 返回所有可用模板的 id → 显示名称 映射（从 ARB 本地化字符串构建）。
  Map<String, String> getTemplates(AppLocalizations l10n) => {
    kTemplateGenericProgress: l10n.templateDownloadName,
    kTemplateNotificationIsland: l10n.templateNotificationIslandName,
    kTemplateAiNotificationIsland: l10n.templateAiNotificationIslandName,
  };

  /// 返回所有可用渲染器（样式）的 id → 显示名称 映射。
  Map<String, String> getRenderers(AppLocalizations l10n) => {
    kRendererImageTextWithButtons4: l10n.rendererImageTextWithButtons4Name,
    kRendererImageTextWithButtons4Wrap: l10n.rendererCoverInfoName,
    kRendererImageTextWithRightTextButton:
        l10n.rendererImageTextWithRightTextButtonName,
    kRendererImageTextWithProgress: l10n.rendererImageTextWithProgressName,
  };

  /// 批量读取指定包内各渠道的模板设置，返回 channelId → template 映射。
  Future<Map<String, String>> getChannelTemplates(
    String packageName,
    List<String> channelIds,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    return Map.fromEntries(
      channelIds.map((id) {
        final template =
            prefs.getString('pref_channel_template_${packageName}_$id') ??
            kTemplateNotificationIsland;
        return MapEntry(id, _normalizeTemplateId(template));
      }),
    );
  }

  /// 保存指定渠道的模板设置。
  Future<void> setChannelTemplate(
    String packageName,
    String channelId,
    String template,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      'pref_channel_template_${packageName}_$channelId',
      template,
    );
  }

  // ── 渠道级额外设置（图标、焦点通知、初次展开、更新展开）────────────────────

  /// 批量读取各渠道的额外设置，返回 channelId → {icon, focus, preserve_small_icon, first_float, enable_float, timeout, marquee, highlight_color, dynamic_highlight_color, outer_glow, out_effect_color}。
  Future<Map<String, Map<String, String>>> getChannelExtraSettings(
    String packageName,
    List<String> channelIds,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    return Map.fromEntries(
      channelIds.map(
        (id) => MapEntry(id, {
          'icon':
              prefs.getString('pref_channel_icon_${packageName}_$id') ??
              kIconModeAuto,
          'focus':
              prefs.getString('pref_channel_focus_${packageName}_$id') ??
              kTriOptDefault,
          'preserve_small_icon':
              prefs.getString(
                'pref_channel_preserve_small_icon_${packageName}_$id',
              ) ??
              kTriOptDefault,
          'first_float':
              prefs.getString('pref_channel_first_float_${packageName}_$id') ??
              kTriOptDefault,
          'enable_float':
              prefs.getString('pref_channel_enable_float_${packageName}_$id') ??
              kTriOptDefault,
          'timeout':
              prefs.getString('pref_channel_timeout_${packageName}_$id') ?? '5',
          'marquee':
              prefs.getString('pref_channel_marquee_${packageName}_$id') ??
              kTriOptDefault,
          'renderer':
              prefs.getString('pref_channel_renderer_${packageName}_$id') ??
              kRendererImageTextWithButtons4,
          'restore_lockscreen':
              prefs.getString(
                'pref_channel_restore_lockscreen_${packageName}_$id',
              ) ??
              kTriOptDefault,
          'highlight_color':
              prefs.getString(
                'pref_channel_highlight_color_${packageName}_$id',
              ) ??
              '',
          'dynamic_highlight_color':
              prefs.getString(
                'pref_channel_dynamic_highlight_color_${packageName}_$id',
              ) ??
              kTriOptDefault,
          'show_left_highlight':
              prefs.getString(
                'pref_channel_show_left_highlight_${packageName}_$id',
              ) ??
              kTriOptOff,
          'show_right_highlight':
              prefs.getString(
                'pref_channel_show_right_highlight_${packageName}_$id',
              ) ??
              kTriOptOff,
          'show_left_narrow_font':
              prefs.getString(
                'pref_channel_show_left_narrow_font_${packageName}_$id',
              ) ??
              kTriOptOff,
          'show_right_narrow_font':
              prefs.getString(
                'pref_channel_show_right_narrow_font_${packageName}_$id',
              ) ??
              kTriOptOff,
          'outer_glow':
              prefs.getString('pref_channel_outer_glow_${packageName}_$id') ??
              kTriOptDefault,
          'out_effect_color':
              prefs.getString(
                'pref_channel_out_effect_color_${packageName}_$id',
              ) ??
              '',
          'focus_custom':
              prefs.getString('pref_channel_focus_custom_${packageName}_$id') ??
              '',
          'island_custom':
              prefs.getString(
                'pref_channel_island_custom_${packageName}_$id',
              ) ??
              '',
        }),
      ),
    );
  }

  Future<Map<String, dynamic>?> getFocusCustomizationSchema(
    String templateId,
    String rendererId,
  ) async {
    try {
      final raw = await _channel.invokeMethod<dynamic>(
        'getFocusCustomizationSchema',
        {'templateId': templateId, 'rendererId': rendererId},
      );
      if (raw is Map) {
        return Map<String, dynamic>.from(raw.cast<String, dynamic>());
      }
    } catch (e) {
      debugPrint('getFocusCustomizationSchema error: $e');
    }
    return null;
  }

  Future<String> mergeFocusCustomizationDefaults(
    String templateId,
    String rendererId,
    String? config,
  ) async {
    try {
      final merged = await _channel.invokeMethod<String>(
        'mergeFocusCustomizationDefaults',
        {'templateId': templateId, 'rendererId': rendererId, 'config': config},
      );
      return merged ?? '{}';
    } catch (e) {
      debugPrint('mergeFocusCustomizationDefaults error: $e');
      return config?.isNotEmpty == true ? config! : '{}';
    }
  }

  Future<Map<String, dynamic>?> getIslandCustomizationSchema(
    String templateId,
  ) async {
    try {
      final raw = await _channel.invokeMethod<dynamic>(
        'getIslandCustomizationSchema',
        {'templateId': templateId},
      );
      if (raw is Map) {
        return Map<String, dynamic>.from(raw.cast<String, dynamic>());
      }
    } catch (e) {
      debugPrint('getIslandCustomizationSchema error: $e');
    }
    return null;
  }

  Future<String> mergeIslandCustomizationDefaults(
    String templateId,
    String? config,
  ) async {
    try {
      final merged = await _channel.invokeMethod<String>(
        'mergeIslandCustomizationDefaults',
        {'templateId': templateId, 'config': config},
      );
      return merged ?? '{}';
    } catch (e) {
      debugPrint('mergeIslandCustomizationDefaults error: $e');
      return config?.isNotEmpty == true ? config! : '{}';
    }
  }

  Future<void> setChannelIconMode(
    String packageName,
    String channelId,
    String value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('pref_channel_icon_${packageName}_$channelId', value);
  }

  Future<void> setChannelFocusNotif(
    String packageName,
    String channelId,
    String value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      'pref_channel_focus_${packageName}_$channelId',
      value,
    );
  }

  Future<void> setChannelPreserveSmallIcon(
    String packageName,
    String channelId,
    String value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      'pref_channel_preserve_small_icon_${packageName}_$channelId',
      value,
    );
  }

  Future<void> setChannelFirstFloat(
    String packageName,
    String channelId,
    String value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      'pref_channel_first_float_${packageName}_$channelId',
      value,
    );
  }

  Future<void> setChannelEnableFloat(
    String packageName,
    String channelId,
    String value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      'pref_channel_enable_float_${packageName}_$channelId',
      value,
    );
  }

  Future<void> setChannelTimeout(
    String packageName,
    String channelId,
    String value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      'pref_channel_timeout_${packageName}_$channelId',
      value,
    );
  }

  Future<void> setChannelMarquee(
    String packageName,
    String channelId,
    String value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      'pref_channel_marquee_${packageName}_$channelId',
      value,
    );
  }

  Future<void> setChannelRestoreLockscreen(
    String packageName,
    String channelId,
    String value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      'pref_channel_restore_lockscreen_${packageName}_$channelId',
      value,
    );
  }

  Future<void> setChannelShowIslandIcon(
    String packageName,
    String channelId,
    String value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      'pref_channel_show_island_icon_${packageName}_$channelId',
      value,
    );
  }

  Future<void> setChannelRenderer(
    String packageName,
    String channelId,
    String value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      'pref_channel_renderer_${packageName}_$channelId',
      value,
    );
  }

  Future<void> setChannelHighlightColor(
    String packageName,
    String channelId,
    String value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    final key = 'pref_channel_highlight_color_${packageName}_$channelId';
    if (value.isEmpty) {
      await prefs.remove(key);
    } else {
      await prefs.setString(key, value);
    }
  }

  Future<void> setChannelDynamicHighlightColor(
    String packageName,
    String channelId,
    String value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      'pref_channel_dynamic_highlight_color_${packageName}_$channelId',
      value,
    );
  }

  Future<void> setChannelShowLeftHighlight(
    String packageName,
    String channelId,
    String value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      'pref_channel_show_left_highlight_${packageName}_$channelId',
      value,
    );
  }

  Future<void> setChannelShowRightHighlight(
    String packageName,
    String channelId,
    String value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      'pref_channel_show_right_highlight_${packageName}_$channelId',
      value,
    );
  }

  Future<void> setChannelShowLeftNarrowFont(
    String packageName,
    String channelId,
    String value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      'pref_channel_show_left_narrow_font_${packageName}_$channelId',
      value,
    );
  }

  Future<void> setChannelShowRightNarrowFont(
    String packageName,
    String channelId,
    String value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      'pref_channel_show_right_narrow_font_${packageName}_$channelId',
      value,
    );
  }

  Future<void> setChannelOuterGlow(
    String packageName,
    String channelId,
    String value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      'pref_channel_outer_glow_${packageName}_$channelId',
      value,
    );
  }

  Future<void> setChannelOutEffectColor(
    String packageName,
    String channelId,
    String value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    final key = 'pref_channel_out_effect_color_${packageName}_$channelId';
    if (value.isEmpty) {
      await prefs.remove(key);
    } else {
      await prefs.setString(key, value);
    }
  }

  Future<void> setChannelFocusCustomization(
    String packageName,
    String channelId,
    String value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    final key = 'pref_channel_focus_custom_${packageName}_$channelId';
    if (value.isEmpty) {
      await prefs.remove(key);
    } else {
      await prefs.setString(key, value);
    }
  }

  Future<void> setChannelIslandCustomization(
    String packageName,
    String channelId,
    String value,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    final key = 'pref_channel_island_custom_${packageName}_$channelId';
    if (value.isEmpty) {
      await prefs.remove(key);
    } else {
      await prefs.setString(key, value);
    }
  }

  /// 批量应用渠道配置到指定渠道列表。
  /// [settings] 中 null 值的 key 表示不更改该项。
  Future<void> batchApplyChannelSettings(
    String packageName,
    List<String> channelIds,
    Map<String, String?> settings,
  ) async {
    if (channelIds.isEmpty || settings.values.every((v) => v == null)) return;
    final prefs = await SharedPreferences.getInstance();
    const keyMap = {
      'template': 'pref_channel_template',
      'renderer': 'pref_channel_renderer',
      'icon': 'pref_channel_icon',
      'focus': 'pref_channel_focus',
      'preserve_small_icon': 'pref_channel_preserve_small_icon',
      'show_island_icon': 'pref_channel_show_island_icon',
      'first_float': 'pref_channel_first_float',
      'enable_float': 'pref_channel_enable_float',
      'timeout': 'pref_channel_timeout',
      'marquee': 'pref_channel_marquee',
      'restore_lockscreen': 'pref_channel_restore_lockscreen',
      'highlight_color': 'pref_channel_highlight_color',
      'dynamic_highlight_color': 'pref_channel_dynamic_highlight_color',
      'show_left_highlight': 'pref_channel_show_left_highlight',
      'show_right_highlight': 'pref_channel_show_right_highlight',
      'show_left_narrow_font': 'pref_channel_show_left_narrow_font',
      'show_right_narrow_font': 'pref_channel_show_right_narrow_font',
      'outer_glow': 'pref_channel_outer_glow',
      'out_effect_color': 'pref_channel_out_effect_color',
      'focus_custom': 'pref_channel_focus_custom',
      'island_custom': 'pref_channel_island_custom',
    };
    final futures = <Future<bool>>[];
    for (final id in channelIds) {
      keyMap.forEach((settingKey, prefPrefix) {
        final value = settings[settingKey];
        if (value != null) {
          futures.add(
            prefs.setString('${prefPrefix}_${packageName}_$id', value),
          );
        }
      });
    }
    await Future.wait(futures);
  }

  /// 对全部已启用应用的所有渠道批量应用配置。
  ///
  /// 逐包获取渠道列表（需要 ROOT），跳过无法读取的包。
  /// [onProgress] 每处理一个包后回调，参数为已处理数量与总数。
  Future<void> batchApplyToAllEnabledApps(
    Map<String, String?> settings, {
    void Function(int done, int total)? onProgress,
  }) async {
    if (enabledPackages.isEmpty || settings.values.every((v) => v == null)) {
      return;
    }
    final pkgList = enabledPackages.toList();
    final total = pkgList.length;

    for (var i = 0; i < total; i++) {
      final pkg = pkgList[i];
      try {
        final channels = await getChannels(pkg);
        final ids = channels.map((c) => c.id).toList();
        if (ids.isNotEmpty) {
          await batchApplyChannelSettings(pkg, ids, settings);
        }
      } catch (_) {
        // ROOT 不足或其他原因无法读取时跳过该应用
      }
      onProgress?.call(i + 1, total);
    }
  }
}
