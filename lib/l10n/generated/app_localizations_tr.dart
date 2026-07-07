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
      '1. Kullanmadan önce sağ üst köşedeki kullanım kılavuzunu mutlaka okuyun';

  @override
  String get note2 =>
      '2. Çoğu ayar sıcak yeniden yüklemeyi destekler; sorun yaşarsanız etki alanını yeniden başlatın';

  @override
  String get note3 =>
      '3. LSPosed Manager\'da etkinleştirdikten sonra ilgili etki alanındaki uygulamaları yeniden başlatmanız gerekir';

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
  String get roundIconTitle => 'Simge Köşelerini Yuvarla';

  @override
  String get roundIconSubtitle =>
      'Bildirim simgelerine yuvarlatılmış köşe uygular.';

  @override
  String get marqueeChannelTitle => 'Ada Metnini Kaydır';

  @override
  String get marqueeAutoHideTitle => '滚动后隐藏岛';

  @override
  String get marqueeAutoHideSubtitle => '消息滚动达到指定次数后隐藏当前岛';

  @override
  String get marqueeAutoHideOnce => '滚动1次';

  @override
  String get marqueeAutoHideTwice => '滚动2次';

  @override
  String get marqueeSpeedTitle => 'Kaydırma Hızı';

  @override
  String marqueeSpeedLabel(int speed) {
    return '$speed px/sn';
  }

  @override
  String get bigIslandMaxWidthTitle => '最大宽度';

  @override
  String get bigIslandMinWidthTitle => '最小宽度';

  @override
  String get testNotifTooltip => '发送测试通知';

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
  String get preserveStatusBarSmallIconLabelSubtitle =>
      'Bu ayar açık olduğunda odak bildirimi sırasında durum çubuğu küçük simgesi görünür kalır.';

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
  String get aiPromptDefault =>
      'Bildirimden ana bilgiyi çıkarın; sol ve sağ metin ayrı ayrı en fazla 6 kelime veya 12 karakter olsun';

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
  String get hideDesktopIconTitle => 'Ana Ekran Simgesini Gizle';

  @override
  String get hideDesktopIconSubtitle =>
      'Uygulama simgesini başlatıcıdan gizler. Gizledikten sonra LSPosed Manager üzerinden açın';

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
    return ' (toplam $total, eşleşen $matched)';
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
  String get keepIslandTitle => 'Adayı Sürekli Göster';

  @override
  String get keepIslandSubtitle =>
      'Adayı sürekli görünür tutmak için boş bir bildirim gönder';

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
  String get keepIslandLeftContentTitle => 'Sol ada içeriği';

  @override
  String get keepIslandRightContentTitle => 'Sağ ada içeriği';

  @override
  String get keepIslandPlaceholdersTitle => 'Kullanılabilir yer tutucular';

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
  String get chargeIslandOuterGlowSubtitle => '控制充电超级岛的外圈光效';

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
  String get off => 'Kapalı';

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
  String get tapToSelectImage => 'Resim veya GIF seçmek için dokunun';

  @override
  String get autoExpandNotification => 'Bildirimi otomatik genişlet';

  @override
  String widthDpLabel(int width) {
    return '$width dp';
  }
}
