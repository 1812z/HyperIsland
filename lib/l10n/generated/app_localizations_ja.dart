// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Japanese (`ja`).
class AppLocalizationsJa extends AppLocalizations {
  AppLocalizationsJa([String locale = 'ja']) : super(locale);

  @override
  String get navHome => 'ホーム';

  @override
  String get navIsland => '島';

  @override
  String get navApps => 'アプリ';

  @override
  String get navSettings => '設定';

  @override
  String get cancel => 'キャンセル';

  @override
  String get confirm => '確認';

  @override
  String get ok => 'OK';

  @override
  String get apply => '適用';

  @override
  String get noChange => '変更しない';

  @override
  String get newVersionFound => '新しいバージョンが利用可能です';

  @override
  String currentVersion(String version) {
    return '現在のバージョン: $version';
  }

  @override
  String latestVersion(String version) {
    return '最新のバージョン: $version';
  }

  @override
  String get later => '後で';

  @override
  String get goUpdate => '更新';

  @override
  String get sponsorSupport => '作者をサポートする';

  @override
  String get sponsorAuthor => 'スポンサー';

  @override
  String get donorList => '寄付者一覧';

  @override
  String get documentation => 'ドキュメント';

  @override
  String versionUpdatedTitle(String version) {
    return '$version に更新されました';
  }

  @override
  String get versionUpdatedContent => '更新後にスコープを再起動してください';

  @override
  String get versionUpdatedChangelog => '更新ログ：クリックして表示';

  @override
  String get versionUpdatedStarHint => 'このソフトが気に入ったら無料のStarをお願いします';

  @override
  String get restartScope => 'スコープを再起動';

  @override
  String get systemUI => 'システム UI';

  @override
  String get downloadManager => 'ダウンロードマネージャー';

  @override
  String get xmsf => 'XMSF (Xiaomi サービスフレームワーク)';

  @override
  String get notificationTest => '通知のテスト';

  @override
  String get sendTestNotification => 'テスト通知を送信';

  @override
  String get customTestNotification => 'カスタムテスト通知';

  @override
  String get customTestTitle => 'タイトル';

  @override
  String get customTestTitleHint => '空欄でデフォルトタイトル';

  @override
  String get customTestContent => '内容';

  @override
  String get customTestContentHint => '空欄でデフォルト内容';

  @override
  String get clearPreviousNotification => '前の通知をクリア';

  @override
  String get clearPreviousNotificationSubtitle => '送信前に既存の Island 通知をキャンセル';

  @override
  String get enableFloatNotificationSubtitle => '通知を受信時にフォーカス通知として自動展開';

  @override
  String get notes => '説明';

  @override
  String get detectingModuleStatus => 'モジュールの状態を検出中...';

  @override
  String get moduleStatus => 'モジュールの状態';

  @override
  String get activated => '有効';

  @override
  String get notActivated => '無効';

  @override
  String get enableInLSPosed => 'LSPosed でこのモジュールを有効化してください';

  @override
  String get enableSystemUiScopeInLSPosed =>
      'LSPosed のスコープで System UI を選択してください';

  @override
  String lsposedApiVersion(int version) {
    return 'LSPosed API バージョン: $version';
  }

  @override
  String get updateLSPosedRequired => 'LSPosed バージョンを更新してください';

  @override
  String get systemNotSupported => 'システムは非対応です';

  @override
  String systemNotSupportedSubtitle(int version) {
    return 'システムは Dynamic Island に非対応です (現在のプロトコルバージョンは $version、プロトコルバージョン 3 が必要です)';
  }

  @override
  String restartFailed(String message) {
    return '再起動に失敗しました: $message';
  }

  @override
  String get restartRootRequired => 'このアプリに root 権限が付与されているか確認してください';

  @override
  String get note1 => '1. 使用前に右上隅の使用チュートリアルを必ずご確認ください';

  @override
  String get note2 => '2. ほとんどの設定はホットリロードに対応しています。異常が発生した場合はスコープを再起動してください';

  @override
  String get note3 => '3. LSPosed Manager で有効化後、関連するスコープアプリを再起動する必要があります';

  @override
  String get note4 => '4. このページはダイナミックアイランドとグロー効果の対応テスト用であり、実際の効果を示すものではありません';

  @override
  String get note5 =>
      '5. ダウンロード上島には「ダウンロードマネージャー」を手動で有効にしてください。「ダウンロード」テンプレートをおすすめします';

  @override
  String get behaviorSection => '動作';

  @override
  String get defaultConfigSection => 'デフォルトのチャンネル設定';

  @override
  String get appearanceSection => '外観';

  @override
  String get configSection => '構成';

  @override
  String get aboutSection => 'アプリについて';

  @override
  String get keepFocusNotifTitle => 'ダウンロードの一時停止後も通知を保持する';

  @override
  String get keepFocusNotifSubtitle =>
      'ダウンロードを再開するためのフォーカス通知を表示しますが、状態の同期でズレが発生する可能性があります';

  @override
  String get unlockAllFocusTitle => 'フォーカス通知のホワイトリストを削除';

