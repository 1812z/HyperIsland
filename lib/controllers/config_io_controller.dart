import 'dart:convert';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../services/app_cache_service.dart';
import '../services/app_config_store.dart';
import '../services/app_info_service.dart';
import 'settings_controller.dart';
import 'whitelist_controller.dart';

enum ConfigIOError {
  invalidFormat,
  noStorageDirectory,
  noFileSelected,
  noFilePath,
  emptyClipboard,
}

class ConfigIOException implements Exception {
  final ConfigIOError error;
  const ConfigIOException(this.error);
}

class ConfigIOController {
  static const _sensitiveExportKeys = {kPrefAiApiKey};

  static const _toastPrefPrefixes = [
    'pref_toast_forward_',
    'pref_toast_block_',
    'pref_toast_show_notification_',
    'pref_toast_show_island_icon_',
    'pref_toast_first_float_',
    'pref_toast_enable_float_',
    'pref_toast_preserve_small_icon_',
    'pref_toast_marquee_',
    'pref_toast_marquee_auto_hide_',
    'pref_toast_timeout_',
    'pref_toast_highlight_color_',
    'pref_toast_dynamic_highlight_color_',
    'pref_toast_show_left_highlight_',
    'pref_toast_show_right_highlight_',
    'pref_toast_outer_glow_',
    'pref_toast_out_effect_color_',
    'pref_toast_island_outer_glow_',
    'pref_toast_island_outer_glow_color_',
    'pref_toast_filter_mode_',
    'pref_toast_filter_whitelist_keywords_',
    'pref_toast_filter_blacklist_keywords_',
  ];

  static const _notificationAppPrefPrefixes = [
    'pref_media_island_enabled_',
    'pref_media_island_normal_notification_',
    'pref_media_outer_glow_',
    'pref_media_out_effect_color_',
    'pref_media_island_outer_glow_',
    'pref_media_island_outer_glow_color_',
  ];

  static const _channelPrefPrefixes = [
    'pref_channels_',
    'pref_channel_template_',
    'pref_channel_renderer_',
    'pref_channel_icon_',
    'pref_channel_focus_',
    'pref_channel_show_notification_',
    'pref_channel_preserve_small_icon_',
    'pref_channel_show_island_icon_',
    'pref_channel_first_float_',
    'pref_channel_enable_float_',
    'pref_channel_timeout_',
    'pref_channel_marquee_',
    'pref_channel_marquee_auto_hide_',
    'pref_channel_restore_lockscreen_',
    'pref_channel_highlight_color_',
    'pref_channel_dynamic_highlight_color_',
    'pref_channel_show_left_highlight_',
    'pref_channel_show_right_highlight_',
    'pref_channel_show_left_narrow_font_',
    'pref_channel_show_right_narrow_font_',
    'pref_channel_outer_glow_',
    'pref_channel_island_outer_glow_',
    'pref_channel_island_outer_glow_color_',
    'pref_channel_out_effect_color_',
    'pref_channel_focus_custom_',
    'pref_channel_island_custom_',
    'pref_channel_aod_text_',
    'pref_channel_aod_custom_',
    'pref_channel_filter_mode_',
    'pref_channel_filter_whitelist_keywords_',
    'pref_channel_filter_blacklist_keywords_',
    'pref_channel_island_enabled_',
  ];

  static bool _matchesPackage(String value, List<String> packageNames) {
    return _matchedPackage(value, packageNames) != null;
  }

  static String? _matchedPackage(String value, List<String> packageNames) {
    for (final packageName in packageNames) {
      if (value == packageName || value.startsWith('${packageName}_')) {
        return packageName;
      }
    }
    return null;
  }

  static bool _isDefaultChannelConfig(String key, Object? value) {
    if (value != 'default') return false;
    for (final prefix in _channelPrefPrefixes) {
      if (prefix == 'pref_channels_') continue;
      if (key.startsWith(prefix)) return true;
    }
    return false;
  }

  static bool _shouldKeepAppConfig(
    String key,
    List<String> packageNamesToKeep,
  ) {
    final appPrefPrefixes = [
      ..._toastPrefPrefixes,
      ..._notificationAppPrefPrefixes,
    ]..sort((a, b) => b.length.compareTo(a.length));
    final channelPrefPrefixes = _channelPrefPrefixes.toList()
      ..sort((a, b) => b.length.compareTo(a.length));

    for (final prefix in appPrefPrefixes) {
      if (!key.startsWith(prefix)) continue;
      return packageNamesToKeep.contains(key.substring(prefix.length));
    }
    for (final prefix in channelPrefPrefixes) {
      if (!key.startsWith(prefix)) continue;
      final rest = key.substring(prefix.length);
      return _matchesPackage(rest, packageNamesToKeep);
    }
    return true;
  }

