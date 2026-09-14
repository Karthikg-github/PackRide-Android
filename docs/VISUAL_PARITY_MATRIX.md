# PackRide iOS ↔ Android visual acceptance matrix

This is the release gate for the requested pixel-level parity. iOS is the visual source of truth. A row is `PASS` only after captures from the same content fixture, appearance, orientation, font scale, and comparable viewport have been overlaid and inspected. Source similarity alone is not a visual pass.

## Capture contract

- Use light appearance, portrait, default text size, 100% display scaling, and the same signed-in Firebase test account.
- Disable ad personalization variability or use the same reserved ad-height fixture.
- Name captures `<flow>__<state>__ios.png` and `<flow>__<state>__android.png`.
- Compare safe areas, section order, typography, icons, imagery, colors, dividers, corner radii, control states, map camera/overlays, scrolling, sheets, dialogs, animation endpoints, and tap outcomes.
- Required states are: `loading`, `empty`, `populated`, `error`, `offline`, `permission-denied`, `active`, `confirmation`, and `completion`. Mark `N/A` only when the state genuinely cannot occur on either platform.
- A platform-native permission prompt or share sheet may differ visually, but its entry point, explanatory copy, cancellation, and resulting state must match.

## Screen gate

| Flow / screen | Required applicable states | iOS capture | Android capture | Status |
|---|---|---|---|---|
| Login, reset password | empty, error, offline, confirmation | pending | pending | NOT VERIFIED |
| Onboarding and profile creation | empty, populated, permission-denied, completion | pending | pending | NOT VERIFIED |
| Home hero and all-time stats | loading, empty, populated, offline | pending | pending | NOT VERIFIED |
| Recent rides expanded/collapsed | loading, empty, populated, error, offline | pending | pending | NOT VERIFIED |
| Badges, Digest, Trends | loading, empty, populated, error | pending | pending | NOT VERIFIED |
| Feed, post detail, comments/reactions | loading, empty, populated, error, offline, confirmation | pending | pending | NOT VERIFIED |
| Group lobby, active ride, completion | loading, empty, populated, permission-denied, offline, active, confirmation, completion | pending | pending | NOT VERIFIED |
| Plan Route and waypoint search | loading, empty, populated, error, offline, permission-denied, confirmation | pending | pending | NOT VERIFIED |
| Turn-by-turn navigation | loading, error, offline, permission-denied, active, confirmation, completion | pending | pending | NOT VERIFIED |
| Track search and start/finish setup | loading, empty, populated, error, offline, permission-denied, confirmation | pending | pending | NOT VERIFIED |
| Active Track session | permission-denied, offline, active, confirmation, completion | pending | pending | NOT VERIFIED |
| Recent Track sessions and detail | loading, empty, populated, error, offline | pending | pending | NOT VERIFIED |
| Racing line, lap comparison, playback | loading, empty, populated, error, offline, active, completion | pending | pending | NOT VERIFIED |
| Track trends and shared session | loading, empty, populated, error, offline, confirmation | pending | pending | NOT VERIFIED |
| Drag mode setup, active run, result | loading, empty, permission-denied, active, confirmation, completion | pending | pending | NOT VERIFIED |
| Solo ride setup, active ride, result | permission-denied, offline, active, confirmation, completion | pending | pending | NOT VERIFIED |
| Ride history and filters | loading, empty, populated, error, offline, confirmation | pending | pending | NOT VERIFIED |
| Ride telemetry map and replay | loading, populated, error, offline, active, completion | pending | pending | NOT VERIFIED |
| GPX export/import/share | loading, error, offline, confirmation, completion | pending | pending | NOT VERIFIED |
| Friends Following/Followers/Discover/Map | loading, empty, populated, error, offline, permission-denied, confirmation | pending | pending | NOT VERIFIED |
| Notifications and follow requests | loading, empty, populated, error, offline, confirmation | pending | pending | NOT VERIFIED |
| Communities and scheduled rides | loading, empty, populated, error, offline, confirmation, completion | pending | pending | NOT VERIFIED |
| Safety hub: Crash and Need Help | empty, populated, permission-denied, offline, active, confirmation, completion | pending | pending | NOT VERIFIED |
| Emergency contacts | loading, empty, populated, error, offline, confirmation | pending | pending | NOT VERIFIED |
| Ride Comms lobby and live room | loading, empty, populated, error, offline, permission-denied, active, confirmation | pending | pending | NOT VERIFIED |
| Garage and maintenance | loading, empty, populated, error, offline, confirmation | pending | pending | NOT VERIFIED |
| Profile full-bleed hero and edit | loading, empty, populated, error, offline, confirmation | pending | pending | NOT VERIFIED |
| Privacy & Data, units, radius | loading, populated, error, offline, permission-denied, confirmation | pending | pending | NOT VERIFIED |
| Weather | loading, populated, error, offline, permission-denied | pending | pending | NOT VERIFIED |
| MotoRun game and result | empty, active, confirmation, completion | pending | pending | NOT VERIFIED |
| Global tab/navigation/deep links | populated, error, confirmation | pending | pending | NOT VERIFIED |
| Crash recovery feedback | offline, confirmation, completion | pending | pending | CODE VERIFIED; DEVICE PENDING |

## Release decision

Pixel parity is **not release-verified** until every applicable capture pair above passes and the bidirectional Firebase/device matrix also passes. Any remaining difference must have either a tracked defect or a documented OS-required exception. Percent estimates must not replace this gate.
