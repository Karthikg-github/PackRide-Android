package com.karthik.packride.waypoints

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Mirrors a group ride's waypoints to Firebase so every rider's phone can see
 * — and navigate to — the same plan, not just the leader's own device.
 * Kotlin port of iOS's GroupWaypointSync (WaypointsView.swift).
 *
 * Stored as one opaque JSON-encoded STRING rather than a native RTDB
 * array/object — same reasoning as iOS: Realtime Database can silently
 * coerce a written array into an object (numeric-string keys) whenever
 * there's a gap, which would corrupt waypoint ORDER. A single string
 * sidesteps that ambiguity.
 */
object GroupWaypointSync {
    private val db get() = FirebaseDatabase.getInstance().reference

    fun publish(rideCode: String, waypoints: List<Waypoint>) {
        if (rideCode.isEmpty()) return
        db.child("rides").child(rideCode).child("waypointsJSON").setValue(waypoints.toJsonString())
    }

    /**
     * Live-updates onUpdate as the leader adds/reorders/removes stops — not a
     * one-time fetch, since a group ride can already be underway when a
     * rider's app opens this screen. Call stopListening with the returned
     * listener (and code) when the screen goes away.
     */
    fun listen(rideCode: String, onUpdate: (List<Waypoint>) -> Unit): ValueEventListener? {
        if (rideCode.isEmpty()) return null
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val jsonString = snapshot.value as? String
                onUpdate(jsonString?.let { waypointsFromJsonString(it) } ?: emptyList())
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }
        db.child("rides").child(rideCode).child("waypointsJSON").addValueEventListener(listener)
        return listener
    }

    fun stopListening(rideCode: String, listener: ValueEventListener?) {
        if (rideCode.isEmpty() || listener == null) return
        db.child("rides").child(rideCode).child("waypointsJSON").removeEventListener(listener)
    }

    /** One-time read — for screens that just need "what's the plan right now." */
    fun fetchOnce(rideCode: String, onResult: (List<Waypoint>) -> Unit) {
        if (rideCode.isEmpty()) {
            onResult(emptyList())
            return
        }
        db.child("rides").child(rideCode).child("waypointsJSON").get()
            .addOnSuccessListener { snap ->
                val jsonString = snap.value as? String
                onResult(jsonString?.let { waypointsFromJsonString(it) } ?: emptyList())
            }
            .addOnFailureListener { onResult(emptyList()) }
    }
}
