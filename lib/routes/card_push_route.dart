import 'package:flutter/material.dart';
import 'dart:ui';

Route<T> buildCardPushRoute<T>({
  required WidgetBuilder builder,
  RouteSettings? settings,
}) {
  return PageRouteBuilder<T>(
    settings: settings,
    transitionDuration: const Duration(milliseconds: 320),
    reverseTransitionDuration: const Duration(milliseconds: 240),
    pageBuilder: (context, animation, secondaryAnimation) {
      return HeroMode(
        enabled: false,
        child: builder(context),
      );
    },
    transitionsBuilder: (context, animation, secondaryAnimation, child) {
      final curved = CurvedAnimation(
        parent: animation,
        curve: Curves.easeOutCubic,
        reverseCurve: Curves.easeInCubic,
      );
      final slide = Tween<Offset>(
        begin: const Offset(1.0, 0.0),
        end: Offset.zero,
      ).animate(curved);

      return AnimatedBuilder(
        animation: curved,
        child: child,
        builder: (context, child) {
          final t = curved.value;
          final cardProgress = 1 - t;
          final radius = lerpDouble(24, 0, t) ?? 0;
          final horizontalInset = lerpDouble(12, 0, t) ?? 0;
          final bottomInset = lerpDouble(12, 0, t) ?? 0;
          final shadowBlur = lerpDouble(26, 0, t) ?? 0;

          return SlideTransition(
            position: slide,
            child: Padding(
              padding: EdgeInsets.fromLTRB(
                horizontalInset,
                0,
                horizontalInset,
                bottomInset,
              ),
              child: DecoratedBox(
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(radius),
                  boxShadow: shadowBlur <= 0
                      ? const []
                      : [
                          BoxShadow(
                            color: Colors.black.withValues(
                              alpha: 0.20 * cardProgress,
                            ),
                            blurRadius: shadowBlur,
                            offset: const Offset(-8, 12),
                          ),
                        ],
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(radius),
                  child: child,
                ),
              ),
            ),
          );
        },
      );
    },
  );
}