  @override
  String get unlockAllFocusSubtitle => 'システム認証がない場合でもすべてのアプリでフォーカス通知を送信可能にします';

  @override
  String get unlockFocusAuthTitle => 'フォーカス通知の署名検証を削除';

  @override
  String get unlockFocusAuthSubtitle =>
      '署名検証のバイパスとすべてのアプリでフォーカス通知を時計/ブレスレットに送信可能な状態にします (XMSF のフックが必要です)';

  @override
  String get checkUpdateOnLaunchTitle => '起動時に更新を確認する';

  @override
  String get checkUpdateOnLaunchSubtitle => 'アプリの起動時に最新のバージョンを自動で確認します';

  @override
  String get debugLogTitle => 'デバッグログを表示';

  @override
  String get debugLogSubtitle => '有効にすると Hook デバッグログを出力し、無効にすると警告とエラーログのみ保持します';

  @override
  String get showWelcomeTitle => '起動時のウェルカムメッセージを表示';

  @override
  String get showWelcomeSubtitle => 'アプリ起動時に Island にウェルカム情報を表示します';

  @override
  String get openOnboardingTitle => '初期ガイドを開く';

  @override
  String get openOnboardingSubtitle => 'ようこそ画面とクイックスタートをもう一度確認します';

  @override
  String get interactionHapticsTitle => 'インタラクションの触覚フィードバック';

  @override
  String get interactionHapticsSubtitle =>
      'スイッチ、スライダー、ボタンに Hyper カスタム振動フィードバックを有効にします';

  @override
  String get checkUpdate => '更新を確認';

  @override
  String get alreadyLatest => '最新のバージョンを使用しています';

  @override
  String get roundIconTitle => 'アイコンの角を丸める';

  @override
  String get roundIconSubtitle => '通知アイコンの角を丸めます';

  @override
  String get marqueeChannelTitle => 'Island のテキストをスクロール';

  @override
  String get marqueeAutoHideTitle => '滚动后隐藏岛';

  @override
  String get marqueeAutoHideSubtitle => '消息滚动达到指定次数后隐藏当前岛';

  @override
  String get marqueeAutoHideOnce => '滚动1次';

  @override
  String get marqueeAutoHideTwice => '滚动2次';

  @override
  String get marqueeSpeedTitle => '速度';

  @override
  String marqueeSpeedLabel(int speed) {
    return '$speed px/秒';
  }

  @override
  String get bigIslandMaxWidthTitle => '最大宽度';

  @override
  String get bigIslandMinWidthTitle => '最小宽度';

  @override
  String get testNotifTooltip => '发送测试通知';

  @override
  String get themeModeTitle => 'カラーモード';

  @override
  String get themeModeSystem => 'システムに従う';

  @override
  String get themeModeLight => 'ライト';

  @override
  String get themeModeDark => 'ダーク';

  @override
  String get languageTitle => '言語';

  @override
  String get languageAuto => 'システムに従う';

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
  String get exportToFile => 'ファイルにエクスポート';

  @override
  String get exportToFileSubtitle => '構成を JSON ファイルで保存します';

  @override
  String get exportToClipboard => 'クリップボードにエクスポート';

  @override
  String get exportToClipboardSubtitle => '構成の JSON テキストをクリップボードにコピーします';

  @override
  String get exportConfig => '構成をエクスポート';

  @override
  String get exportConfigSubtitle => 'ファイルまたはクリップボードへのエクスポートを選択します';

  @override
  String get importFromFile => 'ファイルからインポート';

  @override
  String get importFromFileSubtitle => 'JSON ファイルから構成を復元します';

  @override
  String get importFromClipboard => 'クリップボードからインポート';

  @override
  String get importFromClipboardSubtitle => 'クリップボードの JSON テキストから構成を復元します';

  @override
  String get importConfig => '構成をインポート';

  @override
  String get importConfigSubtitle => 'ファイルまたはクリップボードからのインポートを選択します';

  @override
  String get qqGroup => 'QQ グループ';

  @override
  String get restartScopeApp => '設定を適用するにはスコープアプリを再起動してください';

  @override
  String get groupNumberCopied => 'グループ番号をクリップボードにコピーしました';

  @override
  String exportedTo(String path) {
    return 'エクスポート先: $path';
  }

  @override
  String exportFailed(String error) {
    return 'エクスポートに失敗しました: $error';
  }

  @override
  String get configCopied => '構成をクリップボードにコピーしました';

  @override
  String importSuccess(int count) {
    return '$count 個の項目をインポートしました。アプリを再起動してください。';
  }

  @override
  String importFailed(String error) {
    return 'インポートに失敗しました: $error';
  }

  @override
  String get appAdaptation => 'アプリのアダプティブ表示';

  @override
  String get toastAdaptation => 'Toast のアダプティブ表示';

  @override
  String get adaptationModeNotification => '通知';

  @override
  String get adaptationModeToast => 'Toast';

  @override
  String toastEnabledAppsCount(Object count) {
    return '$count 個のアプリで Toast インターセプトが有効です';
  }

  @override
  String toastEnabledAppsCountWithSystem(Object count) {
    return '$count 個のアプリで Toast インターセプトが有効です（システムアプリを含む）';
  }

