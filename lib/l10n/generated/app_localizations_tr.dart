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
  String get later => 'Daha Sonra';

  @override
  String get goUpdate => 'Güncelle';

  @override
  String get sponsorSupport => 'Geliştiriciyi Destekle';

  @override
  String get sponsorAuthor => 'Sponsor Ol';

  @override
  String get documentation => 'Dokümantasyon';

  @override
  String versionUpdatedTitle(String version) {
    return '$version sürümüne güncellendi';
  }

  @override
  String get versionUpdatedContent =>
      'Güncellemeden sonra lütfen etki alanını yeniden başlatın';

  @override
  String get versionUpdatedChangelog =>
      'Değişiklik günlüğü: Görmek için dokunun';

  @override
  String get versionUpdatedStarHint =>
      'Uygulamayı beğendiyseniz lütfen ücretsiz bir Star verin';

  @override
  String get restartScope => 'Etki Alanını Yeniden Başlat';

  @override
  String get systemUI => 'Sistem Arayüzü';

  @override
  String get downloadManager => 'İndirme Yöneticisi';

  @override
  String get xmsf => 'XMSF (Xiaomi Hizmet Çerçevesi)';

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
  String get activated => 'Etkin';

  @override
  String get notActivated => 'Etkin Değil';

  @override
  String get enableInLSPosed => 'Lütfen bu modülü LSPosed içinde etkinleştirin';

  @override
  String lsposedApiVersion(int version) {
    return 'LSPosed API Sürümü: $version';
  }

  @override
  String get updateLSPosedRequired => 'Lütfen LSPosed sürümünü güncelleyin';

  @override
  String get systemNotSupported => 'Sistem Desteklenmiyor';

  @override
  String systemNotSupportedSubtitle(int version) {
    return 'Mevcut sistem Dynamic Island özelliğini desteklemiyor (protokol sürümü $version, gereken sürüm: 3)';
  }

  @override
  String restartFailed(String message) {
    return 'Yeniden başlatma başarısız: $message';
  }

  @override
  String get restartRootRequired =>
      'Lütfen bu uygulamaya root izni verildiğini doğrulayın';

  @override
  String get note1 =>
      '1. Bu sayfa yalnızca Dynamic Island desteğini test etmek içindir; gerçek görünümü yansıtmaz.';

  @override
  String get note2 =>
      '2. HyperCeiler\'da Sistem Arayüzü ve XMSF için odak bildirimi beyaz listesini kapatın.';

  @override
  String get note3 =>
      '3. LSPosed Manager\'da etkinleştirdikten sonra ilgili etki alanındaki uygulamaları yeniden başlatmanız gerekir.';

  @override
  String get note4 =>
      '4. Genel uyarlama desteklenir; uygun şablonu seçip deneyin.';

  @override
  String get behaviorSection => 'Davranış';

  @override
  String get defaultConfigSection => 'Uygulama Kanal Ayarları Varsayılanları';

  @override
  String get appearanceSection => 'Görünüm';

  @override
  String get configSection => 'Yapılandırma';

  @override
  String get aboutSection => 'Hakkında';

  @override
  String get keepFocusNotifTitle =>
      'İndirme Duraklatılsa da Odak Bildirimini Koru';

  @override
  String get keepFocusNotifSubtitle =>
      'İndirmeyi sürdürmek için tıklanabilir bir bildirim gösterir; durum senkronu bozulabilir.';

  @override
  String get unlockAllFocusTitle => 'Odak Bildirimi Beyaz Listesini Kaldır';

  @override
  String get unlockAllFocusSubtitle =>
      'Sistem yetkisi olmadan tüm uygulamaların odak bildirimi göndermesine izin verir.';

  @override
  String get unlockFocusAuthTitle => 'Odak Bildirimi İmza Doğrulamasını Kaldır';

  @override
  String get unlockFocusAuthSubtitle =>
      'İmza doğrulamasını atlayarak tüm uygulamaların saat/bilekliğe odak bildirimi göndermesine izin verir (XMSF hook gerekir).';

  @override
  String get checkUpdateOnLaunchTitle => 'Açılışta Güncellemeleri Denetle';

  @override
  String get checkUpdateOnLaunchSubtitle =>
      'Uygulama açılırken yeni sürümleri otomatik denetler.';

  @override
  String get showWelcomeTitle => 'Açılışta karşılama mesajını göster';

  @override
  String get showWelcomeSubtitle =>
      'Uygulama başladığında Ada üzerinde karşılama bilgisini göster';

  @override
  String get interactionHapticsTitle => 'Etkileşim haptikleri';

  @override
  String get interactionHapticsSubtitle =>
      'Anahtarlar, kaydırıcılar ve düğmeler için Hyper özel dokunsal geri bildirimi etkinleştir';

  @override
  String get checkUpdate => 'Güncellemeleri Denetle';

  @override
  String get alreadyLatest => 'Zaten en güncel sürümdesiniz';

  @override
  String get useAppIconTitle => 'Uygulama simgesini kullan';

  @override
  String get useAppIconSubtitle =>
      'İndirme yöneticisi bildirimleri için uygulama simgesini kullan';

  @override
  String get roundIconTitle => 'Simge Köşelerini Yuvarla';

  @override
  String get roundIconSubtitle =>
      'Bildirim simgelerine yuvarlatılmış köşe uygular.';

  @override
  String get marqueeChannelTitle => 'Ada Metnini Kaydır';

  @override
  String get marqueeSpeedTitle => 'Kaydırma Hızı';

  @override
  String marqueeSpeedLabel(int speed) {
    return '$speed px/sn';
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
  String get themeModeTitle => 'Tema';

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
  String get exportToFile => 'Dosyaya Dışa Aktar';

  @override
  String get exportToFileSubtitle =>
      'Yapılandırmayı JSON dosyası olarak kaydeder.';

  @override
  String get exportToClipboard => 'Panoya Dışa Aktar';

  @override
  String get exportToClipboardSubtitle =>
      'Yapılandırmayı JSON metni olarak panoya kopyalar.';

  @override
  String get exportConfig => 'Yapılandırmayı Dışa Aktar';

  @override
  String get exportConfigSubtitle =>
      'Dosyaya veya panoya dışa aktarma yöntemini seçin';

  @override
  String get importFromFile => 'Dosyadan İçe Aktar';

  @override
  String get importFromFileSubtitle =>
      'Yapılandırmayı JSON dosyasından geri yükler.';

  @override
  String get importFromClipboard => 'Panodan İçe Aktar';

  @override
  String get importFromClipboardSubtitle =>
      'Panodaki JSON metninden yapılandırmayı geri yükler.';

  @override
  String get importConfig => 'Yapılandırmayı İçe Aktar';

  @override
  String get importConfigSubtitle =>
      'Dosyadan veya panodan içe aktarma yöntemini seçin';

  @override
  String get qqGroup => 'QQ Topluluk Grubu';

  @override
  String get restartScopeApp =>
      'Ayarların geçerli olması için etki alanındaki uygulamayı yeniden başlatın';

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
    return 'İçe aktarma başarılı, toplam $count öğe yüklendi. Lütfen uygulamayı yeniden başlatın.';
  }

  @override
  String importFailed(String error) {
    return 'İçe aktarma başarısız: $error';
  }

  @override
  String get appAdaptation => 'Uygulama Listesi';

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
    return '$count uygulama seçildi';
  }

  @override
  String get cancelSelection => 'Seçimi İptal Et';

  @override
  String get deselectAll => 'Tüm Seçimi Kaldır';

  @override
  String get selectAll => 'Tümünü Seç';

  @override
  String get batchChannelSettings => 'Toplu Kanal Ayarı';

  @override
  String get selectEnabledApps => 'Etkin Uygulamaları Seç';

  @override
  String get batchEnable => 'Toplu Etkinleştir';

  @override
  String get batchDisable => 'Toplu Devre Dışı Bırak';

  @override
  String get multiSelect => 'Çoklu Seçim';

  @override
  String get showSystemApps => 'Sistem Uygulamaları';

  @override
  String get refreshList => 'Listeyi Yenile';

  @override
  String get enableAll => 'Tümünü Etkinleştir';

  @override
  String get disableAll => 'Tümünü Devre Dışı Bırak';

  @override
  String enabledAppsCount(int count) {
    return 'Dynamic Island, $count uygulama için etkin';
  }

  @override
  String enabledAppsCountWithSystem(int count) {
    return 'Dynamic Island, $count uygulama için etkin (sistem uygulamaları dahil)';
  }

  @override
  String get searchApps => 'Uygulama adında veya paket adında ara';

  @override
  String get noAppsFound =>
      'Yüklü uygulama bulunamadı\nUygulama listesi izninin açık olduğunu kontrol edin';

  @override
  String get noMatchingApps => 'Eşleşen uygulama bulunamadı';

  @override
  String applyToSelectedAppsChannels(int count) {
    return 'Seçili $count uygulamanın etkin kanallarına uygulanacak';
  }

  @override
  String get applyingConfig => 'Yapılandırma uygulanıyor...';

  @override
  String progressApps(int done, int total) {
    return '$done / $total uygulama';
  }

  @override
  String batchApplied(int count) {
    return 'Toplu ayar $count uygulamaya uygulandı';
  }

  @override
  String get cannotReadChannels => 'Bildirim Kanalları Okunamıyor';

  @override
  String get rootRequiredMessage =>
      'Bildirim kanallarını okumak için root izni gerekir.\nLütfen bu uygulamaya root izni verdiğinizi doğrulayıp tekrar deneyin.';

  @override
  String get enableAllChannels => 'Tüm Kanalları Etkinleştir';

  @override
  String get noChannelsFound => 'Bildirim kanalı bulunamadı';

  @override
  String get noChannelsFoundSubtitle =>
      'Bu uygulama henüz bildirim kanalı oluşturmamış olabilir veya kanallar okunamıyor.';

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
      'Uygulama devre dışı; aşağıdaki kanal ayarları etkisizdir';

  @override
  String channelImportance(String importance, String id) {
    return 'Önem: $importance  ·  $id';
  }

  @override
  String get channelSettings => 'Kanal Ayarları';

  @override
  String get toastForwardTitle => 'Standart Toast\'u yönlendir';

  @override
  String get toastForwardSubtitle =>
      'Bu uygulamanın standart Toast metnini HyperIsland odak bildirimi ve super island olarak ilet';

  @override
  String get toastBlockOriginalTitle => 'Orijinal Toast\'u engelle';

  @override
  String get toastBlockOriginalSubtitle =>
      'Yönlendirdikten sonra bu uygulamanın orijinal standart Toast penceresini engelle';

  @override
  String get toastShowNotificationTitle => 'Bildirim olarak göster';

  @override
  String get toastShowNotificationSubtitle =>
      'Açıkken yönlendirilen Toast, bildirim merkezinde görünür kalır';

  @override
  String get toastShowIslandIconTitle => 'Super island simgesini göster';

  @override
  String get toastShowIslandIconSubtitle =>
      'Yönlendirilen Toast için büyük adanın sol simgesini göster';

  @override
  String get toastStandardOnlyHint =>
      'Yalnızca standart metin Toast işlenir; özel Toast görünümleri yok sayılır.';

  @override
  String get importanceNone => 'Yok';

  @override
  String get importanceMin => 'En Düşük';

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
    return 'Etkin olan $count kanala uygulanacak';
  }

  @override
  String applyToAllChannels(int count) {
    return 'Tüm $count kanala uygulanacak';
  }

  @override
  String get templateDownloadName => 'İndirme';

  @override
  String get templateNotificationIslandName => 'Bildirim Süper Ada';

  @override
  String get templateNotificationIslandLiteName => 'Bildirim Süper Ada|Lite';

  @override
  String get templateDownloadLiteName => 'İndirme|Lite';

  @override
  String get islandSection => 'Ada';

  @override
  String get template => 'Şablon';

  @override
  String get rendererLabel => 'Stil';

  @override
  String get rendererImageTextWithButtons4Name =>
      'Görsel + Metin + Alt Metin Düğmeleri';

  @override
  String get rendererCoverInfoName => 'Kapak Bilgisi + Otomatik Satır Kaydırma';

  @override
  String get rendererImageTextWithRightTextButtonName =>
      'Görsel + Metin + Sağ Metin Düğmesi';

  @override
  String get rendererImageTextWithProgressName => 'IM图文组件 + 进度条组件';

  @override
  String get islandIcon => 'Ada Simgesi';

  @override
  String get islandIconLabel => 'Büyük Ada Simgesini Göster';

  @override
  String get islandIconLabelSubtitle =>
      'Bu ayar açık olduğunda büyük Ada simgesi gösterilir (küçük Ada etkilenmez).';

  @override
  String get focusIconLabel => 'Odak Simgesi';

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
  String get focusNotificationLabel => 'Odak Bildirimini Kullan';

  @override
  String get preserveStatusBarSmallIconLabel =>
      'Durum Çubuğu Küçük Simgesini Koru';

  @override
  String get restoreLockscreenTitle => 'Kilit Ekranı Bildirimini Geri Yükle';

  @override
  String get restoreLockscreenSubtitle =>
      'Kilit ekranında odak bildirimi işlemini atlayın, özgün gizlilik davranışını koruyun';

  @override
  String get firstFloatLabel => 'İlk Bildirimde Genişlet';

  @override
  String get updateFloatLabel => 'Güncellemede Yeniden Genişlet';

  @override
  String get autoDisappear => 'Otomatik Kapanma';

  @override
  String get seconds => 'sn';

  @override
  String get highlightColorLabel => 'Vurgu Rengi';

  @override
  String get dynamicHighlightColorLabel => 'Dinamik vurgu rengi';

  @override
  String get dynamicHighlightColorLabelSubtitle =>
      'Varsayılan olarak simgeden dinamik renk kullan';

  @override
  String get dynamicHighlightModeDark => 'Koyu';

  @override
  String get dynamicHighlightModeDarker => 'Daha koyu';

  @override
  String get outerGlowLabel => 'Dış parlama';

  @override
  String get outEffectColorLabel => 'Dış parlama rengi';

  @override
  String get highlightColorHint =>
      '#RRGGBB formatı, varsayılan için boş bırakın';

  @override
  String get actionBgColorLabel => 'Düğme arka plan rengi';

  @override
  String get actionBgColorDarkLabel => 'Düğme arka plan rengi (koyu)';

  @override
  String get actionTitleColorLabel => 'Düğme yazı rengi';

  @override
  String get actionTitleColorDarkLabel => 'Düğme yazı rengi (koyu)';

  @override
  String get action1BgColorLabel => 'Düğme 1 arka plan rengi';

  @override
  String get action1BgColorDarkLabel => 'Düğme 1 arka plan rengi (koyu)';

  @override
  String get action1TitleColorLabel => 'Düğme 1 yazı rengi';

  @override
  String get action1TitleColorDarkLabel => 'Düğme 1 yazı rengi (koyu)';

  @override
  String get action2BgColorLabel => 'Düğme 2 arka plan rengi';

  @override
  String get action2BgColorDarkLabel => 'Düğme 2 arka plan rengi (koyu)';

  @override
  String get action2TitleColorLabel => 'Düğme 2 yazı rengi';

  @override
  String get action2TitleColorDarkLabel => 'Düğme 2 yazı rengi (koyu)';

  @override
  String get textHighlightLabel => 'Metin vurgusu';

  @override
  String get narrowFontLabel => 'Dar yazı tipi';

  @override
  String get showLeftHighlightLabel => 'Sol metin vurgusu';

  @override
  String get showRightHighlightLabel => 'Sağ metin vurgusu';

  @override
  String get showLeftHighlightShort => 'Sol';

  @override
  String get showRightHighlightShort => 'Sağ';

  @override
  String get colorHue => 'Ton';

  @override
  String get colorSaturation => 'Doygunluk';

  @override
  String get colorBrightness => 'Parlaklık';

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
      'Kara listedeki bir uygulama açıldığında odak bildiriminin otomatik genişletilmesi devre dışı kalır';

  @override
  String get presetGamesTitle => 'Popüler Oyunları Tek Dokunuşla Filtrele';

  @override
  String presetGamesSuccess(int count) {
    return 'Ön ayardan $count yüklü oyun kara listeye eklendi';
  }

  @override
  String blacklistedAppsCount(int count) {
    return '$count uygulamanın odak bildirimi engellendi';
  }

  @override
  String blacklistedAppsCountWithSystem(int count) {
    return '$count uygulamanın odak bildirimi engellendi (sistem uygulamaları dahil)';
  }

  @override
  String get firstFloatLabelSubtitle =>
      'Bu ayar açık olduğunda ilk bildirim geldiğinde Ada genişler.';

  @override
  String get updateFloatLabelSubtitle =>
      'Bu ayar açık olduğunda bildirim güncellendiğinde Ada yeniden genişler.';

  @override
  String get marqueeChannelTitleSubtitle =>
      'Bu ayar açık olduğunda uzun metin Ada üzerinde kayarak gösterilir.';

  @override
  String get focusNotificationLabelSubtitle =>
      'Bu ayar açık olduğunda normal bildirim yerine odak bildirimi gösterilir. Kapalıysa normal bildirim gösterilir.';

  @override
  String get preserveStatusBarSmallIconLabelSubtitle =>
      'Bu ayar açık olduğunda odak bildirimi sırasında durum çubuğu küçük simgesi görünür kalır.';

  @override
  String get fullscreenBehaviorTitle => 'Tam ekran davranışı';

  @override
  String get fullscreenBehaviorSubtitle =>
      'Yatay/tam ekran algılandığında bildirim stratejisi';

  @override
  String get fullscreenBehaviorOff => 'Kapalı';

  @override
  String get fullscreenBehaviorFallback => 'Normal bildirime dön';

  @override
  String get fullscreenBehaviorExpand => 'Bildirimi otomatik genişlet';

  @override
  String get aiConfigSection => 'AI Geliştirmeleri';

  @override
  String get aiConfigTitle => 'AI Bildirim Özeti';

  @override
  String get aiConfigSubtitleEnabled =>
      'Etkin · AI parametrelerini yapılandırmak için dokunun';

  @override
  String get aiConfigSubtitleDisabled => 'Kapalı · Yapılandırmak için dokunun';

  @override
  String get aiEnabledTitle => 'AI Özetini Etkinleştir';

  @override
  String get aiEnabledSubtitle =>
      'Ada\'nın sol ve sağ metni AI tarafından üretilir; zaman aşımı veya hata durumunda otomatik geri dönüş yapılır';

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
  String get aiPromptLabel => 'Özel Prompt';

  @override
  String get aiPromptHint =>
      'Boş bırakırsanız varsayılan prompt kullanılır: Bildirimden ana bilgiyi çıkarın; sol ve sağ metin ayrı ayrı en fazla 6 kelime veya 12 karakter olsun';

  @override
  String get aiPromptInUserTitle => 'Prompt\'u kullanıcı mesajına yerleştir';

  @override
  String get aiPromptInUserSubtitle =>
      'Bazı modeller sistem talimatlarını desteklemez; etkinleştirilirse prompt kullanıcı mesajına eklenir';

  @override
  String get aiTimeoutTitle => 'AI Yanıt Zaman Aşımı';

  @override
  String aiTimeoutLabel(int seconds) {
    return 'AI Yanıt Zaman Aşımı';
  }

  @override
  String get aiTemperatureTitle => 'Örnekleme Sıcaklığı';

  @override
  String get aiTemperatureSubtitle =>
      'Yanıtların rastgeleliğini kontrol eder. 0 daha kesin, 1 daha yaratıcıdır';

  @override
  String get aiMaxTokensTitle => 'Maksimum Token';

  @override
  String get aiMaxTokensSubtitle =>
      'AI tarafından üretilen yanıtların en fazla uzunluğunu sınırlar';

  @override
  String get aiDefaultPromptFull =>
      'Boş bırakırsanız varsayılan prompt kullanılır: Bildirimden ana bilgiyi çıkarın; sol ve sağ taraf için en fazla 6 kelime veya 12 karakter';

  @override
  String get aiTestButton => 'Bağlantıyı Dene';

  @override
  String get aiTestUrlEmpty => 'Lütfen önce API adresini girin';

  @override
  String get aiLastLogTitle => 'Son AI İstek Günlüğü';

  @override
  String get aiLastLogSubtitle =>
      'Bağlantı testi veya bildirimler tarafından tetiklenen AI istekleri burada gösterilir';

  @override
  String get aiLastLogEmpty => 'Henüz gösterilecek AI istek günlüğü yok';

  @override
  String get aiLastLogSourceLabel => 'Kaynak';

  @override
  String get aiLastLogTimeLabel => 'Zaman';

  @override
  String get aiLastLogStatusLabel => 'Durum';

  @override
  String get aiLastLogDurationLabel => 'Süre';

  @override
  String get aiLastLogSourceNotification => 'Bildirim Tetiklemesi';

  @override
  String get aiLastLogSourceSettingsTest => 'Ayar Testi';

  @override
  String get aiLastLogRendered => 'İşlenmiş';

  @override
  String get aiLastLogRaw => 'Ham';

  @override
  String get aiLastLogCopy => 'Günlüğü Kopyala';

  @override
  String get aiLastLogCopied => 'AI istek günlüğü kopyalandı';

  @override
  String get aiLastLogRequest => 'İstek';

  @override
  String get aiLastLogResponse => 'Yanıt';

  @override
  String get aiLastLogUsage => 'Token Kullanımı';

  @override
  String get aiLastLogMessages => 'Mesajlar';

  @override
  String get aiLastLogError => 'Hata';

  @override
  String get aiLastLogHttpCode => 'HTTP Durum Kodu';

  @override
  String get aiLastLogLeftText => 'Sol Metin';

  @override
  String get aiLastLogRightText => 'Sağ Metin';

  @override
  String get aiLastLogAssistantContent => 'Model Yanıt İçeriği';

  @override
  String get aiConfigSaveButton => 'Kaydet';

  @override
  String get aiConfigSaved => 'AI yapılandırması kaydedildi';

  @override
  String get aiConfigTips =>
      'AI, bildirimdeki uygulama paket adını, başlığı ve metni alır; solda (kaynak) ve sağda (içerik) kısa metin üretir. OpenAI formatı ile uyumlu API\'leri destekler (DeepSeek, Claude vb.). Yanıt gelmezse varsayılan mantığa geri döner.';

  @override
  String get templateAiNotificationIslandName => 'AI Bildirim Süper Ada';

  @override
  String get hideDesktopIconTitle => 'Ana Ekran Simgesini Gizle';

  @override
  String get hideDesktopIconSubtitle =>
      'Uygulama simgesini başlatıcıdan gizler. Gizledikten sonra LSPosed Manager üzerinden açın';
}
