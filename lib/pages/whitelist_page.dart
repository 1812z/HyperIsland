import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../controllers/whitelist_controller.dart';
import '../l10n/generated/app_localizations.dart';
import '../widgets/batch_channel_settings_sheet.dart';
import '../widgets/app_list_widgets.dart';
import '../widgets/color_picker_dialog.dart';
import '../widgets/color_value_field.dart';
import '../widgets/toast_settings_panel.dart';
import 'app_channels_page.dart';
import 'toast_app_settings_page.dart';
import '../services/app_cache_service.dart';
import '../services/interaction_haptics.dart';

class WhitelistPage extends StatefulWidget {
  const WhitelistPage({super.key});

  @override
  State<WhitelistPage> createState() => WhitelistPageState();
}

class WhitelistPageState extends State<WhitelistPage> {
  static const String _selectEnabledAction = 'select_enabled';
  static const String _enableAction = 'enable';
  static const String _disableAction = 'disable';
  static const double _backToTopThreshold = 420;

  late final WhitelistController _ctrl;
  final _searchCtrl = TextEditingController();
  final _searchFocus = FocusNode();
  final _scrollController = ScrollController();
  final Set<String> _selectedPackages = {};
  final Map<int, Map<String, int>> _modeRank = {};
  bool _inSelectionMode = false;
  bool _showBackToTop = false;
  double _lastOffset = 0;
  bool _wasLoading = true;
  int _adaptationMode = 0;

  bool get _isToastMode => _adaptationMode == 1;

  @override
  void initState() {
    super.initState();
    _ctrl = WhitelistController();
    _wasLoading = _ctrl.loading;
    _ctrl.addListener(() {
      if (!mounted) return;
      final finishedLoading = _wasLoading && !_ctrl.loading;
      _wasLoading = _ctrl.loading;
      if (finishedLoading) {
        _rebuildModeRank(_adaptationMode, _ctrl.filteredApps);
      }
      setState(() {});
    });
    _scrollController.addListener(_handleScroll);
  }

  void _handleScroll() {
    if (!_scrollController.hasClients) return;
    final offset = _scrollController.offset;
    final isScrollingUp = offset < _lastOffset;
    final shouldShow = offset > _backToTopThreshold && isScrollingUp;
    if (shouldShow != _showBackToTop && mounted) {
      setState(() => _showBackToTop = shouldShow);
    }
    _lastOffset = offset;
  }

  Future<void> _scrollToTop() async {
    if (!_scrollController.hasClients) return;
    await _scrollController.animateTo(
      0,
      duration: const Duration(milliseconds: 280),
      curve: Curves.easeOutCubic,
    );
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
    _scrollController.dispose();
    super.dispose();
  }

  bool get _selectionMode => _inSelectionMode;

  void _rebuildModeRank(int mode, Iterable<AppInfo> source) {
    final apps = List<AppInfo>.from(source);
    apps.sort((a, b) {
      final isToastMode = mode == 1;
      final aEnabled = isToastMode
          ? _ctrl.isToastForwardEnabledSync(a.packageName)
          : _ctrl.enabledPackages.contains(a.packageName);
      final bEnabled = isToastMode
          ? _ctrl.isToastForwardEnabledSync(b.packageName)
          : _ctrl.enabledPackages.contains(b.packageName);
      if (aEnabled != bEnabled) return aEnabled ? -1 : 1;
      return a.appName.compareTo(b.appName);
    });

    _modeRank[mode] = {
      for (var i = 0; i < apps.length; i++) apps[i].packageName: i,
    };
  }

  List<AppInfo> _sortedAppsForCurrentMode(Iterable<AppInfo> source) {
    final apps = List<AppInfo>.from(source);
    final rank = _modeRank.putIfAbsent(_adaptationMode, () {
      _rebuildModeRank(_adaptationMode, source);
      return _modeRank[_adaptationMode] ?? <String, int>{};
    });

    var nextRank = rank.length;
    for (final app in apps) {
      rank.putIfAbsent(app.packageName, () => nextRank++);
    }

    apps.sort((a, b) {
      final aRank = rank[a.packageName] ?? 1 << 30;
      final bRank = rank[b.packageName] ?? 1 << 30;
      if (aRank != bRank) return aRank.compareTo(bRank);
      return a.appName.compareTo(b.appName);
    });
    return apps;
  }

