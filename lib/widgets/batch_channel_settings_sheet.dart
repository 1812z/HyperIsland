import 'package:flutter/material.dart';
import '../controllers/whitelist_controller.dart';

/// 批量应用渠道配置的结果。
/// [settings] 中 null 表示该项不更改；[onlyEnabled] 表示仅应用到已启用渠道。
class BatchApplyResult {
  final Map<String, String?> settings;
  final bool onlyEnabled;

  const BatchApplyResult({
    required this.settings,
    required this.onlyEnabled,
  });
}

/// 批量设置渠道配置底部弹窗。
///
/// 通过静态方法 [show] 打开，返回用户确认的 [BatchApplyResult]（取消时返回 null）。
///
/// - [subtitle]：覆盖默认副标题文本，用于全局批量等场景。
/// - [showScopeToggle]：是否显示"仅应用到已启用渠道"切换项，全局模式下可关闭。
class BatchChannelSettingsSheet extends StatefulWidget {
  const BatchChannelSettingsSheet({
    super.key,
    this.totalChannels = 0,
    this.enabledChannels = 0,
    required this.templateLabels,
    this.subtitle,
    this.showScopeToggle = true,
  });

  final int totalChannels;
  final int enabledChannels;
  final Map<String, String> templateLabels;
  final String? subtitle;
  final bool showScopeToggle;

  static Future<BatchApplyResult?> show(
    BuildContext context, {
    int totalChannels = 0,
    int enabledChannels = 0,
    required Map<String, String> templateLabels,
    String? subtitle,
    bool showScopeToggle = true,
  }) {
    return showModalBottomSheet<BatchApplyResult>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      builder: (_) => BatchChannelSettingsSheet(
        totalChannels: totalChannels,
        enabledChannels: enabledChannels,
        templateLabels: templateLabels,
        subtitle: subtitle,
        showScopeToggle: showScopeToggle,
      ),
    );
  }

  @override
  State<BatchChannelSettingsSheet> createState() =>
      _BatchChannelSettingsSheetState();
}

class _BatchChannelSettingsSheetState
    extends State<BatchChannelSettingsSheet> {
  // null = 不更改该项
  String? _template;
  String? _iconMode;
  String? _focusIconMode;
  String? _focusNotif;
  String? _firstFloat;
  String? _enableFloat;
  String? _islandTimeout;
  bool _onlyEnabled = false;

  static const _timeoutOptions = [3, 5, 10, 30, 60, 300, 1800, 3600];

  bool get _hasAnyChange =>
      _template != null ||
      _iconMode != null ||
      _focusIconMode != null ||
      _focusNotif != null ||
      _firstFloat != null ||
      _enableFloat != null ||
      _islandTimeout != null;

  int get _targetCount =>
      _onlyEnabled ? widget.enabledChannels : widget.totalChannels;

  void _submit() {
    Navigator.pop(
      context,
      BatchApplyResult(
        settings: {
          'template':     _template,
          'icon':         _iconMode,
          'focus_icon':   _focusIconMode,
          'focus':        _focusNotif,
          'first_float':  _firstFloat,
          'enable_float': _enableFloat,
          'timeout':      _islandTimeout,
        },
        onlyEnabled: _onlyEnabled,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final cs   = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    final keyboardHeight = MediaQuery.of(context).viewInsets.bottom;

    return Padding(
      padding: EdgeInsets.only(bottom: keyboardHeight),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // ── 拖拽把手 ────────────────────────────────────────────────────
          Container(
            margin: const EdgeInsets.symmetric(vertical: 12),
            width: 32,
            height: 4,
            decoration: BoxDecoration(
              color: cs.onSurfaceVariant.withValues(alpha: 0.4),
              borderRadius: BorderRadius.circular(2),
            ),
          ),

          // ── 标题区 ──────────────────────────────────────────────────────
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 0, 24, 16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('批量设置渠道配置', style: text.titleLarge),
                const SizedBox(height: 2),
                Text(
                  widget.subtitle ?? '将应用到 $_targetCount 个渠道',
                  style: text.bodySmall?.copyWith(color: cs.onSurfaceVariant),
                ),
              ],
            ),
          ),

          const Divider(height: 1),

          // ── 可滚动内容区 ─────────────────────────────────────────────────
          Flexible(
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(24, 16, 24, 8),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // 仅应用到已启用渠道 开关（全局模式下隐藏）
                  if (widget.showScopeToggle) ...[
                    _ScopeToggleCard(
                      totalChannels: widget.totalChannels,
                      enabledChannels: widget.enabledChannels,
                      value: _onlyEnabled,
                      onChanged: widget.enabledChannels > 0
                          ? (v) => setState(() => _onlyEnabled = v)
                          : null,
                    ),
                    const SizedBox(height: 16),
                    const Divider(height: 1),
                    const SizedBox(height: 16),
                  ],

                  // 模板
                  _BatchSettingRow(
                    label: '模板',
                    value: _template,
                    items: widget.templateLabels.entries
                        .map((e) => DropdownMenuItem<String?>(
                              value: e.key,
                              child: Text(e.value),
                            ))
                        .toList(),
                    onChanged: (v) => setState(() => _template = v),
                  ),
                  const SizedBox(height: 12),

                  // 超级岛图标
                  _BatchSettingRow(
                    label: '超级岛图标',
                    value: _iconMode,
                    items: const [
                      DropdownMenuItem(value: kIconModeAuto,       child: Text('自动')),
                      DropdownMenuItem(value: kIconModeNotifSmall, child: Text('通知小图标')),
                      DropdownMenuItem(value: kIconModeNotifLarge, child: Text('通知大图标')),
                      DropdownMenuItem(value: kIconModeAppIcon,    child: Text('应用图标')),
                    ],
                    onChanged: (v) => setState(() => _iconMode = v),
                  ),
                  const SizedBox(height: 12),

                  // 焦点图标
                  _BatchSettingRow(
                    label: '焦点图标',
                    value: _focusIconMode,
                    items: const [
                      DropdownMenuItem(value: kIconModeAuto,       child: Text('自动')),
                      DropdownMenuItem(value: kIconModeNotifSmall, child: Text('通知小图标')),
                      DropdownMenuItem(value: kIconModeNotifLarge, child: Text('通知大图标')),
                      DropdownMenuItem(value: kIconModeAppIcon,    child: Text('应用图标')),
                    ],
                    onChanged: (v) => setState(() => _focusIconMode = v),
                  ),
                  const SizedBox(height: 12),

                  // 焦点通知
                  _BatchSettingRow(
                    label: '焦点通知',
                    value: _focusNotif,
                    items: const [
                      DropdownMenuItem(value: kTriOptDefault, child: Text('默认')),
                      DropdownMenuItem(value: kTriOptOff,     child: Text('关闭')),
                    ],
                    onChanged: (v) => setState(() => _focusNotif = v),
                  ),
                  const SizedBox(height: 12),

                  // 初次展开
                  _BatchSettingRow(
                    label: '初次展开',
                    value: _firstFloat,
                    items: const [
                      DropdownMenuItem(value: kTriOptDefault, child: Text('默认')),
                      DropdownMenuItem(value: kTriOptOn,      child: Text('开启')),
                      DropdownMenuItem(value: kTriOptOff,     child: Text('关闭')),
                    ],
                    onChanged: (v) => setState(() => _firstFloat = v),
                  ),
                  const SizedBox(height: 12),

                  // 更新展开
                  _BatchSettingRow(
                    label: '更新展开',
                    value: _enableFloat,
                    items: const [
                      DropdownMenuItem(value: kTriOptDefault, child: Text('默认')),
                      DropdownMenuItem(value: kTriOptOn,      child: Text('开启')),
                      DropdownMenuItem(value: kTriOptOff,     child: Text('关闭')),
                    ],
                    onChanged: (v) => setState(() => _enableFloat = v),
                  ),
                  const SizedBox(height: 12),

                  // 自动消失
                  _BatchSettingRow(
                    label: '自动消失',
                    value: _islandTimeout,
                    items: _timeoutOptions
                        .map((s) => DropdownMenuItem<String?>(
                              value: s.toString(),
                              child: Text('$s 秒'),
                            ))
                        .toList(),
                    onChanged: (v) => setState(() => _islandTimeout = v),
                  ),
                  const SizedBox(height: 24),
                ],
              ),
            ),
          ),

          // ── 底部按钮区 ───────────────────────────────────────────────────
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 8, 24, 16),
            child: Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => Navigator.pop(context),
                    style: OutlinedButton.styleFrom(
                      minimumSize: const Size.fromHeight(48),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12)),
                    ),
                    child: const Text('取消'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton(
                    onPressed: _hasAnyChange ? _submit : null,
                    style: FilledButton.styleFrom(
                      minimumSize: const Size.fromHeight(48),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12)),
                    ),
                    child: const Text('应用'),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ── 应用范围切换卡片 ──────────────────────────────────────────────────────────

