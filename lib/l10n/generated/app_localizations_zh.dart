// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Chinese (`zh`).
class AppLocalizationsZh extends AppLocalizations {
  AppLocalizationsZh([String locale = 'zh']) : super(locale);

  @override
  String get navHome => '主页';

  @override
  String get navIsland => '岛';

  @override
  String get navApps => '应用';

  @override
  String get navSettings => '设置';

  @override
  String get cancel => '取消';

  @override
  String get confirm => '确认';

  @override
  String get ok => '确定';

  @override
  String get apply => '应用';

  @override
  String get noChange => '不更改';

  @override
  String get newVersionFound => '发现新版本';

  @override
  String currentVersion(String version) {
    return '当前版本：$version';
  }

  @override
  String latestVersion(String version) {
    return '最新版本：$version';
  }

  @override
  String get later => '稍后再说';

  @override
  String get goUpdate => '前往更新';

  @override
  String get sponsorSupport => '赞助支持';

  @override
  String get sponsorAuthor => '赞助作者';

  @override
  String get donorList => '捐赠名单';

  @override
  String get documentation => '文档';

  @override
  String versionUpdatedTitle(String version) {
    return '已更新至 $version';
  }

  @override
  String get versionUpdatedContent => '更新后请重启作用域';

  @override
  String get versionUpdatedChangelog => '更新日志：点击查看';

  @override
  String get versionUpdatedStarHint => '如果觉得软件好用请帮忙点一个免费的Star';

  @override
  String get restartScope => '重启作用域';

  @override
  String get systemUI => '系统界面';

  @override
  String get downloadManager => '下载管理器';

  @override
  String get xmsf => '小米服务框架';

  @override
  String get notificationTest => '通知测试';

  @override
  String get sendTestNotification => '发送测试通知';

  @override
  String get customTestNotification => '自定义测试通知';

  @override
  String get customTestTitle => '标题';

  @override
  String get customTestTitleHint => '留空使用默认标题';

  @override
  String get customTestContent => '内容';

  @override
  String get customTestContentHint => '留空使用默认内容';

  @override
  String get clearPreviousNotification => '清除之前通知';

  @override
  String get clearPreviousNotificationSubtitle => '发送前先取消已有的超级岛通知';

  @override
  String get enableFloatNotificationSubtitle => '收到通知后自动展开为焦点通知';

  @override
  String get notes => '注意事项';

  @override
  String get detectingModuleStatus => '正在检测模块状态...';

  @override
  String get moduleStatus => '模块状态';

  @override
  String get activated => '已激活';

  @override
  String get notActivated => '未激活';

  @override
  String get enableInLSPosed => '请在 LSPosed 中启用本模块';

  @override
  String get enableSystemUiScopeInLSPosed => '请在 LSPosed 作用域勾选系统界面';

  @override
  String lsposedApiVersion(int version) {
    return 'LSPosed API: $version';
  }

  @override
  String get updateLSPosedRequired => '请更新 LSPosed 版本';

  @override
  String get systemNotSupported => '系统不支持';

  @override
  String systemNotSupportedSubtitle(int version) {
    return '当前系统不支持超级岛功能（协议版本 $version，需要版本 3）';
  }

  @override
  String restartFailed(String message) {
    return '重启失败：$message';
  }

  @override
  String get restartRootRequired => '请检查是否已给予本应用 ROOT 权限';

  @override
  String get note1 => '1.使用前务必查看软件右上角的使用教程';

  @override
  String get note2 => '2.大部分配置支持热重载，如遇异常请重启作用域';

  @override
  String get note3 => '3.LSPosed 管理器中激活后，必须重启相关作用域软件';

  @override
  String get note4 => '4.此页面仅用于测试是否支持超级岛及光效，并不代表实际效果';

  @override
  String get note5 => '5.下载上岛请手动启用“下载管理程序”，推荐《下载》模板';

  @override
  String get behaviorSection => '行为';

  @override
  String get defaultConfigSection => '默认配置';

  @override
  String get appearanceSection => '外观';

  @override
  String get configSection => '配置';

  @override
  String get aboutSection => '关于';

  @override
  String get keepFocusNotifTitle => '下载管理器暂停后保留焦点通知';

  @override
  String get keepFocusNotifSubtitle => '显示一条通知，点击以继续下载，可能导致状态不同步';

  @override
  String get unlockAllFocusTitle => '移除焦点通知白名单';

  @override
  String get unlockAllFocusSubtitle => '允许所有应用发送焦点通知，无需系统授权';

  @override
  String get unlockFocusAuthTitle => '移除焦点通知签名验证';

  @override
  String get unlockFocusAuthSubtitle => 'XMSF（小米服务框架）去除校验';

  @override
  String get checkUpdateOnLaunchTitle => '启动时检查更新';

  @override
  String get checkUpdateOnLaunchSubtitle => '启动应用时自动检查是否有新版本';

  @override
  String get debugLogTitle => '显示调试日志';

  @override
  String get debugLogSubtitle => '开启后输出 Hook 调试日志，关闭时仅保留警告和错误日志';

  @override
  String get showWelcomeTitle => '显示启动欢迎语';

  @override
  String get showWelcomeSubtitle => '应用启动时在超级岛显示欢迎信息';

  @override
  String get openOnboardingTitle => '打开初始引导';

  @override
  String get openOnboardingSubtitle => '重新查看欢迎与快速上手流程';

  @override
  String get interactionHapticsTitle => '交互触感';

  @override
  String get interactionHapticsSubtitle => '为开关、滑块和按钮启用 Hyper 定制震感反馈';

  @override
  String get checkUpdate => '检查更新';

  @override
  String get alreadyLatest => '已是最新版本';

