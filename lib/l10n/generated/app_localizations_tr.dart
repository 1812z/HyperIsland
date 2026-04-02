// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Turkish (`tr`).
class AppLocalizationsTr extends AppLocalizations {
  AppLocalizationsTr([String locale = 'tr']) : super(locale);

  @override
  String get navHome => 'Ana Sayfa';

  @override
  String get navApps => 'Uygulamalar';

  @override
  String get navSettings => 'Ayarlar';

  @override
  String get cancel => 'İptal';

  @override
  String get confirm => 'Onayla';

  @override
  String get ok => 'Tamam';

  @override
  String get apply => 'Uygula';

  @override
  String get noChange => 'Değiştirme';

  @override
  String get newVersionFound => 'Yeni Sürüm Bulundu';

  @override
  String currentVersion(String version) {
    return 'Mevcut sürüm: $version';
  }

  @override
  String latestVersion(String version) {
    return 'En son sürüm: $version';
  }

  @override
  String get later => 'Sonra Hatırlat';

  @override
  String get goUpdate => 'Güncelle';

  @override
  String get sponsorSupport => 'Sponsor Desteği';

  @override
  String get sponsorAuthor => 'Geliştiriciye Destek Ol';

  @override
  String get restartScope => 'Kapsamı Yeniden Başlat';

  @override
  String get systemUI => 'Sistem Arayüzü';

  @override
  String get downloadManager => 'İndirme Yöneticisi';

  @override
  String get xmsf => 'Xiaomi Servis Çerçevesi';

  @override
  String get notificationTest => 'Bildirim Testi';

  @override
  String get sendTestNotification => 'Test Bildirimi Gönder';

  @override
  String get notes => 'Notlar';

  @override
  String get detectingModuleStatus => 'Modül durumu algılanıyor...';

  @override
  String get moduleStatus => 'Modül Durumu';

  @override
  String get activated => 'Etkinleştirildi';

  @override
  String get notActivated => 'Etkinleştirilmedi';

  @override
  String get enableInLSPosed => 'Lütfen bu modülü LSPosed içinde etkinleştirin';

  @override
  String lsposedApiVersion(int version) {
    return 'LSPosed API 版本: $version';
  }

  @override
  String get updateLSPosedRequired => '请更新 LSPosed 版本';

  @override
  String get systemNotSupported => 'Sistem Desteklenmiyor';

  @override
  String systemNotSupportedSubtitle(int version) {
    return 'Mevcut sistem Dynamic Island özelliğini desteklemiyor (protokol sürümü $version, sürüm 3 gerekli)';
  }

  @override
  String restartFailed(String message) {
    return 'Yeniden başlatma başarısız: $message';
  }

  @override
  String get restartRootRequired =>
      'Lütfen uygulamaya ROOT izni verilip verilmediğini kontrol edin';

  @override
  String get note1 =>
      '1. Bu sayfa yalnızca Dynamic Island desteğini test etmek içindir; gerçek deneyimi yansıtmaz';

  @override
  String get note2 =>
      '2. HyperCeiler\'da Sistem Arayüzü ve Xiaomi Servis Çerçevesi için odaklanmış bildirim beyaz listesini kapatın';

  @override
  String get note3 =>
      '3. LSPosed yöneticisinde etkinleştirildikten sonra ilgili kapsam uygulamalarının yeniden başlatılması gerekir';

  @override
  String get note4 =>
      '4. Genel uyum desteklenmektedir; uygun şablonu kendiniz seçerek deneyin';

  @override
  String get behaviorSection => 'Davranış';

  @override
  String get defaultConfigSection => 'Kanal Varsayılan Yapılandırması';

  @override
  String get appearanceSection => 'Görünüm';

  @override
  String get configSection => 'Yapılandırma';

  @override
  String get aboutSection => 'Hakkında';

  @override
  String get keepFocusNotifTitle =>
      'İndirme Yöneticisi Duraklatıldığında Odaklanmış Bildirimi Koru';

  @override
  String get keepFocusNotifSubtitle =>
      'İndirmeye devam etmek için tıklanabilecek bir bildirim gösterir; durum tutarsızlığına yol açabilir';

