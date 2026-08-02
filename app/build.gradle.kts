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
        versionCode = 12
        versionName = "1.11"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


// Keep the Android launcher icon synchronized with app/src/main/assets/logo.png.
// Replace that asset with the final Exvia logo before building.
val syncExviaLogo by tasks.registering(Copy::class) {
    from("src/main/assets/logo.png")
    into("src/main/res/drawable-nodpi")
    rename { "exvia_logo.png" }
}

tasks.named("preBuild").configure {
    dependsOn(syncExviaLogo)
}
