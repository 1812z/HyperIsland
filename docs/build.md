# 构建指南

从源码构建 HyperIsland APK。应用界面已经从 Flutter 迁移到原生 Kotlin，
使用 Jetpack Compose 与 Miuix；界面和 Xposed Hook 位于同一个 Android Gradle 工程中。
构建时不再需要 Flutter SDK 或 Dart 环境。

## 环境要求

- JDK 21
- Android SDK API 37（通过 Android Studio 或命令行工具安装）

## 构建步骤

1. 克隆仓库：

```bash
git clone https://github.com/1812z/HyperIsland.git
cd HyperIsland
```

2. 构建正式 APK：

```bash
./android/gradlew -p android :app:assembleRelease
```

构建完成后，APK 文件位于 `build/app/outputs/apk/release/app-release.apk`。

Gradle Wrapper 位于 `android/` 目录，但命令应在仓库根目录执行；`-p android`
会指定 Android 工程目录。首次构建会自动下载 Gradle、Compose、Miuix 等依赖。

## 构建变体

调试版本：

```bash
./android/gradlew -p android :app:assembleDebug
```

跳过 R8 混淆和资源压缩的快速 Release 测试版本：

```bash
./android/gradlew -p android :app:assembleReleaseFast
```

三种变体都只打包 `arm64-v8a`。版本号在 `android/gradle.properties` 的
`appVersionName` 和 `appVersionCode` 中维护。

## 常见问题

::: details 构建失败怎么办？
- 确保 `JAVA_HOME` 指向 JDK 21
- 确保 Android SDK 已安装 API 37，并已接受 Android SDK 许可证
:::
