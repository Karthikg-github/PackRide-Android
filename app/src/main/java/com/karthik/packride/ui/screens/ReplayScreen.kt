package com.karthik.packride.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.karthik.packride.gpx.GPXStorage
import com.karthik.packride.group.PendingReplay
import com.karthik.packride.replay.GpxPointParser
import com.karthik.packride.replay.ParsedGpxPoint
import com.karthik.packride.ride.RideHistoryManager
import com.karthik.packride.ride.RideRecord
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrCoral
import com.karthik.packride.ui.theme.PrFont
import com.karthik.packride.ui.theme.PrMapStylePicker
import com.karthik.packride.ui.theme.LocalPrBackAction
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

// Aug 30, 2026 — animated-playback pass. Was a static route preview (one
// polyline, no controls) — real port of RideReplayView.swift now: a marker
// that steps along the recorded GPX track on a timer, play/pause, a
// 1x/2x/4x/8x speed picker (gated by ride length, same thresholds as iOS),
// a skip-forward button (~12% of the route), a scrub slider, tap-on-map to
// jump to the nearest point (pausing playback, same as iOS's
// SpatialTapGesture handler), and a live Speed/Elevation/G-Force readout
// driven off whatever point playback is currently on.
//
// Kept from the prior pass: preferring a PendingReplay-requested ride, with
// the most-recent-ride-with-a-route fallback when nothing was requested —
// that already covers "pick which ride to replay" so there's no separate
// ride-selector UI here (Ride History's card list IS the selector; this
// screen just plays whichever ride it was handed).
//
// Platform adaptations retained from RideReplayView.swift:
//  - Road-snapping a sparse (<=10 point) route through the Directions API
//    (iOS's buildRoadRoute via MKDirections). DirectionsService.kt exists
//    and could drive this, but GPXRecorder captures at ~1Hz so a real ride
//    almost always lands well over 10 points and takes the dense-track
//    path below; the sparse fallback here just draws straight segments
//    between the few points there are, same as iOS's fallback-while-loading
//    behavior, without the extra per-leg network round trip.
@Composable
fun ReplayScreen() {
    val context = LocalContext.current
    val history = remember { RideHistoryManager(context) }
    val rides by history.rides.collectAsState()
    val pendingRideId by PendingReplay.pending.collectAsState()

    val ride = (pendingRideId?.let { id -> rides.firstOrNull { it.id == id } })
        ?: rides.firstOrNull { it.hasRouteData }
    var points by remember(ride?.id) { mutableStateOf<List<ParsedGpxPoint>>(emptyList()) }
    var resolving by remember(ride?.id) { mutableStateOf(ride != null) }
    var resolveFailed by remember(ride?.id) { mutableStateOf(false) }

    LaunchedEffect(ride?.id, ride?.gpxFileName, ride?.gpxURL) {
        val selected = ride
        if (selected == null) {
            resolving = false
            return@LaunchedEffect
        }
        resolving = true
        resolveFailed = false
        val name = history.resolveGpx(selected)
        points = name?.let { GpxPointParser.parse(GPXStorage.resolve(context.filesDir, it)) }.orEmpty()
        resolveFailed = points.isEmpty()
        resolving = false
    }

    if (resolving) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Pr.coral)
                Spacer(Modifier.height(12.dp))
                Text("Loading ride replay…", style = PrFont.bodySmall)
            }
        }
        return
    }

    if (points.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (resolveFailed && ride?.gpxURL != null) "This ride's route couldn't be downloaded. Check your connection and try again."
                else if (pendingRideId != null) "This ride has no route data."
                else "No local GPX yet. End a Solo or Group ride first.",
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    ReplayContent(ride = ride, points = points)
}

