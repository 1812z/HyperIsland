import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../controllers/settings_controller.dart';
import '../../l10n/generated/app_localizations.dart';
import '../../services/interaction_haptics.dart';
import '../../services/island_background_service.dart';
import '../../widgets/blur_app_bar.dart';
import '../../widgets/color_picker_dialog.dart';
import '../../widgets/color_value_field.dart';
import '../../widgets/island_bg_edit_dialog.dart';
import '../../widgets/modern_slider.dart';

class IslandAppearancePage extends StatefulWidget {
  const IslandAppearancePage({super.key});

  @override
  State<IslandAppearancePage> createState() => _IslandAppearancePageState();
}

class _IslandAppearancePageState extends State<IslandAppearancePage> {
  final _ctrl = SettingsController.instance;
  late double _islandHeightDraft;
  late double _islandTopOffsetDraft;
  late int _bigIslandMaxWidthDraft;
  late int _bigIslandMinWidthDraft;
  late int _buildHash;

  int _computeHash() => Object.hashAll([
    _ctrl.islandHeight,
    _ctrl.islandTopOffset,
    _ctrl.bigIslandMaxWidth,
    _ctrl.bigIslandMinWidth,
    _ctrl.roundIcon,
    _ctrl.islandBgSmallPath,
    _ctrl.islandBgBigPath,
    _ctrl.islandBgExpandPath,
    _ctrl.islandBlurSmallEnabled,
    _ctrl.islandBlurSmallRadius,
    _ctrl.islandBlurSmallColor,
    _ctrl.islandBlurBigEnabled,
    _ctrl.islandBlurBigRadius,
    _ctrl.islandBlurBigColor,
    _ctrl.islandBlurExpandEnabled,
    _ctrl.islandBlurExpandRadius,
    _ctrl.islandBlurExpandColor,
    _ctrl.islandTextColorMode,
    _ctrl.focusNotificationTextColorMode,
    _ctrl.alwaysShowIslandOutline,
    _ctrl.alwaysShowFocusOutline,
  ]);

  @override
  void initState() {
    super.initState();
    _islandHeightDraft = _ctrl.islandHeight;
    _islandTopOffsetDraft = _ctrl.islandTopOffset;
    _bigIslandMaxWidthDraft = _ctrl.bigIslandMaxWidth;
    _bigIslandMinWidthDraft = _ctrl.bigIslandMinWidth;
    _buildHash = _computeHash();
    _ctrl.addListener(_onChanged);
    _disableBlurForExistingBackgrounds();
  }

  @override
  void dispose() {
    _ctrl.removeListener(_onChanged);
    super.dispose();
  }

  void _onChanged() {
    if (!mounted) return;
    final nextHash = _computeHash();
    final nextHeight = _ctrl.islandHeight;
    final nextTopOffset = _ctrl.islandTopOffset;
    final nextMaxWidth = _ctrl.bigIslandMaxWidth;
    final nextMinWidth = _ctrl.bigIslandMinWidth;
    if (nextHash == _buildHash &&
        nextHeight == _islandHeightDraft &&
        nextTopOffset == _islandTopOffsetDraft &&
        nextMaxWidth == _bigIslandMaxWidthDraft &&
        nextMinWidth == _bigIslandMinWidthDraft) {
      return;
    }
    setState(() {
      _buildHash = nextHash;
      _islandHeightDraft = nextHeight;
      _islandTopOffsetDraft = nextTopOffset;
      _bigIslandMaxWidthDraft = nextMaxWidth;
      _bigIslandMinWidthDraft = nextMinWidth;
    });
  }

  Future<void> _disableBlurForExistingBackgrounds() async {
    if (_hasAnyBackground) {
      await _disableAllBlur();
    }
  }

  bool get _hasAnyBackground =>
      _ctrl.islandBgSmallPath.isNotEmpty ||
      _ctrl.islandBgBigPath.isNotEmpty ||
      _ctrl.islandBgExpandPath.isNotEmpty;

  Future<void> _disableAllBlur() async {
    if (_ctrl.islandBlurSmallEnabled) {
      await _setBlurEnabled(_IslandBlurType.small, false);
    }
    if (_ctrl.islandBlurBigEnabled) {
      await _setBlurEnabled(_IslandBlurType.big, false);
    }
    if (_ctrl.islandBlurExpandEnabled) {
      await _setBlurEnabled(_IslandBlurType.expand, false);
    }
  }

