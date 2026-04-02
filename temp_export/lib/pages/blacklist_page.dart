import 'dart:typed_data';
import 'package:flutter/material.dart';
import '../controllers/blacklist_controller.dart';
import '../l10n/generated/app_localizations.dart';

class BlacklistPage extends StatefulWidget {
  const BlacklistPage({super.key});

  @override
  State<BlacklistPage> createState() => _BlacklistPageState();
}

class _BlacklistPageState extends State<BlacklistPage> {
  late final BlacklistController _ctrl;
  final _searchCtrl = TextEditingController();
  final _searchFocus = FocusNode();

  bool _getKeyboardVisible(BuildContext context) =>
      MediaQuery.viewInsetsOf(context).bottom > 0;

  bool _shouldHandleSearchBack(BuildContext context) =>
      _searchCtrl.text.isNotEmpty ||
      _searchFocus.hasFocus ||
      _getKeyboardVisible(context);

  void _clearSearch() {
    _searchCtrl.clear();
    _ctrl.setSearch('');
  }

  void _handleSearchBack(BuildContext context) {
    if (_searchCtrl.text.isEmpty) {
      if (_searchFocus.hasFocus) _searchFocus.unfocus();
      return;
    }

    if (_searchFocus.hasFocus || _getKeyboardVisible(context)) {
      _searchFocus.unfocus();
      return;
    }

    _clearSearch();
  }

  @override
  void initState() {
    super.initState();
    _ctrl = BlacklistController();
    _ctrl.addListener(() {
      if (mounted) setState(() {});
    });
    _searchCtrl.addListener(() {
      if (mounted) setState(() {});
    });
    _searchFocus.addListener(() {
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
      canPop: !_shouldHandleSearchBack(context),
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _handleSearchBack(context);
      },
      child: Scaffold(
        backgroundColor: cs.surface,
        body: RefreshIndicator(
          onRefresh: _ctrl.refresh,
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
                  PopupMenuButton<String>(
                    icon: const Icon(Icons.more_vert),
                    onSelected: (value) async {
                      switch (value) {
                        case 'toggle_system':
                          _ctrl.setShowSystemApps(!_ctrl.showSystemApps);
                        case 'refresh':
                          await _ctrl.refresh();
                        case 'enable_all':
                          await _ctrl.enableAll();
                        case 'disable_all':
                          await _ctrl.disableAll();
                      }
                    },
                    itemBuilder: (ctx) {
                      final ml = AppLocalizations.of(ctx)!;
                      return [
                        CheckedPopupMenuItem<String>(
                          value: 'toggle_system',
                          checked: _ctrl.showSystemApps,
                          child: Text(ml.showSystemApps),
                        ),
                        const PopupMenuDivider(),
                        PopupMenuItem<String>(
                          value: 'refresh',
                          child: Text(ml.refreshList),
                        ),
                        const PopupMenuDivider(),
                        PopupMenuItem<String>(
                          value: 'enable_all',
                          child: Text(ml.enableAll),
                        ),
                        PopupMenuItem<String>(
                          value: 'disable_all',
                          child: Text(ml.disableAll),
                        ),
                      ];
                    },
                  ),
                ],
                bottom: PreferredSize(
                  preferredSize: const Size.fromHeight(68),
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                    child: SearchBar(
                      controller: _searchCtrl,
                      focusNode: _searchFocus,
                      hintText: l10n.searchApps,
                      leading: const Icon(Icons.search),
                      trailing: [
                        if (_searchCtrl.text.isNotEmpty)
                          IconButton(
                            icon: const Icon(Icons.clear),
                            onPressed: _clearSearch,
                          ),
                      ],
                      onChanged: _ctrl.setSearch,
                      onSubmitted: (_) => _searchFocus.unfocus(),
                      padding: const WidgetStatePropertyAll(
                        EdgeInsets.symmetric(horizontal: 16),
                      ),
                      elevation: const WidgetStatePropertyAll(0),
                      backgroundColor: WidgetStatePropertyAll(
                        cs.surfaceContainerHighest,
                      ),
                      constraints: const BoxConstraints(
                        minHeight: 48,
                        maxHeight: 48,
                      ),
                    ),
                  ),
                ),
              ),

              // 说明 + 搜索栏
              SliverPadding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                sliver: SliverToBoxAdapter(
                  child: Text(
                    _ctrl.showSystemApps
                        ? l10n.blacklistedAppsCountWithSystem(enabledCount)
                        : l10n.blacklistedAppsCount(enabledCount),
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: cs.onSurfaceVariant,
                    ),
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
                        controller: _ctrl,
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
      ),
    );
  }
}

class _AppTile extends StatelessWidget {
  const _AppTile({
    required this.app,
    required this.controller,
    required this.enabled,
    required this.onChanged,
    required this.onTap,
    required this.isFirst,
    required this.isLast,
  });

  final AppInfo app;
  final BlacklistController controller;
  final bool enabled;
  final ValueChanged<bool?> onChanged;
  final VoidCallback onTap;
  final bool isFirst;
  final bool isLast;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    final radius = BorderRadius.vertical(
      top: isFirst ? const Radius.circular(16) : Radius.zero,
      bottom: isLast ? const Radius.circular(16) : Radius.zero,
    );

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Material(
          color: cs.surfaceContainerHighest,
          borderRadius: radius,
          child: InkWell(
            borderRadius: radius,
            onTap: onTap,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              child: Row(
                children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(10),
                    child: FutureBuilder<Uint8List?>(
                      future: controller.getAppIcon(app.packageName),
                      builder: (context, snapshot) {
                        return AnimatedSwitcher(
                          duration: const Duration(milliseconds: 200),
                          child:
                              (snapshot.connectionState ==
                                          ConnectionState.waiting &&
                                      !snapshot.hasData) ||
                                  snapshot.data == null
                              ? Container(
                                  key: const ValueKey('placeholder'),
                                  width: 44,
                                  height: 44,
                                  color: cs.surfaceContainerHighest,
                                  child: Icon(
                                    Icons.android,
                                    size: 24,
                                    color: cs.onSurfaceVariant.withValues(
                                      alpha: 0.5,
                                    ),
                                  ),
                                )
                              : Image.memory(
                                  snapshot.data!,
                                  key: const ValueKey('icon'),
                                  width: 44,
                                  height: 44,
                                  fit: BoxFit.cover,
                                  gaplessPlayback: true,
                                ),
                        );
                      },
                    ),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          app.appName,
                          style: Theme.of(context).textTheme.bodyLarge,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        const SizedBox(height: 2),
                        Text(
                          app.packageName,
                          style: Theme.of(context).textTheme.bodySmall
                              ?.copyWith(color: cs.onSurfaceVariant),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),
                  Checkbox(value: enabled, onChanged: onChanged),
                ],
              ),
            ),
          ),
        ),
        if (!isLast)
          Divider(
            height: 1,
            thickness: 1,
            indent: 74,
            color: cs.outlineVariant.withValues(alpha: 0.4),
          ),
      ],
    );
  }
}
