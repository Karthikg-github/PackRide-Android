# PackRide Next-Version Track Upgrade

Status: Android implementation started September 8, 2026. The first timing-core slice is complete and verified; shared tracks, sectors UI, deeper analysis, and the iOS port remain.

## Android progress

- Complete: finite two-ended start/finish gate setup and map rendering.
- Complete: selectable crossing direction, directional segment intersection, and interpolated crossing timestamps.
- Complete: stale/inaccurate GPS rejection, minimum-lap validation, skipped-sector/wrong-direction invalid states, ordered-sector engine support, and timing confidence.
- Complete: deterministic JVM coverage for interpolation, finite-gate bounds, poor fixes, and skipped sectors.
- Next: track/configuration persistence in shared Firebase, sector editor and sector dashboard, verified nearby-track selection and offline cache.
- Complete on Android: 10-mile nearby lookup, automatic single-track selection, multiple-track chooser, shared Firebase catalogue parsing, cached catalogue fallback, and OpenStreetMap raceway fallback.
- Next: catalogue authoring/admin tools, configuration picker for multi-layout venues, sector editor and sector dashboard.
- Later: progress-aligned predictive delta, coloured racing lines, braking/acceleration overlays, recovery testing, real-device track validation, then the behaviorally identical Swift port.

## Objective

Build a shared, phone-based track and lap system inspired by RaceBox. PackRide will not claim dedicated 10 Hz GNSS accuracy; it will use phone location data, directional gate-crossing geometry, timestamp interpolation, accuracy indicators, and invalid-lap handling. The design must also allow an external high-rate Bluetooth GNSS receiver to be added later without changing the shared track format.

## Delivery estimate

- RaceBox-style core on iOS and Android: 20–30 focused development hours.
- Polished track creation and analysis: 40–60 total development hours.
- Real-world validation: additional user testing across several devices and track sessions.

## Milestone 1 — Shared track foundation

- Store canonical tracks and configurations in the shared Firebase backend.
- Give each track a stable ID, name, venue, country, center coordinate, provenance, verification state, and one or more configurations.
- Give each configuration a direction, circuit outline, start/finish gate, sector gates, and optional pit-lane metadata.
- Use OpenStreetMap/Overpass to suggest circuit outlines, then cache corrected/verified definitions in Firebase.
- Add nearby-track discovery, name search, configuration selection, offline cache, and custom-track creation on both platforms.
- Repair the current iOS Overpass response parsing and implement the same behavior on Android.

## Milestone 2 — Timing engine

- Replace radius-only start/finish detection with a finite directional line segment.
- Detect crossing direction and interpolate the crossing timestamp between GPS samples.
- Add ordered sector gates, sector timing, best sectors, theoretical best lap, open-course support, and invalid/incomplete lap states.
- Reject stale or low-quality fixes and expose GPS quality to the rider.
- Keep the Swift and Kotlin algorithms behaviorally identical and test both with the same simulated traces/GPX fixtures.

## Milestone 3 — Analysis and presentation

- Render the verified reference circuit and the recorded racing line as separate layers.
- Add speed-coloured traces, acceleration/braking/lean overlays, sector visualization, and spatial two-lap comparison.
- Improve predictive delta by aligning samples to progress around the selected circuit rather than only accumulated distance.
- Add custom-track sharing, moderation, revisioning, and administrator verification.
- Match the resulting user experience on iOS and Android.

## Proposed shared data shape

```text
tracks/{trackId}
  name
  venue
  country
  center
  source
  verificationStatus
  configurations/{configurationId}
    name
    direction
    outline[]
    startFinishGate { a, b, direction }
    sectorGates[]
```

## Accuracy position

- Do not advertise RaceBox-equivalent accuracy from a phone.
- Interpolation should improve timing beyond the raw location-update interval, but results remain dependent on phone hardware, mounting, sky view, OS throttling, and track geometry.
- Mark low-confidence laps rather than displaying false precision.
- Preserve the raw location timestamps and accuracy values so timing can be recalculated as the engine improves.
- PackRide will use phone GPS only; external 10 Hz GNSS receiver integration is not planned.
- User-facing disclosure: PackRide is not a professional lap timer. A 1–2 second difference may be typical, with larger differences possible under weak reception, OS throttling, poor mounting, or obstructed sky view.

## Acceptance gate

- The same track/configuration opens identically on Android and iOS.
- The same simulated GPS trace produces matching lap/sector results on both platforms within the agreed numeric tolerance.
- Nearby parallel track segments and pit lanes do not create false laps.
- Reverse-direction, skipped-sector, poor-fix, background, offline, and process-recovery scenarios behave predictably.
- Physical testing is completed on multiple Android phones and iPhones before release.
