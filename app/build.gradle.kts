plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.autoskip5"
    compileSdk = 35

    defaultConfig {
        // This ID is shared with the working diagnostic build and must remain stable.
        applicationId = "com.example.autoskip5"
        minSdk = 24
        targetSdk = 35
        versionCode = 8
        versionName = "1.0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
