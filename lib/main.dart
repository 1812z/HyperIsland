import 'package:flutter/material.dart';
import 'package:flutter_miuix/miuix.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'controllers/settings_controller.dart';
import 'l10n/generated/app_localizations.dart';
import 'pages/main_page.dart';
import 'pages/onboarding_page.dart';
import 'services/app_cache_service.dart';
import 'services/system_font_weight.dart';

const _platform = MethodChannel('io.github.hyperisland/test');

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
  int _seedColor = kDefaultThemeSeedColor;
  bool _monetTheme = false;
  bool _blurBars = false;
  bool _settingsLoading = true;
  bool _onboardingCompleted = false;
  bool _appCacheInitialized = false;
  int _fontWeightAdjustment = 0;

  @override
  void initState() {
    super.initState();
    _themeMode = _ctrl.themeMode;
    _locale = _ctrl.locale;
    _seedColor = _ctrl.themeSeedColor;
    _monetTheme = _ctrl.monetTheme;
    _blurBars = _ctrl.blurBars;
    _settingsLoading = _ctrl.loading;
    _onboardingCompleted = _ctrl.onboardingCompleted;
    _ctrl.addListener(_onSettingsChanged);
    _platform.setMethodCallHandler(_handlePlatformCall);
    _loadSystemFontWeightAdjustment();
    _initializeAppCacheIfReady();
  }

  @override
  void dispose() {
    _ctrl.removeListener(_onSettingsChanged);
    _platform.setMethodCallHandler(null);
    super.dispose();
  }

  Future<void> _loadSystemFontWeightAdjustment() async {
    try {
      final adjustment =
          await _platform.invokeMethod<int>('getSystemFontWeightAdjustment') ??
          0;
      _setFontWeightAdjustment(adjustment);
    } catch (_) {}
  }

  Future<void> _handlePlatformCall(MethodCall call) async {
    if (call.method == 'systemFontWeightAdjustmentChanged') {
      _setFontWeightAdjustment(call.arguments as int? ?? 0);
    }
  }

  void _setFontWeightAdjustment(int adjustment) {
    if (!mounted || adjustment == _fontWeightAdjustment) return;
    SystemFontWeight.adjustment = adjustment;
    setState(() => _fontWeightAdjustment = adjustment);
  }

  TextTheme _adjustTextTheme(TextTheme theme) {
    return theme.copyWith(
      displayLarge: _adjustTextStyle(theme.displayLarge),
      displayMedium: _adjustTextStyle(theme.displayMedium),
      displaySmall: _adjustTextStyle(theme.displaySmall),
      headlineLarge: _adjustTextStyle(theme.headlineLarge),
      headlineMedium: _adjustTextStyle(theme.headlineMedium),
      headlineSmall: _adjustTextStyle(theme.headlineSmall),
      titleLarge: _adjustTextStyle(theme.titleLarge),
      titleMedium: _adjustTextStyle(theme.titleMedium),
      titleSmall: _adjustTextStyle(theme.titleSmall),
      bodyLarge: _adjustTextStyle(theme.bodyLarge),
      bodyMedium: _adjustTextStyle(theme.bodyMedium),
      bodySmall: _adjustTextStyle(theme.bodySmall),
      labelLarge: _adjustTextStyle(theme.labelLarge),
      labelMedium: _adjustTextStyle(theme.labelMedium),
      labelSmall: _adjustTextStyle(theme.labelSmall),
    );
  }

  TextStyle? _adjustTextStyle(TextStyle? style) {
    return style == null ? null : SystemFontWeight.style(style);
  }

  MiuixTextStyles _adjustMiuixTextStyles(MiuixTextStyles styles) {
    return styles.copy(
      main: SystemFontWeight.style(styles.main),
      paragraph: SystemFontWeight.style(styles.paragraph),
      body1: SystemFontWeight.style(styles.body1),
      body2: SystemFontWeight.style(styles.body2),
      button: SystemFontWeight.style(styles.button),
      footnote1: SystemFontWeight.style(styles.footnote1),
      footnote2: SystemFontWeight.style(styles.footnote2),
      headline1: SystemFontWeight.style(styles.headline1),
      headline2: SystemFontWeight.style(styles.headline2),
      subtitle: SystemFontWeight.style(styles.subtitle),
      title1: SystemFontWeight.style(styles.title1),
      title2: SystemFontWeight.style(styles.title2),
      title3: SystemFontWeight.style(styles.title3),
      title4: SystemFontWeight.style(styles.title4),
    );
  }

  void _onSettingsChanged() {
    final nextThemeMode = _ctrl.themeMode;
    final nextLocale = _ctrl.locale;
    final nextSeedColor = _ctrl.themeSeedColor;
    final nextMonetTheme = _ctrl.monetTheme;
    final nextBlurBars = _ctrl.blurBars;
    final nextSettingsLoading = _ctrl.loading;
    final nextOnboardingCompleted = _ctrl.onboardingCompleted;
    if (nextThemeMode == _themeMode &&
        nextLocale == _locale &&
        nextSeedColor == _seedColor &&
        nextMonetTheme == _monetTheme &&
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
      _monetTheme = nextMonetTheme;
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
    final theme = ThemeData(
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
    return theme.copyWith(textTheme: _adjustTextTheme(theme.textTheme));
  }

  ThemeData _buildMiuixTheme({
    required MiuixThemeData miuix,
    required bool blur,
  }) {
    final colors = miuix.colors;
    final colorScheme =
        ColorScheme.fromSeed(
          seedColor: colors.primary,
          brightness: miuix.brightness,
        ).copyWith(
          primary: colors.primary,
          onPrimary: colors.onPrimary,
          primaryContainer: colors.primaryContainer,
          onPrimaryContainer: colors.onPrimaryContainer,
          secondary: colors.secondary,
          onSecondary: colors.onSecondary,
          secondaryContainer: colors.secondaryContainer,
          onSecondaryContainer: colors.onSecondaryContainer,
          error: colors.error,
          onError: colors.onError,
          errorContainer: colors.errorContainer,
          onErrorContainer: colors.onErrorContainer,
          surface: colors.background,
          onSurface: colors.onBackground,
          surfaceContainer: colors.surfaceContainer,
          surfaceContainerHigh: colors.surfaceContainerHigh,
          surfaceContainerHighest: colors.surfaceContainerHighest,
          outline: colors.outline,
          outlineVariant: colors.dividerLine,
          scrim: colors.windowDimming,
        );

    return _buildTheme(
      seedColor: colors.primary,
      brightness: miuix.brightness,
      blur: blur,
    ).copyWith(
      colorScheme: colorScheme,
      scaffoldBackgroundColor: colors.background,
    );
  }

  Widget _buildApp({
    required ThemeData theme,
    required ThemeData darkTheme,
    required ThemeMode themeMode,
  }) {
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
      theme: theme,
      darkTheme: darkTheme,
      themeMode: themeMode,
      home: _settingsLoading
          ? const Scaffold(body: Center(child: CircularProgressIndicator()))
          : _onboardingCompleted
          ? const MainPage()
          : const OnboardingPage(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final seedColor = Color(_seedColor);

    final miuixMode = switch ((_themeMode, _monetTheme)) {
      (ThemeMode.light, false) => MiuixColorSchemeMode.light,
      (ThemeMode.dark, false) => MiuixColorSchemeMode.dark,
      (ThemeMode.system, false) => MiuixColorSchemeMode.system,
      (ThemeMode.light, true) => MiuixColorSchemeMode.monetLight,
      (ThemeMode.dark, true) => MiuixColorSchemeMode.monetDark,
      (ThemeMode.system, true) => MiuixColorSchemeMode.monetSystem,
    };

    return MiuixThemeController(
      colorSchemeMode: miuixMode,
      keyColor: _monetTheme ? seedColor : null,
      textStyles: _adjustMiuixTextStyles(defaultTextStyles()),
      child: Builder(
        builder: (context) {
          final miuix = MiuixTheme.of(context);
          final theme = _buildMiuixTheme(miuix: miuix, blur: _blurBars);
          return _buildApp(
            theme: theme,
            darkTheme: theme,
            themeMode: ThemeMode.light,
          );
        },
      ),
    );
  }
}
