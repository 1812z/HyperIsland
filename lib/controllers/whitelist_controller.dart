import 'dart:typed_data';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _channel = MethodChannel('io.github.hyperisland/test');
const kPrefGenericWhitelist = 'pref_generic_whitelist';

/// 可用的灵动岛通知模板标识符。
const kTemplateGenericProgress        = 'generic_progress';
const kTemplateNotificationIsland     = 'notification_island';
const kTemplateDownload               = 'download';
// OS2 模板
const kTemplateGenericProgressOld     = 'generic_progress_old';
const kTemplateNotificationIslandOld  = 'notification_island_old';

// 图标模式选项（图标样式 & 焦点图标共用）
const kIconModeAuto       = 'auto';
const kIconModeNotifSmall = 'notif_small';
const kIconModeNotifLarge = 'notif_large';
const kIconModeAppIcon    = 'app_icon';

// 三态选项（焦点通知 / 初次展开 / 更新展开）
const kTriOptDefault = 'default';
const kTriOptOn      = 'on';
const kTriOptOff     = 'off';

class AppInfo {
  final String packageName;
  final String appName;
  final Uint8List icon;
  final bool isSystem;

  const AppInfo({
    required this.packageName,
    required this.appName,
    required this.icon,
    this.isSystem = false,
  });
}

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
  List<AppInfo> _allApps = [];
  // 稳定列表：切换开关时不重排，仅 _resort() 时更新
  List<AppInfo> _sortedApps = [];
  Set<String> enabledPackages = {};
  bool loading = true;
  String _searchQuery = '';
  bool showSystemApps = false;

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
  }

  List<AppInfo> get filteredApps {
    final q = _searchQuery.toLowerCase();
    Iterable<AppInfo> source = showSystemApps
        ? _sortedApps
        : _sortedApps.where((a) => !a.isSystem || enabledPackages.contains(a.packageName));
    if (q.isNotEmpty) {
      source = source.where((a) =>
          a.appName.toLowerCase().contains(q) ||
          a.packageName.toLowerCase().contains(q));
    }
    return source is List<AppInfo> ? source : source.toList();
  }

  Future<void> refresh() => _load();

  Future<void> _load() async {
    loading = true;
    notifyListeners();

    try {
      final prefs = await SharedPreferences.getInstance();
      final csv = prefs.getString(kPrefGenericWhitelist) ?? '';
      enabledPackages =
          csv.isEmpty ? {} : csv.split(',').where((s) => s.isNotEmpty).toSet();

      final rawList =
          await _channel.invokeMethod<List<dynamic>>(
              'getInstalledApps', {'includeSystem': true}) ?? [];
      const _excludedPackages = {
        'com.android.providers.downloads',
        'com.xiaomi.android.app.downloadmanager',
        'com.android.systemui',
      };
      _allApps = rawList
          .map((raw) {
            final map = Map<String, dynamic>.from(raw as Map);
            return AppInfo(
              packageName: map['packageName'] as String,
              appName: map['appName'] as String,
              icon: Uint8List.fromList((map['icon'] as List).cast<int>()),
              isSystem: map['isSystem'] as bool? ?? false,
            );
          })
          .where((a) => !_excludedPackages.contains(a.packageName))
          .toList();
      _resort();
    } catch (e) {
      debugPrint('WhitelistController._load error: $e');
    }

    loading = false;
    notifyListeners();
  }

  Future<void> setEnabled(String packageName, bool enabled) async {
    if (enabled) {
      enabledPackages.add(packageName);
    } else {
      enabledPackages.remove(packageName);
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kPrefGenericWhitelist, enabledPackages.join(','));
    // 切换开关：不重排，直接通知
    notifyListeners();
  }

  void setSearch(String query) {
    _searchQuery = query;
    _resort();
    notifyListeners();
  }

  void setShowSystemApps(bool value) {
    showSystemApps = value;
    _resort();
    notifyListeners();
  }

  Future<void> enableAll() async {
    for (final a in filteredApps) enabledPackages.add(a.packageName);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kPrefGenericWhitelist, enabledPackages.join(','));
    notifyListeners();
  }

  Future<void> disableAll() async {
    for (final a in filteredApps) enabledPackages.remove(a.packageName);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kPrefGenericWhitelist, enabledPackages.join(','));
    notifyListeners();
  }

  /// 批量开启或关闭指定包，仅保存一次 prefs。
  Future<void> setEnabledBatch(List<String> packages, bool enabled) async {
    for (final pkg in packages) {
      if (enabled) {
        enabledPackages.add(pkg);
      } else {
        enabledPackages.remove(pkg);
      }
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kPrefGenericWhitelist, enabledPackages.join(','));
    notifyListeners();
  }

  // ── 渠道管理 ──────────────────────────────────────────────────────────────

  /// 获取指定包的通知渠道列表（调用原生）。
  /// 若读取失败（ROOT权限不足），抛出 [PlatformException]，code 为 'ROOT_REQUIRED'。
  Future<List<ChannelInfo>> getChannels(String packageName) async {
    try {
      final rawList = await _channel.invokeMethod<List<dynamic>>(
            'getNotificationChannels', {'packageName': packageName}) ??
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
      String packageName, Set<String> channelIds) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
        'pref_channels_$packageName', channelIds.join(','));
  }

  /// 从原生侧读取所有可用模板，返回 id → 显示名称 的映射。
  Future<Map<String, String>> getTemplates() async {
    try {
      final rawList =
          await _channel.invokeMethod<List<dynamic>>('getTemplates') ?? [];
      return Map.fromEntries(rawList.map((raw) {
        final map = Map<String, dynamic>.from(raw as Map);
        return MapEntry(map['id'] as String, map['name'] as String);
      }));
    } catch (e) {
      debugPrint('getTemplates error: $e');
      return {kTemplateNotificationIsland: '通知超级岛'};
    }
  }

  /// 批量读取指定包内各渠道的模板设置，返回 channelId → template 映射。
  /// [defaultTemplate] 为未配置渠道的回退值，调用方应传入当前 OS 对应的默认模板 ID。
  Future<Map<String, String>> getChannelTemplates(
      String packageName, List<String> channelIds,
      {String defaultTemplate = kTemplateNotificationIsland}) async {
    final prefs = await SharedPreferences.getInstance();
    return Map.fromEntries(channelIds.map((id) {
      final template =
          prefs.getString('pref_channel_template_${packageName}_$id') ??
              defaultTemplate;
      return MapEntry(id, template);
    }));
  }

  /// 保存指定渠道的模板设置。
  Future<void> setChannelTemplate(
      String packageName, String channelId, String template) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
        'pref_channel_template_${packageName}_$channelId', template);
  }

  // ── 渠道级额外设置（图标、焦点通知、初次展开、更新展开）────────────────────

  /// 批量读取各渠道的额外设置，返回 channelId → {icon, focus_icon, focus, first_float, enable_float, timeout}。
  Future<Map<String, Map<String, String>>> getChannelExtraSettings(
      String packageName, List<String> channelIds) async {
    final prefs = await SharedPreferences.getInstance();
    return Map.fromEntries(channelIds.map((id) => MapEntry(id, {
          'icon': prefs.getString('pref_channel_icon_${packageName}_$id') ?? kIconModeAuto,
          'focus_icon': prefs.getString('pref_channel_focus_icon_${packageName}_$id') ?? kIconModeAuto,
          'focus': prefs.getString('pref_channel_focus_${packageName}_$id') ?? kTriOptDefault,
          'first_float': prefs.getString('pref_channel_first_float_${packageName}_$id') ?? kTriOptDefault,
          'enable_float': prefs.getString('pref_channel_enable_float_${packageName}_$id') ?? kTriOptDefault,
          'timeout': prefs.getString('pref_channel_timeout_${packageName}_$id') ?? '5',
        })));
  }

  Future<void> setChannelIconMode(
      String packageName, String channelId, String value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('pref_channel_icon_${packageName}_$channelId', value);
  }

  Future<void> setChannelFocusIconMode(
      String packageName, String channelId, String value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('pref_channel_focus_icon_${packageName}_$channelId', value);
  }

  Future<void> setChannelFocusNotif(
      String packageName, String channelId, String value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('pref_channel_focus_${packageName}_$channelId', value);
  }

  Future<void> setChannelFirstFloat(
      String packageName, String channelId, String value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('pref_channel_first_float_${packageName}_$channelId', value);
  }

  Future<void> setChannelEnableFloat(
      String packageName, String channelId, String value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('pref_channel_enable_float_${packageName}_$channelId', value);
  }

  Future<void> setChannelTimeout(
      String packageName, String channelId, String value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('pref_channel_timeout_${packageName}_$channelId', value);
  }

  /// 批量应用渠道配置到指定渠道列表。
  /// [settings] 中 null 值的 key 表示不更改该项。
  Future<void> batchApplyChannelSettings(
      String packageName,
      List<String> channelIds,
      Map<String, String?> settings) async {
    if (channelIds.isEmpty || settings.values.every((v) => v == null)) return;
    final prefs = await SharedPreferences.getInstance();
    const keyMap = {
      'template':     'pref_channel_template',
      'icon':         'pref_channel_icon',
      'focus_icon':   'pref_channel_focus_icon',
      'focus':        'pref_channel_focus',
      'first_float':  'pref_channel_first_float',
      'enable_float': 'pref_channel_enable_float',
      'timeout':      'pref_channel_timeout',
    };
    final futures = <Future<bool>>[];
    for (final id in channelIds) {
      keyMap.forEach((settingKey, prefPrefix) {
        final value = settings[settingKey];
        if (value != null) {
          futures.add(prefs.setString('${prefPrefix}_${packageName}_$id', value));
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
    if (enabledPackages.isEmpty || settings.values.every((v) => v == null)) return;
    final pkgList = enabledPackages.toList();
    final total   = pkgList.length;

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
