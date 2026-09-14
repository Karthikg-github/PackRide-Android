package com.karthik.packride.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.karthik.packride.auth.AuthManager
import com.karthik.packride.friends.FriendsManager
import com.karthik.packride.friends.RiderProfile
import com.karthik.packride.group.GroupRideFirebase
import com.karthik.packride.group.PendingGroupRide
import com.karthik.packride.invite.RideInviteManager
import com.karthik.packride.location.SharedLocationManager
import com.karthik.packride.nav.PendingTurnByTurn
import com.karthik.packride.nav.PendingWaypoints
import com.karthik.packride.schedule.ScheduledRideManager
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrCard
import com.karthik.packride.ui.theme.PrMapStylePicker
import com.karthik.packride.ui.theme.PrCoral
import com.karthik.packride.ui.theme.PrFont
import com.karthik.packride.ui.theme.PrimaryButton
import com.karthik.packride.waypoints.DirectionsService
import com.karthik.packride.waypoints.Waypoint
import com.karthik.packride.waypoints.WaypointType
import com.karthik.packride.waypoints.WaypointsManager
import com.karthik.packride.weather.WeatherManager
import com.karthik.packride.weather.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

// MARK: - Real route-planning screen: live map, typed waypoints (start
// override / stop / destination), press-and-drag stop reordering, live
// Places Autocomplete address search (choose-on-map and long-press-to-add
// still work too), a routed polyline via the Directions API, and
// Weather/Share/Schedule actions once 2+ stops are planned. Kotlin port of
// iOS WaypointsView.swift.
//
// Aug 31, 2026 — closed two of the four deliberate scope-skips this screen
// shipped with (see android-build-plan.md): stop reordering is now a real
// hand-rolled long-press-and-drag gesture (ReorderableStopsList below —
// pointerInput + detectDragGesturesAfterLongPress + graphicsLayer, no new
// dependency), matching iOS's List .onMove; and address search is now live
// Places Autocomplete-as-you-type (LocationPickerSheetContent below),
// matching iOS's MKLocalSearchCompleter, debounced ~300ms so it doesn't
// fire a request per keystroke. Places.initialize() happens once app-wide
// in PackRideApp.kt, reusing the same Maps/Directions API key — no second
// key to manage. Two scope-skips remain, still deliberate:
//  1. Map pins are standard colored Google Maps markers + title callouts,
//     not custom numbered-circle bitmaps.
//  2. No ambient "nearby gas/food" POI overlay while panning (also a
//     Places API feature) — the rider can still add a fuel/food/etc. stop
//     by searching or tapping the map directly.
private enum class WaypointSheet { START, STOP, DESTINATION, DROP_PIN, RIDE_TYPE, SHARE, WEATHER, SCHEDULE }

