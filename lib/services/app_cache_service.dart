import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class AppInfo {
  final String packageName;
  final String appName;
  final Uint8List icon;
  final bool isSystem;

  const AppInfo({
    required this.packageName,
    required this.appName,
    required this.icon,
    this.isSystem = false,
  });
}

class AppCacheService extends ChangeNotifier {
  static final AppCacheService instance = AppCacheService._();
  AppCacheService._();

  static const _channel = MethodChannel('io.github.hyperisland/test');

  List<AppInfo> _cachedApps = [];
  final Map<String, Uint8List> _iconCache = {};
  bool _loading = false;
  bool _initialized = false;
  DateTime? _lastLoadTime;
  Future<void>? _loadFuture;

  static const _cacheValidDuration = Duration(minutes: 5);

  List<AppInfo> get apps => _cachedApps;
  bool get loading => _loading;
  bool get initialized => _initialized;

  static const _excludedPackages = {
    "com.android.providers.downloads.ui",
    "com.android.systemui",
  };

  Future<void> initialize() async {
    if (_initialized) return;
    _initialized = true;
    await loadApps();
  }

  Future<List<AppInfo>> getApps({bool forceRefresh = false}) async {
    if (_loading && _loadFuture != null) {
      await _loadFuture;
      return _cachedApps;
    }
    if (forceRefresh || _shouldRefresh()) {
      await loadApps();
    }
    return _cachedApps;
  }

  bool _shouldRefresh() {
    if (_cachedApps.isEmpty) return true;
    if (_lastLoadTime == null) return true;
    return DateTime.now().difference(_lastLoadTime!) > _cacheValidDuration;
  }

  Future<void> loadApps() {
    if (_loading) return _loadFuture ?? Future.value();
    _loadFuture = _doLoadApps();
    return _loadFuture!;
  }

  Future<void> _doLoadApps() async {
    _loading = true;
    notifyListeners();

    try {
      final rawList =
          await _channel.invokeMethod<List<dynamic>>('getInstalledApps', {
            'includeSystem': true,
          }) ??
          [];

      _cachedApps =
          rawList
              .map((raw) {
                final map = Map<String, dynamic>.from(raw as Map);
                final packageName = map['packageName'] as String;
                final iconRaw = map['icon'];
                final icon = iconRaw is List
                    ? Uint8List.fromList(iconRaw.cast<int>())
                    : Uint8List(0);
                if (icon.isNotEmpty) {
                  _iconCache[packageName] = icon;
                }
                return AppInfo(
                  packageName: packageName,
                  appName: map['appName'] as String,
                  icon: icon,
                  isSystem: map['isSystem'] as bool? ?? false,
                );
              })
              .where((a) => !_excludedPackages.contains(a.packageName))
              .toList()
            ..sort(
              (a, b) =>
                  a.appName.toLowerCase().compareTo(b.appName.toLowerCase()),
            );

      _lastLoadTime = DateTime.now();
    } catch (e) {
      debugPrint('AppCacheService.loadApps error: $e');
    }

    _loading = false;
    notifyListeners();
  }

  Future<void> refresh() async {
    await loadApps();
  }

  Future<Uint8List?> getIcon(String packageName) async {
    final cached = _iconCache[packageName];
    if (cached != null && cached.isNotEmpty) return cached;

    try {
      final icon = await _channel.invokeMethod<Uint8List>('getAppIcon', {
        'packageName': packageName,
      });
      if (icon != null && icon.isNotEmpty) {
        _iconCache[packageName] = icon;
        return icon;
      }
    } catch (e) {
      debugPrint('AppCacheService.getIcon error: $e');
    }
    return null;
  }

  void clearCache() {
    _cachedApps = [];
    _iconCache.clear();
    _lastLoadTime = null;
    notifyListeners();
  }
}
