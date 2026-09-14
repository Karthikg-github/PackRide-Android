plugins {
    // Aug 30, 2026 — bumped from 8.7.2 to 8.13.0: Google Play now requires
    // targetSdk 36 (Android 16) for new app submissions (enforcement started
    // Aug 31, 2026; see android-build-plan.md), and AGP 8.7.2's supported
    // compileSdk range tops out at 35. 8.13.0 is the newest 8.x release
    // (supports up to API 36.1) — deliberately NOT jumping to AGP 9.x, since
    // that's a major-version bump with its own migration risk and this
    // project has already had real dependency-version pain this session.
    // Needs Gradle 8.13+ minimum — the project's wrapper is already on 9.3.0.
    id("com.android.application") version "8.13.0" apply false
    // Aug 30, 2026 — bumped from 2.0.21: maps-compose:6.7.0 was compiled
    // with Kotlin 2.2.0 metadata, which the 2.0.21 compiler can't read
    // (Kotlin metadata compat is forward-only — newer compiler reads older
    // metadata, not vice versa). 2.2.21 is the newest 2.2.x patch. These two
    // must stay in lockstep — Kotlin 2.0+ bundles the Compose compiler with
    // the Kotlin compiler itself.
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.8" apply false
}
