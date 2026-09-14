# PackRide Android ↔ iOS Feature-Parity Plan

> Next-version work: the deferred RaceBox-style shared track database, directional timing gates, sectors, and racing-line analysis are documented in [NEXT_VERSION_TRACK_ROADMAP.md](NEXT_VERSION_TRACK_ROADMAP.md).

## Current status — September 8, 2026 code-to-code audit

This supersedes the older percentage addendum below. The audit compared all 60
iOS Swift files with all 107 Android Kotlin files, checked the current
navigation graph and shared Firebase paths, and completed Android unit tests,
lint, debug assembly, installation, and launch smoke testing. Percentages are
implementation estimates, not a claim that physical cross-platform behavior
has passed.

| Area | Current implementation parity | What prevents 100% |
|---|---:|---|
| Core everyday features | **98%** | Group waiting-room/start and participant statistics now match in code; Google and Facebook sign-in still need their missing Android client configuration; cloud-backed ride history requires cross-device verification |
| Safety and privacy | **97%** | Background/process-killed crash and Need Help behavior, urgent notification delivery, and Android↔iPhone responder flows still need physical testing; Android's Need Help presentation does not reproduce both iOS map surfaces |
| Shared Firebase compatibility | **97%** | Current schemas and raw Group Ride codes match, but memberships created before `authUID`/the reverse index cannot always be discovered; current iOS rides are cloud-backed and Android now retries restore on History entry; deployed rules/functions and Agora interoperability remain unverified |
| Visual and navigation parity | **96%** | The Group waiting-room hero is now full-bleed; final screen-by-screen device comparison, dialogs, animations, icon substitutions, and loading/error states remain; map style selection is not persisted globally |
| Current track features | **97%** | Core recording, discovery, scoring, replay, compare, trends, feed posting and sharing exist; draggable start-line adjustment and exact iOS session/history presentation differ; physical on-track validation is outstanding |
| **Overall implementation parity** | **97%** | Weighted code-level estimate after the Group Ride, routing and participant-statistics fixes |
| **Release-verified parity** | **about 82%** | The Android↔iPhone, background lifecycle, notification, Agora, offline/reinstall, and full visual device matrices have not passed yet |

### September 8 test findings — the reported eleven items

| # | Finding | Status after verification | Remaining gap / acceptance test |
|---:|---|---|---|
| 1 | Create Ride and waypoint setup | **Implemented** | Android now creates/joins a waiting room without starting GPS recording, lets the leader add/edit shared waypoints and invite riders, then explicitly starts with the same no-waypoint warning choice as iOS. The room and Agora session survive tab changes. |
| 2 | Existing community does not sync | **Implemented, needs device proof** | Android restores `users/{uid}/communityMemberships` and now scans member records carrying the same `authUID`, then repairs the reverse index. A legacy iOS membership containing only an installation ID and no `authUID` cannot be inferred safely; opening it in the updated iOS app or a one-time backend migration is required. |
| 3 | iOS ride history does not sync | **Implemented, needs account test** | Current iOS writes metadata to `users/{uid}/rideHistory/{id}` and GPX to Storage. Android reads that exact schema, tolerates older Android field names, and now retries the Firebase restore whenever Ride History opens rather than relying only on construction-time auth/network state. Verify the affected account against live Firebase; no iOS migration is assumed for cloud-backed rides. |
| 4 | Center maps on current location | **Implemented** | Live Plan Route, Solo, Group and Track maps request a location and center when the first fix arrives. Route replay/history/telemetry intentionally fit recorded route bounds instead of current location. Verify permission-denied and delayed-fix behavior on the phone. |
| 5 | Editable Plan Route start | **Implemented** | Starting Point can be searched, selected on the map, replaced, or cleared to return to current location. |
| 6 | Pick a pin on the current map | **Implemented** | “Choose on Map” now dismisses search and turns the existing Plan Route map into the picker. Exact iOS draggable-pin fine adjustment is not present. |
| 7 | Straight line instead of road route | **Implemented** | Plan Route and turn-by-turn now accept only successful Google Directions road routes. A failed leg produces a visible routing error and never fabricates a straight polyline or spoken direction. Google Directions API access must be enabled for the Android key. |
| 8 | Predicted addresses while typing | **Implemented** | Google Places Autocomplete is debounced and resolves the selected prediction to coordinates. Requires Places API enablement, billing, and correct Android key restrictions. |
| 9 | Clear Plan Route after completion | **Implemented for solo/navigation** | Solo ride and navigation completion clear `waypoints_solo`. Group routes remain scoped to their ride code, matching the shared-room model; verify a new plan does not reopen the prior solo route after every completion path. |
| 10 | MotoRun exit button | **Implemented** | Visible close button and Android system Back both exit MotoRun. |
| 11 | Match the iOS PackRide logo | **Verified exact asset** | Android's launcher foreground uses the same PNG bytes as the iOS AppIcon (matching SHA-256). Android adaptive icon masking can still crop the square artwork differently by launcher. |