  @override
  String selectedAppsCount(int count) {
    return '$count 個のアプリを選択済み';
  }

  @override
  String get cancelSelection => '選択をキャンセル';

  @override
  String get deselectAll => 'すべての選択を解除';

  @override
  String get selectAll => 'すべて選択';

  @override
  String get batchChannelSettings => 'チャンネルを一括で設定';

  @override
  String get selectEnabledApps => '有効化するアプリを選択';

  @override
  String get batchEnable => '一括で有効化';

  @override
  String get batchDisable => '一括で無効化';

  @override
  String get multiSelect => '複数選択';

  @override
  String get showSystemApps => 'システムアプリを表示';

  @override
  String get refreshList => 'リストを更新';

  @override
  String get enableAll => 'すべて有効';

  @override
  String get disableAll => 'すべて無効';

  @override
  String enabledAppsCount(int count) {
    return 'Dynamic Island は $count 個のアプリで有効です';
  }

  @override
  String enabledAppsCountWithSystem(int count) {
    return 'Dynamic Island は $count 個のアプリで有効です (システムアプリも含む)';
  }

  @override
  String get searchApps => 'アプリ名またはパッケージ名で検索';

  @override
  String get noAppsFound => 'インストール済みのアプリが見つかりません\nアプリリストの権限が有効か確認してください';

  @override
  String get noMatchingApps => '一致するアプリがありません';

  @override
  String applyToSelectedAppsChannels(int count) {
    return '選択した $count 個のアプリで有効なチャンネルに適用されます';
  }

  @override
  String get applyingConfig => '構成を適用中です...';

  @override
  String progressApps(int done, int total) {
    return '進捗: $done / $total';
  }

  @override
  String batchApplied(int count) {
    return '$count 個のアプリを適用しました';
  }

  @override
  String get cannotReadChannels => '通知チャンネルを読み込めません';

  @override
  String get rootRequiredMessage =>
      '通知チャンネルの読み取りには root 権限が必要です。\nroot 権限が付与されていることを確認後に再度お試しください。';

  @override
  String get enableAllChannels => 'すべてのチャンネルで有効';

  @override
  String get noChannelsFound => '通知チャンネルがありません';

  @override
  String get noChannelsFoundSubtitle => 'このアプリには通知チャンネルがありません。通知の読み取りはできません。';

  @override
  String allChannelsActive(int count) {
    return '全 $count 個のチャンネルですべて有効';
  }

  @override
  String selectedChannels(int selected, int total) {
    return '$selected / $total 個のチャンネルを選択済み';
  }

  @override
  String allChannelsDisabled(int count) {
    return 'すべての $count 個のチャンネル (無効化済み)';
  }

  @override
  String get appDisabledBanner => 'アプリが無効化されているため、以下のチャンネル設定は無効です';

  @override
  String channelImportance(String importance, String id) {
    return '重要度: $importance  ·  $id';
  }

  @override
  String get channelSettings => 'チャンネルの設定';

  @override
  String get toastForwardTitle => '標準 Toast を転送';

  @override
  String get toastForwardSubtitle =>
      'このアプリの標準 Toast テキストを HyperIsland のフォーカス通知とスーパーアイランドに転送します';

  @override
  String get toastBlockOriginalTitle => '元の Toast をブロック';

  @override
  String get toastBlockOriginalSubtitle => '転送後にこのアプリの標準 Toast ポップアップをブロックします';

  @override
  String get toastShowNotificationTitle => '通知として表示';

  @override
  String get toastShowNotificationSubtitle => '有効時、転送された Toast は通知センターに表示されます';

  @override
  String get toastShowIslandIconTitle => 'スーパーアイランドのアイコンを表示';

  @override
  String get toastShowIslandIconSubtitle => '転送 Toast の大きな島の左側アイコン表示を制御します';

  @override
  String get toastStandardOnlyHint =>
      '標準テキスト Toast のみ処理します。カスタム Toast ビューは無視されます。';

  @override
  String get importanceNone => 'なし';

  @override
  String get importanceMin => '中';

  @override
  String get importanceLow => '低';

  @override
  String get importanceDefault => 'デフォルト';

  @override
  String get importanceHigh => '高';

  @override
  String get importanceUnknown => '不明';

  @override
  String applyToEnabledChannels(int count) {
    return '有効な $count 個のチャンネルに適用されます';
  }

  @override
  String applyToAllChannels(int count) {
    return 'すべての $count 個のチャンネルに適用されます';
  }

  @override
  String get templateDownloadName => 'ダウンロード';

  @override
  String get templateNotificationIslandName => 'Notification Island';

  @override
  String get templateNotificationIslandLiteName => 'Notification Island|Lite';

  @override
  String get templateDownloadLiteName => 'Lite|をダウンロード';

  @override
  String get islandSection => 'Island';

  @override
  String get islandEnabledLabel => '島を有効化';

  @override
  String get template => 'テンプレート';

  @override
  String get rendererLabel => 'スタイル';

  @override
  String get rendererImageTextWithButtons4Name => '画像 + テキスト + 下部テキストボタン';

