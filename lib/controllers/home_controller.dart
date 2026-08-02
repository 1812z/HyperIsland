import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'whitelist_controller.dart';
import '../services/app_config_store.dart';

class HomeController extends ChangeNotifier {
  static const _platform = MethodChannel('io.github.hyperisland/test');

  bool isSending = false;
  bool? moduleActive;
  int? focusProtocolVersion;
  int? lsposedApiVersion;
  String? xposedFrameworkName;
  String? xposedFrameworkVersion;
  bool? hasSystemUiScope;
  String moduleVersion = '';
  String androidVersion = '';
  String systemVersion = '';
  String deviceModel = '';
  int enabledAppCount = 0;
  int enabledToastCount = 0;

  String get lsposedVersion {
    final framework = [
      xposedFrameworkName?.trim(),
      xposedFrameworkVersion?.trim(),
    ].where((part) => part != null && part.isNotEmpty).join(' ');
    final api = lsposedApiVersion;
    if (framework.isEmpty) return api == null || api == 0 ? '' : 'API $api';
    return api == null || api == 0 ? framework : '$framework, API $api';
  }

  HomeController() {
    _checkStatus();
  }

  Future<void> _checkStatus() async {
    int apiVersion = 0;
    bool hookActive = false;
    try {
      apiVersion = await _platform.invokeMethod('getLSPosedApiVersion');
      lsposedApiVersion = apiVersion;
    } catch (_) {
      lsposedApiVersion = 0;
    }

    try {
      final info = await _platform.invokeMapMethod<String, dynamic>(
        'getXposedFrameworkInfo',
      );
      xposedFrameworkName = info?['frameworkName'] as String?;
      xposedFrameworkVersion = info?['frameworkVersion'] as String?;
      final scope = info?['scope'] as List<dynamic>?;
      hasSystemUiScope = scope?.contains('com.android.systemui');
    } catch (_) {
      xposedFrameworkName = null;
      xposedFrameworkVersion = null;
      hasSystemUiScope = null;
    }

    try {
      hookActive = await _platform.invokeMethod('isModuleActive');
    } catch (_) {
      hookActive = false;
    }

    try {
      final int version = await _platform.invokeMethod(
        'getFocusProtocolVersion',
      );
      focusProtocolVersion = version;
    } catch (_) {
      focusProtocolVersion = 0;
    }

    moduleActive =
        hookActive &&
        apiVersion >= 101 &&
        hasSystemUiScope == true &&
        focusProtocolVersion == 3;

    try {
      final info = await _platform.invokeMapMethod<String, dynamic>(
        'getHomeSystemInfo',
      );
      moduleVersion = info?['moduleVersion'] as String? ?? '';
      androidVersion = info?['androidVersion'] as String? ?? '';
      systemVersion = info?['systemVersion'] as String? ?? '';
      deviceModel = info?['deviceModel'] as String? ?? '';
    } catch (_) {}

    try {
      final prefs = await SharedPreferences.getInstance();
      await AppConfigStore.migrateLegacyPrefs(prefs);
      final whitelist = prefs.getString(kPrefGenericWhitelist) ?? '';
      enabledAppCount = whitelist
          .split(',')
          .where((packageName) => packageName.isNotEmpty)
          .length;
      enabledToastCount = prefs.getKeys().where((key) {
        if (!AppConfigStore.isValidAppConfigKey(key)) return false;
        final raw = prefs.getString(key);
        if (raw == null || raw.isEmpty) return false;
        try {
          final config = jsonDecode(raw);
          return config is Map &&
              config['toast'] is Map &&
              (config['toast'] as Map)['forward'] == true;
        } catch (_) {
          return false;
        }
      }).length;
    } catch (_) {}
    notifyListeners();
  }

  Future<void> sendTest() async {
    isSending = true;
    notifyListeners();
    try {
      await _platform.invokeMethod('showTest');
    } on PlatformException catch (_) {
    } finally {
      isSending = false;
      notifyListeners();
    }
  }

  Future<void> sendCustomTest({
    String? title,
    String? content,
    bool clearPrevious = true,
    bool enableFloat = true,
  }) async {
    isSending = true;
    notifyListeners();
    try {
      await _platform.invokeMethod('showCustomTest', {
        'title': title ?? '',
        'content': content ?? '',
        'clearPrevious': clearPrevious,
        'enableFloat': enableFloat,
      });
    } on PlatformException catch (_) {
    } finally {
      isSending = false;
      notifyListeners();
    }
  }
}
