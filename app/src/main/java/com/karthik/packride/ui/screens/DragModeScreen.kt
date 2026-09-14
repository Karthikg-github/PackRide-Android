package com.karthik.packride.ui.screens

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.karthik.packride.data.MeasurementSystem
import com.karthik.packride.data.MeasurementUnits
import com.karthik.packride.location.SharedLocationManager
import com.karthik.packride.ui.theme.Pr
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.UUID

private data class DragRun(
    val id: String, val dateMs: Long, val distanceMeters: Double, val elapsed: Double,
    val trapMph: Double, val zeroToSixty: Double?, val eighth: Double?, val quarter: Double?
)

@Composable
fun DragModeScreen(onOpenCircuit: () -> Unit) {
    val context = LocalContext.current
    val locationManager = remember { SharedLocationManager.get() }
    val fix by locationManager.location.collectAsState()
    val speedMph = ((fix?.speed ?: 0f).coerceAtLeast(0f) * 2.236936).toDouble()
    var armed by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var startMs by remember { mutableStateOf<Long?>(null) }
    var elapsed by remember { mutableDoubleStateOf(0.0) }
    var distanceMeters by remember { mutableDoubleStateOf(0.0) }
    var lastFix by remember { mutableStateOf<Location?>(null) }
    var route by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var zeroToSixty by remember { mutableStateOf<Double?>(null) }
    var eighth by remember { mutableStateOf<Double?>(null) }
    var quarter by remember { mutableStateOf<Double?>(null) }
    var history by remember { mutableStateOf(loadDragRuns(context)) }
    var showHistory by remember { mutableStateOf(false) }
    val fallback = fix?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(37.33, -122.03)
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(fallback, 17f) }
    val ready = (fix?.accuracy ?: 999f) <= 20f && speedMph < 3

    fun reset() {
        startMs = null; elapsed = 0.0; distanceMeters = 0.0; lastFix = null; route = emptyList()
        zeroToSixty = null; eighth = null; quarter = null
    }
    fun finish() {
        if (!running) return
        running = false; armed = false
        val run = DragRun(UUID.randomUUID().toString(), System.currentTimeMillis(), distanceMeters, elapsed, speedMph, zeroToSixty, eighth, quarter)
        history = listOf(run) + history
        saveDragRuns(context, history)
    }

    DisposableEffect(Unit) {
        locationManager.startUpdating(SharedLocationManager.REASON_LAP_TRACKING)
        onDispose { locationManager.stopUpdating(SharedLocationManager.REASON_LAP_TRACKING) }
    }
    LaunchedEffect(running, startMs) {
        while (running) { elapsed = (System.currentTimeMillis() - (startMs ?: System.currentTimeMillis())) / 1000.0; delay(50) }
    }
    LaunchedEffect(fix?.time) {
        val current = fix ?: return@LaunchedEffect
        if (current.accuracy < 0 || current.accuracy > 25) return@LaunchedEffect
        if (armed && !running && current.speed >= 1.5f) {
            armed = false; running = true; startMs = current.time; lastFix = current; route = listOf(LatLng(current.latitude, current.longitude))
            return@LaunchedEffect
        }
        if (!running) return@LaunchedEffect
        lastFix?.let { previous -> if (current.time > previous.time) current.distanceTo(previous).toDouble().takeIf { it < 100 }?.let { distanceMeters += it } }
        lastFix = current; route = route + LatLng(current.latitude, current.longitude)
        elapsed = (current.time - (startMs ?: current.time)) / 1000.0
        if (zeroToSixty == null && speedMph >= 60) zeroToSixty = elapsed
        if (eighth == null && distanceMeters >= 201.168) eighth = elapsed
        if (distanceMeters >= 402.336) { quarter = quarter ?: elapsed; finish() }
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(), cameraPositionState = camera,
            properties = MapProperties(mapType = MapType.HYBRID),
            uiSettings = MapUiSettings(zoomControlsEnabled = false)
        ) { if (route.size > 1) Polyline(route, color = Pr.coral, width = 9f) }
        Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)).padding(horizontal = 20.dp, vertical = 22.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("DRAG MODE", color = Pr.coral, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.6.sp)
                    Text(if (running) "RUNNING" else if (armed) "ARMED" else "Straight-line timing", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(if (running) "Timing from detected movement" else if (armed) "Hold still, then launch when ready" else if (ready) "Ready for a closed-course run" else "Stop and wait for an accurate GPS lock", color = Color.White.copy(alpha = .72f), fontSize = 12.sp)
                }
                TopPill("CIRCUIT", onOpenCircuit)
                Spacer(Modifier.width(8.dp))
                TopPill("HISTORY") { showHistory = true }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .7f), RoundedCornerShape(22.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row { DragMetric("%.1f".format(elapsed), "SECONDS", Modifier.weight(1f)); DragMetric(if (MeasurementUnits.current == MeasurementSystem.METRIC) "%.0f".format(distanceMeters) else "%.3f".format(distanceMeters / 1609.344), if (MeasurementUnits.current == MeasurementSystem.METRIC) "METERS" else "MILES", Modifier.weight(1f)); DragMetric(MeasurementUnits.speedMph(speedMph), "SPEED", Modifier.weight(1f)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Split("0–60", zeroToSixty, Modifier.weight(1f)); Split("⅛ MILE", eighth, Modifier.weight(1f)); Split("¼ MILE", quarter, Modifier.weight(1f)) }
                Text("PHONE GPS ESTIMATE · Closed course only. Results may differ from professional timing, especially launch and finish detection.", color = Color.White.copy(alpha = .62f), fontSize = 9.sp)
                Text(if (running) "End Run" else if (armed) "Disarm" else "Arm Run", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black, modifier = Modifier.fillMaxWidth().background(if (running) Color.Red else if (ready || armed) Pr.coral else Color.Gray, RoundedCornerShape(18.dp)).clickable(enabled = running || armed || ready) { if (running) finish() else if (armed) armed = false else { reset(); armed = true } }.padding(vertical = 17.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }

    if (showHistory) AlertDialog(onDismissRequest = { showHistory = false }, title = { Text("Drag Runs") }, text = { LazyColumn { items(history) { run -> Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text(run.quarter?.let { "¼ mile · %.2fs".format(it) } ?: "Drag run · %.2fs".format(run.elapsed), fontWeight = FontWeight.Bold); Text("${DateFormat.getDateTimeInstance().format(Date(run.dateMs))} · %.0f m · ${MeasurementUnits.speedMph(run.trapMph)} trap", fontSize = 12.sp) } } } }, confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Done") } })
}

@Composable private fun TopPill(text: String, action: () -> Unit) { Text(text, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.background(Color.Black.copy(alpha = .55f), RoundedCornerShape(50)).clickable(onClick = action).padding(horizontal = 11.dp, vertical = 11.dp)) }
@Composable private fun DragMetric(value: String, label: String, modifier: Modifier) { Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp); Text(label, color = Color.White.copy(alpha = .55f), fontSize = 8.sp, fontWeight = FontWeight.Black) } }
@Composable private fun Split(label: String, value: Double?, modifier: Modifier) { Column(modifier.background(Color.White.copy(alpha = .08f), RoundedCornerShape(12.dp)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(value?.let { "%.2fs".format(it) } ?: "—", color = Color.White, fontWeight = FontWeight.Bold); Text(label, color = Color.White.copy(alpha = .55f), fontSize = 8.sp, fontWeight = FontWeight.Black) } }

private fun loadDragRuns(context: android.content.Context): List<DragRun> = runCatching {
    val array = JSONArray(context.getSharedPreferences("packride_drag", 0).getString("runs", "[]"))
    (0 until array.length()).map { i -> array.getJSONObject(i).let { DragRun(it.getString("id"), it.getLong("date"), it.getDouble("distance"), it.getDouble("elapsed"), it.getDouble("trap"), it.optDoubleOrNull("sixty"), it.optDoubleOrNull("eighth"), it.optDoubleOrNull("quarter")) } }
}.getOrDefault(emptyList())
private fun saveDragRuns(context: android.content.Context, runs: List<DragRun>) { val array = JSONArray(); runs.forEach { r -> array.put(JSONObject().put("id", r.id).put("date", r.dateMs).put("distance", r.distanceMeters).put("elapsed", r.elapsed).put("trap", r.trapMph).put("sixty", r.zeroToSixty).put("eighth", r.eighth).put("quarter", r.quarter)) }; context.getSharedPreferences("packride_drag", 0).edit().putString("runs", array.toString()).apply() }
private fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) optDouble(key) else null