  @override
  String get rendererCoverInfoName => 'カバー情報 + 自動で折りたたみ';

  @override
  String get rendererImageTextWithRightTextButtonName => '画像 + テキスト + 右テキストボタン';

  @override
  String get rendererImageTextWithProgressName => 'IM图文组件 + 进度条组件';

  @override
  String get islandIcon => 'Island のアイコン';

  @override
  String get islandIconLabel => '大きな Island アイコン';

  @override
  String get islandIconLabelSubtitle =>
      '有効にすると Island に大きなアイコンを表示します (小さな Island は影響を受けません)';

  @override
  String get focusIconLabel => 'フォーカスアイコン';

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
  String get focusNotificationLabel => 'フォーカス通知';

  @override
  String get hideNotificationLabel => '通知を非表示';

  @override
  String get hideNotificationLabelSubtitle =>
      'オンにするとアイランドのみ表示し、通知シェードのフォーカス通知を非表示にします';

  @override
  String get preserveStatusBarSmallIconLabel => 'ステータスバーアイコン';

  @override
  String get restoreLockscreenTitle => 'ロック画面の通知を復元する';

  @override
  String get restoreLockscreenSubtitle =>
      'ロック画面でのフォーカス通知処理をスキップし、元のプライバシーに適切な動作を保持します';

  @override
  String get firstFloatLabel => '最初にフロート表示';

  @override
  String get updateFloatLabel => '更新時にフロート表示';

  @override
  String get autoDisappear => '自動で無視';

  @override
  String get seconds => '秒';

  @override
  String get highlightColorLabel => 'ハイライト色';

  @override
  String get dynamicHighlightColorLabel => 'ハイライトの動的色取得';

  @override
  String get dynamicHighlightColorLabelSubtitle => '有効時はデフォルトでアイコンから動的に色を取得します';

  @override
  String get followDynamicColorLabel => '動的色取得に追従';

  @override
  String get dynamicHighlightModeDark => '暗め';

  @override
  String get dynamicHighlightModeDarker => 'さらに暗め';

  @override
  String get outerGlowLabel => '外側グロー';

  @override
  String get forceOuterGlowLabel => 'グローバルで強制';

  @override
  String get forceFocusOuterGlowSubtitle => '有効にすると未一致のフォーカス通知でグローを強制的に有効にします';

  @override
  String get forceIslandOuterGlowSubtitle => '有効にすると未一致の島でグローを強制的に有効にします';

  @override
  String get outEffectColorLabel => '外側グロー色';

  @override
  String get highlightColorHint => '#RRGGBB 形式、空白でデフォルト';

  @override
  String get actionBgColorLabel => 'ボタン背景色';

  @override
  String get actionBgColorDarkLabel => 'ボタン背景色（ダーク）';

  @override
  String get actionTitleColorLabel => 'ボタン文字色';

  @override
  String get actionTitleColorDarkLabel => 'ボタン文字色（ダーク）';

  @override
  String get action1BgColorLabel => 'ボタン1背景色';

  @override
  String get action1BgColorDarkLabel => 'ボタン1背景色（ダーク）';

  @override
  String get action1TitleColorLabel => 'ボタン1文字色';

  @override
  String get action1TitleColorDarkLabel => 'ボタン1文字色（ダーク）';

  @override
  String get action2BgColorLabel => 'ボタン2背景色';

  @override
  String get action2BgColorDarkLabel => 'ボタン2背景色（ダーク）';

  @override
  String get action2TitleColorLabel => 'ボタン2文字色';

  @override
  String get action2TitleColorDarkLabel => 'ボタン2文字色（ダーク）';

  @override
  String get textHighlightLabel => 'テキストハイライト';

  @override
  String get narrowFontLabel => 'ナローフォント';

  @override
  String get showLeftHighlightLabel => '左テキストハイライト';

  @override
  String get showRightHighlightLabel => '右テキストハイライト';

  @override
  String get showLeftHighlightShort => '左';

  @override
  String get showRightHighlightShort => '右';

  @override
  String get colorHue => '色相';

  @override
  String get colorSaturation => '彩度';

  @override
  String get colorBrightness => '明度';

  @override
  String get colorOpacity => '不透明度';

  @override
  String get onlyEnabledChannels => '有効なチャンネルにのみ適用されます';

  @override
  String enabledChannelsCount(int enabled, int total) {
    return '$enabled / $total 個のチャンネルが有効';
  }

  @override
  String get iconModeAuto => '自動';

  @override
  String get iconModeNotifSmall => '小さな通知アイコン';

  @override
  String get iconModeNotifLarge => '大きな通知アイコン';

  @override
  String get iconModeAppIcon => 'アプリアイコン';

  @override
  String get optDefault => 'デフォルト';

  @override
  String get optDefaultOn => 'デフォルト (ON)';

  @override
  String get optDefaultOff => 'デフォルト (OFF)';

  @override
  String get optOn => 'ON';

  @override
  String get optOff => 'OFF';

  @override
  String get errorInvalidFormat => '無効な構成フォーマットです';

  @override
  String get errorNoStorageDir => 'ストレージディレクトリを取得できません';

