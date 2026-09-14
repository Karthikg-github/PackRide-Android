package com.karthik.packride.ui.screens

import android.location.Location
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.karthik.packride.location.SharedLocationManager
import com.karthik.packride.nav.PendingTurnByTurn
import com.karthik.packride.ui.theme.PrCoral
import com.karthik.packride.waypoints.WaypointsManager
import kotlin.math.roundToInt

/**
 * Lightweight in-app navigation: pick a saved waypoint, show bearing line + distance.
 * External Google Maps turn-by-turn still available via button.
 */
@Composable
fun InAppNavScreen() {
    val context = LocalContext.current
    val manager = remember { WaypointsManager(context) }
    // Nav tab shows the solo plan's routeable stops/destination (no rideCode
    // switch here — group rides navigate from within Group Ride itself).
    val waypoints = remember { manager.routeableWaypoints }
    val loc by SharedLocationManager.get().location.collectAsState()
    var destIndex by remember { mutableStateOf(0) }
    var destLat by remember { mutableStateOf<Double?>(null) }
    var destLng by remember { mutableStateOf<Double?>(null) }
    var destName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        SharedLocationManager.get().startUpdating("inAppNav")
    }
    DisposableEffect(Unit) {
        onDispose { SharedLocationManager.get().stopUpdating("inAppNav") }
    }

    val my = loc
    val dLat = destLat
    val dLng = destLng
    val distM = if (my != null && dLat != null && dLng != null) {
        val results = FloatArray(1)
        Location.distanceBetween(my.latitude, my.longitude, dLat, dLng, results)
        results[0]
    } else null

    val start = my?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(37.77, -122.42)
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(start, 13f)
    }

    Column(Modifier.fillMaxSize()) {
        Text("Navigate", style = MaterialTheme.typography.headlineMedium, color = PrCoral, modifier = Modifier.padding(16.dp))
        if (waypoints.isNotEmpty()) {
            val w = waypoints[destIndex.coerceIn(0, waypoints.lastIndex)]
            Text("Waypoint: ${w.name}", modifier = Modifier.padding(horizontal = 16.dp))
            Button(
                onClick = {
                    destLat = w.latitude
                    destLng = w.longitude
                    destName = w.name
                    destIndex = (destIndex + 1) % waypoints.size
                },
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) { Text("Use next waypoint (${w.name})") }
        } else {
            Text("Plan a route under More → Waypoints first.", modifier = Modifier.padding(16.dp))
        }
        distM?.let {
            Text(
                if (it < 1000) "${it.roundToInt()} m to $destName"
                else "${com.karthik.packride.data.MeasurementUnits.distanceMeters(it.toDouble())} to $destName",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleMedium
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            GoogleMap(Modifier.fillMaxSize(), cameraPositionState = camera) {
                my?.let {
                    Marker(state = MarkerState(LatLng(it.latitude, it.longitude)), title = "You")
                }
                if (dLat != null && dLng != null) {
                    Marker(state = MarkerState(LatLng(dLat, dLng)), title = destName)
                    my?.let {
                        Polyline(
                            points = listOf(
                                LatLng(it.latitude, it.longitude),
                                LatLng(dLat, dLng)
                            ),
                            color = PrCoral,
                            width = 10f
                        )
                    }
                }
            }
        }
        if (dLat != null && dLng != null) {
            Button(
                onClick = { PendingTurnByTurn.request(dLat, dLng, destName) },
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) { Text("Start Turn-by-Turn Navigation") }
        }
    }
}
