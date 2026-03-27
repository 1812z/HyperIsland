import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../controllers/whitelist_controller.dart';
import '../l10n/app_localizations.dart';
import '../widgets/batch_channel_settings_sheet.dart';

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
  Map<String, String> _rendererLabels = {};   // id → 显示名称
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

    final enabled         = await widget.controller.getEnabledChannels(pkg);
    final l10nForLabels   = AppLocalizations.of(context)!;
    final templateLabels  = widget.controller.getTemplates(l10nForLabels);
    final rendererLabels  = widget.controller.getRenderers(l10nForLabels);
    final channelIds = channels.map((c) => c.id).toList();
    final channelTemplates = await widget.controller.getChannelTemplates(pkg, channelIds);
    final channelExtras    = await widget.controller.getChannelExtraSettings(pkg, channelIds);
    if (mounted) {
      setState(() {
        _channels         = channels;
        _enabledChannels  = enabled;
        _channelTemplates = channelTemplates;
        _templateLabels   = templateLabels;
        _rendererLabels   = rendererLabels;
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
    final l10n = AppLocalizations.of(context)!;
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(l10n.cannotReadChannels),
        content: Text(l10n.rootRequiredMessage),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text(l10n.ok),
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

  Future<void> _setPreserveSmallIcon(String channelId, String value) async {
    _updateExtra(channelId, 'preserve_small_icon', value);
    await widget.controller.setChannelPreserveSmallIcon(widget.app.packageName, channelId, value);
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

  Future<void> _setMarquee(String channelId, String value) async {
    _updateExtra(channelId, 'marquee', value);
    await widget.controller.setChannelMarquee(widget.app.packageName, channelId, value);
  }

  Future<void> _setRenderer(String channelId, String value) async {
    _updateExtra(channelId, 'renderer', value);
    await widget.controller.setChannelRenderer(widget.app.packageName, channelId, value);
  }

  Future<void> _applyChannelSettings(String channelId, Map<String, String?> settings) async {
    if (settings['template'] case final t?) await _setTemplate(channelId, t);
    if (settings['renderer'] case final v?) await _setRenderer(channelId, v);
    if (settings['icon'] case final v?) await _setIconMode(channelId, v);
    if (settings['focus_icon'] case final v?) await _setFocusIconMode(channelId, v);
    if (settings['focus'] case final v?) await _setFocusNotif(channelId, v);
    if (settings['preserve_small_icon'] case final v?) await _setPreserveSmallIcon(channelId, v);
    if (settings['first_float'] case final v?) await _setFirstFloat(channelId, v);
    if (settings['enable_float'] case final v?) await _setEnableFloat(channelId, v);
    if (settings['timeout'] case final v?) await _setIslandTimeout(channelId, v);
    if (settings['marquee'] case final v?) await _setMarquee(channelId, v);
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

  /// 批量设置渠道配置（仅对已启用渠道生效）。
  Future<void> _batchApply() async {
    final channels = _channels ?? [];
    if (channels.isEmpty) return;

    // 仅对已启用渠道操作；空集合表示全部渠道生效
    final enabledIds = _enabledChannels.isEmpty
        ? channels.map((c) => c.id).toList()
        : _enabledChannels.toList();

    if (!mounted) return;
    final l10n = AppLocalizations.of(context)!;

    final result = await BatchChannelSettingsSheet.show(
      context,
      mode: BatchChannelMode(scope: GlobalScope(
        subtitle: l10n.applyToEnabledChannels(enabledIds.length),
      )),
      templateLabels: _templateLabels,
      rendererLabels: _rendererLabels,
    );
    if (result == null || !mounted) return;

    await widget.controller.batchApplyChannelSettings(
      widget.app.packageName,
      enabledIds,
      result.settings,
    );

    if (mounted) await _reloadSettings();
  }

  /// 清除渠道过滤，使全部渠道生效。
  Future<void> _enableAllChannels() async {
    setState(() => _enabledChannels = {});
    await widget.controller.setEnabledChannels(widget.app.packageName, {});
  }

  String _importanceLabel(int importance, AppLocalizations l10n) => switch (importance) {
        0 => l10n.importanceNone,
        1 => l10n.importanceMin,
        2 => l10n.importanceLow,
        3 => l10n.importanceDefault,
        4 || 5 => l10n.importanceHigh,
        _ => l10n.importanceUnknown,
      };

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
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
                  itemBuilder: (ctx) {
                    final ml = AppLocalizations.of(ctx)!;
                    return [
                      PopupMenuItem(
                        value: 'batch',
                        child: Text(ml.batchChannelSettings),
                      ),
                      const PopupMenuDivider(),
                      PopupMenuItem(
                        value: 'enable_all',
                        child: Text(ml.enableAllChannels),
                      ),
                    ];
                  },
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
                          l10n.appDisabledBanner,
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
                    Text(l10n.noChannelsFound,
                        style: TextStyle(color: cs.onSurfaceVariant)),
                    const SizedBox(height: 4),
                    Text(
                      l10n.noChannelsFoundSubtitle,
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
                          ? l10n.allChannelsActive(channels.length)
                          : l10n.selectedChannels(_enabledChannels.length, channels.length))
                      : l10n.allChannelsDisabled(channels.length),
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
                      renderer: extras['renderer'] ?? kRendererImageTextWithButtons4,
                      rendererLabels: _rendererLabels,
                      importanceLabel: _importanceLabel(ch.importance, l10n),
                      isFirst: isFirst,
                      isLast: isLast,
                      iconMode: extras['icon'] ?? kIconModeAuto,
                      focusIconMode: extras['focus_icon'] ?? kIconModeAuto,
                      focusNotif: extras['focus'] ?? kTriOptDefault,
                      preserveSmallIcon: extras['preserve_small_icon'] ?? kTriOptDefault,
                      firstFloat: extras['first_float'] ?? kTriOptDefault,
                      enableFloat: extras['enable_float'] ?? kTriOptDefault,
                      islandTimeout: extras['timeout'] ?? '5',
                      marquee: extras['marquee'] ?? kTriOptDefault,
                      onToggle: (v) => _toggle(ch.id, v),
                      onSettingsApplied: (s) => _applyChannelSettings(ch.id, s),
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
    required this.renderer,
    required this.rendererLabels,
    required this.importanceLabel,
    required this.isFirst,
    required this.isLast,
    required this.iconMode,
    required this.focusIconMode,
    required this.focusNotif,
    required this.preserveSmallIcon,
    required this.firstFloat,
    required this.enableFloat,
    required this.islandTimeout,
    required this.marquee,
    required this.onToggle,
    required this.onSettingsApplied,
  });

  final ChannelInfo channel;
  final bool channelEnabled;
  final bool appEnabled;
  final String template;
  final Map<String, String> templateLabels;
  final String renderer;
  final Map<String, String> rendererLabels;
  final String importanceLabel;
  final bool isFirst;
  final bool isLast;
  final String iconMode;
  final String focusIconMode;
  final String focusNotif;
  final String preserveSmallIcon;
  final String firstFloat;
  final String enableFloat;
  final String islandTimeout;
  final String marquee;
  final ValueChanged<bool> onToggle;
  final ValueChanged<Map<String, String?>> onSettingsApplied;

  void _openSettings(BuildContext context) async {
    final result = await BatchChannelSettingsSheet.show(
      context,
      mode: SingleChannelMode(
        channelName:   channel.name,
        template:      template,
        renderer:      renderer,
        iconMode:      iconMode,
        focusIconMode: focusIconMode,
        focusNotif:    focusNotif,
        preserveSmallIcon: preserveSmallIcon,
        firstFloat:    firstFloat,
        enableFloat:   enableFloat,
        islandTimeout: islandTimeout,
        marquee:       marquee,
      ),
      templateLabels: templateLabels,
      rendererLabels: rendererLabels,
    );
    if (result != null) onSettingsApplied(result.settings);
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
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
                          l10n.channelImportance(importanceLabel, channel.id),
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
                    tooltip: l10n.channelSettings,
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
