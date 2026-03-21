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
  String get restartScope => 'Restart Scope';

  @override
  String get systemUI => 'System UI';

  @override
  String get downloadManager => 'Download Manager';

  @override
  String get notificationTest => 'Notification Test';

  @override
  String get sendTestNotification => 'Send Test Notification';

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
      '1. This page is only for testing Dynamic Island support, not actual effects';

  @override
  String get note2 =>
      '2. Disable focus notification whitelist for System UI and MIUI Framework in HyperCeiler';

  @override
  String get note3 =>
      '3. After activating in LSPosed Manager, you must restart the related scope app';

  @override
  String get note4 =>
      '4. General adaptation is supported, try checking an appropriate template';

  @override
  String get behaviorSection => 'Behavior';

  @override
  String get appearanceSection => 'Appearance';

  @override
  String get configSection => 'Configuration';

  @override
  String get aboutSection => 'About';

  @override
  String get keepFocusNotifTitle =>
      'Keep focus notification after download pause';

  @override
  String get keepFocusNotifSubtitle =>
      'Show a notification to resume download, may cause state desync';

  @override
  String get checkUpdateOnLaunchTitle => 'Check for updates on launch';

  @override
  String get checkUpdateOnLaunchSubtitle =>
      'Automatically check for new versions when the app starts';

  @override
  String get checkUpdate => 'Check for updates';

  @override
  String get alreadyLatest => 'Already on the latest version';

  @override
  String get useAppIconTitle => 'Use app icon';

  @override
  String get useAppIconSubtitle =>
      'Use app icon for Download Manager notifications';

  @override
  String get roundIconTitle => 'Round icon corners';

  @override
  String get roundIconSubtitle => 'Add rounded corners to notification icons';

  @override
  String get marqueeFeatureTitle => 'Scrolling Island (Experimental)';

  @override
  String get marqueeFeatureSubtitle => 'Auto scroll long text notification';

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
  String get exportToFile => 'Export to file';

  @override
  String get exportToFileSubtitle => 'Save configuration as a JSON file';

  @override
  String get exportToClipboard => 'Export to clipboard';

  @override
  String get exportToClipboardSubtitle => 'Copy configuration as JSON text';

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
    return '$done / $total apps';
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
  String get template => 'Template';

  @override
  String get islandIcon => 'Island icon';

  @override
  String get focusIconLabel => 'Focus icon';

  @override
  String get focusNotificationLabel => 'Focus notification';

  @override
  String get firstFloatLabel => 'First float';

  @override
  String get updateFloatLabel => 'Update float';

  @override
  String get autoDisappear => 'Auto dismiss';

  @override
  String get seconds => 's';

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
}