  @override
  String get errorNoFileSelected => 'ファイルが選択されていません';

  @override
  String get errorNoFilePath => 'ファイルパスを取得できません';

  @override
  String get errorEmptyClipboard => 'クリップボードは空です';

  @override
  String get navBlacklist => 'フォーカスのブラックリスト';

  @override
  String get navBlacklistSubtitle => '特定のアプリでのフォーカス通知をブロック、フローティングまたは非表示にします';

  @override
  String get presetGamesTitle => '人気のゲームをクイックでフィルター';

  @override
  String presetGamesSuccess(int count) {
    return '$count 個のインストールしたゲームをブラックリストに追加しました';
  }

  @override
  String blacklistedAppsCount(int count) {
    return '$count 個のアプリのフォーカス通知をブロックしました';
  }

  @override
  String blacklistedAppsCountWithSystem(int count) {
    return '$count 個のアプリのフォーカス通知をブロックしました (システムアプリを含む)';
  }

  @override
  String get firstFloatLabelSubtitle => 'Island が初めて通知を受信時にフォーカス通知として展開します';

  @override
  String get updateFloatLabelSubtitle => 'Island の更新時にフロート通知を展開します';

  @override
  String get marqueeChannelTitleSubtitle => 'Island で長いメッセージをスクロールします';

  @override
  String get focusNotificationLabelSubtitle =>
      '通知をフォーカス通知に置き換えます (無効で元の通知が表示されます)';

  @override
  String get preserveStatusBarSmallIconLabelSubtitle =>
      'フォーカス通知を表示時にステータスバーアイコンを強制的に保持します';

  @override
  String get fullscreenBehaviorTitle => '全画面時の動作';

  @override
  String get fullscreenBehaviorSubtitle => '横画面/全画面を検出したときの通知処理方式';

  @override
  String get fullscreenBehaviorOff => 'デフォルト';

  @override
  String get fullscreenBehaviorFallback => '通常通知へフォールバック';

  @override
  String get filterRulesTitle => 'フィルタールール';

  @override
  String get filterRulesOrderTitle => '最初に一致したルールを適用';

  @override
  String get filterRuleDnd => '通知制限';

  @override
  String get filterRuleFullscreen => '全画面';

  @override
  String get filterRuleLandscape => '横画面';

  @override
  String get dndBehaviorTitle => '通知制限時';

  @override
  String get fullscreenRuleTitle => '全画面時';

  @override
  String get landscapeRuleTitle => '横画面時';

  @override
  String get behaviorPreviewDefault => '一致時は処理せず、デフォルト動作を使用';

  @override
  String get behaviorPreviewSuppress => '一致時は通常通知に戻します';

  @override
  String get behaviorPreviewSmallOnly => '一致時は小さい Island のみ表示し、自動展開しません';

  @override
  String get behaviorPreviewExpand => '一致時は通知を自動展開します';

  @override
  String get aiConfigSection => 'AI エンハンスメント';

  @override
  String get aiConfigTitle => 'AI 通知の概要';

  @override
  String get aiConfigSubtitleEnabled => '有効 · タップで AI パラメータを構成';

  @override
  String get aiConfigSubtitleDisabled => '無効 · タップで構成';

  @override
  String get aiEnabledTitle => 'AI の概要を有効化';

  @override
  String get aiEnabledSubtitle =>
      'AI が Island の左右のテキストを生成します。タイムアウトまたはエラーの発生時はフォールバックします。';

  @override
  String get aiApiSection => 'API パラメータ';

  @override
  String get aiUrlLabel => 'API URL';

  @override
  String get aiUrlHint => 'https://api.openai.com/v1/chat/completions';

  @override
  String get aiApiKeyLabel => 'API キー';

  @override
  String get aiApiKeyHint => 'sk-...';

  @override
  String get aiModelLabel => 'モデル';

  @override
  String get aiModelHint => 'gpt-4o-mini';

  @override
  String get aiModelPickerTitle => 'モデルを選択';

  @override
  String get aiModelPickerSearchHint => 'モデルを検索…';

  @override
  String get aiModelPickerEmpty => 'モデルが見つかりません';

  @override
  String get aiModelPickerRetry => '再試行';

  @override
  String get aiModelPickerClose => '閉じる';

  @override
  String get aiModelPickerFetchError => 'モデルリストの読み込みに失敗しました';

  @override
  String get aiPromptLabel => 'カスタムプロンプト';

  @override
  String get aiPromptHint =>
      'デフォルトを使用する場合は空欄: 左右それぞれ 6 単語または 12 文字以内の重要な情報を抽出します';

  @override
  String get aiPromptInUserTitle => 'ユーザーメッセージにプロンプトを表示する';

  @override
  String get aiPromptInUserSubtitle =>
      '一部のモデルではシステム命令がサポートされていないため、有効にするとユーザーメッセージにプロンプ​​トを表示させます';

  @override
  String get aiTimeoutTitle => 'AI レスポンスのタイムアウト';

  @override
  String aiTimeoutLabel(int seconds) {
    return 'AI レスポンスのタイムアウト';
  }

