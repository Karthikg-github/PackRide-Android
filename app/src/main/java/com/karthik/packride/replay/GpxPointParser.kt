package com.karthik.packride.replay

import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class ParsedGpxPoint(
    val lat: Double,
    val lng: Double,
    val ele: Double = 0.0,
    val timeMs: Long = 0L,
    val speed: Double = 0.0,
    /** From <packride:gforce> extension — written by GPXRecorder, defaults to 1.0 (resting) when absent. */
    val gforce: Double = 1.0,
    /** From <packride:lean> extension — degrees, defaults to 0.0 when absent. */
    val lean: Double = 0.0
)

/** Minimal GPX trkpt parser for replay — iOS GPXPointParser parity. */
object GpxPointParser {
    // Same format GPXRecorder.isoFormatter() writes (UTC, "Z" literal).
    // Aug 30, 2026 — analytics parity pass: timeMs was declared on
    // ParsedGpxPoint but never actually populated from <time>, so every
    // point read back 0L. Harmless for replay (which never reads timeMs),
    // but RideAnalyticsEngine needs real per-point deltas to compute
    // speed-change-per-second and corner duration — so this now parses it.
    private fun isoFormatter(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    fun parse(file: File): List<ParsedGpxPoint> {
        if (!file.exists()) return emptyList()
        val text = runCatching { file.readText() }.getOrNull() ?: return emptyList()
        return parseXml(text)
    }

    fun parseXml(xml: String): List<ParsedGpxPoint> {
        val points = mutableListOf<ParsedGpxPoint>()
        val iso = isoFormatter()
        val trkpt = Regex(
            """<trkpt\s+lat="([^"]+)"\s+lon="([^"]+)"[^>]*>(.*?)</trkpt>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        for (m in trkpt.findAll(xml)) {
            val lat = m.groupValues[1].toDoubleOrNull() ?: continue
            val lng = m.groupValues[2].toDoubleOrNull() ?: continue
            val body = m.groupValues[3]
            val ele = Regex("""<ele>([^<]+)</ele>""").find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val speed = Regex("""<speed>([^<]+)</speed>""").find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val gforce = Regex("""<packride:gforce>([^<]+)</packride:gforce>""").find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
            val lean = Regex("""<packride:lean>([^<]+)</packride:lean>""").find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val timeStr = Regex("""<time>([^<]+)</time>""").find(body)?.groupValues?.get(1)
            val timeMs = timeStr?.let { runCatching { iso.parse(it)?.time }.getOrNull() } ?: 0L
            points.add(ParsedGpxPoint(lat, lng, ele, timeMs = timeMs, speed = speed, gforce = gforce, lean = lean))
        }
        return points
    }

    /** Nearest point index to a tapped/scrub coordinate — plain squared-distance in lat/lng space (short enough legs that this beats haversine cost with no visible accuracy loss), iOS GPXPointParser.nearestIndex parity. */
    fun nearestIndex(lat: Double, lng: Double, points: List<ParsedGpxPoint>): Int? {
        if (points.isEmpty()) return null
        var bestIdx = 0
        var bestDist = Double.MAX_VALUE
        for (i in points.indices) {
            val dLat = points[i].lat - lat
            val dLng = points[i].lng - lng
            val d = dLat * dLat + dLng * dLng
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }
        return bestIdx
    }
}
