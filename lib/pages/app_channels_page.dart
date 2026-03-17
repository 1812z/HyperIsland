import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../controllers/whitelist_controller.dart';
import '../widgets/batch_channel_settings_sheet.dart';
import '../widgets/channel_settings_dialog.dart';

class AppChannelsPage extends StatefulWidget {
  final AppInfo app;
  final WhitelistController controller;

  /// 应用总开关状态：false 时渠道页全部显示为强制关闭的灰色状态。
  final bool appEnabled;

  const AppChannelsPage({
    super.key,
    required this.app,
    required this.controller,
    required this.appEnabled,
  });

  @override
  State<AppChannelsPage> createState() => _AppChannelsPageState();
}

class _AppChannelsPageState extends State<AppChannelsPage> {
  List<ChannelInfo>? _channels;
  Set<String> _enabledChannels = {};
  Map<String, String> _channelTemplates = {};
  Map<String, String> _templateLabels = {};   // id → 显示名称，从原生侧加载
  Map<String, Map<String, String>> _channelExtras = {};  // channelId → extra settings
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final pkg = widget.app.packageName;

    List<ChannelInfo> channels;
    bool rootError = false;
    try {
      channels = await widget.controller.getChannels(pkg);
    } on PlatformException catch (e) {
      channels = [];
      if (e.code == 'ROOT_REQUIRED') rootError = true;
    } catch (_) {
      channels = [];
    }

