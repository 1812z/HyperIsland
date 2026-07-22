import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../controllers/whitelist_controller.dart';

const kConfigSchemaVersion = 2;
const kPrefConfigSchemaVersion = 'pref_config_schema_version';
const kPrefAppConfigPrefix = 'pref_app_config_';

class AppConfigStore {
  const AppConfigStore._();

  static String appConfigKey(String packageName) =>
      '$kPrefAppConfigPrefix$packageName';

  static bool isAppConfigKey(String key) =>
      key.startsWith(kPrefAppConfigPrefix);

  static String packageNameFromAppConfigKey(String key) =>
      key.substring(kPrefAppConfigPrefix.length);

  static bool isValidAppConfigKey(String key) {
    if (!isAppConfigKey(key)) return false;
    return _isValidPackageName(packageNameFromAppConfigKey(key));
  }

  static bool _isValidPackageName(String packageName) {
    if (packageName.isEmpty) return false;
    return RegExp(r'^[A-Za-z0-9._]+$').hasMatch(packageName);
  }

  static Map<String, dynamic> _emptyConfig() => <String, dynamic>{};

  static Map<String, dynamic> _readConfig(
    SharedPreferences prefs,
    String packageName,
  ) {
    final raw = prefs.getString(appConfigKey(packageName));
    if (raw == null || raw.isEmpty) return _emptyConfig();
    try {
      final decoded = jsonDecode(raw);
      if (decoded is Map) {
        return Map<String, dynamic>.from(decoded);
      }
    } catch (_) {}
    return _emptyConfig();
  }

  static Future<bool> _writeConfig(
    SharedPreferences prefs,
    String packageName,
    Map<String, dynamic> config,
  ) {
    if (config.isEmpty) return prefs.remove(appConfigKey(packageName));
    return prefs.setString(appConfigKey(packageName), jsonEncode(config));
  }

  static Map<String, dynamic> _section(
    Map<String, dynamic> config,
    String name,
  ) {
    final existing = config[name];
    if (existing is Map) return Map<String, dynamic>.from(existing);
    return <String, dynamic>{};
  }

  static Map<String, dynamic> _channels(Map<String, dynamic> config) =>
      _section(config, 'channels');

  static Map<String, dynamic> _channelSettings(Map<String, dynamic> channels) {
    final existing = channels['settings'];
    if (existing is Map) return Map<String, dynamic>.from(existing);
    return <String, dynamic>{};
  }

  static Future<void> ensureMigrated() async {
    final prefs = await SharedPreferences.getInstance();
    await migrateLegacyPrefs(prefs);
  }

