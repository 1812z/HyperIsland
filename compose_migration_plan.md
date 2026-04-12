# Compose 重构清单（可直接开工）

1. **先定迁移策略**
- 选 `一次性切换` 或 `分阶段切换`。
- 建议分阶段：先上原生壳和基础页，再迁 Apps/Channels，再删 Flutter。

2. **建立新 UI 架构**
- 技术栈：`Compose + Material3 + MiuiX + ViewModel + DataStore(或继续SharedPreferences)`。
- 目录建议：
  - `android/app/src/main/kotlin/io/github/hyperisland/ui/`
  - `ui/home`, `ui/apps`, `ui/channels`, `ui/settings`, `ui/ai`, `ui/common`
  - `data/prefs`, `data/repo`, `domain`

3. **配置存储兼容（最关键）**
- 保持所有 `pref_*` 键名完全不变。
- 保持 CSV/渠道键格式不变（如 `pref_generic_whitelist`, `pref_channel_*`）。
- 保持 `FlutterSharedPreferences` 文件名或做兼容读取，确保 Xposed `ConfigManager` 无感。

4. **原生能力层替换**
- 把 `MethodChannel` 能力改成本地 Kotlin UseCase/Repository：
  - 模块状态检测
  - LSPosed API 版本
  - 获取应用列表/图标
  - 读取通知渠道（root + policy xml）
  - 重启作用域进程
  - 桌面图标显隐

5. **页面迁移顺序**
- 第 1 批：Home（状态检测、测试通知、重启作用域）
- 第 2 批：Apps（白名单列表、搜索、批量开关）
- 第 3 批：Channels（渠道开关、模板、渲染器、高级参数）
- 第 4 批：Settings（全局开关、主题语言、导入导出）
- 第 5 批：AI 配置页与日志

6. **状态管理改造**
- 每页 `ViewModel + StateFlow`。
- 偏好变更统一走 `SettingsRepository`。
- 监听配置变化时，及时写入并刷新 UI（替代 Flutter `ChangeNotifier`）。

7. **国际化迁移**
- 把 Flutter ARB 文案迁移到 `res/values*/strings.xml`。
- 先迁中文+英文，其他语种第二批补齐。

8. **测试与验收**
- 回归重点：
  - 配置改动后 Xposed 热生效
  - 白名单/渠道规则命中一致
  - 下载模板与 AI 模板行为一致
  - Root 失败路径与弹窗提示一致
- 真机验证：`SystemUI / DownloadManager / XMSF` 三作用域都测。

9. **清理 Flutter**
- 移除 Dart 与 Flutter 依赖、插件、`lib/` 入口。
- 清理 `pubspec*`、Flutter Gradle 配置、无用资源。
- 保留原 Kotlin/Xposed 包结构不动。

10. **建议里程碑**
- M1（1周）：Home + Settings 基础可用
- M2（1-2周）：Apps + Channels 全量功能
- M3（3-5天）：多语言、测试、移除 Flutter、发布包

如果你要，我下一步可以直接给你“可落地的目标文件树 + 首批基础代码骨架（Activity/NavHost/ViewModel/PrefsRepository）”。
