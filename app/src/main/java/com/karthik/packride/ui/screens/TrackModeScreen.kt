package com.karthik.packride.ui.screens

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.location.Location
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
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
import com.karthik.packride.analytics.LapAnalyticsEngine
import com.karthik.packride.analytics.LapAnalyticsSummary
import com.karthik.packride.garage.GarageManager
import com.karthik.packride.gpx.GPXRecorder
import com.karthik.packride.gpx.GPXStorage
import com.karthik.packride.gpx.GpxCloudUpload
import com.karthik.packride.lap.LapEngine
import com.karthik.packride.lap.GeoPoint
import com.karthik.packride.lap.GateDirection
import com.karthik.packride.lap.TimingGate
import com.karthik.packride.lap.TrackTimingConfiguration
import com.karthik.packride.lap.FirebaseTrackRepository
import com.karthik.packride.lap.KnownTrackConfiguration
import com.karthik.packride.lap.NearbyKnownTrack
import com.karthik.packride.lap.LapInvalidReason
import com.karthik.packride.lap.LapSharingManager
import com.karthik.packride.lap.TrackDiscoveryService
import com.karthik.packride.feed.RideFeedManager
import com.karthik.packride.friends.FriendsManager
import com.karthik.packride.auth.rideInitials
import com.karthik.packride.data.UserPrefs
import com.karthik.packride.group.PendingReplay
import com.karthik.packride.location.SharedLocationManager
import com.karthik.packride.motion.RideMotionMonitor
import com.karthik.packride.ride.RideHistoryManager
import com.karthik.packride.ride.RideRecord
import com.karthik.packride.share.RideShareCard
import com.karthik.packride.ui.theme.Pr
import com.karthik.packride.ui.theme.PrCard
import com.karthik.packride.ui.theme.PrCoral
import com.karthik.packride.ui.theme.PrFont
import com.karthik.packride.ui.theme.PrMetricStrip
import com.karthik.packride.ui.theme.PrPageHeader
import com.karthik.packride.ui.theme.PrTeal
import com.karthik.packride.ui.theme.PrWebSectionLabel
import com.karthik.packride.ui.theme.PrimaryButton
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import kotlin.math.cos

/**
 * Track Mode — GPS-driven lap-timing session screen. Kotlin port of the
 * LIVE-RECORDING portion of iOS's LapModeView.swift (its TrackModeView +
 * ActiveLapView structs) — the single largest screen in the iOS app at
 * ~1861 lines, roughly double anything else ported so far in this
 * engagement. This pass is deliberately scoped to the real, valuable core
 * rather than a blind 1:1 port. Post-session analysis already has its own
 * Android screens (LapCompareScreen / LapTrendsScreen, MoreScreen tabs
 * 1/2, backed by lap/LapCompareEngine.kt) — this screen hands off to them
 * via onOpenCompare/onOpenTrends instead of rebuilding that UI here.
 *
 * ---------------------------------------------------------------------
 * SCOPED OUT of this pass — present in LapModeView.swift, deliberately not
 * ported here, because either the supporting platform infrastructure does
 * not exist on Android yet or the payoff doesn't justify the size for a
 * single pass:
 *
 *  - Track-name search via MKLocalSearch + auto-fetched global track-layout
 *    polylines (TrackLayoutModels.swift / TrackLayoutService.swift, ~86
 *    lines). Android has no Places/geocoding SDK wired into this app
 *    anywhere yet (grepped — none). What ships instead: a plain track-name
 *    field plus a faithful 1:1 port of iOS's OTHER location feature,
 *    TrackLocationStore — "remember the start/finish pin I set the last
 *    time I typed this exact track name" — which needs no network API at
 *    all, see the private object below.
 *  - Post-to-Feed sheet (PostLapToFeedSheet) and LapSharingManager
 *    (share a specific session/lap with a friend). Not wired up for Track
 *    Mode sessions in this pass.
 *  - Map style picker (satellite/hybrid/standard toggle) and the pin
 *    drop/pulse entrance animation — purely cosmetic, trimmed for scope.
 *  - Draggable pin fine-tuning (iOS lets you drag the placed pin a few
 *    meters to nudge it). Android instead uses tap-to-place, plus the
 *    location button doubles as a clear-and-retap toggle once a pin is
 *    set — same end result, less custom gesture code, and doesn't depend
 *    on unverified maps-compose drag-marker behavior.
 *  - A dedicated "Recent Sessions" history sheet on this screen. Android's
 *    LapTrendsScreen (MoreScreen tab 2) already lists every recorded
 *    session with lap times pulled from the same RideHistoryManager this
 *    screen records into, so this screen links there (onOpenTrends)
 *    instead of duplicating that list.
 *
 * What IS real and functional below:
 *  - GPS-driven automatic lap-crossing detection via lap/LapEngine.kt,
 *    which already existed as a complete 1:1 port (dual-radius crossing/
 *    clear zones, interpolated crossing timestamp, live pace-delta trace
 *    against the best lap) — this screen is what was missing to actually
 *    surface it.
 *  - Live current-lap timer, best-lap tracking, and a color-coded
 *    delta-vs-best-lap readout (green = ahead, red = behind — iOS's own
 *    convention, ported exactly: negative delta is green/ahead).
 *  - A running list of this session's completed laps with the best one
 *    highlighted.
 *  - A real Google Map for choosing the start/finish line: tap the map, or
 *    use your current GPS fix, with per-track-name pin memory.
 *  - Session lifecycle wired to SharedLocationManager + GPXRecorder +
 *    RideMotionMonitor. The G-force/lean sensor feed into the session's
 *    GPX (RideMotionMonitor, already used by solo rides) was previously
 *    never wired into Track Mode at all — this pass wires it in, same as
 *    iOS's ActiveLapView motion capture — so Track Mode's recorded GPX
 *    files now actually carry real lean/G-force samples instead of flat
 *    defaults.
 *  - Recording finished sessions into RideHistoryManager, which
 *    LapCompareScreen and LapTrendsScreen both already read from.
 *  - Aug 31, 2026 — Track Score: analytics/RideAnalyticsEngine.kt's
 *    LapAnalyticsEngine (consistency from the lap splits + smoothness from
 *    the session's recorded GPX, iOS LapAnalyticsEngine/TrackScoreCard
 *    parity) now runs right when a session ends, off the main thread, and
 *    the summary screen shows its score/grade/breakdown — see endSession()
 *    and TrackScoreCard below.
 */

private enum class TrackModeStage { SETUP, ACTIVE, SUMMARY }
private enum class GateEditKind { START_FINISH, SECTOR, FINISH, PIT_ENTRY, PIT_EXIT }

// Racing convention ported from iOS LapEngine's deltaColor(for:): negative
// delta (current lap running ahead of the best lap's own pace) is green;
// positive (behind) is red. Same RGB values as iOS's Color(red:green:blue:).
private val DeltaAheadGreen = Color(0xFF2E9E5B)
private val DeltaBehindRed = Color(0xFFD33B2C)
private const val TRACK_NOTICE_PREFS = "packride_track_mode"
private const val TRACK_NOTICE_ACKNOWLEDGED = "trackDataAndGpsNoticeAcknowledgedV2"

private enum class TraceState { COAST, ACCELERATE, BRAKE }
private data class LiveTracePoint(val position: LatLng, val speedMps: Float, val state: TraceState)

/** Longitude offset for one half of a 30 metre default timing gate. */
private fun gateHalfLongitudeDegrees(latitude: Double): Double =
    15.0 / (111_320.0 * cos(Math.toRadians(latitude)).coerceAtLeast(0.15))

@Composable
fun TrackModeScreen(
    onOpenCompare: () -> Unit = {},
    onOpenTrends: () -> Unit = {},
    onOpenReplay: () -> Unit = {}
) {
    var dragMode by rememberSaveable { mutableStateOf(false) }
    if (dragMode) {
        DragModeScreen(onOpenCircuit = { dragMode = false })
    } else {
        CircuitTrackModeScreen(onOpenCompare, onOpenTrends, onOpenReplay, onOpenDrag = { dragMode = true })
    }
}

