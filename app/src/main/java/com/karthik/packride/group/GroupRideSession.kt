package com.karthik.packride.group

import android.content.Context
import com.karthik.packride.auth.rideInitials
import com.karthik.packride.gpx.GPXRecorder
import com.karthik.packride.gpx.GpxCloudUpload
import com.karthik.packride.gpx.GPXStorage
import java.io.File
import com.karthik.packride.location.SharedLocationManager
import com.karthik.packride.motion.RideMotionMonitor
import com.karthik.packride.ride.RideHistoryManager
import com.karthik.packride.ride.RideRecord
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
 * Group ride session — join/create room, broadcast GPS, listen for pack, GPX, history.
 * Port of iOS GroupRideSessionManager.
 */
class GroupRideSession private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val firebase = GroupRideFirebase.create(appContext)
    private val locationManager = SharedLocationManager.get()
    private val gpx = GPXRecorder(appContext.filesDir)
    private val motion = RideMotionMonitor(appContext)
    private val history = RideHistoryManager(appContext)
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _inRoom = MutableStateFlow(false)
    val inRoom: StateFlow<Boolean> = _inRoom.asStateFlow()

    private val _rideCode = MutableStateFlow("")
    val rideCode: StateFlow<String> = _rideCode.asStateFlow()

    private val _isLeader = MutableStateFlow(false)
    val isLeader: StateFlow<Boolean> = _isLeader.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _summary = MutableStateFlow<RideRecord?>(null)
    val summary: StateFlow<RideRecord?> = _summary.asStateFlow()

    val groupRiders: StateFlow<List<LiveRider>> = firebase.groupRiders
    val location get() = locationManager.location
    val speedMph get() = locationManager.speedMph
    val distanceMiles get() = locationManager.distanceMiles
    val maxSpeedMph get() = locationManager.maxSpeedMph

    private var startMs: Long? = null
    private var timerJob: Job? = null
    private var locationJob: Job? = null
    private var riderName: String = "Rider"
    private var initials: String = "?"

    // Aug 30, 2026 — presetCode lets a caller supply a code that was already
    // generated and published elsewhere (see WaypointsScreen's "Group Ride"
    // choice, which publishes the just-planned route to
    // rides/{code}/waypointsJSON via WaypointsManager/GroupWaypointSync
    // BEFORE this session ever starts) instead of always minting a brand-new
    // one here — otherwise the route the rider just planned would live under
    // a different code than the group ride room actually joins.
    fun createRoom(riderName: String, avatarURL: String = "", presetCode: String? = null) {
        val code = presetCode?.uppercase()?.takeIf { it.isNotBlank() } ?: GroupRideFirebase.generateRideCode()
        enterRoom(code, riderName, isLeader = true, avatarURL = avatarURL)
    }

    fun joinRoom(code: String, riderName: String, avatarURL: String = "") {
        val cleaned = code.uppercase().filter { it.isLetterOrDigit() }
        if (cleaned.length < 4) return
        enterRoom(cleaned, riderName, isLeader = false, avatarURL = avatarURL)
    }

    private fun enterRoom(code: String, riderName: String, isLeader: Boolean, avatarURL: String) {
        if (_inRoom.value) return
        this.riderName = riderName
        this.initials = riderName.rideInitials()
        _rideCode.value = code
        _isLeader.value = isLeader
        _inRoom.value = true
        _summary.value = null
        firebase.joinRide(code, riderName, initials, isLeader, avatarURL)
    }

    /** Starts GPS/GPX recording only after the rider explicitly starts from the room. */
    fun startRide() {
        if (!_inRoom.value || _isActive.value || _rideCode.value.isBlank()) return
        _isActive.value = true
        _summary.value = null
        _elapsedSeconds.value = 0
        startMs = System.currentTimeMillis()

        // Aug 30, 2026 — Need Help targeting fix: publish this ride code app-wide
        // so Need Help's "Share with Group Ride" option (see NeedHelpScreen) and the
        // relevantTo() filter (see HelpRequest.kt) can see it from outside this screen.
        val code = _rideCode.value
        ActiveRideTracker.get().set(code)
        locationManager.resetTracking()
        locationManager.startTracking(SharedLocationManager.REASON_RIDE_TRACKING)
        gpx.startRecording("${riderName}_Group_$code")
        motion.start()

        timerJob = scope.launch {
            while (isActive) {
                delay(1_000)
                val s = startMs ?: continue
                _elapsedSeconds.value = ((System.currentTimeMillis() - s) / 1000L).toInt()
            }
        }

        locationJob = scope.launch {
            locationManager.location.collect { loc ->
                if (!_isActive.value || loc == null) return@collect
                gpx.currentGForce = motion.currentGForce
                gpx.currentLeanAngle = motion.currentLeanDegrees
                gpx.capturePoint(loc)
                firebase.updateLocation(
                    _rideCode.value,
                    loc,
                    locationManager.speedMph.value
                )
            }
        }
    }

    fun end(): RideRecord? {
        if (!_isActive.value) return null
        val code = _rideCode.value
        val elapsed = _elapsedSeconds.value
        val distance = locationManager.distanceMiles.value
        val maxSpd = locationManager.maxSpeedMph.value
        val durationStr = formatDuration(elapsed)
        val gpxName = gpx.stopAndSave()

        firebase.publishFinalStats(
            rideCode = code,
            riderName = riderName,
            initials = initials,
            isLeader = _isLeader.value,
            distanceMiles = distance,
            maxSpeedMph = maxSpd,
            duration = durationStr
        )
        firebase.leaveRide(code)
        stopLocal()
        _inRoom.value = false

        val record = RideRecord(
            distanceMiles = distance,
            maxSpeedMph = maxSpd,
            durationSeconds = elapsed,
            // iOS stores the raw room code; isGroupRide already carries the
            // type. Prefixing it broke finalStats lookup on both platforms.
            rideCode = code,
            gpxFileName = gpxName,
            maxLeanAngle = motion.maxLeanDegrees,
            isGroupRide = true,
            bikeId = com.karthik.packride.garage.GarageManager.currentActiveBikeID(appContext),
            isLeader = _isLeader.value
        )
        history.record(record)
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
            gpx.cancelRecording()
            stopLocal()
        }
        leaveRoom()
    }

    fun leaveRoom() {
        if (_inRoom.value) firebase.leaveRide(_rideCode.value)
        _inRoom.value = false
        _rideCode.value = ""
        _isLeader.value = false
    }

    private fun stopLocal() {
        ActiveRideTracker.get().clear()
        timerJob?.cancel(); timerJob = null
        locationJob?.cancel(); locationJob = null
        motion.stop()
        locationManager.stopTracking(SharedLocationManager.REASON_RIDE_TRACKING)
        _isActive.value = false
        startMs = null
    }

    companion object {
        @Volatile private var instance: GroupRideSession? = null

        fun get(context: Context): GroupRideSession = instance ?: synchronized(this) {
            instance ?: GroupRideSession(context.applicationContext).also { instance = it }
        }

        fun formatDuration(totalSeconds: Int): String {
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            return "%02d:%02d:%02d".format(h, m, s)
        }
    }
}
