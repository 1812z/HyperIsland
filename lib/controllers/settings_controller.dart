import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'whitelist_controller.dart';

const kPrefShowWelcome = 'pref_show_welcome';
const kPrefResumeNotification = 'pref_resume_notification';
const kPrefSettingsHomeEntry = 'pref_settings_home_entry';
const kPrefSettingsHomeEntryIconStyle = 'pref_settings_home_entry_icon_style';
const kPrefBluetoothIsland = 'pref_bluetooth_island';
const kPrefBluetoothIslandShowDeviceName =
    'pref_bluetooth_island_show_device_name';
const kPrefBluetoothIslandDisplayDurationSeconds =
    'pref_bluetooth_island_display_duration_seconds';
const kPrefBluetoothIslandOuterGlow = 'pref_bluetooth_island_outer_glow';
const kPrefBluetoothIslandOuterGlowColor =
    'pref_bluetooth_island_outer_glow_color';
const kPrefBluetoothIslandWhitelistEnabled =
    'pref_bluetooth_island_whitelist_enabled';
const kPrefBluetoothIslandWhitelistAddresses =
    'pref_bluetooth_island_whitelist_addresses';

const kPrefInteractionHaptics = 'pref_interaction_haptics';
const kPrefRoundIcon = 'pref_round_icon';
const kPrefRoundIconRadius = 'pref_round_icon_radius';
const kPrefIslandIconSize = 'pref_island_icon_size';
const kPrefMarqueeFeature = 'pref_marquee_feature';
const kPrefMarqueeSpeed = 'pref_marquee_speed';
const kPrefBigIslandMaxWidth = 'pref_big_island_max_width';
const kPrefBigIslandMinWidth = 'pref_big_island_min_width';
const kPrefSmoothIsland = 'pref_smooth_island';
const kPrefSmoothIslandSmoothing = 'pref_smooth_island_smoothing';
const kPrefUnlockAllFocus = 'pref_unlock_all_focus';
const kPrefUnlockFocusAuth = 'pref_unlock_focus_auth';
const kPrefChargeIsland = 'pref_charge_island';
const kPrefChargeIslandLeftMode = 'pref_charge_island_left_mode';
const kPrefChargeIslandRightMode = 'pref_charge_island_right_mode';
const kPrefChargeIslandDurationMode = 'pref_charge_island_duration_mode';
const kPrefChargeIslandDurationSeconds = 'pref_charge_island_duration_seconds';
const kPrefChargeIslandOuterGlow = 'pref_charge_island_outer_glow';
const kPrefFaceUnlockIsland = 'pref_face_unlock_island';
const kPrefFaceUnlockIslandFirstFloat = 'pref_face_unlock_island_first_float';
const kPrefFaceUnlockIslandAnimationStyle =
    'pref_face_unlock_island_animation_style';
const kPrefFaceUnlockIslandKeepUntilKeyguardHidden =
    'pref_face_unlock_island_keep_until_keyguard_hidden';
const kPrefHideLockscreenFaceUnlockIcon =
    'pref_hide_lockscreen_face_unlock_icon';
const kPrefThemeMode = 'pref_theme_mode';
const kPrefLocale = 'pref_locale';
const kPrefCheckUpdateOnLaunch = 'pref_check_update_on_launch';
const kPrefDefaultFirstFloat = 'pref_default_first_float';
const kPrefDefaultEnableFloat = 'pref_default_enable_float';
const kPrefDefaultShowIslandIcon = 'pref_default_show_island_icon';
const kPrefDefaultMarquee = 'pref_default_marquee';
const kPrefDefaultMarqueeAutoHide = 'pref_default_marquee_auto_hide';
const kPrefDefaultFocusNotif = 'pref_default_focus_notif';
const kPrefDefaultAodText = 'pref_default_aod_text';
const kPrefDefaultDynamicHighlightColor =
    'pref_default_dynamic_highlight_color';
const kPrefDefaultOuterGlow = 'pref_default_outer_glow';
const kPrefDefaultIslandOuterGlow = 'pref_default_island_outer_glow';
const kPrefDefaultForceOuterGlow = 'pref_default_force_outer_glow';
const kPrefDefaultForceIslandOuterGlow = 'pref_default_force_island_outer_glow';
const kPrefDefaultOutEffectColor = 'pref_default_out_effect_color';
const kPrefDefaultIslandOuterGlowColor = 'pref_default_island_outer_glow_color';
const kPrefDefaultRestoreLockscreen = 'pref_default_restore_lockscreen';
const kPrefDefaultTimeout = 'pref_default_timeout';
const kPrefDefaultPreserveSmallIcon = 'pref_default_preserve_small_icon';
const kPrefFullscreenBehavior = 'pref_fullscreen_behavior';
const kPrefLandscapeBehavior = 'pref_landscape_behavior';
const kPrefDndBehavior = 'pref_scene_dnd';
const kPrefExpandedCollapseAction = 'pref_expanded_collapse_action';
const kPrefBigIslandCollapseAction = 'pref_big_island_collapse_action';
const kPrefHideDesktopIcon = 'pref_hide_desktop_icon';
const kPrefAiEnabled = 'pref_ai_enabled';
const kPrefAiUrl = 'pref_ai_url';
const kPrefAiApiKey = 'pref_ai_api_key';
const kPrefAiModel = 'pref_ai_model';
const kPrefAiPrompt = 'pref_ai_prompt';
const kPrefAiPromptInUser = 'pref_ai_prompt_in_user';
const kPrefAiCustomFields = 'pref_ai_custom_fields';
const kPrefAiTimeout = 'pref_ai_timeout';
const kPrefAiTemperature = 'pref_ai_temperature';
const kPrefAiMaxTokens = 'pref_ai_max_tokens';
const kPrefAiTriggerCharCount = 'pref_ai_trigger_char_count';
const kPrefConfigAppVersion = 'pref_config_app_version';
const kPrefIslandBgSmallPath = 'pref_island_bg_small_path';
const kPrefIslandBgBigPath = 'pref_island_bg_big_path';
const kPrefIslandBgExpandPath = 'pref_island_bg_expand_path';
const kPrefIslandBlurSmallEnabled = 'pref_island_blur_small_enabled';
const kPrefIslandBlurSmallRadius = 'pref_island_blur_small_radius';
const kPrefIslandBlurSmallColor = 'pref_island_blur_small_color';
const kPrefIslandBlurBigEnabled = 'pref_island_blur_big_enabled';
const kPrefIslandBlurBigRadius = 'pref_island_blur_big_radius';
const kPrefIslandBlurBigColor = 'pref_island_blur_big_color';
const kPrefIslandBlurExpandEnabled = 'pref_island_blur_expand_enabled';
const kPrefIslandBlurExpandRadius = 'pref_island_blur_expand_radius';
const kPrefIslandBlurExpandColor = 'pref_island_blur_expand_color';
const kPrefIslandGlassEnabled = 'pref_island_glass_enabled';
const kPrefIslandGlassSmallEnabled = 'pref_island_glass_small_enabled';
const kPrefIslandGlassBigEnabled = 'pref_island_glass_big_enabled';
const kPrefIslandGlassExpandEnabled = 'pref_island_glass_expand_enabled';
const kPrefIslandGlassEdgeWidth = 'pref_island_glass_edge_width';
const kPrefIslandGlassRefraction = 'pref_island_glass_refraction';
const kPrefIslandGlassHighlight = 'pref_island_glass_highlight';
const kPrefIslandGlassShadow = 'pref_island_glass_shadow';
const kPrefIslandGlassLightDirection = 'pref_island_glass_light_direction';
const kPrefIslandGlassDispersion = 'pref_island_glass_dispersion';
const kPrefIslandGlassGyroscope = 'pref_island_glass_gyroscope';
const kPrefIslandGlassHdrHighlight = 'pref_island_glass_hdr_highlight';
const kPrefIslandGlassTrueRefraction = 'pref_island_glass_true_refraction';
const kPrefIslandRefractionSmallEnabled =
    'pref_island_refraction_small_enabled';
const kPrefIslandRefractionBigEnabled = 'pref_island_refraction_big_enabled';
const kPrefIslandRefractionExpandEnabled =
    'pref_island_refraction_expand_enabled';
const kPrefIslandGlassCaptureFps = 'pref_island_glass_capture_fps';
const kPrefIslandGlassCaptureQuality = 'pref_island_glass_capture_quality';
const kPrefIslandHeight = 'pref_island_height';
const kPrefIslandTopOffset = 'pref_island_top_offset';
const kPrefIslandTextColorMode = 'pref_island_text_color_mode';
const kPrefFocusNotificationTextColorMode =
    'pref_focus_notification_text_color_mode';
const kPrefMediaNotificationTextColorMode =
    'pref_media_notification_text_color_mode';
const kPrefAlwaysShowIslandOutline = 'pref_always_show_island_outline';
const kPrefAlwaysShowFocusOutline = 'pref_always_show_focus_outline';
const kPrefOuterGlowRange = 'pref_outer_glow_range';
const kPrefKeepIsland = 'pref_keep_island';
const kPrefKeepIslandDisplayTiming = 'pref_keep_island_display_timing';
const kPrefKeepIslandShowNotification = 'pref_keep_island_show_notification';
const kPrefKeepIslandAutoHide = 'pref_keep_island_auto_hide';
const kPrefKeepIslandHideLandscape = 'pref_keep_island_hide_landscape';
const kPrefKeepIslandHighlightColor = 'pref_keep_island_highlight_color';
const kPrefKeepIslandLeftHighlight = 'pref_keep_island_left_highlight';
const kPrefKeepIslandRightHighlight = 'pref_keep_island_right_highlight';
const kPrefKeepIslandLeftContent = 'pref_keep_island_left_content';
const kPrefKeepIslandRightContent = 'pref_keep_island_right_content';
const kPrefKeepIslandCarouselInterval =
    'pref_keep_island_carousel_interval_seconds';
