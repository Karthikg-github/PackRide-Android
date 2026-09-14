package com.karthik.packride.lap

import kotlin.math.abs
import kotlin.math.cos

data class GeoPoint(val latitude: Double, val longitude: Double)

enum class GateDirection { NEGATIVE_TO_POSITIVE, POSITIVE_TO_NEGATIVE }

data class TimingGate(
    val id: String,
    val a: GeoPoint,
    val b: GeoPoint,
    val direction: GateDirection
)

data class TrackTimingConfiguration(
    val startFinish: TimingGate,
    val sectors: List<TimingGate> = emptyList(),
    val minimumLapSeconds: Double = 12.0,
    val finishGate: TimingGate? = null,
    val pitEntryGate: TimingGate? = null,
    val pitExitGate: TimingGate? = null
)

data class GpsTimingSample(
    val point: GeoPoint,
    val timestampMs: Long,
    val horizontalAccuracyMeters: Double,
    val speedMetersPerSecond: Double = 0.0
)

enum class LapInvalidReason { WRONG_DIRECTION, SKIPPED_SECTOR, TOO_SHORT }

sealed interface TimingEvent {
    data class FixRejected(val accuracyMeters: Double, val ageMs: Long) : TimingEvent
    data class LapStarted(val timestampMs: Long) : TimingEvent
    data class SectorCompleted(val index: Int, val splitSeconds: Double, val timestampMs: Long) : TimingEvent
    data class LapCompleted(
        val lapSeconds: Double,
        val sectorSeconds: List<Double>,
        val timestampMs: Long,
        val confidence: Double
    ) : TimingEvent
    data class LapInvalid(val reason: LapInvalidReason, val timestampMs: Long) : TimingEvent
    data class PitStateChanged(val inPit: Boolean, val timestampMs: Long) : TimingEvent
}

/**
 * RaceBox-style geometry core. It is deliberately Android-framework-free so
 * identical trace fixtures can later run against the Swift implementation.
 */
