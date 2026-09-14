package com.karthik.packride.alerts

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

data class CrashAlert(
    val id: String,
    val senderName: String,
    val timestamp: Double,
    val mapURL: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val peakG: Double? = null
)

/** In-app crash alerts — users/{uid}/crashAlerts/{senderUid} */
class CrashAlertManager(context: Context) {
    private val appContext = context.applicationContext
    private val db = FirebaseDatabase.getInstance().reference
    private val _alerts = MutableStateFlow<List<CrashAlert>>(emptyList())
    val receivedAlerts: StateFlow<List<CrashAlert>> = _alerts.asStateFlow()
    private var listener: ValueEventListener? = null

    private val myID: String
        get() = FirebaseAuth.getInstance().currentUser?.uid
            ?: Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?: ""

    fun sendCrashAlerts(
        recipientUIDs: List<String>,
        senderName: String,
        lat: Double?,
        lng: Double?,
        peakG: Double = 0.0,
        groupRideCode: String? = null,
        onDone: (String?) -> Unit = {}
    ) {
        if (myID.isEmpty()) {
            onDone("Not signed in")
            return
        }
        val recipients = recipientUIDs.filter { it.isNotEmpty() && it != myID }.toSet()
        if (recipients.isEmpty()) {
            onDone(null)
            return
        }
        val incidentRef = db.child("crashIncidents").push()
        val incidentId = incidentRef.key
        if (incidentId == null) {
            onDone("Couldn't create the safety incident.")
            return
        }
        val now = System.currentTimeMillis() / 1000.0
        val data = mutableMapOf<String, Any>(
            "senderUID" to myID,
            "senderDeviceID" to (Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID) ?: ""),
            "senderName" to senderName,
            "timestamp" to now,
            "peakG" to peakG,
            "status" to "open",
            "escalationCount" to 0,
            "nextEscalationAt" to now + 120,
            "primaryResponderUIDs" to recipients.associateWith { true }
        )
        if (!groupRideCode.isNullOrBlank()) data["groupRideCode"] = groupRideCode
        if (lat != null && lng != null) {
            data["latitude"] = lat
            data["longitude"] = lng
            data["mapURL"] = "https://maps.google.com/?q=$lat,$lng"
        }
        incidentRef.setValue(data).addOnCompleteListener { task -> onDone(task.exception?.localizedMessage) }
    }

    fun listen() {
        stop()
        if (myID.isEmpty()) return
        val ref = db.child("users").child(myID).child("crashAlerts")
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<CrashAlert>()
                for (child in snapshot.children) {
                    val data = child.value as? Map<*, *> ?: continue
                    list.add(
                        CrashAlert(
                            id = child.key ?: continue,
                            senderName = data["senderName"] as? String ?: continue,
                            timestamp = (data["timestamp"] as? Number)?.toDouble() ?: 0.0,
                            mapURL = data["mapURL"] as? String,
                            latitude = (data["latitude"] as? Number)?.toDouble(),
                            longitude = (data["longitude"] as? Number)?.toDouble(),
                            peakG = (data["peakG"] as? Number)?.toDouble()
                        )
                    )
                }
                _alerts.value = list.sortedByDescending { it.timestamp }
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(l)
        listener = l
    }

    fun dismiss(senderId: String) {
        if (myID.isEmpty()) return
        db.child("users").child(myID).child("crashAlerts").child(senderId).removeValue()
    }

    fun acknowledge(alert: CrashAlert, responderName: String, onDone: (String?) -> Unit = {}) {
        if (myID.isEmpty()) {
            onDone("Sign in is required to acknowledge an incident.")
            return
        }
        db.child("crashIncidents").child(alert.id).updateChildren(
            mapOf(
                "status" to "acknowledged",
                "acknowledgedByUID" to myID,
                "acknowledgedByName" to responderName,
                "acknowledgedAt" to System.currentTimeMillis() / 1000.0
            )
        ).addOnCompleteListener { task ->
            if (task.isSuccessful) dismiss(alert.id)
            onDone(task.exception?.localizedMessage)
        }
    }

    fun stop() {
        listener?.let {
            if (myID.isNotEmpty()) {
                db.child("users").child(myID).child("crashAlerts").removeEventListener(it)
            }
        }
        listener = null
    }
}
