package com.karthik.packride.group

import android.location.Location
import android.provider.Settings
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RTDB group-ride room — port of iOS FirebaseManager join/update/listen/leave.
 * Path: rides/{code}/riders/{deviceId}
 * Throttle location writes: 5s or 25m (same as iOS).
 */
class GroupRideFirebase(private val androidId: String, private val fcmToken: String = "") {

    private val db = FirebaseDatabase.getInstance().reference

    private val _groupRiders = MutableStateFlow<List<LiveRider>>(emptyList())
    val groupRiders: StateFlow<List<LiveRider>> = _groupRiders.asStateFlow()

    private var ridersListener: ValueEventListener? = null
    private var ridersRef: DatabaseReference? = null

    private var lastSentLocation: Location? = null
    private var lastSentMs: Long = 0

    val myDeviceId: String get() = androidId

    fun joinRide(
        rideCode: String,
        riderName: String,
        initials: String,
        isLeader: Boolean = false,
        avatarURL: String = ""
    ) {
        val code = rideCode.uppercase().trim()
        val data = mapOf(
            "id" to androidId,
            "name" to riderName,
            "initials" to initials,
            "latitude" to 0.0,
            "longitude" to 0.0,
            "speed" to 0.0,
            "timestamp" to System.currentTimeMillis() / 1000.0,
            "fcmToken" to fcmToken,
            "authUID" to (FirebaseAuth.getInstance().currentUser?.uid ?: ""),
            "isLeader" to isLeader,
            "avatarURL" to avatarURL
        )
        val authUID = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.updateChildren(
            mapOf(
                "rides/$code/riders/$androidId" to data,
                "rideMembers/$code/$authUID" to true
            )
        ).addOnSuccessListener { listenForRiders(code, authUID) }
    }

    fun updateLocation(rideCode: String, location: Location, speedMph: Double) {
        if (!shouldSend(location)) return
        val code = rideCode.uppercase().trim()
        db.child("rides").child(code).child("riders").child(androidId).updateChildren(
            mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "speed" to speedMph,
                "timestamp" to System.currentTimeMillis() / 1000.0
            )
        )
        logTrackPoint(code, location, speedMph)
        lastSentLocation = Location(location)
        lastSentMs = System.currentTimeMillis()
    }

    fun publishFinalStats(
        rideCode: String,
        riderName: String,
        initials: String,
        isLeader: Boolean,
        distanceMiles: Double,
        maxSpeedMph: Double,
        duration: String
    ) {
        val code = rideCode.uppercase().trim()
        val stats = mapOf(
            "name" to riderName,
            "initials" to initials,
            "isLeader" to isLeader,
            "distance" to distanceMiles,
            "maxSpeed" to maxSpeedMph,
            "duration" to duration,
            "timestamp" to System.currentTimeMillis() / 1000.0
        )
        db.child("rides").child(code).child("finalStats").child(androidId).setValue(stats)
    }

    fun leaveRide(rideCode: String) {
        stopListening()
        val code = rideCode.uppercase().trim()
        if (code.isNotEmpty()) {
            val updates = mutableMapOf<String, Any?>("rides/$code/riders/$androidId" to null)
            FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                updates["rideMembers/$code/$uid"] = null
            }
            db.updateChildren(updates)
        }
        _groupRiders.value = emptyList()
        lastSentLocation = null
        lastSentMs = 0
    }

    private fun listenForRiders(rideCode: String, authUID: String) {
        stopListening()
        val path = db.child("groupLocationFeeds").child(rideCode).child(authUID)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<LiveRider>()
                for (child in snapshot.children) {
                    val data = child.value as? Map<*, *> ?: continue
                    val id = data["riderUID"] as? String ?: continue
                    if (id == androidId) continue
                    val name = data["name"] as? String ?: continue
                    val initials = data["initials"] as? String ?: "?"
                    val lat = (data["latitude"] as? Number)?.toDouble() ?: continue
                    val lng = (data["longitude"] as? Number)?.toDouble() ?: continue
                    val speed = (data["speed"] as? Number)?.toDouble() ?: 0.0
                    list.add(
                        LiveRider(
                            id = id,
                            name = name,
                            initials = initials,
                            latitude = lat,
                            longitude = lng,
                            speedMph = speed,
                            isLeader = data["isLeader"] as? Boolean ?: false,
                            avatarURL = data["avatarURL"] as? String ?: ""
                        )
                    )
                }
                _groupRiders.value = list
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        path.addValueEventListener(listener)
        ridersListener = listener
        ridersRef = path
    }

    private fun stopListening() {
        val listener = ridersListener
        if (listener != null) ridersRef?.removeEventListener(listener)
        ridersListener = null
        ridersRef = null
    }

    private fun logTrackPoint(rideCode: String, location: Location, speedMph: Double) {
        val point = mapOf(
            "lat" to location.latitude,
            "lng" to location.longitude,
            "speed" to speedMph,
            "timestamp" to System.currentTimeMillis() / 1000.0
        )
        db.child("rides").child(rideCode).child("tracks").child(androidId).push().setValue(point)
    }

    private fun shouldSend(location: Location): Boolean {
        val last = lastSentLocation
        val now = System.currentTimeMillis()
        if (last == null) return true
        val timeOk = now - lastSentMs >= 5_000
        val distOk = location.distanceTo(last) >= 25f
        return timeOk || distOk
    }

    companion object {
        fun create(context: android.content.Context): GroupRideFirebase {
            val id = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: java.util.UUID.randomUUID().toString()
            // Cached by PackRideMessagingService.onNewToken() — read here so a
            // freshly-joined ride room's member record carries a token from
            // the moment it's created (iOS FirebaseManager.joinRide parity).
            val token = context.getSharedPreferences("packride_prefs", android.content.Context.MODE_PRIVATE)
                .getString("fcmToken", "") ?: ""
            return GroupRideFirebase(id, token)
        }

        // 1:1 port of iOS's JoinCodeGenerator.generate() (DesignSystem.swift):
        // a 4-character code that is either all-letters (no I/O) or all-digits
        // (no 0/1), never mixed within one code, chosen randomly per call.
        fun generateRideCode(): String {
            val letters = "ABCDEFGHJKLMNPQRSTUVWXYZ" // no I/O
            val digits = "23456789" // no 0/1
            val pool = if (kotlin.random.Random.nextBoolean()) letters else digits
            return (1..4).map { pool.random() }.joinToString("")
        }
    }
}