### Remaining cross-app gaps found in the full source audit

1. Implement a true Group Ride waiting room: create/join membership without
   recording, shared waypoint preview/editing for the leader, invite/share,
   waypoint warning, explicit Start Ride, then live-map recording.
2. Configure and implement native Google and Facebook OAuth launchers on
   Android. Apple and email flows are present; the Google/Facebook buttons
   currently return configuration errors.
3. Add iOS ride-history backfill for locally stored legacy rides. This cannot
   be solved from Android because those records never reached Firebase.
4. Add the iOS group `ParticipantsStatsView` equivalent to completed Android
   group rides.
5. Replace turn-by-turn's straight-line failure fallback with a clear routing
   failure/retry state; never present an unrouted segment as road guidance.
6. Complete the two physical iOS map presentations used by Need Help, or
   document an intentionally equivalent Android-native responder experience.
7. Run a screen/state visual matrix covering loading, empty, populated,
   offline, denied-permission, error, confirmation and completion states.
8. Run the bidirectional physical-device matrix for Firebase, FCM/APNs,
   process death, background location, Agora audio, GPX reinstall/offline
   recovery and legacy records. Deploying the reviewed shared rules/functions
   is a prerequisite.
9. Finish Play release validation: signed AAB, Data Safety and background
   location declarations, notification/full-screen-intent policy, consent,
   account deletion, crash reporting, battery soak, and staged rollout gates.

## Current status addendum — September 3, 2026

The detailed audit below records the original baseline and is retained for traceability. Several of its individual “Missing” and “Blocked” entries have since been implemented, including moderation/blocking, privacy controls, safety-state recovery, GPX cloud recovery, scheduled-ride hardening, lap sharing/history improvements, rendered share cards, fixed-light presentation, and Android build/test repair.

Current implementation-parity estimate, excluding the deferred next-version RaceBox-style upgrade:

| Area | Estimate | Main remaining qualification |
|---|---:|---|
| Core everyday features | 100% in code | Everyday flows are implemented; physical Android↔iPhone acceptance testing remains a release-verification gate |
| Safety and privacy | 100% in code | Shared Firebase rules/functions must be deployed; physical background/process-killed and Android↔iPhone delivery tests remain release gates |
| Shared Firebase compatibility | 100% in code | Shared rules/functions must be deployed; physical bidirectional interoperability remains a release-verification gate |
| Visual and navigation parity | 96% in code | Five-tab shell, icons, transitions, swipe navigation, pushed-screen back controls, dark palette, and primary map selectors match; final device screenshot comparison remains |
| Current track feature parity | 100% code-complete | Named-track discovery + OpenStreetMap layouts, GPS lap timing, persistent scores/trends, GPX telemetry comparison, replay, posting and sharing now match the current iOS feature set. Physical on-track validation remains a release gate; the advanced curated/shared RaceBox-style track system stays deferred to the next version. |
| **Overall implementation parity** | **92%** | Runtime-verified parity is lower until the physical-device matrix passes |