  static Future<int> migrateLegacyPrefs(
    SharedPreferences prefs, {
    bool force = false,
  }) async {
    final current = prefs.getInt(kPrefConfigSchemaVersion) ?? 1;
    if (!force && current >= kConfigSchemaVersion) return 0;

    final packages = <String>{};
    final whitelistCsv = prefs.getString(kPrefGenericWhitelist) ?? '';
    packages.addAll(
      whitelistCsv.split(',').where((packageName) => packageName.isNotEmpty),
    );
    for (final key in prefs.getKeys()) {
      if (isAppConfigKey(key)) {
        final packageName = packageNameFromAppConfigKey(key);
        if (packageName.isNotEmpty) packages.add(packageName);
        continue;
      }
      final packageName = _legacyPackageNameFromKey(key);
      if (packageName != null && packageName.isNotEmpty) {
        packages.add(packageName);
      }
    }
    final sortedPackages = packages.toList()
      ..sort((a, b) => b.length.compareTo(a.length));
    for (final key in prefs.getKeys()) {
      final packageName = _legacyPackageNameFromChannelKey(key, sortedPackages);
      if (packageName != null && packageName.isNotEmpty) {
        packages.add(packageName);
      }
    }
    sortedPackages
      ..clear()
      ..addAll(packages)
      ..sort((a, b) => b.length.compareTo(a.length));

    var count = 0;
    for (final packageName in packages) {
      final config = _readConfig(prefs, packageName);

      final toast = _section(config, 'toast');
      for (final entry in _toastLegacyFields.entries) {
        final key = '${entry.value.prefix}$packageName';
        if (!prefs.containsKey(key)) continue;
        final value = prefs.get(key);
        if (_putIfNonDefault(
          toast,
          entry.key,
          value,
          entry.value.defaultValue,
        )) {
          count++;
        }
      }
      if (toast.isEmpty) {
        config.remove('toast');
      } else {
        config['toast'] = toast;
      }

      final notification = _section(config, 'notification');
      for (final entry in _notificationLegacyFields.entries) {
        final key = '${entry.value.prefix}$packageName';
        if (!prefs.containsKey(key)) continue;
        final value = prefs.get(key);
        if (_putIfNonDefault(
          notification,
          entry.key,
          value,
          entry.value.defaultValue,
        )) {
          count++;
        }
      }
      if (notification.isEmpty) {
        config.remove('notification');
      } else {
        config['notification'] = notification;
      }

      final channels = _channels(config);
      final channelCsv = prefs.getString('pref_channels_$packageName');
      if (channelCsv != null && channelCsv.isNotEmpty) {
        channels['enabled'] = channelCsv
            .split(',')
            .where((channelId) => channelId.isNotEmpty)
            .toList();
        count++;
      }

      final settings = _channelSettings(channels);
      for (final key in prefs.getKeys()) {
        final field = _channelLegacyFieldForKey(key, packageName);
        if (field == null) continue;
        final value = prefs.get(key);
        final channelMap = Map<String, dynamic>.from(
          settings[field.channelId] is Map
              ? settings[field.channelId] as Map
              : {},
        );
        if (_putIfNonDefault(
          channelMap,
          field.name,
          value,
          field.defaultValue,
        )) {
          settings[field.channelId] = channelMap;
          count++;
        }
      }
      if (settings.isNotEmpty) channels['settings'] = settings;
      if (channels.isEmpty) {
        config.remove('channels');
      } else {
        config['channels'] = channels;
      }

      if (config.isNotEmpty) {
        await _writeConfig(prefs, packageName, config);
      }
    }

    for (final key in prefs.getKeys()) {
      if (_isLegacyAppConfigKey(key)) {
        await prefs.remove(key);
      }
    }
    await prefs.setInt(kPrefConfigSchemaVersion, kConfigSchemaVersion);
    return count;
  }

  static bool _putIfNonDefault(
    Map<String, dynamic> target,
    String field,
    Object? value,
    Object? defaultValue,
  ) {
    if (value == null || value == defaultValue) {
      target.remove(field);
      return false;
    }
    target[field] = value;
    return true;
  }

  static Future<T> getToast<T>(
    String packageName,
    String field,
    T defaultValue,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    final toast = _section(_readConfig(prefs, packageName), 'toast');
    final value = toast[field];
    return value is T ? value : defaultValue;
  }

  static Future<void> setToast(
    String packageName,
    String field,
    Object? value,
    Object? defaultValue,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    final config = _readConfig(prefs, packageName);
    final toast = _section(config, 'toast');
    _putIfNonDefault(toast, field, value, defaultValue);
    if (toast.isEmpty) {
      config.remove('toast');
    } else {
      config['toast'] = toast;
    }
    await _writeConfig(prefs, packageName, config);
  }

  static Future<Set<String>> getEnabledChannels(String packageName) async {
    final prefs = await SharedPreferences.getInstance();
    final channels = _channels(_readConfig(prefs, packageName));
    final enabled = channels['enabled'];
    if (enabled is List) {
      return enabled.whereType<String>().where((id) => id.isNotEmpty).toSet();
    }
    return {};
  }

  static Future<void> setEnabledChannels(
    String packageName,
    Set<String> channelIds,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    final config = _readConfig(prefs, packageName);
    final channels = _channels(config);
    if (channelIds.isEmpty) {
      channels.remove('enabled');
    } else {
      channels['enabled'] = channelIds.toList();
    }
    if (channels.isEmpty) {
      config.remove('channels');
    } else {
      config['channels'] = channels;
    }
    await _writeConfig(prefs, packageName, config);
  }

  static Future<T> getNotification<T>(
    String packageName,
    String field,
    T defaultValue,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    final section = _section(_readConfig(prefs, packageName), 'notification');
    final value = section[field];
    return value is T ? value : defaultValue;
  }

  static Future<void> setNotification(
    String packageName,
    String field,
    Object? value,
    Object? defaultValue,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    final config = _readConfig(prefs, packageName);
    final notification = _section(config, 'notification');
    _putIfNonDefault(notification, field, value, defaultValue);
    if (notification.isEmpty) {
      config.remove('notification');
    } else {
      config['notification'] = notification;
    }
    await _writeConfig(prefs, packageName, config);
  }

