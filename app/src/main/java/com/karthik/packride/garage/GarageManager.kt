package com.karthik.packride.garage

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Aug 30, 2026 — 1:1 port of iOS's MaintenanceItemType/MaintenanceItem/Bike/
 * BikeManager (GarageView.swift). Previous Android version was a stub: no
 * nickname/make/model split, no isActive concept, no maintenance interval
 * editing, no cloud sync, and totalMileage was just a locally-incremented
 * counter instead of baseline + actual logged ride distance.
 */
enum class MaintenanceItemType(val label: String, val defaultIntervalMiles: Double, val serializedValue: String) {
    OIL("Oil Change", 3000.0, "oil"),
    CHAIN("Chain", 500.0, "chain"),
    TIRES("Tires", 5000.0, "tires"),
    BRAKES("Brakes", 8000.0, "brakes"),
    VALVES("Valves", 15000.0, "valves");

    companion object {
        /**
         * Aug 31, 2026 -- iOS's Codable-derived raw values are the lowercase
         * case names ("oil", "chain", ...), not Kotlin's uppercase enum
         * constant `.name` ("OIL", "CHAIN", ...) that this file used to
         * serialize with. Firebase is a shared "users/{uid}/garage/{bikeId}"
         * path, so an uppercase record written before this fix (or a
         * lowercase one written by iOS) both need to parse cleanly -- this
         * match is case-insensitive against [serializedValue] so either
         * casing resolves to the right case, instead of silently dropping
         * the maintenance item (or the whole bike, upstream).
         */
        fun fromSerialized(value: String?): MaintenanceItemType? =
            value?.let { v -> entries.firstOrNull { it.serializedValue.equals(v, ignoreCase = true) } }
    }
}

data class MaintenanceItem(
    val id: String = UUID.randomUUID().toString(),
    val type: MaintenanceItemType,
    val intervalMiles: Double = type.defaultIntervalMiles,
    val lastServiceMileage: Double = 0.0
)

data class Bike(
    val id: String = UUID.randomUUID().toString(),
    val nickname: String,
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val baselineOdometer: Double = 0.0,
    val isActive: Boolean = false,
    val maintenanceItems: List<MaintenanceItem> = MaintenanceItemType.entries.map {
        MaintenanceItem(type = it, lastServiceMileage = baselineOdometer)
    }
) {
    val subtitle: String
        get() {
            val parts = listOf(year, make, model).filter { it.isNotBlank() }
            return if (parts.isEmpty()) "No details added" else parts.joinToString(" ")
        }
}

/**
 * App-wide garage — SharedPreferences-backed with a one-shot Firebase pull/push
 * sync, same shape as iOS's BikeManager: per-bike keyed under
 * users/{uid}/garage/{bikeId}, whole current list re-pushed on every save
 * (bikes are few and small, so this stays simple and cheap), one-shot pull
 * on init/sign-in of anything the cloud has that isn't already local (covers
 * reinstall / sign-out-sign-in losing the local-only copy).
 */
class GarageManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _bikes = MutableStateFlow(load())
    val bikes: StateFlow<List<Bike>> = _bikes.asStateFlow()

    init {
        syncFromCloud()
    }

    fun addBike(bike: Bike) {
        val withActive = if (_bikes.value.isEmpty()) bike.copy(isActive = true) else bike
        persist(_bikes.value + withActive)
    }

    fun updateBike(bike: Bike) {
        persist(_bikes.value.map { if (it.id == bike.id) bike else it })
    }

    fun deleteBike(id: String) {
        val wasActive = _bikes.value.firstOrNull { it.id == id }?.isActive ?: false
        var remaining = _bikes.value.filterNot { it.id == id }
        if (wasActive && remaining.isNotEmpty()) {
            remaining = remaining.mapIndexed { i, b -> b.copy(isActive = i == 0) }
        }
        persist(remaining)
        deleteBikeFromCloud(id)
    }

    fun setActive(id: String) {
        persist(_bikes.value.map { it.copy(isActive = it.id == id) })
    }

    fun markServiced(bikeId: String, itemType: MaintenanceItemType, currentMileage: Double) {
        persist(_bikes.value.map { b ->
            if (b.id != bikeId) b else b.copy(maintenanceItems = b.maintenanceItems.map { item ->
                if (item.type == itemType) item.copy(lastServiceMileage = currentMileage) else item
            })
        })
    }

    fun updateInterval(bikeId: String, itemType: MaintenanceItemType, intervalMiles: Double) {
        persist(_bikes.value.map { b ->
            if (b.id != bikeId) b else b.copy(maintenanceItems = b.maintenanceItems.map { item ->
                if (item.type == itemType) item.copy(intervalMiles = intervalMiles) else item
            })
        })
    }

    /** baseline + distance of every ride tagged to this bike (recomputed on demand, never drifts). */
    fun totalMileage(bike: Bike, rides: List<com.karthik.packride.ride.RideRecord>): Double {
        val rideMiles = rides.filter { it.bikeId == bike.id }.sumOf { it.distanceMiles }
        return bike.baselineOdometer + rideMiles
    }

    private fun persist(list: List<Bike>) {
        _bikes.value = list
        prefs.edit().putString(KEY, encode(list)).apply()
        syncBikesToCloud(list)
    }

    private fun load(): List<Bike> = decode(prefs.getString(KEY, null))

    private fun syncBikesToCloud(list: List<Bike>) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseDatabase.getInstance().reference.child("users").child(uid).child("garage")
        list.forEach { bike -> ref.child(bike.id).setValue(bikeToMap(bike)) }
    }

    private fun deleteBikeFromCloud(id: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().reference.child("users").child(uid).child("garage").child(id).removeValue()
    }

    private fun syncFromCloud() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance().reference.child("users").child(uid).child("garage")
            .get()
            .addOnSuccessListener { snapshot ->
                val existingIds = _bikes.value.map { it.id }.toSet()
                var added = false
                var merged = _bikes.value
                for (child in snapshot.children) {
                    val id = child.key ?: continue
                    if (id in existingIds) continue
                    val data = child.value as? Map<*, *> ?: continue
                    val bike = runCatching { bikeFromMap(id, data) }.getOrNull() ?: continue
                    merged = merged + bike
                    added = true
                }
                if (!added) return@addOnSuccessListener
                if (merged.none { it.isActive } && merged.isNotEmpty()) {
                    val firstId = merged.first().id
                    merged = merged.map { it.copy(isActive = it.id == firstId) }
                }
                _bikes.value = merged
                prefs.edit().putString(KEY, encode(merged)).apply()
            }
    }

    private fun bikeToMap(bike: Bike): Map<String, Any?> = mapOf(
        "id" to bike.id,
        "nickname" to bike.nickname,
        "make" to bike.make,
        "model" to bike.model,
        "year" to bike.year,
        "baselineOdometer" to bike.baselineOdometer,
        "isActive" to bike.isActive,
        "maintenanceItems" to bike.maintenanceItems.map { item ->
            mapOf(
                "id" to item.id,
                "type" to item.type.serializedValue,
                "intervalMiles" to item.intervalMiles,
                "lastServiceMileage" to item.lastServiceMileage
            )
        }
    )

    private fun bikeFromMap(id: String, data: Map<*, *>): Bike {
        val itemsRaw = data["maintenanceItems"] as? List<*> ?: emptyList<Any?>()
        val items = itemsRaw.mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            val type = MaintenanceItemType.fromSerialized(m["type"] as? String) ?: MaintenanceItemType.OIL
            MaintenanceItem(
                id = m["id"] as? String ?: UUID.randomUUID().toString(),
                type = type,
                intervalMiles = (m["intervalMiles"] as? Number)?.toDouble() ?: type.defaultIntervalMiles,
                lastServiceMileage = (m["lastServiceMileage"] as? Number)?.toDouble() ?: 0.0
            )
        }
        val baseline = (data["baselineOdometer"] as? Number)?.toDouble() ?: 0.0
        return Bike(
            id = id,
            nickname = data["nickname"] as? String ?: "Bike",
            make = data["make"] as? String ?: "",
            model = data["model"] as? String ?: "",
            year = data["year"] as? String ?: "",
            baselineOdometer = baseline,
            isActive = data["isActive"] as? Boolean ?: false,
            maintenanceItems = items.ifEmpty {
                MaintenanceItemType.entries.map { MaintenanceItem(type = it, lastServiceMileage = baseline) }
            }
        )
    }

    private fun encode(list: List<Bike>): String {
        val arr = JSONArray()
        list.forEach { b ->
            val mArr = JSONArray()
            b.maintenanceItems.forEach { item ->
                mArr.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("type", item.type.serializedValue)
                        .put("intervalMiles", item.intervalMiles)
                        .put("lastServiceMileage", item.lastServiceMileage)
                )
            }
            arr.put(
                JSONObject()
                    .put("id", b.id)
                    .put("nickname", b.nickname)
                    .put("make", b.make)
                    .put("model", b.model)
                    .put("year", b.year)
                    .put("baselineOdometer", b.baselineOdometer)
                    .put("isActive", b.isActive)
                    .put("maintenanceItems", mArr)
            )
        }
        return arr.toString()
    }

    private fun decode(raw: String?): List<Bike> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val baseline = o.optDouble("baselineOdometer", 0.0)
                val maintArr = o.optJSONArray("maintenanceItems") ?: JSONArray()
                val items = (0 until maintArr.length()).map { j ->
                    val m = maintArr.getJSONObject(j)
                    val serializedType = if (m.has("type") && !m.isNull("type")) {
                        m.optString("type")
                    } else {
                        null
                    }
                    val type = MaintenanceItemType.fromSerialized(serializedType)
                        ?: MaintenanceItemType.OIL
                    MaintenanceItem(
                        id = m.optString("id", UUID.randomUUID().toString()),
                        type = type,
                        intervalMiles = m.optDouble("intervalMiles", type.defaultIntervalMiles),
                        lastServiceMileage = m.optDouble("lastServiceMileage", baseline)
                    )
                }
                Bike(
                    id = o.getString("id"),
                    nickname = o.getString("nickname"),
                    make = o.optString("make", ""),
                    model = o.optString("model", ""),
                    year = o.optString("year", ""),
                    baselineOdometer = baseline,
                    isActive = o.optBoolean("isActive", false),
                    maintenanceItems = items.ifEmpty {
                        MaintenanceItemType.entries.map { MaintenanceItem(type = it, lastServiceMileage = baseline) }
                    }
                )
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS_NAME = "packride_garage"
        private const val KEY = "bikes_v2"

        @Volatile
        private var instance: GarageManager? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = GarageManager(context.applicationContext)
                    }
                }
            }
        }

        fun get(): GarageManager =
            instance ?: error("Call GarageManager.init(Application) first")

        /**
         * Non-reactive lookup for ride-recording call sites (SoloRideSession,
         * GroupRideSession, NavRideSession, TrackModeScreen) — mirrors iOS's
         * static BikeManager.currentActiveBikeID(), reading straight from
         * SharedPreferences so those call sites don't need a GarageManager
         * instance around. Safe to call even before GarageManager.init().
         */
        fun currentActiveBikeID(context: Context): String? {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY, null) ?: return null
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.getJSONObject(it) }
                    .firstOrNull { it.optBoolean("isActive", false) }
                    ?.getString("id")
            }.getOrNull()
        }
    }
}
