package com.karthik.packride.ride

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.karthik.packride.gpx.GPXStorage
import com.karthik.packride.gpx.GpxCloudUpload
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Local ride history — Android counterpart to iOS RideHistoryManager.
 *
 * Aug 30, 2026 — Ride History parity pass: previously local-only
 * (SharedPreferences), so a reinstall or a sign-in on a different device
 * silently lost every past ride even though the account itself was
 * untouched — the exact bug already fixed for Garage. Now mirrors
 * GarageManager's shape: ride METADATA (everything except the local-only
 * gpxFileName) syncs to users/{uid}/rideHistory/{id}; the GPX bytes
 * themselves already went to Firebase Storage separately (see
 * gpx/GpxCloudUpload.kt, wired in at ride-recording time) and their
 * download URL rides along as gpxURL. A ride restored from the cloud has no
 * local GPX file yet — resolveGpx() downloads and caches it on first use,
 * same "only pay the download cost when something actually asks" shape as
 * iOS's resolveGPXPath.
 *
 * Aug 31, 2026 — cross-platform field-name/type parity fix: this class's
 * own Kotlin properties keep their descriptive local names (dateMs,
 * distanceMiles, maxSpeedMph, durationSeconds), but the map pushed to/read
 * from users/{uid}/rideHistory/{id} now uses iOS's actual wire field names
 * and value shapes exactly — "date"/"distance"/"maxSpeed"/"duration" — so a
 * ride recorded on one platform reads correctly on the other. Previously
 * this pushed "dateMs"/"distanceMiles"/"maxSpeedMph"/"durationSeconds",
 * which iOS's RideRecord (Codable, keys id/date/distance/maxSpeed/duration/
 * ...) never matched — a ride recorded on either platform silently failed
 * to decode on the other (JSONDecoder throws on missing keys, so
 * syncFromCloud's try? just dropped the ride). Two shapes needed real
 * conversion, not just a key rename:
 *   - "date": iOS's RideRecord has no custom JSONEncoder.dateEncodingStrategy
 *     (see RideHistoryView.swift), so Swift's default Codable Date
 *     conformance applies — a Date encodes as a single Double via
 *     Date.timeIntervalSinceReferenceDate, i.e. seconds since 2001-01-01,
 *     NOT Unix epoch seconds/millis. iosReferenceDateSeconds()/
 *     msFromIosReferenceDateSeconds() convert dateMs (Unix millis) through
 *     that offset both ways.
 *   - "duration": iOS's RideRecord.duration is a formatted "HH:mm:ss"
 *     String (ActiveSoloRideView.formatDuration), not a numeric seconds
 *     count. RideRecord.durationFormatted already produces that exact
 *     format for writes; parseDurationSeconds() reverses it for reads.
 * "distance"/distanceMiles and "maxSpeed"/maxSpeedMph carry over as plain
 * value renames — iOS's RideHistoryView.swift displays "%.1f mi" / "%.0f
 * mph", so both platforms already agree on miles/mph as the unit, only the
 * key name differed.
 *
 * No dual-read fallback for the old dateMs/distanceMiles/maxSpeedMph/
 * durationSeconds keys: Android has no live Play Store release yet
 * (per android-build-plan.md/conversion-plan.md, Android is still a
 * first-time, not-yet-submitted Play Console listing), so there is no real
 * user data under the old keys to orphan — a clean cutover is simplest.
 */
class RideHistoryManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("packride_rides", Context.MODE_PRIVATE)

    private val _rides = MutableStateFlow(load())
    val rides: StateFlow<List<RideRecord>> = _rides.asStateFlow()
    private val gpxUploadsInFlight = mutableSetOf<String>()
    private val gpxLookupsInFlight = mutableSetOf<String>()
    private val pendingCloudWrites = mutableSetOf<String>()
    private var cloudHistoryRef: com.google.firebase.database.DatabaseReference? = null
    private var cloudHistoryListener: ValueEventListener? = null

    init {
        // A ride completed offline must survive the first cached/empty cloud
        // snapshot and be uploaded when connectivity returns.
        pendingCloudWrites.addAll(_rides.value.filter { it.gpxURL.isNullOrBlank() && !it.gpxFileName.isNullOrBlank() }.map { it.id })
        syncFromCloud()
        retryPendingGpxUploads()
    }

    fun record(ride: RideRecord) {
        pendingCloudWrites.add(ride.id)
        persist(listOf(ride) + _rides.value)
        pushToCloud(ride)
    }

    fun update(ride: RideRecord) {
        persist(_rides.value.map { if (it.id == ride.id) ride else it })
        pushToCloud(ride)
    }

    fun setGpxUrl(rideId: String, url: String) {
        persist(_rides.value.map {
            if (it.id == rideId) it.copy(gpxURL = url) else it
        })
        _rides.value.firstOrNull { it.id == rideId }?.let { pushToCloud(it) }
    }

    fun setTrackScores(rideId: String, trackScore: Int, consistencyScore: Int, smoothnessScore: Int) {
        persist(_rides.value.map {
            if (it.id == rideId) it.copy(
                trackScore = trackScore,
                consistencyScore = consistencyScore,
                smoothnessScore = smoothnessScore
            ) else it
        })
        _rides.value.firstOrNull { it.id == rideId }?.let { pushToCloud(it) }
    }

    fun delete(id: String) {
        pendingCloudWrites.remove(id)
        _rides.value.firstOrNull { it.id == id }?.gpxFileName?.let { GPXStorage.remove(appContext.filesDir, it) }
        persist(_rides.value.filterNot { it.id == id })
        deleteFromCloud(id)
    }

    fun reload() {
        _rides.value = load()
        retryPendingGpxUploads()
    }

    /**
     * Firebase Storage uploads are not queued durably while the phone is
     * offline. A completed ride is already safe in local history, so retry
     * any local GPX that still lacks a cloud URL whenever history starts or
     * reloads. This closes the "ride ended offline, route never synced"
     * failure without making the rider repeat or manually export the ride.
     */
    fun retryPendingGpxUploads() {
        _rides.value
            .filter { it.gpxURL.isNullOrBlank() && !it.gpxFileName.isNullOrBlank() }
            .forEach { ride ->
                if (!gpxUploadsInFlight.add(ride.id)) return@forEach
                val filename = ride.gpxFileName ?: return@forEach
                val file = File(GPXStorage.ridesDirectory(appContext.filesDir), filename)
                if (!file.isFile) {
                    gpxUploadsInFlight.remove(ride.id)
                    return@forEach
                }
                GpxCloudUpload.upload(file, ride.id) { result ->
                    gpxUploadsInFlight.remove(ride.id)
                    result.onSuccess { url -> setGpxUrl(ride.id, url) }
                }
            }
    }

    /**
     * Ensures this ride's GPX exists as a local file, downloading it from
     * gpxURL first if it isn't cached on this install yet (cloud-restored
     * ride). Returns the local filename, or null if there's no route data
     * at all. Does real network I/O when a download is needed — call from a
     * coroutine, not the main thread directly.
     */
    suspend fun resolveGpx(ride: RideRecord): String? {
        val existing = ride.gpxFileName
        if (!existing.isNullOrBlank() && GPXStorage.exists(appContext.filesDir, existing)) return existing
        // Older iOS recordings can have their GPX at the shared deterministic
        // Storage path even when the second RTDB write that attaches gpxURL
        // never completed. Do not hide Replay based solely on that metadata.
        // Resolve the Storage object on demand, then repair the shared record.
        val url = ride.gpxURL?.takeIf { it.isNotBlank() }
            ?: findAndRepairCloudGpxUrl(ride.id)
            ?: return null
        val filename = withContext(Dispatchers.IO) {
            GPXStorage.downloadAndSave(appContext.filesDir, ride.id, url)
        } ?: return null
        persist(_rides.value.map { if (it.id == ride.id) it.copy(gpxFileName = filename) else it })
        return filename
    }

    private suspend fun findAndRepairCloudGpxUrl(rideId: String): String? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val url = suspendCoroutine<String?> { continuation ->
            FirebaseStorage.getInstance().reference
                .child("users/$uid/rides/$rideId.gpx")
                .downloadUrl
                .addOnSuccessListener { continuation.resume(it.toString()) }
                .addOnFailureListener { continuation.resume(null) }
        } ?: return null

        persist(_rides.value.map { if (it.id == rideId) it.copy(gpxURL = url) else it })
        FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("rideHistory").child(rideId)
            .child("gpxURL").setValue(url)
        return url
    }

    private fun persist(list: List<RideRecord>) {
        _rides.value = list
        prefs.edit().putString(KEY, RideRecord.listToJson(list)).apply()
    }

    private fun load(): List<RideRecord> =
        RideRecord.listFromJson(prefs.getString(KEY, null))

    private fun pushToCloud(ride: RideRecord) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        // gpxFileName is a filename inside THIS install's local files dir —
        // meaningless (and potentially colliding) on another install, so the
        // cloud copy never carries it, matching iOS's syncRideToCloud. Stored
        // as a field map (not a JSON string blob) — same shape as every
        // other Firebase record in this app (Garage, help requests, etc.).
        FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("rideHistory").child(ride.id)
            .setValue(rideToMap(ride))
            .addOnSuccessListener { pendingCloudWrites.remove(ride.id) }
    }

    // Field names below match iOS's RideRecord wire format exactly (see
    // RideHistoryView.swift's syncRideToCloud/CodingKeys) — iOS is the
    // original, already-shipped schema, so Android conforms to it.
    private fun rideToMap(ride: RideRecord): Map<String, Any?> = mapOf(
        "id" to ride.id,
        "date" to iosReferenceDateSeconds(ride.dateMs),
        "distance" to ride.distanceMiles,
        "maxSpeed" to ride.maxSpeedMph,
        "duration" to ride.durationFormatted,
        "rideCode" to ride.rideCode,
        "gpxURL" to ride.gpxURL,
        "maxLeanAngle" to ride.maxLeanAngle,
        "isGroupRide" to ride.isGroupRide,
        "trackName" to ride.trackName,
        "lapTimes" to ride.lapTimes,
        "lapStartTimestamps" to ride.lapStartTimestamps,
        "trackScore" to ride.trackScore,
        "consistencyScore" to ride.consistencyScore,
        "smoothnessScore" to ride.smoothnessScore,
        "bikeId" to ride.bikeId,
        "isLeader" to ride.isLeader
    )

    private fun rideFromMap(id: String, data: Map<*, *>): RideRecord {
        val lapsRaw = data["lapTimes"] as? List<*> ?: emptyList<Any?>()
        val lapStartsRaw = data["lapStartTimestamps"] as? List<*> ?: emptyList<Any?>()
        val dateValue = data["date"] as? Number
        val legacyDateMs = data["dateMs"] as? Number
        val duration = when (val raw = data["duration"]) {
            is String -> parseDurationSeconds(raw)
            is Number -> raw.toInt()
            else -> (data["durationSeconds"] as? Number)?.toInt() ?: 0
        }
        return RideRecord(
            id = id,
            dateMs = dateValue?.let { msFromIosReferenceDateSeconds(it.toDouble()) }
                ?: legacyDateMs?.toLong() ?: System.currentTimeMillis(),
            distanceMiles = ((data["distance"] ?: data["distanceMiles"]) as? Number)?.toDouble() ?: 0.0,
            maxSpeedMph = ((data["maxSpeed"] ?: data["maxSpeedMph"]) as? Number)?.toDouble() ?: 0.0,
            durationSeconds = duration,
            rideCode = data["rideCode"] as? String ?: "SOLO",
            gpxFileName = null,
            gpxURL = (data["gpxURL"] ?: data["gpxUrl"] ?: data["routeURL"] ?: data["routeUrl"]) as? String,
            maxLeanAngle = (data["maxLeanAngle"] as? Number)?.toDouble() ?: 0.0,
            isGroupRide = data["isGroupRide"] as? Boolean ?: false,
            trackName = data["trackName"] as? String ?: "",
            lapTimes = lapsRaw.mapNotNull { (it as? Number)?.toDouble() },
            lapStartTimestamps = lapStartsRaw.mapNotNull { (it as? Number)?.toLong() },
            trackScore = (data["trackScore"] as? Number)?.toInt(),
            consistencyScore = (data["consistencyScore"] as? Number)?.toInt(),
            smoothnessScore = (data["smoothnessScore"] as? Number)?.toInt(),
            bikeId = data["bikeId"] as? String,
            isLeader = data["isLeader"] as? Boolean ?: false
        )
    }

    private fun deleteFromCloud(id: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("rideHistory").child(id).removeValue()
        FirebaseStorage.getInstance().reference.child("users/$uid/rides/$id.gpx").delete()
    }

    /** Re-runnable account restore; screens call this on entry so a transient
     * auth/network miss during construction never leaves history stale. */
    fun syncFromCloud() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        cloudHistoryListener?.let { listener -> cloudHistoryRef?.removeEventListener(listener) }
        val ref = FirebaseDatabase.getInstance().reference.child("users").child(uid).child("rideHistory")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d(TAG, "syncFromCloud: got ${snapshot.childrenCount} remote rides for uid=$uid")
                val localById = _rides.value.associateBy { it.id }
                // Firebase is canonical. This intentionally drops a cached
                // Android ride that was deleted on iOS. The only exception is
                // a newly recorded/offline ride whose metadata write has not
                // succeeded yet.
                val mergedById = localById.filterKeys { it in pendingCloudWrites }.toMutableMap()
                var decodeFailures = 0
                for (child in snapshot.children) {
                    val id = child.key ?: continue
                    val data = child.value as? Map<*, *> ?: continue
                    val remote = runCatching { rideFromMap(id, data) }
                        .onFailure { e -> Log.e(TAG, "syncFromCloud: failed to decode ride $id", e) }
                        .getOrNull()
                    if (remote == null) { decodeFailures++; continue }
                    val local = localById[id]
                    // Cloud metadata is canonical, while a GPX filename is
                    // meaningful only on this installation. Preserve that
                    // local cache pointer and a newer local URL when the
                    // first cloud snapshot still contains the pre-upload
                    // record.
                    mergedById[id] = remote.copy(
                        gpxFileName = local?.gpxFileName,
                        gpxURL = remote.gpxURL ?: local?.gpxURL
                    )
                }
                if (decodeFailures > 0) {
                    Log.w(TAG, "syncFromCloud: $decodeFailures remote ride(s) failed to decode — see errors above")
                }
                val merged = mergedById.values.sortedByDescending { it.dateMs }
                persist(merged)
                recoverMissingCloudGpx(uid, merged)
                retryPendingGpxUploads()
            }
            override fun onCancelled(error: DatabaseError) {
                // Sep 3, 2026 — Karthik-reported bug: rides recorded on iOS
                // weren't showing up in Android Ride History. This read had no
                // failure handling before, so a permission-denied or network
                // error here failed completely silently. Filter Logcat for
                // "PackRideSync" after opening Ride History to see whether
                // this is firing and why (a permission-denied here almost
                // always means the RTDB rules or the signed-in uid, not this
                // code).
                Log.e(TAG, "syncFromCloud: failed reading rideHistory for uid=$uid", error.toException())
            }
        }
        ref.addValueEventListener(listener)
        cloudHistoryRef = ref
        cloudHistoryListener = listener
    }

    /**
     * iOS writes ride metadata, uploads users/{uid}/rides/{rideId}.gpx, then
     * patches gpxURL in a second callback. If that final callback is lost,
     * the file still exists but older Android builds hid Replay. Probe the
     * deterministic shared Storage path and repair the metadata automatically.
     */
    private fun recoverMissingCloudGpx(uid: String, rides: List<RideRecord>) {
        rides.filter { it.gpxURL.isNullOrBlank() && it.gpxFileName.isNullOrBlank() }
            .forEach { ride ->
                if (!gpxLookupsInFlight.add(ride.id)) return@forEach
                FirebaseStorage.getInstance().reference
                    .child("users/$uid/rides/${ride.id}.gpx")
                    .downloadUrl
                    .addOnSuccessListener { url -> setGpxUrl(ride.id, url.toString()) }
                    .addOnCompleteListener { gpxLookupsInFlight.remove(ride.id) }
            }
    }

    companion object {
        private const val TAG = "PackRideSync"
        private const val KEY = "rideHistory"

        // Seconds between the Unix epoch (1970-01-01T00:00:00Z) and Apple's
        // Foundation "reference date" (2001-01-01T00:00:00Z). iOS encodes
        // RideRecord.date with Swift's default Codable Date behavior (no
        // custom dateEncodingStrategy anywhere in RideHistoryView.swift),
        // which serializes a Date as one raw Double via
        // Date.timeIntervalSinceReferenceDate — NOT Unix time. Every "date"
        // value under users/{uid}/rideHistory/{id} must be converted through
        // this offset or ride dates will be off by ~31 years on whichever
        // platform reads them.
        private const val IOS_REFERENCE_DATE_UNIX_OFFSET_SECONDS = 978307200L

        private fun iosReferenceDateSeconds(dateMs: Long): Double =
            (dateMs / 1000.0) - IOS_REFERENCE_DATE_UNIX_OFFSET_SECONDS

        private fun msFromIosReferenceDateSeconds(seconds: Double): Long =
            ((seconds + IOS_REFERENCE_DATE_UNIX_OFFSET_SECONDS) * 1000.0).toLong()

        // iOS's "duration" field is a formatted "HH:mm:ss" string (see
        // ActiveSoloRideView.formatDuration / RideRecord.durationFormatted),
        // not a numeric seconds count.
        private fun parseDurationSeconds(formatted: String): Int {
            val parts = formatted.split(":").mapNotNull { it.trim().toIntOrNull() }
            return when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 60 + parts[1]
                1 -> parts[0]
                else -> 0
            }
        }
    }
}