  @override
  String get aiTemperatureTitle => 'サンプリング温度';

  @override
  String get aiTemperatureSubtitle => '回答のランダム性を制御します。0 は正確、1 はより独創的になります';

  @override
  String get aiMaxTokensTitle => '最大トークン数';

  @override
  String get aiMaxTokensSubtitle => 'AI が生成する回答の最大長を制限します';

  @override
  String get aiDefaultPromptFull =>
      '空欄でデフォルトを使用：通知から重要な情報を抽出します。左右それぞれ 6 漢字 12 文字以内とします。';

  @override
  String get aiTestButton => 'テスト接続';

  @override
  String get aiTestUrlEmpty => '始めに API URL を入力してください';

  @override
  String get aiLastLogTitle => '最近の AI リクエストログ';

  @override
  String get aiLastLogSubtitle => 'テスト接続や通知によってトリガーされた AI リクエストがここに表示されます';

  @override
  String get aiLastLogEmpty => '表示できる AI リクエストログがまだありません';

  @override
  String get aiLastLogSourceLabel => 'ソース';

  @override
  String get aiLastLogTimeLabel => '時間';

  @override
  String get aiLastLogStatusLabel => 'ステータス';

  @override
  String get aiLastLogDurationLabel => '実行時間';

  @override
  String get aiLastLogSourceNotification => '通知トリガー';

  @override
  String get aiLastLogSourceSettingsTest => '設定テスト';

  @override
  String get aiLastLogRendered => 'レンダリング済み';

  @override
  String get aiLastLogRaw => 'オリジナル';

  @override
  String get aiLastLogCopy => 'ログをコピー';

  @override
  String get aiLastLogCopied => 'AI リクエストログをコピーしました';

  @override
  String get aiLastLogRequest => 'リクエスト';

  @override
  String get aiLastLogResponse => 'レスポンス';

  @override
  String get aiLastLogUsage => 'トークン使用量';

  @override
  String get aiLastLogMessages => 'メッセージ';

  @override
  String get aiLastLogError => 'エラー';

  @override
  String get aiLastLogHttpCode => 'HTTP ステータス';

  @override
  String get aiLastLogLeftText => '左側のテキスト';

  @override
  String get aiLastLogRightText => '右側のテキスト';

  @override
  String get aiLastLogAssistantContent => 'モデルのレスポンスコンテンツ';

  @override
  String get aiConfigSaveButton => '保存';

  @override
  String get aiConfigSaved => 'AI の構成を保存しました';

  @override
  String get aiConfigTips =>
      'AI は各通知のアプリパッケージ、タイトル、コンテンツを受信し、短い左側 (ソース) と右側 (コンテンツ) のテキストを返します。OpenAI 形式の API (DeepSeek、Claude など) と互換性があります。レスポンスがない場合は、デフォルトのロジックにフォールバックします。';

  @override
  String get templateAiNotificationIslandName => 'AI Notification Island';

  @override
  String get aiPromptDefault => '通知から重要な情報を抽出します。左右それぞれ 6 単語または 12 文字以内とします。';

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
  String get hideDesktopIconTitle => 'デスクトップアイコンを非表示にする';

  @override
  String get hideDesktopIconSubtitle =>
      'アプリのアイコンをランチャーから非表示にします。非表示後は、LSPosed Manager 経由で開くことができます。';

  @override
  String get filterRulesSection => 'フィルタールール';

  @override
  String get foregroundRulesTab => '前面ルール';

  @override
  String get foregroundExclusionsTab => '除外アプリ';

  @override
  String get foregroundRulesDescription => '前面アプリの起動時の Island 動作を設定します。';

  @override
  String get foregroundExclusionsDescription => '除外リスト内のアプリの通知は前面ルールの影響を受けません。';

  @override
  String get hideSystemApps => 'システムアプリを非表示';

  @override
  String get restoreDefaultConfig => 'デフォルト構成に戻す';

  @override
  String resetDefaultConfigSuccess(int count) {
    return 'デフォルト構成に戻しました。$count 個のアプリをリセットしました';
  }

  @override
  String get sceneActionDefault => 'デフォルト';

  @override
  String get sceneActionSmallOnly => '展開を無効化';

  @override
  String get sceneActionExpand => '自動展開';

  @override
  String get sceneActionSuppress => 'フォールバック';

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
    return '（全 $total 件中、$matched 件一致）';
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
  String get mediaNotificationTitle => 'メディア通知';

  @override
  String get mediaNotificationDisabledSubtitle => 'オフにするとメディア通知全体を直接削除します';

  @override
  String get normalNotificationTitle => '通常通知';

  @override
  String get normalNotificationSubtitle => 'オンにするとメディア項目を削除し、通常通知として処理します';

  @override
  String get channelSettingsUnmodified => '未変更';

  @override
  String get restoreDefault => 'デフォルトに戻す';

  @override
  String get islandDimenSection => 'アイランドサイズ設定';

  @override
  String get islandDimenHeight => '島の高さ';

  @override
  String get islandTopOffset => '画面上端からの距離';

  @override
  String get followSystem => 'システムに従う';

  @override
  String get islandDimenMiniY => '垂直位置';