    final results = await Future.wait([
      widget.controller.getEnabledChannels(pkg),
      widget.controller.getTemplates(),
    ]);
    final enabled        = results[0] as Set<String>;
    final templateLabels = results[1] as Map<String, String>;
    final channelIds = channels.map((c) => c.id).toList();
    final channelTemplates = await widget.controller.getChannelTemplates(pkg, channelIds);
    final channelExtras    = await widget.controller.getChannelExtraSettings(pkg, channelIds);
    if (mounted) {
      setState(() {
        _channels         = channels;
        _enabledChannels  = enabled;
        _channelTemplates = channelTemplates;
        _templateLabels   = templateLabels;
        _channelExtras    = channelExtras;
        _loading          = false;
      });
      if (rootError) {
        WidgetsBinding.instance.addPostFrameCallback((_) => _showRootErrorDialog());
      }
    }
  }

  void _showRootErrorDialog() {
    if (!mounted) return;
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('无法读取通知渠道'),
        content: const Text('读取通知渠道需要 ROOT 权限。\n请确认已授予本应用 ROOT 权限后重试。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('确定'),
          ),
        ],
      ),
    );
  }

  /// 渠道是否生效：应用总开关关闭时强制返回 false。
  bool _isEnabled(String channelId) {
    if (!widget.appEnabled) return false;
    return _enabledChannels.isEmpty || _enabledChannels.contains(channelId);
  }

  Future<void> _toggle(String channelId, bool value) async {
    if (!widget.appEnabled) return;
    final all = _channels ?? [];
    Set<String> newSet;

    if (_enabledChannels.isEmpty) {
      if (!value) {
        newSet = all.map((c) => c.id).where((id) => id != channelId).toSet();
      } else {
        return;
      }
    } else {
      newSet = Set.from(_enabledChannels);
      if (value) {
        newSet.add(channelId);
      } else {
        newSet.remove(channelId);
      }
      if (all.isNotEmpty && newSet.length == all.length) newSet = {};
    }

    setState(() => _enabledChannels = newSet);
    await widget.controller.setEnabledChannels(widget.app.packageName, newSet);
  }

  Future<void> _setTemplate(String channelId, String template) async {
    setState(
        () => _channelTemplates = {..._channelTemplates, channelId: template});
    await widget.controller.setChannelTemplate(
        widget.app.packageName, channelId, template);
  }

  void _updateExtra(String channelId, String key, String value) {
    setState(() {
      _channelExtras = {
        ..._channelExtras,
        channelId: {...?_channelExtras[channelId], key: value},
      };
    });
  }

  Future<void> _setIconMode(String channelId, String value) async {
    _updateExtra(channelId, 'icon', value);
    await widget.controller.setChannelIconMode(widget.app.packageName, channelId, value);
  }

  Future<void> _setFocusIconMode(String channelId, String value) async {
    _updateExtra(channelId, 'focus_icon', value);
    await widget.controller.setChannelFocusIconMode(widget.app.packageName, channelId, value);
  }

  Future<void> _setFocusNotif(String channelId, String value) async {
    _updateExtra(channelId, 'focus', value);
    await widget.controller.setChannelFocusNotif(widget.app.packageName, channelId, value);
  }

  Future<void> _setFirstFloat(String channelId, String value) async {
    _updateExtra(channelId, 'first_float', value);
    await widget.controller.setChannelFirstFloat(widget.app.packageName, channelId, value);
  }

  Future<void> _setEnableFloat(String channelId, String value) async {
    _updateExtra(channelId, 'enable_float', value);
    await widget.controller.setChannelEnableFloat(widget.app.packageName, channelId, value);
  }

  Future<void> _setIslandTimeout(String channelId, String value) async {
    _updateExtra(channelId, 'timeout', value);
    await widget.controller.setChannelTimeout(widget.app.packageName, channelId, value);
  }

  // ── 批量操作 ────────────────────────────────────────────────────────────────

  /// 仅重新加载 prefs 侧的渠道配置，不重新调用原生接口。
  Future<void> _reloadSettings() async {
    final pkg = widget.app.packageName;
    final channelIds = (_channels ?? []).map((c) => c.id).toList();
    if (channelIds.isEmpty) return;
    final results = await Future.wait([
      widget.controller.getChannelTemplates(pkg, channelIds),
      widget.controller.getChannelExtraSettings(pkg, channelIds),
    ]);
    if (mounted) {
      setState(() {
        _channelTemplates = results[0] as Map<String, String>;
        _channelExtras    = results[1] as Map<String, Map<String, String>>;
      });
    }
  }

  /// 批量设置渠道配置。
  Future<void> _batchApply() async {
    final channels = _channels ?? [];
    if (channels.isEmpty) return;

    final enabledCount =
        _enabledChannels.isEmpty ? channels.length : _enabledChannels.length;

    final result = await BatchChannelSettingsSheet.show(
      context,
      scope: SingleAppScope(
        totalChannels: channels.length,
        enabledChannels: enabledCount,
      ),
      templateLabels: _templateLabels,
    );
    if (result == null || !mounted) return;

    final targetIds = result.onlyEnabled
        ? (_enabledChannels.isEmpty
            ? channels.map((c) => c.id).toList()
            : _enabledChannels.toList())
        : channels.map((c) => c.id).toList();

    await widget.controller.batchApplyChannelSettings(
      widget.app.packageName,
      targetIds,
      result.settings,
    );

    if (mounted) await _reloadSettings();
  }

  /// 清除渠道过滤，使全部渠道生效。
  Future<void> _enableAllChannels() async {
    setState(() => _enabledChannels = {});
    await widget.controller.setEnabledChannels(widget.app.packageName, {});
  }

  String _importanceLabel(int importance) => switch (importance) {
        0 => '无',
        1 => '极低',
        2 => '低',
        3 => '默认',
        4 || 5 => '高',
        _ => '未知',
      };

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final channels = _channels ?? [];
    final allEnabled = widget.appEnabled && _enabledChannels.isEmpty;

    return Scaffold(
      backgroundColor: cs.surface,
      body: CustomScrollView(
        slivers: [
          SliverAppBar.large(
            backgroundColor: cs.surface,
            centerTitle: false,
            title: Row(
              children: [
                ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: Image.memory(
                    widget.app.icon,
                    width: 32,
                    height: 32,
                    fit: BoxFit.cover,
                    gaplessPlayback: true,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    widget.app.appName,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
            actions: [
              if (!_loading && channels.isNotEmpty)
                PopupMenuButton<String>(
                  icon: const Icon(Icons.more_vert),
                  onSelected: (value) {
                    switch (value) {
                      case 'batch':      _batchApply();
                      case 'enable_all': _enableAllChannels();
                    }
                  },
                  itemBuilder: (_) => const [
                    PopupMenuItem(
                      value: 'batch',
                      child: Text('批量设置渠道配置'),
                    ),
                    PopupMenuDivider(),
                    PopupMenuItem(
                      value: 'enable_all',
                      child: Text('启用全部渠道'),
                    ),
                  ],
                ),
            ],
          ),

          // 应用总开关关闭时的提示横幅
          if (!widget.appEnabled)
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
              sliver: SliverToBoxAdapter(
                child: Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 14, vertical: 10),
                  decoration: BoxDecoration(
                    color: cs.errorContainer,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    children: [
                      Icon(Icons.block,
                          size: 18, color: cs.onErrorContainer),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          '应用总开关已关闭，以下渠道设置均不生效',
                          style: Theme.of(context).textTheme.bodySmall
                              ?.copyWith(color: cs.onErrorContainer),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),

          if (_loading)
            const SliverFillRemaining(
              child: Center(child: CircularProgressIndicator()),
            )
          else if (channels.isEmpty)
            SliverFillRemaining(
              child: Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.notifications_off_outlined,
                        size: 48, color: cs.onSurfaceVariant),
                    const SizedBox(height: 12),
                    Text('未找到通知渠道',
                        style: TextStyle(color: cs.onSurfaceVariant)),
                    const SizedBox(height: 4),
                    Text(
                      '该应用尚未创建通知渠道，或无法读取',
                      style: Theme.of(context)
                          .textTheme
                          .bodySmall
                          ?.copyWith(color: cs.onSurfaceVariant),
                    ),
                  ],
                ),
              ),
            )
          else ...[
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              sliver: SliverToBoxAdapter(
                child: Text(
                  widget.appEnabled
                      ? (allEnabled
                          ? '对全部 ${channels.length} 个渠道生效'
                          : '已选 ${_enabledChannels.length} / ${channels.length} 个渠道')
                      : '全部 ${channels.length} 个渠道（已停用）',
                  style: Theme.of(context)
                      .textTheme
                      .bodyMedium
                      ?.copyWith(color: cs.onSurfaceVariant),
                ),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 32),
              sliver: SliverList(
                delegate: SliverChildBuilderDelegate(
                  (context, index) {
                    final ch = channels[index];
                    final isFirst = index == 0;
                    final isLast = index == channels.length - 1;
                    final channelEnabled = _isEnabled(ch.id);
                    final template = _channelTemplates[ch.id] ?? kTemplateNotificationIsland;
                    final extras = _channelExtras[ch.id] ?? {};

                    return _ChannelTile(
                      channel: ch,
                      channelEnabled: channelEnabled,
                      appEnabled: widget.appEnabled,
                      template: template,
                      templateLabels: _templateLabels,
                      importanceLabel: _importanceLabel(ch.importance),
                      isFirst: isFirst,
                      isLast: isLast,
                      iconMode: extras['icon'] ?? kIconModeAuto,
                      focusIconMode: extras['focus_icon'] ?? kIconModeAuto,
                      focusNotif: extras['focus'] ?? kTriOptDefault,
                      firstFloat: extras['first_float'] ?? kTriOptDefault,
                      enableFloat: extras['enable_float'] ?? kTriOptDefault,
                      islandTimeout: extras['timeout'] ?? '5',
                      onToggle: (v) => _toggle(ch.id, v),
                      onTemplateChanged: (t) => _setTemplate(ch.id, t),
                      onIconModeChanged: (v) => _setIconMode(ch.id, v),
                      onFocusIconModeChanged: (v) => _setFocusIconMode(ch.id, v),
                      onFocusNotifChanged: (v) => _setFocusNotif(ch.id, v),
                      onFirstFloatChanged: (v) => _setFirstFloat(ch.id, v),
                      onEnableFloatChanged: (v) => _setEnableFloat(ch.id, v),
                      onIslandTimeoutChanged: (v) => _setIslandTimeout(ch.id, v),
                    );
                  },
                  childCount: channels.length,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

// ── 渠道列表项 ──────────────────────────────────────────────────────────────

class _ChannelTile extends StatelessWidget {
  const _ChannelTile({
    required this.channel,
    required this.channelEnabled,
    required this.appEnabled,
    required this.template,
    required this.templateLabels,
    required this.importanceLabel,
    required this.isFirst,
    required this.isLast,
    required this.iconMode,
    required this.focusIconMode,
    required this.focusNotif,
    required this.firstFloat,
    required this.enableFloat,
    required this.islandTimeout,
    required this.onToggle,
    required this.onTemplateChanged,
    required this.onIconModeChanged,
    required this.onFocusIconModeChanged,
    required this.onFocusNotifChanged,
    required this.onFirstFloatChanged,
    required this.onEnableFloatChanged,
    required this.onIslandTimeoutChanged,
  });

  final ChannelInfo channel;
  final bool channelEnabled;
  final bool appEnabled;
  final String template;
  final Map<String, String> templateLabels;
  final String importanceLabel;
  final bool isFirst;
  final bool isLast;
  final String iconMode;
  final String focusIconMode;
  final String focusNotif;
  final String firstFloat;
  final String enableFloat;
  final String islandTimeout;
  final ValueChanged<bool> onToggle;
  final ValueChanged<String> onTemplateChanged;
  final ValueChanged<String> onIconModeChanged;
  final ValueChanged<String> onFocusIconModeChanged;
  final ValueChanged<String> onFocusNotifChanged;
  final ValueChanged<String> onFirstFloatChanged;
  final ValueChanged<String> onEnableFloatChanged;
  final ValueChanged<String> onIslandTimeoutChanged;

  void _openSettings(BuildContext context) {
    showDialog(
      context: context,
      builder: (_) => ChannelSettingsDialog(
        channelName: channel.name,
        template: template,
        templateLabels: templateLabels,
        iconMode: iconMode,
        focusIconMode: focusIconMode,
        focusNotif: focusNotif,
        firstFloat: firstFloat,
        enableFloat: enableFloat,
        islandTimeout: islandTimeout,
        onTemplateChanged: onTemplateChanged,
        onIconModeChanged: onIconModeChanged,
        onFocusIconModeChanged: onFocusIconModeChanged,
        onFocusNotifChanged: onFocusNotifChanged,
        onFirstFloatChanged: onFirstFloatChanged,
        onEnableFloatChanged: onEnableFloatChanged,
        onIslandTimeoutChanged: onIslandTimeoutChanged,
      ),
    );
  }

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
            onTap: appEnabled ? () => onToggle(!channelEnabled) : null,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 4, 12),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          channel.name,
                          style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                                color: appEnabled
                                    ? null
                                    : cs.onSurface.withValues(alpha: 0.38),
                              ),
                        ),
                        if (channel.description.isNotEmpty) ...[
                          const SizedBox(height: 2),
                          Text(
                            channel.description,
                            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                  color: appEnabled
                                      ? cs.onSurfaceVariant
                                      : cs.onSurface.withValues(alpha: 0.28),
                                ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ],
                        const SizedBox(height: 2),
                        Text(
                          '重要性：$importanceLabel  ·  ${channel.id}',
                          style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                color: appEnabled
                                    ? cs.onSurfaceVariant.withValues(alpha: 0.7)
                                    : cs.onSurface.withValues(alpha: 0.22),
                              ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    icon: Icon(
                      Icons.settings_outlined,
                      size: 22,
                      color: appEnabled && channelEnabled
                          ? cs.onSurfaceVariant
                          : cs.onSurface.withValues(alpha: 0.28),
                    ),
                    onPressed: appEnabled && channelEnabled
                        ? () => _openSettings(context)
                        : null,
                    tooltip: '渠道设置',
                  ),
                  Switch(
                    value: channelEnabled,
                    onChanged: appEnabled ? onToggle : null,
                  ),
                ],
              ),
            ),
          ),
        ),
        if (!isLast)
          Divider(
            height: 1,
            thickness: 1,
            indent: 16,
            color: cs.outlineVariant.withValues(alpha: 0.4),
          ),
      ],
    );
  }
}