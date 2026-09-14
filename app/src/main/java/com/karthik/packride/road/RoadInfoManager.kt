package com.karthik.packride.road

import android.content.Context
import android.location.Geocoder
import android.location.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Real road name (Android Geocoder reverse geocoding — same API WaypointsScreen
 * already uses for address search) and posted speed limit (OpenStreetMap's free
 * Overpass API, community-sourced, coverage varies by area). Kotlin port of iOS
 * RoadInfoManager.swift — same throttling (150m moved OR 20s elapsed, whichever
 * first) so this doesn't hammer either service on every GPS tick, same
 * preferred-road-type filtering so a `maxspeed` tag on a parking-aisle way
 * doesn't win over a real street, and same mph/km/h/unitless tag parsing.
 *
 * Deliberately a class (one instance per screen, like iOS's `@StateObject`),
 * not a singleton — nothing here needs to be shared across screens.
 */
class RoadInfoManager(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private val _roadName = MutableStateFlow<String?>(null)
    val roadName: StateFlow<String?> = _roadName.asStateFlow()

    private val _speedLimitMph = MutableStateFlow<Int?>(null)
    val speedLimitMph: StateFlow<Int?> = _speedLimitMph.asStateFlow()

    private var lastLookupLocation: Location? = null
    private var lastLookupAtMs: Long = 0L
    private var isLookingUp = false

    fun update(location: Location) {
        if (isLookingUp) return
        lastLookupLocation?.let { last ->
            val movedFarEnough = location.distanceTo(last) >= MIN_DISTANCE_M
            val enoughTimePassed = System.currentTimeMillis() - lastLookupAtMs >= MIN_INTERVAL_MS
            if (!movedFarEnough && !enoughTimePassed) return
        }
        lastLookupLocation = location
        lastLookupAtMs = System.currentTimeMillis()
        isLookingUp = true

        scope.launch {
            val nameDeferred = async { fetchRoadName(location) }
            val limitDeferred = async { fetchSpeedLimit(location) }
            val name = nameDeferred.await()
            val limit = limitDeferred.await()
            _roadName.value = name ?: _roadName.value
            _speedLimitMph.value = limit
            isLookingUp = false
        }
    }

    fun shutdown() {
        scope.cancel()
    }

    // MARK: - Road name (Android Geocoder reverse geocoding)
    private suspend fun fetchRoadName(location: Location): String? = withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            val addr = Geocoder(appContext, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull() ?: return@runCatching null
            // Address.thoroughfare is already just the street name, no house
            // number attached — matches iOS's CLPlacemark.thoroughfare, the
            // preferred case.
            addr.thoroughfare?.let { return@runCatching it }
            // Fall back to the formatted address line when thoroughfare isn't
            // populated — it can be a full street address ("1234 Main St"), so
            // drop a leading house number so the badge still shows just the
            // road name, same as iOS's fallback.
            val line = addr.getAddressLine(0) ?: return@runCatching null
            val parts = line.split(" ")
            if (parts.size > 1 && parts[0].all { it.isDigit() || it == '-' }) {
                parts.drop(1).joinToString(" ")
            } else {
                line
            }
        }.getOrNull()
    }

    // MARK: - Speed limit (OpenStreetMap Overpass)
    private suspend fun fetchSpeedLimit(location: Location): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val query = "[out:json][timeout:8];" +
                "way(around:40,${location.latitude},${location.longitude})[\"highway\"][\"maxspeed\"];" +
                "out tags 8;"
            val url = URL(OVERPASS_ENDPOINTS[0])
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", "PackRideApp/1.0 (contact: karthikgundavarapu@gmail.com)")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            conn.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
            val body = conn.inputStream.bufferedReader().readText()
            val elements = JSONObject(body).optJSONArray("elements") ?: return@runCatching null

            // Prefer drivable road types over service/parking-aisle style ways.
            var preferred: String? = null
            var fallback: String? = null
            for (i in 0 until elements.length()) {
                val tags = elements.getJSONObject(i).optJSONObject("tags") ?: continue
                val maxspeed = tags.optString("maxspeed", "")
                val highway = tags.optString("highway", "")
                if (maxspeed.isEmpty() || highway.isEmpty()) continue
                if (fallback == null) fallback = maxspeed
                if (preferred == null && highway in PREFERRED_HIGHWAY_TYPES) preferred = maxspeed
            }
            (preferred ?: fallback)?.let { parseMaxSpeed(it) }
        }.getOrNull()
    }

    companion object {
        private const val MIN_DISTANCE_M = 150f
        private const val MIN_INTERVAL_MS = 20_000L

        private val OVERPASS_ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )

        private val PREFERRED_HIGHWAY_TYPES = setOf(
            "motorway", "trunk", "primary", "secondary", "tertiary",
            "unclassified", "residential", "motorway_link", "trunk_link",
            "primary_link", "secondary_link", "tertiary_link"
        )

        // Parses OSM's maxspeed tag formats: "25 mph", "45", "70 mph", "50 km/h".
        // Per OSM convention, an unsuffixed number in the US is assumed mph.
        fun parseMaxSpeed(raw: String): Int? {
            val trimmed = raw.trim().lowercase()
            return when {
                trimmed.endsWith("mph") -> trimmed.removeSuffix("mph").trim().toIntOrNull()
                trimmed.endsWith("km/h") -> {
                    val kmh = trimmed.removeSuffix("km/h").trim().toDoubleOrNull() ?: return null
                    Math.round(kmh * 0.621371).toInt()
                }
                else -> trimmed.toIntOrNull()
            }
        }
    }
}
