package com.karthik.packride.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Motorcycle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.firebase.database.FirebaseDatabase
import coil.compose.AsyncImage
import com.karthik.packride.ads.AdBannerFooter
import com.karthik.packride.analytics.RideAnalyticsEngine
import com.karthik.packride.analytics.RideScoreSummary
import com.karthik.packride.auth.AuthManager
import com.karthik.packride.auth.rideInitials
import com.karthik.packride.feed.RideFeedManager
import com.karthik.packride.gpx.GPXStorage
import com.karthik.packride.group.PendingReplay
import com.karthik.packride.nav.PendingDigest
import com.karthik.packride.nav.PendingMoreTab
import com.karthik.packride.ride.RideHistoryManager
import com.karthik.packride.ride.RideRecord
import com.karthik.packride.share.RideShareCard
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrCard
import com.karthik.packride.ui.theme.PrFont
import com.karthik.packride.ui.theme.PrMetricStrip
import com.karthik.packride.ui.theme.PrPageHeader
import com.karthik.packride.ui.theme.PrWebSectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Aug 30, 2026 — full visual + functionality parity pass, port of iOS
// RideHistoryView.swift (1256 lines; Android was a 95-line stub with no
// cloud sync, no filtering, no multi-select, no route view, no GPX export).
// See RideHistoryManager.kt for the cloud-sync side of this.
//
// Deliberately NOT ported this pass (flagged, not silently dropped):
// - The web header bar's notification bell (iOS's follow-request badge —
//   Android's FriendsManager has no follow-request concept at all yet, a
//   real missing feature, not styling) and Need-Help quick icon (blocked on
//   the same "More tab is a flat ScrollableTabRow, not push nav" structural
//   gap noted in the Garage screen pass).
// - The Badges/Digest/Trends quick-link bar — same flat-tab-strip blocker
//   (no way to jump to a specific MoreScreen sub-tab with a route/argument
//   except the Pending* bridge pattern this file already uses once for
//   Replay; iOS's "Digest"/RidingDigestView also has no Android screen at
//   all yet), plus Trends needs RideAnalyticsSummary data Android doesn't
//   compute.
// - RideScoreCard / per-ride analytics / telemetry map — Android doesn't
//   compute a RideAnalyticsSummary anywhere yet; faking a score card with
//   no real data would be worse than omitting it.
// - "View All Participants' Stats" for group rides — a real, separate
//   comparison screen (ParticipantsStatsView on iOS) that doesn't exist on
//   Android; a genuinely new feature, not a restyle.
// - Swipe-to-reveal-delete gesture — replaced with a plain delete icon
//   button in the expanded row (same delete-with-confirm outcome, simpler
//   gesture surface, consistent with other Compose-idiom swaps this
//   session).
// - iOS separates a static route preview (GPXRouteMapView) from animated
//   replay-with-controls (RideReplayView). Android's own ReplayScreen is
//   currently the static-only version (map + polyline, no playback) — so
//   "View Route" here points at that one real screen instead of building a
//   second, near-duplicate static map view. Deepening Replay into real
//   animated playback stays a separate backlog item.

private enum class HistoryFilter(val label: String) {
    ALL("All"), SOLO("Solo"), LED("Led"), JOINED("Joined");

