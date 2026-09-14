package com.karthik.packride.lap

import android.content.Context
import android.location.Location
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

data class KnownTrackConfiguration(
    val id: String,
    val name: String,
    val outline: List<GeoPoint>,
    val timing: TrackTimingConfiguration?,
    val isDefault: Boolean = false,
    val verificationStatus: String = "unverified",
    val confirmationCount: Int = 0
)

data class NearbyKnownTrack(
    val id: String,
    val name: String,
    val venue: String,
    val verificationStatus: String,
    val center: GeoPoint,
    val distanceMiles: Double,
    val configurations: List<KnownTrackConfiguration>
) {
    val defaultConfiguration get() = configurations.firstOrNull { it.isDefault } ?: configurations.first()
}

/** Shared RTDB track catalogue reader with a last-known-good offline cache. */
object FirebaseTrackRepository {
    private const val CACHE_PREFS = "packride_track_catalogue"
    private const val CACHE_KEY = "tracks_json"

    suspend fun nearby(
        context: Context,
        latitude: Double,
        longitude: Double,
        radiusMiles: Double = 10.0
    ): Result<List<NearbyKnownTrack>> = runCatching {
        val root = fetchTracks().getOrElse { cachedMap(context) ?: throw it }
        cache(context, root)
        parse(root, latitude, longitude)
            .filter { it.distanceMiles <= radiusMiles }
            .sortedBy { it.distanceMiles }
    }

    suspend fun matching(context: Context, query: String, latitude: Double?, longitude: Double?): Result<List<NearbyKnownTrack>> = runCatching {
        val root = fetchTracks().getOrElse { cachedMap(context) ?: throw it }
        cache(context, root)
        val needle = normalize(query)
        if (needle.isBlank()) return@runCatching emptyList()
        parse(root, latitude ?: 0.0, longitude ?: 0.0)
            .mapNotNull { track -> matchScore(needle, track)?.let { score -> track to score } }
            .sortedWith(compareByDescending<Pair<NearbyKnownTrack, Int>> { it.second }
                .thenBy { it.first.distanceMiles })
            .map { it.first }
    }

