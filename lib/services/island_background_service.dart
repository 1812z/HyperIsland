import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import '../controllers/settings_controller.dart';

enum IslandBgType { small, big, expand }

class IslandBackgroundService {
  static const _channel = MethodChannel('io.github.hyperisland/test');

  static const Map<IslandBgType, String> _fileNames = {
    IslandBgType.small: 'hyperisland_bg_small.png',
    IslandBgType.big: 'hyperisland_bg_big.png',
    IslandBgType.expand: 'hyperisland_bg_expand.png',
  };

  static String getFileName(IslandBgType type) => _fileNames[type] ?? '';

  static Future<String?> pickAndCopyImage(IslandBgType type) async {
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.image,
        allowMultiple: false,
      );

      if (result == null || result.files.isEmpty) return null;

      final pickedFile = result.files.first;
      if (pickedFile.path == null) return null;

      final destFileName = _fileNames[type];
      if (destFileName == null) return null;

      final savedPath = await _copyImageToModuleDir(
        pickedFile.path!,
        destFileName,
      );

      if (savedPath != null) {
        await _updateControllerPath(type, savedPath);
      }

      return savedPath;
    } catch (e) {
      debugPrint('IslandBackgroundService.pickAndCopyImage error: $e');
      return null;
    }
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
      final destFileName = _fileNames[type];
      if (destFileName == null) return false;

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
