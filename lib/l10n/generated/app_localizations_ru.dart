// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Russian (`ru`).
class AppLocalizationsRu extends AppLocalizations {
  AppLocalizationsRu([String locale = 'ru']) : super(locale);

  @override
  String get navHome => 'Главная';

  @override
  String get navIsland => 'Остров';

  @override
  String get navApps => 'Приложения';

  @override
  String get navSettings => 'Настройки';

  @override
  String get cancel => 'Отмена';

  @override
  String get confirm => 'Подтвердить';

  @override
  String get ok => 'ОК';

  @override
  String get apply => 'Применить';

  @override
  String get noChange => 'Без изменений';

  @override
  String get newVersionFound => 'Доступна новая версия';

  @override
  String currentVersion(String version) {
    return 'Текущая версия: $version';
  }

  @override
  String latestVersion(String version) {
    return 'Последняя версия: $version';
  }

  @override
  String get later => 'Позже';

  @override
  String get goUpdate => 'Обновить';

  @override
  String get sponsorSupport => 'Поддержать автора';

  @override
  String get sponsorAuthor => 'Спонсировать';

  @override
  String get donorList => 'Список спонсоров';

  @override
  String get documentation => 'Документация';

  @override
  String versionUpdatedTitle(String version) {
    return 'Обновлено до $version';
  }

  @override
  String get versionUpdatedContent =>
      'Пожалуйста, перезапустите приложения в области после обновления';

  @override
  String get versionUpdatedChangelog =>
      'Список изменений: нажмите для просмотра';

  @override
  String get versionUpdatedStarHint =>
      'Если вам нравится это приложение, пожалуйста, поставьте бесплатную звезду';

  @override
  String get restartScope => 'Перезапустить область';

  @override
  String get systemUI => 'Системный UI';

  @override
  String get downloadManager => 'Менеджер загрузок';

  @override
  String get xmsf => 'XMSF (Xiaomi Service Framework)';

  @override
  String get notificationTest => 'Тест уведомлений';

  @override
  String get sendTestNotification => 'Отправить тестовое уведомление';

  @override
  String get customTestNotification => 'Пользовательское тестовое уведомление';

  @override
  String get customTestTitle => 'Заголовок';

  @override
  String get customTestTitleHint =>
      'Оставьте пустым для заголовка по умолчанию';

  @override
  String get customTestContent => 'Содержимое';

  @override
  String get customTestContentHint =>
      'Оставьте пустым для содержимого по умолчанию';

  @override
  String get clearPreviousNotification => 'Очистить предыдущее уведомление';

  @override
  String get clearPreviousNotificationSubtitle =>
      'Отменить существующее уведомление острова перед отправкой';

  @override
  String get enableFloatNotificationSubtitle =>
      'Автоматически разворачивать как уведомление в фокусе при получении';

  @override
  String get notes => 'Примечания';

  @override
  String get detectingModuleStatus => 'Определение статуса модуля...';

  @override
  String get moduleStatus => 'Статус модуля';

  @override
  String get activated => 'Активирован';

  @override
  String get notActivated => 'Не активирован';

  @override
  String get enableInLSPosed => 'Пожалуйста, включите этот модуль в LSPosed';

  @override
  String get enableSystemUiScopeInLSPosed =>
      'Пожалуйста, выберите Системный UI в области LSPosed';

  @override
  String lsposedApiVersion(int version) {
    return 'Версия LSPosed API: $version';
  }

  @override
  String get updateLSPosedRequired => 'Пожалуйста, обновите версию LSPosed';

  @override
  String get systemNotSupported => 'Система не поддерживается';

  @override
  String systemNotSupportedSubtitle(int version) {
    return 'Текущая система не поддерживает Динамический остров (версия протокола $version, требуется версия 3)';
  }

  @override
  String restartFailed(String message) {
    return 'Ошибка перезапуска: $message';
  }

  @override
  String get restartRootRequired =>
      'Пожалуйста, проверьте, предоставлены ли этому приложению ROOT-права';

  @override
  String get note1 =>
      '1. Обязательно прочтите руководство по использованию в правом верхнем углу перед использованием';

  @override
  String get note2 =>
      '2. Большинство настроек поддерживают горячую перезагрузку; перезапустите область при возникновении проблем';

  @override
  String get note3 =>
      '3. После активации в LSPosed Manager необходимо перезапустить связанные приложения в области';

  @override
  String get note4 =>
      '4. Эта страница предназначена только для тестирования поддержки Динамического острова и эффекта свечения, а не для фактических эффектов';

  @override
  String get note5 =>
      '5. Для острова загрузок вручную включите область \"Менеджер загрузок\"; рекомендуется шаблон \"Загрузка\"';

  @override
  String get behaviorSection => 'Поведение';

  @override
  String get defaultConfigSection => 'Настройки канала по умолчанию';

  @override
  String get appearanceSection => 'Внешний вид';

  @override
  String get configSection => 'Конфигурация';

  @override
  String get aboutSection => 'О приложении';

  @override
  String get keepFocusNotifTitle =>
      'Сохранять уведомление после паузы загрузки';

  @override
  String get keepFocusNotifSubtitle =>
      'Показывать уведомление в фокусе для возобновления загрузки, но могут возникнуть проблемы с синхронизацией состояния';

  @override
  String get unlockAllFocusTitle => 'Удалить белый список уведомлений в фокусе';

  @override
  String get unlockAllFocusSubtitle =>
      'Разрешить всем приложениям отправлять уведомления в фокусе без системного разрешения';

  @override
  String get unlockFocusAuthTitle =>
      'Удалить проверку подписи уведомлений в фокусе';

  @override
  String get unlockFocusAuthSubtitle =>
      'Разрешить всем приложениям отправлять уведомления в фокусе на часы/браслет, в обход проверки подписи (требуется перехват XMSF)';

  @override
  String get checkUpdateOnLaunchTitle => 'Проверять обновления при запуске';

  @override
  String get checkUpdateOnLaunchSubtitle =>
      'Автоматически проверять наличие новых версий при запуске приложения';

  @override
  String get debugLogTitle => 'Показывать журналы отладки';

