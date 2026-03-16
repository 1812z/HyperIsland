import 'package:flutter/material.dart';
import '../controllers/whitelist_controller.dart';

/// 渠道高级设置对话框。
/// 所有改动即时通过回调保存，OK 按钮仅关闭对话框。
class ChannelSettingsDialog extends StatefulWidget {
  const ChannelSettingsDialog({
    super.key,
    required this.channelName,
    required this.template,
    required this.templateLabels,
    required this.iconMode,
    required this.focusIconMode,
    required this.focusNotif,
    required this.firstFloat,
    required this.enableFloat,
    required this.islandTimeout,
    required this.onTemplateChanged,
    required this.onIconModeChanged,
    required this.onFocusIconModeChanged,
    required this.onFocusNotifChanged,
    required this.onFirstFloatChanged,
    required this.onEnableFloatChanged,
    required this.onIslandTimeoutChanged,
  });

  final String channelName;
  final String template;
  final Map<String, String> templateLabels;
  final String iconMode;
  final String focusIconMode;
  final String focusNotif;
  final String firstFloat;
  final String enableFloat;
  final String islandTimeout;
  final ValueChanged<String> onTemplateChanged;
  final ValueChanged<String> onIconModeChanged;
  final ValueChanged<String> onFocusIconModeChanged;
  final ValueChanged<String> onFocusNotifChanged;
  final ValueChanged<String> onFirstFloatChanged;
  final ValueChanged<String> onEnableFloatChanged;
  final ValueChanged<String> onIslandTimeoutChanged;

  @override
  State<ChannelSettingsDialog> createState() => _ChannelSettingsDialogState();
}

class _ChannelSettingsDialogState extends State<ChannelSettingsDialog> {
  late String _template;
  late String _iconMode;
  late String _focusIconMode;
  late String _focusNotif;
  late String _firstFloat;
  late String _enableFloat;
  late String _islandTimeout;

  static const _timeoutOptions = [3, 5, 10, 30, 60, 300, 1800, 3600];

  String _normalizeTimeout(String v) {
    final n = int.tryParse(v) ?? 0;
    // 找最近的选项，默认取第一个
    if (_timeoutOptions.contains(n)) return v;
    return _timeoutOptions.first.toString();
  }

  @override
  void initState() {
    super.initState();
    _template      = widget.template;
    _iconMode      = widget.iconMode;
    _focusIconMode = widget.focusIconMode;
    _focusNotif    = widget.focusNotif;
    _firstFloat    = widget.firstFloat;
    _enableFloat   = widget.enableFloat;
    _islandTimeout = _normalizeTimeout(widget.islandTimeout);
  }

