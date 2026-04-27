import 'dart:io';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import '../l10n/generated/app_localizations.dart';
import '../services/island_background_service.dart';
import 'modern_slider.dart';

/// Result of the background editing dialog.
class IslandBgEditResult {
  const IslandBgEditResult({
    required this.sourcePath,
    required this.blur,
    required this.brightness,
    required this.opacity,
    required this.cropRect,
  });

  final String sourcePath;
  final double blur; // 0..5
  final double brightness; // -1.0..1.0
  final double opacity; // 0.0..1.0
  final Rect? cropRect; // null = no crop
}

/// Shows a full-screen editing dialog after picking a background image.
/// Supports crop, gaussian blur, brightness, and opacity adjustments.
Future<IslandBgEditResult?> showIslandBgEditDialog({
  required BuildContext context,
  required String imagePath,
  required IslandBgType type,
}) {
  return showDialog<IslandBgEditResult>(
    context: context,
    barrierDismissible: false,
    builder: (ctx) => _IslandBgEditDialog(
      imagePath: imagePath,
      type: type,
    ),
  );
}

class _IslandBgEditDialog extends StatefulWidget {
  const _IslandBgEditDialog({
    required this.imagePath,
    required this.type,
  });

  final String imagePath;
  final IslandBgType type;

  @override
  State<_IslandBgEditDialog> createState() => _IslandBgEditDialogState();
}

class _IslandBgEditDialogState extends State<_IslandBgEditDialog> {
  late double _blur;
  late double _brightness;
  late double _opacity;

  // Crop state
  bool _cropping = false;
  Rect? _cropRect;

  // Image info
  Size _imageSize = Size.zero;
  bool _loading = true;

  // Processing state
  bool _processing = false;

  @override
  void initState() {
    super.initState();
    _blur = 0;
    _brightness = 0;
    _opacity = 1.0;
    _loadImageInfo();
  }

  Future<void> _loadImageInfo() async {
    final data = await File(widget.imagePath).readAsBytes();
    final codec = await ui.instantiateImageCodec(data);
    final frame = await codec.getNextFrame();
    if (mounted) {
      setState(() {
        _imageSize = Size(
          frame.image.width.toDouble(),
          frame.image.height.toDouble(),
        );
        _loading = false;
      });
      frame.image.dispose();
    }
  }

  String _typeLabel() {
    final l10n = AppLocalizations.of(context)!;
    return switch (widget.type) {
      IslandBgType.small => l10n.islandBgSmallTitle,
      IslandBgType.big => l10n.islandBgBigTitle,
      IslandBgType.expand => l10n.islandBgExpandTitle,
    };
  }

  /// GPU-accelerated image processing — near-instant since Flutter
  /// renders blur/brightness/opacity on the GPU, same as the preview.
  Future<void> _apply() async {
    final result = IslandBgEditResult(
      sourcePath: widget.imagePath,
      blur: _blur,
      brightness: _brightness,
      opacity: _opacity,
      cropRect: _cropRect,
    );

    final needsProcessing =
        _blur > 0 || _brightness != 0 || _opacity < 1.0 || _cropRect != null;

    if (!needsProcessing) {
      if (mounted) Navigator.of(context).pop(result);
      return;
    }

    setState(() => _processing = true);

    try {
      final tempPath = await _gpuProcessAndSave(result);
      if (!mounted) return;

      if (tempPath != null) {
        Navigator.of(context).pop(IslandBgEditResult(
          sourcePath: tempPath,
          blur: 0,
          brightness: 0,
          opacity: 1.0,
          cropRect: null,
        ));
      } else {
        setState(() => _processing = false);
        Navigator.of(context).pop(result);
      }
    } catch (e) {
      if (mounted) {
        setState(() => _processing = false);
        Navigator.of(context).pop(result);
      }
    }
  }

