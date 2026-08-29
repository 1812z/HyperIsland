import 'dart:async';

import 'package:flutter/foundation.dart';

class InteractionHaptics {
  static Future<void> button({bool force = false}) async {}

  static Future<void> toggle({bool force = false}) async {}

  static Future<void> sliderTick({bool force = false}) async {}

  static VoidCallback? interceptButton(
    FutureOr<void> Function()? onPressed, {
    bool force = false,
  }) {
    if (onPressed == null) return null;
    return () {
      final result = onPressed();
      if (result is Future<void>) unawaited(result);
    };
  }

  static ValueChanged<bool>? interceptToggle(
    FutureOr<void> Function(bool value)? onChanged, {
    bool force = false,
  }) {
    if (onChanged == null) return null;
    return (value) {
      final result = onChanged(value);
      if (result is Future<void>) unawaited(result);
    };
  }

  static ValueChanged<bool?>? interceptCheckbox(
    FutureOr<void> Function(bool value)? onChanged, {
    bool force = false,
  }) {
    if (onChanged == null) return null;
    return (value) {
      if (value == null) return;
      final result = onChanged(value);
      if (result is Future<void>) unawaited(result);
    };
  }

  static ValueChanged<T?>? interceptDropdown<T>(
    FutureOr<void> Function(T? value)? onChanged, {
    bool force = false,
  }) {
    if (onChanged == null) return null;
    return (value) {
      final result = onChanged(value);
      if (result is Future<void>) unawaited(result);
    };
  }

  static ValueChanged<double>? interceptSlider(
    ValueChanged<double>? onChanged, {
    bool force = false,
  }) {
    return onChanged;
  }
}
