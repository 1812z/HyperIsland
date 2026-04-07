import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("org.jetbrains.kotlin.plugin.compose")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "io.github.hyperisland"
    compileSdk = 37
    ndkVersion = "27.0.12077973"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += "**"
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
        minSdk = 27
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
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
    }

}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

flutter {
    source = "../.."
}

configurations.all {
    resolutionStrategy {
        force("androidx.core:core:1.15.0")
        force("androidx.core:core-ktx:1.15.0")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.0")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.0")

    implementation("io.github.d4viddf:hyperisland_kit:0.4.3")
    compileOnly("io.github.libxposed:api:101.0.0")
    implementation("io.github.libxposed:service:101.0.0")
}
