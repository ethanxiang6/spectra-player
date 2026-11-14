plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // Screenshot tests; only applied to :app when building with -Pscreenshots.
    id("app.cash.paparazzi") version "1.3.5" apply false
}
