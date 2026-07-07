// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get navHome => 'Home';

  @override
  String get navIsland => 'Island';

  @override
  String get navApps => 'Apps';

  @override
  String get navSettings => 'Settings';

  @override
  String get cancel => 'Cancel';

  @override
  String get confirm => 'Confirm';

  @override
  String get ok => 'OK';

  @override
  String get apply => 'Apply';

  @override
  String get noChange => 'No change';

  @override
  String get newVersionFound => 'New Version Available';

  @override
  String currentVersion(String version) {
    return 'Current version: $version';
  }

  @override
  String latestVersion(String version) {
    return 'Latest version: $version';
  }

  @override
  String get later => 'Later';

  @override
  String get goUpdate => 'Update';

  @override
  String get sponsorSupport => 'Support the Author';

  @override
  String get sponsorAuthor => 'Sponsor';

  @override
  String get donorList => 'Donor List';

  @override
  String get documentation => 'Documentation';

  @override
  String versionUpdatedTitle(String version) {
    return 'Updated to $version';
  }

  @override
  String get versionUpdatedContent =>
      'Please restart the scope apps after updating';

  @override
  String get versionUpdatedChangelog => 'Changelog: Tap to view';

  @override
  String get versionUpdatedStarHint =>
      'If you like this app, please give it a free Star';

  @override
  String get restartScope => 'Restart Scope';

  @override
  String get systemUI => 'System UI';

  @override
  String get downloadManager => 'Download Manager';

  @override
  String get xmsf => 'XMSF (Xiaomi Service Framework)';

  @override
  String get notificationTest => 'Notification Test';

  @override
  String get sendTestNotification => 'Send Test Notification';

  @override
  String get customTestNotification => 'Custom Test Notification';

  @override
  String get customTestTitle => 'Title';

  @override
  String get customTestTitleHint => 'Leave empty for default title';

  @override
  String get customTestContent => 'Content';

  @override
  String get customTestContentHint => 'Leave empty for default content';

  @override
  String get clearPreviousNotification => 'Clear previous notification';

  @override
  String get clearPreviousNotificationSubtitle =>
      'Cancel existing island notification before sending';

  @override
  String get enableFloatNotificationSubtitle =>
      'Automatically expand as focus notification when received';

  @override
  String get notes => 'Notes';

  @override
  String get detectingModuleStatus => 'Detecting module status...';

  @override
  String get moduleStatus => 'Module Status';

  @override
  String get activated => 'Activated';

  @override
  String get notActivated => 'Not Activated';

  @override
  String get enableInLSPosed => 'Please enable this module in LSPosed';

  @override
  String get enableSystemUiScopeInLSPosed =>
      'Please select System UI in the LSPosed scope';

  @override
  String lsposedApiVersion(int version) {
    return 'LSPosed API Version: $version';
  }

  @override
  String get updateLSPosedRequired => 'Please update LSPosed version';

  @override
  String get systemNotSupported => 'System Not Supported';

  @override
  String systemNotSupportedSubtitle(int version) {
    return 'Current system does not support Dynamic Island (protocol version $version, requires version 3)';
  }

  @override
  String restartFailed(String message) {
    return 'Restart failed: $message';
  }

  @override
  String get restartRootRequired =>
      'Please check if ROOT permission has been granted to this app';

  @override
  String get note1 =>
      '1. Be sure to read the usage tutorial in the top-right corner before using';

  @override
  String get note2 =>
      '2. Most settings support hot reload; restart the scope if issues occur';

  @override
  String get note3 =>
      '3. After activating in LSPosed Manager, you must restart the related scope apps';

  @override
  String get note4 =>
      '4. This page is only for testing Dynamic Island and glow effect support, not actual effects';

  @override
  String get note5 =>
      '5. For download island, please manually enable \"Download Manager\" scope; the \"Download\" template is recommended';

  @override
  String get behaviorSection => 'Behavior';

  @override
  String get defaultConfigSection => 'Default Channel Settings';

  @override
  String get appearanceSection => 'Appearance';

  @override
  String get configSection => 'Configuration';

  @override
  String get aboutSection => 'About';

  @override
  String get keepFocusNotifTitle => 'Keep notification after download pause';

  @override
  String get keepFocusNotifSubtitle =>
      'Show a focus notification to resume download, but state synchronization issues may occur';

  @override
  String get unlockAllFocusTitle => 'Remove focus notification whitelist';

  @override
  String get unlockAllFocusSubtitle =>
      'Allow all apps to send focus notifications without system authorization';

  @override
  String get unlockFocusAuthTitle =>
      'Remove focus notification signature verification';

  @override
  String get unlockFocusAuthSubtitle =>
      'Allow all apps to send focus notifications to watch/bracelet, bypassing signature check (requires hooking XMSF)';

  @override
  String get checkUpdateOnLaunchTitle => 'Check for updates on launch';

  @override
  String get checkUpdateOnLaunchSubtitle =>
      'Automatically check for new versions when the app starts';

  @override
  String get debugLogTitle => 'Show Debug Logs';

  @override
  String get debugLogSubtitle =>
      'When enabled, Hook debug logs are output; when disabled, only warning and error logs are kept';

  @override
  String get showWelcomeTitle => 'Show welcome message on launch';

  @override
  String get showWelcomeSubtitle =>
      'Display welcome information on Island when the app starts';

  @override
  String get openOnboardingTitle => 'Open onboarding';

  @override
  String get openOnboardingSubtitle =>
      'Review the welcome and quick start flow';

  @override
  String get interactionHapticsTitle => 'Interaction Haptics';

  @override
  String get interactionHapticsSubtitle =>
      'Enable Hyper custom haptic feedback for switches, sliders, and buttons';

  @override
  String get checkUpdate => 'Check for updates';

  @override
  String get alreadyLatest => 'Already on the latest version';

  @override
  String get roundIconTitle => 'Round icon corners';

  @override
  String get roundIconSubtitle => 'Add rounded corners to notification icons';

  @override
  String get marqueeChannelTitle => 'Text Scrolling Island';

  @override
  String get marqueeAutoHideTitle => 'Hide Island after scrolling';

  @override
  String get marqueeAutoHideSubtitle =>
      'Hide the current Island after the message scrolls the selected number of times';

  @override
  String get marqueeAutoHideOnce => 'Scroll once';

  @override
  String get marqueeAutoHideTwice => 'Scroll twice';

  @override
  String get marqueeSpeedTitle => 'Speed';

  @override
  String marqueeSpeedLabel(int speed) {
    return '$speed px/s';
  }

  @override
  String get bigIslandMaxWidthTitle => 'Max Width';

  @override
  String get bigIslandMinWidthTitle => 'Min Width';

  @override
  String get testNotifTooltip => 'Send test notification';

  @override
  String get themeModeTitle => 'Color mode';

  @override
  String get themeModeSystem => 'Follow system';

  @override
  String get themeModeLight => 'Light';

  @override
  String get themeModeDark => 'Dark';

  @override
  String get languageTitle => 'Language';

  @override
  String get languageAuto => 'Follow system';

  @override
  String get languageZh => '中文';

  @override
  String get languageEn => 'English';

  @override
  String get languageJa => '日本語';

  @override
  String get languageRu => 'Русский';

  @override
  String get languageTr => 'Türkçe';

  @override
  String get exportToFile => 'Export to file';

  @override
  String get exportToFileSubtitle => 'Save configuration as a JSON file';

  @override
  String get exportToClipboard => 'Export to clipboard';

  @override
  String get exportToClipboardSubtitle => 'Copy configuration as JSON text';

  @override
  String get exportConfig => 'Export Configuration';

  @override
  String get exportConfigSubtitle => 'Choose to export to file or clipboard';

  @override
  String get importFromFile => 'Import from file';

  @override
  String get importFromFileSubtitle => 'Restore configuration from a JSON file';

  @override
  String get importFromClipboard => 'Import from clipboard';

  @override
  String get importFromClipboardSubtitle =>
      'Restore configuration from JSON text in clipboard';

  @override
  String get importConfig => 'Import Configuration';

  @override
  String get importConfigSubtitle => 'Choose to import from file or clipboard';

  @override
  String get qqGroup => 'QQ Group';

  @override
  String get restartScopeApp =>
      'Please restart the scope app for settings to take effect';

  @override
  String get groupNumberCopied => 'Group number copied to clipboard';

  @override
  String exportedTo(String path) {
    return 'Exported to: $path';
  }

  @override
  String exportFailed(String error) {
    return 'Export failed: $error';
  }

  @override
  String get configCopied => 'Configuration copied to clipboard';

  @override
  String importSuccess(int count) {
    return 'Import successful, $count items, please restart the app';
  }

  @override
  String importFailed(String error) {
    return 'Import failed: $error';
  }

  @override
  String get appAdaptation => 'App Adaptation';

  @override
  String get toastAdaptation => 'Toast Adaptation';

  @override
  String get adaptationModeNotification => 'Notification';

  @override
  String get adaptationModeToast => 'Toast';

  @override
  String toastEnabledAppsCount(Object count) {
    return 'Toast intercept enabled for $count apps';
  }

  @override
  String toastEnabledAppsCountWithSystem(Object count) {
    return 'Toast intercept enabled for $count apps (including system apps)';
  }

  @override
  String selectedAppsCount(int count) {
    return '$count apps selected';
  }

  @override
  String get cancelSelection => 'Cancel selection';

  @override
  String get deselectAll => 'Deselect all';

  @override
  String get selectAll => 'Select all';

  @override
  String get batchChannelSettings => 'Batch channel settings';

  @override
  String get selectEnabledApps => 'Select enabled apps';

  @override
  String get batchEnable => 'Batch enable';

  @override
  String get batchDisable => 'Batch disable';

  @override
  String get multiSelect => 'Multi-select';

  @override
  String get showSystemApps => 'Show system apps';

  @override
  String get refreshList => 'Refresh list';

  @override
  String get enableAll => 'Enable all';

  @override
  String get disableAll => 'Disable all';

  @override
  String enabledAppsCount(int count) {
    return 'Dynamic Island enabled for $count apps';
  }

  @override
  String enabledAppsCountWithSystem(int count) {
    return 'Dynamic Island enabled for $count apps (including system apps)';
  }

  @override
  String get searchApps => 'Search app name or package name';

  @override
  String get noAppsFound =>
      'No installed apps found\nPlease check if app list permission is enabled';

  @override
  String get noMatchingApps => 'No matching apps';

  @override
  String applyToSelectedAppsChannels(int count) {
    return 'Will apply to enabled channels of $count selected apps';
  }

  @override
  String get applyingConfig => 'Applying configuration...';

  @override
  String progressApps(int done, int total) {
    return 'Progress: $done / $total';
  }

  @override
  String batchApplied(int count) {
    return 'Batch applied to $count apps';
  }

  @override
  String get cannotReadChannels => 'Cannot Read Notification Channels';

  @override
  String get rootRequiredMessage =>
      'Reading notification channels requires ROOT permission.\nPlease confirm ROOT permission is granted and try again.';

  @override
  String get enableAllChannels => 'Enable all channels';

  @override
  String get noChannelsFound => 'No notification channels found';

  @override
  String get noChannelsFoundSubtitle =>
      'This app has no notification channels, or they cannot be read';

  @override
  String allChannelsActive(int count) {
    return 'Active for all $count channels';
  }

  @override
  String selectedChannels(int selected, int total) {
    return '$selected / $total channels selected';
  }

  @override
  String allChannelsDisabled(int count) {
    return 'All $count channels (disabled)';
  }

  @override
  String get appDisabledBanner =>
      'App is disabled, the following channel settings have no effect';

  @override
  String channelImportance(String importance, String id) {
    return 'Importance: $importance  ·  $id';
  }

  @override
  String get channelSettings => 'Channel settings';

  @override
  String get toastForwardTitle => 'Forward standard toast';

  @override
  String get toastForwardSubtitle =>
      'Convert this app\'s standard toast text to HyperIsland focus notification and super island';

  @override
  String get toastBlockOriginalTitle => 'Block original toast';

  @override
  String get toastBlockOriginalSubtitle =>
      'After forwarding, block this app\'s original standard toast popup';

  @override
  String get toastShowNotificationTitle => 'Show in notification center';

  @override
  String get toastShowNotificationSubtitle =>
      'Keep this forwarded toast as a visible notification in the shade';

  @override
  String get toastShowIslandIconTitle => 'Show island icon';

  @override
  String get toastShowIslandIconSubtitle =>
      'Show icon on the left side of the large island for forwarded toast';

  @override
  String get toastStandardOnlyHint =>
      'Only standard text toast is handled; custom toast views are ignored.';

  @override
  String get importanceNone => 'None';

  @override
  String get importanceMin => 'Min';

  @override
  String get importanceLow => 'Low';

  @override
  String get importanceDefault => 'Default';

  @override
  String get importanceHigh => 'High';

  @override
  String get importanceUnknown => 'Unknown';

  @override
  String applyToEnabledChannels(int count) {
    return 'Will apply to $count enabled channels';
  }

  @override
  String applyToAllChannels(int count) {
    return 'Will apply to all $count channels';
  }

  @override
  String get templateDownloadName => 'Download';

  @override
  String get templateNotificationIslandName => 'Notification Island';

  @override
  String get templateNotificationIslandLiteName => 'Notification Island|Lite';

  @override
  String get templateDownloadLiteName => 'Download|Lite';

  @override
  String get islandSection => 'Island';

  @override
  String get islandEnabledLabel => 'Enable island';

  @override
  String get template => 'Template';

  @override
  String get rendererLabel => 'Style';

  @override
  String get rendererImageTextWithButtons4Name =>
      'Image+Text+Bottom Text Buttons';

  @override
  String get rendererCoverInfoName => 'Cover Info+Auto Wrap';

  @override
  String get rendererImageTextWithRightTextButtonName =>
      'Image+Text+Right Text Button';

  @override
  String get rendererImageTextWithProgressName => 'IM Image+Text+Progress';

  @override
  String get islandIcon => 'Island icon';

  @override
  String get islandIconLabel => 'Large island icon';

  @override
  String get islandIconLabelSubtitle =>
      'Show the large icon of the island when enabled (small island not affected)';

  @override
  String get focusIconLabel => 'Focus icon';

  @override
  String get focusExpressionCustomizationSection =>
      'Focus advanced customization';

  @override
  String get islandExpressionCustomizationSection =>
      'Island advanced customization';

  @override
  String get aodSection => 'Always-on display';

  @override
  String get expandCustomization => 'Expand';

  @override
  String get collapseCustomization => 'Collapse';

  @override
  String get availablePlaceholdersLabel =>
      'Available placeholders(Click to copy)';

  @override
  String get expressionFunctionsLabel => 'Expression functions';

  @override
  String get focusTitleExprLabel => 'Focus title expression';

  @override
  String get focusContentExprLabel => 'Focus content expression';

  @override
  String get focusIconSourceLabel => 'Focus icon source';

  @override
  String get focusPicProfileSourceLabel => 'Profile icon source';

  @override
  String get focusAppIconPkgLabel => 'App icon package';

  @override
  String get focusSecondaryIconSourceLabel => 'Secondary icon source';

  @override
  String get chatTitleColorLabel => 'Chat title color';

  @override
  String get chatTitleColorDarkLabel => 'Chat title color (dark)';

  @override
  String get chatContentColorLabel => 'Chat content color';

  @override
  String get chatContentColorDarkLabel => 'Chat content color (dark)';

  @override
  String get progressColorLabel => 'Progress color';

  @override
  String get progressBarColorLabel => 'Progress bar color';

  @override
  String get progressBarColorEndLabel => 'Progress bar end color';

  @override
  String get placeholderTitle => 'Notification title';

  @override
  String get placeholderSubtitle => 'Notification content';

  @override
  String get placeholderSubtitleOrTitle => 'Content (fallback title)';

  @override
  String get placeholderPkg => 'Package name';

  @override
  String get placeholderChannelId => 'Channel ID';

  @override
  String get placeholderProgress => 'Notification progress';

  @override
  String get placeholderStateLabel => 'State label';

  @override
  String get placeholderProgressText => 'Progress text';

  @override
  String get placeholderAiLeft => 'AI left text';

  @override
  String get placeholderAiRight => 'AI right text';

  @override
  String get placeholderRawTitle => 'Raw title';

  @override
  String get placeholderRawSubtitle => 'Raw subtitle';

  @override
  String get placeholderRawSubtitleOrTitle => 'Raw subtitle (fallback title)';

  @override
  String get islandLeftExprLabel => 'Island left expression';

  @override
  String get islandRightExprLabel => 'Island right expression';

  @override
  String get aodTextSwitchLabel => 'AOD text switch';

  @override
  String get aodTextExprLabel => 'AOD text expression';

  @override
  String get aodIconSourceLabel => 'AOD icon source';

  @override
  String get focusNotificationLabel => 'Focus notification';

  @override
  String get hideNotificationLabel => 'Hide notification';

  @override
  String get hideNotificationLabelSubtitle =>
      'Only show the island and hide the focus notification from the notification shade';

  @override
  String get preserveStatusBarSmallIconLabel => 'Status bar icon';

  @override
  String get restoreLockscreenTitle => 'Restore Lockscreen Notification';

  @override
  String get restoreLockscreenSubtitle =>
      'Skip focus notification processing on lockscreen, keep original privacy behavior';

  @override
  String get firstFloatLabel => 'First float';

  @override
  String get updateFloatLabel => 'Update float';

  @override
  String get autoDisappear => 'Auto dismiss';

  @override
  String get seconds => 's';

  @override
  String get highlightColorLabel => 'Highlight color';

  @override
  String get dynamicHighlightColorLabel => 'Dynamic highlight color';

  @override
  String get dynamicHighlightColorLabelSubtitle =>
      'Use icon-based dynamic color by default';

  @override
  String get followDynamicColorLabel => 'Follow dynamic color';

  @override
  String get dynamicHighlightModeDark => 'Dark';

  @override
  String get dynamicHighlightModeDarker => 'Darker';

  @override
  String get outerGlowLabel => 'Outer glow';

  @override
  String get forceOuterGlowLabel => 'Force globally';

  @override
  String get forceFocusOuterGlowSubtitle =>
      'Force glow for unmatched focus notifications when enabled';

  @override
  String get forceIslandOuterGlowSubtitle =>
      'Force glow for unmatched islands when enabled';

  @override
  String get outEffectColorLabel => 'Outer glow color';

  @override
  String get highlightColorHint => '#RRGGBB format, leave empty for default';

  @override
  String get actionBgColorLabel => 'Action background color';

  @override
  String get actionBgColorDarkLabel => 'Action background color (dark)';

  @override
  String get actionTitleColorLabel => 'Action title color';

  @override
  String get actionTitleColorDarkLabel => 'Action title color (dark)';

  @override
  String get action1BgColorLabel => 'Action 1 background color';

  @override
  String get action1BgColorDarkLabel => 'Action 1 background color (dark)';

  @override
  String get action1TitleColorLabel => 'Action 1 title color';

  @override
  String get action1TitleColorDarkLabel => 'Action 1 title color (dark)';

  @override
  String get action2BgColorLabel => 'Action 2 background color';

  @override
  String get action2BgColorDarkLabel => 'Action 2 background color (dark)';

  @override
  String get action2TitleColorLabel => 'Action 2 title color';

  @override
  String get action2TitleColorDarkLabel => 'Action 2 title color (dark)';

  @override
  String get textHighlightLabel => 'Text highlight';

  @override
  String get narrowFontLabel => 'Narrow font';

  @override
  String get showLeftHighlightLabel => 'Left text highlight';

  @override
  String get showRightHighlightLabel => 'Right text highlight';

  @override
  String get showLeftHighlightShort => 'Left';

  @override
  String get showRightHighlightShort => 'Right';

  @override
  String get colorHue => 'Hue';

  @override
  String get colorSaturation => 'Saturation';

  @override
  String get colorBrightness => 'Brightness';

  @override
  String get colorOpacity => 'Opacity';

  @override
  String get onlyEnabledChannels => 'Only apply to enabled channels';

  @override
  String enabledChannelsCount(int enabled, int total) {
    return '$enabled / $total channels enabled';
  }

  @override
  String get iconModeAuto => 'Auto';

  @override
  String get iconModeNotifSmall => 'Small notification icon';

  @override
  String get iconModeNotifLarge => 'Large notification icon';

  @override
  String get iconModeAppIcon => 'App icon';

  @override
  String get optDefault => 'Default';

  @override
  String get optDefaultOn => 'Default (On)';

  @override
  String get optDefaultOff => 'Default (Off)';

  @override
  String get optOn => 'On';

  @override
  String get optOff => 'Off';

  @override
  String get errorInvalidFormat => 'Invalid configuration format';

  @override
  String get errorNoStorageDir => 'Cannot get storage directory';

  @override
  String get errorNoFileSelected => 'No file selected';

  @override
  String get errorNoFilePath => 'Cannot get file path';

  @override
  String get errorEmptyClipboard => 'Clipboard is empty';

  @override
  String get navBlacklist => 'Focus Blacklist';

  @override
  String get navBlacklistSubtitle =>
      'Block focus notification float or hide for specific apps';

  @override
  String get presetGamesTitle => 'Quick Filter Popular Games';

  @override
  String presetGamesSuccess(int count) {
    return 'Added $count installed games to blacklist from preset';
  }

  @override
  String blacklistedAppsCount(int count) {
    return 'Blocked focus notifications for $count apps';
  }

  @override
  String blacklistedAppsCountWithSystem(int count) {
    return 'Blocked focus notifications for $count apps (including system apps)';
  }

  @override
  String get firstFloatLabelSubtitle =>
      'Whether to expand as focus notification when Island receives notification for the first time';

  @override
  String get updateFloatLabelSubtitle =>
      'Whether to expand notification when Island updates';

  @override
  String get marqueeChannelTitleSubtitle =>
      'Whether to scroll long messages on Island';

  @override
  String get focusNotificationLabelSubtitle =>
      'Replace notification with focus notification (shows original notification when disabled)';

  @override
  String get preserveStatusBarSmallIconLabelSubtitle =>
      'Whether to force keep status bar icon when focus notification is displayed';

  @override
  String get fullscreenBehaviorTitle => 'Fullscreen behavior';

  @override
  String get fullscreenBehaviorSubtitle =>
      'Notification strategy when landscape/fullscreen is detected';

  @override
  String get fullscreenBehaviorOff => 'Default';

  @override
  String get fullscreenBehaviorFallback => 'Fallback to normal notification';

  @override
  String get filterRulesTitle => 'Filter rules';

  @override
  String get filterRulesOrderTitle => 'First matching rule wins';

  @override
  String get filterRuleDnd => 'DND';

  @override
  String get filterRuleFullscreen => 'Fullscreen';

  @override
  String get filterRuleLandscape => 'Landscape';

  @override
  String get dndBehaviorTitle => 'When DND';

  @override
  String get fullscreenRuleTitle => 'When fullscreen';

  @override
  String get landscapeRuleTitle => 'When landscape';

  @override
  String get behaviorPreviewDefault =>
      'No override when matched; keep default behavior';

  @override
  String get behaviorPreviewSuppress =>
      'Fallback to normal notification when matched';

  @override
  String get behaviorPreviewSmallOnly =>
      'Show small island only; do not auto expand';

  @override
  String get behaviorPreviewExpand => 'Auto expand notification when matched';

  @override
  String get aiConfigSection => 'AI Enhancement';

  @override
  String get aiConfigTitle => 'AI Notification Summary';

  @override
  String get aiConfigSubtitleEnabled =>
      'Enabled · Tap to configure AI parameters';

  @override
  String get aiConfigSubtitleDisabled => 'Disabled · Tap to configure';

  @override
  String get aiEnabledTitle => 'Enable AI Summary';

  @override
  String get aiEnabledSubtitle =>
      'AI generates Island left/right text, falls back on timeout or error';

  @override
  String get aiApiSection => 'API Parameters';

  @override
  String get aiUrlLabel => 'API URL';

  @override
  String get aiUrlHint => 'https://api.openai.com/v1/chat/completions';

  @override
  String get aiApiKeyLabel => 'API Key';

  @override
  String get aiApiKeyHint => 'sk-...';

  @override
  String get aiModelLabel => 'Model';

  @override
  String get aiModelHint => 'gpt-4o-mini';

  @override
  String get aiModelPickerTitle => 'Select Model';

  @override
  String get aiModelPickerSearchHint => 'Search models…';

  @override
  String get aiModelPickerEmpty => 'No models found';

  @override
  String get aiModelPickerRetry => 'Retry';

  @override
  String get aiModelPickerClose => 'Close';

  @override
  String get aiModelPickerFetchError => 'Failed to load model list';

  @override
  String get aiPromptLabel => 'Custom Prompt';

  @override
  String get aiPromptHint =>
      'Leave empty to use default: Extract key info, left and right each no more than 6 words or 12 characters';

  @override
  String get aiPromptInUserTitle => 'Put prompt in user message';

  @override
  String get aiPromptInUserSubtitle =>
      'Some models do not support system instructions; enable to put prompt in user message';

  @override
  String get aiTimeoutTitle => 'AI Response Timeout';

  @override
  String aiTimeoutLabel(int seconds) {
    return '${seconds}s';
  }

  @override
  String get aiTemperatureTitle => 'Sampling Temperature';

  @override
  String get aiTemperatureSubtitle =>
      'Control the randomness of responses. 0 is precise, 1 is more creative';

  @override
  String get aiMaxTokensTitle => 'Max Tokens';

  @override
  String get aiMaxTokensSubtitle =>
      'Limit the maximum length of AI-generated responses';

  @override
  String get aiDefaultPromptFull =>
      'Leave empty to use default prompt: Extract key info from notification, no more than 6 words or 12 characters for left and right sides';

  @override
  String get aiTestButton => 'Test Connection';

  @override
  String get aiTestUrlEmpty => 'Please enter an API URL first';

  @override
  String get aiLastLogTitle => 'Recent AI Request Log';

  @override
  String get aiLastLogSubtitle =>
      'AI requests triggered by connection tests or notifications are displayed here';

  @override
  String get aiLastLogEmpty => 'No AI request logs to display yet';

  @override
  String get aiLastLogSourceLabel => 'Source';

  @override
  String get aiLastLogTimeLabel => 'Time';

  @override
  String get aiLastLogStatusLabel => 'Status';

  @override
  String get aiLastLogDurationLabel => 'Duration';

  @override
  String get aiLastLogSourceNotification => 'Notification Trigger';

  @override
  String get aiLastLogSourceSettingsTest => 'Settings Test';

  @override
  String get aiLastLogRendered => 'Rendered';

  @override
  String get aiLastLogRaw => 'Raw';

  @override
  String get aiLastLogCopy => 'Copy Log';

  @override
  String get aiLastLogCopied => 'AI request log copied';

  @override
  String get aiLastLogRequest => 'Request';

  @override
  String get aiLastLogResponse => 'Response';

  @override
  String get aiLastLogUsage => 'Token Usage';

  @override
  String get aiLastLogMessages => 'Messages';

  @override
  String get aiLastLogError => 'Error';

  @override
  String get aiLastLogHttpCode => 'HTTP Status';

  @override
  String get aiLastLogLeftText => 'Left Text';

  @override
  String get aiLastLogRightText => 'Right Text';

  @override
  String get aiLastLogAssistantContent => 'Model Response Content';

  @override
  String get aiConfigSaveButton => 'Save';

  @override
  String get aiConfigSaved => 'AI configuration saved';

  @override
  String get aiConfigTips =>
      'AI receives the app package, title, and content of each notification, and returns short left (source) and right (content) text. Compatible with OpenAI-format APIs (e.g. DeepSeek, Claude). Falls back to default logic if no response.';

  @override
  String get templateAiNotificationIslandName => 'AI Notification Island';

  @override
  String get aiPromptDefault =>
      'Extract key info from notification, left and right each no more than 6 words or 12 characters';

  @override
  String get aiDefaultNotificationText =>
      '[Delivery] Your delivery has arrived and was placed in the parcel locker at the door';

  @override
  String get aiTestSampleUserContent => 'Reply exactly: test successful';

  @override
  String aiNotificationUserContent(String content) {
    return 'App package: com.example.app\nTitle: Test notification\nBody: $content';
  }

  @override
  String get aiJsonOnlyInstruction =>
      'Return only the following JSON. Do not include any other text or code block:';

  @override
  String get aiJsonLeftDescription => 'left text (sender)';

  @override
  String get aiJsonRightDescription => 'right text (summary)';

  @override
  String get aiInvalidJsonError =>
      'Invalid AI response format. JSON with left and right fields is required';

  @override
  String get aiEmptyJsonError =>
      'AI response is empty. JSON with left and right fields is required';

  @override
  String get aiNotificationContentLabel => 'Notification Content';

  @override
  String get aiTestNotificationTitle => 'Test Notification';

  @override
  String get aiNotificationSent => 'Notification sent';

  @override
  String get aiAiNotificationSent => 'AI notification sent';

  @override
  String get aiSendNotificationButton => 'Send Notification';

  @override
  String get aiSendAiNotificationButton => 'Send AI Notification';

  @override
  String get hideDesktopIconTitle => 'Hide Desktop Icon';

  @override
  String get hideDesktopIconSubtitle =>
      'Hide the app icon from launcher. Open via LSPosed Manager after hiding';

  @override
  String get filterRulesSection => 'Filter Rules';

  @override
  String get foregroundRulesTab => 'Foreground Rules';

  @override
  String get foregroundExclusionsTab => 'Excluded Apps';

  @override
  String get foregroundRulesDescription =>
      'Set Island behavior when a foreground app starts.';

  @override
  String get foregroundExclusionsDescription =>
      'Notifications from apps in the exclusion list are not affected by foreground rules.';

  @override
  String get hideSystemApps => 'Hide system apps';

  @override
  String get restoreDefaultConfig => 'Restore default config';

  @override
  String resetDefaultConfigSuccess(int count) {
    return 'Default config restored for $count apps';
  }

  @override
  String get sceneActionDefault => 'Default';

  @override
  String get sceneActionSmallOnly => 'Disable expansion';

  @override
  String get sceneActionExpand => 'Auto expand';

  @override
  String get sceneActionSuppress => 'Fallback';

  @override
  String get filterModeLabel => 'Filter Mode';

  @override
  String get filterModeBlacklist => 'Blacklist';

  @override
  String get filterModeWhitelist => 'Whitelist';

  @override
  String get filterModeBlacklistDesc =>
      'Notifications matching keywords will be filtered';

  @override
  String get filterModeWhitelistDesc =>
      'Only notifications matching keywords will be shown';

  @override
  String get whitelistKeywordsLabel => 'Whitelist Keywords';

  @override
  String get blacklistKeywordsLabel => 'Blacklist Keywords';

  @override
  String get addKeyword => 'Add keyword';

  @override
  String get keywordHint => 'Enter keyword';

  @override
  String get removeKeyword => 'Remove';

  @override
  String get keywordFilterPriority =>
      'Whitelist takes priority: only whitelist-matched notifications are shown, but blacklist can still veto';

  @override
  String get exportChannelsToClipboard => 'Export Channel Settings';

  @override
  String get importChannelsFromClipboard => 'Import Channel Settings';

  @override
  String get exportChannelsSuccess => 'Channel settings copied to clipboard';

  @override
  String importChannelsSuccess(int count) {
    return 'Imported $count channel settings';
  }

  @override
  String importChannelsPartialSuffix(int total, int matched) {
    return ' ($matched of $total matched)';
  }

  @override
  String get importErrorEmptyClipboard =>
      'Clipboard is empty. Please copy channel settings first';

  @override
  String get importErrorNotJson => 'Clipboard content is not valid JSON';

  @override
  String get importErrorMissingChannels =>
      'Invalid data format: missing channel list';

  @override
  String get importErrorNoMatch =>
      'No channels matched the current app. Please verify the data source';

  @override
  String get importErrorUnknown => 'Import failed. Please check clipboard data';

  @override
  String get mediaNotificationTitle => 'Media notification';

  @override
  String get mediaNotificationDisabledSubtitle =>
      'Delete the entire media notification when disabled';

  @override
  String get normalNotificationTitle => 'Normal notification';

  @override
  String get normalNotificationSubtitle =>
      'Remove media fields and handle it as a normal notification when enabled';

  @override
  String get channelSettingsUnmodified => 'Not modified';

  @override
  String get restoreDefault => 'Restore default';

  @override
  String get islandDimenSection => 'Island Dimensions';

  @override
  String get islandDimenHeight => 'Island Height';

  @override
  String get islandTopOffset => 'Distance from Top of Screen';

  @override
  String get followSystem => 'Follow system';

  @override
  String get islandDimenMiniY => 'Vertical Position';

  @override
  String get islandDimenMiniYHint => '0=follow system';

  @override
  String get islandBgSection => 'Island Background';

  @override
  String get islandBgSmallTitle => 'Small Island Background';

  @override
  String get islandBgBigTitle => 'Large Island Background';

  @override
  String get islandBgExpandTitle => 'Focus Notification Background';

  @override
  String get islandBgNotSet => 'Not set';

  @override
  String get islandBgCornerRadius => 'Corner Radius';

  @override
  String get islandBgCornerRadiusHint => '0=system default';

  @override
  String get islandBgImageSelected => 'Background image saved';

  @override
  String get islandBgImageDeleted => 'Background image deleted';

  @override
  String get islandBgDeleteFailed => 'Delete failed';

  @override
  String islandBgEditTitle(String type) {
    return 'Edit $type Background';
  }

  @override
  String get islandBgBlurLabel => 'Blur';

  @override
  String get islandBgBrightnessLabel => 'Brightness';

  @override
  String get islandBgOpacityLabel => 'Opacity';

  @override
  String get islandBgDefault => 'Default';

  @override
  String get keepIslandTitle => 'Keep Island Visible';

  @override
  String get keepIslandSubtitle =>
      'Post a blank notification to keep the island always visible';

  @override
  String get keepIslandAutoHideTitle => 'Auto Hide';

  @override
  String get keepIslandAutoHideSubtitle =>
      'Automatically hide the blank island when a notification arrives, and restore it when dismissed';

  @override
  String get keepIslandHideLandscapeTitle => 'Hide in Landscape';

  @override
  String get keepIslandHideLandscapeSubtitle =>
      'Hide the keep island in landscape, then restore in portrait when no notification is active';

  @override
  String get keepIslandHighlightColorTitle => 'Highlight Color';

  @override
  String get keepIslandHighlightColorSubtitle =>
      'Customize the highlight text color for the keep island';

  @override
  String get keepIslandLeftContentTitle => 'Left island content';

  @override
  String get keepIslandRightContentTitle => 'Right island content';

  @override
  String get keepIslandPlaceholdersTitle => 'Available placeholders';

  @override
  String keepIslandPlaceholdersDescription(
    String batteryLevel,
    String cpuUsage,
  ) {
    return 'Enter plain text or expressions for either side, for example: Battery $batteryLevel, CPU $cpuUsage. Tap a tag to copy it.';
  }

  @override
  String keepIslandPlaceholderCopied(String placeholder) {
    return 'Copied $placeholder';
  }

  @override
  String get keepIslandDefaultEmpty => 'Empty by default';

  @override
  String keepIslandContentHint(String placeholder) {
    return 'Empty by default. Enter text or $placeholder';
  }

  @override
  String get clear => 'Clear';

  @override
  String get save => 'Save';

  @override
  String get islandOtherSection => 'Other';

  @override
  String get miscSection => 'Misc';

  @override
  String get onboardingEntryTitle => 'Open Onboarding';

  @override
  String get onboardingEntrySubtitle =>
      'Review the welcome and quick start flow';

  @override
  String get onboardingAppName => 'HyperIsland';

  @override
  String get onboardingWelcomeTitle => 'Welcome to HyperIsland';

  @override
  String get onboardingWelcomeSubtitle =>
      'Configure your island experience quickly and cleanly';

  @override
  String get onboardingEnvironmentTitle => 'Environment Check';

  @override
  String get onboardingEnvironmentSubtitle => 'Check module permission status';

  @override
  String get onboardingNotificationStyleTitle => 'Choose Notification Style';

  @override
  String get onboardingNotificationStyleSubtitle =>
      'Pick your preferred default notification display';

  @override
  String get onboardingOriginalNotificationLabel => 'Original notification';

  @override
  String get onboardingFinishTitle => 'All Set';

  @override
  String get onboardingFinishSubtitle =>
      'After onboarding, you can keep adjusting details in Settings';

  @override
  String onboardingStepLabel(int current, int total) {
    return 'Step $current / $total';
  }

  @override
  String get onboardingPrevious => 'Previous';

  @override
  String get onboardingNext => 'Next';

  @override
  String get onboardingDone => 'Get Started';

  @override
  String get onboardingStatusTitle => 'Status Check';

  @override
  String get onboardingRetry => 'Retry';

  @override
  String get onboardingLsposedStatus => 'LSPosed Activation';

  @override
  String get onboardingRootStatus => 'Root Access';

  @override
  String get onboardingAppListStatus => 'App list permission';

  @override
  String get onboardingProtocolStatus => 'System Protocol Version';

  @override
  String get onboardingAndroidStatus => 'Android Version';

  @override
  String get onboardingUnsupportedSystem => 'Current system is not supported';

  @override
  String get onboardingAndroid15Limited => 'Android 15 support is limited';

  @override
  String get onboardingMissingPermissionTitle => 'Required Permission Missing';

  @override
  String get onboardingMissingPermissionMessage =>
      'The module may not work properly';

  @override
  String get onboardingDialogClose => 'Close';

  @override
  String get onboardingDialogContinue => 'Continue';

  @override
  String get backupRestoreSection => 'Backup & Restore';

  @override
  String get hookExtensionSection => 'Hook Extension';

  @override
  String get hookScopeSettings => 'System Settings';

  @override
  String get settingsHomeEntryTitle => 'System Settings entry';

  @override
  String get settingsHomeEntrySubtitle =>
      'Show the HyperIsland entry on the System Settings home page';

  @override
  String get xposedScopeRequestFailed =>
      'Scope request failed. Make sure the module is enabled in LSPosed';

  @override
  String get hookScopeSystemUI => 'System UI';

  @override
  String get smoothIslandTitle => 'Smooth Island';

  @override
  String get smoothIslandSubtitle =>
      'Use a continuous-curvature capsule for island outlines. Restart the scope after disabling to fully unload the hook';

  @override
  String get smoothIslandSmoothingTitle => 'Smoothing Strength';

  @override
  String get bluetoothIslandStatusEnabled => 'Enabled';

  @override
  String get bluetoothIslandStatusDisabled => 'Disabled';

  @override
  String get bluetoothIslandTitle => 'Bluetooth Island';

  @override
  String bluetoothIslandSubtitle(String status) {
    return '$status · Listen for Bluetooth device connections and disconnections, then forward the island through System UI';
  }

  @override
  String get bluetoothIslandSettingsTitle => 'Bluetooth Island Settings';

  @override
  String get bluetoothIslandEnableTitle => 'Enable Bluetooth Island';

  @override
  String get bluetoothIslandEnableSubtitle =>
      'After disabling, restart System UI to take effect. The Bluetooth Hook will not be registered';

  @override
  String get bluetoothIslandShowDeviceNameTitle => 'Show Device Name';

  @override
  String get bluetoothIslandShowDeviceNameSubtitle =>
      'On connection, show the device name on the right first, then show the connection status afterward';

  @override
  String get bluetoothIslandDisplayDurationTitle => 'Display Duration';

  @override
  String get chargeIslandTitle => 'Charging Island';

  @override
  String chargeIslandSubtitle(String status) {
    return '$status · Replace the power or battery segment in Charging Island';
  }

  @override
  String get chargeIslandSettingsTitle => 'Charging Island Settings';

  @override
  String get chargeIslandEnableTitle => 'Enable Charging Island Hook';

  @override
  String get chargeIslandEnableSubtitle =>
      'After disabling, restart System UI to take effect. The hook will be bypassed completely';

  @override
  String get chargeIslandLeftModeTitle => 'Left Behavior';

  @override
  String get chargeIslandRightModeTitle => 'Right Behavior';

  @override
  String get chargeIslandModeDefault => 'Default';

  @override
  String get chargeIslandModePower => 'Power';

  @override
  String get chargeIslandModeVoltage => 'Voltage';

  @override
  String get chargeIslandModeCurrent => 'Current';

  @override
  String get chargeIslandModeLevel => 'Battery';

  @override
  String get chargeIslandModeTemperature => 'Battery Temperature';

  @override
  String get chargeIslandDurationModeTitle => 'Duration';

  @override
  String get chargeIslandDurationDefault => 'Default';

  @override
  String get chargeIslandDurationCustom => 'Custom';

  @override
  String get chargeIslandDurationPersistent => 'Persistent';

  @override
  String get chargeIslandDurationSecondsTitle => 'Custom Duration';

  @override
  String get chargeIslandOuterGlowSubtitle =>
      'Control the outer glow effect of Charging Island';

  @override
  String get outerGlowTitle => 'Outer Glow';

  @override
  String get bluetoothIslandOuterGlowSubtitle =>
      'Control the outer glow effect of Bluetooth Island';

  @override
  String get outerGlowColorTitle => 'Outer Glow Color';

  @override
  String get hookScopeXMSF => 'Xiaomi Service Framework (XMSF)';

  @override
  String get downloadManagerSection => 'Download Manager';

  @override
  String get themePageTitle => 'Theme';

  @override
  String get themeSeedColorTitle => 'Theme Color';

  @override
  String get themeSeedColorSubtitle => 'Customize the app accent color';

  @override
  String get presetColors => 'Preset Colors';

  @override
  String get themeResetColor => 'Reset to Default';

  @override
  String get blurBarsTitle => 'Frosted Glass';

  @override
  String get blurBarsSubtitle =>
      'Add blur transparency effect to top and bottom bars';

  @override
  String get bluetoothIslandWhitelistTitle => 'Device Whitelist';

  @override
  String get bluetoothIslandWhitelistSubtitle =>
      'Only show the island for whitelisted Bluetooth devices';

  @override
  String get bluetoothIslandWhitelistButton => 'Manage Whitelist';

  @override
  String bluetoothIslandWhitelistButtonSubtitle(int count) {
    return '$count device(s) selected';
  }

  @override
  String get bluetoothIslandWhitelistDialogTitle => 'Select Bluetooth Devices';

  @override
  String get bluetoothIslandWhitelistEmpty =>
      'No paired devices. Please pair a device in system Bluetooth settings first';

  @override
  String get bluetoothIslandWhitelistAllHint =>
      'When disabled, the island shows for all Bluetooth devices';

  @override
  String get bluetoothIslandLoadDevicesFailed =>
      'Failed to load Bluetooth devices';

  @override
  String get bluetoothIslandNeedBtPermission =>
      'Bluetooth permission is required to load devices';

  @override
  String get hideBehaviorTitle => 'Hide Behavior';

  @override
  String get hideBehaviorDescription =>
      'Control whether system scenes are allowed to temporarily hide the island. Turning an item off blocks the matching system hide logic.';

  @override
  String get hideBehaviorMasterSwitch => 'Enable hide behavior hook';

  @override
  String get hideBehaviorMasterSwitchSubtitle =>
      'Registers the hide behavior hook only when enabled. Disabled means no hook and is the default.';

  @override
  String get hideBehaviorScreenPinning => 'Screen pinning';

  @override
  String get hideBehaviorScreenPinningSubtitle =>
      'Hide the island while screen pinning is active';

  @override
  String get hideBehaviorBouncerShowing => 'Unlock screen';

  @override
  String get hideBehaviorBouncerShowingSubtitle =>
      'Hide the island while the unlock challenge is showing';

  @override
  String get hideBehaviorFullscreen => 'Fullscreen mode';

  @override
  String get hideBehaviorFullscreenSubtitle =>
      'Hide the island when the status bar disappears or immersive fullscreen is active';

  @override
  String get hideBehaviorScreenLocked => 'Lock screen';

  @override
  String get hideBehaviorScreenLockedSubtitle =>
      'Hide the island during lock screen or screen-off flows';

  @override
  String get hideBehaviorNotificationCenter => 'Notification center';

  @override
  String get hideBehaviorNotificationCenterSubtitle =>
      'Hide the island while the notification shade expands or transitions';

  @override
  String get off => 'Off';

  @override
  String get islandTextSection => 'Island Text';

  @override
  String get islandTextColorTitle => 'Island Text Color';

  @override
  String get islandTextColorBlack => 'Black';

  @override
  String get islandTextColorFollowBackground => 'Follow island background';

  @override
  String get islandTextColorInvertBackground => 'Invert island background';

  @override
  String get islandTextColorFollowStatusBar => 'Follow status bar';

  @override
  String get islandTextColorInvertStatusBar => 'Invert status bar';

  @override
  String get islandTextColorDefault => 'Default';

  @override
  String get tapToSelectImage => 'Tap to select image or GIF';

  @override
  String get autoExpandNotification => 'Auto expand notification';

  @override
  String widthDpLabel(int width) {
    return '$width dp';
  }
}
