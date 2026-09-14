package com.karthik.packride.ride

import android.content.Context
import com.karthik.packride.gpx.GPXRecorder
import com.karthik.packride.gpx.GpxCloudUpload
import com.karthik.packride.gpx.GPXStorage
import com.karthik.packride.location.SharedLocationManager
import com.karthik.packride.motion.RideMotionMonitor
import com.karthik.packride.waypoints.WaypointsManager
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Records a real ride while in-app turn-by-turn guidance is active — same
 * GPX/motion/history pipeline as SoloRideSession, but scoped to Navigate
 * instead of Solo Ride: no persisted "resume after process death" snapshot,
 * matching iOS's TurnByTurnView exactly (its recording state is plain
 * @State, not UserDefaults-backed like ActiveSoloRideView's is). GPX name
 * suffix "_Navigate" and rideCode "NAV" mirror iOS's
 * startRecordingRide()/RideHistoryManager.recordRide(..., isGroupRide: false).
 */
class NavRideSession(context: Context) {
    private val appContext = context.applicationContext
    private val locationManager = SharedLocationManager.get()
    private val gpx = GPXRecorder(appContext.filesDir)
    private val motion = RideMotionMonitor(appContext)
    private val history = RideHistoryManager(appContext)
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private var isActive = false
    private var startMs: Long = 0L
    private var locationJob: Job? = null

    val currentGForce: Double get() = motion.currentGForce
    val currentLeanDegrees: Double get() = motion.currentLeanDegrees

    fun start(riderName: String) {
        if (isActive) return
        isActive = true
        startMs = System.currentTimeMillis()
        gpx.startRecording("${riderName}_Navigate")
        motion.start()

        locationJob = scope.launch {
            locationManager.location.collect { loc ->
                if (!isActive || loc == null) return@collect
                gpx.currentGForce = motion.currentGForce
                gpx.currentLeanAngle = motion.currentLeanDegrees
                gpx.capturePoint(loc)
            }
        }
    }

    /** Saves the ride and returns the record, or null if nothing was recording. */
    fun end(): RideRecord? {
        if (!isActive) return null
        val elapsed = ((System.currentTimeMillis() - startMs) / 1000L).toInt()
        val distance = locationManager.distanceMiles.value
        val maxSpd = locationManager.maxSpeedMph.value
        val lean = motion.maxLeanDegrees
        val gpxName = gpx.stopAndSave()

        stopServices()
        // Sep 3, 2026 — same fix as SoloRideSession.end(): clear the planned
        // route once this navigated ride finishes, so Plan Route doesn't
        // still show the just-completed route the next time it's opened.
        WaypointsManager(appContext).clearWaypoints()

        val record = RideRecord(
            distanceMiles = distance,
            maxSpeedMph = maxSpd,
            durationSeconds = elapsed,
            rideCode = "NAV",
            gpxFileName = gpxName,
            maxLeanAngle = lean,
            isGroupRide = false,
            bikeId = com.karthik.packride.garage.GarageManager.currentActiveBikeID(appContext)
        )
        history.record(record)
        if (!gpxName.isNullOrBlank()) {
            val file = File(GPXStorage.ridesDirectory(appContext.filesDir), gpxName)
            GpxCloudUpload.upload(file, record.id) { result ->
                result.onSuccess { url -> history.setGpxUrl(record.id, url) }
            }
        }
        return record
    }

    /** Discards an in-progress recording without saving — screen dismissed mid-navigation. */
    fun cancelIfNeeded() {
        if (isActive) {
            gpx.cancelRecording()
            stopServices()
        }
    }

    private fun stopServices() {
        locationJob?.cancel(); locationJob = null
        motion.stop()
        isActive = false
    }
}
