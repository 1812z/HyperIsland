import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../l10n/generated/app_localizations.dart';
import '../services/app_cache_service.dart';
import '../services/app_config_store.dart';

const _channel = MethodChannel('io.github.hyperisland/test');
const kPrefGenericWhitelist = 'pref_generic_whitelist';

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
const kTriOptFollowDynamic = 'follow_dynamic';

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

  static const _channelSettingDefaults = <String, String>{
    'template': kTemplateNotificationIsland,
    'renderer': kRendererImageTextWithButtons4,
    'icon': kIconModeAuto,
    'focus': kTriOptDefault,
    'show_notification': kTriOptOn,
    'preserve_small_icon': kTriOptDefault,
    'show_island_icon': kTriOptDefault,
    'first_float': kTriOptDefault,
    'enable_float': kTriOptDefault,
    'timeout': '5',
    'marquee': kTriOptDefault,
    'marquee_auto_hide': kTriOptDefault,
    'restore_lockscreen': kTriOptDefault,
    'highlight_color': '',
    'dynamic_highlight_color': kTriOptDefault,
    'show_left_highlight': kTriOptOff,
    'show_right_highlight': kTriOptOff,
    'show_left_narrow_font': kTriOptOff,
    'show_right_narrow_font': kTriOptOff,
    'outer_glow': kTriOptDefault,
    'island_outer_glow': kTriOptDefault,
    'island_outer_glow_color': '',
    'out_effect_color': '',
    'focus_custom': '',
    'island_custom': '',
    'aod_text': kTriOptDefault,
    'aod_custom': '',
    'filter_mode': 'blacklist',
    'whitelist_keywords': '',
    'blacklist_keywords': '',
    'island_enabled': 'true',
  };

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
      await AppConfigStore.migrateLegacyPrefs(prefs);
      final csv = prefs.getString(kPrefGenericWhitelist) ?? '';
      enabledPackages = csv.isEmpty
          ? {}
          : csv.split(',').where((s) => s.isNotEmpty).toSet();

      _allApps = await AppCacheService.instance.getApps(forceRefresh: true);
      _toastForwardEnabled
        ..clear()
        ..addEntries(_allApps.map((app) => MapEntry(app.packageName, false)));
      for (final app in _allApps) {
        _toastForwardEnabled[app.packageName] = await AppConfigStore.getToast(
          app.packageName,
          'forward',
          false,
        );
      }
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
    return AppConfigStore.getEnabledChannels(packageName);
  }

  /// 保存启用渠道 ID 集合。空集合表示对全部渠道生效。
  Future<void> setEnabledChannels(
    String packageName,
    Set<String> channelIds,
  ) async {
    await AppConfigStore.setEnabledChannels(packageName, channelIds);
  }

  Future<bool> getToastForwardEnabled(String packageName) async {
    final cached = _toastForwardEnabled[packageName];
    if (cached != null) return cached;
    final value = await AppConfigStore.getToast(packageName, 'forward', false);
    _toastForwardEnabled[packageName] = value;
    return value;
  }

  bool isToastForwardEnabledSync(String packageName) {
    return _toastForwardEnabled[packageName] ?? false;
  }

  Future<void> setToastForwardEnabled(String packageName, bool enabled) async {
    _toastForwardEnabled[packageName] = enabled;
    await AppConfigStore.setToast(packageName, 'forward', enabled, false);
    notifyListeners();
  }

  Future<void> setToastForwardAndBlockOriginal(
    String packageName,
    bool enabled,
  ) async {
    _toastForwardEnabled[packageName] = enabled;
    await AppConfigStore.setToast(packageName, 'forward', enabled, false);
    await AppConfigStore.setToast(packageName, 'block', enabled, false);
    notifyListeners();
  }

  int get toastEnabledCount {
    return _allApps
        .where((a) => isToastForwardEnabledSync(a.packageName))
        .length;
  }

  Future<void> setToastEnabledBatch(List<String> packages, bool enabled) async {
    if (packages.isEmpty) return;
    for (final pkg in packages) {
      _toastForwardEnabled[pkg] = enabled;
      await AppConfigStore.setToast(pkg, 'forward', enabled, false);
      await AppConfigStore.setToast(pkg, 'block', enabled, false);
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
    return AppConfigStore.getToast(packageName, 'block', false);
  }

  Future<void> setToastBlockOriginal(String packageName, bool enabled) async {
    await AppConfigStore.setToast(packageName, 'block', enabled, false);
  }

  Future<bool> getToastShowNotification(String packageName) async {
    return AppConfigStore.getToast(packageName, 'show_notification', false);
  }

  Future<void> setToastShowNotification(
    String packageName,
    bool enabled,
  ) async {
    await AppConfigStore.setToast(
      packageName,
      'show_notification',
      enabled,
      false,
    );
  }

  Future<bool> getToastShowIslandIcon(String packageName) async {
    return AppConfigStore.getToast(packageName, 'show_island_icon', true);
  }

  Future<void> setToastShowIslandIcon(String packageName, bool enabled) async {
    await AppConfigStore.setToast(
      packageName,
      'show_island_icon',
      enabled,
      true,
    );
  }

  Future<String> getToastFirstFloat(String packageName) async {
    return AppConfigStore.getToast(packageName, 'first_float', kTriOptDefault);
  }

  Future<void> setToastFirstFloat(String packageName, String value) async {
    await AppConfigStore.setToast(
      packageName,
      'first_float',
      value,
      kTriOptDefault,
    );
  }

  Future<String> getToastEnableFloat(String packageName) async {
    return AppConfigStore.getToast(packageName, 'enable_float', kTriOptDefault);
  }

  Future<void> setToastEnableFloat(String packageName, String value) async {
    await AppConfigStore.setToast(
      packageName,
      'enable_float',
      value,
      kTriOptDefault,
    );
  }

  Future<String> getToastPreserveSmallIcon(String packageName) async {
    return AppConfigStore.getToast(
      packageName,
      'preserve_small_icon',
      kTriOptDefault,
    );
  }

  Future<void> setToastPreserveSmallIcon(
    String packageName,
    String value,
  ) async {
    await AppConfigStore.setToast(
      packageName,
      'preserve_small_icon',
      value,
      kTriOptDefault,
    );
  }

  Future<String> getToastMarquee(String packageName) async {
    return AppConfigStore.getToast(packageName, 'marquee', kTriOptDefault);
  }

  Future<void> setToastMarquee(String packageName, String value) async {
    await AppConfigStore.setToast(
      packageName,
      'marquee',
      value,
      kTriOptDefault,
    );
  }

  Future<String> getToastMarqueeAutoHide(String packageName) async {
    return AppConfigStore.getToast(
      packageName,
      'marquee_auto_hide',
      kTriOptDefault,
    );
  }

  Future<void> setToastMarqueeAutoHide(String packageName, String value) async {
    await AppConfigStore.setToast(
      packageName,
      'marquee_auto_hide',
      value,
      kTriOptDefault,
    );
  }

  Future<String> getToastTimeout(String packageName) async {
    return AppConfigStore.getToast(packageName, 'timeout', '5');
  }

  Future<void> setToastTimeout(String packageName, String value) async {
    await AppConfigStore.setToast(packageName, 'timeout', value, '5');
  }

  Future<String> getToastHighlightColor(String packageName) async {
    return AppConfigStore.getToast(packageName, 'highlight_color', '');
  }

  Future<void> setToastHighlightColor(String packageName, String value) async {
    await AppConfigStore.setToast(packageName, 'highlight_color', value, '');
  }

  Future<String> getToastDynamicHighlightColor(String packageName) async {
    return AppConfigStore.getToast(
      packageName,
      'dynamic_highlight_color',
      kTriOptDefault,
    );
  }

  Future<void> setToastDynamicHighlightColor(
    String packageName,
    String value,
  ) async {
    await AppConfigStore.setToast(
      packageName,
      'dynamic_highlight_color',
      value,
      kTriOptDefault,
    );
  }

  Future<String> getToastShowLeftHighlight(String packageName) async {
    return AppConfigStore.getToast(
      packageName,
      'show_left_highlight',
      kTriOptOff,
    );
  }

  Future<void> setToastShowLeftHighlight(
    String packageName,
    String value,
  ) async {
    await AppConfigStore.setToast(
      packageName,
      'show_left_highlight',
      value,
      kTriOptOff,
    );
  }

  Future<String> getToastShowRightHighlight(String packageName) async {
    return AppConfigStore.getToast(
      packageName,
      'show_right_highlight',
      kTriOptOff,
    );
  }

  Future<void> setToastShowRightHighlight(
    String packageName,
    String value,
  ) async {
    await AppConfigStore.setToast(
      packageName,
      'show_right_highlight',
      value,
      kTriOptOff,
    );
  }

  Future<String> getToastOuterGlow(String packageName) async {
    return AppConfigStore.getToast(packageName, 'outer_glow', kTriOptDefault);
  }

  Future<void> setToastOuterGlow(String packageName, String value) async {
    await AppConfigStore.setToast(
      packageName,
      'outer_glow',
      value,
      kTriOptDefault,
    );
  }

  Future<String> getToastOutEffectColor(String packageName) async {
    return AppConfigStore.getToast(packageName, 'out_effect_color', '');
  }

  Future<void> setToastOutEffectColor(String packageName, String value) async {
    await AppConfigStore.setToast(packageName, 'out_effect_color', value, '');
  }

  Future<String> getToastIslandOuterGlow(String packageName) async {
    return AppConfigStore.getToast(
      packageName,
      'island_outer_glow',
      kTriOptDefault,
    );
  }

  Future<void> setToastIslandOuterGlow(String packageName, String value) async {
    await AppConfigStore.setToast(
      packageName,
      'island_outer_glow',
      value,
      kTriOptDefault,
    );
  }

  Future<String> getToastIslandOuterGlowColor(String packageName) async {
    return AppConfigStore.getToast(packageName, 'island_outer_glow_color', '');
  }

  Future<void> setToastIslandOuterGlowColor(
    String packageName,
    String value,
  ) async {
    await AppConfigStore.setToast(
      packageName,
      'island_outer_glow_color',
      value,
      '',
    );
  }

  Future<String> getToastFilterMode(String packageName) async {
    return AppConfigStore.getToast(packageName, 'filter_mode', 'blacklist');
  }

  Future<void> setToastFilterMode(String packageName, String value) async {
    await AppConfigStore.setToast(
      packageName,
      'filter_mode',
      value,
      'blacklist',
    );
  }

  Future<List<String>> getToastWhitelistKeywords(String packageName) async {
    return _decodeKeywordList(
      await AppConfigStore.getToast(packageName, 'whitelist_keywords', ''),
    );
  }

  Future<void> setToastWhitelistKeywords(
    String packageName,
    List<String> keywords,
  ) async {
    await AppConfigStore.setToast(
      packageName,
      'whitelist_keywords',
      _encodeKeywordList(keywords),
      '',
    );
  }

  Future<List<String>> getToastBlacklistKeywords(String packageName) async {
    return _decodeKeywordList(
      await AppConfigStore.getToast(packageName, 'blacklist_keywords', ''),
    );
  }

  Future<void> setToastBlacklistKeywords(
    String packageName,
    List<String> keywords,
  ) async {
    await AppConfigStore.setToast(
      packageName,
      'blacklist_keywords',
      _encodeKeywordList(keywords),
      '',
    );
  }

  Future<void> setToastSettingsBatch(
    List<String> packages, {
    bool? forwardEnabled,
    bool? blockOriginal,
    bool? showNotification,
    bool? showIslandIcon,
    String? firstFloat,
    String? enableFloat,
    String? marquee,
    String? marqueeAutoHide,
    String? timeout,
    String? highlightColor,
    String? dynamicHighlightColor,
    String? showLeftHighlight,
    String? showRightHighlight,
    String? outerGlow,
    String? outEffectColor,
    String? islandOuterGlow,
    String? islandOuterGlowColor,
    String? filterMode,
    String? whitelistKeywords,
    String? blacklistKeywords,
  }) async {
    if (packages.isEmpty) return;
    for (final pkg in packages) {
      if (forwardEnabled != null) {
        _toastForwardEnabled[pkg] = forwardEnabled;
        await AppConfigStore.setToast(pkg, 'forward', forwardEnabled, false);
      }
      if (blockOriginal != null) {
        await AppConfigStore.setToast(pkg, 'block', blockOriginal, false);
      }
      if (showNotification != null) {
        await AppConfigStore.setToast(
          pkg,
          'show_notification',
          showNotification,
          false,
        );
      }
      if (showIslandIcon != null) {
        await AppConfigStore.setToast(
          pkg,
          'show_island_icon',
          showIslandIcon,
          true,
        );
      }
      if (firstFloat != null) {
        await AppConfigStore.setToast(
          pkg,
          'first_float',
          firstFloat,
          kTriOptDefault,
        );
      }
      if (enableFloat != null) {
        await AppConfigStore.setToast(
          pkg,
          'enable_float',
          enableFloat,
          kTriOptDefault,
        );
      }
      if (marquee != null) {
        await AppConfigStore.setToast(pkg, 'marquee', marquee, kTriOptDefault);
      }
      if (marqueeAutoHide != null) {
        await AppConfigStore.setToast(
          pkg,
          'marquee_auto_hide',
          marqueeAutoHide,
          kTriOptDefault,
        );
      }
      if (timeout != null) {
        await AppConfigStore.setToast(pkg, 'timeout', timeout, '5');
      }
      if (highlightColor != null) {
        await AppConfigStore.setToast(
          pkg,
          'highlight_color',
          highlightColor,
          '',
        );
      }
      if (dynamicHighlightColor != null) {
        await AppConfigStore.setToast(
          pkg,
          'dynamic_highlight_color',
          dynamicHighlightColor,
          kTriOptDefault,
        );
      }
      if (showLeftHighlight != null) {
        await AppConfigStore.setToast(
          pkg,
          'show_left_highlight',
          showLeftHighlight,
          kTriOptOff,
        );
      }
      if (showRightHighlight != null) {
        await AppConfigStore.setToast(
          pkg,
          'show_right_highlight',
          showRightHighlight,
          kTriOptOff,
        );
      }
      if (outerGlow != null) {
        await AppConfigStore.setToast(
          pkg,
          'outer_glow',
          outerGlow,
          kTriOptDefault,
        );
      }
      if (outEffectColor != null) {
        await AppConfigStore.setToast(
          pkg,
          'out_effect_color',
          outEffectColor,
          '',
        );
      }
      if (islandOuterGlow != null) {
        await AppConfigStore.setToast(
          pkg,
          'island_outer_glow',
          islandOuterGlow,
          kTriOptDefault,
        );
      }
      if (islandOuterGlowColor != null) {
        await AppConfigStore.setToast(
          pkg,
          'island_outer_glow_color',
          islandOuterGlowColor,
          '',
        );
      }
      if (filterMode != null) {
        await AppConfigStore.setToast(
          pkg,
          'filter_mode',
          filterMode,
          'blacklist',
        );
      }
      if (whitelistKeywords != null) {
        await AppConfigStore.setToast(
          pkg,
          'whitelist_keywords',
          whitelistKeywords,
          '',
        );
      }
      if (blacklistKeywords != null) {
        await AppConfigStore.setToast(
          pkg,
          'blacklist_keywords',
          blacklistKeywords,
          '',
        );
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
    final result = <String, String>{};
    for (final id in channelIds) {
      final settings = await AppConfigStore.getChannelSettings(packageName, id);
      result[id] = _normalizeTemplateId(
        settings['template'] ?? kTemplateNotificationIsland,
      );
    }
    return result;
  }

  /// 保存指定渠道的模板设置。
  Future<void> setChannelTemplate(
    String packageName,
    String channelId,
    String template,
  ) async {
    await AppConfigStore.setChannelSetting(
      packageName,
      channelId,
      'template',
      template,
      kTemplateNotificationIsland,
    );
  }

  // ── 渠道级额外设置（图标、焦点通知、初次展开、更新展开）────────────────────

  /// 批量读取各渠道的额外设置，返回 channelId → {icon, focus, preserve_small_icon, first_float, enable_float, timeout, marquee, marquee_auto_hide, highlight_color, dynamic_highlight_color, outer_glow, out_effect_color}。
  Future<Map<String, Map<String, String>>> getChannelExtraSettings(
    String packageName,
    List<String> channelIds,
  ) async {
    final result = <String, Map<String, String>>{};
    for (final id in channelIds) {
      final settings = await AppConfigStore.getChannelSettings(packageName, id);
      result[id] = {
        'icon': settings['icon'] ?? kIconModeAuto,
        'focus': settings['focus'] ?? kTriOptDefault,
        'show_notification': settings['show_notification'] ?? kTriOptOn,
        'preserve_small_icon':
            settings['preserve_small_icon'] ?? kTriOptDefault,
        'first_float': settings['first_float'] ?? kTriOptDefault,
        'enable_float': settings['enable_float'] ?? kTriOptDefault,
        'timeout': settings['timeout'] ?? '5',
        'marquee': settings['marquee'] ?? kTriOptDefault,
        'marquee_auto_hide': settings['marquee_auto_hide'] ?? kTriOptDefault,
        'renderer': settings['renderer'] ?? kRendererImageTextWithButtons4,
        'restore_lockscreen': settings['restore_lockscreen'] ?? kTriOptDefault,
        'highlight_color': settings['highlight_color'] ?? '',
        'dynamic_highlight_color':
            settings['dynamic_highlight_color'] ?? kTriOptDefault,
        'show_left_highlight': settings['show_left_highlight'] ?? kTriOptOff,
        'show_right_highlight': settings['show_right_highlight'] ?? kTriOptOff,
        'show_left_narrow_font':
            settings['show_left_narrow_font'] ?? kTriOptOff,
        'show_right_narrow_font':
            settings['show_right_narrow_font'] ?? kTriOptOff,
        'outer_glow': settings['outer_glow'] ?? kTriOptDefault,
        'island_outer_glow': settings['island_outer_glow'] ?? kTriOptDefault,
        'island_outer_glow_color': settings['island_outer_glow_color'] ?? '',
        'out_effect_color': settings['out_effect_color'] ?? '',
        'focus_custom': settings['focus_custom'] ?? '',
        'island_custom': settings['island_custom'] ?? '',
        'aod_text': settings['aod_text'] ?? kTriOptDefault,
        'aod_custom': settings['aod_custom'] ?? '',
        'filter_mode': settings['filter_mode'] ?? 'blacklist',
        'whitelist_keywords': settings['whitelist_keywords'] ?? '',
        'blacklist_keywords': settings['blacklist_keywords'] ?? '',
        'island_enabled': settings['island_enabled'] ?? 'true',
      };
    }
    return result;
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

  Future<Map<String, dynamic>?> getAodCustomizationSchema(
    String templateId,
  ) async {
    try {
      final raw = await _channel.invokeMethod<dynamic>(
        'getAodCustomizationSchema',
        {'templateId': templateId},
      );
      if (raw is Map) {
        return Map<String, dynamic>.from(raw.cast<String, dynamic>());
      }
    } catch (e) {
      debugPrint('getAodCustomizationSchema error: $e');
    }
    return null;
  }

  Future<String> mergeAodCustomizationDefaults(
    String templateId,
    String? config,
  ) async {
    try {
      final merged = await _channel.invokeMethod<String>(
        'mergeAodCustomizationDefaults',
        {'templateId': templateId, 'config': config},
      );
      return merged ?? '{}';
    } catch (e) {
      debugPrint('mergeAodCustomizationDefaults error: $e');
      return config?.isNotEmpty == true ? config! : '{}';
    }
  }

  Future<void> _setChannelSetting(
    String packageName,
    String channelId,
    String field,
    String value,
    String defaultValue,
  ) {
    return AppConfigStore.setChannelSetting(
      packageName,
      channelId,
      field,
      value,
      defaultValue,
    );
  }

  Future<void> setChannelIconMode(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'icon',
      value,
      kIconModeAuto,
    );
  }

  Future<void> setChannelFocusNotif(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'focus',
      value,
      kTriOptDefault,
    );
  }

  Future<void> setChannelPreserveSmallIcon(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'preserve_small_icon',
      value,
      kTriOptDefault,
    );
  }

  Future<void> setChannelShowNotification(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'show_notification',
      value,
      kTriOptOn,
    );
  }

  Future<void> setChannelFirstFloat(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'first_float',
      value,
      kTriOptDefault,
    );
  }

  Future<void> setChannelEnableFloat(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'enable_float',
      value,
      kTriOptDefault,
    );
  }

  Future<void> setChannelTimeout(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(packageName, channelId, 'timeout', value, '5');
  }

  Future<void> setChannelMarquee(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'marquee',
      value,
      kTriOptDefault,
    );
  }

  static List<String> _decodeKeywordList(String raw) {
    return raw
        .split('\n')
        .map((s) => s.trim())
        .where((s) => s.isNotEmpty)
        .toSet()
        .toList();
  }

  static String _encodeKeywordList(List<String> keywords) {
    return keywords
        .map((s) => s.trim())
        .where((s) => s.isNotEmpty)
        .toSet()
        .join('\n');
  }

  Future<void> setChannelMarqueeAutoHide(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'marquee_auto_hide',
      value,
      kTriOptDefault,
    );
  }

  Future<void> setChannelRestoreLockscreen(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'restore_lockscreen',
      value,
      kTriOptDefault,
    );
  }

  Future<void> setChannelShowIslandIcon(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'show_island_icon',
      value,
      kTriOptDefault,
    );
  }

  Future<void> setChannelRenderer(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'renderer',
      value,
      kRendererImageTextWithButtons4,
    );
  }

  Future<void> setChannelHighlightColor(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'highlight_color',
      value,
      '',
    );
  }

  Future<void> setChannelDynamicHighlightColor(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'dynamic_highlight_color',
      value,
      kTriOptDefault,
    );
  }

  Future<void> setChannelShowLeftHighlight(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'show_left_highlight',
      value,
      kTriOptOff,
    );
  }

  Future<void> setChannelShowRightHighlight(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'show_right_highlight',
      value,
      kTriOptOff,
    );
  }

  Future<void> setChannelShowLeftNarrowFont(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'show_left_narrow_font',
      value,
      kTriOptOff,
    );
  }

  Future<void> setChannelShowRightNarrowFont(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'show_right_narrow_font',
      value,
      kTriOptOff,
    );
  }

  Future<void> setChannelOuterGlow(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'outer_glow',
      value,
      kTriOptDefault,
    );
  }

  Future<void> setChannelIslandOuterGlow(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'island_outer_glow',
      value,
      kTriOptDefault,
    );
  }

  Future<void> setChannelOutEffectColor(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'out_effect_color',
      value,
      '',
    );
  }

  Future<void> setChannelIslandOuterGlowColor(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'island_outer_glow_color',
      value,
      '',
    );
  }

  Future<Map<String, String>> getMediaIslandSettings(String packageName) async {
    return {
      'enabled':
          await AppConfigStore.getNotification(packageName, 'enabled', true) ==
              false
          ? kTriOptOff
          : kTriOptOn,
      'normal_notification':
          await AppConfigStore.getNotification(
                packageName,
                'normal_notification',
                false,
              ) ==
              true
          ? kTriOptOn
          : kTriOptOff,
      'outer_glow': await AppConfigStore.getNotification(
        packageName,
        'outer_glow',
        kTriOptDefault,
      ),
      'out_effect_color': await AppConfigStore.getNotification(
        packageName,
        'out_effect_color',
        '',
      ),
      'island_outer_glow': await AppConfigStore.getNotification(
        packageName,
        'island_outer_glow',
        kTriOptDefault,
      ),
      'island_outer_glow_color': await AppConfigStore.getNotification(
        packageName,
        'island_outer_glow_color',
        '',
      ),
    };
  }

  Future<void> setMediaIslandEnabled(String packageName, bool value) async {
    await AppConfigStore.setNotification(packageName, 'enabled', value, true);
  }

  Future<void> setMediaIslandNormalNotification(
    String packageName,
    bool value,
  ) async {
    await AppConfigStore.setNotification(
      packageName,
      'normal_notification',
      value,
      false,
    );
  }

  Future<void> setMediaIslandOuterGlow(String packageName, String value) async {
    await AppConfigStore.setNotification(
      packageName,
      'island_outer_glow',
      value,
      kTriOptDefault,
    );
  }

  Future<void> setMediaOuterGlow(String packageName, String value) async {
    await AppConfigStore.setNotification(
      packageName,
      'outer_glow',
      value,
      kTriOptDefault,
    );
  }

  Future<void> setMediaOutEffectColor(String packageName, String value) async {
    await AppConfigStore.setNotification(
      packageName,
      'out_effect_color',
      value,
      '',
    );
  }

  Future<void> setMediaIslandOuterGlowColor(
    String packageName,
    String value,
  ) async {
    await AppConfigStore.setNotification(
      packageName,
      'island_outer_glow_color',
      value,
      '',
    );
  }

  Future<void> setChannelFocusCustomization(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(packageName, channelId, 'focus_custom', value, '');
  }

  Future<void> setChannelIslandCustomization(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'island_custom',
      value,
      '',
    );
  }

  Future<void> setChannelAodText(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'aod_text',
      value,
      kTriOptDefault,
    );
  }

  Future<void> setChannelAodCustomization(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(packageName, channelId, 'aod_custom', value, '');
  }

  Future<void> setChannelFilterMode(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'filter_mode',
      value,
      'blacklist',
    );
  }

  Future<void> setChannelWhitelistKeywords(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'whitelist_keywords',
      value,
      '',
    );
  }

  Future<void> setChannelBlacklistKeywords(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'blacklist_keywords',
      value,
      '',
    );
  }

  Future<void> setChannelIslandEnabled(
    String packageName,
    String channelId,
    String value,
  ) async {
    await _setChannelSetting(
      packageName,
      channelId,
      'island_enabled',
      value,
      'true',
    );
  }

  /// 批量应用渠道配置到指定渠道列表。
  /// [settings] 中 null 值的 key 表示不更改该项。
  Future<void> batchApplyChannelSettings(
    String packageName,
    List<String> channelIds,
    Map<String, String?> settings,
  ) async {
    if (channelIds.isEmpty || settings.values.every((v) => v == null)) return;
    await AppConfigStore.mergeChannelSettings(
      packageName,
      channelIds,
      settings,
      _channelSettingDefaults,
    );
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
