import 'package:flutter/material.dart';
import '../../controllers/settings_controller.dart';
import '../../l10n/generated/app_localizations.dart';
import '../../services/interaction_haptics.dart';
import '../../widgets/blur_app_bar.dart';
import '../../widgets/modern_slider.dart';

class IslandOtherPage extends StatefulWidget {
  const IslandOtherPage({super.key});

  @override
  State<IslandOtherPage> createState() => _IslandOtherPageState();
}

class _IslandOtherPageState extends State<IslandOtherPage> {
  final _ctrl = SettingsController.instance;
  late int _marqueeSpeedDraft;
  late int _buildHash;

  int _computeHash() => Object.hashAll([
    _ctrl.fullscreenBehavior,
    _ctrl.landscapeBehavior,
    _ctrl.dndBehavior,
    _ctrl.marqueeSpeed,
  ]);

  @override
  void initState() {
    super.initState();
    _marqueeSpeedDraft = _ctrl.marqueeSpeed;
    _buildHash = _computeHash();
    _ctrl.addListener(_onChanged);
    // 消息滚动不再有开关，确保 feature 始终启用
    if (!_ctrl.marqueeFeature) {
      _ctrl.setMarqueeFeature(true);
    }
  }

  @override
  void dispose() {
    _ctrl.removeListener(_onChanged);
    super.dispose();
  }

  void _onChanged() {
    if (!mounted) return;
    final nextHash = _computeHash();
    final nextSpeed = _ctrl.marqueeSpeed;
    if (nextHash == _buildHash && nextSpeed == _marqueeSpeedDraft) return;
    setState(() {
      _buildHash = nextHash;
      _marqueeSpeedDraft = nextSpeed;
    });
  }

  String _fullscreenBehaviorLabel(AppLocalizations l10n, String value) {
    return switch (value) {
      'fallback' => l10n.fullscreenBehaviorFallback,
      'expand' => l10n.autoExpandNotification,
      _ => l10n.fullscreenBehaviorOff,
    };
  }

  String _dndBehaviorLabel(AppLocalizations l10n, String value) {
    return switch (value) {
      'suppress' => l10n.fullscreenBehaviorFallback,
      'small_only' => l10n.sceneActionSmallOnly,
      _ => l10n.optDefault,
    };
  }

