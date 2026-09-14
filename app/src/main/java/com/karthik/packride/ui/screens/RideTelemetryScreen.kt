package com.karthik.packride.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.karthik.packride.replay.GpxPointParser
import com.karthik.packride.replay.ParsedGpxPoint
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrFont
import java.io.File
import kotlin.math.*

private enum class TelemetryMetric(val label: String, val color: Color) {
    SPEED("Speed", Color(0xFF258B91)), LEAN("Lean Angle", Color(0xFF1E88E5)),
    BRAKE("Braking G", Color(0xFFFB8C00)), ACCEL("Acceleration G", Color(0xFF43A047)),
    ELEVATION("Elevation", Color(0xFF8E24AA))
}

@Composable
fun RideTelemetryScreen(file: File, rideName: String, rideDate: String, onClose: () -> Unit) {
    val points = remember(file) { GpxPointParser.parse(file) }
    val signedG = remember(points) { signedGForces(points) }
    val distances = remember(points) { cumulativeDistances(points) }
    var selectedMetric by remember { mutableStateOf(TelemetryMetric.SPEED) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    val camera = rememberCameraPositionState()

    LaunchedEffect(points) {
        if (points.isNotEmpty()) {
            val bounds = LatLngBounds.builder().apply { points.forEach { include(LatLng(it.lat, it.lng)) } }.build()
            runCatching { camera.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120)) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(), cameraPositionState = camera,
            properties = MapProperties(mapType = mapType),
            uiSettings = MapUiSettings(zoomControlsEnabled = false),
            onMapClick = { coordinate -> selectedIndex = GpxPointParser.nearestIndex(coordinate.latitude, coordinate.longitude, points) }
        ) {
            if (points.size > 1) Polyline(points = points.map { LatLng(it.lat, it.lng) }, color = selectedMetric.color, width = 12f)
            selectedIndex?.let { index ->
                points.getOrNull(index)?.let { Marker(state = MarkerState(LatLng(it.lat, it.lng)), title = formatted(selectedMetric, index, points, signedG)) }
            }
        }
        Box(Modifier.fillMaxWidth().height(170.dp).background(Color.Black.copy(alpha = .54f)))
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(38.dp).background(Color.Black.copy(alpha = .35f), CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, "Close telemetry", tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("RIDE TELEMETRY", color = Color.White.copy(alpha = .72f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text(rideName, color = Color.White, style = PrFont.heading)
                Text(rideDate, color = Color.White.copy(alpha = .68f), style = PrFont.caption)
            }
            Text(
                if (mapType == MapType.NORMAL) "MAP" else "SAT",
                color = Color.White, style = PrFont.caption,
                modifier = Modifier.background(Color.Black.copy(alpha = .48f), CircleShape)
                    .clickable { mapType = if (mapType == MapType.NORMAL) MapType.HYBRID else MapType.NORMAL }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(horizontal = 14.dp)) {
                items(TelemetryMetric.entries) { metric ->
                    Text(metric.label, color = Color.White, style = PrFont.caption,
                        modifier = Modifier.background(if (metric == selectedMetric) metric.color else Color.Black, CircleShape)
                            .clickable { selectedMetric = metric }.padding(horizontal = 12.dp, vertical = 10.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).background(Pr.cardBg.copy(alpha = .95f), RoundedCornerShape(22.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val index = selectedIndex
                if (index == null) {
                    Text("Tap the route to inspect telemetry", style = PrFont.subheading)
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(formatted(selectedMetric, index, points, signedG), color = selectedMetric.color, style = PrFont.heading)
                        Text(selectedMetric.label.uppercase(), style = PrFont.caption)
                    }
                    Text("${com.karthik.packride.data.MeasurementUnits.distanceMeters(distances.getOrNull(index) ?: 0.0)}\nINTO RIDE", style = PrFont.bodySmall)
                }
            }
        }
    }
}

private fun formatted(metric: TelemetryMetric, index: Int, points: List<ParsedGpxPoint>, g: List<Double>): String {
    val p = points.getOrNull(index) ?: return "--"
    return when (metric) {
        TelemetryMetric.SPEED -> com.karthik.packride.data.MeasurementUnits.speedMph(p.speed * 2.23694)
        TelemetryMetric.LEAN -> "%.0f°".format(abs(p.lean))
        TelemetryMetric.BRAKE -> "%.2fg".format(max(0.0, -(g.getOrNull(index) ?: 0.0)))
        TelemetryMetric.ACCEL -> "%.2fg".format(max(0.0, g.getOrNull(index) ?: 0.0))
        TelemetryMetric.ELEVATION -> com.karthik.packride.data.MeasurementUnits.elevationMeters(p.ele)
    }
}

private fun signedGForces(points: List<ParsedGpxPoint>) = points.mapIndexed { index, point ->
    if (index == 0) 0.0 else {
        val previous = points[index - 1]; val dt = (point.timeMs - previous.timeMs) / 1000.0
        if (dt > 0 && dt < 5) ((point.speed - previous.speed) / dt) / 9.80665 else 0.0
    }
}

private fun cumulativeDistances(points: List<ParsedGpxPoint>): List<Double> {
    var total = 0.0
    return points.mapIndexed { i, p ->
        if (i > 0) {
            val a = points[i - 1]; val dLat = Math.toRadians(p.lat - a.lat); val dLon = Math.toRadians(p.lng - a.lng)
            val x = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(p.lat)) * sin(dLon / 2).pow(2)
            total += 12_742_000.0 * asin(min(1.0, sqrt(x)))
        }
        total
    }
}
