pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Spotify publishes the App Remote + Auth SDK through JitPack.
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\.spotify.*") }
        }
    }
}

rootProject.name = "SpectraPlayer"
include(":app")