  @override
  String get roundIconTitle => '图标圆角';

  @override
  String get roundIconSubtitle => '为通知图标添加圆角效果';

  @override
  String get marqueeChannelTitle => '消息滚动';

  @override
  String get marqueeAutoHideTitle => '滚动后隐藏岛';

  @override
  String get marqueeAutoHideSubtitle => '消息滚动达到指定次数后隐藏当前岛';

  @override
  String get marqueeAutoHideOnce => '滚动1次';

  @override
  String get marqueeAutoHideTwice => '滚动2次';

  @override
  String get marqueeSpeedTitle => '滚动速度';

  @override
  String marqueeSpeedLabel(int speed) {
    return '$speed 像素/秒';
  }

  @override
  String get bigIslandMaxWidthTitle => '最大宽度';

  @override
  String get bigIslandMinWidthTitle => '最小宽度';

  @override
  String get testNotifTooltip => '发送测试通知';

  @override
  String get themeModeTitle => '颜色模式';

  @override
  String get themeModeSystem => '跟随系统';

  @override
  String get themeModeLight => '浅色';

  @override
  String get themeModeDark => '深色';

  @override
  String get languageTitle => '语言';

  @override
  String get languageAuto => '跟随系统';

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
  String get exportToFile => '导出到文件';

  @override
  String get exportToFileSubtitle => '将配置保存为 JSON 文件';

  @override
  String get exportToClipboard => '导出到剪贴板';

  @override
  String get exportToClipboardSubtitle => '将配置复制为 JSON 文本';

  @override
  String get exportConfig => '导出配置';

  @override
  String get exportConfigSubtitle => '选择导出到文件或剪贴板';

  @override
  String get importFromFile => '从文件导入';

  @override
  String get importFromFileSubtitle => '从 JSON 文件恢复配置';

  @override
  String get importFromClipboard => '从剪贴板导入';

  @override
  String get importFromClipboardSubtitle => '从剪贴板中的 JSON 文本恢复配置';

  @override
  String get importConfig => '导入配置';

  @override
  String get importConfigSubtitle => '选择从文件或剪贴板导入';

  @override
  String get qqGroup => 'QQ 交流群';

  @override
  String get restartScopeApp => '请重启作用域应用以使设置生效';

  @override
  String get groupNumberCopied => '群号已复制到剪贴板';

  @override
  String exportedTo(String path) {
    return '已导出到：$path';
  }

  @override
  String exportFailed(String error) {
    return '导出失败：$error';
  }

  @override
  String get configCopied => '配置已复制到剪贴板';

  @override
  String importSuccess(int count) {
    return '导入成功，共 $count 项配置，请重启应用生效';
  }

  @override
  String importFailed(String error) {
    return '导入失败：$error';
  }

  @override
  String get appAdaptation => '应用适配';

  @override
  String get toastAdaptation => 'Toast 适配';

  @override
  String get adaptationModeNotification => '通知';

  @override
  String get adaptationModeToast => 'Toast';

  @override
  String toastEnabledAppsCount(Object count) {
    return '已启用 $count 个应用的 Toast 拦截';
  }

  @override
  String toastEnabledAppsCountWithSystem(Object count) {
    return '已启用 $count 个应用的 Toast 拦截（含系统应用）';
  }

  @override
  String selectedAppsCount(int count) {
    return '已选 $count 个应用';
  }

  @override
  String get cancelSelection => '取消选择';

  @override
  String get deselectAll => '全不选';

  @override
  String get selectAll => '全选';

  @override
  String get batchChannelSettings => '批量设置渠道配置';

  @override
  String get selectEnabledApps => '选择已开启应用';

  @override
  String get batchEnable => '批量开启';

  @override
  String get batchDisable => '批量关闭';

  @override
  String get multiSelect => '多选';

  @override
  String get showSystemApps => '显示系统应用';

  @override
  String get refreshList => '刷新列表';

  @override
  String get enableAll => '一键开启全部';

  @override
  String get disableAll => '一键关闭全部';

  @override
  String enabledAppsCount(int count) {
    return '已启用 $count 个应用的超级岛';
  }

  @override
  String enabledAppsCountWithSystem(int count) {
    return '已启用 $count 个应用的超级岛（含系统应用）';
  }

  @override
  String get searchApps => '搜索应用名或包名';

  @override
  String get noAppsFound => '没有找到已安装的应用\n请检查获取应用列表权限是否开启';

  @override
  String get noMatchingApps => '没有匹配的应用';

  @override
  String applyToSelectedAppsChannels(int count) {
    return '将应用到已选 $count 个应用的已启用渠道';
  }

  @override
  String get applyingConfig => '正在应用配置…';

  @override
  String progressApps(int done, int total) {
    return '$done / $total 个应用';
  }

  @override
  String batchApplied(int count) {
    return '已批量应用到 $count 个应用';
  }

  @override
  String get cannotReadChannels => '无法读取通知渠道';

  @override
  String get rootRequiredMessage => '读取通知渠道需要 ROOT 权限。\n请确认已授予本应用 ROOT 权限后重试。';

  @override
  String get enableAllChannels => '启用全部渠道';

  @override
  String get noChannelsFound => '未找到通知渠道';

  @override
  String get noChannelsFoundSubtitle => '该应用尚未创建通知渠道，或无法读取';

  @override
  String allChannelsActive(int count) {
    return '对全部 $count 个渠道生效';
  }

  @override
  String selectedChannels(int selected, int total) {
    return '已选 $selected / $total 个渠道';
  }

  @override
  String allChannelsDisabled(int count) {
    return '全部 $count 个渠道（已停用）';
  }

  @override
  String get appDisabledBanner => '应用总开关已关闭，以下渠道设置均不生效';

