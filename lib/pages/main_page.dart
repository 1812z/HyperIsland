import 'package:flutter/material.dart';
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

class _MainPageState extends State<MainPage>
    with SingleTickerProviderStateMixin {
  int _currentIndex = 0;
  int? _previousIndex;
  // WhitelistPage 懒创建：首次点击「应用」Tab 时才初始化，避免启动时触发权限申请
  WhitelistPage? _whitelistPage;
  final _whitelistKey = GlobalKey<WhitelistPageState>();
  late final AnimationController _tabSwitchController;

  bool get _isTabSwitching => _previousIndex != null;

  @override
  void initState() {
    super.initState();
    _tabSwitchController =
        AnimationController(
          vsync: this,
          duration: const Duration(milliseconds: 260),
        )..addStatusListener((status) {
          if (status == AnimationStatus.completed && mounted) {
            setState(() => _previousIndex = null);
          }
        });
  }

  @override
  void dispose() {
    _tabSwitchController.dispose();
    super.dispose();
  }

  List<Widget> _buildTabs() => [
    const HomePage(),
    _whitelistPage ??= WhitelistPage(key: _whitelistKey),
    const SettingsPage(),
  ];

  Widget _buildAnimatedBody() {
    final tabs = _buildTabs();
    final previousIndex = _previousIndex;
    if (previousIndex == null) {
      return tabs[_currentIndex];
    }

    final progress = Curves.easeOutCubic.transform(_tabSwitchController.value);
    final direction = _currentIndex > previousIndex ? 1.0 : -1.0;

    return Stack(
      children: List.generate(tabs.length, (index) {
        final isCurrent = index == _currentIndex;
        final isPrevious = index == previousIndex;
        final isVisible = isCurrent || isPrevious;
        final enterX = 0.08 * direction * (1 - progress);
        final exitX = -0.04 * direction * progress;

        return Offstage(
          offstage: !isVisible,
          child: IgnorePointer(
            ignoring: true,
            child: AnimatedOpacity(
              duration: Duration.zero,
              opacity: isCurrent ? (0.75 + (0.25 * progress)) : (1 - progress),
              child: FractionalTranslation(
                translation: Offset(isCurrent ? enterX : exitX, 0),
                child: tabs[index],
              ),
            ),
          ),
        );
      }),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        if (_isTabSwitching) return;
        // 先尝试让当前 tab 的子页面消费返回事件
        if (_currentIndex == 1) {
          final state = _whitelistKey.currentState;
          if (state != null && state.handleBackPressed()) return;
        }
        // 没有子页面消费，退出 App
        SystemNavigator.pop();
      },
      child: Scaffold(
        body: AnimatedBuilder(
          animation: _tabSwitchController,
          builder: (_, __) => _buildAnimatedBody(),
        ),
        bottomNavigationBar: NavigationBar(
          selectedIndex: _currentIndex,
          onDestinationSelected: (index) {
            if (index == _currentIndex || _isTabSwitching) return;
            FocusScope.of(context).unfocus();
            if (index == 1 && _whitelistPage == null) {
              _whitelistPage = WhitelistPage(key: _whitelistKey);
            }
            setState(() {
              _previousIndex = _currentIndex;
              _currentIndex = index;
            });
            _tabSwitchController.forward(from: 0);
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
        ),
      ),
    );
  }
}