  static Future<Map<String, String>> getChannelSettings(
    String packageName,
    String channelId,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    final channels = _channels(_readConfig(prefs, packageName));
    final settings = _channelSettings(channels);
    final raw = settings[channelId];
    if (raw is! Map) return <String, String>{};
    return raw.map(
      (key, value) => MapEntry(key.toString(), value?.toString() ?? ''),
    );
  }

  static Future<void> setChannelSetting(
    String packageName,
    String channelId,
    String field,
    String value,
    String defaultValue,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    await setChannelSettingWithPrefs(
      prefs,
      packageName,
      channelId,
      field,
      value,
      defaultValue,
    );
  }

  static Future<void> setChannelSettingWithPrefs(
    SharedPreferences prefs,
    String packageName,
    String channelId,
    String field,
    String value,
    String defaultValue,
  ) async {
    final config = _readConfig(prefs, packageName);
    final channels = _channels(config);
    final settings = _channelSettings(channels);
    final channelMap = Map<String, dynamic>.from(
      settings[channelId] is Map ? settings[channelId] as Map : {},
    );
    _putIfNonDefault(channelMap, field, value, defaultValue);
    if (channelMap.isEmpty) {
      settings.remove(channelId);
    } else {
      settings[channelId] = channelMap;
    }
    if (settings.isEmpty) {
      channels.remove('settings');
    } else {
      channels['settings'] = settings;
    }
    if (channels.isEmpty) {
      config.remove('channels');
    } else {
      config['channels'] = channels;
    }
    await _writeConfig(prefs, packageName, config);
  }

  static Future<void> mergeChannelSettings(
    String packageName,
    List<String> channelIds,
    Map<String, String?> settings,
    Map<String, String> defaults,
  ) async {
    final prefs = await SharedPreferences.getInstance();
    for (final channelId in channelIds) {
      final config = _readConfig(prefs, packageName);
      final channels = _channels(config);
      final allSettings = _channelSettings(channels);
      final channelMap = Map<String, dynamic>.from(
        allSettings[channelId] is Map ? allSettings[channelId] as Map : {},
      );
      for (final entry in settings.entries) {
        final value = entry.value;
        if (value == null) continue;
        _putIfNonDefault(
          channelMap,
          entry.key,
          value,
          defaults[entry.key] ?? '',
        );
      }
      if (channelMap.isEmpty) {
        allSettings.remove(channelId);
      } else {
        allSettings[channelId] = channelMap;
      }
      if (allSettings.isEmpty) {
        channels.remove('settings');
      } else {
        channels['settings'] = allSettings;
      }
      if (channels.isEmpty) {
        config.remove('channels');
      } else {
        config['channels'] = channels;
      }
      await _writeConfig(prefs, packageName, config);
    }
  }

  static Future<int> removeAppConfigsExcept(Set<String> packagesToKeep) async {
    final prefs = await SharedPreferences.getInstance();
    var count = 0;
    for (final key in prefs.getKeys()) {
      if (!isAppConfigKey(key)) continue;
      if (!packagesToKeep.contains(packageNameFromAppConfigKey(key))) {
        if (await prefs.remove(key)) count++;
      }
    }
    return count;
  }

  static Future<int> cleanDisabledConfigs(Set<String> enabledPackages) async {
    final prefs = await SharedPreferences.getInstance();
    var count = 0;
    for (final key in prefs.getKeys()) {
      if (!isAppConfigKey(key)) continue;
      final packageName = packageNameFromAppConfigKey(key);
      final config = _readConfig(prefs, packageName);

      final toast = _section(config, 'toast');
      if (toast['forward'] != true && config.remove('toast') != null) {
        count++;
      }

      if (!enabledPackages.contains(packageName)) {
        if (config.remove('notification') != null) count++;
        if (config.remove('channels') != null) count++;
        await _writeConfig(prefs, packageName, config);
        continue;
      }

      final channels = _channels(config);
      final enabled = channels['enabled'];
      final settings = _channelSettings(channels);
      if (enabled is List && settings.isNotEmpty) {
        final enabledSet = enabled.whereType<String>().toSet();
        final before = settings.length;
        settings.removeWhere((channelId, _) => !enabledSet.contains(channelId));
        if (settings.length != before) {
          if (settings.isEmpty) {
            channels.remove('settings');
          } else {
            channels['settings'] = settings;
          }
          config['channels'] = channels;
          await _writeConfig(prefs, packageName, config);
          count += before - settings.length;
        }
      }
      await _writeConfig(prefs, packageName, config);
    }
    return count;
  }

