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
        versionCode = 14
        versionName = "1.12.1"
        val hasExistingLogo = file("src/main/assets/logo.png").exists() ||
            file("src/main/res/drawable-nodpi/exvia_logo.png").exists()
        manifestPlaceholders["exviaAppIcon"] = if (hasExistingLogo) "@drawable/exvia_logo" else "@android:drawable/sym_def_app_icon"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


// Keep the Android launcher icon synchronized with app/src/main/assets/logo.png.
// Replace that asset with the final Exvia logo before building.
val syncExviaLogo by tasks.registering(Copy::class) {
    val sourceLogo = file("src/main/assets/logo.png")
    onlyIf { sourceLogo.exists() }
    from(sourceLogo)
    into("src/main/res/drawable-nodpi")
    rename { "exvia_logo.png" }
}

tasks.named("preBuild").configure {
    dependsOn(syncExviaLogo)
}