  @override
  String get debugLogSubtitle =>
      'Если включено, выводятся журналы отладки Hook; если выключено, сохраняются только журналы предупреждений и ошибок';

  @override
  String get showWelcomeTitle =>
      'Показывать приветственное сообщение при запуске';

  @override
  String get showWelcomeSubtitle =>
      'Отображать приветственную информацию на Острове при запуске приложения';

  @override
  String get openOnboardingTitle => 'Открыть онбординг';

  @override
  String get openOnboardingSubtitle =>
      'Просмотреть приветствие и процесс быстрого старта';

  @override
  String get interactionHapticsTitle => 'Тактильный отклик при взаимодействии';

  @override
  String get interactionHapticsSubtitle =>
      'Включить пользовательский тактильный отклик Hyper для переключателей, ползунков и кнопок';

  @override
  String get checkUpdate => 'Проверить обновления';

  @override
  String get alreadyLatest => 'Уже установлена последняя версия';

  @override
  String get roundIconTitle => 'Скругленные углы иконок';

  @override
  String get roundIconSubtitle =>
      'Добавить скругленные углы к иконкам уведомлений';

  @override
  String get marqueeChannelTitle => 'Бегущая строка на Острове';

  @override
  String get marqueeAutoHideTitle => '滚动后隐藏岛';

  @override
  String get marqueeAutoHideSubtitle => '消息滚动达到指定次数后隐藏当前岛';

  @override
  String get marqueeAutoHideOnce => '滚动1次';

  @override
  String get marqueeAutoHideTwice => '滚动2次';

  @override
  String get marqueeSpeedTitle => 'Скорость';

  @override
  String marqueeSpeedLabel(int speed) {
    return '$speed пкс/с';
  }

  @override
  String get bigIslandMaxWidthTitle => 'Максимальная ширина';

  @override
  String get bigIslandMinWidthTitle => 'Минимальная ширина';

  @override
  String get testNotifTooltip => 'Отправить тестовое уведомление';

  @override
  String get themeModeTitle => 'Цветовая тема';

  @override
  String get themeModeSystem => 'Как в системе';

  @override
  String get themeModeLight => 'Светлая';

  @override
  String get themeModeDark => 'Темная';

  @override
  String get languageTitle => 'Язык';

  @override
  String get languageAuto => 'Как в системе';

  @override
  String get languageZh => 'Китайский';

  @override
  String get languageEn => 'Английский';

  @override
  String get languageJa => 'Японский';

  @override
  String get languageRu => 'Русский';

  @override
  String get languageTr => 'Турецкий';

  @override
  String get exportToFile => 'Экспорт в файл';

  @override
  String get exportToFileSubtitle => 'Сохранить конфигурацию в виде JSON-файла';

  @override
  String get exportToClipboard => 'Экспорт в буфер обмена';

  @override
  String get exportToClipboardSubtitle =>
      'Скопировать конфигурацию в виде JSON-текста';

  @override
  String get exportConfig => 'Экспорт конфигурации';

  @override
  String get exportConfigSubtitle => 'Выберите экспорт в файл или буфер обмена';

  @override
  String get importFromFile => 'Импорт из файла';

  @override
  String get importFromFileSubtitle =>
      'Восстановить конфигурацию из JSON-файла';

  @override
  String get importFromClipboard => 'Импорт из буфера обмена';

  @override
  String get importFromClipboardSubtitle =>
      'Восстановить конфигурацию из JSON-текста в буфере обмена';

  @override
  String get importConfig => 'Импорт конфигурации';

  @override
  String get importConfigSubtitle =>
      'Выберите импорт из файла или буфера обмена';

  @override
  String get qqGroup => 'Группа QQ';

  @override
  String get restartScopeApp =>
      'Пожалуйста, перезапустите приложение в области, чтобы настройки вступили в силу';

  @override
  String get groupNumberCopied => 'Номер группы скопирован в буфер обмена';

  @override
  String exportedTo(String path) {
    return 'Экспортировано в: $path';
  }

  @override
  String exportFailed(String error) {
    return 'Ошибка экспорта: $error';
  }

  @override
  String get configCopied => 'Конфигурация скопирована в буфер обмена';

  @override
  String importSuccess(int count) {
    return 'Импорт успешно завершен, $count элементов, пожалуйста, перезапустите приложение';
  }

  @override
  String importFailed(String error) {
    return 'Ошибка импорта: $error';
  }

  @override
  String get appAdaptation => 'Адаптация приложений';

  @override
  String get toastAdaptation => 'Адаптация Toast';

  @override
  String get adaptationModeNotification => 'Уведомление';

  @override
  String get adaptationModeToast => 'Toast';

  @override
  String toastEnabledAppsCount(Object count) {
    return 'Перехват Toast включен для $count приложений';
  }

  @override
  String toastEnabledAppsCountWithSystem(Object count) {
    return 'Перехват Toast включен для $count приложений (включая системные)';
  }

  @override
  String selectedAppsCount(int count) {
    return '$count приложений выбрано';
  }

  @override
  String get cancelSelection => 'Отменить выбор';

  @override
  String get deselectAll => 'Снять выделение';

  @override
  String get selectAll => 'Выбрать все';

  @override
  String get batchChannelSettings => 'Пакетные настройки каналов';

  @override
  String get selectEnabledApps => 'Выбрать включенные приложения';

  @override
  String get batchEnable => 'Включить пакетно';

  @override
  String get batchDisable => 'Отключить пакетно';

  @override
  String get multiSelect => 'Множественный выбор';

  @override
  String get showSystemApps => 'Показать системные приложения';

  @override
  String get refreshList => 'Обновить список';

  @override
  String get enableAll => 'Включить все';

  @override
  String get disableAll => 'Отключить все';

  @override
  String enabledAppsCount(int count) {
    return 'Динамический остров включен для $count приложений';
  }

  @override
  String enabledAppsCountWithSystem(int count) {
    return 'Динамический остров включен для $count приложений (включая системные)';
  }

  @override
  String get searchApps => 'Поиск по имени приложения или имени пакета';

  @override
  String get noAppsFound =>
      'Установленные приложения не найдены\nПожалуйста, проверьте, включено ли разрешение на список приложений';

  @override
  String get noMatchingApps => 'Подходящие приложения не найдены';

