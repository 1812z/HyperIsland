enum IslandMaterialType {
  systemDefault('default'),
  gaussian('gaussian'),
  highlightGlass('highlight_glass'),
  liquidGlass('liquid_glass'),
  softGlass('soft_glass');

  const IslandMaterialType(this.value);
  final String value;
  static IslandMaterialType fromValue(String? value) => values.firstWhere(
    (item) => item.value == value,
    orElse: () => systemDefault,
  );
}

enum IslandMaterialState { big, small, expand }

class IslandMaterialConfig {
  const IslandMaterialConfig({
    this.type = IslandMaterialType.systemDefault,
    this.blur = 35,
    this.softLight = -1,
    this.saturation = 2,
    this.brightness = 40,
    this.softDarker = -10,
    this.transparency = -0.57,
    this.burn = 0,
    this.softRefraction = 0,
    this.softEdgeThickness = 0.8,
    this.softReflection = 0,
    this.directionalLightIntensity = 1,
    this.backgroundSaturation = 0,
    this.backgroundBrightness = 0.04,
    this.refraction = 16,
    this.edgeThickness = 16,
    this.reflectionStrength = 42,
    this.darker = 14,
    this.lightDirection = 243,
    this.dispersion = 18,
    this.blendColor = '#FFFFFF',
    this.blendOpacity = 0,
    this.highlight = true,
  });

  final IslandMaterialType type;
  final int blur;

  // HyperLight-compatible adjustments applied to the system's original token.
  final double softLight;
  final double saturation;
  final double brightness;
  final double softDarker;
  final double transparency;
  final double burn;
  final double softRefraction;
  final double softEdgeThickness;
  final double softReflection;
  final double directionalLightIntensity;
  final double backgroundSaturation;
  final double backgroundBrightness;

  // Existing highlight/liquid-glass renderer parameters.
  final int refraction;
  final int edgeThickness;
  final int reflectionStrength;
  final int darker;
  final int lightDirection;
  final int dispersion;
  final String blendColor;
  final int blendOpacity;
  final bool highlight;

  bool get isCustom => type != IslandMaterialType.systemDefault;
  bool get usesLiquidCapture => type == IslandMaterialType.liquidGlass;
  bool get usesGlass => switch (type) {
    IslandMaterialType.highlightGlass ||
    IslandMaterialType.liquidGlass ||
    IslandMaterialType.softGlass => true,
    _ => false,
  };

  IslandMaterialConfig copyWith({
    IslandMaterialType? type,
    int? blur,
    double? softLight,
    double? saturation,
    double? brightness,
    double? softDarker,
    double? transparency,
    double? burn,
    double? softRefraction,
    double? softEdgeThickness,
    double? softReflection,
    double? directionalLightIntensity,
    double? backgroundSaturation,
    double? backgroundBrightness,
    int? refraction,
    int? edgeThickness,
    int? reflectionStrength,
    int? darker,
    int? lightDirection,
    int? dispersion,
    String? blendColor,
    int? blendOpacity,
    bool? highlight,
  }) => IslandMaterialConfig(
    type: type ?? this.type,
    blur: blur ?? this.blur,
    softLight: softLight ?? this.softLight,
    saturation: saturation ?? this.saturation,
    brightness: brightness ?? this.brightness,
    softDarker: softDarker ?? this.softDarker,
    transparency: transparency ?? this.transparency,
    burn: burn ?? this.burn,
    softRefraction: softRefraction ?? this.softRefraction,
    softEdgeThickness: softEdgeThickness ?? this.softEdgeThickness,
    softReflection: softReflection ?? this.softReflection,
    directionalLightIntensity:
        directionalLightIntensity ?? this.directionalLightIntensity,
    backgroundSaturation: backgroundSaturation ?? this.backgroundSaturation,
    backgroundBrightness: backgroundBrightness ?? this.backgroundBrightness,
    refraction: refraction ?? this.refraction,
    edgeThickness: edgeThickness ?? this.edgeThickness,
    reflectionStrength: reflectionStrength ?? this.reflectionStrength,
    darker: darker ?? this.darker,
    lightDirection: lightDirection ?? this.lightDirection,
    dispersion: dispersion ?? this.dispersion,
    blendColor: blendColor ?? this.blendColor,
    blendOpacity: blendOpacity ?? this.blendOpacity,
    highlight: highlight ?? this.highlight,
  );

  factory IslandMaterialConfig.fromJson(Map<String, dynamic> json) {
    int integer(String key, int fallback, int min, int max) {
      final value = json[key];
      return (value is num ? value.round() : fallback).clamp(min, max);
    }

    double decimal(String key, double fallback) {
      final value = json[key];
      return (value is num ? value.toDouble() : fallback).clamp(-50.0, 50.0);
    }

    // The previous implementation used unrelated absolute integer ranges.
    final softV2 = json['softSchema'] == 2;
    double soft(String key, double fallback) =>
        softV2 ? decimal(key, fallback) : fallback;

    final type = IslandMaterialType.fromValue(json['type'] as String?);
    return IslandMaterialConfig(
      type: type,
      blur: integer('blur', 35, 0, 100),
      softLight: soft('softLight', -1),
      saturation: soft('saturation', 2),
      brightness: soft('brightness', 40),
      softDarker: soft('softDarker', -10),
      transparency: soft('transparency', -0.57),
      burn: soft('burn', 0),
      softRefraction: soft('softRefraction', 0),
      softEdgeThickness: soft('softEdgeThickness', 0.8),
      softReflection: soft('softReflection', 0),
      directionalLightIntensity: soft('directionalLightIntensity', 1),
      backgroundSaturation: soft('backgroundSaturation', 0),
      backgroundBrightness: soft('backgroundBrightness', 0.04),
      refraction: integer('refraction', 16, 0, 40),
      edgeThickness: integer('edgeThickness', 16, 4, 40),
      reflectionStrength: integer('reflectionStrength', 42, 0, 100),
      darker: integer('darker', 14, 0, 100),
      lightDirection: integer('lightDirection', 243, 0, 359),
      dispersion: integer('dispersion', 18, 0, 100),
      blendColor: (json['blendColor'] as String? ?? '#FFFFFF').trim(),
      blendOpacity: !softV2 && type == IslandMaterialType.softGlass
          ? 0
          : integer('blendOpacity', 0, 0, 100),
      highlight: json['highlight'] as bool? ?? true,
    );
  }

  Map<String, dynamic> toJson() => {
    'type': type.value,
    'blur': blur,
    'softSchema': 2,
    'softLight': softLight,
    'saturation': saturation,
    'brightness': brightness,
    'softDarker': softDarker,
    'transparency': transparency,
    'burn': burn,
    'softRefraction': softRefraction,
    'softEdgeThickness': softEdgeThickness,
    'softReflection': softReflection,
    'directionalLightIntensity': directionalLightIntensity,
    'backgroundSaturation': backgroundSaturation,
    'backgroundBrightness': backgroundBrightness,
    'refraction': refraction,
    'edgeThickness': edgeThickness,
    'reflectionStrength': reflectionStrength,
    'darker': darker,
    'lightDirection': lightDirection,
    'dispersion': dispersion,
    'blendColor': blendColor,
    'blendOpacity': blendOpacity,
    'highlight': highlight,
  };
}
