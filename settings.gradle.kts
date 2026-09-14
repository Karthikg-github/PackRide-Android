pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Agora RTC Voice SDK (voice chat / group-ride intercom, see
        // app/build.gradle.kts and voice/VoiceChatManager.kt) isn't on
        // Maven Central — it's published only to Agora's own repo.
        // repositoriesMode is FAIL_ON_PROJECT_REPOS above, so this has to
        // live here rather than in app/build.gradle.kts's own repositories{}.
        maven { url = uri("https://download.agora.io/sdk/release/") }
    }
}

rootProject.name = "PackRide"
include(":app")