private data class PickerTarget(val title: String, val accent: Color, val onPicked: (String, String, Double, Double) -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaypointsScreen(auth: AuthManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { WaypointsManager(context) }
    val waypoints by manager.waypointsFlow.collectAsState()
    val myLocation by SharedLocationManager.get().location.collectAsState()

    var boundRideCode by remember { mutableStateOf("") }
    var boundRideIsLeader by remember { mutableStateOf(false) }
    var activeSheet by remember { mutableStateOf<WaypointSheet?>(null) }
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }
    var droppedLat by remember { mutableStateOf<Double?>(null) }
    var droppedLng by remember { mutableStateOf<Double?>(null) }
    var choosingOnMainMap by remember { mutableStateOf(false) }
    var routePolyline by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var routeUnavailable by remember { mutableStateOf(false) }
    var mapLoaded by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf(MapType.HYBRID) }

    val stops = waypoints.filter { !it.isDestination && !it.isStartOverride }
    val destination = waypoints.firstOrNull { it.isDestination }
    val startOverride = waypoints.firstOrNull { it.isStartOverride }
    val routeable = waypoints.filterNot { it.isStartOverride }
    val plannedPoints = if (destination != null) stops + destination else stops
    val hasEnoughStopsPlanned = plannedPoints.size >= 2

    LaunchedEffect(Unit) {
        PendingWaypoints.pending.value?.let { request ->
            if (request.rideCode.isNotBlank()) {
                boundRideCode = request.rideCode
                boundRideIsLeader = request.isLeader
                manager.switchRideCode(request.rideCode)
            }
            PendingWaypoints.clear()
        }
        SharedLocationManager.get().startUpdating("waypointsMap")
    }
    DisposableEffect(Unit) {
        onDispose { SharedLocationManager.get().stopUpdating("waypointsMap") }
    }

    // Straight-line ("as the crow flies") distance across the whole planned
    // route — NOT routed mileage, always labeled as an estimate. Matches
    // iOS's crowFliesDistanceMiles exactly (same honesty reasoning).
    val crowFliesMiles = remember(startOverride, myLocation, plannedPoints) {
        val start = startOverride?.let { LatLng(it.latitude, it.longitude) }
            ?: myLocation?.let { LatLng(it.latitude, it.longitude) }
        val points = (if (start != null) listOf(start) else emptyList()) +
            plannedPoints.map { LatLng(it.latitude, it.longitude) }
        if (points.size < 2) null else {
            var totalMeters = 0f
            for (i in 0 until points.size - 1) {
                val results = FloatArray(1)
                Location.distanceBetween(
                    points[i].latitude, points[i].longitude,
                    points[i + 1].latitude, points[i + 1].longitude, results
                )
                totalMeters += results[0]
            }
            totalMeters * 0.000621371
        }
    }

    // Real routed polyline, leg by leg — only recomputed when the actual
    // route (start + ordered waypoints) changes, same signature-gating idea
    // as iOS's lastWaypointSignature (skip redundant Directions calls on
    // unrelated recompositions).
    val routeSignature = remember(startOverride, myLocation, routeable) {
        val startSig = startOverride?.let { "${it.latitude},${it.longitude}" }
            ?: myLocation?.let { "${it.latitude},${it.longitude}" } ?: "none"
        startSig + "|" + routeable.joinToString("|") { "${it.id}:${it.latitude},${it.longitude}" }
    }
    LaunchedEffect(routeSignature) {
        val start = startOverride?.let { LatLng(it.latitude, it.longitude) }
            ?: myLocation?.let { LatLng(it.latitude, it.longitude) }
        val points = (if (start != null) listOf(start) else emptyList()) +
            routeable.map { LatLng(it.latitude, it.longitude) }
        routePolyline = if (points.size >= 2) DirectionsService.route(context, points) else emptyList()
        routeUnavailable = points.size >= 2 && routePolyline.isEmpty()
    }

    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(37.33, -122.03), 12f)
    }
    // Center on the rider once, as soon as a fix arrives — mirrors iOS's
    // hasCenteredOnUser (never fights the rider's own pan/zoom afterward).
    var hasCenteredOnUser by remember { mutableStateOf(false) }
    LaunchedEffect(myLocation) {
        val loc = myLocation ?: return@LaunchedEffect
        if (!hasCenteredOnUser) {
            hasCenteredOnUser = true
            cameraState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 13f))
        }
    }
    // Fit the camera to the whole route whenever it actually changes.
    LaunchedEffect(routeSignature, mapLoaded) {
        if (!mapLoaded) return@LaunchedEffect
        val start = startOverride?.let { LatLng(it.latitude, it.longitude) }
            ?: myLocation?.let { LatLng(it.latitude, it.longitude) }
        val fitPoints = (if (start != null) listOf(start) else emptyList()) +
            routeable.map { LatLng(it.latitude, it.longitude) }
        if (fitPoints.size >= 2) {
            val bounds = LatLngBounds.Builder().apply { fitPoints.forEach { include(it) } }.build()
            runCatching { cameraState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 140)) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(mapType = mapType, isMyLocationEnabled = false),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
            onMapLoaded = { mapLoaded = true },
            onMapClick = { latLng ->
                if (choosingOnMainMap) {
                    val target = pickerTarget
                    if (target != null) {
                        scope.launch {
                            val address = reverseGeocode(context, latLng.latitude, latLng.longitude)
                            target.onPicked(address, address, latLng.latitude, latLng.longitude)
                            choosingOnMainMap = false
                            pickerTarget = null
                        }
                    }
                }
            },
            onMapLongClick = { latLng ->
                droppedLat = latLng.latitude
                droppedLng = latLng.longitude
                activeSheet = WaypointSheet.DROP_PIN
            }
        ) {
            myLocation?.let {
                Marker(
                    state = MarkerState(LatLng(it.latitude, it.longitude)),
                    title = "You",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
                )
            }
            startOverride?.let { s ->
                Marker(
                    state = MarkerState(LatLng(s.latitude, s.longitude)),
                    title = s.name,
                    snippet = "Starting point",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)
                )
            }
            routeable.forEachIndexed { index, w ->
                Marker(
                    state = MarkerState(LatLng(w.latitude, w.longitude)),
                    title = "${index + 1}. ${w.name}",
                    snippet = if (w.isDestination) "Destination" else w.type.label,
                    icon = BitmapDescriptorFactory.defaultMarker(markerHue(w))
                )
            }
            if (routePolyline.size >= 2) {
                Polyline(points = routePolyline, color = PrCoral, width = 9f)
            }
        }

        Column(Modifier.fillMaxSize()) {
            routePanel(
                manager = manager,
                boundRideCode = boundRideCode,
                onLeaveGroup = { boundRideCode = ""; manager.switchRideCode("") },
                stops = stops,
                destination = destination,
                startOverride = startOverride,
                hasEnoughStopsPlanned = hasEnoughStopsPlanned,
                crowFliesMiles = crowFliesMiles,
                onPickStart = {
                    pickerTarget = PickerTarget("Starting Point", PrCoral) { name, address, lat, lng ->
                        manager.setStart(name, address, lat, lng)
                    }
                    activeSheet = WaypointSheet.START
                },
                onClearStart = { manager.clearStart() },
                onPickStop = {
                    pickerTarget = PickerTarget("Add Stop", PrCoral) { name, address, lat, lng ->
                        manager.addStop(name, address, WaypointType.MEETUP, lat, lng)
                    }
                    activeSheet = WaypointSheet.STOP
                },
                onMoveStop = { from, to -> manager.moveStop(from, to) },
                onRemoveStop = { manager.removeWaypoint(it) },
                onPickDestination = {
                    pickerTarget = PickerTarget("Choose Destination", PrCoral) { name, address, lat, lng ->
                        manager.setDestination(name, address, lat, lng)
                    }
                    activeSheet = WaypointSheet.DESTINATION
                },
                onClearDestination = { manager.clearDestination() },
                onWeather = { activeSheet = WaypointSheet.WEATHER },
                onShare = { activeSheet = WaypointSheet.SHARE },
                onSchedule = { activeSheet = WaypointSheet.SCHEDULE }
            )
            if (choosingOnMainMap) {
                Text(
                    "Tap the map to place this point",
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .background(Pr.inkFixed, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = Color.White,
                    style = PrFont.caption
                )
            } else if (routeUnavailable) {
                Text(
                    "Road route unavailable. Check Directions API access and try again.",
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .background(Pr.cardBg, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    color = Pr.coral,
                    style = PrFont.caption
                )
            }
            Spacer(Modifier.weight(1f))
            bottomButtons(
                routeable = routeable,
                destination = destination,
                onNavigate = {
                    if (routeable.isEmpty()) return@bottomButtons
                    val dest = routeable.last()
                    val stops = routeable.dropLast(1).map { it.latitude to it.longitude }
                    PendingTurnByTurn.request(dest.latitude, dest.longitude, dest.name, stops)
                },
                onDone = {
                    manager.saveWaypoints()
                    if (boundRideCode.isNotEmpty()) {
                        PendingGroupRide.request(boundRideCode, boundRideIsLeader)
                    } else if (routeable.isNotEmpty()) {
                        activeSheet = WaypointSheet.RIDE_TYPE
                    }
                }
            )
        }

        PrMapStylePicker(
            selected = mapType,
            onSelected = { mapType = it },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp).zIndex(2f)
        )
    }

    val sheetState = rememberModalBottomSheetState()
    when (activeSheet) {
        WaypointSheet.START, WaypointSheet.STOP, WaypointSheet.DESTINATION -> {
            val target = pickerTarget
            if (target != null) {
                ModalBottomSheet(onDismissRequest = { activeSheet = null }, sheetState = sheetState) {
                    LocationPickerSheetContent(
                        title = target.title,
                        onSelect = { name, address, lat, lng ->
                            target.onPicked(name, address, lat, lng)
                            activeSheet = null
                        },
                        onChooseOnMap = {
                            activeSheet = null
                            choosingOnMainMap = true
                        }
                    )
                }
            }
        }
        WaypointSheet.DROP_PIN -> {
            val lat = droppedLat
            val lng = droppedLng
            if (lat != null && lng != null) {
                ModalBottomSheet(onDismissRequest = { activeSheet = null }, sheetState = sheetState) {
                    DropPinSheetContent(
                        latitude = lat,
                        longitude = lng,
                        onAdd = { name, type ->
                            manager.addWaypoint(
                                Waypoint(name = name, type = type, latitude = lat, longitude = lng,
                                    address = "%.4f, %.4f".format(lat, lng))
                            )
                            activeSheet = null
                        },
                        onCancel = { activeSheet = null }
                    )
                }
            }
        }
        WaypointSheet.RIDE_TYPE -> {
            ModalBottomSheet(onDismissRequest = { activeSheet = null }, sheetState = sheetState) {
                RideTypeChoiceSheetContent(
                    stopCount = routeable.size,
                    onGroupRide = {
                        val code = GroupRideFirebase.generateRideCode()
                        boundRideCode = code
                        manager.bindToNewRideCode(code)
                        manager.saveWaypoints()
                        activeSheet = null
                        PendingGroupRide.request(code, isLeader = true)
                    },
                    onSoloRide = {
                        manager.switchRideCode("")
                        manager.saveWaypoints()
                        activeSheet = null
                        if (routeable.isNotEmpty()) {
                            val dest = routeable.last()
                            val stops = routeable.dropLast(1).map { it.latitude to it.longitude }
                            PendingTurnByTurn.request(dest.latitude, dest.longitude, dest.name, stops)
                        }
                    },
                    onKeepPlanning = { activeSheet = null }
                )
            }
        }
        WaypointSheet.SHARE -> {
            ModalBottomSheet(onDismissRequest = { activeSheet = null }, sheetState = sheetState) {
                ShareSheetContent(
                    context = context,
                    riderName = auth.prefsSnapshot.riderName,
                    destinationName = destination?.name ?: "",
                    stopCount = routeable.size,
                    promoteToRideCode = {
                        if (boundRideCode.isEmpty()) {
                            val code = GroupRideFirebase.generateRideCode()
                            boundRideCode = code
                            manager.bindToNewRideCode(code)
                            manager.saveWaypoints()
                        }
                        boundRideCode
                    },
                    onDone = { activeSheet = null }
                )
            }
        }
        WaypointSheet.WEATHER -> {
            ModalBottomSheet(onDismissRequest = { activeSheet = null }, sheetState = sheetState) {
                WeatherAheadSheetContent(startOverride = startOverride, myLocation = myLocation, stops = plannedPoints)
            }
        }
        WaypointSheet.SCHEDULE -> {
            ModalBottomSheet(onDismissRequest = { activeSheet = null }, sheetState = sheetState) {
                ScheduleSheetContent(
                    context = context,
                    creatorName = auth.prefsSnapshot.riderName,
                    defaultTitle = destination?.name ?: "PackRide",
                    meetupLocation = startOverride?.name ?: routeable.firstOrNull()?.name ?: "",
                    meetupLat = (startOverride ?: routeable.firstOrNull())?.latitude ?: 0.0,
                    meetupLng = (startOverride ?: routeable.firstOrNull())?.longitude ?: 0.0,
                    promoteToRideCode = {
                        if (boundRideCode.isEmpty()) {
                            val code = GroupRideFirebase.generateRideCode()
                            boundRideCode = code
                            manager.bindToNewRideCode(code)
                            manager.saveWaypoints()
                        }
                        boundRideCode
                    },
                    onDone = { activeSheet = null }
                )
            }
        }
        null -> Unit
    }
}