    fun matches(ride: RideRecord): Boolean = when (this) {
        ALL -> true
        SOLO -> !ride.isGroupRide
        LED -> ride.isGroupRide && ride.isLeader
        JOINED -> ride.isGroupRide && !ride.isLeader
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideHistoryScreen(auth: AuthManager? = null, onOpenReplay: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history = remember { RideHistoryManager(context) }
    val feed = remember { RideFeedManager(context) }
    val rides by history.rides.collectAsState()
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }

    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var postTarget by remember { mutableStateOf<RideRecord?>(null) }
    var deleteTarget by remember { mutableStateOf<RideRecord?>(null) }
    var telemetryTarget by remember { mutableStateOf<Pair<RideRecord, File>?>(null) }
    var participantsTarget by remember { mutableStateOf<RideRecord?>(null) }
    var replayError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { history.syncFromCloud() }

    participantsTarget?.let { ride ->
        ParticipantsStatsScreen(ride.rideCode.removePrefix("GROUP:")) { participantsTarget = null }
        return
    }

    telemetryTarget?.let { (ride, file) ->
        RideTelemetryScreen(file, if (ride.isTrackSession) "Track Session" else if (ride.isGroupRide) "Group Ride" else "Solo Ride", dateFmt.format(Date(ride.dateMs))) {
            telemetryTarget = null
        }
        return
    }

    // Aug 31, 2026 -- Ride Score card (iOS RideScoreCard, SoloRideView.swift
    // ~line 557), ported here for the first time -- Android has no cached
    // `analytics` on RideRecord the way iOS does, so the score is computed
    // on demand off the main thread the moment a ride with route data is
    // expanded (same GPX-scanning RideAnalyticsEngine.analyze already used
    // by badges/BadgeEngine.kt's computeAsync), then cached per ride id so
    // re-expanding the same row doesn't recompute.
    val scoreCache = remember { mutableStateMapOf<String, RideScoreSummary?>() }
    LaunchedEffect(expandedId) {
        val id = expandedId ?: return@LaunchedEffect
        if (scoreCache.containsKey(id)) return@LaunchedEffect
        val ride = rides.firstOrNull { it.id == id } ?: return@LaunchedEffect
        if (!ride.hasRouteData) return@LaunchedEffect
        val filename = history.resolveGpx(ride) ?: run { scoreCache[id] = null; return@LaunchedEffect }
        val summary = withContext(Dispatchers.Default) {
            val file: File = GPXStorage.resolve(context.filesDir, filename)
            RideAnalyticsEngine.analyze(file)
        }
        scoreCache[id] = summary
    }

    val soloCount = rides.count { !it.isGroupRide }
    val groupCount = rides.count { it.isGroupRide }
    val ledCount = rides.count { it.isGroupRide && it.isLeader }
    val joinedCount = rides.count { it.isGroupRide && !it.isLeader }
    val filteredRides = rides.filter { filter.matches(it) }

    fun toggleSelection(id: String) {
        selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
    }

    Column(Modifier.fillMaxSize().background(Pr.bg)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            PrPageHeader(
                eyebrow = "Ride History",
                title = "Your Rides",
                subtitle = if (rides.isEmpty()) "Start a Solo or Group ride to see it here."
                else "${rides.size} ride${if (rides.size == 1) "" else "s"} recorded"
            )

            if (rides.isNotEmpty()) {
                PrMetricStrip(
                    metrics = listOf(
                        "${rides.size}" to "Total Rides",
                        com.karthik.packride.data.MeasurementUnits.distanceMiles(
                            rides.sumOf { it.distanceMiles }, 0
                        ).substringBefore(" ") to
                            if (com.karthik.packride.data.MeasurementUnits.current == com.karthik.packride.data.MeasurementSystem.METRIC) "Total Kilometres" else "Total Miles",
                        com.karthik.packride.data.MeasurementUnits.speedMph(
                            rides.maxOfOrNull { it.maxSpeedMph } ?: 0.0
                        ).substringBefore(" ") to
                            if (com.karthik.packride.data.MeasurementUnits.current == com.karthik.packride.data.MeasurementSystem.METRIC) "Best km/h" else "Best mph"
                    )
                )

                // Aug 31, 2026 -- Badges/Digest/Trends quick-link row (iOS
                // RideHistoryView.swift ~line 350), ported here for the
                // first time. Badges and Trends already have real
                // destinations, just never reached from here -- both go
                // through MoreScreen's existing PendingMoreTab bridge, same
                // pattern HomeScreen.kt's own row links already use (index
                // 10 = Badges, index 2 = Trends -- confirmed against
                // MoreScreen.kt's current labels list). Trends is hidden
                // until at least one ride actually has route data to chart,
                // matching iOS's `rides.contains(where: { $0.analytics != nil })`
                // gate. Digest is a real new destination (see
                // nav/PendingDigest.kt / PackRideNav.kt's Dest.Digest).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .background(Pr.fieldBg)
                ) {
                    HistoryQuickLink("🏆", "Badges", Modifier.weight(1f)) { PendingMoreTab.request(10) }
                    Box(Modifier.width(1.dp).height(46.dp).background(Pr.border))
                    HistoryQuickLink("📅", "Digest", Modifier.weight(1f)) { PendingDigest.request() }
                    if (rides.any { it.hasRouteData }) {
                        Box(Modifier.width(1.dp).height(46.dp).background(Pr.border))
                        HistoryQuickLink("📈", "Trends", Modifier.weight(1f)) { PendingMoreTab.request(2) }
                    }
                }
            }

            if (isSelectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Cancel",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Pr.muted),
                        modifier = Modifier.clickable(onClick = { isSelectionMode = false; selectedIds = emptySet() })
                    )
                    Text(
                        "${selectedIds.size} Selected",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Pr.ink)
                    )
                    Text(
                        "Delete",
                        style = TextStyle(
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = if (selectedIds.isEmpty()) Pr.muted else Color(0xFFD33B2C)
                        ),
                        modifier = Modifier.clickable(
                            enabled = selectedIds.isNotEmpty(),
                            onClick = { showBulkDeleteConfirm = true }
                        )
                    )
                }
            } else {
                PrWebSectionLabel(title = "Recent Rides")
            }

            if (groupCount > 0 && soloCount > 0) {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(HistoryFilter.entries.toList()) { f ->
                        val count = when (f) {
                            HistoryFilter.ALL -> rides.size
                            HistoryFilter.SOLO -> soloCount
                            HistoryFilter.LED -> ledCount
                            HistoryFilter.JOINED -> joinedCount
                        }
                        val selected = filter == f
                        Row(
                            modifier = Modifier
                                .background(if (selected) Pr.coral else Pr.cardBg, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .border(width = if (selected) 0.dp else 1.dp, color = Pr.border, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .clickable(onClick = { filter = f })
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(f.label, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Pr.ink))
                            Text("$count", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White.copy(alpha = 0.85f) else Pr.muted))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            when {
                rides.isEmpty() -> EmptyHistoryState(Modifier.padding(horizontal = 16.dp))
                filteredRides.isEmpty() -> Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                    Text("No ${filter.label.lowercase()} rides yet", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Pr.muted))
                }
                else -> PrCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Column {
                        filteredRides.forEachIndexed { index, ride ->
                            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Pr.border))
                            RideHistoryRow(
                                ride = ride,
                                dateText = dateFmt.format(Date(ride.dateMs)),
                                isExpanded = expandedId == ride.id,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedIds.contains(ride.id),
                                scoreSummary = scoreCache[ride.id],
                                scoreLoading = expandedId == ride.id && ride.hasRouteData && !scoreCache.containsKey(ride.id),
                                onOpenTelemetry = {
                                    scope.launch {
                                        val filename = history.resolveGpx(ride) ?: return@launch
                                        telemetryTarget = ride to GPXStorage.resolve(context.filesDir, filename)
                                    }
                                },
                                onTap = {
                                    when {
                                        isSelectionMode -> toggleSelection(ride.id)
                                        else -> expandedId = if (expandedId == ride.id) null else ride.id
                                    }
                                },
                                onLongPress = {
                                    if (!isSelectionMode) { isSelectionMode = true; selectedIds = setOf(ride.id) }
                                },
                                onShareStats = {
                                    RideShareCard.share(
                                        context = context,
                                        title = if (ride.isTrackSession) "Track Session" else if (ride.isGroupRide) "Group Ride" else "Solo Ride",
                                        rider = auth?.prefsSnapshot?.riderName?.ifBlank { "PackRide Rider" } ?: "PackRide Rider",
                                        distance = com.karthik.packride.data.MeasurementUnits.distanceMiles(ride.distanceMiles),
                                        duration = ride.durationFormatted,
                                        detail = "Top speed ${com.karthik.packride.data.MeasurementUnits.speedMph(ride.maxSpeedMph)} • ${dateFmt.format(Date(ride.dateMs))}"
                                    )
                                },
                                onExportGpx = {
                                    scope.launch {
                                        val filename = history.resolveGpx(ride) ?: return@launch
                                        val file = GPXStorage.resolve(context.filesDir, filename)
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/gpx+xml"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Export GPX"))
                                    }
                                },
                                onViewRoute = {
                                    scope.launch {
                                        val filename = history.resolveGpx(ride)
                                        if (filename == null) {
                                            replayError = "No synced route file was found for this ride. Open PackRide on the iPhone that recorded it once so its GPX can finish uploading, then try again."
                                            return@launch
                                        }
                                        PendingReplay.request(ride.id)
                                        onOpenReplay()
                                    }
                                },
                                onPostToFeed = { postTarget = ride },
                                onParticipants = { participantsTarget = ride },
                                onDelete = { deleteTarget = ride }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            AdBannerFooter()
        }
    }

    replayError?.let { message ->
        AlertDialog(
            onDismissRequest = { replayError = null },
            title = { Text("Replay unavailable") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { replayError = null }) { Text("OK", color = Pr.coral) }
            }
        )
    }

    postTarget?.let { ride ->
        var title by remember(ride.id) {
            mutableStateOf("${auth?.prefsSnapshot?.riderName?.rideInitials() ?: "Rider"}'s Ride ${dateFmt.format(Date(ride.dateMs))}")
        }
        // Aug 31, 2026 — optional photo attach, port of iOS PostToFeedSheet's
        // PhotosPicker. Uploaded by RideFeedManager.postRide (see
        // ImageCloudUpload.uploadFeedPhoto) to feedPhotos/{postId}.jpg before
        // the post write, same as iOS.
        var selectedPhotoUri by remember(ride.id) { mutableStateOf<Uri?>(null) }
        val photoPickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri: Uri? -> selectedPhotoUri = uri }
        AlertDialog(
            onDismissRequest = { postTarget = null },
            title = { Text("Post to Feed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Pr.fieldBg)
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val photoUri = selectedPhotoUri
                        if (photoUri != null) {
                            AsyncImage(
                                model = photoUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Pr.coral)
                                Text("Add a photo (optional)", style = TextStyle(fontSize = 13.sp, color = Pr.muted))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    feed.postRide(
                        context = context,
                        title = title,
                        distanceMiles = ride.distanceMiles,
                        duration = ride.durationFormatted,
                        authorName = auth?.prefsSnapshot?.riderName ?: "Rider",
                        authorInitials = (auth?.prefsSnapshot?.riderName ?: "Rider").rideInitials(),
                        photoUri = selectedPhotoUri,
                        onDone = { _, _ -> }
                    )
                    postTarget = null
                }) { Text("Post") }
            },
            dismissButton = { TextButton(onClick = { postTarget = null }) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { ride ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this ride?") },
            text = { Text("This will permanently remove this ride and its GPX data.") },
            confirmButton = {
                TextButton(onClick = {
                    history.delete(ride.id)
                    if (expandedId == ride.id) expandedId = null
                    deleteTarget = null
                }) { Text("Delete", color = Color(0xFFD33B2C)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text("Delete ${selectedIds.size} Ride${if (selectedIds.size == 1) "" else "s"}?") },
            text = { Text("This will permanently delete the selected rides and GPX data. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    selectedIds.forEach { history.delete(it) }
                    isSelectionMode = false
                    selectedIds = emptySet()
                    showBulkDeleteConfirm = false
                }) { Text("Delete", color = Color(0xFFD33B2C)) }
            },
            dismissButton = { TextButton(onClick = { showBulkDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    PrCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(74.dp).background(Pr.coralSoft, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Motorcycle, contentDescription = null, tint = Pr.coral, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("No rides recorded yet", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Pr.ink))
            Spacer(Modifier.height(6.dp))
            Text(
                "Start a solo or group ride to view telemetry and routes here.",
                style = TextStyle(fontSize = 13.sp, color = Pr.muted),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RideHistoryRow(
    ride: RideRecord,
    dateText: String,
    isExpanded: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    scoreSummary: RideScoreSummary?,
    scoreLoading: Boolean,
    onOpenTelemetry: () -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onShareStats: () -> Unit,
    onExportGpx: () -> Unit,
    onViewRoute: () -> Unit,
    onPostToFeed: () -> Unit,
    onParticipants: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().combinedClickable(onClick = onTap, onLongClick = onLongPress)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isSelectionMode) {
                Icon(
                    if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) Pr.coral else Pr.muted,
                    modifier = Modifier.size(20.dp)
                )
            }
            Box(
                modifier = Modifier.size(44.dp).background(if (!ride.isGroupRide) Pr.coralSoft else Pr.teal.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (!ride.isGroupRide) Icons.Default.Person else Icons.Default.Group,
                    contentDescription = null,
                    tint = if (!ride.isGroupRide) Pr.coral else Pr.teal,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (ride.isTrackSession) "Track Session" else if (ride.isGroupRide) "Group Ride" else "Solo Ride",
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Pr.ink)
                    )
                    if (ride.isGroupRide) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(if (ride.isLeader) Pr.coralSoft else Pr.teal.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (ride.isLeader) "LEADER" else "JOINED",
                                style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp, color = if (ride.isLeader) Pr.coral else Pr.teal)
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(com.karthik.packride.data.MeasurementUnits.distanceMiles(ride.distanceMiles), style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Pr.coral))
                }
                Row(Modifier.fillMaxWidth()) {
                    Text(dateText, style = TextStyle(fontSize = 12.sp, color = Pr.muted))
                    Spacer(Modifier.weight(1f))
                    Text(ride.durationFormatted, style = TextStyle(fontSize = 12.sp, color = Pr.muted))
                }
            }
        }

        if (isExpanded) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Pr.border))
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    DetailStat("Top Speed", com.karthik.packride.data.MeasurementUnits.speedMph(ride.maxSpeedMph), Modifier.weight(1f))
                    DetailStat("Duration", ride.durationFormatted, Modifier.weight(1f))
                    DetailStat("Distance", com.karthik.packride.data.MeasurementUnits.distanceMiles(ride.distanceMiles), Modifier.weight(1f))
                }

                if (ride.hasRouteData) {
                    if (scoreSummary != null) {
                        RideScoreCard(summary = scoreSummary, maxLeanAngle = ride.maxLeanAngle, onClick = onOpenTelemetry)
                    } else if (scoreLoading) {
                        Text(
                            "Calculating ride score…",
                            style = TextStyle(fontSize = 11.sp, color = Pr.muted)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RideActionButton(Icons.Default.Share, Pr.coral, Pr.coralSoft, onShareStats)
                    if (ride.isGroupRide) {
                        RideActionButton(Icons.Default.Group, Pr.teal, Pr.teal.copy(alpha = .12f), onParticipants)
                    }
                    RideActionButton(
                        Icons.Default.Description,
                        if (ride.hasRouteData) Color.White else Pr.muted,
                        if (ride.hasRouteData) Pr.coral else Pr.fieldBg,
                        onExportGpx, enabled = ride.hasRouteData
                    )
                    RideActionButton(Icons.Default.Map, Color.White, Pr.teal, onViewRoute)
                    RideActionButton(Icons.AutoMirrored.Filled.Send, Color.White, Pr.inkFixed, onPostToFeed)
                    RideActionButton(Icons.Default.Delete, Color(0xFFD33B2C), Color(0xFFD33B2C).copy(alpha = 0.1f), onDelete)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Pr.teal.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onViewRoute)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, tint = Pr.teal, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Replay Ride", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Pr.teal))
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (ride.hasRouteData) "GPX includes route, speed, and duration data."
                        else "This ride has no route data recorded.",
                        style = TextStyle(fontSize = 10.sp, color = Pr.muted)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Pr.ink))
        Text(label, style = TextStyle(fontSize = 10.sp, color = Pr.muted))
    }
}