  @override
  String channelImportance(String importance, String id) {
    return '重要性：$importance  ·  $id';
  }

  @override
  String get channelSettings => '渠道设置';

  @override
  String get toastForwardTitle => '转发标准 Toast';

  @override
  String get toastForwardSubtitle =>
      '将此应用的标准 Toast 文本转为 HyperIsland 焦点通知与超级岛代发';

  @override
  String get toastBlockOriginalTitle => '拦截原始 Toast';

  @override
  String get toastBlockOriginalSubtitle => '转发后同时拦截此应用原始标准 Toast 弹窗';

  @override
  String get toastShowNotificationTitle => '显示为通知';

  @override
  String get toastShowNotificationSubtitle => '开启后此转发内容会在通知中心保留为可见通知';

  @override
  String get toastShowIslandIconTitle => '显示超级岛图标';

  @override
  String get toastShowIslandIconSubtitle => '控制转发 Toast 时大岛左侧是否显示图标';

  @override
  String get toastStandardOnlyHint => '仅处理标准文本 Toast，自定义 Toast 视图将被忽略。';

  @override
  String get importanceNone => '无';

  @override
  String get importanceMin => '极低';

  @override
  String get importanceLow => '低';

  @override
  String get importanceDefault => '默认';

  @override
  String get importanceHigh => '高';

  @override
  String get importanceUnknown => '未知';

  @override
  String applyToEnabledChannels(int count) {
    return '将应用到已启用的 $count 个渠道';
  }

  @override
  String applyToAllChannels(int count) {
    return '将应用到全部 $count 个渠道';
  }

  @override
  String get templateDownloadName => '下载';

  @override
  String get templateNotificationIslandName => '通知超级岛';

  @override
  String get templateNotificationIslandLiteName => '通知超级岛 | 精简';

  @override
  String get templateDownloadLiteName => '下载|Lite';

  @override
  String get islandSection => '岛';

  @override
  String get islandEnabledLabel => '启用岛';

  @override
  String get template => '模板';

  @override
  String get rendererLabel => '样式';

  @override
  String get rendererImageTextWithButtons4Name => '新图文组件 + 底部文本按钮';

  @override
  String get rendererCoverInfoName => '封面组件 + 自动换行';

  @override
  String get rendererImageTextWithRightTextButtonName => '新图文组件 + 右侧文本按钮';

  @override
  String get rendererImageTextWithProgressName => 'IM图文组件 + 进度条组件';

  @override
  String get islandIcon => '超级岛图标';

  @override
  String get islandIconLabel => '大岛图标';

  @override
  String get islandIconLabelSubtitle => '开启后显示超级岛的大图标（小岛不受影响）';

  @override
  String get focusIconLabel => '焦点图标';

  @override
  String get focusExpressionCustomizationSection => '焦点高级自定义';

  @override
  String get islandExpressionCustomizationSection => '超级岛高级自定义';

  @override
  String get aodSection => '息屏显示';

  @override
  String get expandCustomization => '展开';

  @override
  String get collapseCustomization => '收起';

  @override
  String get availablePlaceholdersLabel => '可用占位符(点击复制)';

  @override
  String get expressionFunctionsLabel => '表达式函数';

  @override
  String get focusTitleExprLabel => '焦点标题表达式';

  @override
  String get focusContentExprLabel => '焦点正文表达式';

  @override
  String get focusIconSourceLabel => '焦点图标来源';

  @override
  String get focusPicProfileSourceLabel => '头像图标来源';

  @override
  String get focusAppIconPkgLabel => '应用图标包名';

  @override
  String get focusSecondaryIconSourceLabel => '副图标来源';

  @override
  String get chatTitleColorLabel => '聊天标题颜色';

  @override
  String get chatTitleColorDarkLabel => '聊天标题暗色';

  @override
  String get chatContentColorLabel => '聊天正文颜色';

  @override
  String get chatContentColorDarkLabel => '聊天正文暗色';

  @override
  String get progressColorLabel => '进度条颜色';

  @override
  String get progressBarColorLabel => '进度条颜色';

  @override
  String get progressBarColorEndLabel => '进度条结束颜色';

  @override
  String get placeholderTitle => '通知标题';

  @override
  String get placeholderSubtitle => '通知正文';

  @override
  String get placeholderSubtitleOrTitle => '正文（空则标题）';

  @override
  String get placeholderPkg => '包名';

  @override
  String get placeholderChannelId => '渠道 ID';

  @override
  String get placeholderProgress => '通知进度';

  @override
  String get placeholderStateLabel => '状态文本';

  @override
  String get placeholderProgressText => '进度文本';

  @override
  String get placeholderAiLeft => 'AI 左侧文本';

  @override
  String get placeholderAiRight => 'AI 右侧文本';

  @override
  String get placeholderRawTitle => '原始标题';

  @override
  String get placeholderRawSubtitle => '原始正文';

  @override
  String get placeholderRawSubtitleOrTitle => '原始正文（空则标题）';

  @override
  String get islandLeftExprLabel => '超级岛左侧表达式';

  @override
  String get islandRightExprLabel => '超级岛右侧表达式';

  @override
  String get aodTextSwitchLabel => 'AOD文本开关';

  @override
  String get aodTextExprLabel => 'AOD文本表达式';

  @override
  String get aodIconSourceLabel => 'AOD图标来源';

  @override
  String get focusNotificationLabel => '焦点通知';

  @override
  String get hideNotificationLabel => '隐藏通知';

  @override
  String get hideNotificationLabelSubtitle => '开启后仅显示超级岛，不显示通知栏焦点通知';

  @override
  String get preserveStatusBarSmallIconLabel => '状态栏图标';