  @override
  String get unlockAllFocusTitle =>
      'Odaklanmış Bildirim Beyaz Listesini Kaldır';

  @override
  String get unlockAllFocusSubtitle =>
      'Sistem yetkisi gerekmeksizin tüm uygulamaların odaklanmış bildirim göndermesine izin verir';

  @override
  String get unlockFocusAuthTitle =>
      'Odaklanmış Bildirim İmza Doğrulamasını Kaldır';

  @override
  String get unlockFocusAuthSubtitle =>
      'İmza doğrulaması atlanarak tüm uygulamaların saat/bilekliğe odaklanmış bildirim göndermesine izin verir (Xiaomi Servis Çerçevesi Hook gerektirir)';

  @override
  String get checkUpdateOnLaunchTitle => 'Başlangıçta Güncelleme Kontrol Et';

  @override
  String get checkUpdateOnLaunchSubtitle =>
      'Uygulama açılırken otomatik olarak yeni sürüm kontrolü yapar';

  @override
  String get showWelcomeTitle => '显示启动欢迎语';

  @override
  String get showWelcomeSubtitle => '应用启动时在超级岛显示欢迎信息';

  @override
  String get interactionHapticsTitle => 'Etkileşim Dokunuşu';

  @override
  String get interactionHapticsSubtitle =>
      'Anahtarlar, kaydırıcılar ve düğmeler için Hyper özel dokunsal geri bildirimi etkinleştir';

  @override
  String get checkUpdate => 'Güncelleme Kontrol Et';

  @override
  String get alreadyLatest => 'En son sürümde';

  @override
  String get useAppIconTitle => 'Uygulama Simgesi Kullan';

  @override
  String get useAppIconSubtitle =>
      'İndirme yöneticisi bildirimleri uygulama simgesini kullanır';

  @override
  String get roundIconTitle => 'Simge Köşe Yuvarlama';

  @override
  String get roundIconSubtitle =>
      'Bildirim simgelerine yuvarlak köşe efekti ekler';

  @override
  String get marqueeChannelTitle => 'Kayan Mesaj';

  @override
  String get marqueeSpeedTitle => 'Kayan Hız';

  @override
  String marqueeSpeedLabel(int speed) {
    return '$speed piksel/saniye';
  }

  @override
  String get themeModeTitle => 'Renk Modu';

  @override
  String get themeModeSystem => 'Sistemi Takip Et';

  @override
  String get themeModeLight => 'Açık';

  @override
  String get themeModeDark => 'Koyu';

  @override
  String get languageTitle => 'Dil';

  @override
  String get languageAuto => 'Sistemi Takip Et';

  @override
  String get languageZh => '中文';

  @override
  String get languageEn => 'English';

  @override
  String get languageJa => '日本語';

  @override
  String get languageTr => 'Türkçe';

  @override
  String get exportToFile => 'Dosyaya Aktar';

  @override
  String get exportToFileSubtitle =>
      'Yapılandırmayı JSON dosyası olarak kaydet';

  @override
  String get exportToClipboard => 'Panoya Aktar';

  @override
  String get exportToClipboardSubtitle =>
      'Yapılandırmayı JSON metni olarak kopyala';

  @override
  String get exportConfig => 'Yapılandırmayı dışa aktar';

  @override
  String get exportConfigSubtitle => 'Hedef olarak dosya veya panoyu seç';

  @override
  String get importFromFile => 'Dosyadan İçe Aktar';

  @override
  String get importFromFileSubtitle =>
      'Yapılandırmayı JSON dosyasından geri yükle';

  @override
  String get importFromClipboard => 'Panodan İçe Aktar';

  @override
  String get importFromClipboardSubtitle =>
      'Yapılandırmayı panodan JSON metni olarak geri yükle';

  @override
  String get importConfig => 'Yapılandırmayı içe aktar';

  @override
  String get importConfigSubtitle => 'Kaynak olarak dosya veya panoyu seç';

  @override
  String get qqGroup => 'QQ Tartışma Grubu';

  @override
  String get restartScopeApp =>
      'Ayarların geçerli olması için kapsam uygulamasını yeniden başlatın';

  @override
  String get groupNumberCopied => 'Grup numarası panoya kopyalandı';