@Composable
private fun HistoryQuickLink(glyph: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // Text/emoji glyphs rather than Icons.Filled.EmojiEvents/CalendarMonth/
    // ShowChart -- this project's standing rule is only grep-confirmed icon
    // names, and trophy/chart-line icons weren't confirmed compiling
    // anywhere else in this codebase.
    Row(
        modifier = modifier
            .background(Pr.fieldBg)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(glyph, style = TextStyle(fontSize = 12.sp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Pr.coral))
    }
}

// Aug 31, 2026 -- iOS RideScoreCard (SoloRideView.swift ~line 557): a ring
// gauge (score 0-100) + grade label, a divider, and a 4-stat breakdown row
// (smooth corners, max lean, hard brakes, hard accels). iOS wraps this in a
// tappable Button that opens a telemetry map -- that map doesn't exist on
// Android yet (out of scope here), so this card renders non-interactively,
// matching iOS's own showChevron=false variant (SoloRideView's non-tappable
// use of the same component) rather than a button that goes nowhere.
@Composable
private fun RideScoreCard(summary: RideScoreSummary, maxLeanAngle: Double, onClick: () -> Unit) {
    // Resolve the adaptive Compose color before entering Canvas's non-composable
    // DrawScope. Accessing Pr.border from inside the draw lambda does not compile.
    val scoreTrackColor = Pr.border
    val scoreColor = when (summary.rideScore) {
        in 90..100 -> Color(0xFF2E9E5B)
        in 75..89 -> Pr.teal
        in 55..74 -> Pr.coral
        else -> Color(0xFFD33B2C)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Pr.cardBg, RoundedCornerShape(18.dp))
            .border(width = 1.dp, color = Pr.border, shape = RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(60.dp)) {
                    val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(
                        color = scoreTrackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = stroke
                    )
                    drawArc(
                        color = scoreColor,
                        startAngle = -90f,
                        sweepAngle = 360f * (summary.rideScore / 100f),
                        useCenter = false,
                        style = stroke
                    )
                }
                Text(
                    "${summary.rideScore}",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Pr.ink)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Ride Score: ${summary.scoreGrade}",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Pr.ink)
                )
                Text(
                    "Estimated from GPS + motion sensors — not a precise instrument",
                    style = TextStyle(fontSize = 10.sp, color = Pr.muted)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Pr.border))
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth()) {
            AnalyticsStatItem("🔄", "${summary.smoothCornerCount}/${summary.cornerCount}", "Smooth Corners", Modifier.weight(1f))
            AnalyticsStatItem("📐", "%.0f°".format(maxLeanAngle), "Max Lean", Modifier.weight(1f))
            AnalyticsStatItem("✋", "${summary.hardBrakeCount}", "Hard Brakes", Modifier.weight(1f))
            AnalyticsStatItem("⚡", "${summary.hardAccelCount}", "Hard Accels", Modifier.weight(1f))
        }
    }
}

