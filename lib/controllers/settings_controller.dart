import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

const kPrefShowWelcome = 'pref_show_welcome';
const kPrefResumeNotification = 'pref_resume_notification';
const kPrefUseHookAppIcon = 'pref_use_hook_app_icon';
const kPrefInteractionHaptics = 'pref_interaction_haptics';
const kPrefRoundIcon = 'pref_round_icon';
const kPrefMarqueeFeature = 'pref_marquee_feature';
const kPrefMarqueeSpeed = 'pref_marquee_speed';
const kPrefBigIslandMaxWidthEnabled = 'pref_big_island_max_width_enabled';
const kPrefBigIslandMaxWidth = 'pref_big_island_max_width';
const kPrefUnlockAllFocus = 'pref_unlock_all_focus';
const kPrefUnlockFocusAuth = 'pref_unlock_focus_auth';
const kPrefThemeMode = 'pref_theme_mode';
const kPrefLocale = 'pref_locale';
const kPrefCheckUpdateOnLaunch = 'pref_check_update_on_launch';
const kPrefDefaultFirstFloat = 'pref_default_first_float';
const kPrefDefaultEnableFloat = 'pref_default_enable_float';
const kPrefDefaultShowIslandIcon = 'pref_default_show_island_icon';
const kPrefDefaultMarquee = 'pref_default_marquee';
const kPrefDefaultFocusNotif = 'pref_default_focus_notif';
const kPrefDefaultDynamicHighlightColor =
    'pref_default_dynamic_highlight_color';