private fun markerHue(w: Waypoint): Float = when {
    w.isDestination -> BitmapDescriptorFactory.HUE_ROSE
    else -> when (w.type) {
        WaypointType.FUEL -> BitmapDescriptorFactory.HUE_RED
        WaypointType.FOOD -> BitmapDescriptorFactory.HUE_GREEN
        WaypointType.SCENIC -> BitmapDescriptorFactory.HUE_AZURE
        WaypointType.REST -> BitmapDescriptorFactory.HUE_VIOLET
        WaypointType.MEETUP -> BitmapDescriptorFactory.HUE_ORANGE
    }
}

// MARK: - Route panel (start / stops / destination / actions)

@Composable
private fun routePanel(
    manager: WaypointsManager,
    boundRideCode: String,
    onLeaveGroup: () -> Unit,
    stops: List<Waypoint>,
    destination: Waypoint?,
    startOverride: Waypoint?,
    hasEnoughStopsPlanned: Boolean,
    crowFliesMiles: Double?,
    onPickStart: () -> Unit,
    onClearStart: () -> Unit,
    onPickStop: () -> Unit,
    onMoveStop: (Int, Int) -> Unit,
    onRemoveStop: (String) -> Unit,
    onPickDestination: () -> Unit,
    onClearDestination: () -> Unit,
    onWeather: () -> Unit,
    onShare: () -> Unit,
    onSchedule: () -> Unit
) {
    // Aug 31, 2026 — visual restyle pass: PrCard (rounded corners + border +
    // lifted shadow) replacing the default flat Material3 Card, tinted
    // circular icon badges + tinted row backgrounds for Start/Destination
    // (matching Garage/Ride History's badge language and iOS's routePanel),
    // and Pr color/type tokens throughout. Purely chrome — the panel's
    // structure, state, and callbacks below are unchanged.
    PrCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(Pr.RadiusCard)),
        cornerRadius = Pr.RadiusCard
    ) {
        Column(Modifier.padding(14.dp)) {
            if (boundRideCode.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Pr.coralSoft, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Group code: $boundRideCode", color = Pr.coral, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onLeaveGroup) { Text("Leave", color = Pr.muted) }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Start
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Pr.teal.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(34.dp).background(Pr.teal.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Pr.teal, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    startOverride?.name ?: "My Location",
                    modifier = Modifier.weight(1f),
                    color = Pr.ink,
                    fontWeight = FontWeight.Medium
                )
                TextButton(onClick = onPickStart) { Text(if (startOverride != null) "Change" else "Set", color = Pr.teal) }
                if (startOverride != null) {
                    TextButton(onClick = onClearStart) { Text("Clear", color = Pr.muted) }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Pr.border)

            // Stops — real press-and-drag reordering (see ReorderableStopsList
            // below); Aug 31, 2026, replaces the old up/down IconButton pair.
            ReorderableStopsList(stops = stops, onMoveStop = onMoveStop, onRemoveStop = onRemoveStop)
            TextButton(onClick = onPickStop) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Pr.teal)
                Spacer(Modifier.width(4.dp))
                Text("Add Stop", color = Pr.teal)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Pr.border)

            // Destination
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Pr.coral.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(34.dp).background(Pr.coralSoft, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Pr.coral, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    destination?.name ?: "Choose Destination",
                    modifier = Modifier.weight(1f),
                    color = if (destination != null) Pr.ink else Pr.muted,
                    fontWeight = FontWeight.Medium
                )
                TextButton(onClick = onPickDestination) { Text(if (destination != null) "Change" else "Choose", color = Pr.coral) }
                if (destination != null) {
                    TextButton(onClick = onClearDestination) { Text("Clear", color = Pr.muted) }
                }
            }

            if (crowFliesMiles != null && crowFliesMiles > 0.0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "~${com.karthik.packride.data.MeasurementUnits.distanceMiles(crowFliesMiles)} as the crow flies",
                    style = PrFont.caption,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            if (hasEnoughStopsPlanned) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    planActionButton(icon = Icons.Filled.WbSunny, label = "Weather", color = Color(0xFFE8952F), onClick = onWeather, modifier = Modifier.weight(1f))
                    planActionButton(icon = Icons.Filled.Share, label = "Share", color = Pr.coral, onClick = onShare, modifier = Modifier.weight(1f))
                    planActionButton(icon = Icons.Filled.CalendarMonth, label = "Schedule", color = Pr.teal, onClick = onSchedule, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// MARK: - Weather/Share/Schedule action button — small tinted rounded-rect
// button with icon + label, port of iOS's planActionButton. Replaces the
// default OutlinedButton row so these read as the app's own component
// language instead of stock Material3 outlined buttons.
@Composable
private fun planActionButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = PrFont.caption, color = Pr.ink)
    }
}

