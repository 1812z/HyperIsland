import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../controllers/settings_controller.dart';
import '../../l10n/generated/app_localizations.dart';
import '../../models/island_material_config.dart';
import '../../services/interaction_haptics.dart';
import '../../widgets/color_picker_dialog.dart';
import '../../widgets/color_value_field.dart';
import '../../widgets/modern_slider.dart';

class IslandMaterialPage extends StatefulWidget {
  const IslandMaterialPage({super.key});

  @override
  State<IslandMaterialPage> createState() => _IslandMaterialPageState();
}

class _IslandMaterialPageState extends State<IslandMaterialPage> {
  final _controller = SettingsController.instance;

  static const _clipboardConfigType = 'hyperisland_material_config';
  static const _clipboardConfigVersion = 1;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _clearUnsupportedSoftGlassConfigs();
    });
  }

  Future<void> _clearUnsupportedSoftGlassConfigs() async {
    if (_controller.hyperOsMajorVersion != 3) return;
    for (final state in IslandMaterialState.values) {
      if (_controller.materialConfigForState(state).type ==
          IslandMaterialType.softGlass) {
        await _controller.setIslandMaterialConfig(
          state,
          const IslandMaterialConfig(),
        );
      }
    }
  }

  Future<void> _sendTestNotification() async {
    const channel = MethodChannel('io.github.hyperisland/test');
    try {
      await channel.invokeMethod('showCustomTest', {
        'title': '',
        'content': '',
        'clearPrevious': true,
        'enableFloat': true,
      });
    } catch (_) {}
  }

  Future<void> _exportToClipboard() async {
    final data = <String, dynamic>{
      'type': _clipboardConfigType,
      'version': _clipboardConfigVersion,
      'big': _controller.islandMaterialBig.toJson(),
      'small': _controller.islandMaterialSmall.toJson(),
      'expand': _controller.islandMaterialExpand.toJson(),
      'smallFollowBig': _controller.islandMaterialSmallFollowBig,
      'expandFollowBig': _controller.islandMaterialExpandFollowBig,
    };
    await Clipboard.setData(
      ClipboardData(text: const JsonEncoder.withIndent('  ').convert(data)),
    );
    if (!mounted) return;
    _showMessage(AppLocalizations.of(context)!.configCopied);
  }

  Future<void> _importFromClipboard() async {
    final l10n = AppLocalizations.of(context)!;
    final clipboard = await Clipboard.getData(Clipboard.kTextPlain);
    final text = clipboard?.text?.trim() ?? '';
    if (text.isEmpty) {
      _showMessage(l10n.errorEmptyClipboard);
      return;
    }

    try {
      final decoded = jsonDecode(text);
      if (decoded is! Map<String, dynamic> ||
          decoded['type'] != _clipboardConfigType ||
          decoded['version'] != _clipboardConfigVersion ||
          decoded['big'] is! Map ||
          decoded['small'] is! Map ||
          decoded['expand'] is! Map ||
          decoded['smallFollowBig'] is! bool ||
          decoded['expandFollowBig'] is! bool) {
        throw const FormatException('invalid_material_config');
      }

      final big = IslandMaterialConfig.fromJson(
        Map<String, dynamic>.from(decoded['big'] as Map),
      );
      final small = IslandMaterialConfig.fromJson(
        Map<String, dynamic>.from(decoded['small'] as Map),
      );
      final expand = IslandMaterialConfig.fromJson(
        Map<String, dynamic>.from(decoded['expand'] as Map),
      );
      final containsSoftGlass = [
        big,
        small,
        expand,
      ].any((config) => config.type == IslandMaterialType.softGlass);
      if (_controller.hyperOsMajorVersion == 3 && containsSoftGlass) {
        _showMessage(l10n.islandMaterialSoftGlassOs4Only);
        return;
      }

      await _controller.setIslandMaterialConfig(IslandMaterialState.big, big);
      await _controller.setIslandMaterialConfig(
        IslandMaterialState.small,
        small,
      );
      await _controller.setIslandMaterialConfig(
        IslandMaterialState.expand,
        expand,
      );
      await _controller.setIslandMaterialFollowBig(
        IslandMaterialState.small,
        decoded['smallFollowBig'] as bool,
      );
      await _controller.setIslandMaterialFollowBig(
        IslandMaterialState.expand,
        decoded['expandFollowBig'] as bool,
      );
      if (!mounted) return;
      _showMessage(l10n.importSuccess(5));
    } catch (_) {
      if (!mounted) return;
      _showMessage(l10n.importErrorUnknown);
    }
  }

  void _showMessage(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return DefaultTabController(
      length: 3,
      child: Scaffold(
        appBar: AppBar(
          title: Text(l10n.islandMaterialCustomize),
          actions: [
            IconButton(
              icon: const Icon(Icons.copy_outlined),
              tooltip: l10n.exportToClipboard,
              onPressed: InteractionHaptics.interceptButton(_exportToClipboard),
            ),
            IconButton(
              icon: const Icon(Icons.content_paste_go_outlined),
              tooltip: l10n.importFromClipboard,
              onPressed: InteractionHaptics.interceptButton(
                _importFromClipboard,
              ),
            ),
            IconButton(
              icon: const Icon(Icons.notifications_outlined),
              tooltip: l10n.testNotifTooltip,
              onPressed: InteractionHaptics.interceptButton(
                _sendTestNotification,
              ),
            ),
          ],
          bottom: TabBar(
            tabs: [
              Tab(text: l10n.islandMaterialBigTab),
              Tab(text: l10n.islandMaterialSmallTab),
              Tab(text: l10n.islandMaterialExpandTab),
            ],
          ),
        ),
        body: const TabBarView(
          children: [
            _MaterialStateView(state: IslandMaterialState.big),
            _MaterialStateView(state: IslandMaterialState.small),
            _MaterialStateView(state: IslandMaterialState.expand),
          ],
        ),
      ),
    );
  }
}

