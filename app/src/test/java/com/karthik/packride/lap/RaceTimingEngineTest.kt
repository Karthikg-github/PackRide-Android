package com.karthik.packride.lap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RaceTimingEngineTest {
    private val gate = TimingGate(
        "finish", GeoPoint(-0.001, 0.0), GeoPoint(0.001, 0.0),
        GateDirection.POSITIVE_TO_NEGATIVE
    )

    private fun sample(lon: Double, time: Long, accuracy: Double = 3.0) =
        GpsTimingSample(GeoPoint(0.0, lon), time, accuracy)

    @Test fun interpolatesFiniteDirectionalGateCrossing() {
        var now = 0L
        val engine = RaceTimingEngine(TrackTimingConfiguration(gate, minimumLapSeconds = 1.0), nowMs = { now })
        now = 1_000; engine.process(sample(-0.0001, now))
        now = 2_000
        val first = engine.process(sample(0.0001, now))
        assertTrue(first.any { it is TimingEvent.LapStarted && it.timestampMs == 1_500L })

        now = 4_000; engine.process(sample(-0.0001, now)) // wrong direction is ignored for completion
        now = 5_000
        val completed = engine.process(sample(0.0001, now)).filterIsInstance<TimingEvent.LapCompleted>().single()
        assertEquals(3.0, completed.lapSeconds, 0.001)
    }

    @Test fun rejectsPoorAccuracy() {
        val engine = RaceTimingEngine(TrackTimingConfiguration(gate), nowMs = { 1_000 })
        assertTrue(engine.process(sample(-0.001, 1_000, 40.0)).single() is TimingEvent.FixRejected)
    }

    @Test fun invalidatesLapWhenSectorWasSkipped() {
        var now = 0L
        val sector = gate.copy(id = "s1", a = GeoPoint(-0.001, 0.001), b = GeoPoint(0.001, 0.001))
        val engine = RaceTimingEngine(TrackTimingConfiguration(gate, listOf(sector), 1.0), nowMs = { now })
        now = 1_000; engine.process(sample(-0.0001, now))
        now = 2_000; engine.process(sample(0.0001, now))
        now = 4_000; engine.process(sample(-0.0001, now))
        now = 5_000
        assertTrue(engine.process(sample(0.0001, now)).any {
            it is TimingEvent.LapInvalid && it.reason == LapInvalidReason.SKIPPED_SECTOR
        })
    }

    @Test fun ignoresCrossingOutsideGateSegment() {
        var now = 1_000L
        val engine = RaceTimingEngine(TrackTimingConfiguration(gate), nowMs = { now })
        engine.process(GpsTimingSample(GeoPoint(0.01, -0.0001), now, 2.0))
        now = 2_000
        assertTrue(engine.process(GpsTimingSample(GeoPoint(0.01, 0.0001), now, 2.0)).isEmpty())
    }

    @Test fun openCourseCompletesAtSeparateFinishWithoutStartingAnotherRun() {
        var now = 0L
        val finish = gate.copy(id = "course-finish", a = GeoPoint(-0.001, 0.001), b = GeoPoint(0.001, 0.001))
        val engine = RaceTimingEngine(TrackTimingConfiguration(gate, minimumLapSeconds = 1.0, finishGate = finish), nowMs = { now })
        now = 1_000; engine.process(sample(-0.0001, now))
        now = 2_000; assertTrue(engine.process(sample(0.0001, now)).any { it is TimingEvent.LapStarted })
        now = 4_000; engine.process(sample(0.0009, now))
        now = 5_000
        val events = engine.process(sample(0.0011, now))
        assertTrue(events.any { it is TimingEvent.LapCompleted })
        assertTrue(events.none { it is TimingEvent.LapStarted })
    }
}