  @override
  String get islandDimenMiniYHint => '0=システムに従う';

  @override
  String get islandBgSection => 'アイランド背景設定';

  @override
  String get islandBgSmallTitle => 'スモールアイランド背景';

  @override
  String get islandBgBigTitle => 'ラージアイランド背景';

  @override
  String get islandBgExpandTitle => 'フォーカス通知背景';

  @override
  String get islandBgNotSet => '未設定';

  @override
  String get islandBgCornerRadius => '角丸半径';

  @override
  String get islandBgCornerRadiusHint => '0=システムデフォルト';

  @override
  String get islandBgImageSelected => '背景画像を保存しました';

  @override
  String get islandBgImageDeleted => '背景画像を削除しました';

  @override
  String get islandBgDeleteFailed => '削除に失敗しました';

  @override
  String islandBgEditTitle(String type) {
    return '$typeの背景を編集';
  }

  @override
  String get islandBgBlurLabel => 'ぼかし';

  @override
  String get islandBgBrightnessLabel => '明るさ';

  @override
  String get islandBgOpacityLabel => '不透明度';

  @override
  String get islandBgDefault => 'デフォルト';

  @override
  String get keepIslandTitle => '常時ダイナミックアイランド';

  @override
  String get keepIslandSubtitle => '空白通知を投稿してアイランドを常に表示';

  @override
  String get keepIslandAutoHideTitle => '自動非表示';

  @override
  String get keepIslandAutoHideSubtitle =>
      '通知が来た時に空白アイランドを自動的に隠し、通知が消えた後に自動的に復元';

  @override
  String get keepIslandHideLandscapeTitle => '横画面で非表示';

  @override
  String get keepIslandHideLandscapeSubtitle =>
      '横画面では常時アイランドを隠し、縦画面に戻り通知がない時に復元します';

  @override
  String get keepIslandHighlightColorTitle => 'ハイライトカラー';

  @override
  String get keepIslandHighlightColorSubtitle => '常時アイランドのハイライトテキストカラーをカスタマイズ';

  @override
  String get keepIslandLeftContentTitle => '左側の内容';

  @override
  String get keepIslandRightContentTitle => '右側の内容';

  @override
  String get keepIslandPlaceholdersTitle => '利用可能なプレースホルダー';

  @override
  String keepIslandPlaceholdersDescription(
    String batteryLevel,
    String cpuUsage,
  ) {
    return '左右の内容にテキストまたは式を入力できます。例：バッテリー $batteryLevel、CPU $cpuUsage。タグをタップしてコピーします。';
  }

  @override
  String keepIslandPlaceholderCopied(String placeholder) {
    return '$placeholder をコピーしました';
  }

  @override
  String get keepIslandDefaultEmpty => 'デフォルトは空';

  @override
  String keepIslandContentHint(String placeholder) {
    return 'デフォルトは空。テキストまたは $placeholder を入力';
  }

  @override
  String get clear => 'クリア';

  @override
  String get save => '保存';

  @override
  String get islandOtherSection => 'その他';

  @override
  String get miscSection => 'その他の設定';

  @override
  String get onboardingEntryTitle => '初期ガイドを開く';

  @override
  String get onboardingEntrySubtitle => 'ようこそ画面とクイックスタートをもう一度確認';

  @override
  String get onboardingAppName => 'HyperIsland';

  @override
  String get onboardingWelcomeTitle => 'HyperIsland へようこそ';

  @override
  String get onboardingWelcomeSubtitle => 'アイランド体験をすばやくシンプルに設定します';

  @override
  String get onboardingEnvironmentTitle => '環境チェック';

  @override
  String get onboardingEnvironmentSubtitle => 'モジュールの権限状態を確認します';

  @override
  String get onboardingNotificationStyleTitle => '通知スタイルを選択';

  @override
  String get onboardingNotificationStyleSubtitle => '既定の通知表示方法を選択します';

  @override
  String get onboardingOriginalNotificationLabel => '通常の通知';

  @override
  String get onboardingFinishTitle => '準備完了';

  @override
  String get onboardingFinishSubtitle => 'ガイド完了後も、設定から細部を調整できます';

  @override
  String onboardingStepLabel(int current, int total) {
    return '$totalステップ中 $currentステップ目';
  }

  @override
  String get onboardingPrevious => '前へ';

  @override
  String get onboardingNext => '次へ';

  @override
  String get onboardingDone => '開始';

  @override
  String get onboardingStatusTitle => '状態チェック';

  @override
  String get onboardingRetry => '再試行';

  @override
  String get onboardingLsposedStatus => 'LSPosed 有効化状態';

  @override
  String get onboardingRootStatus => 'Root 権限';

  @override
  String get onboardingAppListStatus => 'アプリ一覧権限';

  @override
  String get onboardingProtocolStatus => 'システムプロトコルバージョン';

  @override
  String get onboardingAndroidStatus => 'Android バージョン';

  @override
  String get onboardingUnsupportedSystem => '現在のシステムはサポートされていません';

  @override
  String get onboardingAndroid15Limited => 'Android 15 のサポートは限定的です';

