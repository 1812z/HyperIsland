import 'package:flutter/material.dart';
import 'dart:typed_data';
import '../services/app_cache_service.dart';

class AppListSearchHeader extends StatelessWidget {
  const AppListSearchHeader({
    super.key,
    required this.countText,
    this.showCountText = true,
    required this.searchController,
    required this.searchFocusNode,
    required this.hintText,
    required this.onChanged,
    required this.onClear,
  });

  final String countText;
  final bool showCountText;
  final TextEditingController searchController;
  final FocusNode searchFocusNode;
  final String hintText;
  final ValueChanged<String> onChanged;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (showCountText) ...[
          Text(
            countText,
            style: Theme.of(
              context,
            ).textTheme.bodyMedium?.copyWith(color: cs.onSurfaceVariant),
          ),
          const SizedBox(height: 12),
        ],
        SearchBar(
          controller: searchController,
          focusNode: searchFocusNode,
          hintText: hintText,
          leading: const Icon(Icons.search),
          trailing: [
            if (searchController.text.isNotEmpty)
              IconButton(icon: const Icon(Icons.clear), onPressed: onClear),
          ],
          onChanged: onChanged,
          onSubmitted: (_) => searchFocusNode.unfocus(),
          padding: const WidgetStatePropertyAll(
            EdgeInsets.symmetric(horizontal: 16),
          ),
          elevation: const WidgetStatePropertyAll(0),
          backgroundColor: WidgetStatePropertyAll(cs.surfaceContainerHighest),
          constraints: const BoxConstraints(minHeight: 48, maxHeight: 48),
        ),
      ],
    );
  }
}

class AppListItemFrame extends StatelessWidget {
  const AppListItemFrame({
    super.key,
    required this.app,
    required this.onTap,
    required this.trailing,
    required this.isFirst,
    required this.isLast,
    this.onLongPress,
    this.selected = false,
    this.selectedColor,
  });

  final AppInfo app;
  final VoidCallback onTap;
  final VoidCallback? onLongPress;
  final Widget trailing;
  final bool isFirst;
  final bool isLast;
  final bool selected;
  final Color? selectedColor;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    const large = Radius.circular(22);
    const small = Radius.circular(5);
    final radius = BorderRadius.only(
      topLeft: isFirst ? large : small,
      topRight: isFirst ? large : small,
      bottomLeft: isLast ? large : small,
      bottomRight: isLast ? large : small,
    );

    return Material(
      color: selected
          ? (selectedColor ?? cs.primaryContainer)
          : cs.surfaceContainerHighest,
      borderRadius: radius,
      child: InkWell(
        borderRadius: radius,
        onTap: onTap,
        onLongPress: onLongPress,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          child: Row(
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(10),
                child: _AppIconView(app: app),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      app.appName,
                      style: Theme.of(context).textTheme.bodyLarge,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      app.packageName,
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: cs.onSurfaceVariant,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
              trailing,
            ],
          ),
        ),
      ),
    );
  }
}

class _AppIconView extends StatefulWidget {
  const _AppIconView({required this.app});

  final AppInfo app;

  @override
  State<_AppIconView> createState() => _AppIconViewState();
}

class _AppIconViewState extends State<_AppIconView> {
  Future<Uint8List?>? _iconFuture;

  @override
  void initState() {
    super.initState();
    if (widget.app.icon.isEmpty) {
      _iconFuture = AppCacheService.instance.getIcon(widget.app.packageName);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (widget.app.icon.isNotEmpty) {
      return Image.memory(
        widget.app.icon,
        width: 44,
        height: 44,
        fit: BoxFit.cover,
        gaplessPlayback: true,
      );
    }

    return FutureBuilder<Uint8List?>(
      future: _iconFuture,
      builder: (context, snapshot) {
        final icon = snapshot.data;
        if (icon != null && icon.isNotEmpty) {
          return Image.memory(
            icon,
            width: 44,
            height: 44,
            fit: BoxFit.cover,
            gaplessPlayback: true,
          );
        }
        return Container(
          width: 44,
          height: 44,
          color: Theme.of(context).colorScheme.surfaceContainer,
          alignment: Alignment.center,
          child: Icon(
            Icons.apps_rounded,
            size: 22,
            color: Theme.of(context).colorScheme.onSurfaceVariant,
          ),
        );
      },
    );
  }
}

class AppBarOverflowMenuButton extends StatelessWidget {
  const AppBarOverflowMenuButton({
    super.key,
    required this.onSelected,
    required this.itemBuilder,
  });

  final PopupMenuItemSelected<String>? onSelected;
  final PopupMenuItemBuilder<String> itemBuilder;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final borderColor = cs.outlineVariant.withValues(alpha: 0.34);

