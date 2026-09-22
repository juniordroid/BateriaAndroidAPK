plugins {
    id("com.android.application")
}

android {
    namespace = "com.amarildo.bateria"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.amarildo.nfcdiagnostico11"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
