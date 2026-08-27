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
  String get navIsland => 'Ada';

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
  String lsposedApiVersion(int version) {
    return 'LSPosed API Sürümü: $version';
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
  String get donorList => 'Bagiscilar Listesi';

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
  String get customTestNotification => 'Özel Test Bildirimi';

  @override
  String get customTestTitle => 'Başlık';

  @override
  String get customTestTitleHint => 'Varsayılan başlık için boş bırakın';

  @override
  String get customTestContent => 'İçerik';

  @override
  String get customTestContentHint => 'Varsayılan içerik için boş bırakın';

  @override
  String get clearPreviousNotification => 'Önceki bildirimi temizle';

  @override
  String get clearPreviousNotificationSubtitle =>
      'Göndermeden önce mevcut ada bildirimini iptal et';

  @override
  String get enableFloatNotificationSubtitle =>
      'Bildirim alındığında odak bildirimi olarak otomatik genişlet';

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
  String get enableSystemUiScopeInLSPosed =>
      'Lütfen LSPosed kapsamında Sistem Arayüzü\'nü seçin';

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
      '1. Kullanmadan önce sağ üst köşedeki kullanım kılavuzunu mutlaka okuyun';

  @override
  String get note2 =>
      '2. Çoğu ayar sıcak yeniden yüklemeyi destekler; sorun yaşarsanız etki alanını yeniden başlatın';

  @override
  String get note3 =>
      '3. A16 için Sistem Arayüzü bileşeninin 17.1\'den yeni bir sürümü önerilir';

  @override
  String get note4 =>
      '4. Bu sayfa yalnızca Dynamic Island ve dış parlama desteğini test etmek içindir; gerçek görünümü yansıtmaz';

  @override
  String get note5 =>
      '5. İndirme adası için lütfen \"İndirme Yöneticisi\" kapsamını manuel olarak etkinleştirin; \"İndirme\" şablonu önerilir';

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
  String get debugLogTitle => 'Debug Loglarını Göster';

  @override
  String get debugLogSubtitle =>
      'Etkinleştirildiğinde Hook debug logları çıktı olarak verilir; devre dışı bırakıldığında sadece uyarı ve hata logları tutulur';

  @override
  String get showWelcomeTitle => 'Açılışta karşılama mesajını göster';

  @override
  String get showWelcomeSubtitle =>
      'Uygulama başladığında Ada üzerinde karşılama bilgisini göster';

  @override
  String get openOnboardingTitle => 'İlk kurulumu aç';

  @override
  String get openOnboardingSubtitle =>
      'Karşılama ve hızlı başlangıç akışını yeniden görüntüle';

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
  String get roundIconRadiusTitle => 'Köşe yuvarlaklığı';

  @override
  String get roundIconTitle => 'Bildirim simgesi köşeleri';

  @override
  String get roundIconSubtitle =>
      'Bildirim simgelerine yuvarlatılmış köşeler ekle';

  @override
  String get islandIconSectionTitle => 'Simgeler';

  @override
  String get iconSizeTitle => 'Simge boyutu';

  @override
  String get iconPaddingTitle => 'Simge kenar boşluğu';

  @override
  String get marqueeChannelTitle => 'Ada Metnini Kaydır';

  @override
  String get marqueeAutoHideTitle => 'Hide Island after scrolling';

  @override
  String get marqueeAutoHideSubtitle =>
      'Hide the current Island after the message scrolls the selected number of times';

  @override
  String get marqueeAutoHideOnce => 'Scroll once';

  @override
  String get marqueeAutoHideTwice => 'Scroll twice';

  @override
  String get marqueeAutoHideOnceOverride =>
      '1 kez kaydır (zaman aşımını geçersiz kıl)';

  @override
  String get marqueeAutoHideTwiceOverride =>
      '2 kez kaydır (zaman aşımını geçersiz kıl)';

  @override
  String get marqueeSpeedTitle => 'Kaydırma Hızı';

  @override
  String marqueeSpeedLabel(int speed) {
    return '$speed px/sn';
  }

  @override
  String get bigIslandMaxWidthTitle => 'Max Width';

  @override
  String get bigIslandMinWidthTitle => 'Min Width';

  @override
  String get testNotifTooltip => 'Send test notification';

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
  String get languageRu => 'Русский';

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
  String get exportConfig => 'Yapılandırmayı Dışa Aktar';

  @override
  String get exportConfigSubtitle =>
      'Dosyaya veya panoya dışa aktarma yöntemini seçin';

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
  String get toastAdaptation => 'Toast Adaptation';

  @override
  String get adaptationModeNotification => 'Notification';

  @override
  String get adaptationModeToast => 'Toast';

  @override
  String toastEnabledAppsCount(Object count) {
    return 'Toast intercept enabled for $count apps';
  }

  @override
  String toastEnabledAppsCountWithSystem(Object count) {
    return 'Toast intercept enabled for $count apps (including system apps)';
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
  String get islandEnabledLabel => 'Adayı etkinleştir';

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
  String get rendererImageTextWithProgressName => 'IM Image+Text+Progress';

  @override
  String get islandIcon => 'Ada Simgesi';

  @override
  String get focusIconLabel => 'Odak Simgesi';

  @override
  String get focusExpressionCustomizationSection =>
      'Focus advanced customization';

  @override
  String get islandExpressionCustomizationSection =>
      'Island advanced customization';

  @override
  String get aodSection => 'Always-on display';

  @override
  String get expandCustomization => 'Expand';

  @override
  String get collapseCustomization => 'Collapse';

  @override
  String get availablePlaceholdersLabel =>
      'Available placeholders(Click to copy)';

  @override
  String get expressionFunctionsLabel => 'Expression functions';

  @override
  String get focusTitleExprLabel => 'Focus title expression';

  @override
  String get focusContentExprLabel => 'Focus content expression';

  @override
  String get focusIconSourceLabel => 'Focus icon source';

  @override
  String get focusPicProfileSourceLabel => 'Profile icon source';

  @override
  String get focusAppIconPkgLabel => 'App icon package';

  @override
  String get focusSecondaryIconSourceLabel => 'Secondary icon source';

  @override
  String get chatTitleColorLabel => 'Chat title color';

  @override
  String get chatTitleColorDarkLabel => 'Chat title color (dark)';

  @override
  String get chatContentColorLabel => 'Chat content color';

  @override
  String get chatContentColorDarkLabel => 'Chat content color (dark)';

  @override
  String get progressColorLabel => 'Progress color';

  @override
  String get progressBarColorLabel => 'Progress bar color';

  @override
  String get progressBarColorEndLabel => 'Progress bar end color';

  @override
  String get placeholderTitle => 'Notification title';

  @override
  String get placeholderSubtitle => 'Notification content';

  @override
  String get placeholderSubtitleOrTitle => 'Content (fallback title)';

  @override
  String get placeholderPkg => 'Package name';

  @override
  String get placeholderChannelId => 'Channel ID';

  @override
  String get placeholderProgress => 'Notification progress';

  @override
  String get placeholderStateLabel => 'State label';

  @override
  String get placeholderProgressText => 'Progress text';

  @override
  String get placeholderAiLeft => 'AI left text';

  @override
  String get placeholderAiRight => 'AI right text';

  @override
  String get placeholderRawTitle => 'Raw title';

  @override
  String get placeholderRawSubtitle => 'Raw subtitle';

  @override
  String get placeholderRawSubtitleOrTitle => 'Raw subtitle (fallback title)';

  @override
  String get islandLeftExprLabel => 'Island left expression';

  @override
  String get islandRightExprLabel => 'Island right expression';

  @override
  String get aodTextSwitchLabel => 'AOD text switch';

  @override
  String get aodTextSwitchSubtitle =>
      'Show notification text on the AOD when enabled';

  @override
  String get aodTextExprLabel => 'AOD text expression';

  @override
  String get aodIconSourceLabel => 'AOD icon source';

  @override
  String get focusNotificationLabel => 'Odak Bildirimini Kullan';

  @override
  String get hideNotificationLabel => 'Bildirimi gizle';

  @override
  String get hideNotificationLabelSubtitle =>
      'Açıldığında yalnızca ada gösterilir, bildirim gölgesindeki odak bildirimi gizlenir';

  @override
  String get preserveStatusBarSmallIconLabel =>
      'Durum Çubuğu Küçük Simgesini Koru';

  @override
  String get preserveStatusBarSmallIconLabelSubtitle =>
      'Bu ayar açık olduğunda odak bildirimi sırasında durum çubuğu küçük simgesi görünür kalır.';

  @override
  String get islandIconLabel => 'Büyük Ada Simgesini Göster';

  @override
  String get islandIconLabelSubtitle =>
      'Bu ayar açık olduğunda büyük Ada simgesi gösterilir (küçük Ada etkilenmez).';

  @override
  String get firstFloatLabel => 'İlk Bildirimde Genişlet';

  @override
  String get updateFloatLabel => 'Güncellemede Yeniden Genişlet';

  @override
  String get autoDisappear => 'Otomatik Kapanma';

  @override
  String get seconds => 'sn';

  @override
  String get defaultTimeoutSubtitle =>
      'Bildirim adası varsayılan otomatik kapanma süresi';

  @override
  String get highlightColorLabel => 'Vurgu Rengi';

  @override
  String get dynamicHighlightColorLabel => 'Dinamik vurgu rengi';

  @override
  String get dynamicHighlightColorLabelSubtitle =>
      'Varsayılan olarak simgeden dinamik renk kullan';

  @override
  String get followDynamicColorLabel => 'Dinamik rengi takip et';

  @override
  String get dynamicHighlightModeDark => 'Koyu';

  @override
  String get dynamicHighlightModeDarker => 'Daha koyu';

  @override
  String get outerGlowLabel => 'Dış parlama';

  @override
  String get forceOuterGlowLabel => 'Genel olarak zorla';

  @override
  String get forceFocusOuterGlowSubtitle =>
      'Etkinleştirildiğinde eşleşmeyen odak bildirimlerinde parlamayı zorla etkinleştir';

  @override
  String get forceIslandOuterGlowSubtitle =>
      'Etkinleştirildiğinde eşleşmeyen adalarda parlamayı zorla etkinleştir';

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
  String get colorOpacity => 'Opaklık';

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
  String get fullscreenBehaviorTitle => 'Tam ekran davranışı';

  @override
  String get fullscreenBehaviorSubtitle =>
      'Yatay/tam ekran algılandığında bildirim stratejisi';

  @override
  String get fullscreenBehaviorOff => 'Varsayılan';

  @override
  String get fullscreenBehaviorFallback => 'Normal bildirime dön';

  @override
  String get filterRulesTitle => 'Filtre kuralları';

  @override
  String get filterRulesOrderTitle => 'İlk eşleşen kural uygulanır';

  @override
  String get filterRuleDnd => 'Rahatsız Etmeyin';

  @override
  String get filterRuleFullscreen => 'Tam ekran';

  @override
  String get filterRuleLandscape => 'Yatay';

  @override
  String get dndBehaviorTitle => 'Rahatsız Etmeyin açıkken';

  @override
  String get fullscreenRuleTitle => 'Tam ekrandayken';

  @override
  String get landscapeRuleTitle => 'Yataydayken';

  @override
  String get behaviorPreviewDefault =>
      'Eşleşince işlem yapma, varsayılan davranışı kullan';

  @override
  String get behaviorPreviewSuppress => 'Eşleşince normal bildirime dön';

  @override
  String get behaviorPreviewSmallOnly =>
      'Eşleşince yalnızca küçük Ada göster, otomatik genişletme';

  @override
  String get behaviorPreviewExpand => 'Eşleşince bildirimi otomatik genişlet';

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
  String get aiModelPickerTitle => 'Model Seç';

  @override
  String get aiModelPickerSearchHint => 'Modelleri ara…';

  @override
  String get aiModelPickerEmpty => 'Model bulunamadı';

  @override
  String get aiModelPickerRetry => 'Tekrar dene';

  @override
  String get aiModelPickerClose => 'Kapat';

  @override
  String get aiModelPickerFetchError => 'Model listesi yüklenemedi';

  @override
  String get aiTestButton => 'Bağlantıyı Dene';

  @override
  String get aiTestUrlEmpty => 'Lütfen önce API adresini girin';

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
  String get aiPromptLabel => 'Özel Prompt';

  @override
  String get aiPromptHint =>
      'Boş bırakırsanız varsayılan prompt kullanılır: Bildirimden ana bilgiyi çıkarın; sol ve sağ metin ayrı ayrı en fazla 6 kelime veya 12 karakter olsun';

  @override
  String get aiPromptDefault =>
      'Bildirimden ana bilgiyi çıkarın; sol ve sağ metin ayrı ayrı en fazla 6 kelime veya 12 karakter olsun';

  @override
  String get aiPromptInUserTitle => 'Prompt\'u kullanıcı mesajına yerleştir';

  @override
  String get aiPromptInUserSubtitle =>
      'Bazı modeller sistem talimatlarını desteklemez; etkinleştirilirse prompt kullanıcı mesajına eklenir';

  @override
  String get aiCustomFieldsTitle => 'Özel Alanlar';

  @override
  String get aiCustomFieldsSubtitle => 'Özel alanları ekle veya değiştir';

  @override
  String get aiCustomFieldsDialogTitle => 'Özel İstek Alanları';

  @override
  String get aiCustomFieldsDescription =>
      'Değerler false, 1, \"text\" veya bir JSON nesnesi gibi geçerli JSON olmalıdır.';

  @override
  String get aiCustomFieldsReset => 'Sıfırla';

  @override
  String get aiCustomFieldName => 'Alan adı';

  @override
  String get aiCustomFieldValue => 'JSON değeri';

  @override
  String get aiCustomFieldAdd => 'Alan ekle';

  @override
  String get aiCustomFieldDelete => 'Alanı sil';

  @override
  String get aiCustomFieldsError =>
      'Alan adı boş olamaz ve değer geçerli JSON olmalıdır';

  @override
  String get aiCustomFieldsCancel => 'İptal';

  @override
  String get aiCustomFieldsSave => 'Kaydet';

  @override
  String get aiTimeoutTitle => 'AI Yanıt Zaman Aşımı';

  @override
  String aiTimeoutLabel(int seconds) {
    return 'AI Yanıt Zaman Aşımı';
  }

  @override
  String defaultTimeoutHint(int seconds) {
    return 'Varsayılan (${seconds}sn)';
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
  String get aiTriggerCharCountTitle => 'Tetikleme Karakter Sayısı';

  @override
  String get aiTriggerCharCountSubtitle =>
      'Bildirim başlığı ve metni bu uzunluğa ulaştığında AI\'ı tetikle';

  @override
  String get aiTriggerCharCountAlways =>
      'Bildirim uzunluğundan bağımsız olarak AI\'ı her zaman tetikle';

  @override
  String get aiDefaultPromptFull =>
      'Boş bırakırsanız varsayılan prompt kullanılır: Bildirimden ana bilgiyi çıkarın; sol ve sağ taraf için en fazla 6 kelime veya 12 karakter';

  @override
  String get aiDefaultNotificationText =>
      '[Delivery] Your delivery has arrived and was placed in the parcel locker at the door';

  @override
  String get aiTestSampleUserContent => 'Reply exactly: test successful';

  @override
  String aiNotificationUserContent(String content) {
    return 'App package: com.example.app\nTitle: Test notification\nBody: $content';
  }

  @override
  String get aiJsonOnlyInstruction =>
      'Return only the following JSON. Do not include any other text or code block:';

  @override
  String get aiJsonLeftDescription => 'left text (sender)';

  @override
  String get aiJsonRightDescription => 'right text (summary)';

  @override
  String get aiThinkingModeError =>
      'AI thinking mode is enabled. Add a custom field to disable thinking mode';

  @override
  String get aiInvalidJsonError =>
      'Invalid AI response format. JSON with left and right fields is required';

  @override
  String get aiEmptyJsonError =>
      'AI response is empty. JSON with left and right fields is required';

  @override
  String get aiNotificationContentLabel => 'Notification Content';

  @override
  String get aiTestNotificationTitle => 'Test Notification';

  @override
  String get aiNotificationSent => 'Notification sent';

  @override
  String get aiAiNotificationSent => 'AI notification sent';

  @override
  String get aiSendNotificationButton => 'Send Notification';

  @override
  String get aiSendAiNotificationButton => 'Send AI Notification';

  @override
  String get hideDesktopIconTitle => 'Ana Ekran Simgesini Gizle';

  @override
  String get hideDesktopIconSubtitle =>
      'Uygulama simgesini başlatıcıdan gizler. Gizledikten sonra LSPosed Manager üzerinden açın';

  @override
  String get restoreLockscreenTitle => 'Kilit Ekranı Bildirimini Geri Yükle';

  @override
  String get restoreLockscreenSubtitle =>
      'Kilit ekranında odak bildirimi işlemini atlayın, özgün gizlilik davranışını koruyun';

  @override
  String get filterRulesSection => 'Filtre Kuralları';

  @override
  String get foregroundRulesTab => 'Ön plan kuralları';

  @override
  String get foregroundExclusionsTab => 'Hariç tutulan uygulamalar';

  @override
  String get foregroundRulesDescription =>
      'Ön plandaki uygulama başladığında Ada davranışını ayarlayın.';

  @override
  String get foregroundExclusionsDescription =>
      'Hariç tutma listesindeki uygulamaların bildirimleri ön plan kurallarından etkilenmez.';

  @override
  String get hideSystemApps => 'Sistem uygulamalarını gizle';

  @override
  String get restoreDefaultConfig => 'Varsayılan yapılandırmayı geri yükle';

  @override
  String resetDefaultConfigSuccess(int count) {
    return 'Varsayılan yapılandırma geri yüklendi, $count uygulama sıfırlandı';
  }

  @override
  String get sceneActionDefault => 'Varsayılan';

  @override
  String get sceneActionSmallOnly => 'Genişletmeyi kapat';

  @override
  String get sceneActionExpand => 'Otomatik genişlet';

  @override
  String get sceneActionSuppress => 'Geri dön';

  @override
  String get filterModeLabel => 'Filter Mode';

  @override
  String get filterModeBlacklist => 'Blacklist';

  @override
  String get filterModeWhitelist => 'Whitelist';

  @override
  String get filterModeBlacklistDesc =>
      'Notifications matching keywords will be filtered';

  @override
  String get filterModeWhitelistDesc =>
      'Only notifications matching keywords will be shown';

  @override
  String get whitelistKeywordsLabel => 'Whitelist Keywords';

  @override
  String get blacklistKeywordsLabel => 'Blacklist Keywords';

  @override
  String get addKeyword => 'Add keyword';

  @override
  String get keywordHint => 'Enter keyword';

  @override
  String get removeKeyword => 'Remove';

  @override
  String get keywordFilterPriority =>
      'Whitelist takes priority: only whitelist-matched notifications are shown, but blacklist can still veto';

  @override
  String get exportChannelsToClipboard => 'Export Channel Settings';

  @override
  String get importChannelsFromClipboard => 'Import Channel Settings';

  @override
  String get exportChannelsSuccess => 'Channel settings copied to clipboard';

  @override
  String importChannelsSuccess(int count) {
    return 'Imported $count channel settings';
  }

  @override
  String importChannelsPartialSuffix(int total, int matched) {
    return ' (toplam $total, eşleşen $matched)';
  }

  @override
  String get importErrorEmptyClipboard =>
      'Clipboard is empty. Please copy channel settings first';

  @override
  String get importErrorNotJson => 'Clipboard content is not valid JSON';

  @override
  String get importErrorMissingChannels =>
      'Invalid data format: missing channel list';

  @override
  String get importErrorNoMatch =>
      'No channels matched the current app. Please verify the data source';

  @override
  String get importErrorUnknown => 'Import failed. Please check clipboard data';

  @override
  String get mediaNotificationTitle => 'Medya bildirimi';

  @override
  String get mediaNotificationDisabledSubtitle =>
      'Devre dışı bırakıldığında medya bildiriminin tamamını doğrudan sil';

  @override
  String get normalNotificationTitle => 'Normal bildirim';

  @override
  String get normalNotificationSubtitle =>
      'Etkinleştirildiğinde medya alanlarını kaldırıp normal bildirim olarak işle';

  @override
  String get channelSettingsUnmodified => 'Değiştirilmedi';

  @override
  String get restoreDefault => 'Varsayılanı geri yükle';

  @override
  String get islandDimenSection => 'Ada Boyutları';

  @override
  String get islandDimenHeight => 'Ada Yüksekliği';

  @override
  String get islandTopOffset => 'Ekranın Üstünden Uzaklık';

  @override
  String get smallIslandWidth => 'Small Island Width';

  @override
  String get smallIslandHorizontalOffset => 'Büyük-Küçük Ada Aralığı';

  @override
  String get followSystem => 'Sistem varsayılanı';

  @override
  String get islandDimenMiniY => 'Dikey Konum';

  @override
  String get islandDimenMiniYHint => '0=sistem varsayılanı';

  @override
  String get islandBgSection => 'Ada Arka Planı';

  @override
  String get islandBgSmallTitle => 'Küçük Ada Arka Planı';

  @override
  String get islandBgBigTitle => 'Büyük Ada Arka Planı';

  @override
  String get islandBgExpandTitle => 'Odak Bildirimi Arka Planı';

  @override
  String get islandBgNotSet => 'Ayarlanmamış';

  @override
  String get islandBgCornerRadius => 'Köşe Yarıçapı';

  @override
  String get islandBgCornerRadiusHint => '0=sistem varsayılanı';

  @override
  String get islandBgImageSelected => 'Arka plan görseli kaydedildi';

  @override
  String get islandBgImageDeleted => 'Arka plan görseli silindi';

  @override
  String get islandBgDeleteFailed => 'Silme başarısız';

  @override
  String islandBgEditTitle(String type) {
    return '$type Arka Planını Düzenle';
  }

  @override
  String get islandBgBlurLabel => 'Bulanıklık';

  @override
  String get islandBgBrightnessLabel => 'Parlaklık';

  @override
  String get islandBgOpacityLabel => 'Opaklık';

  @override
  String get islandBgDefault => 'Varsayılan';

  @override
  String get islandBlurSmallTitle => 'Küçük Ada Bulanıklığı';

  @override
  String get islandBlurBigTitle => 'Büyük Ada Bulanıklığı';

  @override
  String get islandBlurExpandTitle => 'Odak Bildirimi Bulanıklığı';

  @override
  String get islandBlurEnabled => 'Canlı arka plan bulanıklığını etkinleştir';

  @override
  String get islandBlurRadius => 'Bulanıklık düzeyi';

  @override
  String get islandBlurBlendColor => 'Karışım rengi';

  @override
  String get islandBlurDisabled => 'Kapalı';

  @override
  String get islandBlurUnavailableWithBackground =>
      'Arka plan ve bulanıklaştırma aynı anda etkinleştirilemez';

  @override
  String get islandBlurBigTextColorSuggestion =>
      'Süper Ada metin rengini durum çubuğunu takip edecek şekilde ayarlamanız önerilir';

  @override
  String islandBlurRadiusValue(int radius) {
    return 'Bulanıklık $radius';
  }

  @override
  String get islandGlassSection => 'Glass Effect';

  @override
  String get islandGlassEnabled => 'Enable glass effect';

  @override
  String get islandGlassEnabledSubtitle =>
      'Add glass rim effects to enabled live background blur states';

  @override
  String get islandGlassRequiresBlur =>
      'Enable Small, Large, or Focus Notification blur first';

  @override
  String get islandGlassEdgeWidth => 'Edge width';

  @override
  String get islandGlassRefraction => 'Refraction strength';

  @override
  String get islandGlassHighlight => 'Highlight strength';

  @override
  String get islandGlassShadow => 'Backlight shadow strength';

  @override
  String get islandGlassLightDirection => 'Light direction';

  @override
  String get islandGlassDispersion => 'Dispersion strength';

  @override
  String get islandGlassGyroscope => 'Gyroscope lighting';

  @override
  String get islandGlassGyroscopeSubtitle =>
      'Move rim lighting with the device pose';

  @override
  String get islandGlassCustomize => 'Customize glass effect';

  @override
  String get islandGlassCustomizeSubtitle =>
      'Customize glass effect parameters';

  @override
  String get islandGlassEnableFirst => 'Enable the glass effect first';

  @override
  String get islandGlassHdrHighlight => 'HDR highlights';

  @override
  String get islandGlassHdrHighlightSubtitle =>
      'Display highlighted edges in HDR';

  @override
  String get islandGlassTrueRefraction => 'Sıvı cam';

  @override
  String get islandGlassTrueRefractionSubtitle =>
      'Refract surrounding screen content on Large Island and Focus Notification; higher performance cost';

  @override
  String get islandGlassCaptureSettings => 'Capture settings';

  @override
  String get islandGlassCaptureSettingsSubtitle =>
      'Customize liquid glass capture settings';

  @override
  String get islandGlassEnableLiquidFirst =>
      'Enable the liquid glass effect first';

  @override
  String get islandGlassCaptureFps => 'Capture frame rate';

  @override
  String get islandGlassCaptureQuality => 'Resolution';

  @override
  String get keepIslandTitle => 'Adayı Sürekli Göster';

  @override
  String get keepIslandSubtitle =>
      'Adayı sürekli görünür tutmak için boş bir bildirim gönder';

  @override
  String get keepIslandIslandConfigTitle => 'Ada yapılandırması';

  @override
  String get keepIslandDisplayTimingTitle => 'Gösterim zamanı';

  @override
  String get keepIslandDisplayTimingAlways => 'Her zaman';

  @override
  String get keepIslandDisplayTimingCharging => 'Şarj olurken';

  @override
  String get keepIslandFocusConfigTitle => 'Odak bildirimi yapılandırması';

  @override
  String get keepIslandEnableIslandTitle => 'Adayı etkinleştir';

  @override
  String get keepIslandShowNotificationTitle => 'Bildirim merkezinde göster';

  @override
  String get keepIslandConfigEnabled => 'Etkin';

  @override
  String get keepIslandConfigDisabled => 'Devre dışı';

  @override
  String get keepIslandAutoHideTitle => 'Otomatik Gizle';

  @override
  String get keepIslandAutoHideSubtitle =>
      'Bildirim geldiğinde boş adayı otomatik gizle, bildirim kalktığında geri yükle';

  @override
  String get keepIslandHideLandscapeTitle => 'Yatayda Gizle';

  @override
  String get keepIslandHideLandscapeSubtitle =>
      'Yatay ekranda sürekli adayı gizle, dikeye dönünce bildirim yoksa geri yükle';

  @override
  String get keepIslandHighlightColorTitle => 'Vurgu Rengi';

  @override
  String get keepIslandHighlightColorSubtitle =>
      'Sürekli adanın vurgu metin rengini özelleştir';

  @override
  String get keepIslandTextHighlightTitle => 'Metin vurgusu';

  @override
  String get keepIslandHighlightLeft => 'Sol';

  @override
  String get keepIslandHighlightRight => 'Sağ';

  @override
  String get keepIslandLeftContentTitle => 'Sol ada içeriği';

  @override
  String get keepIslandRightContentTitle => 'Sağ ada içeriği';

  @override
  String get keepIslandCarouselIntervalTitle => 'Carousel interval';

  @override
  String get keepIslandCarouselIntervalSubtitle =>
      'Switch between multiple left and right contents every 1-6000 seconds';

  @override
  String get keepIslandAddCarouselItem => 'Add content';

  @override
  String keepIslandCarouselItem(int index) {
    return 'Content $index';
  }

  @override
  String get keepIslandFocusNotificationTitle => 'Tıklanabilir ada';

  @override
  String get keepIslandFocusNotificationSubtitle =>
      'Odak bildirim içeriğini göster ve dokunarak genişletmeyi destekle';

  @override
  String get keepIslandFocusContentType => 'Genişletilmiş içerik';

  @override
  String get keepIslandFocusContentNotification => 'Bildirim';

  @override
  String get keepIslandFocusContentPerformance => 'Performans paneli';

  @override
  String get keepIslandFocusContentDevice => 'Cihaz paneli';

  @override
  String get keepIslandFocusContentCharging => 'Pil paneli';

  @override
  String get keepIslandNotificationTitle => 'Bildirim başlığı';

  @override
  String get keepIslandNotificationContent => 'Bildirim içeriği';

  @override
  String get keepIslandShowIslandIconTitle => 'Ada simgesini göster';

  @override
  String get keepIslandShowIslandIconSubtitle =>
      'Sürekli adanın sol tarafında bir simge göster';

  @override
  String get keepIslandCustomIconTitle => 'Özel simge';

  @override
  String get keepIslandCustomIconSelected =>
      'Ayarlandı, değiştirmek için dokunun';

  @override
  String get keepIslandPlaceholdersTitle => 'Kullanılabilir yer tutucular';

  @override
  String get keepIslandTimeCategory => 'Zaman';

  @override
  String get keepIslandWeatherCategory => 'Hava durumu';

  @override
  String get keepIslandDisplayCategory => 'Ekran';

  @override
  String get keepIslandDeviceCategory => 'Cihaz';

  @override
  String get keepIslandNetworkCategory => 'Network';

  @override
  String keepIslandPlaceholdersDescription(
    String batteryLevel,
    String cpuUsage,
  ) {
    return 'Her iki taraf için metin veya ifade girin, örneğin: Battery $batteryLevel, CPU $cpuUsage. Kopyalamak için etikete dokunun.';
  }

  @override
  String keepIslandPlaceholderCopied(String placeholder) {
    return '$placeholder kopyalandı';
  }

  @override
  String get keepIslandDefaultEmpty => 'Varsayılan boş';

  @override
  String keepIslandContentHint(String placeholder) {
    return 'Varsayılan boş. Metin veya $placeholder girin';
  }

  @override
  String get clear => 'Temizle';

  @override
  String get save => 'Kaydet';

  @override
  String get islandOtherSection => 'Diğer';

  @override
  String get islandSwipeActionsTitle => 'Kaydırma eylemleri';

  @override
  String get expandedCollapseActionTitle => 'Genişletilmiş ada daraltılırken';

  @override
  String get bigIslandCollapseActionTitle => 'Büyük ada gizlenirken';

  @override
  String get islandSwipeActionNone => 'Yok';

  @override
  String get islandSwipeActionCancelNotification => 'Bildirimi temizle';

  @override
  String get islandSwipeActionHideIsland => 'Büyük adayı gizle';

  @override
  String get islandSwipeIgnoreOngoingTitle => 'Kalıcı bildirimleri yoksay';

  @override
  String get miscSection => 'Çeşitli';

  @override
  String get onboardingEntryTitle => 'İlk Kurulumu Aç';

  @override
  String get onboardingEntrySubtitle =>
      'Karşılama ve hızlı başlangıç akışını tekrar görüntüle';

  @override
  String get onboardingAppName => 'HyperIsland';

  @override
  String get onboardingWelcomeTitle => 'HyperIsland\'a Hoş Geldiniz';

  @override
  String get onboardingWelcomeSubtitle =>
      'Ada deneyiminizi hızlı ve sade şekilde yapılandırın';

  @override
  String get onboardingEnvironmentTitle => 'Ortam Denetimi';

  @override
  String get onboardingEnvironmentSubtitle =>
      'Modül izin durumunu kontrol edin';

  @override
  String get onboardingFocusUnlockTitle => 'Unlock Focus Whitelist';

  @override
  String get onboardingFocusUnlockSubtitle =>
      'Remove focus notification whitelist and signature verification limits so more apps can use focus notifications';

  @override
  String get onboardingFocusUnlockMethodHyperCeiler =>
      'Method 1: Use HyperCeiler to unlock the whitelist and signature verification';

  @override
  String get onboardingFocusUnlockHyperCeilerSubtitle =>
      'Enable the related unlock options in HyperCeiler. Recommended if HyperCeiler is already installed.';

  @override
  String get onboardingFocusUnlockViewTutorial => 'View Tutorial';

  @override
  String get onboardingFocusUnlockMethodEmbedded => 'Method 2: Built-in module';

  @override
  String get onboardingFocusUnlockEmbeddedSubtitle =>
      'Enable the two built-in unlock verification switches in one tap.';

  @override
  String get onboardingFocusUnlockEmbeddedEnabled =>
      'Both unlock verification switches are enabled. Please manually restart System UI and XMSF.';

  @override
  String get onboardingFocusUnlockEnableButton => 'Enable Now';

  @override
  String get onboardingFocusUnlockEnabledButton => 'Enabled';

  @override
  String get onboardingFocusUnlockEnabled =>
      'Enabled. Please manually restart System UI and XMSF';

  @override
  String get onboardingNotificationStyleTitle => 'Bildirim Stilini Seç';

  @override
  String get onboardingNotificationStyleSubtitle =>
      'Varsayılan bildirim görünümünü seçin';

  @override
  String get onboardingOriginalNotificationLabel => 'Orijinal bildirim';

  @override
  String get onboardingFinishTitle => 'Her Şey Hazır';

  @override
  String get onboardingFinishSubtitle =>
      'Kurulumdan sonra ayrıntıları Ayarlar\'dan düzenlemeye devam edebilirsiniz';

  @override
  String onboardingStepLabel(int current, int total) {
    return 'Adım $current / $total';
  }

  @override
  String get onboardingPrevious => 'Önceki';

  @override
  String get onboardingNext => 'Sonraki';

  @override
  String get onboardingDone => 'Başla';

  @override
  String get onboardingStatusTitle => 'Durum Denetimi';

  @override
  String get onboardingRetry => 'Yeniden dene';

  @override
  String get onboardingLsposedStatus => 'LSPosed Etkinleştirme Durumu';

  @override
  String get onboardingRootStatus => 'Root İzni';

  @override
  String get onboardingAppListStatus => 'Uygulama listesi izni';

  @override
  String get onboardingProtocolStatus => 'Sistem Protokol Sürümü';

  @override
  String get onboardingAndroidStatus => 'Android Sürümü';

  @override
  String get onboardingUnsupportedSystem => 'Mevcut sistem desteklenmiyor';

  @override
  String get onboardingAndroid15Limited => 'Android 15 desteği sınırlıdır';

  @override
  String get onboardingMissingPermissionTitle => 'Gerekli İzin Eksik';

  @override
  String get onboardingMissingPermissionMessage =>
      'Modül düzgün çalışmayabilir';

  @override
  String get onboardingDialogClose => 'Kapat';

  @override
  String get onboardingDialogContinue => 'Devam et';

  @override
  String get backupRestoreSection => 'Yedekleme ve Geri Yükleme';

  @override
  String get hookExtensionSection => 'Hook Uzantısı';

  @override
  String get hookScopeSettings => 'Sistem Ayarları';

  @override
  String get settingsHomeEntryTitle => 'Sistem Ayarları girişi';

  @override
  String get settingsHomeEntrySubtitle =>
      'Sistem Ayarları ana sayfasında HyperIsland girişini göster';

  @override
  String get settingsHomeEntryIconStyle => 'Simge stili';

  @override
  String get settingsHomeEntryIconStyleDefault => 'Varsayılan';

  @override
  String get settingsHomeEntryIconStyleOutline => 'Arka plansız';

  @override
  String get xposedScopeRequestFailed =>
      'Kapsam isteği başarısız oldu. Modülün LSPosed\'de etkin olduğundan emin olun';

  @override
  String get hookScopeSystemUI => 'Sistem UI';

  @override
  String get smoothIslandTitle => 'Pürüzsüz Ada';

  @override
  String get smoothIslandSubtitle =>
      'Ada kenarları için sürekli eğriliğe sahip kapsül kullanır. Devre dışı bıraktıktan sonra Hook\'u tamamen kaldırmak için kapsamı yeniden başlatın';

  @override
  String get smoothIslandSmoothingTitle => 'Pürüzsüzlük Gücü';

  @override
  String get bluetoothIslandStatusEnabled => 'Etkin';

  @override
  String get bluetoothIslandStatusDisabled => 'Devre dışı';

  @override
  String get bluetoothIslandTitle => 'Bluetooth Adası';

  @override
  String bluetoothIslandSubtitle(String status) {
    return '$status · Bluetooth cihaz bağlantılarını ve kopmalarını dinler, ardından adayı Sistem UI üzerinden iletir';
  }

  @override
  String get bluetoothIslandSettingsTitle => 'Bluetooth Adası Ayarları';

  @override
  String get bluetoothIslandEnableTitle => 'Bluetooth Adasını Etkinleştir';

  @override
  String get bluetoothIslandEnableSubtitle =>
      'Devre dışı bıraktıktan sonra geçerli olması için Sistem UI\'ı yeniden başlatın. Bluetooth Hook kaydedilmez';

  @override
  String get bluetoothIslandShowDeviceNameTitle => 'Cihaz Adını Göster';

  @override
  String get bluetoothIslandShowDeviceNameSubtitle =>
      'Bağlandığında önce sağda cihaz adını gösterir, ardından bağlantı durumunu gösterir';

  @override
  String get bluetoothIslandDisplayDurationTitle => 'Gösterim Süresi';

  @override
  String get chargeIslandTitle => 'Şarj Adası';

  @override
  String chargeIslandSubtitle(String status) {
    return '$status · Şarj Adasındaki güç veya pil bölümünü değiştirir';
  }

  @override
  String get chargeIslandSettingsTitle => 'Şarj Adası Ayarları';

  @override
  String get chargeIslandEnableTitle => 'Şarj Adası Hook\'unu Etkinleştir';

  @override
  String get chargeIslandEnableSubtitle =>
      'Devre dışı bıraktıktan sonra geçerli olması için Sistem UI\'ı yeniden başlatın. Hook tamamen atlanır';

  @override
  String get chargeIslandLeftModeTitle => 'Sol Davranış';

  @override
  String get chargeIslandRightModeTitle => 'Sağ Davranış';

  @override
  String get chargeIslandModeDefault => 'Varsayılan';

  @override
  String get chargeIslandModePower => 'Güç';

  @override
  String get chargeIslandModeVoltage => 'Voltaj';

  @override
  String get chargeIslandModeCurrent => 'Akım';

  @override
  String get chargeIslandModeLevel => 'Pil';

  @override
  String get chargeIslandModeTemperature => 'Pil Sıcaklığı';

  @override
  String get chargeIslandDurationModeTitle => 'Süre';

  @override
  String get chargeIslandDurationDefault => 'Varsayılan';

  @override
  String get chargeIslandDurationCustom => 'Özel';

  @override
  String get chargeIslandDurationPersistent => 'Kalıcı';

  @override
  String get chargeIslandDurationSecondsTitle => 'Özel Süre';

  @override
  String get chargeIslandOuterGlowSubtitle =>
      'Control the outer glow effect of Charging Island';

  @override
  String get faceUnlockIslandTitle => 'Kilit Açma Adası';

  @override
  String faceUnlockIslandSubtitle(String status) {
    return '$status · Kilit ekranına kilit açma durumu süper adası ekler';
  }

  @override
  String get faceUnlockIslandSettingsTitle => 'Kilit Açma Adası Ayarları';

  @override
  String get faceUnlockIslandEnableTitle => 'Kilit Açma Adasını Etkinleştir';

  @override
  String get faceUnlockIslandEnableSubtitle =>
      'Açma/kapamadan sonra SystemUI yeniden başlatma gerekir';

  @override
  String get faceUnlockIslandFirstFloatTitle => 'Süper Adayı Otomatik Genişlet';

  @override
  String get faceUnlockIslandFirstFloatSubtitle =>
      'Odak bildirimi olarak genişlet';

  @override
  String get faceUnlockIslandAnimationStyleTitle => 'Animasyon Stili';

  @override
  String get faceUnlockIslandAnimationStyleSubtitle =>
      'Kilit stili, ekran kilitliyken kapalı kilit gösterir ve herhangi bir yöntemle kilit açılınca kelepçeyi sağa döndürür';

  @override
  String get faceUnlockIslandAnimationDefault => 'Varsayılan';

  @override
  String get faceUnlockIslandAnimationLock => 'Kilit';

  @override
  String get faceUnlockIslandKeepUntilKeyguardHiddenTitle =>
      'Yüz Kilidinden Sonra Adayı Koru';

  @override
  String get faceUnlockIslandKeepUntilKeyguardHiddenSubtitle =>
      'Yüz tanıma başarılı olduktan sonra kilit ekranında göstermeye devam eder ve masaüstüne girildiğinde gizler. Diğer kilit açma yöntemleri etkilenmez';

  @override
  String get hideLockscreenFaceUnlockIconTitle =>
      'Yüz Kilidi Simgesini Devre Dışı Bırak';

  @override
  String get hideLockscreenFaceUnlockIconSubtitle =>
      'Yüz kilidi işlevini etkilemeden kilit ekranındaki yüz tanıma grafiğini gizle';

  @override
  String get outerGlowTitle => 'Dış Parlama';

  @override
  String get bluetoothIslandOuterGlowSubtitle =>
      'Bluetooth Adasının dış parlama efektini kontrol eder';

  @override
  String get outerGlowColorTitle => 'Dış Parlama Rengi';

  @override
  String get hookScopeXMSF => 'Xiaomi Servis Çerçevesi (XMSF)';

  @override
  String get downloadManagerSection => 'İndirme Yöneticisi';

  @override
  String get themePageTitle => 'Tema';

  @override
  String get themeSeedColorTitle => 'Tema Rengi';

  @override
  String get themeSeedColorSubtitle => 'Uygulama vurgu rengini özelleştir';

  @override
  String get presetColors => 'Hazır Renkler';

  @override
  String get themeResetColor => 'Varsayılana Sıfırla';

  @override
  String get blurBarsTitle => 'Buzlu Cam Efekti';

  @override
  String get blurBarsSubtitle =>
      'Üst ve alt çubuklara bulanıklık şeffaflık efekti ekle';

  @override
  String get bluetoothIslandWhitelistTitle => 'Device Whitelist';

  @override
  String get bluetoothIslandWhitelistSubtitle =>
      'Only show the island for whitelisted Bluetooth devices';

  @override
  String get bluetoothIslandWhitelistButton => 'Manage Whitelist';

  @override
  String bluetoothIslandWhitelistButtonSubtitle(int count) {
    return '$count device(s) selected';
  }

  @override
  String get bluetoothIslandWhitelistDialogTitle => 'Select Bluetooth Devices';

  @override
  String get bluetoothIslandWhitelistEmpty =>
      'No paired devices. Please pair a device in system Bluetooth settings first';

  @override
  String get bluetoothIslandWhitelistAllHint =>
      'When disabled, the island shows for all Bluetooth devices';

  @override
  String get bluetoothIslandLoadDevicesFailed =>
      'Failed to load Bluetooth devices';

  @override
  String get bluetoothIslandNeedBtPermission =>
      'Bluetooth permission is required to load devices';

  @override
  String get hideBehaviorTitle => 'Hide Behavior';

  @override
  String get hideBehaviorDescription =>
      'Control whether system scenes are allowed to temporarily hide the island. Turning an item off blocks the matching system hide logic.';

  @override
  String get hideBehaviorMasterSwitch =>
      'Gizleme engelleme enjeksiyonunu etkinleştir';

  @override
  String get hideBehaviorMasterSwitchSubtitle =>
      'Açıldığında gizleme davranışı engelleme özelliği etkinleştirilir; kapandığında tamamen devre dışı bırakılır';

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
  String get hideBehaviorFullscreenLandscapeDisable =>
      'Disable fullscreen hide in landscape';

  @override
  String get hideBehaviorFullscreenLandscapeDisableSubtitle =>
      'Restore system behavior and stop intercepting hide when in landscape mode';

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
  String get hideBehaviorForegroundApp => 'Ön plan uygulaması';

  @override
  String get hideBehaviorForegroundAppSubtitle =>
      'Uygulama ön plandayken kendi adasını gizle';

  @override
  String get off => 'Kapalı';

  @override
  String get islandTextSection => 'Island Text';

  @override
  String get islandTextSizeTitle => 'Island Text Size';

  @override
  String get islandOutlineSection => 'Ana hat denetimi';

  @override
  String get outerGlowAppearanceSection => 'Dış parlama';

  @override
  String get outerGlowRangeTitle => 'Parlama aralığı';

  @override
  String get outerGlowSingleColorTitle => 'Tek renkli parlama';

  @override
  String get outerGlowBaseColorTitle => 'Temel renk';

  @override
  String get alwaysShowIslandOutlineTitle => 'Ada ana hattını her zaman göster';

  @override
  String get alwaysShowFocusOutlineTitle =>
      'Odak bildirimi ana hattını her zaman göster';

  @override
  String get islandTextColorTitle => 'Island Text Color';

  @override
  String get focusNotificationTextColorTitle => 'Focus Notification Text Color';

  @override
  String get mediaNotificationTextColorTitle => 'Media Notification Text Color';

  @override
  String get islandTextColorBlack => 'Black';

  @override
  String get islandTextColorFollowBackground => 'Follow island background';

  @override
  String get islandTextColorInvertBackground => 'Invert island background';

  @override
  String get islandTextColorFollowStatusBar => 'Follow status bar';

  @override
  String get islandTextColorInvertStatusBar => 'Invert status bar';

  @override
  String get islandTextColorDefault => 'Default';

  @override
  String get keepIslandExpandTextColorTitle => 'Focus island text color';

  @override
  String get keepIslandExpandTextColorWhite => 'White';

  @override
  String get tapToSelectImage => 'Resim veya GIF seçmek için dokunun';

  @override
  String get autoExpandNotification => 'Bildirimi otomatik genişlet';

  @override
  String widthDpLabel(int width) {
    return '$width dp';
  }

  @override
  String get alwaysOnIsland => 'Kalıcı Ada';

  @override
  String get referencesTitle => 'Referanslar';

  @override
  String get referencesDescription =>
      'HyperIsland geliştirilirken aşağıdaki projelerin bir kısmından veya tamamından yararlanılmıştır. Destekleri için bu projelere teşekkür ederiz.';
}