class _ScopeToggleCard extends StatelessWidget {
  const _ScopeToggleCard({
    required this.totalChannels,
    required this.enabledChannels,
    required this.value,
    required this.onChanged,
  });

  final int totalChannels;
  final int enabledChannels;
  final bool value;
  final ValueChanged<bool>? onChanged;

  @override
  Widget build(BuildContext context) {
    final cs   = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    final enabled = onChanged != null;

    return Material(
      color: cs.surfaceContainerHighest,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: enabled ? () => onChanged!(!value) : null,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '仅应用到已启用渠道',
                      style: text.bodyMedium?.copyWith(
                        color: enabled ? null : cs.onSurface.withValues(alpha: 0.38),
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '已启用 $enabledChannels / $totalChannels 个渠道',
                      style: text.bodySmall?.copyWith(
                        color: enabled
                            ? cs.onSurfaceVariant
                            : cs.onSurface.withValues(alpha: 0.28),
                      ),
                    ),
                  ],
                ),
              ),
              Switch(value: value, onChanged: onChanged),
            ],
          ),
        ),
      ),
    );
  }
}

// ── 设置行（带"不更改"选项的下拉框）──────────────────────────────────────────

class _BatchSettingRow extends StatelessWidget {
  const _BatchSettingRow({
    required this.label,
    required this.value,
    required this.items,
    required this.onChanged,
  });

  final String label;
  final String? value;
  final List<DropdownMenuItem<String?>> items;
  final ValueChanged<String?> onChanged;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Row(
      children: [
        SizedBox(
          width: 76,
          child: Text(
            label,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: cs.onSurfaceVariant,
                ),
          ),
        ),
        Expanded(
          child: DropdownButtonFormField<String?>(
            key: ValueKey(value),
            initialValue: value,
            isExpanded: true,
            items: [
              const DropdownMenuItem<String?>(
                value: null,
                child: Text('不更改'),
              ),
              ...items,
            ],
            onChanged: onChanged,
            decoration: InputDecoration(
              contentPadding:
                  const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              border:
                  OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
              filled: true,
              fillColor: cs.surfaceContainerHighest,
            ),
          ),
        ),
      ],
    );
  }
}
