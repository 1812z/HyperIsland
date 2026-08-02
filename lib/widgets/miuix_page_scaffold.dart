import 'package:flutter/material.dart';
import 'package:flutter_miuix/miuix.dart';

import '../controllers/settings_controller.dart';

class MiuixPageScaffold extends StatefulWidget {
  const MiuixPageScaffold({
    super.key,
    required this.title,
    required this.children,
    this.subtitle = '',
    this.actions,
    this.navigationIcon,
    this.snackbarHost,
    this.overlay,
    this.horizontalPadding = 16,
    this.bottomPadding = 32,
  });

  final String title;
  final String subtitle;
  final List<Widget> children;
  final List<Widget>? actions;
  final Widget? navigationIcon;
  final Widget? snackbarHost;
  final Widget? overlay;
  final double horizontalPadding;
  final double bottomPadding;

  @override
  State<MiuixPageScaffold> createState() => _MiuixPageScaffoldState();
}

class _MiuixPageScaffoldState extends State<MiuixPageScaffold> {
  late final MiuixExitUntilCollapsedScrollBehavior _scrollBehavior;

  @override
  void initState() {
    super.initState();
    _scrollBehavior = MiuixExitUntilCollapsedScrollBehavior();
  }

  @override
  Widget build(BuildContext context) {
    return MiuixScaffold(
      topBar: MiuixTopAppBar(
        title: widget.title,
        subtitle: widget.subtitle,
        navigationIcon: widget.navigationIcon,
        actions: widget.actions,
        scrollBehavior: _scrollBehavior,
        blurred: SettingsController.instance.blurBars,
      ),
      snackbarHost: widget.snackbarHost,
      content: (padding) => Material(
        type: MaterialType.transparency,
        child: Stack(
          children: [
            MiuixScrollBehaviorListener(
              behavior: _scrollBehavior,
              child: ListView(
                physics: const ClampingScrollPhysics(),
                padding: padding.copyWith(
                  left: widget.horizontalPadding,
                  right: widget.horizontalPadding,
                  bottom: widget.bottomPadding,
                ),
                children: widget.children,
              ),
            ),
            ?widget.overlay,
          ],
        ),
      ),
    );
  }
}
