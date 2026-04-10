import 'package:flutter/material.dart';

class ColorValueField extends StatelessWidget {
  const ColorValueField({
    super.key,
    required this.controller,
    required this.decoration,
    required this.previewColor,
    required this.previewFallbackColor,
    required this.onChanged,
    required this.onPickColor,
    this.enabled = true,
    this.readOnly = false,
    this.hintText = '#RRGGBB',
    this.onClear,
    this.presetItems,
    this.onPresetSelected,
  });

  final TextEditingController controller;
  final InputDecoration decoration;
  final Color? previewColor;
  final Color previewFallbackColor;
  final ValueChanged<String>? onChanged;
  final Future<void> Function() onPickColor;
  final bool enabled;
  final bool readOnly;
  final String hintText;
  final VoidCallback? onClear;
  final List<DropdownMenuItem<String>>? presetItems;
  final ValueChanged<String>? onPresetSelected;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final canEdit = enabled && !readOnly;
    return Row(
      children: [
        Expanded(
          child: TextFormField(
            controller: controller,
            enabled: enabled,
            readOnly: readOnly,
            textInputAction: TextInputAction.done,
            scrollPadding: EdgeInsets.zero,
            onTapOutside: (_) => FocusManager.instance.primaryFocus?.unfocus(),
            decoration: decoration.copyWith(
              hintText: hintText,
              suffixIcon:
                  canEdit && controller.text.isNotEmpty && onClear != null
                  ? IconButton(
                      icon: const Icon(Icons.clear, size: 18),
                      onPressed: onClear,
                    )
                  : null,
            ),
            onChanged: canEdit ? onChanged : null,
          ),
        ),
        if (presetItems != null && onPresetSelected != null) ...[
          const SizedBox(width: 8),
          DropdownButton<String>(
            value: null,
            hint: const Icon(Icons.palette_outlined, size: 18),
            items: presetItems,
            onChanged: canEdit
                ? (v) {
                    if (v != null) onPresetSelected!(v);
                  }
                : null,
          ),
        ],
        const SizedBox(width: 8),
        SizedBox(
          width: 48,
          height: 48,
          child: Stack(
            children: [
              Positioned.fill(
                child: GestureDetector(
                  onTap: canEdit ? onPickColor : null,
                  child: Container(
                    decoration: BoxDecoration(
                      color: previewColor ?? previewFallbackColor,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: cs.outline),
                    ),
                  ),
                ),
              ),
              if (canEdit && controller.text.isNotEmpty && onClear != null)
                Positioned(
                  right: 2,
                  top: 2,
                  child: Material(
                    color: Colors.black54,
                    shape: const CircleBorder(),
                    child: InkWell(
                      customBorder: const CircleBorder(),
                      onTap: onClear,
                      child: const Padding(
                        padding: EdgeInsets.all(2),
                        child: Icon(Icons.close, size: 12, color: Colors.white),
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}