class _MaterialStateView extends StatefulWidget {
  const _MaterialStateView({required this.state});

  final IslandMaterialState state;

  @override
  State<_MaterialStateView> createState() => _MaterialStateViewState();
}

class _MaterialStateViewState extends State<_MaterialStateView> {
  final _controller = SettingsController.instance;
  late TextEditingController _colorController;

  @override
  void initState() {
    super.initState();
    _colorController = TextEditingController(
      text: _controller.materialConfigForState(widget.state).blendColor,
    );
  }

  @override
  void dispose() {
    _colorController.dispose();
    super.dispose();
  }

  bool get _followsBig => switch (widget.state) {
    IslandMaterialState.big => false,
    IslandMaterialState.small => _controller.islandMaterialSmallFollowBig,
    IslandMaterialState.expand => _controller.islandMaterialExpandFollowBig,
  };

  Future<void> _clearBackgroundConflict() async {
    final futures = <Future<void>>[];
    switch (widget.state) {
      case IslandMaterialState.big:
        if (_controller.islandBgBigPath.isNotEmpty) {
          futures.add(_controller.setIslandBgBigPath(''));
        }
        if (_controller.islandMaterialSmallFollowBig &&
            _controller.islandBgSmallPath.isNotEmpty) {
          futures.add(_controller.setIslandBgSmallPath(''));
        }
        if (_controller.islandMaterialExpandFollowBig &&
            _controller.islandBgExpandPath.isNotEmpty) {
          futures.add(_controller.setIslandBgExpandPath(''));
        }
      case IslandMaterialState.small:
        if (_controller.islandBgSmallPath.isNotEmpty) {
          futures.add(_controller.setIslandBgSmallPath(''));
        }
      case IslandMaterialState.expand:
        if (_controller.islandBgExpandPath.isNotEmpty) {
          futures.add(_controller.setIslandBgExpandPath(''));
        }
    }
    if (futures.isEmpty) return;
    await Future.wait(futures);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            AppLocalizations.of(context)!.islandMaterialBackgroundConflict,
          ),
        ),
      );
    }
  }

  Future<void> _save(IslandMaterialConfig config) async {
    if (config.isCustom) await _clearBackgroundConflict();
    await _controller.setIslandMaterialConfig(widget.state, config);
  }

  IslandMaterialConfig _withType(
    IslandMaterialConfig config,
    IslandMaterialType type,
  ) {
    if (type == config.type) return config;
    if (type == IslandMaterialType.softGlass) {
      return const IslandMaterialConfig(type: IslandMaterialType.softGlass);
    }
    if (config.type == IslandMaterialType.softGlass ||
        config.type == IslandMaterialType.systemDefault) {
      return config.copyWith(
        type: type,
        blur: 80,
        blendColor: '#FFFFFF',
        blendOpacity: 13,
        edgeThickness: _controller.islandGlassEdgeWidth,
        refraction: _controller.islandGlassRefraction,
        reflectionStrength: _controller.islandGlassHighlight,
        darker: _controller.islandGlassShadow,
        lightDirection: _controller.islandGlassLightDirection,
        dispersion: _controller.islandGlassDispersion,
      );
    }
    return config.copyWith(type: type);
  }

  String _typeLabel(AppLocalizations l10n, IslandMaterialType type) =>
      switch (type) {
        IslandMaterialType.systemDefault => l10n.islandMaterialDefault,
        IslandMaterialType.gaussian => l10n.islandMaterialGaussian,
        IslandMaterialType.highlightGlass => l10n.islandMaterialHighlightGlass,
        IslandMaterialType.liquidGlass => l10n.islandMaterialLiquidGlass,
        IslandMaterialType.softGlass => l10n.islandMaterialSoftGlass,
      };

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, _) {
        final l10n = AppLocalizations.of(context)!;
        final config = _controller.materialConfigForState(widget.state);
        if (_colorController.text != config.blendColor) {
          _colorController.value = TextEditingValue(
            text: config.blendColor,
            selection: TextSelection.collapsed(
              offset: config.blendColor.length,
            ),
          );
        }
        final follow = _followsBig;
        final effective = follow ? _controller.islandMaterialBig : config;
        final isHyperOs3 = _controller.hyperOsMajorVersion == 3;
        final selectableTypes = IslandMaterialType.values
            .where(
              (type) => !isHyperOs3 || type != IslandMaterialType.softGlass,
            )
            .toList();
        final selectedType =
            isHyperOs3 && effective.type == IslandMaterialType.softGlass
            ? IslandMaterialType.systemDefault
            : effective.type;
        return ListView(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 32),
          children: [
            if (widget.state != IslandMaterialState.big) ...[
              Card(
                elevation: 0,
                child: SwitchListTile(
                  secondary: const Icon(Icons.link),
                  title: Text(l10n.islandMaterialFollowBig),
                  subtitle: Text(l10n.islandMaterialFollowBigSubtitle),
                  value: follow,
                  onChanged: InteractionHaptics.interceptToggle((value) async {
                    await _controller.setIslandMaterialFollowBig(
                      widget.state,
                      value,
                    );
                    if (value && _controller.islandMaterialBig.isCustom) {
                      await _clearBackgroundConflict();
                    }
                  }),
                ),
              ),
              const SizedBox(height: 12),
            ],
            Card(
              elevation: 0,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 12, 8),
                child: DropdownButtonFormField<IslandMaterialType>(
                  key: ValueKey(
                    '${widget.state.name}-${selectedType.value}-$follow',
                  ),
                  initialValue: selectedType,
                  isExpanded: true,
                  decoration: InputDecoration(
                    labelText: l10n.islandMaterialType,
                    border: InputBorder.none,
                    filled: false,
                    prefixIcon: const Icon(Icons.blur_circular),
                  ),
                  items: selectableTypes
                      .map(
                        (type) => DropdownMenuItem(
                          value: type,
                          child: Text(_typeLabel(l10n, type)),
                        ),
                      )
                      .toList(),
                  onChanged: follow
                      ? null
                      : (type) {
                          if (type != null) _save(_withType(config, type));
                        },
                ),
              ),
            ),
            if (follow)
              Padding(
                padding: const EdgeInsets.all(16),
                child: Text(
                  '${l10n.islandMaterialType}：${_typeLabel(l10n, selectedType)}',
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
                ),
              )
            else if (config.isCustom &&
                !(isHyperOs3 &&
                    config.type == IslandMaterialType.softGlass)) ...[
              _SectionCard(
                title: l10n.islandMaterialBlurSection,
                icon: Icons.blur_on,
                collapsible: config.type == IslandMaterialType.softGlass,
                children: [
                  _ParameterSlider(
                    label: l10n.islandMaterialBlur,
                    value: config.blur,
                    min: 0,
                    max: 100,
                    onChanged: (value) => _save(config.copyWith(blur: value)),
                  ),
                ],
              ),
              if (config.type == IslandMaterialType.highlightGlass ||
                  config.type == IslandMaterialType.liquidGlass)
                _SectionCard(
                  title: l10n.islandGlassCustomize,
                  icon: Icons.auto_awesome,
                  children: [
                    _ParameterSlider(
                      label: l10n.islandGlassEdgeWidth,
                      value: config.edgeThickness,
                      min: 4,
                      max: 40,
                      onChanged: (v) =>
                          _save(config.copyWith(edgeThickness: v)),
                    ),
                    _ParameterSlider(
                      label: l10n.islandGlassRefraction,
                      value: config.refraction,
                      min: 0,
                      max: 40,
                      onChanged: (v) => _save(config.copyWith(refraction: v)),
                    ),
                    _ParameterSlider(
                      label: l10n.islandGlassHighlight,
                      value: config.reflectionStrength.clamp(0, 100),
                      min: 0,
                      max: 100,
                      onChanged: (v) =>
                          _save(config.copyWith(reflectionStrength: v)),
                    ),
                    _ParameterSlider(
                      label: l10n.islandGlassShadow,
                      value: config.darker,
                      min: 0,
                      max: 100,
                      onChanged: (v) => _save(config.copyWith(darker: v)),
                    ),
                    _ParameterSlider(
                      label: l10n.islandGlassLightDirection,
                      value: config.lightDirection,
                      min: 0,
                      max: 359,
                      onChanged: (v) =>
                          _save(config.copyWith(lightDirection: v)),
                    ),
                    _ParameterSlider(
                      label: l10n.islandGlassDispersion,
                      value: config.dispersion,
                      min: 0,
                      max: 100,
                      onChanged: (v) => _save(config.copyWith(dispersion: v)),
                    ),
                  ],
                ),
              if (config.type == IslandMaterialType.softGlass) ...[
                _SectionCard(
                  title: l10n.islandMaterialLightingSection,
                  icon: Icons.light_mode_outlined,
                  collapsible: true,
                  children: [
                    _DecimalParameterSlider(
                      label: l10n.islandMaterialSoftLight,
                      value: config.softLight,
                      onChanged: (v) => _save(config.copyWith(softLight: v)),
                    ),
                    _DecimalParameterSlider(
                      label: l10n.islandMaterialSaturation,
                      value: config.saturation,
                      onChanged: (v) => _save(config.copyWith(saturation: v)),
                    ),
                    _DecimalParameterSlider(
                      label: l10n.islandMaterialBrightness,
                      value: config.brightness,
                      onChanged: (v) => _save(config.copyWith(brightness: v)),
                    ),
                    _DecimalParameterSlider(
                      label: l10n.islandMaterialDarker,
                      value: config.softDarker,
                      onChanged: (v) => _save(config.copyWith(softDarker: v)),
                    ),
                    _DecimalParameterSlider(
                      label: l10n.islandMaterialTransparency,
                      value: config.transparency,
                      onChanged: (v) => _save(config.copyWith(transparency: v)),
                    ),
                    _DecimalParameterSlider(
                      label: l10n.islandMaterialBurn,
                      value: config.burn,
                      onChanged: (v) => _save(config.copyWith(burn: v)),
                    ),
                  ],
                ),
                _SectionCard(
                  title: l10n.islandMaterialRefractionSection,
                  icon: Icons.auto_awesome,
                  collapsible: true,
                  children: [
                    _DecimalParameterSlider(
                      label: l10n.islandMaterialRefraction,
                      value: config.softRefraction,
                      onChanged: (v) =>
                          _save(config.copyWith(softRefraction: v)),
                    ),
                    _DecimalParameterSlider(
                      label: l10n.islandMaterialEdgeThickness,
                      value: config.softEdgeThickness,
                      onChanged: (v) =>
                          _save(config.copyWith(softEdgeThickness: v)),
                    ),
                    _DecimalParameterSlider(
                      label: l10n.islandMaterialReflectionStrength,
                      value: config.softReflection,
                      onChanged: (v) =>
                          _save(config.copyWith(softReflection: v)),
                    ),
                    _DecimalParameterSlider(
                      label: l10n.islandMaterialDirectionalLightIntensity,
                      value: config.directionalLightIntensity,
                      onChanged: (v) =>
                          _save(config.copyWith(directionalLightIntensity: v)),
                    ),
                  ],
                ),
                _SectionCard(
                  title: l10n.islandMaterialBackgroundSection,
                  icon: Icons.wallpaper_outlined,
                  collapsible: true,
                  children: [
                    _DecimalParameterSlider(
                      label: l10n.islandMaterialBackgroundSaturation,
                      value: config.backgroundSaturation,
                      onChanged: (v) =>
                          _save(config.copyWith(backgroundSaturation: v)),
                    ),
                    _DecimalParameterSlider(
                      label: l10n.islandMaterialBackgroundBrightness,
                      value: config.backgroundBrightness,
                      onChanged: (v) =>
                          _save(config.copyWith(backgroundBrightness: v)),
                    ),
                  ],
                ),
              ],
              _SectionCard(
                title: l10n.islandMaterialBlendSection,
                icon: Icons.palette_outlined,
                collapsible: config.type == IslandMaterialType.softGlass,
                children: [
                  Padding(
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
                    child: ColorValueField(
                      controller: _colorController,
                      decoration: InputDecoration(
                        labelText: l10n.islandMaterialBlendColor,
                      ),
                      previewColor: parseHexColor(config.blendColor),
                      previewFallbackColor: Colors.black,
                      onChanged: (value) {
                        if (parseHexColor(value) != null) {
                          _save(config.copyWith(blendColor: value));
                        }
                      },
                      onPickColor: () async {
                        final color = await showColorPickerDialog(
                          context,
                          initialHex: config.blendColor,
                          title: l10n.islandMaterialBlendColor,
                          enableAlpha: false,
                        );
                        if (color != null) {
                          await _save(
                            config.copyWith(blendColor: colorToHex(color)),
                          );
                        }
                      },
                    ),
                  ),
                  _ParameterSlider(
                    label: l10n.islandMaterialBlendOpacity,
                    value: config.blendOpacity,
                    min: 0,
                    max: 100,
                    onChanged: (v) => _save(config.copyWith(blendOpacity: v)),
                  ),
                  if (config.type == IslandMaterialType.softGlass)
                    SwitchListTile(
                      title: Text(l10n.islandMaterialHighlightSwitch),
                      value: config.highlight,
                      onChanged: InteractionHaptics.interceptToggle(
                        (value) => _save(config.copyWith(highlight: value)),
                      ),
                    ),
                ],
              ),
            ],
          ],
        );
      },
    );
  }
}

