import 'package:flutter/material.dart';
import '../../controllers/settings_controller.dart';
import '../../l10n/generated/app_localizations.dart';
import '../../services/interaction_haptics.dart';
import '../../widgets/blur_app_bar.dart';
import '../../widgets/color_picker_dialog.dart';

class ThemePage extends StatefulWidget {
  const ThemePage({super.key});

  @override
  State<ThemePage> createState() => _ThemePageState();
}

class _ThemePageState extends State<ThemePage> {
  final _ctrl = SettingsController.instance;
  late int _uiStateHash;

  static const _defaultSeedColor = 0xFF6750A4;

  static const _presetColors = <int>[
    0xFF6750A4, // Material Default (Purple)
    0xFFB7254D, // Rose
    0xFF984061, // Mauve
    0xFF7C5800, // Mellow Apricot
    0xFF006B5E, // Teal
    0xFF00658E, // Cerulean
    0xFF4B6B4F, // Forest
    0xFF7B5800, // Amber
    0xFF6B4FA2, // Lavender
    0xFF9B4D00, // Burnt Orange
    0xFF4A6B8A, // Steel Blue
    0xFF8B5E3C, // Caramel
  ];

  int _buildUiStateHash() => Object.hashAll([
        _ctrl.themeSeedColor,
        _ctrl.themeMode,
        _ctrl.blurBars,
      ]);

  void _onChanged() {
    if (!mounted) return;
    final nextHash = _buildUiStateHash();
    if (nextHash == _uiStateHash) return;
    setState(() => _uiStateHash = nextHash);
  }

  @override
  void initState() {
    super.initState();
    _uiStateHash = _buildUiStateHash();
    _ctrl.addListener(_onChanged);
  }

  @override
  void dispose() {
    _ctrl.removeListener(_onChanged);
    super.dispose();
  }

  // --- 颜色模式 ---
  String _themeModeLabel(AppLocalizations l10n) => switch (_ctrl.themeMode) {
        ThemeMode.light => l10n.themeModeLight,
        ThemeMode.dark => l10n.themeModeDark,
        ThemeMode.system => l10n.themeModeSystem,
      };