@Composable
private fun ReplayContent(ride: RideRecord?, points: List<ParsedGpxPoint>) {
    // Base playback rate at 1x — matches iOS's basePointsPerSecond (6).
    val basePointsPerSecond = 6.0
    val skipFraction = 0.12

    // Keyed on ride?.id (not points.size) so switching to a different ride
    // in Ride History always resets playback, even in the unlikely case two
    // different rides happen to share the same point count.
    var currentIndex by rememberSaveable(ride?.id) { mutableIntStateOf(0) }
    var isPlaying by rememberSaveable(ride?.id) { mutableStateOf(false) }
    var speedMultiplier by rememberSaveable(ride?.id) { mutableFloatStateOf(1f) }
    var mapType by rememberSaveable(ride?.id) { mutableStateOf(MapType.NORMAL) }

    val availableSpeeds = remember(points.size) {
        when {
            points.size < 180 -> listOf(1f, 2f)
            points.size < 600 -> listOf(1f, 2f, 4f)
            else -> listOf(1f, 2f, 4f, 8f)
        }
    }
    LaunchedEffect(availableSpeeds) {
        if (speedMultiplier !in availableSpeeds) speedMultiplier = availableSpeeds.first()
    }

    val currentPoint = points.getOrNull(currentIndex)
    val progress = if (points.size > 1) currentIndex.toFloat() / (points.size - 1) else 0f

    // Dense GPS track (the normal case at ~1Hz capture) draws its own
    // points as the route; a sparse recording just connects what little it
    // has with straight segments — see the file-header note on why this
    // doesn't call out to DirectionsService for road-snapping.
    val routeCoordinates = remember(points) { points.map { LatLng(it.lat, it.lng) } }

    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(routeCoordinates.first(), 13f)
    }
    LaunchedEffect(routeCoordinates) {
        if (routeCoordinates.size > 1) {
            val bounds = LatLngBounds.Builder().apply { routeCoordinates.forEach { include(it) } }.build()
            runCatching { camera.animate(CameraUpdateFactory.newLatLngBounds(bounds, 140)) }
        }
    }

    // Playback driver — restarts whenever isPlaying flips true or the speed
    // multiplier changes (iOS parity: onChange(of: speedMultiplier) restarts
    // its Timer the same way when already playing).
    LaunchedEffect(isPlaying, speedMultiplier, points.size) {
        if (!isPlaying) return@LaunchedEffect
        val tickMs = (1000.0 / (basePointsPerSecond * speedMultiplier)).toLong().coerceAtLeast(1L)
        while (true) {
            delay(tickMs)
            if (currentIndex < points.size - 1) {
                currentIndex += 1
            } else {
                isPlaying = false
                break
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = MapProperties(mapType = mapType),
            onMapClick = { latLng ->
                isPlaying = false
                GpxPointParser.nearestIndex(latLng.latitude, latLng.longitude, points)?.let { idx ->
                    currentIndex = idx
                }
            }
        ) {
            if (routeCoordinates.size > 1) {
                Polyline(points = routeCoordinates, color = PrCoral, width = 5f)
            }
            if (currentIndex > 0) {
                Polyline(
                    points = routeCoordinates.take(currentIndex + 1),
                    color = Pr.teal,
                    width = 4f
                )
            }
            currentPoint?.let { pt ->
                Marker(
                    state = MarkerState(LatLng(pt.lat, pt.lng)),
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                )
            }
        }

        Column(Modifier.fillMaxSize()) {
            ReplayTopBar(ride = ride, isPlaying = isPlaying)
            Spacer(Modifier.weight(1f))
            ReplayBottomPanel(
                points = points,
                currentIndex = currentIndex,
                progress = progress,
                currentPoint = currentPoint,
                isPlaying = isPlaying,
                speedMultiplier = speedMultiplier,
                availableSpeeds = availableSpeeds,
                skipFraction = skipFraction,
                onScrub = { newProgress ->
                    if (points.size > 1) currentIndex = (newProgress * (points.size - 1)).toInt()
                },
                onSelectSpeed = { speedMultiplier = it },
                onSkipForward = {
                    val skipCount = (points.size * skipFraction).toInt().coerceAtLeast(1)
                    currentIndex = (currentIndex + skipCount).coerceAtMost(points.size - 1)
                },
                onTogglePlay = {
                    if (currentIndex == points.size - 1) currentIndex = 0
                    isPlaying = !isPlaying
                }
            )
        }
        PrMapStylePicker(
            selected = mapType,
            onSelected = { mapType = it },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 86.dp, end = 14.dp)
        )
    }
}