  @override
  String applyToSelectedAppsChannels(int count) {
    return 'Будет применено к включенным каналам $count выбранных приложений';
  }

  @override
  String get applyingConfig => 'Применение конфигурации...';

  @override
  String progressApps(int done, int total) {
    return 'Прогресс: $done / $total';
  }

  @override
  String batchApplied(int count) {
    return 'Пакетно применено к $count приложениям';
  }

  @override
  String get cannotReadChannels => 'Не удалось прочитать каналы уведомлений';

  @override
  String get rootRequiredMessage =>
      'Для чтения каналов уведомлений требуются ROOT-права.\nПожалуйста, подтвердите предоставление ROOT-прав и повторите попытку.';

  @override
  String get enableAllChannels => 'Включить все каналы';

  @override
  String get noChannelsFound => 'Каналы уведомлений не найдены';

  @override
  String get noChannelsFoundSubtitle => '该应用尚未创建通知渠道，或无法读取';

  @override
  String allChannelsActive(int count) {
    return 'Активно для всех $count каналов';
  }

  @override
  String selectedChannels(int selected, int total) {
    return '$selected / $total каналов выбрано';
  }

  @override
  String allChannelsDisabled(int count) {
    return 'Все $count каналов (отключено)';
  }

  @override
  String get appDisabledBanner =>
      'Приложение отключено, следующие настройки каналов не действуют';

  @override
  String channelImportance(String importance, String id) {
    return 'Важность: $importance  ·  $id';
  }

  @override
  String get channelSettings => 'Настройки канала';

  @override
  String get toastForwardTitle => 'Перенаправлять стандартный toast';

  @override
  String get toastForwardSubtitle =>
      'Преобразовать текст стандартного toast этого приложения в уведомление в фокусе HyperIsland и супер-остров';

  @override
  String get toastBlockOriginalTitle => 'Блокировать оригинальный toast';

  @override
  String get toastBlockOriginalSubtitle =>
      'После перенаправления блокировать оригинальное всплывающее окно стандартного toast этого приложения';

  @override
  String get toastShowNotificationTitle => 'Показывать в центре уведомлений';

  @override
  String get toastShowNotificationSubtitle =>
      'Оставлять этот перенаправленный toast видимым уведомлением в шторке';

  @override
  String get toastShowIslandIconTitle => 'Показывать иконку острова';

  @override
  String get toastShowIslandIconSubtitle =>
      'Показывать иконку в левой части большого острова для перенаправленного toast';

  @override
  String get toastStandardOnlyHint =>
      'Обрабатывается только стандартный текстовый toast; пользовательские представления toast игнорируются.';

  @override
  String get importanceNone => 'Нет';

  @override
  String get importanceMin => 'Мин.';

  @override
  String get importanceLow => 'Низкий';

  @override
  String get importanceDefault => 'По умолчанию';

  @override
  String get importanceHigh => 'Высокий';

  @override
  String get importanceUnknown => 'Неизвестно';

  @override
  String applyToEnabledChannels(int count) {
    return 'Будет применено к $count включенным каналам';
  }

  @override
  String applyToAllChannels(int count) {
    return 'Будет применено ко всем $count каналам';
  }

  @override
  String get templateDownloadName => 'Загрузка';

  @override
  String get templateNotificationIslandName => 'Остров уведомлений';

  @override
  String get templateNotificationIslandLiteName => 'Остров уведомлений|Lite';

  @override
  String get templateDownloadLiteName => 'Загрузка|Lite';

  @override
  String get islandSection => 'Остров';

  @override
  String get islandEnabledLabel => 'Включить остров';

  @override
  String get template => 'Шаблон';

  @override
  String get rendererLabel => 'Стиль';

  @override
  String get rendererImageTextWithButtons4Name =>
      'Изображение+Текст+Текстовые кнопки внизу';

  @override
  String get rendererCoverInfoName => 'Инфо об обложке+Автоперенос';

  @override
  String get rendererImageTextWithRightTextButtonName =>
      'Изображение+Текст+Текстовая кнопка справа';

  @override
  String get rendererImageTextWithProgressName =>
      'IM Изображение+Текст+Прогресс';

  @override
  String get islandIcon => 'Иконка острова';

  @override
  String get islandIconLabel => 'Большая иконка острова';

  @override
  String get islandIconLabelSubtitle =>
      'Показывать большую иконку острова при включении (на малый остров не влияет)';

  @override
  String get focusIconLabel => 'Иконка фокуса';

  @override
  String get focusExpressionCustomizationSection =>
      'Расширенная настройка фокуса';

  @override
  String get islandExpressionCustomizationSection =>
      'Расширенная настройка острова';

  @override
  String get aodSection => 'Постоянно включенный дисплей';

  @override
  String get expandCustomization => 'Развернуть';

  @override
  String get collapseCustomization => 'Свернуть';

  @override
  String get availablePlaceholdersLabel =>
      'Доступные плейсхолдеры(Нажмите, чтобы скопировать)';

  @override
  String get expressionFunctionsLabel => 'Функции выражений';

  @override
  String get focusTitleExprLabel => 'Выражение заголовка фокуса';

  @override
  String get focusContentExprLabel => 'Выражение содержимого фокуса';

  @override
  String get focusIconSourceLabel => 'Источник иконки фокуса';

  @override
  String get focusPicProfileSourceLabel => 'Источник иконки профиля';

  @override
  String get focusAppIconPkgLabel => 'Пакет иконки приложения';

  @override
  String get focusSecondaryIconSourceLabel => 'Источник вторичной иконки';

  @override
  String get chatTitleColorLabel => 'Цвет заголовка чата';

  @override
  String get chatTitleColorDarkLabel => 'Цвет заголовка чата (темная тема)';

  @override
  String get chatContentColorLabel => 'Цвет содержимого чата';

  @override
  String get chatContentColorDarkLabel => 'Цвет содержимого чата (темная тема)';

  @override
  String get progressColorLabel => 'Цвет прогресса';

  @override
  String get progressBarColorLabel => 'Цвет полосы прогресса';

  @override
  String get progressBarColorEndLabel => 'Цвет конца полосы прогресса';