  Future<void> _showThemeModeDialog(AppLocalizations l10n) async {
    if (!mounted) return;
    final result = await showDialog<ThemeMode>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: Text(l10n.themeModeTitle),
        children: [
          RadioGroup<ThemeMode>(
            groupValue: _ctrl.themeMode,
            onChanged: (v) => Navigator.of(ctx).pop(v),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                RadioListTile<ThemeMode>(
                  title: Text(l10n.themeModeSystem),
                  value: ThemeMode.system,
                ),
                RadioListTile<ThemeMode>(
                  title: Text(l10n.themeModeLight),
                  value: ThemeMode.light,
                ),
                RadioListTile<ThemeMode>(
                  title: Text(l10n.themeModeDark),
                  value: ThemeMode.dark,
                ),
              ],
            ),
          ),
        ],
      ),
    );
    if (result != null && mounted) {
      _ctrl.setThemeMode(result);
    }
  }

  Future<void> _pickSeedColor() async {
    final currentHex = colorToHex(Color(_ctrl.themeSeedColor));
    final color = await showColorPickerDialog(
      context,
      initialHex: currentHex,
      title: AppLocalizations.of(context)!.themeSeedColorTitle,
      enableAlpha: false,
    );
    if (color != null && mounted) {
      _ctrl.setThemeSeedColor(color.toARGB32());
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
    final titleStyle = Theme.of(context).textTheme.titleMedium;
    final seedColor = Color(_ctrl.themeSeedColor);

    return Scaffold(
      backgroundColor: cs.surface,
      body: BlurAppBarHost(
        title: l10n.themePageTitle,
        physics: const ClampingScrollPhysics(),
        slivers: [
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            sliver: SliverList(
              delegate: SliverChildListDelegate(
                [
                  const SizedBox(height: 8),

                  // --- 主题色 Card ---
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 4,
                          ),
                          shape: const RoundedRectangleBorder(
                            borderRadius: BorderRadius.vertical(
                              top: Radius.circular(16),
                            ),
                          ),
                          title: Text(l10n.themeSeedColorTitle,
                              style: titleStyle),
                          subtitle: Text(l10n.themeSeedColorSubtitle),
                          trailing: _ColorDot(color: seedColor),
                          onTap:
                              InteractionHaptics.interceptButton(_pickSeedColor),
                        ),
                        // 预设色板
                        Padding(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 8,
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                l10n.presetColors,
                                style: Theme.of(context).textTheme.bodySmall,
                              ),
                              const SizedBox(height: 8),
                              Wrap(
                                spacing: 8,
                                runSpacing: 8,
                                children: _presetColors.map((colorValue) {
                                  final color = Color(colorValue);
                                  final isSelected =
                                      _ctrl.themeSeedColor == colorValue;
                                  return _PresetColorChip(
                                    color: color,
                                    selected: isSelected,
                                    onTap: () => _ctrl
                                        .setThemeSeedColor(colorValue),
                                  );
                                }).toList(),
                              ),
                            ],
                          ),
                        ),
                        // 恢复默认
                        if (_ctrl.themeSeedColor != _defaultSeedColor)
                          Padding(
                            padding: const EdgeInsets.only(
                              left: 16,
                              right: 16,
                              bottom: 12,
                            ),
                            child: Align(
                              alignment: Alignment.centerRight,
                              child: TextButton.icon(
                                onPressed: InteractionHaptics.interceptButton(
                                  () => _ctrl
                                      .setThemeSeedColor(_defaultSeedColor),
                                ),
                                icon: const Icon(Icons.refresh, size: 16),
                                label: Text(l10n.themeResetColor),
                              ),
                            ),
                          )
                        else
                          const SizedBox(height: 8),
                      ],
                    ),
                  ),

                  const SizedBox(height: 8),

                  // --- 毛玻璃效果 Card ---
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    clipBehavior: Clip.antiAlias,
                    child: SwitchListTile(
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 4,
                      ),
                      secondary: const Icon(Icons.blur_on),
                      title: Text(l10n.blurBarsTitle, style: titleStyle),
                      subtitle: Text(l10n.blurBarsSubtitle),
                      value: _ctrl.blurBars,
                      onChanged: InteractionHaptics.interceptToggle(
                        (value) => _ctrl.setBlurBars(value),
                        force: true,
                      ),
                    ),
                  ),

                  const SizedBox(height: 8),

                  // --- 颜色模式 Card ---
                  Card(
                    elevation: 0,
                    color: cs.surfaceContainerHighest,
                    clipBehavior: Clip.antiAlias,
                    child: ListTile(
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 16,
                        vertical: 4,
                      ),
                      leading: const Icon(Icons.dark_mode_outlined),
                      title: Text(l10n.themeModeTitle, style: titleStyle),
                      subtitle: Text(_themeModeLabel(l10n)),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: InteractionHaptics.interceptButton(
                        () => _showThemeModeDialog(l10n),
                      ),
                    ),
                  ),

                  const SizedBox(height: 32),
                ],
                addAutomaticKeepAlives: false,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ColorDot extends StatelessWidget {
  const _ColorDot({required this.color});

  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 32,
      height: 32,
      decoration: BoxDecoration(
        color: color,
        shape: BoxShape.circle,
        border: Border.all(
          color: Theme.of(context).colorScheme.outline,
          width: 1.5,
        ),
      ),
    );
  }
}

class _PresetColorChip extends StatelessWidget {
  const _PresetColorChip({
    required this.color,
    required this.selected,
    required this.onTap,
  });

  final Color color;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: color,
      shape: const CircleBorder(),
      child: InkWell(
        onTap: InteractionHaptics.interceptButton(onTap),
        customBorder: const CircleBorder(),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          width: 40,
          height: 40,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            border: selected
                ? Border.all(
                    color: Theme.of(context).colorScheme.onSurface,
                    width: 3,
                  )
                : null,
          ),
          child: selected
              ? Icon(
                  Icons.check,
                  size: 20,
                  color: ThemeData.estimateBrightnessForColor(color) ==
                          Brightness.light
                      ? Colors.black
                      : Colors.white,
                )
              : null,
        ),
      ),
    );
  }
}
