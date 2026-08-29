import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../controllers/whitelist_controller.dart';
import '../services/app_cache_service.dart';
import 'app_channels_page.dart';
import 'toast_app_settings_page.dart';

/// Compose 应用列表进入现有 Flutter 复杂配置页的临时路由宿主。
class AppSettingsRoutePage extends StatefulWidget {
  const AppSettingsRoutePage({
    super.key,
    required this.packageName,
    required this.appName,
    required this.toastMode,
    required this.isSystem,
  });

  final String packageName;
  final String appName;
  final bool toastMode;
  final bool isSystem;

  @override
  State<AppSettingsRoutePage> createState() => _AppSettingsRoutePageState();
}

class _AppSettingsRoutePageState extends State<AppSettingsRoutePage> {
  late final WhitelistController _controller;

  @override
  void initState() {
    super.initState();
    _controller = WhitelistController()..addListener(_onChanged);
  }

  void _onChanged() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _controller.removeListener(_onChanged);
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_controller.loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    final app = AppInfo(
      packageName: widget.packageName,
      appName: widget.appName,
      icon: Uint8List(0),
      isSystem: widget.isSystem,
    );
    return widget.toastMode
        ? ToastAppSettingsPage(app: app, controller: _controller)
        : AppChannelsPage(
            app: app,
            controller: _controller,
            appEnabled: _controller.enabledPackages.contains(widget.packageName),
          );
  }
}