// MARK: - Press-and-drag stop reordering
//
// Hand-rolled (no reorderable-list dependency, matching this project's
// avoid-new-deps-when-possible history): long-press a stop to pick it up,
// drag it up/down, and it swaps past the stops it passes over — the swap
// commits immediately to WaypointsManager (via onMoveStop, the same
// (from, to) -> manager.moveStop(from, to) callback the old up/down
// buttons used) every time the drag crosses a row boundary, exactly like
// iOS's List .onMove. The list's Column order is intentionally NOT
// re-sorted live — each row keeps the Column slot it started the gesture
// in (dragStartOrder) and only its graphicsLayer offset moves, so the row
// under the finger never loses its pointerInput gesture mid-drag. Other
// rows animate out of the way by comparing their live index (in the real,
// already-reordered `stops`) against their fixed drag-start slot.
@Composable
private fun ReorderableStopsList(
    stops: List<Waypoint>,
    onMoveStop: (Int, Int) -> Unit,
    onRemoveStop: (String) -> Unit
) {
    val density = LocalDensity.current
    val rowHeight = 52.dp
    val rowHeightPx = with(density) { rowHeight.toPx() }

    var dragStartOrder by remember { mutableStateOf<List<String>?>(null) }
    var draggedStopId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    // Long-lived pointerInput coroutines only restart when their key (the
    // stop id) changes, so reads of `stops`/`onMoveStop` inside them would
    // otherwise be frozen at whatever recomposition last (re)started the
    // gesture — rememberUpdatedState keeps every onDrag callback reading
    // the current values instead.
    val currentStops = rememberUpdatedState(stops)
    val currentOnMoveStop = rememberUpdatedState(onMoveStop)

    val displayOrderIds = dragStartOrder ?: stops.map { it.id }
    val byId = stops.associateBy { it.id }

    Column(Modifier.fillMaxWidth()) {
        displayOrderIds.forEachIndexed { slotIndex, id ->
            val stop = byId[id] ?: return@forEachIndexed
            val isDragged = draggedStopId == id
            val liveIndex = stops.indexOfFirst { it.id == id }.let { if (it < 0) slotIndex else it }
            val targetOffset = if (isDragged) dragOffsetY else (liveIndex - slotIndex) * rowHeightPx
            val animatedOffset by animateFloatAsState(
                targetValue = targetOffset,
                animationSpec = if (isDragged) snap() else tween(180),
                label = "stopRowOffset"
            )

            key(id) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .zIndex(if (isDragged) 1f else 0f)
                        // Aug 31, 2026 — graphicsLayer{} (androidx.compose.ui.draw)
                        // was a genuine "Unresolved reference" for this one symbol on
                        // Karthik's Mac across three separate build attempts (fresh
                        // daemon, --refresh-dependencies) even though the identically
                        // packaged .clip() resolves fine elsewhere in this project —
                        // swapped to three separately-declared Modifier extensions that
                        // land the same translate/elevate/fade effect without depending
                        // on whatever's specifically broken about GraphicsLayerModifierKt
                        // in his environment.
                        .offset { IntOffset(0, animatedOffset.roundToInt()) }
                        .shadow(elevation = if (isDragged) 10.dp else 0.dp)
                        .alpha(if (isDragged) 0.96f else 1f)
                        .pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragStartOrder = currentStops.value.map { it.id }
                                    draggedStopId = id
                                    dragOffsetY = 0f
                                },
                                onDragEnd = {
                                    draggedStopId = null
                                    dragStartOrder = null
                                    dragOffsetY = 0f
                                },
                                onDragCancel = {
                                    draggedStopId = null
                                    dragStartOrder = null
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                    val order = dragStartOrder ?: return@detectDragGesturesAfterLongPress
                                    val homeSlot = order.indexOf(id)
                                    if (homeSlot < 0) return@detectDragGesturesAfterLongPress
                                    val liveStops = currentStops.value
                                    val fromIndex = liveStops.indexOfFirst { it.id == id }
                                    if (fromIndex < 0) return@detectDragGesturesAfterLongPress
                                    val proposed = (homeSlot + (dragOffsetY / rowHeightPx).roundToInt())
                                        .coerceIn(0, order.lastIndex)
                                    if (proposed != fromIndex) {
                                        currentOnMoveStop.value(fromIndex, proposed)
                                    }
                                }
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = Pr.muted
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${liveIndex + 1}. ${stop.name}",
                        modifier = Modifier.weight(1f),
                        color = Pr.ink
                    )
                    IconButton(onClick = { onRemoveStop(stop.id) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove stop", tint = Pr.muted)
                    }
                }
            }
        }
    }
}

