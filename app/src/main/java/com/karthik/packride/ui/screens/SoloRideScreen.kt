package com.karthik.packride.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.karthik.packride.auth.AuthManager
import com.karthik.packride.community.CommunityMembershipStore
import com.karthik.packride.ride.SoloRideSession
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrCoral
import com.karthik.packride.ui.theme.PrFont
import com.karthik.packride.ui.theme.PrimaryButton
import com.karthik.packride.ui.theme.PrMapStylePicker

// Aug 31, 2026 — visual restyle pass (Solo Ride was one of the 5 screens that
// skipped the app-wide Pr design-system pass). Recording state, GPS/location
// logic and the Community Share Sheet's sharing wiring are unchanged; only
// chrome (the HUD panel, the Start/End buttons, and the share-sheet dialog's
// styling) now uses Pr tokens/components instead of raw MaterialTheme
// defaults and ad hoc colors, matching Garage/Home/etc.
private val statusRed = Color(0xFFD33B2C)

@Composable
fun SoloRideScreen(auth: AuthManager) {
    val context = LocalContext.current
    val session = remember { SoloRideSession(context) }
    val active by session.isActive.collectAsState()
    val elapsed by session.elapsedSeconds.collectAsState()
    val speed by session.speedMph.collectAsState()
    val distance by session.distanceMiles.collectAsState()
    val maxSpeed by session.maxSpeedMph.collectAsState()
    val location by session.location.collectAsState()
    val summary by session.summary.collectAsState()
    val path by session.trackPath.collectAsState()

    var confirmEnd by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var communitiesToShare by remember { mutableStateOf<Set<String>>(emptySet()) }
    val communityStore = remember { CommunityMembershipStore.get() }
    val myCommunities by communityStore.myCommunities.collectAsState()

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

    LaunchedEffect(Unit) { ensurePermissions() }

    DisposableEffect(Unit) {
        onDispose { session.cancelIfNeeded() }
    }

    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(37.33, -122.03), 14f)
    }

    LaunchedEffect(location?.latitude, location?.longitude) {
        val loc = location ?: return@LaunchedEffect
        cameraState.animate(
            CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15f)
        )
    }

    val hasFine = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    var mapType by remember { mutableStateOf(MapType.HYBRID) }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(mapType = mapType, isMyLocationEnabled = hasFine),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = true)
        ) {
            if (path.size >= 2) {
                Polyline(
                    points = path.map { (lat, lng) -> LatLng(lat, lng) },
                    color = PrCoral,
                    width = 10f
                )
            }
            location?.let { loc ->
                Marker(
                    state = MarkerState(LatLng(loc.latitude, loc.longitude)),
                    title = "You"
                )
            }
        }

        // Top HUD
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .background(Pr.tabBar.copy(alpha = 0.62f), RoundedCornerShape(Pr.RadiusMedium))
                .padding(16.dp)
        ) {
            Text(
                text = if (com.karthik.packride.data.MeasurementUnits.current == com.karthik.packride.data.MeasurementSystem.METRIC) "%.0f".format(speed * 1.609344) else "%.0f".format(speed),
                color = Color.White,
                fontSize = 56.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                if (com.karthik.packride.data.MeasurementUnits.current == com.karthik.packride.data.MeasurementSystem.METRIC) "km/h" else "mph",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HudStat(formatDuration(elapsed), "TIME")
                HudStat(
                    if (com.karthik.packride.data.MeasurementUnits.current == com.karthik.packride.data.MeasurementSystem.METRIC) "%.1f".format(distance * 1.609344) else "%.1f".format(distance),
                    if (com.karthik.packride.data.MeasurementUnits.current == com.karthik.packride.data.MeasurementSystem.METRIC) "KM" else "MI"
                )
                HudStat("%.0f".format(maxSpeed), "TOP")
            }
        }

        // Bottom controls
        PrMapStylePicker(
            selected = mapType,
            onSelected = { mapType = it },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 184.dp, end = 12.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!active) {
                PrimaryButton(
                    text = "Start Solo Ride",
                    onClick = {
                        if (ensurePermissions()) {
                            if (myCommunities.isNotEmpty()) {
                                communitiesToShare = emptySet()
                                showShareSheet = true
                            } else {
                                session.start(auth.prefsSnapshot.riderName)
                            }
                        }
                    }
                )
            } else {
                Button(
                    onClick = { confirmEnd = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Pr.RadiusButton),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = statusRed,
                        contentColor = Color.White
                    )
                ) {
                    Text("End Ride", style = PrFont.button)
                }
            }
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End ride?") },
            text = { Text("You'll see a summary of distance, speed, and duration.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmEnd = false
                        session.end()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = statusRed,
                        contentColor = Color.White
                    )
                ) { Text("End Ride", style = PrFont.button) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false }) { Text("Keep Riding") }
            }
        )
    }

    if (showShareSheet) {
        AlertDialog(
            onDismissRequest = { showShareSheet = false },
            title = { Text("Share this ride?") },
            text = {
                Column {
                    Text(
                        "Let your communities see you're riding and where you are.",
                        color = Pr.muted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    myCommunities.forEach { community ->
                        val isSelected = communitiesToShare.contains(community.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    communitiesToShare = if (isSelected) {
                                        communitiesToShare - community.id
                                    } else {
                                        communitiesToShare + community.id
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (isSelected) "●" else "○",
                                color = if (isSelected) PrCoral else Pr.muted
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(community.name)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showShareSheet = false
                    session.start(auth.prefsSnapshot.riderName, communitiesToShare)
                }) { Text("Share & Start Ride") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showShareSheet = false
                    session.start(auth.prefsSnapshot.riderName, emptySet())
                }) { Text("Don't Share — Start Ride") }
            }
        )
    }

    summary?.let { ride ->
        AlertDialog(
            onDismissRequest = { session.dismissSummary() },
            title = { Text("Ride complete") },
            text = {
                Text(
                    "Distance: ${com.karthik.packride.data.MeasurementUnits.distanceMiles(ride.distanceMiles, 2)}\n" +
                        "Top speed: ${com.karthik.packride.data.MeasurementUnits.speedMph(ride.maxSpeedMph)}\n" +
                        "Duration: ${ride.durationFormatted}\nGPX: ${ride.gpxFileName ?: "none"}"
                )
            },
            confirmButton = {
                Button(onClick = { session.dismissSummary() }) { Text("Done") }
            }
        )
    }
}

@Composable
private fun HudStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
