# PackRide Android

Kotlin / Jetpack Compose skeleton ported from the iOS PackRide app.

## What’s included

| Module | Path | Notes |
|--------|------|--------|
| **SharedLocationManager** | `location/SharedLocationManager.kt` | Reason-counted GPS, accuracy tiers (`lapTracking` 3 m, ride 5 m, browse 50 m), distance/speed gating |
| **LocationTrackingService** | `location/LocationTrackingService.kt` | Foreground service while background reasons are active |
| **LapEngine** | `lap/LapEngine.kt` | Crossing radii, accuracy filter, interpolated crossings, GPS-anchored first lap, live delta |
| **GPXRecorder** | `gpx/GPXRecorder.kt` | ~1 Hz capture, GPS timestamps, lean/G-force extensions |
| **Compose shell** | `ui/` | Bottom nav (Home / Track / Map / Profile), Track Mode demo wiring |

## Setup in Android Studio

1. **Open** the `PackRideAndroid` folder as a project (or copy into your repo).
2. **Firebase**
   - Firebase Console → Project `packride-6f5ab` → Add Android app with package `com.karthik.packride`
   - Download the real `google-services.json` and replace `app/google-services.json`
3. **Maps** (when you add map UI)
   - Create a Maps SDK key in Google Cloud
   - In `app/build.gradle.kts` `defaultConfig`, add:
     ```kotlin
     manifestPlaceholders["MAPS_API_KEY"] = "YOUR_KEY"
     ```
   - Or hardcode the meta-data value in `AndroidManifest.xml`
4. **Icons** — add `mipmap/ic_launcher` (or temporarily point `android:icon` at `@android:drawable/ic_menu_compass`).
5. **Sync Gradle** and run on a device with Google Play services.

## Permissions flow (production)

1. Request `ACCESS_FINE_LOCATION` when starting map / set line / session.
2. After fine is granted, **separately** request `ACCESS_BACKGROUND_LOCATION` (Android 10+) with rationale before long rides / crash protection.
3. Request `POST_NOTIFICATIONS` on API 33+ before starting the foreground service.

## Porting map (iOS → Android)

| iOS | Android |
|-----|---------|
| `CLLocationManager` | `SharedLocationManager` + Fused Location |
| `allowsBackgroundLocationUpdates` | `LocationTrackingService` + background permission |
| `LapEngine` | `com.karthik.packride.lap.LapEngine` |
| `GPXRecorder` | `com.karthik.packride.gpx.GPXRecorder` |
| SwiftUI | Jetpack Compose |
| Firebase iOS | Firebase Android (same project) |

## Next modules to port

1. Solo ride session UI (map + live HUD)
2. Group ride + RTDB listeners
3. Ride feed / friends
4. Need Help live share
5. Crash in-app Firebase alerts (CrashAlertManager)

## Note on API keys

The placeholder `google-services.json` reuses values from the iOS plist for project id only. **You must register an Android app** in Firebase and use the generated file before Auth/RTDB work on device.
