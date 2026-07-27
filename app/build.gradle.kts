plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.exp_tracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.exp_tracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
