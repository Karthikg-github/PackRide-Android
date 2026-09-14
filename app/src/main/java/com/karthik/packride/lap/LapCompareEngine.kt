package com.karthik.packride.lap

import com.karthik.packride.replay.ParsedGpxPoint
import kotlin.math.*

data class LapTracePoint(val distanceMeters: Double, val timeSeconds: Double, val sample: ParsedGpxPoint, val signedG: Double = 0.0) {
    val brakeG get() = max(0.0, -signedG)
    val accelG get() = max(0.0, signedG)
}

data class ReconstructedLap(val lapIndex: Int, val durationSeconds: Double, val points: List<LapTracePoint>) {
    val totalDistanceMeters get() = points.lastOrNull()?.distanceMeters ?: 0.0
}

enum class LapCompareMetric(val label: String, private val imperialUnit: String) {
    SPEED("Speed", "mph"), LEAN("Lean", "°"), ELEVATION("Elevation", "ft"), BRAKE_G("Brake G", "g"), ACCEL_G("Accel G", "g");
    val unit: String get() = when (this) {
        SPEED -> if (com.karthik.packride.data.MeasurementUnits.current == com.karthik.packride.data.MeasurementSystem.METRIC) "km/h" else imperialUnit
        ELEVATION -> if (com.karthik.packride.data.MeasurementUnits.current == com.karthik.packride.data.MeasurementSystem.METRIC) "m" else imperialUnit
        else -> imperialUnit
    }
    fun value(point: LapTracePoint): Double = when (this) {
        SPEED -> point.sample.speed * if (com.karthik.packride.data.MeasurementUnits.current == com.karthik.packride.data.MeasurementSystem.METRIC) 3.6 else 2.23694
        LEAN -> abs(point.sample.lean)
        ELEVATION -> point.sample.ele * if (com.karthik.packride.data.MeasurementUnits.current == com.karthik.packride.data.MeasurementSystem.METRIC) 1.0 else 3.28084
        BRAKE_G -> point.brakeG
        ACCEL_G -> point.accelG
    }
}

data class DistanceComparisonSample(
    val distanceMeters: Double, val lapATime: Double, val lapBTime: Double,
    val metricsA: Map<LapCompareMetric, Double>, val metricsB: Map<LapCompareMetric, Double>
) { val deltaSeconds get() = lapBTime - lapATime }

data class DistanceLapCompareResult(
    val samples: List<DistanceComparisonSample>, val lapADuration: Double, val lapBDuration: Double,
    val comparedDistanceMeters: Double
) {
    // The timing-line result comes from the recorded lap splits. The two GPS
    // traces can end several metres apart, so their final distance-aligned
    // sample is useful for the graph but is not the official finish delta.
    val finishDelta get() = lapBDuration - lapADuration
}

/** Reconstructs each lap from the session's continuous GPX using the recorded split boundaries. */
object LapReconstructor {
    fun reconstruct(points: List<ParsedGpxPoint>, lapDurations: List<Double>, lapStartTimestamps: List<Long> = emptyList()): List<ReconstructedLap> {
        if (points.isEmpty() || lapDurations.isEmpty()) return emptyList()
        if (lapStartTimestamps.size >= lapDurations.size) {
            return lapDurations.indices.map { index ->
                val start = lapStartTimestamps[index]
                val end = start + (lapDurations[index] * 1000.0).toLong()
                val samples = points.filter { it.timeMs in start..end }
                buildLap(index, lapDurations[index], samples, start)
            }
        }
        val firstTime = points.firstOrNull { it.timeMs > 0 }?.timeMs ?: return emptyList()
        val boundaries = mutableListOf<Double>()
        var running = 0.0
        lapDurations.forEach { running += it; boundaries += running }
        val buckets = List(lapDurations.size) { mutableListOf<ParsedGpxPoint>() }
        for (point in points) {
            if (point.timeMs <= 0) continue
            val elapsed = (point.timeMs - firstTime) / 1000.0
            if (elapsed > boundaries.last()) break
            val index = boundaries.indexOfFirst { elapsed < it }.let { if (it < 0) boundaries.lastIndex else it }
            buckets[index] += point
        }
        return buckets.mapIndexed { index, samples ->
            var distance = 0.0
            val lapStart = if (index == 0) 0.0 else boundaries[index - 1]
            val traced = samples.mapIndexed { pointIndex, sample ->
                if (pointIndex > 0) distance += haversineMeters(samples[pointIndex - 1], sample)
                val elapsed = ((sample.timeMs - firstTime) / 1000.0 - lapStart).coerceAtLeast(0.0)
                val signedG = if (pointIndex == 0) 0.0 else {
                    val previous = samples[pointIndex - 1]
                    val dt = (sample.timeMs - previous.timeMs) / 1000.0
                    if (dt > 0 && dt < 5) ((sample.speed - previous.speed) / dt) / 9.80665 else 0.0
                }
                LapTracePoint(distance, elapsed, sample, signedG)
            }
            ReconstructedLap(index, lapDurations[index], traced)
        }
    }