  static bool _shouldKeepEnabledAppConfig(
    String key,
    List<String> enabledPackages,
    List<String> toastEnabledPackages,
    Map<String, Set<String>> enabledChannelIdsByPackage,
  ) {
    final toastPrefPrefixes = _toastPrefPrefixes.toList()
      ..sort((a, b) => b.length.compareTo(a.length));
    final notificationAppPrefPrefixes = _notificationAppPrefPrefixes.toList()
      ..sort((a, b) => b.length.compareTo(a.length));
    final channelPrefPrefixes = _channelPrefPrefixes.toList()
      ..sort((a, b) => b.length.compareTo(a.length));

    for (final prefix in toastPrefPrefixes) {
      if (!key.startsWith(prefix)) continue;
      return toastEnabledPackages.contains(key.substring(prefix.length));
    }
    for (final prefix in notificationAppPrefPrefixes) {
      if (!key.startsWith(prefix)) continue;
      return enabledPackages.contains(key.substring(prefix.length));
    }
    for (final prefix in channelPrefPrefixes) {
      if (!key.startsWith(prefix)) continue;
      final rest = key.substring(prefix.length);
      if (prefix == 'pref_channels_') {
        return enabledPackages.contains(rest);
      }
      final packageName = _matchedPackage(rest, enabledPackages);
      if (packageName == null || rest.length == packageName.length) {
        return false;
      }
      final channelId = rest.substring(packageName.length + 1);
      final enabledChannelIds = enabledChannelIdsByPackage[packageName];
      return enabledChannelIds == null || enabledChannelIds.contains(channelId);
    }
    return true;
  }

  static Future<int> _removeAppConfigExcept(
    Set<String> packageNamesToKeep,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    final sortedPackageNames = packageNamesToKeep.toList()
      ..sort((a, b) => b.length.compareTo(a.length));
    final keysToRemove = <String>[];
    for (final key in prefs.getKeys()) {
      if (key == kPrefGenericWhitelist) continue;
      if (!_shouldKeepAppConfig(key, sortedPackageNames)) {
        keysToRemove.add(key);
      }
    }

    var count = 0;
    for (final key in keysToRemove) {
      if (await prefs.remove(key)) count++;
    }
    return count;
  }

  static Future<int> cleanUninstalledAppConfig() async {
    await AppConfigStore.ensureMigrated();
    final apps = await AppCacheService.instance.getApps(forceRefresh: true);
    final installedPackages = apps.map((app) => app.packageName).toSet();
    final prefs = await SharedPreferences.getInstance();
    final enabledPackages = (prefs.getString(kPrefGenericWhitelist) ?? '')
        .split(',')
        .where((pkg) => pkg.isNotEmpty)
        .toSet();
    final nextEnabledPackages = enabledPackages.intersection(installedPackages);

    var count = await AppConfigStore.removeAppConfigsExcept(installedPackages);
    count += await _removeAppConfigExcept(installedPackages);
    if (nextEnabledPackages.length != enabledPackages.length) {
      await prefs.setString(
        kPrefGenericWhitelist,
        nextEnabledPackages.join(','),
      );
      count += enabledPackages.length - nextEnabledPackages.length;
    }
    return count;
  }

  static Future<int> cleanDisabledAppConfig() async {
    await AppConfigStore.ensureMigrated();
    final prefs = await SharedPreferences.getInstance();
    final enabledPackages = (prefs.getString(kPrefGenericWhitelist) ?? '')
        .split(',')
        .where((pkg) => pkg.isNotEmpty)
        .toSet();
    var count = await AppConfigStore.cleanDisabledConfigs(enabledPackages);
    final sortedEnabledPackages = enabledPackages.toList()
      ..sort((a, b) => b.length.compareTo(a.length));
    final toastEnabledPackages = <String>{};
    for (final key in prefs.getKeys()) {
      if (!key.startsWith('pref_toast_forward_')) continue;
      if (prefs.getBool(key) == true) {
        toastEnabledPackages.add(key.substring('pref_toast_forward_'.length));
      }
    }
    final sortedToastEnabledPackages = toastEnabledPackages.toList()
      ..sort((a, b) => b.length.compareTo(a.length));
    final enabledChannelIdsByPackage = <String, Set<String>>{};
    for (final packageName in enabledPackages) {
      final csv = prefs.getString('pref_channels_$packageName') ?? '';
      if (csv.isEmpty) continue;
      enabledChannelIdsByPackage[packageName] = csv
          .split(',')
          .where((channelId) => channelId.isNotEmpty)
          .toSet();
    }
    final keysToRemove = <String>[];
    for (final key in prefs.getKeys()) {
      if (key == kPrefGenericWhitelist) continue;
      if (_isDefaultChannelConfig(key, prefs.get(key))) {
        keysToRemove.add(key);
        continue;
      }
      if (!_shouldKeepEnabledAppConfig(
        key,
        sortedEnabledPackages,
        sortedToastEnabledPackages,
        enabledChannelIdsByPackage,
      )) {
        keysToRemove.add(key);
      }
    }

    for (final key in keysToRemove) {
      if (await prefs.remove(key)) count++;
    }
    return count;
  }

