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

    return Scaffold(
      backgroundColor: cs.surface,
      body: RefreshIndicator(
        onRefresh: _ctrl.refresh,
        edgeOffset: 300.0,
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverAppBar.large(
              backgroundColor: cs.surface,
              centerTitle: false,
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

            // 说明 + 搜索栏
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
              sliver: SliverToBoxAdapter(
                child: AppListSearchHeader(
                  countText: _ctrl.showSystemApps
                      ? l10n.blacklistedAppsCountWithSystem(enabledCount)
                      : l10n.blacklistedAppsCount(enabledCount),
                  searchController: _searchCtrl,
                  searchFocusNode: _searchFocus,
                  hintText: l10n.searchApps,
                  onChanged: _ctrl.setSearch,
                  onClear: () {
                    _searchCtrl.clear();
                    _ctrl.setSearch('');
                  },
                ),
              ),
            ),

            // 内容区
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
                sliver: SliverList(
                  delegate: SliverChildBuilderDelegate((context, index) {
                    final app = apps[index];
                    final pkg = app.packageName;
                    return _AppTile(
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
                  }, childCount: apps.length),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _AppTile extends StatelessWidget {
  const _AppTile({
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
      trailing: Checkbox(value: enabled, onChanged: onChanged),
      isFirst: isFirst,
      isLast: isLast,
    );
  }
}
