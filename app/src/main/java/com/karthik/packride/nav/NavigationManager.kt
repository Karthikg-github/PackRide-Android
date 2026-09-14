package com.karthik.packride.nav

// Aug 30, 2026 — retired. This used to be the external-Google-Maps-launcher
// stub every "Navigate" button called (openDirections(context, lat, lng, label)
// -> ACTION_VIEW google.navigation:q=...). Real in-app turn-by-turn guidance
// now lives in TurnByTurnNavigator.kt + ui/screens/TurnByTurnScreen.kt,
// requested via PendingTurnByTurn.request(...) from WaypointsScreen and
// InAppNavScreen. Left as an empty file (rather than deleted) because the
// on-device tool this project is edited through can't delete files — see
// waypoints/WaypointStore.kt for the same pattern.