  static bool isLegacyAppConfigKey(String key) => _isLegacyAppConfigKey(key);

  static Future<int> clearImportedAppConfigKeys(SharedPreferences prefs) async {
    var count = 0;
    for (final key in prefs.getKeys()) {
      if (isAppConfigKey(key) || _isLegacyAppConfigKey(key)) {
        if (await prefs.remove(key)) count++;
      }
    }
    return count;
  }

  static String? _legacyPackageNameFromKey(String key) {
    for (final field in _toastLegacyFields.values) {
      if (key.startsWith(field.prefix)) {
        return key.substring(field.prefix.length);
      }
    }
    for (final field in _notificationLegacyFields.values) {
      if (key.startsWith(field.prefix)) {
        return key.substring(field.prefix.length);
      }
    }
    if (key.startsWith('pref_channels_')) {
      return key.substring('pref_channels_'.length);
    }
    return null;
  }

  static bool _isLegacyAppConfigKey(String key) {
    if (_legacyPackageNameFromKey(key) != null) return true;
    return _channelLegacyFields.values.any(
      (field) => key.startsWith(field.prefix),
    );
  }

  static String? _legacyPackageNameFromChannelKey(
    String key,
    List<String> sortedPackageNames,
  ) {
    for (final field in _channelLegacyFields.values) {
      if (!key.startsWith(field.prefix)) continue;
      final rest = key.substring(field.prefix.length);
      for (final packageName in sortedPackageNames) {
        if (rest.startsWith('${packageName}_')) return packageName;
      }
    }
    return null;
  }

  static _ParsedChannelField? _channelLegacyFieldForKey(
    String key,
    String packageName,
  ) {
    for (final entry in _channelLegacyFields.entries) {
      final restPrefix = '${entry.value.prefix}${packageName}_';
      if (!key.startsWith(restPrefix)) continue;
      final rest = key.substring(restPrefix.length);
      return _ParsedChannelField(
        entry.key,
        rest,
        entry.value.defaultValue as String,
      );
    }
    return null;
  }

  static const _toastLegacyFields = <String, _LegacyField>{
    'forward': _LegacyField('pref_toast_forward_', false),
    'block': _LegacyField('pref_toast_block_', false),
    'show_notification': _LegacyField('pref_toast_show_notification_', false),
    'show_island_icon': _LegacyField('pref_toast_show_island_icon_', true),
    'first_float': _LegacyField('pref_toast_first_float_', kTriOptDefault),
    'enable_float': _LegacyField('pref_toast_enable_float_', kTriOptDefault),
    'preserve_small_icon': _LegacyField(
      'pref_toast_preserve_small_icon_',
      kTriOptDefault,
    ),
    'marquee': _LegacyField('pref_toast_marquee_', kTriOptDefault),
    'marquee_auto_hide': _LegacyField(
      'pref_toast_marquee_auto_hide_',
      kTriOptDefault,
    ),
    'timeout': _LegacyField('pref_toast_timeout_', '5'),
    'highlight_color': _LegacyField('pref_toast_highlight_color_', ''),
    'dynamic_highlight_color': _LegacyField(
      'pref_toast_dynamic_highlight_color_',
      kTriOptDefault,
    ),
    'show_left_highlight': _LegacyField(
      'pref_toast_show_left_highlight_',
      kTriOptOff,
    ),
    'show_right_highlight': _LegacyField(
      'pref_toast_show_right_highlight_',
      kTriOptOff,
    ),
    'outer_glow': _LegacyField('pref_toast_outer_glow_', kTriOptDefault),
    'out_effect_color': _LegacyField('pref_toast_out_effect_color_', ''),
    'island_outer_glow': _LegacyField(
      'pref_toast_island_outer_glow_',
      kTriOptDefault,
    ),
    'island_outer_glow_color': _LegacyField(
      'pref_toast_island_outer_glow_color_',
      '',
    ),
    'filter_mode': _LegacyField('pref_toast_filter_mode_', 'blacklist'),
    'whitelist_keywords': _LegacyField(
      'pref_toast_filter_whitelist_keywords_',
      '',
    ),
    'blacklist_keywords': _LegacyField(
      'pref_toast_filter_blacklist_keywords_',
      '',
    ),
  };

