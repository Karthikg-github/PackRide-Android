package com.karthik.packride.analytics

import com.karthik.packride.replay.GpxPointParser
import com.karthik.packride.replay.ParsedGpxPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// Aug 30, 2026 — Kotlin port of iOS RideAnalyticsEngine.swift. Post-ride
// analysis computed from the GPX file already being recorded for every
// ride (see gpx/GPXRecorder.kt) and every Track Mode session. This is a
// heuristic estimate built from phone sensors (GPS + accelerometer/
// gyroscope, fed into the GPX's packride:gforce/packride:lean extensions
// by motion/RideMotionMonitor.kt) — not a lab-grade instrument. It's meant
// to give a rider a useful sense of how a ride went (smooth vs. abrupt),
// not a precise telemetry reading.
//
// Parses via replay/GpxPointParser.kt (already extended this session to
// read the gforce/lean extensions, and now also <time> — see that file's
// header) rather than a second regex-based GPX reader, so there's exactly
// one place that understands this app's GPX shape.
//
// Consumed by three screens, all newly wired up in this same pass:
//  - badges/BadgeEngine.kt — "Smooth Operator" badge (Ride Score 95+)
//  - ui/screens/TrackModeScreen.kt — Track Score on the session summary
//  - ui/screens/RideHistoryScreen.kt — a per-ride score in the expanded row
//
// Analyzing a long ride's GPX (thousands of points, regex-parsed) is
// nontrivial CPU work — every call site must invoke this off the main
// thread (analyzeAsync below wraps that; the plain analyze() overloads are
// synchronous and assume the caller already did that).

/** Mirrors iOS's RideScoreSummary. */
data class RideScoreSummary(
    val rideScore: Int,             // 0-100, higher = smoother ride
    val cornerCount: Int,
    val smoothCornerCount: Int,
    val hardBrakeCount: Int,
    val hardAccelCount: Int,
    val maxLeanAngle: Double        // degrees, unsigned (magnitude)
) {
    val scoreGrade: String
        get() = when (rideScore) {
            in 90..100 -> "Smooth"
            in 75..89 -> "Solid"
            in 55..74 -> "Mixed"
            else -> "Aggressive"
        }

    companion object {
        val EMPTY = RideScoreSummary(
            rideScore = 100, cornerCount = 0, smoothCornerCount = 0,
            hardBrakeCount = 0, hardAccelCount = 0, maxLeanAngle = 0.0
        )
    }
}

/**
 * Solo/road-ride analytics — iOS RideAnalyticsEngine.analyze parity.
 * Scoped to any ride with a recorded GPX (road or otherwise); Ride History
 * uses this uniformly, matching iOS's RideScoreCard usage there.
 */
object RideAnalyticsEngine {
    // location.speed / GPXRecorder's <speed> tag is meters/second; iOS
    // converts the same way (spd * 2.23694) before scoring in mph.
    private const val MPS_TO_MPH = 2.23694
    private const val MIN_POINTS = 5

    /** Synchronous — parses the GPX file and scores it. Call off the main thread. */
    fun analyze(gpxFile: File): RideScoreSummary? {
        if (!gpxFile.exists()) return null
        val points = GpxPointParser.parse(gpxFile)
        if (points.size < MIN_POINTS) return null
        return analyzePoints(points)
    }

    /** Same as [analyze], dispatched to a background thread — safe to call from a Composable/coroutine. */
    suspend fun analyzeAsync(gpxFile: File): RideScoreSummary? =
        withContext(Dispatchers.Default) { analyze(gpxFile) }

    internal fun analyzePoints(points: List<ParsedGpxPoint>): RideScoreSummary {
        var cornerCount = 0
        var smoothCornerCount = 0
        var hardBrakeCount = 0
        var hardAccelCount = 0
        var maxLean = 0.0

        var inCorner = false
        var cornerHadHighG = false

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val dtMs = curr.timeMs - prev.timeMs
            // Skip gaps (e.g. a brief signal loss) — same 0 < dt < 5s window as iOS.
            if (dtMs <= 0 || dtMs >= 5000) continue
            val dt = dtMs / 1000.0

            maxLean = max(maxLean, abs(curr.lean))

            val prevMph = prev.speed * MPS_TO_MPH
            val currMph = curr.speed * MPS_TO_MPH

            // Hard brake / hard accel — a speed change per second beyond
            // typical relaxed riding, only counted while actually moving
            // (excludes stop sign creep/parking lot noise).
            if (currMph > 5 || prevMph > 5) {
                val speedDeltaPerSec = (currMph - prevMph) / dt
                if (speedDeltaPerSec < -8) hardBrakeCount++
                if (speedDeltaPerSec > 7) hardAccelCount++
            }

            // Corner detection: sustained lean beyond a small threshold
            // while moving counts as "in a corner." A corner is scored
            // "smooth" if G-force never spiked hard during it (i.e. no
            // abrupt mid-corner braking/acceleration), "abrupt" otherwise.
            val leaning = abs(curr.lean) > 8 && currMph > 8
            if (leaning && !inCorner) {
                inCorner = true
                cornerHadHighG = false
            }
            if (inCorner) {
                if (curr.gforce > 1.4) cornerHadHighG = true
                if (!leaning) {
                    inCorner = false
                    cornerCount++
                    if (!cornerHadHighG) smoothCornerCount++
                }
            }
        }
        if (inCorner) {
            cornerCount++
            if (!cornerHadHighG) smoothCornerCount++
        }