    return PopupMenuButton<String>(
      icon: SizedBox(
        width: 36,
        height: 36,
        child: Icon(
          Icons.more_horiz_rounded,
          size: 20,
          color: cs.onSurfaceVariant,
        ),
      ),
      tooltip: MaterialLocalizations.of(context).showMenuTooltip,
      offset: const Offset(0, 6),
      position: PopupMenuPosition.under,
      elevation: 8,
      color: cs.surface,
      surfaceTintColor: cs.surfaceTint,
      shadowColor: Colors.black.withValues(alpha: 0.12),
      constraints: const BoxConstraints(minWidth: 228),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: BorderSide(color: borderColor, width: 0.9),
      ),
      onSelected: onSelected,
      itemBuilder: itemBuilder,
    );
  }
}

final class AppListOverflowMenuAction {
  const AppListOverflowMenuAction._();

  static const String toggleSystem = 'toggle_system';
  static const String refresh = 'refresh';
  static const String enableAll = 'enable_all';
  static const String disableAll = 'disable_all';
}

PopupMenuItem<String> buildAppPopupMenuItem({
  required String value,
  required IconData icon,
  required String label,
  bool enabled = true,
  Widget? trailing,
}) {
  return PopupMenuItem<String>(
    value: value,
    enabled: enabled,
    height: 44,
    padding: const EdgeInsets.symmetric(horizontal: 12),
    child: AppPopupMenuLabel(
      icon: icon,
      label: label,
      enabled: enabled,
      trailing: trailing,
    ),
  );
}

List<PopupMenuEntry<String>> buildAppListOverflowMenuItems({
  required BuildContext context,
  required bool showSystemApps,
  required String showSystemAppsLabel,
  required String refreshLabel,
  required String enableAllLabel,
  required String disableAllLabel,
}) {
  final cs = Theme.of(context).colorScheme;

  return [
    buildAppPopupMenuItem(
      value: AppListOverflowMenuAction.toggleSystem,
      icon: Icons.phone_android_rounded,
      label: showSystemAppsLabel,
      trailing: Icon(
        showSystemApps
            ? Icons.check_circle_rounded
            : Icons.radio_button_unchecked_rounded,
        size: 18,
        color: showSystemApps
            ? cs.primary
            : cs.onSurfaceVariant.withValues(alpha: 0.65),
      ),
    ),
    const PopupMenuDivider(height: 6),
    buildAppPopupMenuItem(
      value: AppListOverflowMenuAction.refresh,
      icon: Icons.refresh_rounded,
      label: refreshLabel,
    ),
    const PopupMenuDivider(height: 6),
    buildAppPopupMenuItem(
      value: AppListOverflowMenuAction.enableAll,
      icon: Icons.done_all_rounded,
      label: enableAllLabel,
    ),
    buildAppPopupMenuItem(
      value: AppListOverflowMenuAction.disableAll,
      icon: Icons.block_rounded,
      label: disableAllLabel,
    ),
  ];
}

Future<void> handleAppListOverflowMenuSelection({
  required String value,
  required VoidCallback onToggleSystemApps,
  required Future<void> Function() onRefresh,
  required Future<void> Function() onEnableAll,
  required Future<void> Function() onDisableAll,
}) async {
  switch (value) {
    case AppListOverflowMenuAction.toggleSystem:
      onToggleSystemApps();
      return;
    case AppListOverflowMenuAction.refresh:
      await onRefresh();
      return;
    case AppListOverflowMenuAction.enableAll:
      await onEnableAll();
      return;
    case AppListOverflowMenuAction.disableAll:
      await onDisableAll();
      return;
    default:
      return;
  }
}

class AppPopupMenuLabel extends StatelessWidget {
  const AppPopupMenuLabel({
    super.key,
    required this.icon,
    required this.label,
    this.enabled = true,
    this.trailing,
  });

  final IconData icon;
  final String label;
  final bool enabled;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final color = enabled ? cs.onSurface : cs.onSurface.withValues(alpha: 0.45);

    return Row(
      children: [
        Icon(icon, size: 18, color: color),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            label,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: color,
              fontWeight: FontWeight.w500,
            ),
            overflow: TextOverflow.ellipsis,
          ),
        ),
        if (trailing != null) ...[const SizedBox(width: 8), trailing!],
      ],
    );
  }
}

class FixedSliverHeaderDelegate extends SliverPersistentHeaderDelegate {
  FixedSliverHeaderDelegate({
    required this.height,
    this.minHeight,
    required this.builder,
  });

  final double height;
  final double? minHeight;
  final Widget Function(
    BuildContext context,
    bool overlapsContent,
    double collapseProgress,
  )
  builder;

  @override
  double get minExtent => minHeight ?? height;

  @override
  double get maxExtent => height;

  @override
  Widget build(
    BuildContext context,
    double shrinkOffset,
    bool overlapsContent,
  ) {
    final range = maxExtent - minExtent;
    final progress = range <= 0
        ? 1.0
        : (shrinkOffset / range).clamp(0.0, 1.0).toDouble();
    return builder(context, overlapsContent, progress);
  }

  @override
  bool shouldRebuild(covariant FixedSliverHeaderDelegate oldDelegate) {
    return oldDelegate.height != height ||
        oldDelegate.minHeight != minHeight ||
        oldDelegate.builder != builder;
  }
}
