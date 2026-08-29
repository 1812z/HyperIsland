# 构建指南

从源码构建 HyperIsland APK。

## 环境要求

- JDK 21
- Android SDK（通过 Android Studio 或命令行工具）

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
