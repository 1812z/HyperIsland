import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class HomeController extends ChangeNotifier {
  static const _platform = MethodChannel('io.github.hyperisland/test');

  bool isSending = false;
  bool? moduleActive;
  int? focusProtocolVersion;
  int? lsposedApiVersion;

  HomeController() {
    _checkStatus();
  }

  Future<void> _checkStatus() async {
    int apiVersion = 0;
    try {
      apiVersion = await _platform.invokeMethod('getLSPosedApiVersion');
      lsposedApiVersion = apiVersion;
    } catch (_) {
      lsposedApiVersion = 0;
    }

    try {
      final bool active = await _platform.invokeMethod('isModuleActive');
      moduleActive = active && apiVersion >= 101;
    } catch (_) {
      moduleActive = false;
    }

    try {
      final int version = await _platform.invokeMethod(
        'getFocusProtocolVersion',
      );
      focusProtocolVersion = version;
    } catch (_) {
      focusProtocolVersion = 0;
    }
    notifyListeners();
  }

  Future<void> sendTest() async {
    isSending = true;
    notifyListeners();
    try {
      await _platform.invokeMethod('showTest');
    } on PlatformException catch (_) {
    } finally {
      isSending = false;
      notifyListeners();
    }
  }

  Future<void> sendCustomTest({
    String? title,
    String? content,
    bool clearPrevious = true,
    bool enableFloat = true,
  }) async {
    isSending = true;
    notifyListeners();
    try {
      await _platform.invokeMethod('showCustomTest', {
        'title': title ?? '',
        'content': content ?? '',
        'clearPrevious': clearPrevious,
        'enableFloat': enableFloat,
      });
    } on PlatformException catch (_) {
    } finally {
      isSending = false;
      notifyListeners();
    }
  }
}
