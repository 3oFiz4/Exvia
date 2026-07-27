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
<<<<<<< HEAD
        versionCode = 2
        versionName = "1.1"
=======
        versionCode = 7
        versionName = "1.7"
>>>>>>> 4ed6b6d (add statistics, settings, theme selection)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
