package com.karthik.packride.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One GPS instance for the entire app — Android port of iOS SharedLocationManager.
 *
 * - Reason-counted start/stop so one screen cannot kill GPS under another
 * - Accuracy tiers: lapTracking (3m) > navigation-grade (5m) > coarse browse (50m)
 * - Background reasons start [LocationTrackingService] (foreground service)
 * - Distance / max-speed ignore bad accuracy and glitch jumps
 */
class SharedLocationManager private constructor(private val appContext: Context) {

    private val fused = LocationServices.getFusedLocationProviderClient(appContext)

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location.asStateFlow()

    private val _speedMph = MutableStateFlow(0.0)
    val speedMph: StateFlow<Double> = _speedMph.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _distanceMiles = MutableStateFlow(0.0)
    val distanceMiles: StateFlow<Double> = _distanceMiles.asStateFlow()

    private val _maxSpeedMph = MutableStateFlow(0.0)
    val maxSpeedMph: StateFlow<Double> = _maxSpeedMph.asStateFlow()

    private val _trackPath = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    /** Decimated lat/lng pairs for live map polyline (approx every 15 m). */
    val trackPath: StateFlow<List<Pair<Double, Double>>> = _trackPath.asStateFlow()

    private val activeReasons = mutableSetOf<String>()
    private val backgroundUpdateReasons = mutableSetOf<String>()

