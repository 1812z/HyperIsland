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
  String get restartScope => '重启作用域';

  @override
  String get systemUI => '系统界面';

  @override
  String get downloadManager => '下载管理器';

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
  String get checkUpdateOnLaunchTitle => '启动时检查更新';

  @override
  String get checkUpdateOnLaunchSubtitle => '启动应用时自动检查是否有新版本';

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
  String get exportToFile => '导出到文件';

  @override
  String get exportToFileSubtitle => '将配置保存为 JSON 文件';

  @override
  String get exportToClipboard => '导出到剪贴板';

  @override
  String get exportToClipboardSubtitle => '将配置复制为 JSON 文本';

  @override
  String get importFromFile => '从文件导入';

  @override
  String get importFromFileSubtitle => '从 JSON 文件恢复配置';

  @override
  String get importFromClipboard => '从剪贴板导入';

  @override
  String get importFromClipboardSubtitle => '从剪贴板中的 JSON 文本恢复配置';

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
  String get template => '模板';

  @override
  String get islandIcon => '超级岛图标';

  @override
  String get focusIconLabel => '焦点图标';

  @override
  String get focusNotificationLabel => '焦点通知';

  @override
  String get firstFloatLabel => '初次展开';

  @override
  String get updateFloatLabel => '更新展开';

  @override
  String get autoDisappear => '自动消失';

  @override
  String get seconds => '秒';

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
}