  @override
  String get placeholderTitle => 'Заголовок уведомления';

  @override
  String get placeholderSubtitle => 'Содержимое уведомления';

  @override
  String get placeholderSubtitleOrTitle => 'Содержимое (резервный заголовок)';

  @override
  String get placeholderPkg => 'Имя пакета';

  @override
  String get placeholderChannelId => 'ID канала';

  @override
  String get placeholderProgress => 'Прогресс уведомления';

  @override
  String get placeholderStateLabel => 'Метка состояния';

  @override
  String get placeholderProgressText => 'Текст прогресса';

  @override
  String get placeholderAiLeft => 'Текст ИИ слева';

  @override
  String get placeholderAiRight => 'Текст ИИ справа';

  @override
  String get placeholderRawTitle => 'Исходный заголовок';

  @override
  String get placeholderRawSubtitle => 'Исходный подзаголовок';

  @override
  String get placeholderRawSubtitleOrTitle =>
      'Исходный подзаголовок (резервный заголовок)';

  @override
  String get islandLeftExprLabel => 'Выражение левой части острова';

  @override
  String get islandRightExprLabel => 'Выражение правой части острова';

  @override
  String get aodTextSwitchLabel => 'Переключатель текста AOD';

  @override
  String get aodTextExprLabel => 'Выражение текста AOD';

  @override
  String get aodIconSourceLabel => 'Источник иконки AOD';

  @override
  String get focusNotificationLabel => 'Уведомление в фокусе';

  @override
  String get hideNotificationLabel => 'Скрыть уведомление';

  @override
  String get hideNotificationLabelSubtitle =>
      'Показывать только остров и скрывать уведомление в фокусе из шторки уведомлений';

  @override
  String get preserveStatusBarSmallIconLabel => 'Иконка в строке состояния';

  @override
  String get restoreLockscreenTitle =>
      'Восстановить уведомление на экране блокировки';

  @override
  String get restoreLockscreenSubtitle =>
      'Пропускать обработку уведомления в фокусе на экране блокировки, сохранить оригинальное поведение конфиденциальности';

  @override
  String get firstFloatLabel => 'Первое всплытие';

  @override
  String get updateFloatLabel => 'Обновление всплытия';

  @override
  String get autoDisappear => 'Автоматическое скрытие';

  @override
  String get seconds => 'с';

  @override
  String get highlightColorLabel => 'Цвет подсветки';

  @override
  String get dynamicHighlightColorLabel => 'Динамический цвет подсветки';

  @override
  String get dynamicHighlightColorLabelSubtitle =>
      'По умолчанию использовать динамический цвет на основе иконки';

  @override
  String get followDynamicColorLabel => 'Следовать динамическому цвету';

  @override
  String get dynamicHighlightModeDark => 'Темный';

  @override
  String get dynamicHighlightModeDarker => 'Более темный';

  @override
  String get outerGlowLabel => 'Внешнее свечение';

  @override
  String get forceOuterGlowLabel => 'Принудительно глобально';

  @override
  String get forceFocusOuterGlowSubtitle =>
      'Принудительное свечение для неподходящих уведомлений в фокусе при включении';

  @override
  String get forceIslandOuterGlowSubtitle =>
      'Принудительное свечение для неподходящих островов при включении';

  @override
  String get outEffectColorLabel => 'Цвет внешнего свечения';

  @override
  String get highlightColorHint =>
      'Формат #RRGGBB, оставьте пустым для значения по умолчанию';

  @override
  String get actionBgColorLabel => 'Цвет фона действия';

  @override
  String get actionBgColorDarkLabel => 'Цвет фона действия (темная тема)';

  @override
  String get actionTitleColorLabel => 'Цвет заголовка действия';

  @override
  String get actionTitleColorDarkLabel =>
      'Цвет заголовка действия (темная тема)';

  @override
  String get action1BgColorLabel => 'Цвет фона действия 1';

  @override
  String get action1BgColorDarkLabel => 'Цвет фона действия 1 (темная тема)';

  @override
  String get action1TitleColorLabel => 'Цвет заголовка действия 1';

  @override
  String get action1TitleColorDarkLabel =>
      'Цвет заголовка действия 1 (темная тема)';

  @override
  String get action2BgColorLabel => 'Цвет фона действия 2';

  @override
  String get action2BgColorDarkLabel => 'Цвет фона действия 2 (темная тема)';

  @override
  String get action2TitleColorLabel => 'Цвет заголовка действия 2';

  @override
  String get action2TitleColorDarkLabel =>
      'Цвет заголовка действия 2 (темная тема)';

  @override
  String get textHighlightLabel => 'Подсветка текста';

  @override
  String get narrowFontLabel => 'Узкий шрифт';

  @override
  String get showLeftHighlightLabel => 'Подсветка левого текста';

  @override
  String get showRightHighlightLabel => 'Подсветка правого текста';

  @override
  String get showLeftHighlightShort => 'Слева';

  @override
  String get showRightHighlightShort => 'Справа';

  @override
  String get colorHue => 'Оттенок';

  @override
  String get colorSaturation => 'Насыщенность';

  @override
  String get colorBrightness => 'Яркость';

  @override
  String get colorOpacity => 'Непрозрачность';

  @override
  String get onlyEnabledChannels => 'Применять только к включенным каналам';

  @override
  String enabledChannelsCount(int enabled, int total) {
    return '$enabled / $total каналов включено';
  }

  @override
  String get iconModeAuto => 'Авто';

  @override
  String get iconModeNotifSmall => 'Маленькая иконка уведомления';

  @override
  String get iconModeNotifLarge => 'Большая иконка уведомления';

  @override
  String get iconModeAppIcon => 'Иконка приложения';

  @override
  String get optDefault => 'По умолчанию';

  @override
  String get optDefaultOn => 'По умолчанию (Вкл)';

  @override
  String get optDefaultOff => 'По умолчанию (Выкл)';

  @override
  String get optOn => 'Вкл';

  @override
  String get optOff => 'Выкл';

  @override
  String get errorInvalidFormat => 'Неверный формат конфигурации';

  @override
  String get errorNoStorageDir => 'Не удалось получить директорию хранилища';

  @override
  String get errorNoFileSelected => 'Файл не выбран';