  @override
  String exportedTo(String path) {
    return 'Dışa aktarıldı: $path';
  }

  @override
  String exportFailed(String error) {
    return 'Dışa aktarma başarısız: $error';
  }

  @override
  String get configCopied => 'Yapılandırma panoya kopyalandı';

  @override
  String importSuccess(int count) {
    return 'İçe aktarma başarılı, toplam $count yapılandırma öğesi; değişikliklerin uygulanması için uygulamayı yeniden başlatın';
  }

  @override
  String importFailed(String error) {
    return 'İçe aktarma başarısız: $error';
  }

  @override
  String get appAdaptation => 'Uygulama Uyumu';

  @override
  String selectedAppsCount(int count) {
    return '$count uygulama seçildi';
  }

  @override
  String get cancelSelection => 'Seçimi İptal Et';

  @override
  String get deselectAll => 'Tümünü Kaldır';

  @override
  String get selectAll => 'Tümünü Seç';

  @override
  String get batchChannelSettings => 'Toplu Kanal Yapılandırması';

  @override
  String get selectEnabledApps => 'Etkin Uygulamaları Seç';

  @override
  String get batchEnable => 'Toplu Etkinleştir';

  @override
  String get batchDisable => 'Toplu Devre Dışı Bırak';

  @override
  String get multiSelect => 'Çoklu Seçim';

  @override
  String get showSystemApps => 'Sistem Uygulamalarını Göster';

  @override
  String get refreshList => 'Listeyi Yenile';

  @override
  String get enableAll => 'Tümünü Etkinleştir';

  @override
  String get disableAll => 'Tümünü Devre Dışı Bırak';

  @override
  String enabledAppsCount(int count) {
    return '$count uygulama için Dynamic Island etkinleştirildi';
  }

  @override
  String enabledAppsCountWithSystem(int count) {
    return '$count uygulama için Dynamic Island etkinleştirildi (sistem uygulamaları dahil)';
  }

  @override
  String get searchApps => 'Uygulama adı veya paket adıyla arayın';

  @override
  String get noAppsFound =>
      'Yüklü uygulama bulunamadı\nUygulama listesi izninin etkin olup olmadığını kontrol edin';

  @override
  String get noMatchingApps => 'Eşleşen uygulama bulunamadı';

  @override
  String applyToSelectedAppsChannels(int count) {
    return 'Seçili $count uygulamanın etkin kanallarına uygulanacak';
  }

  @override
  String get applyingConfig => 'Yapılandırma uygulanıyor…';

  @override
  String progressApps(int done, int total) {
    return '$done / $total uygulama';
  }

  @override
  String batchApplied(int count) {
    return '$count uygulamaya toplu olarak uygulandı';
  }

  @override
  String get cannotReadChannels => 'Bildirim kanalları okunamıyor';

  @override
  String get rootRequiredMessage =>
      'Bildirim kanallarını okumak için ROOT izni gereklidir.\nLütfen uygulamaya ROOT izninin verildiğini doğrulayıp tekrar deneyin.';

  @override
  String get enableAllChannels => 'Tüm Kanalları Etkinleştir';

  @override
  String get noChannelsFound => 'Bildirim kanalı bulunamadı';

  @override
  String get noChannelsFoundSubtitle =>
      'Bu uygulama henüz bildirim kanalı oluşturmamış veya kanallar okunamıyor';

  @override
  String allChannelsActive(int count) {
    return 'Tüm $count kanal için geçerli';
  }

  @override
  String selectedChannels(int selected, int total) {
    return '$selected / $total kanal seçildi';
  }

  @override
  String allChannelsDisabled(int count) {
    return 'Tüm $count kanal (devre dışı)';
  }

  @override
  String get appDisabledBanner =>
      'Uygulamanın ana anahtarı kapalı; aşağıdaki kanal ayarları geçerli olmayacak';

  @override
  String channelImportance(String importance, String id) {
    return 'Önem: $importance  ·  $id';
  }

  @override
  String get channelSettings => 'Kanal Ayarları';

  @override
  String get importanceNone => 'Yok';

  @override
  String get importanceMin => 'Çok Düşük';

  @override
  String get importanceLow => 'Düşük';

  @override
  String get importanceDefault => 'Varsayılan';