  Future<void> _setBlurEnabled(_IslandBlurType type, bool enabled) {
    return switch (type) {
      _IslandBlurType.small => _ctrl.setIslandBlurSmall(
        enabled: enabled,
        radius: _ctrl.islandBlurSmallRadius,
        color: _ctrl.islandBlurSmallColor,
      ),
      _IslandBlurType.big => _ctrl.setIslandBlurBig(
        enabled: enabled,
        radius: _ctrl.islandBlurBigRadius,
        color: _ctrl.islandBlurBigColor,
      ),
      _IslandBlurType.expand => _ctrl.setIslandBlurExpand(
        enabled: enabled,
        radius: _ctrl.islandBlurExpandRadius,
        color: _ctrl.islandBlurExpandColor,
      ),
    };
  }

  Future<void> _pickIslandBackground(IslandBgType type) async {
    final l10n = AppLocalizations.of(context)!;
    final sourcePath = await IslandBackgroundService.pickImage();
    if (sourcePath == null || !mounted) return;

    if (IslandBackgroundService.isGif(sourcePath)) {
      final savedPath = await IslandBackgroundService.copyAndUpdate(
        sourcePath,
        type,
      );
      if (savedPath != null && mounted) {
        await _disableAllBlur();
        if (!mounted) return;
        imageCache.evict(FileImage(File(savedPath)));
        setState(() {});
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(l10n.islandBgImageSelected),
            duration: const Duration(seconds: 2),
          ),
        );
      }
      return;
    }

    final editResult = await showIslandBgEditDialog(
      context: context,
      imagePath: sourcePath,
      type: type,
    );
    if (editResult == null || !mounted) return;

    final savedPath = await IslandBackgroundService.copyAndUpdate(
      editResult.sourcePath,
      type,
    );
    if (savedPath != null && mounted) {
      await _disableAllBlur();
      if (!mounted) return;
      imageCache.evict(FileImage(File(savedPath)));
      setState(() {});
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(l10n.islandBgImageSelected),
          duration: const Duration(seconds: 2),
        ),
      );
    }
  }

  Future<void> _deleteIslandBackground(IslandBgType type) async {
    final l10n = AppLocalizations.of(context)!;
    final oldPath = IslandBackgroundService.getImagePath(type);
    final success = await IslandBackgroundService.deleteImage(type);
    if (success && oldPath != null) {
      imageCache.evict(FileImage(File(oldPath)));
    }
    if (mounted) {
      setState(() {});
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            success ? l10n.islandBgImageDeleted : l10n.islandBgDeleteFailed,
          ),
          duration: const Duration(seconds: 2),
        ),
      );
    }
  }

  Future<void> _onRoundIconChanged(bool value) async {
    await _ctrl.setRoundIcon(value);
  }

  Future<void> _showIslandBlurDialog(_IslandBlurType type) async {
    final l10n = AppLocalizations.of(context)!;
    final title = switch (type) {
      _IslandBlurType.small => l10n.islandBlurSmallTitle,
      _IslandBlurType.big => l10n.islandBlurBigTitle,
      _IslandBlurType.expand => l10n.islandBlurExpandTitle,
    };
    var enabled = switch (type) {
      _IslandBlurType.small => _ctrl.islandBlurSmallEnabled,
      _IslandBlurType.big => _ctrl.islandBlurBigEnabled,
      _IslandBlurType.expand => _ctrl.islandBlurExpandEnabled,
    };
    var radius = switch (type) {
      _IslandBlurType.small => _ctrl.islandBlurSmallRadius,
      _IslandBlurType.big => _ctrl.islandBlurBigRadius,
      _IslandBlurType.expand => _ctrl.islandBlurExpandRadius,
    };
    var color = switch (type) {
      _IslandBlurType.small => _ctrl.islandBlurSmallColor,
      _IslandBlurType.big => _ctrl.islandBlurBigColor,
      _IslandBlurType.expand => _ctrl.islandBlurExpandColor,
    };
    final colorController = TextEditingController(text: color);

    final result = await showDialog<_IslandBlurSettings>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text(title),
          content: SizedBox(
            width: 360,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(l10n.islandBlurEnabled),
                  value: enabled,
                  onChanged: (value) => setDialogState(() => enabled = value),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(child: Text(l10n.islandBlurRadius)),
                    Text('$radius'),
                  ],
                ),
                SliderTheme(
                  data: ModernSliderTheme.theme(context),
                  child: Slider(
                    value: radius.toDouble(),
                    min: 0,
                    max: 275,
                    divisions: 55,
                    onChanged: enabled
                        ? (value) =>
                              setDialogState(() => radius = value.round())
                        : null,
                  ),
                ),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      l10n.islandBlurBlendColor,
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                    const SizedBox(height: 6),
                    ColorValueField(
                      controller: colorController,
                      enabled: enabled,
                      decoration: const InputDecoration(
                        border: OutlineInputBorder(),
                        isDense: true,
                      ),
                      previewColor: parseHexColor(color),
                      previewFallbackColor:
                          Theme.of(context).colorScheme.primary,
                      onChanged: (value) =>
                          setDialogState(() => color = value.trim()),
                      onPickColor: () async {
                        final selected = await showColorPickerDialog(
                          context,
                          initialHex: color,
                          title: l10n.islandBlurBlendColor,
                        );
                        if (selected != null) {
                          final hex = colorToArgbHex(selected);
                          colorController.text = hex;
                          setDialogState(() => color = hex);
                        }
                      },
                    ),
                  ],
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: Text(l10n.cancel),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(
                dialogContext,
                _IslandBlurSettings(enabled, radius, color),
              ),
              child: Text(l10n.save),
            ),
          ],
        ),
      ),
    );
    colorController.dispose();
    if (result == null) return;

    switch (type) {
      case _IslandBlurType.small:
        await _ctrl.setIslandBlurSmall(
          enabled: result.enabled,
          radius: result.radius,
          color: result.color,
        );
      case _IslandBlurType.big:
        await _ctrl.setIslandBlurBig(
          enabled: result.enabled,
          radius: result.radius,
          color: result.color,
        );
      case _IslandBlurType.expand:
        await _ctrl.setIslandBlurExpand(
          enabled: result.enabled,
          radius: result.radius,
          color: result.color,
        );
    }
    if (type == _IslandBlurType.big && result.enabled && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.islandBlurBigTextColorSuggestion)),
      );
    }
  }

  Future<void> _sendTestNotification() async {
    const channel = MethodChannel('io.github.hyperisland/test');
    try {
      await channel.invokeMethod('showTest');
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
    final titleStyle = Theme.of(context).textTheme.titleMedium;

    return Scaffold(
      backgroundColor: cs.surface,
      body: BlurAppBarHost(
        title: l10n.appearanceSection,
        physics: const ClampingScrollPhysics(),
        actions: [
          IconButton(
            icon: const Icon(Icons.notifications_outlined),
            tooltip: l10n.testNotifTooltip,
            onPressed: InteractionHaptics.interceptButton(
              _sendTestNotification,
            ),
          ),
        ],
        slivers: [
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                const SizedBox(height: 8),
                // --- 尺寸 ---
                _SectionLabel(l10n.islandDimenSection),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  child: Column(
                    children: [
                      _DimenTile(
                        title: l10n.islandDimenHeight,
                        value: _islandHeightDraft,
                        min: 0,
                        max: 100,
                        unit: 'dp',
                        defaultVal: 0,
                        followSystemLabel: l10n.followSystem,
                        onChanged: (v) {
                          if (_islandHeightDraft == v) return;
                          setState(() => _islandHeightDraft = v);
                        },
                        onPersist: (v) async {
                          if (_ctrl.islandHeight == v) return;
                          await _ctrl.setIslandHeight(v);
                        },
                        isFirst: true,
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _DimenTile(
                        title: l10n.islandTopOffset,
                        value: _islandTopOffsetDraft,
                        min: -100,
                        max: 100,
                        unit: 'dp',
                        defaultVal: 0,
                        followSystemLabel: l10n.followSystem,
                        onChanged: (v) {
                          if (_islandTopOffsetDraft == v) return;
                          setState(() => _islandTopOffsetDraft = v);
                        },
                        onPersist: (v) async {
                          if (_ctrl.islandTopOffset == v) return;
                          await _ctrl.setIslandTopOffset(v);
                        },
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      ListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 2,
                        ),
                        title: Row(
                          children: [
                            Expanded(
                              child: Text(
                                l10n.bigIslandMaxWidthTitle,
                                style: titleStyle,
                              ),
                            ),
                            Text(
                              _bigIslandMaxWidthDraft > 0
                                  ? l10n.widthDpLabel(_bigIslandMaxWidthDraft)
                                  : l10n.followSystem,
                              style: Theme.of(context).textTheme.bodySmall
                                  ?.copyWith(color: cs.onSurfaceVariant),
                            ),
                            if (_bigIslandMaxWidthDraft != 0)
                              SizedBox(
                                width: 18,
                                height: 18,
                                child: IconButton(
                                  icon: const Icon(Icons.refresh, size: 18),
                                  padding: EdgeInsets.zero,
                                  visualDensity: VisualDensity.compact,
                                  onPressed: InteractionHaptics.interceptButton(
                                    () {
                                      setState(
                                        () => _bigIslandMaxWidthDraft = 0,
                                      );
                                      _ctrl.setBigIslandMaxWidth(0);
                                    },
                                  ),
                                ),
                              ),
                          ],
                        ),
                        subtitle: SliderTheme(
                          data: ModernSliderTheme.theme(context),
                          child: Slider(
                            value: _bigIslandMaxWidthDraft.toDouble().clamp(
                              0,
                              500,
                            ),
                            min: 0,
                            max: 500,
                            divisions: 100,
                            onChanged: InteractionHaptics.interceptSlider((v) {
                              final next = v.round();
                              if (_bigIslandMaxWidthDraft == next) return;
                              setState(() => _bigIslandMaxWidthDraft = next);
                            }),
                            onChangeEnd: (v) async {
                              final next = v.round();
                              if (_ctrl.bigIslandMaxWidth == next) return;
                              await _ctrl.setBigIslandMaxWidth(next);
                            },
                          ),
                        ),
                        shape: const RoundedRectangleBorder(
                          borderRadius: BorderRadius.zero,
                        ),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      ListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 2,
                        ),
                        title: Row(
                          children: [
                            Expanded(
                              child: Text(
                                l10n.bigIslandMinWidthTitle,
                                style: titleStyle,
                              ),
                            ),
                            Text(
                              _bigIslandMinWidthDraft > 0
                                  ? l10n.widthDpLabel(_bigIslandMinWidthDraft)
                                  : l10n.followSystem,
                              style: Theme.of(context).textTheme.bodySmall
                                  ?.copyWith(color: cs.onSurfaceVariant),
                            ),
                            if (_bigIslandMinWidthDraft != 0)
                              SizedBox(
                                width: 18,
                                height: 18,
                                child: IconButton(
                                  icon: const Icon(Icons.refresh, size: 18),
                                  padding: EdgeInsets.zero,
                                  visualDensity: VisualDensity.compact,
                                  onPressed: InteractionHaptics.interceptButton(
                                    () {
                                      setState(
                                        () => _bigIslandMinWidthDraft = 0,
                                      );
                                      _ctrl.setBigIslandMinWidth(0);
                                    },
                                  ),
                                ),
                              ),
                          ],
                        ),
                        subtitle: SliderTheme(
                          data: ModernSliderTheme.theme(context),
                          child: Slider(
                            value: _bigIslandMinWidthDraft.toDouble().clamp(
                              0,
                              500,
                            ),
                            min: 0,
                            max: 500,
                            divisions: 100,
                            onChanged: InteractionHaptics.interceptSlider((v) {
                              final next = v.round();
                              if (_bigIslandMinWidthDraft == next) return;
                              setState(() => _bigIslandMinWidthDraft = next);
                            }),
                            onChangeEnd: (v) async {
                              final next = v.round();
                              if (_ctrl.bigIslandMinWidth == next) return;
                              await _ctrl.setBigIslandMinWidth(next);
                            },
                          ),
                        ),
                        shape: const RoundedRectangleBorder(
                          borderRadius: BorderRadius.vertical(
                            bottom: Radius.circular(16),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 8),
                // --- 背景 ---
                _SectionLabel(l10n.islandBgSection),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  child: Column(
                    children: [
                      _IslandBgTile(
                        title: l10n.islandBgSmallTitle,
                        subtitle: l10n.tapToSelectImage,
                        icon: Icons.panorama_vertical,
                        imagePath: _ctrl.islandBgSmallPath,
                        onTap: () => _pickIslandBackground(IslandBgType.small),
                        onDelete: _ctrl.islandBgSmallPath.isNotEmpty
                            ? () => _deleteIslandBackground(IslandBgType.small)
                            : null,
                        isFirst: true,
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _IslandBgTile(
                        title: l10n.islandBgBigTitle,
                        subtitle: l10n.tapToSelectImage,
                        icon: Icons.panorama_vertical,
                        imagePath: _ctrl.islandBgBigPath,
                        onTap: () => _pickIslandBackground(IslandBgType.big),
                        onDelete: _ctrl.islandBgBigPath.isNotEmpty
                            ? () => _deleteIslandBackground(IslandBgType.big)
                            : null,
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _IslandBgTile(
                        title: l10n.islandBgExpandTitle,
                        subtitle: l10n.tapToSelectImage,
                        icon: Icons.panorama_vertical,
                        imagePath: _ctrl.islandBgExpandPath,
                        onTap: () => _pickIslandBackground(IslandBgType.expand),
                        onDelete: _ctrl.islandBgExpandPath.isNotEmpty
                            ? () => _deleteIslandBackground(IslandBgType.expand)
                            : null,
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _IslandBlurTile(
                        title: l10n.islandBlurSmallTitle,
                        enabled: _ctrl.islandBlurSmallEnabled,
                        radius: _ctrl.islandBlurSmallRadius,
                        color: _ctrl.islandBlurSmallColor,
                        blockedByBackground: _hasAnyBackground,
                        onTap: _hasAnyBackground
                            ? null
                            : () =>
                                  _showIslandBlurDialog(_IslandBlurType.small),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _IslandBlurTile(
                        title: l10n.islandBlurBigTitle,
                        enabled: _ctrl.islandBlurBigEnabled,
                        radius: _ctrl.islandBlurBigRadius,
                        color: _ctrl.islandBlurBigColor,
                        blockedByBackground: _hasAnyBackground,
                        onTap: _hasAnyBackground
                            ? null
                            : () => _showIslandBlurDialog(_IslandBlurType.big),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _IslandBlurTile(
                        title: l10n.islandBlurExpandTitle,
                        enabled: _ctrl.islandBlurExpandEnabled,
                        radius: _ctrl.islandBlurExpandRadius,
                        color: _ctrl.islandBlurExpandColor,
                        blockedByBackground: _hasAnyBackground,
                        onTap: _hasAnyBackground
                            ? null
                            : () =>
                                  _showIslandBlurDialog(_IslandBlurType.expand),
                        isLast: true,
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 8),
                // --- 文字 ---
                _SectionLabel(l10n.islandTextSection),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  child: Column(
                    children: [
                      ListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        title: Text(
                          l10n.islandTextColorTitle,
                          style: titleStyle,
                        ),
                        trailing: _buildTextColorDropdown(
                          l10n,
                          _ctrl.islandTextColorMode,
                          _ctrl.setIslandTextColorMode,
                        ),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      ListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        title: Text(
                          l10n.focusNotificationTextColorTitle,
                          style: titleStyle,
                        ),
                        trailing: _buildTextColorDropdown(
                          l10n,
                          _ctrl.focusNotificationTextColorMode,
                          _ctrl.setFocusNotificationTextColorMode,
                          includeBackgroundModes: false,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 8),
                // --- 图标圆角 ---
                _SectionLabel(l10n.roundIconTitle),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  child: SwitchListTile(
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 4,
                    ),
                    title: Text(l10n.roundIconTitle, style: titleStyle),
                    subtitle: Text(l10n.roundIconSubtitle),
                    value: _ctrl.roundIcon,
                    onChanged: InteractionHaptics.interceptToggle(
                      _onRoundIconChanged,
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                // --- 轮廓 ---
                _SectionLabel(l10n.islandOutlineSection),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  child: Column(
                    children: [
                      SwitchListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        title: Text(
                          l10n.alwaysShowIslandOutlineTitle,
                          style: titleStyle,
                        ),
                        value: _ctrl.alwaysShowIslandOutline,
                        onChanged: InteractionHaptics.interceptToggle(
                          _ctrl.setAlwaysShowIslandOutline,
                        ),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      SwitchListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        title: Text(
                          l10n.alwaysShowFocusOutlineTitle,
                          style: titleStyle,
                        ),
                        value: _ctrl.alwaysShowFocusOutline,
                        onChanged: InteractionHaptics.interceptToggle(
                          _ctrl.setAlwaysShowFocusOutline,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 32),
              ], addAutomaticKeepAlives: false),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTextColorDropdown(
    AppLocalizations l10n,
    String value,
    ValueChanged<String> onChanged, {
    bool includeBackgroundModes = true,
  }) {
    final cs = Theme.of(context).colorScheme;
    return PopupMenuButton<String>(
      initialValue: value,
      color: cs.surfaceContainerHigh,
      borderRadius: BorderRadius.circular(12),
      clipBehavior: Clip.antiAlias,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: cs.outlineVariant),
      ),
      onSelected: InteractionHaptics.interceptDropdown<String>((next) {
        if (next != null) onChanged(next);
      }),
      itemBuilder: (context) => [
        PopupMenuItem(
          value: kIslandTextColorDefault,
          child: Text(_textColorModeLabel(l10n, kIslandTextColorDefault)),
        ),
        PopupMenuItem(
          value: kIslandTextColorBlack,
          child: Text(_textColorModeLabel(l10n, kIslandTextColorBlack)),
        ),
        if (includeBackgroundModes) ...[
          PopupMenuItem(
            value: kIslandTextColorFollowBackground,
            child: Text(
              _textColorModeLabel(l10n, kIslandTextColorFollowBackground),
            ),
          ),
          PopupMenuItem(
            value: kIslandTextColorInvertBackground,
            child: Text(
              _textColorModeLabel(l10n, kIslandTextColorInvertBackground),
            ),
          ),
        ],
        PopupMenuItem(
          value: kIslandTextColorFollowStatusBar,
          child: Text(
            _textColorModeLabel(l10n, kIslandTextColorFollowStatusBar),
          ),
        ),
        PopupMenuItem(
          value: kIslandTextColorInvertStatusBar,
          child: Text(
            _textColorModeLabel(l10n, kIslandTextColorInvertStatusBar),
          ),
        ),
      ],
      child: ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          decoration: BoxDecoration(
            color: cs.surfaceContainerHigh,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: cs.outlineVariant),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                _textColorModeLabel(l10n, value),
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(width: 10),
              const Icon(Icons.arrow_drop_down),
            ],
          ),
        ),
      ),
    );
  }

  String _textColorModeLabel(AppLocalizations l10n, String mode) {
    return switch (mode) {
      kIslandTextColorBlack => l10n.islandTextColorBlack,
      kIslandTextColorFollowBackground => l10n.islandTextColorFollowBackground,
      kIslandTextColorInvertBackground => l10n.islandTextColorInvertBackground,
      kIslandTextColorFollowStatusBar => l10n.islandTextColorFollowStatusBar,
      kIslandTextColorInvertStatusBar => l10n.islandTextColorInvertStatusBar,
      _ => l10n.islandTextColorDefault,
    };
  }
}

enum _IslandBlurType { small, big, expand }

class _IslandBlurSettings {
  const _IslandBlurSettings(this.enabled, this.radius, this.color);

  final bool enabled;
  final int radius;
  final String color;
}

class _SectionLabel extends StatelessWidget {
  final String text;
  const _SectionLabel(this.text);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 18),
      child: Text(
        text,
        style: Theme.of(context).textTheme.titleSmall?.copyWith(
          color: Theme.of(context).colorScheme.primary,
          fontWeight: FontWeight.bold,
          letterSpacing: 0.5,
        ),
      ),
    );
  }
}

class _DimenTile extends StatelessWidget {
  const _DimenTile({
    required this.title,
    required this.value,
    required this.min,
    required this.max,
    required this.unit,
    required this.defaultVal,
    required this.followSystemLabel,
    required this.onChanged,
    required this.onPersist,
    this.isFirst = false,
  });

  final String title;
  final double value;
  final double min;
  final double max;
  final String unit;
  final double defaultVal;
  final String followSystemLabel;
  final ValueChanged<double> onChanged;
  final ValueChanged<double> onPersist;
  final bool isFirst;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final titleStyle = Theme.of(context).textTheme.titleMedium;
    final divisions = (max - min).toInt();
    final displayValue = value.roundToDouble();

    BorderRadius? borderRadius;
    if (isFirst) {
      borderRadius = const BorderRadius.vertical(top: Radius.circular(16));
    }

    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
      shape: borderRadius != null
          ? RoundedRectangleBorder(borderRadius: borderRadius)
          : null,
      title: Row(
        children: [
          Expanded(child: Text(title, style: titleStyle)),
          Text(
            displayValue != defaultVal
                ? '${displayValue.toInt()} $unit'
                : followSystemLabel,
            style: Theme.of(
              context,
            ).textTheme.bodySmall?.copyWith(color: cs.onSurfaceVariant),
          ),
          if (displayValue != defaultVal)
            SizedBox(
              width: 18,
              height: 18,
              child: IconButton(
                icon: const Icon(Icons.refresh, size: 18),
                padding: EdgeInsets.zero,
                visualDensity: VisualDensity.compact,
                onPressed: InteractionHaptics.interceptButton(() {
                  onChanged(defaultVal);
                  onPersist(defaultVal);
                }),
              ),
            ),
        ],
      ),
      subtitle: SliderTheme(
        data: ModernSliderTheme.theme(context),
        child: Slider(
          value: displayValue.clamp(min, max),
          min: min,
          max: max,
          divisions: divisions,
          onChanged: InteractionHaptics.interceptSlider(
            (v) => onChanged(v.roundToDouble()),
          ),
          onChangeEnd: (v) => onPersist(v.roundToDouble()),
        ),
      ),
    );
  }
}

class _IslandBgTile extends StatelessWidget {
  const _IslandBgTile({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.imagePath,
    required this.onTap,
    this.onDelete,
    this.isFirst = false,
  });

  final String title;
  final String subtitle;
  final IconData icon;
  final String imagePath;
  final VoidCallback onTap;
  final VoidCallback? onDelete;
  final bool isFirst;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final hasImage = imagePath.isNotEmpty;

    final borderRadius = isFirst
        ? const BorderRadius.vertical(top: Radius.circular(16))
        : null;

    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      shape: borderRadius != null
          ? RoundedRectangleBorder(borderRadius: borderRadius)
          : null,
      leading: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(
          color: cs.surfaceContainerHigh,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: hasImage ? cs.primary : cs.outline.withValues(alpha: 0.3),
            width: hasImage ? 2 : 1,
          ),
        ),
        child: hasImage
            ? ClipRRect(
                borderRadius: BorderRadius.circular(6),
                child: Image.file(
                  File(imagePath),
                  fit: BoxFit.cover,
                  errorBuilder: (_, _, _) =>
                      Icon(icon, color: cs.onSurfaceVariant, size: 24),
                ),
              )
            : Icon(icon, color: cs.onSurfaceVariant, size: 24),
      ),
      title: Text(title),
      subtitle: Text(
        hasImage ? subtitle : AppLocalizations.of(context)!.islandBgNotSet,
        style: Theme.of(
          context,
        ).textTheme.bodySmall?.copyWith(color: cs.onSurfaceVariant),
      ),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (hasImage && onDelete != null)
            IconButton(
              icon: Icon(Icons.delete_outline, color: cs.error),
              onPressed: onDelete,
              visualDensity: VisualDensity.compact,
            ),
          const Icon(Icons.chevron_right),
        ],
      ),
      onTap: onTap,
    );
  }
}

