import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../controllers/home_controller.dart';
import '../widgets/section_label.dart';

const _channel = MethodChannel('com.example.hyperisland/test');

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  late final HomeController _ctrl;
  bool _restarting = false;

  @override
  void initState() {
    super.initState();
    _ctrl = HomeController();
    _ctrl.addListener(() {
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  Future<void> _showRestartDialog() async {
    bool restartSystemUI = true;
    bool restartDownloadManager = true;

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: const Text('重启作用域'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              CheckboxListTile(
                title: const Text('系统界面'),
                subtitle: const Text('com.android.systemui'),
                value: restartSystemUI,
                onChanged: (v) =>
                    setDialogState(() => restartSystemUI = v ?? false),
                controlAffinity: ListTileControlAffinity.leading,
                contentPadding: EdgeInsets.zero,
              ),
              CheckboxListTile(
                title: const Text('下载管理器'),
                subtitle: const Text('com.android.providers.downloads'),
                value: restartDownloadManager,
                onChanged: (v) =>
                    setDialogState(() => restartDownloadManager = v ?? false),
                controlAffinity: ListTileControlAffinity.leading,
                contentPadding: EdgeInsets.zero,
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('取消'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('确认'),
            ),
          ],
        ),
      ),
    );

    if (confirmed != true) return;
    if (!restartSystemUI && !restartDownloadManager) return;

    setState(() => _restarting = true);
    try {
      final commands = <String>[];
      if (restartSystemUI) commands.add('killall com.android.systemui');
      if (restartDownloadManager) {
        commands.add('am force-stop com.android.providers.downloads');
      }
      await _channel.invokeMethod('restartProcesses', {'commands': commands});
    } on PlatformException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('重启失败：${e.message}')),
        );
      }
    } finally {
      if (mounted) setState(() => _restarting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Scaffold(
      backgroundColor: cs.surface,
      body: CustomScrollView(
        slivers: [
          SliverAppBar.large(
            title: const Text('HyperIsland'),
            backgroundColor: cs.surface,
            centerTitle: false,
            actions: [
              _restarting
                  ? const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 16),
                      child: SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      ),
                    )
                  : IconButton(
                      tooltip: '重启作用域',
                      icon: const Icon(Icons.restart_alt),
                      onPressed: _showRestartDialog,
                    ),
            ],
          ),
          SliverPadding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            sliver: SliverList(
              delegate: SliverChildListDelegate([
                _ModuleStatusCard(active: _ctrl.moduleActive),
                const SizedBox(height: 16),

                const SectionLabel('通知测试'),
                const SizedBox(height: 8),
                if (_ctrl.progress > 0 && _ctrl.progress < 100)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: _ProgressCard(progress: _ctrl.progress),
                  ),
                _TestButtons(
                  isSending: _ctrl.isSending,
                  onStartDemo: _ctrl.startProgressDemo,
                  onIndeterminate: () => _ctrl.sendTestNotification('indeterminate'),
                  onComplete: () => _ctrl.sendTestNotification('complete'),
                  onFailed: () => _ctrl.sendTestNotification('failed'),
                  onCustom: () => _ctrl.sendTestNotification('custom'),
                ),
                const SizedBox(height: 24),

                const SectionLabel('注意事项'),
                const SizedBox(height: 8),
                const _NotesCard(),
                const SizedBox(height: 24),
              ]),
            ),
          ),
        ],
      ),
    );
  }
}

// ── 页面专属组件 ──────────────────────────────────────────────────────────────