@Composable
private fun ReplayTopBar(ride: RideRecord?, isPlaying: Boolean) {
    val onBack = LocalPrBackAction.current
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close replay", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.size(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "RIDE REPLAY",
                style = PrFont.micro.copy(color = Color.White.copy(alpha = 0.72f), letterSpacing = 2.2.sp)
            )
            Text(
                ride?.trackName?.takeIf { it.isNotBlank() } ?: "Ride",
                style = PrFont.subheading.copy(color = Color.White),
                maxLines = 1
            )
            Text(
                ride?.let { dateFmt.format(Date(it.dateMs)) } ?: "",
                style = PrFont.caption.copy(color = Color.White.copy(alpha = 0.68f))
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.34f))
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) Pr.teal else Color.White.copy(alpha = 0.45f))
            )
            Text(
                if (isPlaying) "PLAYING" else "PAUSED",
                style = PrFont.micro.copy(color = Color.White, letterSpacing = 1.2.sp)
            )
        }
    }
}

@Composable
private fun ReplayBottomPanel(
    points: List<ParsedGpxPoint>,
    currentIndex: Int,
    progress: Float,
    currentPoint: ParsedGpxPoint?,
    isPlaying: Boolean,
    speedMultiplier: Float,
    availableSpeeds: List<Float>,
    skipFraction: Double,
    onScrub: (Float) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSkipForward: () -> Unit,
    onTogglePlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Pr.cardBg.copy(alpha = 0.94f))
            .padding(horizontal = 18.dp)
            .padding(top = 12.dp, bottom = 18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ROUTE PROGRESS", style = PrFont.micro.copy(letterSpacing = 1.4.sp))
                Text("${(progress * 100).toInt()}% complete", style = PrFont.bodySmall)
            }
            Text(
                "${currentIndex + 1} / ${points.size.coerceAtLeast(1)}",
                style = PrFont.caption
            )
        }

        Slider(
            value = progress,
            onValueChange = onScrub,
            colors = SliderDefaults.colors(thumbColor = PrCoral, activeTrackColor = PrCoral),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ReplayMetric(
                value = currentPoint?.let { com.karthik.packride.data.MeasurementUnits.speedMph(it.speed * 2.23694) } ?: "—",
                unit = "",
                label = "SPEED",
                modifier = Modifier.weight(1f)
            )
            ReplayMetric(
                value = currentPoint?.let { "%.0f".format(it.ele * 3.28084) } ?: "—",
                unit = "ft",
                label = "ELEVATION",
                modifier = Modifier.weight(1f)
            )
            ReplayMetric(
                value = currentPoint?.let { "%.1f".format(it.gforce) } ?: "—",
                unit = "G",
                label = "G-FORCE",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SpeedPicker(
                availableSpeeds = availableSpeeds,
                selected = speedMultiplier,
                onSelect = onSelectSpeed,
                modifier = Modifier.weight(1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Pr.fieldBg)
                    .clickable(onClick = onSkipForward)
                    .padding(horizontal = 14.dp)
                    .height(42.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Skip forward", tint = Pr.ink, modifier = Modifier.size(16.dp))
                Text("+${(skipFraction * 100).toInt()}%", style = PrFont.caption.copy(color = Pr.ink))
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(PrCoral)
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun ReplayMetric(value: String, unit: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Pr.fieldBg.copy(alpha = 0.82f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = PrFont.subheading)
            Text(unit, style = PrFont.caption)
        }
        Text(label, style = PrFont.micro.copy(letterSpacing = 1.1.sp))
    }
}

@Composable
private fun SpeedPicker(
    availableSpeeds: List<Float>,
    selected: Float,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Pr.fieldBg.copy(alpha = 0.65f))
            .padding(4.dp)
    ) {
        availableSpeeds.forEach { speed ->
            val isSelected = selected == speed
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) PrCoral else Pr.fieldBg)
                    .clickable { onSelect(speed) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${speed.toInt()}x",
                    style = PrFont.caption.copy(
                        color = if (isSelected) Color.White else Pr.muted,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}
