package com.karthik.packride.lap

import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class TrackDiscoveryResult(val name: String, val center: LatLng, val layouts: List<List<LatLng>>)

/** Android equivalent of iOS MKLocalSearch + TrackLayoutService (OpenStreetMap/Overpass). */
object TrackDiscoveryService {
    suspend fun nearbyTracks(center: LatLng, radiusMeters: Int = 16_093): Result<List<NearbyKnownTrack>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val query = "[out:json][timeout:25];way[\"highway\"=\"raceway\"][\"name\"](around:$radiusMeters,${center.latitude},${center.longitude});out body;>;out skel qt;"
                val elements = fetchElements(query)
                val nodes = mutableMapOf<Long, LatLng>()
                for (i in 0 until elements.length()) {
                    val item = elements.getJSONObject(i)
                    if (item.optString("type") == "node") nodes[item.getLong("id")] = LatLng(item.getDouble("lat"), item.getDouble("lon"))
                }
                val seen = mutableSetOf<String>()
                buildList {
                    for (i in 0 until elements.length()) {
                        val item = elements.getJSONObject(i)
                        if (item.optString("type") != "way") continue
                        val name = item.optJSONObject("tags")?.optString("name")?.takeIf(String::isNotBlank) ?: continue
                        if (!seen.add(name.lowercase())) continue
                        val ids = item.optJSONArray("nodes") ?: continue
                        val outline = (0 until ids.length()).mapNotNull { nodes[ids.getLong(it)] }
                        if (outline.size < 2) continue
                        val trackCenter = LatLng(outline.map { it.latitude }.average(), outline.map { it.longitude }.average())
                        val result = FloatArray(1)
                        Location.distanceBetween(center.latitude, center.longitude, trackCenter.latitude, trackCenter.longitude, result)
                        add(NearbyKnownTrack(
                            id = "osm-${item.getLong("id")}", name = name, venue = "", verificationStatus = "OpenStreetMap",
                            center = GeoPoint(trackCenter.latitude, trackCenter.longitude), distanceMiles = result[0] / 1609.344,
                            configurations = listOf(KnownTrackConfiguration(
                                "main", "Main Circuit", outline.map { GeoPoint(it.latitude, it.longitude) }, null,
                                isDefault = true, verificationStatus = "geometry_only"
                            ))
                        ))
                    }
                }.sortedBy { it.distanceMiles }
            }
        }

    suspend fun search(context: Context, query: String, nearby: LatLng?): Result<TrackDiscoveryResult> = withContext(Dispatchers.IO) {
        runCatching {
            val geocoder = Geocoder(context.applicationContext, Locale.getDefault())
            @Suppress("DEPRECATION")
            val result = geocoder.getFromLocationName(
                query, 1,
                nearby?.latitude?.minus(2.0) ?: -90.0,
                nearby?.longitude?.minus(2.0) ?: -180.0,
                nearby?.latitude?.plus(2.0) ?: 90.0,
                nearby?.longitude?.plus(2.0) ?: 180.0
            )?.firstOrNull()
                ?: error("Track not found. Try its full name and city.")
            val center = LatLng(result.latitude, result.longitude)
            val displayName = result.featureName?.takeIf { it.isNotBlank() } ?: query.trim()
            TrackDiscoveryResult(displayName, center, fetchLayouts(center, radiusMeters = 16_093))
        }
    }

    private fun fetchLayouts(center: LatLng, radiusMeters: Int): List<List<LatLng>> {
        val query = "[out:json][timeout:25];way[\"highway\"=\"raceway\"](around:$radiusMeters,${center.latitude},${center.longitude});out body;>;out skel qt;"
        return parseLayouts(fetchElements(query))
    }

    private fun fetchElements(query: String): org.json.JSONArray {
        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )
        endpoints.forEach { endpoint ->
            try {
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12_000; readTimeout = 30_000; requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("User-Agent", "PackRide-Android/1.0")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                }
                connection.outputStream.use { it.write("data=${URLEncoder.encode(query, "UTF-8")}".toByteArray()) }
                if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
                return JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONArray("elements")
            } catch (_: Exception) {
                // Public Overpass mirrors are occasionally busy; try the next one.
            }
        }
        throw IOException("Track layout service is temporarily busy. The 10-mile Firebase catalogue is still available; please try again shortly.")
    }

    private fun parseLayouts(elements: org.json.JSONArray): List<List<LatLng>> {
        val nodes = mutableMapOf<Long, LatLng>()
        val ways = mutableListOf<List<Long>>()
        for (i in 0 until elements.length()) {
            val item = elements.getJSONObject(i)
            when (item.optString("type")) {
                "node" -> nodes[item.getLong("id")] = LatLng(item.getDouble("lat"), item.getDouble("lon"))
                "way" -> item.optJSONArray("nodes")?.let { array -> ways += (0 until array.length()).map(array::getLong) }
            }
        }
        return ways.map { ids -> ids.mapNotNull(nodes::get) }.filter { it.size >= 2 }
    }
}
