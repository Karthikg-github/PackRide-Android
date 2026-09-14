package com.karthik.packride.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karthik.packride.auth.AuthManager
import com.karthik.packride.community.PendingCommunityJoin
import com.karthik.packride.nav.PendingMoreTab
import com.karthik.packride.nav.PendingSafetyTab

@Composable
fun MoreScreen(auth: AuthManager) {
    var tab by remember { mutableIntStateOf(0) }
    // Aug 31, 2026 — trimmed from 19 flat tabs down to 13: iOS has no tab
    // strip anywhere in its navigation (confirmed zero TabView/.tabItem hits
    // project-wide) — every one of these lived here only because this screen
    // needed *some* place to put them during the initial port. Five were
    // removed this pass to match iOS's real navigation shape:
    //  - "Waypoints" -> promoted to a real top-level PackRideNav destination
    //    (Dest.Waypoints, see PackRideNav.kt), matching how iOS reaches
    //    WaypointsView via NavigationLink/fullScreenCover, not a tab.
    //  - "Nav" (InAppNavScreen) -> deleted outright: a redundant, simplified
    //    duplicate of the exact same pick-a-waypoint -> PendingTurnByTurn
    //    hand-off WaypointsScreen already does directly, with zero iOS
    //    equivalent. InAppNavScreen.kt is left on disk, unreferenced
    //    (grep-confirmed — only comments mention it now); flagged for Karthik
    //    to delete by hand since this tool can't rm.
    //  - "Voice" (VoiceChannelScreen) -> removed, not relocated. iOS has zero
    //    voice/Agora references anywhere, and Android's GroupRideScreen never
    //    linked to this screen either — legitimate work-in-progress groundwork
    //    for a feature that isn't built on either platform yet, not dead code.
    //    Left on disk unreferenced, deliberately not flagged for deletion.
    //  - "MotoRun" -> promoted to a real top-level PackRideNav destination
    //    (Dest.MotoRun), matching iOS's hidden "PACKRIDE" wordmark easter egg
    //    (ContentView.swift's `.onTapGesture { showMotoRun = true }` +
    //    `.fullScreenCover`) — now triggered the same way from HomeScreen's
    //    hero card instead of living in this tab strip.
    //  - "SOS" (EmergencyContactsScreen) -> removed, no replacement needed.
    //    CrashDetectionScreen already has its own EmergencyContactsSection
    //    backed by the same shared EmergencyContactStore ("one list
    //    everywhere", matching iOS's CrashDetectionView, which has no
    //    separate top-level emergency-contacts screen either) — reachable via
    //    SafetyHubScreen's existing Crash sub-tab. Left on disk unreferenced.
    // "Profile" (index 13, the `else` branch below) is kept as a real,
    // clickable tab here -- Karthik's instruction was not to change WHERE
    // Profile lives (still buried in More, tied to the still-open bottom-tab
    // question), not to make it unreachable. It was reachable before this
    // pass (tab 18 of 19) and must stay reachable after (tab 13 of 14).
    val labels = listOf(
        "Track", "Lap Δ", "Trends", "History", "Replay", "Friends", "Community",
        "Schedule", "Invites", "Garage", "Badges", "Weather", "Analytics", "Profile"
    )
    // Aug 30, 2026 — a packride://joincommunity link has already switched
    // PackRideNav to the More tab (see PackRideNav.kt); this is the second
    // half — selecting the Community sub-tab within More's own flat tab
    // strip, since Community is index 6 here rather than a real navController
    // route. CommunityScreen itself consumes+clears the pending value to
    // prefill Join Community — this effect only picks the sub-tab.
    val pendingCommunityJoin by PendingCommunityJoin.pending.collectAsState()
    LaunchedEffect(pendingCommunityJoin) {
        if (pendingCommunityJoin != null) tab = 6
    }

    // Aug 31, 2026 — Home's flat feature-row list (see HomeScreen.kt) requests
    // a specific sub-tab here the same way a community-join deep link does
    // above; PackRideNav observes the same StateFlow to switch to this tab.
    val pendingMoreTab by PendingMoreTab.pending.collectAsState()
    LaunchedEffect(pendingMoreTab) {
        pendingMoreTab?.let {
            tab = it
            PendingMoreTab.clear()
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
            labels.forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }
        when (tab) {
            0 -> TrackModeScreen(onOpenCompare = { tab = 1 }, onOpenTrends = { tab = 2 })
            1 -> LapCompareScreen()
            2 -> LapTrendsScreen()
            3 -> RideHistoryScreen(auth, onOpenReplay = { tab = 4 })
            4 -> ReplayScreen()
            5 -> FriendsScreen()
            6 -> CommunityScreen(auth)
            7 -> ScheduleRideScreen(auth)
            8 -> InvitesScreen(auth)
            9 -> GarageScreen()
            10 -> BadgesScreen()
            11 -> WeatherScreen()
            12 -> AnalyticsScreen()
            // Aug 31, 2026 — "SOS" (EmergencyContactsScreen, formerly index
            // 17) was removed above; the "N emergency contacts" summary row
            // in ProfileScreen now opens SafetyHubScreen's Crash sub-tab
            // (which has its own EmergencyContactsSection) via
            // PendingSafetyTab instead of jumping to a sub-tab here.
            else -> ProfileScreen(auth, onOpenGarage = { tab = 9 }, onOpenSafety = { PendingSafetyTab.request(0) })
        }
    }
}
