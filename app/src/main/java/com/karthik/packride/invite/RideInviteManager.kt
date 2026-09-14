package com.karthik.packride.invite

import android.content.Context
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RideInvite(
    val id: String,
    val fromUID: String,
    val fromName: String,
    val rideCode: String,
    val message: String,
    val destinationName: String = "",
    val stopCount: Int = 0,
    val timestamp: Double
)

/** users/{uid}/rideInvites — iOS RideInviteManager parity. */
class RideInviteManager(context: Context) {
    private val appContext = context.applicationContext
    private val db = FirebaseDatabase.getInstance().reference
    private val _invites = MutableStateFlow<List<RideInvite>>(emptyList())
    val invites: StateFlow<List<RideInvite>> = _invites.asStateFlow()
    private var listener: ValueEventListener? = null

    private val myID: String
        get() = FirebaseAuth.getInstance().currentUser?.uid
            ?: Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: ""

    fun sendInvite(
        toUID: String,
        rideCode: String,
        fromName: String,
        destinationName: String = "",
        stopCount: Int = 0
    ) {
        if (myID.isEmpty() || toUID.isEmpty()) return
        // iOS keys one current invitation per sender and uses these exact
        // field names. Keeping the sender UID as the key also lets a newer
        // route replace that sender's stale invitation atomically.
        val data = mapOf(
            "senderName" to fromName,
            "rideCode" to rideCode.uppercase(),
            "destinationName" to destinationName,
            "stopCount" to stopCount,
            "timestamp" to System.currentTimeMillis() / 1000.0
        )
        db.child("users").child(toUID).child("rideInvites").child(myID).setValue(data)
    }

    fun listen() {
        stop()
        if (myID.isEmpty()) return
        val ref = db.child("users").child(myID).child("rideInvites")
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<RideInvite>()
                for (child in snapshot.children) {
                    val d = child.value as? Map<*, *> ?: continue
                    list.add(
                        RideInvite(
                            id = child.key ?: d["id"] as? String ?: continue,
                            fromUID = d["fromUID"] as? String ?: child.key.orEmpty(),
                            fromName = d["senderName"] as? String ?: d["fromName"] as? String ?: "Rider",
                            rideCode = d["rideCode"] as? String ?: "",
                            message = d["message"] as? String ?: d["destinationName"] as? String ?: "",
                            destinationName = d["destinationName"] as? String ?: "",
                            stopCount = (d["stopCount"] as? Number)?.toInt() ?: 0,
                            timestamp = (d["timestamp"] as? Number)?.toDouble() ?: 0.0
                        )
                    )
                }
                _invites.value = list.sortedByDescending { it.timestamp }
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(l)
        listener = l
    }

    fun dismiss(inviteId: String) {
        if (myID.isEmpty()) return
        db.child("users").child(myID).child("rideInvites").child(inviteId).removeValue()
    }

    fun stop() {
        listener?.let {
            if (myID.isNotEmpty()) {
                db.child("users").child(myID).child("rideInvites").removeEventListener(it)
            }
        }
        listener = null
    }
}