    suspend fun confirmLayout(venueId: String, configurationId: String): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) {
                continuation.resume(Result.failure(IllegalStateException("Sign in to confirm a layout.")))
                return@suspendCancellableCoroutine
            }
            val ref = FirebaseDatabase.getInstance().reference
                .child("communityLayoutConfirmations").child(venueId).child(configurationId).child(uid)
            ref.get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) continuation.resume(Result.success(Unit))
                else ref.setValue(mapOf("confirmedAt" to com.google.firebase.database.ServerValue.TIMESTAMP, "source" to "community_layout_selection"))
                    .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
                    .addOnFailureListener { continuation.resume(Result.failure(it)) }
            }.addOnFailureListener { continuation.resume(Result.failure(it)) }
        }

    suspend fun submitCommunityLayout(
        venueName: String,
        layoutName: String,
        route: List<GeoPoint>,
        timing: TrackTimingConfiguration,
        completedLapCount: Int,
        timingConfidence: Double?
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null || completedLapCount < 2 || route.size < 20) {
            continuation.resume(Result.failure(IllegalStateException("Sign in and complete at least two valid laps before submitting.")))
            return@suspendCancellableCoroutine
        }
        val normalized = decimate(route, 240)
        val first = normalized.first(); val last = normalized.last()
        if (distanceMiles(first.latitude, first.longitude, last) * 1609.344 > 80) {
            continuation.resume(Result.failure(IllegalStateException("The recorded lap did not form a closed circuit.")))
            return@suspendCancellableCoroutine
        }
        fun pointMap(point: GeoPoint) = mapOf("latitude" to point.latitude, "longitude" to point.longitude)
        fun gateMap(gate: TimingGate) = mapOf("a" to pointMap(gate.a), "b" to pointMap(gate.b), "direction" to gate.direction.name.lowercase())
        val payload = mutableMapOf<String, Any?>(
            "venueName" to venueName.trim(), "layoutName" to layoutName.trim(),
            "center" to pointMap(GeoPoint(normalized.map { it.latitude }.average(), normalized.map { it.longitude }.average())),
            "centerline" to normalized.map(::pointMap), "startFinishGate" to gateMap(timing.startFinish),
            "sectorGates" to timing.sectors.map(::gateMap), "createdBy" to uid,
            "createdAt" to com.google.firebase.database.ServerValue.TIMESTAMP,
            "completedLapCount" to completedLapCount, "timingConfidence" to (timingConfidence ?: 0.0),
            "status" to "pending", "source" to "community_phone_gps", "schemaVersion" to 1
        )
        timing.finishGate?.let { payload["finishGate"] = gateMap(it) }
        timing.pitEntryGate?.let { payload["pitEntryGate"] = gateMap(it) }
        timing.pitExitGate?.let { payload["pitExitGate"] = gateMap(it) }
        FirebaseDatabase.getInstance().reference.child("communityLayoutSubmissions").push().setValue(payload)
            .addOnSuccessListener { continuation.resume(Result.success(Unit)) }
            .addOnFailureListener { continuation.resume(Result.failure(it)) }
    }

    suspend fun saveCustomTrack(
        name: String,
        center: GeoPoint,
        configuration: KnownTrackConfiguration
    ): Result<String> = suspendCancellableCoroutine { continuation ->
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            continuation.resume(Result.failure(IllegalStateException("Sign in to share a track.")))
            return@suspendCancellableCoroutine
        }
        val ref = FirebaseDatabase.getInstance().reference.child("tracks").push()
        val trackId = ref.key ?: ""
        fun pointMap(point: GeoPoint) = mapOf("latitude" to point.latitude, "longitude" to point.longitude)
        fun gateMap(gate: TimingGate) = mapOf("a" to pointMap(gate.a), "b" to pointMap(gate.b), "direction" to gate.direction.name.lowercase())
        val timing = configuration.timing ?: run {
            continuation.resume(Result.failure(IllegalArgumentException("A timing line is required.")))
            return@suspendCancellableCoroutine
        }
        val value = mapOf(
            "name" to name.trim(), "venue" to name.trim(), "center" to pointMap(center),
            "source" to "packride_android", "verificationStatus" to "pending", "createdBy" to uid,
            "configurations" to mapOf(configuration.id to mapOf(
                "name" to configuration.name,
                "outline" to configuration.outline.map(::pointMap),
                "startFinishGate" to gateMap(timing.startFinish),
                "sectorGates" to timing.sectors.map(::gateMap),
                "finishGate" to timing.finishGate?.let(::gateMap),
                "pitEntryGate" to timing.pitEntryGate?.let(::gateMap),
                "pitExitGate" to timing.pitExitGate?.let(::gateMap)
            ))
        )
        ref.setValue(value).addOnSuccessListener { continuation.resume(Result.success(trackId)) }
            .addOnFailureListener { continuation.resume(Result.failure(it)) }
    }

    private suspend fun fetchTracks(): Result<Map<String, Any?>> = suspendCancellableCoroutine { continuation ->
        FirebaseDatabase.getInstance().reference.child("tracks").get()
            .addOnSuccessListener { snapshot ->
                val type = object : GenericTypeIndicator<Map<String, Any?>>() {}
                continuation.resume(Result.success(snapshot.getValue(type).orEmpty()))
            }
            .addOnFailureListener { continuation.resume(Result.failure(it)) }
    }

    private fun parse(root: Map<String, Any?>, latitude: Double, longitude: Double): List<NearbyKnownTrack> =
        root.mapNotNull { (id, raw) ->
            val data = raw as? Map<*, *> ?: return@mapNotNull null
            val center = point(data["center"] as? Map<*, *> ?: data) ?: return@mapNotNull null
            val configsRaw = data["configurations"] as? Map<*, *> ?: return@mapNotNull null
            val configurations = configsRaw.mapNotNull config@{ (configId, value) ->
                val config = value as? Map<*, *> ?: return@config null
                val startGate = gate(config["startFinishGate"] as? Map<*, *>, "start-finish")
                val sectors = when (val rawSectors = config["sectorGates"]) {
                    is List<*> -> rawSectors.mapIndexedNotNull { index, item -> gate(item as? Map<*, *>, "sector-$index") }
                    is Map<*, *> -> rawSectors.entries.mapNotNull { gate(it.value as? Map<*, *>, it.key.toString()) }
                    else -> emptyList()
                }
                KnownTrackConfiguration(
                    id = configId.toString(),
                    name = config["name"] as? String ?: "Main Circuit",
                    outline = points(config["centerline"]).ifEmpty { points(config["outline"]) },
                    timing = startGate?.let { gate -> TrackTimingConfiguration(
                        startFinish = gate,
                        sectors = sectors,
                        finishGate = gate(config["finishGate"] as? Map<*, *>, "finish"),
                        pitEntryGate = gate(config["pitEntryGate"] as? Map<*, *>, "pit-entry"),
                        pitExitGate = gate(config["pitExitGate"] as? Map<*, *>, "pit-exit")
                    ) },
                    isDefault = config["isDefault"] as? Boolean ?: false,
                    verificationStatus = config["verificationStatus"] as? String ?: data["verificationStatus"] as? String ?: "unverified",
                    confirmationCount = (config["confirmationCount"] as? Number)?.toInt() ?: 0
                )
            }.filter { it.timing != null || usableClosedCircuit(it.outline) }
                .sortedWith(compareByDescending<KnownTrackConfiguration> { it.isDefault }
                    .thenByDescending { it.confirmationCount }.thenBy { it.name.lowercase() })
            if (configurations.isEmpty()) return@mapNotNull null
            NearbyKnownTrack(
                id = id,
                name = data["name"] as? String ?: return@mapNotNull null,
                venue = data["venue"] as? String ?: "",
                verificationStatus = data["verificationStatus"] as? String ?: "unverified",
                center = center,
                distanceMiles = distanceMiles(latitude, longitude, center),
                configurations = configurations
            )
        }

    private fun gate(data: Map<*, *>?, id: String): TimingGate? {
        data ?: return null
        val a = point(data["a"] as? Map<*, *>) ?: return null
        val b = point(data["b"] as? Map<*, *>) ?: return null
        val direction = when ((data["direction"] as? String)?.lowercase()) {
            "negative_to_positive", "negativetopositive", "forward" -> GateDirection.NEGATIVE_TO_POSITIVE
            else -> GateDirection.POSITIVE_TO_NEGATIVE
        }
        return TimingGate(id, a, b, direction)
    }

    private fun usableClosedCircuit(points: List<GeoPoint>): Boolean {
        if (points.size < 12) return false
        if (distanceMeters(points.first(), points.last()) > 60.0) return false
        val length = points.zipWithNext().sumOf { (a, b) -> distanceMeters(a, b) }
        return length >= 100.0
    }

    private fun point(data: Map<*, *>?): GeoPoint? {
        data ?: return null
        val lat = (data["latitude"] ?: data["lat"]) as? Number ?: return null
        val lng = (data["longitude"] ?: data["lng"] ?: data["lon"]) as? Number ?: return null
        return GeoPoint(lat.toDouble(), lng.toDouble())
    }

    private fun points(raw: Any?): List<GeoPoint> = when (raw) {
        is List<*> -> raw.mapNotNull { point(it as? Map<*, *>) }
        is Map<*, *> -> raw.values.mapNotNull { point(it as? Map<*, *>) }
        else -> emptyList()
    }

    private fun distanceMiles(lat: Double, lng: Double, point: GeoPoint): Double {
        val result = FloatArray(1)
        Location.distanceBetween(lat, lng, point.latitude, point.longitude, result)
        return result[0] / 1609.344
    }

    private fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val result = FloatArray(1)
        Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result)
        return result[0].toDouble()
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

    /**
     * Name matching must never treat a one-letter catalogue entry as a match
     * merely because that letter occurs in the rider's query (for example the
     * legacy track named "H" winning a search for "Bushnell").
     */
    private fun matchScore(needle: String, track: NearbyKnownTrack): Int? {
        val candidates = listOf(track.name, track.venue).map(::normalize).filter { it.isNotBlank() }
        return candidates.maxOfOrNull { candidate ->
            when {
                candidate == needle -> 1_000
                candidate.startsWith("$needle ") -> 900
                candidate.contains(" $needle ") || candidate.endsWith(" $needle") -> 850
                candidate.contains(needle) && needle.length >= 4 -> 800
                needle.contains(candidate) && candidate.length >= 4 -> 700
                else -> -1
            }
        }?.takeIf { it >= 0 }
    }

    private fun decimate(points: List<GeoPoint>, maximum: Int): List<GeoPoint> {
        if (points.size < 3) return points
        val smoothed = buildList {
            add(points.first())
            for (index in 1 until points.lastIndex) {
                val before = points[index - 1]
                val point = points[index]
                val after = points[index + 1]
                add(GeoPoint(
                    (before.latitude + point.latitude + after.latitude) / 3.0,
                    (before.longitude + point.longitude + after.longitude) / 3.0
                ))
            }
            add(points.last())
        }
        val stride = kotlin.math.ceil(smoothed.size.toDouble() / maximum).toInt().coerceAtLeast(1)
        val result = smoothed.filterIndexed { index, _ -> index % stride == 0 }.toMutableList()
        if (result.last() != smoothed.last()) result += smoothed.last()
        return result
    }

    private fun cache(context: Context, root: Map<String, Any?>) {
        context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).edit()
            .putString(CACHE_KEY, JSONObject(root).toString()).apply()
    }

    private fun cachedMap(context: Context): Map<String, Any?>? {
        val raw = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).getString(CACHE_KEY, null) ?: return null
        return jsonObjectToMap(JSONObject(raw))
    }

    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> = json.keys().asSequence().associateWith { key ->
        when (val value = json.get(key)) {
            is JSONObject -> jsonObjectToMap(value)
            is JSONArray -> (0 until value.length()).map { index ->
                when (val item = value.get(index)) { is JSONObject -> jsonObjectToMap(item); else -> item }
            }
            JSONObject.NULL -> null
            else -> value
        }
    }
}
