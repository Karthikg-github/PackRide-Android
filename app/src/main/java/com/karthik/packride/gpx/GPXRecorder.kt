package com.karthik.packride.gpx

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class GPXTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double,
    val speed: Double,      // m/s
    val gforce: Double,
    val lean: Double,
    val timestampMs: Long
)

/**
 * GPX recorder — Kotlin port of iOS GPXRecorder.
 * Throttles to ~1 Hz; stamps GPS time (location.time), not wall clock.
 */
class GPXRecorder(private val filesDir: File) {

    private val _pointCount = MutableStateFlow(0)
    val pointCount: StateFlow<Int> = _pointCount.asStateFlow()

    private val points = mutableListOf<GPXTrackPoint>()
    private var startTimeMs: Long? = null
    private var rideName: String = "PackRide"
    private var lastCaptureTimeMs: Long? = null
    private var lastAcceptedLocation: Location? = null

    /** Updated from motion sensors on the UI/main thread. */
    @Volatile var currentGForce: Double = 1.0
    @Volatile var currentLeanAngle: Double = 0.0

    fun startRecording(rideName: String) {
        this.rideName = rideName
        points.clear()
        _pointCount.value = 0
        startTimeMs = System.currentTimeMillis()
        lastCaptureTimeMs = null
        lastAcceptedLocation = null
    }

    fun capturePoint(location: Location) {
        val now = System.currentTimeMillis()
        if (!location.hasAccuracy() || location.accuracy !in 0f..25f || kotlin.math.abs(now - location.time) > 5_000) return
        lastAcceptedLocation?.let { previous ->
            val dtSeconds = (location.time - previous.time) / 1_000.0
            val step = location.distanceTo(previous)
            if (dtSeconds <= 0 || step > maxOf(75.0, dtSeconds * 70.0)) return
        }
        val last = lastCaptureTimeMs
        if (last != null && now - last < 900) return
        lastCaptureTimeMs = now

        points.add(
            GPXTrackPoint(
                latitude = location.latitude,
                longitude = location.longitude,
                elevation = if (location.hasAltitude()) location.altitude else 0.0,
                speed = if (location.hasSpeed()) maxOf(location.speed.toDouble(), 0.0) else 0.0,
                gforce = currentGForce,
                lean = currentLeanAngle,
                timestampMs = location.time
            )
        )
        lastAcceptedLocation = Location(location)
        _pointCount.value = points.size
    }

    /** Returns stored filename (not absolute path), or null if nothing to save. */
    fun stopAndSave(): String? {
        if (points.isEmpty()) return null
        return saveGPXFile()
    }

    fun cancelRecording() {
        points.clear()
        lastAcceptedLocation = null
        _pointCount.value = 0
    }

    private fun saveGPXFile(): String? {
        val dir = GPXStorage.ridesDirectory(filesDir)
        if (!dir.exists()) dir.mkdirs()

        val iso = isoFormatter()
        val dateStr = iso.format(Date(startTimeMs ?: System.currentTimeMillis()))
            .replace(":", "-")
        val safeName = rideName
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .take(80)
            .ifBlank { "PackRide" }
        val filename = "${safeName}_$dateStr.gpx"
        val file = File(dir, filename)
        return try {
            file.writeText(generateGPXXML())
            filename
        } catch (_: Exception) {
            null
        }
    }

    private fun generateGPXXML(): String {
        val iso = isoFormatter()
        val startISO = iso.format(Date(startTimeMs ?: System.currentTimeMillis()))
        val sb = StringBuilder()
        sb.append(
            """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<gpx version="1.1" creator="PackRide Android"
            |     xmlns="http://www.topografix.com/GPX/1/1"
            |     xmlns:packride="http://packride.app/gpx/1.0">
            |  <metadata>
            |    <name>${escapeXml(rideName)}</name>
            |    <time>$startISO</time>
            |  </metadata>
            |  <trk>
            |    <name>${escapeXml(rideName)}</name>
            |    <trkseg>
            |
            """.trimMargin()
        )
        for (p in points) {
            val timeISO = iso.format(Date(p.timestampMs))
            sb.append(
                """
                |      <trkpt lat="${p.latitude}" lon="${p.longitude}">
                |        <ele>${String.format(Locale.US, "%.2f", p.elevation)}</ele>
                |        <time>$timeISO</time>
                |        <speed>${String.format(Locale.US, "%.2f", p.speed)}</speed>
                |        <extensions>
                |          <packride:gforce>${String.format(Locale.US, "%.2f", p.gforce)}</packride:gforce>
                |          <packride:lean>${String.format(Locale.US, "%.1f", p.lean)}</packride:lean>
                |        </extensions>
                |      </trkpt>
                |
                """.trimMargin()
            )
        }
        sb.append(
            """
            |    </trkseg>
            |  </trk>
            |</gpx>
            """.trimMargin()
        )
        return sb.toString()
    }

    private fun isoFormatter(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

object GPXStorage {
    fun ridesDirectory(filesDir: File): File = File(filesDir, "rides")

    fun resolve(filesDir: File, stored: String): File {
        val filename = File(stored).name
        return File(ridesDirectory(filesDir), filename)
    }

    fun exists(filesDir: File, stored: String): Boolean =
        resolve(filesDir, stored).exists()

    fun remove(filesDir: File, stored: String) {
        runCatching { resolve(filesDir, stored).delete() }
    }

    /**
     * Aug 30, 2026 — Ride History cloud-restore parity: downloads a ride's
     * GPX from its Firebase Storage download URL (RideRecord.gpxURL) into
     * this install's local rides/ dir, so a ride pulled in from the cloud
     * (new install, different device, reinstall) can still show its route,
     * export, or feed a map — same "download once, cache the filename"
     * shape as iOS's RideHistoryManager.resolveGPXPath. Blocking I/O — call
     * off the main thread.
     */
    fun downloadAndSave(filesDir: File, rideId: String, urlString: String): String? {
        var temporary: File? = null
        return try {
            val dir = ridesDirectory(filesDir)
            if (!dir.exists()) dir.mkdirs()
            val filename = "${rideId}_synced.gpx"
            val file = File(dir, filename)
            if (file.isFile && file.length() > 0) return filename
            val url = java.net.URL(urlString)
            if (url.protocol != "https") return null
            val connection = url.openConnection().apply {
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            val downloadFile = File(dir, ".$filename.download")
            temporary = downloadFile
            connection.getInputStream().use { input ->
                downloadFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (downloadFile.length() <= 0L) return null
            if (!downloadFile.renameTo(file)) {
                downloadFile.copyTo(file, overwrite = true)
                downloadFile.delete()
            }
            filename
        } catch (_: Exception) {
            temporary?.delete()
            null
        }
    }
}