// MARK: - Bottom action bar

@Composable
private fun bottomButtons(
    routeable: List<Waypoint>,
    destination: Waypoint?,
    onNavigate: () -> Unit,
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) {
            Text(if (destination != null) "Save & Choose Ride Type" else "Save Route", color = Pr.ink)
        }
        PrimaryButton(
            text = "Navigate",
            onClick = onNavigate,
            enabled = routeable.isNotEmpty(),
            modifier = Modifier.weight(1f)
        )
    }
}

// MARK: - Search / pick a location (start, stop, or destination) — live
// Places Autocomplete-as-you-type, debounced ~300ms, matching iOS's
// MKLocalSearchCompleter-backed LocationPickerSheet. Selecting a prediction
// calls Places' fetchPlace (Place Details) to resolve the actual lat/lng,
// then adds the waypoint exactly like the old Geocoder flow did.

@Composable
private fun LocationPickerSheetContent(
    title: String,
    onSelect: (String, String, Double, Double) -> Unit,
    onChooseOnMap: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val placesClient = remember { Places.createClient(context) }
    var query by remember { mutableStateOf("") }
    var predictions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var resolvingPlaceId by remember { mutableStateOf<String?>(null) }
    // One Autocomplete "session" = the predictions the rider sees plus the
    // eventual fetchPlace() on whichever one they pick — Places bills by
    // session (not per keystroke) when the same token is threaded through
    // both calls. A fresh token starts once picking a place ends the
    // current session.
    var sessionToken by remember { mutableStateOf(AutocompleteSessionToken.newInstance()) }

    // Debounce: LaunchedEffect(query) cancels and restarts on every
    // keystroke, so the delay(300) below only ever completes for the
    // fragment the rider has paused on — no separate debounce plumbing
    // needed, and no request fired per keystroke.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            predictions = emptyList()
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(300)
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(q)
            .setSessionToken(sessionToken)
            .build()
        predictions = runCatching { placesClient.findAutocompletePredictions(request).await() }
            .getOrNull()?.autocompletePredictions ?: emptyList()
        searching = false
    }

    fun pick(prediction: AutocompletePrediction) {
        val placeId = prediction.placeId
        resolvingPlaceId = placeId
        scope.launch {
            val fields = listOf(Place.Field.LAT_LNG, Place.Field.NAME, Place.Field.ADDRESS)
            val request = FetchPlaceRequest.builder(placeId, fields)
                .setSessionToken(sessionToken)
                .build()
            val place = runCatching { placesClient.fetchPlace(request).await().place }.getOrNull()
            val latLng = place?.latLng
            resolvingPlaceId = null
            if (latLng != null) {
                val name = place.name ?: prediction.getPrimaryText(null).toString()
                val address = place.address ?: prediction.getSecondaryText(null).toString()
                onSelect(name, address, latLng.latitude, latLng.longitude)
            }
            // Selecting a place ends this Autocomplete session — next
            // keystroke starts a fresh one.
            sessionToken = AutocompleteSessionToken.newInstance()
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text(title, style = PrFont.heading)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search address or place") },
            singleLine = true,
            trailingIcon = {
                if (searching) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Pr.coral)
                } else {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Pr.muted)
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onChooseOnMap, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Map, contentDescription = null, tint = Pr.teal)
            Spacer(Modifier.width(6.dp))
            Text("Choose on Map", color = Pr.teal)
        }
        Spacer(Modifier.height(8.dp))
        predictions.forEach { prediction ->
            TextButton(
                onClick = { pick(prediction) },
                enabled = resolvingPlaceId == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (resolvingPlaceId == prediction.placeId) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Pr.coral)
                    } else {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Pr.coral)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(prediction.getPrimaryText(null).toString(), maxLines = 1, color = Pr.ink)
                        val secondary = prediction.getSecondaryText(null).toString()
                        if (secondary.isNotEmpty()) {
                            Text(
                                secondary,
                                style = PrFont.caption,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = Pr.border)
        }
        if (query.isEmpty()) {
            Text(
                "Start typing an address, place, or city",
                style = PrFont.caption,
                modifier = Modifier.padding(top = 12.dp)
            )
        } else if (!searching && predictions.isEmpty()) {
            Text(
                "No matches found",
                style = PrFont.caption,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

private suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): String =
    withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault())
                .getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
                ?.getAddressLine(0)
        }.getOrNull().orEmpty().ifBlank { "%.5f, %.5f".format(latitude, longitude) }
    }

