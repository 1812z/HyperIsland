pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // settings 脚本的 plugins 块在 version catalog 建立之前编译，无法使用 libs.plugins.*，
    // 因此这里的版本只能硬编码（其余插件版本见 gradle/libs.versions.toml）。
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":app")
