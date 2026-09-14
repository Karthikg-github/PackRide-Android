package com.karthik.packride.waypoints

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// MARK: - Waypoint Model
// Kotlin port of iOS's Waypoint (WaypointsView.swift) — same field names/shape
// so GroupWaypointSync's JSON wire format is byte-compatible with what iOS
// already publishes to Firebase at rides/{code}/waypointsJSON.
enum class WaypointType(val iconLabel: String, val label: String, val searchQuery: String) {
    FUEL("Fuel", "Fuel Stop", "gas station"),
    FOOD("Food", "Food Break", "restaurant"),
    SCENIC("Scenic", "Scenic Spot", "scenic viewpoint"),
    REST("Rest", "Rest Stop", "rest stop"),
    MEETUP("Meetup", "Meet Up", "parking")
}

data class Waypoint(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var type: WaypointType = WaypointType.MEETUP,
    var latitude: Double,
    var longitude: Double,
    var note: String = "",
    var isCompleted: Boolean = false,
    var address: String = "",
    var isDestination: Boolean = false,
    // Aug 30, 2026 — marks the one entry (if any) that's a rider-picked
    // starting point override rather than a real stop/destination. Always
    // kept at index 0 by WaypointsManager.setStart, same as iOS.
    var isStartOverride: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("type", type.name.lowercase())
        .put("latitude", latitude)
        .put("longitude", longitude)
        .put("note", note)
        .put("isCompleted", isCompleted)
        .put("address", address)
        .put("isDestination", isDestination)
        .put("isStartOverride", isStartOverride)

    companion object {
        fun fromJson(o: JSONObject): Waypoint = Waypoint(
            id = o.getString("id"),
            name = o.getString("name"),
            type = runCatching { WaypointType.valueOf(o.optString("type", "meetup").uppercase()) }
                .getOrDefault(WaypointType.MEETUP),
            latitude = o.getDouble("latitude"),
            longitude = o.getDouble("longitude"),
            note = o.optString("note", ""),
            isCompleted = o.optBoolean("isCompleted", false),
            address = o.optString("address", ""),
            isDestination = o.optBoolean("isDestination", false),
            isStartOverride = o.optBoolean("isStartOverride", false)
        )
    }
}

fun List<Waypoint>.toJsonString(): String {
    val arr = JSONArray()
    forEach { arr.put(it.toJson()) }
    return arr.toString()
}

fun waypointsFromJsonString(jsonString: String): List<Waypoint> = runCatching {
    val arr = JSONArray(jsonString)
    (0 until arr.length()).map { Waypoint.fromJson(arr.getJSONObject(it)) }
}.getOrDefault(emptyList())