// MARK: - Choose a location by tapping the map

@Composable
private fun MapPickerSheetContent(
    startLocation: Location?,
    onConfirm: (String, Double, Double) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initial = remember {
        if (startLocation != null) LatLng(startLocation.latitude, startLocation.longitude)
        else LatLng(39.8283, -98.5795)
    }
    var picked by remember { mutableStateOf<LatLng?>(null) }
    var resolving by remember { mutableStateOf(false) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initial, if (startLocation != null) 12f else 4f)
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Tap the map to drop a pin", style = PrFont.heading)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(mapType = MapType.NORMAL),
                uiSettings = MapUiSettings(zoomControlsEnabled = true),
                onMapClick = { latLng -> picked = latLng }
            ) {
                picked?.let { p ->
                    Marker(state = MarkerState(p))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { picked = null }, enabled = picked != null, modifier = Modifier.weight(1f)) { Text("Clear Pin", color = Pr.ink) }
            PrimaryButton(
                text = if (resolving) "Locating..." else "Confirm Location",
                enabled = picked != null && !resolving,
                modifier = Modifier.weight(1f),
                onClick = {
                    picked?.let { p ->
                        resolving = true
                        scope.launch {
                            val address = withContext(Dispatchers.IO) {
                                runCatching {
                                    @Suppress("DEPRECATION")
                                    Geocoder(context, Locale.getDefault())
                                        .getFromLocation(p.latitude, p.longitude, 1)
                                        ?.firstOrNull()?.getAddressLine(0)
                                }.getOrNull() ?: "%.4f, %.4f".format(p.latitude, p.longitude)
                            }
                            resolving = false
                            onConfirm(address, p.latitude, p.longitude)
                        }
                    }
                }
            )
        }
    }
}