  void _clearSearch() {
    _searchCtrl.clear();
    _ctrl.setSearch('');
  }

  bool handleBackPressed() {
    if (_selectionMode) {
      _clearSelection();
      return true;
    }

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
    if (_isToastMode) {
      await _ctrl.setToastEnabledBatch(_selectedPackages.toList(), enabled);
    } else {
      await _ctrl.setEnabledBatch(_selectedPackages.toList(), enabled);
    }
  }

  /// 对已选应用的已启用渠道批量应用配置。
  Future<void> _batchApplySelected() async {
    if (_isToastMode) return;
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
      controller: _ctrl,
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
        final channelsFuture = _ctrl.getChannels(pkg);
        final enabledChannelsFuture = _ctrl.getEnabledChannels(pkg);
        final channels = await channelsFuture;
        final enabledChannels = await enabledChannelsFuture;
        final ids = enabledChannels.isEmpty
            ? channels.map((c) => c.id).toList()
            : enabledChannels.toList();
        if (ids.isNotEmpty) {
          await _ctrl.batchApplyChannelSettings(pkg, ids, result.settings);
        }
      } catch (_) {}
      doneNotifier.value = i + 1;
    }

    if (mounted) Navigator.pop(context);
    doneNotifier.dispose();
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

  Future<void> _batchApplySelectedToast() async {
    if (!_isToastMode) return;
    if (_selectedPackages.isEmpty) return;
    if (!mounted) return;

    final result = await _BatchToastSettingsSheet.show(context);
    if (result == null) return;

    await _ctrl.setToastSettingsBatch(
      _selectedPackages.toList(),
      forwardEnabled: result.forwardEnabled,
      blockOriginal: result.blockOriginal,
      showNotification: result.showNotification,
      showIslandIcon: result.showIslandIcon,
      firstFloat: result.firstFloat,
      marquee: result.marquee,
      timeout: result.timeout,
      highlightColor: result.highlightColor,
      dynamicHighlightColor: result.dynamicHighlightColor,
      showLeftHighlight: result.showLeftHighlight,
      showRightHighlight: result.showRightHighlight,
      outerGlow: result.outerGlow,
      outEffectColor: result.outEffectColor,
    );
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          AppLocalizations.of(context)!.batchApplied(_selectedPackages.length),
        ),
      ),
    );
    _clearSelection();
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
    final apps = _sortedAppsForCurrentMode(_ctrl.filteredApps);
    final enabledCount = _isToastMode
        ? _ctrl.toastEnabledCount
        : _ctrl.enabledPackages.length;
    final allSelected =
        apps.isNotEmpty &&
        apps.every((a) => _selectedPackages.contains(a.packageName));

    return Scaffold(
      backgroundColor: cs.surface,
      floatingActionButton: AnimatedScale(
        scale: _showBackToTop ? 1 : 0,
        duration: const Duration(milliseconds: 180),
        child: AnimatedOpacity(
          opacity: _showBackToTop ? 1 : 0,
          duration: const Duration(milliseconds: 180),
          child: FloatingActionButton.small(
            onPressed: InteractionHaptics.interceptButton(_scrollToTop),
            child: const Icon(Icons.keyboard_arrow_up_rounded),
          ),
        ),
      ),
      body: RefreshIndicator(
        onRefresh: _ctrl.refresh,
        edgeOffset: 300.0,
        child: CustomScrollView(
          controller: _scrollController,
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverAppBar.large(
              backgroundColor: cs.surface,
              centerTitle: false,
              automaticallyImplyLeading: !_selectionMode,
              leading: _selectionMode
                  ? IconButton(
                      icon: const Icon(Icons.close),
                      onPressed: InteractionHaptics.interceptButton(
                        _clearSelection,
                      ),
                      tooltip: l10n.cancelSelection,
                    )
                  : null,
              title: _selectionMode
                  ? Text(l10n.selectedAppsCount(_selectedPackages.length))
                  : Text(
                      _isToastMode ? l10n.toastAdaptation : l10n.appAdaptation,
                    ),
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
                        onPressed: InteractionHaptics.interceptButton(
                          allSelected ? _deselectAll : _selectAll,
                        ),
                      ),
                      // 批量设置渠道配置
                      IconButton(
                        icon: const Icon(Icons.tune),
                        tooltip: l10n.batchChannelSettings,
                        onPressed: _selectedPackages.isNotEmpty
                            ? InteractionHaptics.interceptButton(
                                _isToastMode
                                    ? _batchApplySelectedToast
                                    : _batchApplySelected,
                              )
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
                        onPressed: _ctrl.loading
                            ? null
                            : InteractionHaptics.interceptButton(
                                _enterSelectionMode,
                              ),
                      ),
                      AppBarOverflowMenuButton(
                        onSelected: (value) =>
                            handleAppListOverflowMenuSelection(
                              value: value,
                              onToggleSystemApps: () {
                                _ctrl.setShowSystemApps(!_ctrl.showSystemApps);
                              },
                              onRefresh: _ctrl.refresh,
                              onEnableAll: _isToastMode
                                  ? _ctrl.enableAllToast
                                  : _ctrl.enableAll,
                              onDisableAll: _isToastMode
                                  ? _ctrl.disableAllToast
                                  : _ctrl.disableAll,
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
            SliverToBoxAdapter(
              child: Container(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                child: AppListSearchHeader(
                  countText: _ctrl.showSystemApps
                      ? (_isToastMode
                            ? l10n.toastEnabledAppsCountWithSystem(enabledCount)
                            : l10n.enabledAppsCountWithSystem(enabledCount))
                      : (_isToastMode
                            ? l10n.toastEnabledAppsCount(enabledCount)
                            : l10n.enabledAppsCount(enabledCount)),
                  showCountText: true,
                  searchController: _searchCtrl,
                  searchFocusNode: _searchFocus,
                  hintText: l10n.searchApps,
                  onChanged: _ctrl.setSearch,
                  onClear: _clearSearch,
                ),
              ),
            ),

            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                child: SegmentedButton<int>(
                  segments: [
                    ButtonSegment<int>(
                      value: 0,
                      icon: const Icon(Icons.notifications_active_outlined),
                      label: Text(l10n.adaptationModeNotification),
                    ),
                    ButtonSegment<int>(
                      value: 1,
                      icon: const Icon(Icons.chat_bubble_outline_rounded),
                      label: Text(l10n.adaptationModeToast),
                    ),
                  ],
                  selected: {_adaptationMode},
                  onSelectionChanged: (selection) {
                    final newMode = selection.first;
                    if (newMode == _adaptationMode) return;
                    setState(() {
                      _adaptationMode = newMode;
                    });
                    _rebuildModeRank(newMode, _ctrl.filteredApps);
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
                      enabled: _isToastMode
                          ? _ctrl.isToastForwardEnabledSync(pkg)
                          : _ctrl.enabledPackages.contains(pkg),
                      onChanged: _selectionMode
                          ? null
                          : (v) => _isToastMode
                                ? _ctrl.setToastForwardEnabled(pkg, v)
                                : _ctrl.setEnabled(pkg, v),
                      onTap: _selectionMode
                          ? () => _toggleSelection(pkg)
                          : () => Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => _isToastMode
                                    ? ToastAppSettingsPage(
                                        app: app,
                                        controller: _ctrl,
                                      )
                                    : AppChannelsPage(
                                        app: app,
                                        controller: _ctrl,
                                        appEnabled: _ctrl.enabledPackages
                                            .contains(pkg),
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
    super.key,
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
      onTap: selectionMode ? InteractionHaptics.interceptButton(onTap)! : onTap,
      onLongPress: InteractionHaptics.interceptButton(onLongPress),
      selected: isSelected,
      isFirst: isFirst,
      isLast: isLast,
      trailing: selectionMode
          ? Checkbox(
              value: isSelected,
              onChanged: InteractionHaptics.interceptCheckbox((_) => onTap()),
            )
          : Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Switch(
                  value: enabled,
                  onChanged: InteractionHaptics.interceptToggle(onChanged),
                ),
                const SizedBox(width: 4),
                Icon(Icons.chevron_right, color: cs.onSurfaceVariant, size: 20),
              ],
            ),
    );
  }
}

class _BatchToastSettings {
  const _BatchToastSettings({
    required this.forwardEnabled,
    required this.blockOriginal,
    required this.showNotification,
    required this.showIslandIcon,
    this.firstFloat,
    this.marquee,
    this.timeout,
    this.highlightColor,
    this.dynamicHighlightColor,
    this.showLeftHighlight,
    this.showRightHighlight,
    this.outerGlow,
    this.outEffectColor,
  });

  final bool forwardEnabled;
  final bool blockOriginal;
  final bool showNotification;
  final bool showIslandIcon;
  final String? firstFloat;
  final String? marquee;
  final String? timeout;
  final String? highlightColor;
  final String? dynamicHighlightColor;
  final String? showLeftHighlight;
  final String? showRightHighlight;
  final String? outerGlow;
  final String? outEffectColor;
}

class _BatchToastSettingsSheet extends StatefulWidget {
  const _BatchToastSettingsSheet();

  static Future<_BatchToastSettings?> show(BuildContext context) {
    return showModalBottomSheet<_BatchToastSettings>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) => const _BatchToastSettingsSheet(),
    );
  }

  @override
  State<_BatchToastSettingsSheet> createState() =>
      _BatchToastSettingsSheetState();
}

class _BatchToastSettingsSheetState extends State<_BatchToastSettingsSheet> {
  final _timeoutController = TextEditingController();
  final _highlightColorController = TextEditingController();
  final _outEffectColorController = TextEditingController();

  bool _forwardEnabled = false;
  bool _blockOriginal = true;
  bool _showNotification = false;
  bool _showIslandIcon = true;

  String? _firstFloat;
  String? _marquee;
  String? _timeout;
  String? _highlightColor;
  String? _dynamicHighlightColor;
  bool? _showLeftHighlight;
  bool? _showRightHighlight;
  String? _outerGlow;
  String? _outEffectColor;

  @override
  void dispose() {
    _timeoutController.dispose();
    _highlightColorController.dispose();
    _outEffectColorController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final viewInsets = MediaQuery.viewInsetsOf(context);
    final cs = Theme.of(context).colorScheme;

    return Padding(
      padding: EdgeInsets.only(bottom: viewInsets.bottom),
      child: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                l10n.toastAdaptation,
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 12),
              ToastSettingsPanel(
                forwardEnabled: _forwardEnabled,
                blockOriginal: _blockOriginal,
                showNotification: _showNotification,
                showIslandIcon: _showIslandIcon,
                onForwardEnabledChanged: (value) {
                  setState(() {
                    _forwardEnabled = value;
                    if (!value && _blockOriginal) {
                      _blockOriginal = false;
                    }
                  });
                },
                onBlockOriginalChanged: (value) {
                  setState(() => _blockOriginal = value);
                },
                onShowNotificationChanged: (value) {
                  if (!_forwardEnabled && value) return;
                  setState(() => _showNotification = value);
                },
                onShowIslandIconChanged: (value) {
                  if (!_forwardEnabled) return;
                  setState(() => _showIslandIcon = value);
                },
                showHint: false,
                allowIndependentBlockOriginal: true,
              ),
              const SizedBox(height: 12),
              _BatchTriOptTile(
                label: l10n.firstFloatLabel,
                value: _firstFloat,
                defaultLabel: l10n.noChange,
                onChanged: (v) => setState(() => _firstFloat = v),
              ),
              const SizedBox(height: 10),
              _BatchTriOptTile(
                label: l10n.marqueeChannelTitle,
                value: _marquee,
                defaultLabel: l10n.noChange,
                onChanged: (v) => setState(() => _marquee = v),
              ),
              const SizedBox(height: 10),
              _BatchField(
                label: l10n.autoDisappear,
                child: TextFormField(
                  controller: _timeoutController,
                  keyboardType: TextInputType.number,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                  decoration: _batchFieldDecoration(
                    context,
                    hintText: l10n.noChange,
                    suffixText: l10n.seconds,
                  ),
                  onChanged: (v) {
                    final trimmed = v.trim();
                    final n = int.tryParse(trimmed);
                    setState(() {
                      _timeout = (trimmed.isNotEmpty && n != null && n >= 1)
                          ? trimmed
                          : null;
                    });
                  },
                ),
              ),
              const SizedBox(height: 10),
              _BatchField(
                label: l10n.highlightColorLabel,
                child: ColorValueField(
                  controller: _highlightColorController,
                  decoration: _batchFieldDecoration(
                    context,
                    hintText: l10n.noChange,
                  ),
                  previewColor: parseHexColor(_highlightColor),
                  previewFallbackColor: cs.primary,
                  onChanged: (v) => setState(
                    () => _highlightColor = v.trim().isEmpty ? null : v.trim(),
                  ),
                  onClear: () {
                    _highlightColorController.clear();
                    setState(() => _highlightColor = '');
                  },
                  onPickColor: () async {
                    final color = await showColorPickerDialog(
                      context,
                      initialHex: _highlightColor,
                      title: l10n.highlightColorLabel,
                      enableAlpha: true,
                    );
                    if (color == null) return;
                    final hex = colorToArgbHex(color);
                    _highlightColorController.text = hex;
                    setState(() => _highlightColor = hex);
                  },
                ),
              ),
              const SizedBox(height: 10),
              _BatchField(
                label: l10n.dynamicHighlightColorLabel,
                child: DropdownButtonFormField<String?>(
                  initialValue: _dynamicHighlightColor,
                  decoration: _batchFieldDecoration(context),
                  items: [
                    DropdownMenuItem<String?>(
                      value: null,
                      child: Text(l10n.noChange),
                    ),
                    DropdownMenuItem<String?>(
                      value: kTriOptDefault,
                      child: Text(l10n.optDefault),
                    ),
                    DropdownMenuItem<String?>(
                      value: kTriOptOff,
                      child: Text(l10n.optOff),
                    ),
                    DropdownMenuItem<String?>(
                      value: kTriOptOn,
                      child: Text(l10n.optOn),
                    ),
                    DropdownMenuItem<String?>(
                      value: 'dark',
                      child: Text(l10n.dynamicHighlightModeDark),
                    ),
                    DropdownMenuItem<String?>(
                      value: 'darker',
                      child: Text(l10n.dynamicHighlightModeDarker),
                    ),
                  ],
                  onChanged: (v) => setState(() => _dynamicHighlightColor = v),
                ),
              ),
              const SizedBox(height: 10),
              _BatchField(
                label: l10n.textHighlightLabel,
                child: Row(
                  children: [
                    Expanded(
                      child: SwitchListTile(
                        dense: true,
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 8,
                        ),
                        title: Text(l10n.showLeftHighlightShort),
                        value: _showLeftHighlight ?? false,
                        onChanged: (v) =>
                            setState(() => _showLeftHighlight = v),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: SwitchListTile(
                        dense: true,
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 8,
                        ),
                        title: Text(l10n.showRightHighlightShort),
                        value: _showRightHighlight ?? false,
                        onChanged: (v) =>
                            setState(() => _showRightHighlight = v),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 10),
              _BatchTriOptTile(
                label: l10n.outerGlowLabel,
                value: _outerGlow,
                defaultLabel: l10n.noChange,
                includeFollowDynamic: true,
                onChanged: (v) => setState(() => _outerGlow = v),
              ),
              const SizedBox(height: 10),
              _BatchField(
                label: l10n.outEffectColorLabel,
                child: ColorValueField(
                  controller: _outEffectColorController,
                  decoration: _batchFieldDecoration(
                    context,
                    hintText: l10n.noChange,
                  ),
                  previewColor: parseHexColor(_outEffectColor),
                  previewFallbackColor: cs.primary,
                  onChanged: (v) => setState(
                    () => _outEffectColor = v.trim().isEmpty ? null : v.trim(),
                  ),
                  onClear: () {
                    _outEffectColorController.clear();
                    setState(() => _outEffectColor = '');
                  },
                  onPickColor: () async {
                    final color = await showColorPickerDialog(
                      context,
                      initialHex: _outEffectColor,
                      title: l10n.outEffectColorLabel,
                      enableAlpha: true,
                    );
                    if (color == null) return;
                    final hex = colorToArgbHex(color);
                    _outEffectColorController.text = hex;
                    setState(() => _outEffectColor = hex);
                  },
                ),
              ),
              const SizedBox(height: 12),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  TextButton(
                    onPressed: InteractionHaptics.interceptButton(() {
                      Navigator.pop(context);
                    }),
                    child: Text(l10n.cancel),
                  ),
                  const SizedBox(width: 8),
                  FilledButton(
                    onPressed: InteractionHaptics.interceptButton(() {
                      Navigator.pop(
                        context,
                        _BatchToastSettings(
                          forwardEnabled: _forwardEnabled,
                          blockOriginal: _blockOriginal,
                          showNotification: _showNotification,
                          showIslandIcon: _showIslandIcon,
                          firstFloat: _firstFloat,
                          marquee: _marquee,
                          timeout: _timeout,
                          highlightColor: _highlightColor,
                          dynamicHighlightColor: _dynamicHighlightColor,
                          showLeftHighlight: _showLeftHighlight == null
                              ? null
                              : (_showLeftHighlight! ? kTriOptOn : kTriOptOff),
                          showRightHighlight: _showRightHighlight == null
                              ? null
                              : (_showRightHighlight! ? kTriOptOn : kTriOptOff),
                          outerGlow: _outerGlow,
                          outEffectColor: _outEffectColor,
                        ),
                      );
                    }),
                    child: Text(l10n.apply),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _BatchField extends StatelessWidget {
  const _BatchField({required this.label, required this.child});

  final String label;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: Theme.of(
            context,
          ).textTheme.bodyMedium?.copyWith(color: cs.onSurfaceVariant),
        ),
        const SizedBox(height: 6),
        child,
      ],
    );
  }
}

class _BatchTriOptTile extends StatelessWidget {
  const _BatchTriOptTile({
    required this.label,
    required this.value,
    required this.defaultLabel,
    required this.onChanged,
    this.includeFollowDynamic = false,
  });

  final String label;
  final String? value;
  final String defaultLabel;
  final ValueChanged<String?> onChanged;
  final bool includeFollowDynamic;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return _BatchField(
      label: label,
      child: DropdownButtonFormField<String?>(
        initialValue: value,
        decoration: _batchFieldDecoration(context),
        items: [
          DropdownMenuItem<String?>(value: null, child: Text(defaultLabel)),
          DropdownMenuItem<String?>(
            value: kTriOptDefault,
            child: Text(l10n.optDefault),
          ),
          DropdownMenuItem<String?>(value: kTriOptOn, child: Text(l10n.optOn)),
          DropdownMenuItem<String?>(
            value: kTriOptOff,
            child: Text(l10n.optOff),
          ),
          if (includeFollowDynamic)
            DropdownMenuItem<String?>(
              value: kTriOptFollowDynamic,
              child: Text(l10n.followDynamicColorLabel),
            ),
        ],
        onChanged: onChanged,
      ),
    );
  }
}

InputDecoration _batchFieldDecoration(
  BuildContext context, {
  String? hintText,
  String? suffixText,
}) {
  final cs = Theme.of(context).colorScheme;
  return InputDecoration(
    hintText: hintText,
    suffixText: suffixText,
    isDense: true,
    contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
    border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
    filled: true,
    fillColor: cs.surfaceContainerHighest,
  );
}

class _SimpleColorSlider extends StatelessWidget {
  const _SimpleColorSlider({
    required this.label,
    required this.value,
    required this.max,
    required this.onChanged,
    required this.gradientColors,
  });

  final String label;
  final double value;
  final double max;
  final ValueChanged<double> onChanged;
  final List<Color> gradientColors;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label),
        const SizedBox(height: 4),
        Stack(
          alignment: Alignment.center,
          children: [
            Container(
              height: 24,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(12),
                gradient: LinearGradient(colors: gradientColors),
              ),
            ),
            SliderTheme(
              data: SliderThemeData(
                trackHeight: 24,
                thumbColor: Colors.white,
                overlayColor: Colors.white.withValues(alpha: 0.12),
                thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 12),
                activeTrackColor: Colors.transparent,
                inactiveTrackColor: Colors.transparent,
              ),
              child: Slider(value: value, max: max, onChanged: onChanged),
            ),
          ],
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