@Composable
private fun AnalyticsStatItem(glyph: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(glyph, style = TextStyle(fontSize = 12.sp))
        Text(value, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Pr.ink))
        Text(label, style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Pr.muted), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun RideActionButton(icon: ImageVector, tint: Color, background: Color, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(background, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
    }
}

private data class ParticipantStat(
    val name: String,
    val initials: String,
    val leader: Boolean,
    val distance: Double,
    val maxSpeed: Double,
    val duration: String
)

@Composable
private fun ParticipantsStatsScreen(rideCode: String, onClose: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var stats by remember { mutableStateOf<List<ParticipantStat>>(emptyList()) }

    LaunchedEffect(rideCode) {
        FirebaseDatabase.getInstance().reference
            .child("rides").child(rideCode).child("finalStats").get()
            .addOnCompleteListener { task ->
                stats = if (!task.isSuccessful) emptyList() else task.result.children.mapNotNull { child ->
                    val data = child.value as? Map<*, *> ?: return@mapNotNull null
                    ParticipantStat(
                        name = data["name"] as? String ?: return@mapNotNull null,
                        initials = data["initials"] as? String ?: "?",
                        leader = data["isLeader"] as? Boolean ?: false,
                        distance = (data["distance"] as? Number)?.toDouble() ?: 0.0,
                        maxSpeed = (data["maxSpeed"] as? Number)?.toDouble() ?: 0.0,
                        duration = data["duration"] as? String ?: "00:00:00"
                    )
                }.sortedByDescending { it.leader }
                loading = false
            }
    }

    Column(Modifier.fillMaxSize().background(Pr.bg)) {
        Row(
            Modifier.fillMaxWidth().background(Pr.cardBg).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("THE PACK", style = PrFont.sectionHeader)
                Text("Participants' Stats", style = PrFont.heading)
            }
            TextButton(onClick = onClose) { Text("Close", color = Pr.ink) }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Pr.coral)
            }
            stats.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No participant stats saved for this ride yet", style = PrFont.caption)
            }
            else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                stats.forEachIndexed { index, stat ->
                    if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Pr.border))
                    Row(
                        Modifier.fillMaxWidth().background(Pr.cardBg).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(44.dp).background(if (stat.leader) Pr.coralSoft else Pr.teal.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                            Text(stat.initials, color = if (stat.leader) Pr.coral else Pr.teal, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stat.name + if (stat.leader) " · LEADER" else "", style = PrFont.subheading)
                            Text("${com.karthik.packride.data.MeasurementUnits.distanceMiles(stat.distance)}  ·  ${com.karthik.packride.data.MeasurementUnits.speedMph(stat.maxSpeed)}  ·  ${stat.duration}", style = PrFont.caption)
                        }
                    }
                }
            }
        }
    }
}
