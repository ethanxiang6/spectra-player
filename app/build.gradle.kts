import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The Spotify Client ID is read from local.properties (git-ignored) or the environment.
// PKCE needs no client secret, so nothing sensitive ever ships in the APK.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val spotifyClientId: String =
    localProps.getProperty("spotify.clientId") ?: System.getenv("SPOTIFY_CLIENT_ID") ?: ""

android {
    namespace = "com.spectra.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.spectra.player"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 2
        versionName = "1.5.0" + (System.getenv("GITHUB_RUN_NUMBER")?.let { "-build.$it" } ?: "")

        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
        buildConfigField("String", "SPOTIFY_REDIRECT_URI", "\"spectra://callback\"")

    }

    // Optional stable signing key for CI builds. Spotify App Remote only accepts an app whose
    // signing SHA-1 is registered in the dashboard, and a fresh runner would otherwise generate
    // a new random debug key on every build.
    val ciKeystorePath = System.getenv("KEYSTORE_PATH")
    if (!ciKeystorePath.isNullOrBlank() && file(ciKeystorePath).exists()) {
        signingConfigs {
            create("ci") {
                storeFile = file(ciKeystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("ci")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // --- AndroidX / Compose (Material 3, Canvas graphics) ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.browser) // Custom Tabs for the PKCE login page

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics) // Brush.sweepGradient, Path, DrawScope
    implementation(libs.compose.ui.text.google.fonts) // downloadable fonts for the visualizer title
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // --- Async / data ---
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)

    // --- Spotify App Remote SDK ---
    // Official Spotify App Remote AAR (app/libs). Spotify does not publish it to Maven Central.
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))
    implementation(libs.gson) // required at runtime by the App Remote SDK
    // The App Remote protocol layer (de)serializes with Jackson. Without it the connection never
    // completes and no error is reported ("Connecting..." forever).
    implementation(libs.jackson.databind)

    // --- Tests ---
    testImplementation(libs.junit)
}