Audit date: September 2, 2026  
Android baseline: `/Users/karthikgundavarapu/Desktop/PackRideAndroid`  
iOS baseline: `/Users/karthikgundavarapu/Desktop/PackRide`

## Outcome

The iOS app is the canonical product specification. Android must reproduce its visual design, information architecture, interaction states, copy, and functionality as exactly as Android permits. Platform-native substitutions are acceptable only where iOS APIs or platform policy cannot be reproduced literally, and they must preserve the same user outcome.

The Android port has broad **screen-level coverage**, but it is not yet at release-grade feature parity with iOS. The highest-priority issue is more basic than a missing feature: `assembleDebug` currently fails to compile. After that, the largest product gaps are follow-request/privacy behavior, lap-session persistence and sharing, ride telemetry, track-layout discovery, map controls, emergency presentation, and the navigation structure.

This assessment is based on static code inspection plus an Android debug build. It does not claim runtime parity until the two-device scenarios in the release gate are completed.

## Parity rules

1. **iOS is the source of truth.** Compare Android against the current iOS screen and state—not against old Android comments, screenshots, or the original skeleton README.
2. **Match the whole state machine.** Every iOS screen must be compared in loading, empty, populated, error, permission-denied, offline, active, confirmation, and completion states.
3. **Match visual tokens.** Port colors, typography hierarchy, spacing, corner radii, borders, shadows, icon intent, imagery, map overlays, animation timing, and light-theme behavior into reusable Compose tokens.
4. **Match navigation.** Preserve the same five primary destinations, destination ownership, modal-versus-push behavior, tab persistence, deep-link landing point, and active-tab reset behavior.
5. **Match copy and ordering.** Titles, labels, helper text, alerts, action order, section order, and disabled-state explanations should match unless Android conventions require a documented exception.
6. **Preserve functionality and data semantics.** A visually identical control is not complete until it reads/writes the same shared backend contract and produces the same outcome on the other platform.
7. **Document necessary platform adaptations.** Examples include Android runtime permissions, foreground services, notification channels, system Sharesheet, full-screen-intent policy, Google Maps, and Open-Meteo replacing iOS-only frameworks.

## Shared-service decisions

- **Firebase:** iOS and Android use one Firebase project, one live Realtime Database dataset, one Storage namespace, one Authentication user pool, and the same deployed Cloud Functions. Riders, friendships, groups, communities, rides, locations, feed activity, invitations, help requests, crash alerts, and lap shares are inherently cross-platform records; there must be no platform-prefixed trees, duplicated Android collections, or synchronization layer between separate datasets. Firebase still requires platform-specific client registrations/configuration files (`GoogleService-Info.plist` for iOS and `google-services.json` for Android), but those files point both clients to the same backend. The existing iOS schema and Firebase rules are canonical: Android must use the identical paths, keys, value types, units, identifiers, timestamp conventions, privacy rules, and lifecycle semantics. Add tolerant decoding and a migration plan for legacy records.
- **AdMob:** use the Android-specific AdMob app ID and Android ad-unit IDs. AdMob identifiers are platform-specific even when both apps belong to the same publisher/account. Keep all IDs in Android resources/build configuration and never reuse the iOS app/ad-unit IDs.
- **Agora:** one Agora project can serve both iOS and Android. The checked-in clients already use the same App ID, the same `generateAgoraToken` Firebase callable, the same communication/broadcaster options, and the ride code as the channel name. Keep the App Certificate server-side. Each participant must receive a valid token for that same App ID/channel and a distinct Agora UID (the current UID `0` behavior asks Agora to assign one). Treat interoperability as configured but unverified until an iPhone and Android phone pass live join, speak, mute, reconnect, Bluetooth, and interruption tests.

## Current parity snapshot

Legend: **Parity** = equivalent flow is present in code; **Partial** = useful implementation exists but behavior/UX differs or lacks a key subflow; **Missing** = no Android equivalent was found; **Blocked** = cannot currently ship or validate.

