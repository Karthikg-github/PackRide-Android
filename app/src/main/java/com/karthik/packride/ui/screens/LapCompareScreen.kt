package com.karthik.packride.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.karthik.packride.lap.LapCompareEngine
import com.karthik.packride.lap.DistanceLapCompareResult
import com.karthik.packride.lap.DistanceLapComparisonBuilder
import com.karthik.packride.lap.LapCompareMetric
import com.karthik.packride.lap.LapReconstructor
import com.karthik.packride.lap.LapSharingManager
import com.karthik.packride.lap.SharedLapSession
import com.karthik.packride.ride.RideHistoryManager
import com.karthik.packride.ride.RideRecord
import com.karthik.packride.gpx.GPXStorage
import com.karthik.packride.replay.GpxPointParser
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrCard
import com.karthik.packride.ui.theme.PrFont
import com.karthik.packride.ui.theme.PrMetricStrip
import com.karthik.packride.ui.theme.PrPageHeader
import com.karthik.packride.ui.theme.PrWebSectionLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.roundToInt

private data class ChosenLap(
    val trackName: String, val owner: String, val dateMs: Long, val index: Int, val seconds: Double,
    val allLaps: List<Double>, val ride: RideRecord? = null, val shared: SharedLapSession? = null
) { val lapStartTimestamps: List<Long> get() = ride?.lapStartTimestamps ?: shared?.lapStartTimestamps.orEmpty() }

