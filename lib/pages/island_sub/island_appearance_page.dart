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
  late int _smallIslandWidthDraft;
  late int _smallIslandHorizontalOffsetDraft;
  late int _islandTextScaleDraft;
  late int _roundIconRadiusDraft;
  late int _islandIconSizeDraft;
  late double _islandIconPaddingDraft;
  late int _outerGlowRangeDraft;
  late final TextEditingController _outerGlowBaseColorController;
  late int _buildHash;

  int _computeHash() => Object.hashAll([
    _ctrl.islandHeight,
    _ctrl.islandTopOffset,
    _ctrl.bigIslandMaxWidth,
    _ctrl.bigIslandMinWidth,
    _ctrl.smallIslandWidth,
    _ctrl.smallIslandHorizontalOffset,
    _ctrl.islandTextScale,
    _ctrl.roundIcon,
    _ctrl.roundIconRadius,
    _ctrl.islandIconSize,
    _ctrl.islandIconPadding,
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
    _ctrl.islandGlassSmallEnabled,
    _ctrl.islandGlassBigEnabled,
    _ctrl.islandGlassExpandEnabled,
    _ctrl.islandGlassEdgeWidth,
    _ctrl.islandGlassRefraction,
    _ctrl.islandGlassHighlight,
    _ctrl.islandGlassShadow,
    _ctrl.islandGlassLightDirection,
    _ctrl.islandGlassDispersion,
    _ctrl.islandGlassGyroscope,
    _ctrl.islandGlassHdrHighlight,
    _ctrl.islandRefractionSmallEnabled,
    _ctrl.islandRefractionBigEnabled,
    _ctrl.islandRefractionExpandEnabled,
    _ctrl.islandGlassCaptureFps,
    _ctrl.islandGlassCaptureQuality,
    _ctrl.islandTextColorMode,
    _ctrl.focusNotificationTextColorMode,
    _ctrl.mediaNotificationTextColorMode,
    _ctrl.alwaysShowIslandOutline,
    _ctrl.alwaysShowFocusOutline,
    _ctrl.outerGlowRange,
    _ctrl.outerGlowSingleColor,
    _ctrl.outerGlowBaseColor,
  ]);

  @override
  void initState() {
    super.initState();
    _islandHeightDraft = _ctrl.islandHeight;
    _islandTopOffsetDraft = _ctrl.islandTopOffset;
    _bigIslandMaxWidthDraft = _ctrl.bigIslandMaxWidth;
    _bigIslandMinWidthDraft = _ctrl.bigIslandMinWidth;
    _smallIslandWidthDraft = _ctrl.smallIslandWidth;
    _smallIslandHorizontalOffsetDraft = _ctrl.smallIslandHorizontalOffset;
    _islandTextScaleDraft = _ctrl.islandTextScale;
    _roundIconRadiusDraft = _ctrl.roundIconRadius;
    _islandIconSizeDraft = _ctrl.islandIconSize;
    _islandIconPaddingDraft = _ctrl.islandIconPadding;
    _outerGlowRangeDraft = _ctrl.outerGlowRange;
    _outerGlowBaseColorController = TextEditingController(
      text: _ctrl.outerGlowBaseColor,
    );
    _buildHash = _computeHash();
    _ctrl.addListener(_onChanged);
  }

  @override
  void dispose() {
    _ctrl.removeListener(_onChanged);
    _outerGlowBaseColorController.dispose();
    super.dispose();
  }

  void _onChanged() {
    if (!mounted) return;
    final nextHash = _computeHash();
    final nextHeight = _ctrl.islandHeight;
    final nextTopOffset = _ctrl.islandTopOffset;
    final nextMaxWidth = _ctrl.bigIslandMaxWidth;
    final nextMinWidth = _ctrl.bigIslandMinWidth;
    final nextSmallWidth = _ctrl.smallIslandWidth;
    final nextSmallOffset = _ctrl.smallIslandHorizontalOffset;
    final nextTextScale = _ctrl.islandTextScale;
    final nextRoundIconRadius = _ctrl.roundIconRadius;
    final nextIslandIconSize = _ctrl.islandIconSize;
    final nextIslandIconPadding = _ctrl.islandIconPadding;
    final nextGlowRange = _ctrl.outerGlowRange;
    final nextGlowBaseColor = _ctrl.outerGlowBaseColor;
    if (nextHash == _buildHash &&
        nextHeight == _islandHeightDraft &&
        nextTopOffset == _islandTopOffsetDraft &&
        nextMaxWidth == _bigIslandMaxWidthDraft &&
        nextMinWidth == _bigIslandMinWidthDraft &&
        nextSmallWidth == _smallIslandWidthDraft &&
        nextSmallOffset == _smallIslandHorizontalOffsetDraft &&
        nextTextScale == _islandTextScaleDraft &&
        nextRoundIconRadius == _roundIconRadiusDraft &&
        nextIslandIconSize == _islandIconSizeDraft &&
        nextIslandIconPadding == _islandIconPaddingDraft &&
        nextGlowRange == _outerGlowRangeDraft) {
      return;
    }
    setState(() {
      _buildHash = nextHash;
      _islandHeightDraft = nextHeight;
      _islandTopOffsetDraft = nextTopOffset;
      _bigIslandMaxWidthDraft = nextMaxWidth;
      _bigIslandMinWidthDraft = nextMinWidth;
      _smallIslandWidthDraft = nextSmallWidth;
      _smallIslandHorizontalOffsetDraft = nextSmallOffset;
      _islandTextScaleDraft = nextTextScale;
      _roundIconRadiusDraft = nextRoundIconRadius;
      _islandIconSizeDraft = nextIslandIconSize;
      _islandIconPaddingDraft = nextIslandIconPadding;
      _outerGlowRangeDraft = nextGlowRange;
      if (_outerGlowBaseColorController.text != nextGlowBaseColor) {
        _outerGlowBaseColorController.text = nextGlowBaseColor;
      }
    });
  }

  bool get _hasAnyGlass =>
      (_ctrl.islandBlurSmallEnabled && _ctrl.islandGlassSmallEnabled) ||
      (_ctrl.islandBlurBigEnabled && _ctrl.islandGlassBigEnabled) ||
      (_ctrl.islandBlurExpandEnabled && _ctrl.islandGlassExpandEnabled);

  bool get _hasAnyRefraction =>
      (_ctrl.islandBlurSmallEnabled &&
          _ctrl.islandGlassSmallEnabled &&
          _ctrl.islandRefractionSmallEnabled) ||
      (_ctrl.islandBlurBigEnabled &&
          _ctrl.islandGlassBigEnabled &&
          _ctrl.islandRefractionBigEnabled) ||
      (_ctrl.islandBlurExpandEnabled &&
          _ctrl.islandGlassExpandEnabled &&
          _ctrl.islandRefractionExpandEnabled);

  bool _hasBackground(IslandBgType type) => switch (type) {
    IslandBgType.small => _ctrl.islandBgSmallPath.isNotEmpty,
    IslandBgType.big => _ctrl.islandBgBigPath.isNotEmpty,
    IslandBgType.expand => _ctrl.islandBgExpandPath.isNotEmpty,
  };

  bool _isBlurEnabled(IslandBgType type) => switch (type) {
    IslandBgType.small => _ctrl.islandBlurSmallEnabled,
    IslandBgType.big => _ctrl.islandBlurBigEnabled,
    IslandBgType.expand => _ctrl.islandBlurExpandEnabled,
  };

  /// 任意背景图或模糊启用时，轮廓控制需要禁用
  bool get _hasAnyBackgroundOrBlur =>
      _hasBackground(IslandBgType.small) ||
      _hasBackground(IslandBgType.big) ||
      _hasBackground(IslandBgType.expand) ||
      _isBlurEnabled(IslandBgType.small) ||
      _isBlurEnabled(IslandBgType.big) ||
      _isBlurEnabled(IslandBgType.expand);

  IslandBgType _backgroundTypeForBlur(_IslandBlurType type) => switch (type) {
    _IslandBlurType.small => IslandBgType.small,
    _IslandBlurType.big => IslandBgType.big,
    _IslandBlurType.expand => IslandBgType.expand,
  };

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
    var glassEnabled = switch (type) {
      _IslandBlurType.small => _ctrl.islandGlassSmallEnabled,
      _IslandBlurType.big => _ctrl.islandGlassBigEnabled,
      _IslandBlurType.expand => _ctrl.islandGlassExpandEnabled,
    };
    var refractionEnabled = switch (type) {
      _IslandBlurType.small => _ctrl.islandRefractionSmallEnabled,
      _IslandBlurType.big => _ctrl.islandRefractionBigEnabled,
      _IslandBlurType.expand => _ctrl.islandRefractionExpandEnabled,
    };
    var radius = switch (type) {
      _IslandBlurType.small => _ctrl.islandBlurSmallRadius,
      _IslandBlurType.big => _ctrl.islandBlurBigRadius,
      _IslandBlurType.expand => _ctrl.islandBlurExpandRadius,
    }.clamp(0, refractionEnabled ? 20 : 100).toInt();
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
                SwitchListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  title: Text(l10n.islandGlassEnabled),
                  value: glassEnabled,
                  onChanged: enabled
                      ? (value) => setDialogState(() {
                          glassEnabled = value;
                          if (!value) refractionEnabled = false;
                        })
                      : null,
                ),
                SwitchListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  title: Text(l10n.islandGlassTrueRefraction),
                  value: refractionEnabled,
                  onChanged: enabled && glassEnabled
                      ? (value) => setDialogState(() {
                          refractionEnabled = value;
                          if (value) radius = radius.clamp(0, 20).toInt();
                        })
                      : null,
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
                    max: refractionEnabled ? 20 : 100,
                    divisions: refractionEnabled ? 20 : 100,
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
                      previewFallbackColor: Theme.of(
                        context,
                      ).colorScheme.primary,
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
                _IslandBlurSettings(
                  enabled,
                  radius,
                  color,
                  glassEnabled,
                  refractionEnabled,
                ),
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
        await _ctrl.setIslandGlassSmallEnabled(result.glassEnabled);
        await _ctrl.setIslandRefractionSmallEnabled(result.refractionEnabled);
      case _IslandBlurType.big:
        await _ctrl.setIslandBlurBig(
          enabled: result.enabled,
          radius: result.radius,
          color: result.color,
        );
        await _ctrl.setIslandGlassBigEnabled(result.glassEnabled);
        await _ctrl.setIslandRefractionBigEnabled(result.refractionEnabled);
      case _IslandBlurType.expand:
        await _ctrl.setIslandBlurExpand(
          enabled: result.enabled,
          radius: result.radius,
          color: result.color,
        );
        await _ctrl.setIslandGlassExpandEnabled(result.glassEnabled);
        await _ctrl.setIslandRefractionExpandEnabled(result.refractionEnabled);
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

  Future<void> _showGlassEffectSettings() async {
    final l10n = AppLocalizations.of(context)!;
    var edgeWidth = _ctrl.islandGlassEdgeWidth;
    var refraction = _ctrl.islandGlassRefraction;
    var highlight = _ctrl.islandGlassHighlight;
    var shadow = _ctrl.islandGlassShadow;
    var lightDirection = _ctrl.islandGlassLightDirection;
    var dispersion = _ctrl.islandGlassDispersion;
    final result =
        await showDialog<
          ({
            int edgeWidth,
            int refraction,
            int highlight,
            int shadow,
            int lightDirection,
            int dispersion,
          })
        >(
          context: context,
          builder: (dialogContext) => StatefulBuilder(
            builder: (context, setDialogState) => AlertDialog(
              title: Text(l10n.islandGlassCustomize),
              content: SizedBox(
                width: 360,
                child: SingleChildScrollView(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      _GlassSliderTile(
                        title: l10n.islandGlassEdgeWidth,
                        value: edgeWidth,
                        min: 4,
                        max: 40,
                        unit: '%',
                        enabled: true,
                        onChanged: (value) =>
                            setDialogState(() => edgeWidth = value),
                        onPersist: (_) {},
                      ),
                      _GlassSliderTile(
                        title: l10n.islandGlassRefraction,
                        value: refraction,
                        min: 0,
                        max: 40,
                        unit: '%',
                        enabled: true,
                        onChanged: (value) =>
                            setDialogState(() => refraction = value),
                        onPersist: (_) {},
                      ),
                      _GlassSliderTile(
                        title: l10n.islandGlassHighlight,
                        value: highlight,
                        min: 0,
                        max: 100,
                        unit: '%',
                        enabled: true,
                        onChanged: (value) =>
                            setDialogState(() => highlight = value),
                        onPersist: (_) {},
                      ),
                      _GlassSliderTile(
                        title: l10n.islandGlassShadow,
                        value: shadow,
                        min: 0,
                        max: 100,
                        unit: '%',
                        enabled: true,
                        onChanged: (value) =>
                            setDialogState(() => shadow = value),
                        onPersist: (_) {},
                      ),
                      _GlassSliderTile(
                        title: l10n.islandGlassLightDirection,
                        value: lightDirection,
                        min: 0,
                        max: 359,
                        unit: '°',
                        enabled: true,
                        onChanged: (value) =>
                            setDialogState(() => lightDirection = value),
                        onPersist: (_) {},
                      ),
                      _GlassSliderTile(
                        title: l10n.islandGlassDispersion,
                        value: dispersion,
                        min: 0,
                        max: 100,
                        unit: '%',
                        enabled: true,
                        onChanged: (value) =>
                            setDialogState(() => dispersion = value),
                        onPersist: (_) {},
                      ),
                    ],
                  ),
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(dialogContext),
                  child: Text(l10n.cancel),
                ),
                FilledButton(
                  onPressed: () => Navigator.pop(dialogContext, (
                    edgeWidth: edgeWidth,
                    refraction: refraction,
                    highlight: highlight,
                    shadow: shadow,
                    lightDirection: lightDirection,
                    dispersion: dispersion,
                  )),
                  child: Text(l10n.save),
                ),
              ],
            ),
          ),
        );
    if (result == null) return;
    await _ctrl.setIslandGlassEdgeWidth(result.edgeWidth);
    await _ctrl.setIslandGlassRefraction(result.refraction);
    await _ctrl.setIslandGlassHighlight(result.highlight);
    await _ctrl.setIslandGlassShadow(result.shadow);
    await _ctrl.setIslandGlassLightDirection(result.lightDirection);
    await _ctrl.setIslandGlassDispersion(result.dispersion);
  }

  Future<void> _showGlassCaptureSettings() async {
    final l10n = AppLocalizations.of(context)!;
    var fps = _ctrl.islandGlassCaptureFps;
    var quality = _ctrl.islandGlassCaptureQuality;
    final result = await showDialog<(int, int)>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text(l10n.islandGlassCaptureSettings),
          content: SizedBox(
            width: 360,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Row(
                  children: [
                    Expanded(child: Text(l10n.islandGlassCaptureFps)),
                    Text('$fps fps'),
                  ],
                ),
                SliderTheme(
                  data: ModernSliderTheme.theme(context),
                  child: Slider(
                    value: fps.toDouble(),
                    min: 1,
                    max: 90,
                    divisions: 89,
                    onChanged: (value) =>
                        setDialogState(() => fps = value.round()),
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  children: [
                    Expanded(child: Text(l10n.islandGlassCaptureQuality)),
                    Text('$quality%'),
                  ],
                ),
                SliderTheme(
                  data: ModernSliderTheme.theme(context),
                  child: Slider(
                    value: quality.toDouble(),
                    min: 10,
                    max: 100,
                    divisions: 18,
                    onChanged: (value) =>
                        setDialogState(() => quality = value.round()),
                  ),
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
              onPressed: () => Navigator.pop(dialogContext, (fps, quality)),
              child: Text(l10n.save),
            ),
          ],
        ),
      ),
    );
    if (result == null) return;
    await _ctrl.setIslandGlassCaptureSettings(
      fps: result.$1,
      quality: result.$2,
    );
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
                  clipBehavior: Clip.antiAlias,
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
                        min: -10,
                        max: 50,
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
                          borderRadius: BorderRadius.zero,
                        ),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _DimenTile(
                        title: l10n.smallIslandWidth,
                        value: _smallIslandWidthDraft.toDouble(),
                        min: 1,
                        max: 100,
                        unit: 'dp',
                        defaultVal: 34,
                        followSystemLabel: l10n.optDefault,
                        onChanged: (value) => setState(
                          () => _smallIslandWidthDraft = value.round(),
                        ),
                        onPersist: (value) =>
                            _ctrl.setSmallIslandWidth(value.round()),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _DimenTile(
                        title: l10n.smallIslandHorizontalOffset,
                        value: _smallIslandHorizontalOffsetDraft.toDouble(),
                        min: -10,
                        max: 50,
                        unit: 'dp',
                        defaultVal: 0,
                        followSystemLabel: l10n.optDefault,
                        onChanged: (value) => setState(
                          () =>
                              _smallIslandHorizontalOffsetDraft = value.round(),
                        ),
                        onPersist: (value) =>
                            _ctrl.setSmallIslandHorizontalOffset(value.round()),
                        isLast: true,
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
                  clipBehavior: Clip.antiAlias,
                  child: Column(
                    children: [
                      _IslandBgTile(
                        title: l10n.islandBgSmallTitle,
                        subtitle: l10n.tapToSelectImage,
                        icon: Icons.panorama_vertical,
                        imagePath: _ctrl.islandBgSmallPath,
                        blocked: _isBlurEnabled(IslandBgType.small),
                        onTap: _isBlurEnabled(IslandBgType.small)
                            ? null
                            : () => _pickIslandBackground(IslandBgType.small),
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
                        blocked: _isBlurEnabled(IslandBgType.big),
                        onTap: _isBlurEnabled(IslandBgType.big)
                            ? null
                            : () => _pickIslandBackground(IslandBgType.big),
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
                        blocked: _isBlurEnabled(IslandBgType.expand),
                        onTap: _isBlurEnabled(IslandBgType.expand)
                            ? null
                            : () => _pickIslandBackground(IslandBgType.expand),
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
                        blocked: _hasBackground(
                          _backgroundTypeForBlur(_IslandBlurType.small),
                        ),
                        onTap:
                            _hasBackground(
                              _backgroundTypeForBlur(_IslandBlurType.small),
                            )
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
                        blocked: _hasBackground(
                          _backgroundTypeForBlur(_IslandBlurType.big),
                        ),
                        onTap:
                            _hasBackground(
                              _backgroundTypeForBlur(_IslandBlurType.big),
                            )
                            ? null
                            : () => _showIslandBlurDialog(_IslandBlurType.big),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _IslandBlurTile(
                        title: l10n.islandBlurExpandTitle,
                        enabled: _ctrl.islandBlurExpandEnabled,
                        radius: _ctrl.islandBlurExpandRadius,
                        color: _ctrl.islandBlurExpandColor,
                        blocked: _hasBackground(
                          _backgroundTypeForBlur(_IslandBlurType.expand),
                        ),
                        onTap:
                            _hasBackground(
                              _backgroundTypeForBlur(_IslandBlurType.expand),
                            )
                            ? null
                            : () =>
                                  _showIslandBlurDialog(_IslandBlurType.expand),
                        isLast: true,
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 8),
                // --- 玻璃效果 ---
                _SectionLabel(l10n.islandGlassSection),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  clipBehavior: Clip.antiAlias,
                  child: Column(
                    children: [
                      ListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        leading: const Icon(Icons.tune),
                        title: Text(
                          l10n.islandGlassCustomize,
                          style: titleStyle,
                        ),
                        subtitle: Text(
                          _hasAnyGlass
                              ? l10n.islandGlassCustomizeSubtitle
                              : l10n.islandGlassEnableFirst,
                        ),
                        trailing: const Icon(Icons.chevron_right),
                        enabled: _hasAnyGlass,
                        onTap: _hasAnyGlass ? _showGlassEffectSettings : null,
                      ),
                      SwitchListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        title: Text(
                          l10n.islandGlassGyroscope,
                          style: titleStyle,
                        ),
                        subtitle: Text(l10n.islandGlassGyroscopeSubtitle),
                        value: _ctrl.islandGlassGyroscope,
                        onChanged: _hasAnyGlass
                            ? InteractionHaptics.interceptToggle(
                                _ctrl.setIslandGlassGyroscope,
                              )
                            : null,
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      SwitchListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        title: Text(
                          l10n.islandGlassHdrHighlight,
                          style: titleStyle,
                        ),
                        subtitle: Text(l10n.islandGlassHdrHighlightSubtitle),
                        value: _ctrl.islandGlassHdrHighlight,
                        onChanged: _hasAnyGlass
                            ? InteractionHaptics.interceptToggle(
                                _ctrl.setIslandGlassHdrHighlight,
                              )
                            : null,
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      ListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        leading: const Icon(Icons.tune),
                        title: Text(
                          l10n.islandGlassCaptureSettings,
                          style: titleStyle,
                        ),
                        subtitle: Text(
                          _hasAnyRefraction
                              ? l10n.islandGlassCaptureSettingsSubtitle
                              : l10n.islandGlassEnableLiquidFirst,
                        ),
                        trailing: const Icon(Icons.chevron_right),
                        enabled: _hasAnyRefraction,
                        onTap: _hasAnyRefraction
                            ? _showGlassCaptureSettings
                            : null,
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
                  clipBehavior: Clip.antiAlias,
                  child: Column(
                    children: [
                      _DimenTile(
                        title: l10n.islandTextSizeTitle,
                        value: _islandTextScaleDraft.toDouble(),
                        min: 10,
                        max: 200,
                        unit: '%',
                        defaultVal: 100,
                        followSystemLabel: '100%',
                        onChanged: (value) => setState(
                          () => _islandTextScaleDraft = value.round(),
                        ),
                        onPersist: (value) =>
                            _ctrl.setIslandTextScale(value.round()),
                        isFirst: true,
                        alwaysShowReset: true,
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
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
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      ListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        title: Text(
                          l10n.mediaNotificationTextColorTitle,
                          style: titleStyle,
                        ),
                        trailing: _buildTextColorDropdown(
                          l10n,
                          _ctrl.mediaNotificationTextColorMode,
                          _ctrl.setMediaNotificationTextColorMode,
                          includeBackgroundModes: false,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 8),
                // --- 图标 ---
                _SectionLabel(l10n.islandIconSectionTitle),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  clipBehavior: Clip.antiAlias,
                  child: Column(
                    children: [
                      _DimenTile(
                        title: l10n.iconSizeTitle,
                        value: _islandIconSizeDraft.toDouble(),
                        min: 50,
                        max: 150,
                        unit: '%',
                        defaultVal: 100,
                        followSystemLabel: '100 %',
                        onChanged: (value) {
                          final next = value.round();
                          if (_islandIconSizeDraft == next) return;
                          setState(() => _islandIconSizeDraft = next);
                        },
                        onPersist: (value) =>
                            _ctrl.setIslandIconSize(value.round()),
                        isFirst: true,
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _DimenTile(
                        title: l10n.roundIconRadiusTitle,
                        value: _roundIconRadiusDraft.toDouble(),
                        min: 0,
                        max: 100,
                        unit: '%',
                        defaultVal: 40,
                        followSystemLabel: '40 %',
                        onChanged: (value) {
                          final next = value.round();
                          if (_roundIconRadiusDraft == next) return;
                          setState(() => _roundIconRadiusDraft = next);
                        },
                        onPersist: (value) =>
                            _ctrl.setRoundIconRadius(value.round()),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      SwitchListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        title: Text(l10n.roundIconTitle, style: titleStyle),
                        subtitle: Text(l10n.roundIconSubtitle),
                        value: _ctrl.roundIcon,
                        onChanged: InteractionHaptics.interceptToggle(
                          _ctrl.setRoundIcon,
                        ),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _DimenTile(
                        title: l10n.iconPaddingTitle,
                        value: _islandIconPaddingDraft.toDouble(),
                        min: 0,
                        max: 10,
                        unit: 'dp',
                        defaultVal: 8,
                        followSystemLabel: '8.0 dp',
                        decimalPlaces: 1,
                        onChanged: (value) {
                          final next = (value * 10).round() / 10;
                          if (_islandIconPaddingDraft == next) return;
                          setState(() => _islandIconPaddingDraft = next);
                        },
                        onPersist: _ctrl.setIslandIconPadding,
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 8),
                // --- 轮廓 ---
                _SectionLabel(l10n.islandOutlineSection),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  clipBehavior: Clip.antiAlias,
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
                        onChanged: _hasAnyBackgroundOrBlur
                            ? null
                            : InteractionHaptics.interceptToggle(
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
                        onChanged: _hasAnyBackgroundOrBlur
                            ? null
                            : InteractionHaptics.interceptToggle(
                                _ctrl.setAlwaysShowFocusOutline,
                              ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 8),
                // --- 外圈光效 ---
                _SectionLabel(l10n.outerGlowAppearanceSection),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  clipBehavior: Clip.antiAlias,
                  child: Column(
                    children: [
                      _DimenTile(
                        title: l10n.outerGlowRangeTitle,
                        value: _outerGlowRangeDraft.toDouble(),
                        min: 0,
                        max: 100,
                        unit: '%',
                        defaultVal: 0,
                        followSystemLabel: l10n.followSystem,
                        onChanged: (value) {
                          final next = value.round();
                          if (_outerGlowRangeDraft == next) return;
                          setState(() => _outerGlowRangeDraft = next);
                        },
                        onPersist: (value) =>
                            _ctrl.setOuterGlowRange(value.round()),
                        isFirst: true,
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      SwitchListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        title: Text(
                          l10n.outerGlowSingleColorTitle,
                          style: titleStyle,
                        ),
                        value: _ctrl.outerGlowSingleColor,
                        onChanged: InteractionHaptics.interceptToggle(
                          _ctrl.setOuterGlowSingleColor,
                        ),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      Padding(
                        padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              l10n.outerGlowBaseColorTitle,
                              style: titleStyle?.copyWith(
                                color: _ctrl.outerGlowSingleColor
                                    ? cs.onSurface.withValues(alpha: 0.38)
                                    : null,
                              ),
                            ),
                            const SizedBox(height: 8),
                            Opacity(
                              opacity: _ctrl.outerGlowSingleColor ? 0.38 : 1,
                              child: ColorValueField(
                                controller: _outerGlowBaseColorController,
                                enabled: !_ctrl.outerGlowSingleColor,
                                decoration: const InputDecoration(
                                  border: OutlineInputBorder(),
                                  isDense: true,
                                ),
                                previewColor: parseHexColor(
                                  _ctrl.outerGlowBaseColor,
                                ),
                                previewFallbackColor: const Color(0xFF0096FF),
                                onChanged: _ctrl.setOuterGlowBaseColor,
                                onClear: () {
                                  _outerGlowBaseColorController.clear();
                                  _ctrl.setOuterGlowBaseColor('');
                                },
                                onPickColor: () async {
                                  final selected = await showColorPickerDialog(
                                    context,
                                    initialHex: _ctrl.outerGlowBaseColor,
                                    title: l10n.outerGlowBaseColorTitle,
                                    enableAlpha: false,
                                  );
                                  if (selected == null) return;
                                  final hex = colorToArgbHex(selected);
                                  _outerGlowBaseColorController.text = hex;
                                  await _ctrl.setOuterGlowBaseColor(hex);
                                },
                              ),
                            ),
                          ],
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
    final values = [
      kIslandTextColorDefault,
      kIslandTextColorBlack,
      if (includeBackgroundModes) ...[
        kIslandTextColorFollowBackground,
        kIslandTextColorInvertBackground,
      ],
      kIslandTextColorFollowStatusBar,
      kIslandTextColorInvertStatusBar,
    ];
    final dropdownWidth = (MediaQuery.sizeOf(context).width * 0.36).clamp(
      112.0,
      172.0,
    );

    return DropdownButtonHideUnderline(
      child: SizedBox(
        width: dropdownWidth,
        child: ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 10),
            decoration: BoxDecoration(
              color: cs.surfaceContainerHigh,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: cs.outlineVariant),
            ),
            child: DropdownButton<String>(
              value: value,
              isExpanded: true,
              alignment: Alignment.center,
              borderRadius: BorderRadius.circular(16),
              onChanged: InteractionHaptics.interceptDropdown((next) {
                if (next == null) return;
                onChanged(next);
              }),
              selectedItemBuilder: (context) => [
                for (final item in values)
                  Center(
                    child: Text(
                      _textColorModeLabel(l10n, item),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
              ],
              items: [
                for (final item in values)
                  DropdownMenuItem<String>(
                    value: item,
                    child: Text(_textColorModeLabel(l10n, item)),
                  ),
              ],
            ),
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
  const _IslandBlurSettings(
    this.enabled,
    this.radius,
    this.color,
    this.glassEnabled,
    this.refractionEnabled,
  );

  final bool enabled;
  final int radius;
  final String color;
  final bool glassEnabled;
  final bool refractionEnabled;
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
    this.isLast = false,
    this.alwaysShowReset = false,
    this.decimalPlaces = 0,
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
  final bool isLast;
  final bool alwaysShowReset;
  final int decimalPlaces;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final titleStyle = Theme.of(context).textTheme.titleMedium;
    final scale = switch (decimalPlaces) {
      1 => 10,
      2 => 100,
      _ => 1,
    };
    final divisions = ((max - min) * scale).round();
    final displayValue = (value * scale).round() / scale;
    final defaultDisplayValue = (defaultVal * scale).round() / scale;

    BorderRadius? borderRadius;
    if (isFirst) {
      borderRadius = const BorderRadius.vertical(top: Radius.circular(16));
    } else if (isLast) {
      borderRadius = const BorderRadius.vertical(bottom: Radius.circular(16));
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
            displayValue != defaultDisplayValue
                ? '${displayValue.toStringAsFixed(decimalPlaces)} $unit'
                : followSystemLabel,
            style: Theme.of(
              context,
            ).textTheme.bodySmall?.copyWith(color: cs.onSurfaceVariant),
          ),
          if (alwaysShowReset || displayValue != defaultDisplayValue)
            SizedBox(
              width: 18,
              height: 18,
              child: IconButton(
                icon: const Icon(Icons.refresh, size: 18),
                padding: EdgeInsets.zero,
                visualDensity: VisualDensity.compact,
                onPressed: displayValue == defaultDisplayValue
                    ? null
                    : InteractionHaptics.interceptButton(() {
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
            (v) => onChanged((v * scale).round() / scale),
          ),
          onChangeEnd: (v) => onPersist((v * scale).round() / scale),
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
    required this.blocked,
    this.isFirst = false,
  });

  final String title;
  final String subtitle;
  final IconData icon;
  final String imagePath;
  final VoidCallback? onTap;
  final VoidCallback? onDelete;
  final bool blocked;
  final bool isFirst;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final hasImage = imagePath.isNotEmpty;
    final l10n = AppLocalizations.of(context)!;

    final borderRadius = isFirst
        ? const BorderRadius.vertical(top: Radius.circular(16))
        : null;

    return ListTile(
      enabled: !blocked,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      shape: borderRadius != null
          ? RoundedRectangleBorder(borderRadius: borderRadius)
          : null,
      leading: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(
          color: blocked ? cs.surfaceContainerHighest : cs.surfaceContainerHigh,
          borderRadius: BorderRadius.circular(8),
          border: Border.all(
            color: !blocked && hasImage
                ? cs.primary
                : cs.outline.withValues(alpha: 0.3),
            width: !blocked && hasImage ? 2 : 1,
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
            : Icon(
                icon,
                color: blocked ? cs.outline : cs.onSurfaceVariant,
                size: 24,
              ),
      ),
      title: Text(title),
      subtitle: Text(
        blocked
            ? l10n.islandBlurUnavailableWithBackground
            : hasImage
            ? subtitle
            : l10n.islandBgNotSet,
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

class _GlassSliderTile extends StatelessWidget {
  const _GlassSliderTile({
    required this.title,
    required this.value,
    required this.min,
    required this.max,
    required this.unit,
    required this.enabled,
    required this.onChanged,
    required this.onPersist,
  });

  final String title;
  final int value;
  final int min;
  final int max;
  final String unit;
  final bool enabled;
  final ValueChanged<int> onChanged;
  final ValueChanged<int> onPersist;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      enabled: enabled,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
      title: Row(
        children: [
          Expanded(child: Text(title)),
          Text(
            '$value$unit',
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
      subtitle: SliderTheme(
        data: ModernSliderTheme.theme(context),
        child: Slider(
          value: value.toDouble().clamp(min.toDouble(), max.toDouble()),
          min: min.toDouble(),
          max: max.toDouble(),
          divisions: max - min,
          onChanged: enabled
              ? InteractionHaptics.interceptSlider(
                  (next) => onChanged(next.round()),
                )
              : null,
          onChangeEnd: enabled ? (next) => onPersist(next.round()) : null,
        ),
      ),
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
    required this.blocked,
    this.isLast = false,
  });

  final String title;
  final bool enabled;
  final int radius;
  final String color;
  final VoidCallback? onTap;
  final bool blocked;
  final bool isLast;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
    return ListTile(
      enabled: !blocked,
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
          color: enabled && !blocked
              ? parseHexColor(color)
              : cs.surfaceContainerHigh,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: cs.outlineVariant),
        ),
        child: Icon(
          Icons.blur_on,
          color: enabled && !blocked ? cs.onSurface : cs.onSurfaceVariant,
        ),
      ),
      title: Text(title),
      subtitle: Text(
        blocked
            ? l10n.islandBlurUnavailableWithBackground
            : enabled
            ? l10n.islandBlurRadiusValue(radius)
            : l10n.islandBlurDisabled,
      ),
      trailing: const Icon(Icons.chevron_right),
      onTap: onTap,
    );
  }
}
