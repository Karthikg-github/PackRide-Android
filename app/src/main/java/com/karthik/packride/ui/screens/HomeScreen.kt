package com.karthik.packride.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.karthik.packride.auth.AuthManager
import com.karthik.packride.auth.rideInitials
import com.karthik.packride.friends.FriendsManager
import com.karthik.packride.location.SharedLocationManager
import com.karthik.packride.nav.PendingMotoRun
import com.karthik.packride.nav.PendingSafetyTab
import com.karthik.packride.nav.PendingWaypoints
import com.karthik.packride.profile.ProfileImageResolver
import com.karthik.packride.ride.RideHistoryManager
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrAvatar
import com.karthik.packride.ui.theme.PrCoral
import com.karthik.packride.weather.WeatherManager
import com.karthik.packride.weather.WeatherSnapshot
import java.util.Locale

// Aug 31, 2026 — real 1:1 port of iOS's HomeView (ContentView.swift, lines
// 222-561), replacing the Aug 30 first pass (hero card + 4 big Ride/Pack/
// Safety/You category tiles), which was flagged at the time as "a real first
// pass at parity", not the genuine article — Karthik confirmed via
// side-by-side screenshots that it still didn't match. This version matches
// the real iOS source structure, copy, icons and behavior:
//  - hero card: route illustration (Canvas — iOS's SwiftUI Path port), a
//    real live weather badge (WeatherManager + SharedLocationManager, same
//    pattern WeatherScreen.kt already uses), the exact greeting/stat/top-speed
//    copy, and a flat coral "Record Ride" bar wired to the Ride tab (the
//    existing SoloRideScreen — same recording flow, not a second one).
//  - below the hero: iOS's exact flat sectioned row list (Ride / The Pack /
//    You) with the exact titles, subtitles and icons from HomeView, replacing
//    the old 4-tile grid. Rows that don't have a top-level PackRideNav route
//    (Plan Route, Track Mode, Friends Nearby, Communities, Ride History all
//    live nested inside MoreScreen's own tab strip — see MoreScreen.kt) reach
//    their destination via nav/PendingMoreTab.kt, the same "pending target"
//    bridge pattern already used by PendingGroupRide/PendingCommunityJoin/
//    PendingTurnByTurn for exactly this shape of problem.
//  - header row: added the bell icon iOS has (previously missing entirely).
//    iOS's bell opens a real NotificationCenterView sheet backed by a
//    follow-request count (UserProfileManager.followRequests) — Android has
//    no such backing at all yet (confirmed: no follow-request concept
//    anywhere in this codebase, flagged in three separate places by the Aug
//    30 pass). Building that whole feature is out of scope for a Home-screen
//    parity fix, so the icon is here for visual parity but is deliberately a
//    no-op for now — flagged, not silently invented. The warning-triangle
//    icon now opens Need Help directly (nav/PendingSafetyTab.kt), matching
//    iOS's fullScreenCover behavior more precisely than the old plain
//    switchTab("safety") (which landed on SafetyHubScreen's Crash sub-tab).
@Composable
fun HomeScreen(auth: AuthManager, switchTab: (String) -> Unit = {}) {
    val context = LocalContext.current
    val history = remember { RideHistoryManager(context) }
    val rides by history.rides.collectAsState()
    val friendsManager = remember { FriendsManager(context) }
    val followRequests by friendsManager.followRequests.collectAsState()

    LaunchedEffect(Unit) { friendsManager.start() }
    DisposableEffect(friendsManager) { onDispose { friendsManager.stop() } }

    val name = auth.prefsSnapshot.riderName
    var avatarUrl by remember(auth.uid) { mutableStateOf(auth.prefsSnapshot.avatarURL) }
    LaunchedEffect(auth.uid) {
        val uid = auth.uid ?: return@LaunchedEffect
        val images = ProfileImageResolver.resolve(uid)
        if (images.avatarUrl.isNotBlank()) {
            avatarUrl = images.avatarUrl
            auth.prefsSnapshot.avatarURL = images.avatarUrl
        }
        if (images.bannerUrl.isNotBlank()) auth.prefsSnapshot.bannerURL = images.bannerUrl
    }
    val firstName = name.trim().split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: "Rider"
    val initials = name.rideInitials()
    val bestSpeed = rides.maxOfOrNull { it.maxSpeedMph } ?: 0.0
    val totalMiles = rides.sumOf { it.distanceMiles }

    val motoRunHighScore = remember {
        context.getSharedPreferences("packride_prefs", android.content.Context.MODE_PRIVATE)
            .getInt("motoRunHighScore", 0)
    }

    Column(Modifier.fillMaxSize().background(Pr.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            heroCard(
                firstName = firstName,
                initials = initials,
                avatarUrl = avatarUrl,
                bestSpeedMph = bestSpeed,
                rideCount = rides.size,
                totalMiles = totalMiles,
                motoRunHighScore = motoRunHighScore,
                onAvatarClick = { switchTab("profile") },
                onNotificationsClick = { switchTab("notifications") },
                notificationCount = followRequests.size,
                onNeedHelpClick = { PendingSafetyTab.request(1) },
                onMotoRunClick = { PendingMotoRun.request() },
                onRecordRide = { switchTab("soloRide") }
            )

            // MARK: - Sections (flat web list, port of iOS's rideSection/packSection/youSection)
            homeSectionLabel("Ride")
            Column(Modifier.fillMaxWidth().background(Pr.cardBg)) {
                homeFeatureRow(
                    icon = Icons.Filled.Map,
                    title = "Plan Route",
                    subtitle = "Build a curvy road and navigate",
                    onClick = { PendingWaypoints.request() }
                )
                homeDivider()
                homeFeatureRow(
                    icon = Icons.Filled.Flag,
                    title = "Track Mode",
                    subtitle = "Automatic lap timing with GPS",
                    onClick = { switchTab("track") }
                )
            }

            homeSectionLabel("The Pack")
            Column(Modifier.fillMaxWidth().background(Pr.cardBg)) {
                homeFeatureRow(
                    icon = Icons.Filled.Groups,
                    title = "Group Ride",
                    subtitle = "Ride together, live on the map",
                    onClick = { switchTab("group") }
                )
                homeDivider()
                homeFeatureRow(
                    icon = Icons.Filled.HeadsetMic,
                    title = "Ride Comms",
                    subtitle = "Private voice with any rider",
                    onClick = { switchTab("rideComms") }
                )
                homeDivider()
                homeFeatureRow(
                    icon = Icons.Filled.LocationOn,
                    title = "Friends Nearby",
                    subtitle = "See who's out riding",
                    onClick = { switchTab("friends") }
                )
                homeDivider()
                homeFeatureRow(
                    icon = Icons.Filled.Group,
                    title = "Communities",
                    subtitle = "Find your local riders",
                    onClick = { switchTab("community") }
                )
            }

            homeSectionLabel("You")
            Column(Modifier.fillMaxWidth().background(Pr.cardBg)) {
                homeFeatureRow(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "Ride History",
                    subtitle = "Maps, stats, and replays",
                    onClick = { switchTab("history") }
                )
                homeDivider()
                homeFeatureRow(
                    icon = Icons.Filled.Shield,
                    title = "Safety",
                    subtitle = "Crash detection & alerts",
                    onClick = { switchTab("safety") }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// MARK: - Hero card (full-bleed cover, port of iOS's webHero)
@Composable
private fun heroCard(
    firstName: String,
    initials: String,
    avatarUrl: String,
    bestSpeedMph: Double,
    rideCount: Int,
    totalMiles: Double,
    motoRunHighScore: Int,
    onAvatarClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    notificationCount: Int,
    onNeedHelpClick: () -> Unit,
    onMotoRunClick: () -> Unit,
    onRecordRide: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(312.dp)
            .background(Pr.cover)
    ) {
        HeroRouteIllustration(modifier = Modifier.fillMaxSize().alpha(0.9f))
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.55f))
                    )
                )
        )

        // Live weather badge, upper-right — port of iOS's WeatherStrip, real
        // WeatherManager + SharedLocationManager data (not stubbed). Top
        // padding of 56dp (matches iOS's own `.padding(.top, 56)`) clears the
        // header row above it (avatar/bell/warning row sits ~50dp tall).
        HeroWeatherBadge(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 56.dp, end = 20.dp)
        )

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Aug 31, 2026 — hidden Moto Run easter egg, matching iOS's
                // ContentView.swift exactly (`.onTapGesture { showMotoRun = true }`
                // on this same wordmark, ~line 387) — no visible affordance on
                // either platform, deliberately.
                Text(
                    "PACKRIDE",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.4.sp,
                    modifier = Modifier.clickable(onClick = onMotoRunClick)
                )
                Spacer(Modifier.weight(1f))
                HeroBellButton(count = notificationCount, onClick = onNotificationsClick)
                Spacer(Modifier.width(12.dp))
                HeroIconButton(icon = Icons.Filled.Warning, contentDescription = "Need Help", onClick = onNeedHelpClick)
                Spacer(Modifier.width(12.dp))
                Box(modifier = Modifier.clickable(onClick = onAvatarClick)) {
                    PrAvatar(initials = initials, size = 34.dp, photoUrl = avatarUrl)
                }
            }

            Spacer(Modifier.weight(1f))

            Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                Text("Hey $firstName", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    heroStatText(rideCount, totalMiles),
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Top speed ever: ${com.karthik.packride.data.MeasurementUnits.speedMph(bestSpeedMph)}",
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (motoRunHighScore > 0) {
                    Text(
                        "Moto Run best: $motoRunHighScore",
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Record Ride — a flat coral bar (iOS applies no corner radius
            // here, unlike PrimaryButton elsewhere), wired to the app's
            // existing Ride tab / SoloRideScreen recording flow rather than a
            // second, parallel entry point.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(PrCoral)
                    .clickable(onClick = onRecordRide)
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Record Ride", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun heroStatText(rideCount: Int, totalMiles: Double): String {
    if (rideCount == 0) return "Ready for your first ride"
    return "$rideCount rides  ·  ${com.karthik.packride.data.MeasurementUnits.distanceMiles(totalMiles, 0)} on the road"
}

@Composable
private fun HeroIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(15.dp))
    }
}