class _SectionCard extends StatelessWidget {
  const _SectionCard({
    required this.title,
    required this.icon,
    required this.children,
    this.collapsible = false,
  });
  final String title;
  final IconData icon;
  final List<Widget> children;
  final bool collapsible;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 12),
    child: Card(
      elevation: 0,
      clipBehavior: Clip.antiAlias,
      child: collapsible
          ? ExpansionTile(
              leading: Icon(icon),
              title: Text(
                title,
                style: Theme.of(context).textTheme.titleMedium,
              ),
              initiallyExpanded: true,
              shape: const Border(),
              collapsedShape: const Border(),
              children: [
                const Divider(height: 1, indent: 16, endIndent: 16),
                ...children,
              ],
            )
          : Column(
              children: [
                ListTile(
                  leading: Icon(icon),
                  title: Text(
                    title,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
                const Divider(height: 1, indent: 16, endIndent: 16),
                ...children,
              ],
            ),
    ),
  );
}

class _ParameterSlider extends StatefulWidget {
  const _ParameterSlider({
    required this.label,
    required this.value,
    required this.min,
    required this.max,
    required this.onChanged,
  });
  final String label;
  final int value;
  final int min;
  final int max;
  final ValueChanged<int> onChanged;

  @override
  State<_ParameterSlider> createState() => _ParameterSliderState();
}

