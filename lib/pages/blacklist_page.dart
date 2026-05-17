import 'dart:async';

import 'package:flutter/material.dart';
import '../controllers/blacklist_controller.dart';
import '../l10n/generated/app_localizations.dart';
import '../services/interaction_haptics.dart';
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

  static const _menuPresetGames = 'preset_games';
  static const _menuToggleSystemApps = 'toggle_system_apps';
  static const _menuRefresh = 'refresh';
  static const _menuResetDefaults = 'reset_defaults';

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

  Future<void> _handleMenuAction(String action) async {
    switch (action) {
      case _menuPresetGames:
        final count = await _ctrl.applyGamePreset();
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)!.presetGamesSuccess(count),
            ),
          ),
        );
        return;
      case _menuToggleSystemApps:
        _ctrl.setShowSystemApps(!_ctrl.showSystemApps);
        return;
      case _menuRefresh:
        await _ctrl.refresh();
        return;
      case _menuResetDefaults:
        final count = await _ctrl.resetToDefaults();
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              AppLocalizations.of(context)!.resetDefaultConfigSuccess(count),
            ),
          ),
        );
        return;
    }
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
    final exclusionMode = _ctrl.exclusionMode;

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
              SliverAppBar.large(
                backgroundColor: cs.surface,
                centerTitle: false,
                title: Text(l10n.filterRulesSection),
                actions: [
                  PopupMenuButton<String>(
                    icon: const Icon(Icons.more_vert),
                    tooltip: MaterialLocalizations.of(context).showMenuTooltip,
                    enabled: !_ctrl.loading,
                    onSelected: (value) {
                      unawaited(InteractionHaptics.button());
                      unawaited(_handleMenuAction(value));
                    },
                    itemBuilder: (context) => [
                      PopupMenuItem(
                        value: _menuPresetGames,
                        child: ListTile(
                          leading: const Icon(Icons.videogame_asset_outlined),
                          title: Text(l10n.presetGamesTitle),
                          contentPadding: EdgeInsets.zero,
                        ),
                      ),
                      PopupMenuItem(
                        value: _menuToggleSystemApps,
                        child: ListTile(
                          leading: Icon(
                            _ctrl.showSystemApps
                                ? Icons.phone_android
                                : Icons.phone_android_outlined,
                          ),
                          title: Text(
                            _ctrl.showSystemApps
                                ? l10n.hideSystemApps
                                : l10n.showSystemApps,
                          ),
                          contentPadding: EdgeInsets.zero,
                        ),
                      ),
                      PopupMenuItem(
                        value: _menuRefresh,
                        child: ListTile(
                          leading: const Icon(Icons.refresh),
                          title: Text(l10n.refreshList),
                          contentPadding: EdgeInsets.zero,
                        ),
                      ),
                      PopupMenuItem(
                        value: _menuResetDefaults,
                        child: ListTile(
                          leading: const Icon(Icons.restore_outlined),
                          title: Text(l10n.restoreDefaultConfig),
                          contentPadding: EdgeInsets.zero,
                        ),
                      ),
                    ],
                  ),
                ],
              ),

              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                  child: SegmentedButton<int>(
                    segments: [
                      ButtonSegment<int>(
                        value: 0,
                        icon: const Icon(Icons.rule_outlined),
                        label: Text(l10n.foregroundRulesTab),
                      ),
                      ButtonSegment<int>(
                        value: 1,
                        icon: const Icon(Icons.do_not_disturb_on_outlined),
                        label: Text(l10n.foregroundExclusionsTab),
                      ),
                    ],
                    selected: {exclusionMode ? 1 : 0},
                    onSelectionChanged: (selection) {
                      _ctrl.setExclusionMode(selection.first == 1);
                    },
                  ),
                ),
              ),

              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                  child: Text(
                    exclusionMode
                        ? l10n.foregroundExclusionsDescription
                        : l10n.foregroundRulesDescription,
                    style: Theme.of(
                      context,
                    ).textTheme.bodySmall?.copyWith(color: cs.onSurfaceVariant),
                  ),
                ),
              ),

              SliverToBoxAdapter(
                child: Container(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                  child: AppListSearchHeader(
                    countText: '',
                    showCountText: false,
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
                        exclusionMode: exclusionMode,
                        action: _ctrl.actionForPackage(pkg),
                        excluded: _ctrl.isForegroundExcluded(pkg),
                        onActionChanged: (v) => _ctrl.setSceneAction(pkg, v),
                        onExcludedChanged: (v) =>
                            _ctrl.setForegroundExcluded(pkg, v),
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
    required this.exclusionMode,
    required this.action,
    required this.excluded,
    required this.onActionChanged,
    required this.onExcludedChanged,
    required this.isFirst,
    required this.isLast,
  });

  final AppInfo app;
  final bool exclusionMode;
  final String action;
  final bool excluded;
  final ValueChanged<String> onActionChanged;
  final ValueChanged<bool> onExcludedChanged;
  final bool isFirst;
  final bool isLast;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final configured = action != kSceneActionDefault;
    final l10n = AppLocalizations.of(context)!;

    return AppListItemFrame(
      app: app,
      onTap: exclusionMode ? () => onExcludedChanged(!excluded) : () {},
      isFirst: isFirst,
      isLast: isLast,
      trailing: exclusionMode
          ? SizedBox(
              height: 48,
              child: Center(
                child: Switch(
                  value: excluded,
                  onChanged: InteractionHaptics.interceptToggle(
                    onExcludedChanged,
                  ),
                ),
              ),
            )
          : SizedBox(
              width: 112,
              child: Container(
                height: 40,
                padding: const EdgeInsets.only(left: 10, right: 4),
                decoration: BoxDecoration(
                  color: configured
                      ? cs.primaryContainer
                      : cs.surfaceContainerHigh,
                  border: Border.all(
                    color: configured
                        ? cs.primary.withValues(alpha: 0.24)
                        : Colors.transparent,
                  ),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: DropdownButtonHideUnderline(
                  child: DropdownButton<String>(
                    value: action,
                    isExpanded: true,
                    borderRadius: BorderRadius.circular(16),
                    dropdownColor: cs.surfaceContainerHigh,
                    iconSize: 20,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: configured ? cs.onPrimaryContainer : cs.onSurface,
                      fontWeight: configured
                          ? FontWeight.w600
                          : FontWeight.w400,
                    ),
                    selectedItemBuilder: (context) => [
                      Center(
                        child: Text(
                          l10n.sceneActionDefault,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Center(
                        child: Text(
                          l10n.sceneActionSmallOnly,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Center(
                        child: Text(
                          l10n.sceneActionExpand,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Center(
                        child: Text(
                          l10n.sceneActionSuppress,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                    onChanged: (value) {
                      if (value != null) onActionChanged(value);
                    },
                    items: [
                      DropdownMenuItem(
                        value: kSceneActionDefault,
                        child: Text(l10n.sceneActionDefault),
                      ),
                      DropdownMenuItem(
                        value: kSceneActionSmallOnly,
                        child: Text(l10n.sceneActionSmallOnly),
                      ),
                      DropdownMenuItem(
                        value: kSceneActionExpand,
                        child: Text(l10n.sceneActionExpand),
                      ),
                      DropdownMenuItem(
                        value: kSceneActionSuppress,
                        child: Text(l10n.sceneActionSuppress),
                      ),
                    ],
                  ),
                ),
              ),
            ),
    );
  }
}
