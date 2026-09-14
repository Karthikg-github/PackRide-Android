package com.karthik.packride.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.karthik.packride.auth.AuthManager
import com.karthik.packride.location.SharedLocationManager
import com.karthik.packride.nav.NavState
import com.karthik.packride.nav.PendingTurnByTurn
import com.karthik.packride.nav.RouteStep
import com.karthik.packride.nav.TurnByTurnNavigator
import com.karthik.packride.ride.NavRideSession
import com.karthik.packride.ride.RideRecord
import com.karthik.packride.road.RoadInfoManager
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrCard
import com.karthik.packride.ui.theme.PrCoral
import com.karthik.packride.ui.theme.PrDarkPill
import com.karthik.packride.ui.theme.PrMapStylePicker
import kotlinx.coroutines.delay

// MARK: - Real in-app turn-by-turn navigation. Kotlin port of iOS
// TurnByTurnView.swift + NavigationManager.swift: actual routed guidance
// (not just an external-Google-Maps launcher), voice announcements via
// Android TextToSpeech, off-route rerouting, and a real ride recording that
// starts once guidance begins and saves through the same RideHistoryManager
// pipeline Solo Ride uses.
//
// Road name (Android Geocoder reverse geocoding) + posted speed limit
// (OpenStreetMap Overpass API) — see RoadInfoManager.kt, a real Kotlin port
// of iOS's RoadInfoManager.swift (same throttling, same preferred-road-type
// filtering, same mph/km/h tag parsing).
//
// One remaining scope simplification versus 1:1 iOS parity: maneuver icons
// use the Directions API's own maneuver field (turn-left/turn-right/etc, see
// DirectionsService.NavStep) to pick left/right/straight/arrived, but render
// with a small, Compose-confirmed-working icon set rather than exact
// turn-type glyphs (slight-left, roundabout, merge, fork) — stays clear of
// unverified icon names after today's several rounds of dependency/version
// pain elsewhere in this project.
//
// Aug 31, 2026 — visual restyle pass (Group Ride's day-one "close enough to
// iOS" call turned out to be wrong for all five screens skipped back then;
// see project history). Swapped stock Material3 colorScheme.* roles and
// hardcoded one-off colors for the app's real `Pr` design-system tokens
// (ui/theme/DesignSystem.kt), matching the styling GarageScreen/HomeScreen
// etc. already got. The full-screen dark map stays dark chrome-on-map by
// design — that's iOS's own convention here (Color.prInkFixed instruction
// card, prCardBg "THEN"/steps rows, black-glass road pill), not something
// this screen skipped. Nothing about map rendering, the nav state machine,
// TextToSpeech, reroute detection, or road-info fetching changed.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurnByTurnScreen(auth: AuthManager, onDone: () -> Unit) {
    val context = LocalContext.current
    val navigator = remember { TurnByTurnNavigator(context) }
    val rideSession = remember { NavRideSession(context) }
    val roadInfo = remember { RoadInfoManager(context) }

    val navState by navigator.state.collectAsState()
    val routeError by navigator.routeError.collectAsState()
    val steps by navigator.steps.collectAsState()
    val currentStepIndex by navigator.currentStepIndex.collectAsState()
    val routePolylines by navigator.routePolylines.collectAsState()
    val distanceRemaining by navigator.distanceRemainingMeters.collectAsState()
    val etaMinutes by navigator.etaMinutes.collectAsState()
    val distanceToNextStep by navigator.distanceToNextStepMeters.collectAsState()
    val isVoiceEnabled by navigator.isVoiceEnabled.collectAsState()

    val myLocation by SharedLocationManager.get().location.collectAsState()
    val speedMph by SharedLocationManager.get().speedMph.collectAsState()
    val roadName by roadInfo.roadName.collectAsState()
    val speedLimitMph by roadInfo.speedLimitMph.collectAsState()

    var request by remember { mutableStateOf<PendingTurnByTurn.Pending?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var showStepsList by remember { mutableStateOf(false) }
    var showEndConfirm by remember { mutableStateOf(false) }
    var summaryRecord by remember { mutableStateOf<RideRecord?>(null) }
    var summaryWasRecorded by remember { mutableStateOf(false) }

    val currentStepValue = steps.getOrNull(currentStepIndex)
    val nextStepValue = steps.getOrNull(currentStepIndex + 1)

    fun finishRideAndNavigation() {
        navigator.stopNavigation()
        if (!isRecording) {
            onDone()
            return
        }
        val record = rideSession.end()
        isRecording = false
        if (record != null) {
            summaryWasRecorded = true
            summaryRecord = record
        } else {
            onDone()
        }
    }

    // Consume the pending nav request once, then start GPS for this screen.
    LaunchedEffect(Unit) {
        val req = PendingTurnByTurn.pending.value
        PendingTurnByTurn.clear()
        if (req == null) {
            onDone()
            return@LaunchedEffect
        }
        request = req
        SharedLocationManager.get().startUpdating("turnByTurn")
    }

    DisposableEffect(Unit) {
        onDispose {
            SharedLocationManager.get().stopUpdating("turnByTurn")
            if (isRecording) rideSession.cancelIfNeeded()
            navigator.shutdown()
            roadInfo.shutdown()
        }
    }

    // Calculate the route once we have both a destination request and a first GPS fix.
    LaunchedEffect(request) {
        val req = request ?: return@LaunchedEffect
        var loc = SharedLocationManager.get().location.value
        var attempts = 0
        while (loc == null && attempts < 20) {
            delay(500)
            loc = SharedLocationManager.get().location.value
            attempts++
        }
        val from = loc?.let { LatLng(it.latitude, it.longitude) } ?: return@LaunchedEffect
        navigator.calculateRoute(
            from = from,
            to = LatLng(req.destLat, req.destLng),
            waypoints = req.waypoints.map { LatLng(it.first, it.second) }
        )
    }

    // Ride recording starts the first time real guidance begins — not at
    // "calculating" — and never restarts on a mid-ride reroute.
    LaunchedEffect(navState) {
        if (navState == NavState.NAVIGATING && !isRecording) {
            rideSession.start(auth.prefsSnapshot.riderName)
            isRecording = true
        }
    }

    val cameraPositionState = rememberCameraPositionState()
    var mapType by remember { mutableStateOf(MapType.HYBRID) }

    LaunchedEffect(myLocation?.latitude, myLocation?.longitude) {
        val loc = myLocation ?: return@LaunchedEffect
        navigator.updatePosition(loc)
        roadInfo.update(loc)
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 17f)
        )
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = mapType),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
        ) {
            routePolylines.forEach { leg ->
                if (leg.size >= 2) Polyline(points = leg, color = PrCoral, width = 9f)
            }
            request?.let { req ->
                Marker(
                    state = MarkerState(LatLng(req.destLat, req.destLng)),
                    title = req.destName
                )
            }
            currentStepValue?.let { step ->
                Marker(
                    state = MarkerState(LatLng(step.lat, step.lng)),
                    title = "Next turn"
                )
            }
        }

        Column(Modifier.fillMaxSize()) {
            instructionCard(
                state = navState,
                currentStep = currentStepValue,
                nextStep = nextStepValue,
                distanceToNextStepMeters = distanceToNextStep,
                destinationName = request?.destName ?: "",
                routeError = routeError,
                onArrivedDone = { finishRideAndNavigation() }
            )

            if (navState == NavState.NAVIGATING) {
                roadInfoRow(roadName = roadName, speedLimitMph = speedLimitMph, currentSpeedMph = speedMph)
            }

            if (isRecording) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    recordingBadge()
                }
            }

            Spacer(Modifier.weight(1f))

            bottomPanel(
                etaMinutes = etaMinutes,
                distanceRemainingMeters = distanceRemaining,
                speedMph = speedMph,
                isVoiceEnabled = isVoiceEnabled,
                onToggleVoice = { navigator.toggleVoice() },
                onShowSteps = { showStepsList = true },
                onEndNavigation = {
                    if (isRecording) showEndConfirm = true
                    else {
                        navigator.stopNavigation()
                        onDone()
                    }
                }
            )
        }

        PrMapStylePicker(
            selected = mapType,
            onSelected = { mapType = it },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 118.dp, end = 12.dp)
        )
    }

    if (showStepsList) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showStepsList = false },
            sheetState = sheetState,
            containerColor = Pr.bg
        ) {
            stepsListContent(steps = steps, currentStepIndex = currentStepIndex)
        }
    }

    if (showEndConfirm) {
        AlertDialog(
            onDismissRequest = { showEndConfirm = false },
            title = { Text("End Navigation?", color = Pr.ink) },
            text = {
                Text(
                    "Your ride is being recorded. Ending now will save it to your Ride History.",
                    color = Pr.muted
                )
            },
            confirmButton = {
                TextButton(onClick = { showEndConfirm = false; finishRideAndNavigation() }) {
                    Text("End Navigation", color = Pr.coral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirm = false }) {
                    Text("Keep Riding", color = Pr.muted)
                }
            }
        )
    }

    summaryRecord?.let { ride ->
        AlertDialog(
            onDismissRequest = { summaryRecord = null; onDone() },
            title = { Text("Ride complete", color = Pr.ink) },
            text = {
                Text(
                    "Distance: ${com.karthik.packride.data.MeasurementUnits.distanceMiles(ride.distanceMiles, 2)}\n" +
                        "Top speed: ${com.karthik.packride.data.MeasurementUnits.speedMph(ride.maxSpeedMph)}\n" +
                        "Duration: ${ride.durationFormatted}",
                    color = Pr.muted
                )
            },
            confirmButton = {
                TextButton(onClick = { summaryRecord = null; onDone() }) {
                    Text("Done", color = Pr.coral, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

// MARK: - Road name + speed limit — floats over the map, below the turn card

@Composable
private fun roadInfoRow(roadName: String?, speedLimitMph: Int?, currentSpeedMph: Double) {
    if (roadName == null && speedLimitMph == null) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            // Port of iOS's RoadNamePill — reuse the Pr design-system's dark
            // glass pill (black 0.5-alpha, 12dp radius) exactly as-is instead
            // of the old inline Text+background here.
            roadName?.let { name -> PrDarkPill(text = name) }
        }
        speedLimitMph?.let { limit ->
            val overLimit = currentSpeedMph > limit + 5
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(Pr.cardBg, RoundedCornerShape(6.dp))
                    .border(
                        width = if (overLimit) 3.dp else 1.dp,
                        color = if (overLimit) Color(0xFFD33B2C) else Color.Black,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("LIMIT", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Black)
                Text(
                    "$limit",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = if (overLimit) Color(0xFFD33B2C) else Color.Black
                )
            }
        }
    }
}

// MARK: - Recording badge (mirrors Solo Ride's LIVE pill)

@Composable
private fun recordingBadge() {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(PrCoral, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(6.dp))
        Text("RECORDING RIDE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// MARK: - Top instruction card

@Composable
private fun instructionCard(
    state: NavState,
    currentStep: RouteStep?,
    nextStep: RouteStep?,
    distanceToNextStepMeters: Double,
    destinationName: String,
    routeError: String?,
    onArrivedDone: () -> Unit
) {
    when (state) {
        NavState.CALCULATING, NavState.REROUTING -> {
            Row(
                modifier = Modifier.fillMaxWidth().background(Pr.inkFixed).padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    if (state == NavState.REROUTING) "Rerouting..." else "Calculating route...",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        NavState.NAVIGATING -> {
            if (currentStep != null) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Pr.inkFixed).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(56.dp).background(PrCoral, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(maneuverIcon(currentStep), contentDescription = null, tint = Color.White)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                com.karthik.packride.data.MeasurementUnits.distanceMeters(distanceToNextStepMeters),
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                currentStep.instruction,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                maxLines = 2
                            )
                        }
                    }
                    if (nextStep != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Pr.cardBg)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "THEN",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Pr.muted
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                maneuverIcon(nextStep),
                                contentDescription = null,
                                tint = Pr.ink,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                nextStep.instruction,
                                fontSize = 12.sp,
                                maxLines = 1,
                                color = Pr.ink,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        NavState.ARRIVED -> {
            // Fixed arrival green — matches iOS's own hardcoded
            // Color(red: 0.2, green: 0.7, blue: 0.4) rather than a Pr token
            // (iOS doesn't route this one through its design system either).
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF2FA85C)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Flag, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("You've Arrived!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(destinationName, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
                Button(
                    onClick = onArrivedDone,
                    colors = ButtonDefaults.buttonColors(containerColor = Pr.cardBg, contentColor = Pr.coral)
                ) { Text("Done") }
            }
        }
        NavState.IDLE -> if (routeError != null) {
            Text(
                routeError,
                modifier = Modifier.fillMaxWidth().background(Pr.cardBg).padding(18.dp),
                color = Pr.coral,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// MARK: - Bottom stats + controls

@Composable
private fun bottomPanel(
    etaMinutes: Int,
    distanceRemainingMeters: Double,
    speedMph: Double,
    isVoiceEnabled: Boolean,
    onToggleVoice: () -> Unit,
    onShowSteps: () -> Unit,
    onEndNavigation: () -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        PrCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                statColumn(if (etaMinutes < 60) "$etaMinutes min" else "${etaMinutes / 60}h ${etaMinutes % 60}m", "ETA")
                Box(Modifier.height(30.dp).width(1.dp).background(Pr.border))
                statColumn(com.karthik.packride.data.MeasurementUnits.distanceMeters(distanceRemainingMeters), "DISTANCE")
                Box(Modifier.height(30.dp).width(1.dp).background(Pr.border))
                statColumn(com.karthik.packride.data.MeasurementUnits.speedMph(speedMph), "SPEED")
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(
                onClick = onShowSteps,
                modifier = Modifier
                    .size(48.dp)
                    .background(Pr.cardBg, RoundedCornerShape(14.dp))
                    .border(1.dp, Pr.border, RoundedCornerShape(14.dp))
            ) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Route steps", tint = Pr.ink) }

            IconButton(
                onClick = onToggleVoice,
                modifier = Modifier
                    .size(48.dp)
                    .background(Pr.cardBg, RoundedCornerShape(14.dp))
                    .border(1.dp, Pr.border, RoundedCornerShape(14.dp))
            ) {
                Icon(
                    if (isVoiceEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = "Toggle voice",
                    tint = if (isVoiceEnabled) Pr.coral else Pr.muted
                )
            }

            Button(
                onClick = onEndNavigation,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrCoral)
            ) { Text("End Navigation") }
        }
    }
}

@Composable
private fun statColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Pr.ink)
        Text(label, fontSize = 9.sp, color = Pr.muted)
    }
}

// MARK: - Route steps sheet

@Composable
private fun stepsListContent(steps: List<RouteStep>, currentStepIndex: Int) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            "Route Steps",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Pr.ink,
            modifier = Modifier.padding(16.dp)
        )
        LazyColumn(modifier = Modifier.fillMaxWidth().height(400.dp)) {
            itemsIndexed(steps) { index, step ->
                val isCurrent = index == currentStepIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .background(Pr.cardBg, RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            if (isCurrent) Pr.coral.copy(alpha = 0.3f) else Pr.border,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (step.isCompleted) Icons.Filled.Check else maneuverIcon(step),
                        contentDescription = null,
                        tint = when {
                            isCurrent -> Pr.coral
                            step.isCompleted -> Pr.muted
                            else -> Pr.ink
                        }
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            step.instruction,
                            fontWeight = FontWeight.Medium,
                            color = if (step.isCompleted) Pr.muted else Pr.ink
                        )
                        Text(
                            com.karthik.packride.data.MeasurementUnits.distanceMeters(step.distanceMeters),
                            fontSize = 12.sp,
                            color = Pr.muted
                        )
                    }
                    if (isCurrent) {
                        Text(
                            "NOW",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Pr.coral,
                            modifier = Modifier
                                .background(Pr.coral.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

// Prefers the Directions API's own maneuver type (more reliable than
// guessing from instruction text, which can misfire — e.g. "Turn right onto
// Left Lane Ave" contains both "left" and "right") and falls back to
// keyword-matching the instruction for steps that don't have one (many
// "continue straight" steps don't carry a maneuver field at all).
private fun maneuverIcon(step: RouteStep): ImageVector {
    val instruction = step.instruction.lowercase()
    if (instruction.contains("arrive") || instruction.contains("destination")) return Icons.Filled.Flag
    val maneuver = step.maneuver.lowercase()
    return when {
        maneuver.contains("left") -> Icons.AutoMirrored.Filled.ArrowBack
        maneuver.contains("right") -> Icons.AutoMirrored.Filled.ArrowForward
        maneuver.isNotEmpty() -> Icons.Filled.ArrowUpward
        instruction.contains("left") -> Icons.AutoMirrored.Filled.ArrowBack
        instruction.contains("right") -> Icons.AutoMirrored.Filled.ArrowForward
        else -> Icons.Filled.ArrowUpward
    }
}
