import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("app.cash.paparazzi")
}

android {
    namespace = "app.wallglass"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.wallglass"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.2.1"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Release signing: path from WALLGLASS_KEYSTORE_PROPERTIES or ./keystore.properties (never committed).
    // Without it the release build falls back to the debug key.
    val keystoreProps = (System.getenv("WALLGLASS_KEYSTORE_PROPERTIES") ?: rootProject.file("keystore.properties").path)
        .let { file(it) }
        .takeIf { it.exists() }
        ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    // full: with the on-device "Hej Dom" wake word (ONNX Runtime + models); lite: without it, smaller
    flavorDimensions += "voice"
    productFlavors {
        create("full") {
            dimension = "voice"
            buildConfigField("boolean", "WAKE_WORD", "true")
        }
        create("lite") {
            dimension = "voice"
            versionNameSuffix = "-lite"
            buildConfigField("boolean", "WAKE_WORD", "false")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    "fullImplementation"("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    // WebRTC for talking to the door station through go2rtc (prebuilt libwebrtc, org.webrtc API)
    implementation("io.getstream:stream-webrtc-android:1.3.9")

    testImplementation("junit:junit:4.13.2")
}
