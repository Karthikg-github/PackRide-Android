package com.karthik.packride.ride

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class RideRecord(
    val id: String = UUID.randomUUID().toString(),
    val dateMs: Long = System.currentTimeMillis(),
    val distanceMiles: Double,
    val maxSpeedMph: Double,
    val durationSeconds: Int,
    val rideCode: String = "SOLO",
    val gpxFileName: String? = null,
    /** Firebase Storage download URL after cloud sync (iOS gpxURL parity). */
    val gpxURL: String? = null,
    val maxLeanAngle: Double = 0.0,
    val isGroupRide: Boolean = false,
    val trackName: String = "",
    val lapTimes: List<Double> = emptyList(),
    /** Absolute Unix epoch milliseconds for each completed lap's start crossing. */
    val lapStartTimestamps: List<Long> = emptyList(),
    val trackScore: Int? = null,
    val consistencyScore: Int? = null,
    val smoothnessScore: Int? = null,
    /** Which Garage bike this ride was logged against (Aug 30, 2026, iOS parity). */
    val bikeId: String? = null,
    /** True if this device created/led the group ride (Aug 30, 2026, iOS parity — powers the Led/Joined filter). */
    val isLeader: Boolean = false
) {
    val durationFormatted: String
        get() {
            val h = durationSeconds / 3600
            val m = (durationSeconds % 3600) / 60
            val s = durationSeconds % 60
            return "%02d:%02d:%02d".format(h, m, s)
        }

    val hasRouteData: Boolean
        get() = !gpxFileName.isNullOrBlank() || !gpxURL.isNullOrBlank()

    val isTrackSession: Boolean
        get() = rideCode == "TRACK" || trackName.isNotBlank() || lapTimes.isNotEmpty()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("dateMs", dateMs)
        put("distanceMiles", distanceMiles)
        put("maxSpeedMph", maxSpeedMph)
        put("durationSeconds", durationSeconds)
        put("rideCode", rideCode)
        put("gpxFileName", gpxFileName ?: JSONObject.NULL)
        put("gpxURL", gpxURL ?: JSONObject.NULL)
        put("maxLeanAngle", maxLeanAngle)
        put("isGroupRide", isGroupRide)
        put("trackName", trackName)
        put("lapTimes", JSONArray(lapTimes))
        put("lapStartTimestamps", JSONArray(lapStartTimestamps))
        put("trackScore", trackScore ?: JSONObject.NULL)
        put("consistencyScore", consistencyScore ?: JSONObject.NULL)
        put("smoothnessScore", smoothnessScore ?: JSONObject.NULL)
        put("bikeId", bikeId ?: JSONObject.NULL)
        put("isLeader", isLeader)
    }

    companion object {
        fun fromJson(o: JSONObject): RideRecord {
            val lapsArr = o.optJSONArray("lapTimes")
            val laps = if (lapsArr != null) {
                (0 until lapsArr.length()).map { lapsArr.getDouble(it) }
            } else emptyList()
            val startsArr = o.optJSONArray("lapStartTimestamps")
            val starts = if (startsArr != null) (0 until startsArr.length()).map { startsArr.getLong(it) } else emptyList()
            return RideRecord(
                id = o.getString("id"),
                dateMs = o.getLong("dateMs"),
                distanceMiles = o.getDouble("distanceMiles"),
                maxSpeedMph = o.getDouble("maxSpeedMph"),
                durationSeconds = o.getInt("durationSeconds"),
                rideCode = o.optString("rideCode", "SOLO"),
                gpxFileName = if (o.isNull("gpxFileName")) null else o.optString("gpxFileName"),
                gpxURL = if (o.isNull("gpxURL")) null else o.optString("gpxURL"),
                maxLeanAngle = o.optDouble("maxLeanAngle", 0.0),
                isGroupRide = o.optBoolean("isGroupRide", false),
                trackName = o.optString("trackName", ""),
                lapTimes = laps,
                lapStartTimestamps = starts,
                trackScore = if (o.isNull("trackScore")) null else o.optInt("trackScore"),
                consistencyScore = if (o.isNull("consistencyScore")) null else o.optInt("consistencyScore"),
                smoothnessScore = if (o.isNull("smoothnessScore")) null else o.optInt("smoothnessScore"),
                bikeId = if (o.isNull("bikeId")) null else o.optString("bikeId"),
                isLeader = o.optBoolean("isLeader", false)
            )
        }

        fun listFromJson(raw: String?): List<RideRecord> {
            if (raw.isNullOrBlank()) return emptyList()
            val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
            // One interrupted/legacy entry must not make the entire ride
            // history unreadable at app launch. Keep every valid record and
            // let cloud reconciliation restore anything that was skipped.
            return (0 until arr.length()).mapNotNull { index ->
                runCatching { fromJson(arr.getJSONObject(index)) }.getOrNull()
            }
        }

        fun listToJson(list: List<RideRecord>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }
    }
}