// A bell glyph, hand-drawn on a Canvas rather than a Material icon — no
// bell/notification icon is confirmed to compile anywhere in this codebase
// (grepped the whole app; material-icons-extended is on the classpath but no
// call site here has ever proven a bell name against it), so per the same
// "draw it instead of risking an unconfirmed icon name" rule this codebase
// already follows in MotoRunScreen.kt, this is a small dome + base + clapper
// shape rather than a guessed `Icons.Filled.Notifications`.
@Composable
private fun HeroBellButton(count: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(15.dp)) {
            val w = size.width
            val h = size.height
            val body = Path().apply {
                moveTo(w * 0.5f, 0f)
                cubicTo(w * 0.14f, h * 0.05f, w * 0.10f, h * 0.48f, w * 0.02f, h * 0.72f)
                lineTo(w * 0.98f, h * 0.72f)
                cubicTo(w * 0.90f, h * 0.48f, w * 0.86f, h * 0.05f, w * 0.5f, 0f)
                close()
            }
            drawPath(body, Color.White)
            drawRect(Color.White, topLeft = Offset(0f, h * 0.74f), size = Size(w, h * 0.09f))
            drawCircle(Color.White, radius = h * 0.09f, center = Offset(w * 0.5f, h * 0.95f))
        }
        if (count > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).size(15.dp).clip(CircleShape).background(Pr.coral),
                contentAlignment = Alignment.Center
            ) {
                Text(if (count > 9) "9+" else "$count", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// MARK: - Live weather badge (port of iOS's WeatherStrip)
// Reuses the exact permission/fetch pattern WeatherScreen.kt already
// established (SharedLocationManager.REASON_WEATHER + WeatherManager.fetch +
// Geocoder reverse-lookup for the city name) rather than inventing a second
// weather stack. Deliberately does NOT copy iOS's Apple/WeatherKit
// attribution link — that requirement is specific to WeatherKit, which
// Android doesn't use (WeatherManager.kt is Open-Meteo backed); a small
// "Weather · Open-Meteo" caption stands in its place, which is both accurate
// and keeps a "Weather" label under the badge for visual parity.
private val HERO_WEATHER_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

@Composable
private fun HeroWeatherBadge(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val locMgr = remember { SharedLocationManager.get() }
    val location by locMgr.location.collectAsState()

    var snap by remember { mutableStateOf<WeatherSnapshot?>(null) }
    var locationName by remember { mutableStateOf("") }

    val hasPermission = HERO_WEATHER_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) locMgr.onPermissionGranted()
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(HERO_WEATHER_PERMISSIONS)
        locMgr.startUpdating(SharedLocationManager.REASON_WEATHER)
    }
    DisposableEffect(Unit) {
        onDispose { locMgr.stopUpdating(SharedLocationManager.REASON_WEATHER) }
    }

    // Fetch once per fresh GPS fix, mirrors iOS's "if currentWeather == nil" guard.
    LaunchedEffect(location) {
        val loc = location ?: return@LaunchedEffect
        if (snap != null) return@LaunchedEffect

        @Suppress("DEPRECATION")
        val resolvedName = runCatching {
            Geocoder(context, Locale.getDefault())
                .getFromLocation(loc.latitude, loc.longitude, 1)
                ?.firstOrNull()?.locality
        }.getOrNull()
        locationName = resolvedName ?: ""
        snap = WeatherManager.fetch(loc)
    }

    val current = snap
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        if (current != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Same rule as WeatherScreen.kt: only "Clear" has a confirmed
                // matching icon (WbSunny) anywhere in this codebase, so every
                // other condition shows no icon rather than a guessed one.
                if (current.condition == "Clear") {
                    Icon(Icons.Filled.WbSunny, contentDescription = null, tint = Color(0xFFFFBF33), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Column(horizontalAlignment = Alignment.Start) {
                    Text(com.karthik.packride.data.MeasurementUnits.temperatureF(current.tempF), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (locationName.isNotBlank()) "$locationName · ${current.condition}" else current.condition,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Weather · Open-Meteo", color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    if (hasPermission) "Loading weather..." else "Enable location for weather",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// MARK: - Hero Route Illustration (port of iOS's HeroRouteIllustration)
// A gentle S-curve road with a small motorcycle marker, drawn straight on a
// Canvas — the same hand-drawn-vehicle-art approach MotoRunScreen.kt already
// uses (drawPath/drawLine/drawOval/drawCircle) rather than a MapKit-style
// real map, matching iOS's reasoning exactly (no single "current route" to
// show on Home). The marker sits at a fixed point along the curve rather
// than iOS's continuous TimelineView loop — kept static to limit risk in an
// environment with no compiler to verify an animation loop against; still a
// faithful "road with a motorcycle marker on it" illustration.
@Composable
private fun HeroRouteIllustration(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "home-hero-motorcycle")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "route-progress"
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val p0 = Offset(-10f, h * 0.72f)
        val c1 = Offset(w * 0.08f, h * 0.95f)
        val c2 = Offset(w * 0.22f, h * 0.05f)
        val p1 = Offset(w * 0.42f, h * 0.22f)
        val c3 = Offset(w * 0.66f, h * 0.42f)
        val c4 = Offset(w * 0.88f, h * 0.88f)
        val p2 = Offset(w + 10f, h * 0.58f)

        val road = Path().apply {
            moveTo(p0.x, p0.y)
            cubicTo(c1.x, c1.y, c2.x, c2.y, p1.x, p1.y)
            cubicTo(c3.x, c3.y, c4.x, c4.y, p2.x, p2.y)
        }

        drawPath(road, Color.White.copy(alpha = 0.16f), style = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Round))
        drawPath(
            road,
            Color.White.copy(alpha = 0.55f),
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 9.dp.toPx()))
            )
        )

        // Match iOS TimelineView: continuously traverse both connected
        // cubic segments and restart every 5.5 seconds.
        val onFirstSegment = progress < 0.5f
        val t = if (onFirstSegment) progress * 2f else (progress - 0.5f) * 2f
        val mt = 1f - t
        val a = if (onFirstSegment) p0 else p1
        val b = if (onFirstSegment) c1 else c3
        val c = if (onFirstSegment) c2 else c4
        val d = if (onFirstSegment) p1 else p2
        val markerX = mt * mt * mt * a.x + 3 * mt * mt * t * b.x + 3 * mt * t * t * c.x + t * t * t * d.x
        val markerY = mt * mt * mt * a.y + 3 * mt * mt * t * b.y + 3 * mt * t * t * c.y + t * t * t * d.y
        val marker = Offset(markerX, markerY)

        drawCircle(Color.White, radius = 14.dp.toPx(), center = marker)
        val bikeW = 16.dp.toPx()
        val bikeH = 9.dp.toPx()
        val wheelR = 2.6.dp.toPx()
        drawOval(PrCoral, topLeft = Offset(marker.x - bikeW / 2, marker.y - bikeH / 2), size = Size(bikeW, bikeH))
        drawCircle(Color(0xFF1A1A1A), radius = wheelR, center = Offset(marker.x - bikeW * 0.28f, marker.y + bikeH * 0.32f))
        drawCircle(Color(0xFF1A1A1A), radius = wheelR, center = Offset(marker.x + bikeW * 0.28f, marker.y + bikeH * 0.32f))
    }
}

// MARK: - Section label (port of iOS's HomeSectionLabel — not uppercased)
@Composable
private fun homeSectionLabel(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
    ) {
        Text(title, color = Pr.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// MARK: - Divider inset under the row text, not the icon (port of iOS's HomeDivider)
@Composable
private fun homeDivider() {
    Box(
        Modifier
            .padding(start = 56.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Pr.border)
    )
}

// MARK: - Flat feature row (port of iOS's HomeWebRow)
@Composable
private fun homeFeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
            Icon(icon, contentDescription = null, tint = Pr.coral, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Pr.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Pr.muted, fontSize = 12.sp, maxLines = 1)
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Pr.muted.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
    }
}