    private var lastTrackingLocation: Location? = null
    private var lastLocationForSpeedFallback: Location? = null
    private var receivingUpdates = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            onLocation(loc)
        }
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED
    }

    /** Call after runtime permission is granted from the UI. */
    fun onPermissionGranted() {
        if (activeReasons.isNotEmpty()) {
            applyAccuracyTiersAndStart()
        }
    }

    fun startUpdating(reason: String) {
        activeReasons.add(reason)
        applyAccuracyTiersAndStart()
    }

    fun stopUpdating(reason: String) {
        activeReasons.remove(reason)
        applyAccuracyTiersAndStart()
        if (activeReasons.isEmpty()) {
            stopUpdatesInternal()
        }
    }

    fun requestBackgroundUpdates(reason: String) {
        activeReasons.add(reason)
        backgroundUpdateReasons.add(reason)
        applyBackgroundService()
        applyAccuracyTiersAndStart()
    }

    fun releaseBackgroundUpdates(reason: String) {
        activeReasons.remove(reason)
        backgroundUpdateReasons.remove(reason)
        applyBackgroundService()
        applyAccuracyTiersAndStart()
        if (activeReasons.isEmpty()) {
            stopUpdatesInternal()
        }
    }

    fun startTracking(reason: String = REASON_RIDE_TRACKING) {
        _distanceMiles.value = 0.0
        _maxSpeedMph.value = 0.0
        lastTrackingLocation = null
        _isTracking.value = true
        requestBackgroundUpdates(reason)
    }

    fun stopTracking(reason: String = REASON_RIDE_TRACKING) {
        _isTracking.value = false
        lastTrackingLocation = null
        lastLocationForSpeedFallback = null
        _speedMph.value = 0.0
        releaseBackgroundUpdates(reason)
    }

    fun resetTracking() {
        _trackPath.value = emptyList()
        _distanceMiles.value = 0.0
        _maxSpeedMph.value = 0.0
        lastTrackingLocation = null
        lastLocationForSpeedFallback = null
    }

    private fun applyBackgroundService() {
        val intent = Intent(appContext, LocationTrackingService::class.java)
        if (backgroundUpdateReasons.isNotEmpty()) {
            ContextCompat.startForegroundService(appContext, intent)
        } else {
            appContext.stopService(intent)
        }
    }

    private fun applyAccuracyTiersAndStart() {
        if (activeReasons.isEmpty()) return
        if (!hasLocationPermission()) return

        val (priority, minDistanceM, intervalMs) = when {
            activeReasons.contains(REASON_LAP_TRACKING) ->
                Triple(Priority.PRIORITY_HIGH_ACCURACY, 3f, 1_000L)
            activeReasons.any { it in NAVIGATION_GRADE_REASONS } ->
                Triple(Priority.PRIORITY_HIGH_ACCURACY, 5f, 1_000L)
            else ->
                Triple(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 50f, 5_000L)
        }

        val request = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateDistanceMeters(minDistanceM)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setWaitForAccurateLocation(false)
            .build()

        restartUpdates(request)
    }

    @SuppressLint("MissingPermission")
    private fun restartUpdates(request: LocationRequest) {
        if (!hasLocationPermission()) return
        fused.removeLocationUpdates(locationCallback)
        fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        receivingUpdates = true
    }

    private fun stopUpdatesInternal() {
        if (!receivingUpdates) return
        fused.removeLocationUpdates(locationCallback)
        receivingUpdates = false
    }

    private fun onLocation(loc: Location) {
        _location.value = loc

        val speedMph = when {
            loc.hasSpeed() && loc.speed >= 0f -> loc.speed * MPS_TO_MPH
            else -> {
                val last = lastLocationForSpeedFallback
                if (last != null) {
                    val dt = (loc.time - last.time) / 1000.0
                    if (dt > 0) (loc.distanceTo(last) / dt) * MPS_TO_MPH else _speedMph.value
                } else 0.0
            }
        }
        _speedMph.value = maxOf(speedMph, 0.0)
        lastLocationForSpeedFallback = loc

        if (_isTracking.value) {
            val accuracyOk = loc.hasAccuracy() && loc.accuracy in 0f..MAX_ACCEPTABLE_ACCURACY_M
            if (accuracyOk) {
                if (_speedMph.value > _maxSpeedMph.value) {
                    _maxSpeedMph.value = _speedMph.value
                }
                val last = lastTrackingLocation
                if (last != null) {
                    val delta = loc.distanceTo(last)
                    if (delta <= MAX_ACCEPTABLE_STEP_M) {
                        _distanceMiles.value += delta * METERS_TO_MILES
                        lastTrackingLocation = loc
                        appendTrackPoint(loc, minSeparationM = 15f)
                    }
                    // else keep last good point so next step isn't measured from the glitch
                } else {
                    lastTrackingLocation = loc
                    appendTrackPoint(loc, minSeparationM = 0f)
                }
            }
        }
    }

    private fun appendTrackPoint(loc: Location, minSeparationM: Float) {
        val path = _trackPath.value
        if (path.isNotEmpty() && minSeparationM > 0f) {
            val (plat, plng) = path.last()
            val results = FloatArray(1)
            Location.distanceBetween(plat, plng, loc.latitude, loc.longitude, results)
            if (results[0] < minSeparationM) return
        }
        _trackPath.value = path + (loc.latitude to loc.longitude)
    }

    companion object {
        const val REASON_RIDE_TRACKING = "rideTracking"
        const val REASON_LAP_TRACKING = "lapTracking"
        const val REASON_NEED_HELP = "needHelp"
        const val REASON_CRASH_DETECTION = "crashDetection"
        const val REASON_MAP = "mapView"
        const val REASON_WEATHER = "weather"

        private val NAVIGATION_GRADE_REASONS = setOf(
            REASON_RIDE_TRACKING,
            REASON_NEED_HELP,
            REASON_CRASH_DETECTION,
            REASON_LAP_TRACKING
        )

        private const val MPS_TO_MPH = 2.23694
        private const val METERS_TO_MILES = 0.000621371
        private const val MAX_ACCEPTABLE_ACCURACY_M = 50f
        private const val MAX_ACCEPTABLE_STEP_M = 150f

        @Volatile
        private var instance: SharedLocationManager? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = SharedLocationManager(context.applicationContext)
                    }
                }
            }
        }

        fun get(): SharedLocationManager =
            instance ?: error("Call SharedLocationManager.init(Application) first")
    }
}
