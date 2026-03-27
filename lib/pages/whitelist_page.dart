import 'package:flutter/material.dart';
import '../controllers/whitelist_controller.dart';
import '../l10n/app_localizations.dart';
import '../widgets/batch_channel_settings_sheet.dart';
import 'app_channels_page.dart';

class WhitelistPage extends StatefulWidget {
  const WhitelistPage({super.key});

  @override
  State<WhitelistPage> createState() => _WhitelistPageState();
}

class _WhitelistPageState extends State<WhitelistPage> {
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
      _selectedPackages.addAll(
          _ctrl.filteredApps.map((a) => a.packageName));
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

  Future<void> _enableSelected() async {
    if (_selectedPackages.isEmpty) return;
    await _ctrl.setEnabledBatch(_selectedPackages.toList(), true);
  }

  Future<void> _disableSelected() async {
    if (_selectedPackages.isEmpty) return;
    await _ctrl.setEnabledBatch(_selectedPackages.toList(), false);
  }

  /// 对已选应用的已启用渠道批量应用配置。
  Future<void> _batchApplySelected() async {
    if (_selectedPackages.isEmpty) return;
    if (!mounted) return;

    final l10n            = AppLocalizations.of(context)!;
    final templateLabels  = _ctrl.getTemplates(l10n);
    final rendererLabels  = _ctrl.getRenderers(l10n);
    final selected = _selectedPackages.toList();
    final result = await BatchChannelSettingsSheet.show(
      context,
      mode: BatchChannelMode(scope: GlobalScope(
        subtitle: l10n.applyToSelectedAppsChannels(selected.length),
      )),
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
        SnackBar(content: Text(AppLocalizations.of(context)!.batchApplied(selected.length))),
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
    final allSelected = apps.isNotEmpty &&
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
                      icon: Icon(allSelected
                          ? Icons.deselect
                          : Icons.select_all),
                      tooltip: allSelected ? l10n.deselectAll : l10n.selectAll,
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
                    PopupMenuButton<String>(
                      icon: const Icon(Icons.more_vert),
                      onSelected: (value) async {
                        switch (value) {
                          case 'select_enabled':
                            _selectEnabled();
                          case 'enable':
                            await _enableSelected();
                          case 'disable':
                            await _disableSelected();
                        }
                      },
                      itemBuilder: (ctx) {
                        final ml = AppLocalizations.of(ctx)!;
                        return [
                          PopupMenuItem(
                            value: 'select_enabled',
                            child: Text(ml.selectEnabledApps),
                          ),
                          const PopupMenuDivider(),
                          PopupMenuItem(
                            value: 'enable',
                            enabled: _selectedPackages.isNotEmpty,
                            child: Text(ml.batchEnable),
                          ),
                          PopupMenuItem(
                            value: 'disable',
                            enabled: _selectedPackages.isNotEmpty,
                            child: Text(ml.batchDisable),
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
          ),

          // 说明 + 搜索栏
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            sliver: SliverToBoxAdapter(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _ctrl.showSystemApps
                        ? l10n.enabledAppsCountWithSystem(enabledCount)
                        : l10n.enabledAppsCount(enabledCount),
                    style: Theme.of(context)
                        .textTheme
                        .bodyMedium
                        ?.copyWith(color: cs.onSurfaceVariant),
                  ),
                  const SizedBox(height: 12),
                  SearchBar(
                    controller: _searchCtrl,
                    focusNode: _searchFocus,
                    hintText: l10n.searchApps,
                    leading: const Icon(Icons.search),
                    trailing: [
                      if (_searchCtrl.text.isNotEmpty)
                        IconButton(
                          icon: const Icon(Icons.clear),
                          onPressed: () {
                            _searchCtrl.clear();
                            _ctrl.setSearch('');
                          },
                        ),
                    ],
                    onChanged: _ctrl.setSearch,
                    onSubmitted: (_) => _searchFocus.unfocus(),
                    padding: const WidgetStatePropertyAll(
                      EdgeInsets.symmetric(horizontal: 16),
                    ),
                  ),
                ],
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
                  _searchCtrl.text.isEmpty ? l10n.noAppsFound : l10n.noMatchingApps,
                  style: TextStyle(color: cs.onSurfaceVariant),
                  textAlign: TextAlign.center,
                ),
              ),
            )
          else
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 32),
              sliver: SliverList(
                delegate: SliverChildBuilderDelegate(
                  (context, index) {
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
                                    appEnabled:
                                        _ctrl.enabledPackages.contains(pkg),
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
                  },
                  childCount: apps.length,
                ),
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

    final radius = BorderRadius.vertical(
      top: isFirst ? const Radius.circular(16) : Radius.zero,
      bottom: isLast ? const Radius.circular(16) : Radius.zero,
    );

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Material(
          color: isSelected
              ? cs.primaryContainer
              : cs.surfaceContainerHighest,
          borderRadius: radius,
          child: InkWell(
            borderRadius: radius,
            onTap: onTap,
            onLongPress: onLongPress,
            child: Padding(
              padding:
                  const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              child: Row(
                children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(10),
                    child: Image.memory(
                      app.icon,
                      width: 44,
                      height: 44,
                      fit: BoxFit.cover,
                      gaplessPlayback: true,
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
                          style: Theme.of(context)
                              .textTheme
                              .bodySmall
                              ?.copyWith(color: cs.onSurfaceVariant),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),
                  if (selectionMode)
                    Checkbox(
                      value: isSelected,
                      onChanged: (_) => onTap(),
                    )
                  else ...[
                    Switch(value: enabled, onChanged: onChanged),
                    const SizedBox(width: 4),
                    Icon(
                      Icons.chevron_right,
                      color: cs.onSurfaceVariant,
                      size: 20,
                    ),
                  ],
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
    final cs   = Theme.of(context).colorScheme;
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