  /// 将所有 pref_ 开头的设置序列化为 JSON 字符串。
  static Future<String> exportToJson() async {
    await AppConfigStore.ensureMigrated();
    final prefs = await SharedPreferences.getInstance();
    final appVersion = await AppInfoService.getVersion();
    final keys = prefs.getKeys().where(
      (k) => k.startsWith('pref_') && !_sensitiveExportKeys.contains(k),
    );
    final Map<String, dynamic> settings = {};
    for (final key in keys) {
      settings[key] = prefs.get(key);
    }
    settings[kPrefConfigAppVersion] = appVersion;
    settings[kPrefConfigSchemaVersion] = kConfigSchemaVersion;
    return const JsonEncoder.withIndent('  ').convert({
      'version': kConfigSchemaVersion,
      'appVersion': appVersion,
      'settings': settings,
    });
  }

  /// 从 JSON 字符串恢复所有设置，返回写入的条目数。
  static Future<int> importFromJson(String json) async {
    final dynamic decoded = jsonDecode(json);
    if (decoded is! Map) {
      throw const ConfigIOException(ConfigIOError.invalidFormat);
    }
    final settings = decoded['settings'];
    if (settings is! Map) {
      throw const ConfigIOException(ConfigIOError.invalidFormat);
    }
    final prefs = await SharedPreferences.getInstance();
    final version = decoded['version'] is int ? decoded['version'] as int : 1;
    final appVersion = decoded['appVersion'];
    if (appVersion is String && appVersion.trim().isNotEmpty) {
      await prefs.setString(kPrefConfigAppVersion, appVersion.trim());
    }
    int count = 0;
    count += await AppConfigStore.clearImportedAppConfigKeys(prefs);
    for (final entry in settings.entries) {
      final key = entry.key as String;
      final value = entry.value;
      if (!_isImportKeyValid(key, value)) {
        throw const ConfigIOException(ConfigIOError.invalidFormat);
      }
      if (value is bool) {
        await prefs.setBool(key, value);
        count++;
      } else if (value is int) {
        await prefs.setInt(key, value);
        count++;
      } else if (value is double) {
        await prefs.setDouble(key, value);
        count++;
      } else if (value is String) {
        await prefs.setString(key, value);
        count++;
      }
    }
    if (version < kConfigSchemaVersion) {
      count += await AppConfigStore.migrateLegacyPrefs(prefs, force: true);
    } else {
      await prefs.setInt(kPrefConfigSchemaVersion, kConfigSchemaVersion);
    }
    return count;
  }

  static bool _isImportKeyValid(String key, Object? value) {
    if (!AppConfigStore.isAppConfigKey(key)) return true;
    if (!AppConfigStore.isValidAppConfigKey(key) || value is! String) {
      return false;
    }
    try {
      final decoded = jsonDecode(value);
      return decoded is Map;
    } catch (_) {
      return false;
    }
  }

  /// 导出到 app 外部存储目录，返回文件路径。
  static Future<String> exportToFile() async {
    final json = await exportToJson();
    final dir = await getExternalStorageDirectory();
    if (dir == null) {
      throw const ConfigIOException(ConfigIOError.noStorageDirectory);
    }
    final file = File('${dir.path}/hyperisland_config.json');
    await file.writeAsString(json);
    return file.path;
  }

  /// 导出到剪贴板。
  static Future<void> exportToClipboard() async {
    final json = await exportToJson();
    await Clipboard.setData(ClipboardData(text: json));
  }

  /// 从用户选择的 JSON 文件导入，返回写入的条目数。
  static Future<int> importFromFile() async {
    final pickedFile = await FilePicker.pickFile(
      type: FileType.custom,
      allowedExtensions: ['json'],
    );
    if (pickedFile == null) {
      throw const ConfigIOException(ConfigIOError.noFileSelected);
    }
    final path = pickedFile.path;
    if (path == null) throw const ConfigIOException(ConfigIOError.noFilePath);
    final json = await File(path).readAsString();
    return importFromJson(json);
  }

  /// 从剪贴板导入，返回写入的条目数。
  static Future<int> importFromClipboard() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    if (data?.text == null || data!.text!.isEmpty) {
      throw const ConfigIOException(ConfigIOError.emptyClipboard);
    }
    return importFromJson(data.text!);
  }
}