  @override
  String get errorNoFilePath => 'Не удалось получить путь к файлу';

  @override
  String get errorEmptyClipboard => 'Буфер обмена пуст';

  @override
  String get navBlacklist => 'Черный список фокуса';

  @override
  String get navBlacklistSubtitle =>
      'Блокировать всплытие уведомления в фокусе или скрывать его для определенных приложений';

  @override
  String get presetGamesTitle => 'Быстрый фильтр популярных игр';

  @override
  String presetGamesSuccess(int count) {
    return 'Добавлено $count установленных игр в черный список из предустановки';
  }

  @override
  String blacklistedAppsCount(int count) {
    return 'Заблокированы уведомления в фокусе для $count приложений';
  }

  @override
  String blacklistedAppsCountWithSystem(int count) {
    return 'Заблокированы уведомления в фокусе для $count приложений (включая системные)';
  }

  @override
  String get firstFloatLabelSubtitle =>
      'Разворачивать ли как уведомление в фокусе, когда Остров впервые получает уведомление';

  @override
  String get updateFloatLabelSubtitle =>
      'Разворачивать ли уведомление при обновлении Острова';

  @override
  String get marqueeChannelTitleSubtitle =>
      'Прокручивать ли длинные сообщения на Острове';

  @override
  String get focusNotificationLabelSubtitle =>
      'Заменять уведомление уведомлением в фокусе (при отключении показывается оригинальное уведомление)';

  @override
  String get preserveStatusBarSmallIconLabelSubtitle =>
      'Принудительно сохранять иконку в строке состояния при отображении уведомления в фокусе';

  @override
  String get fullscreenBehaviorTitle => 'Поведение в полноэкранном режиме';

  @override
  String get fullscreenBehaviorSubtitle =>
      'Стратегия уведомлений при обнаружении альбомной ориентации или полноэкранного режима';

  @override
  String get fullscreenBehaviorOff => 'По умолчанию';

  @override
  String get fullscreenBehaviorFallback => 'Откат к обычному уведомлению';

  @override
  String get filterRulesTitle => 'Правила фильтрации';

  @override
  String get filterRulesOrderTitle => 'Применяется первое совпавшее правило';

  @override
  String get filterRuleDnd => 'Не беспокоить';

  @override
  String get filterRuleFullscreen => 'Полноэкранный режим';

  @override
  String get filterRuleLandscape => 'Альбомная ориентация';

  @override
  String get dndBehaviorTitle => 'В режиме «Не беспокоить»';

  @override
  String get fullscreenRuleTitle => 'В полноэкранном режиме';

  @override
  String get landscapeRuleTitle => 'В альбомной ориентации';

  @override
  String get behaviorPreviewDefault =>
      'Без переопределения при совпадении; сохранить поведение по умолчанию';

  @override
  String get behaviorPreviewSuppress =>
      'Откат к обычному уведомлению при совпадении';

  @override
  String get behaviorPreviewSmallOnly =>
      'Показывать только малый остров; не разворачивать автоматически';

  @override
  String get behaviorPreviewExpand =>
      'Автоматически разворачивать уведомление при совпадении';

  @override
  String get aiConfigSection => 'Улучшение с помощью ИИ';

  @override
  String get aiConfigTitle => 'Сводка уведомлений с помощью ИИ';

  @override
  String get aiConfigSubtitleEnabled =>
      'Включено · Нажмите для настройки параметров ИИ';

  @override
  String get aiConfigSubtitleDisabled => 'Отключено · Нажмите для настройки';

  @override
  String get aiEnabledTitle => 'Включить сводку ИИ';

  @override
  String get aiEnabledSubtitle =>
      'ИИ генерирует левый/правый текст Острова, откатывается при тайм-ауте или ошибке';

  @override
  String get aiApiSection => 'Параметры API';

  @override
  String get aiUrlLabel => 'URL API';

  @override
  String get aiUrlHint => 'https://api.openai.com/v1/chat/completions';

  @override
  String get aiApiKeyLabel => 'Ключ API';

  @override
  String get aiApiKeyHint => 'sk-...';

  @override
  String get aiModelLabel => 'Модель';

  @override
  String get aiModelHint => 'gpt-4o-mini';

  @override
  String get aiModelPickerTitle => 'Выбрать модель';

  @override
  String get aiModelPickerSearchHint => 'Поиск моделей…';

  @override
  String get aiModelPickerEmpty => 'Модели не найдены';

  @override
  String get aiModelPickerRetry => 'Повторить';

  @override
  String get aiModelPickerClose => 'Закрыть';

  @override
  String get aiModelPickerFetchError => 'Не удалось загрузить список моделей';

  @override
  String get aiPromptLabel => 'Пользовательский промпт';

  @override
  String get aiPromptHint =>
      'Оставьте пустым для использования по умолчанию: Извлечь ключевую информацию, слева и справа не более 6 слов или 12 символов';

  @override
  String get aiPromptInUserTitle => 'Поместить промпт в сообщение пользователя';

  @override
  String get aiPromptInUserSubtitle =>
      'Некоторые модели не поддерживают системные инструкции; включите, чтобы поместить промпт в сообщение пользователя';

  @override
  String get aiTimeoutTitle => 'Тайм-аут ответа ИИ';

  @override
  String aiTimeoutLabel(int seconds) {
    return 'Тайм-аут ответа ИИ';
  }

  @override
  String get aiTemperatureTitle => 'Температура выборки';

  @override
  String get aiTemperatureSubtitle =>
      'Управляет случайностью ответов. 0 — точно, 1 — более креативно';

  @override
  String get aiMaxTokensTitle => 'Максимум токенов';

  @override
  String get aiMaxTokensSubtitle =>
      'Ограничить максимальную длину ответов, генерируемых ИИ';

  @override
  String get aiDefaultPromptFull =>
      'Оставьте пустым для использования промпта по умолчанию: Извлечь ключевую информацию из уведомления, не более 6 слов или 12 символов для левой и правой сторон';

  @override
  String get aiTestButton => 'Тест соединения';

  @override
  String get aiTestUrlEmpty => 'Сначала введите URL API';

  @override
  String get aiLastLogTitle => 'Журнал последних запросов ИИ';

