import 'package:flutter/material.dart';
import '../controllers/blacklist_controller.dart';
import '../l10n/generated/app_localizations.dart';
import '../services/app_cache_service.dart';
import '../widgets/app_list_widgets.dart';

class BlacklistPage extends StatefulWidget {
  const BlacklistPage({super.key});

  @override
  State<BlacklistPage> createState() => _BlacklistPageState();
}

class _BlacklistPageState extends State<BlacklistPage> {
  late final BlacklistController _ctrl;
  final _searchCtrl = TextEditingController();
  final _searchFocus = FocusNode();

  void _clearSearch() {
    _searchCtrl.clear();
    _ctrl.setSearch('');
  }

  bool _handleBackPressed() {
    final keyboardVisible = MediaQuery.viewInsetsOf(context).bottom > 0;
    if (_searchFocus.hasFocus && keyboardVisible) {
      _searchFocus.unfocus();
      return true;
    }

    if (!_searchFocus.hasFocus && _searchCtrl.text.isNotEmpty) {
      _clearSearch();
      return true;
    }

    return false;
  }

  @override
  void initState() {
    super.initState();
    _ctrl = BlacklistController();
    _ctrl.addListener(() {
      if (mounted) setState(() {});
    });
  }

  @override
  void deactivate() {
    _searchFocus.unfocus();
    super.deactivate();
  }

  @override
  void dispose() {
    _ctrl.dispose();
    _searchCtrl.dispose();
    _searchFocus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
    final apps = _ctrl.filteredApps;
    final enabledCount = _ctrl.blacklistedPackages.length;

    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        final consumed = _handleBackPressed();
        if (!consumed && mounted) {
          Navigator.of(context).pop();
        }
      },
      child: Scaffold(
        backgroundColor: cs.surface,
        body: RefreshIndicator(
          onRefresh: _ctrl.refresh,
          edgeOffset: 300.0,
          child: CustomScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            slivers: [
              SliverAppBar(
                backgroundColor: cs.surface,
                centerTitle: false,
                pinned: true,
                title: Text(l10n.navBlacklist),
                actions: [
                  IconButton(
                    icon: const Icon(Icons.videogame_asset),
                    tooltip: l10n.presetGamesTitle,
                    onPressed: _ctrl.loading
                        ? null
                        : () async {
                            final count = await _ctrl.applyGamePreset();
                            if (context.mounted) {
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(
                                  content: Text(l10n.presetGamesSuccess(count)),
                                ),
                              );
                            }
                          },
                  ),
                  AppBarOverflowMenuButton(
                    onSelected: (value) => handleAppListOverflowMenuSelection(
                      value: value,
                      onToggleSystemApps: () {
                        _ctrl.setShowSystemApps(!_ctrl.showSystemApps);
                      },
                      onRefresh: _ctrl.refresh,
                      onEnableAll: _ctrl.enableAll,
                      onDisableAll: _ctrl.disableAll,
                    ),
                    itemBuilder: (ctx) {
                      final ml = AppLocalizations.of(ctx)!;
                      return buildAppListOverflowMenuItems(
                        context: ctx,
                        showSystemApps: _ctrl.showSystemApps,
                        showSystemAppsLabel: ml.showSystemApps,
                        refreshLabel: ml.refreshList,
                        enableAllLabel: ml.enableAll,
                        disableAllLabel: ml.disableAll,
                      );
                    },
                  ),
                ],
              ),

              SliverToBoxAdapter(
                child: Container(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                  child: AppListSearchHeader(
                    countText: _ctrl.showSystemApps
                        ? l10n.blacklistedAppsCountWithSystem(enabledCount)
                        : l10n.blacklistedAppsCount(enabledCount),
                    searchController: _searchCtrl,
                    searchFocusNode: _searchFocus,
                    hintText: l10n.searchApps,
                    onChanged: _ctrl.setSearch,
                    onClear: _clearSearch,
                  ),
                ),
              ),

              if (_ctrl.loading)
                const SliverFillRemaining(
                  child: Center(child: CircularProgressIndicator()),
                )
              else if (apps.isEmpty)
                SliverFillRemaining(
                  child: Center(
                    child: Text(
                      _searchCtrl.text.isEmpty
                          ? l10n.noAppsFound
                          : l10n.noMatchingApps,
                      style: TextStyle(color: cs.onSurfaceVariant),
                      textAlign: TextAlign.center,
                    ),
                  ),
                )
              else
                SliverPadding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 32),
                  sliver: SliverList.separated(
                    itemCount: apps.length,
                    addAutomaticKeepAlives: false,
                    separatorBuilder: (_, __) => const SizedBox(height: 2),
                    itemBuilder: (context, index) {
                      final app = apps[index];
                      final pkg = app.packageName;
                      return _AppTile(
                        key: ValueKey(pkg),
                        app: app,
                        enabled: _ctrl.blacklistedPackages.contains(pkg),
                        onChanged: (v) => _ctrl.setBlacklisted(pkg, v ?? false),
                        onTap: () {
                          _ctrl.setBlacklisted(
                            pkg,
                            !_ctrl.blacklistedPackages.contains(pkg),
                          );
                        },
                        isFirst: index == 0,
                        isLast: index == apps.length - 1,
                      );
                    },
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

class _AppTile extends StatelessWidget {
  const _AppTile({
    super.key,
    required this.app,
    required this.enabled,
    required this.onChanged,
    required this.onTap,
    required this.isFirst,
    required this.isLast,
  });

  final AppInfo app;
  final bool enabled;
  final ValueChanged<bool?> onChanged;
  final VoidCallback onTap;
  final bool isFirst;
  final bool isLast;

  @override
  Widget build(BuildContext context) {
    return AppListItemFrame(
      app: app,
      onTap: onTap,
      isFirst: isFirst,
      isLast: isLast,
      trailing: Checkbox(value: enabled, onChanged: onChanged),
    );
  }
}