  @override
  String get restoreLockscreenTitle => '锁屏通知复原';

  @override
  String get restoreLockscreenSubtitle => '锁屏时跳过焦点通知处理，保持原始通知隐私行为';

  @override
  String get firstFloatLabel => '初次展开';

  @override
  String get updateFloatLabel => '更新展开';

  @override
  String get autoDisappear => '自动消失';

  @override
  String get seconds => '秒';

  @override
  String get highlightColorLabel => '高亮颜色';

  @override
  String get dynamicHighlightColorLabel => '高亮动态取色';

  @override
  String get dynamicHighlightColorLabelSubtitle => '开启后默认使用图标自动取色';

  @override
  String get followDynamicColorLabel => '跟随动态取色';

  @override
  String get dynamicHighlightModeDark => '暗';

  @override
  String get dynamicHighlightModeDarker => '更暗';

  @override
  String get outerGlowLabel => '外圈光效';

  @override
  String get forceOuterGlowLabel => '全局启用';

  @override
  String get forceFocusOuterGlowSubtitle => '开启后未匹配到的焦点通知强制启用光效';

  @override
  String get forceIslandOuterGlowSubtitle => '开启后未匹配到的岛强制启用光效';

  @override
  String get outEffectColorLabel => '外圈光效颜色';

  @override
  String get highlightColorHint => '#RRGGBB 格式，留空使用默认';

  @override
  String get actionBgColorLabel => '按钮背景色';

  @override
  String get actionBgColorDarkLabel => '按钮背景色（暗色）';

  @override
  String get actionTitleColorLabel => '按钮文字颜色';

  @override
  String get actionTitleColorDarkLabel => '按钮文字颜色（暗色）';

  @override
  String get action1BgColorLabel => '按钮1背景色';

  @override
  String get action1BgColorDarkLabel => '按钮1背景色（暗色）';

  @override
  String get action1TitleColorLabel => '按钮1文字颜色';

  @override
  String get action1TitleColorDarkLabel => '按钮1文字颜色（暗色）';

  @override
  String get action2BgColorLabel => '按钮2背景色';

  @override
  String get action2BgColorDarkLabel => '按钮2背景色（暗色）';

  @override
  String get action2TitleColorLabel => '按钮2文字颜色';

  @override
  String get action2TitleColorDarkLabel => '按钮2文字颜色（暗色）';

  @override
  String get textHighlightLabel => '文本高亮';

  @override
  String get narrowFontLabel => '窄字体';

  @override
  String get showLeftHighlightLabel => '左侧文本高亮';

  @override
  String get showRightHighlightLabel => '右侧文本高亮';

  @override
  String get showLeftHighlightShort => '左侧';

  @override
  String get showRightHighlightShort => '右侧';

  @override
  String get colorHue => '色相';

  @override
  String get colorSaturation => '饱和度';

  @override
  String get colorBrightness => '亮度';

  @override
  String get colorOpacity => '透明度';

  @override
  String get onlyEnabledChannels => '仅应用到已启用渠道';

  @override
  String enabledChannelsCount(int enabled, int total) {
    return '已启用 $enabled / $total 个渠道';
  }

  @override
  String get iconModeAuto => '自动';

  @override
  String get iconModeNotifSmall => '通知小图标';

  @override
  String get iconModeNotifLarge => '通知大图标';

  @override
  String get iconModeAppIcon => '应用图标';

  @override
  String get optDefault => '默认';

  @override
  String get optDefaultOn => '默认（开启）';

  @override
  String get optDefaultOff => '默认（关闭）';

  @override
  String get optOn => '开启';

  @override
  String get optOff => '关闭';

  @override
  String get errorInvalidFormat => '配置格式无效';

  @override
  String get errorNoStorageDir => '无法获取存储目录';

  @override
  String get errorNoFileSelected => '未选择文件';

  @override
  String get errorNoFilePath => '无法获取文件路径';

  @override
  String get errorEmptyClipboard => '剪贴板为空';

  @override
  String get navBlacklist => '通知黑名单';

  @override
  String get navBlacklistSubtitle => '启动黑名单应用时，停用焦点通知的自动展开功能';

  @override
  String get presetGamesTitle => '一键过滤热门游戏';

  @override
  String presetGamesSuccess(int count) {
    return '已从模板中添加 $count 款已安装游戏至黑名单';
  }

  @override
  String blacklistedAppsCount(int count) {
    return '已拦截 $count 个应用的焦点通知';
  }

  @override
  String blacklistedAppsCountWithSystem(int count) {
    return '已拦截 $count 个应用的焦点通知（含系统应用）';
  }

  @override
  String get firstFloatLabelSubtitle => '超级岛初次收到通知后是否展开为焦点通知';

  @override
  String get updateFloatLabelSubtitle => '超级岛更新后是否展开通知';

  @override
  String get marqueeChannelTitleSubtitle => '超级岛消息过长是否滚动显示';

  @override
  String get focusNotificationLabelSubtitle => '替换通知为焦点通知（关闭后显示原始通知）';

  @override
  String get preserveStatusBarSmallIconLabelSubtitle => '焦点通知打开时，是否强制保留状态栏小图标';

  @override
  String get fullscreenBehaviorTitle => '全屏时行为';

  @override
  String get fullscreenBehaviorSubtitle => '检测到横屏/全屏时的通知处理策略';

  @override
  String get fullscreenBehaviorOff => '默认';

  @override
  String get fullscreenBehaviorFallback => '回退普通通知';

  @override
  String get filterRulesTitle => '过滤规则';

  @override
  String get filterRulesOrderTitle => '按顺序命中第一条规则';

  @override
  String get filterRuleDnd => '勿扰';

