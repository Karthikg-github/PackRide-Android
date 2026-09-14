package com.karthik.packride.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Motorcycle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.LocalPrBackAction
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.karthik.packride.auth.AuthManager
import com.karthik.packride.community.CommunityMembershipStore
import com.karthik.packride.community.PendingCommunityJoin
import com.karthik.packride.group.ActiveRideTracker
import com.karthik.packride.group.PendingGroupRide
import com.karthik.packride.help.HelpRequest
import com.karthik.packride.help.HelpRequestManager
import com.karthik.packride.help.relevantTo
import com.karthik.packride.nav.PendingTurnByTurn
import com.karthik.packride.nav.PendingMoreTab
import com.karthik.packride.nav.PendingSafetyTab
import com.karthik.packride.nav.PendingWaypoints
import com.karthik.packride.nav.PendingMotoRun
import com.karthik.packride.nav.PendingDigest
import com.karthik.packride.nav.PendingCrashIncident
import com.karthik.packride.nav.PendingNotificationDestination
import com.karthik.packride.ui.screens.DigestScreen
import com.karthik.packride.ui.screens.AnalyticsScreen
import com.karthik.packride.ui.screens.BadgesScreen
import com.karthik.packride.ui.screens.CommunityScreen
import com.karthik.packride.ui.screens.FeedScreen
import com.karthik.packride.ui.screens.FriendsScreen
import com.karthik.packride.ui.screens.GarageScreen
import com.karthik.packride.ui.screens.GroupRideScreen
import com.karthik.packride.ui.screens.HomeScreen
import com.karthik.packride.ui.screens.InvitesScreen
import com.karthik.packride.ui.screens.LapCompareScreen
import com.karthik.packride.ui.screens.LapTrendsScreen
import com.karthik.packride.ui.screens.MotoRunScreen
import com.karthik.packride.ui.screens.NotificationsScreen
import com.karthik.packride.ui.screens.ProfileScreen
import com.karthik.packride.ui.screens.PrivacyDataScreen
import com.karthik.packride.ui.screens.ReplayScreen
import com.karthik.packride.ui.screens.RideHistoryScreen
import com.karthik.packride.ui.screens.ScheduleRideScreen
import com.karthik.packride.ui.screens.SafetyHubScreen
import com.karthik.packride.ui.screens.SoloRideScreen
import com.karthik.packride.ui.screens.TrackModeScreen
import com.karthik.packride.ui.screens.TurnByTurnScreen
import com.karthik.packride.ui.screens.WaypointsScreen
import com.karthik.packride.ui.screens.WeatherScreen
import com.karthik.packride.ui.screens.VoiceChannelScreen
import kotlin.math.abs

sealed class Dest(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Dest("home", "Home", Icons.Default.Home)
    data object Feed : Dest("feed", "Feed", Icons.Default.Newspaper)
    data object Group : Dest("group", "Group", Icons.Default.Groups)
    data object Track : Dest("track", "Track", Icons.Default.Timer)
    data object Profile : Dest("profile", "Profile", Icons.Default.AccountCircle)
    data object Privacy : Dest("privacy", "Privacy & Data", Icons.Default.Person)

    // Secondary destinations mirror pushes/sheets inside the five iOS tabs.
    // They remain in the graph but never appear as Android-only bottom tabs.
    data object SoloRide : Dest("soloRide", "Ride", Icons.Default.Motorcycle)
    data object Safety : Dest("safety", "Safety", Icons.Default.Warning)
    data object Friends : Dest("friends", "Friends", Icons.Default.Person)
    data object Community : Dest("community", "Community", Icons.Default.Share)
    data object History : Dest("history", "History", Icons.Default.CalendarMonth)
    data object Replay : Dest("replay", "Replay", Icons.Default.Motorcycle)
    data object Garage : Dest("garage", "Garage", Icons.Default.Motorcycle)
    data object Schedule : Dest("schedule", "Schedule", Icons.Default.CalendarMonth)
    data object Invites : Dest("invites", "Invites", Icons.Default.CalendarMonth)
    data object Notifications : Dest("notifications", "Notifications", Icons.Default.Warning)
    data object Badges : Dest("badges", "Badges", Icons.Default.Warning)
    data object Weather : Dest("weather", "Weather", Icons.Default.Map)
    data object Analytics : Dest("analytics", "Analytics", Icons.Default.CalendarMonth)
    data object LapCompare : Dest("lapCompare", "Lap Δ", Icons.Default.Timer)
    data object LapTrends : Dest("lapTrends", "Trends", Icons.Default.Timer)
    // Aug 30, 2026 — real in-app turn-by-turn guidance (see TurnByTurnScreen).
    // Deliberately NOT in `tabs` below: this is a full-screen destination
    // pushed on top of whatever tab requested it (WaypointsScreen or
    // InAppNavScreen via PendingTurnByTurn), matching iOS's fullScreenCover
    // — not a persistent bottom-tab like the others.
    data object TurnByTurn : Dest("turnByTurn", "Navigate", Icons.Default.Motorcycle)
    // Aug 31, 2026 — Waypoints/MotoRun promoted out of MoreScreen's flat tab
    // strip to real top-level destinations (see MoreScreen.kt's Aug 31
    // comment), same "full-screen push, not a bottom tab" shape as
    // TurnByTurn above — iOS reaches WaypointsView via NavigationLink and
    // MotoRunView via fullScreenCover, neither one a persistent tab.
    data object Waypoints : Dest("waypoints", "Plan Route", Icons.Default.Map)
    data object MotoRun : Dest("motoRun", "Moto Run", Icons.Default.Motorcycle)
    data object Digest : Dest("digest", "Digest", Icons.Filled.CalendarMonth)
    data object RideComms : Dest("rideComms", "Ride Comms", Icons.Default.Groups)
}

