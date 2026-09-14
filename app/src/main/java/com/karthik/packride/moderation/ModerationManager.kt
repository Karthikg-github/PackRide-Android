package com.karthik.packride.moderation

import android.content.Context
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BlockedRider(val id: String, val name: String)

/** Shared Firebase contract for user blocking and user-generated-content reports. */
class ModerationManager(context: Context) {
    private val appContext = context.applicationContext
    private val db = FirebaseDatabase.getInstance().reference
    private var blockedListener: ValueEventListener? = null

    private val _blockedRiders = MutableStateFlow<List<BlockedRider>>(emptyList())
    val blockedRiders: StateFlow<List<BlockedRider>> = _blockedRiders.asStateFlow()

    private val myID: String
        get() = FirebaseAuth.getInstance().currentUser?.uid
            ?: Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()

    fun start() {
        stop()
        val me = myID
        if (me.isEmpty()) return
        val ref = db.child("users").child(me).child("blockedUsers")
        blockedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _blockedRiders.value = snapshot.children.mapNotNull { child ->
                    val id = child.key ?: return@mapNotNull null
                    val name = child.child("name").getValue(String::class.java)
                        ?: child.getValue(String::class.java)
                        ?: "Blocked rider"
                    BlockedRider(id, name)
                }.sortedBy { it.name.lowercase() }
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }.also { ref.addValueEventListener(it) }
    }

    fun stop() {
        val me = myID
        blockedListener?.let { if (me.isNotEmpty()) db.child("users").child(me).child("blockedUsers").removeEventListener(it) }
        blockedListener = null
    }

    fun block(riderID: String, riderName: String, onDone: (String?) -> Unit = {}) {
        val me = myID
        if (me.isEmpty() || riderID.isEmpty() || riderID == me) return onDone("Unable to block this rider.")
        val value = mapOf("name" to riderName, "blockedAt" to ServerValue.TIMESTAMP)
        db.child("users").child(me).child("blockedUsers").child(riderID).setValue(value)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Relationship cleanup is best-effort and deliberately happens only
                    // after the privacy-critical block itself is committed.
                    db.updateChildren(mapOf(
                        "users/$me/following/$riderID" to null,
                        "users/$me/followers/$riderID" to null,
                        "users/$me/followRequests/$riderID" to null,
                        "users/$riderID/following/$me" to null,
                        "users/$riderID/followers/$me" to null,
                        "users/$riderID/followRequests/$me" to null,
                        "locationFeeds/$me/$riderID" to null,
                        "locationFeeds/$riderID/$me" to null
                    ))
                }
                onDone(task.exception?.localizedMessage)
            }
    }

    fun unblock(riderID: String, onDone: (String?) -> Unit = {}) {
        val me = myID
        if (me.isEmpty()) return onDone("Sign in to manage blocked riders.")
        db.child("users").child(me).child("blockedUsers").child(riderID).removeValue()
            .addOnCompleteListener { task -> onDone(task.exception?.localizedMessage) }
    }

    fun report(
        targetType: String,
        targetID: String,
        targetAuthorID: String,
        reason: String,
        onDone: (String?) -> Unit = {}
    ) {
        val me = myID
        if (me.isEmpty()) return onDone("Sign in to report content.")
        val key = db.child("moderationReports").push().key ?: return onDone("Unable to create report.")
        val value = mapOf(
            "reporterUID" to me,
            "targetType" to targetType,
            "targetID" to targetID,
            "targetAuthorUID" to targetAuthorID,
            "reason" to reason,
            "status" to "open",
            "createdAt" to ServerValue.TIMESTAMP,
            "sourcePlatform" to "android"
        )
        db.child("moderationReports").child(key).setValue(value)
            .addOnCompleteListener { task -> onDone(task.exception?.localizedMessage) }
    }
}
