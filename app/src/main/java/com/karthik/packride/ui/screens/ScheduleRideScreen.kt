package com.karthik.packride.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.karthik.packride.auth.AuthManager
import com.karthik.packride.community.Community
import com.karthik.packride.community.CommunityMembershipStore
import com.karthik.packride.schedule.RsvpStatus
import com.karthik.packride.schedule.ScheduledRide
import com.karthik.packride.schedule.ScheduledRideManager
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrAvatar
import com.karthik.packride.ui.theme.PrCard
import com.karthik.packride.ui.theme.PrFont
import com.karthik.packride.ui.theme.PrPageHeader
import com.karthik.packride.ui.theme.PrWebSectionLabel
import com.karthik.packride.ui.theme.PrimaryButton
import com.karthik.packride.waypoints.GroupWaypointSync
import com.karthik.packride.waypoints.Waypoint
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Aug 30, 2026 — Schedule Ride visual+functionality parity pass. Port of
// iOS's ScheduleRideView.swift (ScheduleRideSheet + ScheduledRideDetailView +
// InfoCard; ~650 lines) into the flat, non-sheet shape MoreScreen's tab strip
// uses everywhere else on Android (see PrPageHeader's own doc comment) —
// master/detail handled with in-composable state, same pattern as
// CommunityScreen.kt's selectedCommunityId. The old 84-line stub had a
// bare-bones create form and a flat delete-only list with none of iOS's
// RSVP flow, community sharing, or ride detail — see
// ScheduledRideManager.kt's file header for exactly what was added there to
// support this screen.
//
// Deliberately NOT ported from ScheduleRideView.swift: ScheduledRideDetailView's
// route-map card (RouteMapView + "View Planned Route" full-screen map) — it
// reads GroupWaypointSync, the Firebase-synced planned-route feed, which has
// no Android port; a scheduled ride here has no associated route to show a
// map for. UpcomingRidesSection / CommunityScheduledRidesSection are for
// GroupRideView / CommunityDashboard, neither of which has had its own
// parity pass yet.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleRideScreen(auth: AuthManager) {
    val context = LocalContext.current
    val manager = remember { ScheduledRideManager(context) }
    val rides by manager.myScheduledRides.collectAsState()
    var selectedRideId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { manager.listenMine() }
    DisposableEffect(Unit) { onDispose { manager.stop() } }

    val selectedRide = rides.firstOrNull { it.id == selectedRideId }

    if (selectedRide != null) {
        ScheduledRideDetail(
            ride = selectedRide,
            manager = manager,
            riderName = auth.prefsSnapshot.riderName,
            onBack = { selectedRideId = null },
            onDeleted = { selectedRideId = null }
        )
    } else {
        ScheduleRideList(
            auth = auth,
            manager = manager,
            rides = rides,
            onOpenRide = { selectedRideId = it }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleRideList(
    auth: AuthManager,
    manager: ScheduledRideManager,
    rides: List<ScheduledRide>,
    onOpenRide: (String) -> Unit
) {
    val store = remember { CommunityMembershipStore.get() }
    val myCommunities by store.myCommunities.collectAsState()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var meetup by remember { mutableStateOf("") }
    var selectedCommunityId by remember { mutableStateOf<String?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    // Default to an hour from now, matching iOS's ScheduleRideSheet default.
    var scheduledMillis by remember {
        mutableStateOf(Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }.timeInMillis)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val canSchedule = title.isNotBlank() && scheduledMillis > System.currentTimeMillis()

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = scheduledMillis,
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val today = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    return utcTimeMillis >= today - TimeZone.getDefault().rawOffset
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val pickedUtc = datePickerState.selectedDateMillis
                    if (pickedUtc != null) {
                        // DatePicker works in UTC-midnight terms; merge the picked
                        // calendar day onto the existing time-of-day in local time.
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = pickedUtc }
                        val local = Calendar.getInstance().apply { timeInMillis = scheduledMillis }
                        local.set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                        local.set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                        local.set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                        scheduledMillis = local.timeInMillis
                    }
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val initial = Calendar.getInstance().apply { timeInMillis = scheduledMillis }
        val timePickerState = rememberTimePickerState(
            initialHour = initial.get(Calendar.HOUR_OF_DAY),
            initialMinute = initial.get(Calendar.MINUTE),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val c = Calendar.getInstance().apply { timeInMillis = scheduledMillis }
                    c.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    c.set(Calendar.MINUTE, timePickerState.minute)
                    scheduledMillis = c.timeInMillis
                    showTimePicker = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = timePickerState) }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Pr.bg),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)
    ) {
        item {
            PrPageHeader(
                eyebrow = "Plan Ahead",
                title = "Schedule a Ride",
                subtitle = "Plan ahead and invite your pack"
            )
        }

        item {
            PrCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("RIDE TITLE", style = PrFont.micro)
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            placeholder = { Text("e.g. Sunday Coast Cruise") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("DESCRIPTION", style = PrFont.micro)
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            placeholder = { Text("Route details, what to bring...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 5
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("DATE & TIME", style = PrFont.micro)
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(formatScheduled(scheduledMillis))
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("MEETUP POINT", style = PrFont.micro)
                        OutlinedTextField(
                            value = meetup,
                            onValueChange = { meetup = it },
                            placeholder = { Text("e.g. Starbucks on Main St") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (myCommunities.isNotEmpty()) {
            item {
                Text(
                    "SHARE TO COMMUNITY",
                    style = PrFont.micro,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            items(myCommunities, key = { it.id }) { community ->
                CommunityToggleRow(
                    community = community,
                    isSelected = selectedCommunityId == community.id,
                    onToggle = {
                        selectedCommunityId = if (selectedCommunityId == community.id) null else community.id
                    }
                )
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                PrimaryButton(
                    text = if (isCreating) "Scheduling..." else "Schedule Ride",
                    enabled = canSchedule && !isCreating,
                    onClick = {
                        isCreating = true
                        manager.create(
                            title = title.trim(),
                            description = description.trim(),
                            scheduledDateMs = scheduledMillis,
                            meetupLocation = meetup.trim(),
                            meetupLat = 0.0,
                            meetupLng = 0.0,
                            creatorName = auth.prefsSnapshot.riderName,
                            communityID = selectedCommunityId
                        ) { ok ->
                            isCreating = false
                            if (ok) {
                                title = ""; description = ""; meetup = ""; selectedCommunityId = null
                                scheduledMillis = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }.timeInMillis
                            }
                        }
                    }
                )
            }
        }

        item {
            PrWebSectionLabel(title = "My Scheduled Rides", detail = if (rides.isNotEmpty()) "${rides.size}" else null)
        }

        if (rides.isEmpty()) {
            item {
                Text(
                    "No rides scheduled yet.",
                    style = PrFont.bodySmall,
                    color = Pr.muted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
        } else {
            items(rides, key = { it.id }) { ride ->
                ScheduledRideRow(ride = ride, onClick = { onOpenRide(ride.id) })
            }
        }
    }
}

@Composable
private fun CommunityToggleRow(community: Community, isSelected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Pr.cardBg)
            .clickable(onClick = onToggle)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape).background(if (isSelected) Pr.coral else Pr.fieldBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Groups, contentDescription = null, tint = if (isSelected) Color.White else Pr.muted)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(community.name, style = PrFont.subheading)
            Text(
                if (isSelected) "All members will see this ride" else "Tap to share with this community",
                style = PrFont.micro
            )
        }
        if (isSelected) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Pr.coral)
        }
    }
}

@Composable
private fun ScheduledRideRow(ride: ScheduledRide, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Pr.cardBg)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.width(50.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(monthLabel(ride.scheduledDate), style = PrFont.micro)
            Text("${dayLabel(ride.scheduledDate)}", style = PrFont.stat)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(ride.title, style = PrFont.subheading, maxLines = 1)
            Text(timeLabel(ride.scheduledDate) + " · by ${ride.creatorName}", style = PrFont.caption)
            if (ride.meetupLocation.isNotEmpty()) {
                Text(ride.meetupLocation, style = PrFont.caption, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${ride.rsvpCount}", style = PrFont.stat.copy(color = GoingGreen))
            Text("going", style = PrFont.micro, color = GoingGreen)
        }
    }
}

@Composable
private fun ScheduledRideDetail(
    ride: ScheduledRide,
    manager: ScheduledRideManager,
    riderName: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val rsvps by manager.rsvpList.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var routeWaypoints by remember(ride.rideCode) { mutableStateOf<List<Waypoint>>(emptyList()) }
    val isCreator = manager.myID.isNotEmpty() && ride.creatorID == manager.myID
    val myStatus = rsvps.firstOrNull { it.id == manager.myID }?.status

    LaunchedEffect(ride.id) {
        manager.loadRSVPs(ride.id)
        GroupWaypointSync.fetchOnce(ride.rideCode) { routeWaypoints = it }
    }
    DisposableEffect(ride.id) { onDispose { manager.stopRSVPs() } }

    Column(Modifier.fillMaxSize().background(Pr.bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Pr.ink,
                modifier = Modifier.clickable(onClick = onBack)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(ride.title, style = PrFont.heading, maxLines = 1)
                Text(fullDateTimeLabel(ride.scheduledDate), style = PrFont.caption)
            }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoRow(icon = Icons.Filled.CalendarMonth, label = "Ride Code", value = ride.rideCode, tint = Pr.coral)
                InfoRow(icon = Icons.Filled.Groups, label = "Organizer", value = ride.creatorName, tint = Pr.teal)
                if (ride.meetupLocation.isNotEmpty()) {
                    InfoRow(icon = Icons.Filled.LocationOn, label = "Meetup Point", value = ride.meetupLocation, tint = GoingGreen)
                }
                if (routeWaypoints.isNotEmpty()) {
                    ScheduledRouteMap(routeWaypoints)
                }
                if (ride.description.isNotEmpty()) {
                    PrCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("DETAILS", style = PrFont.micro)
                            Text(ride.description, style = PrFont.bodySmall)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            PrimaryButton(
                text = "Share Ride",
                onClick = {
                    val details = buildString {
                        append("Join my PackRide scheduled ride!\n\n")
                        append(ride.title).append('\n')
                        append(fullDateTimeLabel(ride.scheduledDate))
                        if (ride.meetupLocation.isNotEmpty()) append("\nMeetup: ").append(ride.meetupLocation)
                        append("\nRide code: ").append(ride.rideCode)
                        append("\n\nOpen PackRide and enter the ride code to join.")
                    }
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, details)
                    }, "Share scheduled ride"))
                }
            )

            Spacer(Modifier.height(20.dp))

            PrCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("RIDERS", style = PrFont.sectionHeader)
                        Text(
                            "${rsvps.count { it.status == RsvpStatus.GOING }} going",
                            style = PrFont.caption,
                            color = GoingGreen
                        )
                    }
                    rsvps.forEach { rsvp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Pr.fieldBg)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PrAvatar(initials = rsvp.initials, size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(rsvp.name, style = PrFont.body, modifier = Modifier.weight(1f))
                            val statusText = when (rsvp.status) {
                                RsvpStatus.GOING -> "Going"
                                RsvpStatus.MAYBE -> "Maybe"
                                RsvpStatus.NOT_GOING -> "Not Going"
                            }
                            Text(
                                statusText,
                                style = PrFont.caption,
                                color = if (rsvp.status == RsvpStatus.GOING) GoingGreen else Pr.muted
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            if (!isCreator) {
                if (myStatus == RsvpStatus.GOING) {
                    OutlinedButton(
                        onClick = { manager.cancelRSVP(ride.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = GoingGreen)
                        Spacer(Modifier.width(8.dp))
                        Text("You're Going! Tap to cancel")
                    }
                } else {
                    PrimaryButton(
                        text = "I'm In!",
                        onClick = { manager.rsvp(ride.id, riderName, RsvpStatus.GOING) }
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { manager.rsvp(ride.id, riderName, RsvpStatus.MAYBE) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Maybe") }
                }
                Spacer(Modifier.height(20.dp))
            }

            if (isCreator) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete This Ride", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Scheduled Ride?") },
            text = { Text("This will permanently remove this ride and notify all RSVPs.") },
            confirmButton = {
                TextButton(onClick = {
                    manager.deleteRide(ride.id, ride.communityID)
                    showDeleteConfirm = false
                    onDeleted()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ScheduledRouteMap(waypoints: List<Waypoint>) {
    val points = waypoints.map { LatLng(it.latitude, it.longitude) }
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(points.first(), 12f)
    }
    LaunchedEffect(points) {
        if (points.size == 1) {
            camera.animate(CameraUpdateFactory.newLatLngZoom(points.first(), 14f))
        } else {
            val bounds = LatLngBounds.builder().also { builder -> points.forEach(builder::include) }.build()
            camera.animate(CameraUpdateFactory.newLatLngBounds(bounds, 80))
        }
    }
    PrCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PLANNED ROUTE", style = PrFont.micro)
            GoogleMap(
                modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)),
                cameraPositionState = camera,
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
            ) {
                if (points.size > 1) Polyline(points = points, color = Pr.coral, width = 9f)
                waypoints.forEach { waypoint ->
                    Marker(
                        state = MarkerState(LatLng(waypoint.latitude, waypoint.longitude)),
                        title = waypoint.name
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, tint: Color) {
    PrCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, style = PrFont.micro)
                Text(value, style = PrFont.subheading)
            }
        }
    }
}

private val GoingGreen = Color(0xFF2E9E5B)

private fun calendarFor(epochSeconds: Double): Calendar =
    Calendar.getInstance().apply { timeInMillis = (epochSeconds * 1000).toLong() }

private fun monthLabel(epochSeconds: Double): String {
    val fmt = java.text.SimpleDateFormat("MMM", Locale.getDefault())
    return fmt.format(Date((epochSeconds * 1000).toLong())).uppercase()
}

private fun dayLabel(epochSeconds: Double): Int = calendarFor(epochSeconds).get(Calendar.DAY_OF_MONTH)

private fun timeLabel(epochSeconds: Double): String {
    val fmt = java.text.SimpleDateFormat("h:mm a", Locale.getDefault())
    return fmt.format(Date((epochSeconds * 1000).toLong()))
}

private fun fullDateTimeLabel(epochSeconds: Double): String {
    val fmt = java.text.SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())
    return fmt.format(Date((epochSeconds * 1000).toLong()))
}

private fun formatScheduled(millis: Long): String {
    val fmt = java.text.SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())
    return fmt.format(Date(millis))
}
