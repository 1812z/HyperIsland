import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'controllers/settings_controller.dart';
import 'l10n/generated/app_localizations.dart';
import 'pages/main_page.dart';
import 'services/app_cache_service.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  AppCacheService.instance.initialize();
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

  @override
  void initState() {
    super.initState();
    _themeMode = _ctrl.themeMode;
    _locale = _ctrl.locale;
    _ctrl.addListener(_onSettingsChanged);
  }

  @override
  void dispose() {
    _ctrl.removeListener(_onSettingsChanged);
    super.dispose();
  }

  void _onSettingsChanged() {
    final nextThemeMode = _ctrl.themeMode;
    final nextLocale = _ctrl.locale;
    if (nextThemeMode == _themeMode && nextLocale == _locale) return;
    if (!mounted) return;
    setState(() {
      _themeMode = nextThemeMode;
      _locale = nextLocale;
    });
  }

  @override
  Widget build(BuildContext context) {
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
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF6750A4),
          brightness: Brightness.light,
        ),
        useMaterial3: true,
      ),
      darkTheme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF6750A4),
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
      ),
      builder: (context, child) =>
          HeroControllerScope.none(child: child ?? const SizedBox.shrink()),
      themeMode: _themeMode,
      home: const MainPage(),
    );
  }
}
