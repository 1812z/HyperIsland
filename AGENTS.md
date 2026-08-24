# HyperIsland 开发指引

澎湃OS3 (HyperOS 3) 超级岛通知增强模块（LSPosed Xposed 模块）。Flutter UI 与 Kotlin Hook 在同一个 Flutter 工程里，应用包名 `io.github.hyperisland`。

## 常用命令

```bash
flutter pub get
flutter analyze                                      # 静态检查（flutter_lints，无自定义规则）
flutter build apk --target-platform=android-arm64    # 官方构建方式，仅 arm64
flutter gen-l10n                                     # 生成多语言（模板是 app_en.arb）
```

文档站（VitePress，位于 `docs/`，用 yarn 1）：

```bash
yarn docs:dev      # 本地预览
yarn docs:build
```

环境：JDK 21、Flutter 3.44.x（CI 锁 3.44.8）、Dart SDK ^3.9.0、compileSdk 37、minSdk 33。`test/` 只有 Flutter 模板自带的 counter 测试，不作为验证手段。

## 架构

- `lib/` — Flutter 端（配置 UI 与读写）
  - `controllers/settings_controller.dart`：全局/默认配置（独立 `pref_*` key）
  - `controllers/whitelist_controller.dart`：应用级、渠道级、Toast 设置及批量应用
  - `services/app_config_store.dart`：应用级配置的 JSON 存取
  - `controllers/config_io_controller.dart`：配置导入/导出
  - `pages/island_sub/default_config_page.dart`：默认配置页
- `android/app/src/main/kotlin/io/github/hyperisland/` — Xposed 端
  - `xposed/HyperIslandModule.kt`：唯一入口，`onPackageLoaded` 按 packageName 分发（systemui / 下载器 / xmsf / settings）
  - `xposed/hook/SystemUI/`：绝大多数 hook；`xposed/template/`：岛模板与渲染器；`xposed/islanddispatch/`：代理通知分发
  - `xposed/ConfigManager.kt`：hook 进程内统一配置读取入口
  - `XposedPrefsSyncApp.kt`：Application，把 Flutter prefs 镜像到 LSPosed RemotePreferences
  - `META-INF/xposed/scope.list`：hook 目标进程声明，新增目标进程必须改这里

Xposed 用的是 libxposed 新 API（`io.github.libxposed.api`），不是旧 XposedBridge。

## 配置链路（最容易踩坑的机制）

1. Flutter 端写入 SharedPreferences：
   - 全局/默认配置：独立 key（如 `pref_default_marquee`），常量定义在 `settings_controller.dart`
   - 应用级/渠道级（schema v2 起）：JSON 存在 `pref_app_config_<packageName>` 一个 key 里，分节为 `toast`、`notification`、`channels.enabled`、`channels.settings.<channelId>.<field>`（见 `AppConfigStore`）
2. `XposedPrefsSyncApp` 监听变更，把**所有 `pref_` 前缀的 key**（少数排除项除外）同步到 LSPosed RemotePreferences，按 key hash 拆成 1 个 core + 32 个 shard，避免 Binder TransactionTooLarge
3. Hook 进程统一通过 `ConfigManager.getBoolean/getString/...("pref_xxx")` 读取。ConfigManager 是兼容门面：`pref_toast_forward_<pkg>`、`pref_channel_xxx_<pkg>_<channelId>` 这类“虚拟 key”实际解析进 `pref_app_config_<pkg>` 的 JSON；旧版独立 key 也兼容

由此产生的硬性规则：

- 新配置 key 必须以 `pref_` 开头，否则不会被同步到 hook 进程
- 新增应用级/渠道级字段必须同时登记两侧字段表：Flutter `AppConfigStore` 的 `_toastLegacyFields` / `_channelLegacyFields` 与 Kotlin `ConfigManager` 的 `TOAST_FIELDS` / `NOTIFICATION_FIELDS` / `CHANNEL_FIELDS`，缺一侧 ConfigManager 就读不到
- `ConfigManager.getString` 返回 default 值时，无法区分“用户设置了这个值”和“JSON 里没有该字段”，逻辑上要按“未配置”处理

