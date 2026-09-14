package com.karthik.packride.ride

import android.content.Context
import android.location.Location
import com.karthik.packride.gpx.GPXRecorder
import com.karthik.packride.gpx.GpxCloudUpload
import com.karthik.packride.gpx.GPXStorage
import java.io.File
import com.karthik.packride.community.CommunityMembershipStore
import com.karthik.packride.location.SharedLocationManager
import com.karthik.packride.motion.RideMotionMonitor
import com.karthik.packride.waypoints.WaypointsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Solo ride session controller — start/stop tracking, GPX, motion, elapsed time,
 * and progress snapshot for recovery after process death (iOS ActiveSoloRideView).
 * Also owns the "share this ride with my communities" fan-out (isRiding flip
 * at start/end + throttled location publish while riding) — port of iOS
 * ActiveSoloRideView's setCommunityRidingStatus()/updateCommunityLocation().
 */
class SoloRideSession(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager = SharedLocationManager.get()
    private val gpx = GPXRecorder(appContext.filesDir)
    private val motion = RideMotionMonitor(appContext)
    private val history = RideHistoryManager(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _summary = MutableStateFlow<RideRecord?>(null)
    val summary: StateFlow<RideRecord?> = _summary.asStateFlow()

    val distanceMiles get() = locationManager.distanceMiles
    val maxSpeedMph get() = locationManager.maxSpeedMph
    val speedMph get() = locationManager.speedMph
    val location get() = locationManager.location
    val trackPath get() = locationManager.trackPath

    private var rideStartMs: Long? = null
    private var timerJob: Job? = null
    private var locationJob: Job? = null
    private var lastPublishLoc: Location? = null
    private var lastPublishMs: Long = 0
    private var sharedCommunityIds: Set<String> = emptySet()

    fun start(riderName: String, sharedCommunityIds: Set<String> = emptySet()) {
        if (_isActive.value) return
        _summary.value = null
        _isActive.value = true
        this.sharedCommunityIds = sharedCommunityIds
        lastPublishLoc = null
        lastPublishMs = 0
        CommunityMembershipStore.get().setRidingStatus(true, sharedCommunityIds)

        locationManager.resetTracking()

        val savedStart = prefs.getLong(KEY_START, 0L).takeIf { it > 0 }
        val wasActive = prefs.getBoolean(KEY_ACTIVE, false)
        if (wasActive && savedStart != null &&
            System.currentTimeMillis() - savedStart < 6 * 3600_000L
        ) {
            // Resume distance/max from snapshot — SharedLocationManager starts at 0;
            // we keep elapsed from saved start.
            rideStartMs = savedStart
            // Note: distance/max will rebuild from new GPS; full resume of distance
            // would need injecting into SharedLocationManager — elapsed is restored.
            _elapsedSeconds.value =
                ((System.currentTimeMillis() - savedStart) / 1000L).toInt().coerceAtLeast(0)
        } else {
            rideStartMs = System.currentTimeMillis()
            prefs.edit().putLong(KEY_START, rideStartMs!!).apply()
            _elapsedSeconds.value = 0
        }
        prefs.edit().putBoolean(KEY_ACTIVE, true).apply()

        locationManager.startTracking(SharedLocationManager.REASON_RIDE_TRACKING)
        gpx.startRecording("${riderName}_Solo")
        motion.start()

        timerJob = scope.launch {
            while (isActive) {
                delay(1_000)
                val start = rideStartMs ?: continue
                _elapsedSeconds.value = ((System.currentTimeMillis() - start) / 1000L).toInt()
            }
        }

        locationJob = scope.launch {
            locationManager.location.collect { loc ->
                if (!_isActive.value || loc == null) return@collect
                gpx.currentGForce = motion.currentGForce
                gpx.currentLeanAngle = motion.currentLeanDegrees
                gpx.capturePoint(loc)
                snapshotProgress()
                publishCommunityLocationIfNeeded(loc)
            }
        }
    }

    fun end(): RideRecord? {
        if (!_isActive.value) return null
        CommunityMembershipStore.get().setRidingStatus(false, sharedCommunityIds)
        val start = rideStartMs ?: System.currentTimeMillis()
        val elapsed = ((System.currentTimeMillis() - start) / 1000L).toInt()
        val distance = locationManager.distanceMiles.value
        val maxSpd = locationManager.maxSpeedMph.value
        val lean = motion.maxLeanDegrees
        val gpxName = gpx.stopAndSave()

        stopServices()
        clearProgress()
        // Sep 3, 2026 — Karthik-reported bug: the planned route on the Plan
        // Route screen was sticking around after a solo ride finished,
        // because nothing ever called WaypointsManager.clearWaypoints() at
        // ride end. A fresh instance here just reads/writes the same
        // SharedPreferences the screen's own WaypointsManager(context) does
        // (no shared listener state to worry about), so this is safe even
        // though WaypointsScreen isn't necessarily on screen right now.
        WaypointsManager(appContext).clearWaypoints()

        val record = RideRecord(
            distanceMiles = distance,
            maxSpeedMph = maxSpd,
            durationSeconds = elapsed,
            rideCode = "SOLO",
            gpxFileName = gpxName,
            maxLeanAngle = lean,
            isGroupRide = false,
            bikeId = com.karthik.packride.garage.GarageManager.currentActiveBikeID(appContext)
        )
        history.record(record)
        // Background cloud sync (iOS syncRideToCloud parity)
        if (!gpxName.isNullOrBlank()) {
            val file = File(GPXStorage.ridesDirectory(appContext.filesDir), gpxName)
            GpxCloudUpload.upload(file, record.id) { result ->
                result.onSuccess { url ->
                    history.setGpxUrl(record.id, url)
                    _summary.value = record.copy(gpxURL = url)
                }
            }
        }
        _summary.value = record
        return record
    }

    fun dismissSummary() {
        _summary.value = null
    }

    fun cancelIfNeeded() {
        if (_isActive.value) {
            CommunityMembershipStore.get().setRidingStatus(false, sharedCommunityIds)
            gpx.cancelRecording()
            stopServices()
            clearProgress()
        }
    }

    private fun stopServices() {
        timerJob?.cancel(); timerJob = null
        locationJob?.cancel(); locationJob = null
        motion.stop()
        locationManager.stopTracking(SharedLocationManager.REASON_RIDE_TRACKING)
        _isActive.value = false
        rideStartMs = null
    }

    private fun publishCommunityLocationIfNeeded(loc: Location) {
        if (sharedCommunityIds.isEmpty()) return
        val now = System.currentTimeMillis()
        val last = lastPublishLoc
        val timeOk = last == null || now - lastPublishMs >= 5_000
        val distOk = last != null && loc.distanceTo(last) >= 25f
        if (last != null && !timeOk && !distOk) return
        lastPublishLoc = loc
        lastPublishMs = now
        CommunityMembershipStore.get().updateRidingLocation(loc, locationManager.speedMph.value, sharedCommunityIds)
    }

    private fun snapshotProgress() {
        prefs.edit()
            .putBoolean(KEY_ACTIVE, true)
            .putFloat(KEY_DISTANCE, locationManager.distanceMiles.value.toFloat())
            .putFloat(KEY_MAX_SPEED, locationManager.maxSpeedMph.value.toFloat())
            .apply()
    }

    private fun clearProgress() {
        prefs.edit()
            .remove(KEY_ACTIVE)
            .remove(KEY_DISTANCE)
            .remove(KEY_MAX_SPEED)
            .remove(KEY_START)
            .apply()
    }

    fun historyManager(): RideHistoryManager = history

    companion object {
        private const val PREFS = "packride_solo_session"
        private const val KEY_ACTIVE = "pr_soloRideActive"
        private const val KEY_DISTANCE = "pr_soloRideDistance"
        private const val KEY_MAX_SPEED = "pr_soloRideMaxSpeed"
        private const val KEY_START = "pr_soloRideStart"
    }
}
