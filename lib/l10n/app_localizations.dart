import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_ja.dart';
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
/// import 'l10n/app_localizations.dart';
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
    Locale('zh'),
  ];

  /// No description provided for @navHome.
  ///
  /// In zh, this message translates to:
  /// **'主页'**
  String get navHome;

  /// No description provided for @navApps.
  ///
  /// In zh, this message translates to:
  /// **'应用'**
  String get navApps;

  /// No description provided for @navSettings.
  ///
  /// In zh, this message translates to:
  /// **'设置'**
  String get navSettings;

  /// No description provided for @cancel.
  ///
  /// In zh, this message translates to:
  /// **'取消'**
  String get cancel;

  /// No description provided for @confirm.
  ///
  /// In zh, this message translates to:
  /// **'确认'**
  String get confirm;

  /// No description provided for @ok.
  ///
  /// In zh, this message translates to:
  /// **'确定'**
  String get ok;

  /// No description provided for @apply.
  ///
  /// In zh, this message translates to:
  /// **'应用'**
  String get apply;

  /// No description provided for @noChange.
  ///
  /// In zh, this message translates to:
  /// **'不更改'**
  String get noChange;

  /// No description provided for @newVersionFound.
  ///
  /// In zh, this message translates to:
  /// **'发现新版本'**
  String get newVersionFound;

  /// No description provided for @currentVersion.
  ///
  /// In zh, this message translates to:
  /// **'当前版本：{version}'**
  String currentVersion(String version);

  /// No description provided for @latestVersion.
  ///
  /// In zh, this message translates to:
  /// **'最新版本：{version}'**
  String latestVersion(String version);

  /// No description provided for @later.
  ///
  /// In zh, this message translates to:
  /// **'稍后再说'**
  String get later;

  /// No description provided for @goUpdate.
  ///
  /// In zh, this message translates to:
  /// **'前往更新'**
  String get goUpdate;

  /// No description provided for @sponsorSupport.
  ///
  /// In zh, this message translates to:
  /// **'赞助支持'**
  String get sponsorSupport;

  /// No description provided for @sponsorAuthor.
  ///
  /// In zh, this message translates to:
  /// **'赞助作者'**
  String get sponsorAuthor;

  /// No description provided for @restartScope.
  ///
  /// In zh, this message translates to:
  /// **'重启作用域'**
  String get restartScope;

  /// No description provided for @systemUI.
  ///
  /// In zh, this message translates to:
  /// **'系统界面'**
  String get systemUI;

  /// No description provided for @downloadManager.
  ///
  /// In zh, this message translates to:
  /// **'下载管理器'**
  String get downloadManager;

  /// No description provided for @notificationTest.
  ///
  /// In zh, this message translates to:
  /// **'通知测试'**
  String get notificationTest;

  /// No description provided for @sendTestNotification.
  ///
  /// In zh, this message translates to:
  /// **'发送测试通知'**
  String get sendTestNotification;

  /// No description provided for @notes.
  ///
  /// In zh, this message translates to:
  /// **'注意事项'**
  String get notes;

  /// No description provided for @detectingModuleStatus.
  ///
  /// In zh, this message translates to:
  /// **'正在检测模块状态...'**
  String get detectingModuleStatus;

  /// No description provided for @moduleStatus.
  ///
  /// In zh, this message translates to:
  /// **'模块状态'**
  String get moduleStatus;

  /// No description provided for @activated.
  ///
  /// In zh, this message translates to:
  /// **'已激活'**
  String get activated;

  /// No description provided for @notActivated.
  ///
  /// In zh, this message translates to:
  /// **'未激活'**
  String get notActivated;

  /// No description provided for @enableInLSPosed.
  ///
  /// In zh, this message translates to:
  /// **'请在 LSPosed 中启用本模块'**
  String get enableInLSPosed;

  /// No description provided for @systemNotSupported.
  ///
  /// In zh, this message translates to:
  /// **'系统不支持'**
  String get systemNotSupported;

  /// No description provided for @systemNotSupportedSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'当前系统不支持超级岛功能（协议版本 {version}，需要版本 3）'**
  String systemNotSupportedSubtitle(int version);

  /// No description provided for @restartFailed.
  ///
  /// In zh, this message translates to:
  /// **'重启失败：{message}'**
  String restartFailed(String message);

  /// No description provided for @restartRootRequired.
  ///
  /// In zh, this message translates to:
  /// **'请检查是否已给予本应用 ROOT 权限'**
  String get restartRootRequired;

  /// No description provided for @note1.
  ///
  /// In zh, this message translates to:
  /// **'1.此页面仅用于测试是否支持超级岛，并不代表实际效果'**
  String get note1;

  /// No description provided for @note2.
  ///
  /// In zh, this message translates to:
  /// **'2.请在 HyperCeiler 中关闭系统界面和小米服务框架的焦点通知白名单'**
  String get note2;

  /// No description provided for @note3.
  ///
  /// In zh, this message translates to:
  /// **'3.LSPosed 管理器中激活后，必须重启相关作用域软件'**
  String get note3;

  /// No description provided for @note4.
  ///
  /// In zh, this message translates to:
  /// **'4.支持通用适配，自行勾选合适的模板尝试'**
  String get note4;

  /// No description provided for @behaviorSection.
  ///
  /// In zh, this message translates to:
  /// **'行为'**
  String get behaviorSection;

  /// No description provided for @appearanceSection.
  ///
  /// In zh, this message translates to:
  /// **'外观'**
  String get appearanceSection;

  /// No description provided for @configSection.
  ///
  /// In zh, this message translates to:
  /// **'配置'**
  String get configSection;

  /// No description provided for @aboutSection.
  ///
  /// In zh, this message translates to:
  /// **'关于'**
  String get aboutSection;

  /// No description provided for @keepFocusNotifTitle.
  ///
  /// In zh, this message translates to:
  /// **'下载管理器暂停后保留焦点通知'**
  String get keepFocusNotifTitle;

  /// No description provided for @keepFocusNotifSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'显示一条通知，点击以继续下载，可能导致状态不同步'**
  String get keepFocusNotifSubtitle;

  /// No description provided for @checkUpdateOnLaunchTitle.
  ///
  /// In zh, this message translates to:
  /// **'启动时检查更新'**
  String get checkUpdateOnLaunchTitle;

  /// No description provided for @checkUpdateOnLaunchSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'启动应用时自动检查是否有新版本'**
  String get checkUpdateOnLaunchSubtitle;

  /// No description provided for @checkUpdate.
  ///
  /// In zh, this message translates to:
  /// **'检查更新'**
  String get checkUpdate;

  /// No description provided for @alreadyLatest.
  ///
  /// In zh, this message translates to:
  /// **'已是最新版本'**
  String get alreadyLatest;

  /// No description provided for @useAppIconTitle.
  ///
  /// In zh, this message translates to:
  /// **'使用应用图标'**
  String get useAppIconTitle;

  /// No description provided for @useAppIconSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'下载管理器通知使用应用图标'**
  String get useAppIconSubtitle;

  /// No description provided for @roundIconTitle.
  ///
  /// In zh, this message translates to:
  /// **'图标圆角'**
  String get roundIconTitle;

  /// No description provided for @roundIconSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'为通知图标添加圆角效果'**
  String get roundIconSubtitle;

  /// No description provided for @themeModeTitle.
  ///
  /// In zh, this message translates to:
  /// **'颜色模式'**
  String get themeModeTitle;

  /// No description provided for @themeModeSystem.
  ///
  /// In zh, this message translates to:
  /// **'跟随系统'**
  String get themeModeSystem;

  /// No description provided for @themeModeLight.
  ///
  /// In zh, this message translates to:
  /// **'浅色'**
  String get themeModeLight;

  /// No description provided for @themeModeDark.
  ///
  /// In zh, this message translates to:
  /// **'深色'**
  String get themeModeDark;

  /// No description provided for @languageTitle.
  ///
  /// In zh, this message translates to:
  /// **'语言'**
  String get languageTitle;

  /// No description provided for @languageAuto.
  ///
  /// In zh, this message translates to:
  /// **'跟随系统'**
  String get languageAuto;

  /// No description provided for @languageZh.
  ///
  /// In zh, this message translates to:
  /// **'中文'**
  String get languageZh;

  /// No description provided for @languageEn.
  ///
  /// In zh, this message translates to:
  /// **'English'**
  String get languageEn;

  /// No description provided for @languageJa.
  ///
  /// In zh, this message translates to:
  /// **'日本語'**
  String get languageJa;

  /// No description provided for @exportToFile.
  ///
  /// In zh, this message translates to:
  /// **'导出到文件'**
  String get exportToFile;

  /// No description provided for @exportToFileSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'将配置保存为 JSON 文件'**
  String get exportToFileSubtitle;

  /// No description provided for @exportToClipboard.
  ///
  /// In zh, this message translates to:
  /// **'导出到剪贴板'**
  String get exportToClipboard;

  /// No description provided for @exportToClipboardSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'将配置复制为 JSON 文本'**
  String get exportToClipboardSubtitle;

  /// No description provided for @importFromFile.
  ///
  /// In zh, this message translates to:
  /// **'从文件导入'**
  String get importFromFile;

  /// No description provided for @importFromFileSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'从 JSON 文件恢复配置'**
  String get importFromFileSubtitle;

  /// No description provided for @importFromClipboard.
  ///
  /// In zh, this message translates to:
  /// **'从剪贴板导入'**
  String get importFromClipboard;

  /// No description provided for @importFromClipboardSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'从剪贴板中的 JSON 文本恢复配置'**
  String get importFromClipboardSubtitle;

  /// No description provided for @qqGroup.
  ///
  /// In zh, this message translates to:
  /// **'QQ 交流群'**
  String get qqGroup;

  /// No description provided for @restartScopeApp.
  ///
  /// In zh, this message translates to:
  /// **'请重启作用域应用以使设置生效'**
  String get restartScopeApp;

  /// No description provided for @groupNumberCopied.
  ///
  /// In zh, this message translates to:
  /// **'群号已复制到剪贴板'**
  String get groupNumberCopied;

  /// No description provided for @exportedTo.
  ///
  /// In zh, this message translates to:
  /// **'已导出到：{path}'**
  String exportedTo(String path);

  /// No description provided for @exportFailed.
  ///
  /// In zh, this message translates to:
  /// **'导出失败：{error}'**
  String exportFailed(String error);

  /// No description provided for @configCopied.
  ///
  /// In zh, this message translates to:
  /// **'配置已复制到剪贴板'**
  String get configCopied;

  /// No description provided for @importSuccess.
  ///
  /// In zh, this message translates to:
  /// **'导入成功，共 {count} 项配置，请重启应用生效'**
  String importSuccess(int count);

  /// No description provided for @importFailed.
  ///
  /// In zh, this message translates to:
  /// **'导入失败：{error}'**
  String importFailed(String error);

  /// No description provided for @appAdaptation.
  ///
  /// In zh, this message translates to:
  /// **'应用适配'**
  String get appAdaptation;

  /// No description provided for @selectedAppsCount.
  ///
  /// In zh, this message translates to:
  /// **'已选 {count} 个应用'**
  String selectedAppsCount(int count);

  /// No description provided for @cancelSelection.
  ///
  /// In zh, this message translates to:
  /// **'取消选择'**
  String get cancelSelection;

  /// No description provided for @deselectAll.
  ///
  /// In zh, this message translates to:
  /// **'全不选'**
  String get deselectAll;

  /// No description provided for @selectAll.
  ///
  /// In zh, this message translates to:
  /// **'全选'**
  String get selectAll;

  /// No description provided for @batchChannelSettings.
  ///
  /// In zh, this message translates to:
  /// **'批量设置渠道配置'**
  String get batchChannelSettings;

  /// No description provided for @selectEnabledApps.
  ///
  /// In zh, this message translates to:
  /// **'选择已开启应用'**
  String get selectEnabledApps;

  /// No description provided for @batchEnable.
  ///
  /// In zh, this message translates to:
  /// **'批量开启'**
  String get batchEnable;

  /// No description provided for @batchDisable.
  ///
  /// In zh, this message translates to:
  /// **'批量关闭'**
  String get batchDisable;

  /// No description provided for @multiSelect.
  ///
  /// In zh, this message translates to:
  /// **'多选'**
  String get multiSelect;

  /// No description provided for @showSystemApps.
  ///
  /// In zh, this message translates to:
  /// **'显示系统应用'**
  String get showSystemApps;

  /// No description provided for @refreshList.
  ///
  /// In zh, this message translates to:
  /// **'刷新列表'**
  String get refreshList;

  /// No description provided for @enableAll.
  ///
  /// In zh, this message translates to:
  /// **'一键开启全部'**
  String get enableAll;

  /// No description provided for @disableAll.
  ///
  /// In zh, this message translates to:
  /// **'一键关闭全部'**
  String get disableAll;

  /// No description provided for @enabledAppsCount.
  ///
  /// In zh, this message translates to:
  /// **'已启用 {count} 个应用的超级岛'**
  String enabledAppsCount(int count);

  /// No description provided for @enabledAppsCountWithSystem.
  ///
  /// In zh, this message translates to:
  /// **'已启用 {count} 个应用的超级岛（含系统应用）'**
  String enabledAppsCountWithSystem(int count);

  /// No description provided for @searchApps.
  ///
  /// In zh, this message translates to:
  /// **'搜索应用名或包名'**
  String get searchApps;

  /// No description provided for @noAppsFound.
  ///
  /// In zh, this message translates to:
  /// **'没有找到已安装的应用\n请检查获取应用列表权限是否开启'**
  String get noAppsFound;

  /// No description provided for @noMatchingApps.
  ///
  /// In zh, this message translates to:
  /// **'没有匹配的应用'**
  String get noMatchingApps;

  /// No description provided for @applyToSelectedAppsChannels.
  ///
  /// In zh, this message translates to:
  /// **'将应用到已选 {count} 个应用的已启用渠道'**
  String applyToSelectedAppsChannels(int count);

  /// No description provided for @applyingConfig.
  ///
  /// In zh, this message translates to:
  /// **'正在应用配置…'**
  String get applyingConfig;

  /// No description provided for @progressApps.
  ///
  /// In zh, this message translates to:
  /// **'{done} / {total} 个应用'**
  String progressApps(int done, int total);

  /// No description provided for @batchApplied.
  ///
  /// In zh, this message translates to:
  /// **'已批量应用到 {count} 个应用'**
  String batchApplied(int count);

  /// No description provided for @cannotReadChannels.
  ///
  /// In zh, this message translates to:
  /// **'无法读取通知渠道'**
  String get cannotReadChannels;

  /// No description provided for @rootRequiredMessage.
  ///
  /// In zh, this message translates to:
  /// **'读取通知渠道需要 ROOT 权限。\n请确认已授予本应用 ROOT 权限后重试。'**
  String get rootRequiredMessage;

  /// No description provided for @enableAllChannels.
  ///
  /// In zh, this message translates to:
  /// **'启用全部渠道'**
  String get enableAllChannels;

  /// No description provided for @noChannelsFound.
  ///
  /// In zh, this message translates to:
  /// **'未找到通知渠道'**
  String get noChannelsFound;

  /// No description provided for @noChannelsFoundSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'该应用尚未创建通知渠道，或无法读取'**
  String get noChannelsFoundSubtitle;

  /// No description provided for @allChannelsActive.
  ///
  /// In zh, this message translates to:
  /// **'对全部 {count} 个渠道生效'**
  String allChannelsActive(int count);

  /// No description provided for @selectedChannels.
  ///
  /// In zh, this message translates to:
  /// **'已选 {selected} / {total} 个渠道'**
  String selectedChannels(int selected, int total);

  /// No description provided for @allChannelsDisabled.
  ///
  /// In zh, this message translates to:
  /// **'全部 {count} 个渠道（已停用）'**
  String allChannelsDisabled(int count);

  /// No description provided for @appDisabledBanner.
  ///
  /// In zh, this message translates to:
  /// **'应用总开关已关闭，以下渠道设置均不生效'**
  String get appDisabledBanner;

  /// No description provided for @channelImportance.
  ///
  /// In zh, this message translates to:
  /// **'重要性：{importance}  ·  {id}'**
  String channelImportance(String importance, String id);

  /// No description provided for @channelSettings.
  ///
  /// In zh, this message translates to:
  /// **'渠道设置'**
  String get channelSettings;

  /// No description provided for @importanceNone.
  ///
  /// In zh, this message translates to:
  /// **'无'**
  String get importanceNone;

  /// No description provided for @importanceMin.
  ///
  /// In zh, this message translates to:
  /// **'极低'**
  String get importanceMin;

  /// No description provided for @importanceLow.
  ///
  /// In zh, this message translates to:
  /// **'低'**
  String get importanceLow;

  /// No description provided for @importanceDefault.
  ///
  /// In zh, this message translates to:
  /// **'默认'**
  String get importanceDefault;

  /// No description provided for @importanceHigh.
  ///
  /// In zh, this message translates to:
  /// **'高'**
  String get importanceHigh;

  /// No description provided for @importanceUnknown.
  ///
  /// In zh, this message translates to:
  /// **'未知'**
  String get importanceUnknown;

  /// No description provided for @applyToEnabledChannels.
  ///
  /// In zh, this message translates to:
  /// **'将应用到已启用的 {count} 个渠道'**
  String applyToEnabledChannels(int count);

  /// No description provided for @applyToAllChannels.
  ///
  /// In zh, this message translates to:
  /// **'将应用到全部 {count} 个渠道'**
  String applyToAllChannels(int count);

  /// No description provided for @template.
  ///
  /// In zh, this message translates to:
  /// **'模板'**
  String get template;

  /// No description provided for @islandIcon.
  ///
  /// In zh, this message translates to:
  /// **'超级岛图标'**
  String get islandIcon;

  /// No description provided for @focusIconLabel.
  ///
  /// In zh, this message translates to:
  /// **'焦点图标'**
  String get focusIconLabel;

  /// No description provided for @focusNotificationLabel.
  ///
  /// In zh, this message translates to:
  /// **'焦点通知'**
  String get focusNotificationLabel;

  /// No description provided for @firstFloatLabel.
  ///
  /// In zh, this message translates to:
  /// **'初次展开'**
  String get firstFloatLabel;

  /// No description provided for @updateFloatLabel.
  ///
  /// In zh, this message translates to:
  /// **'更新展开'**
  String get updateFloatLabel;

  /// No description provided for @autoDisappear.
  ///
  /// In zh, this message translates to:
  /// **'自动消失'**
  String get autoDisappear;

  /// No description provided for @seconds.
  ///
  /// In zh, this message translates to:
  /// **'秒'**
  String get seconds;

  /// No description provided for @onlyEnabledChannels.
  ///
  /// In zh, this message translates to:
  /// **'仅应用到已启用渠道'**
  String get onlyEnabledChannels;

  /// No description provided for @enabledChannelsCount.
  ///
  /// In zh, this message translates to:
  /// **'已启用 {enabled} / {total} 个渠道'**
  String enabledChannelsCount(int enabled, int total);

  /// No description provided for @iconModeAuto.
  ///
  /// In zh, this message translates to:
  /// **'自动'**
  String get iconModeAuto;

  /// No description provided for @iconModeNotifSmall.
  ///
  /// In zh, this message translates to:
  /// **'通知小图标'**
  String get iconModeNotifSmall;

  /// No description provided for @iconModeNotifLarge.
  ///
  /// In zh, this message translates to:
  /// **'通知大图标'**
  String get iconModeNotifLarge;

  /// No description provided for @iconModeAppIcon.
  ///
  /// In zh, this message translates to:
  /// **'应用图标'**
  String get iconModeAppIcon;

  /// No description provided for @optDefault.
  ///
  /// In zh, this message translates to:
  /// **'默认'**
  String get optDefault;

  /// No description provided for @optOn.
  ///
  /// In zh, this message translates to:
  /// **'开启'**
  String get optOn;

  /// No description provided for @optOff.
  ///
  /// In zh, this message translates to:
  /// **'关闭'**
  String get optOff;

  /// No description provided for @errorInvalidFormat.
  ///
  /// In zh, this message translates to:
  /// **'配置格式无效'**
  String get errorInvalidFormat;

  /// No description provided for @errorNoStorageDir.
  ///
  /// In zh, this message translates to:
  /// **'无法获取存储目录'**
  String get errorNoStorageDir;

  /// No description provided for @errorNoFileSelected.
  ///
  /// In zh, this message translates to:
  /// **'未选择文件'**
  String get errorNoFileSelected;

  /// No description provided for @errorNoFilePath.
  ///
  /// In zh, this message translates to:
  /// **'无法获取文件路径'**
  String get errorNoFilePath;

  /// No description provided for @errorEmptyClipboard.
  ///
  /// In zh, this message translates to:
  /// **'剪贴板为空'**
  String get errorEmptyClipboard;

  /// No description provided for @navBlacklist.
  ///
  /// In zh, this message translates to:
  /// **'通知黑名单'**
  String get navBlacklist;

  /// No description provided for @navBlacklistSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'启动黑名单应用时，停用焦点通知的自动展开功能'**
  String get navBlacklistSubtitle;

  /// No description provided for @presetGamesTitle.
  ///
  /// In zh, this message translates to:
  /// **'一键过滤热门游戏'**
  String get presetGamesTitle;

  /// No description provided for @presetGamesSuccess.
  ///
  /// In zh, this message translates to:
  /// **'已从模板中添加 {count} 款已安装游戏至黑名单'**
  String presetGamesSuccess(int count);

  /// No description provided for @blacklistedAppsCount.
  ///
  /// In zh, this message translates to:
  /// **'已拦截 {count} 个应用的焦点通知'**
  String blacklistedAppsCount(int count);

  /// No description provided for @blacklistedAppsCountWithSystem.
  ///
  /// In zh, this message translates to:
  /// **'已拦截 {count} 个应用的焦点通知（含系统应用）'**
  String blacklistedAppsCountWithSystem(int count);
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
      <String>['en', 'ja', 'zh'].contains(locale.languageCode);

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