  @override
  String get onboardingMissingPermissionTitle => '必要な権限がありません';

  @override
  String get onboardingMissingPermissionMessage => 'モジュールが正常に動作しない可能性があります';

  @override
  String get onboardingDialogClose => '閉じる';

  @override
  String get onboardingDialogContinue => '続行';

  @override
  String get backupRestoreSection => 'バックアップと復元';

  @override
  String get hookExtensionSection => 'Hook拡張';

  @override
  String get hookScopeSettings => 'システム設定';

  @override
  String get settingsHomeEntryTitle => 'システム設定の入口';

  @override
  String get settingsHomeEntrySubtitle => 'システム設定のホーム画面に HyperIsland の入口を表示します';

  @override
  String get xposedScopeRequestFailed =>
      'スコープの申請に失敗しました。LSPosed でモジュールが有効になっているか確認してください';

  @override
  String get hookScopeSystemUI => 'システムUI';

  @override
  String get smoothIslandTitle => 'スムーズ Island';

  @override
  String get smoothIslandSubtitle =>
      '連続曲率のカプセルで Island の輪郭を滑らかにします。オフ後に Hook を完全解除するにはスコープを再起動してください';

  @override
  String get smoothIslandSmoothingTitle => 'スムーズ強度';

  @override
  String get bluetoothIslandStatusEnabled => '有効';

  @override
  String get bluetoothIslandStatusDisabled => '無効';

  @override
  String get bluetoothIslandTitle => 'Bluetooth Island';

  @override
  String bluetoothIslandSubtitle(String status) {
    return '$status · Bluetooth デバイスの接続と切断を監視し、System UI から Island を転送します';
  }

  @override
  String get bluetoothIslandSettingsTitle => 'Bluetooth Island 設定';

  @override
  String get bluetoothIslandEnableTitle => 'Bluetooth Island を有効化';

  @override
  String get bluetoothIslandEnableSubtitle =>
      'オフにした後は System UI を再起動すると反映され、Bluetooth Hook は登録されません';

  @override
  String get bluetoothIslandShowDeviceNameTitle => 'デバイス名を表示';

  @override
  String get bluetoothIslandShowDeviceNameSubtitle =>
      '接続時は右側にデバイス名を先に表示し、その後に接続状態を表示します';

  @override
  String get bluetoothIslandDisplayDurationTitle => '表示時間';

  @override
  String get chargeIslandTitle => 'Charging Island';

  @override
  String chargeIslandSubtitle(String status) {
    return '$status · Charging Island の電力または電池表示部分を置き換えます';
  }

  @override
  String get chargeIslandSettingsTitle => 'Charging Island 設定';

  @override
  String get chargeIslandEnableTitle => 'Charging Island Hook を有効化';

  @override
  String get chargeIslandEnableSubtitle =>
      'オフにした後は System UI を再起動すると反映され、Hook は完全にバイパスされます';

  @override
  String get chargeIslandLeftModeTitle => '左側の動作';

  @override
  String get chargeIslandRightModeTitle => '右側の動作';

  @override
  String get chargeIslandModeDefault => 'デフォルト';

  @override
  String get chargeIslandModePower => '電力';

  @override
  String get chargeIslandModeVoltage => '電圧';

  @override
  String get chargeIslandModeCurrent => '電流';

  @override
  String get chargeIslandModeLevel => '電池残量';

  @override
  String get chargeIslandModeTemperature => '電池温度';

  @override
  String get chargeIslandDurationModeTitle => '表示時間';

  @override
  String get chargeIslandDurationDefault => 'デフォルト';

  @override
  String get chargeIslandDurationCustom => 'カスタム';

  @override
  String get chargeIslandDurationPersistent => '常駐';

  @override
  String get chargeIslandDurationSecondsTitle => 'カスタム時間';

  @override
  String get chargeIslandOuterGlowSubtitle => '控制充电超级岛的外圈光效';

  @override
  String get outerGlowTitle => '外側のグロー';

  @override
  String get bluetoothIslandOuterGlowSubtitle =>
      'Bluetooth Island の外側のグロー効果を制御します';

  @override
  String get outerGlowColorTitle => '外側のグローの色';

  @override
  String get hookScopeXMSF => 'Xiaomiサービスフレームワーク (XMSF)';

  @override
  String get downloadManagerSection => 'ダウンロードマネージャー';

  @override
  String get themePageTitle => 'テーマ';

  @override
  String get themeSeedColorTitle => 'テーマカラー';

  @override
  String get themeSeedColorSubtitle => 'アプリのアクセントカラーをカスタマイズ';

  @override
  String get presetColors => 'プリセットカラー';

  @override
  String get themeResetColor => 'デフォルトに戻す';

  @override
  String get blurBarsTitle => 'すりガラス効果';

  @override
  String get blurBarsSubtitle => 'トップバーとボトムバーにぼかし透明効果を追加';

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
  String get off => 'オフ';

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
  String get tapToSelectImage => 'タップして画像またはGIFを選択';

  @override
  String get autoExpandNotification => '通知を自動展開';

  @override
  String widthDpLabel(int width) {
    return '$width dp';
  }
}
