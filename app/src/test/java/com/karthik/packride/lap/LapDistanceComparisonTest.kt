package com.karthik.packride.lap

import com.karthik.packride.replay.ParsedGpxPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LapDistanceComparisonTest {
    @Test fun exactCrossingTimestampsExcludeApproachAndCooldown() {
        val recordingStart = 1_000_000L
        val points = (0..20).map { second ->
            ParsedGpxPoint(35.0, -80.0 + second * 0.00001,
                timeMs = recordingStart + second * 1000L, speed = 10.0)
        }
        // Recording began five seconds before lap 1 crossed the line; two
        // five-second laps ended at t=15 and recording continued afterward.
        val starts = listOf(recordingStart + 5_000L, recordingStart + 10_000L)
        val laps = LapReconstructor.reconstruct(points, listOf(5.0, 5.0), starts)
        assertEquals(2, laps.size)
        assertTrue(laps[0].points.all { it.sample.timeMs in starts[0]..(starts[0] + 5_000L) })
        assertTrue(laps[1].points.all { it.sample.timeMs in starts[1]..(starts[1] + 5_000L) })
        assertEquals(0.0, laps[0].points.first().timeSeconds, 0.001)
    }

    @Test fun reconstructsBoundariesAndExcludesPostSessionPoints() {
        val start = 1_000_000L
        val points = (0..12).map { second ->
            ParsedGpxPoint(35.0, -80.0 + second * 0.00001, timeMs = start + second * 1000L, speed = 10.0)
        }
        val laps = LapReconstructor.reconstruct(points, listOf(5.0, 5.0))
        assertEquals(2, laps.size)
        assertEquals(5, laps[0].points.size)
        assertTrue(laps[1].points.none { it.sample.timeMs > start + 10_000L })
    }

    @Test fun distanceAlignsTwoLapsAndPreservesDeltaSign() {
        fun lap(index: Int, secondsPerPoint: Long) = ReconstructedLap(
            index, secondsPerPoint * 2 / 1000.0,
            listOf(
                LapTracePoint(0.0, 0.0, ParsedGpxPoint(0.0, 0.0, speed = 5.0)),
                LapTracePoint(50.0, secondsPerPoint / 1000.0, ParsedGpxPoint(0.0, 0.0, speed = 10.0)),
                LapTracePoint(100.0, secondsPerPoint * 2 / 1000.0, ParsedGpxPoint(0.0, 0.0, speed = 15.0))
            )
        )
        val result = DistanceLapComparisonBuilder.build(lap(0, 2_000), lap(1, 1_500), 3)
        assertNotNull(result)
        assertEquals(100.0, result!!.comparedDistanceMeters, 0.001)
        assertTrue(result.finishDelta < 0) // Lap B reaches the same distance first.
    }

    @Test fun finishDeltaUsesOfficialLapSplitsInsteadOfMismatchedTraceEndpoints() {
        val a = ReconstructedLap(0, 57.5, listOf(
            LapTracePoint(0.0, 0.0, ParsedGpxPoint(0.0, 0.0)),
            LapTracePoint(100.0, 55.0, ParsedGpxPoint(0.0, 0.0))
        ))
        val b = ReconstructedLap(1, 58.3, listOf(
            LapTracePoint(0.0, 0.0, ParsedGpxPoint(0.0, 0.0)),
            LapTracePoint(90.0, 57.1, ParsedGpxPoint(0.0, 0.0))
        ))
        val result = DistanceLapComparisonBuilder.build(a, b, 3)
        assertNotNull(result)
        assertEquals(0.8, result!!.finishDelta, 0.001)
        assertTrue(result.samples.last().deltaSeconds > result.finishDelta)
    }
}
