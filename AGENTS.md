# HyperIsland 开发指引

澎湃 OS 3（HyperOS 3）超级岛通知增强模块（LSPosed Xposed 模块）。应用界面使用 Kotlin、Jetpack Compose 与 Miuix，Hook 与界面位于同一个 Android Gradle 工程，包名 `io.github.hyperisland`。

## 常用命令

```bash
./android/gradlew -p android :app:assembleDebug       # 调试包
./android/gradlew -p android :app:assembleReleaseFast # 快速 Release，不执行 R8/资源压缩
./android/gradlew -p android :app:assembleRelease     # 正式 Release，仅 arm64
```

文档站（VitePress，位于 `docs/`，使用 yarn 1）：

```bash
yarn docs:dev
yarn docs:build
```

环境：JDK 21、Gradle 9.6.1、AGP 9.3.1、Kotlin 2.4.10、compileSdk/targetSdk 37、minSdk 33。版本号在 `android/gradle.properties` 的 `appVersionName` / `appVersionCode` 中维护。

## 架构

- `android/app/src/main/kotlin/io/github/hyperisland/compose/` — Compose/Miuix 界面与原生服务
  - `navigation/AppShell.kt`：根导航、底栏、页面层级与预测返回
  - `data/FlutterPrefsRepository.kt`：配置读写。类名及存储格式暂时保留兼容语义
  - `page/`：页面；`component/`：复用组件；`service/`：文件、备份、通知、重启等平台能力
- `android/app/src/main/kotlin/io/github/hyperisland/xposed/` — Xposed 端
  - `HyperIslandModule.kt`：唯一入口，`onPackageLoaded` 按 packageName 分发
  - `hook/SystemUI/`：绝大多数 Hook；`template/`：岛模板与渲染器；`islanddispatch/`：代理通知分发
  - `ConfigManager.kt`：Hook 进程内统一配置读取入口
- `XposedPrefsSyncApp.kt`：把应用配置镜像到 LSPosed RemotePreferences
- `META-INF/xposed/scope.list`：Hook 目标进程声明，新增目标进程必须同步修改

Xposed 使用 libxposed 新 API（`io.github.libxposed.api`），不是旧 XposedBridge。

## 配置链路

1. Compose 端通过 `FlutterPrefsRepository` 写入名为 `FlutterSharedPreferences` 的 SharedPreferences。为了兼容已有安装，键仍使用 `flutter.pref_*`；业务 API 传入的通常是去掉 `flutter.` 后的 `pref_*`。
2. 全局/默认配置使用独立 `pref_*` key；应用级/渠道级配置使用 `pref_app_config_<packageName>` JSON，分节为 `toast`、`notification`、`channels.enabled`、`channels.settings.<channelId>.<field>`。
3. `XposedPrefsSyncApp` 监听变更，把所有 `pref_` 业务键同步到 LSPosed RemotePreferences，按 key hash 拆成 1 个 core + 32 个 shard，避免 Binder TransactionTooLarge。
4. Hook 进程统一通过 `ConfigManager.getBoolean/getString/...("pref_xxx")` 读取。旧版独立 key 继续兼容。

硬性规则：

- 新配置业务 key 必须以 `pref_` 开头，否则不会同步到 Hook 进程
- 新增应用级/渠道级字段必须同时登记 Compose `FlutterPrefsRepository` 的字段表与 Kotlin `ConfigManager` 的 `TOAST_FIELDS` / `NOTIFICATION_FIELDS` / `CHANNEL_FIELDS`
- `ConfigManager.getString` 返回默认值时无法区分“用户设置了默认值”和“JSON 未配置”，逻辑上需按未配置处理
- 暂时不得修改 `FlutterSharedPreferences` 文件名或 `flutter.` 前缀；配置格式迁移应作为独立版本处理

## 配置预设与配置管理映射表

