import 'package:flutter/material.dart';

class SystemFontWeight {
  const SystemFontWeight._();

  static int adjustment = 0;

  static FontWeight resolve([FontWeight? base]) {
    final baseValue = ((base ?? FontWeight.w400).index + 1) * 100;
    final adjusted = (baseValue + adjustment).clamp(100, 900);
    final index = ((adjusted / 100).round() - 1).clamp(0, 8).toInt();
    return FontWeight.values[index];
  }

  static TextStyle style(TextStyle style, [FontWeight? base]) {
    return style.copyWith(fontWeight: resolve(base ?? style.fontWeight));
  }
}
