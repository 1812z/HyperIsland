import 'package:flutter/material.dart';

import '../controllers/whitelist_controller.dart';
import '../services/app_cache_service.dart';
import '../widgets/toast_settings_panel.dart';

class ToastAppSettingsPage extends StatefulWidget {
  const ToastAppSettingsPage({
    super.key,
    required this.app,
    required this.controller,
  });

  final AppInfo app;
  final WhitelistController controller;

  @override
  State<ToastAppSettingsPage> createState() => _ToastAppSettingsPageState();
}

class _ToastAppSettingsPageState extends State<ToastAppSettingsPage> {
  bool _loading = true;
  bool _forwardEnabled = false;
  bool _blockOriginal = true;
  bool _showNotification = false;
  bool _showIslandIcon = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final pkg = widget.app.packageName;
    final forwardEnabled = await widget.controller.getToastForwardEnabled(pkg);
    final blockOriginal = await widget.controller.getToastBlockOriginal(pkg);
    final showNotification = await widget.controller.getToastShowNotification(
      pkg,
    );
    final showIslandIcon = await widget.controller.getToastShowIslandIcon(pkg);
    if (!mounted) return;
    setState(() {
      _forwardEnabled = forwardEnabled;
      _blockOriginal = blockOriginal;
      _showNotification = showNotification;
      _showIslandIcon = showIslandIcon;
      _loading = false;
    });
  }

  Future<void> _setForwardEnabled(bool value) async {
    setState(() => _forwardEnabled = value);
    await widget.controller.setToastForwardEnabled(
      widget.app.packageName,
      value,
    );
    if (!value && _blockOriginal) {
      setState(() => _blockOriginal = false);
      await widget.controller.setToastBlockOriginal(
        widget.app.packageName,
        false,
      );
    }
  }

  Future<void> _setBlockOriginal(bool value) async {
    if (!_forwardEnabled && value) return;
    setState(() => _blockOriginal = value);
    await widget.controller.setToastBlockOriginal(
      widget.app.packageName,
      value,
    );
  }

  Future<void> _setShowNotification(bool value) async {
    if (!_forwardEnabled && value) return;
    setState(() => _showNotification = value);
    await widget.controller.setToastShowNotification(
      widget.app.packageName,
      value,
    );
  }

  Future<void> _setShowIslandIcon(bool value) async {
    if (!_forwardEnabled) return;
    setState(() => _showIslandIcon = value);
    await widget.controller.setToastShowIslandIcon(
      widget.app.packageName,
      value,
    );
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Scaffold(
      backgroundColor: cs.surface,
      appBar: AppBar(title: Text(widget.app.appName)),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
              children: [
                ToastSettingsPanel(
                  forwardEnabled: _forwardEnabled,
                  blockOriginal: _blockOriginal,
                  showNotification: _showNotification,
                  showIslandIcon: _showIslandIcon,
                  onForwardEnabledChanged: _setForwardEnabled,
                  onBlockOriginalChanged: _setBlockOriginal,
                  onShowNotificationChanged: _setShowNotification,
                  onShowIslandIconChanged: _setShowIslandIcon,
                ),
              ],
            ),
    );
  }
}
