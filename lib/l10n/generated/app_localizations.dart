import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_ja.dart';
import 'app_localizations_ru.dart';
import 'app_localizations_tr.dart';
import 'app_localizations_zh.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'generated/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations? of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations);
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('ja'),
    Locale('ru'),
    Locale('tr'),
    Locale('zh'),
  ];

  /// No description provided for @navHome.
  ///
  /// In en, this message translates to:
  /// **'Home'**
  String get navHome;

  /// No description provided for @navIsland.
  ///
  /// In en, this message translates to:
  /// **'Island'**
  String get navIsland;

  /// No description provided for @navApps.
  ///
  /// In en, this message translates to:
  /// **'Apps'**
  String get navApps;

  /// No description provided for @navSettings.
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get navSettings;

  /// No description provided for @cancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get cancel;

  /// No description provided for @confirm.
  ///
  /// In en, this message translates to:
  /// **'Confirm'**
  String get confirm;

  /// No description provided for @ok.
  ///
  /// In en, this message translates to:
  /// **'OK'**
  String get ok;

  /// No description provided for @apply.
  ///
  /// In en, this message translates to:
  /// **'Apply'**
  String get apply;

  /// No description provided for @noChange.
  ///
  /// In en, this message translates to:
  /// **'No change'**
  String get noChange;

  /// No description provided for @newVersionFound.
  ///
  /// In en, this message translates to:
  /// **'New Version Available'**
  String get newVersionFound;

  /// No description provided for @currentVersion.
  ///
  /// In en, this message translates to:
  /// **'Current version: {version}'**
  String currentVersion(String version);

  /// No description provided for @latestVersion.
  ///
  /// In en, this message translates to:
  /// **'Latest version: {version}'**
  String latestVersion(String version);

  /// No description provided for @lsposedApiVersion.
  ///
  /// In en, this message translates to:
  /// **'LSPosed API Version: {version}'**
  String lsposedApiVersion(int version);

  /// No description provided for @later.
  ///
  /// In en, this message translates to:
  /// **'Later'**
  String get later;

  /// No description provided for @goUpdate.
  ///
  /// In en, this message translates to:
  /// **'Update'**
  String get goUpdate;

  /// No description provided for @sponsorSupport.
  ///
  /// In en, this message translates to:
  /// **'Support the Author'**
  String get sponsorSupport;

  /// No description provided for @sponsorAuthor.
  ///
  /// In en, this message translates to:
  /// **'Sponsor'**
  String get sponsorAuthor;

  /// No description provided for @donorList.
  ///
  /// In en, this message translates to:
  /// **'Donor List'**
  String get donorList;

  /// No description provided for @documentation.
  ///
  /// In en, this message translates to:
  /// **'Documentation'**
  String get documentation;

  /// No description provided for @versionUpdatedTitle.
  ///
  /// In en, this message translates to:
  /// **'Updated to {version}'**
  String versionUpdatedTitle(String version);

  /// No description provided for @versionUpdatedContent.
  ///
  /// In en, this message translates to:
  /// **'Please restart the scope apps after updating'**
  String get versionUpdatedContent;

  /// No description provided for @versionUpdatedChangelog.
  ///
  /// In en, this message translates to:
  /// **'Changelog: Tap to view'**
  String get versionUpdatedChangelog;

  /// No description provided for @versionUpdatedStarHint.
  ///
  /// In en, this message translates to:
  /// **'If you like this app, please give it a free Star'**
  String get versionUpdatedStarHint;

  /// No description provided for @restartScope.
  ///
  /// In en, this message translates to:
  /// **'Restart Scope'**
  String get restartScope;

  /// No description provided for @systemUI.
  ///
  /// In en, this message translates to:
  /// **'System UI'**
  String get systemUI;

  /// No description provided for @downloadManager.
  ///
  /// In en, this message translates to:
  /// **'Download Manager'**
  String get downloadManager;

  /// No description provided for @xmsf.
  ///
  /// In en, this message translates to:
  /// **'XMSF (Xiaomi Service Framework)'**
  String get xmsf;

  /// No description provided for @notificationTest.
  ///
  /// In en, this message translates to:
  /// **'Notification Test'**
  String get notificationTest;

  /// No description provided for @sendTestNotification.
  ///
  /// In en, this message translates to:
  /// **'Send Test Notification'**
  String get sendTestNotification;

  /// No description provided for @customTestNotification.
  ///
  /// In en, this message translates to:
  /// **'Custom Test Notification'**
  String get customTestNotification;

  /// No description provided for @customTestTitle.
  ///
  /// In en, this message translates to:
  /// **'Title'**
  String get customTestTitle;

  /// No description provided for @customTestTitleHint.
  ///
  /// In en, this message translates to:
  /// **'Leave empty for default title'**
  String get customTestTitleHint;

  /// No description provided for @customTestContent.
  ///
  /// In en, this message translates to:
  /// **'Content'**
  String get customTestContent;

  /// No description provided for @customTestContentHint.
  ///
  /// In en, this message translates to:
  /// **'Leave empty for default content'**
  String get customTestContentHint;

  /// No description provided for @clearPreviousNotification.
  ///
  /// In en, this message translates to:
  /// **'Clear previous notification'**
  String get clearPreviousNotification;

  /// No description provided for @clearPreviousNotificationSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Cancel existing island notification before sending'**
  String get clearPreviousNotificationSubtitle;

  /// No description provided for @enableFloatNotificationSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Automatically expand as focus notification when received'**
  String get enableFloatNotificationSubtitle;

  /// No description provided for @notes.
  ///
  /// In en, this message translates to:
  /// **'Notes'**
  String get notes;

  /// No description provided for @detectingModuleStatus.
  ///
  /// In en, this message translates to:
  /// **'Detecting module status...'**
  String get detectingModuleStatus;

  /// No description provided for @moduleStatus.
  ///
  /// In en, this message translates to:
  /// **'Module Status'**
  String get moduleStatus;

  /// No description provided for @activated.
  ///
  /// In en, this message translates to:
  /// **'Activated'**
  String get activated;

  /// No description provided for @notActivated.
  ///
  /// In en, this message translates to:
  /// **'Not Activated'**
  String get notActivated;

  /// No description provided for @enableInLSPosed.
  ///
  /// In en, this message translates to:
  /// **'Please enable this module in LSPosed'**
  String get enableInLSPosed;

  /// No description provided for @enableSystemUiScopeInLSPosed.
  ///
  /// In en, this message translates to:
  /// **'Please select System UI in the LSPosed scope'**
  String get enableSystemUiScopeInLSPosed;

  /// No description provided for @updateLSPosedRequired.
  ///
  /// In en, this message translates to:
  /// **'Please update LSPosed version'**
  String get updateLSPosedRequired;

  /// No description provided for @systemNotSupported.
  ///
  /// In en, this message translates to:
  /// **'System Not Supported'**
  String get systemNotSupported;

  /// No description provided for @systemNotSupportedSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Current system does not support Dynamic Island (protocol version {version}, requires version 3)'**
  String systemNotSupportedSubtitle(int version);

  /// No description provided for @restartFailed.
  ///
  /// In en, this message translates to:
  /// **'Restart failed: {message}'**
  String restartFailed(String message);

  /// No description provided for @restartRootRequired.
  ///
  /// In en, this message translates to:
  /// **'Please check if ROOT permission has been granted to this app'**
  String get restartRootRequired;

  /// No description provided for @note1.
  ///
  /// In en, this message translates to:
  /// **'1. Be sure to read the usage tutorial in the top-right corner before using'**
  String get note1;

  /// No description provided for @note2.
  ///
  /// In en, this message translates to:
  /// **'2. Most settings support hot reload; restart the scope if issues occur'**
  String get note2;

  /// No description provided for @note3.
  ///
  /// In en, this message translates to:
  /// **'3. For A16, System UI component version >17.1 is recommended'**
  String get note3;

  /// No description provided for @note4.
  ///
  /// In en, this message translates to:
  /// **'4. This page is only for testing Dynamic Island and glow effect support, not actual effects'**
  String get note4;

  /// No description provided for @note5.
  ///
  /// In en, this message translates to:
  /// **'5. For download island, please manually enable \"Download Manager\" scope; the \"Download\" template is recommended'**
  String get note5;

  /// No description provided for @behaviorSection.
  ///
  /// In en, this message translates to:
  /// **'Behavior'**
  String get behaviorSection;

  /// No description provided for @defaultConfigSection.
  ///
  /// In en, this message translates to:
  /// **'Default Channel Settings'**
  String get defaultConfigSection;

  /// No description provided for @appearanceSection.
  ///
  /// In en, this message translates to:
  /// **'Appearance'**
  String get appearanceSection;

  /// No description provided for @configSection.
  ///
  /// In en, this message translates to:
  /// **'Configuration'**
  String get configSection;

  /// No description provided for @aboutSection.
  ///
  /// In en, this message translates to:
  /// **'About'**
  String get aboutSection;

  /// No description provided for @keepFocusNotifTitle.
  ///
  /// In en, this message translates to:
  /// **'Keep notification after download pause'**
  String get keepFocusNotifTitle;

  /// No description provided for @keepFocusNotifSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Show a focus notification to resume download, but state synchronization issues may occur'**
  String get keepFocusNotifSubtitle;

  /// No description provided for @unlockAllFocusTitle.
  ///
  /// In en, this message translates to:
  /// **'Remove focus notification whitelist'**
  String get unlockAllFocusTitle;

  /// No description provided for @unlockAllFocusSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Allow all apps to send focus notifications without system authorization'**
  String get unlockAllFocusSubtitle;

  /// No description provided for @unlockFocusAuthTitle.
  ///
  /// In en, this message translates to:
  /// **'Remove focus notification signature verification'**
  String get unlockFocusAuthTitle;

  /// No description provided for @unlockFocusAuthSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Allow all apps to send focus notifications to watch/bracelet, bypassing signature check (requires hooking XMSF)'**
  String get unlockFocusAuthSubtitle;

  /// No description provided for @checkUpdateOnLaunchTitle.
  ///
  /// In en, this message translates to:
  /// **'Check for updates on launch'**
  String get checkUpdateOnLaunchTitle;

  /// No description provided for @checkUpdateOnLaunchSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Automatically check for new versions when the app starts'**
  String get checkUpdateOnLaunchSubtitle;

  /// No description provided for @debugLogTitle.
  ///
  /// In en, this message translates to:
  /// **'Show Debug Logs'**
  String get debugLogTitle;

  /// No description provided for @debugLogSubtitle.
  ///
  /// In en, this message translates to:
  /// **'When enabled, Hook debug logs are output; when disabled, only warning and error logs are kept'**
  String get debugLogSubtitle;

  /// No description provided for @showWelcomeTitle.
  ///
  /// In en, this message translates to:
  /// **'Show welcome message on launch'**
  String get showWelcomeTitle;

  /// No description provided for @showWelcomeSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Display welcome information on Island when the app starts'**
  String get showWelcomeSubtitle;

  /// No description provided for @openOnboardingTitle.
  ///
  /// In en, this message translates to:
  /// **'Open onboarding'**
  String get openOnboardingTitle;

  /// No description provided for @openOnboardingSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Review the welcome and quick start flow'**
  String get openOnboardingSubtitle;

  /// No description provided for @interactionHapticsTitle.
  ///
  /// In en, this message translates to:
  /// **'Interaction Haptics'**
  String get interactionHapticsTitle;

  /// No description provided for @interactionHapticsSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Enable Hyper custom haptic feedback for switches, sliders, and buttons'**
  String get interactionHapticsSubtitle;

  /// No description provided for @checkUpdate.
  ///
  /// In en, this message translates to:
  /// **'Check for updates'**
  String get checkUpdate;

  /// No description provided for @alreadyLatest.
  ///
  /// In en, this message translates to:
  /// **'Already on the latest version'**
  String get alreadyLatest;

  /// No description provided for @roundIconRadiusTitle.
  ///
  /// In en, this message translates to:
  /// **'Corner roundness'**
  String get roundIconRadiusTitle;

  /// No description provided for @roundIconTitle.
  ///
  /// In en, this message translates to:
  /// **'Notification icon corners'**
  String get roundIconTitle;

  /// No description provided for @roundIconSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Add rounded corners to notification icons'**
  String get roundIconSubtitle;

  /// No description provided for @islandIconSectionTitle.
  ///
  /// In en, this message translates to:
  /// **'Icons'**
  String get islandIconSectionTitle;

  /// No description provided for @iconSizeTitle.
  ///
  /// In en, this message translates to:
  /// **'Icon size'**
  String get iconSizeTitle;

  /// No description provided for @iconPaddingTitle.
  ///
  /// In en, this message translates to:
  /// **'Icon padding'**
  String get iconPaddingTitle;

  /// No description provided for @marqueeChannelTitle.
  ///
  /// In en, this message translates to:
  /// **'Text Scrolling Island'**
  String get marqueeChannelTitle;

  /// No description provided for @marqueeAutoHideTitle.
  ///
  /// In en, this message translates to:
  /// **'Hide Island after scrolling'**
  String get marqueeAutoHideTitle;

  /// No description provided for @marqueeAutoHideSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Hide the current Island after the message scrolls the selected number of times'**
  String get marqueeAutoHideSubtitle;

  /// No description provided for @marqueeAutoHideOnce.
  ///
  /// In en, this message translates to:
  /// **'Scroll once'**
  String get marqueeAutoHideOnce;

  /// No description provided for @marqueeAutoHideTwice.
  ///
  /// In en, this message translates to:
  /// **'Scroll twice'**
  String get marqueeAutoHideTwice;

  /// No description provided for @marqueeAutoHideOnceOverride.
  ///
  /// In en, this message translates to:
  /// **'Scroll once (override timeout)'**
  String get marqueeAutoHideOnceOverride;

  /// No description provided for @marqueeAutoHideTwiceOverride.
  ///
  /// In en, this message translates to:
  /// **'Scroll twice (override timeout)'**
  String get marqueeAutoHideTwiceOverride;

  /// No description provided for @marqueeSpeedTitle.
  ///
  /// In en, this message translates to:
  /// **'Speed'**
  String get marqueeSpeedTitle;

  /// No description provided for @marqueeSpeedLabel.
  ///
  /// In en, this message translates to:
  /// **'{speed} px/s'**
  String marqueeSpeedLabel(int speed);

  /// No description provided for @bigIslandMaxWidthTitle.
  ///
  /// In en, this message translates to:
  /// **'Max Width'**
  String get bigIslandMaxWidthTitle;

  /// No description provided for @bigIslandMinWidthTitle.
  ///
  /// In en, this message translates to:
  /// **'Min Width'**
  String get bigIslandMinWidthTitle;

  /// No description provided for @testNotifTooltip.
  ///
  /// In en, this message translates to:
  /// **'Send test notification'**
  String get testNotifTooltip;

  /// No description provided for @themeModeTitle.
  ///
  /// In en, this message translates to:
  /// **'Color mode'**
  String get themeModeTitle;

  /// No description provided for @themeModeSystem.
  ///
  /// In en, this message translates to:
  /// **'Follow system'**
  String get themeModeSystem;

  /// No description provided for @themeModeLight.
  ///
  /// In en, this message translates to:
  /// **'Light'**
  String get themeModeLight;

  /// No description provided for @themeModeDark.
  ///
  /// In en, this message translates to:
  /// **'Dark'**
  String get themeModeDark;

  /// No description provided for @languageTitle.
  ///
  /// In en, this message translates to:
  /// **'Language'**
  String get languageTitle;

  /// No description provided for @languageAuto.
  ///
  /// In en, this message translates to:
  /// **'Follow system'**
  String get languageAuto;

  /// No description provided for @languageZh.
  ///
  /// In en, this message translates to:
  /// **'中文'**
  String get languageZh;

  /// No description provided for @languageEn.
  ///
  /// In en, this message translates to:
  /// **'English'**
  String get languageEn;

  /// No description provided for @languageJa.
  ///
  /// In en, this message translates to:
  /// **'日本語'**
  String get languageJa;

  /// No description provided for @languageRu.
  ///
  /// In en, this message translates to:
  /// **'Русский'**
  String get languageRu;

  /// No description provided for @languageTr.
  ///
  /// In en, this message translates to:
  /// **'Türkçe'**
  String get languageTr;

  /// No description provided for @exportToFile.
  ///
  /// In en, this message translates to:
  /// **'Export to file'**
  String get exportToFile;

  /// No description provided for @exportToFileSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Save configuration as a JSON file'**
  String get exportToFileSubtitle;

  /// No description provided for @exportToClipboard.
  ///
  /// In en, this message translates to:
  /// **'Export to clipboard'**
  String get exportToClipboard;

  /// No description provided for @exportToClipboardSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Copy configuration as JSON text'**
  String get exportToClipboardSubtitle;

  /// No description provided for @importFromFile.
  ///
  /// In en, this message translates to:
  /// **'Import from file'**
  String get importFromFile;

  /// No description provided for @importFromFileSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Restore configuration from a JSON file'**
  String get importFromFileSubtitle;

  /// No description provided for @importFromClipboard.
  ///
  /// In en, this message translates to:
  /// **'Import from clipboard'**
  String get importFromClipboard;

  /// No description provided for @importFromClipboardSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Restore configuration from JSON text in clipboard'**
  String get importFromClipboardSubtitle;

  /// No description provided for @exportConfig.
  ///
  /// In en, this message translates to:
  /// **'Export Configuration'**
  String get exportConfig;

  /// No description provided for @exportConfigSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Choose to export to file or clipboard'**
  String get exportConfigSubtitle;

  /// No description provided for @importConfig.
  ///
  /// In en, this message translates to:
  /// **'Import Configuration'**
  String get importConfig;

  /// No description provided for @importConfigSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Choose to import from file or clipboard'**
  String get importConfigSubtitle;

  /// No description provided for @qqGroup.
  ///
  /// In en, this message translates to:
  /// **'QQ Group'**
  String get qqGroup;

  /// No description provided for @restartScopeApp.
  ///
  /// In en, this message translates to:
  /// **'Please restart the scope app for settings to take effect'**
  String get restartScopeApp;

  /// No description provided for @groupNumberCopied.
  ///
  /// In en, this message translates to:
  /// **'Group number copied to clipboard'**
  String get groupNumberCopied;

  /// No description provided for @exportedTo.
  ///
  /// In en, this message translates to:
  /// **'Exported to: {path}'**
  String exportedTo(String path);

  /// No description provided for @exportFailed.
  ///
  /// In en, this message translates to:
  /// **'Export failed: {error}'**
  String exportFailed(String error);

  /// No description provided for @configCopied.
  ///
  /// In en, this message translates to:
  /// **'Configuration copied to clipboard'**
  String get configCopied;

  /// No description provided for @importSuccess.
  ///
  /// In en, this message translates to:
  /// **'Import successful, {count} items, please restart the app'**
  String importSuccess(int count);

  /// No description provided for @importFailed.
  ///
  /// In en, this message translates to:
  /// **'Import failed: {error}'**
  String importFailed(String error);

  /// No description provided for @appAdaptation.
  ///
  /// In en, this message translates to:
  /// **'App Adaptation'**
  String get appAdaptation;

  /// No description provided for @toastAdaptation.
  ///
  /// In en, this message translates to:
  /// **'Toast Adaptation'**
  String get toastAdaptation;

  /// No description provided for @adaptationModeNotification.
  ///
  /// In en, this message translates to:
  /// **'Notification'**
  String get adaptationModeNotification;

  /// No description provided for @adaptationModeToast.
  ///
  /// In en, this message translates to:
  /// **'Toast'**
  String get adaptationModeToast;

  /// No description provided for @toastEnabledAppsCount.
  ///
  /// In en, this message translates to:
  /// **'Toast intercept enabled for {count} apps'**
  String toastEnabledAppsCount(Object count);

  /// No description provided for @toastEnabledAppsCountWithSystem.
  ///
  /// In en, this message translates to:
  /// **'Toast intercept enabled for {count} apps (including system apps)'**
  String toastEnabledAppsCountWithSystem(Object count);

  /// No description provided for @selectedAppsCount.
  ///
  /// In en, this message translates to:
  /// **'{count} apps selected'**
  String selectedAppsCount(int count);

  /// No description provided for @cancelSelection.
  ///
  /// In en, this message translates to:
  /// **'Cancel selection'**
  String get cancelSelection;

  /// No description provided for @deselectAll.
  ///
  /// In en, this message translates to:
  /// **'Deselect all'**
  String get deselectAll;

  /// No description provided for @selectAll.
  ///
  /// In en, this message translates to:
  /// **'Select all'**
  String get selectAll;

  /// No description provided for @batchChannelSettings.
  ///
  /// In en, this message translates to:
  /// **'Batch channel settings'**
  String get batchChannelSettings;

  /// No description provided for @selectEnabledApps.
  ///
  /// In en, this message translates to:
  /// **'Select enabled apps'**
  String get selectEnabledApps;

  /// No description provided for @batchEnable.
  ///
  /// In en, this message translates to:
  /// **'Batch enable'**
  String get batchEnable;

  /// No description provided for @batchDisable.
  ///
  /// In en, this message translates to:
  /// **'Batch disable'**
  String get batchDisable;

  /// No description provided for @multiSelect.
  ///
  /// In en, this message translates to:
  /// **'Multi-select'**
  String get multiSelect;

  /// No description provided for @showSystemApps.
  ///
  /// In en, this message translates to:
  /// **'Show system apps'**
  String get showSystemApps;

  /// No description provided for @refreshList.
  ///
  /// In en, this message translates to:
  /// **'Refresh list'**
  String get refreshList;

  /// No description provided for @enableAll.
  ///
  /// In en, this message translates to:
  /// **'Enable all'**
  String get enableAll;

  /// No description provided for @disableAll.
  ///
  /// In en, this message translates to:
  /// **'Disable all'**
  String get disableAll;

  /// No description provided for @enabledAppsCount.
  ///
  /// In en, this message translates to:
  /// **'Dynamic Island enabled for {count} apps'**
  String enabledAppsCount(int count);

  /// No description provided for @enabledAppsCountWithSystem.
  ///
  /// In en, this message translates to:
  /// **'Dynamic Island enabled for {count} apps (including system apps)'**
  String enabledAppsCountWithSystem(int count);

  /// No description provided for @searchApps.
  ///
  /// In en, this message translates to:
  /// **'Search app name or package name'**
  String get searchApps;

  /// No description provided for @noAppsFound.
  ///
  /// In en, this message translates to:
  /// **'No installed apps found\nPlease check if app list permission is enabled'**
  String get noAppsFound;

  /// No description provided for @noMatchingApps.
  ///
  /// In en, this message translates to:
  /// **'No matching apps'**
  String get noMatchingApps;

  /// No description provided for @applyToSelectedAppsChannels.
  ///
  /// In en, this message translates to:
  /// **'Will apply to enabled channels of {count} selected apps'**
  String applyToSelectedAppsChannels(int count);

  /// No description provided for @applyingConfig.
  ///
  /// In en, this message translates to:
  /// **'Applying configuration...'**
  String get applyingConfig;

  /// No description provided for @progressApps.
  ///
  /// In en, this message translates to:
  /// **'Progress: {done} / {total}'**
  String progressApps(int done, int total);

  /// No description provided for @batchApplied.
  ///
  /// In en, this message translates to:
  /// **'Batch applied to {count} apps'**
  String batchApplied(int count);

  /// No description provided for @cannotReadChannels.
  ///
  /// In en, this message translates to:
  /// **'Cannot Read Notification Channels'**
  String get cannotReadChannels;

  /// No description provided for @rootRequiredMessage.
  ///
  /// In en, this message translates to:
  /// **'Reading notification channels requires ROOT permission.\nPlease confirm ROOT permission is granted and try again.'**
  String get rootRequiredMessage;

  /// No description provided for @enableAllChannels.
  ///
  /// In en, this message translates to:
  /// **'Enable all channels'**
  String get enableAllChannels;

  /// No description provided for @noChannelsFound.
  ///
  /// In en, this message translates to:
  /// **'No notification channels found'**
  String get noChannelsFound;

  /// No description provided for @noChannelsFoundSubtitle.
  ///
  /// In en, this message translates to:
  /// **'This app has no notification channels, or they cannot be read'**
  String get noChannelsFoundSubtitle;

  /// No description provided for @allChannelsActive.
  ///
  /// In en, this message translates to:
  /// **'Active for all {count} channels'**
  String allChannelsActive(int count);

  /// No description provided for @selectedChannels.
  ///
  /// In en, this message translates to:
  /// **'{selected} / {total} channels selected'**
  String selectedChannels(int selected, int total);

  /// No description provided for @allChannelsDisabled.
  ///
  /// In en, this message translates to:
  /// **'All {count} channels (disabled)'**
  String allChannelsDisabled(int count);

  /// No description provided for @appDisabledBanner.
  ///
  /// In en, this message translates to:
  /// **'App is disabled, the following channel settings have no effect'**
  String get appDisabledBanner;

  /// No description provided for @channelImportance.
  ///
  /// In en, this message translates to:
  /// **'Importance: {importance}  ·  {id}'**
  String channelImportance(String importance, String id);

  /// No description provided for @channelSettings.
  ///
  /// In en, this message translates to:
  /// **'Channel settings'**
  String get channelSettings;

  /// No description provided for @toastForwardTitle.
  ///
  /// In en, this message translates to:
  /// **'Forward standard toast'**
  String get toastForwardTitle;

  /// No description provided for @toastForwardSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Convert this app\'s standard toast text to HyperIsland focus notification and super island'**
  String get toastForwardSubtitle;

  /// No description provided for @toastBlockOriginalTitle.
  ///
  /// In en, this message translates to:
  /// **'Block original toast'**
  String get toastBlockOriginalTitle;

  /// No description provided for @toastBlockOriginalSubtitle.
  ///
  /// In en, this message translates to:
  /// **'After forwarding, block this app\'s original standard toast popup'**
  String get toastBlockOriginalSubtitle;

  /// No description provided for @toastShowNotificationTitle.
  ///
  /// In en, this message translates to:
  /// **'Show in notification center'**
  String get toastShowNotificationTitle;

  /// No description provided for @toastShowNotificationSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Keep this forwarded toast as a visible notification in the shade'**
  String get toastShowNotificationSubtitle;

  /// No description provided for @toastShowIslandIconTitle.
  ///
  /// In en, this message translates to:
  /// **'Show island icon'**
  String get toastShowIslandIconTitle;

  /// No description provided for @toastShowIslandIconSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Show icon on the left side of the large island for forwarded toast'**
  String get toastShowIslandIconSubtitle;

  /// No description provided for @toastStandardOnlyHint.
  ///
  /// In en, this message translates to:
  /// **'Only standard text toast is handled; custom toast views are ignored.'**
  String get toastStandardOnlyHint;

  /// No description provided for @importanceNone.
  ///
  /// In en, this message translates to:
  /// **'None'**
  String get importanceNone;

  /// No description provided for @importanceMin.
  ///
  /// In en, this message translates to:
  /// **'Min'**
  String get importanceMin;

  /// No description provided for @importanceLow.
  ///
  /// In en, this message translates to:
  /// **'Low'**
  String get importanceLow;

  /// No description provided for @importanceDefault.
  ///
  /// In en, this message translates to:
  /// **'Default'**
  String get importanceDefault;

  /// No description provided for @importanceHigh.
  ///
  /// In en, this message translates to:
  /// **'High'**
  String get importanceHigh;

  /// No description provided for @importanceUnknown.
  ///
  /// In en, this message translates to:
  /// **'Unknown'**
  String get importanceUnknown;

  /// No description provided for @applyToEnabledChannels.
  ///
  /// In en, this message translates to:
  /// **'Will apply to {count} enabled channels'**
  String applyToEnabledChannels(int count);

  /// No description provided for @applyToAllChannels.
  ///
  /// In en, this message translates to:
  /// **'Will apply to all {count} channels'**
  String applyToAllChannels(int count);

  /// No description provided for @templateDownloadName.
  ///
  /// In en, this message translates to:
  /// **'Download'**
  String get templateDownloadName;

  /// No description provided for @templateNotificationIslandName.
  ///
  /// In en, this message translates to:
  /// **'Notification Island'**
  String get templateNotificationIslandName;

  /// No description provided for @templateNotificationIslandLiteName.
  ///
  /// In en, this message translates to:
  /// **'Notification Island|Lite'**
  String get templateNotificationIslandLiteName;

  /// No description provided for @templateDownloadLiteName.
  ///
  /// In en, this message translates to:
  /// **'Download|Lite'**
  String get templateDownloadLiteName;

  /// No description provided for @islandSection.
  ///
  /// In en, this message translates to:
  /// **'Island'**
  String get islandSection;

  /// No description provided for @islandEnabledLabel.
  ///
  /// In en, this message translates to:
  /// **'Enable island'**
  String get islandEnabledLabel;

  /// No description provided for @template.
  ///
  /// In en, this message translates to:
  /// **'Template'**
  String get template;

  /// No description provided for @rendererLabel.
  ///
  /// In en, this message translates to:
  /// **'Style'**
  String get rendererLabel;

  /// No description provided for @rendererImageTextWithButtons4Name.
  ///
  /// In en, this message translates to:
  /// **'Image+Text+Bottom Text Buttons'**
  String get rendererImageTextWithButtons4Name;

  /// No description provided for @rendererCoverInfoName.
  ///
  /// In en, this message translates to:
  /// **'Cover Info+Auto Wrap'**
  String get rendererCoverInfoName;

  /// No description provided for @rendererImageTextWithRightTextButtonName.
  ///
  /// In en, this message translates to:
  /// **'Image+Text+Right Text Button'**
  String get rendererImageTextWithRightTextButtonName;

  /// No description provided for @rendererImageTextWithProgressName.
  ///
  /// In en, this message translates to:
  /// **'IM Image+Text+Progress'**
  String get rendererImageTextWithProgressName;

  /// No description provided for @islandIcon.
  ///
  /// In en, this message translates to:
  /// **'Island icon'**
  String get islandIcon;

  /// No description provided for @focusIconLabel.
  ///
  /// In en, this message translates to:
  /// **'Focus icon'**
  String get focusIconLabel;

  /// No description provided for @focusExpressionCustomizationSection.
  ///
  /// In en, this message translates to:
  /// **'Focus advanced customization'**
  String get focusExpressionCustomizationSection;

  /// No description provided for @islandExpressionCustomizationSection.
  ///
  /// In en, this message translates to:
  /// **'Island advanced customization'**
  String get islandExpressionCustomizationSection;

  /// No description provided for @aodSection.
  ///
  /// In en, this message translates to:
  /// **'Always-on display'**
  String get aodSection;

  /// No description provided for @expandCustomization.
  ///
  /// In en, this message translates to:
  /// **'Expand'**
  String get expandCustomization;

  /// No description provided for @collapseCustomization.
  ///
  /// In en, this message translates to:
  /// **'Collapse'**
  String get collapseCustomization;

  /// No description provided for @availablePlaceholdersLabel.
  ///
  /// In en, this message translates to:
  /// **'Available placeholders(Click to copy)'**
  String get availablePlaceholdersLabel;

  /// No description provided for @expressionFunctionsLabel.
  ///
  /// In en, this message translates to:
  /// **'Expression functions'**
  String get expressionFunctionsLabel;

  /// No description provided for @focusTitleExprLabel.
  ///
  /// In en, this message translates to:
  /// **'Focus title expression'**
  String get focusTitleExprLabel;

  /// No description provided for @focusContentExprLabel.
  ///
  /// In en, this message translates to:
  /// **'Focus content expression'**
  String get focusContentExprLabel;

  /// No description provided for @focusIconSourceLabel.
  ///
  /// In en, this message translates to:
  /// **'Focus icon source'**
  String get focusIconSourceLabel;

  /// No description provided for @focusPicProfileSourceLabel.
  ///
  /// In en, this message translates to:
  /// **'Profile icon source'**
  String get focusPicProfileSourceLabel;

  /// No description provided for @focusAppIconPkgLabel.
  ///
  /// In en, this message translates to:
  /// **'App icon package'**
  String get focusAppIconPkgLabel;

  /// No description provided for @focusSecondaryIconSourceLabel.
  ///
  /// In en, this message translates to:
  /// **'Secondary icon source'**
  String get focusSecondaryIconSourceLabel;

  /// No description provided for @chatTitleColorLabel.
  ///
  /// In en, this message translates to:
  /// **'Chat title color'**
  String get chatTitleColorLabel;

  /// No description provided for @chatTitleColorDarkLabel.
  ///
  /// In en, this message translates to:
  /// **'Chat title color (dark)'**
  String get chatTitleColorDarkLabel;

  /// No description provided for @chatContentColorLabel.
  ///
  /// In en, this message translates to:
  /// **'Chat content color'**
  String get chatContentColorLabel;

  /// No description provided for @chatContentColorDarkLabel.
  ///
  /// In en, this message translates to:
  /// **'Chat content color (dark)'**
  String get chatContentColorDarkLabel;

  /// No description provided for @progressColorLabel.
  ///
  /// In en, this message translates to:
  /// **'Progress color'**
  String get progressColorLabel;

  /// No description provided for @progressBarColorLabel.
  ///
  /// In en, this message translates to:
  /// **'Progress bar color'**
  String get progressBarColorLabel;

  /// No description provided for @progressBarColorEndLabel.
  ///
  /// In en, this message translates to:
  /// **'Progress bar end color'**
  String get progressBarColorEndLabel;

  /// No description provided for @placeholderTitle.
  ///
  /// In en, this message translates to:
  /// **'Notification title'**
  String get placeholderTitle;

  /// No description provided for @placeholderSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Notification content'**
  String get placeholderSubtitle;

  /// No description provided for @placeholderSubtitleOrTitle.
  ///
  /// In en, this message translates to:
  /// **'Content (fallback title)'**
  String get placeholderSubtitleOrTitle;

  /// No description provided for @placeholderPkg.
  ///
  /// In en, this message translates to:
  /// **'Package name'**
  String get placeholderPkg;

  /// No description provided for @placeholderChannelId.
  ///
  /// In en, this message translates to:
  /// **'Channel ID'**
  String get placeholderChannelId;

  /// No description provided for @placeholderProgress.
  ///
  /// In en, this message translates to:
  /// **'Notification progress'**
  String get placeholderProgress;

  /// No description provided for @placeholderStateLabel.
  ///
  /// In en, this message translates to:
  /// **'State label'**
  String get placeholderStateLabel;

  /// No description provided for @placeholderProgressText.
  ///
  /// In en, this message translates to:
  /// **'Progress text'**
  String get placeholderProgressText;

  /// No description provided for @placeholderAiLeft.
  ///
  /// In en, this message translates to:
  /// **'AI left text'**
  String get placeholderAiLeft;

  /// No description provided for @placeholderAiRight.
  ///
  /// In en, this message translates to:
  /// **'AI right text'**
  String get placeholderAiRight;

  /// No description provided for @placeholderRawTitle.
  ///
  /// In en, this message translates to:
  /// **'Raw title'**
  String get placeholderRawTitle;

  /// No description provided for @placeholderRawSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Raw subtitle'**
  String get placeholderRawSubtitle;

  /// No description provided for @placeholderRawSubtitleOrTitle.
  ///
  /// In en, this message translates to:
  /// **'Raw subtitle (fallback title)'**
  String get placeholderRawSubtitleOrTitle;

  /// No description provided for @islandLeftExprLabel.
  ///
  /// In en, this message translates to:
  /// **'Island left expression'**
  String get islandLeftExprLabel;

  /// No description provided for @islandRightExprLabel.
  ///
  /// In en, this message translates to:
  /// **'Island right expression'**
  String get islandRightExprLabel;

  /// No description provided for @aodTextSwitchLabel.
  ///
  /// In en, this message translates to:
  /// **'AOD text switch'**
  String get aodTextSwitchLabel;

  /// No description provided for @aodTextSwitchSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Show notification text on the AOD when enabled'**
  String get aodTextSwitchSubtitle;

  /// No description provided for @aodTextExprLabel.
  ///
  /// In en, this message translates to:
  /// **'AOD text expression'**
  String get aodTextExprLabel;

  /// No description provided for @aodIconSourceLabel.
  ///
  /// In en, this message translates to:
  /// **'AOD icon source'**
  String get aodIconSourceLabel;

  /// No description provided for @focusNotificationLabel.
  ///
  /// In en, this message translates to:
  /// **'Focus notification'**
  String get focusNotificationLabel;

  /// No description provided for @hideNotificationLabel.
  ///
  /// In en, this message translates to:
  /// **'Hide notification'**
  String get hideNotificationLabel;

  /// No description provided for @hideNotificationLabelSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Only show the island and hide the focus notification from the notification shade'**
  String get hideNotificationLabelSubtitle;

  /// No description provided for @preserveStatusBarSmallIconLabel.
  ///
  /// In en, this message translates to:
  /// **'Status bar icon'**
  String get preserveStatusBarSmallIconLabel;

  /// No description provided for @preserveStatusBarSmallIconLabelSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Whether to force keep status bar icon when focus notification is displayed'**
  String get preserveStatusBarSmallIconLabelSubtitle;

  /// No description provided for @islandIconLabel.
  ///
  /// In en, this message translates to:
  /// **'Large island icon'**
  String get islandIconLabel;

  /// No description provided for @islandIconLabelSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Show the large icon of the island when enabled (small island not affected)'**
  String get islandIconLabelSubtitle;

  /// No description provided for @firstFloatLabel.
  ///
  /// In en, this message translates to:
  /// **'First float'**
  String get firstFloatLabel;

  /// No description provided for @updateFloatLabel.
  ///
  /// In en, this message translates to:
  /// **'Update float'**
  String get updateFloatLabel;

  /// No description provided for @autoDisappear.
  ///
  /// In en, this message translates to:
  /// **'Auto dismiss'**
  String get autoDisappear;

  /// No description provided for @seconds.
  ///
  /// In en, this message translates to:
  /// **'s'**
  String get seconds;

  /// No description provided for @defaultTimeoutSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Default auto-dismiss duration for notification islands'**
  String get defaultTimeoutSubtitle;

  /// No description provided for @highlightColorLabel.
  ///
  /// In en, this message translates to:
  /// **'Highlight color'**
  String get highlightColorLabel;

  /// No description provided for @dynamicHighlightColorLabel.
  ///
  /// In en, this message translates to:
  /// **'Dynamic highlight color'**
  String get dynamicHighlightColorLabel;

  /// No description provided for @dynamicHighlightColorLabelSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Use icon-based dynamic color by default'**
  String get dynamicHighlightColorLabelSubtitle;

  /// No description provided for @followDynamicColorLabel.
  ///
  /// In en, this message translates to:
  /// **'Follow dynamic color'**
  String get followDynamicColorLabel;

  /// No description provided for @dynamicHighlightModeDark.
  ///
  /// In en, this message translates to:
  /// **'Dark'**
  String get dynamicHighlightModeDark;

  /// No description provided for @dynamicHighlightModeDarker.
  ///
  /// In en, this message translates to:
  /// **'Darker'**
  String get dynamicHighlightModeDarker;

  /// No description provided for @outerGlowLabel.
  ///
  /// In en, this message translates to:
  /// **'Outer glow'**
  String get outerGlowLabel;

  /// No description provided for @forceOuterGlowLabel.
  ///
  /// In en, this message translates to:
  /// **'Force globally'**
  String get forceOuterGlowLabel;

  /// No description provided for @forceFocusOuterGlowSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Force glow for unmatched focus notifications when enabled'**
  String get forceFocusOuterGlowSubtitle;

  /// No description provided for @forceIslandOuterGlowSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Force glow for unmatched islands when enabled'**
  String get forceIslandOuterGlowSubtitle;

  /// No description provided for @outEffectColorLabel.
  ///
  /// In en, this message translates to:
  /// **'Outer glow color'**
  String get outEffectColorLabel;

  /// No description provided for @highlightColorHint.
  ///
  /// In en, this message translates to:
  /// **'#RRGGBB format, leave empty for default'**
  String get highlightColorHint;

  /// No description provided for @actionBgColorLabel.
  ///
  /// In en, this message translates to:
  /// **'Action background color'**
  String get actionBgColorLabel;

  /// No description provided for @actionBgColorDarkLabel.
  ///
  /// In en, this message translates to:
  /// **'Action background color (dark)'**
  String get actionBgColorDarkLabel;

  /// No description provided for @actionTitleColorLabel.
  ///
  /// In en, this message translates to:
  /// **'Action title color'**
  String get actionTitleColorLabel;

  /// No description provided for @actionTitleColorDarkLabel.
  ///
  /// In en, this message translates to:
  /// **'Action title color (dark)'**
  String get actionTitleColorDarkLabel;

  /// No description provided for @action1BgColorLabel.
  ///
  /// In en, this message translates to:
  /// **'Action 1 background color'**
  String get action1BgColorLabel;

  /// No description provided for @action1BgColorDarkLabel.
  ///
  /// In en, this message translates to:
  /// **'Action 1 background color (dark)'**
  String get action1BgColorDarkLabel;

  /// No description provided for @action1TitleColorLabel.
  ///
  /// In en, this message translates to:
  /// **'Action 1 title color'**
  String get action1TitleColorLabel;

  /// No description provided for @action1TitleColorDarkLabel.
  ///
  /// In en, this message translates to:
  /// **'Action 1 title color (dark)'**
  String get action1TitleColorDarkLabel;

  /// No description provided for @action2BgColorLabel.
  ///
  /// In en, this message translates to:
  /// **'Action 2 background color'**
  String get action2BgColorLabel;

  /// No description provided for @action2BgColorDarkLabel.
  ///
  /// In en, this message translates to:
  /// **'Action 2 background color (dark)'**
  String get action2BgColorDarkLabel;

  /// No description provided for @action2TitleColorLabel.
  ///
  /// In en, this message translates to:
  /// **'Action 2 title color'**
  String get action2TitleColorLabel;

  /// No description provided for @action2TitleColorDarkLabel.
  ///
  /// In en, this message translates to:
  /// **'Action 2 title color (dark)'**
  String get action2TitleColorDarkLabel;

  /// No description provided for @textHighlightLabel.
  ///
  /// In en, this message translates to:
  /// **'Text highlight'**
  String get textHighlightLabel;

  /// No description provided for @narrowFontLabel.
  ///
  /// In en, this message translates to:
  /// **'Narrow font'**
  String get narrowFontLabel;

  /// No description provided for @showLeftHighlightLabel.
  ///
  /// In en, this message translates to:
  /// **'Left text highlight'**
  String get showLeftHighlightLabel;

  /// No description provided for @showRightHighlightLabel.
  ///
  /// In en, this message translates to:
  /// **'Right text highlight'**
  String get showRightHighlightLabel;

  /// No description provided for @showLeftHighlightShort.
  ///
  /// In en, this message translates to:
  /// **'Left'**
  String get showLeftHighlightShort;

  /// No description provided for @showRightHighlightShort.
  ///
  /// In en, this message translates to:
  /// **'Right'**
  String get showRightHighlightShort;

  /// No description provided for @colorHue.
  ///
  /// In en, this message translates to:
  /// **'Hue'**
  String get colorHue;

  /// No description provided for @colorSaturation.
  ///
  /// In en, this message translates to:
  /// **'Saturation'**
  String get colorSaturation;

  /// No description provided for @colorBrightness.
  ///
  /// In en, this message translates to:
  /// **'Brightness'**
  String get colorBrightness;

  /// No description provided for @colorOpacity.
  ///
  /// In en, this message translates to:
  /// **'Opacity'**
  String get colorOpacity;

  /// No description provided for @onlyEnabledChannels.
  ///
  /// In en, this message translates to:
  /// **'Only apply to enabled channels'**
  String get onlyEnabledChannels;

  /// No description provided for @enabledChannelsCount.
  ///
  /// In en, this message translates to:
  /// **'{enabled} / {total} channels enabled'**
  String enabledChannelsCount(int enabled, int total);

  /// No description provided for @iconModeAuto.
  ///
  /// In en, this message translates to:
  /// **'Auto'**
  String get iconModeAuto;

  /// No description provided for @iconModeNotifSmall.
  ///
  /// In en, this message translates to:
  /// **'Small notification icon'**
  String get iconModeNotifSmall;

  /// No description provided for @iconModeNotifLarge.
  ///
  /// In en, this message translates to:
  /// **'Large notification icon'**
  String get iconModeNotifLarge;

  /// No description provided for @iconModeAppIcon.
  ///
  /// In en, this message translates to:
  /// **'App icon'**
  String get iconModeAppIcon;

  /// No description provided for @optDefault.
  ///
  /// In en, this message translates to:
  /// **'Default'**
  String get optDefault;

  /// No description provided for @optDefaultOn.
  ///
  /// In en, this message translates to:
  /// **'Default (On)'**
  String get optDefaultOn;

  /// No description provided for @optDefaultOff.
  ///
  /// In en, this message translates to:
  /// **'Default (Off)'**
  String get optDefaultOff;

  /// No description provided for @optOn.
  ///
  /// In en, this message translates to:
  /// **'On'**
  String get optOn;

  /// No description provided for @optOff.
  ///
  /// In en, this message translates to:
  /// **'Off'**
  String get optOff;

  /// No description provided for @errorInvalidFormat.
  ///
  /// In en, this message translates to:
  /// **'Invalid configuration format'**
  String get errorInvalidFormat;

  /// No description provided for @errorNoStorageDir.
  ///
  /// In en, this message translates to:
  /// **'Cannot get storage directory'**
  String get errorNoStorageDir;

  /// No description provided for @errorNoFileSelected.
  ///
  /// In en, this message translates to:
  /// **'No file selected'**
  String get errorNoFileSelected;

  /// No description provided for @errorNoFilePath.
  ///
  /// In en, this message translates to:
  /// **'Cannot get file path'**
  String get errorNoFilePath;

  /// No description provided for @errorEmptyClipboard.
  ///
  /// In en, this message translates to:
  /// **'Clipboard is empty'**
  String get errorEmptyClipboard;

  /// No description provided for @navBlacklist.
  ///
  /// In en, this message translates to:
  /// **'Focus Blacklist'**
  String get navBlacklist;

  /// No description provided for @navBlacklistSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Block focus notification float or hide for specific apps'**
  String get navBlacklistSubtitle;

  /// No description provided for @presetGamesTitle.
  ///
  /// In en, this message translates to:
  /// **'Quick Filter Popular Games'**
  String get presetGamesTitle;

  /// No description provided for @presetGamesSuccess.
  ///
  /// In en, this message translates to:
  /// **'Added {count} installed games to blacklist from preset'**
  String presetGamesSuccess(int count);

  /// No description provided for @blacklistedAppsCount.
  ///
  /// In en, this message translates to:
  /// **'Blocked focus notifications for {count} apps'**
  String blacklistedAppsCount(int count);

  /// No description provided for @blacklistedAppsCountWithSystem.
  ///
  /// In en, this message translates to:
  /// **'Blocked focus notifications for {count} apps (including system apps)'**
  String blacklistedAppsCountWithSystem(int count);

  /// No description provided for @firstFloatLabelSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Whether to expand as focus notification when Island receives notification for the first time'**
  String get firstFloatLabelSubtitle;

  /// No description provided for @updateFloatLabelSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Whether to expand notification when Island updates'**
  String get updateFloatLabelSubtitle;

  /// No description provided for @marqueeChannelTitleSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Whether to scroll long messages on Island'**
  String get marqueeChannelTitleSubtitle;

  /// No description provided for @focusNotificationLabelSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Replace notification with focus notification (shows original notification when disabled)'**
  String get focusNotificationLabelSubtitle;

  /// No description provided for @fullscreenBehaviorTitle.
  ///
  /// In en, this message translates to:
  /// **'Fullscreen behavior'**
  String get fullscreenBehaviorTitle;

  /// No description provided for @fullscreenBehaviorSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Notification strategy when landscape/fullscreen is detected'**
  String get fullscreenBehaviorSubtitle;

  /// No description provided for @fullscreenBehaviorOff.
  ///
  /// In en, this message translates to:
  /// **'Default'**
  String get fullscreenBehaviorOff;

  /// No description provided for @fullscreenBehaviorFallback.
  ///
  /// In en, this message translates to:
  /// **'Fallback to normal notification'**
  String get fullscreenBehaviorFallback;

  /// No description provided for @filterRulesTitle.
  ///
  /// In en, this message translates to:
  /// **'Filter rules'**
  String get filterRulesTitle;

  /// No description provided for @filterRulesOrderTitle.
  ///
  /// In en, this message translates to:
  /// **'First matching rule wins'**
  String get filterRulesOrderTitle;

  /// No description provided for @filterRuleDnd.
  ///
  /// In en, this message translates to:
  /// **'DND'**
  String get filterRuleDnd;

  /// No description provided for @filterRuleFullscreen.
  ///
  /// In en, this message translates to:
  /// **'Fullscreen'**
  String get filterRuleFullscreen;

  /// No description provided for @filterRuleLandscape.
  ///
  /// In en, this message translates to:
  /// **'Landscape'**
  String get filterRuleLandscape;

  /// No description provided for @dndBehaviorTitle.
  ///
  /// In en, this message translates to:
  /// **'When DND'**
  String get dndBehaviorTitle;

  /// No description provided for @fullscreenRuleTitle.
  ///
  /// In en, this message translates to:
  /// **'When fullscreen'**
  String get fullscreenRuleTitle;

  /// No description provided for @landscapeRuleTitle.
  ///
  /// In en, this message translates to:
  /// **'When landscape'**
  String get landscapeRuleTitle;

  /// No description provided for @behaviorPreviewDefault.
  ///
  /// In en, this message translates to:
  /// **'No override when matched; keep default behavior'**
  String get behaviorPreviewDefault;

  /// No description provided for @behaviorPreviewSuppress.
  ///
  /// In en, this message translates to:
  /// **'Fallback to normal notification when matched'**
  String get behaviorPreviewSuppress;

  /// No description provided for @behaviorPreviewSmallOnly.
  ///
  /// In en, this message translates to:
  /// **'Show small island only; do not auto expand'**
  String get behaviorPreviewSmallOnly;

  /// No description provided for @behaviorPreviewExpand.
  ///
  /// In en, this message translates to:
  /// **'Auto expand notification when matched'**
  String get behaviorPreviewExpand;

  /// No description provided for @aiConfigSection.
  ///
  /// In en, this message translates to:
  /// **'AI Enhancement'**
  String get aiConfigSection;

  /// No description provided for @aiConfigTitle.
  ///
  /// In en, this message translates to:
  /// **'AI Notification Summary'**
  String get aiConfigTitle;

  /// No description provided for @aiConfigSubtitleEnabled.
  ///
  /// In en, this message translates to:
  /// **'Enabled · Tap to configure AI parameters'**
  String get aiConfigSubtitleEnabled;

  /// No description provided for @aiConfigSubtitleDisabled.
  ///
  /// In en, this message translates to:
  /// **'Disabled · Tap to configure'**
  String get aiConfigSubtitleDisabled;

  /// No description provided for @aiEnabledTitle.
  ///
  /// In en, this message translates to:
  /// **'Enable AI Summary'**
  String get aiEnabledTitle;

  /// No description provided for @aiEnabledSubtitle.
  ///
  /// In en, this message translates to:
  /// **'AI generates Island left/right text, falls back on timeout or error'**
  String get aiEnabledSubtitle;

  /// No description provided for @aiApiSection.
  ///
  /// In en, this message translates to:
  /// **'API Parameters'**
  String get aiApiSection;

  /// No description provided for @aiUrlLabel.
  ///
  /// In en, this message translates to:
  /// **'API URL'**
  String get aiUrlLabel;

  /// No description provided for @aiUrlHint.
  ///
  /// In en, this message translates to:
  /// **'https://api.openai.com/v1/chat/completions'**
  String get aiUrlHint;

  /// No description provided for @aiApiKeyLabel.
  ///
  /// In en, this message translates to:
  /// **'API Key'**
  String get aiApiKeyLabel;

  /// No description provided for @aiApiKeyHint.
  ///
  /// In en, this message translates to:
  /// **'sk-...'**
  String get aiApiKeyHint;

  /// No description provided for @aiModelLabel.
  ///
  /// In en, this message translates to:
  /// **'Model'**
  String get aiModelLabel;

  /// No description provided for @aiModelHint.
  ///
  /// In en, this message translates to:
  /// **'gpt-4o-mini'**
  String get aiModelHint;

  /// No description provided for @aiModelPickerTitle.
  ///
  /// In en, this message translates to:
  /// **'Select Model'**
  String get aiModelPickerTitle;

  /// No description provided for @aiModelPickerSearchHint.
  ///
  /// In en, this message translates to:
  /// **'Search models…'**
  String get aiModelPickerSearchHint;

  /// No description provided for @aiModelPickerEmpty.
  ///
  /// In en, this message translates to:
  /// **'No models found'**
  String get aiModelPickerEmpty;

  /// No description provided for @aiModelPickerRetry.
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get aiModelPickerRetry;

  /// No description provided for @aiModelPickerClose.
  ///
  /// In en, this message translates to:
  /// **'Close'**
  String get aiModelPickerClose;

  /// No description provided for @aiModelPickerFetchError.
  ///
  /// In en, this message translates to:
  /// **'Failed to load model list'**
  String get aiModelPickerFetchError;

  /// No description provided for @aiTestButton.
  ///
  /// In en, this message translates to:
  /// **'Test Connection'**
  String get aiTestButton;

  /// No description provided for @aiTestUrlEmpty.
  ///
  /// In en, this message translates to:
  /// **'Please enter an API URL first'**
  String get aiTestUrlEmpty;

  /// No description provided for @aiConfigSaveButton.
  ///
  /// In en, this message translates to:
  /// **'Save'**
  String get aiConfigSaveButton;

  /// No description provided for @aiConfigSaved.
  ///
  /// In en, this message translates to:
  /// **'AI configuration saved'**
  String get aiConfigSaved;

  /// No description provided for @aiConfigTips.
  ///
  /// In en, this message translates to:
  /// **'AI receives the app package, title, and content of each notification, and returns short left (source) and right (content) text. Compatible with OpenAI-format APIs (e.g. DeepSeek, Claude). Falls back to default logic if no response.'**
  String get aiConfigTips;

  /// No description provided for @templateAiNotificationIslandName.
  ///
  /// In en, this message translates to:
  /// **'AI Notification Island'**
  String get templateAiNotificationIslandName;

  /// No description provided for @aiPromptLabel.
  ///
  /// In en, this message translates to:
  /// **'Custom Prompt'**
  String get aiPromptLabel;

  /// No description provided for @aiPromptHint.
  ///
  /// In en, this message translates to:
  /// **'Leave empty to use default: Extract key info, left and right each no more than 6 words or 12 characters'**
  String get aiPromptHint;

  /// No description provided for @aiPromptDefault.
  ///
  /// In en, this message translates to:
  /// **'Extract key info from notification, left and right each no more than 6 words or 12 characters'**
  String get aiPromptDefault;

  /// No description provided for @aiPromptInUserTitle.
  ///
  /// In en, this message translates to:
  /// **'Put prompt in user message'**
  String get aiPromptInUserTitle;

  /// No description provided for @aiPromptInUserSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Some models do not support system instructions; enable to put prompt in user message'**
  String get aiPromptInUserSubtitle;

  /// No description provided for @aiCustomFieldsTitle.
  ///
  /// In en, this message translates to:
  /// **'Custom Fields'**
  String get aiCustomFieldsTitle;

  /// No description provided for @aiCustomFieldsSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Add or modify custom fields'**
  String get aiCustomFieldsSubtitle;

  /// No description provided for @aiCustomFieldsDialogTitle.
  ///
  /// In en, this message translates to:
  /// **'Custom Request Fields'**
  String get aiCustomFieldsDialogTitle;

  /// No description provided for @aiCustomFieldsDescription.
  ///
  /// In en, this message translates to:
  /// **'Values must be valid JSON, such as false, 1, \"text\", or a JSON object.'**
  String get aiCustomFieldsDescription;

  /// No description provided for @aiCustomFieldsReset.
  ///
  /// In en, this message translates to:
  /// **'Reset'**
  String get aiCustomFieldsReset;

  /// No description provided for @aiCustomFieldName.
  ///
  /// In en, this message translates to:
  /// **'Field name'**
  String get aiCustomFieldName;

  /// No description provided for @aiCustomFieldValue.
  ///
  /// In en, this message translates to:
  /// **'JSON value'**
  String get aiCustomFieldValue;

  /// No description provided for @aiCustomFieldAdd.
  ///
  /// In en, this message translates to:
  /// **'Add field'**
  String get aiCustomFieldAdd;

  /// No description provided for @aiCustomFieldDelete.
  ///
  /// In en, this message translates to:
  /// **'Delete field'**
  String get aiCustomFieldDelete;

  /// No description provided for @aiCustomFieldsError.
  ///
  /// In en, this message translates to:
  /// **'Field names cannot be empty and values must be valid JSON'**
  String get aiCustomFieldsError;

  /// No description provided for @aiCustomFieldsCancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get aiCustomFieldsCancel;

  /// No description provided for @aiCustomFieldsSave.
  ///
  /// In en, this message translates to:
  /// **'Save'**
  String get aiCustomFieldsSave;

  /// No description provided for @aiTimeoutTitle.
  ///
  /// In en, this message translates to:
  /// **'AI Response Timeout'**
  String get aiTimeoutTitle;

  /// No description provided for @aiTimeoutLabel.
  ///
  /// In en, this message translates to:
  /// **'{seconds}s'**
  String aiTimeoutLabel(int seconds);

  /// No description provided for @defaultTimeoutHint.
  ///
  /// In en, this message translates to:
  /// **'Default ({seconds}s)'**
  String defaultTimeoutHint(int seconds);

  /// No description provided for @aiTemperatureTitle.
  ///
  /// In en, this message translates to:
  /// **'Sampling Temperature'**
  String get aiTemperatureTitle;

  /// No description provided for @aiTemperatureSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Control the randomness of responses. 0 is precise, 1 is more creative'**
  String get aiTemperatureSubtitle;

  /// No description provided for @aiMaxTokensTitle.
  ///
  /// In en, this message translates to:
  /// **'Max Tokens'**
  String get aiMaxTokensTitle;

  /// No description provided for @aiMaxTokensSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Limit the maximum length of AI-generated responses'**
  String get aiMaxTokensSubtitle;

  /// No description provided for @aiTriggerCharCountTitle.
  ///
  /// In en, this message translates to:
  /// **'Trigger Character Count'**
  String get aiTriggerCharCountTitle;

  /// No description provided for @aiTriggerCharCountSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Trigger AI when the notification title and body reach this length'**
  String get aiTriggerCharCountSubtitle;

  /// No description provided for @aiTriggerCharCountAlways.
  ///
  /// In en, this message translates to:
  /// **'Always trigger AI regardless of notification length'**
  String get aiTriggerCharCountAlways;

  /// No description provided for @aiDefaultPromptFull.
  ///
  /// In en, this message translates to:
  /// **'Leave empty to use default prompt: Extract key info from notification, no more than 6 words or 12 characters for left and right sides'**
  String get aiDefaultPromptFull;

  /// No description provided for @aiDefaultNotificationText.
  ///
  /// In en, this message translates to:
  /// **'[Delivery] Your delivery has arrived and was placed in the parcel locker at the door'**
  String get aiDefaultNotificationText;

  /// No description provided for @aiTestSampleUserContent.
  ///
  /// In en, this message translates to:
  /// **'Reply exactly: test successful'**
  String get aiTestSampleUserContent;

  /// No description provided for @aiNotificationUserContent.
  ///
  /// In en, this message translates to:
  /// **'App package: com.example.app\nTitle: Test notification\nBody: {content}'**
  String aiNotificationUserContent(String content);

  /// No description provided for @aiJsonOnlyInstruction.
  ///
  /// In en, this message translates to:
  /// **'Return only the following JSON. Do not include any other text or code block:'**
  String get aiJsonOnlyInstruction;

  /// No description provided for @aiJsonLeftDescription.
  ///
  /// In en, this message translates to:
  /// **'left text (sender)'**
  String get aiJsonLeftDescription;

  /// No description provided for @aiJsonRightDescription.
  ///
  /// In en, this message translates to:
  /// **'right text (summary)'**
  String get aiJsonRightDescription;

  /// No description provided for @aiThinkingModeError.
  ///
  /// In en, this message translates to:
  /// **'AI thinking mode is enabled. Add a custom field to disable thinking mode'**
  String get aiThinkingModeError;

  /// No description provided for @aiInvalidJsonError.
  ///
  /// In en, this message translates to:
  /// **'Invalid AI response format. JSON with left and right fields is required'**
  String get aiInvalidJsonError;

  /// No description provided for @aiEmptyJsonError.
  ///
  /// In en, this message translates to:
  /// **'AI response is empty. JSON with left and right fields is required'**
  String get aiEmptyJsonError;

  /// No description provided for @aiNotificationContentLabel.
  ///
  /// In en, this message translates to:
  /// **'Notification Content'**
  String get aiNotificationContentLabel;

  /// No description provided for @aiTestNotificationTitle.
  ///
  /// In en, this message translates to:
  /// **'Test Notification'**
  String get aiTestNotificationTitle;

  /// No description provided for @aiNotificationSent.
  ///
  /// In en, this message translates to:
  /// **'Notification sent'**
  String get aiNotificationSent;

  /// No description provided for @aiAiNotificationSent.
  ///
  /// In en, this message translates to:
  /// **'AI notification sent'**
  String get aiAiNotificationSent;

  /// No description provided for @aiSendNotificationButton.
  ///
  /// In en, this message translates to:
  /// **'Send Notification'**
  String get aiSendNotificationButton;

  /// No description provided for @aiSendAiNotificationButton.
  ///
  /// In en, this message translates to:
  /// **'Send AI Notification'**
  String get aiSendAiNotificationButton;

  /// No description provided for @hideDesktopIconTitle.
  ///
  /// In en, this message translates to:
  /// **'Hide Desktop Icon'**
  String get hideDesktopIconTitle;

  /// No description provided for @hideDesktopIconSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Hide the app icon from launcher. Open via LSPosed Manager after hiding'**
  String get hideDesktopIconSubtitle;

  /// No description provided for @restoreLockscreenTitle.
  ///
  /// In en, this message translates to:
  /// **'Restore Lockscreen Notification'**
  String get restoreLockscreenTitle;

  /// No description provided for @restoreLockscreenSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Skip focus notification processing on lockscreen, keep original privacy behavior'**
  String get restoreLockscreenSubtitle;

  /// No description provided for @filterRulesSection.
  ///
  /// In en, this message translates to:
  /// **'Filter Rules'**
  String get filterRulesSection;

  /// No description provided for @foregroundRulesTab.
  ///
  /// In en, this message translates to:
  /// **'Foreground Rules'**
  String get foregroundRulesTab;

  /// No description provided for @foregroundExclusionsTab.
  ///
  /// In en, this message translates to:
  /// **'Excluded Apps'**
  String get foregroundExclusionsTab;

  /// No description provided for @foregroundRulesDescription.
  ///
  /// In en, this message translates to:
  /// **'Set Island behavior when a foreground app starts.'**
  String get foregroundRulesDescription;

  /// No description provided for @foregroundExclusionsDescription.
  ///
  /// In en, this message translates to:
  /// **'Notifications from apps in the exclusion list are not affected by foreground rules.'**
  String get foregroundExclusionsDescription;

  /// No description provided for @hideSystemApps.
  ///
  /// In en, this message translates to:
  /// **'Hide system apps'**
  String get hideSystemApps;

  /// No description provided for @restoreDefaultConfig.
  ///
  /// In en, this message translates to:
  /// **'Restore default config'**
  String get restoreDefaultConfig;

  /// No description provided for @resetDefaultConfigSuccess.
  ///
  /// In en, this message translates to:
  /// **'Default config restored for {count} apps'**
  String resetDefaultConfigSuccess(int count);

  /// No description provided for @sceneActionDefault.
  ///
  /// In en, this message translates to:
  /// **'Default'**
  String get sceneActionDefault;

  /// No description provided for @sceneActionSmallOnly.
  ///
  /// In en, this message translates to:
  /// **'Disable expansion'**
  String get sceneActionSmallOnly;

  /// No description provided for @sceneActionExpand.
  ///
  /// In en, this message translates to:
  /// **'Auto expand'**
  String get sceneActionExpand;

  /// No description provided for @sceneActionSuppress.
  ///
  /// In en, this message translates to:
  /// **'Fallback'**
  String get sceneActionSuppress;

  /// No description provided for @filterModeLabel.
  ///
  /// In en, this message translates to:
  /// **'Filter Mode'**
  String get filterModeLabel;

  /// No description provided for @filterModeBlacklist.
  ///
  /// In en, this message translates to:
  /// **'Blacklist'**
  String get filterModeBlacklist;

  /// No description provided for @filterModeWhitelist.
  ///
  /// In en, this message translates to:
  /// **'Whitelist'**
  String get filterModeWhitelist;

  /// No description provided for @filterModeBlacklistDesc.
  ///
  /// In en, this message translates to:
  /// **'Notifications matching keywords will be filtered'**
  String get filterModeBlacklistDesc;

  /// No description provided for @filterModeWhitelistDesc.
  ///
  /// In en, this message translates to:
  /// **'Only notifications matching keywords will be shown'**
  String get filterModeWhitelistDesc;

  /// No description provided for @whitelistKeywordsLabel.
  ///
  /// In en, this message translates to:
  /// **'Whitelist Keywords'**
  String get whitelistKeywordsLabel;

  /// No description provided for @blacklistKeywordsLabel.
  ///
  /// In en, this message translates to:
  /// **'Blacklist Keywords'**
  String get blacklistKeywordsLabel;

  /// No description provided for @addKeyword.
  ///
  /// In en, this message translates to:
  /// **'Add keyword'**
  String get addKeyword;

  /// No description provided for @keywordHint.
  ///
  /// In en, this message translates to:
  /// **'Enter keyword'**
  String get keywordHint;

  /// No description provided for @removeKeyword.
  ///
  /// In en, this message translates to:
  /// **'Remove'**
  String get removeKeyword;

  /// No description provided for @keywordFilterPriority.
  ///
  /// In en, this message translates to:
  /// **'Whitelist takes priority: only whitelist-matched notifications are shown, but blacklist can still veto'**
  String get keywordFilterPriority;

  /// No description provided for @exportChannelsToClipboard.
  ///
  /// In en, this message translates to:
  /// **'Export Channel Settings'**
  String get exportChannelsToClipboard;

  /// No description provided for @importChannelsFromClipboard.
  ///
  /// In en, this message translates to:
  /// **'Import Channel Settings'**
  String get importChannelsFromClipboard;

  /// No description provided for @exportChannelsSuccess.
  ///
  /// In en, this message translates to:
  /// **'Channel settings copied to clipboard'**
  String get exportChannelsSuccess;

  /// No description provided for @importChannelsSuccess.
  ///
  /// In en, this message translates to:
  /// **'Imported {count} channel settings'**
  String importChannelsSuccess(int count);

  /// No description provided for @importChannelsPartialSuffix.
  ///
  /// In en, this message translates to:
  /// **' ({matched} of {total} matched)'**
  String importChannelsPartialSuffix(int total, int matched);

  /// No description provided for @importErrorEmptyClipboard.
  ///
  /// In en, this message translates to:
  /// **'Clipboard is empty. Please copy channel settings first'**
  String get importErrorEmptyClipboard;

  /// No description provided for @importErrorNotJson.
  ///
  /// In en, this message translates to:
  /// **'Clipboard content is not valid JSON'**
  String get importErrorNotJson;

  /// No description provided for @importErrorMissingChannels.
  ///
  /// In en, this message translates to:
  /// **'Invalid data format: missing channel list'**
  String get importErrorMissingChannels;

  /// No description provided for @importErrorNoMatch.
  ///
  /// In en, this message translates to:
  /// **'No channels matched the current app. Please verify the data source'**
  String get importErrorNoMatch;

  /// No description provided for @importErrorUnknown.
  ///
  /// In en, this message translates to:
  /// **'Import failed. Please check clipboard data'**
  String get importErrorUnknown;

  /// No description provided for @mediaNotificationTitle.
  ///
  /// In en, this message translates to:
  /// **'Media notification'**
  String get mediaNotificationTitle;

  /// No description provided for @mediaNotificationDisabledSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Delete the entire media notification when disabled'**
  String get mediaNotificationDisabledSubtitle;

  /// No description provided for @normalNotificationTitle.
  ///
  /// In en, this message translates to:
  /// **'Normal notification'**
  String get normalNotificationTitle;

  /// No description provided for @normalNotificationSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Remove media fields and handle it as a normal notification when enabled'**
  String get normalNotificationSubtitle;

  /// No description provided for @channelSettingsUnmodified.
  ///
  /// In en, this message translates to:
  /// **'Not modified'**
  String get channelSettingsUnmodified;

  /// No description provided for @restoreDefault.
  ///
  /// In en, this message translates to:
  /// **'Restore default'**
  String get restoreDefault;

  /// No description provided for @islandDimenSection.
  ///
  /// In en, this message translates to:
  /// **'Island Dimensions'**
  String get islandDimenSection;

  /// No description provided for @islandDimenHeight.
  ///
  /// In en, this message translates to:
  /// **'Island Height'**
  String get islandDimenHeight;

  /// No description provided for @islandTopOffset.
  ///
  /// In en, this message translates to:
  /// **'Distance from Top of Screen'**
  String get islandTopOffset;

  /// No description provided for @smallIslandWidth.
  ///
  /// In en, this message translates to:
  /// **'Small Island Width'**
  String get smallIslandWidth;

  /// No description provided for @smallIslandHorizontalOffset.
  ///
  /// In en, this message translates to:
  /// **'Large-Small Island Gap'**
  String get smallIslandHorizontalOffset;

  /// No description provided for @followSystem.
  ///
  /// In en, this message translates to:
  /// **'Follow system'**
  String get followSystem;

  /// No description provided for @islandDimenMiniY.
  ///
  /// In en, this message translates to:
  /// **'Vertical Position'**
  String get islandDimenMiniY;

  /// No description provided for @islandDimenMiniYHint.
  ///
  /// In en, this message translates to:
  /// **'0=follow system'**
  String get islandDimenMiniYHint;

  /// No description provided for @islandBgSection.
  ///
  /// In en, this message translates to:
  /// **'Island Background'**
  String get islandBgSection;

  /// No description provided for @islandBgSmallTitle.
  ///
  /// In en, this message translates to:
  /// **'Small Island Background'**
  String get islandBgSmallTitle;

  /// No description provided for @islandBgBigTitle.
  ///
  /// In en, this message translates to:
  /// **'Large Island Background'**
  String get islandBgBigTitle;

  /// No description provided for @islandBgExpandTitle.
  ///
  /// In en, this message translates to:
  /// **'Focus Notification Background'**
  String get islandBgExpandTitle;

  /// No description provided for @islandBgNotSet.
  ///
  /// In en, this message translates to:
  /// **'Not set'**
  String get islandBgNotSet;

  /// No description provided for @islandBgCornerRadius.
  ///
  /// In en, this message translates to:
  /// **'Corner Radius'**
  String get islandBgCornerRadius;

  /// No description provided for @islandBgCornerRadiusHint.
  ///
  /// In en, this message translates to:
  /// **'0=system default'**
  String get islandBgCornerRadiusHint;

  /// No description provided for @islandBgImageSelected.
  ///
  /// In en, this message translates to:
  /// **'Background image saved'**
  String get islandBgImageSelected;

  /// No description provided for @islandBgImageDeleted.
  ///
  /// In en, this message translates to:
  /// **'Background image deleted'**
  String get islandBgImageDeleted;

  /// No description provided for @islandBgDeleteFailed.
  ///
  /// In en, this message translates to:
  /// **'Delete failed'**
  String get islandBgDeleteFailed;

  /// No description provided for @islandBgEditTitle.
  ///
  /// In en, this message translates to:
  /// **'Edit {type} Background'**
  String islandBgEditTitle(String type);

  /// No description provided for @islandBgBlurLabel.
  ///
  /// In en, this message translates to:
  /// **'Blur'**
  String get islandBgBlurLabel;

  /// No description provided for @islandBgBrightnessLabel.
  ///
  /// In en, this message translates to:
  /// **'Brightness'**
  String get islandBgBrightnessLabel;

  /// No description provided for @islandBgOpacityLabel.
  ///
  /// In en, this message translates to:
  /// **'Opacity'**
  String get islandBgOpacityLabel;

  /// No description provided for @islandBgDefault.
  ///
  /// In en, this message translates to:
  /// **'Default'**
  String get islandBgDefault;

  /// No description provided for @islandBlurSmallTitle.
  ///
  /// In en, this message translates to:
  /// **'Small Island Blur'**
  String get islandBlurSmallTitle;

  /// No description provided for @islandBlurBigTitle.
  ///
  /// In en, this message translates to:
  /// **'Large Island Blur'**
  String get islandBlurBigTitle;

  /// No description provided for @islandBlurExpandTitle.
  ///
  /// In en, this message translates to:
  /// **'Focus Notification Blur'**
  String get islandBlurExpandTitle;

  /// No description provided for @islandBlurEnabled.
  ///
  /// In en, this message translates to:
  /// **'Enable live background blur'**
  String get islandBlurEnabled;

  /// No description provided for @islandBlurRadius.
  ///
  /// In en, this message translates to:
  /// **'Blur radius'**
  String get islandBlurRadius;

  /// No description provided for @islandBlurBlendColor.
  ///
  /// In en, this message translates to:
  /// **'Blend color'**
  String get islandBlurBlendColor;

  /// No description provided for @islandBlurDisabled.
  ///
  /// In en, this message translates to:
  /// **'Disabled'**
  String get islandBlurDisabled;

  /// No description provided for @islandBlurUnavailableWithBackground.
  ///
  /// In en, this message translates to:
  /// **'Background and blur cannot be enabled at the same time'**
  String get islandBlurUnavailableWithBackground;

  /// No description provided for @islandBlurBigTextColorSuggestion.
  ///
  /// In en, this message translates to:
  /// **'Consider setting the Super Island text color to Follow Status Bar'**
  String get islandBlurBigTextColorSuggestion;

  /// No description provided for @islandBlurRadiusValue.
  ///
  /// In en, this message translates to:
  /// **'Blur {radius}'**
  String islandBlurRadiusValue(int radius);

  /// No description provided for @islandGlassSection.
  ///
  /// In en, this message translates to:
  /// **'Glass Effect'**
  String get islandGlassSection;

  /// No description provided for @islandGlassEnabled.
  ///
  /// In en, this message translates to:
  /// **'Enable glass effect'**
  String get islandGlassEnabled;

  /// No description provided for @islandGlassEnabledSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Add glass rim effects to enabled live background blur states'**
  String get islandGlassEnabledSubtitle;

  /// No description provided for @islandGlassRequiresBlur.
  ///
  /// In en, this message translates to:
  /// **'Enable Small, Large, or Focus Notification blur first'**
  String get islandGlassRequiresBlur;

  /// No description provided for @islandGlassEdgeWidth.
  ///
  /// In en, this message translates to:
  /// **'Edge width'**
  String get islandGlassEdgeWidth;

  /// No description provided for @islandGlassRefraction.
  ///
  /// In en, this message translates to:
  /// **'Refraction strength'**
  String get islandGlassRefraction;

  /// No description provided for @islandGlassHighlight.
  ///
  /// In en, this message translates to:
  /// **'Highlight strength'**
  String get islandGlassHighlight;

  /// No description provided for @islandGlassShadow.
  ///
  /// In en, this message translates to:
  /// **'Backlight shadow strength'**
  String get islandGlassShadow;

  /// No description provided for @islandGlassLightDirection.
  ///
  /// In en, this message translates to:
  /// **'Light direction'**
  String get islandGlassLightDirection;

  /// No description provided for @islandGlassDispersion.
  ///
  /// In en, this message translates to:
  /// **'Dispersion strength'**
  String get islandGlassDispersion;

  /// No description provided for @islandGlassGyroscope.
  ///
  /// In en, this message translates to:
  /// **'Gyroscope lighting'**
  String get islandGlassGyroscope;

  /// No description provided for @islandGlassGyroscopeSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Move rim lighting with the device pose'**
  String get islandGlassGyroscopeSubtitle;

  /// No description provided for @islandGlassCustomize.
  ///
  /// In en, this message translates to:
  /// **'Customize glass effect'**
  String get islandGlassCustomize;

  /// No description provided for @islandGlassCustomizeSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Customize glass effect parameters'**
  String get islandGlassCustomizeSubtitle;

  /// No description provided for @islandGlassEnableFirst.
  ///
  /// In en, this message translates to:
  /// **'Enable the glass effect first'**
  String get islandGlassEnableFirst;

  /// No description provided for @islandGlassHdrHighlight.
  ///
  /// In en, this message translates to:
  /// **'HDR highlights'**
  String get islandGlassHdrHighlight;

  /// No description provided for @islandGlassHdrHighlightSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Display highlighted edges in HDR'**
  String get islandGlassHdrHighlightSubtitle;

  /// No description provided for @islandGlassTrueRefraction.
  ///
  /// In en, this message translates to:
  /// **'Liquid glass'**
  String get islandGlassTrueRefraction;

  /// No description provided for @islandGlassTrueRefractionSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Refract surrounding screen content on Large Island and Focus Notification; higher performance cost'**
  String get islandGlassTrueRefractionSubtitle;

  /// No description provided for @islandGlassCaptureSettings.
  ///
  /// In en, this message translates to:
  /// **'Capture settings'**
  String get islandGlassCaptureSettings;

  /// No description provided for @islandGlassCaptureSettingsSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Customize liquid glass capture settings'**
  String get islandGlassCaptureSettingsSubtitle;

  /// No description provided for @islandGlassEnableLiquidFirst.
  ///
  /// In en, this message translates to:
  /// **'Enable the liquid glass effect first'**
  String get islandGlassEnableLiquidFirst;

  /// No description provided for @islandGlassCaptureFps.
  ///
  /// In en, this message translates to:
  /// **'Capture frame rate'**
  String get islandGlassCaptureFps;

  /// No description provided for @islandGlassCaptureQuality.
  ///
  /// In en, this message translates to:
  /// **'Resolution'**
  String get islandGlassCaptureQuality;

  /// No description provided for @keepIslandTitle.
  ///
  /// In en, this message translates to:
  /// **'Keep Island Visible'**
  String get keepIslandTitle;

  /// No description provided for @keepIslandSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Post a blank notification to keep the island always visible'**
  String get keepIslandSubtitle;

  /// No description provided for @keepIslandIslandConfigTitle.
  ///
  /// In en, this message translates to:
  /// **'Island configuration'**
  String get keepIslandIslandConfigTitle;

  /// No description provided for @keepIslandDisplayTimingTitle.
  ///
  /// In en, this message translates to:
  /// **'Display timing'**
  String get keepIslandDisplayTimingTitle;

  /// No description provided for @keepIslandDisplayTimingAlways.
  ///
  /// In en, this message translates to:
  /// **'Always'**
  String get keepIslandDisplayTimingAlways;

  /// No description provided for @keepIslandDisplayTimingCharging.
  ///
  /// In en, this message translates to:
  /// **'While charging'**
  String get keepIslandDisplayTimingCharging;

  /// No description provided for @keepIslandFocusConfigTitle.
  ///
  /// In en, this message translates to:
  /// **'Focus notification configuration'**
  String get keepIslandFocusConfigTitle;

  /// No description provided for @keepIslandEnableIslandTitle.
  ///
  /// In en, this message translates to:
  /// **'Enable island'**
  String get keepIslandEnableIslandTitle;

  /// No description provided for @keepIslandShowNotificationTitle.
  ///
  /// In en, this message translates to:
  /// **'Show in notification center'**
  String get keepIslandShowNotificationTitle;

  /// No description provided for @keepIslandConfigEnabled.
  ///
  /// In en, this message translates to:
  /// **'Enabled'**
  String get keepIslandConfigEnabled;

  /// No description provided for @keepIslandConfigDisabled.
  ///
  /// In en, this message translates to:
  /// **'Disabled'**
  String get keepIslandConfigDisabled;

  /// No description provided for @keepIslandAutoHideTitle.
  ///
  /// In en, this message translates to:
  /// **'Auto Hide'**
  String get keepIslandAutoHideTitle;

  /// No description provided for @keepIslandAutoHideSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Automatically hide the blank island when a notification arrives, and restore it when dismissed'**
  String get keepIslandAutoHideSubtitle;

  /// No description provided for @keepIslandHideLandscapeTitle.
  ///
  /// In en, this message translates to:
  /// **'Hide in Landscape'**
  String get keepIslandHideLandscapeTitle;

  /// No description provided for @keepIslandHideLandscapeSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Hide the keep island in landscape, then restore in portrait when no notification is active'**
  String get keepIslandHideLandscapeSubtitle;

  /// No description provided for @keepIslandHighlightColorTitle.
  ///
  /// In en, this message translates to:
  /// **'Highlight Color'**
  String get keepIslandHighlightColorTitle;

  /// No description provided for @keepIslandHighlightColorSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Customize the highlight text color for the keep island'**
  String get keepIslandHighlightColorSubtitle;

  /// No description provided for @keepIslandTextHighlightTitle.
  ///
  /// In en, this message translates to:
  /// **'Text highlight'**
  String get keepIslandTextHighlightTitle;

  /// No description provided for @keepIslandHighlightLeft.
  ///
  /// In en, this message translates to:
  /// **'Left'**
  String get keepIslandHighlightLeft;

  /// No description provided for @keepIslandHighlightRight.
  ///
  /// In en, this message translates to:
  /// **'Right'**
  String get keepIslandHighlightRight;

  /// No description provided for @keepIslandLeftContentTitle.
  ///
  /// In en, this message translates to:
  /// **'Left island content'**
  String get keepIslandLeftContentTitle;

  /// No description provided for @keepIslandRightContentTitle.
  ///
  /// In en, this message translates to:
  /// **'Right island content'**
  String get keepIslandRightContentTitle;

  /// No description provided for @keepIslandCarouselIntervalTitle.
  ///
  /// In en, this message translates to:
  /// **'Carousel interval'**
  String get keepIslandCarouselIntervalTitle;

  /// No description provided for @keepIslandCarouselIntervalSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Switch between multiple left and right contents every 1-6000 seconds'**
  String get keepIslandCarouselIntervalSubtitle;

  /// No description provided for @keepIslandAddCarouselItem.
  ///
  /// In en, this message translates to:
  /// **'Add content'**
  String get keepIslandAddCarouselItem;

  /// No description provided for @keepIslandCarouselItem.
  ///
  /// In en, this message translates to:
  /// **'Content {index}'**
  String keepIslandCarouselItem(int index);

  /// No description provided for @keepIslandFocusNotificationTitle.
  ///
  /// In en, this message translates to:
  /// **'Clickable island'**
  String get keepIslandFocusNotificationTitle;

  /// No description provided for @keepIslandFocusNotificationSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Show focus notification content and support tap to expand'**
  String get keepIslandFocusNotificationSubtitle;

  /// No description provided for @keepIslandFocusContentType.
  ///
  /// In en, this message translates to:
  /// **'Expanded content'**
  String get keepIslandFocusContentType;

  /// No description provided for @keepIslandFocusContentNotification.
  ///
  /// In en, this message translates to:
  /// **'Notification'**
  String get keepIslandFocusContentNotification;

  /// No description provided for @keepIslandFocusContentPerformance.
  ///
  /// In en, this message translates to:
  /// **'Performance panel'**
  String get keepIslandFocusContentPerformance;

  /// No description provided for @keepIslandFocusContentDevice.
  ///
  /// In en, this message translates to:
  /// **'Device panel'**
  String get keepIslandFocusContentDevice;

  /// No description provided for @keepIslandFocusContentCharging.
  ///
  /// In en, this message translates to:
  /// **'Battery panel'**
  String get keepIslandFocusContentCharging;

  /// No description provided for @keepIslandNotificationTitle.
  ///
  /// In en, this message translates to:
  /// **'Notification title'**
  String get keepIslandNotificationTitle;

  /// No description provided for @keepIslandNotificationContent.
  ///
  /// In en, this message translates to:
  /// **'Notification content'**
  String get keepIslandNotificationContent;

  /// No description provided for @keepIslandShowIslandIconTitle.
  ///
  /// In en, this message translates to:
  /// **'Show island icon'**
  String get keepIslandShowIslandIconTitle;

  /// No description provided for @keepIslandShowIslandIconSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Show an icon on the left side of the keep island'**
  String get keepIslandShowIslandIconSubtitle;

  /// No description provided for @keepIslandCustomIconTitle.
  ///
  /// In en, this message translates to:
  /// **'Custom icon'**
  String get keepIslandCustomIconTitle;

  /// No description provided for @keepIslandCustomIconSelected.
  ///
  /// In en, this message translates to:
  /// **'Set, tap to replace'**
  String get keepIslandCustomIconSelected;

  /// No description provided for @keepIslandPlaceholdersTitle.
  ///
  /// In en, this message translates to:
  /// **'Available placeholders'**
  String get keepIslandPlaceholdersTitle;

  /// No description provided for @keepIslandTimeCategory.
  ///
  /// In en, this message translates to:
  /// **'Time'**
  String get keepIslandTimeCategory;

  /// No description provided for @keepIslandWeatherCategory.
  ///
  /// In en, this message translates to:
  /// **'Weather'**
  String get keepIslandWeatherCategory;

  /// No description provided for @keepIslandDisplayCategory.
  ///
  /// In en, this message translates to:
  /// **'Display'**
  String get keepIslandDisplayCategory;

  /// No description provided for @keepIslandDeviceCategory.
  ///
  /// In en, this message translates to:
  /// **'Device'**
  String get keepIslandDeviceCategory;

  /// No description provided for @keepIslandNetworkCategory.
  ///
  /// In en, this message translates to:
  /// **'Network'**
  String get keepIslandNetworkCategory;

  /// No description provided for @keepIslandPlaceholdersDescription.
  ///
  /// In en, this message translates to:
  /// **'Enter plain text or expressions for either side, for example: Battery {batteryLevel}, CPU {cpuUsage}. Tap a tag to copy it.'**
  String keepIslandPlaceholdersDescription(
    String batteryLevel,
    String cpuUsage,
  );

  /// No description provided for @keepIslandPlaceholderCopied.
  ///
  /// In en, this message translates to:
  /// **'Copied {placeholder}'**
  String keepIslandPlaceholderCopied(String placeholder);

  /// No description provided for @keepIslandDefaultEmpty.
  ///
  /// In en, this message translates to:
  /// **'Empty by default'**
  String get keepIslandDefaultEmpty;

  /// No description provided for @keepIslandContentHint.
  ///
  /// In en, this message translates to:
  /// **'Empty by default. Enter text or {placeholder}'**
  String keepIslandContentHint(String placeholder);

  /// No description provided for @clear.
  ///
  /// In en, this message translates to:
  /// **'Clear'**
  String get clear;

  /// No description provided for @save.
  ///
  /// In en, this message translates to:
  /// **'Save'**
  String get save;

  /// No description provided for @islandOtherSection.
  ///
  /// In en, this message translates to:
  /// **'Other'**
  String get islandOtherSection;

  /// No description provided for @islandSwipeActionsTitle.
  ///
  /// In en, this message translates to:
  /// **'Swipe actions'**
  String get islandSwipeActionsTitle;

  /// No description provided for @expandedCollapseActionTitle.
  ///
  /// In en, this message translates to:
  /// **'When collapsing expanded island'**
  String get expandedCollapseActionTitle;

  /// No description provided for @bigIslandCollapseActionTitle.
  ///
  /// In en, this message translates to:
  /// **'When hiding big island'**
  String get bigIslandCollapseActionTitle;

  /// No description provided for @islandSwipeActionNone.
  ///
  /// In en, this message translates to:
  /// **'None'**
  String get islandSwipeActionNone;

  /// No description provided for @islandSwipeActionCancelNotification.
  ///
  /// In en, this message translates to:
  /// **'Clear notification'**
  String get islandSwipeActionCancelNotification;

  /// No description provided for @islandSwipeActionHideIsland.
  ///
  /// In en, this message translates to:
  /// **'Hide big island'**
  String get islandSwipeActionHideIsland;

  /// No description provided for @islandSwipeIgnoreOngoingTitle.
  ///
  /// In en, this message translates to:
  /// **'Ignore ongoing notifications'**
  String get islandSwipeIgnoreOngoingTitle;

  /// No description provided for @miscSection.
  ///
  /// In en, this message translates to:
  /// **'Misc'**
  String get miscSection;

  /// No description provided for @onboardingEntryTitle.
  ///
  /// In en, this message translates to:
  /// **'Open Onboarding'**
  String get onboardingEntryTitle;

  /// No description provided for @onboardingEntrySubtitle.
  ///
  /// In en, this message translates to:
  /// **'Review the welcome and quick start flow'**
  String get onboardingEntrySubtitle;

  /// No description provided for @onboardingAppName.
  ///
  /// In en, this message translates to:
  /// **'HyperIsland'**
  String get onboardingAppName;

  /// No description provided for @onboardingWelcomeTitle.
  ///
  /// In en, this message translates to:
  /// **'Welcome to HyperIsland'**
  String get onboardingWelcomeTitle;

  /// No description provided for @onboardingWelcomeSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Configure your island experience quickly and cleanly'**
  String get onboardingWelcomeSubtitle;

  /// No description provided for @onboardingEnvironmentTitle.
  ///
  /// In en, this message translates to:
  /// **'Environment Check'**
  String get onboardingEnvironmentTitle;

  /// No description provided for @onboardingEnvironmentSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Check module permission status'**
  String get onboardingEnvironmentSubtitle;

  /// No description provided for @onboardingFocusUnlockTitle.
  ///
  /// In en, this message translates to:
  /// **'Unlock Focus Whitelist'**
  String get onboardingFocusUnlockTitle;

  /// No description provided for @onboardingFocusUnlockSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Remove focus notification whitelist and signature verification limits so more apps can use focus notifications'**
  String get onboardingFocusUnlockSubtitle;

  /// No description provided for @onboardingFocusUnlockMethodHyperCeiler.
  ///
  /// In en, this message translates to:
  /// **'Method 1: Use HyperCeiler to unlock the whitelist and signature verification'**
  String get onboardingFocusUnlockMethodHyperCeiler;

  /// No description provided for @onboardingFocusUnlockHyperCeilerSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Enable the related unlock options in HyperCeiler. Recommended if HyperCeiler is already installed.'**
  String get onboardingFocusUnlockHyperCeilerSubtitle;

  /// No description provided for @onboardingFocusUnlockViewTutorial.
  ///
  /// In en, this message translates to:
  /// **'View Tutorial'**
  String get onboardingFocusUnlockViewTutorial;

  /// No description provided for @onboardingFocusUnlockMethodEmbedded.
  ///
  /// In en, this message translates to:
  /// **'Method 2: Built-in module'**
  String get onboardingFocusUnlockMethodEmbedded;

  /// No description provided for @onboardingFocusUnlockEmbeddedSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Enable the two built-in unlock verification switches in one tap.'**
  String get onboardingFocusUnlockEmbeddedSubtitle;

  /// No description provided for @onboardingFocusUnlockEmbeddedEnabled.
  ///
  /// In en, this message translates to:
  /// **'Both unlock verification switches are enabled. Please manually restart System UI and XMSF.'**
  String get onboardingFocusUnlockEmbeddedEnabled;

  /// No description provided for @onboardingFocusUnlockEnableButton.
  ///
  /// In en, this message translates to:
  /// **'Enable Now'**
  String get onboardingFocusUnlockEnableButton;

  /// No description provided for @onboardingFocusUnlockEnabledButton.
  ///
  /// In en, this message translates to:
  /// **'Enabled'**
  String get onboardingFocusUnlockEnabledButton;

  /// No description provided for @onboardingFocusUnlockEnabled.
  ///
  /// In en, this message translates to:
  /// **'Enabled. Please manually restart System UI and XMSF'**
  String get onboardingFocusUnlockEnabled;

  /// No description provided for @onboardingNotificationStyleTitle.
  ///
  /// In en, this message translates to:
  /// **'Choose Notification Style'**
  String get onboardingNotificationStyleTitle;

  /// No description provided for @onboardingNotificationStyleSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Pick your preferred default notification display'**
  String get onboardingNotificationStyleSubtitle;

  /// No description provided for @onboardingOriginalNotificationLabel.
  ///
  /// In en, this message translates to:
  /// **'Original notification'**
  String get onboardingOriginalNotificationLabel;

  /// No description provided for @onboardingFinishTitle.
  ///
  /// In en, this message translates to:
  /// **'All Set'**
  String get onboardingFinishTitle;

  /// No description provided for @onboardingFinishSubtitle.
  ///
  /// In en, this message translates to:
  /// **'After onboarding, you can keep adjusting details in Settings'**
  String get onboardingFinishSubtitle;

  /// No description provided for @onboardingStepLabel.
  ///
  /// In en, this message translates to:
  /// **'Step {current} / {total}'**
  String onboardingStepLabel(int current, int total);

  /// No description provided for @onboardingPrevious.
  ///
  /// In en, this message translates to:
  /// **'Previous'**
  String get onboardingPrevious;

  /// No description provided for @onboardingNext.
  ///
  /// In en, this message translates to:
  /// **'Next'**
  String get onboardingNext;

  /// No description provided for @onboardingDone.
  ///
  /// In en, this message translates to:
  /// **'Get Started'**
  String get onboardingDone;

  /// No description provided for @onboardingStatusTitle.
  ///
  /// In en, this message translates to:
  /// **'Status Check'**
  String get onboardingStatusTitle;

  /// No description provided for @onboardingRetry.
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get onboardingRetry;

  /// No description provided for @onboardingLsposedStatus.
  ///
  /// In en, this message translates to:
  /// **'LSPosed Activation'**
  String get onboardingLsposedStatus;

  /// No description provided for @onboardingRootStatus.
  ///
  /// In en, this message translates to:
  /// **'Root Access'**
  String get onboardingRootStatus;

  /// No description provided for @onboardingAppListStatus.
  ///
  /// In en, this message translates to:
  /// **'App list permission'**
  String get onboardingAppListStatus;

  /// No description provided for @onboardingProtocolStatus.
  ///
  /// In en, this message translates to:
  /// **'System Protocol Version'**
  String get onboardingProtocolStatus;

  /// No description provided for @onboardingAndroidStatus.
  ///
  /// In en, this message translates to:
  /// **'Android Version'**
  String get onboardingAndroidStatus;

  /// No description provided for @onboardingUnsupportedSystem.
  ///
  /// In en, this message translates to:
  /// **'Current system is not supported'**
  String get onboardingUnsupportedSystem;

  /// No description provided for @onboardingAndroid15Limited.
  ///
  /// In en, this message translates to:
  /// **'Android 15 support is limited'**
  String get onboardingAndroid15Limited;

  /// No description provided for @onboardingMissingPermissionTitle.
  ///
  /// In en, this message translates to:
  /// **'Required Permission Missing'**
  String get onboardingMissingPermissionTitle;

  /// No description provided for @onboardingMissingPermissionMessage.
  ///
  /// In en, this message translates to:
  /// **'The module may not work properly'**
  String get onboardingMissingPermissionMessage;

  /// No description provided for @onboardingDialogClose.
  ///
  /// In en, this message translates to:
  /// **'Close'**
  String get onboardingDialogClose;

  /// No description provided for @onboardingDialogContinue.
  ///
  /// In en, this message translates to:
  /// **'Continue'**
  String get onboardingDialogContinue;

  /// No description provided for @backupRestoreSection.
  ///
  /// In en, this message translates to:
  /// **'Backup & Restore'**
  String get backupRestoreSection;

  /// No description provided for @hookExtensionSection.
  ///
  /// In en, this message translates to:
  /// **'Hook Extension'**
  String get hookExtensionSection;

  /// No description provided for @hookScopeSettings.
  ///
  /// In en, this message translates to:
  /// **'System Settings'**
  String get hookScopeSettings;

  /// No description provided for @settingsHomeEntryTitle.
  ///
  /// In en, this message translates to:
  /// **'System Settings entry'**
  String get settingsHomeEntryTitle;

  /// No description provided for @settingsHomeEntrySubtitle.
  ///
  /// In en, this message translates to:
  /// **'Show the HyperIsland entry on the System Settings home page'**
  String get settingsHomeEntrySubtitle;

  /// No description provided for @settingsHomeEntryIconStyle.
  ///
  /// In en, this message translates to:
  /// **'Icon style'**
  String get settingsHomeEntryIconStyle;

  /// No description provided for @settingsHomeEntryIconStyleDefault.
  ///
  /// In en, this message translates to:
  /// **'Default'**
  String get settingsHomeEntryIconStyleDefault;

  /// No description provided for @settingsHomeEntryIconStyleOutline.
  ///
  /// In en, this message translates to:
  /// **'No background'**
  String get settingsHomeEntryIconStyleOutline;

  /// No description provided for @xposedScopeRequestFailed.
  ///
  /// In en, this message translates to:
  /// **'Scope request failed. Make sure the module is enabled in LSPosed'**
  String get xposedScopeRequestFailed;

  /// No description provided for @hookScopeSystemUI.
  ///
  /// In en, this message translates to:
  /// **'System UI'**
  String get hookScopeSystemUI;

  /// No description provided for @smoothIslandTitle.
  ///
  /// In en, this message translates to:
  /// **'Smooth Island'**
  String get smoothIslandTitle;

  /// No description provided for @smoothIslandSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Use a continuous-curvature capsule for island outlines. Restart the scope after disabling to fully unload the hook'**
  String get smoothIslandSubtitle;

  /// No description provided for @smoothIslandSmoothingTitle.
  ///
  /// In en, this message translates to:
  /// **'Smoothing Strength'**
  String get smoothIslandSmoothingTitle;

  /// No description provided for @bluetoothIslandStatusEnabled.
  ///
  /// In en, this message translates to:
  /// **'Enabled'**
  String get bluetoothIslandStatusEnabled;

  /// No description provided for @bluetoothIslandStatusDisabled.
  ///
  /// In en, this message translates to:
  /// **'Disabled'**
  String get bluetoothIslandStatusDisabled;

  /// No description provided for @bluetoothIslandTitle.
  ///
  /// In en, this message translates to:
  /// **'Bluetooth Island'**
  String get bluetoothIslandTitle;

  /// No description provided for @bluetoothIslandSubtitle.
  ///
  /// In en, this message translates to:
  /// **'{status} · Listen for Bluetooth device connections and disconnections, then forward the island through System UI'**
  String bluetoothIslandSubtitle(String status);

  /// No description provided for @bluetoothIslandSettingsTitle.
  ///
  /// In en, this message translates to:
  /// **'Bluetooth Island Settings'**
  String get bluetoothIslandSettingsTitle;

  /// No description provided for @bluetoothIslandEnableTitle.
  ///
  /// In en, this message translates to:
  /// **'Enable Bluetooth Island'**
  String get bluetoothIslandEnableTitle;

  /// No description provided for @bluetoothIslandEnableSubtitle.
  ///
  /// In en, this message translates to:
  /// **'After disabling, restart System UI to take effect. The Bluetooth Hook will not be registered'**
  String get bluetoothIslandEnableSubtitle;

  /// No description provided for @bluetoothIslandShowDeviceNameTitle.
  ///
  /// In en, this message translates to:
  /// **'Show Device Name'**
  String get bluetoothIslandShowDeviceNameTitle;

  /// No description provided for @bluetoothIslandShowDeviceNameSubtitle.
  ///
  /// In en, this message translates to:
  /// **'On connection, show the device name on the right first, then show the connection status afterward'**
  String get bluetoothIslandShowDeviceNameSubtitle;

  /// No description provided for @bluetoothIslandDisplayDurationTitle.
  ///
  /// In en, this message translates to:
  /// **'Display Duration'**
  String get bluetoothIslandDisplayDurationTitle;

  /// No description provided for @chargeIslandTitle.
  ///
  /// In en, this message translates to:
  /// **'Charging Island'**
  String get chargeIslandTitle;

  /// No description provided for @chargeIslandSubtitle.
  ///
  /// In en, this message translates to:
  /// **'{status} · Replace the power or battery segment in Charging Island'**
  String chargeIslandSubtitle(String status);

  /// No description provided for @chargeIslandSettingsTitle.
  ///
  /// In en, this message translates to:
  /// **'Charging Island Settings'**
  String get chargeIslandSettingsTitle;

  /// No description provided for @chargeIslandEnableTitle.
  ///
  /// In en, this message translates to:
  /// **'Enable Charging Island Hook'**
  String get chargeIslandEnableTitle;

  /// No description provided for @chargeIslandEnableSubtitle.
  ///
  /// In en, this message translates to:
  /// **'After disabling, restart System UI to take effect. The hook will be bypassed completely'**
  String get chargeIslandEnableSubtitle;

  /// No description provided for @chargeIslandLeftModeTitle.
  ///
  /// In en, this message translates to:
  /// **'Left Behavior'**
  String get chargeIslandLeftModeTitle;

  /// No description provided for @chargeIslandRightModeTitle.
  ///
  /// In en, this message translates to:
  /// **'Right Behavior'**
  String get chargeIslandRightModeTitle;

  /// No description provided for @chargeIslandModeDefault.
  ///
  /// In en, this message translates to:
  /// **'Default'**
  String get chargeIslandModeDefault;

  /// No description provided for @chargeIslandModePower.
  ///
  /// In en, this message translates to:
  /// **'Power'**
  String get chargeIslandModePower;

  /// No description provided for @chargeIslandModeVoltage.
  ///
  /// In en, this message translates to:
  /// **'Voltage'**
  String get chargeIslandModeVoltage;

  /// No description provided for @chargeIslandModeCurrent.
  ///
  /// In en, this message translates to:
  /// **'Current'**
  String get chargeIslandModeCurrent;

  /// No description provided for @chargeIslandModeLevel.
  ///
  /// In en, this message translates to:
  /// **'Battery'**
  String get chargeIslandModeLevel;

  /// No description provided for @chargeIslandModeTemperature.
  ///
  /// In en, this message translates to:
  /// **'Battery Temperature'**
  String get chargeIslandModeTemperature;

  /// No description provided for @chargeIslandDurationModeTitle.
  ///
  /// In en, this message translates to:
  /// **'Duration'**
  String get chargeIslandDurationModeTitle;

  /// No description provided for @chargeIslandDurationDefault.
  ///
  /// In en, this message translates to:
  /// **'Default'**
  String get chargeIslandDurationDefault;

  /// No description provided for @chargeIslandDurationCustom.
  ///
  /// In en, this message translates to:
  /// **'Custom'**
  String get chargeIslandDurationCustom;

  /// No description provided for @chargeIslandDurationPersistent.
  ///
  /// In en, this message translates to:
  /// **'Persistent'**
  String get chargeIslandDurationPersistent;

  /// No description provided for @chargeIslandDurationSecondsTitle.
  ///
  /// In en, this message translates to:
  /// **'Custom Duration'**
  String get chargeIslandDurationSecondsTitle;

  /// No description provided for @chargeIslandOuterGlowSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Control the outer glow effect of Charging Island'**
  String get chargeIslandOuterGlowSubtitle;

  /// No description provided for @faceUnlockIslandTitle.
  ///
  /// In en, this message translates to:
  /// **'Unlock Island'**
  String get faceUnlockIslandTitle;

  /// No description provided for @faceUnlockIslandSubtitle.
  ///
  /// In en, this message translates to:
  /// **'{status} · Adds an unlock state super island to the lock screen'**
  String faceUnlockIslandSubtitle(String status);

  /// No description provided for @faceUnlockIslandSettingsTitle.
  ///
  /// In en, this message translates to:
  /// **'Unlock Island Settings'**
  String get faceUnlockIslandSettingsTitle;

  /// No description provided for @faceUnlockIslandEnableTitle.
  ///
  /// In en, this message translates to:
  /// **'Enable Unlock Island'**
  String get faceUnlockIslandEnableTitle;

  /// No description provided for @faceUnlockIslandEnableSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Restart SystemUI required after toggling'**
  String get faceUnlockIslandEnableSubtitle;

  /// No description provided for @faceUnlockIslandFirstFloatTitle.
  ///
  /// In en, this message translates to:
  /// **'Auto Expand Super Island'**
  String get faceUnlockIslandFirstFloatTitle;

  /// No description provided for @faceUnlockIslandFirstFloatSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Expand as focus notification'**
  String get faceUnlockIslandFirstFloatSubtitle;

  /// No description provided for @faceUnlockIslandAnimationStyleTitle.
  ///
  /// In en, this message translates to:
  /// **'Animation Style'**
  String get faceUnlockIslandAnimationStyleTitle;

  /// No description provided for @faceUnlockIslandAnimationStyleSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Lock style shows a closed lock while locked, then rotates the shackle right when any unlock method succeeds'**
  String get faceUnlockIslandAnimationStyleSubtitle;

  /// No description provided for @faceUnlockIslandAnimationDefault.
  ///
  /// In en, this message translates to:
  /// **'Default'**
  String get faceUnlockIslandAnimationDefault;

  /// No description provided for @faceUnlockIslandAnimationLock.
  ///
  /// In en, this message translates to:
  /// **'Lock'**
  String get faceUnlockIslandAnimationLock;

  /// No description provided for @faceUnlockIslandKeepUntilKeyguardHiddenTitle.
  ///
  /// In en, this message translates to:
  /// **'Keep Island After Face Unlock'**
  String get faceUnlockIslandKeepUntilKeyguardHiddenTitle;

  /// No description provided for @faceUnlockIslandKeepUntilKeyguardHiddenSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Keep showing it on the lock screen after face recognition succeeds, then hide it when the desktop appears. Other unlock methods are unaffected'**
  String get faceUnlockIslandKeepUntilKeyguardHiddenSubtitle;

  /// No description provided for @hideLockscreenFaceUnlockIconTitle.
  ///
  /// In en, this message translates to:
  /// **'Disable Face Unlock Icon'**
  String get hideLockscreenFaceUnlockIconTitle;

  /// No description provided for @hideLockscreenFaceUnlockIconSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Hide the face recognition graphic on the lock screen without affecting face unlock'**
  String get hideLockscreenFaceUnlockIconSubtitle;

  /// No description provided for @outerGlowTitle.
  ///
  /// In en, this message translates to:
  /// **'Outer Glow'**
  String get outerGlowTitle;

  /// No description provided for @bluetoothIslandOuterGlowSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Control the outer glow effect of Bluetooth Island'**
  String get bluetoothIslandOuterGlowSubtitle;

  /// No description provided for @outerGlowColorTitle.
  ///
  /// In en, this message translates to:
  /// **'Outer Glow Color'**
  String get outerGlowColorTitle;

  /// No description provided for @hookScopeXMSF.
  ///
  /// In en, this message translates to:
  /// **'Xiaomi Service Framework (XMSF)'**
  String get hookScopeXMSF;

  /// No description provided for @downloadManagerSection.
  ///
  /// In en, this message translates to:
  /// **'Download Manager'**
  String get downloadManagerSection;

  /// No description provided for @themePageTitle.
  ///
  /// In en, this message translates to:
  /// **'Theme'**
  String get themePageTitle;

  /// No description provided for @themeSeedColorTitle.
  ///
  /// In en, this message translates to:
  /// **'Theme Color'**
  String get themeSeedColorTitle;

  /// No description provided for @themeSeedColorSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Customize the app accent color'**
  String get themeSeedColorSubtitle;

  /// No description provided for @presetColors.
  ///
  /// In en, this message translates to:
  /// **'Preset Colors'**
  String get presetColors;

  /// No description provided for @themeResetColor.
  ///
  /// In en, this message translates to:
  /// **'Reset to Default'**
  String get themeResetColor;

  /// No description provided for @blurBarsTitle.
  ///
  /// In en, this message translates to:
  /// **'Frosted Glass'**
  String get blurBarsTitle;

  /// No description provided for @blurBarsSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Add blur transparency effect to top and bottom bars'**
  String get blurBarsSubtitle;

  /// No description provided for @bluetoothIslandWhitelistTitle.
  ///
  /// In en, this message translates to:
  /// **'Device Whitelist'**
  String get bluetoothIslandWhitelistTitle;

  /// No description provided for @bluetoothIslandWhitelistSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Only show the island for whitelisted Bluetooth devices'**
  String get bluetoothIslandWhitelistSubtitle;

  /// No description provided for @bluetoothIslandWhitelistButton.
  ///
  /// In en, this message translates to:
  /// **'Manage Whitelist'**
  String get bluetoothIslandWhitelistButton;

  /// No description provided for @bluetoothIslandWhitelistButtonSubtitle.
  ///
  /// In en, this message translates to:
  /// **'{count} device(s) selected'**
  String bluetoothIslandWhitelistButtonSubtitle(int count);

  /// No description provided for @bluetoothIslandWhitelistDialogTitle.
  ///
  /// In en, this message translates to:
  /// **'Select Bluetooth Devices'**
  String get bluetoothIslandWhitelistDialogTitle;

  /// No description provided for @bluetoothIslandWhitelistEmpty.
  ///
  /// In en, this message translates to:
  /// **'No paired devices. Please pair a device in system Bluetooth settings first'**
  String get bluetoothIslandWhitelistEmpty;

  /// No description provided for @bluetoothIslandWhitelistAllHint.
  ///
  /// In en, this message translates to:
  /// **'When disabled, the island shows for all Bluetooth devices'**
  String get bluetoothIslandWhitelistAllHint;

  /// No description provided for @bluetoothIslandLoadDevicesFailed.
  ///
  /// In en, this message translates to:
  /// **'Failed to load Bluetooth devices'**
  String get bluetoothIslandLoadDevicesFailed;

  /// No description provided for @bluetoothIslandNeedBtPermission.
  ///
  /// In en, this message translates to:
  /// **'Bluetooth permission is required to load devices'**
  String get bluetoothIslandNeedBtPermission;

  /// No description provided for @hideBehaviorTitle.
  ///
  /// In en, this message translates to:
  /// **'Hide Behavior'**
  String get hideBehaviorTitle;

  /// No description provided for @hideBehaviorDescription.
  ///
  /// In en, this message translates to:
  /// **'Control whether system scenes are allowed to temporarily hide the island. Turning an item off blocks the matching system hide logic.'**
  String get hideBehaviorDescription;

  /// No description provided for @hideBehaviorMasterSwitch.
  ///
  /// In en, this message translates to:
  /// **'Enable hide interception injection'**
  String get hideBehaviorMasterSwitch;

  /// No description provided for @hideBehaviorMasterSwitchSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Only enables the hide behavior interception feature when turned on; completely disabled when off.'**
  String get hideBehaviorMasterSwitchSubtitle;

  /// No description provided for @hideBehaviorScreenPinning.
  ///
  /// In en, this message translates to:
  /// **'Screen pinning'**
  String get hideBehaviorScreenPinning;

  /// No description provided for @hideBehaviorScreenPinningSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Hide the island while screen pinning is active'**
  String get hideBehaviorScreenPinningSubtitle;

  /// No description provided for @hideBehaviorBouncerShowing.
  ///
  /// In en, this message translates to:
  /// **'Unlock screen'**
  String get hideBehaviorBouncerShowing;

  /// No description provided for @hideBehaviorBouncerShowingSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Hide the island while the unlock challenge is showing'**
  String get hideBehaviorBouncerShowingSubtitle;

  /// No description provided for @hideBehaviorFullscreen.
  ///
  /// In en, this message translates to:
  /// **'Fullscreen mode'**
  String get hideBehaviorFullscreen;

  /// No description provided for @hideBehaviorFullscreenSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Hide the island when the status bar disappears or immersive fullscreen is active'**
  String get hideBehaviorFullscreenSubtitle;

  /// No description provided for @hideBehaviorFullscreenLandscapeDisable.
  ///
  /// In en, this message translates to:
  /// **'Disable fullscreen hide in landscape'**
  String get hideBehaviorFullscreenLandscapeDisable;

  /// No description provided for @hideBehaviorFullscreenLandscapeDisableSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Restore system behavior and stop intercepting hide when in landscape mode'**
  String get hideBehaviorFullscreenLandscapeDisableSubtitle;

  /// No description provided for @hideBehaviorScreenLocked.
  ///
  /// In en, this message translates to:
  /// **'Lock screen'**
  String get hideBehaviorScreenLocked;

  /// No description provided for @hideBehaviorScreenLockedSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Hide the island during lock screen or screen-off flows'**
  String get hideBehaviorScreenLockedSubtitle;

  /// No description provided for @hideBehaviorNotificationCenter.
  ///
  /// In en, this message translates to:
  /// **'Notification center'**
  String get hideBehaviorNotificationCenter;

  /// No description provided for @hideBehaviorNotificationCenterSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Hide the island while the notification shade expands or transitions'**
  String get hideBehaviorNotificationCenterSubtitle;

  /// No description provided for @hideBehaviorForegroundApp.
  ///
  /// In en, this message translates to:
  /// **'Foreground app'**
  String get hideBehaviorForegroundApp;

  /// No description provided for @hideBehaviorForegroundAppSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Hide the app\'s own island while it is in the foreground'**
  String get hideBehaviorForegroundAppSubtitle;

  /// No description provided for @off.
  ///
  /// In en, this message translates to:
  /// **'Off'**
  String get off;

  /// No description provided for @islandTextSection.
  ///
  /// In en, this message translates to:
  /// **'Island Text'**
  String get islandTextSection;

  /// No description provided for @islandTextSizeTitle.
  ///
  /// In en, this message translates to:
  /// **'Island Text Size'**
  String get islandTextSizeTitle;

  /// No description provided for @islandOutlineSection.
  ///
  /// In en, this message translates to:
  /// **'Outline controls'**
  String get islandOutlineSection;

  /// No description provided for @outerGlowAppearanceSection.
  ///
  /// In en, this message translates to:
  /// **'Outer glow'**
  String get outerGlowAppearanceSection;

  /// No description provided for @outerGlowRangeTitle.
  ///
  /// In en, this message translates to:
  /// **'Glow range'**
  String get outerGlowRangeTitle;

  /// No description provided for @outerGlowSingleColorTitle.
  ///
  /// In en, this message translates to:
  /// **'Single-color glow'**
  String get outerGlowSingleColorTitle;

  /// No description provided for @outerGlowBaseColorTitle.
  ///
  /// In en, this message translates to:
  /// **'Base color'**
  String get outerGlowBaseColorTitle;

  /// No description provided for @alwaysShowIslandOutlineTitle.
  ///
  /// In en, this message translates to:
  /// **'Always show island outline'**
  String get alwaysShowIslandOutlineTitle;

  /// No description provided for @alwaysShowFocusOutlineTitle.
  ///
  /// In en, this message translates to:
  /// **'Always show Focus Notification outline'**
  String get alwaysShowFocusOutlineTitle;

  /// No description provided for @islandTextColorTitle.
  ///
  /// In en, this message translates to:
  /// **'Island Text Color'**
  String get islandTextColorTitle;

  /// No description provided for @focusNotificationTextColorTitle.
  ///
  /// In en, this message translates to:
  /// **'Focus Notification Text Color'**
  String get focusNotificationTextColorTitle;

  /// No description provided for @mediaNotificationTextColorTitle.
  ///
  /// In en, this message translates to:
  /// **'Media Notification Text Color'**
  String get mediaNotificationTextColorTitle;

  /// No description provided for @islandTextColorBlack.
  ///
  /// In en, this message translates to:
  /// **'Black'**
  String get islandTextColorBlack;

  /// No description provided for @islandTextColorFollowBackground.
  ///
  /// In en, this message translates to:
  /// **'Follow island background'**
  String get islandTextColorFollowBackground;

  /// No description provided for @islandTextColorInvertBackground.
  ///
  /// In en, this message translates to:
  /// **'Invert island background'**
  String get islandTextColorInvertBackground;

  /// No description provided for @islandTextColorFollowStatusBar.
  ///
  /// In en, this message translates to:
  /// **'Follow status bar'**
  String get islandTextColorFollowStatusBar;

  /// No description provided for @islandTextColorInvertStatusBar.
  ///
  /// In en, this message translates to:
  /// **'Invert status bar'**
  String get islandTextColorInvertStatusBar;

  /// No description provided for @islandTextColorDefault.
  ///
  /// In en, this message translates to:
  /// **'Default'**
  String get islandTextColorDefault;

  /// No description provided for @keepIslandExpandTextColorTitle.
  ///
  /// In en, this message translates to:
  /// **'Focus island text color'**
  String get keepIslandExpandTextColorTitle;

  /// No description provided for @keepIslandExpandTextColorWhite.
  ///
  /// In en, this message translates to:
  /// **'White'**
  String get keepIslandExpandTextColorWhite;

  /// No description provided for @tapToSelectImage.
  ///
  /// In en, this message translates to:
  /// **'Tap to select image or GIF'**
  String get tapToSelectImage;

  /// No description provided for @autoExpandNotification.
  ///
  /// In en, this message translates to:
  /// **'Auto expand notification'**
  String get autoExpandNotification;

  /// No description provided for @widthDpLabel.
  ///
  /// In en, this message translates to:
  /// **'{width} dp'**
  String widthDpLabel(int width);

  /// No description provided for @alwaysOnIsland.
  ///
  /// In en, this message translates to:
  /// **'Always-on Island'**
  String get alwaysOnIsland;

  /// No description provided for @referencesTitle.
  ///
  /// In en, this message translates to:
  /// **'References'**
  String get referencesTitle;

  /// No description provided for @referencesDescription.
  ///
  /// In en, this message translates to:
  /// **'During the development of HyperIsland, parts or all of the following projects were referenced or used. Thank you to these projects for their support.'**
  String get referencesDescription;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'ja', 'ru', 'tr', 'zh'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'ja':
      return AppLocalizationsJa();
    case 'ru':
      return AppLocalizationsRu();
    case 'tr':
      return AppLocalizationsTr();
    case 'zh':
      return AppLocalizationsZh();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