  @override
  String get aiLastLogSubtitle =>
      'Здесь отображаются запросы ИИ, вызванные тестами соединения или уведомлениями';

  @override
  String get aiLastLogEmpty => 'Журналы запросов ИИ пока отсутствуют';

  @override
  String get aiLastLogSourceLabel => 'Источник';

  @override
  String get aiLastLogTimeLabel => 'Время';

  @override
  String get aiLastLogStatusLabel => 'Статус';

  @override
  String get aiLastLogDurationLabel => 'Длительность';

  @override
  String get aiLastLogSourceNotification => 'Триггер уведомления';

  @override
  String get aiLastLogSourceSettingsTest => 'Тест настроек';

  @override
  String get aiLastLogRendered => 'Отрисовано';

  @override
  String get aiLastLogRaw => 'Исходные данные';

  @override
  String get aiLastLogCopy => 'Копировать журнал';

  @override
  String get aiLastLogCopied => 'Журнал запросов ИИ скопирован';

  @override
  String get aiLastLogRequest => 'Запрос';

  @override
  String get aiLastLogResponse => 'Ответ';

  @override
  String get aiLastLogUsage => 'Использование токенов';

  @override
  String get aiLastLogMessages => 'Сообщения';

  @override
  String get aiLastLogError => 'Ошибка';

  @override
  String get aiLastLogHttpCode => 'Статус HTTP';

  @override
  String get aiLastLogLeftText => 'Левый текст';

  @override
  String get aiLastLogRightText => 'Правый текст';

  @override
  String get aiLastLogAssistantContent => 'Содержимое ответа модели';

  @override
  String get aiConfigSaveButton => 'Сохранить';

  @override
  String get aiConfigSaved => 'Конфигурация ИИ сохранена';

  @override
  String get aiConfigTips =>
      'ИИ получает пакет приложения, заголовок и содержимое каждого уведомления и возвращает короткий левый (источник) и правый (содержимое) текст. Совместимо с API формата OpenAI (например, DeepSeek, Claude). При отсутствии ответа откатывается к логике по умолчанию.';

  @override
  String get templateAiNotificationIslandName => 'Остров уведомлений ИИ';

  @override
  String get aiPromptDefault =>
      'Извлечь ключевую информацию из уведомления, слева и справа не более 6 слов или 12 символов';

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
  String get hideDesktopIconTitle => 'Скрыть иконку на рабочем столе';

  @override
  String get hideDesktopIconSubtitle =>
      'Скрыть иконку приложения из лаунчера. Открывайте через LSPosed Manager после скрытия';

  @override
  String get filterRulesSection => 'Правила фильтрации';

  @override
  String get foregroundRulesTab => 'Правила переднего плана';

  @override
  String get foregroundExclusionsTab => 'Исключенные приложения';

  @override
  String get foregroundRulesDescription =>
      'Задать поведение Острова при запуске приложения на переднем плане.';

  @override
  String get foregroundExclusionsDescription =>
      'Уведомления от приложений в списке исключений не подпадают под правила переднего плана.';

  @override
  String get hideSystemApps => 'Скрыть системные приложения';

  @override
  String get restoreDefaultConfig => 'Восстановить конфигурацию по умолчанию';

  @override
  String resetDefaultConfigSuccess(int count) {
    return 'Конфигурация по умолчанию восстановлена для $count приложений';
  }

  @override
  String get sceneActionDefault => 'По умолчанию';

  @override
  String get sceneActionSmallOnly => 'Отключить расширение';

  @override
  String get sceneActionExpand => 'Автоматически расширять';

  @override
  String get sceneActionSuppress => 'Откат';

  @override
  String get filterModeLabel => 'Режим фильтрации';

  @override
  String get filterModeBlacklist => 'Черный список';

  @override
  String get filterModeWhitelist => 'Белый список';

  @override
  String get filterModeBlacklistDesc =>
      'Уведомления, совпадающие с ключевыми словами, будут отфильтрованы';

  @override
  String get filterModeWhitelistDesc =>
      'Будут показаны только уведомления, совпадающие с ключевыми словами';

  @override
  String get whitelistKeywordsLabel => 'Ключевые слова белого списка';

  @override
  String get blacklistKeywordsLabel => 'Ключевые слова черного списка';

  @override
  String get addKeyword => 'Добавить ключевое слово';

  @override
  String get keywordHint => 'Введите ключевое слово';

  @override
  String get removeKeyword => 'Удалить';

  @override
  String get keywordFilterPriority =>
      'Белый список имеет приоритет: показываются только уведомления, совпадающие с белым списком, но черный список все равно может заблокировать';

  @override
  String get exportChannelsToClipboard => 'Экспорт настроек каналов';

  @override
  String get importChannelsFromClipboard => 'Импорт настроек каналов';

  @override
  String get exportChannelsSuccess =>
      'Настройки каналов скопированы в буфер обмена';

  @override
  String importChannelsSuccess(int count) {
    return 'Импортировано настроек каналов: $count';
  }

  @override
  String importChannelsPartialSuffix(int total, int matched) {
    return ' (совпало $matched из $total)';
  }

  @override
  String get importErrorEmptyClipboard =>
      'Буфер обмена пуст. Сначала скопируйте настройки каналов';

  @override
  String get importErrorNotJson =>
      'Содержимое буфера обмена не является валидным JSON';

  @override
  String get importErrorMissingChannels =>
      'Неверный формат данных: отсутствует список каналов';

  @override
  String get importErrorNoMatch =>
      'Ни один канал не совпал с текущим приложением. Пожалуйста, проверьте источник данных';

  @override
  String get importErrorUnknown =>
      'Ошибка импорта. Пожалуйста, проверьте данные в буфере обмена';

  @override
  String get mediaNotificationTitle => 'Медиа-уведомление';

  @override
  String get mediaNotificationDisabledSubtitle =>
      'Удалять все медиа-уведомление при отключении';

  @override
  String get normalNotificationTitle => 'Обычное уведомление';

  @override
  String get normalNotificationSubtitle =>
      'Удалять медиа-поля и обрабатывать как обычное уведомление при включении';

  @override
  String get channelSettingsUnmodified => 'Не изменено';

