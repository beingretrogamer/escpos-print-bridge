plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.escposbridge.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.escposbridge.app"
        minSdk = 24          // Android 7 — covers anything a shop tablet is likely running
        targetSdk = 34
        versionCode = 3
        versionName = "1.1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Deliberately none. The HTTP server and the printer socket are written
    // against the JDK's own ServerSocket/Socket, mirroring print-bridge.js,
    // so there is no dependency tree to age badly on a till that never updates.
}