| Capability | Android status | Evidence / gap |
|---|---|---|
| Email auth, password reset, onboarding, sign-out, account deletion | Parity | `AuthManager.kt`, `LoginScreen.kt`, `OnboardingScreen.kt`, `ProfileScreen.kt` |
| Home dashboard and live ride stats | Parity | `HomeScreen.kt` uses real history, weather, and location data |
| Solo ride tracking, background location, GPX, summary | Partial | Core flow exists; device/background lifecycle still needs long-ride validation |
| Group ride create/join, live riders, invite deep link | Partial | Core flow and `packride://join` exist; multi-device interoperability is unverified |
| Turn-by-turn route guidance and waypoint planning | Partial | Routing and Places autocomplete exist; repeat navigation requests can fail because two pending bridges are not cleared |
| Group voice chat | Partial | Agora client is wired into `GroupRideScreen.kt`; dependency/version and cross-platform audio remain unverified |
| Ride feed, reactions, comments, photos, suggested riders | Partial | Main social feed is ported; suggested-rider “Follow” bypasses iOS's request/accept model |
| Follow requests, accept/decline, remove follower | Parity in code | Android now uses the canonical iOS `followRequests`, `followers`, and `following` paths and exposes the Home notification inbox; cross-device validation remains |
| Location audience/privacy settings | Parity in code | Android now uses the canonical `locationVisibility` schema for follower/community toggles, selected followers, and radius; Cloud Function deployment and cross-device revocation remain to validate |
| Nearby friends map | Partial | Android has `FriendsScreen.kt`, but the iOS privacy/radius contract is absent |
| Communities, membership, active riders, create/join/delete/share | Parity in code | `CommunityScreen.kt` and community stores/managers cover the main flow |
| Scheduled rides, RSVP, invitations | Parity in code | `ScheduleRideScreen.kt`, `InvitesScreen.kt` |
| Need Help sharing and app-wide alert banner | Parity in code | Android now writes the canonical request and reads only its private `users/{uid}/helpAlerts` fan-out; background delivery requires two-device validation |
| Crash detection, countdown, contacts, responder alert | Parity in code | Android now creates canonical `crashIncidents`, includes impact/group context, routes push taps, and supports responder acknowledgement; physical-device validation remains |
| Push notifications | Partial | FCM service and manifest entry exist; token delivery, channels, notification routing, and server triggers need end-to-end tests |
| Ride history, cloud sync, filters, GPX export/share/replay | Blocked | Feature exists, but current Kotlin compile error is in `RideHistoryScreen.kt:666` |
| Ride analytics, digest, badges, aggregate trends | Partial | Summary engines/screens exist; telemetry drill-down is missing and lap data is flattened into ride history |
| Per-ride telemetry route map | Missing | iOS `RideTelemetryMapView.swift`; Android explicitly renders the score card as non-interactive |
| Participant statistics for group rides | Missing | Android source explicitly notes no equivalent of iOS `ParticipantStatsView` |
| Track mode and live lap delta | Partial | Core lap engine and UI exist; iOS's separate lap record/history model and detailed corner/timeline analytics are absent |
| Lap history, lap-to-lap comparison | Partial | Android can compare entered lap arrays, but lacks iOS session-backed selection and GPX comparison workflow |
| Share a lap session with a friend | Missing | No equivalent of `LapSharingManager.swift`, shared-session inbox, or GPX download/cache |
| Post lap session to feed | Partial | Feed supports lap posts, but Track Mode explicitly lacks the iOS post-to-feed composer handoff |
| Nearby track-layout discovery | Missing | No equivalent of `TrackLayoutModels.swift` / `TrackLayoutService.swift` Overpass discovery |
| Map style picker across map surfaces | Missing | Android maps use fixed configuration; replay source explicitly calls out the gap |
| Weather and road/speed-limit information | Parity in code | Open-Meteo/Android geocoding/Overpass substitute for WeatherKit/MapKit behavior |
| Garage and maintenance tracking | Parity in code | Local persistence plus Firebase sync in `GarageManager.kt` |
| Native visual share cards | Partial | Android shares text/GPX; no equivalent rendered share-card system to `NativeShareCards.swift` |
| Dark mode | Android-only divergence | Android exposes Dark Mode; iOS is intentionally pinned to light mode. Decide whether this is acceptable platform adaptation |
| App navigation/information architecture | Partial | iOS has 5 tabs (Home/Feed/Group/Track/Profile); Android has 6 (Home/Feed/Ride/Group/Safety/More) plus a 14-item horizontal sub-tab strip |
| Automated tests | Missing on both | No app unit/UI test suites were found; parity-sensitive shared-schema logic is unprotected |
| Build/release | Blocked | `./gradlew assembleDebug` fails; release credentials and Maps key are configured locally, but no successful APK/AAB gate exists yet |