@Composable
private fun CircuitTrackModeScreen(
    onOpenCompare: () -> Unit = {},
    onOpenTrends: () -> Unit = {},
    onOpenReplay: () -> Unit = {},
    onOpenDrag: () -> Unit = {}
) {
    val context = LocalContext.current
    val locationManager = remember { SharedLocationManager.get() }
    val lapEngine = remember { LapEngine() }
    val gpxRecorder = remember { GPXRecorder(context.filesDir) }
    val motion = remember { RideMotionMonitor(context) }
    val historyManager = remember { RideHistoryManager(context) }

    var stage by remember { mutableStateOf(TrackModeStage.SETUP) }
    var trackName by remember { mutableStateOf("") }
    var startLat by remember { mutableStateOf<Double?>(null) }
    var startLng by remember { mutableStateOf<Double?>(null) }
    var finishLat by remember { mutableStateOf<Double?>(null) }
    var finishLng by remember { mutableStateOf<Double?>(null) }
    var gateDirection by remember { mutableStateOf(GateDirection.POSITIVE_TO_NEGATIVE) }
    var selectedConfiguration by remember { mutableStateOf<KnownTrackConfiguration?>(null) }
    var nearbyTracks by remember { mutableStateOf<List<NearbyKnownTrack>>(emptyList()) }
    var showNearbyChooser by remember { mutableStateOf(false) }
    var configurationChoices by remember { mutableStateOf<List<KnownTrackConfiguration>>(emptyList()) }
    var configurationTrackId by remember { mutableStateOf("") }
    var configurationTrackName by remember { mutableStateOf("") }
    var pendingConfirmationTrack by remember { mutableStateOf<NearbyKnownTrack?>(null) }
    var pendingConfirmationConfiguration by remember { mutableStateOf<KnownTrackConfiguration?>(null) }
    var nearbyLookupDone by remember { mutableStateOf(false) }
    var liveTrace by remember { mutableStateOf<List<LiveTracePoint>>(emptyList()) }
    var priorTraceLocation by remember { mutableStateOf<Location?>(null) }
    var gateEditKind by remember { mutableStateOf(GateEditKind.START_FINISH) }
    var draftGatePoint by remember { mutableStateOf<GeoPoint?>(null) }
    var customSectors by remember { mutableStateOf<List<TimingGate>>(emptyList()) }
    var customFinishGate by remember { mutableStateOf<TimingGate?>(null) }
    var customPitEntryGate by remember { mutableStateOf<TimingGate?>(null) }
    var customPitExitGate by remember { mutableStateOf<TimingGate?>(null) }
    var sessionStartMs by remember { mutableStateOf<Long?>(null) }
    var showEndConfirm by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf(MapType.HYBRID) }
    var trackShareStatus by remember { mutableStateOf<String?>(null) }
    var showLayoutSubmission by remember { mutableStateOf(false) }
    var proposedLayoutName by remember { mutableStateOf("Main Circuit") }
    var pendingSubmissionRoute by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var pendingSubmissionTiming by remember { mutableStateOf<TrackTimingConfiguration?>(null) }
    var showAccuracyNotice by remember {
        mutableStateOf(!context.getSharedPreferences(TRACK_NOTICE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(TRACK_NOTICE_ACKNOWLEDGED, false))
    }
    var showMountingAndCalibrationNotice by remember { mutableStateOf(false) }
    var leanCalibrated by remember { mutableStateOf(false) }

    var finalLaps by remember { mutableStateOf<List<Double>>(emptyList()) }
    var finalBest by remember { mutableStateOf(0.0) }
    var finalDistance by remember { mutableStateOf(0.0) }
    var finalMaxSpeed by remember { mutableStateOf(0.0) }
    var finalGpxFile by remember { mutableStateOf<String?>(null) }
    var finalAnalytics by remember { mutableStateOf<LapAnalyticsSummary?>(null) }
    var finalRideId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val startFinishSet = startLat != null && startLng != null && finishLat != null && finishLng != null

    val location by locationManager.location.collectAsState()
    val speed by locationManager.speedMph.collectAsState()
    val distance by locationManager.distanceMiles.collectAsState()
    val maxSpeed by locationManager.maxSpeedMph.collectAsState()
    val currentLap by lapEngine.currentLapElapsed.collectAsState()
    val bestLap by lapEngine.bestLapTime.collectAsState()
    val bestLapCoordinates by lapEngine.bestLapCoordinates.collectAsState()
    val lastLap by lapEngine.lastLapTime.collectAsState()
    val laps by lapEngine.laps.collectAsState()
    val lapStartTimestamps by lapEngine.lapStartTimestamps.collectAsState()
    val delta by lapEngine.liveDeltaSeconds.collectAsState()
    val gpsAccuracy by lapEngine.gpsAccuracyMeters.collectAsState()
    val timingConfidence by lapEngine.timingConfidence.collectAsState()
    val sectorSplits by lapEngine.sectorSplits.collectAsState()
    val invalidLapReason by lapEngine.invalidLapReason.collectAsState()
    val bestSectorTimes by lapEngine.bestSectorTimes.collectAsState()
    val inPitLane by lapEngine.inPitLane.collectAsState()

    val hasFineLocation = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            locationManager.onPermissionGranted()
        }
    }

    fun ensurePermissions() {
        val need = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) {
            need += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = need.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(Unit) {
        ensurePermissions()
        locationManager.startUpdating(SharedLocationManager.REASON_MAP)
    }

    fun applyConfiguration(trackId: String, name: String, config: KnownTrackConfiguration) {
        selectedConfiguration = config
        trackName = name
        config.timing?.let { timing ->
            startLat = timing.startFinish.a.latitude
            startLng = timing.startFinish.a.longitude
            finishLat = timing.startFinish.b.latitude
            finishLng = timing.startFinish.b.longitude
            gateDirection = timing.startFinish.direction
        } ?: run {
            startLat = null; startLng = null; finishLat = null; finishLng = null
        }
        showNearbyChooser = false
        configurationChoices = emptyList()
    }

    fun selectCatalogLayout(track: NearbyKnownTrack, config: KnownTrackConfiguration) {
        if (config.timing == null) applyConfiguration(track.id, track.name, config)
        else {
            configurationChoices = emptyList()
            pendingConfirmationTrack = track
            pendingConfirmationConfiguration = config
        }
    }

    fun selectNearbyTrack(track: NearbyKnownTrack) {
        // Use the catalogue only to identify and center the venue. Imported
        // outlines/timing lines are not presented as defaults; the rider's
        // driven GPS laps become the proposed crowd-sourced layout.
        trackName = track.name
        selectedConfiguration = null
        startLat = null; startLng = null; finishLat = null; finishLng = null
        configurationChoices = emptyList()
        pendingConfirmationTrack = null
        pendingConfirmationConfiguration = null
        showNearbyChooser = false
    }

    // A catalogue match is only attempted once per screen visit and never
    // overwrites a name/line the rider has already entered manually.
    LaunchedEffect(location?.latitude, location?.longitude, nearbyLookupDone) {
        val loc = location ?: return@LaunchedEffect
        if (nearbyLookupDone || trackName.isNotBlank() || startFinishSet) return@LaunchedEffect
        nearbyLookupDone = true
        var matches = FirebaseTrackRepository.nearby(context, loc.latitude, loc.longitude).getOrDefault(emptyList())
        if (matches.isEmpty()) {
            matches = TrackDiscoveryService.nearbyTracks(LatLng(loc.latitude, loc.longitude)).getOrDefault(emptyList())
        }
        run {
            nearbyTracks = matches
            when (matches.size) {
                1 -> selectNearbyTrack(matches.first())
                in 2..Int.MAX_VALUE -> showNearbyChooser = true
            }
        }
    }

    // Remember whatever pin is currently set against the current track
    // name, so typing the same name again later auto-loads it. No-op for a
    // blank name — matches iOS's rememberCurrentPin() guard.
    LaunchedEffect(startLat, startLng, trackName) {
        val lat = startLat
        val lng = startLng
        if (lat != null && lng != null) {
            TrackLocationStore.save(context, trackName, lat, lng)
        }
    }

    // Feed GPS into the lap engine + GPX + motion sensors while a session
    // is running.
    LaunchedEffect(stage, location) {
        if (stage != TrackModeStage.ACTIVE) return@LaunchedEffect
        val loc = location ?: return@LaunchedEffect
        lapEngine.processLocation(loc)
        val previous = priorTraceLocation
        val acceleration = if (previous != null && loc.time > previous.time) (loc.speed - previous.speed) / ((loc.time - previous.time) / 1000f) else 0f
        val traceState = when { acceleration > 0.8f -> TraceState.ACCELERATE; acceleration < -1.1f -> TraceState.BRAKE; else -> TraceState.COAST }
        liveTrace = (liveTrace + LiveTracePoint(LatLng(loc.latitude, loc.longitude), loc.speed, traceState)).takeLast(1200)
        priorTraceLocation = Location(loc)
        gpxRecorder.currentGForce = motion.currentGForce
        gpxRecorder.currentLeanAngle = motion.currentLeanDegrees
        gpxRecorder.capturePoint(loc)
    }

    val stageAtDispose by rememberUpdatedState(stage)
    DisposableEffect(Unit) {
        onDispose {
            locationManager.stopUpdating(SharedLocationManager.REASON_MAP)
            if (stageAtDispose == TrackModeStage.ACTIVE) {
                locationManager.stopTracking(SharedLocationManager.REASON_LAP_TRACKING)
                lapEngine.stop()
                motion.stop()
                gpxRecorder.cancelRecording()
            }
        }
    }

    fun startSession() {
        val lat = startLat ?: return
        val lng = startLng ?: return
        val endLat = finishLat ?: return
        val endLng = finishLng ?: return
        // Permission requests are asynchronous. Starting the location
        // foreground service in the same click before permission is granted
        // can throw a SecurityException/ForegroundServiceStartNotAllowedException
        // and make the app appear to exit on modern Android.
        if (!hasFineLocation) {
            trackShareStatus = "Allow precise location, then tap Start Session again."
            ensurePermissions()
            return
        }
        try {
            locationManager.resetTracking()
            locationManager.startTracking(SharedLocationManager.REASON_LAP_TRACKING)
            val safeName = trackName.ifBlank { "Track Session" }
            gpxRecorder.startRecording("${safeName}_Laps")
            val gate = TimingGate(
                    id = "start-finish",
                    a = GeoPoint(lat, lng),
                    b = GeoPoint(endLat, endLng),
                    direction = gateDirection
            )
            val base = selectedConfiguration?.timing ?: TrackTimingConfiguration(gate)
            if (selectedConfiguration?.timing == null && customSectors.isEmpty() && customFinishGate == null) {
                // A one-point manual gate has no inherent orientation. Learn
                // a centered line perpendicular to the first valid moving
                // GPS course instead of assuming east-west.
                lapEngine.begin((lat + endLat) / 2.0, (lng + endLng) / 2.0, gateDirection)
            } else {
                lapEngine.begin(base.copy(
                    startFinish = gate,
                    sectors = if (customSectors.isNotEmpty()) customSectors else base.sectors,
                    finishGate = customFinishGate ?: base.finishGate,
                    pitEntryGate = customPitEntryGate ?: base.pitEntryGate,
                    pitExitGate = customPitExitGate ?: base.pitExitGate
                ))
            }
            motion.start()
            liveTrace = emptyList()
            priorTraceLocation = null
            sessionStartMs = System.currentTimeMillis()
            trackShareStatus = null
            stage = TrackModeStage.ACTIVE
        } catch (error: Throwable) {
            runCatching { locationManager.stopTracking(SharedLocationManager.REASON_LAP_TRACKING) }
            lapEngine.stop()
            motion.stop()
            gpxRecorder.cancelRecording()
            trackShareStatus = "Could not start GPS timing: ${error.localizedMessage ?: "location service unavailable"}"
        }
    }

    fun shareCustomTrack() {
        val lat = startLat ?: return
        val lng = startLng ?: return
        val endLat = finishLat ?: return
        val endLng = finishLng ?: return
        if (trackName.isBlank()) return
        val gate = TimingGate("start-finish", GeoPoint(lat, lng), GeoPoint(endLat, endLng), gateDirection)
        trackShareStatus = "Submitting…"
        coroutineScope.launch {
            FirebaseTrackRepository.saveCustomTrack(
                trackName,
                GeoPoint((lat + endLat) / 2.0, (lng + endLng) / 2.0),
                KnownTrackConfiguration("main", "Main Circuit", emptyList(), TrackTimingConfiguration(gate))
            ).onSuccess { trackShareStatus = "Community layout submitted for review" }
                .onFailure { trackShareStatus = it.localizedMessage ?: "Submission failed" }
        }
    }

    fun endSession() {
        val completedLaps = laps
        val recordedBestRoute = bestLapCoordinates
        val best = bestLap ?: 0.0
        val dist = distance
        val top = maxSpeed
        val gpxFile = gpxRecorder.stopAndSave()

        locationManager.stopTracking(SharedLocationManager.REASON_LAP_TRACKING)
        lapEngine.stop()
        motion.stop()

        // Total session wall time (not just the sum of completed laps,
        // which would silently drop out-lap/in-lap time and any partial
        // final lap).
        val elapsedSeconds = sessionStartMs?.let {
            ((System.currentTimeMillis() - it) / 1000L).toInt().coerceAtLeast(0)
        } ?: completedLaps.sum().toInt()

        if (completedLaps.isNotEmpty() || dist > 0) {
            val record = RideRecord(
                    distanceMiles = dist,
                    maxSpeedMph = top,
                    durationSeconds = elapsedSeconds,
                    rideCode = "TRACK",
                    gpxFileName = gpxFile,
                    trackName = trackName.ifBlank { "Track Session" },
                    lapTimes = completedLaps,
                    lapStartTimestamps = lapStartTimestamps.take(completedLaps.size),
                    bikeId = GarageManager.currentActiveBikeID(context)
                )
            historyManager.record(record)
            finalRideId = record.id
            if (!gpxFile.isNullOrBlank()) {
                GpxCloudUpload.upload(GPXStorage.resolve(context.filesDir, gpxFile), record.id) { result ->
                    result.onSuccess { url -> historyManager.setGpxUrl(record.id, url) }
                }
            }
        }

        finalLaps = completedLaps
        finalBest = best
        finalDistance = dist
        finalMaxSpeed = top
        finalGpxFile = gpxFile
        finalAnalytics = null
        stage = TrackModeStage.SUMMARY

        if (selectedConfiguration?.timing == null && completedLaps.size >= 2 && recordedBestRoute.size >= 20) {
            val lat = startLat
            val lng = startLng
            val endLat = finishLat
            val endLng = finishLng
            if (lat != null && lng != null && endLat != null && endLng != null) {
                pendingSubmissionRoute = recordedBestRoute
                pendingSubmissionTiming = TrackTimingConfiguration(
                    startFinish = TimingGate("start-finish", GeoPoint(lat, lng), GeoPoint(endLat, endLng), gateDirection),
                    sectors = customSectors,
                    finishGate = customFinishGate,
                    pitEntryGate = customPitEntryGate,
                    pitExitGate = customPitExitGate
                )
                proposedLayoutName = selectedConfiguration?.name ?: "Main Circuit"
                showLayoutSubmission = true
            }
        }

        // Track Score — consistency from the lap splits + smoothness from
        // the GPX just saved above (LapAnalyticsEngine.analyzeAsync already
        // dispatches to Dispatchers.Default internally). Launched after
        // stage flips to SUMMARY so the summary screen appears immediately;
        // the score card fades in once this resolves, same "don't block on
        // GPX parsing" approach as BadgeEngine.computeAsync.
        coroutineScope.launch {
            finalAnalytics = LapAnalyticsEngine.analyzeAsync(
                gpxFile?.let { GPXStorage.resolve(context.filesDir, it) },
                completedLaps
            )
            finalAnalytics?.let { score ->
                finalRideId?.let { id ->
                    historyManager.setTrackScores(id, score.trackScore, score.consistencyScore, score.smoothnessScore)
                }
            }
        }
    }

    fun newSession() {
        finalLaps = emptyList()
        finalGpxFile = null
        finalAnalytics = null
        finalRideId = null
        stage = TrackModeStage.SETUP
    }

    when (stage) {
        TrackModeStage.SETUP -> SetupContent(
            trackName = trackName,
            onTrackNameChange = { trackName = it },
            startLat = startLat,
            startLng = startLng,
            finishLat = finishLat,
            finishLng = finishLng,
            knownConfiguration = selectedConfiguration,
            customSectors = customSectors,
            customFinishGate = customFinishGate,
            customPitEntryGate = customPitEntryGate,
            customPitExitGate = customPitExitGate,
            gateEditKind = gateEditKind,
            startFinishSet = startFinishSet,
            hasFineLocation = hasFineLocation,
            currentLocation = location,
            onCatalogSearch = { query ->
                val loc = location
                val matches = FirebaseTrackRepository.matching(context, query, loc?.latitude, loc?.longitude)
                    .getOrDefault(emptyList())
                when (matches.size) {
                    0 -> false
                    1 -> { selectNearbyTrack(matches.first()); true }
                    else -> { nearbyTracks = matches; showNearbyChooser = true; true }
                }
            },
            onMapTap = { lat, lng ->
                val point = GeoPoint(lat, lng)
                if (gateEditKind == GateEditKind.START_FINISH) {
                    selectedConfiguration = selectedConfiguration?.takeIf { it.timing == null }
                    val half = gateHalfLongitudeDegrees(lat)
                    startLat = lat; startLng = lng - half
                    finishLat = lat; finishLng = lng + half
                } else if (draftGatePoint == null) {
                    draftGatePoint = point
                } else {
                    val newGate = TimingGate("${gateEditKind.name.lowercase()}-${System.currentTimeMillis()}", draftGatePoint!!, point, GateDirection.POSITIVE_TO_NEGATIVE)
                    when (gateEditKind) {
                        GateEditKind.SECTOR -> customSectors = customSectors + newGate
                        GateEditKind.FINISH -> customFinishGate = newGate
                        GateEditKind.PIT_ENTRY -> customPitEntryGate = newGate
                        GateEditKind.PIT_EXIT -> customPitExitGate = newGate
                        else -> Unit
                    }
                    draftGatePoint = null
                }
            },
            onGateEditKind = { gateEditKind = it; draftGatePoint = null },
            onClearCustomGates = { customSectors = emptyList(); customFinishGate = null; customPitEntryGate = null; customPitExitGate = null; draftGatePoint = null },
            onUseCurrentLocation = {
                if (startFinishSet) {
                    selectedConfiguration = null
                    startLat = null
                    startLng = null
                    finishLat = null
                    finishLng = null
                } else {
                    location?.let {
                        val half = gateHalfLongitudeDegrees(it.latitude)
                        startLat = it.latitude; startLng = it.longitude - half
                        finishLat = it.latitude; finishLng = it.longitude + half
                    }
                }
            },
            gateDirection = gateDirection,
            onReverseDirection = {
                // Keep the physical timing line and selected layout fixed.
                // Swapping A/B as well as flipping this enum reverses the
                // side test twice, so the accepted travel direction does
                // not actually change.
                gateDirection = if (gateDirection == GateDirection.POSITIVE_TO_NEGATIVE) GateDirection.NEGATIVE_TO_POSITIVE else GateDirection.POSITIVE_TO_NEGATIVE
            },
            canShareCustomTrack = false,
            trackShareStatus = trackShareStatus,
            onShareCustomTrack = { shareCustomTrack() },
            onOpenCompare = onOpenCompare,
            onOpenTrends = onOpenTrends,
            onOpenDrag = onOpenDrag,
            mapType = mapType,
            onMapType = { mapType = it },
            onStartSession = { showMountingAndCalibrationNotice = true }
        )

        TrackModeStage.ACTIVE -> ActiveContent(
            trackName = trackName.ifBlank { "Track Session" },
            startLat = startLat,
            startLng = startLng,
            finishLat = finishLat,
            finishLng = finishLng,
            currentLapElapsed = currentLap,
            bestLapTime = bestLap,
            lastLapTime = lastLap,
            laps = laps,
            liveDelta = delta,
            gpsAccuracyMeters = gpsAccuracy,
            timingConfidence = timingConfidence,
            sectorSplits = sectorSplits,
            invalidLapReason = invalidLapReason,
            bestSectorTimes = bestSectorTimes,
            liveTrace = liveTrace,
            inPitLane = inPitLane,
            speedMph = speed,
            distanceMiles = distance,
            maxSpeedMph = maxSpeed,
            leanCalibrated = leanCalibrated,
            hasFineLocation = hasFineLocation,
            mapType = mapType,
            onMapType = { mapType = it },
            onEndSessionTap = { showEndConfirm = true }
        )

        TrackModeStage.SUMMARY -> SummaryContent(
            trackName = trackName.ifBlank { "Track Session" },
            laps = finalLaps,
            bestLapTime = finalBest,
            distanceMiles = finalDistance,
            maxSpeedMph = finalMaxSpeed,
            gpxFileName = finalGpxFile,
            analytics = finalAnalytics,
            rideId = finalRideId,
            onOpenCompare = onOpenCompare,
            onOpenTrends = onOpenTrends,
            onOpenReplay = {
                finalRideId?.let { PendingReplay.request(it) }
                onOpenReplay()
            },
            onNewSession = { newSession() }
        )
    }

    LaunchedEffect(stage) {
        if (stage == TrackModeStage.ACTIVE) {
            leanCalibrated = false
            while (!motion.isLeanCalibrated) {
                delay(150)
            }
            leanCalibrated = true
        }
    }

    if (showMountingAndCalibrationNotice) {
        AlertDialog(
            onDismissRequest = { showMountingAndCalibrationNotice = false },
            title = { Text("Before you start") },
            text = {
                Text("Mount the phone securely on the handlebar with a clear view of the sky for a more accurate racing line and timing. Keep the motorcycle upright and the phone still for a few seconds after starting while PackRide calibrates lean angle.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showMountingAndCalibrationNotice = false
                    startSession()
                }) { Text("Start Session") }
            },
            dismissButton = {
                TextButton(onClick = { showMountingAndCalibrationNotice = false }) { Text("Cancel") }
            }
        )
    }

    if (showEndConfirm) {
        AlertDialog(
            onDismissRequest = { showEndConfirm = false },
            title = { Text("End session?") },
            text = { Text("You'll see your lap times and best lap.") },
            confirmButton = {
                TextButton(onClick = { showEndConfirm = false; endSession() }) {
                    Text("End Session", color = DeltaBehindRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirm = false }) { Text("Keep Riding") }
            }
        )
    }


    if (showNearbyChooser) {
        AlertDialog(
            onDismissRequest = { showNearbyChooser = false },
            title = { Text(if (com.karthik.packride.data.MeasurementUnits.current == com.karthik.packride.data.MeasurementSystem.METRIC) "Tracks within 16 km" else "Tracks within 10 miles") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose the circuit you are riding.")
                    nearbyTracks.forEach { track ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { selectNearbyTrack(track) }.padding(12.dp)
                        ) {
                            Text(track.name, fontWeight = FontWeight.Bold)
                            Text("${com.karthik.packride.data.MeasurementUnits.distanceMiles(track.distanceMiles)} away · ${track.verificationStatus}", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showNearbyChooser = false }) { Text("Search manually") } }
        )
    }

    if (configurationChoices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { configurationChoices = emptyList() },
            title = { Text("Choose $configurationTrackName layout") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    configurationChoices.forEach { config ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                val track = nearbyTracks.firstOrNull { it.id == configurationTrackId }
                                if (track != null) selectCatalogLayout(track, config)
                                else applyConfiguration(configurationTrackId, configurationTrackName, config)
                            }.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TrackLayoutMiniPreview(config.outline, Modifier.size(width = 88.dp, height = 58.dp))
                            Column(Modifier.weight(1f)) {
                                Text(config.name, fontWeight = FontWeight.Bold)
                                Text(config.timing?.let { "Start/finish saved · ${it.sectors.size} sectors" } ?: "Set start/finish", fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { configurationChoices = emptyList() }) { Text("Cancel") } }
        )
    }

    if (pendingConfirmationTrack != null && pendingConfirmationConfiguration != null) {
        val pendingTrack = pendingConfirmationTrack!!
        val pendingConfig = pendingConfirmationConfiguration!!
        AlertDialog(
            onDismissRequest = { pendingConfirmationTrack = null; pendingConfirmationConfiguration = null },
            title = { Text("Confirm this layout") },
            text = { Text("Are you riding \"${pendingConfig.name}\" with this same start/finish line? Confirming helps improve this community-sourced layout for the next rider.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingConfirmationTrack = null
                    pendingConfirmationConfiguration = null
                    applyConfiguration(pendingTrack.id, pendingTrack.name, pendingConfig)
                    coroutineScope.launch {
                        FirebaseTrackRepository.confirmLayout(pendingTrack.id, pendingConfig.id)
                            .onSuccess { trackShareStatus = "Community layout confirmed. Thank you!" }
                            .onFailure { trackShareStatus = "Layout loaded. Confirmation will be retried next time." }
                    }
                }) { Text("Yes, Use This Layout") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        pendingConfirmationTrack = null
                        pendingConfirmationConfiguration = null
                        applyConfiguration(pendingTrack.id, pendingTrack.name, pendingConfig.copy(timing = null))
                    }) { Text("Different Layout") }
                    TextButton(onClick = { pendingConfirmationTrack = null; pendingConfirmationConfiguration = null }) { Text("Cancel") }
                }
            }
        )
    }

    if (showAccuracyNotice) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Track Mode data and timing") },
            text = {
                Text(
                    "PackRide builds a circuit layout from the GPS trace you record after placing the start/finish point. " +
                    "After at least two valid laps, you can submit that layout for community review. " +
                        "PackRide uses your phone’s GPS and is not a certified professional lap timer. " +
                        "Mounting the phone securely on the handlebar with a clear view of the sky provides a much more accurate racing line and timing. " +
                        "In good conditions, recorded laps will typically be within about 1–2 seconds of professional timing. " +
                        "Weak GPS reception may produce larger differences or invalidate a lap."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    context.getSharedPreferences(TRACK_NOTICE_PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean(TRACK_NOTICE_ACKNOWLEDGED, true).apply()
                    showAccuracyNotice = false
                }) { Text("I Understand") }
            }
        )
    }

    if (showLayoutSubmission) {
        AlertDialog(
            onDismissRequest = { showLayoutSubmission = false },
            title = { Text("Share this track layout?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Your best completed lap will be submitted for review. Future riders can use it after it is verified.")
                    OutlinedTextField(
                        value = proposedLayoutName,
                        onValueChange = { proposedLayoutName = it },
                        label = { Text("Layout name") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val timing = pendingSubmissionTiming ?: return@TextButton
                    showLayoutSubmission = false
                    trackShareStatus = "Submitting…"
                    coroutineScope.launch {
                        FirebaseTrackRepository.submitCommunityLayout(
                            venueName = trackName.ifBlank { "Unnamed Track" },
                            layoutName = proposedLayoutName.ifBlank { "Main Circuit" },
                            route = pendingSubmissionRoute,
                            timing = timing,
                            completedLapCount = finalLaps.size,
                            timingConfidence = timingConfidence
                        ).onSuccess { trackShareStatus = "Community layout submitted for review" }
                            .onFailure { trackShareStatus = it.localizedMessage ?: "Submission failed" }
                    }
                }) { Text("Submit") }
            },
            dismissButton = { TextButton(onClick = { showLayoutSubmission = false }) { Text("Not Now") } }
        )
    }
}

