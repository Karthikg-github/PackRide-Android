package com.karthik.packride.lap

import android.location.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * GPS-driven lap crossing engine — Kotlin port of iOS LapEngine.
 *
 * - Dual radii (crossing 20m / clear 45m) to avoid double-counting
 * - horizontalAccuracy + stale-fix gates
 * - Interpolated crossing time between previous (outside) and current (inside)
 * - First lap clock anchored to first good GPS fix (not wall-clock Start)
 * - Live delta vs best-lap distance/time trace
 */
class LapEngine(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) {
    private val _laps = MutableStateFlow<List<Double>>(emptyList())
    val laps: StateFlow<List<Double>> = _laps.asStateFlow()
    private val _lapStartTimestamps = MutableStateFlow<List<Long>>(emptyList())
    val lapStartTimestamps: StateFlow<List<Long>> = _lapStartTimestamps.asStateFlow()

    private val _bestLapTime = MutableStateFlow<Double?>(null)
    val bestLapTime: StateFlow<Double?> = _bestLapTime.asStateFlow()

    private val _bestLapCoordinates = MutableStateFlow<List<GeoPoint>>(emptyList())
    val bestLapCoordinates: StateFlow<List<GeoPoint>> = _bestLapCoordinates.asStateFlow()

    private val _lastLapTime = MutableStateFlow<Double?>(null)
    val lastLapTime: StateFlow<Double?> = _lastLapTime.asStateFlow()

    private val _currentLapElapsed = MutableStateFlow(0.0)
    val currentLapElapsed: StateFlow<Double> = _currentLapElapsed.asStateFlow()

    private val _liveDeltaSeconds = MutableStateFlow<Double?>(null)
    val liveDeltaSeconds: StateFlow<Double?> = _liveDeltaSeconds.asStateFlow()

    private val _gpsAccuracyMeters = MutableStateFlow<Double?>(null)
    val gpsAccuracyMeters: StateFlow<Double?> = _gpsAccuracyMeters.asStateFlow()

    private val _timingConfidence = MutableStateFlow<Double?>(null)
    val timingConfidence: StateFlow<Double?> = _timingConfidence.asStateFlow()

    private val _sectorSplits = MutableStateFlow<List<Double>>(emptyList())
    val sectorSplits: StateFlow<List<Double>> = _sectorSplits.asStateFlow()

    private val _invalidLapReason = MutableStateFlow<LapInvalidReason?>(null)
    val invalidLapReason: StateFlow<LapInvalidReason?> = _invalidLapReason.asStateFlow()

    private val _bestSectorTimes = MutableStateFlow<List<Double>>(emptyList())
    val bestSectorTimes: StateFlow<List<Double>> = _bestSectorTimes.asStateFlow()
    private val _inPitLane = MutableStateFlow(false)
    val inPitLane: StateFlow<Boolean> = _inPitLane.asStateFlow()

    private var startFinish: Location? = null
    private var hasClearedZone = false
    private var lapStartTimeMs: Long? = null
    private var awaitingFirstFix = false
    private var timerJob: Job? = null

    private var lastCrossingCandidate: Pair<Location, Float>? = null

    private data class LapSample(val distance: Double, val elapsedTime: Double)

    private var currentLapSamples: MutableList<LapSample> = mutableListOf()
    private var currentLapCoordinates: MutableList<GeoPoint> = mutableListOf()
    private var currentLapDistance = 0.0
    private var lastTracePoint: Location? = null
    private var bestLapSamples: List<LapSample>? = null
    private var raceTimingEngine: RaceTimingEngine? = null
    private var pendingGateCenter: GeoPoint? = null
    private var pendingGateDirection = GateDirection.POSITIVE_TO_NEGATIVE

    fun begin(configuration: TrackTimingConfiguration) {
        pendingGateCenter = null
        raceTimingEngine = RaceTimingEngine(configuration)
        resetSessionState()
    }

    fun begin(latitude: Double, longitude: Double, direction: GateDirection = GateDirection.POSITIVE_TO_NEGATIVE) {
        raceTimingEngine = null
        pendingGateCenter = GeoPoint(latitude, longitude)
        pendingGateDirection = direction
        startFinish = Location("startFinish").apply {
            this.latitude = latitude
            this.longitude = longitude
        }
        resetSessionState()
    }