  @override
  String get filterRuleFullscreen => '全屏';

  @override
  String get filterRuleLandscape => '横屏';

  @override
  String get dndBehaviorTitle => '勿扰时';

  @override
  String get fullscreenRuleTitle => '全屏时';

  @override
  String get landscapeRuleTitle => '横屏时';

  @override
  String get behaviorPreviewDefault => '命中时不处理，继续使用默认行为';

  @override
  String get behaviorPreviewSuppress => '命中时回退为普通通知';

  @override
  String get behaviorPreviewSmallOnly => '命中时只显示小岛，不自动展开';

  @override
  String get behaviorPreviewExpand => '命中时自动展开通知';

  @override
  String get aiConfigSection => 'AI 增强';

  @override
  String get aiConfigTitle => 'AI 通知摘要';

  @override
  String get aiConfigSubtitleEnabled => '已启用 · 点击配置 AI 参数';

  @override
  String get aiConfigSubtitleDisabled => '已关闭 · 点击进行配置';

  @override
  String get aiEnabledTitle => '启用 AI 摘要';

  @override
  String get aiEnabledSubtitle => '由 AI 生成超级岛左右文本，超时或失败时自动回退';

  @override
  String get aiApiSection => 'API 参数';

  @override
  String get aiUrlLabel => 'API 地址（必须完整）';

  @override
  String get aiUrlHint => 'https://api.openai.com/v1/chat/completions';

  @override
  String get aiApiKeyLabel => 'API 密钥';

  @override
  String get aiApiKeyHint => 'sk-...';

  @override
  String get aiModelLabel => '模型';

  @override
  String get aiModelHint => 'gpt-4o-mini';

  @override
  String get aiModelPickerTitle => '选择模型';

  @override
  String get aiModelPickerSearchHint => '搜索模型…';

  @override
  String get aiModelPickerEmpty => '未找到模型';

  @override
  String get aiModelPickerRetry => '重试';

  @override
  String get aiModelPickerClose => '关闭';

  @override
  String get aiModelPickerFetchError => '加载模型列表失败';

  @override
  String get aiPromptLabel => '系统提示词';

  @override
  String get aiPromptHint => '留空则使用默认提示词';

  @override
  String get aiPromptInUserTitle => '提示词放在用户消息';

  @override
  String get aiPromptInUserSubtitle => '某些模型不支持系统指令，开启后将提示词放在用户消息中';

  @override
  String get aiTimeoutTitle => 'AI 响应超时';

  @override
  String aiTimeoutLabel(int seconds) {
    return '${seconds}s';
  }

  @override
  String get aiTemperatureTitle => '采样温度 (Temperature)';

  @override
  String get aiTemperatureSubtitle => '控制回答的随机性。0 为准确，1 则更具创意';

  @override
  String get aiMaxTokensTitle => '最大 Token 数 (Max Tokens)';

  @override
  String get aiMaxTokensSubtitle => '限制 AI 生成回答的最大长度';

  @override
  String get aiDefaultPromptFull =>
      '留空使用默认提示词：根据通知信息，提取关键信息，左右分别不超过 6 汉字 12 字符';

  @override
  String get aiTestButton => '测试连接';

  @override
  String get aiTestUrlEmpty => '请先填写 API 地址';

  @override
  String get aiLastLogTitle => '最近一次 AI 请求日志';

  @override
  String get aiLastLogSubtitle => '测试连接和通知触发的 AI 请求都会显示在这里';

  @override
  String get aiLastLogEmpty => '还没有可显示的 AI 请求日志';

  @override
  String get aiLastLogSourceLabel => '来源';

  @override
  String get aiLastLogTimeLabel => '时间';

  @override
  String get aiLastLogStatusLabel => '状态';

  @override
  String get aiLastLogDurationLabel => '耗时';

  @override
  String get aiLastLogSourceNotification => '通知触发';

  @override
  String get aiLastLogSourceSettingsTest => '设置页测试';

  @override
  String get aiLastLogRendered => '渲染';

  @override
  String get aiLastLogRaw => '原始';

  @override
  String get aiLastLogCopy => '复制日志';

  @override
  String get aiLastLogCopied => 'AI 请求日志已复制';

  @override
  String get aiLastLogRequest => '请求';

  @override
  String get aiLastLogResponse => '回复';

  @override
  String get aiLastLogUsage => 'Token 用量';

  @override
  String get aiLastLogMessages => '消息';

  @override
  String get aiLastLogError => '错误';

  @override
  String get aiLastLogHttpCode => 'HTTP 状态';

  @override
  String get aiLastLogLeftText => '左侧文本';

  @override
  String get aiLastLogRightText => '右侧文本';

  @override
  String get aiLastLogAssistantContent => '模型回复内容';

  @override
  String get aiConfigSaveButton => '保存';

  @override
  String get aiConfigSaved => 'AI 配置已保存';

  @override
  String get aiConfigTips =>
      'AI 将收到通知的应用包名、标题和正文，返回左侧（来源）和右侧（内容）短文本。支持兼容 OpenAI 格式的接口（如 DeepSeek、Claude 等）。超过 3 秒未响应时自动回退到默认逻辑。';

  @override
  String get templateAiNotificationIslandName => 'AI 通知超级岛';

  @override
  String get aiPromptDefault => '根据通知信息，提取关键信息，左右分别不超过 6 汉字 12 字符';

  @override
  String get aiDefaultNotificationText => '[外卖]，您的外卖到了，送至门口外卖柜';

  @override
  String get aiTestSampleUserContent => '请直接回复：测试成功';

  @override
  String aiNotificationUserContent(String content) {
    return '应用包名：com.example.app\n标题：测试通知\n正文：$content';
  }

