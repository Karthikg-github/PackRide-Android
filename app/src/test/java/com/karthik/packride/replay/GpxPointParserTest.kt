package com.karthik.packride.replay

import org.junit.Assert.assertEquals
import org.junit.Test

class GpxPointParserTest {
    @Test fun readsTrackTelemetryUsedByReplayAndTrackAnalytics() {
        val xml = """<gpx><trk><trkseg><trkpt lat="33.1" lon="-84.2"><ele>310.5</ele><time>2026-09-02T12:00:00Z</time><speed>20.5</speed><extensions><packride:gforce>1.22</packride:gforce><packride:lean>-31.4</packride:lean></extensions></trkpt></trkseg></trk></gpx>"""
        val point = GpxPointParser.parseXml(xml).single()
        assertEquals(33.1, point.lat, 0.0001)
        assertEquals(20.5, point.speed, 0.0001)
        assertEquals(1.22, point.gforce, 0.0001)
        assertEquals(-31.4, point.lean, 0.0001)
        assert(point.timeMs > 0)
    }
}