  @override
  String get importanceHigh => 'Yüksek';

  @override
  String get importanceUnknown => 'Bilinmiyor';

  @override
  String applyToEnabledChannels(int count) {
    return 'Etkin $count kanala uygulanacak';
  }

  @override
  String applyToAllChannels(int count) {
    return 'Tüm $count kanala uygulanacak';
  }

  @override
  String get templateDownloadName => 'İndirme';

  @override
  String get templateNotificationIslandName => 'Bildirim Dynamic Island';

  @override
  String get templateNotificationIslandLiteName =>
      'Bildirim Dynamic Island | Hafif';

  @override
  String get templateDownloadLiteName => 'İndirme | Lite';

  @override
  String get islandSection => 'Ada';

  @override
  String get template => 'Şablon';

  @override
  String get rendererLabel => 'Stil';

  @override
  String get rendererImageTextWithButtons4Name =>
      'Yeni Görsel+Metin Bileşeni + Alt Metin Düğmeleri';

  @override
  String get rendererCoverInfoName =>
      'Kapak Bileşeni + Otomatik Satır Kaydırma';

  @override
  String get rendererImageTextWithRightTextButtonName =>
      'Yeni Görsel+Metin Bileşeni + Sağ Metin Düğmesi';

  @override
  String get islandIcon => 'Dynamic Island Simgesi';

  @override
  String get islandIconLabel => '大岛图标';

  @override
  String get islandIconLabelSubtitle => '开启后显示超级岛的大图标（小岛不受影响）';

  @override
  String get focusIconLabel => 'Odak Simgesi';

  @override
  String get focusNotificationLabel => 'Odaklanmış Bildirim';

  @override
  String get preserveStatusBarSmallIconLabel => 'Durum Çubuğu Simgesi';

  @override
  String get restoreLockscreenTitle => 'Kilit Ekranı Bildirimini Geri Yükle';

  @override
  String get restoreLockscreenSubtitle =>
      'Kilit ekranında odak bildirim işlemesini atla ve özgün bildirim gizlilik davranışını koru';

  @override
  String get firstFloatLabel => 'İlk Açılış';

  @override
  String get updateFloatLabel => 'Güncelleme Açılışı';

  @override
  String get autoDisappear => 'Otomatik Kaybol';

  @override
  String get seconds => 'saniye';

  @override
  String get onlyEnabledChannels => 'Yalnızca Etkin Kanallara Uygula';

  @override
  String enabledChannelsCount(int enabled, int total) {
    return '$enabled / $total kanal etkin';
  }

  @override
  String get iconModeAuto => 'Otomatik';

  @override
  String get iconModeNotifSmall => 'Bildirim Küçük Simgesi';

  @override
  String get iconModeNotifLarge => 'Bildirim Büyük Simgesi';

  @override
  String get iconModeAppIcon => 'Uygulama Simgesi';

  @override
  String get optDefault => 'Varsayılan';

  @override
  String get optDefaultOn => 'Varsayılan (Açık)';

  @override
  String get optDefaultOff => 'Varsayılan (Kapalı)';

  @override
  String get optOn => 'Açık';

  @override
  String get optOff => 'Kapalı';

  @override
  String get errorInvalidFormat => 'Geçersiz yapılandırma biçimi';

  @override
  String get errorNoStorageDir => 'Depolama dizinine erişilemiyor';

  @override
  String get errorNoFileSelected => 'Dosya seçilmedi';

  @override
  String get errorNoFilePath => 'Dosya yolu alınamıyor';

  @override
  String get errorEmptyClipboard => 'Pano boş';

  @override
  String get navBlacklist => 'Bildirim Kara Listesi';

  @override
  String get navBlacklistSubtitle =>
      'Kara listedeki bir uygulama başlatıldığında odaklanmış bildirimlerin otomatik açılması devre dışı bırakılır';

  @override
  String get presetGamesTitle => 'Popüler Oyunları Tek Tıkla Filtrele';

  @override
  String presetGamesSuccess(int count) {
    return 'Şablondan $count yüklü oyun kara listeye eklendi';
  }

  @override
  String blacklistedAppsCount(int count) {
    return '$count uygulamanın odaklanmış bildirimleri engellendi';
  }