@Composable
fun LapCompareScreen() {
    val context = LocalContext.current
    val history = remember { RideHistoryManager(context) }
    val rides by history.rides.collectAsState()
    val sessions = rides.filter { it.lapTimes.isNotEmpty() }
    val sharing = remember { LapSharingManager(context) }
    val shared by sharing.sharedWithMe.collectAsState()
    DisposableEffect(Unit) { sharing.start(); onDispose { sharing.stop() } }
    var slotA by remember { mutableStateOf<ChosenLap?>(null) }
    var slotB by remember { mutableStateOf<ChosenLap?>(null) }
    var pickingA by remember { mutableStateOf<Boolean?>(null) }
    var traceA by remember { mutableStateOf<com.karthik.packride.lap.ReconstructedLap?>(null) }
    var traceB by remember { mutableStateOf<com.karthik.packride.lap.ReconstructedLap?>(null) }
    var loadErrorA by remember { mutableStateOf<String?>(null) }
    var loadErrorB by remember { mutableStateOf<String?>(null) }
    var loadingA by remember { mutableStateOf(false) }
    var loadingB by remember { mutableStateOf(false) }
    var metric by remember { mutableStateOf(LapCompareMetric.SPEED) }
    var selectedSampleIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<DistanceLapCompareResult?>(null) }
    LaunchedEffect(traceA, traceB) {
        val a = traceA
        val b = traceB
        result = if (a != null && b != null) {
            withContext(Dispatchers.Default) { DistanceLapComparisonBuilder.build(a, b) }
        } else null
    }
    LaunchedEffect(result) {
        selectedSampleIndex = 0
        isPlaying = false
    }
    LaunchedEffect(isPlaying, result) {
        val comparison = result ?: return@LaunchedEffect
        while (isPlaying) {
            delay(120)
            if (selectedSampleIndex >= comparison.samples.lastIndex) {
                isPlaying = false
            } else {
                selectedSampleIndex++
            }
        }
    }

    fun loadLap(chosen: ChosenLap, isA: Boolean) {
        if (isA) { loadingA = true; loadErrorA = null; traceA = null }
        else { loadingB = true; loadErrorB = null; traceB = null }
        scope.launch {
            val file = chosen.ride?.let { ride ->
                history.resolveGpx(ride)?.let { GPXStorage.resolve(context.filesDir, it) }
            } ?: chosen.shared?.let { session ->
                suspendCancellableCoroutine { continuation ->
                    sharing.download(session) { downloaded, _ -> if (continuation.isActive) continuation.resume(downloaded) }
                }
            }
            val trace = file?.let { source ->
                withContext(Dispatchers.IO) {
                    LapReconstructor.reconstruct(GpxPointParser.parse(source), chosen.allLaps, chosen.lapStartTimestamps)
                        .firstOrNull { it.lapIndex == chosen.index }
                }
            }
            if (isA) { traceA = trace; loadingA = false; if (trace == null) loadErrorA = "Couldn't read this lap's recorded route." }
            else { traceB = trace; loadingB = false; if (trace == null) loadErrorB = "Couldn't read this lap's recorded route." }
        }
    }

    Column(Modifier.fillMaxSize().background(Pr.bg).verticalScroll(rememberScrollState())) {
        PrPageHeader("LAP ANALYTICS", "Compare Laps", "Any lap from any recorded session")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LapSlot("LAP A", slotA, Pr.coral, loadingA, loadErrorA, Modifier.weight(1f)) { pickingA = true }
            LapSlot("LAP B", slotB, Color(0xFF2E9E5B), loadingB, loadErrorB, Modifier.weight(1f)) { pickingA = false }
        }
        val comparison = result
        if (comparison == null) {
            PrWebSectionLabel("Build a comparison", "Two laps required")
            Text(
                if (sessions.isEmpty() && shared.isEmpty()) "Record a Track Mode session first."
                else "Choose a lap for both slots. You can compare laps from the same session or different days.",
                style = PrFont.bodySmall,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            PrWebSectionLabel("Result", "Lower is faster")
            PrMetricStrip(listOf(
                LapCompareEngine.formatLap(comparison.lapADuration) to "Lap A",
                LapCompareEngine.formatLap(comparison.lapBDuration) to "Lap B",
                "%+.2fs".format(comparison.finishDelta) to "At Finish"
            ))
            PrWebSectionLabel("Track Position", "Synchronized lap playback")
            TrackPositionCard(
                traceA = traceA!!,
                traceB = traceB!!,
                result = comparison,
                selectedIndex = selectedSampleIndex,
                isPlaying = isPlaying,
                onTogglePlayback = {
                    if (!isPlaying && selectedSampleIndex >= comparison.samples.lastIndex) selectedSampleIndex = 0
                    isPlaying = !isPlaying
                }
            )
            PrWebSectionLabel("Telemetry", "Choose a metric")
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                items(LapCompareMetric.entries) { item ->
                    Text(item.label, color = if (metric == item) Color.White else Pr.muted, style = PrFont.bodySmall,
                        modifier = Modifier.background(if (metric == item) Pr.coral else Pr.cardBg, RoundedCornerShape(10.dp))
                            .clickable { metric = item }.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
            PrCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${metric.label} vs. Distance", style = PrFont.subheading)
                    DualTraceChart(comparison, metric, selectedSampleIndex) { index ->
                        isPlaying = false
                        selectedSampleIndex = index
                    }
                    val selected = comparison.samples[selectedSampleIndex.coerceIn(comparison.samples.indices)]
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TelemetryValue("Lap A", selected.metricsA[metric] ?: 0.0, metric.unit, Pr.coral, Modifier.weight(1f))
                        TelemetryValue("Lap B", selected.metricsB[metric] ?: 0.0, metric.unit, Color(0xFF2E9E5B), Modifier.weight(1f))
                    }
                    Text("Orange = Lap A • Green = Lap B", style = PrFont.caption)
                }
            }
            PrCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Delta vs. Distance", style = PrFont.subheading)
                    Text("Green = Lap B ahead of Lap A's pace; red = behind", style = PrFont.caption)
                    DeltaStrip(comparison)
                }
            }
        }
    }

    pickingA?.let { isA ->
        LapPickerDialog(sessions, shared, onDismiss = { pickingA = null }) { chosen ->
            if (isA) slotA = chosen else slotB = chosen
            loadLap(chosen, isA)
            pickingA = null
        }
    }
}

@Composable
private fun LapSlot(label: String, lap: ChosenLap?, color: Color, loading: Boolean, error: String?, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).background(Pr.cardBg, RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(lap?.let { "Lap ${it.index + 1}" } ?: "Choose a Lap", style = PrFont.subheading)
        lap?.let {
            Text(LapCompareEngine.formatLap(it.seconds), color = color, fontWeight = FontWeight.Bold)
            Text("${it.owner} · ${it.trackName}", style = PrFont.caption, maxLines = 1)
        }
        if (loading) Text("Loading route…", style = PrFont.caption)
        error?.let { Text(it, color = Color(0xFFD33B2C), style = PrFont.caption) }
    }
}