    private fun resetSessionState() {
        hasClearedZone = false
        awaitingFirstFix = raceTimingEngine == null
        lapStartTimeMs = null
        _laps.value = emptyList()
        _lapStartTimestamps.value = emptyList()
        _bestLapTime.value = null
        _bestLapCoordinates.value = emptyList()
        _lastLapTime.value = null
        _currentLapElapsed.value = 0.0
        resetTrace()
        bestLapSamples = null
        _liveDeltaSeconds.value = null
        _gpsAccuracyMeters.value = null
        _timingConfidence.value = null
        _sectorSplits.value = emptyList()
        _invalidLapReason.value = null
        _bestSectorTimes.value = emptyList()
        _inPitLane.value = false
        lastCrossingCandidate = null
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1_000)
                val start = lapStartTimeMs ?: continue
                _currentLapElapsed.value = (System.currentTimeMillis() - start) / 1000.0
            }
        }
    }

    fun stop() {
        timerJob?.cancel()
        timerJob = null
    }

    fun processLocation(location: Location) {
        // A manually dropped timing point stays a point. Inferring a line
        // from the first moving fix commonly learns the pit-exit heading and
        // makes the effective gate disagree with the pin shown to the rider.
        raceTimingEngine?.let { engine ->
            _gpsAccuracyMeters.value = if (location.hasAccuracy()) location.accuracy.toDouble() else null
            updateTrace(location)
            engine.process(
                GpsTimingSample(
                    point = GeoPoint(location.latitude, location.longitude),
                    timestampMs = location.time,
                    horizontalAccuracyMeters = if (location.hasAccuracy()) location.accuracy.toDouble() else Double.NaN,
                    speedMetersPerSecond = if (location.hasSpeed()) location.speed.toDouble() else 0.0
                )
            ).forEach { event ->
                when (event) {
                    is TimingEvent.LapStarted -> {
                        lapStartTimeMs = event.timestampMs
                        _currentLapElapsed.value = 0.0
                        resetTrace(startingAt = location)
                        _sectorSplits.value = emptyList()
                    }
                    is TimingEvent.SectorCompleted -> _sectorSplits.value = _sectorSplits.value + event.splitSeconds
                    is TimingEvent.LapCompleted -> {
                        _timingConfidence.value = event.confidence
                        _invalidLapReason.value = null
                        val prior = _bestSectorTimes.value
                        _bestSectorTimes.value = event.sectorSeconds.mapIndexed { index, value -> minOf(value, prior.getOrNull(index) ?: Double.MAX_VALUE) }
                        recordLap(event.lapSeconds, event.timestampMs, location)
                    }
                    is TimingEvent.LapInvalid -> _invalidLapReason.value = event.reason
                    is TimingEvent.PitStateChanged -> _inPitLane.value = event.inPit
                    else -> Unit
                }
            }
            return
        }
        val line = startFinish ?: return
        if (!location.hasAccuracy() ||
            location.accuracy < 0f ||
            location.accuracy > MAX_ACCEPTABLE_HORIZONTAL_ACCURACY ||
            abs(System.currentTimeMillis() - location.time) > MAX_ACCEPTABLE_FIX_AGE_MS
        ) {
            return
        }

        if (awaitingFirstFix) {
            awaitingFirstFix = false
            lapStartTimeMs = location.time
            _currentLapElapsed.value = 0.0
            resetTrace(startingAt = location)
        }

        val distance = location.distanceTo(line)

        if (!hasClearedZone) {
            if (distance > CLEAR_RADIUS) hasClearedZone = true
            lastCrossingCandidate = location to distance
            return
        }

        val startMs = lapStartTimeMs
        if (distance > CROSSING_RADIUS || startMs == null) {
            updateTrace(location)
            lastCrossingCandidate = location to distance
            return
        }

        // Interpolate crossing instant between previous (outside) and current (inside)
        val prev = lastCrossingCandidate
        val crossingTimeMs = if (prev != null && prev.second > CROSSING_RADIUS && prev.second > distance) {
            val t = ((prev.second - CROSSING_RADIUS) / (prev.second - distance)).coerceIn(0f, 1f)
            val dt = location.time - prev.first.time
            prev.first.time + (dt * t).toLong()
        } else {
            location.time
        }

        val elapsed = (crossingTimeMs - startMs) / 1000.0
        if (elapsed < MIN_LAP_DURATION) {
            updateTrace(location)
            lastCrossingCandidate = location to distance
            return
        }

        recordLap(elapsed, crossingTimeMs, location)
        hasClearedZone = false
        lastCrossingCandidate = location to distance
    }

    private fun recordLap(elapsed: Double, crossingTimeMs: Long, location: Location) {
        lapStartTimeMs?.let { _lapStartTimestamps.value = _lapStartTimestamps.value + it }
        val isNewBest = _bestLapTime.value == null || elapsed < _bestLapTime.value!!
        _laps.value = _laps.value + elapsed
        _lastLapTime.value = elapsed
        if (isNewBest) {
            _bestLapTime.value = elapsed
            bestLapSamples = currentLapSamples.toList()
            _bestLapCoordinates.value = currentLapCoordinates.toList()
        }

        lapStartTimeMs = crossingTimeMs
        _currentLapElapsed.value = 0.0
        resetTrace(startingAt = location)
        _liveDeltaSeconds.value = null
    }

    private fun resetTrace(startingAt: Location? = null) {
        currentLapSamples = mutableListOf(LapSample(0.0, 0.0))
        currentLapCoordinates = startingAt?.let { mutableListOf(GeoPoint(it.latitude, it.longitude)) }
            ?: mutableListOf()
        currentLapDistance = 0.0
        lastTracePoint = startingAt
    }

    private fun updateTrace(location: Location) {
        val startMs = lapStartTimeMs ?: return

        val last = lastTracePoint
        if (last != null) {
            val step = location.distanceTo(last).toDouble()
            if (step > MAX_TRACE_STEP_DISTANCE) return
            currentLapDistance += step
            lastTracePoint = location
        } else {
            lastTracePoint = location
        }

        val elapsedTime = (location.time - startMs) / 1000.0
        if (elapsedTime < 0) return
        currentLapSamples.add(LapSample(currentLapDistance, elapsedTime))
        currentLapCoordinates.add(GeoPoint(location.latitude, location.longitude))
        refreshLiveDelta()
    }

    private fun refreshLiveDelta() {
        val best = bestLapSamples
        val current = currentLapSamples.lastOrNull()
        if (best == null || current == null) {
            _liveDeltaSeconds.value = null
            return
        }
        val bestTime = interpolatedTime(best, current.distance)
        if (bestTime == null) {
            _liveDeltaSeconds.value = null
            return
        }
        _liveDeltaSeconds.value = current.elapsedTime - bestTime
    }

    companion object {
        private const val CROSSING_RADIUS = 20f
        private const val CLEAR_RADIUS = 45f
        private const val MIN_LAP_DURATION = 12.0
        private const val MAX_ACCEPTABLE_HORIZONTAL_ACCURACY = 25f
        private const val MAX_ACCEPTABLE_FIX_AGE_MS = 3_000L
        private const val MAX_TRACE_STEP_DISTANCE = 150.0

        private fun destination(origin: GeoPoint, meters: Double, bearingDegrees: Double): GeoPoint {
            val radius = 6_371_000.0
            val angular = meters / radius
            val bearing = Math.toRadians(bearingDegrees)
            val lat1 = Math.toRadians(origin.latitude)
            val lon1 = Math.toRadians(origin.longitude)
            val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
            val lon2 = lon1 + atan2(
                sin(bearing) * sin(angular) * cos(lat1),
                cos(angular) - sin(lat1) * sin(lat2)
            )
            return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
        }

        private fun interpolatedTime(samples: List<LapSample>, atDistance: Double): Double? {
            val first = samples.firstOrNull() ?: return null
            val last = samples.lastOrNull() ?: return null
            if (atDistance <= first.distance) return first.elapsedTime
            if (atDistance > last.distance) return null
            for (i in 1 until samples.size) {
                val prev = samples[i - 1]
                val curr = samples[i]
                if (atDistance > curr.distance) continue
                if (curr.distance <= prev.distance) return prev.elapsedTime
                val t = (atDistance - prev.distance) / (curr.distance - prev.distance)
                return prev.elapsedTime + t * (curr.elapsedTime - prev.elapsedTime)
            }
            return last.elapsedTime
        }

        fun formatLapTime(seconds: Double): String {
            if (!seconds.isFinite() || seconds < 0) return "--:--"
            val m = seconds.toInt() / 60
            val s = seconds.toInt() % 60
            val tenths = ((seconds - seconds.toInt()) * 10).toInt().coerceIn(0, 9)
            return "%d:%02d.%d".format(m, s, tenths)
        }

        fun formatDelta(seconds: Double): String = "%+.1fs".format(seconds)
    }
}