// MARK: - Waypoints Manager
// Kotlin port of iOS WaypointsManager. Local SharedPreferences copy (the
// leader's own editable draft) plus a one-way mirror to Firebase for group
// rides via GroupWaypointSync — see saveWaypoints() below and its iOS
// counterpart's comment for why the mirror exists (bug #9: without it, only
// the leader's own phone ever knew the plan).
class WaypointsManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("packride_waypoints", Context.MODE_PRIVATE)

    // Aug 30, 2026 — StateFlow (not a plain var) so WaypointsScreen recomposes
    // automatically on every mutation, matching this codebase's established
    // manager pattern (FriendsManager, RideInviteManager, GroupRideFirebase).
    private val _waypoints = MutableStateFlow<List<Waypoint>>(emptyList())
    val waypointsFlow: StateFlow<List<Waypoint>> = _waypoints.asStateFlow()
    var waypoints: List<Waypoint>
        get() = _waypoints.value
        private set(value) { _waypoints.value = value }

    var rideCode: String = ""
        private set

    private val storageKey: String
        get() = if (rideCode.isEmpty()) "waypoints_solo" else "waypoints_$rideCode"

    init {
        loadWaypoints()
    }

    // Intermediate stops, in route order (destination and any start override excluded).
    val stops: List<Waypoint> get() = waypoints.filter { !it.isDestination && !it.isStartOverride }

    val destination: Waypoint? get() = waypoints.firstOrNull { it.isDestination }

    // The rider-picked starting point, if set (null means "use my current location").
    val startOverride: Waypoint? get() = waypoints.firstOrNull { it.isStartOverride }

    // stops + destination, in route order — what gets drawn as numbered pins.
    // Excludes the start override, which the map renders separately.
    val routeableWaypoints: List<Waypoint> get() = waypoints.filterNot { it.isStartOverride }

    fun addStop(name: String, address: String, type: WaypointType, latitude: Double, longitude: Double) {
        val wp = Waypoint(name = name, type = type, latitude = latitude, longitude = longitude, address = address)
        val destIndex = waypoints.indexOfFirst { it.isDestination }
        waypoints = if (destIndex >= 0) {
            waypoints.toMutableList().apply { add(destIndex, wp) }
        } else {
            waypoints + wp
        }
        saveWaypoints()
    }

    fun setStart(name: String, address: String, latitude: Double, longitude: Double) {
        val wp = Waypoint(
            name = name, type = WaypointType.MEETUP, latitude = latitude, longitude = longitude,
            address = address, isStartOverride = true
        )
        waypoints = listOf(wp) + waypoints.filterNot { it.isStartOverride }
        saveWaypoints()
    }

    fun clearStart() {
        waypoints = waypoints.filterNot { it.isStartOverride }
        saveWaypoints()
    }

    fun setDestination(name: String, address: String, latitude: Double, longitude: Double) {
        val wp = Waypoint(
            name = name, type = WaypointType.MEETUP, latitude = latitude, longitude = longitude,
            address = address, isDestination = true
        )
        waypoints = waypoints.filterNot { it.isDestination } + wp
        saveWaypoints()
    }

    fun clearDestination() {
        waypoints = waypoints.filterNot { it.isDestination }
        saveWaypoints()
    }

    // Reorders stops[fromIndex] to beforeIndex (both indices into `stops`,
    // matching the simple up/down-button reorder UI — see WaypointsScreen).
    fun moveStop(fromIndex: Int, toIndex: Int) {
        val currentStops = stops.toMutableList()
        if (fromIndex !in currentStops.indices || toIndex !in currentStops.indices) return
        val item = currentStops.removeAt(fromIndex)
        currentStops.add(toIndex, item)
        waypoints = (startOverride?.let { listOf(it) } ?: emptyList()) +
            currentStops +
            (destination?.let { listOf(it) } ?: emptyList())
        saveWaypoints()
    }

    fun removeWaypoint(id: String) {
        waypoints = waypoints.filterNot { it.id == id }
        saveWaypoints()
    }

    fun addWaypoint(waypoint: Waypoint) {
        waypoints = if (waypoint.isDestination) {
            waypoints.filterNot { it.isDestination } + waypoint
        } else {
            val destIndex = waypoints.indexOfFirst { it.isDestination }
            if (destIndex >= 0) waypoints.toMutableList().apply { add(destIndex, waypoint) }
            else waypoints + waypoint
        }
        saveWaypoints()
    }

    fun saveWaypoints() {
        prefs.edit().putString(storageKey, waypoints.toJsonString()).apply()
        // Mirrors to Firebase for group rides — no-op for solo (rideCode empty).
        GroupWaypointSync.publish(rideCode, waypoints)
    }

    fun loadWaypoints() {
        val raw = prefs.getString(storageKey, null) ?: return
        waypoints = waypointsFromJsonString(raw)
    }

    fun switchRideCode(newCode: String) {
        rideCode = newCode
        waypoints = emptyList()
        loadWaypoints()
    }

    // Fully resets for a brand-new solo planning session — clears in-memory
    // waypoints AND wipes the persisted "waypoints_solo" entry, so the next
    // switchRideCode("")/loadWaypoints() doesn't silently resurrect the old route.
    fun clearWaypoints() {
        rideCode = ""
        waypoints = emptyList()
        prefs.edit().remove(storageKey).apply()
    }

    // Aug 30, 2026 — sets rideCode directly WITHOUT clearing/reloading
    // waypoints (unlike switchRideCode). Needed when promoting a
    // just-planned local route to a brand-new group code: switchRideCode
    // would wipe the in-memory plan and reload from the new code's (empty)
    // storage key before saveWaypoints() could ever persist/publish the
    // real route — exactly the iOS bug fixed earlier this session in
    // WaypointsView's "Group Ride" button (see swift-port-plan.md).
    fun bindToNewRideCode(code: String) {
        rideCode = code
    }
}
