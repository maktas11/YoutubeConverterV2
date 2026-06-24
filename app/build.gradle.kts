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

        // Phone target is arm64-v8a ONLY (the Galaxy S25 is arm64, and the
        // bundled Python + yt-dlp + FFmpeg make every extra ABI hugely inflate
        // the APK). x86_64 is included ONLY so the app also runs on a standard
        // Windows/Intel emulator for testing.
        // TODO: remove "x86_64" before building the final APK for the phone.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
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