// MARK: - Setup (choose start/finish line)

@Composable
private fun SetupContent(
    trackName: String,
    onTrackNameChange: (String) -> Unit,
    startLat: Double?,
    startLng: Double?,
    finishLat: Double?,
    finishLng: Double?,
    knownConfiguration: KnownTrackConfiguration?,
    customSectors: List<TimingGate>,
    customFinishGate: TimingGate?,
    customPitEntryGate: TimingGate?,
    customPitExitGate: TimingGate?,
    gateEditKind: GateEditKind,
    startFinishSet: Boolean,
    hasFineLocation: Boolean,
    currentLocation: Location?,
    onCatalogSearch: suspend (String) -> Boolean,
    onMapTap: (Double, Double) -> Unit,
    onGateEditKind: (GateEditKind) -> Unit,
    onClearCustomGates: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    gateDirection: GateDirection,
    onReverseDirection: () -> Unit,
    canShareCustomTrack: Boolean,
    trackShareStatus: String?,
    onShareCustomTrack: () -> Unit,
    onOpenCompare: () -> Unit,
    onOpenTrends: () -> Unit,
    onOpenDrag: () -> Unit,
    mapType: MapType,
    onMapType: (MapType) -> Unit,
    onStartSession: () -> Unit
) {
    val fallback = LatLng(37.33, -122.03)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(fallback, 12f)
    }
    var hasCenteredOnUser by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var trackLayouts by remember { mutableStateOf<List<List<LatLng>>>(emptyList()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun searchTrack() {
        if (trackName.isBlank() || isSearching) return
        isSearching = true
        searchError = null
        scope.launch {
            if (onCatalogSearch(trackName)) {
                searchError = null
                isSearching = false
                return@launch
            }
            TrackDiscoveryService.search(context, trackName, currentLocation?.let { LatLng(it.latitude, it.longitude) })
                .onSuccess { result ->
                    onTrackNameChange(result.name)
                    trackLayouts = emptyList()
                    cameraState.animate(CameraUpdateFactory.newLatLngZoom(result.center, 16f))
                    searchError = "Track found. Drop the start / finish pin, then ride at least two laps to build the GPS layout."
                }
                .onFailure {
                    searchError = if (it is java.io.IOException) it.message
                        ?: "Track layout service is temporarily unavailable. Please try again."
                    else if (com.karthik.packride.data.MeasurementUnits.current == com.karthik.packride.data.MeasurementSystem.METRIC) "Track not found within 16 km. Try its full name and city." else "Track not found within 10 miles. Try its full name and city."
                }
            isSearching = false
        }
    }

    LaunchedEffect(currentLocation?.latitude, currentLocation?.longitude) {
        val loc = currentLocation ?: return@LaunchedEffect
        if (!hasCenteredOnUser) {
            hasCenteredOnUser = true
            cameraState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 14f)
            )
        }
    }
    // Zoom in on the pin the moment it's set (tap, current-location button,
    // or a remembered-name lookup) — but only that once, not on every
    // recomposition while it stays set (guarded by the null -> non-null
    // transition captured as the LaunchedEffect key).
    LaunchedEffect(startLat != null && startLng != null) {
        val lat = startLat ?: return@LaunchedEffect
        val lng = startLng ?: return@LaunchedEffect
        cameraState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16f))
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            properties = MapProperties(mapType = mapType, isMyLocationEnabled = hasFineLocation),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
            onMapClick = { latLng -> onMapTap(latLng.latitude, latLng.longitude) }
        ) {
            trackLayouts.forEach { layout ->
                Polyline(points = layout, color = PrCoral, width = 8f)
            }
            if (startLat != null && startLng != null) {
                val point = if (knownConfiguration?.timing == null && finishLat != null && finishLng != null) {
                    LatLng((startLat + finishLat) / 2.0, (startLng + finishLng) / 2.0)
                } else LatLng(startLat, startLng)
                Marker(state = MarkerState(point), title = "Start/Finish", icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
            }
            if (knownConfiguration?.timing != null && finishLat != null && finishLng != null) {
                Marker(
                    state = MarkerState(LatLng(finishLat, finishLng)),
                    title = "Crossing direction",
                    icon = gateDirectionArrowDescriptor(),
                    rotation = if (gateDirection == GateDirection.POSITIVE_TO_NEGATIVE) 0f else 180f,
                    flat = true
                )
                if (startLat != null && startLng != null) {
                    Polyline(points = listOf(LatLng(startLat, startLng), LatLng(finishLat, finishLng)), color = PrCoral, width = 12f)
                }
            }
            customSectors.forEach { gate -> Polyline(points = listOf(LatLng(gate.a.latitude, gate.a.longitude), LatLng(gate.b.latitude, gate.b.longitude)), color = Color(0xFFB46CFF), width = 10f) }
            customFinishGate?.let { gate -> Polyline(points = listOf(LatLng(gate.a.latitude, gate.a.longitude), LatLng(gate.b.latitude, gate.b.longitude)), color = Color.White, width = 10f) }
            customPitEntryGate?.let { gate -> Polyline(points = listOf(LatLng(gate.a.latitude, gate.a.longitude), LatLng(gate.b.latitude, gate.b.longitude)), color = Color(0xFFF9B327), width = 10f) }
            customPitExitGate?.let { gate -> Polyline(points = listOf(LatLng(gate.a.latitude, gate.a.longitude), LatLng(gate.b.latitude, gate.b.longitude)), color = Color(0xFF00B8D9), width = 10f) }
        }

        Column(Modifier.fillMaxSize().zIndex(2f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(5f)
                    .padding(horizontal = 20.dp)
                    .padding(top = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "TRACK MODE",
                        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.6.sp, color = PrCoral)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        trackName.ifBlank { "Set your line" },
                        style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White),
                        maxLines = 1
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (startFinishSet) {
                            if (knownConfiguration?.timing != null) "Directional start / finish line ready"
                            else "Start / finish point ready — direction calibrates while moving"
                        } else "Tap once to place the start / finish point",
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.68f))
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HandoffPill(text = "Drag", onClick = onOpenDrag)
                    MapStylePill(mapType, onMapType)
                    HandoffPill(text = "Compare", onClick = onOpenCompare)
                    HandoffPill(text = "Trends", onClick = onOpenTrends)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                        .clickable {
                            searchFocusRequester.requestFocus()
                            keyboardController?.show()
                        }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (trackName.isEmpty()) {
                            Text("Track name", style = TextStyle(fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f)))
                        }
                        BasicTextField(
                            value = trackName,
                            onValueChange = onTrackNameChange,
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White),
                            cursorBrush = SolidColor(Color.White)
                            ,keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                            ,keyboardActions = KeyboardActions(onSearch = { searchTrack() })
                        )
                    }
                    if (isSearching) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp
                        )
                    }
                    if (trackName.isNotEmpty()) {
                        IconButton(onClick = { onTrackNameChange("") }, modifier = Modifier.size(22.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(15.dp))
                        }
                    }
                }

                val pinButtonEnabled = startFinishSet || currentLocation != null
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (startFinishSet) DeltaAheadGreen else Color.Black.copy(alpha = 0.48f))
                        .clickable(enabled = pinButtonEnabled, onClick = onUseCurrentLocation),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (startFinishSet) Icons.Filled.CheckCircle else Icons.Filled.LocationOn,
                        contentDescription = if (startFinishSet) "Clear start/finish" else "Use my location",
                        tint = Color.White
                    )
                }
            }

            val statusText = when {
                searchError != null -> searchError!!
                startFinishSet && knownConfiguration?.timing != null -> "Gate set — crossing direction: ${if (gateDirection == GateDirection.POSITIVE_TO_NEGATIVE) "forward" else "reverse"}"
                startFinishSet -> "Start / finish point set — timing direction calibrates from your movement"
                currentLocation == null -> "Waiting for GPS signal…"
                else -> "Tap the map, or use the location button, to place start/finish"
            }

            if (startFinishSet && knownConfiguration?.timing != null) {
                Text(
                    "Reverse crossing direction",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp).background(Color.Black.copy(alpha = .48f), RoundedCornerShape(50)).clickable(onClick = onReverseDirection).padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    statusText,
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White),
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(20.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    items(GateEditKind.entries) { kind ->
                        val label = when (kind) { GateEditKind.START_FINISH -> "START"; GateEditKind.SECTOR -> "+ SECTOR"; GateEditKind.FINISH -> "SPRINT FINISH"; GateEditKind.PIT_ENTRY -> "PIT IN"; GateEditKind.PIT_EXIT -> "PIT OUT" }
                        Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (gateEditKind == kind) PrCoral else Color.White.copy(alpha = .12f)).clickable { onGateEditKind(kind) }.padding(horizontal = 10.dp, vertical = 7.dp))
                    }
                    item {
                        Text("CLEAR GATES", color = DeltaBehindRed, fontSize = 9.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .12f)).clickable(onClick = onClearCustomGates).padding(horizontal = 10.dp, vertical = 7.dp))
                    }
                }
                Text(
                    "PHONE GPS TIMING · Not a professional lap timer. Times may differ by 1–2 seconds, or more when GPS reception is weak.",
                    color = Color.White.copy(alpha = .68f),
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StepLabel("01", "Choose line", startFinishSet, Modifier.weight(1f))
                    StepLabel("02", "Ride laps", false, Modifier.weight(1f))
                    StepLabel("03", "Compare", false, Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "READY TO RUN",
                            style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.8.sp, color = Color.White.copy(alpha = 0.55f))
                        )
                        Text(
                            "GPS timing + lap tracking",
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (startFinishSet) PrCoral else Color.White.copy(alpha = 0.18f))
                            .clickable(enabled = startFinishSet, onClick = onStartSession)
                            .padding(horizontal = 18.dp, vertical = 13.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("Start Session", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White))
                        }
                    }
                }
                if (canShareCustomTrack || trackShareStatus != null) {
                    Text(
                        trackShareStatus ?: "Contribute this layout for community review",
                        color = if (trackShareStatus == "Community layout submitted for review") DeltaAheadGreen else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .1f)).clickable(enabled = canShareCustomTrack && trackShareStatus != "Submitting…", onClick = onShareCustomTrack).padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Text(
                    "Your driven GPS laps form the circuit layout. After two valid laps, you can contribute it for community review.",
                    style = TextStyle(
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                )
            }
        }
    }
}

