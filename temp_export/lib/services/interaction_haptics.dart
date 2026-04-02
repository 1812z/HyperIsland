import 'dart:async';

import 'package:flutter/services.dart';

import '../controllers/settings_controller.dart';

class InteractionHaptics {
  static const MethodChannel _channel = MethodChannel(
    'io.github.hyperisland/haptics',
  );

  static DateTime? _lastSliderTickAt;

  static bool get _enabled => SettingsController.instance.interactionHaptics;

  static Future<void> button({bool force = false}) async {
    if (!force && !_enabled) return;
    await _invoke('button');
  }

  static Future<void> toggle({bool force = false}) async {
    if (!force && !_enabled) return;
    await _invoke('toggle');
  }

  static Future<void> sliderTick({bool force = false}) async {
    if (!force && !_enabled) return;
    final now = DateTime.now();
    if (_lastSliderTickAt != null &&
        now.difference(_lastSliderTickAt!) < const Duration(milliseconds: 32)) {
      return;
    }
    _lastSliderTickAt = now;
    await _invoke('sliderTick');
  }

  static Future<void> _invoke(String method) async {
    try {
      await _channel.invokeMethod<void>(method);
    } catch (_) {
      switch (method) {
        case 'toggle':
          await HapticFeedback.selectionClick();
          break;
        case 'sliderTick':
          await HapticFeedback.selectionClick();
          break;
        default:
          await HapticFeedback.lightImpact();
      }
    }
  }
}
