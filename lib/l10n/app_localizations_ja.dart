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
  String get appearanceSection => '外観';

  @override
  String get configSection => '構成';

  @override
  String get aboutSection => 'アプリについて';

  @override
  String get keepFocusNotifTitle => 'ダウンロードの一時停止後も通知をフォーカスする';

  @override
  String get keepFocusNotifSubtitle =>
      'ダウンロードを再開するための通知を表示しますが、状態の同期でズレが発生する可能性があります';

  @override
  String get checkUpdateOnLaunchTitle => '起動時に更新を確認する';

  @override
  String get checkUpdateOnLaunchSubtitle => 'アプリの起動時に最新のバージョンを自動で確認します';

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
  String get marqueeFeatureTitle => 'スクロールアイランド (実験的)';

  @override
  String get marqueeFeatureSubtitle => '長いテキストの通知を自動スクロールします';

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
  String get exportToFile => 'ファイルにエクスポート';

  @override
  String get exportToFileSubtitle => '構成を JSON ファイルで保存します';

  @override
  String get exportToClipboard => 'クリップボードにエクスポート';

  @override
  String get exportToClipboardSubtitle => '構成の JSON テキストをクリップボードにコピーします';

  @override
  String get importFromFile => 'ファイルからインポート';

  @override
  String get importFromFileSubtitle => 'JSON ファイルから構成を復元します';

  @override
  String get importFromClipboard => 'クリップボードからインポート';

  @override
  String get importFromClipboardSubtitle => 'クリップボードの JSON テキストから構成を復元します';

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
  String get templateNotificationIslandName => '通知 Island';

  @override
  String get template => 'テンプレート';

  @override
  String get islandIcon => 'Island のアイコン';

  @override
  String get focusIconLabel => 'フォーカスアイコン';

  @override
  String get focusNotificationLabel => 'フォーカス通知';

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
}