private fun gateDirectionArrowDescriptor(): com.google.android.gms.maps.model.BitmapDescriptor {
    val size = 64
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val coral = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(255, 106, 0) }
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    canvas.drawCircle(size / 2f, size / 2f, size * 0.46f, coral)
    val arrow = Path().apply {
        moveTo(size * 0.30f, size * 0.23f)
        lineTo(size * 0.76f, size * 0.50f)
        lineTo(size * 0.30f, size * 0.77f)
        close()
    }
    canvas.drawPath(arrow, white)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

@Composable
private fun StepLabel(number: String, title: String, done: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(number, style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Black, color = if (done) PrCoral else Color.White.copy(alpha = 0.42f)))
        Text(title, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = if (done) 0.92f else 0.55f)))
    }
}

@Composable
private fun TrackLayoutMiniPreview(points: List<GeoPoint>, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF0E0F12)).padding(7.dp)) {
        if (points.size < 2) {
            drawCircle(Color.White.copy(alpha = 0.25f), radius = 5.dp.toPx(), center = center)
            return@Canvas
        }
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLng = points.minOf { it.longitude }
        val maxLng = points.maxOf { it.longitude }
        val latRange = (maxLat - minLat).takeIf { it > 0.0 } ?: 1.0
        val lngRange = (maxLng - minLng).takeIf { it > 0.0 } ?: 1.0
        fun projected(point: GeoPoint) = Offset(
            x = (((point.longitude - minLng) / lngRange) * size.width).toFloat(),
            y = (size.height - ((point.latitude - minLat) / latRange * size.height).toFloat())
        )
        points.zipWithNext().forEach { (a, b) ->
            drawLine(Color.White.copy(alpha = 0.22f), projected(a), projected(b), strokeWidth = 9.dp.toPx(), cap = StrokeCap.Round)
        }
        points.zipWithNext().forEach { (a, b) ->
            drawLine(PrCoral, projected(a), projected(b), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun HandoffPill(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.42f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(text, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
    }
}

// MARK: - Active session (live lap timing)

@Composable
private fun ActiveContent(
    trackName: String,
    startLat: Double?,
    startLng: Double?,
    finishLat: Double?,
    finishLng: Double?,
    currentLapElapsed: Double,
    bestLapTime: Double?,
    lastLapTime: Double?,
    laps: List<Double>,
    liveDelta: Double?,
    gpsAccuracyMeters: Double?,
    timingConfidence: Double?,
    sectorSplits: List<Double>,
    invalidLapReason: LapInvalidReason?,
    bestSectorTimes: List<Double>,
    liveTrace: List<LiveTracePoint>,
    inPitLane: Boolean,
    speedMph: Double,
    distanceMiles: Double,
    maxSpeedMph: Double,
    leanCalibrated: Boolean,
    hasFineLocation: Boolean,
    mapType: MapType,
    onMapType: (MapType) -> Unit,
    onEndSessionTap: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val theoreticalBest = bestSectorTimes.takeIf { it.isNotEmpty() }?.sum()
    val cameraState = rememberCameraPositionState {
        if (startLat != null && startLng != null) {
            position = CameraPosition.fromLatLngZoom(LatLng(startLat, startLng), 15.5f)
        }
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
            // Fixed camera on the start/finish line rather than continuously
            // re-centering on the rider every fix — the line and the track
            // around it don't move, so there's no reason to pay a re-center
            // cost on every GPS update (same reasoning as iOS's Aug 27, 2026
            // ActiveLapView perf fix).
            properties = MapProperties(mapType = mapType, isMyLocationEnabled = hasFineLocation),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
        ) {
            if (startLat != null && startLng != null) {
                Marker(
                    state = MarkerState(position = LatLng(startLat, startLng)),
                    title = "Start / Finish",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                )
            }
            if (startLat != null && startLng != null && finishLat != null && finishLng != null) {
                Polyline(points = listOf(LatLng(startLat, startLng), LatLng(finishLat, finishLng)), color = PrCoral, width = 12f)
            }
            liveTrace.zipWithNext().forEach { (from, to) ->
                val traceColor = when (to.state) {
                    TraceState.BRAKE -> DeltaBehindRed
                    TraceState.ACCELERATE -> DeltaAheadGreen
                    TraceState.COAST -> when { to.speedMps >= 35f -> Color(0xFF9C4DFF); to.speedMps >= 20f -> Color(0xFF00B8D9); else -> Color.White }
                }
                Polyline(points = listOf(from.position, to.position), color = traceColor, width = 8f)
            }
        }

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(DeltaAheadGreen)
                        )
                        Text("LIVE SESSION", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = Color.White))
                    }
                    Text(trackName, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White), maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapStylePill(mapType, onMapType)
                    Icon(Icons.Filled.Flag, contentDescription = null, tint = PrCoral, modifier = Modifier.size(20.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) .58f else 1f)
                    .padding(horizontal = 16.dp)
                    .padding(top = 14.dp)
                    .background(Color.Black.copy(alpha = .72f), RoundedCornerShape(18.dp))
                    .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (leanCalibrated) "✓ Lean angle calibrated to your mount" else "● Keep phone still · calibrating lean angle…",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (leanCalibrated) DeltaAheadGreen else Color(0xFFF9B327),
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .background(Color.Black.copy(alpha = .48f), RoundedCornerShape(50))
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("CURRENT LAP", fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.8.sp, color = Color.White.copy(alpha = .55f))
                    val qualityColor = when { gpsAccuracyMeters == null -> Color(0xFFF9B327); gpsAccuracyMeters <= 8 -> DeltaAheadGreen; gpsAccuracyMeters <= 18 -> Color(0xFFF9B327); else -> DeltaBehindRed }
                    Text(gpsAccuracyMeters?.let { "GPS ±%.0fm".format(it) } ?: "GPS SEARCHING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = qualityColor)
                }
                Text(LapEngine.formatLapTime(currentLapElapsed), fontSize = 54.sp, lineHeight = 58.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = Color.White)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("BEST  ${bestLapTime?.let { LapEngine.formatLapTime(it) } ?: "--:--"}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DeltaAheadGreen)
                    Text("LAP ${laps.size + 1}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    timingConfidence?.let { Text("CONF ${(it * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = .72f)) }
                }
                theoreticalBest?.let {
                    Text("THEORETICAL  ${LapEngine.formatLapTime(it)}", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFB46CFF), modifier = Modifier.padding(top = 5.dp))
                }
            }

            if (liveDelta != null) {
                val color = if (liveDelta < 0) DeltaAheadGreen else DeltaBehindRed
                Column(
                    modifier = Modifier
                        .padding(top = 9.dp)
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.52f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (liveDelta < 0) "AHEAD" else "BEHIND", fontSize = 10.sp, fontWeight = FontWeight.Black, color = color)
                        Text(LapEngine.formatDelta(liveDelta), fontSize = 18.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, color = color)
                    }
                    PredictiveDeltaBar(liveDelta)
                }
            }

            if (sectorSplits.isNotEmpty()) {
                LazyRow(modifier = Modifier.padding(top = 8.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(sectorSplits.withIndex().toList()) { (index, split) ->
                        Text("S${index + 1}  ${LapEngine.formatLapTime(split)}", color = DeltaAheadGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.background(Color.Black.copy(alpha = .58f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 7.dp))
                    }
                }
            }

            invalidLapReason?.let { reason ->
                val message = when (reason) { LapInvalidReason.WRONG_DIRECTION -> "INVALID LAP · WRONG DIRECTION"; LapInvalidReason.SKIPPED_SECTOR -> "INVALID LAP · SECTOR MISSED"; LapInvalidReason.TOO_SHORT -> "INVALID LAP · TOO SHORT" }
                Text(message, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp).background(DeltaBehindRed, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp))
            }
            if (inPitLane) {
                Text("PIT LANE · TIMING PAUSED", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp).background(Color(0xFFF9B327), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp))
            }

            if (laps.isEmpty()) {
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .padding(start = 16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Cold tires — ease in this first lap",
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF9B327))
                    )
                }
            }

            if (laps.isNotEmpty()) {
                val best = bestLapTime
                LazyRow(
                    modifier = Modifier.padding(top = 12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(laps.withIndex().toList().asReversed()) { (index, lapTime) ->
                        val isBest = best != null && lapTime == best
                        val lapNumber = index + 1
                        Column(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "LAP $lapNumber",
                                style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.5f))
                            )
                            Text(
                                LapEngine.formatLapTime(lapTime),
                                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isBest) DeltaAheadGreen else Color.White)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
                    .fillMaxWidth(if (isLandscape) .48f else 1f)
                    .align(if (isLandscape) Alignment.End else Alignment.CenterHorizontally)
                    .background(Color.Black.copy(alpha = 0.64f), RoundedCornerShape(20.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    LiveStat(com.karthik.packride.data.MeasurementUnits.speedMph(speedMph), "SPEED", Modifier.weight(1f))
                    LiveStat(com.karthik.packride.data.MeasurementUnits.distanceMiles(distanceMiles), "DISTANCE", Modifier.weight(1f))
                    LiveStat(com.karthik.packride.data.MeasurementUnits.speedMph(maxSpeedMph), "TOP SPEED", Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PrCoral)
                        .clickable(onClick = onEndSessionTap)
                        .padding(vertical = 15.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("End Session", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White))
                }
            }
        }
    }
}