class _ModuleStatusCard extends StatelessWidget {
  final bool? active;
  const _ModuleStatusCard({required this.active});

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    if (active == null) {
      return Card(
        elevation: 0,
        color: cs.surfaceContainerHighest,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        child: const Padding(
          padding: EdgeInsets.all(20),
          child: Row(
            children: [
              SizedBox(
                width: 24,
                height: 24,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
              SizedBox(width: 16),
              Text('正在检测模块状态...'),
            ],
          ),
        ),
      );
    }

    final bool isActive = active!;
    final color = isActive ? Colors.green : cs.error;
    final bgColor = isActive
        ? Colors.green.withValues(alpha: 0.12)
        : cs.errorContainer;

    return Card(
      elevation: 0,
      color: bgColor,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Row(
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.15),
                shape: BoxShape.circle,
              ),
              child: Icon(
                isActive ? Icons.check_circle : Icons.cancel,
                color: color,
                size: 28,
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '模块状态',
                    style: Theme.of(context).textTheme.labelLarge?.copyWith(
                          color: color.withValues(alpha: 0.8),
                        ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    isActive ? '已激活' : '未激活',
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                          color: color,
                          fontWeight: FontWeight.bold,
                        ),
                  ),
                  if (!isActive) ...[
                    const SizedBox(height: 4),
                    Text(
                      '请在 LSPosed 中启用本模块',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: color.withValues(alpha: 0.7),
                          ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ProgressCard extends StatelessWidget {
  final double progress;
  const _ProgressCard({required this.progress});

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Card(
      elevation: 0,
      color: cs.surfaceContainerHighest,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('下载进度',
                    style: Theme.of(context)
                        .textTheme
                        .labelLarge
                        ?.copyWith(color: cs.onSurfaceVariant)),
                Text('${progress.toInt()}%',
                    style: Theme.of(context).textTheme.labelMedium),
              ],
            ),
            const SizedBox(height: 10),
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: progress / 100,
                minHeight: 6,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TestButtons extends StatelessWidget {
  final bool isSending;
  final VoidCallback onStartDemo;
  final VoidCallback onIndeterminate;
  final VoidCallback onComplete;
  final VoidCallback onFailed;
  final VoidCallback onCustom;

  const _TestButtons({
    required this.isSending,
    required this.onStartDemo,
    required this.onIndeterminate,
    required this.onComplete,
    required this.onFailed,
    required this.onCustom,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        FilledButton.icon(
          onPressed: isSending ? null : onStartDemo,
          icon: const Icon(Icons.play_circle_outline),
          label: const Text('开始进度演示'),
          style: FilledButton.styleFrom(
            padding: const EdgeInsets.symmetric(vertical: 14),
            shape:
                RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            Expanded(
              child: _OutlinedActionButton(
                icon: Icons.hourglass_empty,
                label: '不确定进度',
                onPressed: isSending ? null : onIndeterminate,
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: _OutlinedActionButton(
                icon: Icons.check_circle_outline,
                label: '下载完成',
                onPressed: isSending ? null : onComplete,
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            Expanded(
              child: _OutlinedActionButton(
                icon: Icons.error_outline,
                label: '下载失败',
                onPressed: isSending ? null : onFailed,
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: _OutlinedActionButton(
                icon: Icons.notifications_outlined,
                label: '自定义通知',
                onPressed: isSending ? null : onCustom,
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _OutlinedActionButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback? onPressed;

  const _OutlinedActionButton({
    required this.icon,
    required this.label,
    this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return OutlinedButton.icon(
      onPressed: onPressed,
      icon: Icon(icon, size: 18),
      label: Text(label, style: const TextStyle(fontSize: 13)),
      style: OutlinedButton.styleFrom(
        padding: const EdgeInsets.symmetric(vertical: 12),
        shape:
            RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    );
  }
}

class _NotesCard extends StatelessWidget {
  const _NotesCard();

  static const _items = [
    '1.此页面仅用于测试是否支持超级岛，并不代表实际效果',
    '2.请在 HyperCeiler 中关闭系统界面和小米服务框架的焦点通知白名单',
    '3.LSPosed 管理器中激活后，必须并重启相关作用域软件',
    '4.支持通用适配有进度条的通知，自行勾选作用域即可实现',
  ];

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Card(
      elevation: 0,
      color: cs.surfaceContainerHighest,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: _items
              .map(
                (text) => Padding(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(Icons.arrow_right,
                          size: 20, color: cs.onSurfaceVariant),
                      const SizedBox(width: 4),
                      Expanded(
                        child: Text(
                          text,
                          style: Theme.of(context)
                              .textTheme
                              .bodyMedium
                              ?.copyWith(color: cs.onSurfaceVariant),
                        ),
                      ),
                    ],
                  ),
                ),
              )
              .toList(),
        ),
      ),
    );
  }
}
