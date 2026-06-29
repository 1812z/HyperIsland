import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import '../controllers/settings_controller.dart';

enum IslandBgType { small, big, expand }

class IslandBackgroundService {
  static const _channel = MethodChannel('io.github.hyperisland/test');

  static const Map<IslandBgType, String> _fileNames = {
    IslandBgType.small: 'hyperisland_bg_small',
    IslandBgType.big: 'hyperisland_bg_big',
    IslandBgType.expand: 'hyperisland_bg_expand',
  };

  static String getFileName(IslandBgType type) => _fileNames[type] ?? '';

  /// Picks an image file and returns the local path (no copy yet).
  static Future<String?> pickImage() async {
    try {
      final pickedFile = await FilePicker.pickFile(
        type: FileType.custom,
        allowedExtensions: ['jpg', 'jpeg', 'png', 'webp', 'bmp', 'gif'],
      );

      return pickedFile?.path;
    } catch (e) {
      debugPrint('IslandBackgroundService.pickImage error: $e');
      return null;
    }
  }

  /// Copies the source image to the module dir and updates the controller.
  /// Returns the saved path, or null on failure.
  static Future<String?> copyAndUpdate(
    String sourcePath,
    IslandBgType type,
  ) async {
    final destFileName = _buildDestFileName(sourcePath, type);
    if (destFileName == null) return null;

    final savedPath = await _copyImageToModuleDir(
      sourcePath,
      destFileName,
    );

    if (savedPath != null) {
      await _updateControllerPath(type, savedPath);
    }

    return savedPath;
  }

  /// Legacy method: pick, copy, and update in one step (no edit dialog).
  static Future<String?> pickAndCopyImage(IslandBgType type) async {
    final sourcePath = await pickImage();
    if (sourcePath == null) return null;
    return copyAndUpdate(sourcePath, type);
  }

  static bool isGif(String path) => path.trim().toLowerCase().endsWith('.gif');

  static String? _buildDestFileName(String sourcePath, IslandBgType type) {
    final baseName = _fileNames[type];
    if (baseName == null) return null;
    final extension = sourcePath.trim().toLowerCase().endsWith('.gif')
        ? 'gif'
        : 'png';
    return '$baseName.$extension';
  }

  static Future<String?> _copyImageToModuleDir(
    String sourcePath,
    String destFileName,
  ) async {
    try {
      final result = await _channel.invokeMethod<String>(
        'copyImageToModuleDir',
        {
          'sourcePath': sourcePath,
          'destFileName': destFileName,
        },
      );
      return result;
    } catch (e) {
      debugPrint('IslandBackgroundService._copyImageToModuleDir error: $e');
      return null;
    }
  }

  static Future<bool> deleteImage(IslandBgType type) async {
    try {
      final currentPath = getImagePath(type);
      final destFileName = currentPath?.split(Platform.pathSeparator).last;
      if (destFileName == null || destFileName.isEmpty) return false;

      final success = await _channel.invokeMethod<bool>(
        'deleteImageFromModuleDir',
        {'fileName': destFileName},
      );

      if (success == true) {
        await _clearControllerPath(type);
      }

      return success ?? false;
    } catch (e) {
      debugPrint('IslandBackgroundService.deleteImage error: $e');
      return false;
    }
  }

  static Future<void> _updateControllerPath(
    IslandBgType type,
    String path,
  ) async {
    final ctrl = SettingsController.instance;
    switch (type) {
      case IslandBgType.small:
        await ctrl.setIslandBgSmallPath(path);
        break;
      case IslandBgType.big:
        await ctrl.setIslandBgBigPath(path);
        break;
      case IslandBgType.expand:
        await ctrl.setIslandBgExpandPath(path);
        break;
    }
  }

  static Future<void> _clearControllerPath(IslandBgType type) async {
    final ctrl = SettingsController.instance;
    switch (type) {
      case IslandBgType.small:
        await ctrl.setIslandBgSmallPath('');
        break;
      case IslandBgType.big:
        await ctrl.setIslandBgBigPath('');
        break;
      case IslandBgType.expand:
        await ctrl.setIslandBgExpandPath('');
        break;
    }
  }

  static bool hasImage(IslandBgType type) {
    final ctrl = SettingsController.instance;
    switch (type) {
      case IslandBgType.small:
        return ctrl.islandBgSmallPath.isNotEmpty;
      case IslandBgType.big:
        return ctrl.islandBgBigPath.isNotEmpty;
      case IslandBgType.expand:
        return ctrl.islandBgExpandPath.isNotEmpty;
    }
  }

  static String? getImagePath(IslandBgType type) {
    final ctrl = SettingsController.instance;
    switch (type) {
      case IslandBgType.small:
        return ctrl.islandBgSmallPath.isEmpty ? null : ctrl.islandBgSmallPath;
      case IslandBgType.big:
        return ctrl.islandBgBigPath.isEmpty ? null : ctrl.islandBgBigPath;
      case IslandBgType.expand:
        return ctrl.islandBgExpandPath.isEmpty ? null : ctrl.islandBgExpandPath;
    }
  }
}