  static const _notificationLegacyFields = <String, _LegacyField>{
    'enabled': _LegacyField('pref_media_island_enabled_', true),
    'normal_notification': _LegacyField(
      'pref_media_island_normal_notification_',
      false,
    ),
    'outer_glow': _LegacyField('pref_media_outer_glow_', kTriOptDefault),
    'out_effect_color': _LegacyField('pref_media_out_effect_color_', ''),
    'island_outer_glow': _LegacyField(
      'pref_media_island_outer_glow_',
      kTriOptDefault,
    ),
    'island_outer_glow_color': _LegacyField(
      'pref_media_island_outer_glow_color_',
      '',
    ),
  };

  static const _channelLegacyFields = <String, _LegacyField>{
    'template': _LegacyField(
      'pref_channel_template_',
      kTemplateNotificationIsland,
    ),
    'renderer': _LegacyField(
      'pref_channel_renderer_',
      kRendererImageTextWithButtons4,
    ),
    'icon': _LegacyField('pref_channel_icon_', kIconModeAuto),
    'focus': _LegacyField('pref_channel_focus_', kTriOptDefault),
    'show_notification': _LegacyField(
      'pref_channel_show_notification_',
      kTriOptOn,
    ),
    'preserve_small_icon': _LegacyField(
      'pref_channel_preserve_small_icon_',
      kTriOptDefault,
    ),
    'show_island_icon': _LegacyField(
      'pref_channel_show_island_icon_',
      kTriOptDefault,
    ),
    'first_float': _LegacyField('pref_channel_first_float_', kTriOptDefault),
    'enable_float': _LegacyField('pref_channel_enable_float_', kTriOptDefault),
    'timeout': _LegacyField('pref_channel_timeout_', '5'),
    'marquee': _LegacyField('pref_channel_marquee_', kTriOptDefault),
    'marquee_auto_hide': _LegacyField(
      'pref_channel_marquee_auto_hide_',
      kTriOptDefault,
    ),
    'restore_lockscreen': _LegacyField(
      'pref_channel_restore_lockscreen_',
      kTriOptDefault,
    ),
    'highlight_color': _LegacyField('pref_channel_highlight_color_', ''),
    'dynamic_highlight_color': _LegacyField(
      'pref_channel_dynamic_highlight_color_',
      kTriOptDefault,
    ),
    'show_left_highlight': _LegacyField(
      'pref_channel_show_left_highlight_',
      kTriOptOff,
    ),
    'show_right_highlight': _LegacyField(
      'pref_channel_show_right_highlight_',
      kTriOptOff,
    ),
    'show_left_narrow_font': _LegacyField(
      'pref_channel_show_left_narrow_font_',
      kTriOptOff,
    ),
    'show_right_narrow_font': _LegacyField(
      'pref_channel_show_right_narrow_font_',
      kTriOptOff,
    ),
    'outer_glow': _LegacyField('pref_channel_outer_glow_', kTriOptDefault),
    'island_outer_glow': _LegacyField(
      'pref_channel_island_outer_glow_',
      kTriOptDefault,
    ),
    'island_outer_glow_color': _LegacyField(
      'pref_channel_island_outer_glow_color_',
      '',
    ),
    'out_effect_color': _LegacyField('pref_channel_out_effect_color_', ''),
    'focus_custom': _LegacyField('pref_channel_focus_custom_', ''),
    'island_custom': _LegacyField('pref_channel_island_custom_', ''),
    'aod_text': _LegacyField('pref_channel_aod_text_', kTriOptDefault),
    'aod_custom': _LegacyField('pref_channel_aod_custom_', ''),
    'filter_mode': _LegacyField('pref_channel_filter_mode_', 'blacklist'),
    'whitelist_keywords': _LegacyField(
      'pref_channel_filter_whitelist_keywords_',
      '',
    ),
    'blacklist_keywords': _LegacyField(
      'pref_channel_filter_blacklist_keywords_',
      '',
    ),
    'island_enabled': _LegacyField('pref_channel_island_enabled_', 'true'),
  };
}

class _LegacyField {
  final String prefix;
  final Object defaultValue;

  const _LegacyField(this.prefix, this.defaultValue);
}

class _ParsedChannelField {
  final String name;
  final String channelId;
  final String defaultValue;

  const _ParsedChannelField(this.name, this.channelId, this.defaultValue);
}