  @override
  String blacklistedAppsCountWithSystem(int count) {
    return '$count uygulamanın odaklanmış bildirimleri engellendi (sistem uygulamaları dahil)';
  }

  @override
  String get firstFloatLabelSubtitle =>
      'Dynamic Island ilk bildirimi aldığında odaklanmış bildirim olarak açılıp açılmayacağı';

  @override
  String get updateFloatLabelSubtitle =>
      'Dynamic Island güncellendiğinde bildirimin açılıp açılmayacağı';

  @override
  String get marqueeChannelTitleSubtitle =>
      'Dynamic Island mesajı çok uzun olduğunda kayan yazı olarak gösterilip gösterilmeyeceği';

  @override
  String get focusNotificationLabelSubtitle =>
      'Bildirimi odaklanmış bildirimle değiştirir (kapatıldığında orijinal bildirim gösterilir)';

  @override
  String get preserveStatusBarSmallIconLabelSubtitle =>
      'Odaklanmış bildirim açıkken durum çubuğu küçük simgesinin zorla korunup korunmayacağı';

  @override
  String get aiConfigSection => 'Yapay Zeka Geliştirme';

  @override
  String get aiConfigTitle => 'Yapay Zeka Bildirim Özeti';

  @override
  String get aiConfigSubtitleEnabled =>
      'Etkin · Yapay Zeka parametrelerini yapılandırmak için tıklayın';

  @override
  String get aiConfigSubtitleDisabled => 'Kapalı · Yapılandırmak için tıklayın';

  @override
  String get aiEnabledTitle => 'Yapay Zeka Özetini Etkinleştir';

  @override
  String get aiEnabledSubtitle =>
      'Dynamic Island sol ve sağ metni yapay zeka tarafından oluşturulur; zaman aşımı veya hata durumunda otomatik olarak geri döner';

  @override
  String get aiApiSection => 'API Parametreleri';

  @override
  String get aiUrlLabel => 'API Adresi';

  @override
  String get aiUrlHint => 'https://api.openai.com/v1/chat/completions';

  @override
  String get aiApiKeyLabel => 'API Anahtarı';

  @override
  String get aiApiKeyHint => 'sk-...';

  @override
  String get aiModelLabel => 'Model';

  @override
  String get aiModelHint => 'gpt-4o-mini';

  @override
  String get aiPromptLabel => 'Sistem İstemi';

  @override
  String get aiPromptHint => 'Varsayılanı kullanmak için boş bırakın';

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
  String get aiTestButton => 'Bağlantıyı Test Et';

  @override
  String get aiTestUrlEmpty => 'Lütfen önce API adresini girin';

  @override
  String get aiLastLogTitle => 'Son AI İstek Günlüğü';

  @override
  String get aiLastLogSubtitle =>
      'Bağlantı testi ve bildirim tetikli AI istekleri burada görünür';

  @override
  String get aiLastLogEmpty => 'Henüz gösterilecek bir AI istek günlüğü yok';

  @override
  String get aiLastLogSourceLabel => 'Kaynak';

  @override
  String get aiLastLogTimeLabel => 'Zaman';

  @override
  String get aiLastLogStatusLabel => 'Durum';

  @override
  String get aiLastLogDurationLabel => '耗时';

  @override
  String get aiLastLogSourceNotification => 'Bildirim';

  @override
  String get aiLastLogSourceSettingsTest => 'Ayar Testi';

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
  String get aiLastLogUsage => 'Token Kullanımı';

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
  String get aiConfigSaveButton => 'Kaydet';

  @override
  String get aiConfigSaved => 'Yapay Zeka yapılandırması kaydedildi';

  @override
  String get aiConfigTips =>
      'Yapay Zeka, bildirimin uygulama paket adını, başlığını ve gövdesini alarak sol (kaynak) ve sağ (içerik) kısa metinleri döndürür. OpenAI biçimiyle uyumlu API\'leri destekler (DeepSeek, Claude vb.). 3 saniye içinde yanıt gelmezse otomatik olarak varsayılan mantığa geri döner.';

  @override
  String get templateAiNotificationIslandName =>
      'Yapay Zeka Bildirim Dynamic Island';
}
