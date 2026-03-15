import 'dart:typed_data';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _channel = MethodChannel('com.example.hyperisland/test');
const kPrefGenericWhitelist = 'pref_generic_whitelist';

/// 可用的灵动岛通知模板标识符。
const kTemplateGenericProgress = 'generic_progress';
const kTemplateDownload = 'download';

// 图标模式选项
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

  const AppInfo({
    required this.packageName,
    required this.appName,
    required this.icon,
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
  Set<String> enabledPackages = {};
  bool loading = true;
  String _searchQuery = '';

  WhitelistController() {
    _load();
  }

  List<AppInfo> get filteredApps {
    final q = _searchQuery.toLowerCase();
    final list = q.isEmpty
        ? List<AppInfo>.from(_allApps)
        : _allApps
            .where((a) =>
                a.appName.toLowerCase().contains(q) ||
                a.packageName.toLowerCase().contains(q))
            .toList();
    // 已启用的应用排在最前，组内按字母序。
    list.sort((a, b) {
      final aOn = enabledPackages.contains(a.packageName);
      final bOn = enabledPackages.contains(b.packageName);
      if (aOn != bOn) return aOn ? -1 : 1;
      return a.appName.compareTo(b.appName);
    });
    return list;
  }

  Future<void> _load() async {
    loading = true;
    notifyListeners();

    try {
      final prefs = await SharedPreferences.getInstance();
      final csv = prefs.getString(kPrefGenericWhitelist) ?? '';
      enabledPackages =
          csv.isEmpty ? {} : csv.split(',').where((s) => s.isNotEmpty).toSet();

      final rawList =
          await _channel.invokeMethod<List<dynamic>>('getInstalledApps') ?? [];
      _allApps = rawList.map((raw) {
        final map = Map<String, dynamic>.from(raw as Map);
        return AppInfo(
          packageName: map['packageName'] as String,
          appName: map['appName'] as String,
          icon: Uint8List.fromList((map['icon'] as List).cast<int>()),
        );
      }).toList();
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
    notifyListeners();
  }

  void setSearch(String query) {
    _searchQuery = query;
    notifyListeners();
  }

  // ── 渠道管理 ──────────────────────────────────────────────────────────────

  /// 获取指定包的通知渠道列表（调用原生）。
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
      return {kTemplateGenericProgress: '通用进度条'};
    }
  }

  /// 批量读取指定包内各渠道的模板设置，返回 channelId → template 映射。
  Future<Map<String, String>> getChannelTemplates(
      String packageName, List<String> channelIds) async {
    final prefs = await SharedPreferences.getInstance();
    return Map.fromEntries(channelIds.map((id) {
      final template =
          prefs.getString('pref_channel_template_${packageName}_$id') ??
              kTemplateGenericProgress;
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

  /// 批量读取各渠道的额外设置，返回 channelId → {icon, focus, first_float, enable_float}。
  Future<Map<String, Map<String, String>>> getChannelExtraSettings(
      String packageName, List<String> channelIds) async {
    final prefs = await SharedPreferences.getInstance();
    return Map.fromEntries(channelIds.map((id) => MapEntry(id, {
          'icon': prefs.getString('pref_channel_icon_${packageName}_$id') ?? kIconModeAuto,
          'focus': prefs.getString('pref_channel_focus_${packageName}_$id') ?? kTriOptDefault,
          'first_float': prefs.getString('pref_channel_first_float_${packageName}_$id') ?? kTriOptDefault,
          'enable_float': prefs.getString('pref_channel_enable_float_${packageName}_$id') ?? kTriOptDefault,
        })));
  }

  Future<void> setChannelIconMode(
      String packageName, String channelId, String value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('pref_channel_icon_${packageName}_$channelId', value);
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
}