  /// Renders the image with all filters via Flutter's GPU pipeline
  /// and saves the result to a temp file. Near-instant.
  Future<String?> _gpuProcessAndSave(IslandBgEditResult result) async {
    final bytes = await File(result.sourcePath).readAsBytes();

    // Limit resolution to 2048px max (island backgrounds don't need 4K)
    const maxDim = 2048;
    final codec = await ui.instantiateImageCodec(
      bytes,
      allowUpscaling: false,
    );
    final frame = await codec.getNextFrame();
    var source = frame.image;
    final origW = source.width;
    final origH = source.height;

    // Downscale if needed
    if (origW > maxDim || origH > maxDim) {
      final scale = math.min(maxDim / origW, maxDim / origH);
      final targetW = (origW * scale).toInt();
      final targetH = (origH * scale).toInt();

      final rec = ui.PictureRecorder();
      final canvas = Canvas(rec, Rect.fromLTWH(0, 0, targetW.toDouble(), targetH.toDouble()));
      canvas.drawImageRect(
        source,
        Rect.fromLTWH(0, 0, origW.toDouble(), origH.toDouble()),
        Rect.fromLTWH(0, 0, targetW.toDouble(), targetH.toDouble()),
        Paint(),
      );
      source.dispose();
      final pic = rec.endRecording();
      source = await pic.toImage(targetW, targetH);
    }

    final w = source.width;
    final h = source.height;

    // Step 1: Composite onto black background (eliminates alpha edge artifacts for blur)
    ui.Image opaqueImage;
    if (result.blur > 0) {
      final rec = ui.PictureRecorder();
      final canvas = Canvas(rec, Rect.fromLTWH(0, 0, w.toDouble(), h.toDouble()));
      // Black fill
      canvas.drawRect(
        Rect.fromLTWH(0, 0, w.toDouble(), h.toDouble()),
        Paint()..color = const Color(0xFF000000),
      );
      // Image on top
      canvas.drawImage(source, Offset.zero, Paint());
      source.dispose();
      final pic = rec.endRecording();
      opaqueImage = await pic.toImage(w, h);
    } else {
      opaqueImage = source;
    }

    // Step 2: Apply blur + brightness + opacity in one GPU draw call
    final rec = ui.PictureRecorder();
    final canvas = Canvas(rec, Rect.fromLTWH(0, 0, w.toDouble(), h.toDouble()));
    final paint = Paint();

    // Blur
    if (result.blur > 0) {
      paint.imageFilter = ui.ImageFilter.blur(
        sigmaX: result.blur * 1.5,
        sigmaY: result.blur * 1.5,
      );
    }

    // Combined brightness + opacity via ColorFilter matrix
    if (result.brightness != 0 || result.opacity < 1.0) {
      final factor = 1.0 + result.brightness * 0.8;
      paint.colorFilter = ColorFilter.matrix(<double>[
        factor, 0, 0, 0, 0, //
        0, factor, 0, 0, 0, //
        0, 0, factor, 0, 0, //
        0, 0, 0, result.opacity, 0, //
      ]);
    }

    canvas.drawImage(opaqueImage, Offset.zero, paint);
    opaqueImage.dispose();

    final picture = rec.endRecording();
    final filteredImage = await picture.toImage(w, h);

    // Step 3: Encode to PNG
    final byteData = await filteredImage.toByteData(format: ui.ImageByteFormat.png);
    filteredImage.dispose();

    if (byteData == null) return null;

    // Step 4: Save to temp file
    final tempDir = await getTemporaryDirectory();
    final tempPath = p.join(
      tempDir.path,
      'hyperisland_bg_processed_${DateTime.now().millisecondsSinceEpoch}.png',
    );
    await File(tempPath).writeAsBytes(byteData.buffer.asUint8List());
    return tempPath;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final cs = Theme.of(context).colorScheme;

    return Dialog(
      backgroundColor: cs.surface,
      insetPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 24),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Title bar
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 16, 8, 0),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    l10n.islandBgEditTitle(_typeLabel()),
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.close),
                  onPressed:
                      _processing ? null : () => Navigator.of(context).pop(),
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),

          // Preview
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: _buildPreview(cs),
          ),
          const SizedBox(height: 12),

          // Sliders
          Flexible(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  _SliderRow(
                    label: l10n.islandBgBlurLabel,
                    value: _blur,
                    min: 0,
                    max: 5,
                    displayText:
                        _blur <= 0 ? l10n.islandBgOff : _blur.toStringAsFixed(1),
                    onChanged: _processing
                        ? null
                        : (v) => setState(() => _blur = v),
                  ),
                  const SizedBox(height: 8),
                  _SliderRow(
                    label: l10n.islandBgBrightnessLabel,
                    value: _brightness,
                    min: -1,
                    max: 1,
                    displayText: _brightness == 0
                        ? l10n.islandBgDefault
                        : (_brightness > 0 ? '+' : '') +
                            _brightness.toStringAsFixed(2),
                    onChanged: _processing
                        ? null
                        : (v) => setState(() => _brightness = v),
                  ),
                  const SizedBox(height: 8),
                  _SliderRow(
                    label: l10n.islandBgOpacityLabel,
                    value: _opacity,
                    min: 0,
                    max: 1,
                    displayText: (_opacity * 100).toStringAsFixed(0) + '%',
                    onChanged: _processing
                        ? null
                        : (v) => setState(() => _opacity = v),
                  ),
                ],
              ),
            ),
          ),

          // Action buttons
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 12, 20, 16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton(
                  onPressed:
                      _processing ? null : () => Navigator.of(context).pop(),
                  child: Text(l10n.cancel),
                ),
                const SizedBox(width: 8),
                FilledButton(
                  onPressed: _loading || _processing ? null : _apply,
                  child: _processing
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : Text(l10n.apply),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPreview(ColorScheme cs) {
    if (_loading) {
      return Container(
        height: 160,
        decoration: BoxDecoration(
          color: cs.surfaceContainerHigh,
          borderRadius: BorderRadius.circular(12),
        ),
        child:
            const Center(child: CircularProgressIndicator(strokeWidth: 2)),
      );
    }

    return Container(
      height: 160,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          return _FilteredImagePreview(
            imagePath: widget.imagePath,
            blur: _blur,
            brightness: _brightness,
            opacity: _opacity,
            containerSize: Size(constraints.maxWidth, 160),
          );
        },
      ),
    );
  }
}

