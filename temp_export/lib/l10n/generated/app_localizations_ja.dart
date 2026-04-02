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
  String lsposedApiVersion(int version) {
    return 'LSPosed API 版本: $version';
  }

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
  String get note1 =>
      '1. このページは Dynamic Island の対応をテストするためのものであり、実際の効果を示すものではありません。';

  @override
  String get note2 =>
      '2. HyperCeiler でシステム UI と MIUI フレームワークのフォーカス通知のホワイトリストを無効化してください。';

  @override
  String get note3 => '3. LSPosed Manager で有効化後に関連するスコープアプリを再起動する必要があります。';

  @override
  String get note4 => '4. 一般的なアダプティブ表示に対応しています。適切なテンプレートを確認してみてください。';

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
  String get keepFocusNotifTitle => 'ダウンロードの一時停止後もフォーカス通知を表示';

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
  String get showWelcomeTitle => '显示启动欢迎语';

  @override
  String get showWelcomeSubtitle => '应用启动时在超级岛显示欢迎信息';

  @override
  String get interactionHapticsTitle => '操作触覚';

  @override
  String get interactionHapticsSubtitle =>
      'スイッチ、スライダー、ボタンに Hyper カスタム触覚フィードバックを有効化します';

  @override
  String get checkUpdate => '更新を確認';

  @override
  String get alreadyLatest => '最新のバージョンを使用しています';

  @override
  String get useAppIconTitle => 'アプリアイコンを使用する';

  @override
  String get useAppIconSubtitle => 'ダウンロードマネージャーの通知でアプリアイコンを使用します';

  @override
  String get roundIconTitle => 'アイコンの角を丸める';

  @override
  String get roundIconSubtitle => '通知アイコンの角を丸めます';

  @override
  String get marqueeChannelTitle => 'Island のテキストをスクロール';

  @override
  String get marqueeSpeedTitle => '速度 ';

  @override
  String marqueeSpeedLabel(int speed) {
    return '$speed px/秒';
  }

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
  String get exportConfig => '設定をエクスポート';

  @override
  String get exportConfigSubtitle => '出力先としてファイルまたはクリップボードを選択します';

  @override
  String get importFromFile => 'ファイルからインポート';

  @override
  String get importFromFileSubtitle => 'JSON ファイルから構成を復元します';

  @override
  String get importFromClipboard => 'クリップボードからインポート';

  @override
  String get importFromClipboardSubtitle => 'クリップボードの JSON テキストから構成を復元します';

  @override
  String get importConfig => '設定をインポート';

  @override
  String get importConfigSubtitle => '入力元としてファイルまたはクリップボードを選択します';

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
    return '$count 個のチャンネルですべて有効';
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
  String get template => 'テンプレート';

  @override
  String get rendererLabel => 'スタイル';

  @override
  String get rendererImageTextWithButtons4Name => '画像+テキスト＋下部テキストボタン';

  @override
  String get rendererCoverInfoName => 'カバー情報+自動で折り返す';

  @override
  String get rendererImageTextWithRightTextButtonName => '画像+テキスト+右テキストボタン';

  @override
  String get islandIcon => 'Island のアイコン';

  @override
  String get focusIconLabel => 'フォーカスアイコン';

  @override
  String get focusNotificationLabel => 'フォーカス通知';

  @override
  String get preserveStatusBarSmallIconLabel => 'ステータスバーアイコン';

  @override
  String get firstFloatLabel => '最初にフロート表示';

  @override
  String get updateFloatLabel => '更新時にフロート表示';

  @override
  String get autoDisappear => '自動で無視';

  @override
  String get seconds => '秒';

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
  String get aiPromptLabel => 'システムプロンプト';

  @override
  String get aiPromptHint => '空のままにしてデフォルトを使用';

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
  String get aiDefaultPromptFull => '留空使用默认提示词：根据通知信息，提取关键信息，左右分别不超过6汉字12字符';

  @override
  String get aiTestButton => '接続テスト';

  @override
  String get aiTestUrlEmpty => '始めに API URL を入力してください';

  @override
  String get aiLastLogTitle => '最新の AI リクエストログ';

  @override
  String get aiLastLogSubtitle => '接続テストと通知トリガーの AI リクエストをここに表示します';

  @override
  String get aiLastLogEmpty => '表示できる AI リクエストログはまだありません';

  @override
  String get aiLastLogSourceLabel => 'ソース';

  @override
  String get aiLastLogTimeLabel => '時刻';

  @override
  String get aiLastLogStatusLabel => '状態';

  @override
  String get aiLastLogDurationLabel => '耗时';

  @override
  String get aiLastLogSourceNotification => '通知トリガー';

  @override
  String get aiLastLogSourceSettingsTest => '設定ページのテスト';

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
  String get aiLastLogUsage => 'Token 使用量';

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
  String get aiConfigSaved => 'AI の構成を保存しました';

  @override
  String get aiConfigTips =>
      'AI は各通知のアプリパッケージ、タイトル、コンテンツを受信し短い左側 (ソース) と右側 (コンテンツ) のテキストを返します。OpenAI 形式の API (DeepSeek、Claude など) と互換性があります。3 秒以内に応答がない場合は、デフォルトのロジックにフォールバックします。';

  @override
  String get templateAiNotificationIslandName => 'AI Notification Island';
}
