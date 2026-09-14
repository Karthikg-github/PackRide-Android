package com.karthik.packride.waypoints

import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// MARK: - Turn-by-turn step models (Aug 30, 2026 — added for TurnByTurnNavigator;
// same file as the plain-polyline route() below since both hit the same
// Directions API endpoint, just parsing more of the response).

/**
 * One spoken/displayed maneuver — plain-text instruction (HTML stripped) +
 * where it ends. `maneuver` is the Directions API's own maneuver type (e.g.
 * "turn-left", "turn-right", "uturn-left", "merge", "roundabout-left",
 * "fork-right") when that step has one — many "continue straight" steps
 * don't, in which case it's blank and the UI falls back to guessing from
 * the instruction text.
 */
data class NavStep(
    val instruction: String,
    val distanceMeters: Double,
    val endLat: Double,
    val endLng: Double,
    val maneuver: String = ""
)

data class LegResult(
    val polyline: List<LatLng>,
    val steps: List<NavStep>,
    val distanceMeters: Double,
    val durationSeconds: Double
)

data class RouteResult(val legs: List<LegResult>)

/**
 * Real routed directions, leg by leg (current/start location -> stop 1 ->
 * stop 2 -> ... -> destination), via the Directions API REST endpoint —
 * plain HTTP + JSON, same style as WeatherManager.kt, no extra SDK
 * dependency. Kotlin port of iOS RouteMapView.drawRoute's per-leg
 * MKDirections.calculate loop (falls back to a straight line between the
 * two points of a leg if that leg's request fails, same as iOS).
 */
object DirectionsService {

    /** Reads the same key wired into AndroidManifest.xml's Maps SDK meta-data. */
    fun apiKey(context: Context): String = runCatching {
        val ai = context.packageManager.getApplicationInfo(
            context.packageName, PackageManager.GET_META_DATA
        )
        ai.metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
    }.getOrDefault("")

    /** One leg's routed polyline (empty list if the request fails and no fallback wanted). */
    private suspend fun fetchLeg(context: Context, origin: LatLng, destination: LatLng): List<LatLng> =
        withContext(Dispatchers.IO) {
            val key = apiKey(context)
            if (key.isBlank()) return@withContext emptyList()
            runCatching {
                val url = URL(
                    "https://maps.googleapis.com/maps/api/directions/json" +
                        "?origin=${origin.latitude},${origin.longitude}" +
                        "&destination=${destination.latitude},${destination.longitude}" +
                        "&mode=driving&key=$key"
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                }
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                val routes = json.optJSONArray("routes")
                if (routes == null || routes.length() == 0) return@runCatching emptyList()
                val overview = routes.getJSONObject(0)
                    .getJSONObject("overview_polyline")
                    .getString("points")
                decodePolyline(overview)
            }.getOrDefault(emptyList())
        }

    /** Same per-leg HTTP call as fetchLeg, but keeps the turn-by-turn steps and leg totals too. */
    private suspend fun fetchLegWithSteps(context: Context, origin: LatLng, destination: LatLng): LegResult =
        withContext(Dispatchers.IO) {
            val fallback = LegResult(
                polyline = emptyList(), steps = emptyList(),
                distanceMeters = 0.0, durationSeconds = 0.0
            )
            val key = apiKey(context)
            if (key.isBlank()) return@withContext fallback
            runCatching {
                val url = URL(
                    "https://maps.googleapis.com/maps/api/directions/json" +
                        "?origin=${origin.latitude},${origin.longitude}" +
                        "&destination=${destination.latitude},${destination.longitude}" +
                        "&mode=driving&key=$key"
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                }
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                val routes = json.optJSONArray("routes")
                if (routes == null || routes.length() == 0) return@runCatching fallback
                val route = routes.getJSONObject(0)
                val overview = route.getJSONObject("overview_polyline").getString("points")
                val legsArr = route.getJSONArray("legs")
                val steps = mutableListOf<NavStep>()
                var legDistance = 0.0
                var legDuration = 0.0
                for (li in 0 until legsArr.length()) {
                    val leg = legsArr.getJSONObject(li)
                    legDistance += leg.optJSONObject("distance")?.optDouble("value", 0.0) ?: 0.0
                    legDuration += leg.optJSONObject("duration")?.optDouble("value", 0.0) ?: 0.0
                    val stepsArr = leg.optJSONArray("steps") ?: continue
                    for (si in 0 until stepsArr.length()) {
                        val s = stepsArr.getJSONObject(si)
                        val html = s.optString("html_instructions", "")
                        val plain = html.replace(Regex("<[^>]*>"), "").trim()
                        if (plain.isEmpty()) continue
                        val end = s.optJSONObject("end_location") ?: continue
                        steps.add(
                            NavStep(
                                instruction = plain,
                                distanceMeters = s.optJSONObject("distance")?.optDouble("value", 0.0) ?: 0.0,
                                endLat = end.getDouble("lat"),
                                endLng = end.getDouble("lng"),
                                maneuver = s.optString("maneuver", "")
                            )
                        )
                    }
                }
                if (steps.isEmpty()) return@runCatching fallback
                LegResult(
                    polyline = decodePolyline(overview),
                    steps = steps,
                    distanceMeters = legDistance,
                    durationSeconds = legDuration
                )
            }.getOrDefault(fallback)
        }

    /**
     * Turn-by-turn version of route() above — same per-leg concatenation and
     * straight-line fallback, but keeps each leg's steps/distance/duration
     * for TurnByTurnNavigator instead of collapsing everything to points.
     */
    suspend fun routeWithSteps(context: Context, points: List<LatLng>): RouteResult {
        if (points.size < 2) return RouteResult(emptyList())
        val legs = mutableListOf<LegResult>()
        for (i in 0 until points.size - 1) {
            val leg = fetchLegWithSteps(context, points[i], points[i + 1])
            if (leg.polyline.isEmpty() || leg.steps.isEmpty()) return RouteResult(emptyList())
            legs.add(leg)
        }
        return RouteResult(legs)
    }

    /**
     * Routes through every leg in order (points.size - 1 legs total),
     * Returns no route if any leg fails. Drawing a straight fallback is
     * misleading for road navigation (and was reported as a defect), so the
     * planning screen now shows an actionable error instead.
     */
    suspend fun route(context: Context, points: List<LatLng>): List<LatLng> {
        if (points.size < 2) return emptyList()
        val full = mutableListOf<LatLng>()
        for (i in 0 until points.size - 1) {
            val leg = fetchLeg(context, points[i], points[i + 1])
            if (leg.isEmpty()) return emptyList()
            full.addAll(leg)
        }
        return full
    }

    // Google's standard encoded-polyline algorithm.
    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = mutableListOf<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0
        while (index < encoded.length) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLat = if (result and 1 != 0) (result shr 1).inv() else (result shr 1)
            lat += dLat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLng = if (result and 1 != 0) (result shr 1).inv() else (result shr 1)
            lng += dLng

            poly.add(LatLng(lat / 1E5, lng / 1E5))
        }
        return poly
    }
}