## Priority plan

### P0 — Restore a trustworthy baseline (1–2 days)

1. Fix the Compose context error at `RideHistoryScreen.kt:666` and make `assembleDebug` pass.
2. Add a minimal CI/build gate: debug compile, release bundle compile, lint, and unit tests.
3. Replace the stale skeleton README with the actual current feature and setup inventory.
4. Remove or quarantine dead/unreferenced UI (`InAppNavScreen.kt`, standalone emergency contacts duplication, experimental voice-channel surface) after verifying there are no intended entry points.
5. Clear `PendingWaypoints` and `PendingMotoRun` after navigation, and add tests for launching the same destination twice.

**Exit criteria:** clean debug build; clean release bundle with minification; no no-op primary actions; app launches through auth/onboarding to every top-level destination.

### P1 — Backend contract and safety-critical parity (1–2 weeks)

1. Write a shared Firebase contract document for every cross-platform path (`users`, follows/requests, rides, feed posts/comments/reactions, communities, scheduled rides, help requests, crash alerts, invites, FCM tokens, lap shares).
2. Implement iOS-compatible follow requests on Android: pending state, accept/decline, remove follower, counts, notification inbox, and suggested-rider behavior.
3. Port location visibility controls: follower/community toggles, selected-follower audience, nearby radius, persistence, and enforcement in all location writers/readers—not just UI switches.
4. Complete FCM notification channels and tap routing for follow requests, invites, ride starts, help requests, and crash incidents.
5. Validate crash and Need Help behavior with the app foregrounded, backgrounded, and process-killed. Implement an Android-native urgent emergency surface (high-importance notification/full-screen intent only where Play policy permits), rather than copying iOS UI literally.
6. Verify account deletion removes or anonymizes all Android-written data under the same policy as iOS.

**Exit criteria:** two accounts can complete the full follow/privacy/help/crash lifecycle across one iPhone and one Android device; Firebase rules allow intended operations and reject unauthorized ones.

### P2 — Core riding and social parity (2–3 weeks)

1. Introduce a first-class Android `LapRecord`/`LapHistoryManager` compatible with the iOS lap schema, including track, timestamps, bike, GPX reference, analytics, and best-lap metadata.
2. Port session-backed lap comparison and `LapSharingManager`: upload, recipient inbox, download/cache, revoke/delete behavior, and cross-platform schema tests.
3. Wire Track Mode summaries to post lap sessions to the feed.
4. Add ride telemetry drill-down: route-colored metric selection, timeline/scrubbing, corner breakdown, and ride-score navigation.
5. Add participant statistics for completed group rides.
6. Finish photo/share parity: rendered ride/community/group cards and consistent Android Sharesheet output.
7. Exercise GPX local/cloud fallback after reinstall, app update, offline launch, and cross-device history sync.

**Exit criteria:** a lap recorded on either platform can be shared, opened, compared, and posted on the other; a ride synced from iOS opens history, map, replay, analytics, and export correctly on Android.

### P3 — Navigation and map UX parity (1–2 weeks)

