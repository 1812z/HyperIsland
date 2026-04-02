import 'package:flutter/material.dart';

class ModernSliderTheme {
  static SliderThemeData theme(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return SliderTheme.of(context).copyWith(
      trackHeight: 20,
      activeTrackColor: cs.primary,
      inactiveTrackColor: cs.primaryContainer.withValues(alpha: 0.5),
      thumbColor: cs.primary,
      thumbShape: const ModernSliderThumbShape(),
      trackShape: const ModernSliderTrackShape(),
      overlayColor: cs.primary.withValues(alpha: 0.1),
      valueIndicatorColor: cs.primary,
      valueIndicatorTextStyle: const TextStyle(color: Colors.white),
      tickMarkShape: SliderTickMarkShape.noTickMark,
    );
  }
}

class ModernSliderTrackShape extends SliderTrackShape with BaseSliderTrackShape {
  const ModernSliderTrackShape();

  @override
  void paint(
    PaintingContext context,
    Offset offset, {
    required RenderBox parentBox,
    required SliderThemeData sliderTheme,
    required Animation<double> enableAnimation,
    required TextDirection textDirection,
    required Offset thumbCenter,
    Offset? secondaryOffset,
    bool isDiscrete = false,
    bool isEnabled = false,
    double additionalActiveTrackHeight = 0,
  }) {
    if (sliderTheme.trackHeight == null || sliderTheme.trackHeight! <= 0) return;

    final ColorTween activeTrackColorTween = ColorTween(
      begin: sliderTheme.disabledActiveTrackColor,
      end: sliderTheme.activeTrackColor,
    );
    final ColorTween inactiveTrackColorTween = ColorTween(
      begin: sliderTheme.disabledInactiveTrackColor,
      end: sliderTheme.inactiveTrackColor,
    );
    final Paint activePaint = Paint()
      ..color = activeTrackColorTween.evaluate(enableAnimation)!;
    final Paint inactivePaint = Paint()
      ..color = inactiveTrackColorTween.evaluate(enableAnimation)!;

    final Rect trackRect = getPreferredRect(
      parentBox: parentBox,
      offset: offset,
      sliderTheme: sliderTheme,
      isDiscrete: isDiscrete,
      isEnabled: isEnabled,
    );

    final Radius radius = Radius.circular(trackRect.height / 2);

    // Active track (left of thumb)
    context.canvas.drawRRect(
      RRect.fromLTRBAndCorners(
        trackRect.left,
        trackRect.top,
        thumbCenter.dx,
        trackRect.bottom,
        topLeft: radius,
        bottomLeft: radius,
      ),
      activePaint,
    );

    // Inactive track (right of thumb)
    context.canvas.drawRRect(
      RRect.fromLTRBAndCorners(
        thumbCenter.dx,
        trackRect.top,
        trackRect.right,
        trackRect.bottom,
        topRight: radius,
        bottomRight: radius,
      ),
      inactivePaint,
    );
  }
}

class ModernSliderThumbShape extends SliderComponentShape {
  final double thumbWidth;
  final double thumbHeight;

  const ModernSliderThumbShape({
    this.thumbWidth = 4,
    this.thumbHeight = 32,
  });

  @override
  Size getPreferredSize(bool isEnabled, bool isDiscrete) {
    return Size(thumbWidth, thumbHeight);
  }

  @override
  void paint(
    PaintingContext context,
    Offset center, {
    required Animation<double> activationAnimation,
    required Animation<double> enableAnimation,
    required bool isDiscrete,
    required TextPainter labelPainter,
    required RenderBox parentBox,
    required SliderThemeData sliderTheme,
    required TextDirection textDirection,
    required double value,
    required double textScaleFactor,
    required Size sizeWithOverflow,
  }) {
    final Canvas canvas = context.canvas;

    final paint = Paint()
      ..color = sliderTheme.thumbColor ?? Colors.black
      ..style = PaintingStyle.fill;

    canvas.drawRRect(
      RRect.fromRectAndRadius(
        Rect.fromCenter(
          center: center,
          width: thumbWidth,
          height: thumbHeight,
        ),
        Radius.circular(thumbWidth / 2),
      ),
      paint,
    );
  }
}
