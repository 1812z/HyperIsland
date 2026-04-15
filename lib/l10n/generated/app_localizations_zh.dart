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
  String get note1 => '1.此页面仅用于测试是否支持超级岛，并不代表实际效果';

  @override
  String get note2 => '2.请在 HyperCeiler 中关闭系统界面和小米服务框架的焦点通知白名单';

  @override
  String get note3 => '3.LSPosed 管理器中激活后，必须重启相关作用域软件';

  @override
  String get note4 => '4.支持通用适配，自行勾选合适的模板尝试';

  @override
  String get behaviorSection => '行为';

  @override
  String get defaultConfigSection => '渠道默认配置';

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
  String get unlockFocusAuthSubtitle =>
      '允许所有应用向手表/手环发送焦点通知，跳过签名校验（需 Hook 小米服务框架）';

  @override
  String get checkUpdateOnLaunchTitle => '启动时检查更新';

  @override
  String get checkUpdateOnLaunchSubtitle => '启动应用时自动检查是否有新版本';

  @override
  String get showWelcomeTitle => '显示启动欢迎语';

  @override
  String get showWelcomeSubtitle => '应用启动时在超级岛显示欢迎信息';

  @override
  String get interactionHapticsTitle => '交互触感';

  @override
  String get interactionHapticsSubtitle => '为开关、滑块和按钮启用 Hyper 定制震感反馈';

  @override
  String get checkUpdate => '检查更新';

  @override
  String get alreadyLatest => '已是最新版本';

  @override
  String get useAppIconTitle => '使用应用图标';

  @override
  String get useAppIconSubtitle => '下载管理器通知使用应用图标';

  @override
  String get roundIconTitle => '图标圆角';

  @override
  String get roundIconSubtitle => '为通知图标添加圆角效果';

  @override
  String get marqueeChannelTitle => '消息滚动';

  @override
  String get marqueeSpeedTitle => '滚动速度';

  @override
  String marqueeSpeedLabel(int speed) {
    return '$speed 像素/秒';
  }

  @override
  String get bigIslandMaxWidthTitle => '修改超级岛最大宽度';

  @override
  String bigIslandMaxWidthLabel(int width) {
    return '$width dp';
  }

  @override
  String get bigIslandMaxWidthSubtitle => '开启后修改超级岛的最大宽度';

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
  String get focusNotificationLabel => '焦点通知';

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
  String get dynamicHighlightModeDark => '暗';

  @override
  String get dynamicHighlightModeDarker => '更暗';

  @override
  String get outerGlowLabel => '外圈光效';

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
  String get fullscreenBehaviorOff => '关闭';

  @override
  String get fullscreenBehaviorFallback => '回退普通通知';

  @override
  String get fullscreenBehaviorExpand => '自动展开通知';

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
  String get hideDesktopIconTitle => '隐藏桌面图标';

  @override
  String get hideDesktopIconSubtitle => '隐藏启动器中的应用图标，隐藏后可通过 LSPosed 管理器打开';
}
