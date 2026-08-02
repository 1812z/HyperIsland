import 'package:flutter/material.dart';
import 'package:flutter_miuix/miuix.dart';
import 'package:flutter/services.dart';
import '../l10n/generated/app_localizations.dart';
import 'home_page.dart';
import 'whitelist_page.dart';
import 'settings_page.dart';

class MainPage extends StatefulWidget {
  const MainPage({super.key});

  @override
  State<MainPage> createState() => _MainPageState();
}

class _MainPageState extends State<MainPage> {
  int _currentIndex = 0;
  // WhitelistPage 懒创建：首次点击「应用」Tab 时才初始化，避免启动时触发权限申请
  WhitelistPage? _whitelistPage;
  final _whitelistKey = GlobalKey<WhitelistPageState>();

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    void selectTab(int index) {
      FocusScope.of(context).unfocus();
      if (index == 1 && _whitelistPage == null) {
        _whitelistPage = WhitelistPage(key: _whitelistKey);
      }
      setState(() => _currentIndex = index);
    }

    final navBar = MiuixNavigationBar(
      children: [
        MiuixNavigationBarItem(
          selected: _currentIndex == 0,
          onPressed: () => selectTab(0),
          icon: const Icon(Icons.home_outlined),
          label: l10n.navHome,
        ),
        MiuixNavigationBarItem(
          selected: _currentIndex == 1,
          onPressed: () => selectTab(1),
          icon: const Icon(Icons.apps_outlined),
          label: l10n.navApps,
        ),
        MiuixNavigationBarItem(
          selected: _currentIndex == 2,
          onPressed: () => selectTab(2),
          icon: const Icon(Icons.settings_outlined),
          label: l10n.navSettings,
        ),
      ],
    );

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
      child: MiuixScaffold(
        bottomBar: navBar,
        contentWindowInsets: EdgeInsets.zero,
        content: (padding) => Padding(
          padding: EdgeInsets.only(bottom: padding.bottom),
          child: IndexedStack(
            index: _currentIndex,
            children: [
              const HomePage(),
              _whitelistPage ??= WhitelistPage(key: _whitelistKey),
              const SettingsPage(),
            ],
          ),
        ),
      ),
    );
  }
}
