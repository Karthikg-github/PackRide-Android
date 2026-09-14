# PackRide Android — Play Store checklist

## 1. Firebase
- [ ] Create Android app in Firebase project with package `com.karthik.packride`
- [ ] Download `google-services.json` into `app/`
- [ ] Enable Email/Password Auth
- [ ] Realtime Database rules for `users`, `rides`, `feedPosts`, `helpRequests`
- [ ] Storage rules for `rides/{uid}/**` and optional `feedPhotos`

## 2. Maps
- [ ] Google Cloud Maps SDK for Android API key
- [ ] Restrict key to package + signing SHA-1
- [ ] Put key in `AndroidManifest.xml` meta-data `com.google.android.geo.API_KEY`

## 3. Signing
```bash
keytool -genkey -v -keystore packride-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias packride
```
Configure `signingConfigs` in `app/build.gradle.kts` (use env vars; never commit the jks).

## 4. Store listing
- App name: PackRide
- Short description: Ride tracking, group rides, and safety for motorcyclists.
- Privacy policy URL: host `docs/privacy-policy.html`
- Category: Health & Fitness or Maps & Navigation
- Content rating; target audience 18+

## 5. Data safety form
- Location: collected/shared for group and Need Help
- Account: email, name
- App activity: ride stats you post

## 6. Build release
```bash
./gradlew :app:bundleRelease
```
Upload the `.aab` from `app/build/outputs/bundle/release/`.

## 7. Device test matrix
- [ ] Login / register / onboarding
- [ ] Solo ride with screen off (background GPS)
- [ ] Group create + join
- [ ] Track mode
- [ ] Crash simulate
- [ ] Need Help start/stop
- [ ] Feed post, reaction, comment
- [ ] Follow user
- [ ] GPX cloud sync after end ride

## Feature surface (Android port)

Implemented for device testing:
Auth, Solo ride, Group ride, Track laps, Crash detection, Need Help, Feed, Friends,
Community, Schedule rides, Garage, Badges, Weather, Analytics, GPX replay, Waypoints,
Crash alert inbox, FCM service, GPX cloud upload.

Pragmatic stubs (external or later polish):
Turn-by-turn (opens Google Maps), Voice chat, MotoRun mini-game, Ad banners (disabled),
iOS widgets / Live Activities.