## 新增配置项的检查清单

1. 全局默认值：`settings_controller.dart` 加 key 常量、字段、读取与 setter
2. 默认配置页 UI：`lib/pages/island_sub/default_config_page.dart`
3. 渠道级：`whitelist_controller.dart` 加读写方法（走 `AppConfigStore.setChannelSetting`）；`batch_channel_settings_sheet.dart` 加 UI 与提交 key；`app_channels_page.dart` 接读取/传参/保存
4. 应用级（Toast）：`toast_app_settings_page.dart` / `toast_settings_panel.dart`，并把字段登记进两侧字段表（见上）
5. 批量应用：`whitelist_controller.batchApplyChannelSettings` 的 keyMap 加映射
6. 导入导出：`config_io_controller.dart` 加对应前缀
7. Xposed 行为：在对应 hook 里读配置实现；新 hook 需在 `HyperIslandModule.onPackageLoaded` 注册（受开关控制的 hook 用 `ConfigManager.getBoolean` 包住 init）
8. 文案：功能开发阶段可优先用中文固定实现和测试；功能稳定后抽取 i18n，先补英文模板 `app_en.arb` 和中文 `app_zh.arb`，再补 `app_ja/ru/tr.arb`，跑 `flutter gen-l10n`

## SystemUI Hook 要点

- **`ISLAND.md` 是权威参考**：超级岛 View 层级、SMALL/BIG/EXPAND 状态映射、fake 动画层、背景与模糊的写入位置。改视觉类 hook 前必读
- 稳定状态由真实内容 View 绘制，过渡/手势动画由 `DynamicIslandContentFakeView` 绘制；只改一边会出现黑块、错位或模糊突然消失
- 自定义背景图片与焦点 BlurDrawable 都写入 `DynamicIslandBackgroundView.drawable`（外层槽位，二者互斥），不是 `expanded_view`
- 判断 View 当前状态不能只看类名，还要看所属 `DynamicIslandContentView.state`（过渡期会短暂不同步）
- 查证 SystemUI 内部实现用 `opencode.json` 里配置的 jadx MCP（127.0.0.1:9999）
- hook 持有的对象用弱引用，缓存要有清理和数量上限（参考 `ActiveIslandDismissHook`）

## 国际化约定

- 模板语言是英文（`l10n.yaml`：`template-arb-file: app_en.arb`）
- 流程：开发阶段优先固定中文实现，方便测试；功能稳定后再做国际化
- 英文仍是模板与缺失翻译的回退语言，避免其他语言未完善时显示中文
- 只改 `lib/l10n/*.arb` 和页面 dart 文件；`lib/l10n/generated/` 是生成物，不要手改
- 未翻译词条会输出到根目录 `untranslated_messages.txt`
- Android 原生字符串同样多语言：`res/values-{en,ja,ru,tr}/strings.xml`

## 构建与发布

- 版本号在 `pubspec.yaml`：`X.Y.Z+YYYYMMDDNN`，发版前手动改
- CI（`.github/workflows/release.yml`）：推 `v*` tag 或手动触发。versionName 与上个提交相比未变 → 只构建（追加 `-dev.N` 后缀）不发布；变了 → 自动创建 GitHub Release
- Release 说明优先从 `docs/CHANGELOG.md` 提取 `# V<版本号>` 段落（格式不对会退化成 git log），发版前先写好该段落
- 签名：CI 用 secrets 注入；本地没有 `android/keystore.properties` 或环境变量时自动回退 debug 签名，可以直接构建
- 发布后可选联动：推送 Telegram 频道、同步 LSPosed 模块仓库（workflow_dispatch 输入控制）

## 文档维护

- 使用文档在 `docs/`（VitePress，线上地址 hyperisland.1812z.top），用户可见的行为变更要同步更新
- `小米超级岛通知模板库_AI版.md` 是通知模板设计的参考素材
- 更新本文档时直接在对应小节修改、保持精简，不要追加“新增 xx”式流水账