// MARK: - Long-press drop-pin quick add

@Composable
private fun DropPinSheetContent(
    latitude: Double,
    longitude: Double,
    onAdd: (String, WaypointType) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(WaypointType.MEETUP) }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Add a Stop", style = PrFont.heading)
        Spacer(Modifier.height(4.dp))
        Text("%.4f, %.4f".format(latitude, longitude), style = PrFont.caption)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Text("Type", style = PrFont.bodySmall, fontWeight = FontWeight.SemiBold)
        WaypointType.values().forEach { t ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { type = t }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = type == t, onClick = { type = t })
                Spacer(Modifier.width(4.dp))
                Text(t.label, color = Pr.ink)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel", color = Pr.ink) }
            PrimaryButton(
                text = "Add",
                onClick = { onAdd(name.trim().ifEmpty { type.label }, type) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// MARK: - Solo vs Group choice once a route is saved

@Composable
private fun RideTypeChoiceSheetContent(
    stopCount: Int,
    onGroupRide: () -> Unit,
    onSoloRide: () -> Unit,
    onKeepPlanning: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Route Saved", style = PrFont.heading)
        Spacer(Modifier.height(4.dp))
        Text(
            "$stopCount stop${if (stopCount == 1) "" else "s"} planned. How do you want to ride it?",
            style = PrFont.body
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = "Start a Group Ride", onClick = onGroupRide)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onSoloRide, modifier = Modifier.fillMaxWidth()) { Text("Start Riding Solo", color = Pr.ink) }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onKeepPlanning, modifier = Modifier.fillMaxWidth()) { Text("Keep Planning", color = Pr.muted) }
    }
}

// MARK: - Share the planned route (ride-code share + direct invite to followed riders)

@Composable
private fun ShareSheetContent(
    context: Context,
    riderName: String,
    destinationName: String,
    stopCount: Int,
    promoteToRideCode: () -> String,
    onDone: () -> Unit
) {
    val friendsManager = remember { FriendsManager(context) }
    val followed by friendsManager.followedUsers.collectAsState()
    val inviteManager = remember { RideInviteManager(context) }
    var sentTo by remember { mutableStateOf<Set<String>>(emptySet()) }

    DisposableEffect(Unit) {
        friendsManager.start()
        onDispose { friendsManager.stop() }
    }

    Column(
        Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text("Share This Route", style = PrFont.heading)
        Spacer(Modifier.height(4.dp))
        val label = if (destinationName.isNotEmpty()) "To $destinationName · $stopCount stops" else "$stopCount stops planned"
        Text(label, style = PrFont.body)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "Share Ride Code",
            onClick = {
                val code = promoteToRideCode()
                val shareText = "Ride with me on PackRide! Join with code $code"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(intent, "Share Route"))
            }
        )
        if (followed.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Or invite a rider you follow:", style = PrFont.bodySmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            followed.forEach { rider ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(rider.name, modifier = Modifier.weight(1f), color = Pr.ink)
                    val sent = rider.id in sentTo
                    TextButton(
                        enabled = !sent,
                        onClick = {
                            val code = promoteToRideCode()
                            inviteManager.sendInvite(rider.id, code, riderName, destinationName, stopCount)
                            sentTo = sentTo + rider.id
                        }
                    ) { Text(if (sent) "Invited" else "Invite", color = Pr.teal) }
                }
                HorizontalDivider(color = Pr.border)
            }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done", color = Pr.muted) }
    }
}

// MARK: - Weather at each planned stop ("as the crow flies")