  @override
  String get restoreDefault => 'Восстановить по умолчанию';

  @override
  String get islandDimenSection => 'Размеры Острова';

  @override
  String get islandDimenHeight => 'Высота Острова';

  @override
  String get islandTopOffset => 'Расстояние от верха экрана';

  @override
  String get followSystem => 'Как в системе';

  @override
  String get islandDimenMiniY => 'Вертикальное положение';

  @override
  String get islandDimenMiniYHint => '0=как в системе';

  @override
  String get islandBgSection => 'Фон Острова';

  @override
  String get islandBgSmallTitle => 'Фон малого Острова';

  @override
  String get islandBgBigTitle => 'Фон большого Острова';

  @override
  String get islandBgExpandTitle => 'Фон уведомления в фокусе';

  @override
  String get islandBgNotSet => 'Не задано';

  @override
  String get islandBgCornerRadius => 'Радиус скругления углов';

  @override
  String get islandBgCornerRadiusHint => '0=системный по умолчанию';

  @override
  String get islandBgImageSelected => 'Фоновое изображение сохранено';

  @override
  String get islandBgImageDeleted => 'Фоновое изображение удалено';

  @override
  String get islandBgDeleteFailed => 'Ошибка удаления';

  @override
  String islandBgEditTitle(String type) {
    return 'Редактировать фон: $type';
  }

  @override
  String get islandBgBlurLabel => 'Размытие';

  @override
  String get islandBgBrightnessLabel => 'Яркость';

  @override
  String get islandBgOpacityLabel => 'Непрозрачность';

  @override
  String get islandBgDefault => 'По умолчанию';

  @override
  String get keepIslandTitle => 'Держать Остров видимым';

  @override
  String get keepIslandSubtitle =>
      'Отправлять пустое уведомление, чтобы Остров всегда был виден';

  @override
  String get keepIslandAutoHideTitle => 'Автоскрытие';

  @override
  String get keepIslandAutoHideSubtitle =>
      'Автоматически скрывать пустой остров при поступлении уведомления и восстанавливать при его закрытии';

  @override
  String get keepIslandHideLandscapeTitle => 'Скрывать в альбомной ориентации';

  @override
  String get keepIslandHideLandscapeSubtitle =>
      'Скрывать постоянный Остров в альбомной ориентации и восстанавливать в портретной, если нет уведомления';

  @override
  String get keepIslandHighlightColorTitle => 'Цвет подсветки';

  @override
  String get keepIslandHighlightColorSubtitle =>
      'Настроить цвет текста подсветки для режима удержания Острова';

  @override
  String get keepIslandLeftContentTitle => 'Левое содержимое Острова';

  @override
  String get keepIslandRightContentTitle => 'Правое содержимое Острова';

  @override
  String get keepIslandPlaceholdersTitle => 'Доступные плейсхолдеры';

  @override
  String keepIslandPlaceholdersDescription(
    String batteryLevel,
    String cpuUsage,
  ) {
    return 'Введите текст или выражения для любой стороны, например: Battery $batteryLevel, CPU $cpuUsage. Нажмите тег, чтобы скопировать.';
  }

  @override
  String keepIslandPlaceholderCopied(String placeholder) {
    return 'Скопировано $placeholder';
  }

  @override
  String get keepIslandDefaultEmpty => 'По умолчанию пусто';

  @override
  String keepIslandContentHint(String placeholder) {
    return 'По умолчанию пусто. Введите текст или $placeholder';
  }

  @override
  String get clear => 'Очистить';

  @override
  String get save => 'Сохранить';

  @override
  String get islandOtherSection => 'Прочее';

  @override
  String get miscSection => 'Разное';

  @override
  String get onboardingEntryTitle => 'Открыть онбординг';

  @override
  String get onboardingEntrySubtitle =>
      'Просмотреть приветствие и процесс быстрого старта';

  @override
  String get onboardingAppName => 'HyperIsland';

  @override
  String get onboardingWelcomeTitle => 'Добро пожаловать в HyperIsland';

  @override
  String get onboardingWelcomeSubtitle =>
      'Быстро и аккуратно настройте работу вашего Острова';

  @override
  String get onboardingEnvironmentTitle => 'Проверка окружения';

  @override
  String get onboardingEnvironmentSubtitle =>
      'Проверить статус разрешений модуля';

  @override
  String get onboardingNotificationStyleTitle => 'Выберите стиль уведомлений';

  @override
  String get onboardingNotificationStyleSubtitle =>
      'Выберите предпочтительное отображение уведомлений по умолчанию';

  @override
  String get onboardingOriginalNotificationLabel => 'Оригинальное уведомление';

  @override
  String get onboardingFinishTitle => 'Все готово';

  @override
  String get onboardingFinishSubtitle =>
      'После онбординга вы можете продолжить настройку деталей в Настройках';

  @override
  String onboardingStepLabel(int current, int total) {
    return 'Шаг $current / $total';
  }

  @override
  String get onboardingPrevious => 'Назад';

  @override
  String get onboardingNext => 'Далее';

  @override
  String get onboardingDone => 'Начать';

  @override
  String get onboardingStatusTitle => 'Проверка статуса';

  @override
  String get onboardingRetry => 'Повторить';

  @override
  String get onboardingLsposedStatus => 'Активация LSPosed';

  @override
  String get onboardingRootStatus => 'Root-доступ';

  @override
  String get onboardingAppListStatus => 'Разрешение на список приложений';

  @override
  String get onboardingProtocolStatus => 'Версия системного протокола';

  @override
  String get onboardingAndroidStatus => 'Версия Android';

  @override
  String get onboardingUnsupportedSystem => 'Текущая система не поддерживается';

  @override
  String get onboardingAndroid15Limited => 'Поддержка Android 15 ограничена';

  @override
  String get onboardingMissingPermissionTitle =>
      'Отсутствует необходимое разрешение';

  @override
  String get onboardingMissingPermissionMessage =>
      'Модуль может работать некорректно';

  @override
  String get onboardingDialogClose => 'Закрыть';

  @override
  String get onboardingDialogContinue => 'Продолжить';

  @override
  String get backupRestoreSection => 'Резервное копирование и восстановление';

