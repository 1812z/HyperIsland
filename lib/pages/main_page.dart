import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../controllers/settings_controller.dart';
import '../l10n/generated/app_localizations.dart';
import 'home_page.dart';
import 'whitelist_page.dart';
import 'settings_page.dart';

class MainPage extends StatefulWidget {
  const MainPage({super.key, this.initialIndex = 0});

  final int initialIndex;

  @override
  State<MainPage> createState() => _MainPageState();
}

class _MainPageState extends State<MainPage> {
  late int _currentIndex;
  final _ctrl = SettingsController.instance;
  // WhitelistPage 懒创建：首次点击「应用」Tab 时才初始化，避免启动时触发权限申请
  WhitelistPage? _whitelistPage;
  final _whitelistKey = GlobalKey<WhitelistPageState>();

  @override
  void initState() {
    super.initState();
    _currentIndex = widget.initialIndex.clamp(0, 2);
    _ctrl.addListener(_onChanged);
  }

  @override
  void dispose() {
    _ctrl.removeListener(_onChanged);
    super.dispose();
  }

  void _onChanged() {
    if (!mounted) return;
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final blurBars = _ctrl.blurBars;

    Widget navBar = NavigationBar(
      selectedIndex: _currentIndex,
      onDestinationSelected: (index) {
        FocusScope.of(context).unfocus();
        if (index == 1 && _whitelistPage == null) {
          _whitelistPage = WhitelistPage(key: _whitelistKey);
        }
        setState(() => _currentIndex = index);
      },
      destinations: [
        NavigationDestination(
          icon: const Icon(Icons.home_outlined),
          selectedIcon: const Icon(Icons.home),
          label: l10n.navHome,
        ),
        NavigationDestination(
          icon: const Icon(Icons.apps_outlined),
          selectedIcon: const Icon(Icons.apps),
          label: l10n.navApps,
        ),
        NavigationDestination(
          icon: const Icon(Icons.settings_outlined),
          selectedIcon: const Icon(Icons.settings),
          label: l10n.navSettings,
        ),
      ],
    );

    if (blurBars) {
      navBar = ClipRect(
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
          child: navBar,
        ),
      );
    }

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        // 先尝试让当前 tab 的子页面消费返回事件
        if (_currentIndex == 1) {
          final state = _whitelistKey.currentState;
          if (state != null && state.handleBackPressed()) return;
        }
        // 没有子页面消费，退出 App
        SystemNavigator.pop();
      },
      child: Scaffold(
        extendBody: blurBars,
        body: IndexedStack(
          index: _currentIndex,
          children: [
            const HomePage(),
            _whitelistPage ??= WhitelistPage(key: _whitelistKey),
            const SettingsPage(),
          ],
        ),
        bottomNavigationBar: navBar,
      ),
    );
  }
}