/// Displays the image with real-time filter preview using Flutter's
/// built-in ImageFilter + ColorFiltered for zero-delay feedback.
class _FilteredImagePreview extends StatelessWidget {
  const _FilteredImagePreview({
    required this.imagePath,
    required this.blur,
    required this.brightness,
    required this.opacity,
    required this.containerSize,
  });

  final String imagePath;
  final double blur;
  final double brightness;
  final double opacity;
  final Size containerSize;

  @override
  Widget build(BuildContext context) {
    return Stack(
      fit: StackFit.expand,
      children: [
        Container(color: Colors.black),
        _buildFilteredImage(),
      ],
    );
  }

  Widget _buildFilteredImage() {
    // Downsample for fast GPU blur preview (container is only 160px tall)
    Widget image = Image.file(
      File(imagePath),
      fit: BoxFit.cover,
      width: containerSize.width,
      height: containerSize.height,
      cacheWidth: 360,
      cacheHeight: 200,
    );

    // Blur
    if (blur > 0) {
      final sigma = blur * 1.5;
      image = ImageFiltered(
        imageFilter: ui.ImageFilter.blur(sigmaX: sigma, sigmaY: sigma),
        child: image,
      );
    }

    // Brightness via ColorFilter matrix (avoids overexposure)
    if (brightness != 0) {
      final factor = 1.0 + brightness * 0.8;
      image = ColorFiltered(
        colorFilter: ColorFilter.matrix(<double>[
          factor, 0, 0, 0, 0, //
          0, factor, 0, 0, 0, //
          0, 0, factor, 0, 0, //
          0, 0, 0, 1, 0, //
        ]),
        child: image,
      );
    }

    // Opacity
    if (opacity < 1.0) {
      image = Opacity(opacity: opacity, child: image);
    }

    return image;
  }
}

class _SliderRow extends StatelessWidget {
  const _SliderRow({
    required this.label,
    required this.value,
    required this.min,
    required this.max,
    required this.displayText,
    required this.onChanged,
  });

  final String label;
  final double value;
  final double min;
  final double max;
  final String displayText;
  final ValueChanged<double>? onChanged;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Row(
      children: [
        SizedBox(
          width: 60,
          child: Text(label, style: Theme.of(context).textTheme.bodySmall),
        ),
        Expanded(
          child: SliderTheme(
            data: ModernSliderTheme.theme(context),
            child: Slider(
              value: value.clamp(min, max),
              min: min,
              max: max,
              onChanged: onChanged,
            ),
          ),
        ),
        SizedBox(
          width: 48,
          child: Text(
            displayText,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: cs.onSurfaceVariant,
                ),
            textAlign: TextAlign.end,
          ),
        ),
      ],
    );
  }
}