  @override
  String get hookExtensionSection => 'Расширение перехвата';

  @override
  String get hookScopeSettings => 'Системные настройки';

  @override
  String get settingsHomeEntryTitle => 'Запись в Системных настройках';

  @override
  String get settingsHomeEntrySubtitle =>
      'Показывать запись HyperIsland на главной странице Системных настроек';

  @override
  String get xposedScopeRequestFailed =>
      'Ошибка запроса области. Убедитесь, что модуль включен в LSPosed';

  @override
  String get hookScopeSystemUI => 'Системный UI';

  @override
  String get smoothIslandTitle => 'Плавный Остров';

  @override
  String get smoothIslandSubtitle =>
      'Использовать капсулу с непрерывной кривизной для контуров Острова. Перезапустите область после отключения для полной выгрузки перехвата';

  @override
  String get smoothIslandSmoothingTitle => 'Сила сглаживания';

  @override
  String get bluetoothIslandStatusEnabled => 'Включено';

  @override
  String get bluetoothIslandStatusDisabled => 'Отключено';

  @override
  String get bluetoothIslandTitle => 'Bluetooth Остров';

  @override
  String bluetoothIslandSubtitle(String status) {
    return '$status · Отслеживать подключения и отключения Bluetooth-устройств, затем передавать Остров через Системный UI';
  }

  @override
  String get bluetoothIslandSettingsTitle => 'Настройки Bluetooth Острова';

  @override
  String get bluetoothIslandEnableTitle => 'Включить Bluetooth Остров';

  @override
  String get bluetoothIslandEnableSubtitle =>
      'После отключения перезапустите Системный UI для применения. Перехват Bluetooth не будет зарегистрирован';

  @override
  String get bluetoothIslandShowDeviceNameTitle => 'Показывать имя устройства';

  @override
  String get bluetoothIslandShowDeviceNameSubtitle =>
      'При подключении сначала показать имя устройства справа, затем показать статус подключения';

  @override
  String get bluetoothIslandDisplayDurationTitle => 'Длительность показа';

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
  String get outerGlowTitle => 'Внешнее свечение';

  @override
  String get bluetoothIslandOuterGlowSubtitle =>
      'Управлять эффектом внешнего свечения Bluetooth Острова';

  @override
  String get outerGlowColorTitle => 'Цвет внешнего свечения';

  @override
  String get hookScopeXMSF => 'Xiaomi Service Framework (XMSF)';

  @override
  String get downloadManagerSection => 'Менеджер загрузок';

  @override
  String get themePageTitle => 'Тема';

  @override
  String get themeSeedColorTitle => 'Цвет темы';

  @override
  String get themeSeedColorSubtitle => 'Настроить акцентный цвет приложения';

  @override
  String get presetColors => 'Предустановленные цвета';

  @override
  String get themeResetColor => 'Сбросить до значений по умолчанию';

  @override
  String get blurBarsTitle => 'Матовое стекло';

  @override
  String get blurBarsSubtitle =>
      'Добавить эффект размытия и прозрачности к верхней и нижней панелям';

  @override
  String get bluetoothIslandWhitelistTitle => 'Белый список устройств';

  @override
  String get bluetoothIslandWhitelistSubtitle =>
      'Показывать Остров только для Bluetooth-устройств из белого списка';

  @override
  String get bluetoothIslandWhitelistButton => 'Управление белым списком';

  @override
  String bluetoothIslandWhitelistButtonSubtitle(int count) {
    return 'Выбрано устройств: $count';
  }

  @override
  String get bluetoothIslandWhitelistDialogTitle =>
      'Выберите Bluetooth-устройства';

  @override
  String get bluetoothIslandWhitelistEmpty =>
      'Нет сопряженных устройств. Сначала выполните сопряжение устройства в системных настройках Bluetooth';

  @override
  String get bluetoothIslandWhitelistAllHint =>
      'При отключении Остров показывается для всех Bluetooth-устройств';

  @override
  String get bluetoothIslandLoadDevicesFailed =>
      'Не удалось загрузить Bluetooth-устройства';

  @override
  String get bluetoothIslandNeedBtPermission =>
      'Для загрузки устройств требуется разрешение Bluetooth';

  @override
  String get hideBehaviorTitle => 'Поведение при скрытии';

  @override
  String get hideBehaviorDescription =>
      'Управлять тем, разрешено ли системным сценам временно скрывать Остров. Отключение элемента блокирует соответствующую системную логику скрытия.';

  @override
  String get hideBehaviorMasterSwitch =>
      'Включить перехват поведения при скрытии';

  @override
  String get hideBehaviorMasterSwitchSubtitle =>
      'Регистрирует перехват поведения при скрытии только при включении. Отключение означает отсутствие перехвата и является значением по умолчанию.';

  @override
  String get hideBehaviorScreenPinning => 'Закрепление экрана';

  @override
  String get hideBehaviorScreenPinningSubtitle =>
      'Скрывать Остров, пока активно закрепление экрана';

  @override
  String get hideBehaviorBouncerShowing => 'Экран разблокировки';

  @override
  String get hideBehaviorBouncerShowingSubtitle =>
      'Скрывать Остров, пока отображается запрос на разблокировку';

  @override
  String get hideBehaviorFullscreen => 'Полноэкранный режим';

  @override
  String get hideBehaviorFullscreenSubtitle =>
      'Скрывать Остров, когда строка состояния исчезает или активен иммерсивный полноэкранный режим';

  @override
  String get hideBehaviorScreenLocked => 'Экран блокировки';

  @override
  String get hideBehaviorScreenLockedSubtitle =>
      'Скрывать Остров во время работы экрана блокировки или выключения экрана';

  @override
  String get hideBehaviorNotificationCenter => 'Центр уведомлений';

  @override
  String get hideBehaviorNotificationCenterSubtitle =>
      'Скрывать Остров, пока шторка уведомлений разворачивается или переходит';

  @override
  String get off => 'Выкл';

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
  String get tapToSelectImage => 'Нажмите, чтобы выбрать изображение или GIF';

  @override
  String get autoExpandNotification =>
      'Автоматически разворачивать уведомление';

  @override
  String widthDpLabel(int width) {
    return '$width dp';
  }
}
