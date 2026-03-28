import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

const kPrefResumeNotification          = 'pref_resume_notification';
const kPrefUseHookAppIcon             = 'pref_use_hook_app_icon';
const kPrefRoundIcon                  = 'pref_round_icon';
const kPrefMarqueeFeature             = 'pref_marquee_feature';
const kPrefMarqueeSpeed               = 'pref_marquee_speed';
const kPrefUnlockAllFocus             = 'pref_unlock_all_focus';
const kPrefUnlockFocusAuth            = 'pref_unlock_focus_auth';
const kPrefThemeMode                  = 'pref_theme_mode';
const kPrefLocale                     = 'pref_locale';
const kPrefCheckUpdateOnLaunch        = 'pref_check_update_on_launch';
const kPrefDefaultFirstFloat          = 'pref_default_first_float';
const kPrefDefaultEnableFloat         = 'pref_default_enable_float';
const kPrefDefaultMarquee             = 'pref_default_marquee';
const kPrefDefaultFocusNotif          = 'pref_default_focus_notif';
const kPrefDefaultPreserveSmallIcon   = 'pref_default_preserve_small_icon';

const kPrefAiEnabled            = 'pref_ai_enabled';
const kPrefAiUrl                = 'pref_ai_url';
const kPrefAiApiKey             = 'pref_ai_api_key';
const kPrefAiModel              = 'pref_ai_model';
const kPrefAiUsePublicPreset    = 'pref_ai_use_public_preset';
const kPrefAiCustomUrl          = 'pref_ai_custom_url';
const kPrefAiCustomApiKey       = 'pref_ai_custom_api_key';
const kPrefAiCustomModel        = 'pref_ai_custom_model';

const kPublicAiBaseUrl          = 'https://api.9e.nz/v1';
const kPublicAiRuntimeUrl       = 'https://api.9e.nz/v1/chat/completions';
const kPublicAiApiKey           = 'sk-2p8S9WnKKHdwZtSKqboqYZW1lqBWLOi8fcDzOnbxENx1Unkp';
const kPublicAiModel            = 'gemma-3-27b-it';

class SettingsController extends ChangeNotifier {
  static final SettingsController instance = SettingsController._();

  SettingsController._() {
    _load();
  }

  bool resumeNotification = true;
  bool useHookAppIcon = true;
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
  bool aiUsePublicPreset = false;
  String aiUrl = '';
  String aiApiKey = '';
  String aiModel = '';
  ThemeMode themeMode = ThemeMode.system;
  Locale? locale; // null = follow system
  bool loading = true;

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    resumeNotification        = prefs.getBool(kPrefResumeNotification) ?? true;
    useHookAppIcon            = prefs.getBool(kPrefUseHookAppIcon) ?? true;
    roundIcon                 = prefs.getBool(kPrefRoundIcon) ?? true;
    marqueeFeature        = prefs.getBool(kPrefMarqueeFeature) ?? false;
    marqueeSpeed          = (prefs.getInt(kPrefMarqueeSpeed) ?? 100).clamp(20, 500);
    unlockAllFocus        = prefs.getBool(kPrefUnlockAllFocus) ?? false;
    unlockFocusAuth       = prefs.getBool(kPrefUnlockFocusAuth) ?? false;
    checkUpdateOnLaunch        = prefs.getBool(kPrefCheckUpdateOnLaunch) ?? true;
    defaultFirstFloat          = prefs.getBool(kPrefDefaultFirstFloat) ?? false;
    defaultEnableFloat         = prefs.getBool(kPrefDefaultEnableFloat) ?? false;
    defaultMarquee             = prefs.getBool(kPrefDefaultMarquee) ?? false;
    defaultFocusNotif          = prefs.getBool(kPrefDefaultFocusNotif) ?? true;
    defaultPreserveSmallIcon   = prefs.getBool(kPrefDefaultPreserveSmallIcon) ?? false;
    aiEnabled          = prefs.getBool(kPrefAiEnabled) ?? false;
    aiUsePublicPreset  = prefs.getBool(kPrefAiUsePublicPreset) ?? false;
    final runtimeUrl   = normalizeAiUrl(prefs.getString(kPrefAiUrl) ?? '');
    final runtimeKey   = prefs.getString(kPrefAiApiKey) ?? '';
    final runtimeModel = prefs.getString(kPrefAiModel) ?? '';
    final customUrl    = normalizeAiUrl(prefs.getString(kPrefAiCustomUrl) ?? '');
    final customKey    = prefs.getString(kPrefAiCustomApiKey) ?? '';
    final customModel  = prefs.getString(kPrefAiCustomModel) ?? '';
    aiUrl              = customUrl.isNotEmpty ? customUrl : runtimeUrl;
    aiApiKey           = customKey.isNotEmpty ? customKey : runtimeKey;
    aiModel            = customModel.isNotEmpty ? customModel : runtimeModel;
    if (aiUsePublicPreset) {
      aiUrl = customUrl;
      aiApiKey = customKey;
      aiModel = customModel;
    }
    themeMode = switch (prefs.getString(kPrefThemeMode)) {
      'light'  => ThemeMode.light,
      'dark'   => ThemeMode.dark,
      _        => ThemeMode.system,
    };
    final localeStr = prefs.getString(kPrefLocale);
    locale = localeStr != null ? Locale(localeStr) : null;
    loading = false;
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

