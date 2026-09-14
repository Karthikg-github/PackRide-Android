package com.karthik.packride.ui.screens

import android.Manifest
import android.location.Location
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.karthik.packride.auth.AuthManager
import com.karthik.packride.group.GroupRideSession
import com.karthik.packride.group.PendingGroupRide
import com.karthik.packride.nav.PendingMoreTab
import com.karthik.packride.nav.PendingWaypoints
import com.karthik.packride.schedule.ScheduledRideManager
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrCoral
import com.karthik.packride.ui.theme.PrFont
import com.karthik.packride.ui.theme.PrPageHeader
import com.karthik.packride.ui.theme.PrMapStylePicker
import com.karthik.packride.voice.VoiceChatManager
import com.karthik.packride.waypoints.GroupWaypointSync
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Aug 31, 2026 — Group Ride visual parity pass. The landing (no active ride)
// state was still on stock Material3 defaults from before ui/theme/DesignSystem.kt
// existed: dynamic-theme dark background, default purple/yellow Button
// colors, and a leftover dev-note subtitle ("...same rooms as iOS ... over
// Firebase") that was never meant to ship. Rebuilt onto Pr/PrFont/PrPageHeader
// per the real GroupRideView.swift: page header ("THE PACK" / "Group Ride"),
// a Create New Ride row, a Join-by-code row (# prefix, coral Join button),
// and the two sections that were missing entirely —
//  - Schedule a Ride: bridges to ScheduleRideScreen (MoreScreen sub-tab 7)
//    via the same nav/PendingMoreTab.kt StateFlow HomeScreen's feature rows
//    already use to reach MoreScreen sub-tabs without a navController
//    threaded down into this screen.
//  - Upcoming Rides: backed by schedule/ScheduledRideManager's new
//    listenForUpcomingRides()/communityRides (ported from iOS's
//    ScheduledRideManager.swift — see that file's own header for why it
//    wasn't added until this pass).
// The active-ride (already in a room) map view below is intentionally left
// as-is — this pass is scoped to the landing screen the bug report's
// screenshots showed; the in-ride map HUD wasn't part of that report.
@Composable
fun GroupRideScreen(auth: AuthManager) {
    val context = LocalContext.current
    val session = remember { GroupRideSession.get(context) }
    val scheduleManager = remember { ScheduledRideManager(context) }

    val active by session.isActive.collectAsState()
    val inRoom by session.inRoom.collectAsState()
    val code by session.rideCode.collectAsState()
    val isLeader by session.isLeader.collectAsState()
    val riders by session.groupRiders.collectAsState()
    val elapsed by session.elapsedSeconds.collectAsState()
    val speed by session.speedMph.collectAsState()
    val distance by session.distanceMiles.collectAsState()
    val maxSpeed by session.maxSpeedMph.collectAsState()
    val location by session.location.collectAsState()
    val summary by session.summary.collectAsState()
    val upcomingRides by scheduleManager.communityRides.collectAsState()

    var joinInput by remember { mutableStateOf("") }
    var confirmEnd by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf(MapType.HYBRID) }
    var waypointCount by remember { mutableStateOf(0) }
    var showNoWaypoints by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    fun ensurePermissions(): Boolean {
        val need = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) need += Manifest.permission.POST_NOTIFICATIONS
        val missing = need.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
            return false
        }
        return true
    }

    // Aug 31, 2026 — Voice chat (group ride intercom). App-wide singleton
    // (see VoiceChatManager.kt) so the Agora engine/channel survives
    // navigating away from this screen while a ride is still active.
    val voiceChat = remember { VoiceChatManager.get() }
    val voiceConnected by voiceChat.isConnected.collectAsState()
    val voiceMuted by voiceChat.isMuted.collectAsState()
    val voiceConnecting by voiceChat.isConnecting.collectAsState()
    val voiceError by voiceChat.connectionError.collectAsState()
    val voiceAudioRoute by voiceChat.audioRoute.collectAsState()
    val voiceSpeakerEnabled by voiceChat.speakerEnabled.collectAsState()

    // Deliberately opt-in — first tap asks for RECORD_AUDIO (a system
    // prompt, not something to pre-empt) and joins; a separate launcher
    // from ensurePermissions() above since mic access is only ever needed
    // once a rider actually taps the voice button, not on screen entry.
    val voicePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) voiceChat.join(code) }

    fun joinVoiceChat() {
        val hasMic = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            voiceChat.join(code)
        } else {
            voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Ride ended — don't leave a mic connected to a channel that's over.
    // Android equivalent of iOS's .onChange(of: activeRideCode) going empty.
    LaunchedEffect(inRoom) {
        if (!inRoom) voiceChat.leave()
    }

    LaunchedEffect(Unit) { ensurePermissions() }
    LaunchedEffect(Unit) { scheduleManager.listenForUpcomingRides() }
    DisposableEffect(Unit) {
        onDispose {
            // The room, recording session and voice intercom are app-wide;
            // switching tabs must not tear them down.
            scheduleManager.stop()
        }
    }

    DisposableEffect(code, inRoom) {
        val handle = if (inRoom) GroupWaypointSync.listen(code) { points ->
            waypointCount = points.count { !it.isStartOverride }
        } else null
        onDispose { GroupWaypointSync.stopListening(code, handle) }
    }

    // Aug 30, 2026 — consumes a route just planned in Plan Route's "Group
    // Ride" choice (see PendingGroupRide.kt / WaypointsScreen), auto-joining
    // or auto-creating with the SAME code the route was already published
    // under instead of leaving the rider staring at the manual create/join
    // form for a route they just finished planning.
    LaunchedEffect(Unit) {
        val request = PendingGroupRide.pending.value ?: return@LaunchedEffect
        PendingGroupRide.clear()
        if (request.isLeader) {
            session.createRoom(
                auth.prefsSnapshot.riderName,
                auth.prefsSnapshot.avatarURL,
                presetCode = request.code
            )
        } else {
            session.joinRoom(request.code, auth.prefsSnapshot.riderName, auth.prefsSnapshot.avatarURL)
        }
    }

    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(37.33, -122.03), 13f)
    }
    LaunchedEffect(location?.latitude, location?.longitude) {
        val loc = location ?: return@LaunchedEffect
        cameraState.animate(
            CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 14f)
        )
    }

    val hasFine = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!inRoom) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Pr.bg)
                .verticalScroll(rememberScrollState())
        ) {
            PrPageHeader(
                eyebrow = "THE PACK",
                title = "Group Ride",
                subtitle = "Create a code and invite your pack, or join one already in progress"
            )

            // Create New Ride
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(Pr.RadiusCard))
                    .background(Pr.cardBg)
                    .border(1.dp, Pr.border, RoundedCornerShape(Pr.RadiusCard))
                    .clickable(onClick = {
                        session.createRoom(auth.prefsSnapshot.riderName, auth.prefsSnapshot.avatarURL)
                    })
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(46.dp).clip(CircleShape).background(Pr.inkFixed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Create New Ride", style = PrFont.subheading)
                        Text("Generate a code and invite your pack", style = PrFont.caption)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Pr.muted, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // Or join an existing ride
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(Pr.RadiusCard))
                    .background(Pr.cardBg)
                    .border(1.dp, Pr.border, RoundedCornerShape(Pr.RadiusCard))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Or join an existing ride",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Pr.muted)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Pr.fieldBg)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "#",
                                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Pr.coral)
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f)) {
                                if (joinInput.isEmpty()) {
                                    Text(
                                        "Enter ride code",
                                        style = TextStyle(
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = FontFamily.Monospace,
                                            color = Pr.muted
                                        )
                                    )
                                }
                                BasicTextField(
                                    value = joinInput,
                                    onValueChange = { joinInput = it.uppercase() },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace,
                                        color = Pr.ink
                                    ),
                                    cursorBrush = SolidColor(Pr.coral)
                                )
                            }
                        }

                        val canJoin = joinInput.length >= 4
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (canJoin) Pr.coral else Pr.coral.copy(alpha = 0.4f))
                                .clickable(
                                    enabled = canJoin,
                                    onClick = {
                                        session.joinRoom(
                                            joinInput,
                                            auth.prefsSnapshot.riderName,
                                            auth.prefsSnapshot.avatarURL
                                        )
                                        joinInput = ""
                                    }
                                )
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Text(
                                "Join",
                                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Schedule a Ride
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(Pr.RadiusCard))
                    .background(Pr.cardBg)
                    .border(1.dp, Pr.border, RoundedCornerShape(Pr.RadiusCard))
                    .clickable(onClick = { PendingMoreTab.request(7) })
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(46.dp).clip(CircleShape).background(Pr.teal),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Schedule a Ride", style = PrFont.subheading)
                        Text("Plan a future ride and invite your pack", style = PrFont.caption)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Pr.muted, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Upcoming Rides
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(Pr.RadiusCard))
                    .background(Pr.cardBg)
                    .border(1.dp, Pr.border, RoundedCornerShape(Pr.RadiusCard))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Pr.teal, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Upcoming Rides", style = PrFont.subheading)
                        Spacer(Modifier.weight(1f))
                        if (upcomingRides.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GrTealSoft)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    "${upcomingRides.size}",
                                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Pr.teal)
                                )
                            }
                        }
                    }

                    if (upcomingRides.isEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Pr.muted, modifier = Modifier.size(16.dp))
                            Text("No upcoming rides scheduled", style = PrFont.caption)
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Pr.fieldBg)
                        ) {
                            upcomingRides.take(3).forEachIndexed { index, ride ->
                                if (index > 0) {
                                    Box(Modifier.fillMaxWidth().height(1.dp).background(Pr.border))
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = { PendingMoreTab.request(7) })
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            grMonthLabel(ride.scheduledDate),
                                            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Pr.teal)
                                        )
                                        Text(
                                            "${grDayLabel(ride.scheduledDate)}",
                                            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Pr.ink)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            ride.title,
                                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Pr.ink),
                                            maxLines = 1
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(grTimeLabel(ride.scheduledDate), style = PrFont.caption)
                                            Text("by ${ride.creatorName}", style = PrFont.caption)
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "${ride.rsvpCount}",
                                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GrGoingGreen)
                                        )
                                        Text(
                                            "going",
                                            style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium, color = GrGoingGreen)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Pr.muted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(36.dp))
        }
        return
    }

    if (!active) {
        Column(Modifier.fillMaxSize().background(Pr.bg).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().background(Pr.inkFixed).padding(horizontal = 20.dp, vertical = 24.dp)) {
                Column {
                    Text("GROUP RIDE ROOM", style = PrFont.sectionHeader, color = PrCoral)
                    Text(
                        "#$code",
                        style = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = Color.White)
                    )
                    Text(
                        if (isLeader) "You’re the leader · invite your pack and plan the route"
                        else "Waiting for the leader to start the ride",
                        color = Color.White.copy(alpha = .72f), fontSize = 13.sp
                    )
                }
            }

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${riders.size + 1} rider${if (riders.isEmpty()) "" else "s"} in the room", style = PrFont.subheading)
                if (riders.isEmpty()) Text("Share the code to invite your pack.", style = PrFont.caption)
                riders.forEach { rider ->
                    Text("${rider.initials}  ${rider.name}${if (rider.isLeader) " · Leader" else ""}", style = PrFont.body)
                }

                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Join my PackRide group: $code\npackride://join?code=$code")
                        }
                        context.startActivity(Intent.createChooser(send, "Share ride code"))
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text("Share Ride Code") }

                if (isLeader) {
                    OutlinedButton(
                        onClick = { PendingWaypoints.request(code, isLeader = true) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (waypointCount > 0) "Edit Waypoints ($waypointCount)" else "Add Waypoints") }
                } else if (waypointCount > 0) {
                    Text("$waypointCount route point${if (waypointCount == 1) "" else "s"} planned", style = PrFont.caption)
                }

                Button(
                    onClick = {
                        if (!ensurePermissions()) return@Button
                        if (waypointCount == 0) showNoWaypoints = true else session.startRide()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Pr.inkFixed)
                ) { Text("START RIDE", color = Color.White) }

                TextButton(onClick = { session.leaveRoom() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Leave Room", color = Pr.muted)
                }
            }
        }

        if (showNoWaypoints) {
            AlertDialog(
                onDismissRequest = { showNoWaypoints = false },
                title = { Text("No waypoints planned") },
                text = { Text("Set waypoints before starting, or start without a route.") },
                confirmButton = {
                    TextButton(onClick = { showNoWaypoints = false; session.startRide() }) { Text("Start Without Route") }
                },
                dismissButton = {
                    if (isLeader) TextButton(onClick = {
                        showNoWaypoints = false
                        PendingWaypoints.request(code, true)
                    }) { Text("Set Waypoints") }
                }
            )
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(mapType = mapType, isMyLocationEnabled = hasFine),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = true)
        ) {
            riders.forEach { r ->
                if (r.latitude != 0.0 || r.longitude != 0.0) {
                    Marker(
                        state = MarkerState(LatLng(r.latitude, r.longitude)),
                        title = r.name + if (r.isLeader) " (Leader)" else "",
                        snippet = com.karthik.packride.data.MeasurementUnits.speedMph(r.speedMph)
                    )
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CODE $code" + if (isLeader) " · LEADER" else "",
                    color = PrCoral,
                    fontFamily = FontFamily.Monospace
                )
                // Voice chat button — tap once to join (asks for mic
                // permission the first time), tap again to mute/unmute once
                // connected. Deliberately opt-in, never auto-joins. Mirrors
                // iOS MapView's voiceChatButton: filled green + white glyph
                // when connected & unmuted, translucent circle otherwise.
                // Plain text glyphs rather than Icons.Filled.Mic/MicOff —
                // this project's standing rule is only grep-confirmed icon
                // names, and no dependency cache was reachable from this
                // shell to confirm those extended-icon names compile.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (voiceConnected) {
                        Text(
                            text = (if (voiceSpeakerEnabled) "\uD83D\uDD0A " else "\uD83C\uDFA7 ") + voiceAudioRoute,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { voiceChat.toggleSpeaker() }
                                .padding(horizontal = 7.dp, vertical = 5.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                if (voiceConnected && !voiceMuted) {
                                    GrGoingGreen
                                } else {
                                    Color.White.copy(alpha = 0.18f)
                                }
                            )
                            .clickable {
                                if (voiceConnected) voiceChat.toggleMute() else joinVoiceChat()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (voiceConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text(
                                text = if (voiceConnected && !voiceMuted) "\uD83C\uDF99\uFE0F" else "\uD83D\uDD07",
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            Text(
                "${com.karthik.packride.data.MeasurementUnits.speedMph(speed)}  ·  " +
                    "${com.karthik.packride.data.MeasurementUnits.distanceMiles(distance)}  ·  ${GroupRideSession.formatDuration(elapsed)}",
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "Pack: ${riders.size} other rider${if (riders.size == 1) "" else "s"} · top %.0f".format(maxSpeed),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp
            )

            val leader = riders.firstOrNull { it.isLeader }
            val distToLeaderM = run {
                val loc = location
                if (leader == null || loc == null) return@run null
                if (leader.latitude == 0.0 && leader.longitude == 0.0) return@run null
                val results = FloatArray(1)
                Location.distanceBetween(
                    loc.latitude, loc.longitude,
                    leader.latitude, leader.longitude, results
                )
                results[0]
            }
            if (distToLeaderM != null) {
                Text(
                    "Leader: ${com.karthik.packride.data.MeasurementUnits.distanceMeters(distToLeaderM.toDouble())}",
                    color = PrCoral,
                    fontSize = 13.sp
                )
            }
            riders.take(4).forEach { r ->
                Text(
                    "${r.initials} ${r.name} · ${com.karthik.packride.data.MeasurementUnits.speedMph(r.speedMph)}",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
            }
        }

        PrMapStylePicker(
            selected = mapType,
            onSelected = { mapType = it },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 116.dp, end = 12.dp)
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Join my PackRide group: $code\npackride://join?code=$code"
                            )
                        }
                        context.startActivity(Intent.createChooser(send, "Share ride code"))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Share code") }
                Button(
                    onClick = { confirmEnd = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("End ride") }
            }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End group ride?") },
            text = { Text("You'll leave the live room and save your stats.") },
            confirmButton = {
                Button(onClick = {
                    confirmEnd = false
                    session.end()
                }) { Text("End") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false }) { Text("Stay") }
            }
        )
    }

    summary?.let { ride ->
        AlertDialog(
            onDismissRequest = { session.dismissSummary() },
            title = { Text("Group ride complete") },
            text = {
                Text(
                    "Code: ${ride.rideCode}\nDistance: ${com.karthik.packride.data.MeasurementUnits.distanceMiles(ride.distanceMiles, 2)}\n" +
                        "Top: ${com.karthik.packride.data.MeasurementUnits.speedMph(ride.maxSpeedMph)}\nDuration: ${ride.durationFormatted}"
                )
            },
            confirmButton = {
                Button(onClick = { session.dismissSummary() }) { Text("Done") }
            }
        )
    }

    voiceError?.let { err ->
        AlertDialog(
            onDismissRequest = { voiceChat.clearError() },
            title = { Text("Voice Chat") },
            text = { Text(err) },
            confirmButton = {
                Button(onClick = { voiceChat.clearError() }) { Text("OK") }
            }
        )
    }
}

private val GrTealSoft = Color(0xFFE7EFF1)
private val GrGoingGreen = Color(0xFF2E9E5B)

private fun grMonthLabel(epochSeconds: Double): String {
    val fmt = SimpleDateFormat("MMM", Locale.getDefault())
    return fmt.format(Date((epochSeconds * 1000).toLong())).uppercase()
}

private fun grDayLabel(epochSeconds: Double): Int {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = (epochSeconds * 1000).toLong()
    return cal.get(java.util.Calendar.DAY_OF_MONTH)
}

private fun grTimeLabel(epochSeconds: Double): String {
    val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
    return fmt.format(Date((epochSeconds * 1000).toLong()))
}
