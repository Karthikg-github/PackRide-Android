package com.karthik.packride.lap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LapCompareEngineTest {
    @Test fun rejectsEmptySessions() {
        assertNull(LapCompareEngine.compare(emptyList(), listOf(90.0)))
        assertNull(LapCompareEngine.compare(listOf(90.0), emptyList()))
    }

    @Test fun comparesBestAverageAndSharedIndices() {
        val result = LapCompareEngine.compare(
            listOf(92.4, 91.15, 90.88),
            listOf(93.1, 91.8, 91.05, 90.95)
        )!!
        assertEquals(90.88, result.bestA, 0.0001)
        assertEquals(90.95, result.bestB, 0.0001)
        assertEquals(-0.07, result.deltaBestSeconds, 0.0001)
        assertEquals(3, result.perLapDeltas.size)
    }

    @Test fun formatsSubMinuteAndMultiMinuteLaps() {
        assertEquals("0:45.20", LapCompareEngine.formatLap(45.2))
        assertEquals("2:05.50", LapCompareEngine.formatLap(125.5))
    }
}