  static String normalizeAiUrl(String value) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) return '';
    if (trimmed.endsWith('/v1/chat/completions')) return trimmed;
    if (trimmed.endsWith('/v1')) return '$trimmed/chat/completions';
    if (trimmed.endsWith('/v1/')) return '${trimmed}chat/completions';
    return trimmed;
  }

  Future<void> setAiUrl(String value) async {
    final normalized = normalizeAiUrl(value);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kPrefAiUrl, normalized);
    await prefs.setString(kPrefAiCustomUrl, normalized);
    aiUrl = normalized;
    notifyListeners();
  }

  Future<void> setAiApiKey(String value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kPrefAiApiKey, value);
    await prefs.setString(kPrefAiCustomApiKey, value);
    aiApiKey = value;
    notifyListeners();
  }

  Future<void> setAiModel(String value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(kPrefAiModel, value);
    await prefs.setString(kPrefAiCustomModel, value);
    aiModel = value;
    notifyListeners();
  }

  Future<void> setAiUsePublicPreset(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefAiUsePublicPreset, value);
    aiUsePublicPreset = value;
    if (value) {
      await prefs.setString(kPrefAiUrl, kPublicAiRuntimeUrl);
      await prefs.setString(kPrefAiApiKey, kPublicAiApiKey);
      await prefs.setString(kPrefAiModel, kPublicAiModel);
    } else {
      final customUrl = normalizeAiUrl(prefs.getString(kPrefAiCustomUrl) ?? '');
      final customKey = prefs.getString(kPrefAiCustomApiKey) ?? '';
      final customModel = prefs.getString(kPrefAiCustomModel) ?? '';
      await prefs.setString(kPrefAiUrl, customUrl);
      await prefs.setString(kPrefAiApiKey, customKey);
      await prefs.setString(kPrefAiModel, customModel);
      aiUrl = customUrl;
      aiApiKey = customKey;
      aiModel = customModel;
    }
    notifyListeners();
  }

  Future<void> applyPublicAiPreset() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(kPrefAiUsePublicPreset, true);
    await prefs.setString(kPrefAiUrl, kPublicAiRuntimeUrl);
    await prefs.setString(kPrefAiApiKey, kPublicAiApiKey);
    await prefs.setString(kPrefAiModel, kPublicAiModel);
    aiUsePublicPreset = true;
    notifyListeners();
  }

  Future<void> setThemeMode(ThemeMode mode) async {
    final prefs = await SharedPreferences.getInstance();
    final str = switch (mode) {
      ThemeMode.light  => 'light',
      ThemeMode.dark   => 'dark',
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
}
