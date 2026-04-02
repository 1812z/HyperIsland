import 'package:flutter/material.dart';

/// 现代风格滑块主题配置类
class ModernSliderTheme {
  /// 基于当前上下文颜色方案生成滑块主题数据
  static SliderThemeData theme(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return SliderTheme.of(context).copyWith(
      trackHeight: 20, // 轨道高度
      activeTrackColor: cs.primary, // 激活部分轨道颜色
      inactiveTrackColor: cs.primaryContainer.withValues(alpha: 0.5), // 未激活部分轨道颜色（半透明）
      thumbColor: cs.primary, // 滑块按钮颜色
      thumbShape: const ModernSliderThumbShape(), // 自定义滑块形状
      trackShape: const ModernSliderTrackShape(), // 自定义轨道形状
      overlayColor: cs.primary.withValues(alpha: 0.1), // 触摸反馈层颜色
      valueIndicatorColor: cs.primary, // 数值提示气泡颜色
      valueIndicatorTextStyle: const TextStyle(color: Colors.white), // 数值提示文字样式
      tickMarkShape: SliderTickMarkShape.noTickMark, // 隐藏刻度标记
    );
  }
}

/// 自定义轨道形状：绘制圆角矩形轨道，以滑块为中心分割激活/未激活区域
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
    // 若轨道高度无效则直接返回
    if (sliderTheme.trackHeight == null || sliderTheme.trackHeight! <= 0) return;

    // 根据启用状态插值计算颜色（支持禁用态过渡动画）
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

    // 获取轨道标准矩形区域
    final Rect trackRect = getPreferredRect(
      parentBox: parentBox,
      offset: offset,
      sliderTheme: sliderTheme,
      isDiscrete: isDiscrete,
      isEnabled: isEnabled,
    );

    // 计算圆角半径（高度的一半实现全圆角）
    final Radius radius = Radius.circular(trackRect.height / 2);

    // 绘制左侧激活轨道（从起点到滑块中心）
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

    // 绘制右侧未激活轨道（从滑块中心到终点）
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

/// 自定义滑块形状：垂直胶囊形按钮
class ModernSliderThumbShape extends SliderComponentShape {
  final double thumbWidth;  // 滑块宽度
  final double thumbHeight; // 滑块高度

  const ModernSliderThumbShape({
    this.thumbWidth = 4,   // 默认细条状
    this.thumbHeight = 32, // 默认较高
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

    // 绘制圆角矩形滑块（圆角半径为宽度一半，形成胶囊效果）
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