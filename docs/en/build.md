# Build Guide

Build HyperIsland APK from source.

## Requirements

- JDK 21
- Android SDK (via Android Studio or command line tools)

## Build Steps

1. Clone the repository:

```bash
git clone https://github.com/1812z/HyperIsland.git
cd HyperIsland
```

2. Build the release APK:

```bash
./android/gradlew -p android :app:assembleRelease
```

The APK is written to `build/app/outputs/apk/release/app-release.apk`.

## Build Variants

Debug build:

```bash
./android/gradlew -p android :app:assembleDebug
```

Fast release test build without R8 minification or resource shrinking:

```bash
./android/gradlew -p android :app:assembleReleaseFast
```

All variants package only `arm64-v8a`. Maintain `appVersionName` and
`appVersionCode` in `android/gradle.properties`.

## FAQ

::: details Build fails?
- Ensure `JAVA_HOME` points to JDK 21
- Ensure Android SDK API 37 is installed and its licenses are accepted
:::