class _ParameterSliderState extends State<_ParameterSlider> {
  late double _value = widget.value.toDouble();

  @override
  void didUpdateWidget(covariant _ParameterSlider oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.value != widget.value) _value = widget.value.toDouble();
  }

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 10, 16, 8),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(child: Text(widget.label)),
            Text('${_value.round()}'),
          ],
        ),
        SliderTheme(
          data: ModernSliderTheme.theme(context),
          child: Slider(
            value: _value.clamp(widget.min.toDouble(), widget.max.toDouble()),
            min: widget.min.toDouble(),
            max: widget.max.toDouble(),
            label: '${_value.round()}',
            onChanged: (value) => setState(() => _value = value),
            onChangeEnd: (value) => widget.onChanged(value.round()),
          ),
        ),
      ],
    ),
  );
}

class _DecimalParameterSlider extends StatefulWidget {
  const _DecimalParameterSlider({
    required this.label,
    required this.value,
    required this.onChanged,
  });

  final String label;
  final double value;
  final ValueChanged<double> onChanged;

  @override
  State<_DecimalParameterSlider> createState() =>
      _DecimalParameterSliderState();
}

class _DecimalParameterSliderState extends State<_DecimalParameterSlider> {
  late double _value = widget.value;

  @override
  void didUpdateWidget(covariant _DecimalParameterSlider oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.value != widget.value) _value = widget.value;
  }

  String _display(double value) {
    final hundredths = value.toStringAsFixed(2);
    if (!hundredths.endsWith('0')) return hundredths;
    return value.toStringAsFixed(1);
  }

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 10, 16, 8),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(child: Text(widget.label)),
            Text(_display(_value)),
          ],
        ),
        SliderTheme(
          data: ModernSliderTheme.theme(context),
          child: Slider(
            value: _value.clamp(-50, 50),
            min: -50,
            max: 50,
            divisions: 1000,
            label: _display(_value),
            onChanged: (value) => setState(() => _value = value),
            onChangeEnd: (value) => widget.onChanged((value * 10).round() / 10),
          ),
        ),
      ],
    ),
  );
}
