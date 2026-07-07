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
  /// In zh, this message translates to:
  /// **'主页'**
  String get navHome;

  /// No description provided for @navIsland.
  ///
  /// In zh, this message translates to:
  /// **'岛'**
  String get navIsland;

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

  /// No description provided for @donorList.
  ///
  /// In zh, this message translates to:
  /// **'捐赠名单'**
  String get donorList;

  /// No description provided for @documentation.
  ///
  /// In zh, this message translates to:
  /// **'文档'**
  String get documentation;

  /// No description provided for @versionUpdatedTitle.
  ///
  /// In zh, this message translates to:
  /// **'已更新至 {version}'**
  String versionUpdatedTitle(String version);

  /// No description provided for @versionUpdatedContent.
  ///
  /// In zh, this message translates to:
  /// **'更新后请重启作用域'**
  String get versionUpdatedContent;

  /// No description provided for @versionUpdatedChangelog.
  ///
  /// In zh, this message translates to:
  /// **'更新日志：点击查看'**
  String get versionUpdatedChangelog;

  /// No description provided for @versionUpdatedStarHint.
  ///
  /// In zh, this message translates to:
  /// **'如果觉得软件好用请帮忙点一个免费的Star'**
  String get versionUpdatedStarHint;

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

  /// No description provided for @xmsf.
  ///
  /// In zh, this message translates to:
  /// **'小米服务框架'**
  String get xmsf;

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

  /// No description provided for @customTestNotification.
  ///
  /// In zh, this message translates to:
  /// **'自定义测试通知'**
  String get customTestNotification;

  /// No description provided for @customTestTitle.
  ///
  /// In zh, this message translates to:
  /// **'标题'**
  String get customTestTitle;

  /// No description provided for @customTestTitleHint.
  ///
  /// In zh, this message translates to:
  /// **'留空使用默认标题'**
  String get customTestTitleHint;

  /// No description provided for @customTestContent.
  ///
  /// In zh, this message translates to:
  /// **'内容'**
  String get customTestContent;

  /// No description provided for @customTestContentHint.
  ///
  /// In zh, this message translates to:
  /// **'留空使用默认内容'**
  String get customTestContentHint;

  /// No description provided for @clearPreviousNotification.
  ///
  /// In zh, this message translates to:
  /// **'清除之前通知'**
  String get clearPreviousNotification;

  /// No description provided for @clearPreviousNotificationSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'发送前先取消已有的超级岛通知'**
  String get clearPreviousNotificationSubtitle;

  /// No description provided for @enableFloatNotificationSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'收到通知后自动展开为焦点通知'**
  String get enableFloatNotificationSubtitle;

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

  /// No description provided for @enableSystemUiScopeInLSPosed.
  ///
  /// In zh, this message translates to:
  /// **'请在 LSPosed 作用域勾选系统界面'**
  String get enableSystemUiScopeInLSPosed;

  /// No description provided for @lsposedApiVersion.
  ///
  /// In zh, this message translates to:
  /// **'LSPosed API: {version}'**
  String lsposedApiVersion(int version);

  /// No description provided for @updateLSPosedRequired.
  ///
  /// In zh, this message translates to:
  /// **'请更新 LSPosed 版本'**
  String get updateLSPosedRequired;

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
  /// **'1.使用前务必查看软件右上角的使用教程'**
  String get note1;

  /// No description provided for @note2.
  ///
  /// In zh, this message translates to:
  /// **'2.大部分配置支持热重载，如遇异常请重启作用域'**
  String get note2;

  /// No description provided for @note3.
  ///
  /// In zh, this message translates to:
  /// **'3.LSPosed 管理器中激活后，必须重启相关作用域软件'**
  String get note3;

  /// No description provided for @note4.
  ///
  /// In zh, this message translates to:
  /// **'4.此页面仅用于测试是否支持超级岛及光效，并不代表实际效果'**
  String get note4;

  /// No description provided for @note5.
  ///
  /// In zh, this message translates to:
  /// **'5.下载上岛请手动启用“下载管理程序”，推荐《下载》模板'**
  String get note5;

  /// No description provided for @behaviorSection.
  ///
  /// In zh, this message translates to:
  /// **'行为'**
  String get behaviorSection;

  /// No description provided for @defaultConfigSection.
  ///
  /// In zh, this message translates to:
  /// **'默认配置'**
  String get defaultConfigSection;

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

  /// No description provided for @unlockAllFocusTitle.
  ///
  /// In zh, this message translates to:
  /// **'移除焦点通知白名单'**
  String get unlockAllFocusTitle;

  /// No description provided for @unlockAllFocusSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'允许所有应用发送焦点通知，无需系统授权'**
  String get unlockAllFocusSubtitle;

  /// No description provided for @unlockFocusAuthTitle.
  ///
  /// In zh, this message translates to:
  /// **'移除焦点通知签名验证'**
  String get unlockFocusAuthTitle;

  /// No description provided for @unlockFocusAuthSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'XMSF（小米服务框架）去除校验'**
  String get unlockFocusAuthSubtitle;

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

  /// No description provided for @debugLogTitle.
  ///
  /// In zh, this message translates to:
  /// **'显示调试日志'**
  String get debugLogTitle;

  /// No description provided for @debugLogSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'开启后输出 Hook 调试日志，关闭时仅保留警告和错误日志'**
  String get debugLogSubtitle;

  /// No description provided for @showWelcomeTitle.
  ///
  /// In zh, this message translates to:
  /// **'显示启动欢迎语'**
  String get showWelcomeTitle;

  /// No description provided for @showWelcomeSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'应用启动时在超级岛显示欢迎信息'**
  String get showWelcomeSubtitle;

  /// No description provided for @openOnboardingTitle.
  ///
  /// In zh, this message translates to:
  /// **'打开初始引导'**
  String get openOnboardingTitle;

  /// No description provided for @openOnboardingSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'重新查看欢迎与快速上手流程'**
  String get openOnboardingSubtitle;

  /// No description provided for @interactionHapticsTitle.
  ///
  /// In zh, this message translates to:
  /// **'交互触感'**
  String get interactionHapticsTitle;

  /// No description provided for @interactionHapticsSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'为开关、滑块和按钮启用 Hyper 定制震感反馈'**
  String get interactionHapticsSubtitle;

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

  /// No description provided for @marqueeChannelTitle.
  ///
  /// In zh, this message translates to:
  /// **'消息滚动'**
  String get marqueeChannelTitle;

  /// No description provided for @marqueeAutoHideTitle.
  ///
  /// In zh, this message translates to:
  /// **'滚动后隐藏岛'**
  String get marqueeAutoHideTitle;

  /// No description provided for @marqueeAutoHideSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'消息滚动达到指定次数后隐藏当前岛'**
  String get marqueeAutoHideSubtitle;

  /// No description provided for @marqueeAutoHideOnce.
  ///
  /// In zh, this message translates to:
  /// **'滚动1次'**
  String get marqueeAutoHideOnce;

  /// No description provided for @marqueeAutoHideTwice.
  ///
  /// In zh, this message translates to:
  /// **'滚动2次'**
  String get marqueeAutoHideTwice;

  /// No description provided for @marqueeSpeedTitle.
  ///
  /// In zh, this message translates to:
  /// **'滚动速度'**
  String get marqueeSpeedTitle;

  /// No description provided for @marqueeSpeedLabel.
  ///
  /// In zh, this message translates to:
  /// **'{speed} 像素/秒'**
  String marqueeSpeedLabel(int speed);

  /// No description provided for @bigIslandMaxWidthTitle.
  ///
  /// In zh, this message translates to:
  /// **'最大宽度'**
  String get bigIslandMaxWidthTitle;

  /// No description provided for @bigIslandMinWidthTitle.
  ///
  /// In zh, this message translates to:
  /// **'最小宽度'**
  String get bigIslandMinWidthTitle;

  /// No description provided for @testNotifTooltip.
  ///
  /// In zh, this message translates to:
  /// **'发送测试通知'**
  String get testNotifTooltip;

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

  /// No description provided for @languageRu.
  ///
  /// In zh, this message translates to:
  /// **'Русский'**
  String get languageRu;

  /// No description provided for @languageTr.
  ///
  /// In zh, this message translates to:
  /// **'Türkçe'**
  String get languageTr;

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

  /// No description provided for @exportConfig.
  ///
  /// In zh, this message translates to:
  /// **'导出配置'**
  String get exportConfig;

  /// No description provided for @exportConfigSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'选择导出到文件或剪贴板'**
  String get exportConfigSubtitle;

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

  /// No description provided for @importConfig.
  ///
  /// In zh, this message translates to:
  /// **'导入配置'**
  String get importConfig;

  /// No description provided for @importConfigSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'选择从文件或剪贴板导入'**
  String get importConfigSubtitle;

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

  /// No description provided for @toastAdaptation.
  ///
  /// In zh, this message translates to:
  /// **'Toast 适配'**
  String get toastAdaptation;

  /// No description provided for @adaptationModeNotification.
  ///
  /// In zh, this message translates to:
  /// **'通知'**
  String get adaptationModeNotification;

  /// No description provided for @adaptationModeToast.
  ///
  /// In zh, this message translates to:
  /// **'Toast'**
  String get adaptationModeToast;

  /// No description provided for @toastEnabledAppsCount.
  ///
  /// In zh, this message translates to:
  /// **'已启用 {count} 个应用的 Toast 拦截'**
  String toastEnabledAppsCount(Object count);

  /// No description provided for @toastEnabledAppsCountWithSystem.
  ///
  /// In zh, this message translates to:
  /// **'已启用 {count} 个应用的 Toast 拦截（含系统应用）'**
  String toastEnabledAppsCountWithSystem(Object count);

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

  /// No description provided for @toastForwardTitle.
  ///
  /// In zh, this message translates to:
  /// **'转发标准 Toast'**
  String get toastForwardTitle;

  /// No description provided for @toastForwardSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'将此应用的标准 Toast 文本转为 HyperIsland 焦点通知与超级岛代发'**
  String get toastForwardSubtitle;

  /// No description provided for @toastBlockOriginalTitle.
  ///
  /// In zh, this message translates to:
  /// **'拦截原始 Toast'**
  String get toastBlockOriginalTitle;

  /// No description provided for @toastBlockOriginalSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'转发后同时拦截此应用原始标准 Toast 弹窗'**
  String get toastBlockOriginalSubtitle;

  /// No description provided for @toastShowNotificationTitle.
  ///
  /// In zh, this message translates to:
  /// **'显示为通知'**
  String get toastShowNotificationTitle;

  /// No description provided for @toastShowNotificationSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'开启后此转发内容会在通知中心保留为可见通知'**
  String get toastShowNotificationSubtitle;

  /// No description provided for @toastShowIslandIconTitle.
  ///
  /// In zh, this message translates to:
  /// **'显示超级岛图标'**
  String get toastShowIslandIconTitle;

  /// No description provided for @toastShowIslandIconSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'控制转发 Toast 时大岛左侧是否显示图标'**
  String get toastShowIslandIconSubtitle;

  /// No description provided for @toastStandardOnlyHint.
  ///
  /// In zh, this message translates to:
  /// **'仅处理标准文本 Toast，自定义 Toast 视图将被忽略。'**
  String get toastStandardOnlyHint;

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

  /// No description provided for @templateDownloadName.
  ///
  /// In zh, this message translates to:
  /// **'下载'**
  String get templateDownloadName;

  /// No description provided for @templateNotificationIslandName.
  ///
  /// In zh, this message translates to:
  /// **'通知超级岛'**
  String get templateNotificationIslandName;

  /// No description provided for @templateNotificationIslandLiteName.
  ///
  /// In zh, this message translates to:
  /// **'通知超级岛 | 精简'**
  String get templateNotificationIslandLiteName;

  /// No description provided for @templateDownloadLiteName.
  ///
  /// In zh, this message translates to:
  /// **'下载|Lite'**
  String get templateDownloadLiteName;

  /// No description provided for @islandSection.
  ///
  /// In zh, this message translates to:
  /// **'岛'**
  String get islandSection;

  /// No description provided for @islandEnabledLabel.
  ///
  /// In zh, this message translates to:
  /// **'启用岛'**
  String get islandEnabledLabel;

  /// No description provided for @template.
  ///
  /// In zh, this message translates to:
  /// **'模板'**
  String get template;

  /// No description provided for @rendererLabel.
  ///
  /// In zh, this message translates to:
  /// **'样式'**
  String get rendererLabel;

  /// No description provided for @rendererImageTextWithButtons4Name.
  ///
  /// In zh, this message translates to:
  /// **'新图文组件 + 底部文本按钮'**
  String get rendererImageTextWithButtons4Name;

  /// No description provided for @rendererCoverInfoName.
  ///
  /// In zh, this message translates to:
  /// **'封面组件 + 自动换行'**
  String get rendererCoverInfoName;

  /// No description provided for @rendererImageTextWithRightTextButtonName.
  ///
  /// In zh, this message translates to:
  /// **'新图文组件 + 右侧文本按钮'**
  String get rendererImageTextWithRightTextButtonName;

  /// No description provided for @rendererImageTextWithProgressName.
  ///
  /// In zh, this message translates to:
  /// **'IM图文组件 + 进度条组件'**
  String get rendererImageTextWithProgressName;

  /// No description provided for @islandIcon.
  ///
  /// In zh, this message translates to:
  /// **'超级岛图标'**
  String get islandIcon;

  /// No description provided for @islandIconLabel.
  ///
  /// In zh, this message translates to:
  /// **'大岛图标'**
  String get islandIconLabel;

  /// No description provided for @islandIconLabelSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'开启后显示超级岛的大图标（小岛不受影响）'**
  String get islandIconLabelSubtitle;

  /// No description provided for @focusIconLabel.
  ///
  /// In zh, this message translates to:
  /// **'焦点图标'**
  String get focusIconLabel;

  /// No description provided for @focusExpressionCustomizationSection.
  ///
  /// In zh, this message translates to:
  /// **'焦点高级自定义'**
  String get focusExpressionCustomizationSection;

  /// No description provided for @islandExpressionCustomizationSection.
  ///
  /// In zh, this message translates to:
  /// **'超级岛高级自定义'**
  String get islandExpressionCustomizationSection;

  /// No description provided for @aodSection.
  ///
  /// In zh, this message translates to:
  /// **'息屏显示'**
  String get aodSection;

  /// No description provided for @expandCustomization.
  ///
  /// In zh, this message translates to:
  /// **'展开'**
  String get expandCustomization;

  /// No description provided for @collapseCustomization.
  ///
  /// In zh, this message translates to:
  /// **'收起'**
  String get collapseCustomization;

  /// No description provided for @availablePlaceholdersLabel.
  ///
  /// In zh, this message translates to:
  /// **'可用占位符(点击复制)'**
  String get availablePlaceholdersLabel;

  /// No description provided for @expressionFunctionsLabel.
  ///
  /// In zh, this message translates to:
  /// **'表达式函数'**
  String get expressionFunctionsLabel;

  /// No description provided for @focusTitleExprLabel.
  ///
  /// In zh, this message translates to:
  /// **'焦点标题表达式'**
  String get focusTitleExprLabel;

  /// No description provided for @focusContentExprLabel.
  ///
  /// In zh, this message translates to:
  /// **'焦点正文表达式'**
  String get focusContentExprLabel;

  /// No description provided for @focusIconSourceLabel.
  ///
  /// In zh, this message translates to:
  /// **'焦点图标来源'**
  String get focusIconSourceLabel;

  /// No description provided for @focusPicProfileSourceLabel.
  ///
  /// In zh, this message translates to:
  /// **'头像图标来源'**
  String get focusPicProfileSourceLabel;

  /// No description provided for @focusAppIconPkgLabel.
  ///
  /// In zh, this message translates to:
  /// **'应用图标包名'**
  String get focusAppIconPkgLabel;

  /// No description provided for @focusSecondaryIconSourceLabel.
  ///
  /// In zh, this message translates to:
  /// **'副图标来源'**
  String get focusSecondaryIconSourceLabel;

  /// No description provided for @chatTitleColorLabel.
  ///
  /// In zh, this message translates to:
  /// **'聊天标题颜色'**
  String get chatTitleColorLabel;

  /// No description provided for @chatTitleColorDarkLabel.
  ///
  /// In zh, this message translates to:
  /// **'聊天标题暗色'**
  String get chatTitleColorDarkLabel;

  /// No description provided for @chatContentColorLabel.
  ///
  /// In zh, this message translates to:
  /// **'聊天正文颜色'**
  String get chatContentColorLabel;

  /// No description provided for @chatContentColorDarkLabel.
  ///
  /// In zh, this message translates to:
  /// **'聊天正文暗色'**
  String get chatContentColorDarkLabel;

  /// No description provided for @progressColorLabel.
  ///
  /// In zh, this message translates to:
  /// **'进度条颜色'**
  String get progressColorLabel;

  /// No description provided for @progressBarColorLabel.
  ///
  /// In zh, this message translates to:
  /// **'进度条颜色'**
  String get progressBarColorLabel;

  /// No description provided for @progressBarColorEndLabel.
  ///
  /// In zh, this message translates to:
  /// **'进度条结束颜色'**
  String get progressBarColorEndLabel;

  /// No description provided for @placeholderTitle.
  ///
  /// In zh, this message translates to:
  /// **'通知标题'**
  String get placeholderTitle;

  /// No description provided for @placeholderSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'通知正文'**
  String get placeholderSubtitle;

  /// No description provided for @placeholderSubtitleOrTitle.
  ///
  /// In zh, this message translates to:
  /// **'正文（空则标题）'**
  String get placeholderSubtitleOrTitle;

  /// No description provided for @placeholderPkg.
  ///
  /// In zh, this message translates to:
  /// **'包名'**
  String get placeholderPkg;

  /// No description provided for @placeholderChannelId.
  ///
  /// In zh, this message translates to:
  /// **'渠道 ID'**
  String get placeholderChannelId;

  /// No description provided for @placeholderProgress.
  ///
  /// In zh, this message translates to:
  /// **'通知进度'**
  String get placeholderProgress;

  /// No description provided for @placeholderStateLabel.
  ///
  /// In zh, this message translates to:
  /// **'状态文本'**
  String get placeholderStateLabel;

  /// No description provided for @placeholderProgressText.
  ///
  /// In zh, this message translates to:
  /// **'进度文本'**
  String get placeholderProgressText;

  /// No description provided for @placeholderAiLeft.
  ///
  /// In zh, this message translates to:
  /// **'AI 左侧文本'**
  String get placeholderAiLeft;

  /// No description provided for @placeholderAiRight.
  ///
  /// In zh, this message translates to:
  /// **'AI 右侧文本'**
  String get placeholderAiRight;

  /// No description provided for @placeholderRawTitle.
  ///
  /// In zh, this message translates to:
  /// **'原始标题'**
  String get placeholderRawTitle;

  /// No description provided for @placeholderRawSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'原始正文'**
  String get placeholderRawSubtitle;

  /// No description provided for @placeholderRawSubtitleOrTitle.
  ///
  /// In zh, this message translates to:
  /// **'原始正文（空则标题）'**
  String get placeholderRawSubtitleOrTitle;

  /// No description provided for @islandLeftExprLabel.
  ///
  /// In zh, this message translates to:
  /// **'超级岛左侧表达式'**
  String get islandLeftExprLabel;

  /// No description provided for @islandRightExprLabel.
  ///
  /// In zh, this message translates to:
  /// **'超级岛右侧表达式'**
  String get islandRightExprLabel;

  /// No description provided for @aodTextSwitchLabel.
  ///
  /// In zh, this message translates to:
  /// **'AOD文本开关'**
  String get aodTextSwitchLabel;

  /// No description provided for @aodTextExprLabel.
  ///
  /// In zh, this message translates to:
  /// **'AOD文本表达式'**
  String get aodTextExprLabel;

  /// No description provided for @aodIconSourceLabel.
  ///
  /// In zh, this message translates to:
  /// **'AOD图标来源'**
  String get aodIconSourceLabel;

  /// No description provided for @focusNotificationLabel.
  ///
  /// In zh, this message translates to:
  /// **'焦点通知'**
  String get focusNotificationLabel;

  /// No description provided for @hideNotificationLabel.
  ///
  /// In zh, this message translates to:
  /// **'隐藏通知'**
  String get hideNotificationLabel;

  /// No description provided for @hideNotificationLabelSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'开启后仅显示超级岛，不显示通知栏焦点通知'**
  String get hideNotificationLabelSubtitle;

  /// No description provided for @preserveStatusBarSmallIconLabel.
  ///
  /// In zh, this message translates to:
  /// **'状态栏图标'**
  String get preserveStatusBarSmallIconLabel;

  /// No description provided for @restoreLockscreenTitle.
  ///
  /// In zh, this message translates to:
  /// **'锁屏通知复原'**
  String get restoreLockscreenTitle;

  /// No description provided for @restoreLockscreenSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'锁屏时跳过焦点通知处理，保持原始通知隐私行为'**
  String get restoreLockscreenSubtitle;

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

  /// No description provided for @highlightColorLabel.
  ///
  /// In zh, this message translates to:
  /// **'高亮颜色'**
  String get highlightColorLabel;

  /// No description provided for @dynamicHighlightColorLabel.
  ///
  /// In zh, this message translates to:
  /// **'高亮动态取色'**
  String get dynamicHighlightColorLabel;

  /// No description provided for @dynamicHighlightColorLabelSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'开启后默认使用图标自动取色'**
  String get dynamicHighlightColorLabelSubtitle;

  /// No description provided for @followDynamicColorLabel.
  ///
  /// In zh, this message translates to:
  /// **'跟随动态取色'**
  String get followDynamicColorLabel;

  /// No description provided for @dynamicHighlightModeDark.
  ///
  /// In zh, this message translates to:
  /// **'暗'**
  String get dynamicHighlightModeDark;

  /// No description provided for @dynamicHighlightModeDarker.
  ///
  /// In zh, this message translates to:
  /// **'更暗'**
  String get dynamicHighlightModeDarker;

  /// No description provided for @outerGlowLabel.
  ///
  /// In zh, this message translates to:
  /// **'外圈光效'**
  String get outerGlowLabel;

  /// No description provided for @forceOuterGlowLabel.
  ///
  /// In zh, this message translates to:
  /// **'全局启用'**
  String get forceOuterGlowLabel;

  /// No description provided for @forceFocusOuterGlowSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'开启后未匹配到的焦点通知强制启用光效'**
  String get forceFocusOuterGlowSubtitle;

  /// No description provided for @forceIslandOuterGlowSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'开启后未匹配到的岛强制启用光效'**
  String get forceIslandOuterGlowSubtitle;

  /// No description provided for @outEffectColorLabel.
  ///
  /// In zh, this message translates to:
  /// **'外圈光效颜色'**
  String get outEffectColorLabel;

  /// No description provided for @highlightColorHint.
  ///
  /// In zh, this message translates to:
  /// **'#RRGGBB 格式，留空使用默认'**
  String get highlightColorHint;

  /// No description provided for @actionBgColorLabel.
  ///
  /// In zh, this message translates to:
  /// **'按钮背景色'**
  String get actionBgColorLabel;

  /// No description provided for @actionBgColorDarkLabel.
  ///
  /// In zh, this message translates to:
  /// **'按钮背景色（暗色）'**
  String get actionBgColorDarkLabel;

  /// No description provided for @actionTitleColorLabel.
  ///
  /// In zh, this message translates to:
  /// **'按钮文字颜色'**
  String get actionTitleColorLabel;

  /// No description provided for @actionTitleColorDarkLabel.
  ///
  /// In zh, this message translates to:
  /// **'按钮文字颜色（暗色）'**
  String get actionTitleColorDarkLabel;

  /// No description provided for @action1BgColorLabel.
  ///
  /// In zh, this message translates to:
  /// **'按钮1背景色'**
  String get action1BgColorLabel;

  /// No description provided for @action1BgColorDarkLabel.
  ///
  /// In zh, this message translates to:
  /// **'按钮1背景色（暗色）'**
  String get action1BgColorDarkLabel;

  /// No description provided for @action1TitleColorLabel.
  ///
  /// In zh, this message translates to:
  /// **'按钮1文字颜色'**
  String get action1TitleColorLabel;

  /// No description provided for @action1TitleColorDarkLabel.
  ///
  /// In zh, this message translates to:
  /// **'按钮1文字颜色（暗色）'**
  String get action1TitleColorDarkLabel;

  /// No description provided for @action2BgColorLabel.
  ///
  /// In zh, this message translates to:
  /// **'按钮2背景色'**
  String get action2BgColorLabel;

  /// No description provided for @action2BgColorDarkLabel.
  ///
  /// In zh, this message translates to:
  /// **'按钮2背景色（暗色）'**
  String get action2BgColorDarkLabel;

  /// No description provided for @action2TitleColorLabel.
  ///
  /// In zh, this message translates to:
  /// **'按钮2文字颜色'**
  String get action2TitleColorLabel;

  /// No description provided for @action2TitleColorDarkLabel.
  ///
  /// In zh, this message translates to:
  /// **'按钮2文字颜色（暗色）'**
  String get action2TitleColorDarkLabel;

  /// No description provided for @textHighlightLabel.
  ///
  /// In zh, this message translates to:
  /// **'文本高亮'**
  String get textHighlightLabel;

  /// No description provided for @narrowFontLabel.
  ///
  /// In zh, this message translates to:
  /// **'窄字体'**
  String get narrowFontLabel;

  /// No description provided for @showLeftHighlightLabel.
  ///
  /// In zh, this message translates to:
  /// **'左侧文本高亮'**
  String get showLeftHighlightLabel;

  /// No description provided for @showRightHighlightLabel.
  ///
  /// In zh, this message translates to:
  /// **'右侧文本高亮'**
  String get showRightHighlightLabel;

  /// No description provided for @showLeftHighlightShort.
  ///
  /// In zh, this message translates to:
  /// **'左侧'**
  String get showLeftHighlightShort;

  /// No description provided for @showRightHighlightShort.
  ///
  /// In zh, this message translates to:
  /// **'右侧'**
  String get showRightHighlightShort;

  /// No description provided for @colorHue.
  ///
  /// In zh, this message translates to:
  /// **'色相'**
  String get colorHue;

  /// No description provided for @colorSaturation.
  ///
  /// In zh, this message translates to:
  /// **'饱和度'**
  String get colorSaturation;

  /// No description provided for @colorBrightness.
  ///
  /// In zh, this message translates to:
  /// **'亮度'**
  String get colorBrightness;

  /// No description provided for @colorOpacity.
  ///
  /// In zh, this message translates to:
  /// **'透明度'**
  String get colorOpacity;

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

  /// No description provided for @optDefaultOn.
  ///
  /// In zh, this message translates to:
  /// **'默认（开启）'**
  String get optDefaultOn;

  /// No description provided for @optDefaultOff.
  ///
  /// In zh, this message translates to:
  /// **'默认（关闭）'**
  String get optDefaultOff;

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

  /// No description provided for @firstFloatLabelSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'超级岛初次收到通知后是否展开为焦点通知'**
  String get firstFloatLabelSubtitle;

  /// No description provided for @updateFloatLabelSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'超级岛更新后是否展开通知'**
  String get updateFloatLabelSubtitle;

  /// No description provided for @marqueeChannelTitleSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'超级岛消息过长是否滚动显示'**
  String get marqueeChannelTitleSubtitle;

  /// No description provided for @focusNotificationLabelSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'替换通知为焦点通知（关闭后显示原始通知）'**
  String get focusNotificationLabelSubtitle;

  /// No description provided for @preserveStatusBarSmallIconLabelSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'焦点通知打开时，是否强制保留状态栏小图标'**
  String get preserveStatusBarSmallIconLabelSubtitle;

  /// No description provided for @fullscreenBehaviorTitle.
  ///
  /// In zh, this message translates to:
  /// **'全屏时行为'**
  String get fullscreenBehaviorTitle;

  /// No description provided for @fullscreenBehaviorSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'检测到横屏/全屏时的通知处理策略'**
  String get fullscreenBehaviorSubtitle;

  /// No description provided for @fullscreenBehaviorOff.
  ///
  /// In zh, this message translates to:
  /// **'默认'**
  String get fullscreenBehaviorOff;

  /// No description provided for @fullscreenBehaviorFallback.
  ///
  /// In zh, this message translates to:
  /// **'回退普通通知'**
  String get fullscreenBehaviorFallback;

  /// No description provided for @filterRulesTitle.
  ///
  /// In zh, this message translates to:
  /// **'过滤规则'**
  String get filterRulesTitle;

  /// No description provided for @filterRulesOrderTitle.
  ///
  /// In zh, this message translates to:
  /// **'按顺序命中第一条规则'**
  String get filterRulesOrderTitle;

  /// No description provided for @filterRuleDnd.
  ///
  /// In zh, this message translates to:
  /// **'勿扰'**
  String get filterRuleDnd;

  /// No description provided for @filterRuleFullscreen.
  ///
  /// In zh, this message translates to:
  /// **'全屏'**
  String get filterRuleFullscreen;

  /// No description provided for @filterRuleLandscape.
  ///
  /// In zh, this message translates to:
  /// **'横屏'**
  String get filterRuleLandscape;

  /// No description provided for @dndBehaviorTitle.
  ///
  /// In zh, this message translates to:
  /// **'勿扰时'**
  String get dndBehaviorTitle;

  /// No description provided for @fullscreenRuleTitle.
  ///
  /// In zh, this message translates to:
  /// **'全屏时'**
  String get fullscreenRuleTitle;

  /// No description provided for @landscapeRuleTitle.
  ///
  /// In zh, this message translates to:
  /// **'横屏时'**
  String get landscapeRuleTitle;

  /// No description provided for @behaviorPreviewDefault.
  ///
  /// In zh, this message translates to:
  /// **'命中时不处理，继续使用默认行为'**
  String get behaviorPreviewDefault;

  /// No description provided for @behaviorPreviewSuppress.
  ///
  /// In zh, this message translates to:
  /// **'命中时回退为普通通知'**
  String get behaviorPreviewSuppress;

  /// No description provided for @behaviorPreviewSmallOnly.
  ///
  /// In zh, this message translates to:
  /// **'命中时只显示小岛，不自动展开'**
  String get behaviorPreviewSmallOnly;

  /// No description provided for @behaviorPreviewExpand.
  ///
  /// In zh, this message translates to:
  /// **'命中时自动展开通知'**
  String get behaviorPreviewExpand;

  /// No description provided for @aiConfigSection.
  ///
  /// In zh, this message translates to:
  /// **'AI 增强'**
  String get aiConfigSection;

  /// No description provided for @aiConfigTitle.
  ///
  /// In zh, this message translates to:
  /// **'AI 通知摘要'**
  String get aiConfigTitle;

  /// No description provided for @aiConfigSubtitleEnabled.
  ///
  /// In zh, this message translates to:
  /// **'已启用 · 点击配置 AI 参数'**
  String get aiConfigSubtitleEnabled;

  /// No description provided for @aiConfigSubtitleDisabled.
  ///
  /// In zh, this message translates to:
  /// **'已关闭 · 点击进行配置'**
  String get aiConfigSubtitleDisabled;

  /// No description provided for @aiEnabledTitle.
  ///
  /// In zh, this message translates to:
  /// **'启用 AI 摘要'**
  String get aiEnabledTitle;

  /// No description provided for @aiEnabledSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'由 AI 生成超级岛左右文本，超时或失败时自动回退'**
  String get aiEnabledSubtitle;

  /// No description provided for @aiApiSection.
  ///
  /// In zh, this message translates to:
  /// **'API 参数'**
  String get aiApiSection;

  /// No description provided for @aiUrlLabel.
  ///
  /// In zh, this message translates to:
  /// **'API 地址（必须完整）'**
  String get aiUrlLabel;

  /// No description provided for @aiUrlHint.
  ///
  /// In zh, this message translates to:
  /// **'https://api.openai.com/v1/chat/completions'**
  String get aiUrlHint;

  /// No description provided for @aiApiKeyLabel.
  ///
  /// In zh, this message translates to:
  /// **'API 密钥'**
  String get aiApiKeyLabel;

  /// No description provided for @aiApiKeyHint.
  ///
  /// In zh, this message translates to:
  /// **'sk-...'**
  String get aiApiKeyHint;

  /// No description provided for @aiModelLabel.
  ///
  /// In zh, this message translates to:
  /// **'模型'**
  String get aiModelLabel;

  /// No description provided for @aiModelHint.
  ///
  /// In zh, this message translates to:
  /// **'gpt-4o-mini'**
  String get aiModelHint;

  /// No description provided for @aiModelPickerTitle.
  ///
  /// In zh, this message translates to:
  /// **'选择模型'**
  String get aiModelPickerTitle;

  /// No description provided for @aiModelPickerSearchHint.
  ///
  /// In zh, this message translates to:
  /// **'搜索模型…'**
  String get aiModelPickerSearchHint;

  /// No description provided for @aiModelPickerEmpty.
  ///
  /// In zh, this message translates to:
  /// **'未找到模型'**
  String get aiModelPickerEmpty;

  /// No description provided for @aiModelPickerRetry.
  ///
  /// In zh, this message translates to:
  /// **'重试'**
  String get aiModelPickerRetry;

  /// No description provided for @aiModelPickerClose.
  ///
  /// In zh, this message translates to:
  /// **'关闭'**
  String get aiModelPickerClose;

  /// No description provided for @aiModelPickerFetchError.
  ///
  /// In zh, this message translates to:
  /// **'加载模型列表失败'**
  String get aiModelPickerFetchError;

  /// No description provided for @aiPromptLabel.
  ///
  /// In zh, this message translates to:
  /// **'系统提示词'**
  String get aiPromptLabel;

  /// No description provided for @aiPromptHint.
  ///
  /// In zh, this message translates to:
  /// **'留空则使用默认提示词'**
  String get aiPromptHint;

  /// No description provided for @aiPromptInUserTitle.
  ///
  /// In zh, this message translates to:
  /// **'提示词放在用户消息'**
  String get aiPromptInUserTitle;

  /// No description provided for @aiPromptInUserSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'某些模型不支持系统指令，开启后将提示词放在用户消息中'**
  String get aiPromptInUserSubtitle;

  /// No description provided for @aiTimeoutTitle.
  ///
  /// In zh, this message translates to:
  /// **'AI 响应超时'**
  String get aiTimeoutTitle;

  /// No description provided for @aiTimeoutLabel.
  ///
  /// In zh, this message translates to:
  /// **'{seconds}s'**
  String aiTimeoutLabel(int seconds);

  /// No description provided for @aiTemperatureTitle.
  ///
  /// In zh, this message translates to:
  /// **'采样温度 (Temperature)'**
  String get aiTemperatureTitle;

  /// No description provided for @aiTemperatureSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'控制回答的随机性。0 为准确，1 则更具创意'**
  String get aiTemperatureSubtitle;

  /// No description provided for @aiMaxTokensTitle.
  ///
  /// In zh, this message translates to:
  /// **'最大 Token 数 (Max Tokens)'**
  String get aiMaxTokensTitle;

  /// No description provided for @aiMaxTokensSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'限制 AI 生成回答的最大长度'**
  String get aiMaxTokensSubtitle;

  /// No description provided for @aiDefaultPromptFull.
  ///
  /// In zh, this message translates to:
  /// **'留空使用默认提示词：根据通知信息，提取关键信息，左右分别不超过 6 汉字 12 字符'**
  String get aiDefaultPromptFull;

  /// No description provided for @aiTestButton.
  ///
  /// In zh, this message translates to:
  /// **'测试连接'**
  String get aiTestButton;

  /// No description provided for @aiTestUrlEmpty.
  ///
  /// In zh, this message translates to:
  /// **'请先填写 API 地址'**
  String get aiTestUrlEmpty;

  /// No description provided for @aiLastLogTitle.
  ///
  /// In zh, this message translates to:
  /// **'最近一次 AI 请求日志'**
  String get aiLastLogTitle;

  /// No description provided for @aiLastLogSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'测试连接和通知触发的 AI 请求都会显示在这里'**
  String get aiLastLogSubtitle;

  /// No description provided for @aiLastLogEmpty.
  ///
  /// In zh, this message translates to:
  /// **'还没有可显示的 AI 请求日志'**
  String get aiLastLogEmpty;

  /// No description provided for @aiLastLogSourceLabel.
  ///
  /// In zh, this message translates to:
  /// **'来源'**
  String get aiLastLogSourceLabel;

  /// No description provided for @aiLastLogTimeLabel.
  ///
  /// In zh, this message translates to:
  /// **'时间'**
  String get aiLastLogTimeLabel;

  /// No description provided for @aiLastLogStatusLabel.
  ///
  /// In zh, this message translates to:
  /// **'状态'**
  String get aiLastLogStatusLabel;

  /// No description provided for @aiLastLogDurationLabel.
  ///
  /// In zh, this message translates to:
  /// **'耗时'**
  String get aiLastLogDurationLabel;

  /// No description provided for @aiLastLogSourceNotification.
  ///
  /// In zh, this message translates to:
  /// **'通知触发'**
  String get aiLastLogSourceNotification;

  /// No description provided for @aiLastLogSourceSettingsTest.
  ///
  /// In zh, this message translates to:
  /// **'设置页测试'**
  String get aiLastLogSourceSettingsTest;

  /// No description provided for @aiLastLogRendered.
  ///
  /// In zh, this message translates to:
  /// **'渲染'**
  String get aiLastLogRendered;

  /// No description provided for @aiLastLogRaw.
  ///
  /// In zh, this message translates to:
  /// **'原始'**
  String get aiLastLogRaw;

  /// No description provided for @aiLastLogCopy.
  ///
  /// In zh, this message translates to:
  /// **'复制日志'**
  String get aiLastLogCopy;

  /// No description provided for @aiLastLogCopied.
  ///
  /// In zh, this message translates to:
  /// **'AI 请求日志已复制'**
  String get aiLastLogCopied;

  /// No description provided for @aiLastLogRequest.
  ///
  /// In zh, this message translates to:
  /// **'请求'**
  String get aiLastLogRequest;

  /// No description provided for @aiLastLogResponse.
  ///
  /// In zh, this message translates to:
  /// **'回复'**
  String get aiLastLogResponse;

  /// No description provided for @aiLastLogUsage.
  ///
  /// In zh, this message translates to:
  /// **'Token 用量'**
  String get aiLastLogUsage;

  /// No description provided for @aiLastLogMessages.
  ///
  /// In zh, this message translates to:
  /// **'消息'**
  String get aiLastLogMessages;

  /// No description provided for @aiLastLogError.
  ///
  /// In zh, this message translates to:
  /// **'错误'**
  String get aiLastLogError;

  /// No description provided for @aiLastLogHttpCode.
  ///
  /// In zh, this message translates to:
  /// **'HTTP 状态'**
  String get aiLastLogHttpCode;

  /// No description provided for @aiLastLogLeftText.
  ///
  /// In zh, this message translates to:
  /// **'左侧文本'**
  String get aiLastLogLeftText;

  /// No description provided for @aiLastLogRightText.
  ///
  /// In zh, this message translates to:
  /// **'右侧文本'**
  String get aiLastLogRightText;

  /// No description provided for @aiLastLogAssistantContent.
  ///
  /// In zh, this message translates to:
  /// **'模型回复内容'**
  String get aiLastLogAssistantContent;

  /// No description provided for @aiConfigSaveButton.
  ///
  /// In zh, this message translates to:
  /// **'保存'**
  String get aiConfigSaveButton;

  /// No description provided for @aiConfigSaved.
  ///
  /// In zh, this message translates to:
  /// **'AI 配置已保存'**
  String get aiConfigSaved;

  /// No description provided for @aiConfigTips.
  ///
  /// In zh, this message translates to:
  /// **'AI 将收到通知的应用包名、标题和正文，返回左侧（来源）和右侧（内容）短文本。支持兼容 OpenAI 格式的接口（如 DeepSeek、Claude 等）。超过 3 秒未响应时自动回退到默认逻辑。'**
  String get aiConfigTips;

  /// No description provided for @templateAiNotificationIslandName.
  ///
  /// In zh, this message translates to:
  /// **'AI 通知超级岛'**
  String get templateAiNotificationIslandName;

  /// No description provided for @aiPromptDefault.
  ///
  /// In zh, this message translates to:
  /// **'根据通知信息，提取关键信息，左右分别不超过 6 汉字 12 字符'**
  String get aiPromptDefault;

  /// No description provided for @aiDefaultNotificationText.
  ///
  /// In zh, this message translates to:
  /// **'[外卖]，您的外卖到了，送至门口外卖柜'**
  String get aiDefaultNotificationText;

  /// No description provided for @aiTestSampleUserContent.
  ///
  /// In zh, this message translates to:
  /// **'请直接回复：测试成功'**
  String get aiTestSampleUserContent;

  /// No description provided for @aiNotificationUserContent.
  ///
  /// In zh, this message translates to:
  /// **'应用包名：com.example.app\n标题：测试通知\n正文：{content}'**
  String aiNotificationUserContent(String content);

  /// No description provided for @aiJsonOnlyInstruction.
  ///
  /// In zh, this message translates to:
  /// **'仅返回如下 JSON，不得包含任何其他文字或代码块：'**
  String get aiJsonOnlyInstruction;

  /// No description provided for @aiJsonLeftDescription.
  ///
  /// In zh, this message translates to:
  /// **'左侧文本（谁发的）'**
  String get aiJsonLeftDescription;

  /// No description provided for @aiJsonRightDescription.
  ///
  /// In zh, this message translates to:
  /// **'右侧文本（总结）'**
  String get aiJsonRightDescription;

  /// No description provided for @aiInvalidJsonError.
  ///
  /// In zh, this message translates to:
  /// **'AI 返回格式错误，需要包含 left 和 right 字段的 JSON'**
  String get aiInvalidJsonError;

  /// No description provided for @aiEmptyJsonError.
  ///
  /// In zh, this message translates to:
  /// **'AI 返回为空，需要包含 left 和 right 字段的 JSON'**
  String get aiEmptyJsonError;

  /// No description provided for @aiNotificationContentLabel.
  ///
  /// In zh, this message translates to:
  /// **'通知内容'**
  String get aiNotificationContentLabel;

  /// No description provided for @aiTestNotificationTitle.
  ///
  /// In zh, this message translates to:
  /// **'测试通知'**
  String get aiTestNotificationTitle;

  /// No description provided for @aiNotificationSent.
  ///
  /// In zh, this message translates to:
  /// **'通知已发送'**
  String get aiNotificationSent;

  /// No description provided for @aiAiNotificationSent.
  ///
  /// In zh, this message translates to:
  /// **'AI 通知已发送'**
  String get aiAiNotificationSent;

  /// No description provided for @aiSendNotificationButton.
  ///
  /// In zh, this message translates to:
  /// **'发送通知'**
  String get aiSendNotificationButton;

  /// No description provided for @aiSendAiNotificationButton.
  ///
  /// In zh, this message translates to:
  /// **'发送 AI 通知'**
  String get aiSendAiNotificationButton;

  /// No description provided for @hideDesktopIconTitle.
  ///
  /// In zh, this message translates to:
  /// **'隐藏桌面图标'**
  String get hideDesktopIconTitle;

  /// No description provided for @hideDesktopIconSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'隐藏启动器中的应用图标，隐藏后可通过 LSPosed 管理器打开'**
  String get hideDesktopIconSubtitle;

  /// No description provided for @filterRulesSection.
  ///
  /// In zh, this message translates to:
  /// **'过滤规则'**
  String get filterRulesSection;

  /// No description provided for @foregroundRulesTab.
  ///
  /// In zh, this message translates to:
  /// **'前台规则'**
  String get foregroundRulesTab;

  /// No description provided for @foregroundExclusionsTab.
  ///
  /// In zh, this message translates to:
  /// **'排除应用'**
  String get foregroundExclusionsTab;

  /// No description provided for @foregroundRulesDescription.
  ///
  /// In zh, this message translates to:
  /// **'前台应用启动时，设置超级岛行为。'**
  String get foregroundRulesDescription;

  /// No description provided for @foregroundExclusionsDescription.
  ///
  /// In zh, this message translates to:
  /// **'排除列表内应用的通知不受前台规则限制。'**
  String get foregroundExclusionsDescription;

  /// No description provided for @hideSystemApps.
  ///
  /// In zh, this message translates to:
  /// **'隐藏系统应用'**
  String get hideSystemApps;

  /// No description provided for @restoreDefaultConfig.
  ///
  /// In zh, this message translates to:
  /// **'恢复默认配置'**
  String get restoreDefaultConfig;

  /// No description provided for @resetDefaultConfigSuccess.
  ///
  /// In zh, this message translates to:
  /// **'已恢复默认配置，共重置 {count} 个应用'**
  String resetDefaultConfigSuccess(int count);

  /// No description provided for @sceneActionDefault.
  ///
  /// In zh, this message translates to:
  /// **'默认'**
  String get sceneActionDefault;

  /// No description provided for @sceneActionSmallOnly.
  ///
  /// In zh, this message translates to:
  /// **'关闭展开'**
  String get sceneActionSmallOnly;

  /// No description provided for @sceneActionExpand.
  ///
  /// In zh, this message translates to:
  /// **'自动展开'**
  String get sceneActionExpand;

  /// No description provided for @sceneActionSuppress.
  ///
  /// In zh, this message translates to:
  /// **'回退'**
  String get sceneActionSuppress;

  /// No description provided for @filterModeLabel.
  ///
  /// In zh, this message translates to:
  /// **'过滤模式'**
  String get filterModeLabel;

  /// No description provided for @filterModeBlacklist.
  ///
  /// In zh, this message translates to:
  /// **'黑名单'**
  String get filterModeBlacklist;

  /// No description provided for @filterModeWhitelist.
  ///
  /// In zh, this message translates to:
  /// **'白名单'**
  String get filterModeWhitelist;

  /// No description provided for @filterModeBlacklistDesc.
  ///
  /// In zh, this message translates to:
  /// **'匹配关键词的通知将被过滤'**
  String get filterModeBlacklistDesc;

  /// No description provided for @filterModeWhitelistDesc.
  ///
  /// In zh, this message translates to:
  /// **'仅匹配关键词的通知会显示'**
  String get filterModeWhitelistDesc;

  /// No description provided for @whitelistKeywordsLabel.
  ///
  /// In zh, this message translates to:
  /// **'白名单关键词'**
  String get whitelistKeywordsLabel;

  /// No description provided for @blacklistKeywordsLabel.
  ///
  /// In zh, this message translates to:
  /// **'黑名单关键词'**
  String get blacklistKeywordsLabel;

  /// No description provided for @addKeyword.
  ///
  /// In zh, this message translates to:
  /// **'添加关键词'**
  String get addKeyword;

  /// No description provided for @keywordHint.
  ///
  /// In zh, this message translates to:
  /// **'输入关键词'**
  String get keywordHint;

  /// No description provided for @removeKeyword.
  ///
  /// In zh, this message translates to:
  /// **'移除'**
  String get removeKeyword;

  /// No description provided for @keywordFilterPriority.
  ///
  /// In zh, this message translates to:
  /// **'白名单优先：仅白名单匹配的通知显示，但黑名单仍可否决'**
  String get keywordFilterPriority;

  /// No description provided for @exportChannelsToClipboard.
  ///
  /// In zh, this message translates to:
  /// **'导出渠道设置'**
  String get exportChannelsToClipboard;

  /// No description provided for @importChannelsFromClipboard.
  ///
  /// In zh, this message translates to:
  /// **'导入渠道设置'**
  String get importChannelsFromClipboard;

  /// No description provided for @exportChannelsSuccess.
  ///
  /// In zh, this message translates to:
  /// **'渠道设置已复制到剪贴板'**
  String get exportChannelsSuccess;

  /// No description provided for @importChannelsSuccess.
  ///
  /// In zh, this message translates to:
  /// **'导入成功，共 {count} 个渠道设置已恢复'**
  String importChannelsSuccess(int count);

  /// No description provided for @importChannelsPartialSuffix.
  ///
  /// In zh, this message translates to:
  /// **'（共 {total} 个，已匹配 {matched} 个）'**
  String importChannelsPartialSuffix(int total, int matched);

  /// No description provided for @importErrorEmptyClipboard.
  ///
  /// In zh, this message translates to:
  /// **'剪贴板为空，请先复制渠道设置数据'**
  String get importErrorEmptyClipboard;

  /// No description provided for @importErrorNotJson.
  ///
  /// In zh, this message translates to:
  /// **'剪贴板内容不是有效的 JSON 数据'**
  String get importErrorNotJson;

  /// No description provided for @importErrorMissingChannels.
  ///
  /// In zh, this message translates to:
  /// **'数据格式不正确，缺少渠道列表'**
  String get importErrorMissingChannels;

  /// No description provided for @importErrorNoMatch.
  ///
  /// In zh, this message translates to:
  /// **'没有与当前应用匹配的渠道，请确认数据来源正确'**
  String get importErrorNoMatch;

  /// No description provided for @importErrorUnknown.
  ///
  /// In zh, this message translates to:
  /// **'导入失败，请检查剪贴板数据是否正确'**
  String get importErrorUnknown;

  /// No description provided for @mediaNotificationTitle.
  ///
  /// In zh, this message translates to:
  /// **'媒体通知'**
  String get mediaNotificationTitle;

  /// No description provided for @mediaNotificationDisabledSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'关闭后直接删除整条媒体通知'**
  String get mediaNotificationDisabledSubtitle;

  /// No description provided for @normalNotificationTitle.
  ///
  /// In zh, this message translates to:
  /// **'普通通知'**
  String get normalNotificationTitle;

  /// No description provided for @normalNotificationSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'开启后移除媒体字段，按普通通知处理'**
  String get normalNotificationSubtitle;

  /// No description provided for @channelSettingsUnmodified.
  ///
  /// In zh, this message translates to:
  /// **'未修改'**
  String get channelSettingsUnmodified;

  /// No description provided for @restoreDefault.
  ///
  /// In zh, this message translates to:
  /// **'恢复默认'**
  String get restoreDefault;

  /// No description provided for @islandDimenSection.
  ///
  /// In zh, this message translates to:
  /// **'岛尺寸设置'**
  String get islandDimenSection;

  /// No description provided for @islandDimenHeight.
  ///
  /// In zh, this message translates to:
  /// **'岛高度'**
  String get islandDimenHeight;

  /// No description provided for @islandTopOffset.
  ///
  /// In zh, this message translates to:
  /// **'距屏幕顶部'**
  String get islandTopOffset;

  /// No description provided for @followSystem.
  ///
  /// In zh, this message translates to:
  /// **'跟随系统'**
  String get followSystem;

  /// No description provided for @islandDimenMiniY.
  ///
  /// In zh, this message translates to:
  /// **'垂直位置'**
  String get islandDimenMiniY;

  /// No description provided for @islandDimenMiniYHint.
  ///
  /// In zh, this message translates to:
  /// **'0=跟随系统'**
  String get islandDimenMiniYHint;

  /// No description provided for @islandBgSection.
  ///
  /// In zh, this message translates to:
  /// **'岛背景设置'**
  String get islandBgSection;

  /// No description provided for @islandBgSmallTitle.
  ///
  /// In zh, this message translates to:
  /// **'小岛背景图'**
  String get islandBgSmallTitle;

  /// No description provided for @islandBgBigTitle.
  ///
  /// In zh, this message translates to:
  /// **'大岛背景图'**
  String get islandBgBigTitle;

  /// No description provided for @islandBgExpandTitle.
  ///
  /// In zh, this message translates to:
  /// **'焦点通知背景图'**
  String get islandBgExpandTitle;

  /// No description provided for @islandBgNotSet.
  ///
  /// In zh, this message translates to:
  /// **'未设置'**
  String get islandBgNotSet;

  /// No description provided for @islandBgCornerRadius.
  ///
  /// In zh, this message translates to:
  /// **'圆角半径'**
  String get islandBgCornerRadius;

  /// No description provided for @islandBgCornerRadiusHint.
  ///
  /// In zh, this message translates to:
  /// **'0=跟随系统'**
  String get islandBgCornerRadiusHint;

  /// No description provided for @islandBgImageSelected.
  ///
  /// In zh, this message translates to:
  /// **'背景图片已保存'**
  String get islandBgImageSelected;

  /// No description provided for @islandBgImageDeleted.
  ///
  /// In zh, this message translates to:
  /// **'背景图片已删除'**
  String get islandBgImageDeleted;

  /// No description provided for @islandBgDeleteFailed.
  ///
  /// In zh, this message translates to:
  /// **'删除失败'**
  String get islandBgDeleteFailed;

  /// No description provided for @islandBgEditTitle.
  ///
  /// In zh, this message translates to:
  /// **'编辑{type}背景'**
  String islandBgEditTitle(String type);

  /// No description provided for @islandBgBlurLabel.
  ///
  /// In zh, this message translates to:
  /// **'模糊'**
  String get islandBgBlurLabel;

  /// No description provided for @islandBgBrightnessLabel.
  ///
  /// In zh, this message translates to:
  /// **'亮度'**
  String get islandBgBrightnessLabel;

  /// No description provided for @islandBgOpacityLabel.
  ///
  /// In zh, this message translates to:
  /// **'不透明度'**
  String get islandBgOpacityLabel;

  /// No description provided for @islandBgDefault.
  ///
  /// In zh, this message translates to:
  /// **'默认'**
  String get islandBgDefault;

  /// No description provided for @keepIslandTitle.
  ///
  /// In zh, this message translates to:
  /// **'常驻超级岛'**
  String get keepIslandTitle;

  /// No description provided for @keepIslandSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'显示一条空白通知使岛始终可见'**
  String get keepIslandSubtitle;

  /// No description provided for @keepIslandAutoHideTitle.
  ///
  /// In zh, this message translates to:
  /// **'自动隐藏'**
  String get keepIslandAutoHideTitle;

  /// No description provided for @keepIslandAutoHideSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'通知到来时自动隐藏空白岛，通知消失后自动恢复'**
  String get keepIslandAutoHideSubtitle;

  /// No description provided for @keepIslandHideLandscapeTitle.
  ///
  /// In zh, this message translates to:
  /// **'横屏时隐藏'**
  String get keepIslandHideLandscapeTitle;

  /// No description provided for @keepIslandHideLandscapeSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'横屏时自动隐藏常驻岛，回到竖屏时恢复'**
  String get keepIslandHideLandscapeSubtitle;

  /// No description provided for @keepIslandHighlightColorTitle.
  ///
  /// In zh, this message translates to:
  /// **'高亮颜色'**
  String get keepIslandHighlightColorTitle;

  /// No description provided for @keepIslandHighlightColorSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'自定义常驻岛的高亮文字颜色'**
  String get keepIslandHighlightColorSubtitle;

  /// No description provided for @keepIslandLeftContentTitle.
  ///
  /// In zh, this message translates to:
  /// **'岛左边内容'**
  String get keepIslandLeftContentTitle;

  /// No description provided for @keepIslandRightContentTitle.
  ///
  /// In zh, this message translates to:
  /// **'岛右边内容'**
  String get keepIslandRightContentTitle;

  /// No description provided for @keepIslandPlaceholdersTitle.
  ///
  /// In zh, this message translates to:
  /// **'可用占位符'**
  String get keepIslandPlaceholdersTitle;

  /// No description provided for @keepIslandPlaceholdersDescription.
  ///
  /// In zh, this message translates to:
  /// **'可在左右内容中直接输入文本或表达式，例如：电量 {batteryLevel}、CPU {cpuUsage}。点击标签复制。'**
  String keepIslandPlaceholdersDescription(
    String batteryLevel,
    String cpuUsage,
  );

  /// No description provided for @keepIslandPlaceholderCopied.
  ///
  /// In zh, this message translates to:
  /// **'已复制 {placeholder}'**
  String keepIslandPlaceholderCopied(String placeholder);

  /// No description provided for @keepIslandDefaultEmpty.
  ///
  /// In zh, this message translates to:
  /// **'默认空'**
  String get keepIslandDefaultEmpty;

  /// No description provided for @keepIslandContentHint.
  ///
  /// In zh, this message translates to:
  /// **'默认空，可输入文本或 {placeholder}'**
  String keepIslandContentHint(String placeholder);

  /// No description provided for @clear.
  ///
  /// In zh, this message translates to:
  /// **'清空'**
  String get clear;

  /// No description provided for @save.
  ///
  /// In zh, this message translates to:
  /// **'保存'**
  String get save;

  /// No description provided for @islandOtherSection.
  ///
  /// In zh, this message translates to:
  /// **'其他'**
  String get islandOtherSection;

  /// No description provided for @miscSection.
  ///
  /// In zh, this message translates to:
  /// **'杂项'**
  String get miscSection;

  /// No description provided for @onboardingEntryTitle.
  ///
  /// In zh, this message translates to:
  /// **'打开初始引导'**
  String get onboardingEntryTitle;

  /// No description provided for @onboardingEntrySubtitle.
  ///
  /// In zh, this message translates to:
  /// **'重新查看欢迎与快速上手流程'**
  String get onboardingEntrySubtitle;

  /// No description provided for @onboardingAppName.
  ///
  /// In zh, this message translates to:
  /// **'HyperIsland'**
  String get onboardingAppName;

  /// No description provided for @onboardingWelcomeTitle.
  ///
  /// In zh, this message translates to:
  /// **'欢迎使用 HyperIsland'**
  String get onboardingWelcomeTitle;

  /// No description provided for @onboardingWelcomeSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'简洁、快速地配置你的超级岛体验'**
  String get onboardingWelcomeSubtitle;

  /// No description provided for @onboardingEnvironmentTitle.
  ///
  /// In zh, this message translates to:
  /// **'环境检测'**
  String get onboardingEnvironmentTitle;

  /// No description provided for @onboardingEnvironmentSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'确认模块权限状态'**
  String get onboardingEnvironmentSubtitle;

  /// No description provided for @onboardingNotificationStyleTitle.
  ///
  /// In zh, this message translates to:
  /// **'选择通知样式'**
  String get onboardingNotificationStyleTitle;

  /// No description provided for @onboardingNotificationStyleSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'选择你更喜欢的默认通知展示方式'**
  String get onboardingNotificationStyleSubtitle;

  /// No description provided for @onboardingOriginalNotificationLabel.
  ///
  /// In zh, this message translates to:
  /// **'普通通知'**
  String get onboardingOriginalNotificationLabel;

  /// No description provided for @onboardingFinishTitle.
  ///
  /// In zh, this message translates to:
  /// **'一切就绪'**
  String get onboardingFinishTitle;

  /// No description provided for @onboardingFinishSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'完成引导后，你可以到设置继续调整细节'**
  String get onboardingFinishSubtitle;

  /// No description provided for @onboardingStepLabel.
  ///
  /// In zh, this message translates to:
  /// **'第{current}步 / 共{total}步'**
  String onboardingStepLabel(int current, int total);

  /// No description provided for @onboardingPrevious.
  ///
  /// In zh, this message translates to:
  /// **'上一步'**
  String get onboardingPrevious;

  /// No description provided for @onboardingNext.
  ///
  /// In zh, this message translates to:
  /// **'下一步'**
  String get onboardingNext;

  /// No description provided for @onboardingDone.
  ///
  /// In zh, this message translates to:
  /// **'开始使用'**
  String get onboardingDone;

  /// No description provided for @onboardingStatusTitle.
  ///
  /// In zh, this message translates to:
  /// **'状态检测'**
  String get onboardingStatusTitle;

  /// No description provided for @onboardingRetry.
  ///
  /// In zh, this message translates to:
  /// **'重试'**
  String get onboardingRetry;

  /// No description provided for @onboardingLsposedStatus.
  ///
  /// In zh, this message translates to:
  /// **'LSPosed 激活状态'**
  String get onboardingLsposedStatus;

  /// No description provided for @onboardingRootStatus.
  ///
  /// In zh, this message translates to:
  /// **'Root 权限'**
  String get onboardingRootStatus;

  /// No description provided for @onboardingAppListStatus.
  ///
  /// In zh, this message translates to:
  /// **'应用列表权限'**
  String get onboardingAppListStatus;

  /// No description provided for @onboardingProtocolStatus.
  ///
  /// In zh, this message translates to:
  /// **'系统协议版本'**
  String get onboardingProtocolStatus;

  /// No description provided for @onboardingAndroidStatus.
  ///
  /// In zh, this message translates to:
  /// **'安卓版本'**
  String get onboardingAndroidStatus;

  /// No description provided for @onboardingUnsupportedSystem.
  ///
  /// In zh, this message translates to:
  /// **'不支持当前系统'**
  String get onboardingUnsupportedSystem;

  /// No description provided for @onboardingAndroid15Limited.
  ///
  /// In zh, this message translates to:
  /// **'A15系统支持有限'**
  String get onboardingAndroid15Limited;

  /// No description provided for @onboardingMissingPermissionTitle.
  ///
  /// In zh, this message translates to:
  /// **'缺少必要权限'**
  String get onboardingMissingPermissionTitle;

  /// No description provided for @onboardingMissingPermissionMessage.
  ///
  /// In zh, this message translates to:
  /// **'模块可能无法正常工作'**
  String get onboardingMissingPermissionMessage;

  /// No description provided for @onboardingDialogClose.
  ///
  /// In zh, this message translates to:
  /// **'关闭'**
  String get onboardingDialogClose;

  /// No description provided for @onboardingDialogContinue.
  ///
  /// In zh, this message translates to:
  /// **'继续'**
  String get onboardingDialogContinue;

  /// No description provided for @backupRestoreSection.
  ///
  /// In zh, this message translates to:
  /// **'备份与恢复'**
  String get backupRestoreSection;

  /// No description provided for @hookExtensionSection.
  ///
  /// In zh, this message translates to:
  /// **'Hook拓展'**
  String get hookExtensionSection;

  /// No description provided for @hookScopeSettings.
  ///
  /// In zh, this message translates to:
  /// **'系统设置'**
  String get hookScopeSettings;

  /// No description provided for @settingsHomeEntryTitle.
  ///
  /// In zh, this message translates to:
  /// **'系统设置入口'**
  String get settingsHomeEntryTitle;

  /// No description provided for @settingsHomeEntrySubtitle.
  ///
  /// In zh, this message translates to:
  /// **'在系统设置首页显示 HyperIsland 入口'**
  String get settingsHomeEntrySubtitle;

  /// No description provided for @xposedScopeRequestFailed.
  ///
  /// In zh, this message translates to:
  /// **'作用域申请失败，请确认模块已在 LSPosed 中启用'**
  String get xposedScopeRequestFailed;

  /// No description provided for @hookScopeSystemUI.
  ///
  /// In zh, this message translates to:
  /// **'系统界面'**
  String get hookScopeSystemUI;

  /// No description provided for @smoothIslandTitle.
  ///
  /// In zh, this message translates to:
  /// **'平滑超级岛'**
  String get smoothIslandTitle;

  /// No description provided for @smoothIslandSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'使用连续曲率胶囊优化超级岛轮廓，关闭后需重启作用域以完全卸载 Hook'**
  String get smoothIslandSubtitle;

  /// No description provided for @smoothIslandSmoothingTitle.
  ///
  /// In zh, this message translates to:
  /// **'平滑强度'**
  String get smoothIslandSmoothingTitle;

  /// No description provided for @bluetoothIslandStatusEnabled.
  ///
  /// In zh, this message translates to:
  /// **'已开启'**
  String get bluetoothIslandStatusEnabled;

  /// No description provided for @bluetoothIslandStatusDisabled.
  ///
  /// In zh, this message translates to:
  /// **'已关闭'**
  String get bluetoothIslandStatusDisabled;

  /// No description provided for @bluetoothIslandTitle.
  ///
  /// In zh, this message translates to:
  /// **'蓝牙超级岛'**
  String get bluetoothIslandTitle;

  /// No description provided for @bluetoothIslandSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'{status} · 监听蓝牙设备连接和断开，由 SystemUI 代发超级岛'**
  String bluetoothIslandSubtitle(String status);

  /// No description provided for @bluetoothIslandSettingsTitle.
  ///
  /// In zh, this message translates to:
  /// **'蓝牙超级岛设置'**
  String get bluetoothIslandSettingsTitle;

  /// No description provided for @bluetoothIslandEnableTitle.
  ///
  /// In zh, this message translates to:
  /// **'启用蓝牙超级岛'**
  String get bluetoothIslandEnableTitle;

  /// No description provided for @bluetoothIslandEnableSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'关闭后重启 SystemUI 生效，且不会注册蓝牙 Hook'**
  String get bluetoothIslandEnableSubtitle;

  /// No description provided for @bluetoothIslandShowDeviceNameTitle.
  ///
  /// In zh, this message translates to:
  /// **'显示设备名称'**
  String get bluetoothIslandShowDeviceNameTitle;

  /// No description provided for @bluetoothIslandShowDeviceNameSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'连接时右侧先显示设备名称，之后再显示连接状态'**
  String get bluetoothIslandShowDeviceNameSubtitle;

  /// No description provided for @bluetoothIslandDisplayDurationTitle.
  ///
  /// In zh, this message translates to:
  /// **'显示时长'**
  String get bluetoothIslandDisplayDurationTitle;

  /// No description provided for @chargeIslandTitle.
  ///
  /// In zh, this message translates to:
  /// **'充电超级岛'**
  String get chargeIslandTitle;

  /// No description provided for @chargeIslandSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'{status} · 替换充电超级岛中的功率或电量片段'**
  String chargeIslandSubtitle(String status);

  /// No description provided for @chargeIslandSettingsTitle.
  ///
  /// In zh, this message translates to:
  /// **'充电超级岛设置'**
  String get chargeIslandSettingsTitle;

  /// No description provided for @chargeIslandEnableTitle.
  ///
  /// In zh, this message translates to:
  /// **'启用充电超级岛 Hook'**
  String get chargeIslandEnableTitle;

  /// No description provided for @chargeIslandEnableSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'关闭后重启 SystemUI 生效，Hook 将完全旁路'**
  String get chargeIslandEnableSubtitle;

  /// No description provided for @chargeIslandLeftModeTitle.
  ///
  /// In zh, this message translates to:
  /// **'左侧行为'**
  String get chargeIslandLeftModeTitle;

  /// No description provided for @chargeIslandRightModeTitle.
  ///
  /// In zh, this message translates to:
  /// **'右侧行为'**
  String get chargeIslandRightModeTitle;

  /// No description provided for @chargeIslandModeDefault.
  ///
  /// In zh, this message translates to:
  /// **'默认'**
  String get chargeIslandModeDefault;

  /// No description provided for @chargeIslandModePower.
  ///
  /// In zh, this message translates to:
  /// **'功率'**
  String get chargeIslandModePower;

  /// No description provided for @chargeIslandModeVoltage.
  ///
  /// In zh, this message translates to:
  /// **'电压'**
  String get chargeIslandModeVoltage;

  /// No description provided for @chargeIslandModeCurrent.
  ///
  /// In zh, this message translates to:
  /// **'电流'**
  String get chargeIslandModeCurrent;

  /// No description provided for @chargeIslandModeLevel.
  ///
  /// In zh, this message translates to:
  /// **'电量'**
  String get chargeIslandModeLevel;

  /// No description provided for @chargeIslandModeTemperature.
  ///
  /// In zh, this message translates to:
  /// **'电池温度'**
  String get chargeIslandModeTemperature;

  /// No description provided for @chargeIslandDurationModeTitle.
  ///
  /// In zh, this message translates to:
  /// **'持续时间'**
  String get chargeIslandDurationModeTitle;

  /// No description provided for @chargeIslandDurationDefault.
  ///
  /// In zh, this message translates to:
  /// **'默认'**
  String get chargeIslandDurationDefault;

  /// No description provided for @chargeIslandDurationCustom.
  ///
  /// In zh, this message translates to:
  /// **'自定义'**
  String get chargeIslandDurationCustom;

  /// No description provided for @chargeIslandDurationPersistent.
  ///
  /// In zh, this message translates to:
  /// **'常驻'**
  String get chargeIslandDurationPersistent;

  /// No description provided for @chargeIslandDurationSecondsTitle.
  ///
  /// In zh, this message translates to:
  /// **'自定义时长'**
  String get chargeIslandDurationSecondsTitle;

  /// No description provided for @chargeIslandOuterGlowSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'控制充电超级岛的外圈光效'**
  String get chargeIslandOuterGlowSubtitle;

  /// No description provided for @outerGlowTitle.
  ///
  /// In zh, this message translates to:
  /// **'外圈光效'**
  String get outerGlowTitle;

  /// No description provided for @bluetoothIslandOuterGlowSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'控制蓝牙超级岛的外圈光效'**
  String get bluetoothIslandOuterGlowSubtitle;

  /// No description provided for @outerGlowColorTitle.
  ///
  /// In zh, this message translates to:
  /// **'外圈光效颜色'**
  String get outerGlowColorTitle;

  /// No description provided for @hookScopeXMSF.
  ///
  /// In zh, this message translates to:
  /// **'小米服务框架'**
  String get hookScopeXMSF;

  /// No description provided for @downloadManagerSection.
  ///
  /// In zh, this message translates to:
  /// **'下载管理程序'**
  String get downloadManagerSection;

  /// No description provided for @themePageTitle.
  ///
  /// In zh, this message translates to:
  /// **'主题'**
  String get themePageTitle;

  /// No description provided for @themeSeedColorTitle.
  ///
  /// In zh, this message translates to:
  /// **'主题色'**
  String get themeSeedColorTitle;

  /// No description provided for @themeSeedColorSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'自定义应用强调色'**
  String get themeSeedColorSubtitle;

  /// No description provided for @presetColors.
  ///
  /// In zh, this message translates to:
  /// **'预设色板'**
  String get presetColors;

  /// No description provided for @themeResetColor.
  ///
  /// In zh, this message translates to:
  /// **'恢复默认'**
  String get themeResetColor;

  /// No description provided for @blurBarsTitle.
  ///
  /// In zh, this message translates to:
  /// **'毛玻璃效果'**
  String get blurBarsTitle;

  /// No description provided for @blurBarsSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'为顶栏和底栏添加模糊透明效果'**
  String get blurBarsSubtitle;

  /// No description provided for @bluetoothIslandWhitelistTitle.
  ///
  /// In zh, this message translates to:
  /// **'设备白名单'**
  String get bluetoothIslandWhitelistTitle;

  /// No description provided for @bluetoothIslandWhitelistSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'仅对白名单中的蓝牙设备显示超级岛'**
  String get bluetoothIslandWhitelistSubtitle;

  /// No description provided for @bluetoothIslandWhitelistButton.
  ///
  /// In zh, this message translates to:
  /// **'管理白名单设备'**
  String get bluetoothIslandWhitelistButton;

  /// No description provided for @bluetoothIslandWhitelistButtonSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'已选择 {count} 个设备'**
  String bluetoothIslandWhitelistButtonSubtitle(int count);

  /// No description provided for @bluetoothIslandWhitelistDialogTitle.
  ///
  /// In zh, this message translates to:
  /// **'选择蓝牙设备'**
  String get bluetoothIslandWhitelistDialogTitle;

  /// No description provided for @bluetoothIslandWhitelistEmpty.
  ///
  /// In zh, this message translates to:
  /// **'暂无已配对设备，请先在系统蓝牙中配对'**
  String get bluetoothIslandWhitelistEmpty;

  /// No description provided for @bluetoothIslandWhitelistAllHint.
  ///
  /// In zh, this message translates to:
  /// **'未开启白名单时，对所有蓝牙设备生效'**
  String get bluetoothIslandWhitelistAllHint;

  /// No description provided for @bluetoothIslandLoadDevicesFailed.
  ///
  /// In zh, this message translates to:
  /// **'获取蓝牙设备失败'**
  String get bluetoothIslandLoadDevicesFailed;

  /// No description provided for @bluetoothIslandNeedBtPermission.
  ///
  /// In zh, this message translates to:
  /// **'需要蓝牙权限才能获取设备列表'**
  String get bluetoothIslandNeedBtPermission;

  /// No description provided for @hideBehaviorTitle.
  ///
  /// In zh, this message translates to:
  /// **'隐藏行为'**
  String get hideBehaviorTitle;

  /// No description provided for @hideBehaviorDescription.
  ///
  /// In zh, this message translates to:
  /// **'控制系统场景是否允许临时隐藏超级岛。关闭某项后，会拦截对应场景的系统隐藏逻辑。'**
  String get hideBehaviorDescription;

  /// No description provided for @hideBehaviorMasterSwitch.
  ///
  /// In zh, this message translates to:
  /// **'启用隐藏行为 Hook'**
  String get hideBehaviorMasterSwitch;

  /// No description provided for @hideBehaviorMasterSwitchSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'开启后才注册隐藏行为 Hook；关闭后完全不 Hook，默认关闭'**
  String get hideBehaviorMasterSwitchSubtitle;

  /// No description provided for @hideBehaviorScreenPinning.
  ///
  /// In zh, this message translates to:
  /// **'屏幕固定'**
  String get hideBehaviorScreenPinning;

  /// No description provided for @hideBehaviorScreenPinningSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'屏幕固定激活时隐藏超级岛'**
  String get hideBehaviorScreenPinningSubtitle;

  /// No description provided for @hideBehaviorBouncerShowing.
  ///
  /// In zh, this message translates to:
  /// **'解锁界面'**
  String get hideBehaviorBouncerShowing;

  /// No description provided for @hideBehaviorBouncerShowingSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'密码、指纹等解锁界面显示时隐藏超级岛'**
  String get hideBehaviorBouncerShowingSubtitle;

  /// No description provided for @hideBehaviorFullscreen.
  ///
  /// In zh, this message translates to:
  /// **'全屏模式'**
  String get hideBehaviorFullscreen;

  /// No description provided for @hideBehaviorFullscreenSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'状态栏消失或沉浸式全屏时隐藏超级岛'**
  String get hideBehaviorFullscreenSubtitle;

  /// No description provided for @hideBehaviorScreenLocked.
  ///
  /// In zh, this message translates to:
  /// **'锁屏'**
  String get hideBehaviorScreenLocked;

  /// No description provided for @hideBehaviorScreenLockedSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'锁屏或息屏流程中隐藏超级岛'**
  String get hideBehaviorScreenLockedSubtitle;

  /// No description provided for @hideBehaviorNotificationCenter.
  ///
  /// In zh, this message translates to:
  /// **'通知中心'**
  String get hideBehaviorNotificationCenter;

  /// No description provided for @hideBehaviorNotificationCenterSubtitle.
  ///
  /// In zh, this message translates to:
  /// **'通知栏展开或下滑过渡时隐藏超级岛'**
  String get hideBehaviorNotificationCenterSubtitle;

  /// No description provided for @off.
  ///
  /// In zh, this message translates to:
  /// **'关闭'**
  String get off;

  /// No description provided for @islandTextSection.
  ///
  /// In zh, this message translates to:
  /// **'超级岛文字'**
  String get islandTextSection;

  /// No description provided for @islandTextColorTitle.
  ///
  /// In zh, this message translates to:
  /// **'超级岛文本颜色'**
  String get islandTextColorTitle;

  /// No description provided for @islandTextColorBlack.
  ///
  /// In zh, this message translates to:
  /// **'黑色'**
  String get islandTextColorBlack;

  /// No description provided for @islandTextColorFollowBackground.
  ///
  /// In zh, this message translates to:
  /// **'跟随岛背景'**
  String get islandTextColorFollowBackground;

  /// No description provided for @islandTextColorInvertBackground.
  ///
  /// In zh, this message translates to:
  /// **'反跟随岛背景'**
  String get islandTextColorInvertBackground;

  /// No description provided for @islandTextColorFollowStatusBar.
  ///
  /// In zh, this message translates to:
  /// **'跟随状态栏'**
  String get islandTextColorFollowStatusBar;

  /// No description provided for @islandTextColorInvertStatusBar.
  ///
  /// In zh, this message translates to:
  /// **'反跟随状态栏'**
  String get islandTextColorInvertStatusBar;

  /// No description provided for @islandTextColorDefault.
  ///
  /// In zh, this message translates to:
  /// **'默认'**
  String get islandTextColorDefault;

  /// No description provided for @tapToSelectImage.
  ///
  /// In zh, this message translates to:
  /// **'点击选择图片或 GIF'**
  String get tapToSelectImage;

  /// No description provided for @autoExpandNotification.
  ///
  /// In zh, this message translates to:
  /// **'自动展开通知'**
  String get autoExpandNotification;

  /// No description provided for @widthDpLabel.
  ///
  /// In zh, this message translates to:
  /// **'{width} dp'**
  String widthDpLabel(int width);
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