class RaceTimingEngine(
    private val configuration: TrackTimingConfiguration,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val maximumAccuracyMeters: Double = 25.0,
    private val maximumFixAgeMs: Long = 3_000
) {
    private var previous: GpsTimingSample? = null
    private var lapStartMs: Long? = null
    private var lastSplitMs: Long? = null
    private var nextSectorIndex = 0
    private val sectorTimes = mutableListOf<Double>()
    private var worstAccuracy = 0.0
    private var inPitLane = false
    private var learnedStartFinishDirection: GateDirection? = null

    fun reset() {
        previous = null
        lapStartMs = null
        lastSplitMs = null
        nextSectorIndex = 0
        sectorTimes.clear()
        worstAccuracy = 0.0
        inPitLane = false
        learnedStartFinishDirection = null
    }

    /** Arms the first full loop for a manually placed, course-oriented gate. */
    fun armFirstLap(timestampMs: Long) {
        learnedStartFinishDirection = GateDirection.POSITIVE_TO_NEGATIVE
        startLap(timestampMs)
    }

    fun process(sample: GpsTimingSample): List<TimingEvent> {
        val age = abs(nowMs() - sample.timestampMs)
        if (!sample.horizontalAccuracyMeters.isFinite() ||
            sample.horizontalAccuracyMeters < 0 ||
            sample.horizontalAccuracyMeters > maximumAccuracyMeters ||
            age > maximumFixAgeMs
        ) return listOf(TimingEvent.FixRejected(sample.horizontalAccuracyMeters, age))

        val from = previous
        previous = sample
        worstAccuracy = maxOf(worstAccuracy, sample.horizontalAccuracyMeters)
        if (from == null || sample.timestampMs <= from.timestampMs) return emptyList()

        val events = mutableListOf<TimingEvent>()
        configuration.pitExitGate?.let { gate -> crossing(from, sample, gate)?.takeIf { it.correctDirection }?.let { inPitLane = false; events += TimingEvent.PitStateChanged(false, it.timestampMs) } }
        configuration.pitEntryGate?.let { gate -> crossing(from, sample, gate)?.takeIf { it.correctDirection }?.let { inPitLane = true; events += TimingEvent.PitStateChanged(true, it.timestampMs) } }
        if (inPitLane) return events
        val expectedSector = configuration.sectors.getOrNull(nextSectorIndex)
        if (expectedSector != null) {
            crossing(from, sample, expectedSector)?.let { crossing ->
                if (crossing.correctDirection && lapStartMs != null) {
                    val prior = lastSplitMs ?: lapStartMs!!
                    sectorTimes += (crossing.timestampMs - prior) / 1000.0
                    lastSplitMs = crossing.timestampMs
                    events += TimingEvent.SectorCompleted(nextSectorIndex, sectorTimes.last(), crossing.timestampMs)
                    nextSectorIndex++
                }
            }
        }

        var activeGate = if (lapStartMs == null) configuration.startFinish else configuration.finishGate ?: configuration.startFinish
        if (activeGate.id == configuration.startFinish.id) {
            learnedStartFinishDirection?.let { activeGate = activeGate.copy(direction = it) }
        }
        crossing(from, sample, activeGate)?.let { crossing ->
            if (lapStartMs == null && learnedStartFinishDirection == null) {
                learnedStartFinishDirection = if (crossing.correctDirection) activeGate.direction else when (activeGate.direction) {
                    GateDirection.NEGATIVE_TO_POSITIVE -> GateDirection.POSITIVE_TO_NEGATIVE
                    GateDirection.POSITIVE_TO_NEGATIVE -> GateDirection.NEGATIVE_TO_POSITIVE
                }
                startLap(crossing.timestampMs)
                events += TimingEvent.LapStarted(crossing.timestampMs)
                return@let
            }
            if (!crossing.correctDirection) {
                if (lapStartMs != null) events += TimingEvent.LapInvalid(LapInvalidReason.WRONG_DIRECTION, crossing.timestampMs)
                return@let
            }
            val started = lapStartMs
            if (started == null) {
                startLap(crossing.timestampMs)
                events += TimingEvent.LapStarted(crossing.timestampMs)
            } else {
                val elapsed = (crossing.timestampMs - started) / 1000.0
                val reason = when {
                    nextSectorIndex < configuration.sectors.size -> LapInvalidReason.SKIPPED_SECTOR
                    elapsed < configuration.minimumLapSeconds -> LapInvalidReason.TOO_SHORT
                    else -> null
                }
                if (reason != null) {
                    events += TimingEvent.LapInvalid(reason, crossing.timestampMs)
                } else {
                    val finalSectorStart = lastSplitMs ?: started
                    val completedSectors = sectorTimes + ((crossing.timestampMs - finalSectorStart) / 1000.0)
                    events += TimingEvent.LapCompleted(
                        lapSeconds = elapsed,
                        sectorSeconds = completedSectors,
                        timestampMs = crossing.timestampMs,
                        confidence = (1.0 - worstAccuracy / maximumAccuracyMeters).coerceIn(0.0, 1.0)
                    )
                }
                if (configuration.finishGate == null) {
                    startLap(crossing.timestampMs)
                    events += TimingEvent.LapStarted(crossing.timestampMs)
                } else {
                    lapStartMs = null
                    lastSplitMs = null
                }
            }
        }
        return events
    }

    private fun startLap(timestampMs: Long) {
        lapStartMs = timestampMs
        lastSplitMs = timestampMs
        nextSectorIndex = 0
        sectorTimes.clear()
        worstAccuracy = 0.0
    }

    private data class Crossing(val timestampMs: Long, val correctDirection: Boolean)

    private fun crossing(from: GpsTimingSample, to: GpsTimingSample, gate: TimingGate): Crossing? {
        val originLat = (gate.a.latitude + gate.b.latitude) / 2.0
        fun xy(point: GeoPoint): Pair<Double, Double> {
            val x = point.longitude * cos(Math.toRadians(originLat)) * METERS_PER_DEGREE
            val y = point.latitude * METERS_PER_DEGREE
            return x to y
        }
        val (ax, ay) = xy(gate.a); val (bx, by) = xy(gate.b)
        val (px, py) = xy(from.point); val (qx, qy) = xy(to.point)
        val rx = qx - px; val ry = qy - py
        val sx = bx - ax; val sy = by - ay
        val denominator = cross(rx, ry, sx, sy)
        if (abs(denominator) < 1e-9) return null
        val t = cross(ax - px, ay - py, sx, sy) / denominator
        val u = cross(ax - px, ay - py, rx, ry) / denominator
        if (t !in 0.0..1.0 || u !in 0.0..1.0) return null

        val fromSide = cross(sx, sy, px - ax, py - ay)
        val toSide = cross(sx, sy, qx - ax, qy - ay)
        if (fromSide == 0.0 || toSide == 0.0 || fromSide * toSide >= 0) return null
        val correct = when (gate.direction) {
            GateDirection.NEGATIVE_TO_POSITIVE -> fromSide < 0 && toSide > 0
            GateDirection.POSITIVE_TO_NEGATIVE -> fromSide > 0 && toSide < 0
        }
        val time = from.timestampMs + ((to.timestampMs - from.timestampMs) * t).toLong()
        return Crossing(time, correct)
    }

    private fun cross(ax: Double, ay: Double, bx: Double, by: Double) = ax * by - ay * bx

    companion object { private const val METERS_PER_DEGREE = 111_320.0 }
}