const kPrefDefaultOuterGlow = 'pref_default_outer_glow';
const kPrefDefaultIslandOuterGlow = 'pref_default_island_outer_glow';
const kPrefDefaultOutEffectColor = 'pref_default_out_effect_color';
const kPrefDefaultIslandOuterGlowColor = 'pref_default_island_outer_glow_color';
const kPrefDefaultRestoreLockscreen = 'pref_default_restore_lockscreen';
const kPrefDefaultPreserveSmallIcon = 'pref_default_preserve_small_icon';
const kPrefFullscreenBehavior = 'pref_fullscreen_behavior';
const kPrefHideDesktopIcon = 'pref_hide_desktop_icon';
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
const kPrefConfigAppVersion = 'pref_config_app_version';

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
  SharedPreferences? _prefs;

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
  bool bigIslandMaxWidthEnabled = false;
  int bigIslandMaxWidth = 200;
  bool unlockAllFocus = false;
  bool unlockFocusAuth = false;
  bool checkUpdateOnLaunch = true;
  bool defaultFirstFloat = false;
  bool defaultEnableFloat = false;
  bool defaultShowIslandIcon = true;
  bool defaultMarquee = false;
  bool defaultFocusNotif = true;
  bool defaultDynamicHighlightColor = false;
  bool defaultOuterGlow = false;
  bool defaultIslandOuterGlow = false;
  bool hideDesktopIcon = false;
  bool defaultRestoreLockscreen = false;
  bool defaultPreserveSmallIcon = false;
  String defaultOutEffectColor = '';
  String defaultIslandOuterGlowColor = '';
  String fullscreenBehavior = 'off';
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
  String configAppVersion = '';
  ThemeMode themeMode = ThemeMode.system;
  Locale? locale;
  bool loading = true;

  Future<SharedPreferences> _getPrefs() async {
    final cached = _prefs;
    if (cached != null) return cached;
    final prefs = await SharedPreferences.getInstance();
    _prefs = prefs;
    return prefs;
  }

  Future<void> _load() async {
    final prefs = await _getPrefs();
    showWelcome = prefs.getBool(kPrefShowWelcome) ?? true;
    resumeNotification = prefs.getBool(kPrefResumeNotification) ?? true;
    useHookAppIcon = prefs.getBool(kPrefUseHookAppIcon) ?? true;
    interactionHaptics = prefs.getBool(kPrefInteractionHaptics) ?? true;
    roundIcon = prefs.getBool(kPrefRoundIcon) ?? true;
    marqueeFeature = prefs.getBool(kPrefMarqueeFeature) ?? false;
    marqueeSpeed = prefs.getInt(kPrefMarqueeSpeed) ?? 100;
    bigIslandMaxWidthEnabled =
        prefs.getBool(kPrefBigIslandMaxWidthEnabled) ?? false;
    bigIslandMaxWidth = prefs.getInt(kPrefBigIslandMaxWidth) ?? 200;
    unlockAllFocus = prefs.getBool(kPrefUnlockAllFocus) ?? false;
    unlockFocusAuth = prefs.getBool(kPrefUnlockFocusAuth) ?? false;
    checkUpdateOnLaunch = prefs.getBool(kPrefCheckUpdateOnLaunch) ?? true;
    defaultFirstFloat = prefs.getBool(kPrefDefaultFirstFloat) ?? false;
    defaultEnableFloat = prefs.getBool(kPrefDefaultEnableFloat) ?? false;
    defaultShowIslandIcon = prefs.getBool(kPrefDefaultShowIslandIcon) ?? true;
    defaultMarquee = prefs.getBool(kPrefDefaultMarquee) ?? false;
    defaultFocusNotif = prefs.getBool(kPrefDefaultFocusNotif) ?? true;
    defaultDynamicHighlightColor =
        prefs.getBool(kPrefDefaultDynamicHighlightColor) ?? false;
    defaultOuterGlow = prefs.getBool(kPrefDefaultOuterGlow) ?? false;
    defaultIslandOuterGlow =
        prefs.getBool(kPrefDefaultIslandOuterGlow) ?? false;
    hideDesktopIcon = prefs.getBool(kPrefHideDesktopIcon) ?? false;
    defaultShowIslandIcon = prefs.getBool(kPrefDefaultShowIslandIcon) ?? true;
    defaultRestoreLockscreen =
        prefs.getBool(kPrefDefaultRestoreLockscreen) ?? false;
    defaultPreserveSmallIcon =
        prefs.getBool(kPrefDefaultPreserveSmallIcon) ?? false;
    defaultOutEffectColor = prefs.getString(kPrefDefaultOutEffectColor) ?? '';
    defaultIslandOuterGlowColor =
        prefs.getString(kPrefDefaultIslandOuterGlowColor) ?? '';
    fullscreenBehavior = prefs.getString(kPrefFullscreenBehavior) ?? 'off';
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
    configAppVersion = prefs.getString(kPrefConfigAppVersion) ?? '';
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
    if (resumeNotification == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefResumeNotification, value);
    resumeNotification = value;
    notifyListeners();
  }

  Future<void> setUseHookAppIcon(bool value) async {
    if (useHookAppIcon == value) return;
    final prefs = await _getPrefs();
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
    if (roundIcon == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefRoundIcon, value);
    roundIcon = value;
    notifyListeners();
  }

  Future<void> setMarqueeFeature(bool value) async {
    if (marqueeFeature == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefMarqueeFeature, value);
    marqueeFeature = value;
    notifyListeners();
  }

  Future<void> setMarqueeSpeed(int value) async {
    final clamped = value.clamp(20, 500);
    if (marqueeSpeed == clamped) return;
    final prefs = await _getPrefs();
    await prefs.setInt(kPrefMarqueeSpeed, clamped);
    marqueeSpeed = clamped;
    notifyListeners();
  }

  Future<void> setBigIslandMaxWidthEnabled(bool value) async {
    if (bigIslandMaxWidthEnabled == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefBigIslandMaxWidthEnabled, value);
    bigIslandMaxWidthEnabled = value;
    notifyListeners();
  }

  Future<void> setBigIslandMaxWidth(int value) async {
    final clamped = value.clamp(50, 500);
    if (bigIslandMaxWidth == clamped) return;
    final prefs = await _getPrefs();
    await prefs.setInt(kPrefBigIslandMaxWidth, clamped);
    bigIslandMaxWidth = clamped;
    notifyListeners();
  }

  Future<void> setUnlockAllFocus(bool value) async {
    if (unlockAllFocus == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefUnlockAllFocus, value);
    unlockAllFocus = value;
    notifyListeners();
  }

  Future<void> setUnlockFocusAuth(bool value) async {
    if (unlockFocusAuth == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefUnlockFocusAuth, value);
    unlockFocusAuth = value;
    notifyListeners();
  }

  Future<void> setCheckUpdateOnLaunch(bool value) async {
    if (checkUpdateOnLaunch == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefCheckUpdateOnLaunch, value);
    checkUpdateOnLaunch = value;
    notifyListeners();
  }

  Future<void> setDefaultFirstFloat(bool value) async {
    if (defaultFirstFloat == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefDefaultFirstFloat, value);
    defaultFirstFloat = value;
    notifyListeners();
  }

  Future<void> setDefaultEnableFloat(bool value) async {
    if (defaultEnableFloat == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefDefaultEnableFloat, value);
    defaultEnableFloat = value;
    notifyListeners();
  }

  Future<void> setDefaultShowIslandIcon(bool value) async {
    if (defaultShowIslandIcon == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefDefaultShowIslandIcon, value);
    defaultShowIslandIcon = value;
    notifyListeners();
  }

  Future<void> setDefaultMarquee(bool value) async {
    if (defaultMarquee == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefDefaultMarquee, value);
    defaultMarquee = value;
    notifyListeners();
  }

  Future<void> setDefaultFocusNotif(bool value) async {
    if (defaultFocusNotif == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefDefaultFocusNotif, value);
    defaultFocusNotif = value;
    notifyListeners();
  }

  Future<void> setDefaultDynamicHighlightColor(bool value) async {
    if (defaultDynamicHighlightColor == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefDefaultDynamicHighlightColor, value);
    defaultDynamicHighlightColor = value;
    notifyListeners();
  }

  Future<void> setDefaultOuterGlow(bool value) async {
    if (defaultOuterGlow == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefDefaultOuterGlow, value);
    defaultOuterGlow = value;
    notifyListeners();
  }

  Future<void> setDefaultIslandOuterGlow(bool value) async {
    if (defaultIslandOuterGlow == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefDefaultIslandOuterGlow, value);
    defaultIslandOuterGlow = value;
    notifyListeners();
  }

  Future<void> setDefaultOutEffectColor(String value) async {
    final normalized = value.trim();
    if (defaultOutEffectColor == normalized) return;
    final prefs = await _getPrefs();
    if (normalized.isEmpty) {
      await prefs.remove(kPrefDefaultOutEffectColor);
    } else {
      await prefs.setString(kPrefDefaultOutEffectColor, normalized);
    }
    defaultOutEffectColor = normalized;
    notifyListeners();
  }

  Future<void> setDefaultIslandOuterGlowColor(String value) async {
    final normalized = value.trim();
    if (defaultIslandOuterGlowColor == normalized) return;
    final prefs = await _getPrefs();
    if (normalized.isEmpty) {
      await prefs.remove(kPrefDefaultIslandOuterGlowColor);
    } else {
      await prefs.setString(kPrefDefaultIslandOuterGlowColor, normalized);
    }
    defaultIslandOuterGlowColor = normalized;
    notifyListeners();
  }

  Future<void> setDefaultPreserveSmallIcon(bool value) async {
    if (defaultPreserveSmallIcon == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefDefaultPreserveSmallIcon, value);
    defaultPreserveSmallIcon = value;
    notifyListeners();
  }

  Future<void> setFullscreenBehavior(String value) async {
    if (fullscreenBehavior == value) return;
    final prefs = await _getPrefs();
    await prefs.setString(kPrefFullscreenBehavior, value);
    fullscreenBehavior = value;
    notifyListeners();
  }

  Future<void> setHideDesktopIcon(bool value) async {
    if (hideDesktopIcon == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefHideDesktopIcon, value);
    hideDesktopIcon = value;
    const channel = MethodChannel('io.github.hyperisland/test');
    try {
      await channel.invokeMethod('setDesktopIconVisible', {'visible': !value});
    } catch (_) {}
    notifyListeners();
  }

  Future<void> setDefaultRestoreLockscreen(bool value) async {
    if (defaultRestoreLockscreen == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefDefaultRestoreLockscreen, value);
    defaultRestoreLockscreen = value;
    notifyListeners();
  }

  Future<void> syncHideDesktopIconFromSystem() async {
    const channel = MethodChannel('io.github.hyperisland/test');
    try {
      final visible = await channel.invokeMethod<bool>('isDesktopIconVisible');
      if (visible != null) {
        final hidden = !visible;
        if (hideDesktopIcon != hidden) {
          final prefs = await _getPrefs();
          await prefs.setBool(kPrefHideDesktopIcon, hidden);
          hideDesktopIcon = hidden;
          notifyListeners();
        }
      }
    } catch (_) {}
  }

  Future<void> setAiEnabled(bool value) async {
    if (aiEnabled == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefAiEnabled, value);
    aiEnabled = value;
    notifyListeners();
  }

  Future<void> setAiUrl(String value) async {
    if (aiUrl == value) return;
    final prefs = await _getPrefs();
    await prefs.setString(kPrefAiUrl, value);
    aiUrl = value;
    notifyListeners();
  }

  Future<void> setAiApiKey(String value) async {
    if (aiApiKey == value) return;
    final prefs = await _getPrefs();
    await prefs.setString(kPrefAiApiKey, value);
    aiApiKey = value;
    notifyListeners();
  }

  Future<void> setAiModel(String value) async {
    if (aiModel == value) return;
    final prefs = await _getPrefs();
    await prefs.setString(kPrefAiModel, value);
    aiModel = value;
    notifyListeners();
  }

  Future<void> setAiPrompt(String value) async {
    if (aiPrompt == value) return;
    final prefs = await _getPrefs();
    await prefs.setString(kPrefAiPrompt, value);
    aiPrompt = value;
    notifyListeners();
  }

  Future<void> setAiPromptInUser(bool value) async {
    if (aiPromptInUser == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefAiPromptInUser, value);
    aiPromptInUser = value;
    notifyListeners();
  }

  Future<void> setAiTimeout(int value) async {
    final clamped = value.clamp(3, 15);
    if (aiTimeout == clamped) return;
    final prefs = await _getPrefs();
    await prefs.setInt(kPrefAiTimeout, clamped);
    aiTimeout = clamped;
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
    if (themeMode == mode) return;
    final prefs = await _getPrefs();
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
    if (locale == loc) return;
    final prefs = await _getPrefs();
    if (loc == null) {
      await prefs.remove(kPrefLocale);
    } else {
      await prefs.setString(kPrefLocale, loc.languageCode);
    }
    locale = loc;
    notifyListeners();
  }

  Future<bool> syncConfigAppVersion(String currentVersion) async {
    final version = currentVersion.trim();
    if (version.isEmpty) return false;
    final prefs = await _getPrefs();
    final stored = (prefs.getString(kPrefConfigAppVersion) ?? '').trim();
    final shouldUpdate =
        stored.isEmpty || compareVersionStrings(version, stored) > 0;
    if (shouldUpdate) {
      await prefs.setString(kPrefConfigAppVersion, version);
      configAppVersion = version;
      notifyListeners();
      return true;
    }
    return false;
  }

  static int compareVersionStrings(String a, String b) {
    final aParts = _parseVersionParts(a);
    final bParts = _parseVersionParts(b);
    final maxLen = aParts.length > bParts.length
        ? aParts.length
        : bParts.length;
    for (int i = 0; i < maxLen; i++) {
      final av = i < aParts.length ? aParts[i] : 0;
      final bv = i < bParts.length ? bParts[i] : 0;
      if (av != bv) return av > bv ? 1 : -1;
    }
    return 0;
  }

  static List<int> _parseVersionParts(String version) {
    final core = version.split('+').first.trim();
    final matches = RegExp(r'\d+').allMatches(core);
    if (matches.isEmpty) return const [0];
    return matches.map((m) => int.tryParse(m.group(0) ?? '0') ?? 0).toList();
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
