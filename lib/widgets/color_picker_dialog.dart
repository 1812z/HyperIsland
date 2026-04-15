import 'package:flutter/material.dart';

import '../l10n/generated/app_localizations.dart';
import '../controllers/whitelist_controller.dart';

const kDynamicLabelFocus = 'focus';
const kDynamicLabelIsland = 'island';

bool isDynamicColorMode(String? value) {
  return value == kTriOptOn || value == 'dark' || value == 'darker';
}

bool resolvesDynamicColorMode(String? value, bool defaultEnabled) {
  if (value == kTriOptDefault) return defaultEnabled;
  return isDynamicColorMode(value);
}

Color? parseHexColor(String? hex) {
  if (hex == null || hex.trim().isEmpty) return null;
  final cleaned = hex.trim().replaceFirst('#', '');
  if (cleaned.length != 6 && cleaned.length != 8) return null;
  final value = int.tryParse(cleaned, radix: 16);
  if (value == null) return null;
  return cleaned.length == 6 ? Color(value).withAlpha(255) : Color(value);
}

String colorToHex(Color color) {
  return '#${color.toARGB32().toRadixString(16).padLeft(8, '0').substring(2).toUpperCase()}';
}

String colorToArgbHex(Color color) {
  return '#${color.toARGB32().toRadixString(16).padLeft(8, '0').toUpperCase()}';
}

Future<Color?> showColorPickerDialog(
  BuildContext context, {
  String? initialHex,
  String? title,
  bool enableAlpha = true,
}) async {
  final l10n = AppLocalizations.of(context)!;
  final initialColor =
      parseHexColor(initialHex) ?? Theme.of(context).colorScheme.primary;
  var selectedColor = HSVColor.fromColor(initialColor);
  var alpha = initialColor.a * 100;

  return showDialog<Color>(
    context: context,
    builder: (ctx) => StatefulBuilder(
      builder: (context, setDialogState) {
        final previewColor = selectedColor.toColor().withValues(
          alpha: enableAlpha ? alpha / 100 : 1,
        );
        return AlertDialog(
          title: Text(title ?? l10n.highlightColorLabel),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 80,
                height: 80,
                decoration: BoxDecoration(
                  color: previewColor,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: Theme.of(ctx).colorScheme.outline),
                ),
              ),
              const SizedBox(height: 16),
              _ColorPickerSlider(
                label: l10n.colorHue,
                value: selectedColor.hue,
                max: 360,
                onChanged: (v) => setDialogState(
                  () => selectedColor = selectedColor.withHue(v),
                ),
                gradientColors: List.generate(
                  7,
                  (i) => HSVColor.fromAHSV(1, i * 60, 1, 1).toColor(),
                ),
              ),
              const SizedBox(height: 12),
              _ColorPickerSlider(
                label: l10n.colorSaturation,
                value: selectedColor.saturation * 100,
                max: 100,
                onChanged: (v) => setDialogState(
                  () => selectedColor = selectedColor.withSaturation(v / 100),
                ),
                gradientColors: [
                  HSVColor.fromAHSV(1, selectedColor.hue, 0, 1).toColor(),
                  HSVColor.fromAHSV(1, selectedColor.hue, 1, 1).toColor(),
                ],
              ),
              const SizedBox(height: 12),
              _ColorPickerSlider(
                label: l10n.colorBrightness,
                value: selectedColor.value * 100,
                max: 100,
                onChanged: (v) => setDialogState(
                  () => selectedColor = selectedColor.withValue(v / 100),
                ),
                gradientColors: [
                  HSVColor.fromAHSV(
                    1,
                    selectedColor.hue,
                    selectedColor.saturation,
                    0,
                  ).toColor(),
                  HSVColor.fromAHSV(
                    1,
                    selectedColor.hue,
                    selectedColor.saturation,
                    1,
                  ).toColor(),
                ],
              ),
              if (enableAlpha) ...[
                const SizedBox(height: 12),
                _ColorPickerSlider(
                  label: l10n.colorOpacity,
                  value: alpha,
                  max: 100,
                  onChanged: (v) => setDialogState(() => alpha = v),
                  gradientColors: [
                    selectedColor.toColor().withValues(alpha: 0),
                    selectedColor.toColor().withValues(alpha: 1),
                  ],
                ),
              ],
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: Text(l10n.cancel),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(ctx, previewColor),
              child: Text(l10n.apply),
            ),
          ],
        );
      },
    ),
  );
}

class _ColorPickerSlider extends StatelessWidget {
  const _ColorPickerSlider({
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
        Container(
          height: 36,
          decoration: BoxDecoration(
            gradient: LinearGradient(colors: gradientColors),
            borderRadius: BorderRadius.circular(999),
          ),
          child: SliderTheme(
            data: SliderTheme.of(context).copyWith(
              trackHeight: 36,
              thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 10),
              overlayShape: const RoundSliderOverlayShape(overlayRadius: 18),
              activeTrackColor: Colors.transparent,
              inactiveTrackColor: Colors.transparent,
            ),
            child: Slider(value: value, min: 0, max: max, onChanged: onChanged),
          ),
        ),
      ],
    );
  }
}
