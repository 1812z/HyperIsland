import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../l10n/generated/app_localizations.dart';
import '../services/interaction_haptics.dart';
import '../widgets/blur_app_bar.dart';

class ReferenceProject {
  const ReferenceProject({required this.name, required this.url});

  final String name;
  final String url;
}

const referenceProjects = <ReferenceProject>[
  ReferenceProject(
    name: 'HyperIsland ToolKit',
    url: 'https://github.com/D4vidDf/HyperIsland-ToolKit',
  ),
  ReferenceProject(
    name: 'libxposed API',
    url: 'https://github.com/libxposed/api',
  ),
  ReferenceProject(
    name: 'MIUISmoothIsland',
    url: 'https://github.com/Leaf-lsgtky/MIUISmoothIsland',
  ),
  ReferenceProject(
    name: 'HyperLight',
    url: 'https://github.com/KiminonawaResa/HyperLight',
  ),
  ReferenceProject(
    name: 'HyperCeiler',
    url: 'https://github.com/ReChronoRain/HyperCeiler',
  ),
  ReferenceProject(
    name: 'AndroidX Graphics Shapes',
    url:
        'https://github.com/androidx/androidx/tree/androidx-main/graphics/graphics-shapes',
  ),
  ReferenceProject(name: 'Flutter', url: 'https://github.com/flutter/flutter'),
];

class ReferencesPage extends StatelessWidget {
  const ReferencesPage({super.key});

  Future<void> _openProject(ReferenceProject project) async {
    await launchUrl(
      Uri.parse(project.url),
      mode: LaunchMode.externalApplication,
    );
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;

    return Scaffold(
      backgroundColor: cs.surface,
      body: BlurAppBarHost(
        title: l10n.referencesTitle,
        physics: const ClampingScrollPhysics(),
        slivers: [
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                const SizedBox(height: 8),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 8),
                  child: Text(
                    l10n.referencesDescription,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: cs.onSurfaceVariant,
                      height: 1.5,
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  child: Column(
                    children: [
                      for (
                        var index = 0;
                        index < referenceProjects.length;
                        index++
                      ) ...[
                        _ReferenceTile(
                          project: referenceProjects[index],
                          isFirst: index == 0,
                          isLast: index == referenceProjects.length - 1,
                          onTap: InteractionHaptics.interceptButton(
                            () => _openProject(referenceProjects[index]),
                          ),
                        ),
                        if (index < referenceProjects.length - 1)
                          const Divider(height: 1, indent: 16, endIndent: 16),
                      ],
                    ],
                  ),
                ),
                const SizedBox(height: 32),
              ], addAutomaticKeepAlives: false),
            ),
          ),
        ],
      ),
    );
  }
}

class _ReferenceTile extends StatelessWidget {
  const _ReferenceTile({
    required this.project,
    required this.isFirst,
    required this.isLast,
    required this.onTap,
  });

  final ReferenceProject project;
  final bool isFirst;
  final bool isLast;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    BorderRadius? borderRadius;
    if (isFirst && isLast) {
      borderRadius = BorderRadius.circular(16);
    } else if (isFirst) {
      borderRadius = const BorderRadius.vertical(top: Radius.circular(16));
    } else if (isLast) {
      borderRadius = const BorderRadius.vertical(bottom: Radius.circular(16));
    }

    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      shape: borderRadius == null
          ? null
          : RoundedRectangleBorder(borderRadius: borderRadius),
      leading: const Icon(Icons.source_outlined),
      title: Text(project.name, style: Theme.of(context).textTheme.titleMedium),
      subtitle: Text(project.url),
      trailing: const Icon(Icons.open_in_new, size: 18),
      onTap: onTap,
    );
  }
}