1. Replace the 14-item `MoreScreen` tab strip with hierarchical navigation.
2. Match the iOS primary destinations exactly: Home, Feed, Group, Track, Profile. Move Solo Ride and Safety to the same Home/Profile entry points used on iOS rather than keeping Android-only primary tabs.
3. Give each primary tab an independent back stack and define repeated-tab behavior (tap active tab to return to root).
4. Add consistent map style selection and persist it across route planning, navigation, group map, track, history, replay, and telemetry.
5. Port nearby track-layout discovery with caching, request throttling, clear provenance, and graceful Overpass failure.
6. Audit accessibility: TalkBack labels, dynamic font scaling, touch targets, color contrast, reduced motion, and landscape behavior.

**Exit criteria:** every iOS destination has an obvious Android path in no more taps than the iOS flow; back behavior, deep links, and repeated tab taps are deterministic.

### P4 — Release hardening (1 week plus field testing)

1. Add unit tests for lap crossing/interpolation, analytics, GPX parsing, schema/date conversions, badge rules, audience filtering, and route-step advancement.
2. Add Compose UI tests for auth/onboarding, solo ride start/end, group join, feed interaction, follow request, privacy controls, emergency cancellation, and account deletion.
3. Run physical-device soak tests: 2+ hour ride, screen off, Doze/battery saver, lost network, location revoked mid-ride, process recreation, Bluetooth audio, phone call interruption, and thermal/battery measurement.
4. Run an iOS ↔ Android interoperability matrix using fresh and legacy Firebase records.
5. Validate Play requirements: data-safety declaration, privacy policy, background-location disclosure, foreground-service behavior, notification permission timing, account deletion, ad consent, signing, obfuscation, and crash reporting.
6. Ship to internal testing first, then staged rollout with crash-free sessions and battery thresholds defined before expansion.

**Exit criteria:** all critical two-device cases pass; no P0/P1 defects; signed AAB uploads successfully; staged release meets agreed crash-free and battery targets.

## Recommended delivery slices

| Slice | User-visible outcome | Dependencies |
|---|---|---|
| 1. Buildable beta | Android installs and every current flow is reachable | P0 |
| 2. Safe social beta | Correct follow requests, privacy, notifications, SOS/crash behavior | P1 |
| 3. Rider-data parity | Cross-platform lap sharing, telemetry, participant stats | P2 |
| 4. UX parity | Coherent navigation, map controls, track discovery | P3 |
| 5. Store release | Tested, policy-compliant signed AAB | P4 |

## Cross-platform acceptance matrix

Run each scenario in all four directions where applicable: Android→Android, iOS→iOS, Android→iOS, and iOS→Android.

- Account/profile creation, profile photo update, sign-out/in, deletion.
- Follow request, accept, decline, unfollow, remove follower, notification tap.
- Solo ride start/end, live visibility, ride-start push, history sync, GPX recovery.
- Group create/join/deep link, waypoint sync, live map, leader exit, voice join/mute/rejoin.
- Community create/join/share/delete, active rider, scheduled ride and RSVP.
- Feed ride/lap post, photo, reaction toggle, comment add/delete, share.
- Need Help target selection, background update, responder navigation, stop/expiry.
- Crash countdown cancel/send, emergency contact data, responder acknowledge/dismiss.
- Lap record/share/download/compare and telemetry rendering.

## Decisions required before implementation

1. Should Android remove its dark-mode control and match iOS's fixed light appearance? Exact look-and-feel parity implies yes.
2. Should legacy iOS Firebase records be migrated centrally, or supported indefinitely by tolerant Android/iOS decoders?
3. What release thresholds are acceptable (for example, crash-free sessions, maximum battery drain per riding hour, and maximum location staleness)?

## Audit limitations

- The iOS app was used as the product baseline even where its handover notes describe external setup or testing still pending.
- No live Firebase rules, Cloud Functions deployment, APNs/FCM console state, Agora console state, or App/Play Console configuration was inspected.
- Runtime visual parity, sensor behavior, notification delivery, and real cross-platform data exchange remain unverified.
- Android build result on the audit date: `compileDebugKotlin` failed at `RideHistoryScreen.kt:666` with “@Composable invocations can only happen from the context of a @Composable function.”
