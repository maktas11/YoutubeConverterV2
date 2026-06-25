import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.maktas.ytconverter"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.maktas.ytconverter"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // arm64-v8a ONLY — phones are arm64, and the bundled Python + yt-dlp +
        // FFmpeg make extra ABIs hugely inflate the APK. (Temporarily add
        // "x86_64" back if you ever want to run it on a standard Intel emulator.)
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            // Personal sideload build; keep it simple. Debug signing is fine.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // Required by youtubedl-android: native libs must be extracted on
            // install (equivalent to android:extractNativeLibs="true").
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // AndroidX core + lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)

    // Coil 3 for loading search-result thumbnails over the network.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Jetpack Compose (Material 3) via BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    // youtubedl-android: on-device yt-dlp + Python; ffmpeg for audio extraction.
    // aria2c is optional (faster downloads) and not yet wired up.
    implementation(libs.youtubedl.android.library)
    implementation(libs.youtubedl.android.ffmpeg)
    implementation(libs.youtubedl.android.aria2c)
}