@Composable
private fun WeatherAheadSheetContent(
    startOverride: Waypoint?,
    myLocation: Location?,
    stops: List<Waypoint>
) {
    data class Leg(val label: String, val lat: Double, val lng: Double)
    val legs = remember(startOverride, myLocation, stops) {
        buildList {
            when {
                startOverride != null -> add(Leg("Start: ${startOverride.name}", startOverride.latitude, startOverride.longitude))
                myLocation != null -> add(Leg("Start: My Location", myLocation.latitude, myLocation.longitude))
            }
            stops.forEach { add(Leg(if (it.isDestination) "Destination: ${it.name}" else it.name, it.latitude, it.longitude)) }
        }
    }
    var results by remember { mutableStateOf<Map<String, WeatherSnapshot?>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(legs) {
        loading = true
        val map = mutableMapOf<String, WeatherSnapshot?>()
        legs.forEach { leg -> map[leg.label] = WeatherManager.fetch(leg.lat, leg.lng) }
        results = map
        loading = false
    }

    Column(
        Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text("Weather Ahead", style = PrFont.heading)
        Spacer(Modifier.height(4.dp))
        Text("As the crow flies — not weather along the routed roads.", style = PrFont.caption)
        Spacer(Modifier.height(12.dp))
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp), color = Pr.coral)
        } else if (legs.isEmpty()) {
            Text("Plan a start and at least one stop to see weather ahead.", style = PrFont.body)
        } else {
            legs.forEach { leg ->
                val w = results[leg.label]
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.WbSunny, contentDescription = null, tint = Color(0xFFE8952F))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(leg.label, color = Pr.ink, fontWeight = FontWeight.Medium)
                        Text(
                            if (w != null) "${w.condition} · ${com.karthik.packride.data.MeasurementUnits.temperatureF(w.tempF)} · wind ${com.karthik.packride.data.MeasurementUnits.speedMph(w.windMph)}"
                            else "Unavailable",
                            style = PrFont.caption
                        )
                    }
                }
                HorizontalDivider(color = Pr.border)
            }
        }
    }
}

// MARK: - Schedule this route for later (creates a ScheduledRide bound to the SAME ride code)

@Composable
private fun ScheduleSheetContent(
    context: Context,
    creatorName: String,
    defaultTitle: String,
    meetupLocation: String,
    meetupLat: Double,
    meetupLng: Double,
    promoteToRideCode: () -> String,
    onDone: () -> Unit
) {
    val scheduleManager = remember { ScheduledRideManager(context) }
    var title by remember { mutableStateOf(defaultTitle) }
    var pickedMillis by remember {
        mutableStateOf(Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis)
    }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    fun displayDateTime(): String {
        val c = Calendar.getInstance().apply { timeInMillis = pickedMillis }
        val month = c.get(Calendar.MONTH) + 1
        val day = c.get(Calendar.DAY_OF_MONTH)
        val year = c.get(Calendar.YEAR)
        var hour = c.get(Calendar.HOUR)
        if (hour == 0) hour = 12
        val minute = c.get(Calendar.MINUTE)
        val ampm = if (c.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        return "%02d/%02d/%04d at %d:%02d %s".format(month, day, year, hour, minute, ampm)
    }

    fun pickDateTime() {
        val c = Calendar.getInstance().apply { timeInMillis = pickedMillis }
        DatePickerDialog(
            context,
            { _, y, m, d ->
                c.set(Calendar.YEAR, y)
                c.set(Calendar.MONTH, m)
                c.set(Calendar.DAY_OF_MONTH, d)
                TimePickerDialog(
                    context,
                    { _, h, min ->
                        c.set(Calendar.HOUR_OF_DAY, h)
                        c.set(Calendar.MINUTE, min)
                        pickedMillis = c.timeInMillis
                    },
                    c.get(Calendar.HOUR_OF_DAY),
                    c.get(Calendar.MINUTE),
                    false
                ).show()
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Column(
        Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text("Schedule This Ride", style = PrFont.heading)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Ride Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { pickDateTime() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Pr.teal)
            Spacer(Modifier.width(6.dp))
            Text(displayDateTime(), color = Pr.ink)
        }
        if (meetupLocation.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Pr.coral)
                Spacer(Modifier.width(6.dp))
                Text("Meet at $meetupLocation", color = Pr.ink)
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = if (saving) "Saving..." else "Save Scheduled Ride",
            enabled = !saving,
            onClick = {
                val code = promoteToRideCode()
                saving = true
                scheduleManager.create(
                    title = title.trim().ifEmpty { defaultTitle },
                    description = "Ride code: $code",
                    scheduledDateMs = pickedMillis,
                    meetupLocation = meetupLocation,
                    meetupLat = meetupLat,
                    meetupLng = meetupLng,
                    creatorName = creatorName,
                    rideCode = code
                ) { ok ->
                    saving = false
                    if (ok) saved = true
                }
            }
        )
        if (saved) {
            Spacer(Modifier.height(8.dp))
            Text("Scheduled! Riders can RSVP from the Schedule tab.", style = PrFont.caption)
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done", color = Pr.muted) }
    }
}