- 入口：设置页顶部强调色卡片 → `compose/page/settings/PresetConfigPage.kt`。
- **分类权威来源**：`compose/data/ConfigSectionRegistry.kt` 的 `ConfigSectionGroups`。每个 `ConfigSection` 声明精确键 `exactKeys` 或前缀家族 `keyPrefixes`；可展开节点（外观、拓展功能）只作分组，选择单元是叶子 `leafIds`。新增配置页面或字段必须在此登记，避免分类错误。
- 新建预设：`NewPresetBottomSheet`（配置信息 3 输入框：标题 10 字 / 内容 100 字 / 作者 20 字 + 分类选择树）→ `PresetStore.snapshot()` 抓取选中叶子分类的键值 → `PresetStore.saveLocal()`。目前仅存本地，不上传云端。
- 应用预设：点击卡片 → `ApplyPresetBottomSheet` 查看详情 + 多选要应用的分节 → `PresetStore.apply()`。本地预设底部为「删除（红）/ 上传云端 / 应用（强调色）」，云端预设为「取消 / 应用」，标题统一「配置预设」，sheet 内不使用图标。
- 本地预设存于独立 `HyperIslandPresets` SharedPreferences，**不写入** `FlutterSharedPreferences`，避免同步到 Hook 进程或混入配置备份。快照 / 还原直接读写 `FlutterSharedPreferences`，Double 前缀处理与 `ConfigBackupService` 一致。
- AI 分类显式排除 `pref_ai_api_key`。通知 / Toast 已按 `pref_app_config_<包名>` 内部子对象区分：通知 = `notification` + `channels`，Toast = `toast`。二者在配置树中展开为「按应用」的叶子（`appconfig:<kind>:<包名>`，仅显示应用名），快照只抓对应子对象、应用时合并回原 JSON 并保留另一类；叶子 id 解析见 `parseAppConfigLeafId`，动态树由 `PresetStore.appConfigSectionTree` 构建。
- 云端配置：`compose/service/HubClient.kt` 对接 `https://hyperisland-hub.1812z.top`（文档 `HyperIsland-Hub/API.md`）。列表 `GET /api/configs?page=N`（仅元数据）、详情 `GET /api/configs/{id}`（含 `payload.sections`，累加下载量）、上传 `POST /api/configs`（匿名，按 IP 每日限流）。上传必须显式构造 Hub 信封（形态 A，`payload` 为对象），不能只依赖服务端自动包裹。云端预设 id 加 `hub:` 前缀；应用云端预设时先查本地缓存再按需下载正文。审核令牌不下发客户端。
- 云端正文缓存在 `HyperIslandPresets` 的 `hub_cache`（最多 100 份，LRU 淘汰），避免重复下载；缓存读写走 IO 线程。云端列表是进程级状态，软件启动后**首次进入页面**自动拉取一次，之后复用内存列表，仅下拉刷新才重新拉取。

## 新增配置项检查清单

1. 在 `FlutterPrefsRepository.kt` 添加默认值、读取与写入
2. 在对应 Compose 页面接入状态，复用 `component/` 下的 Miuix 组件
3. 在 `ConfigSectionRegistry.kt` 把新键登记到对应分类（预设快照 / 还原依赖）
4. 应用级/渠道级字段登记两侧字段表，并补批量应用映射
5. 导入导出需同步更新 `ConfigBackupService.kt`
6. Xposed 行为在对应 Hook 中实现；新 Hook 需在 `HyperIslandModule.onPackageLoaded` 注册
7. Android 原生文案先写 `res/values/strings.xml`，稳定后补 `values-{en,ja,ru,tr}`

## SystemUI Hook 要点

- **`ISLAND.md` 是权威参考**：改视觉类 Hook 前必读
- 稳定状态由真实内容 View 绘制，过渡/手势动画由 `DynamicIslandContentFakeView` 绘制；两边必须同步
- 自定义背景图片与焦点 BlurDrawable 都写入 `DynamicIslandBackgroundView.drawable`，二者互斥
- 判断 View 状态不能只看类名，还要看所属 `DynamicIslandContentView.state`
- 查证 SystemUI 内部实现使用 `opencode.json` 配置的 jadx MCP（127.0.0.1:9999）
- Hook 持有对象使用弱引用，缓存必须有清理和数量上限

## 构建与发布

- 正式构建命令为 `./android/gradlew -p android :app:assembleRelease`
- APK 输出到 `build/app/outputs/apk/release/app-release.apk`
- CI 从 `android/gradle.properties` 读取版本；推送 `v*` tag 或手动触发发布
- Release 说明优先从 `docs/CHANGELOG.md` 提取 `# V<版本号>` 段落
- CI 从 secrets 注入签名；本地无签名配置时回退到 debug 签名

## 文档维护

- 用户文档位于 `docs/`，线上地址为 hyperisland.1812z.top
- 用户可见行为变更需同步更新文档
- `小米超级岛通知模板库_AI版.md` 是通知模板设计参考
- 更新本文档时直接修改对应小节，保持精简
