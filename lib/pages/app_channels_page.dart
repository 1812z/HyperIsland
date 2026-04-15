import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../controllers/whitelist_controller.dart';
import '../l10n/generated/app_localizations.dart';
import '../widgets/batch_channel_settings_sheet.dart';
import '../widgets/app_list_widgets.dart';
import '../services/app_cache_service.dart';

class AppChannelsPage extends StatefulWidget {
  final AppInfo app;
  final WhitelistController controller;

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
  static const String _batchAction = 'batch';
  static const String _enableAllChannelsAction = 'enable_all';

  List<ChannelInfo>? _channels;
  Set<String> _enabledChannels = {};
  Map<String, String> _channelTemplates = {};
  Map<String, String> _templateLabels = {};
  Map<String, String> _rendererLabels = {};
  Map<String, Map<String, String>> _channelExtras = {};
  bool _loading = true;
  late bool _appEnabled;

  @override
  void initState() {
    super.initState();
    _appEnabled = widget.appEnabled;
    widget.controller.addListener(_onControllerChanged);
    _load();
  }

  @override
  void dispose() {
    widget.controller.removeListener(_onControllerChanged);
    super.dispose();
  }

  void _onControllerChanged() {
    final newEnabled = widget.controller.enabledPackages.contains(
      widget.app.packageName,
    );
    if (newEnabled != _appEnabled) {
      setState(() => _appEnabled = newEnabled);
    }
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

    final enabled = await widget.controller.getEnabledChannels(pkg);
    if (!mounted) return;
    final l10nForLabels = AppLocalizations.of(context)!;
    final templateLabels = widget.controller.getTemplates(l10nForLabels);
    final rendererLabels = widget.controller.getRenderers(l10nForLabels);
    final channelIds = channels.map((c) => c.id).toList();
    final channelTemplates = await widget.controller.getChannelTemplates(
      pkg,
      channelIds,
    );
    final channelExtras = await widget.controller.getChannelExtraSettings(
      pkg,
      channelIds,
    );
    if (mounted) {
      setState(() {
        _channels = channels;
        _enabledChannels = enabled;
        _channelTemplates = channelTemplates;
        _templateLabels = templateLabels;
        _rendererLabels = rendererLabels;
        _channelExtras = channelExtras;
        _loading = false;
      });
      if (rootError) {
        WidgetsBinding.instance.addPostFrameCallback(
          (_) => _showRootErrorDialog(),
        );
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
          TextButton(onPressed: () => Navigator.pop(ctx), child: Text(l10n.ok)),
        ],
      ),
    );
  }

  bool _isEnabled(String channelId) {
    if (!_appEnabled) return false;
    return _enabledChannels.isEmpty || _enabledChannels.contains(channelId);
  }

  Future<void> _toggle(String channelId, bool value) async {
    if (!_appEnabled) return;
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

  Future<void> _setAppEnabled(bool value) async {
    await widget.controller.setEnabled(widget.app.packageName, value);
  }

  Future<void> _applyChannelSettings(
    String channelId,
    Map<String, String?> settings,
  ) async {
    final pkg = widget.app.packageName;
    final futures = <Future<void>>[];
    var templateChanged = false;
    var extrasChanged = false;

    String? nextTemplate;
    final nextExtras = Map<String, String>.from(
      _channelExtras[channelId] ?? {},
    );

    if (settings['template'] case final t?) {
      if (_channelTemplates[channelId] != t) {
        nextTemplate = t;
        templateChanged = true;
        futures.add(widget.controller.setChannelTemplate(pkg, channelId, t));
      }
    }

    void queueExtra(
      String key,
      Future<void> Function(String, String, String) persist,
    ) {
      final value = settings[key];
      if (value == null) return;
      final current = nextExtras[key];
      if (current == value) return;
      if (value.isEmpty && (current == null || current.isEmpty)) return;
      nextExtras[key] = value;
      extrasChanged = true;
      futures.add(persist(pkg, channelId, value));
    }

    queueExtra('renderer', widget.controller.setChannelRenderer);
    queueExtra('icon', widget.controller.setChannelIconMode);
    queueExtra('focus', widget.controller.setChannelFocusNotif);
    queueExtra(
      'preserve_small_icon',
      widget.controller.setChannelPreserveSmallIcon,
    );
    queueExtra('show_island_icon', widget.controller.setChannelShowIslandIcon);
    queueExtra('first_float', widget.controller.setChannelFirstFloat);
    queueExtra('enable_float', widget.controller.setChannelEnableFloat);
    queueExtra('timeout', widget.controller.setChannelTimeout);
    queueExtra('marquee', widget.controller.setChannelMarquee);
    queueExtra(
      'restore_lockscreen',
      widget.controller.setChannelRestoreLockscreen,
    );
    queueExtra('highlight_color', widget.controller.setChannelHighlightColor);
    queueExtra(
      'dynamic_highlight_color',
      widget.controller.setChannelDynamicHighlightColor,
    );
    queueExtra(
      'show_left_highlight',
      widget.controller.setChannelShowLeftHighlight,
    );
    queueExtra(
      'show_right_highlight',
      widget.controller.setChannelShowRightHighlight,
    );
    queueExtra(
      'show_left_narrow_font',
      widget.controller.setChannelShowLeftNarrowFont,
    );
    queueExtra(
      'show_right_narrow_font',
      widget.controller.setChannelShowRightNarrowFont,
    );
    queueExtra('outer_glow', widget.controller.setChannelOuterGlow);
    queueExtra(
      'island_outer_glow',
      widget.controller.setChannelIslandOuterGlow,
    );
    queueExtra(
      'island_outer_glow_color',
      widget.controller.setChannelIslandOuterGlowColor,
    );
    queueExtra('out_effect_color', widget.controller.setChannelOutEffectColor);
    queueExtra('focus_custom', widget.controller.setChannelFocusCustomization);
    queueExtra(
      'island_custom',
      widget.controller.setChannelIslandCustomization,
    );

    if (templateChanged || extrasChanged) {
      setState(() {
        if (templateChanged && nextTemplate != null) {
          _channelTemplates = {..._channelTemplates, channelId: nextTemplate};
        }
        if (extrasChanged) {
          _channelExtras = {..._channelExtras, channelId: nextExtras};
        }
      });
    }

    if (futures.isNotEmpty) {
      await Future.wait(futures);
    }
  }

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
        _channelExtras = results[1] as Map<String, Map<String, String>>;
      });
    }
  }

  Future<void> _batchApply() async {
    final channels = _channels ?? [];
    if (channels.isEmpty) return;

    final enabledIds = _enabledChannels.isEmpty
        ? channels.map((c) => c.id).toList()
        : _enabledChannels.toList();

    if (!mounted) return;
    final l10n = AppLocalizations.of(context)!;

    final result = await BatchChannelSettingsSheet.show(
      context,
      mode: BatchChannelMode(
        scope: GlobalScope(
          subtitle: l10n.applyToEnabledChannels(enabledIds.length),
        ),
      ),
      templateLabels: _templateLabels,
      rendererLabels: _rendererLabels,
      controller: widget.controller,
    );
    if (result == null || !mounted) return;

    await widget.controller.batchApplyChannelSettings(
      widget.app.packageName,
      enabledIds,
      result.settings,
    );

    if (mounted) await _reloadSettings();
  }

  Future<void> _enableAllChannels() async {
    setState(() => _enabledChannels = {});
    await widget.controller.setEnabledChannels(widget.app.packageName, {});
  }

  String _importanceLabel(int importance, AppLocalizations l10n) =>
      switch (importance) {
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
    final allEnabled = _appEnabled && _enabledChannels.isEmpty;

    return Scaffold(
      backgroundColor: cs.surface,
      body: CustomScrollView(
        slivers: [
          SliverAppBar.large(
            backgroundColor: cs.surface,
            centerTitle: false,
            title: Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                _AppHeaderIcon(app: widget.app),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(widget.app.appName, overflow: TextOverflow.ellipsis),
                      Text(
                        widget.app.packageName,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: cs.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
                Transform.scale(
                  scale: 0.9,
                  child: Switch(value: _appEnabled, onChanged: _setAppEnabled),
                ),
              ],
            ),
            actions: [
              if (!_loading && channels.isNotEmpty)
                AppBarOverflowMenuButton(
                  onSelected: (value) {
                    switch (value) {
                      case _batchAction:
                        _batchApply();
                      case _enableAllChannelsAction:
                        _enableAllChannels();
                    }
                  },
                  itemBuilder: (ctx) {
                    final ml = AppLocalizations.of(ctx)!;
                    return [
                      buildAppPopupMenuItem(
                        value: _batchAction,
                        icon: Icons.tune_rounded,
                        label: ml.batchChannelSettings,
                      ),
                      const PopupMenuDivider(height: 8),
                      buildAppPopupMenuItem(
                        value: _enableAllChannelsAction,
                        icon: Icons.done_all_rounded,
                        label: ml.enableAllChannels,
                      ),
                    ];
                  },
                ),
            ],
          ),

          if (!_appEnabled)
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
              sliver: SliverToBoxAdapter(
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 14,
                    vertical: 10,
                  ),
                  decoration: BoxDecoration(
                    color: cs.errorContainer,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    children: [
                      Icon(Icons.block, size: 18, color: cs.onErrorContainer),
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
                    Icon(
                      Icons.notifications_off_outlined,
                      size: 48,
                      color: cs.onSurfaceVariant,
                    ),
                    const SizedBox(height: 12),
                    Text(
                      l10n.noChannelsFound,
                      style: TextStyle(color: cs.onSurfaceVariant),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      l10n.noChannelsFoundSubtitle,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: cs.onSurfaceVariant,
                      ),
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
                  _appEnabled
                      ? (allEnabled
                            ? l10n.allChannelsActive(channels.length)
                            : l10n.selectedChannels(
                                _enabledChannels.length,
                                channels.length,
                              ))
                      : l10n.allChannelsDisabled(channels.length),
                  style: Theme.of(
                    context,
                  ).textTheme.bodyMedium?.copyWith(color: cs.onSurfaceVariant),
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
                    final template =
                        _channelTemplates[ch.id] ?? kTemplateNotificationIsland;
                    final extras = _channelExtras[ch.id] ?? {};

                    return _ChannelTile(
                      channel: ch,
                      channelEnabled: channelEnabled,
                      appEnabled: _appEnabled,
                      template: template,
                      templateLabels: _templateLabels,
                      renderer:
                          extras['renderer'] ?? kRendererImageTextWithButtons4,
                      rendererLabels: _rendererLabels,
                      importanceLabel: _importanceLabel(ch.importance, l10n),
                      isFirst: isFirst,
                      isLast: isLast,
                      iconMode: extras['icon'] ?? kIconModeAuto,
                      focusNotif: extras['focus'] ?? kTriOptDefault,
                      preserveSmallIcon:
                          extras['preserve_small_icon'] ?? kTriOptDefault,
                      showIslandIcon:
                          extras['show_island_icon'] ?? kTriOptDefault,
                      firstFloat: extras['first_float'] ?? kTriOptDefault,
                      enableFloat: extras['enable_float'] ?? kTriOptDefault,
                      islandTimeout: extras['timeout'] ?? '5',
                      marquee: extras['marquee'] ?? kTriOptDefault,
                      restoreLockscreen:
                          extras['restore_lockscreen'] ?? kTriOptDefault,
                      highlightColor: extras['highlight_color'] ?? '',
                      dynamicHighlightColor:
                          extras['dynamic_highlight_color'] ?? kTriOptDefault,
                      showLeftHighlight:
                          extras['show_left_highlight'] ?? kTriOptOff,
                      showRightHighlight:
                          extras['show_right_highlight'] ?? kTriOptOff,
                      showLeftNarrowFont:
                          extras['show_left_narrow_font'] ?? kTriOptOff,
                      showRightNarrowFont:
                          extras['show_right_narrow_font'] ?? kTriOptOff,
                      outerGlow: extras['outer_glow'] ?? kTriOptDefault,
                      islandOuterGlow:
                          extras['island_outer_glow'] ?? kTriOptDefault,
                      islandOuterGlowColor:
                          extras['island_outer_glow_color'] ?? '',
                      outEffectColor: extras['out_effect_color'] ?? '',
                      focusCustom: extras['focus_custom'] ?? '',
                      islandCustom: extras['island_custom'] ?? '',
                      controller: widget.controller,
                      onToggle: (v) => _toggle(ch.id, v),
                      onSettingsApplied: (s) => _applyChannelSettings(ch.id, s),
                    );
                  },
                  childCount: channels.length,
                  addAutomaticKeepAlives: false,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _AppHeaderIcon extends StatefulWidget {
  const _AppHeaderIcon({required this.app});

  final AppInfo app;

  @override
  State<_AppHeaderIcon> createState() => _AppHeaderIconState();
}

class _AppHeaderIconState extends State<_AppHeaderIcon> {
  Future<Uint8List?>? _iconFuture;

  @override
  void initState() {
    super.initState();
    if (widget.app.icon.isEmpty) {
      _iconFuture = AppCacheService.instance.getIcon(widget.app.packageName);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (widget.app.icon.isNotEmpty) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: Image.memory(
          widget.app.icon,
          width: 32,
          height: 32,
          fit: BoxFit.cover,
          gaplessPlayback: true,
        ),
      );
    }

    return FutureBuilder<Uint8List?>(
      future: _iconFuture,
      builder: (context, snapshot) {
        final icon = snapshot.data;
        if (icon != null && icon.isNotEmpty) {
          return ClipRRect(
            borderRadius: BorderRadius.circular(8),
            child: Image.memory(
              icon,
              width: 32,
              height: 32,
              fit: BoxFit.cover,
              gaplessPlayback: true,
            ),
          );
        }
        return Container(
          width: 32,
          height: 32,
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surfaceContainer,
            borderRadius: BorderRadius.circular(8),
          ),
          alignment: Alignment.center,
          child: Icon(
            Icons.apps_rounded,
            size: 18,
            color: Theme.of(context).colorScheme.onSurfaceVariant,
          ),
        );
      },
    );
  }
}

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
    required this.focusNotif,
    required this.preserveSmallIcon,
    required this.showIslandIcon,
    required this.firstFloat,
    required this.enableFloat,
    required this.islandTimeout,
    required this.marquee,
    required this.restoreLockscreen,
    required this.highlightColor,
    required this.dynamicHighlightColor,
    required this.showLeftHighlight,
    required this.showRightHighlight,
    required this.showLeftNarrowFont,
    required this.showRightNarrowFont,
    required this.outerGlow,
    required this.islandOuterGlow,
    required this.islandOuterGlowColor,
    required this.outEffectColor,
    required this.focusCustom,
    required this.islandCustom,
    required this.controller,
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
  final String focusNotif;
  final String preserveSmallIcon;
  final String showIslandIcon;
  final String firstFloat;
  final String enableFloat;
  final String islandTimeout;
  final String marquee;
  final String restoreLockscreen;
  final String highlightColor;
  final String dynamicHighlightColor;
  final String showLeftHighlight;
  final String showRightHighlight;
  final String showLeftNarrowFont;
  final String showRightNarrowFont;
  final String outerGlow;
  final String islandOuterGlow;
  final String islandOuterGlowColor;
  final String outEffectColor;
  final String focusCustom;
  final String islandCustom;
  final WhitelistController controller;
  final ValueChanged<bool> onToggle;
  final ValueChanged<Map<String, String?>> onSettingsApplied;

  void _openSettings(BuildContext context) async {
    final result = await BatchChannelSettingsSheet.show(
      context,
      mode: SingleChannelMode(
        channelName: channel.name,
        template: template,
        renderer: renderer,
        iconMode: iconMode,
        focusNotif: focusNotif,
        preserveSmallIcon: preserveSmallIcon,
        showIslandIcon: showIslandIcon,
        firstFloat: firstFloat,
        enableFloat: enableFloat,
        islandTimeout: islandTimeout,
        marquee: marquee,
        restoreLockscreen: restoreLockscreen,
        highlightColor: highlightColor,
        dynamicHighlightColor: dynamicHighlightColor,
        showLeftHighlight: showLeftHighlight,
        showRightHighlight: showRightHighlight,
        showLeftNarrowFont: showLeftNarrowFont,
        showRightNarrowFont: showRightNarrowFont,
        outerGlow: outerGlow,
        islandOuterGlow: islandOuterGlow,
        islandOuterGlowColor: islandOuterGlowColor,
        outEffectColor: outEffectColor,
        focusCustom: focusCustom,
        islandCustom: islandCustom,
      ),
      templateLabels: templateLabels,
      rendererLabels: rendererLabels,
      controller: controller,
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
                          style: Theme.of(context).textTheme.bodyLarge
                              ?.copyWith(
                                color: appEnabled
                                    ? null
                                    : cs.onSurface.withValues(alpha: 0.38),
                              ),
                        ),
                        if (channel.description.isNotEmpty) ...[
                          const SizedBox(height: 2),
                          Text(
                            channel.description,
                            style: Theme.of(context).textTheme.bodySmall
                                ?.copyWith(
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
                          style: Theme.of(context).textTheme.bodySmall
                              ?.copyWith(
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