@Composable
private fun MapStylePill(current: MapType, onChange: (MapType) -> Unit) {
    val next = when (current) { MapType.HYBRID -> MapType.SATELLITE; MapType.SATELLITE -> MapType.NORMAL; else -> MapType.HYBRID }
    val label = when (current) { MapType.HYBRID -> "HYB"; MapType.SATELLITE -> "SAT"; else -> "MAP" }
    Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(Color.Black.copy(alpha = .48f), RoundedCornerShape(50)).clickable { onChange(next) }.padding(horizontal = 10.dp, vertical = 8.dp))
}

@Composable
private fun PredictiveDeltaBar(deltaSeconds: Double) {
    val normalized = (deltaSeconds / 3.0).coerceIn(-1.0, 1.0).toFloat()
    Canvas(Modifier.fillMaxWidth().height(14.dp).padding(top = 5.dp)) {
        val center = size.width / 2f
        drawLine(Color.White.copy(alpha = .24f), start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f), end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = .65f), start = androidx.compose.ui.geometry.Offset(center, 0f), end = androidx.compose.ui.geometry.Offset(center, size.height), strokeWidth = 1.dp.toPx())
        drawCircle(if (deltaSeconds < 0) DeltaAheadGreen else DeltaBehindRed, radius = 5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(center + normalized * center, size.height / 2f))
    }
}