    private fun buildLap(index: Int, duration: Double, samples: List<ParsedGpxPoint>, startMs: Long): ReconstructedLap {
        var distance = 0.0
        val traced = samples.mapIndexed { pointIndex, sample ->
            if (pointIndex > 0) distance += haversineMeters(samples[pointIndex - 1], sample)
            val signedG = if (pointIndex == 0) 0.0 else {
                val previous = samples[pointIndex - 1]
                val dt = (sample.timeMs - previous.timeMs) / 1000.0
                if (dt > 0 && dt < 5) ((sample.speed - previous.speed) / dt) / 9.80665 else 0.0
            }
            LapTracePoint(distance, ((sample.timeMs - startMs) / 1000.0).coerceAtLeast(0.0), sample, signedG)
        }
        return ReconstructedLap(index, duration, traced)
    }

    private fun haversineMeters(a: ParsedGpxPoint, b: ParsedGpxPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat); val dLon = Math.toRadians(b.lng - a.lng)
        val x = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 12_742_000.0 * asin(min(1.0, sqrt(x)))
    }
}

object DistanceLapComparisonBuilder {
    fun build(a: ReconstructedLap, b: ReconstructedLap, sampleCount: Int = 40): DistanceLapCompareResult? {
        if (a.points.size < 2 || b.points.size < 2) return null
        val distance = min(a.totalDistanceMeters, b.totalDistanceMeters)
        if (distance <= 0) return null
        val count = max(sampleCount, 2)
        val samples = (0 until count).map { i ->
            val d = if (i == count - 1) distance else distance * i / (count - 1)
            DistanceComparisonSample(
                d, interpolate(a.points, d) { it.timeSeconds }, interpolate(b.points, d) { it.timeSeconds },
                LapCompareMetric.entries.associateWith { metric -> interpolate(a.points, d) { metric.value(it) } },
                LapCompareMetric.entries.associateWith { metric -> interpolate(b.points, d) { metric.value(it) } }
            )
        }
        return DistanceLapCompareResult(samples, a.durationSeconds, b.durationSeconds, distance)
    }

    private fun interpolate(points: List<LapTracePoint>, distance: Double, value: (LapTracePoint) -> Double): Double {
        if (distance <= points.first().distanceMeters) return value(points.first())
        if (distance >= points.last().distanceMeters) return value(points.last())
        val upper = points.indexOfFirst { distance <= it.distanceMeters }.coerceAtLeast(1)
        val lo = points[upper - 1]; val hi = points[upper]
        if (hi.distanceMeters <= lo.distanceMeters) return value(lo)
        val fraction = (distance - lo.distanceMeters) / (hi.distanceMeters - lo.distanceMeters)
        return value(lo) + fraction * (value(hi) - value(lo))
    }
}

data class LapCompareResult(
    val lapCountA: Int, val lapCountB: Int, val bestA: Double, val bestB: Double,
    val avgA: Double, val avgB: Double, val deltaBestSeconds: Double, val perLapDeltas: List<Double>
) {
    val fasterLabel get() = when {
        deltaBestSeconds < -0.05 -> "A faster by ${"%.2f".format(-deltaBestSeconds)}s"
        deltaBestSeconds > 0.05 -> "B faster by ${"%.2f".format(deltaBestSeconds)}s"
        else -> "Best laps essentially equal"
    }
}

object LapCompareEngine {
    fun compare(lapsA: List<Double>, lapsB: List<Double>): LapCompareResult? {
        if (lapsA.isEmpty() || lapsB.isEmpty()) return null
        val a = lapsA.minOrNull() ?: return null; val b = lapsB.minOrNull() ?: return null
        return LapCompareResult(lapsA.size, lapsB.size, a, b, lapsA.average(), lapsB.average(), a - b,
            (0 until min(lapsA.size, lapsB.size)).map { lapsA[it] - lapsB[it] })
    }
    fun formatLap(seconds: Double): String = "%d:%05.2f".format((seconds / 60).toInt(), seconds % 60)
}