@Composable
private fun LapPickerDialog(sessions: List<RideRecord>, shared: List<SharedLapSession>, onDismiss: () -> Unit, onPick: (ChosenLap) -> Unit) {
    val fmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a lap") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                sessions.forEach { session ->
                    Text("${session.trackName.ifBlank { "Track Session" }} · ${fmt.format(Date(session.dateMs))}", style = PrFont.caption, modifier = Modifier.padding(top = 10.dp))
                    session.lapTimes.forEachIndexed { index, seconds ->
                        Row(Modifier.fillMaxWidth().clickable {
                            onPick(ChosenLap(session.trackName.ifBlank { "Track Session" }, "You", session.dateMs, index, seconds, session.lapTimes, ride = session))
                        }.padding(vertical = 10.dp)) {
                            Text("Lap ${index + 1}", modifier = Modifier.weight(1f), style = PrFont.body)
                            Text(LapCompareEngine.formatLap(seconds), color = Pr.coral, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                shared.forEach { session ->
                    Text("${session.ownerName} · ${session.trackName} · ${fmt.format(Date(session.dateMs))}", style = PrFont.caption, modifier = Modifier.padding(top = 10.dp))
                    session.laps.forEachIndexed { index, seconds ->
                        Row(Modifier.fillMaxWidth().clickable {
                            onPick(ChosenLap(session.trackName, session.ownerName, session.dateMs, index, seconds, session.laps, shared = session))
                        }.padding(vertical = 10.dp)) {
                            Text("Lap ${index + 1}", modifier = Modifier.weight(1f), style = PrFont.body)
                            Text(LapCompareEngine.formatLap(seconds), color = Pr.teal, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TrackPositionCard(
    traceA: com.karthik.packride.lap.ReconstructedLap,
    traceB: com.karthik.packride.lap.ReconstructedLap,
    result: DistanceLapCompareResult,
    selectedIndex: Int,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit
) {
    val index = selectedIndex.coerceIn(result.samples.indices)
    val distance = result.samples[index].distanceMeters
    val coral = Pr.coral
    val allPoints = remember(traceA, traceB) { traceA.points + traceB.points }
    val center = remember(allPoints) {
        LatLng(
            allPoints.map { it.sample.lat }.average(),
            allPoints.map { it.sample.lng }.average()
        )
    }
    val zoom = remember(allPoints) {
        val latSpan = (allPoints.maxOfOrNull { it.sample.lat } ?: center.latitude) - (allPoints.minOfOrNull { it.sample.lat } ?: center.latitude)
        val lngSpan = (allPoints.maxOfOrNull { it.sample.lng } ?: center.longitude) - (allPoints.minOfOrNull { it.sample.lng } ?: center.longitude)
        when (maxOf(latSpan, lngSpan)) {
            in 0.0..0.0005 -> 18.5f
            in 0.0005..0.001 -> 17.5f
            in 0.001..0.003 -> 16.5f
            in 0.003..0.008 -> 15.5f
            else -> 14f
        }
    }
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(center, zoom) }
    val pointA = traceA.points.minByOrNull { kotlin.math.abs(it.distanceMeters - distance) }
    val pointB = traceB.points.minByOrNull { kotlin.math.abs(it.distanceMeters - distance) }
    PrCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Lap position at ${"%.0f".format(distance)} m", style = PrFont.subheading)
            GoogleMap(
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)),
                cameraPositionState = camera,
                properties = MapProperties(mapType = MapType.SATELLITE),
                uiSettings = MapUiSettings(
                    compassEnabled = false, zoomControlsEnabled = false,
                    scrollGesturesEnabled = false, zoomGesturesEnabled = false,
                    tiltGesturesEnabled = false, rotationGesturesEnabled = false
                )
            ) {
                Polyline(traceA.points.map { LatLng(it.sample.lat, it.sample.lng) }, color = coral.copy(alpha = .9f), width = 8f)
                Polyline(traceB.points.map { LatLng(it.sample.lat, it.sample.lng) }, color = Color(0xFF2E9E5B).copy(alpha = .9f), width = 8f)
                pointA?.let { Circle(LatLng(it.sample.lat, it.sample.lng), radius = 3.5, fillColor = coral, strokeColor = Color.White, strokeWidth = 3f) }
                pointB?.let { Circle(LatLng(it.sample.lat, it.sample.lng), radius = 3.5, fillColor = Color(0xFF2E9E5B), strokeColor = Color.White, strokeWidth = 3f) }
            }
            Row {
                Text(
                    if (isPlaying) "❚❚  Pause" else "▶  Play",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(if (isPlaying) Color(0xFF2E9E5B) else Pr.coral, RoundedCornerShape(50))
                        .clickable(onClick = onTogglePlayback).padding(horizontal = 20.dp, vertical = 11.dp)
                )
                Spacer(Modifier.weight(1f))
                Text("${"%.0f".format(distance)} / ${"%.0f".format(result.comparedDistanceMeters)} m", style = PrFont.caption)
            }
        }
    }
}

@Composable
private fun TelemetryValue(label: String, value: Double, unit: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.Black.copy(alpha = .035f), RoundedCornerShape(10.dp)).padding(10.dp)) {
        Text(label, style = PrFont.caption)
        Text("${"%.2f".format(value)} $unit", color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun DualTraceChart(result: DistanceLapCompareResult, metric: LapCompareMetric, selectedIndex: Int, onScrub: (Int) -> Unit) {
    val values = result.samples.flatMap { listOf(it.metricsA[metric] ?: 0.0, it.metricsB[metric] ?: 0.0) }
    val lo = values.minOrNull() ?: 0.0; val hi = values.maxOrNull() ?: 1.0; val range = (hi - lo).coerceAtLeast(0.01)
    val colorA = Pr.coral
    val colorB = Color(0xFF2E9E5B)
    val cursorColor = Pr.muted
    val modifier = Modifier.fillMaxWidth().height(210.dp).pointerInput(result) {
        detectDragGestures(
            onDragStart = { offset -> onScrub(((offset.x / size.width) * result.samples.lastIndex).roundToInt().coerceIn(result.samples.indices)) },
            onDrag = { change, _ ->
                change.consume()
                onScrub(((change.position.x / size.width) * result.samples.lastIndex).roundToInt().coerceIn(result.samples.indices))
            }
        )
    }
    Canvas(modifier) {
        fun point(index: Int, value: Double) = Offset(index * size.width / (result.samples.size - 1), size.height - ((value - lo) / range * size.height).toFloat())
        result.samples.indices.zipWithNext().forEach { (a, b) ->
            drawLine(colorA, point(a, result.samples[a].metricsA[metric] ?: 0.0), point(b, result.samples[b].metricsA[metric] ?: 0.0), 5f)
            drawLine(colorB, point(a, result.samples[a].metricsB[metric] ?: 0.0), point(b, result.samples[b].metricsB[metric] ?: 0.0), 5f)
        }
        val index = selectedIndex.coerceIn(result.samples.indices)
        val a = point(index, result.samples[index].metricsA[metric] ?: 0.0)
        val b = point(index, result.samples[index].metricsB[metric] ?: 0.0)
        drawLine(cursorColor, Offset(a.x, 0f), Offset(a.x, size.height), 2f)
        drawCircle(Color.White, 10f, a); drawCircle(colorA, 7f, a)
        drawCircle(Color.White, 10f, b); drawCircle(colorB, 7f, b)
    }
}

@Composable
private fun DeltaStrip(result: DistanceLapCompareResult) {
    val peak = result.samples.maxOfOrNull { kotlin.math.abs(it.deltaSeconds) }?.coerceAtLeast(0.05) ?: 1.0
    val axisColor = Pr.muted
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        val center = size.height / 2
        val barWidth = size.width / result.samples.size
        result.samples.forEachIndexed { index, sample ->
            val height = (kotlin.math.abs(sample.deltaSeconds) / peak * center).toFloat()
            val top = if (sample.deltaSeconds <= 0) center - height else center
            drawRect(if (sample.deltaSeconds <= 0) Color(0xFF2E9E5B) else Color(0xFFD33B2C), Offset(index * barWidth, top), androidx.compose.ui.geometry.Size(barWidth.coerceAtLeast(1f), height))
        }
        drawLine(axisColor, Offset(0f, center), Offset(size.width, center), 1f)
    }
}
