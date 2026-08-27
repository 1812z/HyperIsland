import 'package:flutter/services.dart';

/// HyperOS 版本查询的公共入口。
abstract final class OsVersionService {
  static const _channel = MethodChannel('io.github.hyperisland/test');

  /// 返回 HyperOS 主版本 3 或 4；无法识别及其他版本返回 0。
  static Future<int> getHyperOsMajorVersion() async {
    try {
      return await _channel.invokeMethod<int>('getHyperOsMajorVersion') ?? 0;
    } on PlatformException {
      return 0;
    } on MissingPluginException {
      return 0;
    }
  }
}
