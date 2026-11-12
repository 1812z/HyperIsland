import java.util.Properties

val buildTime = providers.gradleProperty("buildTime")
    .orElse(providers.environmentVariable("BUILD_TIME"))
    .getOrElse("dev")
val appVersionName = providers.gradleProperty("appVersionName").get()
val appVersionCode = providers.gradleProperty("appVersionCode").map(String::toInt).get()
val nativeTapBuild = providers.gradleProperty("nativeTapBuild").getOrElse("")
require(nativeTapBuild.isEmpty() || nativeTapBuild.matches(Regex("[0-9a-f]{64}"))) {
    "nativeTapBuild must be the source_sha256 from the HyperSpectrum native build manifest"
}

plugins {
    // 版本在根 build.gradle.kts（libs.versions.toml）统一声明
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.hyperisland"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    signingConfigs {
        create("release") {
            // 优先从环境变量读取（GitHub Actions 使用）
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: ""
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            val keyAlias = System.getenv("KEY_ALIAS") ?: ""
            val keyPassword = System.getenv("KEY_PASSWORD") ?: ""

            if (keystorePath.isNotEmpty()) {
                // 使用环境变量配置
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            } else {
                // 回退到 keystore.properties 文件
                val propsFile = rootProject.file("keystore.properties")
                val props = Properties()
                if (propsFile.exists()) props.load(propsFile.inputStream())
                storeFile     = props.getProperty("storeFile")?.let { file(it) }
                storePassword = props.getProperty("storePassword") ?: ""
                this.keyAlias      = props.getProperty("keyAlias") ?: ""
                this.keyPassword   = props.getProperty("keyPassword") ?: ""
            }
        }
    }

    defaultConfig {
        applicationId = "io.github.hyperisland"
        minSdk = 33
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
        buildConfigField("String", "NATIVE_TAP_BUILD", "\"$nativeTapBuild\"")

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null && releaseSigning.storeFile!!.exists()) {
                releaseSigning
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("releaseFast") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += "release"
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigationevent)
    implementation(libs.jetbrains.compose.foundation)
    implementation(libs.bundles.miuix)
    implementation(libs.iconics.core)
    // 需要 @aar 后缀强制按 AAR 解析，版本目录不支持该后缀，故只复用版本号
    implementation("com.mikepenz:community-material-typeface:${libs.versions.communityMaterialTypeface.get()}@aar")
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.hyperisland.kit)
    implementation(libs.aptabase.kotlin)
    implementation(libs.dexkit)
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
}
