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

class IslandMaterialConfig {
  const IslandMaterialConfig({
    this.type = IslandMaterialType.systemDefault,
    this.blur = 80,
    this.softLight = 15,
    this.saturation = 240,
    this.brightness = 30,
    this.darker = 20,
    this.transparency = 100,
    this.burn = 0,
    this.refraction = 16,
    this.edgeThickness = 16,
    this.reflectionStrength = 60,
    this.directionalLightIntensity = 180,
    this.backgroundSaturation = 0,
    this.backgroundBrightness = 0,
    this.blendColor = '#0F0F0F',
    this.blendOpacity = 60,
    this.highlight = true,
  });

  final IslandMaterialType type;
  final int blur;
  final int softLight;
  final int saturation;
  final int brightness;
  final int darker;
  final int transparency;
  final int burn;
  final int refraction;
  final int edgeThickness;
  final int reflectionStrength;
  final int directionalLightIntensity;
  final int backgroundSaturation;
  final int backgroundBrightness;
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
    int? softLight,
    int? saturation,
    int? brightness,
    int? darker,
    int? transparency,
    int? burn,
    int? refraction,
    int? edgeThickness,
    int? reflectionStrength,
    int? directionalLightIntensity,
    int? backgroundSaturation,
    int? backgroundBrightness,
    String? blendColor,
    int? blendOpacity,
    bool? highlight,
  }) => IslandMaterialConfig(
    type: type ?? this.type,
    blur: blur ?? this.blur,
    softLight: softLight ?? this.softLight,
    saturation: saturation ?? this.saturation,
    brightness: brightness ?? this.brightness,
    darker: darker ?? this.darker,
    transparency: transparency ?? this.transparency,
    burn: burn ?? this.burn,
    refraction: refraction ?? this.refraction,
    edgeThickness: edgeThickness ?? this.edgeThickness,
    reflectionStrength: reflectionStrength ?? this.reflectionStrength,
    directionalLightIntensity:
        directionalLightIntensity ?? this.directionalLightIntensity,
    backgroundSaturation: backgroundSaturation ?? this.backgroundSaturation,
    backgroundBrightness: backgroundBrightness ?? this.backgroundBrightness,
    blendColor: blendColor ?? this.blendColor,
    blendOpacity: blendOpacity ?? this.blendOpacity,
    highlight: highlight ?? this.highlight,
  );

  factory IslandMaterialConfig.fromJson(Map<String, dynamic> json) {
    int integer(String key, int fallback, int min, int max) {
      final value = json[key];
      return (value is num ? value.round() : fallback).clamp(min, max);
    }

    return IslandMaterialConfig(
      type: IslandMaterialType.fromValue(json['type'] as String?),
      blur: integer('blur', 80, 0, 100),
      softLight: integer('softLight', 15, 0, 100),
      saturation: integer('saturation', 240, 0, 300),
      brightness: integer('brightness', 30, -100, 100),
      darker: integer('darker', 20, 0, 100),
      transparency: integer('transparency', 100, 0, 100),
      burn: integer('burn', 0, 0, 100),
      refraction: integer('refraction', 16, 0, 40),
      edgeThickness: integer('edgeThickness', 16, 0, 40),
      reflectionStrength: integer('reflectionStrength', 60, 0, 200),
      directionalLightIntensity: integer(
        'directionalLightIntensity',
        180,
        0,
        300,
      ),
      backgroundSaturation: integer('backgroundSaturation', 0, -100, 200),
      backgroundBrightness: integer('backgroundBrightness', 0, -100, 100),
      blendColor: (json['blendColor'] as String? ?? '#0F0F0F').trim(),
      blendOpacity: integer('blendOpacity', 60, 0, 100),
      highlight: json['highlight'] as bool? ?? true,
    );
  }

  Map<String, dynamic> toJson() => {
    'type': type.value,
    'blur': blur,
    'softLight': softLight,
    'saturation': saturation,
    'brightness': brightness,
    'darker': darker,
    'transparency': transparency,
    'burn': burn,
    'refraction': refraction,
    'edgeThickness': edgeThickness,
    'reflectionStrength': reflectionStrength,
    'directionalLightIntensity': directionalLightIntensity,
    'backgroundSaturation': backgroundSaturation,
    'backgroundBrightness': backgroundBrightness,
    'blendColor': blendColor,
    'blendOpacity': blendOpacity,
    'highlight': highlight,
  };
}
