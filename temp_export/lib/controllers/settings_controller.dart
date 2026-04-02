import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

const kPrefShowWelcome = 'pref_show_welcome';
const kPrefResumeNotification = 'pref_resume_notification';
const kPrefUseHookAppIcon = 'pref_use_hook_app_icon';
const kPrefInteractionHaptics = 'pref_interaction_haptics';
const kPrefRoundIcon = 'pref_round_icon';
const kPrefMarqueeFeature = 'pref_marquee_feature';
const kPrefMarqueeSpeed = 'pref_marquee_speed';
const kPrefUnlockAllFocus = 'pref_unlock_all_focus';
const kPrefUnlockFocusAuth = 'pref_unlock_focus_auth';
const kPrefThemeMode = 'pref_theme_mode';
const kPrefLocale = 'pref_locale';
const kPrefCheckUpdateOnLaunch = 'pref_check_update_on_launch';
const kPrefDefaultFirstFloat = 'pref_default_first_float';
const kPrefDefaultEnableFloat = 'pref_default_enable_float';
const kPrefDefaultMarquee = 'pref_default_marquee';
const kPrefDefaultFocusNotif = 'pref_default_focus_notif';
const kPrefDefaultPreserveSmallIcon = 'pref_default_preserve_small_icon';

const kPrefAiEnabled = 'pref_ai_enabled';
const kPrefAiUrl = 'pref_ai_url';
const kPrefAiApiKey = 'pref_ai_api_key';
const kPrefAiModel = 'pref_ai_model';
const kPrefAiPrompt = 'pref_ai_prompt';
const kPrefAiPromptInUser = 'pref_ai_prompt_in_user';
const kPrefAiTimeout = 'pref_ai_timeout';
const kPrefAiTemperature = 'pref_ai_temperature';
const kPrefAiMaxTokens = 'pref_ai_max_tokens';
const kPrefAiLastLogJson = 'pref_ai_last_log_json';

class AiLogEntry {
  const AiLogEntry({
    required this.timestamp,
    required this.source,
    required this.url,
    required this.model,
    required this.requestBody,
    required this.responseBody,
    required this.error,
    required this.statusCode,
    required this.durationMs,
  });

  final DateTime timestamp;
  final String source;
  final String url;
  final String model;
  final String requestBody;
  final String responseBody;
  final String error;
  final int? statusCode;
  final int? durationMs;

  Map<String, dynamic> toJson() => {
    'timestamp': timestamp.toIso8601String(),
    'source': source,
    'url': url,
    'model': model,
    'requestBody': requestBody,
    'responseBody': responseBody,
    'error': error,
    'statusCode': statusCode,
    'durationMs': durationMs,
  };

  factory AiLogEntry.fromJson(Map<String, dynamic> json) {
    return AiLogEntry(
      timestamp:
          DateTime.tryParse(json['timestamp'] as String? ?? '')?.toLocal() ??
          DateTime.fromMillisecondsSinceEpoch(0),
      source: json['source'] as String? ?? '',
      url: json['url'] as String? ?? '',
      model: json['model'] as String? ?? '',
      requestBody: json['requestBody'] as String? ?? '',
      responseBody: json['responseBody'] as String? ?? '',
      error: json['error'] as String? ?? '',
      statusCode: (json['statusCode'] as num?)?.toInt(),
      durationMs: (json['durationMs'] as num?)?.toInt(),
    );
  }
}

class SettingsController extends ChangeNotifier {
  static final SettingsController instance = SettingsController._();

  SettingsController._() {
    _load();
  }