@Composable
private fun OverlayMetric(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(label, style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, color = Color.White.copy(alpha = 0.5f)))
        Text(value, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accent))
    }
}

@Composable
private fun LiveStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White))
        Text(label, style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, color = Color.White.copy(alpha = 0.45f)))
    }
}

// MARK: - Summary (session complete)

@Composable
private fun SummaryContent(
    trackName: String,
    laps: List<Double>,
    bestLapTime: Double,
    distanceMiles: Double,
    maxSpeedMph: Double,
    gpxFileName: String?,
    analytics: LapAnalyticsSummary?,
    rideId: String?,
    onOpenCompare: () -> Unit,
    onOpenTrends: () -> Unit,
    onOpenReplay: () -> Unit,
    onNewSession: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { UserPrefs(context) }
    val feed = remember { RideFeedManager(context) }
    val friends = remember { FriendsManager(context) }
    val followed by friends.followedUsers.collectAsState()
    val sharing = remember { LapSharingManager(context) }
    val sessionHistory = remember { RideHistoryManager(context) }
    val storedRides by sessionHistory.rides.collectAsState()
    val storedSession = storedRides.firstOrNull { it.id == rideId }
    var showPostDialog by remember { mutableStateOf(false) }
    var showFriendPicker by remember { mutableStateOf(false) }
    var postTitle by remember(trackName) { mutableStateOf("Track day at $trackName") }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        friends.start()
        onDispose { friends.stop() }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pr.bg)
            .verticalScroll(rememberScrollState())
    ) {
        PrPageHeader(
            eyebrow = "TRACK SESSION",
            title = trackName,
            subtitle = "${laps.size} completed lap${if (laps.size == 1) "" else "s"}"
        )

        PrMetricStrip(
            metrics = listOf(
                "${laps.size}" to "Laps",
                (if (bestLapTime > 0) LapEngine.formatLapTime(bestLapTime) else "--:--") to "Best Lap",
                "%.1f".format(distanceMiles) to "Miles"
            )
        )

        if (analytics != null) {
            PrWebSectionLabel(title = "Performance", detail = "Track score")
            TrackScoreCard(analytics, modifier = Modifier.padding(horizontal = 20.dp))
        }

        if (laps.isNotEmpty()) {
            Text(
                "LAP TIMES",
                style = PrFont.sectionHeader,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 8.dp)
            )
            PrCard(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            ) {
                Column(Modifier.padding(4.dp)) {
                    laps.forEachIndexed { index, lapTime ->
                        val isBest = lapTime == bestLapTime
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Lap ${index + 1}", style = PrFont.bodySmall)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (isBest) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = "Best lap", tint = Pr.coral, modifier = Modifier.size(14.dp))
                                }
                                Text(
                                    LapEngine.formatLapTime(lapTime),
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBest) Pr.coral else Pr.ink
                                    )
                                )
                            }
                        }
                        if (index < laps.size - 1) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Pr.border))
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (gpxFileName != null && rideId != null) {
                TrackSummaryAction("View Racing Line", "See this session on the track", onClick = onOpenReplay)
            }
            PrimaryButton(text = "Compare Laps", onClick = onOpenCompare)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Pr.RadiusButton))
                    .border(1.dp, Pr.border, RoundedCornerShape(Pr.RadiusButton))
                    .clickable(onClick = onOpenTrends)
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("View Trends", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Pr.ink))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNewSession)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Start Another Session", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Pr.muted))
            }
            TrackSummaryAction("Post to Feed", "Share your lap result with the Pack", enabled = laps.isNotEmpty()) {
                showPostDialog = true
            }
            TrackSummaryAction("Share With a Friend", "Send the full session for lap comparison", enabled = storedSession?.gpxFileName != null) {
                showFriendPicker = true
            }
            TrackSummaryAction("Share My Session", "Create a rendered PackRide share card") {
                RideShareCard.share(
                    context, trackName, prefs.riderName.ifBlank { "PackRide Rider" },
                    com.karthik.packride.data.MeasurementUnits.distanceMiles(distanceMiles),
                    if (bestLapTime > 0) "Best ${LapEngine.formatLapTime(bestLapTime)}" else "${laps.size} laps",
                    "Top speed ${com.karthik.packride.data.MeasurementUnits.speedMph(maxSpeedMph)}"
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    if (showPostDialog) {
        AlertDialog(
            onDismissRequest = { showPostDialog = false },
            title = { Text("Post Track Session") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = postTitle,
                    onValueChange = { postTitle = it },
                    label = { Text("Post title") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(enabled = postTitle.isNotBlank(), onClick = {
                    showPostDialog = false
                    feed.postLapSession(
                        title = postTitle,
                        trackName = trackName,
                        laps = laps,
                        bestLapTime = bestLapTime,
                        distanceMiles = distanceMiles,
                        authorName = prefs.riderName.ifBlank { "Rider" },
                        authorInitials = prefs.riderName.rideInitials()
                    ) { error -> actionMessage = error ?: "Track session posted to Feed." }
                }) { Text("Post", color = Pr.coral) }
            },
            dismissButton = { TextButton(onClick = { showPostDialog = false }) { Text("Cancel") } }
        )
    }
    if (showFriendPicker) {
        AlertDialog(
            onDismissRequest = { showFriendPicker = false },
            title = { Text("Share With a Friend") },
            text = {
                Column {
                    if (followed.isEmpty()) Text("Follow a rider before sharing a session.", style = PrFont.bodySmall)
                    followed.forEach { rider ->
                        TextButton(onClick = {
                            val session = storedSession ?: return@TextButton
                            showFriendPicker = false
                            sharing.shareSession(session, prefs.riderName.ifBlank { "Rider" }, rider.id) { error ->
                                actionMessage = error ?: "Shared $trackName with ${rider.name}."
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(rider.name, modifier = Modifier.fillMaxWidth(), color = Pr.ink)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showFriendPicker = false }) { Text("Cancel") } }
        )
    }
    actionMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { actionMessage = null },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { actionMessage = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun TrackSummaryAction(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pr.cardBg)
            .clickable(enabled = enabled, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = PrFont.body, color = if (enabled) Pr.ink else Pr.muted)
            Text(subtitle, style = PrFont.caption)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Pr.muted, modifier = Modifier.size(16.dp))
    }
}

// MARK: - Track Score Card (shown right after a session ends)
// Kotlin port of iOS's TrackScoreCard (LapModeView.swift): a circular ring
// showing the composite 0-100 Track Score, colored by the same thresholds
// iOS uses (>=90 green / >=75 teal / >=55 coral / else red — DeltaAheadGreen
// and DeltaBehindRed above are iOS's literal RGB for the two non-Pr-token
// tiers), plus a 4-stat breakdown (consistency, smoothness, max lean, hard
// brakes). iOS's per-corner breakdown and full telemetry timeline are not
// ported — see this file's header comment.
@Composable
private fun TrackScoreCard(analytics: LapAnalyticsSummary, modifier: Modifier = Modifier) {
    val scoreColor = when (analytics.trackScore) {
        in 90..100 -> DeltaAheadGreen
        in 75..89 -> PrTeal
        in 55..74 -> PrCoral
        else -> DeltaBehindRed
    }
    val ringTrackColor = Pr.border
    val inkColor = Pr.ink
    val mutedColor = Pr.muted

    PrCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(60.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                        drawArc(color = ringTrackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
                        drawArc(
                            color = scoreColor,
                            startAngle = -90f,
                            sweepAngle = 360f * (analytics.trackScore.coerceIn(0, 100) / 100f),
                            useCenter = false,
                            style = stroke
                        )
                    }
                    Text(
                        "${analytics.trackScore}",
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = inkColor, fontFamily = FontFamily.Monospace)
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Track Score: ${analytics.scoreGrade}",
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = inkColor)
                    )
                    Text(
                        "Consistency + smoothness, estimated from GPS + motion sensors",
                        style = TextStyle(fontSize = 10.sp, color = mutedColor)
                    )
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(ringTrackColor))

            PrMetricStrip(
                metrics = listOf(
                    "${analytics.consistencyScore}" to "Consistency",
                    "${analytics.smoothnessScore}" to "Smoothness",
                    "%.0f°".format(analytics.maxLeanAngle) to "Max Lean",
                    "${analytics.hardBrakeCount}" to "Hard Brakes"
                )
            )
        }
    }
}

