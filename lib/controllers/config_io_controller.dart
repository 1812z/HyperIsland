import 'dart:convert';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'settings_controller.dart';

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
  /// 将所有 pref_ 开头的设置序列化为 JSON 字符串。
  static Future<String> exportToJson() async {
    final prefs = await SharedPreferences.getInstance();
    final packageInfo = await PackageInfo.fromPlatform();
    final keys = prefs.getKeys().where((k) => k.startsWith('pref_'));
    final Map<String, dynamic> settings = {};
    for (final key in keys) {
      settings[key] = prefs.get(key);
    }
    settings[kPrefConfigAppVersion] = packageInfo.version;
    return const JsonEncoder.withIndent('  ').convert({
      'version': 1,
      'appVersion': packageInfo.version,
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
    final appVersion = decoded['appVersion'];
    if (appVersion is String && appVersion.trim().isNotEmpty) {
      await prefs.setString(kPrefConfigAppVersion, appVersion.trim());
    }
    int count = 0;
    for (final entry in settings.entries) {
      final key = entry.key as String;
      final value = entry.value;
      if (value is bool) {
        await prefs.setBool(key, value);
      } else if (value is int) {
        await prefs.setInt(key, value);
      } else if (value is double) {
        await prefs.setDouble(key, value);
      } else if (value is String) {
        await prefs.setString(key, value);
      }
      count++;
    }
    return count;
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
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['json'],
      withData: false,
      withReadStream: false,
    );
    if (result == null || result.files.isEmpty) {
      throw const ConfigIOException(ConfigIOError.noFileSelected);
    }
    final path = result.files.first.path;
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