  bool showWelcome = true;
  bool resumeNotification = true;
  bool useHookAppIcon = true;
  bool interactionHaptics = true;
  bool roundIcon = true;
  bool marqueeFeature = false;
  int marqueeSpeed = 100;
  bool unlockAllFocus = false;
  bool unlockFocusAuth = false;
  bool checkUpdateOnLaunch = true;
  bool defaultFirstFloat = false;
  bool defaultEnableFloat = false;
  bool defaultMarquee = false;
  bool defaultFocusNotif = true;
  bool defaultPreserveSmallIcon = false;
  bool aiEnabled = false;
  String aiUrl = '';
  String aiApiKey = '';
  String aiModel = '';
  String aiPrompt = '';
  bool aiPromptInUser = false;
  int aiTimeout = 3;
  double aiTemperature = 0.1;
  int aiMaxTokens = 50;
  AiLogEntry? aiLastLog;
  ThemeMode themeMode = ThemeMode.system;
  Locale? locale; // null = follow system
  bool loading = true;

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    showWelcome = prefs.getBool(kPrefShowWelcome) ?? true;
    resumeNotification = prefs.getBool(kPrefResumeNotification) ?? true;
    useHookAppIcon = prefs.getBool(kPrefUseHookAppIcon) ?? true;
    interactionHaptics = prefs.getBool(kPrefInteractionHaptics) ?? true;
    roundIcon = prefs.getBool(kPrefRoundIcon) ?? true;
    marqueeFeature = prefs.getBool(kPrefMarqueeFeature) ?? false;
    marqueeSpeed = (prefs.getInt(kPrefMarqueeSpeed) ?? 100).clamp(20, 500);
    unlockAllFocus = prefs.getBool(kPrefUnlockAllFocus) ?? false;
    unlockFocusAuth = prefs.getBool(kPrefUnlockFocusAuth) ?? false;
    checkUpdateOnLaunch = prefs.getBool(kPrefCheckUpdateOnLaunch) ?? true;
    defaultFirstFloat = prefs.getBool(kPrefDefaultFirstFloat) ?? false;
    defaultEnableFloat = prefs.getBool(kPrefDefaultEnableFloat) ?? false;
    defaultMarquee = prefs.getBool(kPrefDefaultMarquee) ?? false;
    defaultFocusNotif = prefs.getBool(kPrefDefaultFocusNotif) ?? true;
    defaultPreserveSmallIcon =
        prefs.getBool(kPrefDefaultPreserveSmallIcon) ?? false;
    aiEnabled = prefs.getBool(kPrefAiEnabled) ?? false;
    aiUrl = prefs.getString(kPrefAiUrl) ?? '';
    aiApiKey = prefs.getString(kPrefAiApiKey) ?? '';
    aiModel = prefs.getString(kPrefAiModel) ?? '';
    aiPrompt = prefs.getString(kPrefAiPrompt) ?? '';
    aiPromptInUser = prefs.getBool(kPrefAiPromptInUser) ?? false;
    aiTimeout = prefs.getInt(kPrefAiTimeout) ?? 3;
    aiTemperature = prefs.getDouble(kPrefAiTemperature) ?? 0.1;
    aiMaxTokens = prefs.getInt(kPrefAiMaxTokens) ?? 50;
    aiLastLog = _parseAiLog(prefs.getString(kPrefAiLastLogJson));
    themeMode = switch (prefs.getString(kPrefThemeMode)) {
      'light' => ThemeMode.light,
      'dark' => ThemeMode.dark,
      _ => ThemeMode.system,
    };
    final localeStr = prefs.getString(kPrefLocale);
    locale = localeStr != null ? Locale(localeStr) : null;
    loading = false;
    notifyListeners();
  }

  Future<void> setShowWelcome(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefShowWelcome, value);
    showWelcome = value;
    notifyListeners();
  }

  Future<void> setResumeNotification(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefResumeNotification, value);
    resumeNotification = value;
    notifyListeners();
  }

  Future<void> setUseHookAppIcon(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefUseHookAppIcon, value);
    useHookAppIcon = value;
    notifyListeners();
  }

  Future<void> setInteractionHaptics(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefInteractionHaptics, value);
    interactionHaptics = value;
    notifyListeners();
  }

  Future<void> setRoundIcon(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefRoundIcon, value);
    roundIcon = value;
    notifyListeners();
  }

  Future<void> setMarqueeFeature(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefMarqueeFeature, value);
    marqueeFeature = value;
    notifyListeners();
  }

  Future<void> setMarqueeSpeed(int value) async {
    final clamped = value.clamp(20, 500);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(kPrefMarqueeSpeed, clamped);
    marqueeSpeed = clamped;
    notifyListeners();
  }

  Future<void> setUnlockAllFocus(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefUnlockAllFocus, value);
    unlockAllFocus = value;
    notifyListeners();
  }

  Future<void> setUnlockFocusAuth(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefUnlockFocusAuth, value);
    unlockFocusAuth = value;
    notifyListeners();
  }

  Future<void> setCheckUpdateOnLaunch(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefCheckUpdateOnLaunch, value);
    checkUpdateOnLaunch = value;
    notifyListeners();
  }

  Future<void> setDefaultFirstFloat(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefDefaultFirstFloat, value);
    defaultFirstFloat = value;
    notifyListeners();
  }

  Future<void> setDefaultEnableFloat(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefDefaultEnableFloat, value);
    defaultEnableFloat = value;
    notifyListeners();
  }

  Future<void> setDefaultMarquee(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefDefaultMarquee, value);
    defaultMarquee = value;
    notifyListeners();
  }

  Future<void> setDefaultFocusNotif(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefDefaultFocusNotif, value);
    defaultFocusNotif = value;
    notifyListeners();
  }

  Future<void> setDefaultPreserveSmallIcon(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefDefaultPreserveSmallIcon, value);
    defaultPreserveSmallIcon = value;
    notifyListeners();
  }

  Future<void> setAiEnabled(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefAiEnabled, value);
    aiEnabled = value;
    notifyListeners();
  }

  Future<void> setAiUrl(String value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kPrefAiUrl, value);
    aiUrl = value;
    notifyListeners();
  }

  Future<void> setAiApiKey(String value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kPrefAiApiKey, value);
    aiApiKey = value;
    notifyListeners();
  }

  Future<void> setAiModel(String value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kPrefAiModel, value);
    aiModel = value;
    notifyListeners();
  }

  Future<void> setAiPrompt(String value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kPrefAiPrompt, value);
    aiPrompt = value;
    notifyListeners();
  }

  Future<void> setAiPromptInUser(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefAiPromptInUser, value);
    aiPromptInUser = value;
    notifyListeners();
  }

  Future<void> setAiTimeout(int value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(kPrefAiTimeout, value);
    aiTimeout = value;
    notifyListeners();
  }

  Future<void> setAiTemperature(double value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setDouble(kPrefAiTemperature, value);
    aiTemperature = value;
    notifyListeners();
  }

  Future<void> setAiMaxTokens(int value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(kPrefAiMaxTokens, value);
    aiMaxTokens = value;
    notifyListeners();
  }

  Future<void> saveAiLastLog(AiLogEntry? entry) async {
    final prefs = await SharedPreferences.getInstance();
    if (entry == null) {
      await prefs.remove(kPrefAiLastLogJson);
      aiLastLog = null;
    } else {
      await prefs.setString(kPrefAiLastLogJson, jsonEncode(entry.toJson()));
      aiLastLog = entry;
    }
    notifyListeners();
  }

  Future<void> refreshAiLastLog() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.reload();
    aiLastLog = _parseAiLog(prefs.getString(kPrefAiLastLogJson));
    notifyListeners();
  }

  Future<void> setThemeMode(ThemeMode mode) async {
    final prefs = await SharedPreferences.getInstance();
    final str = switch (mode) {
      ThemeMode.light => 'light',
      ThemeMode.dark => 'dark',
      ThemeMode.system => 'system',
    };
    await prefs.setString(kPrefThemeMode, str);
    themeMode = mode;
    notifyListeners();
  }

  Future<void> setLocale(Locale? loc) async {
    final prefs = await SharedPreferences.getInstance();
    if (loc == null) {
      await prefs.remove(kPrefLocale);
    } else {
      await prefs.setString(kPrefLocale, loc.languageCode);
    }
    locale = loc;
    notifyListeners();
  }

  AiLogEntry? _parseAiLog(String? raw) {
    if (raw == null || raw.isEmpty) return null;
    try {
      return AiLogEntry.fromJson(
        Map<String, dynamic>.from(jsonDecode(raw) as Map),
      );
    } catch (_) {
      return null;
    }
  }
}