class _IslandBlurTile extends StatelessWidget {
  const _IslandBlurTile({
    required this.title,
    required this.enabled,
    required this.radius,
    required this.color,
    required this.onTap,
    required this.blockedByBackground,
    this.isLast = false,
  });

  final String title;
  final bool enabled;
  final int radius;
  final String color;
  final VoidCallback? onTap;
  final bool blockedByBackground;
  final bool isLast;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
    final effectiveEnabled = enabled && !blockedByBackground;
    return ListTile(
      enabled: !blockedByBackground,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      shape: isLast
          ? const RoundedRectangleBorder(
              borderRadius: BorderRadius.vertical(bottom: Radius.circular(16)),
            )
          : null,
      leading: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(
          color: effectiveEnabled
              ? parseHexColor(color)
              : cs.surfaceContainerHigh,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: cs.outlineVariant),
        ),
        child: Icon(
          Icons.blur_on,
          color: effectiveEnabled ? cs.onSurface : cs.onSurfaceVariant,
        ),
      ),
      title: Text(title),
      subtitle: Text(
        blockedByBackground
            ? l10n.islandBlurUnavailableWithBackground
            : effectiveEnabled
            ? l10n.islandBlurRadiusValue(radius)
            : l10n.islandBlurDisabled,
      ),
      trailing: const Icon(Icons.chevron_right),
      onTap: onTap,
    );
  }
}
