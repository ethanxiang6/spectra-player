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
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
        buildConfigField("String", "SPOTIFY_REDIRECT_URI", "\"spectra://callback\"")

        // The bundled Spotify Auth library declares an intent filter built from these two
        // placeholders. We run our own PKCE flow (redirect: spectra://callback), so point the
        // library's filter at a scheme nothing else uses to avoid a duplicate-handler clash.
        manifestPlaceholders["redirectSchemeName"] = "spectra-sdk-unused"
        manifestPlaceholders["redirectHostName"] = "unused"
    }

    buildTypes {
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

    // --- Spotify (App Remote SDK + Auth library, via JitPack) ---
    implementation(libs.spotify.app.remote)
    implementation(libs.gson) // required at runtime by the App Remote SDK

    // --- Tests ---
    testImplementation(libs.junit)
}
