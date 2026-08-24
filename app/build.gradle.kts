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
        versionCode = 9
        versionName = "1.5.1"
    }

    /*
     * Release signing is driven by environment variables so the keystore never
     * enters the repo. CI supplies them from secrets; locally they are simply
     * absent and the release build stays unsigned, which is a clearer failure
     * than silently shipping a debug-signed artifact as a release.
     */
    // Empty, not absent: the workflow always sets KEYSTORE_FILE, and it is ""
    // when there is no keystore secret. file("") throws, so treat blank as unset.
    val storeFilePath = System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (storeFilePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