  String _behaviorPreview(AppLocalizations l10n, String value) {
    return switch (value) {
      'fallback' || 'suppress' => l10n.behaviorPreviewSuppress,
      'small_only' => l10n.behaviorPreviewSmallOnly,
      'expand' => l10n.behaviorPreviewExpand,
      _ => l10n.behaviorPreviewDefault,
    };
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;
    final titleStyle = Theme.of(context).textTheme.titleMedium;

    return Scaffold(
      backgroundColor: cs.surface,
      body: BlurAppBarHost(
        title: l10n.islandOtherSection,
        physics: const ClampingScrollPhysics(),
        slivers: [
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                const SizedBox(height: 8),
                // --- 过滤规则 ---
                _SectionLabel(l10n.filterRulesTitle),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  child: Column(
                    children: [
                      Padding(
                        padding: const EdgeInsets.fromLTRB(16, 14, 16, 8),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(l10n.filterRulesOrderTitle, style: titleStyle),
                            const SizedBox(height: 10),
                            Wrap(
                              spacing: 8,
                              runSpacing: 8,
                              children: [
                                _RulePriorityChip(
                                  index: 1,
                                  label: l10n.filterRuleDnd,
                                ),
                                _RulePriorityChip(
                                  index: 2,
                                  label: l10n.filterRuleFullscreen,
                                ),
                                _RulePriorityChip(
                                  index: 3,
                                  label: l10n.filterRuleLandscape,
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _BehaviorRuleTile(
                        icon: Icons.do_not_disturb_on_outlined,
                        title: l10n.dndBehaviorTitle,
                        subtitle: _behaviorPreview(l10n, _ctrl.dndBehavior),
                        value: _ctrl.dndBehavior,
                        labelForValue: (v) => _dndBehaviorLabel(l10n, v),
                        values: const ['default', 'suppress', 'small_only'],
                        onChanged: _ctrl.setDndBehavior,
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _BehaviorRuleTile(
                        icon: Icons.fullscreen_outlined,
                        title: l10n.fullscreenRuleTitle,
                        subtitle: _behaviorPreview(
                          l10n,
                          _ctrl.fullscreenBehavior,
                        ),
                        value: _ctrl.fullscreenBehavior,
                        labelForValue: (v) => _fullscreenBehaviorLabel(l10n, v),
                        values: const ['off', 'fallback', 'expand'],
                        onChanged: _ctrl.setFullscreenBehavior,
                      ),
                      const Divider(height: 1, indent: 16, endIndent: 16),
                      _BehaviorRuleTile(
                        icon: Icons.screen_rotation_alt_outlined,
                        title: l10n.landscapeRuleTitle,
                        subtitle: _behaviorPreview(
                          l10n,
                          _ctrl.landscapeBehavior,
                        ),
                        value: _ctrl.landscapeBehavior,
                        labelForValue: (v) => _fullscreenBehaviorLabel(l10n, v),
                        values: const ['off', 'fallback', 'expand'],
                        onChanged: _ctrl.setLandscapeBehavior,
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 8),
                // --- 消息滚动（速度滑块，无开关） ---
                _SectionLabel(l10n.marqueeChannelTitle),
                const SizedBox(height: 8),
                Card(
                  elevation: 0,
                  color: cs.surfaceContainerHighest,
                  child: ListTile(
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 16,
                      vertical: 2,
                    ),
                    title: Row(
                      children: [
                        Expanded(
                          child: Text(
                            l10n.marqueeChannelTitle,
                            style: titleStyle,
                          ),
                        ),
                        Text(
                          l10n.marqueeSpeedLabel(_marqueeSpeedDraft),
                          style: Theme.of(context).textTheme.bodySmall
                              ?.copyWith(color: cs.onSurfaceVariant),
                        ),
                        if (_marqueeSpeedDraft != 100)
                          SizedBox(
                            width: 18,
                            height: 18,
                            child: IconButton(
                              icon: const Icon(Icons.refresh, size: 18),
                              padding: EdgeInsets.zero,
                              visualDensity: VisualDensity.compact,
                              onPressed: InteractionHaptics.interceptButton(() {
                                setState(() => _marqueeSpeedDraft = 100);
                                _ctrl.setMarqueeSpeed(100);
                              }),
                            ),
                          ),
                      ],
                    ),
                    subtitle: Row(
                      children: [
                        Text(
                          l10n.marqueeSpeedTitle,
                          style: Theme.of(context).textTheme.bodySmall
                              ?.copyWith(color: cs.onSurfaceVariant),
                        ),
                        Expanded(
                          child: SliderTheme(
                            data: ModernSliderTheme.theme(context),
                            child: Slider(
                              value: _marqueeSpeedDraft.toDouble(),
                              min: 20,
                              max: 500,
                              divisions: 48,
                              onChanged: InteractionHaptics.interceptSlider((
                                v,
                              ) {
                                final next = v.round();
                                if (_marqueeSpeedDraft == next) return;
                                setState(() => _marqueeSpeedDraft = next);
                              }),
                              onChangeEnd: (v) async {
                                final next = v.round();
                                if (_ctrl.marqueeSpeed == next) return;
                                await _ctrl.setMarqueeSpeed(next);
                              },
                            ),
                          ),
                        ),
                      ],
                    ),
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

class _SectionLabel extends StatelessWidget {
  final String text;
  const _SectionLabel(this.text);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 18),
      child: Text(
        text,
        style: Theme.of(context).textTheme.titleSmall?.copyWith(
          color: Theme.of(context).colorScheme.primary,
          fontWeight: FontWeight.bold,
          letterSpacing: 0.5,
        ),
      ),
    );
  }
}

class _RulePriorityChip extends StatelessWidget {
  const _RulePriorityChip({required this.index, required this.label});

  final int index;
  final String label;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: cs.surfaceContainerHigh,
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 18,
            height: 18,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: cs.primary,
              shape: BoxShape.circle,
            ),
            child: Text(
              '$index',
              style: Theme.of(context).textTheme.labelSmall?.copyWith(
                color: cs.onPrimary,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
          const SizedBox(width: 6),
          Text(
            label,
            style: Theme.of(
              context,
            ).textTheme.labelLarge?.copyWith(color: cs.onSurfaceVariant),
          ),
        ],
      ),
    );
  }
}

class _BehaviorRuleTile extends StatelessWidget {
  const _BehaviorRuleTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.value,
    required this.labelForValue,
    required this.values,
    required this.onChanged,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final String value;
  final String Function(String value) labelForValue;
  final List<String> values;
  final Future<void> Function(String value) onChanged;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final configured = value != 'default' && value != 'off';
    final dropdownWidth = (MediaQuery.sizeOf(context).width * 0.36).clamp(
      112.0,
      172.0,
    );

    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      leading: Container(
        width: 40,
        height: 40,
        decoration: BoxDecoration(
          color: configured ? cs.primaryContainer : cs.surfaceContainerHigh,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Icon(
          icon,
          color: configured ? cs.onPrimaryContainer : cs.onSurfaceVariant,
        ),
      ),
      title: Text(title),
      subtitle: Text(
        subtitle,
        style: Theme.of(
          context,
        ).textTheme.bodySmall?.copyWith(color: cs.onSurfaceVariant),
      ),
      trailing: DropdownButtonHideUnderline(
        child: SizedBox(
          width: dropdownWidth,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 10),
            decoration: BoxDecoration(
              color: cs.surfaceContainerHigh,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: cs.outlineVariant),
            ),
            child: DropdownButton<String>(
              value: value,
              isExpanded: true,
              alignment: Alignment.center,
              borderRadius: BorderRadius.circular(16),
              onChanged: InteractionHaptics.interceptDropdown((next) async {
                if (next == null) return;
                await onChanged(next);
              }),
              selectedItemBuilder: (context) => [
                for (final item in values)
                  Center(
                    child: Text(
                      labelForValue(item),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
              ],
              items: [
                for (final item in values)
                  DropdownMenuItem<String>(
                    value: item,
                    child: Text(labelForValue(item)),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
