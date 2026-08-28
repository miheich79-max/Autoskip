plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.autoskip5"
    compileSdk = 35

    defaultConfig {
        // Keep this stable: version 0.5 used this ID, so 0.6 installs as an update.
        applicationId = "com.example.autoskip5"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "0.6"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}