        // Score: start at 100, deduct for harsh events. Deductions are
        // capped per category so one rough stretch of road doesn't tank the
        // whole ride's score, and floored at 40 so the number stays
        // meaningful/encouraging rather than reading as "broken."
        var score = 100
        score -= min(hardBrakeCount * 3, 24)
        score -= min(hardAccelCount * 2, 16)
        val abruptCorners = cornerCount - smoothCornerCount
        score -= min(abruptCorners * 2, 20)
        score = max(score, 40)

        return RideScoreSummary(
            rideScore = score,
            cornerCount = cornerCount,
            smoothCornerCount = smoothCornerCount,
            hardBrakeCount = hardBrakeCount,
            hardAccelCount = hardAccelCount,
            maxLeanAngle = maxLean
        )
    }
}

/** Mirrors iOS's LapAnalyticsSummary. */
data class LapAnalyticsSummary(
    val trackScore: Int,          // 0-100 composite of consistency + smoothness
    val consistencyScore: Int,    // 0-100, tighter lap-time spread = higher
    val smoothnessScore: Int,     // 0-100, same GPS/motion analysis as solo rides
    val lapTimeStdDev: Double,    // seconds, standard deviation across laps
    val cornerCount: Int,
    val smoothCornerCount: Int,
    val hardBrakeCount: Int,
    val hardAccelCount: Int,
    val maxLeanAngle: Double
) {
    val scoreGrade: String
        get() = when (trackScore) {
            in 90..100 -> "Dialed In"
            in 75..89 -> "Solid"
            in 55..74 -> "Building"
            else -> "Rough Session"
        }

    companion object {
        val EMPTY = LapAnalyticsSummary(
            trackScore = 100, consistencyScore = 100, smoothnessScore = 100,
            lapTimeStdDev = 0.0, cornerCount = 0, smoothCornerCount = 0,
            hardBrakeCount = 0, hardAccelCount = 0, maxLeanAngle = 0.0
        )
    }
}

/**
 * Track/lap analytics — iOS LapAnalyticsEngine.analyze parity. Track Score
 * is a composite of lap-time consistency (from the splits themselves) and
 * the same GPS/motion smoothness analysis used for solo rides, applied to
 * the session's own recorded GPX.
 */
object LapAnalyticsEngine {
    /**
     * Returns null if there aren't at least 2 completed laps — consistency
     * has nothing to measure a spread against with only one lap (or none),
     * same guard as RideAnalyticsEngine's minimum-point-count check.
     * [gpxFile] may be null/missing (e.g. save failed); smoothness then
     * defaults to a perfect 100/no events rather than failing the whole
     * score, since consistency alone is still useful.
     */
    fun analyze(gpxFile: File?, laps: List<Double>): LapAnalyticsSummary? {
        if (laps.size < 2) return null

        val consistency = consistencyScore(laps)

        var smoothness = 100
        var corner = 0
        var smoothCorner = 0
        var hardBrake = 0
        var hardAccel = 0
        var maxLean = 0.0

        if (gpxFile != null) {
            RideAnalyticsEngine.analyze(gpxFile)?.let { r ->
                smoothness = r.rideScore
                corner = r.cornerCount
                smoothCorner = r.smoothCornerCount
                hardBrake = r.hardBrakeCount
                hardAccel = r.hardAccelCount
                maxLean = r.maxLeanAngle
            }
        }

        val composite = Math.round((consistency + smoothness) / 2.0).toInt()

        return LapAnalyticsSummary(
            trackScore = composite,
            consistencyScore = consistency,
            smoothnessScore = smoothness,
            lapTimeStdDev = stdDev(laps),
            cornerCount = corner,
            smoothCornerCount = smoothCorner,
            hardBrakeCount = hardBrake,
            hardAccelCount = hardAccel,
            maxLeanAngle = maxLean
        )
    }

    /** Same as [analyze], dispatched to a background thread — safe to call from a Composable/coroutine. */
    suspend fun analyzeAsync(gpxFile: File?, laps: List<Double>): LapAnalyticsSummary? =
        withContext(Dispatchers.Default) { analyze(gpxFile, laps) }

    /**
     * 0-100, scored by *percentage* spread (coefficient of variation)
     * rather than raw seconds — a 2-second spread matters a lot more on a
     * 45-second lap than a 3-minute one. Tuned so ~10% spread lands around
     * 60 and a couple percent spread reads as 90+.
     */
    private fun consistencyScore(laps: List<Double>): Int {
        val mean = laps.average()
        if (mean <= 0) return 100
        val coefficientOfVariation = stdDev(laps) / mean
        val score = 100 - Math.round(coefficientOfVariation * 400).toInt()
        return score.coerceIn(0, 100)
    }

    private fun stdDev(values: List<Double>): Double {
        if (values.size <= 1) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
        return sqrt(variance)
    }
}