// MARK: - Remembered track locations
// 1:1 port of iOS's TrackLocationStore (LapModeView.swift) — keys the
// start/finish pin to a lowercased, trimmed track name so typing the same
// name again auto-fills the line used last time. No network dependency, no
// geocoding — pure local storage, same as iOS's UserDefaults-backed version.
private object TrackLocationStore {
    private const val PREFS_NAME = "packride_track_locations"
    private const val KEY = "pr_savedTrackLocations"

    private fun normalize(name: String): String = name.trim().lowercase()

    private fun loadAll(context: Context): JSONObject {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null) ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    fun save(context: Context, name: String, latitude: Double, longitude: Double) {
        val normalized = normalize(name)
        if (normalized.isEmpty()) return
        val all = loadAll(context)
        val entry = JSONObject().put("latitude", latitude).put("longitude", longitude)
        all.put(normalized, entry)
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY, all.toString()).apply()
    }

    fun lookup(context: Context, name: String): Pair<Double, Double>? {
        val normalized = normalize(name)
        if (normalized.isEmpty()) return null
        val entry = loadAll(context).optJSONObject(normalized) ?: return null
        if (!entry.has("latitude") || !entry.has("longitude")) return null
        return entry.optDouble("latitude") to entry.optDouble("longitude")
    }
}
