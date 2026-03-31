import 'package:flutter/material.dart';
import '../controllers/whitelist_controller.dart';
import '../l10n/generated/app_localizations.dart';
import '../widgets/batch_channel_settings_sheet.dart';
import '../widgets/app_list_widgets.dart';
import 'app_channels_page.dart';
import '../services/app_cache_service.dart';

class WhitelistPage extends StatefulWidget {
  const WhitelistPage({super.key});

  @override
  State<WhitelistPage> createState() => _WhitelistPageState();
}

class _WhitelistPageState extends State<WhitelistPage> {
  static const String _selectEnabledAction = 'select_enabled';
  static const String _enableAction = 'enable';
  static const String _disableAction = 'disable';

  late final WhitelistController _ctrl;
  final _searchCtrl = TextEditingController();
  final _searchFocus = FocusNode();
  final Set<String> _selectedPackages = {};
  bool _inSelectionMode = false;

  @override
  void initState() {
    super.initState();
    _ctrl = WhitelistController();
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

  bool get _selectionMode => _inSelectionMode;

  void _enterSelectionMode([String? pkg]) {
    setState(() {
      _inSelectionMode = true;
      if (pkg != null) _selectedPackages.add(pkg);
    });
  }

  void _toggleSelection(String pkg) {
    setState(() {
      if (_selectedPackages.contains(pkg)) {
        _selectedPackages.remove(pkg);
      } else {
        _selectedPackages.add(pkg);
      }
    });
  }

  void _selectAll() {
    setState(() {
      _selectedPackages.addAll(_ctrl.filteredApps.map((a) => a.packageName));
    });
  }

  void _deselectAll() => setState(() => _selectedPackages.clear());

  void _selectEnabled() {
    setState(() {
      _selectedPackages.addAll(
        _ctrl.filteredApps
            .where((a) => _ctrl.enabledPackages.contains(a.packageName))
            .map((a) => a.packageName),
      );
    });
  }

  void _clearSelection() => setState(() {
    _selectedPackages.clear();
    _inSelectionMode = false;
  });

  Future<void> _setSelectedEnabled(bool enabled) async {
    if (_selectedPackages.isEmpty) return;
    await _ctrl.setEnabledBatch(_selectedPackages.toList(), enabled);
  }

  /// 对已选应用的已启用渠道批量应用配置。
  Future<void> _batchApplySelected() async {
    if (_selectedPackages.isEmpty) return;
    if (!mounted) return;

    final l10n = AppLocalizations.of(context)!;
    final templateLabels = _ctrl.getTemplates(l10n);
    final rendererLabels = _ctrl.getRenderers(l10n);
    final selected = _selectedPackages.toList();
    final result = await BatchChannelSettingsSheet.show(
      context,
      mode: BatchChannelMode(
        scope: GlobalScope(
          subtitle: l10n.applyToSelectedAppsChannels(selected.length),
        ),
      ),
      templateLabels: templateLabels,
      rendererLabels: rendererLabels,
    );
    if (result == null || !mounted) return;

    final doneNotifier = ValueNotifier(0);
    showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (_) => _GlobalBatchProgressDialog(
        total: selected.length,
        doneNotifier: doneNotifier,
      ),
    );

    for (var i = 0; i < selected.length; i++) {
      final pkg = selected[i];
      try {
        final channels = await _ctrl.getChannels(pkg);
        final enabledChannels = await _ctrl.getEnabledChannels(pkg);
        final ids = enabledChannels.isEmpty
            ? channels.map((c) => c.id).toList()
            : enabledChannels.toList();
        if (ids.isNotEmpty) {
          await _ctrl.batchApplyChannelSettings(pkg, ids, result.settings);
        }
      } catch (_) {}
      doneNotifier.value = i + 1;
    }

    doneNotifier.dispose();
    if (mounted) Navigator.pop(context);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            AppLocalizations.of(context)!.batchApplied(selected.length),
          ),
        ),
      );
      _clearSelection();
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
    final apps = _ctrl.filteredApps;
    final enabledCount = _ctrl.enabledPackages.length;
    final allSelected =
        apps.isNotEmpty &&
        apps.every((a) => _selectedPackages.contains(a.packageName));

    return PopScope(
      canPop: !_selectionMode,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && _selectionMode) _clearSelection();
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
                automaticallyImplyLeading: !_selectionMode,
                leading: _selectionMode
                    ? IconButton(
                        icon: const Icon(Icons.close),
                        onPressed: _clearSelection,
                        tooltip: l10n.cancelSelection,
                      )
                    : null,
                title: _selectionMode
                    ? Text(l10n.selectedAppsCount(_selectedPackages.length))
                    : Text(l10n.appAdaptation),
                actions: _selectionMode
                    ? [
                        // 全选 / 全不选
                        IconButton(
                          icon: Icon(
                            allSelected ? Icons.deselect : Icons.select_all,
                          ),
                          tooltip: allSelected
                              ? l10n.deselectAll
                              : l10n.selectAll,
                          onPressed: allSelected ? _deselectAll : _selectAll,
                        ),
                        // 批量设置渠道配置
                        IconButton(
                          icon: const Icon(Icons.tune),
                          tooltip: l10n.batchChannelSettings,
                          onPressed: _selectedPackages.isNotEmpty
                              ? _batchApplySelected
                              : null,
                        ),
                        // 批量操作菜单
                        AppBarOverflowMenuButton(
                          onSelected: (value) async {
                            switch (value) {
                              case _selectEnabledAction:
                                _selectEnabled();
                              case _enableAction:
                                await _setSelectedEnabled(true);
                              case _disableAction:
                                await _setSelectedEnabled(false);
                            }
                          },
                          itemBuilder: (ctx) {
                            final ml = AppLocalizations.of(ctx)!;
                            return [
                              buildAppPopupMenuItem(
                                value: _selectEnabledAction,
                                icon: Icons.playlist_add_check_circle_rounded,
                                label: ml.selectEnabledApps,
                              ),
                              const PopupMenuDivider(height: 8),
                              buildAppPopupMenuItem(
                                value: _enableAction,
                                icon: Icons.done_all_rounded,
                                label: ml.batchEnable,
                                enabled: _selectedPackages.isNotEmpty,
                              ),
                              buildAppPopupMenuItem(
                                value: _disableAction,
                                icon: Icons.block_rounded,
                                label: ml.batchDisable,
                                enabled: _selectedPackages.isNotEmpty,
                              ),
                            ];
                          },
                        ),
                      ]
                    : [
                        // 进入多选模式
                        IconButton(
                          icon: const Icon(Icons.checklist_outlined),
                          tooltip: l10n.multiSelect,
                          onPressed: _ctrl.loading ? null : _enterSelectionMode,
                        ),
                        AppBarOverflowMenuButton(
                          onSelected: (value) =>
                              handleAppListOverflowMenuSelection(
                                value: value,
                                onToggleSystemApps: () {
                                  _ctrl.setShowSystemApps(
                                    !_ctrl.showSystemApps,
                                  );
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
                        ? l10n.enabledAppsCountWithSystem(enabledCount)
                        : l10n.enabledAppsCount(enabledCount),
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
                        enabled: _ctrl.enabledPackages.contains(pkg),
                        onChanged: _selectionMode
                            ? null
                            : (v) => _ctrl.setEnabled(pkg, v),
                        onTap: _selectionMode
                            ? () => _toggleSelection(pkg)
                            : () => Navigator.push(
                                context,
                                MaterialPageRoute(
                                  builder: (_) => AppChannelsPage(
                                    app: app,
                                    controller: _ctrl,
                                    appEnabled: _ctrl.enabledPackages.contains(
                                      pkg,
                                    ),
                                  ),
                                ),
                              ),
                        onLongPress: _selectionMode
                            ? null
                            : () => _enterSelectionMode(pkg),
                        isSelected: _selectedPackages.contains(pkg),
                        selectionMode: _selectionMode,
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
    required this.enabled,
    required this.onChanged,
    required this.onTap,
    required this.isFirst,
    required this.isLast,
    this.selectionMode = false,
    this.isSelected = false,
    this.onLongPress,
  });

  final AppInfo app;
  final bool enabled;
  final ValueChanged<bool>? onChanged;
  final VoidCallback onTap;
  final bool isFirst;
  final bool isLast;
  final bool selectionMode;
  final bool isSelected;
  final VoidCallback? onLongPress;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return AppListItemFrame(
      app: app,
      onTap: onTap,
      onLongPress: onLongPress,
      selected: isSelected,
      trailing: selectionMode
          ? Checkbox(value: isSelected, onChanged: (_) => onTap())
          : Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Switch(value: enabled, onChanged: onChanged),
                const SizedBox(width: 4),
                Icon(Icons.chevron_right, color: cs.onSurfaceVariant, size: 20),
              ],
            ),
      isFirst: isFirst,
      isLast: isLast,
    );
  }
}

// ── 全局批量进度对话框 ────────────────────────────────────────────────────────

class _GlobalBatchProgressDialog extends StatelessWidget {
  const _GlobalBatchProgressDialog({
    required this.total,
    required this.doneNotifier,
  });

  final int total;
  final ValueNotifier<int> doneNotifier;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    final l10n = AppLocalizations.of(context)!;

    return PopScope(
      canPop: false,
      child: AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        content: ValueListenableBuilder<int>(
          valueListenable: doneNotifier,
          builder: (_, done, __) {
            final progress = total > 0 ? done / total : 0.0;
            return Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(l10n.applyingConfig, style: text.titleMedium),
                const SizedBox(height: 16),
                LinearProgressIndicator(
                  value: progress,
                  borderRadius: BorderRadius.circular(4),
                  backgroundColor: cs.surfaceContainerHighest,
                ),
                const SizedBox(height: 10),
                Text(
                  l10n.progressApps(done, total),
                  style: text.bodySmall?.copyWith(color: cs.onSurfaceVariant),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}