const kPrefKeepIslandFocusNotification = 'pref_keep_island_focus_notification';
const kPrefKeepIslandFocusContentType = 'pref_keep_island_focus_content_type';
const kPrefKeepIslandExpandTextColorMode =
    'pref_keep_island_expand_text_color_mode';
const kPrefKeepIslandNotificationTitle = 'pref_keep_island_notification_title';
const kPrefKeepIslandNotificationContent =
    'pref_keep_island_notification_content';
const kPrefKeepIslandShowIslandIcon = 'pref_keep_island_show_island_icon';
const kPrefKeepIslandCustomIconPath = 'pref_keep_island_custom_icon_path';
const kPrefTempHideBehaviorEnabled = 'pref_temp_hide_behavior_enabled';
const kPrefTempHideScreenPinning = 'pref_temp_hide_screen_pinning';
const kPrefTempHideBouncerShowing = 'pref_temp_hide_bouncer_showing';
const kPrefTempHideFullscreen = 'pref_temp_hide_fullscreen';
const kPrefTempHideScreenLocked = 'pref_temp_hide_screen_locked';
const kPrefTempHideNotificationCenter = 'pref_temp_hide_notification_center';
const kPrefTempHideForegroundApp = 'pref_temp_hide_foreground_app';
const kPrefTempHideFullscreenLandscapeDisable =
    'pref_temp_hide_fullscreen_landscape_disable';
const kPrefThemeSeedColor = 'pref_theme_seed_color';
const kPrefBlurBars = 'pref_blur_bars';
const kPrefDebugLog = 'pref_debug_log';
const kPrefOnboardingCompleted = 'pref_onboarding_completed';

const kIslandTextColorDefault = 'default';
const kIslandTextColorBlack = 'black';
const kIslandTextColorFollowBackground = 'follow_background';
const kIslandTextColorInvertBackground = 'invert_background';
const kIslandTextColorFollowStatusBar = 'follow_status_bar';
const kIslandTextColorInvertStatusBar = 'invert_status_bar';

const kChargeIslandModeDefault = 'default';
const kChargeIslandModePower = 'power';
const kChargeIslandModeVoltage = 'voltage';
const kChargeIslandModeCurrent = 'current';
const kChargeIslandModeLevel = 'level';
const kChargeIslandModeTemperature = 'temperature';

const kChargeIslandDurationDefault = 'default';
const kChargeIslandDurationCustom = 'custom';
const kChargeIslandDurationPersistent = 'persistent';

const kKeepIslandFocusContentNotification = 'notification';
const kKeepIslandFocusContentPerformance = 'performance';
const kKeepIslandFocusContentDevice = 'device';
const kKeepIslandFocusContentCharging = 'charging';

const kKeepIslandExpandTextColorWhite = 'white';
const kKeepIslandExpandTextColorFollowStatusBar = 'follow_status_bar';
const kKeepIslandExpandTextColorInvertStatusBar = 'invert_status_bar';
const kKeepIslandExpandTextColorBlack = 'black';

const kKeepIslandDisplayTimingAlways = 'always';
const kKeepIslandDisplayTimingCharging = 'charging';

const kDefaultAiCustomFields = '{"enable_thinking":false}';

const kFaceUnlockIslandAnimationDefault = 'default';
const kFaceUnlockIslandAnimationLock = 'lock';

const kIslandSwipeActionNone = 'none';
const kIslandSwipeActionCancelNotification = 'cancel_notification';
const kIslandSwipeActionHideIsland = 'hide_island';

const kSettingsHomeEntryIconStyleDefault = 'default';
const kSettingsHomeEntryIconStyleOutline = 'outline';

class SettingsController extends ChangeNotifier {
  static final SettingsController instance = SettingsController._();
  SharedPreferences? _prefs;

  SettingsController._() {
    _load();
  }

