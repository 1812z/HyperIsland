import 'package:flutter/material.dart';
import '../controllers/whitelist_controller.dart';
import '../l10n/generated/app_localizations.dart';
import '../services/app_cache_service.dart';
import '../widgets/app_list_widgets.dart';
import 'app_channels_page.dart';

class TemplatePreviewPage extends StatefulWidget {
  const TemplatePreviewPage({super.key});

  @override
  State<TemplatePreviewPage> createState() => _TemplatePreviewPageState();
}

class _TemplatePreviewPageState extends State<TemplatePreviewPage> {
  final _channelController = WhitelistController();
  final _searchController = TextEditingController();
  final _searchFocusNode = FocusNode();

  List<AppInfo> _apps = [];
  List<AppInfo> _filtered = [];
  bool _loading = true;
  bool _showSystemApps = false;

  @override
  void initState() {
    super.initState();
    _loadApps();
  }

  @override
  void dispose() {
    _channelController.dispose();
    _searchController.dispose();
    _searchFocusNode.dispose();
    super.dispose();
  }

  Future<void> _loadApps({bool forceRefresh = false}) async {
    setState(() => _loading = true);
    final apps = await AppCacheService.instance.getApps(
      forceRefresh: forceRefresh,
    );
    if (!mounted) return;
    setState(() {
      _apps = apps;
      _loading = false;
    });
    _applyFilter(_searchController.text);
  }

  void _applyFilter(String query) {
    final q = query.trim().toLowerCase();
    final filtered = _apps.where((app) {
      if (!_showSystemApps && app.isSystem) return false;
      if (q.isEmpty) return true;
      return app.appName.toLowerCase().contains(q) ||
          app.packageName.toLowerCase().contains(q);
    }).toList();

    filtered.sort((a, b) => a.appName.compareTo(b.appName));
    setState(() => _filtered = filtered);
  }

  void _openAppChannels(AppInfo app) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => AppChannelsPage(
          app: app,
          controller: _channelController,
          appEnabled: true,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final l10n = AppLocalizations.of(context)!;

    return Scaffold(
      backgroundColor: cs.surface,
      body: RefreshIndicator(
        onRefresh: () => _loadApps(forceRefresh: true),
        child: CustomScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverAppBar.large(
              backgroundColor: cs.surface,
              centerTitle: false,
              title: Text(l10n.notificationTest),
              actions: [
                IconButton(
                  tooltip: l10n.showSystemApps,
                  icon: Icon(
                    _showSystemApps
                        ? Icons.phone_android_rounded
                        : Icons.phone_android_outlined,
                  ),
                  onPressed: () {
                    setState(() => _showSystemApps = !_showSystemApps);
                    _applyFilter(_searchController.text);
                  },
                ),
              ],
            ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
              sliver: SliverToBoxAdapter(
                child: AppListSearchHeader(
                  countText: '${l10n.navApps}: ${_filtered.length}',
                  searchController: _searchController,
                  searchFocusNode: _searchFocusNode,
                  hintText: l10n.searchApps,
                  onChanged: _applyFilter,
                  onClear: () {
                    _searchController.clear();
                    _applyFilter('');
                  },
                ),
              ),
            ),
            if (_loading)
              const SliverFillRemaining(
                child: Center(child: CircularProgressIndicator()),
              )
            else if (_filtered.isEmpty)
              SliverFillRemaining(
                child: Center(
                  child: Text(
                    _searchController.text.isEmpty
                        ? l10n.noAppsFound
                        : l10n.noMatchingApps,
                    style: TextStyle(color: cs.onSurfaceVariant),
                    textAlign: TextAlign.center,
                  ),
                ),
              )
            else
              SliverPadding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 32),
                sliver: SliverList.separated(
                  itemCount: _filtered.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 2),
                  itemBuilder: (context, index) {
                    final app = _filtered[index];
                    return AppListItemFrame(
                      app: app,
                      onTap: () => _openAppChannels(app),
                      isFirst: index == 0,
                      isLast: index == _filtered.length - 1,
                      trailing: Icon(
                        Icons.chevron_right,
                        color: cs.onSurfaceVariant,
                        size: 20,
                      ),
                    );
                  },
                ),
              ),
          ],
        ),
      ),
    );
  }
}
