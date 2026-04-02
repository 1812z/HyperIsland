import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'controllers/settings_controller.dart';
import 'l10n/generated/app_localizations.dart';
import 'pages/main_page.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _ctrl = SettingsController.instance;

  @override
  void initState() {
    super.initState();
    _ctrl.addListener(_onSettingsChanged);
  }

  @override
  void dispose() {
    _ctrl.removeListener(_onSettingsChanged);
    super.dispose();
  }

  void _onSettingsChanged() => setState(() {});

  @override
  Widget build(BuildContext context) {
    const seedColor = Color(0xFF6750A4);

    final lightColorScheme = ColorScheme.fromSeed(
      seedColor: seedColor,
      brightness: Brightness.light,
    );
    final darkColorScheme = ColorScheme.fromSeed(
      seedColor: seedColor,
      brightness: Brightness.dark,
    );

    final commonSwitchTheme = SwitchThemeData(
      thumbIcon: WidgetStateProperty.resolveWith<Icon?>(
        (Set<WidgetState> states) {
          if (states.contains(WidgetState.selected)) {
            return const Icon(Icons.check);
          }
          return null;
        },
      ),
    );

    const commonSliderTheme = SliderThemeData(
      showValueIndicator: ShowValueIndicator.onDrag,
    );

    return MaterialApp(
      title: 'HyperIsland',
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      locale: _ctrl.locale,
      theme: ThemeData(
        colorScheme: lightColorScheme,
        useMaterial3: true,
        switchTheme: commonSwitchTheme,
        sliderTheme: commonSliderTheme,
        appBarTheme: const AppBarTheme(centerTitle: false),
      ),
      darkTheme: ThemeData(
        colorScheme: darkColorScheme,
        useMaterial3: true,
        switchTheme: commonSwitchTheme,
        sliderTheme: commonSliderTheme,
        appBarTheme: const AppBarTheme(centerTitle: false),
      ),
      themeMode: _ctrl.themeMode,
      home: const MainPage(),
    );
  }
}
