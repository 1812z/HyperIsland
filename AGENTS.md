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

## 新增配置项检查清单

1. 在 `FlutterPrefsRepository.kt` 添加默认值、读取与写入
2. 在对应 Compose 页面接入状态，复用 `component/` 下的 Miuix 组件
3. 应用级/渠道级字段登记两侧字段表，并补批量应用映射
4. 导入导出需同步更新 `ConfigBackupService.kt`
5. Xposed 行为在对应 Hook 中实现；新 Hook 需在 `HyperIslandModule.onPackageLoaded` 注册
6. Android 原生文案先写 `res/values/strings.xml`，稳定后补 `values-{en,ja,ru,tr}`

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