  bool showWelcome = true;
  bool resumeNotification = true;
  bool settingsHomeEntry = true;
  String settingsHomeEntryIconStyle = kSettingsHomeEntryIconStyleDefault;
  bool bluetoothIsland = false;
  bool bluetoothIslandShowDeviceName = true;
  int bluetoothIslandDisplayDurationSeconds = 2;
  bool bluetoothIslandOuterGlow = false;
  String bluetoothIslandOuterGlowColor = '';
  bool bluetoothIslandWhitelistEnabled = false;
  List<String> bluetoothIslandWhitelistAddresses = [];
  bool interactionHaptics = true;
  bool roundIcon = true;
  int roundIconRadius = 50;
  int islandIconSize = 100;
  bool marqueeFeature = false;
  int marqueeSpeed = 100;
  int bigIslandMaxWidth = 0;
  int bigIslandMinWidth = 0;
  bool smoothIsland = false;
  double smoothIslandSmoothing = 0.8;
  bool unlockAllFocus = false;
  bool unlockFocusAuth = false;
  bool chargeIsland = false;
  String chargeIslandLeftMode = kChargeIslandModeDefault;
  String chargeIslandRightMode = kChargeIslandModeDefault;
  String chargeIslandDurationMode = kChargeIslandDurationDefault;
  int chargeIslandDurationSeconds = 10;
  bool chargeIslandOuterGlow = false;
  bool faceUnlockIsland = false;
  bool faceUnlockIslandFirstFloat = true;
  String faceUnlockIslandAnimationStyle = kFaceUnlockIslandAnimationDefault;
  bool faceUnlockIslandKeepUntilKeyguardHidden = false;
  bool hideLockscreenFaceUnlockIcon = false;
  bool checkUpdateOnLaunch = true;
  bool defaultFirstFloat = false;
  bool defaultEnableFloat = false;
  bool defaultShowIslandIcon = true;
  bool defaultMarquee = false;
  String defaultMarqueeAutoHide = kTriOptOff;
  bool defaultFocusNotif = true;
  bool defaultAodText = false;
  bool defaultDynamicHighlightColor = false;
  String defaultOuterGlow = kTriOptOff;
  String defaultIslandOuterGlow = kTriOptOff;
  bool defaultForceOuterGlow = false;
  bool defaultForceIslandOuterGlow = false;
  bool hideDesktopIcon = false;
  bool defaultRestoreLockscreen = false;
  bool defaultPreserveSmallIcon = false;
  String defaultOutEffectColor = '';
  int defaultTimeout = 5;
  String defaultIslandOuterGlowColor = '';
  String fullscreenBehavior = 'off';
  String landscapeBehavior = 'off';
  String dndBehavior = 'default';
  String expandedCollapseAction = kIslandSwipeActionNone;
  String bigIslandCollapseAction = kIslandSwipeActionNone;
  bool aiEnabled = false;
  String aiUrl = '';
  String aiApiKey = '';
  String aiModel = '';
  String aiPrompt = '';
  bool aiPromptInUser = false;
  String aiCustomFields = kDefaultAiCustomFields;
  int aiTimeout = 3;
  double aiTemperature = 0.1;
  int aiMaxTokens = 50;
  int aiTriggerCharCount = 10;
  String configAppVersion = '';
  ThemeMode themeMode = ThemeMode.system;
  String islandBgSmallPath = '';
  String islandBgBigPath = '';
  String islandBgExpandPath = '';
  bool islandBlurSmallEnabled = false;
  int islandBlurSmallRadius = 80;
  String islandBlurSmallColor = '#20FFFFFF';
  bool islandBlurBigEnabled = false;
  int islandBlurBigRadius = 80;
  String islandBlurBigColor = '#20FFFFFF';
  bool islandBlurExpandEnabled = false;
  int islandBlurExpandRadius = 80;
  String islandBlurExpandColor = '#20FFFFFF';
  bool islandGlassEnabled = false;
  bool islandGlassSmallEnabled = false;
  bool islandGlassBigEnabled = false;
  bool islandGlassExpandEnabled = false;
  int islandGlassEdgeWidth = 16;
  int islandGlassRefraction = 16;
  int islandGlassHighlight = 42;
  int islandGlassShadow = 14;
  int islandGlassLightDirection = 243;
  int islandGlassDispersion = 18;
  bool islandGlassGyroscope = true;
  bool islandGlassHdrHighlight = false;
  bool islandGlassTrueRefraction = false;
  bool islandRefractionSmallEnabled = false;
  bool islandRefractionBigEnabled = false;
  bool islandRefractionExpandEnabled = false;
  int islandGlassCaptureFps = 20;
  int islandGlassCaptureQuality = 30;
  double islandHeight = 0;
  double islandTopOffset = 0;
  String islandTextColorMode = kIslandTextColorDefault;
  String focusNotificationTextColorMode = kIslandTextColorDefault;
  String mediaNotificationTextColorMode = kIslandTextColorDefault;
  bool alwaysShowIslandOutline = false;
  bool alwaysShowFocusOutline = false;
  int outerGlowRange = 0;
  bool keepIsland = false;
  String keepIslandDisplayTiming = kKeepIslandDisplayTimingAlways;
  bool keepIslandShowNotification = false;
  bool keepIslandAutoHide = true;
  bool keepIslandHideLandscape = false;
  String keepIslandHighlightColor = '';
  bool keepIslandLeftHighlight = false;
  bool keepIslandRightHighlight = false;
  List<String> keepIslandLeftContents = const ['{time.HH:mm}'];
  List<String> keepIslandRightContents = const ['{battery.level}'];
  int keepIslandCarouselInterval = 5;
  bool keepIslandFocusNotification = false;
  String keepIslandFocusContentType = kKeepIslandFocusContentNotification;
  String keepIslandExpandTextColorMode = kKeepIslandExpandTextColorWhite;
  String keepIslandNotificationTitle = '';
  String keepIslandNotificationContent = '';
  bool keepIslandShowIslandIcon = false;
  String keepIslandCustomIconPath = '';
  bool tempHideBehaviorEnabled = false;
  bool tempHideScreenPinning = true;
  bool tempHideBouncerShowing = true;
  bool tempHideFullscreen = true;
  bool tempHideScreenLocked = true;
  bool tempHideNotificationCenter = true;
  bool tempHideForegroundApp = true;
  bool tempHideFullscreenLandscapeDisable = false;
  int themeSeedColor = 0xFF6750A4;
  bool blurBars = true;
  bool debugLog = false;
  bool onboardingCompleted = false;
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
    settingsHomeEntry = prefs.getBool(kPrefSettingsHomeEntry) ?? true;
    settingsHomeEntryIconStyle = _normalizeSettingsHomeEntryIconStyle(
      prefs.getString(kPrefSettingsHomeEntryIconStyle),
    );
    bluetoothIsland = prefs.getBool(kPrefBluetoothIsland) ?? false;
    bluetoothIslandShowDeviceName =
        prefs.getBool(kPrefBluetoothIslandShowDeviceName) ?? true;
    bluetoothIslandDisplayDurationSeconds =
        _normalizeBluetoothIslandDisplayDurationSeconds(
          prefs.getInt(kPrefBluetoothIslandDisplayDurationSeconds),
        );
    bluetoothIslandOuterGlow =
        prefs.getBool(kPrefBluetoothIslandOuterGlow) ?? false;
    bluetoothIslandOuterGlowColor =
        prefs.getString(kPrefBluetoothIslandOuterGlowColor) ?? '';
    bluetoothIslandWhitelistEnabled =
        prefs.getBool(kPrefBluetoothIslandWhitelistEnabled) ?? false;
    bluetoothIslandWhitelistAddresses = _decodeStringList(
      prefs.getString(kPrefBluetoothIslandWhitelistAddresses),
    );
    interactionHaptics = prefs.getBool(kPrefInteractionHaptics) ?? true;
    roundIcon = prefs.getBool(kPrefRoundIcon) ?? true;
    roundIconRadius = prefs.getInt(kPrefRoundIconRadius)?.clamp(0, 100) ?? 50;
    islandIconSize = (prefs.getInt(kPrefIslandIconSize) ?? 100).clamp(50, 150);
    marqueeFeature = prefs.getBool(kPrefMarqueeFeature) ?? false;
    marqueeSpeed = prefs.getInt(kPrefMarqueeSpeed) ?? 100;
    bigIslandMaxWidth = prefs.getInt(kPrefBigIslandMaxWidth) ?? 0;
    bigIslandMinWidth = prefs.getInt(kPrefBigIslandMinWidth) ?? 0;
    smoothIsland = prefs.getBool(kPrefSmoothIsland) ?? false;
    smoothIslandSmoothing = prefs.getDouble(kPrefSmoothIslandSmoothing) ?? 0.8;
    unlockAllFocus = prefs.getBool(kPrefUnlockAllFocus) ?? false;
    unlockFocusAuth = prefs.getBool(kPrefUnlockFocusAuth) ?? false;
    chargeIsland = prefs.getBool(kPrefChargeIsland) ?? false;
    chargeIslandLeftMode = _normalizeChargeIslandMode(
      prefs.getString(kPrefChargeIslandLeftMode),
    );
    chargeIslandRightMode = _normalizeChargeIslandMode(
      prefs.getString(kPrefChargeIslandRightMode),
    );
    chargeIslandDurationMode = _normalizeChargeIslandDurationMode(
      prefs.getString(kPrefChargeIslandDurationMode),
    );
    chargeIslandDurationSeconds = _normalizeChargeIslandDurationSeconds(
      prefs.getInt(kPrefChargeIslandDurationSeconds),
    );
    chargeIslandOuterGlow = prefs.getBool(kPrefChargeIslandOuterGlow) ?? false;
    faceUnlockIsland = prefs.getBool(kPrefFaceUnlockIsland) ?? false;
    faceUnlockIslandFirstFloat =
        prefs.getBool(kPrefFaceUnlockIslandFirstFloat) ?? true;
    faceUnlockIslandAnimationStyle = _normalizeFaceUnlockIslandAnimationStyle(
      prefs.getString(kPrefFaceUnlockIslandAnimationStyle),
    );
    faceUnlockIslandKeepUntilKeyguardHidden =
        prefs.getBool(kPrefFaceUnlockIslandKeepUntilKeyguardHidden) ?? false;
    hideLockscreenFaceUnlockIcon =
        prefs.getBool(kPrefHideLockscreenFaceUnlockIcon) ?? false;
    checkUpdateOnLaunch = prefs.getBool(kPrefCheckUpdateOnLaunch) ?? true;
    defaultFirstFloat = prefs.getBool(kPrefDefaultFirstFloat) ?? false;
    defaultEnableFloat = prefs.getBool(kPrefDefaultEnableFloat) ?? false;
    defaultShowIslandIcon = prefs.getBool(kPrefDefaultShowIslandIcon) ?? true;
    defaultMarquee = prefs.getBool(kPrefDefaultMarquee) ?? false;
    defaultMarqueeAutoHide = _normalizeMarqueeAutoHide(
      prefs.getString(kPrefDefaultMarqueeAutoHide),
    );
    defaultFocusNotif = prefs.getBool(kPrefDefaultFocusNotif) ?? true;
    defaultAodText = prefs.getBool(kPrefDefaultAodText) ?? false;
    defaultDynamicHighlightColor =
        prefs.getBool(kPrefDefaultDynamicHighlightColor) ?? false;
    defaultOuterGlow = _readOuterGlowMode(
      prefs,
      modeKey: kPrefDefaultOuterGlow,
      legacyBoolKey: kPrefDefaultOuterGlow,
    );
    defaultIslandOuterGlow = _readOuterGlowMode(
      prefs,
      modeKey: kPrefDefaultIslandOuterGlow,
      legacyBoolKey: kPrefDefaultIslandOuterGlow,
    );
    defaultForceOuterGlow = prefs.getBool(kPrefDefaultForceOuterGlow) ?? false;
    defaultForceIslandOuterGlow =
        prefs.getBool(kPrefDefaultForceIslandOuterGlow) ?? false;
    hideDesktopIcon = prefs.getBool(kPrefHideDesktopIcon) ?? false;
    defaultShowIslandIcon = prefs.getBool(kPrefDefaultShowIslandIcon) ?? true;
    defaultRestoreLockscreen =
        prefs.getBool(kPrefDefaultRestoreLockscreen) ?? false;
    defaultPreserveSmallIcon =
        prefs.getBool(kPrefDefaultPreserveSmallIcon) ?? false;
    defaultOutEffectColor = prefs.getString(kPrefDefaultOutEffectColor) ?? '';
    final storedDefaultTimeout = prefs.getInt(kPrefDefaultTimeout) ?? 5;
    defaultTimeout = storedDefaultTimeout < 1 ? 1 : storedDefaultTimeout;
    defaultIslandOuterGlowColor =
        prefs.getString(kPrefDefaultIslandOuterGlowColor) ?? '';
    fullscreenBehavior = _normalizeSceneBehavior(
      prefs.getString(kPrefFullscreenBehavior),
    );
    landscapeBehavior = _normalizeSceneBehavior(
      prefs.getString(kPrefLandscapeBehavior),
    );
    dndBehavior = _normalizeDndBehavior(prefs.getString(kPrefDndBehavior));
    expandedCollapseAction = _normalizeIslandSwipeAction(
      prefs.getString(kPrefExpandedCollapseAction),
      allowHideIsland: true,
    );
    bigIslandCollapseAction = _normalizeIslandSwipeAction(
      prefs.getString(kPrefBigIslandCollapseAction),
      allowHideIsland: false,
    );
    aiEnabled = prefs.getBool(kPrefAiEnabled) ?? false;
    aiUrl = prefs.getString(kPrefAiUrl) ?? '';
    aiApiKey = prefs.getString(kPrefAiApiKey) ?? '';
    aiModel = prefs.getString(kPrefAiModel) ?? '';
    aiPrompt = prefs.getString(kPrefAiPrompt) ?? '';
    aiPromptInUser = prefs.getBool(kPrefAiPromptInUser) ?? false;
    aiCustomFields =
        prefs.getString(kPrefAiCustomFields) ?? kDefaultAiCustomFields;
    aiTimeout = prefs.getInt(kPrefAiTimeout) ?? 3;
    aiTemperature = prefs.getDouble(kPrefAiTemperature) ?? 0.1;
    aiMaxTokens = prefs.getInt(kPrefAiMaxTokens) ?? 50;
    aiTriggerCharCount = (prefs.getInt(kPrefAiTriggerCharCount) ?? 10).clamp(
      0,
      100,
    );
    configAppVersion = prefs.getString(kPrefConfigAppVersion) ?? '';
    themeMode = switch (prefs.getString(kPrefThemeMode)) {
      'light' => ThemeMode.light,
      'dark' => ThemeMode.dark,
      _ => ThemeMode.system,
    };
    final localeStr = prefs.getString(kPrefLocale);
    locale = localeStr != null ? Locale(localeStr) : null;
    islandBgSmallPath = prefs.getString(kPrefIslandBgSmallPath) ?? '';
    islandBgBigPath = prefs.getString(kPrefIslandBgBigPath) ?? '';
    islandBgExpandPath = prefs.getString(kPrefIslandBgExpandPath) ?? '';
    islandBlurSmallEnabled =
        prefs.getBool(kPrefIslandBlurSmallEnabled) ?? false;
    islandBlurSmallRadius = (prefs.getInt(kPrefIslandBlurSmallRadius) ?? 80)
        .clamp(0, 100);
    islandBlurSmallColor =
        prefs.getString(kPrefIslandBlurSmallColor) ?? '#20FFFFFF';
    islandBlurBigEnabled = prefs.getBool(kPrefIslandBlurBigEnabled) ?? false;
    islandBlurBigRadius = (prefs.getInt(kPrefIslandBlurBigRadius) ?? 80).clamp(
      0,
      100,
    );
    islandBlurBigColor =
        prefs.getString(kPrefIslandBlurBigColor) ?? '#20FFFFFF';
    islandBlurExpandEnabled =
        prefs.getBool(kPrefIslandBlurExpandEnabled) ?? false;
    islandBlurExpandRadius = (prefs.getInt(kPrefIslandBlurExpandRadius) ?? 80)
        .clamp(0, 100);
    islandBlurExpandColor =
        prefs.getString(kPrefIslandBlurExpandColor) ?? '#20FFFFFF';
    islandGlassEnabled = prefs.getBool(kPrefIslandGlassEnabled) ?? false;
    islandGlassSmallEnabled =
        prefs.getBool(kPrefIslandGlassSmallEnabled) ?? islandGlassEnabled;
    islandGlassBigEnabled =
        prefs.getBool(kPrefIslandGlassBigEnabled) ?? islandGlassEnabled;
    islandGlassExpandEnabled =
        prefs.getBool(kPrefIslandGlassExpandEnabled) ?? islandGlassEnabled;
    islandGlassEdgeWidth = (prefs.getInt(kPrefIslandGlassEdgeWidth) ?? 16)
        .clamp(4, 40);
    islandGlassRefraction = (prefs.getInt(kPrefIslandGlassRefraction) ?? 16)
        .clamp(0, 40);
    islandGlassHighlight = (prefs.getInt(kPrefIslandGlassHighlight) ?? 42)
        .clamp(0, 100);
    islandGlassShadow = (prefs.getInt(kPrefIslandGlassShadow) ?? 14).clamp(
      0,
      100,
    );
    islandGlassLightDirection =
        (prefs.getInt(kPrefIslandGlassLightDirection) ?? 243) % 360;
    islandGlassDispersion = (prefs.getInt(kPrefIslandGlassDispersion) ?? 18)
        .clamp(0, 100);
    islandGlassGyroscope = prefs.getBool(kPrefIslandGlassGyroscope) ?? true;
    islandGlassHdrHighlight =
        prefs.getBool(kPrefIslandGlassHdrHighlight) ?? false;
    islandGlassTrueRefraction =
        prefs.getBool(kPrefIslandGlassTrueRefraction) ?? false;
    islandRefractionSmallEnabled =
        prefs.getBool(kPrefIslandRefractionSmallEnabled) ??
        islandGlassTrueRefraction;
    islandRefractionBigEnabled =
        prefs.getBool(kPrefIslandRefractionBigEnabled) ??
        islandGlassTrueRefraction;
    islandRefractionExpandEnabled =
        prefs.getBool(kPrefIslandRefractionExpandEnabled) ??
        islandGlassTrueRefraction;
    final migratedGlassSettings = <String, bool>{
      kPrefIslandGlassSmallEnabled: islandGlassSmallEnabled,
      kPrefIslandGlassBigEnabled: islandGlassBigEnabled,
      kPrefIslandGlassExpandEnabled: islandGlassExpandEnabled,
      kPrefIslandRefractionSmallEnabled: islandRefractionSmallEnabled,
      kPrefIslandRefractionBigEnabled: islandRefractionBigEnabled,
      kPrefIslandRefractionExpandEnabled: islandRefractionExpandEnabled,
    };
    for (final entry in migratedGlassSettings.entries) {
      if (!prefs.containsKey(entry.key)) {
        await prefs.setBool(entry.key, entry.value);
      }
    }
    islandGlassCaptureFps = (prefs.getInt(kPrefIslandGlassCaptureFps) ?? 20)
        .clamp(1, 90);
    islandGlassCaptureQuality =
        (prefs.getInt(kPrefIslandGlassCaptureQuality) ?? 30).clamp(10, 100);
    islandHeight = prefs.getDouble(kPrefIslandHeight) ?? 0;
    islandTopOffset = prefs.getDouble(kPrefIslandTopOffset) ?? 0;
    islandTextColorMode = _normalizeIslandTextColorMode(
      prefs.getString(kPrefIslandTextColorMode),
    );
    final storedFocusTextColorMode = prefs.getString(
      kPrefFocusNotificationTextColorMode,
    );
    focusNotificationTextColorMode = _normalizeFocusTextColorMode(
      storedFocusTextColorMode,
    );
    if (storedFocusTextColorMode != null &&
        storedFocusTextColorMode != focusNotificationTextColorMode) {
      await prefs.remove(kPrefFocusNotificationTextColorMode);
    }
    final storedMediaTextColorMode = prefs.getString(
      kPrefMediaNotificationTextColorMode,
    );
    mediaNotificationTextColorMode = _normalizeFocusTextColorMode(
      storedMediaTextColorMode,
    );
    if (storedMediaTextColorMode != null &&
        storedMediaTextColorMode != mediaNotificationTextColorMode) {
      await prefs.remove(kPrefMediaNotificationTextColorMode);
    }
    alwaysShowIslandOutline =
        prefs.getBool(kPrefAlwaysShowIslandOutline) ?? false;
    alwaysShowFocusOutline =
        prefs.getBool(kPrefAlwaysShowFocusOutline) ?? false;
    outerGlowRange = (prefs.getInt(kPrefOuterGlowRange) ?? 0).clamp(0, 100);
    keepIsland = prefs.getBool(kPrefKeepIsland) ?? false;
    keepIslandDisplayTiming = switch (prefs.getString(
      kPrefKeepIslandDisplayTiming,
    )) {
      kKeepIslandDisplayTimingCharging => kKeepIslandDisplayTimingCharging,
      _ => kKeepIslandDisplayTimingAlways,
    };
    keepIslandShowNotification =
        prefs.getBool(kPrefKeepIslandShowNotification) ?? false;
    keepIslandAutoHide = prefs.getBool(kPrefKeepIslandAutoHide) ?? true;
    keepIslandHideLandscape =
        prefs.getBool(kPrefKeepIslandHideLandscape) ?? false;
    keepIslandHighlightColor =
        prefs.getString(kPrefKeepIslandHighlightColor) ?? '';
    keepIslandLeftHighlight =
        prefs.getBool(kPrefKeepIslandLeftHighlight) ?? false;
    keepIslandRightHighlight =
        prefs.getBool(kPrefKeepIslandRightHighlight) ?? false;
    keepIslandLeftContents = _decodeKeepIslandContents(
      prefs.getString(kPrefKeepIslandLeftContent),
      const ['{time.HH:mm}'],
    );
    keepIslandRightContents = _decodeKeepIslandContents(
      prefs.getString(kPrefKeepIslandRightContent),
      const ['{battery.level}'],
    );
    keepIslandCarouselInterval =
        (prefs.getInt(kPrefKeepIslandCarouselInterval) ?? 5).clamp(1, 6000);
    keepIslandFocusNotification =
        prefs.getBool(kPrefKeepIslandFocusNotification) ?? false;
    keepIslandFocusContentType = switch (prefs.getString(
      kPrefKeepIslandFocusContentType,
    )) {
      kKeepIslandFocusContentPerformance => kKeepIslandFocusContentPerformance,
      kKeepIslandFocusContentDevice => kKeepIslandFocusContentDevice,
      kKeepIslandFocusContentCharging => kKeepIslandFocusContentCharging,
      _ => kKeepIslandFocusContentNotification,
    };
    keepIslandExpandTextColorMode = switch (prefs.getString(
      kPrefKeepIslandExpandTextColorMode,
    )) {
      kKeepIslandExpandTextColorFollowStatusBar =>
        kKeepIslandExpandTextColorFollowStatusBar,
      kKeepIslandExpandTextColorInvertStatusBar =>
        kKeepIslandExpandTextColorInvertStatusBar,
      kKeepIslandExpandTextColorBlack => kKeepIslandExpandTextColorBlack,
      _ => kKeepIslandExpandTextColorWhite,
    };
    keepIslandNotificationTitle =
        prefs.getString(kPrefKeepIslandNotificationTitle) ?? '';
    keepIslandNotificationContent =
        prefs.getString(kPrefKeepIslandNotificationContent) ?? '';
    keepIslandShowIslandIcon =
        prefs.getBool(kPrefKeepIslandShowIslandIcon) ?? false;
    keepIslandCustomIconPath =
        prefs.getString(kPrefKeepIslandCustomIconPath) ?? '';
    tempHideBehaviorEnabled =
        prefs.getBool(kPrefTempHideBehaviorEnabled) ?? false;
    tempHideScreenPinning = prefs.getBool(kPrefTempHideScreenPinning) ?? true;
    tempHideBouncerShowing = prefs.getBool(kPrefTempHideBouncerShowing) ?? true;
    tempHideFullscreen = prefs.getBool(kPrefTempHideFullscreen) ?? true;
    tempHideScreenLocked = prefs.getBool(kPrefTempHideScreenLocked) ?? true;
    tempHideNotificationCenter =
        prefs.getBool(kPrefTempHideNotificationCenter) ?? true;
    tempHideForegroundApp = prefs.getBool(kPrefTempHideForegroundApp) ?? true;
    tempHideFullscreenLandscapeDisable =
        prefs.getBool(kPrefTempHideFullscreenLandscapeDisable) ?? false;
    themeSeedColor = prefs.getInt(kPrefThemeSeedColor) ?? 0xFF6750A4;
    blurBars = prefs.getBool(kPrefBlurBars) ?? true;
    debugLog = prefs.getBool(kPrefDebugLog) ?? false;
    onboardingCompleted = prefs.getBool(kPrefOnboardingCompleted) ?? false;
    loading = false;
    notifyListeners();
  }

  Future<void> setOnboardingCompleted(bool value) async {
    if (onboardingCompleted == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefOnboardingCompleted, value);
    onboardingCompleted = value;
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

  Future<void> setSettingsHomeEntry(bool value) async {
    if (settingsHomeEntry == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefSettingsHomeEntry, value);
    settingsHomeEntry = value;
    notifyListeners();
  }

  Future<void> setSettingsHomeEntryIconStyle(String value) async {
    final normalized = _normalizeSettingsHomeEntryIconStyle(value);
    if (settingsHomeEntryIconStyle == normalized) return;
    final prefs = await _getPrefs();
    await prefs.setString(kPrefSettingsHomeEntryIconStyle, normalized);
    settingsHomeEntryIconStyle = normalized;
    notifyListeners();
  }

  Future<void> setBluetoothIsland(bool value) async {
    if (bluetoothIsland == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefBluetoothIsland, value);
    bluetoothIsland = value;
    notifyListeners();
  }

  Future<void> setBluetoothIslandShowDeviceName(bool value) async {
    if (bluetoothIslandShowDeviceName == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefBluetoothIslandShowDeviceName, value);
    bluetoothIslandShowDeviceName = value;
    notifyListeners();
  }

  Future<void> setBluetoothIslandOuterGlow(bool value) async {
    if (bluetoothIslandOuterGlow == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefBluetoothIslandOuterGlow, value);
    bluetoothIslandOuterGlow = value;
    notifyListeners();
  }

  Future<void> setBluetoothIslandOuterGlowColor(String value) async {
    final normalized = value.trim();
    if (bluetoothIslandOuterGlowColor == normalized) return;
    final prefs = await _getPrefs();
    if (normalized.isEmpty) {
      await prefs.remove(kPrefBluetoothIslandOuterGlowColor);
    } else {
      await prefs.setString(kPrefBluetoothIslandOuterGlowColor, normalized);
    }
    bluetoothIslandOuterGlowColor = normalized;
    notifyListeners();
  }

  Future<void> setBluetoothIslandWhitelistEnabled(bool value) async {
    if (bluetoothIslandWhitelistEnabled == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefBluetoothIslandWhitelistEnabled, value);
    bluetoothIslandWhitelistEnabled = value;
    notifyListeners();
  }

  Future<void> setBluetoothIslandWhitelistAddresses(
    List<String> addresses,
  ) async {
    final prefs = await _getPrefs();
    if (addresses.isEmpty) {
      await prefs.remove(kPrefBluetoothIslandWhitelistAddresses);
    } else {
      await prefs.setString(
        kPrefBluetoothIslandWhitelistAddresses,
        jsonEncode(addresses),
      );
    }
    bluetoothIslandWhitelistAddresses = List.unmodifiable(addresses);
    notifyListeners();
  }

  static List<String> _decodeStringList(String? raw) {
    if (raw == null || raw.isEmpty) return [];
    try {
      final decoded = jsonDecode(raw);
      if (decoded is List) {
        return decoded.map((e) => e.toString()).toList();
      }
    } catch (_) {}
    return [];
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

  Future<void> setRoundIconRadius(int value) async {
    final clamped = value.clamp(0, 100);
    if (roundIconRadius == clamped) return;
    final prefs = await _getPrefs();
    await prefs.setInt(kPrefRoundIconRadius, clamped);
    roundIconRadius = clamped;
    notifyListeners();
  }

  Future<void> setIslandIconSize(int value) async {
    final clamped = value.clamp(50, 150);
    if (islandIconSize == clamped) return;
    final prefs = await _getPrefs();
    if (clamped == 100) {
      await prefs.remove(kPrefIslandIconSize);
    } else {
      await prefs.setInt(kPrefIslandIconSize, clamped);
    }
    islandIconSize = clamped;
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

  Future<void> setBigIslandMaxWidth(int value) async {
    final clamped = value.clamp(0, 500);
    if (bigIslandMaxWidth == clamped) return;
    final prefs = await _getPrefs();
    await prefs.setInt(kPrefBigIslandMaxWidth, clamped);
    bigIslandMaxWidth = clamped;
    notifyListeners();
  }

  Future<void> setBigIslandMinWidth(int value) async {
    final clamped = value.clamp(0, 500);
    if (bigIslandMinWidth == clamped) return;
    final prefs = await _getPrefs();
    if (clamped <= 0) {
      await prefs.remove(kPrefBigIslandMinWidth);
    } else {
      await prefs.setInt(kPrefBigIslandMinWidth, clamped);
    }
    bigIslandMinWidth = clamped;
    notifyListeners();
  }

  Future<void> setSmoothIsland(bool value) async {
    if (smoothIsland == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefSmoothIsland, value);
    smoothIsland = value;
    notifyListeners();
  }

  Future<void> setSmoothIslandSmoothing(double value) async {
    final clamped = value.clamp(0.0, 1.0).toDouble();
    if (smoothIslandSmoothing == clamped) return;
    final prefs = await _getPrefs();
    await prefs.setDouble(kPrefSmoothIslandSmoothing, clamped);
    smoothIslandSmoothing = clamped;
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

  Future<void> setChargeIsland(bool value) async {
    if (chargeIsland == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefChargeIsland, value);
    chargeIsland = value;
    notifyListeners();
  }

  Future<void> setChargeIslandLeftMode(String value) async {
    final normalized = _normalizeChargeIslandMode(value);
    if (chargeIslandLeftMode == normalized) return;
    final prefs = await _getPrefs();
    await prefs.setString(kPrefChargeIslandLeftMode, normalized);
    chargeIslandLeftMode = normalized;
    notifyListeners();
  }

  Future<void> setChargeIslandRightMode(String value) async {
    final normalized = _normalizeChargeIslandMode(value);
    if (chargeIslandRightMode == normalized) return;
    final prefs = await _getPrefs();
    await prefs.setString(kPrefChargeIslandRightMode, normalized);
    chargeIslandRightMode = normalized;
    notifyListeners();
  }

  Future<void> setChargeIslandDurationMode(String value) async {
    final normalized = _normalizeChargeIslandDurationMode(value);
    if (chargeIslandDurationMode == normalized) return;
    final prefs = await _getPrefs();
    await prefs.setString(kPrefChargeIslandDurationMode, normalized);
    chargeIslandDurationMode = normalized;
    notifyListeners();
  }

  Future<void> setChargeIslandDurationSeconds(int value) async {
    final normalized = _normalizeChargeIslandDurationSeconds(value);
    if (chargeIslandDurationSeconds == normalized) return;
    final prefs = await _getPrefs();
    await prefs.setInt(kPrefChargeIslandDurationSeconds, normalized);
    chargeIslandDurationSeconds = normalized;
    notifyListeners();
  }

  Future<void> setChargeIslandOuterGlow(bool value) async {
    if (chargeIslandOuterGlow == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefChargeIslandOuterGlow, value);
    chargeIslandOuterGlow = value;
    notifyListeners();
  }

  Future<void> setFaceUnlockIslandFirstFloat(bool value) async {
    if (faceUnlockIslandFirstFloat == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefFaceUnlockIslandFirstFloat, value);
    faceUnlockIslandFirstFloat = value;
    notifyListeners();
  }

  Future<void> setFaceUnlockIsland(bool value) async {
    if (faceUnlockIsland == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefFaceUnlockIsland, value);
    faceUnlockIsland = value;
    notifyListeners();
  }

  Future<void> setFaceUnlockIslandAnimationStyle(String value) async {
    final normalized = _normalizeFaceUnlockIslandAnimationStyle(value);
    if (faceUnlockIslandAnimationStyle == normalized) return;
    final prefs = await _getPrefs();
    await prefs.setString(kPrefFaceUnlockIslandAnimationStyle, normalized);
    faceUnlockIslandAnimationStyle = normalized;
    notifyListeners();
  }

  Future<void> setFaceUnlockIslandKeepUntilKeyguardHidden(bool value) async {
    if (faceUnlockIslandKeepUntilKeyguardHidden == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefFaceUnlockIslandKeepUntilKeyguardHidden, value);
    faceUnlockIslandKeepUntilKeyguardHidden = value;
    notifyListeners();
  }

  Future<void> setHideLockscreenFaceUnlockIcon(bool value) async {
    if (hideLockscreenFaceUnlockIcon == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefHideLockscreenFaceUnlockIcon, value);
    hideLockscreenFaceUnlockIcon = value;
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

  Future<void> setBluetoothIslandDisplayDurationSeconds(int value) async {
    final normalized = _normalizeBluetoothIslandDisplayDurationSeconds(value);
    if (bluetoothIslandDisplayDurationSeconds == normalized) return;
    final prefs = await _getPrefs();
    await prefs.setInt(kPrefBluetoothIslandDisplayDurationSeconds, normalized);
    bluetoothIslandDisplayDurationSeconds = normalized;
    notifyListeners();
  }

  Future<void> setDefaultMarqueeAutoHide(String value) async {
    final normalized = _normalizeMarqueeAutoHide(value);
    if (defaultMarqueeAutoHide == normalized) return;
    final prefs = await _getPrefs();
    await prefs.setString(kPrefDefaultMarqueeAutoHide, normalized);
    defaultMarqueeAutoHide = normalized;
    notifyListeners();
  }

  String _normalizeMarqueeAutoHide(String? value) {
    return switch (value) {
      '1' => '1',
      '2' => '2',
      '1_override' => '1_override',
      '2_override' => '2_override',
      _ => kTriOptOff,
    };
  }

  Future<void> setDefaultFocusNotif(bool value) async {
    if (defaultFocusNotif == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefDefaultFocusNotif, value);
    defaultFocusNotif = value;
    notifyListeners();
  }

  Future<void> setDefaultAodText(bool value) async {
    if (defaultAodText == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefDefaultAodText, value);
    defaultAodText = value;
    notifyListeners();
  }

  Future<void> setDefaultDynamicHighlightColor(bool value) async {
    if (defaultDynamicHighlightColor == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefDefaultDynamicHighlightColor, value);
    defaultDynamicHighlightColor = value;
    notifyListeners();
  }

  String _readOuterGlowMode(
    SharedPreferences prefs, {
    required String modeKey,
    required String legacyBoolKey,
  }) {
    final raw = prefs.get(modeKey);
    if (raw is String) {
      if (raw == kTriOptOn ||
          raw == kTriOptOff ||
          raw == kTriOptFollowDynamic) {
        return raw;
      }
    } else if (raw is bool) {
      return raw ? kTriOptOn : kTriOptOff;
    }
    if (legacyBoolKey != modeKey) {
      final legacy = prefs.getBool(legacyBoolKey);
      if (legacy != null) {
        return legacy ? kTriOptOn : kTriOptOff;
      }
    }
    return kTriOptOff;
  }

  Future<void> setDefaultOuterGlow(String value) async {
    if (defaultOuterGlow == value) return;
    final prefs = await _getPrefs();
    await prefs.setString(kPrefDefaultOuterGlow, value);
    defaultOuterGlow = value;
    notifyListeners();
  }

  Future<void> setDefaultIslandOuterGlow(String value) async {
    if (defaultIslandOuterGlow == value) return;
    final prefs = await _getPrefs();
    await prefs.setString(kPrefDefaultIslandOuterGlow, value);
    defaultIslandOuterGlow = value;
    notifyListeners();
  }

  Future<void> setDefaultForceOuterGlow(bool value) async {
    if (defaultForceOuterGlow == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefDefaultForceOuterGlow, value);
    defaultForceOuterGlow = value;
    notifyListeners();
  }

  Future<void> setDefaultForceIslandOuterGlow(bool value) async {
    if (defaultForceIslandOuterGlow == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefDefaultForceIslandOuterGlow, value);
    defaultForceIslandOuterGlow = value;
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

  Future<void> setDefaultTimeout(int value) async {
    final normalized = value < 1 ? 1 : value;
    if (defaultTimeout == normalized) return;
    final prefs = await _getPrefs();
    await prefs.setInt(kPrefDefaultTimeout, normalized);
    defaultTimeout = normalized;
    notifyListeners();
  }

  Future<void> setFullscreenBehavior(String value) async {
    final normalized = _normalizeSceneBehavior(value);
    if (fullscreenBehavior == normalized) return;
    final prefs = await _getPrefs();
    await prefs.setString(kPrefFullscreenBehavior, normalized);
    fullscreenBehavior = normalized;
    notifyListeners();
  }

  Future<void> setLandscapeBehavior(String value) async {
    final normalized = _normalizeSceneBehavior(value);
    if (landscapeBehavior == normalized) return;
    final prefs = await _getPrefs();
    await prefs.setString(kPrefLandscapeBehavior, normalized);
    landscapeBehavior = normalized;
    notifyListeners();
  }

  Future<void> setDndBehavior(String value) async {
    final normalized = _normalizeDndBehavior(value);
    if (dndBehavior == normalized) return;
    final prefs = await _getPrefs();
    if (normalized == 'default') {
      await prefs.remove(kPrefDndBehavior);
    } else {
      await prefs.setString(kPrefDndBehavior, normalized);
    }
    dndBehavior = normalized;
    notifyListeners();
  }

  Future<void> setExpandedCollapseAction(String value) async {
    final normalized = _normalizeIslandSwipeAction(
      value,
      allowHideIsland: true,
    );
    if (expandedCollapseAction == normalized) return;
    final prefs = await _getPrefs();
    await prefs.setString(kPrefExpandedCollapseAction, normalized);
    expandedCollapseAction = normalized;
    notifyListeners();
  }

  Future<void> setBigIslandCollapseAction(String value) async {
    final normalized = _normalizeIslandSwipeAction(
      value,
      allowHideIsland: false,
    );
    if (bigIslandCollapseAction == normalized) return;
    final prefs = await _getPrefs();
    await prefs.setString(kPrefBigIslandCollapseAction, normalized);
    bigIslandCollapseAction = normalized;
    notifyListeners();
  }

  String _normalizeIslandSwipeAction(
    String? value, {
    required bool allowHideIsland,
  }) {
    return switch (value) {
      kIslandSwipeActionCancelNotification =>
        kIslandSwipeActionCancelNotification,
      kIslandSwipeActionHideIsland when allowHideIsland =>
        kIslandSwipeActionHideIsland,
      _ => kIslandSwipeActionNone,
    };
  }

  String _normalizeDndBehavior(String? value) {
    return switch (value) {
      'fallback' => 'suppress',
      'suppress' => 'suppress',
      'small_only' => 'small_only',
      _ => 'default',
    };
  }

  String _normalizeSceneBehavior(String? value) {
    return switch (value) {
      'fallback' => 'fallback',
      'expand' => 'expand',
      _ => 'off',
    };
  }

  String _normalizeSettingsHomeEntryIconStyle(String? value) {
    return value == kSettingsHomeEntryIconStyleOutline
        ? kSettingsHomeEntryIconStyleOutline
        : kSettingsHomeEntryIconStyleDefault;
  }

  String _normalizeChargeIslandMode(String? value) {
    return switch (value) {
      kChargeIslandModePower => kChargeIslandModePower,
      kChargeIslandModeVoltage => kChargeIslandModeVoltage,
      kChargeIslandModeCurrent => kChargeIslandModeCurrent,
      kChargeIslandModeLevel => kChargeIslandModeLevel,
      kChargeIslandModeTemperature => kChargeIslandModeTemperature,
      _ => kChargeIslandModeDefault,
    };
  }

  String _normalizeFaceUnlockIslandAnimationStyle(String? value) {
    return value == kFaceUnlockIslandAnimationLock
        ? kFaceUnlockIslandAnimationLock
        : kFaceUnlockIslandAnimationDefault;
  }

  String _normalizeChargeIslandDurationMode(String? value) {
    return switch (value) {
      kChargeIslandDurationCustom => kChargeIslandDurationCustom,
      kChargeIslandDurationPersistent => kChargeIslandDurationPersistent,
      _ => kChargeIslandDurationDefault,
    };
  }

  int _normalizeChargeIslandDurationSeconds(int? value) {
    return (value ?? 10).clamp(1, 86400);
  }

  int _normalizeBluetoothIslandDisplayDurationSeconds(int? value) {
    return (value ?? 2).clamp(1, 86400);
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

  Future<void> setAiCustomFields(String value) async {
    if (aiCustomFields == value) return;
    final prefs = await _getPrefs();
    await prefs.setString(kPrefAiCustomFields, value);
    aiCustomFields = value;
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

  Future<void> setAiTriggerCharCount(int value) async {
    final clamped = value.clamp(0, 100);
    if (aiTriggerCharCount == clamped) return;
    final prefs = await _getPrefs();
    await prefs.setInt(kPrefAiTriggerCharCount, clamped);
    aiTriggerCharCount = clamped;
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

  Future<void> setIslandBgSmallPath(String value) async {
    final normalized = value.trim();
    // Always write and notify — the file content may have changed even if
    // the path string is the same (overwrite), so the UI must refresh.
    final prefs = await _getPrefs();
    if (normalized.isEmpty) {
      await prefs.remove(kPrefIslandBgSmallPath);
    } else {
      await prefs.setString(kPrefIslandBgSmallPath, normalized);
    }
    islandBgSmallPath = normalized;
    notifyListeners();
  }

  Future<void> setIslandBgBigPath(String value) async {
    final normalized = value.trim();
    // Always write and notify — the file content may have changed even if
    // the path string is the same (overwrite), so the UI must refresh.
    final prefs = await _getPrefs();
    if (normalized.isEmpty) {
      await prefs.remove(kPrefIslandBgBigPath);
    } else {
      await prefs.setString(kPrefIslandBgBigPath, normalized);
    }
    islandBgBigPath = normalized;
    notifyListeners();
  }

  Future<void> setIslandBgExpandPath(String value) async {
    final normalized = value.trim();
    // Always write and notify — the file content may have changed even if
    // the path string is the same (overwrite), so the UI must refresh.
    final prefs = await _getPrefs();
    if (normalized.isEmpty) {
      await prefs.remove(kPrefIslandBgExpandPath);
    } else {
      await prefs.setString(kPrefIslandBgExpandPath, normalized);
    }
    islandBgExpandPath = normalized;
    notifyListeners();
  }

  Future<void> setIslandBlurSmall({
    required bool enabled,
    required int radius,
    required String color,
  }) => _setIslandBlur(
    enabledKey: kPrefIslandBlurSmallEnabled,
    radiusKey: kPrefIslandBlurSmallRadius,
    colorKey: kPrefIslandBlurSmallColor,
    enabled: enabled,
    radius: radius,
    color: color,
    update: (nextEnabled, nextRadius, nextColor) {
      islandBlurSmallEnabled = nextEnabled;
      islandBlurSmallRadius = nextRadius;
      islandBlurSmallColor = nextColor;
    },
  );

  Future<void> setIslandBlurBig({
    required bool enabled,
    required int radius,
    required String color,
  }) => _setIslandBlur(
    enabledKey: kPrefIslandBlurBigEnabled,
    radiusKey: kPrefIslandBlurBigRadius,
    colorKey: kPrefIslandBlurBigColor,
    enabled: enabled,
    radius: radius,
    color: color,
    update: (nextEnabled, nextRadius, nextColor) {
      islandBlurBigEnabled = nextEnabled;
      islandBlurBigRadius = nextRadius;
      islandBlurBigColor = nextColor;
    },
  );

  Future<void> setIslandBlurExpand({
    required bool enabled,
    required int radius,
    required String color,
  }) => _setIslandBlur(
    enabledKey: kPrefIslandBlurExpandEnabled,
    radiusKey: kPrefIslandBlurExpandRadius,
    colorKey: kPrefIslandBlurExpandColor,
    enabled: enabled,
    radius: radius,
    color: color,
    update: (nextEnabled, nextRadius, nextColor) {
      islandBlurExpandEnabled = nextEnabled;
      islandBlurExpandRadius = nextRadius;
      islandBlurExpandColor = nextColor;
    },
  );

  Future<void> _setIslandBlur({
    required String enabledKey,
    required String radiusKey,
    required String colorKey,
    required bool enabled,
    required int radius,
    required String color,
    required void Function(bool enabled, int radius, String color) update,
  }) async {
    final normalizedRadius = radius.clamp(0, 100);
    final normalizedColor = color.trim().toUpperCase();
    final prefs = await _getPrefs();
    await prefs.setBool(enabledKey, false);
    await prefs.setInt(radiusKey, normalizedRadius);
    await prefs.setString(colorKey, normalizedColor);
    if (enabled) await prefs.setBool(enabledKey, true);
    update(enabled, normalizedRadius, normalizedColor);
    notifyListeners();
  }

  Future<void> setIslandGlassEnabled(bool value) =>
      _setIslandGlassBool(kPrefIslandGlassEnabled, value, () {
        islandGlassEnabled = value;
      });

  Future<void> setIslandGlassGyroscope(bool value) =>
      _setIslandGlassBool(kPrefIslandGlassGyroscope, value, () {
        islandGlassGyroscope = value;
      });

  Future<void> setIslandGlassHdrHighlight(bool value) =>
      _setIslandGlassBool(kPrefIslandGlassHdrHighlight, value, () {
        islandGlassHdrHighlight = value;
      });

  Future<void> setIslandGlassTrueRefraction(bool value) =>
      _setIslandGlassBool(kPrefIslandGlassTrueRefraction, value, () {
        islandGlassTrueRefraction = value;
      });

  Future<void> setIslandGlassSmallEnabled(bool value) =>
      _setIslandGlassBool(kPrefIslandGlassSmallEnabled, value, () {
        islandGlassSmallEnabled = value;
      });

  Future<void> setIslandGlassBigEnabled(bool value) =>
      _setIslandGlassBool(kPrefIslandGlassBigEnabled, value, () {
        islandGlassBigEnabled = value;
      });

  Future<void> setIslandGlassExpandEnabled(bool value) =>
      _setIslandGlassBool(kPrefIslandGlassExpandEnabled, value, () {
        islandGlassExpandEnabled = value;
      });

  Future<void> setIslandRefractionSmallEnabled(bool value) =>
      _setIslandGlassBool(kPrefIslandRefractionSmallEnabled, value, () {
        islandRefractionSmallEnabled = value;
      });

  Future<void> setIslandRefractionBigEnabled(bool value) =>
      _setIslandGlassBool(kPrefIslandRefractionBigEnabled, value, () {
        islandRefractionBigEnabled = value;
      });

  Future<void> setIslandRefractionExpandEnabled(bool value) =>
      _setIslandGlassBool(kPrefIslandRefractionExpandEnabled, value, () {
        islandRefractionExpandEnabled = value;
      });

  Future<void> setIslandGlassEdgeWidth(int value) => _setIslandGlassInt(
    kPrefIslandGlassEdgeWidth,
    value,
    4,
    40,
    (next) => islandGlassEdgeWidth = next,
  );

  Future<void> setIslandGlassRefraction(int value) => _setIslandGlassInt(
    kPrefIslandGlassRefraction,
    value,
    0,
    40,
    (next) => islandGlassRefraction = next,
  );

  Future<void> setIslandGlassHighlight(int value) => _setIslandGlassInt(
    kPrefIslandGlassHighlight,
    value,
    0,
    100,
    (next) => islandGlassHighlight = next,
  );

  Future<void> setIslandGlassShadow(int value) => _setIslandGlassInt(
    kPrefIslandGlassShadow,
    value,
    0,
    100,
    (next) => islandGlassShadow = next,
  );

  Future<void> setIslandGlassLightDirection(int value) => _setIslandGlassInt(
    kPrefIslandGlassLightDirection,
    value,
    0,
    359,
    (next) => islandGlassLightDirection = next,
  );

  Future<void> setIslandGlassDispersion(int value) => _setIslandGlassInt(
    kPrefIslandGlassDispersion,
    value,
    0,
    100,
    (next) => islandGlassDispersion = next,
  );

  Future<void> setIslandGlassCaptureSettings({
    required int fps,
    required int quality,
  }) async {
    final normalizedFps = fps.clamp(1, 90);
    final normalizedQuality = quality.clamp(10, 100);
    final prefs = await _getPrefs();
    await prefs.setInt(kPrefIslandGlassCaptureFps, normalizedFps);
    await prefs.setInt(kPrefIslandGlassCaptureQuality, normalizedQuality);
    islandGlassCaptureFps = normalizedFps;
    islandGlassCaptureQuality = normalizedQuality;
    notifyListeners();
  }

  Future<void> _setIslandGlassBool(
    String key,
    bool value,
    VoidCallback update,
  ) async {
    final prefs = await _getPrefs();
    await prefs.setBool(key, value);
    update();
    notifyListeners();
  }

  Future<void> _setIslandGlassInt(
    String key,
    int value,
    int min,
    int max,
    ValueChanged<int> update,
  ) async {
    final normalized = value.clamp(min, max);
    final prefs = await _getPrefs();
    await prefs.setInt(key, normalized);
    update(normalized);
    notifyListeners();
  }

  Future<void> setIslandHeight(double value) async {
    if (islandHeight == value) return;
    final prefs = await _getPrefs();
    if (value <= 0) {
      await prefs.remove(kPrefIslandHeight);
    } else {
      await prefs.setDouble(kPrefIslandHeight, value);
    }
    islandHeight = value;
    notifyListeners();
  }

  Future<void> setIslandTopOffset(double value) async {
    final clamped = value.clamp(-100, 100).toDouble();
    if (islandTopOffset == clamped) return;
    final prefs = await _getPrefs();
    if (clamped == 0) {
      await prefs.remove(kPrefIslandTopOffset);
    } else {
      await prefs.setDouble(kPrefIslandTopOffset, clamped);
    }
    islandTopOffset = clamped;
    notifyListeners();
  }

  Future<void> setOuterGlowRange(int value) async {
    final clamped = value.clamp(0, 100);
    if (outerGlowRange == clamped) return;
    final prefs = await _getPrefs();
    if (clamped == 0) {
      await prefs.remove(kPrefOuterGlowRange);
    } else {
      await prefs.setInt(kPrefOuterGlowRange, clamped);
    }
    outerGlowRange = clamped;
    notifyListeners();
  }

  Future<void> setIslandTextColorMode(String value) async {
    final normalized = _normalizeIslandTextColorMode(value);
    if (islandTextColorMode == normalized) return;
    final prefs = await _getPrefs();
    if (normalized == kIslandTextColorDefault) {
      await prefs.remove(kPrefIslandTextColorMode);
    } else {
      await prefs.setString(kPrefIslandTextColorMode, normalized);
    }
    islandTextColorMode = normalized;
    notifyListeners();
  }

  Future<void> setFocusNotificationTextColorMode(String value) async {
    final normalized = _normalizeFocusTextColorMode(value);
    if (focusNotificationTextColorMode == normalized) return;
    final prefs = await _getPrefs();
    if (normalized == kIslandTextColorDefault) {
      await prefs.remove(kPrefFocusNotificationTextColorMode);
    } else {
      await prefs.setString(kPrefFocusNotificationTextColorMode, normalized);
    }
    focusNotificationTextColorMode = normalized;
    notifyListeners();
  }

  Future<void> setMediaNotificationTextColorMode(String value) async {
    final normalized = _normalizeFocusTextColorMode(value);
    if (mediaNotificationTextColorMode == normalized) return;
    final prefs = await _getPrefs();
    if (normalized == kIslandTextColorDefault) {
      await prefs.remove(kPrefMediaNotificationTextColorMode);
    } else {
      await prefs.setString(kPrefMediaNotificationTextColorMode, normalized);
    }
    mediaNotificationTextColorMode = normalized;
    notifyListeners();
  }

  Future<void> setKeepIsland(bool value) async {
    if (keepIsland == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefKeepIsland, value);
    keepIsland = value;
    notifyListeners();
    await _refreshKeepIsland();
  }

  Future<void> setKeepIslandDisplayTiming(String value) async {
    await _setStringPref(
      kPrefKeepIslandDisplayTiming,
      value == kKeepIslandDisplayTimingCharging
          ? kKeepIslandDisplayTimingCharging
          : kKeepIslandDisplayTimingAlways,
      (v) => keepIslandDisplayTiming = v,
    );
    await _refreshKeepIsland();
  }

  Future<void> setKeepIslandShowNotification(bool value) async {
    if (keepIslandShowNotification == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefKeepIslandShowNotification, value);
    keepIslandShowNotification = value;
    if (value && !keepIslandFocusNotification && keepIsland) {
      await prefs.setBool(kPrefKeepIslandFocusNotification, true);
      keepIslandFocusNotification = true;
    }
    notifyListeners();
    await _refreshKeepIsland();
  }

  Future<void> setKeepIslandAutoHide(bool value) async {
    if (keepIslandAutoHide == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefKeepIslandAutoHide, value);
    keepIslandAutoHide = value;
    notifyListeners();
    await _refreshKeepIsland();
  }

  Future<void> setKeepIslandHideLandscape(bool value) async {
    if (keepIslandHideLandscape == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefKeepIslandHideLandscape, value);
    keepIslandHideLandscape = value;
    notifyListeners();
    await _refreshKeepIsland();
  }

  Future<void> setKeepIslandHighlightColor(String value) async {
    final normalized = value.trim();
    if (keepIslandHighlightColor == normalized) return;
    final prefs = await _getPrefs();
    if (normalized.isEmpty) {
      await prefs.remove(kPrefKeepIslandHighlightColor);
    } else {
      await prefs.setString(kPrefKeepIslandHighlightColor, normalized);
    }
    keepIslandHighlightColor = normalized;
    notifyListeners();
    await _refreshKeepIsland();
  }

  Future<void> setKeepIslandLeftHighlight(bool value) async {
    await _setBoolPref(
      kPrefKeepIslandLeftHighlight,
      value,
      (v) => keepIslandLeftHighlight = v,
    );
    await _refreshKeepIsland();
  }

  Future<void> setKeepIslandRightHighlight(bool value) async {
    await _setBoolPref(
      kPrefKeepIslandRightHighlight,
      value,
      (v) => keepIslandRightHighlight = v,
    );
    await _refreshKeepIsland();
  }

  Future<void> setKeepIslandLeftContents(List<String> values) async {
    await _setKeepIslandContents(
      kPrefKeepIslandLeftContent,
      values,
      (v) => keepIslandLeftContents = v,
    );
    await _refreshKeepIsland();
  }

  Future<void> setKeepIslandRightContents(List<String> values) async {
    await _setKeepIslandContents(
      kPrefKeepIslandRightContent,
      values,
      (v) => keepIslandRightContents = v,
    );
    await _refreshKeepIsland();
  }

  Future<void> setKeepIslandCarouselInterval(int value) async {
    final normalized = value.clamp(1, 6000);
    if (keepIslandCarouselInterval == normalized) return;
    final prefs = await _getPrefs();
    await prefs.setInt(kPrefKeepIslandCarouselInterval, normalized);
    keepIslandCarouselInterval = normalized;
    notifyListeners();
    await _refreshKeepIsland();
  }

  Future<void> _setKeepIslandContents(
    String key,
    List<String> values,
    void Function(List<String>) assign,
  ) async {
    final normalized = values.map((value) => value.trim()).toList();
    final prefs = await _getPrefs();
    await prefs.setString(key, jsonEncode(normalized));
    assign(List.unmodifiable(normalized));
    notifyListeners();
  }

  static List<String> _decodeKeepIslandContents(
    String? raw,
    List<String> defaults,
  ) {
    if (raw == null) return List.unmodifiable(defaults);
    try {
      final decoded = jsonDecode(raw);
      if (decoded is List) {
        return List.unmodifiable(
          decoded.map((value) => value.toString()).toList(),
        );
      }
    } catch (_) {
      // Existing versions stored one plain expression under the same key.
    }
    return List.unmodifiable([raw]);
  }

  Future<void> setKeepIslandFocusNotification(bool value) async {
    if (keepIslandFocusNotification == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefKeepIslandFocusNotification, value);
    keepIslandFocusNotification = value;
    notifyListeners();
    await _refreshKeepIsland();
  }

  Future<void> setKeepIslandFocusContentType(String value) async {
    await _setStringPref(kPrefKeepIslandFocusContentType, switch (value) {
      kKeepIslandFocusContentPerformance => kKeepIslandFocusContentPerformance,
      kKeepIslandFocusContentDevice => kKeepIslandFocusContentDevice,
      kKeepIslandFocusContentCharging => kKeepIslandFocusContentCharging,
      _ => kKeepIslandFocusContentNotification,
    }, (v) => keepIslandFocusContentType = v);
    await _refreshKeepIsland();
  }

  Future<void> setKeepIslandExpandTextColorMode(String value) async {
    await _setStringPref(kPrefKeepIslandExpandTextColorMode, switch (value) {
      kKeepIslandExpandTextColorFollowStatusBar =>
        kKeepIslandExpandTextColorFollowStatusBar,
      kKeepIslandExpandTextColorInvertStatusBar =>
        kKeepIslandExpandTextColorInvertStatusBar,
      kKeepIslandExpandTextColorBlack => kKeepIslandExpandTextColorBlack,
      _ => kKeepIslandExpandTextColorWhite,
    }, (v) => keepIslandExpandTextColorMode = v);
    await _refreshKeepIsland();
  }

  Future<void> setKeepIslandNotificationTitle(String value) async {
    await _setStringPref(
      kPrefKeepIslandNotificationTitle,
      value.trim(),
      (v) => keepIslandNotificationTitle = v,
    );
    await _refreshKeepIsland();
  }

  Future<void> setKeepIslandNotificationContent(String value) async {
    await _setStringPref(
      kPrefKeepIslandNotificationContent,
      value.trim(),
      (v) => keepIslandNotificationContent = v,
    );
    await _refreshKeepIsland();
  }

  Future<void> setKeepIslandShowIslandIcon(bool value) async {
    await _setBoolPref(
      kPrefKeepIslandShowIslandIcon,
      value,
      (v) => keepIslandShowIslandIcon = v,
    );
    await _refreshKeepIsland();
  }

  Future<void> setKeepIslandCustomIconPath(String value) async {
    await _setStringPref(
      kPrefKeepIslandCustomIconPath,
      value.trim(),
      (v) => keepIslandCustomIconPath = v,
    );
    await _refreshKeepIsland();
  }

  Future<void> _refreshKeepIsland() async {
    const channel = MethodChannel('io.github.hyperisland/test');
    try {
      await channel.invokeMethod<bool>('refreshKeepIsland');
    } catch (_) {}
  }

  Future<void> setTempHideBehaviorEnabled(bool value) => _setBoolPref(
    kPrefTempHideBehaviorEnabled,
    value,
    (v) => tempHideBehaviorEnabled = v,
  );

  Future<void> setTempHideScreenPinning(bool value) => _setBoolPref(
    kPrefTempHideScreenPinning,
    value,
    (v) => tempHideScreenPinning = v,
  );

  Future<void> setTempHideBouncerShowing(bool value) => _setBoolPref(
    kPrefTempHideBouncerShowing,
    value,
    (v) => tempHideBouncerShowing = v,
  );

  Future<void> setTempHideFullscreen(bool value) => _setBoolPref(
    kPrefTempHideFullscreen,
    value,
    (v) => tempHideFullscreen = v,
  );

  Future<void> setTempHideScreenLocked(bool value) => _setBoolPref(
    kPrefTempHideScreenLocked,
    value,
    (v) => tempHideScreenLocked = v,
  );

  Future<void> setTempHideNotificationCenter(bool value) => _setBoolPref(
    kPrefTempHideNotificationCenter,
    value,
    (v) => tempHideNotificationCenter = v,
  );

  Future<void> setTempHideForegroundApp(bool value) => _setBoolPref(
    kPrefTempHideForegroundApp,
    value,
    (v) => tempHideForegroundApp = v,
  );

  Future<void> setTempHideFullscreenLandscapeDisable(bool value) =>
      _setBoolPref(
        kPrefTempHideFullscreenLandscapeDisable,
        value,
        (v) => tempHideFullscreenLandscapeDisable = v,
      );

  Future<void> setAlwaysShowIslandOutline(bool value) => _setBoolPref(
    kPrefAlwaysShowIslandOutline,
    value,
    (v) => alwaysShowIslandOutline = v,
  );

  Future<void> setAlwaysShowFocusOutline(bool value) => _setBoolPref(
    kPrefAlwaysShowFocusOutline,
    value,
    (v) => alwaysShowFocusOutline = v,
  );

  Future<void> _setBoolPref(
    String key,
    bool value,
    void Function(bool value) update,
  ) async {
    final prefs = await _getPrefs();
    await prefs.setBool(key, value);
    update(value);
    notifyListeners();
  }

  Future<void> _setStringPref(
    String key,
    String value,
    void Function(String value) update,
  ) async {
    final prefs = await _getPrefs();
    if (value.isEmpty) {
      await prefs.remove(key);
    } else {
      await prefs.setString(key, value);
    }
    update(value);
    notifyListeners();
  }

  Future<void> setThemeSeedColor(int value) async {
    if (themeSeedColor == value) return;
    final prefs = await _getPrefs();
    await prefs.setInt(kPrefThemeSeedColor, value);
    themeSeedColor = value;
    notifyListeners();
  }

  Future<void> setBlurBars(bool value) async {
    if (blurBars == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefBlurBars, value);
    blurBars = value;
    notifyListeners();
  }

  Future<void> setDebugLog(bool value) async {
    if (debugLog == value) return;
    final prefs = await _getPrefs();
    await prefs.setBool(kPrefDebugLog, value);
    debugLog = value;
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

  String _normalizeIslandTextColorMode(String? value) {
    return switch (value) {
      kIslandTextColorBlack => kIslandTextColorBlack,
      kIslandTextColorFollowBackground => kIslandTextColorFollowBackground,
      kIslandTextColorInvertBackground => kIslandTextColorInvertBackground,
      kIslandTextColorFollowStatusBar => kIslandTextColorFollowStatusBar,
      kIslandTextColorInvertStatusBar => kIslandTextColorInvertStatusBar,
      _ => kIslandTextColorDefault,
    };
  }

  String _normalizeFocusTextColorMode(String? value) {
    return switch (value) {
      kIslandTextColorBlack => kIslandTextColorBlack,
      kIslandTextColorFollowStatusBar => kIslandTextColorFollowStatusBar,
      kIslandTextColorInvertStatusBar => kIslandTextColorInvertStatusBar,
      _ => kIslandTextColorDefault,
    };
  }
}
