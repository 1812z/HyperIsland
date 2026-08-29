import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'controllers/settings_controller.dart';
import 'l10n/generated/app_localizations.dart';
import 'pages/main_page.dart';
import 'pages/onboarding_page.dart';
import 'pages/blacklist_page.dart';
import 'pages/ai_config_page.dart';
import 'pages/references_page.dart';
import 'pages/island_sub/backup_restore_page.dart';
import 'pages/island_sub/default_config_page.dart';
import 'pages/island_sub/hook_extension_page.dart';
import 'pages/island_sub/hide_behavior_page.dart';
import 'pages/island_sub/island_appearance_page.dart';
import 'pages/island_sub/island_other_page.dart';
import 'pages/island_sub/keep_island_page.dart';
import 'pages/island_sub/misc_page.dart';
import 'pages/island_sub/theme_page.dart';
import 'services/app_cache_service.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _ctrl = SettingsController.instance;
  late ThemeMode _themeMode;
  Locale? _locale;
  int _seedColor = 0xFF6750A4;
  bool _blurBars = false;
  bool _settingsLoading = true;
  bool _onboardingCompleted = false;
  bool _appCacheInitialized = false;

  @override
  void initState() {
    super.initState();
    _themeMode = _ctrl.themeMode;
    _locale = _ctrl.locale;
    _seedColor = _ctrl.themeSeedColor;
    _blurBars = _ctrl.blurBars;
    _settingsLoading = _ctrl.loading;
    _onboardingCompleted = _ctrl.onboardingCompleted;
    _ctrl.addListener(_onSettingsChanged);
    _initializeAppCacheIfReady();
  }

  @override
  void dispose() {
    _ctrl.removeListener(_onSettingsChanged);
    super.dispose();
  }

  void _onSettingsChanged() {
    final nextThemeMode = _ctrl.themeMode;
    final nextLocale = _ctrl.locale;
    final nextSeedColor = _ctrl.themeSeedColor;
    final nextBlurBars = _ctrl.blurBars;
    final nextSettingsLoading = _ctrl.loading;
    final nextOnboardingCompleted = _ctrl.onboardingCompleted;
    if (nextThemeMode == _themeMode &&
        nextLocale == _locale &&
        nextSeedColor == _seedColor &&
        nextBlurBars == _blurBars &&
        nextSettingsLoading == _settingsLoading &&
        nextOnboardingCompleted == _onboardingCompleted) {
      return;
    }
    if (!mounted) return;
    setState(() {
      _themeMode = nextThemeMode;
      _locale = nextLocale;
      _seedColor = nextSeedColor;
      _blurBars = nextBlurBars;
      _settingsLoading = nextSettingsLoading;
      _onboardingCompleted = nextOnboardingCompleted;
    });
    _initializeAppCacheIfReady();
  }

  void _initializeAppCacheIfReady() {
    if (_appCacheInitialized || _settingsLoading || !_onboardingCompleted) {
      return;
    }
    _appCacheInitialized = true;
    AppCacheService.instance.initialize();
  }

  ThemeData _buildTheme({
    required Color seedColor,
    required Brightness brightness,
    required bool blur,
  }) {
    final colorScheme = ColorScheme.fromSeed(
      seedColor: seedColor,
      brightness: brightness,
    );
    return ThemeData(
      colorScheme: colorScheme,
      useMaterial3: true,
      // ── 全局圆角主题 ──
      dialogTheme: DialogThemeData(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
        ),
      ),
      cardTheme: CardThemeData(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      ),
      inputDecorationTheme: InputDecorationTheme(
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
        filled: true,
        fillColor: colorScheme.surfaceContainerHighest,
      ),
      popupMenuTheme: PopupMenuThemeData(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
      menuTheme: MenuThemeData(
        style: MenuStyle(
          shape: WidgetStatePropertyAll(
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
        ),
      ),
      dropdownMenuTheme: DropdownMenuThemeData(
        menuStyle: MenuStyle(
          shape: WidgetStatePropertyAll(
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      ),
      snackBarTheme: SnackBarThemeData(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        behavior: SnackBarBehavior.floating,
      ),
      chipTheme: ChipThemeData(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      ),
      // ── 原有自定义 ──
      appBarTheme: AppBarTheme(
        backgroundColor: blur ? Colors.transparent : colorScheme.surface,
        scrolledUnderElevation: blur ? 0 : null,
        systemOverlayStyle: blur
            ? (brightness == Brightness.light
                  ? SystemUiOverlayStyle.dark
                  : SystemUiOverlayStyle.light)
            : null,
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: blur
            ? colorScheme.surface.withValues(alpha: 0.7)
            : colorScheme.surface,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final seedColor = Color(_seedColor);

    return MaterialApp(
      title: 'HyperIsland',
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      locale: _locale,
      theme: _buildTheme(
        seedColor: seedColor,
        brightness: Brightness.light,
        blur: _blurBars,
      ),
      darkTheme: _buildTheme(
        seedColor: seedColor,
        brightness: Brightness.dark,
        blur: _blurBars,
      ),
      themeMode: _themeMode,
      routes: {
        '/apps': (_) => const MainPage(initialIndex: 1),
        '/onboarding': (_) => const OnboardingPage(showCloseButton: true),
        '/settings': (_) => const MainPage(initialIndex: 2),
        '/settings/appearance': (_) => const IslandAppearancePage(),
        '/settings/ai': (_) => const AiConfigPage(),
        '/settings/theme': (_) => const ThemePage(),
        '/settings/default': (_) => const DefaultConfigPage(),
        '/settings/keep-island': (_) => const KeepIslandPage(),
        '/settings/filter-rules': (_) => const BlacklistPage(),
        '/settings/hide-behavior': (_) => const HideBehaviorPage(),
        '/settings/other': (_) => const IslandOtherPage(),
        '/settings/misc': (_) => const MiscPage(),
        '/settings/hook-extension': (_) => const HookExtensionPage(),
        '/settings/backup-restore': (_) => const BackupRestorePage(),
        '/settings/references': (_) => const ReferencesPage(),
      },
      home: _settingsLoading
          ? const Scaffold(body: Center(child: CircularProgressIndicator()))
          : _onboardingCompleted
          ? const MainPage()
          : const OnboardingPage(),
    );
  }
}
