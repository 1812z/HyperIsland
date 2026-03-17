import 'package:flutter/material.dart';
import '../controllers/whitelist_controller.dart';
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

  /// 全局批量配置：对所有已启用应用的全部渠道应用配置。
  Future<void> _batchApplyAll() async {
    final enabledCount = _ctrl.enabledPackages.length;
    if (enabledCount == 0) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('尚未启用任何应用')),
      );
      return;
    }

    final templateLabels = await _ctrl.getTemplates();
    if (!mounted) return;

    final result = await BatchChannelSettingsSheet.show(
      context,
      templateLabels: templateLabels,
      subtitle: '将应用到全部 $enabledCount 个已启用应用的所有渠道',
      showScopeToggle: false,
    );
    if (result == null || !mounted) return;

    final doneNotifier = ValueNotifier(0);
    showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (_) => _GlobalBatchProgressDialog(
        total: enabledCount,
        doneNotifier: doneNotifier,
      ),
    );

    await _ctrl.batchApplyToAllEnabledApps(
      result.settings,
      onProgress: (done, _) => doneNotifier.value = done,
    );

    doneNotifier.dispose();
    if (mounted) Navigator.pop(context);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('已批量应用到 $enabledCount 个应用')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final apps = _ctrl.filteredApps;
    final enabledCount = _ctrl.enabledPackages.length;

    return Scaffold(
      backgroundColor: cs.surface,
      body: RefreshIndicator(
        onRefresh: _ctrl.refresh,
        child: CustomScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        slivers: [
          SliverAppBar.large(
            title: const Text('应用适配'),
            backgroundColor: cs.surface,
            centerTitle: false,
            actions: [
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
                    case 'batch_all':
                      await _batchApplyAll();
                  }
                },
                itemBuilder: (_) => [
                  CheckedPopupMenuItem<String>(
                    value: 'toggle_system',
                    checked: _ctrl.showSystemApps,
                    child: const Text('显示系统应用'),
                  ),
                  const PopupMenuDivider(),
                  const PopupMenuItem<String>(
                    value: 'refresh',
                    child: Text('刷新列表'),
                  ),
                  const PopupMenuDivider(),
                  const PopupMenuItem<String>(
                    value: 'enable_all',
                    child: Text('一键开启全部'),
                  ),
                  const PopupMenuItem<String>(
                    value: 'disable_all',
                    child: Text('一键关闭全部'),
                  ),
                  const PopupMenuDivider(),
                  const PopupMenuItem<String>(
                    value: 'batch_all',
                    child: Text('批量设置全部应用渠道'),
                  ),
                ],
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
                    '已启用 $enabledCount 个应用的超级岛'
                    '${_ctrl.showSystemApps ? '（含系统应用）' : ''}',
                    style: Theme.of(context)
                        .textTheme
                        .bodyMedium
                        ?.copyWith(color: cs.onSurfaceVariant),
                  ),
                  const SizedBox(height: 12),
                  SearchBar(
                    controller: _searchCtrl,
                    focusNode: _searchFocus,
                    hintText: '搜索应用名或包名',
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
                  _searchCtrl.text.isEmpty ? '没有找到已安装的应用\n请检查获取应用列表权限是否开启' : '没有匹配的应用',
                  style: TextStyle(color: cs.onSurfaceVariant),
                ),
              ),
            )
          else
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 32),
              sliver: SliverList(
                delegate: SliverChildBuilderDelegate(
                  (context, index) => _AppTile(
                    app: apps[index],
                    enabled:
                        _ctrl.enabledPackages.contains(apps[index].packageName),
                    onChanged: (v) => _ctrl.setEnabled(apps[index].packageName, v),
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => AppChannelsPage(
                          app: apps[index],
                          controller: _ctrl,
                          appEnabled: _ctrl.enabledPackages
                              .contains(apps[index].packageName),
                        ),
                      ),
                    ),
                    isFirst: index == 0,
                    isLast: index == apps.length - 1,
                  ),
                  childCount: apps.length,
                ),
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
  final ValueChanged<bool>? onChanged;
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
                  Switch(value: enabled, onChanged: onChanged),
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
                Text('正在应用配置…', style: text.titleMedium),
                const SizedBox(height: 16),
                LinearProgressIndicator(
                  value: progress,
                  borderRadius: BorderRadius.circular(4),
                  backgroundColor: cs.surfaceContainerHighest,
                ),
                const SizedBox(height: 10),
                Text(
                  '$done / $total 个应用',
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