  @override
  String get aiJsonOnlyInstruction => '仅返回如下 JSON，不得包含任何其他文字或代码块：';

  @override
  String get aiJsonLeftDescription => '左侧文本（谁发的）';

  @override
  String get aiJsonRightDescription => '右侧文本（总结）';

  @override
  String get aiInvalidJsonError => 'AI 返回格式错误，需要包含 left 和 right 字段的 JSON';

  @override
  String get aiEmptyJsonError => 'AI 返回为空，需要包含 left 和 right 字段的 JSON';

  @override
  String get aiNotificationContentLabel => '通知内容';

  @override
  String get aiTestNotificationTitle => '测试通知';

  @override
  String get aiNotificationSent => '通知已发送';

  @override
  String get aiAiNotificationSent => 'AI 通知已发送';

  @override
  String get aiSendNotificationButton => '发送通知';

  @override
  String get aiSendAiNotificationButton => '发送 AI 通知';

  @override
  String get hideDesktopIconTitle => '隐藏桌面图标';

  @override
  String get hideDesktopIconSubtitle => '隐藏启动器中的应用图标，隐藏后可通过 LSPosed 管理器打开';

  @override
  String get filterRulesSection => '过滤规则';

  @override
  String get foregroundRulesTab => '前台规则';

  @override
  String get foregroundExclusionsTab => '排除应用';

  @override
  String get foregroundRulesDescription => '前台应用启动时，设置超级岛行为。';

  @override
  String get foregroundExclusionsDescription => '排除列表内应用的通知不受前台规则限制。';

  @override
  String get hideSystemApps => '隐藏系统应用';

  @override
  String get restoreDefaultConfig => '恢复默认配置';

  @override
  String resetDefaultConfigSuccess(int count) {
    return '已恢复默认配置，共重置 $count 个应用';
  }

  @override
  String get sceneActionDefault => '默认';

  @override
  String get sceneActionSmallOnly => '关闭展开';

  @override
  String get sceneActionExpand => '自动展开';

  @override
  String get sceneActionSuppress => '回退';

  @override
  String get filterModeLabel => '过滤模式';

  @override
  String get filterModeBlacklist => '黑名单';

  @override
  String get filterModeWhitelist => '白名单';

  @override
  String get filterModeBlacklistDesc => '匹配关键词的通知将被过滤';

  @override
  String get filterModeWhitelistDesc => '仅匹配关键词的通知会显示';

  @override
  String get whitelistKeywordsLabel => '白名单关键词';

  @override
  String get blacklistKeywordsLabel => '黑名单关键词';

  @override
  String get addKeyword => '添加关键词';

  @override
  String get keywordHint => '输入关键词';

  @override
  String get removeKeyword => '移除';

  @override
  String get keywordFilterPriority => '白名单优先：仅白名单匹配的通知显示，但黑名单仍可否决';

  @override
  String get exportChannelsToClipboard => '导出渠道设置';

  @override
  String get importChannelsFromClipboard => '导入渠道设置';

  @override
  String get exportChannelsSuccess => '渠道设置已复制到剪贴板';

  @override
  String importChannelsSuccess(int count) {
    return '导入成功，共 $count 个渠道设置已恢复';
  }

  @override
  String importChannelsPartialSuffix(int total, int matched) {
    return '（共 $total 个，已匹配 $matched 个）';
  }

  @override
  String get importErrorEmptyClipboard => '剪贴板为空，请先复制渠道设置数据';

  @override
  String get importErrorNotJson => '剪贴板内容不是有效的 JSON 数据';

  @override
  String get importErrorMissingChannels => '数据格式不正确，缺少渠道列表';

  @override
  String get importErrorNoMatch => '没有与当前应用匹配的渠道，请确认数据来源正确';

  @override
  String get importErrorUnknown => '导入失败，请检查剪贴板数据是否正确';

  @override
  String get mediaNotificationTitle => '媒体通知';

  @override
  String get mediaNotificationDisabledSubtitle => '关闭后直接删除整条媒体通知';

  @override
  String get normalNotificationTitle => '普通通知';

  @override
  String get normalNotificationSubtitle => '开启后移除媒体字段，按普通通知处理';

  @override
  String get channelSettingsUnmodified => '未修改';

  @override
  String get restoreDefault => '恢复默认';

  @override
  String get islandDimenSection => '岛尺寸设置';

  @override
  String get islandDimenHeight => '岛高度';

  @override
  String get islandTopOffset => '距屏幕顶部';

  @override
  String get followSystem => '跟随系统';

  @override
  String get islandDimenMiniY => '垂直位置';

  @override
  String get islandDimenMiniYHint => '0=跟随系统';

  @override
  String get islandBgSection => '岛背景设置';

  @override
  String get islandBgSmallTitle => '小岛背景图';

  @override
  String get islandBgBigTitle => '大岛背景图';

  @override
  String get islandBgExpandTitle => '焦点通知背景图';

  @override
  String get islandBgNotSet => '未设置';

  @override
  String get islandBgCornerRadius => '圆角半径';

  @override
  String get islandBgCornerRadiusHint => '0=跟随系统';

  @override
  String get islandBgImageSelected => '背景图片已保存';

  @override
  String get islandBgImageDeleted => '背景图片已删除';

  @override
  String get islandBgDeleteFailed => '删除失败';

  @override
  String islandBgEditTitle(String type) {
    return '编辑$type背景';
  }

  @override
  String get islandBgBlurLabel => '模糊';

  @override
  String get islandBgBrightnessLabel => '亮度';

  @override
  String get islandBgOpacityLabel => '不透明度';

  @override
  String get islandBgDefault => '默认';