private val tabs = listOf(
    Dest.Home, Dest.Feed, Dest.Group, Dest.Track, Dest.Profile
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackRideNav(auth: AuthManager) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    // Aug 30, 2026 — Plan Route's "Group Ride" choice lives nested inside the
    // More tab (WaypointsScreen has no navController of its own), so it
    // requests the Group tab via this shared bridge instead of trying to
    // navigate directly — mirrors iOS's deepLinkRouter.pendingRideCode fix
    // (WaypointsView used to present Group Ride via fullScreenCover outside
    // ContentView's tab structure entirely, losing the bottom tab bar; see
    // swift-port-plan.md). GroupRideScreen itself consumes the pending code
    // to auto-join/create — this effect only handles switching tabs.
    val pendingGroupRide by PendingGroupRide.pending.collectAsState()
    LaunchedEffect(pendingGroupRide) {
        if (pendingGroupRide != null) {
            navController.navigate(Dest.Group.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Aug 30, 2026 — "Navigate" from WaypointsScreen/InAppNavScreen requests
    // this the same way Group Ride does above, but as a plain push (not a
    // tab switch) so popping back afterwards (TurnByTurnScreen's onDone)
    // returns to exactly wherever guidance was launched from.
    val pendingTurnByTurn by PendingTurnByTurn.pending.collectAsState()
    LaunchedEffect(pendingTurnByTurn) {
        if (pendingTurnByTurn != null) {
            navController.navigate(Dest.TurnByTurn.route)
        }
    }

    // Aug 31, 2026 — "Plan Route" from HomeScreen requests this the same way
    // pendingTurnByTurn does above: a plain push (not a tab switch), since
    // WaypointsScreen is a full-screen destination, matching iOS's
    // NavigationLink push, not a persistent tab.
    val pendingWaypoints by PendingWaypoints.pending.collectAsState()
    LaunchedEffect(pendingWaypoints) {
        if (pendingWaypoints != null) {
            navController.navigate(Dest.Waypoints.route)
        }
    }

    // Aug 31, 2026 — the "PACKRIDE" wordmark easter egg on Home requests this
    // the same way pendingWaypoints does above: a plain push, matching iOS's
    // fullScreenCover(isPresented: $showMotoRun) — not a persistent tab.
    val pendingMotoRun by PendingMotoRun.pending.collectAsState()
    LaunchedEffect(pendingMotoRun) {
        if (pendingMotoRun != null) {
            navController.navigate(Dest.MotoRun.route)
            PendingMotoRun.clear()
        }
    }

    // Aug 31, 2026 — Ride History's "Digest" quick-link requests this the
    // same way pendingWaypoints/pendingMotoRun do above: a plain push,
    // matching iOS's NavigationLink(destination: RidingDigestView()) pushed
    // from Ride History's own NavigationStack, not a tab. Unlike the two
    // bridges above (which never clear(), a pre-existing latent issue where
    // a second tap on the same row after already navigating once won't
    // re-fire — flagged for a follow-up, not fixed here since it's not part
    // of this task), this one clears itself right after navigating so
    // repeat taps on the Digest link always work.
    val pendingDigest by PendingDigest.pending.collectAsState()
    LaunchedEffect(pendingDigest) {
        if (pendingDigest != null) {
            navController.navigate(Dest.Digest.route)
            PendingDigest.clear()
        }
    }

    // Community links push the same community list reached from iOS Home.
    // CommunityScreen consumes the payload and opens its prefilled join UI.
    val pendingCommunityJoin by PendingCommunityJoin.pending.collectAsState()
    LaunchedEffect(pendingCommunityJoin) {
        if (pendingCommunityJoin != null) {
            navController.navigate(Dest.Community.route) { launchSingleTop = true }
        }
    }

    // Compatibility bridge for callers not yet converted from the former
    // MoreScreen indices. Each target now opens as an iOS-style destination.
    val pendingMoreTab by PendingMoreTab.pending.collectAsState()
    LaunchedEffect(pendingMoreTab) {
        val route = when (pendingMoreTab) {
            0 -> Dest.Track.route
            1 -> Dest.LapCompare.route
            2 -> Dest.LapTrends.route
            3 -> Dest.History.route
            4 -> Dest.Replay.route
            5 -> Dest.Friends.route
            6 -> Dest.Community.route
            7 -> Dest.Schedule.route
            8 -> Dest.Invites.route
            9 -> Dest.Garage.route
            10 -> Dest.Badges.route
            11 -> Dest.Weather.route
            12 -> Dest.Analytics.route
            13 -> Dest.Profile.route
            else -> null
        }
        if (route != null) {
            navController.navigate(route) { launchSingleTop = true }
            PendingMoreTab.clear()
        }
    }

    // Aug 31, 2026 — Home hero header's warning-triangle icon requests the
    // Need Help sub-tab directly (see nav/PendingSafetyTab.kt), same bridge
    // shape as pendingMoreTab above but for SafetyHubScreen's own tab strip.
    val pendingSafetyTab by PendingSafetyTab.pending.collectAsState()
    LaunchedEffect(pendingSafetyTab) {
        if (pendingSafetyTab != null) {
            navController.navigate(Dest.Safety.route) { launchSingleTop = true }
        }
    }

    val pendingCrashIncident by PendingCrashIncident.incidentId.collectAsState()
    LaunchedEffect(pendingCrashIncident) {
        if (pendingCrashIncident != null) {
            navController.navigate(Dest.Profile.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            PendingCrashIncident.clear()
        }
    }

    val pendingNotificationType by PendingNotificationDestination.type.collectAsState()
    LaunchedEffect(pendingNotificationType) {
        val route = when (pendingNotificationType) {
            "followRequest" -> Dest.Notifications.route
            "rideInvite" -> Dest.Invites.route
            "communityRide" -> Dest.Community.route
            "followerRide" -> Dest.Friends.route
            "helpRequest" -> {
                PendingSafetyTab.request(1)
                Dest.Safety.route
            }
            else -> null
        }
        if (route != null) navController.navigate(route) { launchSingleTop = true }
        if (pendingNotificationType != null) PendingNotificationDestination.clear()
    }

    // Aug 30, 2026 — Need Help targeting fix: app-wide incoming-alert banner,
    // port of iOS ContentView's incomingHelpBanner. Listens continuously from
    // here (root-level, survives tab switches — see HelpRequestManager's
    // ref-counted listener) rather than only while NeedHelpScreen happens to
    // be open, exactly like iOS's helpSharing.listenForActiveRequests() in
    // ContentView.onAppear.
    val helpManager = remember { HelpRequestManager.get() }
    DisposableEffect(Unit) {
        helpManager.listenForActiveRequests()
        onDispose { helpManager.stopListening() }
    }
    val allHelpRequests by helpManager.activeRequests.collectAsState()
    val myCommunities by CommunityMembershipStore.get().myCommunities.collectAsState()
    val activeRideCode by ActiveRideTracker.get().activeRideCode.collectAsState()
    val voiceConnected by com.karthik.packride.voice.VoiceChatManager.get().isConnected.collectAsState()
    val myCommunityIds = myCommunities.map { it.id }.toSet()
    val relevantHelpRequests = allHelpRequests.relevantTo(
        helpManager.myUID, helpManager.myDeviceID, myCommunityIds, activeRideCode
    )
    var showHelpAlerts by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        Scaffold(
            // Aug 30, 2026 — full-bleed fix: Scaffold's default contentWindowInsets
            // auto-pads every screen clear of the status/nav bars, which is why
            // nothing could bleed under the status bar like iOS's hero cards do
            // (enableEdgeToEdge() in MainActivity lets the window draw there, but
            // Scaffold was immediately re-padding it away). Zeroed out here so each
            // screen controls its own insets instead — Home's hero card ignores
            // the top inset entirely (background bleeds under the status bar,
            // only its header row pads for it), every other screen still gets
            // statusBarsPadding() below so nothing regresses. PrTabBar handles its
            // own bottom inset via navigationBarsPadding().
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                // Hidden during turn-by-turn guidance — full-screen, no tab chrome,
                // matching iOS's fullScreenCover presentation.
                if (current != Dest.TurnByTurn.route && current != Dest.MotoRun.route) {
                    PrTabBar(
                        tabs = tabs,
                        current = current,
                        groupVoiceConnected = voiceConnected,
                        onSelect = { dest ->
                            // A selected iOS tab is a no-op. Previously this
                            // still ran popUpTo+navigate, tearing down and
                            // recreating Profile/Group (and their Firebase,
                            // AdMob, permissions, map, and Agora lifecycles).
                            // Repeated/rapid taps could overlap disposal with
                            // initialization and was the common path behind
                            // intermittent tab-entry process exits.
                            if (current == dest.route) return@PrTabBar
                            navController.navigate(dest.route) {
                                // These are flat iOS-style tabs. Dispose the old
                                // destination instead of retaining heavyweight
                                // maps/Firebase listeners in saved back stacks.
                                popUpTo(navController.graph.findStartDestination().id)
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        ) { padding ->
            // Home keeps its hero card full-bleed under the status bar (no top
            // inset); every other screen gets it back via statusBarsPadding()
            // so existing layouts don't suddenly render under the status bar.
            val contentModifier = Modifier.padding(padding).let {
                if (current == Dest.Home.route || current == Dest.Profile.route) it else it.statusBarsPadding()
            }
            // Track Mode is a gesture-heavy full-screen map. Horizontal tab
            // swiping here stole drags intended for the map/start-finish gate
            // and could navigate straight to Profile. Match iOS by disabling
            // the app-level tab swipe only on Track; bottom-tab taps remain.
            val navigationModifier = if (current == Dest.Track.route) {
                contentModifier
            } else {
                contentModifier.pointerInput(current) {
                    val swipeThreshold = 70.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var movement = Offset.Zero
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            movement += change.positionChange()
                            pressed = change.pressed
                        }
                        if (abs(movement.x) > swipeThreshold && abs(movement.x) > abs(movement.y) * 1.5f) {
                            val index = tabs.indexOfFirst { it.route == current }
                            val target = index + if (movement.x < 0) 1 else -1
                            tabs.getOrNull(target)?.let { dest ->
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    launchSingleTop = true
                                }
                            }
                        }
                    }
                }
            }
            NavHost(
                navController = navController,
                startDestination = Dest.Home.route,
                modifier = navigationModifier,
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(200)) }
            ) {
                composable(Dest.Home.route) {
                    HomeScreen(auth) { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
                composable(Dest.Feed.route) { FeedScreen(auth) }
                composable(Dest.Group.route) { GroupRideScreen(auth) }
                composable(Dest.Track.route) {
                    TrackModeScreen(
                        onOpenCompare = { navController.navigate(Dest.LapCompare.route) },
                        onOpenTrends = { navController.navigate(Dest.LapTrends.route) },
                        onOpenReplay = { navController.navigate(Dest.Replay.route) }
                    )
                }
                composable(Dest.Profile.route) {
                    ProfileScreen(
                        auth = auth,
                        onOpenGarage = { navController.navigate(Dest.Garage.route) },
                        onOpenSafety = {
                            PendingSafetyTab.request(0)
                            navController.navigate(Dest.Safety.route) { launchSingleTop = true }
                        },
                        onOpenNotifications = { navController.navigate(Dest.Notifications.route) },
                        onOpenPrivacy = { navController.navigate(Dest.Privacy.route) }
                    )
                }
                composable(Dest.Privacy.route) {
                    PrivacyDataScreen(onDone = { navController.popBackStack() })
                }
                composable(Dest.SoloRide.route) { SoloRideScreen(auth) }
                composable(Dest.Safety.route) { SafetyHubScreen(auth) }
                composable(Dest.Friends.route) { FriendsScreen(auth) }
                composable(Dest.Community.route) {
                    CompositionLocalProvider(LocalPrBackAction provides { navController.popBackStack() }) { CommunityScreen(auth) }
                }
                composable(Dest.History.route) {
                    CompositionLocalProvider(LocalPrBackAction provides { navController.popBackStack() }) {
                        RideHistoryScreen(auth, onOpenReplay = { navController.navigate(Dest.Replay.route) })
                    }
                }
                composable(Dest.Replay.route) {
                    CompositionLocalProvider(LocalPrBackAction provides { navController.popBackStack() }) { ReplayScreen() }
                }
                composable(Dest.Garage.route) {
                    CompositionLocalProvider(LocalPrBackAction provides { navController.popBackStack() }) { GarageScreen() }
                }
                composable(Dest.Schedule.route) {
                    CompositionLocalProvider(LocalPrBackAction provides { navController.popBackStack() }) { ScheduleRideScreen(auth) }
                }
                composable(Dest.Invites.route) { InvitesScreen(auth) }
                composable(Dest.Notifications.route) {
                    NotificationsScreen(onDone = { navController.popBackStack() })
                }
                composable(Dest.Badges.route) {
                    CompositionLocalProvider(LocalPrBackAction provides { navController.popBackStack() }) { BadgesScreen() }
                }
                composable(Dest.Weather.route) {
                    CompositionLocalProvider(LocalPrBackAction provides { navController.popBackStack() }) { WeatherScreen() }
                }
                composable(Dest.Analytics.route) { AnalyticsScreen() }
                composable(Dest.LapCompare.route) {
                    CompositionLocalProvider(LocalPrBackAction provides { navController.popBackStack() }) { LapCompareScreen() }
                }
                composable(Dest.LapTrends.route) {
                    CompositionLocalProvider(LocalPrBackAction provides { navController.popBackStack() }) { LapTrendsScreen() }
                }
                composable(Dest.TurnByTurn.route) {
                    TurnByTurnScreen(auth) { navController.popBackStack() }
                }
                composable(Dest.Waypoints.route) { WaypointsScreen(auth) }
                composable(Dest.MotoRun.route) { MotoRunScreen(onExit = { navController.popBackStack() }) }
                composable(Dest.Digest.route) {
                    CompositionLocalProvider(LocalPrBackAction provides { navController.popBackStack() }) { DigestScreen() }
                }
                composable(Dest.RideComms.route) { VoiceChannelScreen(auth) { navController.popBackStack() } }
            }
        }

        // Positioned above the Scaffold (same as iOS's VStack sitting above the
        // tab ZStack) so it survives whichever tab is showing underneath, and
        // hidden during turn-by-turn guidance so it never covers the nav card.
        if (relevantHelpRequests.isNotEmpty() && current != Dest.TurnByTurn.route && current != Dest.MotoRun.route) {
            IncomingHelpBanner(
                count = relevantHelpRequests.size,
                onClick = { showHelpAlerts = true },
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }

    if (showHelpAlerts) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { showHelpAlerts = false }, sheetState = sheetState) {
            IncomingHelpAlertsContent(
                requests = relevantHelpRequests,
                onNavigate = { req ->
                    showHelpAlerts = false
                    PendingTurnByTurn.request(req.latitude, req.longitude, req.requesterName)
                }
            )
        }
    }
}

@Composable
private fun IncomingHelpBanner(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFD33B2C)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
            Text(
                if (count == 1) "1 rider needs help — tap to view" else "$count riders need help — tap to view",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun IncomingHelpAlertsContent(requests: List<HelpRequest>, onNavigate: (HelpRequest) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("Riders Needing Help", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(requests, key = { it.id }) { req ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${req.requesterName} needs help", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Shared with ${req.targetName}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(
                            onClick = { onNavigate(req) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Navigate to Them") }
                    }
                }
            }
        }
    }
}

/**
 * Custom bottom tab bar — port of iOS ContentView's BottomTab row. Always a
 * dark charcoal background (Pr.tabBar) regardless of light/dark app theme,
 * a subtle top hairline, coral icon+label when active vs. muted-white when
 * not — replacing the stock Material3 NavigationBar (which followed the
 * app's light/dark ColorScheme and looked like a generic Android widget
 * instead of iOS's fixed dark chrome).
 */
@Composable
private fun PrTabBar(
    tabs: List<Dest>,
    current: String?,
    groupVoiceConnected: Boolean,
    onSelect: (Dest) -> Unit
) {
    Column {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.08f)))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Pr.tabBar)
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 14.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEach { dest ->
                val active = current == dest.route
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable(onClick = { onSelect(dest) })
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Box {
                        Icon(
                            dest.icon,
                            contentDescription = dest.label,
                            tint = if (active) Pr.coral else Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.height(24.dp)
                        )
                        if (dest == Dest.Group && groupVoiceConnected) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(8.dp)
                                    .background(Color(0xFF2E9E5B), androidx.compose.foundation.shape.CircleShape)
                            )
                        }
                    }
                    Text(
                        dest.label,
                        color = if (active) Pr.coral else Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
