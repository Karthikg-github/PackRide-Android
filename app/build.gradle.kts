import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

// Aug 30, 2026 — Maps API key lives in local.properties (gitignored), not
// hardcoded in AndroidManifest.xml, so it never ends up committed. Exposed
// to the manifest via manifestPlaceholders below.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(FileInputStream(f))
}

android {
    namespace = "com.karthik.packride"
    // Aug 30, 2026 — bumped 35 -> 36 (Android 16): Play Store now requires
    // targetSdk 36 for new submissions (see root build.gradle.kts comment
    // and android-build-plan.md). Needs AGP 8.13.0+ (also bumped) — AGP
    // 8.7.2 topped out at compileSdk 35.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.karthik.packride"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["mapsApiKey"] =
            localProperties.getProperty("MAPS_API_KEY", "")
    }

    // Aug 30, 2026 — Play Store readiness: a release build must be signed
    // with a real (non-debug) key before it can be uploaded, or bundleRelease/
    // assembleRelease fails outright. Same local.properties pattern as
    // MAPS_API_KEY above so the keystore path/passwords never get committed —
    // add these four keys to local.properties once you've generated a
    // keystore (see android-build-plan.md for the exact `keytool` command):
    //   RELEASE_STORE_FILE=/absolute/path/to/packride-release.jks
    //   RELEASE_STORE_PASSWORD=...
    //   RELEASE_KEY_ALIAS=packride
    //   RELEASE_KEY_PASSWORD=...
    // Until those are set, this config is skipped and only the plain debug
    // build type is signable — assembleDebug (what's been used all along)
    // keeps working exactly as before.
    val releaseStoreFile = localProperties.getProperty("RELEASE_STORE_FILE", "")
    val hasReleaseSigning = releaseStoreFile.isNotBlank()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD", "")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS", "")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    testImplementation("junit:junit:4.13.2")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Maps (for track / ride screens later)
    // Aug 30, 2026 — maps-compose:6.1.3 never existed as a published
    // artifact (broke the build with "Could not find ...6.1.3"). Tried
    // bumping straight to the newest 8.5.0, but its transitive core-ktx
    // (1.19.0) requires compileSdk 37 / AGP 9.1+, which this project isn't
    // on. Settled on 6.7.0 — newest version whose transitive deps
    // (core-ktx 1.16.0, maps-ktx 5.2.0) are still compileSdk-35/AGP-8.7.2
    // compatible. Revisit once the project bumps compileSdk/AGP.
    implementation("com.google.maps.android:maps-compose:6.7.0")
    implementation("com.google.android.gms:play-services-maps:20.0.0")
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")

    // Firebase (Auth + Realtime Database + Storage) — match iOS backend
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    // notify/PackRideMessagingService.kt extends FirebaseMessagingService and
    // imports com.google.firebase.messaging.* — that module isn't pulled in by
    // the BOM or any other dependency here, so without this the service (and
    // anything referencing it) fails to compile despite being registered in
    // AndroidManifest.xml for MESSAGING_EVENT.
    implementation("com.google.firebase:firebase-messaging-ktx")
    // Aug 31, 2026 — voice/VoiceChatManager.kt fetches its Agora join token
    // from the SAME generateAgoraToken Cloud Function iOS calls
    // (packride-functions/functions/index.js) via Firebase.functions —
    // that function is already platform-agnostic, no backend change needed.
    implementation("com.google.firebase:firebase-functions-ktx")
    implementation("com.google.firebase:firebase-crashlytics")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Aug 30, 2026 — image loading for profile avatars + feed post photos
    // (closing the "no photo upload anywhere" scope gap flagged across
    // items 13/19). Picking the file itself uses androidx.activity's
    // built-in PickVisualMedia contract (already available via
    // activity-compose 1.9.3 above, no separate dependency needed) —
    // Coil is only for *displaying* the resulting Firebase Storage URLs.
    // Pinned to the long-stable coil-compose 2.x line (not the newer
    // Multiplatform-oriented coil3) to avoid repeating this project's
    // earlier maps-compose version-mismatch pain.
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Aug 30, 2026 — Places SDK for live address autocomplete (Waypoints)
    // and track-name search (Track Mode), closing the "no live Places
    // Autocomplete" scope gap flagged in items 3/22. Reuses the existing
    // Maps API key (local.properties/MAPS_API_KEY) — Karthik needs to
    // enable "Places API (New)" for that same Google Cloud project before
    // this compiles against real data; see android-build-plan.md.
    implementation("com.google.android.libraries.places:places:3.4.0")

    // Aug 31, 2026 — Voice chat (group ride intercom): real audio via
    // Agora's RTC SDK, Android port of iOS VoiceChatManager.swift (which
    // pins AgoraRtcEngine_iOS 4.6.2 via Package.resolved — same Agora
    // project/App ID, cross-platform). "voice-sdk" (not "full-sdk") since
    // PackRide only needs audio, not video — same io.agora.rtc2.* package
    // namespace either way, just a smaller APK footprint. Pulled from
    // Agora's own Maven repo, added in settings.gradle.kts.
    //
    // UNVERIFIED — the single highest-risk line in this change. 4.3.2 is a
    // real, publicly documented stable Agora Android 4.x release (same
    // major SDK generation as iOS's 4.6.2 pin), picked with no network
    // access from this shell to confirm it's still resolvable / still the
    // newest 4.x release. Same class of risk as this project's
    // maps-compose 6.1.3-never-existed / 8.5.0-needed-compileSdk-37 saga
    // above — check https://download.agora.io/sdk/release/ at Karthik's
    // own Gradle sync and bump this version if it doesn't resolve.
    implementation("io.agora.rtc:voice-sdk:4.6.4")
}
