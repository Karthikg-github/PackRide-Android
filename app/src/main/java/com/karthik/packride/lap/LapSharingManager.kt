package com.karthik.packride.lap

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.karthik.packride.gpx.GPXStorage
import com.karthik.packride.ride.RideRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class SharedLapSession(
    val id: String,
    val ownerUid: String,
    val sessionId: String,
    val ownerName: String,
    val dateMs: Long,
    val trackName: String,
    val bestLapTime: Double,
    val laps: List<Double>,
    val lapStartTimestamps: List<Long> = emptyList()
)

/** Android implementation of iOS LapSharingManager's exact RTDB/Storage contract. */
class LapSharingManager(context: Context) {
    private val appContext = context.applicationContext
    private val db = FirebaseDatabase.getInstance().reference
    private val _sharedWithMe = MutableStateFlow<List<SharedLapSession>>(emptyList())
    val sharedWithMe: StateFlow<List<SharedLapSession>> = _sharedWithMe.asStateFlow()
    private var listener: ValueEventListener? = null
    private val myID get() = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()

    fun shareSession(session: RideRecord, ownerName: String, recipientUID: String, onDone: (String?) -> Unit) {
        val me = myID
        if (me.isEmpty()) return onDone("Sign in to share a track session.")
        if (recipientUID == me) return onDone("You can't share a session with yourself.")
        val filename = session.gpxFileName ?: return onDone("This session has no recorded route to share.")
        val file = GPXStorage.resolve(appContext.filesDir, filename)
        if (!file.exists()) return onDone("This session's GPX file isn't available on this device.")
        val ref = FirebaseStorage.getInstance().reference.child("sharedLapGpx/$me/${session.id}.gpx")
        ref.putFile(Uri.fromFile(file), StorageMetadata.Builder().setContentType("application/gpx+xml").build())
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: Exception("Upload failed")
                ref.downloadUrl
            }
            .addOnSuccessListener {
                val data = mapOf(
                    "ownerUid" to me, "sessionId" to session.id, "ownerName" to ownerName,
                    "date" to session.dateMs / 1000.0,
                    "trackName" to session.trackName,
                    "bestLapTime" to (session.lapTimes.minOrNull() ?: 0.0),
                    "laps" to session.lapTimes,
                    "lapStartTimestamps" to session.lapStartTimestamps
                )
                db.child("users").child(recipientUID).child("sharedLapSessions")
                    .child("${me}_${session.id}").setValue(data)
                    .addOnCompleteListener { onDone(it.exception?.localizedMessage) }
            }
            .addOnFailureListener { onDone(it.localizedMessage) }
    }

    fun start() {
        stop()
        val me = myID
        if (me.isEmpty()) return
        val ref = db.child("users").child(me).child("sharedLapSessions")
        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _sharedWithMe.value = snapshot.children.mapNotNull { child ->
                    val d = child.value as? Map<*, *> ?: return@mapNotNull null
                    val laps = (d["laps"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() }.orEmpty()
                    val starts = (d["lapStartTimestamps"] as? List<*>)?.mapNotNull { (it as? Number)?.toLong() }.orEmpty()
                    SharedLapSession(
                        child.key ?: return@mapNotNull null,
                        d["ownerUid"] as? String ?: return@mapNotNull null,
                        d["sessionId"] as? String ?: return@mapNotNull null,
                        d["ownerName"] as? String ?: "Rider",
                        ((d["date"] as? Number)?.toDouble()?.times(1000))?.toLong() ?: 0,
                        d["trackName"] as? String ?: "Track Session",
                        (d["bestLapTime"] as? Number)?.toDouble() ?: 0.0,
                        laps, starts
                    )
                }.sortedByDescending { it.dateMs }
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }.also { ref.addValueEventListener(it) }
    }

    fun stop() {
        val me = myID
        listener?.let { if (me.isNotEmpty()) db.child("users").child(me).child("sharedLapSessions").removeEventListener(it) }
        listener = null
    }

    fun download(session: SharedLapSession, onDone: (File?, String?) -> Unit) {
        val dir = File(appContext.filesDir, "sharedLapGpx").apply { mkdirs() }
        val file = File(dir, "${session.ownerUid}_${session.sessionId}.gpx")
        if (file.exists()) return onDone(file, null)
        FirebaseStorage.getInstance().reference.child("sharedLapGpx/${session.ownerUid}/${session.sessionId}.gpx")
            .getFile(file).addOnSuccessListener { onDone(file, null) }
            .addOnFailureListener { onDone(null, it.localizedMessage) }
    }
}