  @override
  String get keepIslandTitle => '常驻超级岛';

  @override
  String get keepIslandSubtitle => '显示一条空白通知使岛始终可见';

  @override
  String get keepIslandAutoHideTitle => '自动隐藏';

  @override
  String get keepIslandAutoHideSubtitle => '通知到来时自动隐藏空白岛，通知消失后自动恢复';

  @override
  String get keepIslandHideLandscapeTitle => '横屏时隐藏';

  @override
  String get keepIslandHideLandscapeSubtitle => '横屏时自动隐藏常驻岛，回到竖屏时恢复';

  @override
  String get keepIslandHighlightColorTitle => '高亮颜色';

  @override
  String get keepIslandHighlightColorSubtitle => '自定义常驻岛的高亮文字颜色';

  @override
  String get keepIslandLeftContentTitle => '岛左边内容';

  @override
  String get keepIslandRightContentTitle => '岛右边内容';

  @override
  String get keepIslandPlaceholdersTitle => '可用占位符';

  @override
  String keepIslandPlaceholdersDescription(
    String batteryLevel,
    String cpuUsage,
  ) {
    return '可在左右内容中直接输入文本或表达式，例如：电量 $batteryLevel、CPU $cpuUsage。点击标签复制。';
  }

  @override
  String keepIslandPlaceholderCopied(String placeholder) {
    return '已复制 $placeholder';
  }

  @override
  String get keepIslandDefaultEmpty => '默认空';

  @override
  String keepIslandContentHint(String placeholder) {
    return '默认空，可输入文本或 $placeholder';
  }

  @override
  String get clear => '清空';

  @override
  String get save => '保存';

  @override
  String get islandOtherSection => '其他';

  @override
  String get miscSection => '杂项';

  @override
  String get onboardingEntryTitle => '打开初始引导';

  @override
  String get onboardingEntrySubtitle => '重新查看欢迎与快速上手流程';

  @override
  String get onboardingAppName => 'HyperIsland';

  @override
  String get onboardingWelcomeTitle => '欢迎使用 HyperIsland';

  @override
  String get onboardingWelcomeSubtitle => '简洁、快速地配置你的超级岛体验';

  @override
  String get onboardingEnvironmentTitle => '环境检测';

  @override
  String get onboardingEnvironmentSubtitle => '确认模块权限状态';

  @override
  String get onboardingNotificationStyleTitle => '选择通知样式';

  @override
  String get onboardingNotificationStyleSubtitle => '选择你更喜欢的默认通知展示方式';

  @override
  String get onboardingOriginalNotificationLabel => '普通通知';

  @override
  String get onboardingFinishTitle => '一切就绪';

  @override
  String get onboardingFinishSubtitle => '完成引导后，你可以到设置继续调整细节';

  @override
  String onboardingStepLabel(int current, int total) {
    return '第$current步 / 共$total步';
  }

  @override
  String get onboardingPrevious => '上一步';

  @override
  String get onboardingNext => '下一步';

  @override
  String get onboardingDone => '开始使用';

  @override
  String get onboardingStatusTitle => '状态检测';

  @override
  String get onboardingRetry => '重试';

  @override
  String get onboardingLsposedStatus => 'LSPosed 激活状态';

  @override
  String get onboardingRootStatus => 'Root 权限';

  @override
  String get onboardingAppListStatus => '应用列表权限';

  @override
  String get onboardingProtocolStatus => '系统协议版本';

  @override
  String get onboardingAndroidStatus => '安卓版本';

  @override
  String get onboardingUnsupportedSystem => '不支持当前系统';

  @override
  String get onboardingAndroid15Limited => 'A15系统支持有限';

  @override
  String get onboardingMissingPermissionTitle => '缺少必要权限';

  @override
  String get onboardingMissingPermissionMessage => '模块可能无法正常工作';

  @override
  String get onboardingDialogClose => '关闭';

  @override
  String get onboardingDialogContinue => '继续';

  @override
  String get backupRestoreSection => '备份与恢复';

  @override
  String get hookExtensionSection => 'Hook拓展';

  @override
  String get hookScopeSettings => '系统设置';

  @override
  String get settingsHomeEntryTitle => '系统设置入口';

  @override
  String get settingsHomeEntrySubtitle => '在系统设置首页显示 HyperIsland 入口';

  @override
  String get xposedScopeRequestFailed => '作用域申请失败，请确认模块已在 LSPosed 中启用';

  @override
  String get hookScopeSystemUI => '系统界面';

  @override
  String get smoothIslandTitle => '平滑超级岛';

  @override
  String get smoothIslandSubtitle => '使用连续曲率胶囊优化超级岛轮廓，关闭后需重启作用域以完全卸载 Hook';

  @override
  String get smoothIslandSmoothingTitle => '平滑强度';

  @override
  String get bluetoothIslandStatusEnabled => '已开启';

  @override
  String get bluetoothIslandStatusDisabled => '已关闭';

  @override
  String get bluetoothIslandTitle => '蓝牙超级岛';

  @override
  String bluetoothIslandSubtitle(String status) {
    return '$status · 监听蓝牙设备连接和断开，由 SystemUI 代发超级岛';
  }

  @override
  String get bluetoothIslandSettingsTitle => '蓝牙超级岛设置';

  @override
  String get bluetoothIslandEnableTitle => '启用蓝牙超级岛';

  @override
  String get bluetoothIslandEnableSubtitle => '关闭后重启 SystemUI 生效，且不会注册蓝牙 Hook';

  @override
  String get bluetoothIslandShowDeviceNameTitle => '显示设备名称';

  @override
  String get bluetoothIslandShowDeviceNameSubtitle => '连接时右侧先显示设备名称，之后再显示连接状态';

