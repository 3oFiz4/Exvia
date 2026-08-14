import java.awt.image.BufferedImage
import javax.imageio.ImageIO

plugins {
    id("com.android.application")
    alias(libs.plugins.kotlin.serialization)
}

fun readableImage(file: File): BufferedImage? = if (!file.isFile || file.length() == 0L) {
    null
} else {
    runCatching { ImageIO.read(file) }.getOrNull()?.takeIf { it.width > 0 && it.height > 0 }
}

val sourceLogo = file("src/main/assets/logo.png")
val existingResourceLogo = file("src/main/res/drawable-nodpi/exvia_logo.png")
val hasValidSourceLogo = readableImage(sourceLogo) != null
val hasValidResourceLogo = readableImage(existingResourceLogo) != null

android {
    namespace = "xyz.x3ofiz4.exvia"
    compileSdk = 36

    defaultConfig {
        applicationId = "xyz.x3ofiz4.exvia"
        minSdk = 26
        targetSdk = 36
        versionCode = 23
        versionName = "1.13.6"
        manifestPlaceholders["exviaAppIcon"] = if (hasValidSourceLogo || hasValidResourceLogo) {
            "@drawable/exvia_logo"
        } else {
            "@android:drawable/sym_def_app_icon"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}


// Keep the Android launcher icon synchronized with app/src/main/assets/logo.png.
// Replace that asset with the final Exvia logo before building.
val syncExviaLogo by tasks.registering(Copy::class) {
    onlyIf { hasValidSourceLogo }
    from(sourceLogo)
    into("src/main/res/drawable-nodpi")
    rename { "exvia_logo.png" }
}

tasks.named("preBuild").configure {
    dependsOn(syncExviaLogo)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.openai.client)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