  @override
  void dispose() {
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final cs   = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;

    final keyboardHeight = MediaQuery.of(context).viewInsets.bottom;

    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      insetPadding: EdgeInsets.fromLTRB(28, 40, 28, 40 + keyboardHeight),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(24, 24, 24, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 标题
            Text(widget.channelName,
                style: text.titleLarge,
                maxLines: 1,
                overflow: TextOverflow.ellipsis),
            const SizedBox(height: 4),
            Text('渠道设置',
                style: text.bodySmall?.copyWith(color: cs.onSurfaceVariant)),
            const Divider(height: 24),

            // 模板
            _SettingRow(
              label: '模板',
              child: _DropdownField<String>(
                value: _template,
                items: widget.templateLabels.entries
                    .map((e) => DropdownMenuItem(
                          value: e.key,
                          child: Text(e.value),
                        ))
                    .toList(),
                onChanged: (v) {
                  if (v == null) return;
                  setState(() => _template = v);
                  widget.onTemplateChanged(v);
                },
              ),
            ),
            const SizedBox(height: 14),

            // 图标样式
            _SettingRow(
              label: '超级岛图标',
              child: _DropdownField<String>(
                value: _iconMode,
                items: const [
                  DropdownMenuItem(value: kIconModeAuto,       child: Text('自动')),
                  DropdownMenuItem(value: kIconModeNotifSmall, child: Text('通知小图标')),
                  DropdownMenuItem(value: kIconModeNotifLarge, child: Text('通知大图标')),
                  DropdownMenuItem(value: kIconModeAppIcon,    child: Text('应用图标')),
                ],
                onChanged: (v) {
                  if (v == null) return;
                  setState(() => _iconMode = v);
                  widget.onIconModeChanged(v);
                },
              ),
            ),
            const SizedBox(height: 14),

            // 焦点图标
            _SettingRow(
              label: '焦点图标',
              child: _DropdownField<String>(
                value: _focusIconMode,
                items: const [
                  DropdownMenuItem(value: kIconModeAuto,       child: Text('自动')),
                  DropdownMenuItem(value: kIconModeNotifSmall, child: Text('通知小图标')),
                  DropdownMenuItem(value: kIconModeNotifLarge, child: Text('通知大图标')),
                  DropdownMenuItem(value: kIconModeAppIcon,    child: Text('应用图标')),
                ],
                onChanged: (v) {
                  if (v == null) return;
                  setState(() => _focusIconMode = v);
                  widget.onFocusIconModeChanged(v);
                },
              ),
            ),
            const SizedBox(height: 14),

            // 焦点通知
            _SettingRow(
              label: '焦点通知',
              child: _DropdownField<String>(
                value: _focusNotif,
                items: const [
                  DropdownMenuItem(value: kTriOptDefault, child: Text('默认')),
                  DropdownMenuItem(value: kTriOptOff,     child: Text('关闭')),
                ],
                onChanged: (v) {
                  if (v == null) return;
                  setState(() => _focusNotif = v);
                  widget.onFocusNotifChanged(v);
                },
              ),
            ),
            const SizedBox(height: 14),

            // 初次展开
            _SettingRow(
              label: '初次展开',
              child: _DropdownField<String>(
                value: _firstFloat,
                items: const [
                  DropdownMenuItem(value: kTriOptDefault, child: Text('默认')),
                  DropdownMenuItem(value: kTriOptOn,      child: Text('开启')),
                  DropdownMenuItem(value: kTriOptOff,     child: Text('关闭')),
                ],
                onChanged: (v) {
                  if (v == null) return;
                  setState(() => _firstFloat = v);
                  widget.onFirstFloatChanged(v);
                },
              ),
            ),
            const SizedBox(height: 14),

            // 更新展开
            _SettingRow(
              label: '更新展开',
              child: _DropdownField<String>(
                value: _enableFloat,
                items: const [
                  DropdownMenuItem(value: kTriOptDefault, child: Text('默认')),
                  DropdownMenuItem(value: kTriOptOn,      child: Text('开启')),
                  DropdownMenuItem(value: kTriOptOff,     child: Text('关闭')),
                ],
                onChanged: (v) {
                  if (v == null) return;
                  setState(() => _enableFloat = v);
                  widget.onEnableFloatChanged(v);
                },
              ),
            ),
            const SizedBox(height: 14),

            // 自动消失时间
            _SettingRow(
              label: '自动消失',
              child: _DropdownField<String>(
                value: _islandTimeout,
                items: _timeoutOptions
                    .map((s) => DropdownMenuItem(
                          value: s.toString(),
                          child: Text('$s 秒'),
                        ))
                    .toList(),
                onChanged: (v) {
                  if (v == null) return;
                  setState(() => _islandTimeout = v);
                  widget.onIslandTimeoutChanged(v);
                },
              ),
            ),

            const SizedBox(height: 20),

            // OK 按钮
            Align(
              alignment: Alignment.centerRight,
              child: FilledButton(
                onPressed: () => Navigator.pop(context),
                style: FilledButton.styleFrom(
                  minimumSize: const Size(96, 44),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12)),
                ),
                child: const Text('OK'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}


// ── 设置行布局 ────────────────────────────────────────────────────────────────

class _SettingRow extends StatelessWidget {
  const _SettingRow({required this.label, required this.child});
  final String label;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        SizedBox(
          width: 76,
          child: Text(label,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  )),
        ),
        Expanded(child: child),
      ],
    );
  }
}

// ── 下拉选择框 ────────────────────────────────────────────────────────────────

class _DropdownField<T> extends StatelessWidget {
  const _DropdownField({
    required this.value,
    required this.items,
    required this.onChanged,
  });

  final T value;
  final List<DropdownMenuItem<T>> items;
  final ValueChanged<T?> onChanged;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return DropdownButtonFormField<T>(
      value: value,
      items: items,
      onChanged: onChanged,
      isExpanded: true,
      decoration: InputDecoration(
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
        filled: true,
        fillColor: cs.surfaceContainerHighest,
      ),
    );
  }
}