  @override
  String get bluetoothIslandDisplayDurationTitle => '显示时长';

  @override
  String get chargeIslandTitle => '充电超级岛';

  @override
  String chargeIslandSubtitle(String status) {
    return '$status · 替换充电超级岛中的功率或电量片段';
  }

  @override
  String get chargeIslandSettingsTitle => '充电超级岛设置';

  @override
  String get chargeIslandEnableTitle => '启用充电超级岛 Hook';

  @override
  String get chargeIslandEnableSubtitle => '关闭后重启 SystemUI 生效，Hook 将完全旁路';

  @override
  String get chargeIslandLeftModeTitle => '左侧行为';

  @override
  String get chargeIslandRightModeTitle => '右侧行为';

  @override
  String get chargeIslandModeDefault => '默认';

  @override
  String get chargeIslandModePower => '功率';

  @override
  String get chargeIslandModeVoltage => '电压';

  @override
  String get chargeIslandModeCurrent => '电流';

  @override
  String get chargeIslandModeLevel => '电量';

  @override
  String get chargeIslandModeTemperature => '电池温度';

  @override
  String get chargeIslandDurationModeTitle => '持续时间';

  @override
  String get chargeIslandDurationDefault => '默认';

  @override
  String get chargeIslandDurationCustom => '自定义';

  @override
  String get chargeIslandDurationPersistent => '常驻';

  @override
  String get chargeIslandDurationSecondsTitle => '自定义时长';

  @override
  String get chargeIslandOuterGlowSubtitle => '控制充电超级岛的外圈光效';

  @override
  String get outerGlowTitle => '外圈光效';

  @override
  String get bluetoothIslandOuterGlowSubtitle => '控制蓝牙超级岛的外圈光效';

  @override
  String get outerGlowColorTitle => '外圈光效颜色';

  @override
  String get hookScopeXMSF => '小米服务框架';

  @override
  String get downloadManagerSection => '下载管理程序';

  @override
  String get themePageTitle => '主题';

  @override
  String get themeSeedColorTitle => '主题色';

  @override
  String get themeSeedColorSubtitle => '自定义应用强调色';

  @override
  String get presetColors => '预设色板';

  @override
  String get themeResetColor => '恢复默认';

  @override
  String get blurBarsTitle => '毛玻璃效果';

  @override
  String get blurBarsSubtitle => '为顶栏和底栏添加模糊透明效果';

  @override
  String get bluetoothIslandWhitelistTitle => '设备白名单';

  @override
  String get bluetoothIslandWhitelistSubtitle => '仅对白名单中的蓝牙设备显示超级岛';

  @override
  String get bluetoothIslandWhitelistButton => '管理白名单设备';

  @override
  String bluetoothIslandWhitelistButtonSubtitle(int count) {
    return '已选择 $count 个设备';
  }

  @override
  String get bluetoothIslandWhitelistDialogTitle => '选择蓝牙设备';

  @override
  String get bluetoothIslandWhitelistEmpty => '暂无已配对设备，请先在系统蓝牙中配对';

  @override
  String get bluetoothIslandWhitelistAllHint => '未开启白名单时，对所有蓝牙设备生效';

  @override
  String get bluetoothIslandLoadDevicesFailed => '获取蓝牙设备失败';

  @override
  String get bluetoothIslandNeedBtPermission => '需要蓝牙权限才能获取设备列表';

  @override
  String get hideBehaviorTitle => '隐藏行为';

  @override
  String get hideBehaviorDescription =>
      '控制系统场景是否允许临时隐藏超级岛。关闭某项后，会拦截对应场景的系统隐藏逻辑。';

  @override
  String get hideBehaviorMasterSwitch => '启用隐藏行为 Hook';

  @override
  String get hideBehaviorMasterSwitchSubtitle =>
      '开启后才注册隐藏行为 Hook；关闭后完全不 Hook，默认关闭';

  @override
  String get hideBehaviorScreenPinning => '屏幕固定';

  @override
  String get hideBehaviorScreenPinningSubtitle => '屏幕固定激活时隐藏超级岛';

  @override
  String get hideBehaviorBouncerShowing => '解锁界面';

  @override
  String get hideBehaviorBouncerShowingSubtitle => '密码、指纹等解锁界面显示时隐藏超级岛';

  @override
  String get hideBehaviorFullscreen => '全屏模式';

  @override
  String get hideBehaviorFullscreenSubtitle => '状态栏消失或沉浸式全屏时隐藏超级岛';

  @override
  String get hideBehaviorScreenLocked => '锁屏';

  @override
  String get hideBehaviorScreenLockedSubtitle => '锁屏或息屏流程中隐藏超级岛';

  @override
  String get hideBehaviorNotificationCenter => '通知中心';

  @override
  String get hideBehaviorNotificationCenterSubtitle => '通知栏展开或下滑过渡时隐藏超级岛';

  @override
  String get off => '关闭';

  @override
  String get islandTextSection => '超级岛文字';

  @override
  String get islandTextColorTitle => '超级岛文本颜色';

  @override
  String get islandTextColorBlack => '黑色';

  @override
  String get islandTextColorFollowBackground => '跟随岛背景';

  @override
  String get islandTextColorInvertBackground => '反跟随岛背景';

  @override
  String get islandTextColorFollowStatusBar => '跟随状态栏';

  @override
  String get islandTextColorInvertStatusBar => '反跟随状态栏';

  @override
  String get islandTextColorDefault => '默认';

  @override
  String get tapToSelectImage => '点击选择图片或 GIF';

  @override
  String get autoExpandNotification => '自动展开通知';

  @override
  String widthDpLabel(int width) {
    return '$width dp';
  }
}
